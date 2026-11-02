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
            R.drawable.herb_grow_0, R.drawable.herb_grow_1, R.drawable.herb_grow_2, R.drawable.herb_grow_3
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
        int wideWidgets = update(app, mgr, HerbWidgetProvider2x1.class, true, now, finish, active);
        widgets += wideWidgets;

        if (widgets > 0) scheduleNext(app, now, finish, true); else cancelAlarm(app);
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
            plant = R.drawable.herb_grow_4;
        } else {
            long elapsed = now - (finish - GROW_MS);
            int stage = elapsed < 0 ? 0 : (int) Math.min(3, elapsed / STAGE_MS + 1);   // same stages as the app
            plant = PLANT_BY_STAGE[stage];
        }
        v.setImageViewResource(R.id.herb_plant, plant);

        v.setViewVisibility(R.id.herb_countdown, android.view.View.GONE);   // no ticking seconds any more
        v.setViewVisibility(R.id.herb_status, android.view.View.VISIBLE);
        if (running) {
            v.setTextViewText(R.id.herb_status, minutesLeft(finish - now));   // e.g. "1h 2m", redrawn once a minute
            v.setTextColor(R.id.herb_status, 0xFFE8D9A8);
        } else {
            v.setTextViewText(R.id.herb_status, ready ? "Ready!" : "Empty");
            v.setTextColor(R.id.herb_status, ready ? 0xFFE8D83A : 0xFFC25A5A);
        }

        // Top line (both sizes): the patch name, "Growing", "Harvest" or "Plant"
        int growing = 0, done = 0;
        String lastName = "";
        org.json.JSONArray arr = HerbWidgetStore.timers(app);
        for (int i = 0; i < arr.length(); i++) {
            org.json.JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            long f = o.optLong("finish", 0);
            if (f <= 0) continue;
            if (f > now) { growing++; lastName = o.optString("name", ""); } else { done++; }
        }
        String caption;
        if (done > 0) caption = "Harvest";
        else if (growing >= 2) caption = "Growing";
        else if (growing == 1) caption = lastName;
        else caption = "Plant";
        int captionColor = done > 0 ? 0xFFE8D83A : 0xFFC9A227;
        int captionId = wide ? R.id.herb_caption : R.id.herb_finish;
        v.setTextViewText(captionId, caption);
        v.setTextColor(captionId, captionColor);
        v.setViewVisibility(captionId, android.view.View.VISIBLE);

        if (wide) {
            // Bottom line: only the finish time, and only while something is growing
            if (running) {
                String time = DateFormat.getTimeFormat(app).format(new Date(finish));
                v.setTextViewText(R.id.herb_sub, "Done " + time);
                v.setViewVisibility(R.id.herb_sub, android.view.View.VISIBLE);
            } else {
                v.setViewVisibility(R.id.herb_sub, android.view.View.GONE);
            }
        }

        v.setContentDescription(R.id.herb_root, running ? "Herb timer running" : ready ? "Herbs ready" : "No herbs planted");
        v.setContentDescription(R.id.herb_plant, wide ? "Open Herb Timer" : "Plant all");

        // Two tap zones: the plant picture and everything else (the widget background / text).
        //   1x1: plant = plant/restart everything, rest = open the app
        //   2x1: plant = open the app,            rest = plant/restart everything
        PendingIntent open = null;
        Intent launch = app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
        if (launch != null) {
            open = PendingIntent.getActivity(app, 0, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        PendingIntent plantAllTap = open;   // until the app has sent its timers, just open it
        if (HerbWidgetStore.hasTimers(app)) {
            Intent plantAll = new Intent(app, HerbReceiver.class).setAction(HerbReceiver.ACTION_PLANT_ALL);
            plantAllTap = PendingIntent.getBroadcast(app, 5, plantAll,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        PendingIntent onPlant = wide ? open : plantAllTap;
        PendingIntent onRest = wide ? plantAllTap : open;
        if (onRest != null) v.setOnClickPendingIntent(R.id.herb_root, onRest);
        if (onPlant != null) v.setOnClickPendingIntent(R.id.herb_plant, onPlant);
        return v;
    }

    /** "42m" or "1h 2m" - rounded up, so it reaches 0 exactly when the timer ends. */
    private static String minutesLeft(long ms) {
        long mins = Math.max(1, (ms + 59999) / 60000);
        long h = mins / 60;
        long m = mins % 60;
        if (h == 0) return m + "m";
        return m == 0 ? h + "h" : h + "h " + m + "m";
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

    private static void scheduleNext(Context c, long now, long finish, boolean minuteTicks) {
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
        if (minuteTicks) {   // the 2x1 shows whole minutes, so redraw when the minute number changes
            long delay = (finish - now) % 60000L;
            if (delay == 0) delay = 60000L;
            if (now + delay < next) next = now + delay;
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
