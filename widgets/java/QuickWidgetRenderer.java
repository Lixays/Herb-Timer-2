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
        WidgetTheme th = WidgetTheme.current(app);   // follows the app's theme
        v.setInt(R.id.quick_root, "setBackgroundResource", th.bgRes);

        // Warning state (deathpile): the last stretch of the countdown, and after it has expired
        boolean warn = qt.warnImageRes != 0 && (ready || (running && finish - now <= qt.alertBeforeMs));
        v.setImageViewResource(R.id.quick_image, qt.imageFor(now, finish));

        // Top line (like the herb widget): what to do next
        v.setTextViewText(R.id.quick_title, running ? qt.runningTop : ready ? qt.readyTop : qt.idleTop);
        v.setTextColor(R.id.quick_title, warn ? th.idle : ready ? th.ready : th.accent);

        // Bottom line: minutes left, the "over" text or "Start"
        v.setTextViewText(R.id.quick_status, running ? HerbWidgetRenderer.minutesLeft(finish - now) : ready ? qt.readyBottom : qt.idleBottom);
        v.setTextColor(R.id.quick_status, warn ? th.idle : running ? th.text : ready ? th.ready : th.idle);

        v.setContentDescription(R.id.quick_root, running ? qt.runningTop + " timer running" : ready ? qt.runningTop + (qt.warnImageRes != 0 ? " expired" : " ready") : qt.runningTop + " timer not started");
        v.setContentDescription(R.id.quick_image, (running ? "Cancel " : "Start ") + qt.runningTop + " timer");

        // Tap the picture: cancel while running, otherwise start / restart. Tap the rest: open the app.
        Intent launch = app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
        if (launch != null) {
            v.setOnClickPendingIntent(R.id.quick_root, PendingIntent.getActivity(app, 0, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        v.setOnClickPendingIntent(R.id.quick_image, qt.toggleIntent(app));

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
