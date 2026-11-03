package com.example.herbtimer;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Switches the launcher icon to match the app theme. Each theme has an activity-alias in the manifest
 * (IconOldschool, IconIron, ...); exactly one of them is enabled at a time.
 */
final class IconSwitcher {
    static final String[] THEMES = { "oldschool", "iron", "uim", "hcim", "gim" };

    private IconSwitcher() {}

    private static ComponentName alias(Context c, String id) {
        return new ComponentName(c.getPackageName(), "com.example.herbtimer.Icon" + Character.toUpperCase(id.charAt(0)) + id.substring(1));
    }

    /** follow = false puts the original Old School icon back. */
    static void apply(Context ctx, String theme, boolean follow) {
        Context c = ctx.getApplicationContext();
        String target = "oldschool";
        if (follow) {
            for (String t : THEMES) if (t.equals(theme)) target = t;
        }
        if (target.equals(HerbWidgetStore.iconApplied(c))) return;   // already showing it: don't disturb the launcher
        PackageManager pm = c.getPackageManager();
        try {
            // turn the new one on first so there is always a launcher entry
            pm.setComponentEnabledSetting(alias(c, target), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            for (String t : THEMES) {
                if (!t.equals(target)) {
                    pm.setComponentEnabledSetting(alias(c, t), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                }
            }
            HerbWidgetStore.setIconApplied(c, target);
        } catch (Exception ignored) { }
    }
}
