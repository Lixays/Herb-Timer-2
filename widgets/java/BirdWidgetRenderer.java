package com.example.herbtimer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

/** Draws the 1x1 Birdhouse widget. Tapping it restarts the countdown. */
final class BirdWidgetRenderer {
    private BirdWidgetRenderer() {}

    static void refreshAll(Context ctx) {
        Context app = ctx.getApplicationContext();
        AppWidgetManager mgr = AppWidgetManager.getInstance(app);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(app, BirdWidgetProvider1x1.class));
        if (ids == null || ids.length == 0) {
            cancelRefresh(app);
            return;
        }
        long now = System.currentTimeMillis();
        long finish = HerbWidgetStore.birdFinish(app);
        boolean idle = finish <= 0;
        boolean ready = !idle && finish <= now;
        boolean running = !idle && !ready;

        RemoteViews v = new RemoteViews(app.getPackageName(), R.layout.widget_bird_1x1);
        v.setImageViewResource(R.id.bird_image, R.drawable.bird_house);

        // Top line, like the herb widget: what to do next
        v.setTextViewText(R.id.bird_title, running ? "Birdhouses" : ready ? "Collect" : "Place");
        v.setTextColor(R.id.bird_title, ready ? 0xFFE8D83A : 0xFFC9A227);

        // Bottom line: minutes left, "Ready!" or "Start"
        v.setTextViewText(R.id.bird_status, running ? HerbWidgetRenderer.minutesLeft(finish - now) : ready ? "Ready!" : "Start");
        v.setTextColor(R.id.bird_status, running ? 0xFFE8D9A8 : ready ? 0xFFE8D83A : 0xFFC25A5A);

        v.setContentDescription(R.id.bird_root, running ? "Birdhouse timer running. Tap to restart" : ready ? "Birdhouses ready. Tap to restart" : "Tap to start the birdhouse timer");

        // Any tap restarts the countdown (no need to open the app)
        Intent restart = new Intent(app, HerbReceiver.class).setAction(HerbReceiver.ACTION_BIRD_RESTART);
        v.setOnClickPendingIntent(R.id.bird_root, PendingIntent.getBroadcast(app, 6, restart,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        mgr.updateAppWidget(ids, v);
        scheduleRefresh(app, now, finish);
    }

    // ---------- redraw once a minute while counting down ----------
    private static PendingIntent refreshIntent(Context c) {
        Intent i = new Intent(c, HerbReceiver.class)
                .setAction(HerbReceiver.ACTION_BIRD_REFRESH)
                .setData(Uri.parse("herb://bird/refresh"));
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void cancelRefresh(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(refreshIntent(c));
    }

    private static void scheduleRefresh(Context c, long now, long finish) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = refreshIntent(c);
        am.cancel(pi);
        if (finish <= now) return;   // nothing left to count down
        long delay = (finish - now) % 60000L;   // when the minute number changes
        if (delay == 0) delay = 60000L;
        HerbNotifier.setAlarm(c, now + delay + 300, pi);
    }
}
