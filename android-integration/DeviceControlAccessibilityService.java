package ai.myraa.mobile.plugins;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Lets MYRAA perform actions on the SAME device it is running on
 * (tap, swipe, type). Only works after the user manually enables
 * "MYRAA" under Settings > Accessibility - Android requires this
 * explicit opt-in and it cannot be automated from code.
 *
 * Strictly local, on-device automation only - no ability to see or
 * act on any other device.
 */
public class DeviceControlAccessibilityService extends AccessibilityService {

    public static DeviceControlAccessibilityService instance;

    public interface BoolCallback {
        void onResult(boolean success);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not used for control actions, only required override.
    }

    @Override
    public void onInterrupt() {}

    public void tap(float x, float y, final BoolCallback callback) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path, 0, 50))
            .build();
        dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                callback.onResult(true);
            }
            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                callback.onResult(false);
            }
        }, null);
    }

    public void swipe(float x1, float y1, float x2, float y2, long durationMs, final BoolCallback callback) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path, 0, durationMs))
            .build();
        dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                callback.onResult(true);
            }
            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                callback.onResult(false);
            }
        }, null);
    }

    /** Types text into whichever field currently has focus. */
    public boolean typeText(String text) {
        AccessibilityNodeInfo node = findFocusedEditable(getRootInActiveWindow());
        if (node == null) return false;
        Bundle arguments = new Bundle();
        arguments.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text
        );
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
    }

    private AccessibilityNodeInfo findFocusedEditable(AccessibilityNodeInfo root) {
        if (root == null) return null;
        if (root.isFocused() && root.isEditable()) return root;
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo found = findFocusedEditable(root.getChild(i));
            if (found != null) return found;
        }
        return null;
    }
}
