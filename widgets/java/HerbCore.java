package com.example.herbtimer;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** The app's planting rules, ported from index.html so buttons can work without opening the app. */
final class HerbCore {
    static final long STAGE_MS = 20L * 60 * 1000;
    static final long GROW_MS = 60L * 60 * 1000;
    static final long SNOOZE_MS = 10L * 60 * 1000;

    private HerbCore() {}

    /** Next farming tick after `nowMs` (same as getFirstTick in the app). */
    static long firstTick(Context c, long nowMs) {
        int[] w = HerbWidgetStore.windows(c);
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(nowMs);
        int m = cal.get(Calendar.MINUTE);
        int target = w[0];
        for (int x : w) {
            if (x > m) { target = x; break; }
        }
        if (target <= m) cal.add(Calendar.HOUR_OF_DAY, 1);
        cal.set(Calendar.MINUTE, target);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /**
     * Replants every patch that is ready. With includeEmpty it also plants empty patches.
     * Growing patches are left alone.
     * @return how many patches changed, or -1 if the app hasn't sent its timers yet
     */
    static int replant(Context c, boolean includeEmpty) {
        JSONArray arr = HerbWidgetStore.timers(c);
        if (arr.length() == 0) return -1;
        long now = System.currentTimeMillis();
        long newFinish = firstTick(c, now) + GROW_MS;
        int changed = 0;
        try {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                long f = o.optLong("finish", 0);
                boolean ready = f > 0 && f <= now;
                boolean empty = f <= 0;
                if (ready || (includeEmpty && empty)) {
                    o.put("finish", newFinish);
                    changed++;
                }
            }
        } catch (JSONException e) {
            return -1;
        }
        if (changed > 0) {
            HerbWidgetStore.saveTimers(c, arr.toString());
            HerbWidgetStore.setDirty(c, true);   // tells the app to pick this up next time it looks
        }
        return changed;
    }

    static String clock(long ms) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ms));
    }

    static String label(List<String> names, int total) {
        if (names.size() == total) return "All patches";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(names.get(i));
        }
        return sb.toString();
    }
}
