package ai.myraa.mobile.plugins;

import android.content.Intent;
import android.graphics.Point;
import android.provider.Settings;
import android.util.DisplayMetrics;
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

    /** NEW: Returns everything currently visible on screen (text + coords)
     *  as a JSON array, so the AI actually knows what's on screen before
     *  deciding what to tap. This did not exist before - nothing exposed
     *  DeviceControlAccessibilityService.dumpScreenElements() to JS. */
    @PluginMethod
    public void readScreen(PluginCall call) {
        DeviceControlAccessibilityService service = DeviceControlAccessibilityService.instance;
        if (service == null) {
            call.reject("Accessibility service not enabled. Call openSettings() first.");
            return;
        }
        try {
            String json = service.dumpScreenElements();
            JSObject ret = new JSObject();
            ret.put("elements", new org.json.JSONArray(json));
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to read screen: " + e.getMessage());
        }
    }

    /** NEW: Finds the on-screen element matching the given text and taps it
     *  directly - the AI should use this instead of guessing raw x/y, since
     *  it doesn't need pixel coordinates, just the label it can see from
     *  readScreen (or that it's reasonably sure is there). */
    @PluginMethod
    public void findAndTap(PluginCall call) {
        DeviceControlAccessibilityService service = DeviceControlAccessibilityService.instance;
        if (service == null) {
            call.reject("Accessibility service not enabled. Call openSettings() first.");
            return;
        }
        String text = call.getString("text");
        if (text == null || text.trim().isEmpty()) {
            call.reject("text required");
            return;
        }
        boolean success = service.findAndTap(text);
        JSObject ret = new JSObject();
        ret.put("success", success);
        if (!success) {
            ret.put("message", "No element matching \"" + text + "\" was found on screen.");
        }
        call.resolve(ret);
    }

    /** Raw coordinate tap - kept as a fallback for when the caller already
     *  knows exact coordinates (e.g. from a prior readScreen() result). */
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

    /** NEW: Scrolls the screen in a direction ("up"/"down"/"left"/"right").
     *  Wraps DeviceControlAccessibilityService.scrollScreen(), which existed
     *  but was never wired up to JS either. */
    @PluginMethod
    public void scroll(PluginCall call) {
        DeviceControlAccessibilityService service = DeviceControlAccessibilityService.instance;
        if (service == null) {
            call.reject("Accessibility service not enabled. Call openSettings() first.");
            return;
        }
        String direction = call.getString("direction", "down");
        DisplayMetrics metrics = getActivity().getResources().getDisplayMetrics();
        Point screenSize = new Point(metrics.widthPixels, metrics.heightPixels);
        service.scrollScreen(direction, screenSize, success -> {
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
