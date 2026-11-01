# Herb Timer widgets + native notifications

Added on top of the web app, all in `widgets/` (copied into the Android project by `apply_widgets.py`):

- **Widgets 1x1 / 2x1** - plant picture + live countdown to the patch that finishes last.
  Tap the **plant** = plant empty patches and restart ready ones (no app opening).
  Tap the **text** = open the app.
- **"Herbs ready" notifications** - sent by the Android side, so the buttons work with the app closed:
  **Restart all** (replants everything that is ready) and **Snooze 10 min**.
- **Status-bar icon** while anything is growing or ready.
- Changes made from a button are handed to the app the next time it opens (or within ~3 s if it's open).

`www/index.html` is unchanged from V1_6 - it already contained the code that talks to this.
