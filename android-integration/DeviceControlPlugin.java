package ai.myraa.mobile.plugins;

import android.content.Intent;
import android.provider.Settings;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "DeviceControl")
public class DeviceControlPlugin extends Plugin {

    @PluginMethod
    public void isEnabled(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("enabled", DeviceControlAccessibilityService.instance != null);
        call.resolve(ret);
    }

    /** Opens the system screen where the user manually flips MYRAA's
     *  accessibility toggle on. This step cannot be done in code. */
    @PluginMethod
    public void openSettings(PluginCall call) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        getActivity().startActivity(intent);
        call.resolve();
    }

    @PluginMethod
    public void tap(PluginCall call) {
        DeviceControlAccessibilityService service = DeviceControlAccessibilityService.instance;
        if (service == null) {
            call.reject("Accessibility service not enabled. Call openSettings() first.");
            return;
        }
        Float x = call.getFloat("x");
        Float y = call.getFloat("y");
        if (x == null || y == null) {
            call.reject("x and y required");
            return;
        }
        service.tap(x, y, success -> {
            JSObject ret = new JSObject();
            ret.put("success", success);
            call.resolve(ret);
        });
    }

    @PluginMethod
    public void swipe(PluginCall call) {
        DeviceControlAccessibilityService service = DeviceControlAccessibilityService.instance;
        if (service == null) {
            call.reject("Accessibility service not enabled. Call openSettings() first.");
            return;
        }
        Float x1 = call.getFloat("x1");
        Float y1 = call.getFloat("y1");
        Float x2 = call.getFloat("x2");
        Float y2 = call.getFloat("y2");
        Integer duration = call.getInt("durationMs");
        if (x1 == null || y1 == null || x2 == null || y2 == null) {
            call.reject("x1, y1, x2, y2 required");
            return;
        }
        long durationMs = duration != null ? duration : 300;
        service.swipe(x1, y1, x2, y2, durationMs, success -> {
            JSObject ret = new JSObject();
            ret.put("success", success);
            call.resolve(ret);
        });
    }

    @PluginMethod
    public void typeText(PluginCall call) {
        DeviceControlAccessibilityService service = DeviceControlAccessibilityService.instance;
        if (service == null) {
            call.reject("Accessibility service not enabled. Call openSettings() first.");
            return;
        }
        String text = call.getString("text");
        if (text == null) {
            call.reject("text required");
            return;
        }
        JSObject ret = new JSObject();
        ret.put("success", service.typeText(text));
        call.resolve(ret);
    }
}
