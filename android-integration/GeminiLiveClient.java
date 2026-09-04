package ai.myraa.mobile.plugins;

import android.media.AudioManager;
import android.content.Context;
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
    private final AudioManager audioManager;
    private final AudioManager.OnAudioFocusChangeListener audioFocusListener;

    private WebSocket webSocket;
    private String apiKey, model, systemInstruction;
    private JSONArray tools;
    private boolean transcriptionEnabled;
    private volatile boolean manualClose = false;
    private int reconnectAttempt = 0;

    public GeminiLiveClient(Listener listener, Context context) {
        this.listener = listener;
        this.httpClient = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build();

        // Audio Focus Setup
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.audioFocusListener = focusChange -> {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                Log.w(TAG, "Audio focus lost - pausing");
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                Log.d(TAG, "Audio focus gained - resuming");
            }
        };
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

        // Request audio focus
        if (audioManager != null) {
            int result = audioManager.requestAudioFocus(audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.w(TAG, "Audio focus request denied");
            }
        }

        openSocket();
    }

    private void openSocket() {
        String url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key="
            + apiKey;
        Request request = new Request.Builder().url(url).build();

        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
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

        // Abandon audio focus
        if (audioManager != null && audioFocusListener != null) {
            audioManager.abandonAudioFocus(audioFocusListener);
        }

        if (webSocket != null) {
            webSocket.close(1000, "Client closing");
            webSocket = null;
        }
    }
}