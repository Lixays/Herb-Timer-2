package com.example.herbtimer;

import android.content.Context;

/**
 * How often the countdown text on the widgets and the Deathpile notification is redrawn.
 * Normal: whenever the minute number changes. Battery saver: every 5 minutes.
 * Both line up with the end of the countdown, so the real events (growth stages, the red 15-minute mark,
 * the finish) are never late: they all sit on 5-minute marks.
 */
final class PowerMode {
    static final long NORMAL_STEP_MS = 60L * 1000;
    static final long SAVER_STEP_MS = 5L * 60 * 1000;

    private PowerMode() {}

    static long step(Context c) {
        return HerbWidgetStore.saver(c) ? SAVER_STEP_MS : NORMAL_STEP_MS;
    }

    /** Milliseconds until the next redraw of a countdown that has `remainingMs` left. */
    static long delay(Context c, long remainingMs) {
        long step = step(c);
        long d = remainingMs % step;
        return d == 0 ? step : d;
    }
}
