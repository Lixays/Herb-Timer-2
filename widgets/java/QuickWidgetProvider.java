package com.example.herbtimer;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/** Shared behaviour of the limpwurt and birdhouse widgets. */
public abstract class QuickWidgetProvider extends AppWidgetProvider {
    abstract QuickTimer timer();

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        timer().sync(context);
    }

    @Override
    public void onDisabled(Context context) {
        QuickWidgetRenderer.refresh(context, timer());   // last widget removed: stops the minute alarm
    }
}
