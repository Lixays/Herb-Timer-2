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

