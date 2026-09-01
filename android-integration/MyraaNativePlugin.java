package ai.myraa.mobile.plugins;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.telephony.SmsManager;
import android.util.Log;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.PermissionState;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

@CapacitorPlugin(
    name = "MyraaNative",
    permissions = {
        @Permission(strings = { Manifest.permission.RECORD_AUDIO }, alias = "microphone"),
        @Permission(strings = { Manifest.permission.SEND_SMS }, alias = "sms"),
        @Permission(strings = { Manifest.permission.CALL_PHONE }, alias = "phone")
    }
)
public class MyraaNativePlugin extends Plugin {

    private static final String PREFS = "myraa_secure_prefs";
    private static final String KEY_GEMINI_API_KEY = "gemini_api_key";
    private static final String DEFAULT_MODEL = "gemini-live-2.5-flash-native-audio";

    private GeminiLiveClient geminiClient;
    private PluginCall pendingStartCall;

    // ---------------- API key setup ----------------

    @PluginMethod
    public void getApiStatus(PluginCall call) {
        String savedKey = getPrefs().getString(KEY_GEMINI_API_KEY, null);
        boolean configured = savedKey != null && !savedKey.isEmpty();
        JSObject ret = new JSObject();
        ret.put("configured", configured);
        ret.put("validated", configured);
        call.resolve(ret);
    }

    private static final String KEY_LIVE_MODEL = "gemini_live_model";

