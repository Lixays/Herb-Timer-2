package com.example.herbtimer;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import java.util.Calendar;

import androidx.core.app.NotificationCompat;

/**
 * A single fixed-length countdown with its own alert and 1x1 widget (limpwurt roots, birdhouses).
 * Everything here works with the app closed.
 */
final class QuickTimer {
    static final String CHANNEL = "extra-timers";   // same channel the app creates for its other timers
    static final String ONGOING_CHANNEL = "deathpile-active";   // quiet channel for the always-on notification

    final String key;            // "limp" / "bird" / "death"
    final long defaultMs;
    final int nid;               // notification id (2,000,000 and up are left alone by the herb alerts)
    final String notifTitle, notifText, restartLabel;
    final String runningTop, readyTop, idleTop;   // top line of the widget
    final int imageRes;
    // optional extras (see the setters below)
    long alertBeforeMs = 0;                        // alert this long BEFORE the countdown ends
    int tickEveryMin = 0;                          // growth ticks every N minutes (flowers: 5); 0 = plain countdown
    long growMs = 0;                               // time from the first tick to ready (flowers: 3 ticks = 15 min)
    int warnImageRes = 0;                          // picture shown while the warning applies
    String readyBottom = "Ready!";                 // bottom line once the countdown is over
    String idleBottom = "Start";                   // bottom line when nothing is running
    String ongoingTitle = null;                    // silent "still running" notification (null = none)
    String ongoingPrefix = "";
    int ongoingNid = 0;
    int smallIconRes = R.drawable.ic_stat_herb;    // status-bar icon of the alert
    final Class<?> provider;
    final String readyAction, restartAction, refreshAction, tickAction;

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
        this.tickAction = "com.example.herbtimer." + up + "_TICK";
    }

    static final QuickTimer LIMP = new QuickTimer("limp", 20L * 60 * 1000, 2900000,
            "Limpwurt roots ready!", "Time to harvest and replant your limpwurt.", "Replant",
            "Limpwurt", "Harvest", "Plant", R.drawable.limp_grow_4, LimpWidgetProvider1x1.class)
            .tickTimes(5, 15L * 60 * 1000)
            .idleBottom("Empty");

    static final QuickTimer BIRD = new QuickTimer("bird", 50L * 60 * 1000, 2000000,
            "Birdhouses ready!", "Time to collect and reset your birdhouses.", "Restart timer",
            "Birdhouses", "Collect", "Place", R.drawable.bird_empty, BirdWidgetProvider1x1.class);

    /** UIM deathpile: 60 minutes, warns 15 minutes before it expires. The widget turns red for those last 15 minutes. */
    static final QuickTimer DEATH = new QuickTimer("death", 60L * 60 * 1000, 2910000,
            "DEATHPILE EXPIRES SOON", "Your deathpile has 15 minutes left.", null,
            "Deathpile", "Deathpile", "Deathpile", R.drawable.skull_white, DeathWidgetProvider1x1.class)
            .alertBefore(15L * 60 * 1000)
            .warnImage(R.drawable.skull_red)
            .readyBottom("Expired")
            .smallIcon(R.drawable.ic_stat_deathpile)
            .ongoing("Deathpile ACTIVE", "Despawn in ", 2920000);

    static final QuickTimer[] ALL = { DEATH, LIMP, BIRD };

    private QuickTimer tickTimes(int everyMin, long growMs) { this.tickEveryMin = everyMin; this.growMs = growMs; return this; }
    private QuickTimer idleBottom(String s) { this.idleBottom = s; return this; }
    private QuickTimer ongoing(String title, String prefix, int nid) { this.ongoingTitle = title; this.ongoingPrefix = prefix; this.ongoingNid = nid; return this; }
    private QuickTimer alertBefore(long ms) { this.alertBeforeMs = ms; return this; }
    private QuickTimer warnImage(int res) { this.warnImageRes = res; return this; }
    private QuickTimer readyBottom(String s) { this.readyBottom = s; return this; }
    private QuickTimer smallIcon(int res) { this.smallIconRes = res; return this; }

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
        updateOngoing(c);
    }

    static void syncAll(Context c) {
        for (QuickTimer q : ALL) q.sync(c);
    }

    /**
     * When a countdown started now ends. Flowers grow on a 5-minute tick lined up with the herb tick
     * (herb :09 -> flowers :04, :09, :14 ...): wait for the next tick, then three more ticks.
     */
    long startFinish(Context c, long now) {
        if (tickEveryMin <= 0) return now + ms(c);
        int every = tickEveryMin;
        int offset = HerbWidgetStore.windows(c)[0] % every;
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        int minute = cal.get(Calendar.MINUTE);
        int step = ((offset - (minute % every)) + every) % every;
        if (step == 0) step = every;
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.MINUTE, step);
        return cal.getTimeInMillis() + growMs;
    }

    /** Which picture the widget shows. */
    int imageFor(long now, long finish) {
        boolean idle = finish <= 0;
        boolean ready = !idle && finish <= now;
        switch (key) {
            case "limp": {
                if (idle) return R.drawable.herb_plant_empty;          // bare soil, like the herb widget
                int[] stages = { R.drawable.limp_grow_0, R.drawable.limp_grow_1, R.drawable.limp_grow_2,
                                 R.drawable.limp_grow_3, R.drawable.limp_grow_4 };
                if (ready) return stages[4];
                long elapsed = now - (finish - growMs);                 // time since the first tick
                int stage = elapsed < 0 ? 0 : (int) Math.min(3, elapsed / (tickEveryMin * 60000L) + 1);
                return stages[stage];
            }
            case "bird":
                return idle ? R.drawable.bird_empty : ready ? R.drawable.bird_done : R.drawable.bird_running;
            default: {                                                  // deathpile
                if (idle) return R.drawable.deathpile_bones;            // nothing running: a pile of bones
                boolean warn = ready || (!idle && finish - now <= alertBeforeMs);
                return warn ? R.drawable.skull_red : R.drawable.skull_white;
            }
        }
    }

    /** Widget tap / notification button: start the countdown again from now. */
    void restart(Context ctx) {
        Context c = ctx.getApplicationContext();
        long ms = ms(c);
        HerbWidgetStore.setQuick(c, key, startFinish(c, System.currentTimeMillis()), ms, defaultMs);
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
            if (q.tickAction.equals(action)) {
                q.updateOngoing(c);
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

    private PendingIntent tickIntent(Context c) {
        return broadcast(c, tickAction, "tick", PendingIntent.FLAG_UPDATE_CURRENT);
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
        long alertAt = finish - alertBeforeMs;
        if (finish > 0 && alertAt > System.currentTimeMillis()) HerbNotifier.setAlarm(c, alertAt + 250, pi);
    }

    /** The alarm fired: tell the user the timer is done. */
    void postReady(Context ctx) {
        Context c = ctx.getApplicationContext();
        long finish = finish(c);
        long now = System.currentTimeMillis();
        if (finish <= 0 || finish - alertBeforeMs > now + 2000) return;   // restarted or cancelled since
        if (alertBeforeMs > 0 && finish <= now) return;                    // too late to warn
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Other timers", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Alerts for deathpile, limpwurt, birdhouses, trees and custom timers");
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
        }
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (launch == null) launch = new Intent();
        PendingIntent open = PendingIntent.getActivity(c, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(smallIconRes)
                .setContentTitle(notifTitle)
                .setContentText(notifText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(open);
        if (restartLabel != null) b.addAction(0, restartLabel, restartIntent(c));
        nm.notify(nid, b.build());
    }

    /** "Despawn in 42m": the text of the always-on deathpile notification. */
    static String ongoingText(String prefix, long remainingMs) {
        return prefix + HerbWidgetRenderer.minutesLeft(remainingMs);
    }

    /**
     * Silent notification that stays while the countdown runs and counts down in whole minutes.
     * Removed again when the timer is cleared or has run out.
     */
    void updateOngoing(Context ctx) {
        if (ongoingTitle == null) return;
        Context c = ctx.getApplicationContext();
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (nm == null) return;
        PendingIntent tick = tickIntent(c);
        long now = System.currentTimeMillis();
        long finish = finish(c);
        if (finish <= now) {                       // nothing running (or it despawned)
            nm.cancel(ongoingNid);
            if (am != null) am.cancel(tick);
            return;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(ONGOING_CHANNEL, "Deathpile timer", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Silent notification while a deathpile countdown is running");
            nm.createNotificationChannel(ch);
        }
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (launch == null) launch = new Intent();
        PendingIntent open = PendingIntent.getActivity(c, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, ONGOING_CHANNEL)
                .setSmallIcon(smallIconRes)
                .setContentTitle(ongoingTitle)
                .setContentText(ongoingText(ongoingPrefix, finish - now))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(open);
        nm.notify(ongoingNid, b.build());
        if (am != null) {                          // refresh the text when the minute number changes
            long delay = (finish - now) % 60000L;
            if (delay == 0) delay = 60000L;
            HerbNotifier.setAlarm(c, now + delay + 300, tick);
        }
    }

    void dismissAlert(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(nid);
    }
}
