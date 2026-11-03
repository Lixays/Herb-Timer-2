package com.example.herbtimer;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

/**
 * A single fixed-length countdown with its own alert and 1x1 widget (limpwurt roots, birdhouses).
 * Everything here works with the app closed.
 */
final class QuickTimer {
    static final String CHANNEL = "extra-timers";   // same channel the app creates for its other timers

    final String key;            // "limp" / "bird"
    final long defaultMs;
    final int nid;               // notification id (2,000,000 and up are left alone by the herb alerts)
    final String notifTitle, notifText, restartLabel;
    final String runningTop, readyTop, idleTop;   // top line of the widget
    final int imageRes;
    final Class<?> provider;
    final String readyAction, restartAction, refreshAction;

    private QuickTimer(String key, long defaultMs, int nid, String notifTitle, String notifText, String restartLabel,
                       String runningTop, String readyTop, String idleTop, int imageRes, Class<?> provider) {
        this.key = key;
        this.defaultMs = defaultMs;
        this.nid = nid;
        this.notifTitle = notifTitle;
        this.notifText = notifText;
        this.restartLabel = restartLabel;
        this.runningTop = runningTop;
        this.readyTop = readyTop;
        this.idleTop = idleTop;
        this.imageRes = imageRes;
        this.provider = provider;
        String up = key.toUpperCase();
        this.readyAction = "com.example.herbtimer." + up + "_READY";
        this.restartAction = "com.example.herbtimer." + up + "_RESTART";
        this.refreshAction = "com.example.herbtimer." + up + "_REFRESH";
    }

    static final QuickTimer LIMP = new QuickTimer("limp", 20L * 60 * 1000, 2900000,
            "Limpwurt roots ready!", "Time to harvest and replant your limpwurt.", "Replant",
            "Limpwurt", "Harvest", "Plant", R.drawable.limp_root, LimpWidgetProvider1x1.class);

    static final QuickTimer BIRD = new QuickTimer("bird", 50L * 60 * 1000, 2000000,
            "Birdhouses ready!", "Time to collect and reset your birdhouses.", "Restart timer",
            "Birdhouses", "Collect", "Place", R.drawable.bird_house, BirdWidgetProvider1x1.class);

    static final QuickTimer[] ALL = { LIMP, BIRD };

    static QuickTimer byKey(String key) {
        for (QuickTimer q : ALL) if (q.key.equals(key)) return q;
        return null;
    }

    long finish(Context c) { return HerbWidgetStore.quickFinish(c, key); }

    long ms(Context c) { return HerbWidgetStore.quickMs(c, key, defaultMs); }

    // ---------- state changes ----------

    /** Call after any change: sets up the alert alarm and redraws the widget. */
    void sync(Context ctx) {
        Context c = ctx.getApplicationContext();
        scheduleAlert(c);
        QuickWidgetRenderer.refresh(c, this);
    }

    static void syncAll(Context c) {
        for (QuickTimer q : ALL) q.sync(c);
    }

    /** Widget tap / notification button: start the countdown again from now. */
    void restart(Context ctx) {
        Context c = ctx.getApplicationContext();
        long ms = ms(c);
        HerbWidgetStore.setQuick(c, key, System.currentTimeMillis() + ms, ms, defaultMs);
        HerbWidgetStore.setQuickDirty(c, key, true);   // the app picks this up next time it looks
        dismissAlert(c);
        sync(c);
    }

    /** Handles this timer's broadcasts. Returns true if the action belonged to one of them. */
    static boolean handleAction(Context c, String action) {
        for (QuickTimer q : ALL) {
            if (q.readyAction.equals(action)) {
                q.postReady(c);
                q.sync(c);
                return true;
            }
            if (q.restartAction.equals(action)) {
                q.restart(c);
                return true;
            }
            if (q.refreshAction.equals(action)) {
                QuickWidgetRenderer.refresh(c, q);
                return true;
            }
        }
        return false;
    }

    // ---------- intents ----------

    private PendingIntent broadcast(Context c, String action, String what, int flags) {
        Intent i = new Intent(c, HerbReceiver.class).setAction(action).setData(Uri.parse("herb://quick/" + key + "/" + what));
        return PendingIntent.getBroadcast(c, 0, i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    PendingIntent restartIntent(Context c) {
        return broadcast(c, restartAction, "restart", PendingIntent.FLAG_UPDATE_CURRENT);
    }

    PendingIntent refreshIntent(Context c) {
        return broadcast(c, refreshAction, "refresh", PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent readyIntent(Context c) {
        return broadcast(c, readyAction, "ready", PendingIntent.FLAG_UPDATE_CURRENT);
    }

    // ---------- alert ----------

    private void scheduleAlert(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = readyIntent(c);
        am.cancel(pi);
        long finish = finish(c);
        if (finish > System.currentTimeMillis()) HerbNotifier.setAlarm(c, finish + 250, pi);
    }

    /** The alarm fired: tell the user the timer is done. */
    void postReady(Context ctx) {
        Context c = ctx.getApplicationContext();
        long finish = finish(c);
        if (finish <= 0 || finish > System.currentTimeMillis() + 2000) return;   // restarted or cancelled since
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Other timers", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Alerts for limpwurt, birdhouses, trees and custom timers");
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
        }
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (launch == null) launch = new Intent();
        PendingIntent open = PendingIntent.getActivity(c, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_herb)
                .setContentTitle(notifTitle)
                .setContentText(notifText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(open)
                .addAction(0, restartLabel, restartIntent(c));
        nm.notify(nid, b.build());
    }

    void dismissAlert(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(nid);
    }
}
