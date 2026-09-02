package ai.myraa.mobile.plugins;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.KeyEvent;
import org.json.JSONObject;

public class DeviceActionExecutor {

    public static JSONObject execute(Activity activity, String action, JSONObject params) {
        JSONObject result = new JSONObject();
        if (action == null) {
            fail(result, "No action specified");
            return result;
        }
        try {
            switch (action) {
                case "OPEN_APP": {
                    String target = params.optString("target", "");
                    String pkg = findPackageByNameOrLabel(activity, target);
                    if (pkg != null) {
                        Intent launch = activity.getPackageManager().getLaunchIntentForPackage(pkg);
                        if (launch != null) { activity.startActivity(launch); ok(result); break; }
                    }
                    fail(result, "Could not find an installed app matching: " + target);
                    break;
                }
                case "OPEN_CAMERA":
                    launch(activity, new Intent(MediaStore.ACTION_IMAGE_CAPTURE), result);
                    break;
                case "OPEN_GALLERY":
                    launch(activity, new Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI), result);
                    break;
                case "OPEN_SETTINGS":
                    launch(activity, new Intent(Settings.ACTION_SETTINGS), result);
                    break;
                case "OPEN_WIFI_SETTINGS":
                    launch(activity, new Intent(Settings.ACTION_WIFI_SETTINGS), result);
                    break;
                case "OPEN_BLUETOOTH_SETTINGS":
                    launch(activity, new Intent(Settings.ACTION_BLUETOOTH_SETTINGS), result);
                    break;
                case "OPEN_BATTERY_SETTINGS":
                    launch(activity, new Intent(Intent.ACTION_POWER_USAGE_SUMMARY), result);
                    break;
                case "OPEN_DISPLAY_SETTINGS":
                    launch(activity, new Intent(Settings.ACTION_DISPLAY_SETTINGS), result);
                    break;
                case "OPEN_STORAGE_SETTINGS":
                    launch(activity, new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS), result);
                    break;
                case "OPEN_HOTSPOT_SETTINGS":
                    launch(activity, new Intent(Settings.ACTION_WIRELESS_SETTINGS), result);
                    break;
                case "OPEN_ACCESSIBILITY_SETTINGS":
                    launch(activity, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), result);
                    break;
                case "OPEN_NOTIFICATION_SETTINGS": {
                    Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    i.putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
                    launch(activity, i, result);
                    break;
                }
                case "OPEN_DND_SETTINGS":
                    launch(activity, new Intent("android.settings.ZEN_MODE_SETTINGS"), result);
                    break;
                case "CHANGE_BRIGHTNESS":
                    // Changing brightness programmatically needs WRITE_SETTINGS,
                    // which Play Store heavily restricts. Opening display
                    // settings lets the user do it themselves instead.
                    launch(activity, new Intent(Settings.ACTION_DISPLAY_SETTINGS), result);
                    break;
                case "CLOSE_MYRAAA":
                    activity.finish();
                    ok(result);
                    break;
                case "GO_HOME":
                    // Sends the app to the background like the Home button
                    // would - keeps the process (and voice/services) alive,
                    // unlike finish() which would end the app entirely.
                    activity.moveTaskToBack(true);
                    ok(result);
                    break;
                case "OPEN_MAPS": {
                    String query = params.optString("query", params.optString("target", ""));
                    launch(activity, new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query))), result);
                    break;
                }
                case "START_NAVIGATION": {
                    String dest = params.optString("destination", params.optString("target", ""));
                    launch(activity, new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(dest))), result);
                    break;
                }
                case "START_TIMER": {
                    int seconds = params.optInt("durationSeconds", 60);
                    Intent i = new Intent(AlarmClock.ACTION_SET_TIMER);
                    i.putExtra(AlarmClock.EXTRA_LENGTH, seconds);
                    i.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
                    launch(activity, i, result);
                    break;
                }
                case "CREATE_ALARM": {
                    // "timestamp" is epoch millis for the desired alarm time.
                    long timestamp = params.optLong("timestamp", 0);
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    int hour = 7, minute = 0;
                    if (timestamp > 0) {
                        cal.setTimeInMillis(timestamp);
                        hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
                        minute = cal.get(java.util.Calendar.MINUTE);
                    }
                    Intent i = new Intent(AlarmClock.ACTION_SET_ALARM);
                    i.putExtra(AlarmClock.EXTRA_HOUR, hour);
                    i.putExtra(AlarmClock.EXTRA_MINUTES, minute);
                    i.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
                    launch(activity, i, result);
                    break;
                }
                case "CREATE_REMINDER": {
                    Intent i = new Intent(Intent.ACTION_INSERT);
                    i.setData(CalendarContract.Events.CONTENT_URI);
                    i.putExtra(CalendarContract.Events.TITLE, params.optString("title", "Reminder"));
                    long ts = params.optLong("timestamp", 0);
                    if (ts > 0) i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, ts);
                    launch(activity, i, result);
                    break;
                }
                case "CREATE_CALENDAR_EVENT": {
                    long start = params.optLong("timestamp", System.currentTimeMillis());
                    long durationSec = params.optLong("durationSeconds", 3600);
                    long end = start + durationSec * 1000;
                    Intent i = new Intent(Intent.ACTION_INSERT);
                    i.setData(CalendarContract.Events.CONTENT_URI);
                    i.putExtra(CalendarContract.Events.TITLE, params.optString("title", "Event"));
                    i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start);
                    i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end);
                    launch(activity, i, result);
                    break;
                }
                case "CALL_CONTACT": {
                    // Opens the dialer pre-filled - the user still has to tap
                    // the call button themselves. MYRAA never places a call
                    // on its own.
                    String number = params.optString("target", "");
                    launch(activity, new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))), result);
                    break;
                }
                case "PREPARE_SMS":
                case "SEND_SMS": {
                    // Opens the messaging app pre-filled - the user still has
                    // to tap send themselves. MYRAA never sends a message on
                    // its own.
                    String number = params.optString("target", "");
                    String body = params.optString("message", "");
                    Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number)));
                    i.putExtra("sms_body", body);
                    launch(activity, i, result);
                    break;
                }
                case "PREPARE_WHATSAPP_MESSAGE":
                case "SEND_WHATSAPP_MESSAGE": {
                    String number = params.optString("target", "");
                    String text = params.optString("message", "");
                    Uri uri = Uri.parse("https://wa.me/" + Uri.encode(number) + "?text=" + Uri.encode(text));
                    launch(activity, new Intent(Intent.ACTION_VIEW, uri), result);
                    break;
                }
                case "WEB_SEARCH": {
                    Intent i = new Intent(Intent.ACTION_WEB_SEARCH);
                    i.putExtra("query", params.optString("query", ""));
                    launch(activity, i, result);
                    break;
                }
                case "YOUTUBE_SEARCH": {
                    String q = params.optString("query", "");
                    Uri uri = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q));
                    launch(activity, new Intent(Intent.ACTION_VIEW, uri), result);
                    break;
                }
                case "MEDIA_PLAY":
                    sendMediaKey(activity, KeyEvent.KEYCODE_MEDIA_PLAY);
                    ok(result);
                    break;
                case "MEDIA_PAUSE":
                    sendMediaKey(activity, KeyEvent.KEYCODE_MEDIA_PAUSE);
                    ok(result);
                    break;
                case "MEDIA_NEXT":
                    sendMediaKey(activity, KeyEvent.KEYCODE_MEDIA_NEXT);
                    ok(result);
                    break;
                case "MEDIA_PREVIOUS":
                    sendMediaKey(activity, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                    ok(result);
                    break;
                case "CHANGE_MEDIA_VOLUME": {
                    AudioManager am = (AudioManager) activity.getSystemService(Activity.AUDIO_SERVICE);
                    int maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    if (params.has("volume") && !params.isNull("volume")) {
                        double raw = params.optDouble("volume", -1);
                        if (raw >= 0) {
                            // Treat as an absolute target: 0-1 as a fraction,
                            // or 0-100 as a percentage of max volume.
                            double fraction = raw > 1 ? (raw / 100.0) : raw;
                            fraction = Math.max(0, Math.min(1, fraction));
                            int target = (int) Math.round(fraction * maxVolume);
                            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI);
                            ok(result);
                            break;
                        }
                    }
                    // No usable number given (e.g. just "turn it up") - nudge it.
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                    ok(result);
                    break;
                }
                case "FLASHLIGHT_ON":
                case "FLASHLIGHT_OFF":
                    setFlashlight(activity, "FLASHLIGHT_ON".equals(action), result);
                    break;
                case "READ_NOTIFICATION":
                    fail(result, "Reading notifications needs a separate notification-access setup that isn't wired up yet.");
                    break;
                case "OPEN_MYRAAA_SETTINGS":
                case "OPEN_API_SETUP":
                case "CONTROL_3D_CAMERA":
                    // Handled entirely in the web UI, nothing to do natively.
                    ok(result);
                    break;
                default:
                    fail(result, "Action not implemented yet: " + action);
            }
        } catch (ActivityNotFoundException e) {
            fail(result, "No app available to handle this action.");
        } catch (Exception e) {
            fail(result, e.getMessage() != null ? e.getMessage() : "Unknown error");
        }
        return result;
    }

    /** Voice commands rarely give an exact package name (e.g. "open
     *  Instagram" instead of "com.instagram.android"). This tries an
     *  exact package match first, then falls back to matching against
     *  each installed app's visible display name. */
    private static String findPackageByNameOrLabel(Activity activity, String target) {
        if (target == null || target.trim().isEmpty()) return null;
        android.content.pm.PackageManager pm = activity.getPackageManager();

        // 1) Exact package name match.
        try {
            pm.getPackageInfo(target, 0);
            return target;
        } catch (Exception ignored) {}

        // 2) Fuzzy match against each launchable app's display label.
        String needle = target.trim().toLowerCase();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        java.util.List<android.content.pm.ResolveInfo> apps = pm.queryIntentActivities(launcherIntent, 0);

        String bestPkg = null;
        int bestScore = -1;
        for (android.content.pm.ResolveInfo info : apps) {
            String label = String.valueOf(info.loadLabel(pm)).toLowerCase();
            String pkgName = info.activityInfo.packageName;
            int score = -1;
            if (label.equals(needle)) score = 100;
            else if (label.startsWith(needle)) score = 80;
            else if (label.contains(needle)) score = 60;
            else if (needle.contains(label) && label.length() > 2) score = 40;
            if (score > bestScore) {
                bestScore = score;
                bestPkg = pkgName;
            }
        }
        return bestScore >= 40 ? bestPkg : null;
    }

    private static void launch(Activity activity, Intent intent, JSONObject result) {
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
        ok(result);
    }

    private static void sendMediaKey(Activity activity, int keyCode) {
        AudioManager am = (AudioManager) activity.getSystemService(Activity.AUDIO_SERVICE);
        long eventTime = System.currentTimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0));
    }

    private static void setFlashlight(Activity activity, boolean on, JSONObject result) {
        try {
            CameraManager camManager = (CameraManager) activity.getSystemService(Activity.CAMERA_SERVICE);
            String camId = camManager.getCameraIdList()[0];
            camManager.setTorchMode(camId, on);
            ok(result);
        } catch (CameraAccessException e) {
            fail(result, "Could not access the flashlight.");
        } catch (Exception e) {
            fail(result, "This device has no flashlight.");
        }
    }

    private static void ok(JSONObject result) {
        try { result.put("ok", true); } catch (Exception ignored) {}
    }

    private static void fail(JSONObject result, String message) {
        try { result.put("ok", false); result.put("message", message); } catch (Exception ignored) {}
    }
}
