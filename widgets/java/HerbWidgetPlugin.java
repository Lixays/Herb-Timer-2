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
}
