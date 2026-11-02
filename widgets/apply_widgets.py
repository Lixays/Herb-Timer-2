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
for src in sorted((HERE / "java").glob("*.java")):
    shutil.copy2(src, PKG_DIR / src.name)
print("java      ok")


# 2. Resources (layouts, drawables, widget info, strings).
#    Never overwrite a birdhouse picture that is already there (e.g. one you put in android-res/).
def copy_res(src, dst):
    if os.path.basename(dst) == "bird_house.png" and os.path.exists(dst):
        return dst
    return shutil.copy2(src, dst)


shutil.copytree(HERE / "res", RES, dirs_exist_ok=True, copy_function=copy_res)
print("res       ok")


# 3. Birdhouse picture: use the real one from the OSRS wiki if the build can download it.
#    Falls back to the drawn birdhouse that ships in widgets/res if anything goes wrong.
def fetch_birdhouse():
    own = REPO / "android-res" / "drawable-nodpi" / "bird_house.png"
    if own.exists():
        print("birdhouse using your own android-res/drawable-nodpi/bird_house.png")
        return
    dest = RES / "drawable-nodpi" / "bird_house.png"
    base = os.environ.get("HERB_WIKI_IMAGES", "https://oldschool.runescape.wiki/images/")
    names = ["Bird_house_detail.png", "Oak_bird_house_detail.png",
             "Maple_bird_house_detail.png", "Redwood_bird_house_detail.png"]
    for name in names:
        try:
            req = urllib.request.Request(base + name, headers={
                "User-Agent": "HerbTimerBuild/1.0 (personal GitHub Actions build of a timer app)"})
            with urllib.request.urlopen(req, timeout=20) as r:
                data = r.read()
            if data[:8] != b"\x89PNG\r\n\x1a\n" or len(data) < 2000:
                continue
            tmp = dest.with_suffix(".download.png")
            tmp.write_bytes(data)
            small = dest.with_suffix(".small.png")
            resized = False
            for tool in (["magick"], ["convert"]):
                if shutil.which(tool[0]):
                    r2 = subprocess.run(tool + [str(tmp), "-trim", "+repage", "-resize", "320x320>", str(small)],
                                        capture_output=True)
                    if r2.returncode == 0 and small.exists():
                        resized = True
                        break
            if not resized:
                try:
                    from PIL import Image
                    im = Image.open(tmp).convert("RGBA")
                    im = im.crop(im.getchannel("A").getbbox() or (0, 0, im.width, im.height))
                    im.thumbnail((320, 320))
                    im.save(small)
                    resized = True
                except Exception:
                    pass
            shutil.move(str(small if resized else tmp), str(dest))
            for leftover in (tmp, small):
                if leftover.exists():
                    leftover.unlink()
            print(f"birdhouse using the wiki picture ({name})")
            return
        except Exception as e:  # network / missing file: try the next name
            print(f"birdhouse {name}: {e}")
    print("birdhouse using the drawn birdhouse (could not download the wiki picture)")


try:
    fetch_birdhouse()
except Exception as e:
    print("birdhouse fallback in use:", e)

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

print("\nDone - widgets are in the build.")
