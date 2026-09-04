package ai.myraa.mobile.plugins;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

/**
 * Speaks Google's raw Gemini Live WebSocket protocol
 * (BidiGenerateContent). Knows nothing about MYRAA's own event shape -
 * that translation happens in MyraaNativePlugin. This class only
 * connects, sends client messages, and forwards whatever JSON the
 * server sends back.
 *
 * Automatically reconnects on unexpected drops (network blips, the
 * occasional server-side close) so a brief hiccup doesn't kill the
 * whole voice session and force the user to manually restart it.
 * NOTE: a reconnect starts a brand-new Gemini session (no session
 * resumption token implemented yet), so very recent conversational
 * context can be lost across a reconnect - but the voice link itself
 * recovers on its own instead of going silent and staying silent.
 *
 * pingInterval is intentionally short (15s). Counterintuitively, a
 * SHORTER interval is more reliable on mobile than a longer one: many
 * carrier NATs silently drop an "idle" connection's mapping after
 * 30-60s of no traffic, and a ping sent into an already-dropped
 * mapping never gets a pong back regardless of how patiently we wait -
 * OkHttp then reports "sent ping but didn't receive pong". Frequent
 * small pings keep the NAT mapping alive AND detect a real stall
 * faster, which matters because detection is what triggers reconnect.
 */
public class GeminiLiveClient {

    private static final String TAG = "GeminiLiveClient";
    private static final int MAX_RECONNECT_ATTEMPTS = 6;
    private static final long[] RECONNECT_DELAYS_MS = {1000, 2000, 4000, 8000, 8000, 8000};

    public interface Listener {
        void onServerMessage(JSONObject message);
        void onClosed(String reason);
        void onFailure(String error, int httpCode);
    }

    private final OkHttpClient httpClient;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WebSocket webSocket;

    // Stored so a reconnect can rebuild the exact same session setup
    // without the caller having to call connect() again.
    private String apiKey, model, systemInstruction;
    private JSONArray tools;
    private boolean transcriptionEnabled;

    private volatile boolean manualClose = false;
    private int reconnectAttempt = 0;

    public GeminiLiveClient(Listener listener) {
        this.listener = listener;
        this.httpClient = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build();
    }

    public void connect(String apiKey, String model, String systemInstruction, JSONArray tools,
                         boolean transcriptionEnabled) {
        this.apiKey = apiKey;
        this.model = model;
        this.systemInstruction = systemInstruction;
        this.tools = tools;
        this.transcriptionEnabled = transcriptionEnabled;
        this.manualClose = false;
        this.reconnectAttempt = 0;
        openSocket();
    }

    private void openSocket() {
        String url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key="
            + apiKey;
        Request request = new Request.Builder().url(url).build();

        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                // A live TCP/TLS connection means the network path currently
                // works - reset the retry counter so a future drop gets a
                // full set of attempts again instead of inheriting an
                // already-exhausted count from an earlier, unrelated blip.
                reconnectAttempt = 0;
                try {
                    JSONObject setupInner = new JSONObject();
                    setupInner.put("model", "models/" + model);

                    JSONObject genConfig = new JSONObject();
                    genConfig.put("responseModalities", new JSONArray().put("AUDIO"));
                    setupInner.put("generationConfig", genConfig);

                    if (systemInstruction != null && !systemInstruction.isEmpty()) {
                        JSONObject parts0 = new JSONObject().put("text", systemInstruction);
                        JSONObject sysInstr = new JSONObject().put("parts", new JSONArray().put(parts0));
                        setupInner.put("systemInstruction", sysInstr);
                    }
                    if (tools != null) {
                        setupInner.put("tools", tools);
                    }
                    if (transcriptionEnabled) {
                        setupInner.put("inputAudioTranscription", new JSONObject());
                        setupInner.put("outputAudioTranscription", new JSONObject());
                    }

                    JSONObject setup = new JSONObject().put("setup", setupInner);
                    ws.send(setup.toString());
                } catch (Exception e) {
                    listener.onFailure("Failed to send setup: " + e.getMessage(), 0);
                }
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                try {
                    listener.onServerMessage(new JSONObject(text));
                } catch (Exception e) {
                    Log.e(TAG, "Bad JSON from server", e);
                }
            }

