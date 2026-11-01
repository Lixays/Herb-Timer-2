package com.example.herbtimer;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Bridge the web app already calls as "HerbWidget": it hands the timers over so the widgets can show them. */
@CapacitorPlugin(name = "HerbWidget")
public class HerbWidgetPlugin extends Plugin {

    @PluginMethod
    public void update(PluginCall call) {
        HerbWidgetStore.save(getContext(), call.getString("timers", "[]"), call.getString("windows", "[]"));
        HerbWidgetRenderer.refreshAll(getContext());
        call.resolve();
    }

    @PluginMethod
    public void getState(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("dirty", false);   // the widgets are display-only, so nothing ever changes behind the app's back
        ret.put("timers", HerbWidgetStore.timersJson(getContext()));
        call.resolve(ret);
    }

    @PluginMethod
    public void clearDirty(PluginCall call) {
        call.resolve();
    }
}