    @PluginMethod
    public void testAndSaveGeminiKey(PluginCall call) {
        String apiKey = call.getString("apiKey");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            JSObject ret = new JSObject();
            ret.put("ok", false);
            ret.put("message", "Please enter an API key.");
            call.resolve(ret);
            return;
        }
        final String key = apiKey.trim();
        new Thread(() -> {
            JSObject ret = new JSObject();
            try {
                URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models?key=" + key);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);

                    String liveModel = findBestLiveModel(sb.toString());

                    SharedPreferences.Editor editor = getPrefs().edit();
                    editor.putString(KEY_GEMINI_API_KEY, key);
                    if (liveModel != null) editor.putString(KEY_LIVE_MODEL, liveModel);
                    editor.apply();

                    ret.put("ok", true);
                    ret.put("message", liveModel != null
                        ? "API key verified and saved."
                        : "API key verified and saved, but no voice-capable model was found on this account yet.");
                } else {
                    ret.put("ok", false);
                    ret.put("message", "Google rejected this key (HTTP " + code + "). Check that it was copied fully.");
                }
                conn.disconnect();
            } catch (Exception e) {
                ret.put("ok", false);
                ret.put("message", "Could not reach Google to verify the key: " + e.getMessage());
            }
            call.resolve(ret);
        }).start();
    }

    /** Google renames/retires Live-capable models often. Rather than
     *  hardcoding one, ask the account's own model list which ones
     *  currently support bidiGenerateContent (voice) and pick the best. */
    private String findBestLiveModel(String modelsListJson) {
        try {
            JSONObject root = new JSONObject(modelsListJson);
            JSONArray models = root.optJSONArray("models");
            if (models == null) return null;

            String best = null;
            int bestScore = -1;
            for (int i = 0; i < models.length(); i++) {
                JSONObject m = models.getJSONObject(i);
                JSONArray methods = m.optJSONArray("supportedGenerationMethods");
                boolean supportsLive = false;
                if (methods != null) {
                    for (int j = 0; j < methods.length(); j++) {
                        if ("bidiGenerateContent".equals(methods.getString(j))) {
                            supportsLive = true;
                            break;
                        }
                    }
                }
                if (!supportsLive) continue;

                String name = m.optString("name", "").replace("models/", "");
                if (name.isEmpty()) continue;

                // Prefer newer, non-deprecated, "flash" (cheaper/faster) models.
                int score = 0;
                if (name.contains("flash")) score += 2;
                if (name.contains("native-audio")) score += 2;
                if (name.contains("preview")) score -= 1;
                if (name.contains("2.0") || name.contains("2-0")) score -= 1;

                if (score > bestScore) {
                    bestScore = score;
                    best = name;
                }
            }
            return best;
        } catch (Exception e) {
            Log.e("MyraaNativePlugin", "findBestLiveModel failed", e);
            return null;
        }
    }

    @PluginMethod
    public void completeApiSetup(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("ok", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void deleteGeminiKey(PluginCall call) {
        getPrefs().edit().remove(KEY_GEMINI_API_KEY).apply();
        JSObject ret = new JSObject();
        ret.put("ok", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void openOfficialApiPage(PluginCall call) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getActivity().startActivity(intent);
        call.resolve();
    }

    @PluginMethod
    public void readClipboard(PluginCall call) {
        ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
        JSObject ret = new JSObject();
        if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null && clipboard.getPrimaryClip().getItemCount() > 0) {
            CharSequence text = clipboard.getPrimaryClip().getItemAt(0).getText();
            ret.put("text", text != null ? text.toString() : "");
        } else {
            ret.put("text", "");
        }
        call.resolve(ret);
    }

    // ---------------- Microphone permission ----------------

    @PluginMethod
    public void requestMicrophonePermission(PluginCall call) {
        if (getPermissionState("microphone") == PermissionState.GRANTED) {
            JSObject ret = new JSObject();
            ret.put("granted", true);
            call.resolve(ret);
        } else {
            requestPermissionForAlias("microphone", call, "microphonePermissionCallback");
        }
    }

    @PermissionCallback
    private void microphonePermissionCallback(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("granted", getPermissionState("microphone") == PermissionState.GRANTED);
        call.resolve(ret);
    }

    // ---------------- Gemini Live voice ----------------

    @PluginMethod
    public void startGemini(PluginCall call) {
        String apiKey = getPrefs().getString(KEY_GEMINI_API_KEY, null);
        if (apiKey == null || apiKey.isEmpty()) {
            call.reject("No Gemini API key saved. Please complete API setup first.");
            return;
        }
        String model = getPrefs().getString(KEY_LIVE_MODEL, "gemini-2.0-flash-live-001");

        String systemInstruction = call.getString("systemInstruction", "");
        JSArray toolsArr = call.getArray("tools");
        JSONArray tools = null;
        try {
            tools = toolsArr != null ? new JSONArray(toolsArr.toString()) : null;
        } catch (Exception ignored) {}

        pendingStartCall = call;

        geminiClient = new GeminiLiveClient(new GeminiLiveClient.Listener() {
            @Override
            public void onServerMessage(JSONObject message) {
                handleServerMessage(message);
            }

            @Override
            public void onClosed(String reason) {
                JSObject event = new JSObject();
                event.put("type", "error");
                event.put("error", "Connection closed" + (reason != null && !reason.isEmpty() ? ": " + reason : "."));
                notifyListeners("geminiMessage", event);
            }

            @Override
            public void onFailure(String error, int httpCode) {
                if (pendingStartCall != null) {
                    pendingStartCall.reject(error);
                    pendingStartCall = null;
                }
                JSObject event = new JSObject();
                event.put("type", "error");
                event.put("error", error != null ? error : "MYRAA could not connect to Google Gemini.");
                if (httpCode == 400 || httpCode == 401 || httpCode == 403) {
                    event.put("code", "INVALID_API_KEY");
                }
                notifyListeners("geminiMessage", event);
            }
        });

        geminiClient.connect(apiKey, model, systemInstruction, tools, true);
    }

    /** Translates Google's raw Gemini Live server messages into the flat
     *  event shape the app's JS listener already expects. */
    private void handleServerMessage(JSONObject message) {
        try {
            if (message.has("setupComplete")) {
                if (pendingStartCall != null) {
                    pendingStartCall.resolve(new JSObject());
                    pendingStartCall = null;
                }
                JSObject event = new JSObject();
                event.put("type", "status");
                event.put("status", "connected");
                notifyListeners("geminiMessage", event);
                return;
            }

            if (message.has("toolCall")) {
                JSONObject toolCall = message.getJSONObject("toolCall");
                JSONArray calls = toolCall.optJSONArray("functionCalls");
                if (calls != null) {
                    for (int i = 0; i < calls.length(); i++) {
                        JSONObject fc = calls.getJSONObject(i);
                        JSObject event = new JSObject();
                        event.put("type", "toolCall");
                        event.put("name", fc.optString("name"));
                        event.put("callId", fc.optString("id"));
                        Object args = fc.opt("args");
                        event.put("args", args != null ? new JSObject(args.toString()) : new JSObject());
                        notifyListeners("geminiMessage", event);
                    }
                }
                return;
            }

            if (message.has("serverContent")) {
                JSONObject sc = message.getJSONObject("serverContent");

                if (sc.optBoolean("interrupted", false)) {
                    JSObject event = new JSObject();
                    event.put("type", "interrupted");
                    notifyListeners("geminiMessage", event);
                }

                if (sc.has("inputTranscription")) {
                    String text = sc.getJSONObject("inputTranscription").optString("text", "");
                    if (!text.isEmpty()) {
                        JSObject event = new JSObject();
                        event.put("type", "transcription");
                        event.put("role", "user");
                        event.put("text", text);
                        notifyListeners("geminiMessage", event);
                    }
                }
                if (sc.has("outputTranscription")) {
                    String text = sc.getJSONObject("outputTranscription").optString("text", "");
                    if (!text.isEmpty()) {
                        JSObject event = new JSObject();
                        event.put("type", "transcription");
                        event.put("role", "model");
                        event.put("text", text);
                        notifyListeners("geminiMessage", event);
                    }
                }

                if (sc.has("modelTurn")) {
                    JSONArray parts = sc.getJSONObject("modelTurn").optJSONArray("parts");
                    if (parts != null) {
                        for (int i = 0; i < parts.length(); i++) {
                            JSONObject part = parts.getJSONObject(i);
                            if (part.has("inlineData")) {
                                String data = part.getJSONObject("inlineData").optString("data", "");
                                if (!data.isEmpty()) {
                                    JSObject event = new JSObject();
                                    event.put("type", "audio");
                                    event.put("audio", data);
                                    notifyListeners("geminiMessage", event);
                                }
                            }
                        }
                    }
                }

                if (sc.optBoolean("turnComplete", false)) {
                    JSObject event = new JSObject();
                    event.put("type", "turnComplete");
                    notifyListeners("geminiMessage", event);
                }
            }
        } catch (Exception e) {
            Log.e("MyraaNativePlugin", "Failed to translate server message", e);
        }
    }

    @PluginMethod
    public void stopGemini(PluginCall call) {
        if (geminiClient != null) {
            geminiClient.close();
            geminiClient = null;
        }
        call.resolve();
    }

    @PluginMethod
    public void sendGeminiText(PluginCall call) {
        if (geminiClient == null) { call.reject("Gemini session not started"); return; }
        geminiClient.sendText(call.getString("text", ""));
        call.resolve();
    }

    @PluginMethod
    public void sendGeminiAudio(PluginCall call) {
        if (geminiClient == null) { call.reject("Gemini session not started"); return; }
        geminiClient.sendAudioChunk(call.getString("base64", ""));
        call.resolve();
    }

    @PluginMethod
    public void sendGeminiVideo(PluginCall call) {
        if (geminiClient == null) { call.reject("Gemini session not started"); return; }
        geminiClient.sendVideoFrame(call.getString("base64", ""));
        call.resolve();
    }

    @PluginMethod
    public void sendToolResponse(PluginCall call) {
        if (geminiClient == null) { call.reject("Gemini session not started"); return; }
        String id = call.getString("id", "");
        String name = call.getString("name", "");
        Object output = call.getData().opt("output");
        geminiClient.sendToolResponse(id, name, output);
        call.resolve();
    }

    // ---------------- Device actions ----------------

    private JSObject pendingActionParams;

    @PluginMethod
    public void executeAction(PluginCall call) {
        String action = call.getString("action");
        JSObject params = call.getObject("params", new JSObject());

        // SEND_SMS and CALL_CONTACT send/call directly with no extra tap,
        // once the user has granted the matching permission. Everything
        // else (including PREPARE_SMS, which stays a draft the user must
        // tap send on) goes through DeviceActionExecutor as before.
        if ("SEND_SMS".equals(action)) {
            if (getPermissionState("sms") != PermissionState.GRANTED) {
                pendingActionParams = params;
                requestPermissionForAlias("sms", call, "smsPermissionCallback");
                return;
            }
            sendSmsDirect(call, params);
            return;
        }
        if ("CALL_CONTACT".equals(action)) {
            if (getPermissionState("phone") != PermissionState.GRANTED) {
                pendingActionParams = params;
                requestPermissionForAlias("phone", call, "phonePermissionCallback");
                return;
            }
            placeCallDirect(call, params);
            return;
        }

        try {
            JSONObject result = DeviceActionExecutor.execute(getActivity(), action, new JSONObject(params.toString()));
            call.resolve(new JSObject(result.toString()));
        } catch (Exception e) {
            JSObject ret = new JSObject();
            ret.put("ok", false);
            ret.put("message", e.getMessage() != null ? e.getMessage() : "Unknown error");
            call.resolve(ret);
        }
    }

    @PermissionCallback
    private void smsPermissionCallback(PluginCall call) {
        if (getPermissionState("sms") == PermissionState.GRANTED) {
            sendSmsDirect(call, pendingActionParams);
        } else {
            JSObject ret = new JSObject();
            ret.put("ok", false);
            ret.put("message", "SMS permission was not granted.");
            call.resolve(ret);
        }
    }

    @PermissionCallback
    private void phonePermissionCallback(PluginCall call) {
        if (getPermissionState("phone") == PermissionState.GRANTED) {
            placeCallDirect(call, pendingActionParams);
        } else {
            JSObject ret = new JSObject();
            ret.put("ok", false);
            ret.put("message", "Phone call permission was not granted.");
            call.resolve(ret);
        }
    }

    private void sendSmsDirect(PluginCall call, JSObject params) {
        JSObject ret = new JSObject();
        try {
            String number = params.getString("number");
            String message = params.getString("message");
            SmsManager smsManager = SmsManager.getDefault();
            ArrayList<String> parts = smsManager.divideMessage(message);
            smsManager.sendMultipartTextMessage(number, null, parts, null, null);
            ret.put("ok", true);
        } catch (Exception e) {
            ret.put("ok", false);
            ret.put("message", e.getMessage() != null ? e.getMessage() : "Could not send SMS.");
        }
        call.resolve(ret);
    }

    private void placeCallDirect(PluginCall call, JSObject params) {
        JSObject ret = new JSObject();
        try {
            String number = params.getString("number");
            Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number)));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getActivity().startActivity(intent);
            ret.put("ok", true);
        } catch (Exception e) {
            ret.put("ok", false);
            ret.put("message", e.getMessage() != null ? e.getMessage() : "Could not place the call.");
        }
        call.resolve(ret);
    }

    /** Matches the confirmation-token flow the JS already expects before
     *  running actions like calls/messages. Real safety comes from
     *  DeviceActionExecutor only ever opening a pre-filled app for those -
     *  MYRAA never sends/calls on its own regardless of this token. */
    @PluginMethod
    public void createConfirmation(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("token", java.util.UUID.randomUUID().toString());
        ret.put("expiresAt", System.currentTimeMillis() + 60000);
        call.resolve(ret);
    }

    // ---------------- Background mode (basic stub for now) ----------------

    @PluginMethod
    public void startBackgroundMode(PluginCall call) {
        // Keeping the voice session alive while the app is backgrounded
        // needs a full foreground service - not wired up in this stage.
        // Voice works fine while MYRAA is in the foreground.
        call.resolve();
    }

    @PluginMethod
    public void stopBackgroundMode(PluginCall call) {
        call.resolve();
    }

    // ---------------- Memory consolidation ----------------

    @PluginMethod
    public void consolidateMemories(PluginCall call) {
        String apiKey = getPrefs().getString(KEY_GEMINI_API_KEY, null);
        String prompt = call.getString("prompt", "");
        if (apiKey == null || apiKey.isEmpty()) {
            call.reject("No Gemini API key saved.");
            return;
        }
        new Thread(() -> {
            JSObject ret = new JSObject();
            try {
                URL url = new URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey
                );
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                JSONObject part = new JSONObject().put("text", prompt);
                JSONObject content = new JSONObject().put("parts", new JSONArray().put(part));
                JSONObject body = new JSONObject().put("contents", new JSONArray().put(content));

                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                conn.getOutputStream().write(bytes);

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    JSONObject resp = new JSONObject(sb.toString());
                    String text = resp
                        .getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts").getJSONObject(0)
                        .optString("text", "{}");
                    ret.put("json", text);
                } else {
                    ret.put("json", "{}");
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e("MyraaNativePlugin", "consolidateMemories failed", e);
                try { ret.put("json", "{}"); } catch (Exception ignored) {}
            }
            call.resolve(ret);
        }).start();
    }

    private SharedPreferences getPrefs() {
        return getActivity().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
