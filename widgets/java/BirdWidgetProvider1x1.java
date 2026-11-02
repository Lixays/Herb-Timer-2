package com.example.herbtimer;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/** The 1x1 Birdhouse widget. */
public class BirdWidgetProvider1x1 extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        HerbBird.sync(context);
    }

    @Override
    public void onDisabled(Context context) {
        BirdWidgetRenderer.refreshAll(context);   // last widget removed: stops the minute alarm
    }
}
