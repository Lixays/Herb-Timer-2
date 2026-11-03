package com.example.herbtimer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Keeps the last timers the app sent, so the widgets still work when the app is closed. */
final class HerbWidgetStore {
    private static final String PREFS = "herb_widget";

    private HerbWidgetStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void save(Context c, String timers, String windows) {
        prefs(c).edit().putString("timers", timers).putString("windows", windows).apply();
    }

    static String timersJson(Context c) {
        return prefs(c).getString("timers", "[]");
    }

    /** Finish time (ms) of the patch that finishes LAST, or 0 if nothing is planted. */
    static long finalFinish(Context c) {
        long max = 0;
        try {
            JSONArray arr = new JSONArray(timersJson(c));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                long f = o.optLong("finish", 0);
                if (f > max) max = f;
            }
        } catch (Exception ignored) { }
        return max;
    }

    /** How many patches are planted (running or waiting to be harvested). */
    static int activeCount(Context c) {
        int n = 0;
        try {
            JSONArray arr = new JSONArray(timersJson(c));
            for (int i = 0; i < arr.length(); i++) {
                if (arr.getJSONObject(i).optLong("finish", 0) > 0) n++;
            }
        } catch (Exception ignored) { }
        return n;
    }

    static JSONArray timers(Context c) {
        try { return new JSONArray(timersJson(c)); } catch (Exception e) { return new JSONArray(); }
    }

    static boolean hasTimers(Context c) {
        return timers(c).length() > 0;
    }

    static void saveTimers(Context c, String json) {
        prefs(c).edit().putString("timers", json).apply();
    }

    /** True when the notification/widget changed the timers and the app hasn't picked that up yet. */
    static boolean isDirty(Context c) {
        return prefs(c).getBoolean("dirty", false);
    }

    static void setDirty(Context c, boolean dirty) {
        prefs(c).edit().putBoolean("dirty", dirty).apply();
    }

    /** The three "minutes past the hour" the patches tick at, sorted. */
    static int[] windows(Context c) {
        int[] out = {9, 29, 49};
        try {
            JSONArray a = new JSONArray(prefs(c).getString("windows", "[]"));
            if (a.length() == 3) {
                int[] w = {a.getInt(0), a.getInt(1), a.getInt(2)};
                java.util.Arrays.sort(w);
                out = w;
            }
        } catch (Exception ignored) { }
        return out;
    }

    static String alarmList(Context c) {
        return prefs(c).getString("alarms", "");
    }

    static void setAlarmList(Context c, String s) {
        prefs(c).edit().putString("alarms", s).apply();
    }

    // ---------- quick timers (limpwurt roots, birdhouses) ----------
    // Stored per timer under "<key>_finish", "<key>_ms" and "<key>_dirty".

    static long quickFinish(Context c, String key) {
        return prefs(c).getLong(key + "_finish", 0);
    }

    static long quickMs(Context c, String key, long fallback) {
        long ms = prefs(c).getLong(key + "_ms", fallback);
        return ms > 0 ? ms : fallback;
    }

    static void setQuick(Context c, String key, long finish, long ms, long fallbackMs) {
        prefs(c).edit().putLong(key + "_finish", Math.max(0, finish)).putLong(key + "_ms", ms > 0 ? ms : fallbackMs).apply();
    }

    /** True when the widget / notification button restarted the countdown and the app hasn't read that yet. */
    static boolean isQuickDirty(Context c, String key) {
        return prefs(c).getBoolean(key + "_dirty", false);
    }

    static void setQuickDirty(Context c, String key, boolean dirty) {
        prefs(c).edit().putBoolean(key + "_dirty", dirty).apply();
    }
}
