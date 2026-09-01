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
                    String pkg = params.optString("packageName", params.optString("appName", ""));
                    Intent launch = activity.getPackageManager().getLaunchIntentForPackage(pkg);
                    if (launch != null) { activity.startActivity(launch); ok(result); }
                    else fail(result, "App not found: " + pkg);
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
                case "OPEN_MAPS": {
                    String query = params.optString("query", "");
                    launch(activity, new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query))), result);
                    break;
                }
                case "START_NAVIGATION": {
                    String dest = params.optString("destination", "");
                    launch(activity, new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(dest))), result);
                    break;
                }
                case "START_TIMER": {
                    int seconds = params.optInt("seconds", 60);
                    Intent i = new Intent(AlarmClock.ACTION_SET_TIMER);
                    i.putExtra(AlarmClock.EXTRA_LENGTH, seconds);
                    i.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
                    launch(activity, i, result);
                    break;
                }
                case "CREATE_ALARM": {
                    Intent i = new Intent(AlarmClock.ACTION_SET_ALARM);
                    i.putExtra(AlarmClock.EXTRA_HOUR, params.optInt("hour", 7));
                    i.putExtra(AlarmClock.EXTRA_MINUTES, params.optInt("minute", 0));
                    i.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
                    launch(activity, i, result);
                    break;
                }
                case "CREATE_REMINDER": {
                    Intent i = new Intent(Intent.ACTION_INSERT);
                    i.setData(CalendarContract.Events.CONTENT_URI);
                    i.putExtra(CalendarContract.Events.TITLE, params.optString("title", "Reminder"));
                    launch(activity, i, result);
                    break;
                }
                case "CREATE_CALENDAR_EVENT": {
                    long start = params.optLong("startMillis", System.currentTimeMillis());
                    long end = params.optLong("endMillis", start + 3600000);
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
                    String number = params.optString("number", "");
                    launch(activity, new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))), result);
                    break;
                }
                case "PREPARE_SMS":
                case "SEND_SMS": {
                    // Opens the messaging app pre-filled - the user still has
                    // to tap send themselves. MYRAA never sends a message on
                    // its own.
                    String number = params.optString("number", "");
                    String body = params.optString("message", "");
                    Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number)));
                    i.putExtra("sms_body", body);
                    launch(activity, i, result);
                    break;
                }
                case "PREPARE_WHATSAPP_MESSAGE":
                case "SEND_WHATSAPP_MESSAGE": {
                    String number = params.optString("number", "");
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
                    boolean down = "down".equals(params.optString("direction", "up"));
                    am.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        down ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                    );
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
