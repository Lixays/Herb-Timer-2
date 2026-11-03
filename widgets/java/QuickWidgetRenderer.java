package com.example.herbtimer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/**
 * Draws the 1x1 limpwurt and birdhouse widgets. Same behaviour as the herb 1x1:
 * tap the picture = restart the countdown (no app opening), tap anywhere else = open the app.
 */
final class QuickWidgetRenderer {
    private QuickWidgetRenderer() {}

    static void refresh(Context ctx, QuickTimer qt) {
        Context app = ctx.getApplicationContext();
        AppWidgetManager mgr = AppWidgetManager.getInstance(app);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(app, qt.provider));
        if (ids == null || ids.length == 0) {
            cancelRefresh(app, qt);
            return;
        }
        long now = System.currentTimeMillis();
        long finish = qt.finish(app);
        boolean idle = finish <= 0;
        boolean ready = !idle && finish <= now;
        boolean running = !idle && !ready;

        RemoteViews v = new RemoteViews(app.getPackageName(), R.layout.widget_quick_1x1);
        v.setImageViewResource(R.id.quick_image, qt.imageRes);

        // Top line (like the herb widget): what to do next
        v.setTextViewText(R.id.quick_title, running ? qt.runningTop : ready ? qt.readyTop : qt.idleTop);
        v.setTextColor(R.id.quick_title, ready ? 0xFFE8D83A : 0xFFC9A227);

        // Bottom line: minutes left, "Ready!" or "Start"
        v.setTextViewText(R.id.quick_status, running ? HerbWidgetRenderer.minutesLeft(finish - now) : ready ? "Ready!" : "Start");
        v.setTextColor(R.id.quick_status, running ? 0xFFE8D9A8 : ready ? 0xFFE8D83A : 0xFFC25A5A);

        v.setContentDescription(R.id.quick_root, running ? qt.runningTop + " timer running" : ready ? qt.runningTop + " ready" : qt.runningTop + " timer not started");
        v.setContentDescription(R.id.quick_image, "Restart " + qt.runningTop + " timer");

        // Tap the picture: restart. Tap the rest: open the app.
        Intent launch = app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
        if (launch != null) {
            v.setOnClickPendingIntent(R.id.quick_root, PendingIntent.getActivity(app, 0, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        v.setOnClickPendingIntent(R.id.quick_image, qt.restartIntent(app));

        mgr.updateAppWidget(ids, v);
        scheduleRefresh(app, qt, now, finish);
    }

    // ---------- redraw once a minute while counting down ----------
    private static void cancelRefresh(Context c, QuickTimer qt) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(qt.refreshIntent(c));
    }

    private static void scheduleRefresh(Context c, QuickTimer qt, long now, long finish) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = qt.refreshIntent(c);
        am.cancel(pi);
        if (finish <= now) return;   // nothing left to count down
        long delay = (finish - now) % 60000L;   // when the minute number changes
        if (delay == 0) delay = 60000L;
        HerbNotifier.setAlarm(c, now + delay + 300, pi);
    }
}
