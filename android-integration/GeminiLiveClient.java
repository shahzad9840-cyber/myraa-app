package ai.myraa.mobile.plugins;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import org.json.JSONObject; // ← JSON import add kiya

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class GeminiLiveClient {
    private static final String TAG = "GeminiLiveClient";
    private static final String GEMINI_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService/StreamGenerateContent";
    
    private Listener listener;
    private WebSocket webSocket;
    private OkHttpClient client;
    private AudioTrack audioTrack;
    private String apiKey;

    // Constructor - Sirf Listener
    public GeminiLiveClient(Listener listener) {
        this.listener = listener;
        
        this.client = new OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .connectTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    // API Key set karne ke liye
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    // Connect to Gemini
    public void start() {
        if (apiKey == null || apiKey.isEmpty()) {
            Log.e(TAG, "API Key not set!");
            if (listener != null) {
                listener.onError(new Throwable("API Key not set"));
            }
            return;
        }

        String url = GEMINI_URL + "?key=" + apiKey;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.d(TAG, "WebSocket opened");
                if (listener != null) {
                    listener.onConnected();
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.d(TAG, "Message received");
                try {
                    // JSON parse karo agar zaroorat ho
                    JSONObject json = new JSONObject(text);
                    // yahan se audio extract karo
                    
                    if (listener != null) {
                        listener.onMessage(text);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Parse error: " + e.getMessage());
                    if (listener != null) {
                        listener.onError(e);
                    }
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                Log.d(TAG, "Binary message received");
                if (listener != null) {
                    listener.onMessage(bytes.base64());
                }
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "Closing: " + reason);
                webSocket.close(code, reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "Closed: " + reason);
                if (listener != null) {
                    listener.onDisconnected();
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "WebSocket error: " + t.getMessage());
                if (listener != null) {
                    listener.onError(t);
                }
            }
        });
    }

    // Send message to Gemini
    public void sendMessage(String message) {
        if (webSocket == null) {
            Log.e(TAG, "WebSocket is null");
            return;
        }

        try {
            JSONObject json = new JSONObject();
            JSONObject contents = new JSONObject();
            // JSON build karo
            String payload = "{\"contents\":[{\"parts\":[{\"text\":\"" + message + "\"}]}]}";
            webSocket.send(payload);
        } catch (Exception e) {
            Log.e(TAG, "Send error: " + e.getMessage());
        }
    }

    // Stop connection
    public void stop() {
        if (webSocket != null) {
            webSocket.close(1000, "Normal closure");
            webSocket = null;
        }
        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack.release();
            audioTrack = null;
        }
    }

    // Create AudioTrack - Redmi Note 9S Optimized
    public AudioTrack createAudioTrack() {
        try {
            int sampleRate = 16000;
            int minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            int bufferSize = minBufferSize * 8;

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();

            AudioFormat audioFormat = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();

            audioTrack = new AudioTrack(
                    audioAttributes,
                    audioFormat,
                    bufferSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
            );

            audioTrack.play();
            return audioTrack;

        } catch (Exception e) {
            Log.e(TAG, "AudioTrack error: " + e.getMessage());
            return null;
        }
    }

    // Play audio
    public void playAudio(byte[] audioData) {
        if (audioTrack == null) {
            createAudioTrack();
        }

        if (audioTrack == null || audioData == null) {
            return;
        }

        try {
            int written = audioTrack.write(
                    audioData,
                    0,
                    audioData.length,
                    AudioTrack.WRITE_NON_BLOCKING
            );

            if (written < audioData.length) {
                Log.w(TAG, "Partial write: " + written + "/" + audioData.length);
            }

        } catch (Exception e) {
            Log.e(TAG, "Play error: " + e.getMessage());
        }
    }

    // Listener Interface
    public interface Listener {
        void onConnected();
        void onDisconnected();
        void onError(Throwable t);
        void onMessage(String message);
    }
}