package ai.myraa.mobile.plugins;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Lets MYRAA perform actions on the SAME device it is running on
 * (tap, swipe, type, read). Only works after the user manually enables
 * "MYRAA" under Settings > Accessibility - Android requires this
 * explicit opt-in and it cannot be automated from code.
 *
 * Strictly local, on-device automation only - no ability to see or
 * act on any other device.
 */
public class DeviceControlAccessibilityService extends AccessibilityService {

    public static DeviceControlAccessibilityService instance;

    private HandlerThread gestureThread;
    private Handler gestureHandler;

    public interface BoolCallback {
        void onResult(boolean success);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        gestureThread = new HandlerThread("MyraaGestureCallbacks");
        gestureThread.start();
        gestureHandler = new Handler(gestureThread.getLooper());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (gestureThread != null) gestureThread.quitSafely();
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
        }, gestureHandler);
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
        }, gestureHandler);
    }

    /** Finds the on-screen element whose visible text or content
     *  description best matches the given text, and taps it.
     *  Returns false if nothing close enough was found. */
    public boolean findAndTap(String searchText) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || searchText == null || searchText.trim().isEmpty()) return false;

        AccessibilityNodeInfo match = findBestTextMatch(root, searchText.trim().toLowerCase());
        if (match == null) return false;

        // FIX: prefer the bounds of the nearest clickable ANCESTOR (the real
        // touch target in most real apps - label text sits inside a clickable
        // container), falling back to the matched node's own bounds if
        // nothing up the chain is clickable.
        AccessibilityNodeInfo target = match;
        AccessibilityNodeInfo walker = match;
        while (walker != null) {
            if (walker.isClickable()) {
                target = walker;
                break;
            }
            walker = walker.getParent();
        }

        android.graphics.Rect bounds = new android.graphics.Rect();
        target.getBoundsInScreen(bounds);
        float centerX = bounds.centerX();
        float centerY = bounds.centerY();

        final boolean[] result = { false };
        final Object lock = new Object();
        tap(centerX, centerY, success -> {
            synchronized (lock) {
                result[0] = success;
                lock.notifyAll();
            }
        });
        synchronized (lock) {
            try { lock.wait(1500); } catch (InterruptedException ignored) {}
        }
        return result[0];
    }

    private AccessibilityNodeInfo findBestTextMatch(AccessibilityNodeInfo root, String needle) {
        AccessibilityNodeInfo best = null;
        int bestScore = -1;
        java.util.ArrayDeque<AccessibilityNodeInfo> queue = new java.util.ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.poll();
            if (node == null) continue;
            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            String label = (text != null ? text : desc != null ? desc : "").toString().toLowerCase();
            // FIX: removed the isClickable()/isCheckable() requirement that
            // used to gate this match. Labels very often live on a plain
            // TextView inside a clickable parent - requiring the LABELED
            // node itself to be clickable made those buttons invisible to
            // findAndTap. findAndTap() now walks up to the clickable
            // ancestor separately, after the text match is found.
            if (!label.isEmpty()) {
                int score = -1;
                if (label.equals(needle)) score = 100;
                else if (label.contains(needle)) score = 70;
                else if (needle.contains(label) && label.length() > 1) score = 40;
                if (score > bestScore) {
                    bestScore = score;
                    best = node;
                }
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                queue.add(node.getChild(i));
            }
        }
        return bestScore >= 40 ? best : null;
    }

    /** NEW: Returns a JSON array string of every visible text element on
     *  screen right now, with its center coordinates - this is what MYRAA
     *  reads to know what's on screen and decide what to tap. This method
     *  did not exist before; "can't read the screen" was because there was
     *  nothing here to call. */
    public String dumpScreenElements() {
        JSONArray result = new JSONArray();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return result.toString();

        java.util.ArrayDeque<AccessibilityNodeInfo> queue = new java.util.ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.poll();
            if (node == null) continue;
            try {
                CharSequence text = node.getText();
                CharSequence desc = node.getContentDescription();
                String label = text != null ? text.toString() : (desc != null ? desc.toString() : null);
                if (label != null && !label.trim().isEmpty()) {
                    android.graphics.Rect bounds = new android.graphics.Rect();
                    node.getBoundsInScreen(bounds);
                    JSONObject obj = new JSONObject();
                    obj.put("text", label.trim());
                    obj.put("clickable", node.isClickable());
                    obj.put("x", bounds.centerX());
                    obj.put("y", bounds.centerY());
                    result.put(obj);
                }
            } catch (JSONException ignored) {
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                queue.add(node.getChild(i));
            }
        }
        return result.toString();
    }

    /** Swipes across most of the screen in the given direction. */
    public void scrollScreen(String direction, android.graphics.Point screenSize, final BoolCallback callback) {
        float centerX = screenSize.x / 2f;
        float centerY = screenSize.y / 2f;
        float topY = screenSize.y * 0.2f;
        float bottomY = screenSize.y * 0.8f;
        float leftX = screenSize.x * 0.15f;
        float rightX = screenSize.x * 0.85f;

        float x1 = centerX, y1 = centerY, x2 = centerX, y2 = centerY;
        if (direction == null) direction = "down";
        switch (direction.toLowerCase()) {
            case "up": y1 = bottomY; y2 = topY; break;
            case "down": y1 = topY; y2 = bottomY; break;
            case "left": x1 = rightX; x2 = leftX; break;
            case "right": x1 = leftX; x2 = rightX; break;
            default: y1 = topY; y2 = bottomY;
        }
        swipe(x1, y1, x2, y2, 300, callback);
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
