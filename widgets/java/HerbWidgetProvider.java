package com.example.herbtimer;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

/** Shared behaviour for both widget sizes. */
public abstract class HerbWidgetProvider extends AppWidgetProvider {
    static final String ACTION_REFRESH = "com.example.herbtimer.HERB_WIDGET_REFRESH";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        HerbWidgetRenderer.refreshAll(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            HerbWidgetRenderer.refreshAll(context);
        }
    }

    @Override
    public void onDisabled(Context context) {
        // Last widget of this size removed: refreshAll cancels the alarm if no widgets are left at all.
        HerbWidgetRenderer.refreshAll(context);
    }
}
