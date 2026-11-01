# Herb Timer home-screen widgets

Two widgets: **1x1** and **2x1**. Each shows a plant that grows as your patches grow
(seed -> 3 growth stages -> ready with a gold sparkle) and a live countdown to the patch
that finishes LAST. Empty = soil only, ready = "Ready!". Tap = opens the app.

## Add to the repo
1. Drop `widgets/` and `www/index.html` into the repo root (replace `www/index.html`).
2. In your GitHub workflow, after the step that creates/syncs the Android project
   (`npx cap add android` / `npx cap sync android`) and before Gradle builds it, add:

       - name: Add home-screen widgets
         run: python3 widgets/apply_widgets.py

3. Build, install, long-press the home screen -> Widgets -> Herb Timer.
   Open the app once so it can send the timers to the widgets.

## What the script does
- copies `widgets/java/*` into `com/example/herbtimer/`
- copies `widgets/res/*` into the app's `res/`
- adds the two `<receiver>` entries to `AndroidManifest.xml`
- registers the `HerbWidget` plugin in `MainActivity` (your `index.html` already calls it)

## What changed in index.html
Two lines: notifications keep going through the notification plugin, and the timers are
additionally pushed to the widgets.
