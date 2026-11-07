package com.example.herbtimer;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** The bridge the web app already calls as "HerbWidget". */
@CapacitorPlugin(name = "HerbWidget")
public class HerbWidgetPlugin extends Plugin {

    /** The app sends its timers. Also (re)schedules the "ready" notifications and redraws the widgets. */
    @PluginMethod
    public void update(PluginCall call) {
        JSObject ret = new JSObject();
        if (HerbWidgetStore.isDirty(getContext())) {
            // A notification/widget button changed things and the app hasn't read that yet:
            // don't let the app's older copy overwrite it. The app will pull the new state shortly.
            ret.put("ignored", true);
            call.resolve(ret);
            return;
        }
        HerbWidgetStore.save(getContext(), call.getString("timers", "[]"), call.getString("windows", "[]"));
        HerbNotifier.sync(getContext());
        call.resolve(ret);
    }

    @PluginMethod
    public void getState(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("dirty", HerbWidgetStore.isDirty(getContext()));
        ret.put("timers", HerbWidgetStore.timersJson(getContext()));
        call.resolve(ret);
    }

    @PluginMethod
    public void clearDirty(PluginCall call) {
        HerbWidgetStore.setDirty(getContext(), false);
        call.resolve();
    }

    // ---------- quick timers: limpwurt roots + birdhouses ----------

    /** The app tells us when a countdown ends. We schedule the alert and redraw that timer's widget. */
    @PluginMethod
    public void setQuick(PluginCall call) {
        QuickTimer qt = QuickTimer.byKey(call.getString("kind", ""));
        if (qt == null) { call.reject("Unknown timer"); return; }
        JSObject ret = new JSObject();
        if (HerbWidgetStore.isQuickDirty(getContext(), qt.key)) {
            ret.put("ignored", true);   // the widget changed it first; the app will pull that shortly
            call.resolve(ret);
            return;
        }
        long finish = call.getData().optLong("finish", 0);
        long ms = call.getData().optLong("ms", qt.defaultMs);
        HerbWidgetStore.setQuick(getContext(), qt.key, finish, ms, qt.defaultMs);
        qt.sync(getContext());
        call.resolve(ret);
    }

    @PluginMethod
    public void getQuick(PluginCall call) {
        QuickTimer qt = QuickTimer.byKey(call.getString("kind", ""));
        if (qt == null) { call.reject("Unknown timer"); return; }
        JSObject ret = new JSObject();
        ret.put("dirty", HerbWidgetStore.isQuickDirty(getContext(), qt.key));
        ret.put("finish", qt.finish(getContext()));
        ret.put("ms", qt.ms(getContext()));
        call.resolve(ret);
    }

    @PluginMethod
    public void clearQuickDirty(PluginCall call) {
        QuickTimer qt = QuickTimer.byKey(call.getString("kind", ""));
        if (qt == null) { call.reject("Unknown timer"); return; }
        HerbWidgetStore.setQuickDirty(getContext(), qt.key, false);
        call.resolve();
    }

    // ---------- theme: widgets + app icon follow the app ----------
    @PluginMethod
    public void setTheme(PluginCall call) {
        String theme = call.getString("theme", "oldschool");
        boolean dark = call.getData().optBoolean("dark", false);
        boolean icon = call.getData().optBoolean("icon", true);
        HerbWidgetStore.setTheme(getContext(), theme, dark);
        HerbWidgetStore.setSaver(getContext(), call.getData().optBoolean("saver", false));
        HerbWidgetRenderer.refreshAll(getContext());   // redraws and re-plans the refresh alarms (battery saver changes how often)
        QuickTimer.syncAll(getContext());
        IconSwitcher.apply(getContext(), theme, icon);
        call.resolve();
    }
}
