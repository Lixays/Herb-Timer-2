package com.example.herbtimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Handles alarms and the notification / widget buttons. Runs even when the app is closed. */
public class HerbReceiver extends BroadcastReceiver {
    static final String ACTION_READY = "com.example.herbtimer.HERB_READY";
    static final String ACTION_RESTART = "com.example.herbtimer.HERB_RESTART";
    static final String ACTION_SNOOZE = "com.example.herbtimer.HERB_SNOOZE";
    static final String ACTION_SNOOZE_FIRE = "com.example.herbtimer.HERB_SNOOZE_FIRE";
    static final String ACTION_PLANT_ALL = "com.example.herbtimer.HERB_PLANT_ALL";
    static final String ACTION_BIRD_READY = "com.example.herbtimer.BIRD_READY";
    static final String ACTION_BIRD_RESTART = "com.example.herbtimer.BIRD_RESTART";
    static final String ACTION_BIRD_REFRESH = "com.example.herbtimer.BIRD_REFRESH";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        Context c = context.getApplicationContext();
        switch (intent.getAction()) {
            case ACTION_READY: {
                long t = 0;
                if (intent.getData() != null) {
                    try { t = Long.parseLong(intent.getData().getLastPathSegment()); } catch (Exception ignored) { }
                }
                if (t > 0) HerbNotifier.postReady(c, t);
                HerbNotifier.sync(c);   // refresh the status icon and the widgets
                break;
            }
            case ACTION_RESTART:      // notification: "Restart all" = replant everything that is ready
                HerbCore.replant(c, false);
                HerbNotifier.sync(c);
                break;
            case ACTION_PLANT_ALL:    // widget: plant empty patches and restart ready ones
                HerbCore.replant(c, true);
                HerbNotifier.sync(c);
                break;
            case ACTION_SNOOZE:
                HerbNotifier.snooze(c);
                break;
            case ACTION_SNOOZE_FIRE:
                HerbNotifier.postSnoozed(c);
                break;
            case ACTION_BIRD_READY:       // the birdhouse countdown ended
                HerbBird.postReady(c);
                HerbBird.sync(c);
                break;
            case ACTION_BIRD_RESTART:     // widget tap / notification button: start the countdown again
                HerbBird.restart(c);
                break;
            case ACTION_BIRD_REFRESH:     // once a minute while counting down
                BirdWidgetRenderer.refreshAll(c);
                break;
            default:
                break;
        }
    }
}
