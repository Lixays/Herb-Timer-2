#!/usr/bin/env python3
"""
Adds the Herb Timer home-screen widgets (1x1 and 2x1) to the Capacitor Android project.

Run it AFTER the android project exists (npx cap add android / npx cap sync android)
and BEFORE gradle builds it:

    python3 widgets/apply_widgets.py

Safe to run more than once. Optional argument: path to android/app/src/main.
"""
import re
import shutil
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
MAIN = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE.parent / "android" / "app" / "src" / "main"
PKG_DIR = MAIN / "java" / "com" / "example" / "herbtimer"

if not MAIN.is_dir():
    sys.exit(f"Android project not found at {MAIN}\n"
             "Run `npx cap add android` (or `npx cap sync android`) before this script.")

# 1. Java sources
PKG_DIR.mkdir(parents=True, exist_ok=True)
for src in sorted((HERE / "java").glob("*.java")):
    shutil.copy2(src, PKG_DIR / src.name)
print("java      ok")

# 2. Resources (layouts, drawables, widget info, strings)
shutil.copytree(HERE / "res", MAIN / "res", dirs_exist_ok=True)
print("res       ok")

# 3. Manifest: register the two widget receivers
manifest = MAIN / "AndroidManifest.xml"
text = manifest.read_text(encoding="utf-8")
if "HerbWidgetProvider1x1" in text:
    print("manifest  already patched")
else:
    receivers = '''
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

    '''
    if "</application>" not in text:
        sys.exit("Could not find </application> in AndroidManifest.xml")
    manifest.write_text(text.replace("</application>", receivers + "</application>", 1), encoding="utf-8")
    print("manifest  patched")

# 4. MainActivity: register the HerbWidget plugin so the web app can reach it
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

print("\nDone - widgets are in the build.")
