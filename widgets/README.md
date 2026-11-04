# Herb Timer widgets + native notifications

Added on top of the web app, all in `widgets/` (copied into the Android project by `apply_widgets.py`):

- **Widgets 1x1 / 2x1** - plant picture + live countdown to the patch that finishes last.
  1x1: tap the **plant** = plant empty patches and restart ready ones (no app opening); tap the rest = open the app.
  2x1: the opposite - tap the **plant** = open the app; tap the rest = plant / restart everything.
  Top text on both: the patch name, "Growing", "Harvest" or "Plant".
- **"Herbs ready" notifications** - sent by the Android side, so the buttons work with the app closed:
  **Restart all** (replants everything that is ready) and **Snooze 10 min**.

- **Status-bar icon** while anything is growing or ready.
- Changes made from a button are handed to the app the next time it opens (or within ~3 s if it's open).

`www/index.html` is unchanged from V1_6 - it already contained the code that talks to this.
- **Limpwurt Root + Birdhouse widgets (1x1)** - a picture, the minutes left underneath and a top line
  ("Plant"/"Limpwurt"/"Harvest", "Place"/"Birdhouses"/"Collect"). Same taps as the herb 1x1:
  tap the **picture** = restart that countdown (no app opening), tap the rest = open the app.
  Each has its own alert when it ends, with a restart button. Timers: Limpwurt 20 min, Birdhouses 50 min.
  The pictures are downloaded from the OSRS wiki during the build; if that fails, drawn ones are used.
  To use your own, put them at `android-res/drawable-nodpi/limp_root.png` and `bird_house.png`.
- **Deathpile widget (1x1)** - white skull, turns into the red skull for the last 15 minutes. Tap the skull = start the 60 minute countdown.
  An alert "DEATHPILE EXPIRES SOON" (white skull icon) arrives 15 minutes before it ends.
- **Themes everywhere** - all widgets use the app's theme (and Dark Mode). The app icon has one variant per theme
  (`android-res/`), switched with activity-aliases that `apply_widgets.py` adds to the manifest.
- **Deathpile** - while a deathpile countdown runs there is a silent notification "Deathpile ACTIVE / Despawn in 42m" that stays
  and updates every minute (it disappears when the timer is cleared or runs out). The widget shows a pile of bones while idle.
- **Limpwurt widget** - flower growth stages (5-minute tick lined up with the herb tick); says "Empty" when nothing is planted.

