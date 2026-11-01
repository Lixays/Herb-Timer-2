package com.example.herbtimer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.widget.RemoteViews;

import java.util.Date;
import java.util.Locale;

/**
 * Draws both widgets. The countdown is a native Chronometer, so it ticks by itself with no wake-ups.
 * An alarm only fires when the plant needs a new picture (every 20 min) or when the final timer ends.
 */
final class HerbWidgetRenderer {
    private static final long STAGE_MS = 20L * 60 * 1000;   // same as the app
    private static final long GROW_MS = 60L * 60 * 1000;

    private static final int[] PLANT_BY_STAGE = {
            R.drawable.herb_plant_0, R.drawable.herb_plant_1, R.drawable.herb_plant_2, R.drawable.herb_plant_3
    };

    private HerbWidgetRenderer() {}

    static void refreshAll(Context context) {
        Context app = context.getApplicationContext();
        AppWidgetManager mgr = AppWidgetManager.getInstance(app);
        long now = System.currentTimeMillis();
        long finish = HerbWidgetStore.finalFinish(app);
        int active = HerbWidgetStore.activeCount(app);

        int widgets = 0;
        widgets += update(app, mgr, HerbWidgetProvider1x1.class, false, now, finish, active);
        widgets += update(app, mgr, HerbWidgetProvider2x1.class, true, now, finish, active);

        if (widgets > 0) scheduleNext(app, now, finish); else cancelAlarm(app);
    }

    private static int update(Context app, AppWidgetManager mgr, Class<?> provider, boolean wide,
                              long now, long finish, int active) {
        int[] ids = mgr.getAppWidgetIds(new ComponentName(app, provider));
        if (ids == null || ids.length == 0) return 0;
        RemoteViews views = build(app, wide, now, finish, active);
        mgr.updateAppWidget(ids, views);
        return ids.length;
    }

    private static RemoteViews build(Context app, boolean wide, long now, long finish, int active) {
        RemoteViews v = new RemoteViews(app.getPackageName(),
                wide ? R.layout.widget_herb_2x1 : R.layout.widget_herb_1x1);

        boolean empty = finish <= 0;
        boolean ready = !empty && finish <= now;
        boolean running = !empty && !ready;

        // Which plant picture: soil only / seed / 3 growth stages / ready to harvest
        int plant;
        if (empty) {
            plant = R.drawable.herb_plant_empty;
        } else if (ready) {
            plant = R.drawable.herb_plant_4;
        } else {
            long elapsed = now - (finish - GROW_MS);
            int stage = elapsed < 0 ? 0 : (int) Math.min(3, elapsed / STAGE_MS + 1);   // same stages as the app
            plant = PLANT_BY_STAGE[stage];
        }
        v.setImageViewResource(R.id.herb_plant, plant);

        if (running && Build.VERSION.SDK_INT >= 24) {
            long base = SystemClock.elapsedRealtime() + (finish - now);
            v.setChronometerCountDown(R.id.herb_countdown, true);
            v.setChronometer(R.id.herb_countdown, base, null, true);
            v.setViewVisibility(R.id.herb_countdown, android.view.View.VISIBLE);
            v.setViewVisibility(R.id.herb_status, android.view.View.GONE);
        } else if (running) {   // very old Android: no counting-down chronometer, show whole minutes
            v.setViewVisibility(R.id.herb_countdown, android.view.View.GONE);
            v.setViewVisibility(R.id.herb_status, android.view.View.VISIBLE);
            v.setTextViewText(R.id.herb_status, ((finish - now) / 60000 + 1) + " min");
            v.setTextColor(R.id.herb_status, 0xFFE8D9A8);
        } else {
            v.setChronometer(R.id.herb_countdown, SystemClock.elapsedRealtime(), null, false);
            v.setViewVisibility(R.id.herb_countdown, android.view.View.GONE);
            v.setViewVisibility(R.id.herb_status, android.view.View.VISIBLE);
            v.setTextViewText(R.id.herb_status, ready ? "Ready!" : "Empty");
            v.setTextColor(R.id.herb_status, ready ? 0xFFE8D83A : 0xFFC25A5A);
        }

        if (wide) {
            v.setTextViewText(R.id.herb_caption, "HERB PATCHES");
            String sub;
            if (running) {
                String time = DateFormat.getTimeFormat(app).format(new Date(finish));
                sub = String.format(Locale.getDefault(), "Done %s \u00B7 %d growing", time, active);
            } else if (ready) {
                sub = "Tap to harvest";
            } else {
                sub = "Tap to plant";
            }
            v.setTextViewText(R.id.herb_sub, sub);
        }

        v.setContentDescription(R.id.herb_root, running ? "Herb timer running" : ready ? "Herbs ready" : "No herbs planted");

        // Tapping the widget opens the app
        Intent launch = app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
        if (launch != null) {
            PendingIntent open = PendingIntent.getActivity(app, 0, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(R.id.herb_root, open);
        }
        return v;
    }

    // ---------- alarm: redraw when the plant grows or the final timer finishes ----------

    private static PendingIntent refreshIntent(Context c) {
        Intent i = new Intent(c, HerbWidgetProvider1x1.class).setAction(HerbWidgetProvider.ACTION_REFRESH);
        return PendingIntent.getBroadcast(c, 4711, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void cancelAlarm(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(refreshIntent(c));
    }

    private static void scheduleNext(Context c, long now, long finish) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = refreshIntent(c);
        am.cancel(pi);
        if (finish <= now) return;   // nothing left to wait for

        long next = finish;
        long start = finish - GROW_MS;
        for (int k = 0; k <= 3; k++) {
            long boundary = start + k * STAGE_MS;
            if (boundary > now && boundary < next) next = boundary;
        }
        long at = next + 300;   // just after the boundary

        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }
}
