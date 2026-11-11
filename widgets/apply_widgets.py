#!/usr/bin/env python3
"""
Adds the Herb Timer home-screen widgets (herb 1x1 + 2x1, birdhouse 1x1) and the native
notification code to the Capacitor Android project.

Run it AFTER the android project exists (npx cap add android / npx cap sync android)
and BEFORE gradle builds it:

    python3 widgets/apply_widgets.py

Safe to run more than once. Optional argument: path to android/app/src/main.
"""
import os
import re
import shutil
import subprocess
import sys
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent
MAIN = Path(sys.argv[1]) if len(sys.argv) > 1 else REPO / "android" / "app" / "src" / "main"
PKG_DIR = MAIN / "java" / "com" / "example" / "herbtimer"
RES = MAIN / "res"

if not MAIN.is_dir():
    sys.exit(f"Android project not found at {MAIN}\n"
             "Run `npx cap add android` (or `npx cap sync android`) before this script.")

# 1. Java sources
PKG_DIR.mkdir(parents=True, exist_ok=True)
# Classes from older versions of this package that no longer exist (they would break the build if copied)
OBSOLETE_JAVA = {"HerbBird.java", "BirdWidgetRenderer.java"}
for src in sorted((HERE / "java").glob("*.java")):
    if src.name in OBSOLETE_JAVA:
        continue
    shutil.copy2(src, PKG_DIR / src.name)
print("java      ok")


# 2. Resources (layouts, drawables, widget info, strings, all the widget pictures)
shutil.copytree(HERE / "res", RES, dirs_exist_ok=True)
print("res       ok")

# 4. Manifest: widget + receiver entries and permissions (each added only if missing)
manifest = MAIN / "AndroidManifest.xml"
text = manifest.read_text(encoding="utf-8")
if "</application>" not in text:
    sys.exit("Could not find </application> in AndroidManifest.xml")

COMPONENTS = {
    "HerbWidgetProvider1x1": '''
        <receiver
            android:name="com.example.herbtimer.HerbWidgetProvider1x1"
            android:exported="false"
            android:label="Herb Timer 1x1">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/herb_widget_1x1_info" />
        </receiver>
''',
    "HerbWidgetProvider2x1": '''
        <receiver
            android:name="com.example.herbtimer.HerbWidgetProvider2x1"
            android:exported="false"
            android:label="Herb Timer 2x1">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/herb_widget_2x1_info" />
        </receiver>
''',
    "BirdWidgetProvider1x1": '''
        <receiver
            android:name="com.example.herbtimer.BirdWidgetProvider1x1"
            android:exported="false"
            android:label="Birdhouse Timer 1x1">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/bird_widget_1x1_info" />
        </receiver>
''',
    "LimpWidgetProvider1x1": '''
        <receiver
            android:name="com.example.herbtimer.LimpWidgetProvider1x1"
            android:exported="false"
            android:label="Limpwurt Timer 1x1">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/limp_widget_1x1_info" />
        </receiver>
''',
    "DeathWidgetProvider1x1": '''
        <receiver
            android:name="com.example.herbtimer.DeathWidgetProvider1x1"
            android:exported="false"
            android:label="Deathpile Timer 1x1">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/death_widget_1x1_info" />
        </receiver>
''',
    "HerbReceiver": '''
        <receiver
            android:name="com.example.herbtimer.HerbReceiver"
            android:exported="false" />
''',
    "HerbBootReceiver": '''
        <receiver
            android:name="com.example.herbtimer.HerbBootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>
''',
}
added = []
for name, xml in COMPONENTS.items():
    if f"com.example.herbtimer.{name}" not in text:
        text = text.replace("</application>", xml + "\n    </application>", 1)
        added.append(name)
for name in ("RECEIVE_BOOT_COMPLETED", "POST_NOTIFICATIONS", "SCHEDULE_EXACT_ALARM"):
    if "android.permission." + name not in text:
        text = text.replace("</manifest>", f'    <uses-permission android:name="android.permission.{name}" />\n</manifest>', 1)
        added.append(name)

