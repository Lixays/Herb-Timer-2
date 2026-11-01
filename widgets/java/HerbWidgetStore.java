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
}
