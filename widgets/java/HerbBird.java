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

/** The birdhouse timer: its alert, its notification button and the one-tap widget restart. Works with the app closed. */
final class HerbBird {
    static final int NID = 2000000;                 // same id the app uses for its fallback alert
    static final String CHANNEL = "extra-timers";   // same channel the app creates for its other timers

    private HerbBird() {}

    /** Call after any change: sets up the alert alarm and redraws the widget. */
    static void sync(Context ctx) {
        Context c = ctx.getApplicationContext();
        scheduleAlert(c);
        BirdWidgetRenderer.refreshAll(c);
    }

    /** Widget tap / notification button: start the countdown again from now. */
    static void restart(Context ctx) {
        Context c = ctx.getApplicationContext();
        long ms = HerbWidgetStore.birdMs(c);
        HerbWidgetStore.setBird(c, System.currentTimeMillis() + ms, ms);
        HerbWidgetStore.setBirdDirty(c, true);   // the app picks this up next time it looks
        dismissAlert(c);
        sync(c);
    }

    private static PendingIntent readyIntent(Context c) {
        Intent i = new Intent(c, HerbReceiver.class)
                .setAction(HerbReceiver.ACTION_BIRD_READY)
                .setData(Uri.parse("herb://bird/ready"));
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void scheduleAlert(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = readyIntent(c);
        am.cancel(pi);
        long finish = HerbWidgetStore.birdFinish(c);
        if (finish > System.currentTimeMillis()) HerbNotifier.setAlarm(c, finish + 250, pi);
    }

    /** The alarm fired: tell the user the birdhouses are ready. */
    static void postReady(Context ctx) {
        Context c = ctx.getApplicationContext();
        long finish = HerbWidgetStore.birdFinish(c);
        if (finish <= 0 || finish > System.currentTimeMillis() + 2000) return;   // restarted or cancelled since
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Other timers", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Alerts for birdhouses, trees and custom timers");
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
        }
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (launch == null) launch = new Intent();
        PendingIntent open = PendingIntent.getActivity(c, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent again = new Intent(c, HerbReceiver.class).setAction(HerbReceiver.ACTION_BIRD_RESTART);
        PendingIntent restart = PendingIntent.getBroadcast(c, 3, again, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_herb)
                .setContentTitle("Birdhouses ready!")
                .setContentText("Time to collect and reset your birdhouses.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(open)
                .addAction(0, "Restart timer", restart);
        nm.notify(NID, b.build());
    }

    static void dismissAlert(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NID);
    }
}
