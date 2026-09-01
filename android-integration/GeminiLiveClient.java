package ai.myraa.mobile.plugins;

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
 */
public class GeminiLiveClient {

    public interface Listener {
        void onServerMessage(JSONObject message);
        void onClosed(String reason);
        void onFailure(String error, int httpCode);
    }

    private final OkHttpClient httpClient;
    private WebSocket webSocket;
    private final Listener listener;

    public GeminiLiveClient(Listener listener) {
        this.listener = listener;
        this.httpClient = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build();
    }

    public void connect(String apiKey, String model, String systemInstruction, JSONArray tools,
                         boolean transcriptionEnabled) {
        String url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key="
            + apiKey;
        Request request = new Request.Builder().url(url).build();

        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
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
                    Log.e("GeminiLiveClient", "Bad JSON from server", e);
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
                listener.onClosed(reason);
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                int code = response != null ? response.code() : 0;
                listener.onFailure(t.getMessage() != null ? t.getMessage() : "Connection failed", code);
            }
        });
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
            Log.e("GeminiLiveClient", "sendRealtime(" + field + ") failed", e);
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
            Log.e("GeminiLiveClient", "sendText failed", e);
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
            Log.e("GeminiLiveClient", "sendToolResponse failed", e);
        }
    }

    private void sendRaw(JSONObject msg) {
        if (webSocket != null) webSocket.send(msg.toString());
    }

    public void close() {
        if (webSocket != null) {
            webSocket.close(1000, "Client closing");
            webSocket = null;
        }
    }
}
