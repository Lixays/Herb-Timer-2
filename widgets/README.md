# Herb Timer widgets + native notifications

Added on top of the web app, all in `widgets/` (copied into the Android project by `apply_widgets.py`):

- **Widgets 1x1 / 2x1** - plant picture + live countdown to the patch that finishes last.
  1x1: tap the **plant** = plant empty patches and restart ready ones (no app opening); tap the rest = open the app.
  2x1: the opposite - tap the **plant** = open the app; tap the rest = plant / restart everything.
  Top text on both: the patch name, "Growing", "Harvest" or "Plant".
- **"Herbs ready" notifications** - sent by the Android side, so the buttons work with the app closed:
  **Restart all** (replants everything that is ready) and **Snooze 10 min**.
- **Birdhouse widget (1x1)** - picture of a birdhouse, "Birdhouses" / "Collect" / "Place" on top and the minutes left underneath.
  Tap it = restart the 50 minute countdown (no app opening). An alert arrives when it ends, with a "Restart timer" button.
  The picture is downloaded from the OSRS wiki during the build; if that fails a drawn birdhouse is used.
  To use your own picture, put it at `android-res/drawable-nodpi/bird_house.png`.
- **Status-bar icon** while anything is growing or ready.
- Changes made from a button are handed to the app the next time it opens (or within ~3 s if it's open).

`www/index.html` is unchanged from V1_6 - it already contained the code that talks to this.