            @Override
            public void onMessage(WebSocket ws, ByteString bytes) {
                try {
                    listener.onServerMessage(new JSONObject(bytes.utf8()));
                } catch (Exception ignored) {}
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                // A manual (user-initiated) close is silent - no reconnect,
                // no error surfaced to the UI. An unexpected close tries to
                // reconnect first; only once attempts are exhausted does the
                // UI see anything.
                if (manualClose) return;
                if (!tryReconnect()) {
                    listener.onClosed(reason);
                }
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                if (manualClose) return;
                if (!tryReconnect()) {
                    int code = response != null ? response.code() : 0;
                    listener.onFailure(t.getMessage() != null ? t.getMessage() : "Connection failed", code);
                }
            }
        });
    }

    /** Attempts to reconnect after an unexpected drop. Returns true if a
     *  retry was scheduled (caller should NOT surface an error yet),
     *  false once attempts are exhausted (caller should surface it now). */
    private boolean tryReconnect() {
        if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) return false;
        long delay = RECONNECT_DELAYS_MS[Math.min(reconnectAttempt, RECONNECT_DELAYS_MS.length - 1)];
        reconnectAttempt++;
        Log.w(TAG, "Voice connection dropped, reconnecting (attempt "
            + reconnectAttempt + "/" + MAX_RECONNECT_ATTEMPTS + ") in " + delay + "ms");
        mainHandler.postDelayed(() -> {
            if (!manualClose) openSocket();
        }, delay);
        return true;
    }

    public void sendAudioChunk(String base64Data) {
        sendRealtime("audio", base64Data, "audio/pcm;rate=16000");
    }

    public void sendVideoFrame(String base64Data) {
        sendRealtime("video", base64Data, "image/jpeg");
    }

    private void sendRealtime(String field, String base64Data, String mimeType) {
        try {
            JSONObject media = new JSONObject();
            media.put("data", base64Data);
            media.put("mimeType", mimeType);
            JSONObject realtimeInput = new JSONObject();
            realtimeInput.put(field, media);
            JSONObject msg = new JSONObject().put("realtimeInput", realtimeInput);
            sendRaw(msg);
        } catch (Exception e) {
            Log.e(TAG, "sendRealtime(" + field + ") failed", e);
        }
    }

    public void sendText(String text) {
        try {
            JSONObject part = new JSONObject().put("text", text);
            JSONObject turn = new JSONObject();
            turn.put("role", "user");
            turn.put("parts", new JSONArray().put(part));
            JSONObject clientContent = new JSONObject();
            clientContent.put("turns", new JSONArray().put(turn));
            clientContent.put("turnComplete", true);
            sendRaw(new JSONObject().put("clientContent", clientContent));
        } catch (Exception e) {
            Log.e(TAG, "sendText failed", e);
        }
    }

    public void sendToolResponse(String callId, String name, Object output) {
        try {
            JSONObject response = new JSONObject().put("result", output);
            JSONObject functionResponse = new JSONObject();
            functionResponse.put("id", callId);
            functionResponse.put("name", name);
            functionResponse.put("response", response);
            JSONObject toolResponse = new JSONObject();
            toolResponse.put("functionResponses", new JSONArray().put(functionResponse));
            sendRaw(new JSONObject().put("toolResponse", toolResponse));
        } catch (Exception e) {
            Log.e(TAG, "sendToolResponse failed", e);
        }
    }

    private void sendRaw(JSONObject msg) {
        if (webSocket != null) webSocket.send(msg.toString());
    }

    public void close() {
        manualClose = true;
        mainHandler.removeCallbacksAndMessages(null);
        if (webSocket != null) {
            webSocket.close(1000, "Client closing");
            webSocket = null;
        }
    }
}