# Launcher icon that follows the app theme: one <activity-alias> per theme, exactly one enabled at a time.
# The launcher entry moves from MainActivity to the Old School alias; the app switches aliases at runtime.
ICONS = [("Oldschool", "ic_launcher", "ic_launcher_round", "true"),
         ("Iron", "ic_launcher_iron", "ic_launcher_iron_round", "false"),
         ("Uim", "ic_launcher_uim", "ic_launcher_uim_round", "false"),
         ("Hcim", "ic_launcher_hcim", "ic_launcher_hcim_round", "false"),
         ("Gim", "ic_launcher_gim", "ic_launcher_gim_round", "false"),
         ("Hcworld", "ic_launcher_hcworld", "ic_launcher_hcworld_round", "false")]
if "com.example.herbtimer.IconOldschool" not in text:
    launcher = re.compile(r'[ \t]*<intent-filter>\s*<action android:name="android\.intent\.action\.MAIN"\s*/>\s*'
                          r'<category android:name="android\.intent\.category\.LAUNCHER"\s*/>\s*</intent-filter>[ \t]*\n?')
    stripped, n = launcher.subn("", text, count=1)
    if n == 1:
        aliases = ""
        for name, icon, round_icon, enabled in ICONS:
            aliases += f'''
        <activity-alias
            android:name="com.example.herbtimer.Icon{name}"
            android:enabled="{enabled}"
            android:exported="true"
            android:icon="@mipmap/{icon}"
            android:roundIcon="@mipmap/{round_icon}"
            android:label="@string/app_name"
            android:targetActivity="com.example.herbtimer.MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>
'''
        text = stripped.replace("</application>", aliases + "\n    </application>", 1)
        added.append("theme icons")
    else:
        print("manifest  WARNING: launcher entry not found, theme icons skipped")
manifest.write_text(text, encoding="utf-8")
print("manifest ", "added " + ", ".join(added) if added else "already patched")

# 5. MainActivity: register the HerbWidget plugin so the web app can reach it
STOCK = '''package com.example.herbtimer;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(HerbWidgetPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
'''
activity = PKG_DIR / "MainActivity.java"
if not activity.exists():
    activity.write_text(STOCK, encoding="utf-8")
    print("activity  created")
else:
    t = activity.read_text(encoding="utf-8")
    if "HerbWidgetPlugin" in t:
        print("activity  already patched")
    else:
        if re.search(r"super\.onCreate\(", t):
            t = re.sub(r"(\n[ \t]*)(super\.onCreate\()", r"\1registerPlugin(HerbWidgetPlugin.class);\1\2", t, count=1)
        else:
            end = t.rstrip().rfind("}")
            t = (t[:end] + "\n    @Override\n    public void onCreate(Bundle savedInstanceState) {\n"
                 "        registerPlugin(HerbWidgetPlugin.class);\n"
                 "        super.onCreate(savedInstanceState);\n    }\n" + t[end:])
        if "import android.os.Bundle;" not in t:
            t = re.sub(r"(package [^;]+;)", r"\1\n\nimport android.os.Bundle;", t, count=1)
        activity.write_text(t, encoding="utf-8")
        print("activity  patched")

# 5b. Version: the Android app info shows the same version as the app's info box
try:
    import json
    version = json.loads((REPO / "package.json").read_text(encoding="utf-8")).get("version", "")
    gradle = MAIN.parent.parent / "build.gradle"
    if version and gradle.exists():
        g = gradle.read_text(encoding="utf-8")
        g2, n = re.subn(r'versionName\s+"[^"]*"', f'versionName "{version}"', g, count=1)
        if n:
            gradle.write_text(g2, encoding="utf-8")
            print(f"version   {version}")
except Exception as e:
    print("version   skipped:", e)

# 6. Offline: bundle every picture and font the page uses into the app (nothing is loaded from the internet)
PUBLIC = MAIN / "assets" / "public"
if (PUBLIC / "index.html").exists():
    result = subprocess.run([sys.executable, str(HERE / "make_offline.py"), str(PUBLIC)])
    if result.returncode != 0:
        sys.exit(result.returncode)
else:
    print("offline   skipped: built web files not found (run `npx cap sync android` first)")

print("\nDone - widgets are in the build.")
