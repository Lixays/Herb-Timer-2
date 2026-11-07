#!/usr/bin/env python3
"""
Makes the BUILT app work fully offline.

During the GitHub build this downloads every picture and font the web page uses ONCE, stores them inside the app
(assets/public/img/wiki and assets/public/fonts) and rewrites index.html to use those local copies. The finished
APK never contacts the OSRS wiki or Google.

The build STOPS with an error if something cannot be downloaded, so you never ship an app that still depends
on the internet. (If you ever want to allow that, add an empty file called allow_online.txt to the repo.)

Usage: python3 widgets/make_offline.py <path to android/app/src/main/assets/public>
"""
import os
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

PUBLIC = Path(sys.argv[1])
REPO = Path(__file__).resolve().parent.parent
ALLOW_ONLINE = (REPO / "allow_online.txt").exists()

UA_FILES = "HerbTimerBuild/1.0 (personal GitHub Actions build of an OSRS timer app; bundles pictures once into the app)"
UA_BROWSER = ("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) "
              "Chrome/126.0.0.0 Mobile Safari/537.36")   # Google Fonts only hands out modern .woff2 files to a browser

# Where each host really lives (only changed by the tests, which point these at a local stand-in server)
MIRRORS = [("https://oldschool.runescape.wiki", "HERB_WIKI_MIRROR"),
           ("https://fonts.googleapis.com", "HERB_GF_MIRROR"),
           ("https://fonts.gstatic.com", "HERB_GS_MIRROR")]


def real_url(url):
    for host, env in MIRRORS:
        if url.startswith(host) and os.environ.get(env):
            return os.environ[env].rstrip("/") + url[len(host):]
    return url


def fetch(url, ua, tries=3):
    last = None
    for attempt in range(tries):
        try:
            req = urllib.request.Request(real_url(url), headers={"User-Agent": ua, "Accept": "*/*"})
            with urllib.request.urlopen(req, timeout=30) as r:
                data = r.read()
            if not data:
                raise ValueError("empty answer")
            return data
        except Exception as e:  # network trouble / 403 / 404: try again, then give up
            last = e
            time.sleep(float(os.environ.get("HERB_RETRY_DELAY", "1.5")) * (attempt + 1))
    raise last


SIGNATURES = (b"\x89PNG", b"GIF8", b"\xff\xd8\xff", b"RIFF", b"<svg", b"<?xml")


def looks_like_image(data):
    return data[:8].startswith(SIGNATURES) or any(data.startswith(s) for s in SIGNATURES)


def safe_name(text):
    text = urllib.parse.unquote(text)
    return re.sub(r"[^A-Za-z0-9._-]+", "_", text).strip("_") or "file"


html_path = PUBLIC / "index.html"
html = html_path.read_text(encoding="utf-8")
failures = []

# ---------------------------------------------------------------- fonts
fonts_dir = PUBLIC / "fonts"
font_files = 0
link = re.search(r'<link[^>]+href="(https://fonts\.googleapis\.com/css[^"]*)"[^>]*>', html)
if link:
    try:
        css = fetch(link.group(1), UA_BROWSER).decode("utf-8")
        fonts_dir.mkdir(parents=True, exist_ok=True)
        for i, furl in enumerate(dict.fromkeys(re.findall(r"url\((https://fonts\.gstatic\.com/[^)]+)\)", css))):
            name = f"{i:02d}_{safe_name(furl.rsplit('/', 1)[-1])}"
            (fonts_dir / name).write_bytes(fetch(furl, UA_BROWSER))
            css = css.replace(furl, "fonts/" + name)
            font_files += 1
        if "fonts.gstatic.com" in css:
            raise ValueError("font files left in the style sheet")
        html = html.replace(link.group(0), "<style>\n" + css + "\n    </style>")
    except Exception as e:
        failures.append(f"fonts ({link.group(1)[:60]}...): {e}")
html = re.sub(r'\s*<link[^>]+rel="preconnect"[^>]+href="https://fonts\.(?:googleapis|gstatic)\.com"[^>]*>', "", html)

# ---------------------------------------------------------------- pictures
img_dir = PUBLIC / "img" / "wiki"
urls = sorted(set(re.findall(r"https://oldschool\.runescape\.wiki/[^\s\"'<>)\\]+", html)), key=len, reverse=True)
done = {}
for url in urls:
    path = urllib.parse.urlparse(url).path
    name = safe_name(path.split("/images/", 1)[-1].replace("/", "_"))
    try:
        data = fetch(url, UA_FILES)
        if not looks_like_image(data):
            raise ValueError("did not get a picture (got a web page?)")
        img_dir.mkdir(parents=True, exist_ok=True)
        target = img_dir / name
        if target.exists() and target.read_bytes() != data:     # two different pictures with the same file name
            name = f"{len(done):02d}_{name}"
            target = img_dir / name
        target.write_bytes(data)
        done[url] = "img/wiki/" + name
        time.sleep(0.25)    # be polite to the wiki
    except Exception as e:
        failures.append(f"{url}: {e}")

for url, local in done.items():      # longest URLs first, so a URL never gets cut short by a shorter one
    html = html.replace(url, local)

# ---------------------------------------------------------------- check + report
if failures and not ALLOW_ONLINE:
    print("\nOFFLINE BUNDLING FAILED - the build is stopped so the app is not shipped needing the internet.\n")
    for f in failures:
        print("  could not download:", f)
    print("\nRe-run the build in a few minutes. To build anyway (the app then loads those pictures online),\n"
          "add an empty file called allow_online.txt to the repo.")
    sys.exit(1)

leftovers = sorted(set(re.findall(r"https?://[^\s\"'<>)\\]*(?:runescape\.wiki|googleapis|gstatic)[^\s\"'<>)\\]*", html)))
if leftovers and not ALLOW_ONLINE:
    print("\nOFFLINE CHECK FAILED - the page still points at the internet:")
    for u in leftovers:
        print("  ", u)
    sys.exit(1)

html_path.write_text(html, encoding="utf-8")
print(f"offline   {len(done)} pictures and {font_files} font files bundled into the app"
      + (f"  (WARNING: {len(failures)} still online because allow_online.txt exists)" if failures else ""))
