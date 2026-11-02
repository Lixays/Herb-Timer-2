package com.example.herbtimer;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** "Herbs ready" notifications, the status-bar icon and their buttons. Works with the app closed. */
final class HerbNotifier {
    static final String CH_READY = "herb-timers";
    static final String CH_STATUS = "herb-status";
    static final int STATUS_ID = 1000;
    static final int SNOOZE_ID = 20;

    private HerbNotifier() {}

    /** Call after any change to the timers. */
    static void sync(Context ctx) {
        Context c = ctx.getApplicationContext();
        ensureChannels(c);
        rescheduleAlarms(c);
        updateStatus(c);
        HerbWidgetRenderer.refreshAll(c);
    }

    // ---------- channels ----------
    private static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ready = new NotificationChannel(CH_READY, "Herb timers", NotificationManager.IMPORTANCE_HIGH);
        ready.setDescription("Alerts when herb patches are ready");
        ready.enableVibration(true);
        nm.createNotificationChannel(ready);
        NotificationChannel status = new NotificationChannel(CH_STATUS, "Timer status icon", NotificationManager.IMPORTANCE_LOW);
        status.setDescription("Shows an icon while herb timers are growing or ready");
        nm.createNotificationChannel(status);
    }

    // ---------- alarms: one per distinct finish time ----------
    private static PendingIntent readyIntent(Context c, long t) {
        Intent i = new Intent(c, HerbReceiver.class)
                .setAction(HerbReceiver.ACTION_READY)
                .setData(Uri.parse("herb://ready/" + t));
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent snoozeFireIntent(Context c) {
        Intent i = new Intent(c, HerbReceiver.class)
                .setAction(HerbReceiver.ACTION_SNOOZE_FIRE)
                .setData(Uri.parse("herb://snooze"));
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void setAlarm(Context c, long at, PendingIntent pi) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
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

    private static void rescheduleAlarms(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        // forget the alarms we set last time
        String old = HerbWidgetStore.alarmList(c);
        if (!old.isEmpty()) {
            for (String s : old.split(",")) {
                try { am.cancel(readyIntent(c, Long.parseLong(s))); } catch (NumberFormatException ignored) { }
            }
        }

        long now = System.currentTimeMillis();
        TreeSet<Long> times = new TreeSet<>();
        JSONArray arr = HerbWidgetStore.timers(c);
        for (int i = 0; i < arr.length(); i++) {
            long f = arr.optJSONObject(i) == null ? 0 : arr.optJSONObject(i).optLong("finish", 0);
            if (f > now) times.add(f);
        }
        StringBuilder sb = new StringBuilder();
        for (long t : times) {
            setAlarm(c, t + 250, readyIntent(c, t));
            if (sb.length() > 0) sb.append(',');
            sb.append(t);
        }
        HerbWidgetStore.setAlarmList(c, sb.toString());
    }

    // ---------- "ready" notifications ----------
    /** Alarm fired for finish time t: tell the user which patches are ready. */
    static void postReady(Context c, long t) {
        JSONArray arr = HerbWidgetStore.timers(c);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && o.optLong("finish", 0) == t) names.add(o.optString("name", "Patch"));
        }
        if (names.isEmpty()) return;   // changed since, nothing to say
        String who = HerbCore.label(names, arr.length());
        String restartDone = HerbCore.clock(HerbCore.firstTick(c, t) + HerbCore.GROW_MS);
        post(c, 100 + (int) ((t / 60000L) % 1000000L), who, restartDone);
    }

    private static void post(Context c, int id, String who, String restartDone) {
        ensureChannels(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH_READY)
                .setSmallIcon(R.drawable.ic_stat_herb)
                .setContentTitle("Herbs ready to harvest!")
                .setContentText(who + " \u00B7 restart to be done at " + restartDone)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        who + " ready to harvest.\nRestart now and they'll next be done at " + restartDone + "."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(openApp(c))
                .addAction(0, "Restart all", actionIntent(c, HerbReceiver.ACTION_RESTART, 1))
                .addAction(0, "Snooze 10 min", actionIntent(c, HerbReceiver.ACTION_SNOOZE, 2));
        nm.notify(id, b.build());
    }

    // ---------- snooze ----------
    /** Snooze button: hide the alert and bring it back in 10 minutes (if anything is still ready). */
    static void snooze(Context c) {
        dismissAlerts(c);
        if (readyNames(c).isEmpty()) return;
        setAlarm(c, System.currentTimeMillis() + HerbCore.SNOOZE_MS, snoozeFireIntent(c));
    }

    static void postSnoozed(Context c) {
        List<String> ready = readyNames(c);
        if (ready.isEmpty()) return;
        long now = System.currentTimeMillis();
        String who = HerbCore.label(ready, HerbWidgetStore.timers(c).length());
        post(c, SNOOZE_ID, who, HerbCore.clock(HerbCore.firstTick(c, now) + HerbCore.GROW_MS));
    }

    private static List<String> readyNames(Context c) {
        long now = System.currentTimeMillis();
        JSONArray arr = HerbWidgetStore.timers(c);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            long f = o.optLong("finish", 0);
            if (f > 0 && f <= now) out.add(o.optString("name", "Patch"));
        }
        return out;
    }

    /** Removes the "ready" alerts but keeps the status-bar icon. */
    static void dismissAlerts(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || Build.VERSION.SDK_INT < 23) return;
        for (StatusBarNotification sbn : nm.getActiveNotifications()) {
            // ids from 2,000,000 up belong to the app's other timers (birdhouses etc.): leave those alone
            if (sbn.getId() != STATUS_ID && sbn.getId() < 2000000) nm.cancel(sbn.getId());
        }
    }

    // ---------- status-bar icon ----------
    private static void updateStatus(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        long now = System.currentTimeMillis();
        JSONArray arr = HerbWidgetStore.timers(c);
        int running = 0, ready = 0;
        long next = 0;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            long f = o.optLong("finish", 0);
            if (f <= 0) continue;
            if (f > now) {
                running++;
                if (next == 0 || f < next) next = f;
            } else {
                ready++;
            }
        }
        if (ready == 0) dismissAlerts(c);   // nothing waiting to be harvested any more

        if (running == 0 && ready == 0) {
            nm.cancel(STATUS_ID);
            return;
        }
        String title = running > 0 ? "Herb timers running" : "Herbs ready to harvest!";
        String body = running > 0
                ? running + " growing \u00B7 next ready " + HerbCore.clock(next) + (ready > 0 ? " \u00B7 " + ready + " ready" : "")
                : ready + (ready == 1 ? " patch is" : " patches are") + " ready";
        Notification n = new NotificationCompat.Builder(c, CH_STATUS)
                .setSmallIcon(R.drawable.ic_stat_herb)
                .setContentTitle(title)
                .setContentText(body)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(openApp(c))
                .build();
        nm.notify(STATUS_ID, n);
    }

    // ---------- helpers ----------
    private static PendingIntent openApp(Context c) {
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (launch == null) launch = new Intent();
        return PendingIntent.getActivity(c, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent actionIntent(Context c, String action, int requestCode) {
        Intent i = new Intent(c, HerbReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(c, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
