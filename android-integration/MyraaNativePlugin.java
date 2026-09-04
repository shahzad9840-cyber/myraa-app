package ai.myraa.mobile.plugins;

import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CapacitorPlugin(name = "MyraaNativePlugin")
public class MyraaNativePlugin extends Plugin {

    private static final String TAG = "MyraaNativePlugin";
    private GeminiLiveClient geminiClient;
    private ExecutorService audioExecutor = Executors.newSingleThreadExecutor();
    private Queue<byte[]> audioBuffer = new LinkedList<>();
    private static final int BUFFER_THRESHOLD = 3;

    @PluginMethod
    public void initGemini(PluginCall call) {
        String apiKey = call.getString("apiKey");
        String model = call.getString("model", "gemini-2.0-flash-exp");
        String systemInstruction = call.getString("systemInstruction", "");
        JSONArray tools = call.getArray("tools");

        if (apiKey == null || apiKey.isEmpty()) {
            call.reject("API Key is required");
            return;
        }

        try {
            geminiClient = new GeminiLiveClient(new GeminiLiveClient.Listener() {
                @Override
                public void onConnected() {
                    Log.d(TAG, "Connected to Gemini");
                    JSObject result = new JSObject();
                    result.put("status", "connected");
                    notifyListeners("geminiStatus", result);
                }

                @Override
                public void onDisconnected() {
                    Log.d(TAG, "Disconnected from Gemini");
                    JSObject result = new JSObject();
                    result.put("status", "disconnected");
                    notifyListeners("geminiStatus", result);
                }

                @Override
                public void onError(Throwable t) {
                    Log.e(TAG, "Gemini error: " + t.getMessage());
                    JSObject result = new JSObject();
                    result.put("error", t.getMessage());
                    notifyListeners("geminiError", result);
                }

                @Override
                public void onMessage(String message) {
                    audioExecutor.execute(() -> {
                        try {
                            byte[] audioData = message.getBytes();
                            processAudioChunk(audioData);
                        } catch (Exception e) {
                            Log.e(TAG, "Audio processing error: " + e.getMessage());
                        }
                    });
                }
            });

            geminiClient.setApiKey(apiKey);

            JSObject result = new JSObject();
            result.put("success", true);
            call.resolve(result);

        } catch (Exception e) {
            Log.e(TAG, "Init error: " + e.getMessage());
            call.reject(e.getMessage());
        }
    }

    private void processAudioChunk(byte[] audioData) {
        audioBuffer.add(audioData);

        if (audioBuffer.size() >= BUFFER_THRESHOLD) {
            int totalSize = 0;
            for (byte[] chunk : audioBuffer) {
                totalSize += chunk.length;
            }

            byte[] merged = new byte[totalSize];
            int offset = 0;
            for (byte[] chunk : audioBuffer) {
                System.arraycopy(chunk, 0, merged, offset, chunk.length);
                offset += chunk.length;
            }

            if (geminiClient != null) {
                geminiClient.playAudio(merged);
            }
            audioBuffer.clear();
        }
    }

    @PluginMethod
    public void startGemini(PluginCall call) {
        try {
            if (geminiClient != null) {
                geminiClient.start();
            }
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void stopGemini(PluginCall call) {
        try {
            if (geminiClient != null) {
                geminiClient.stop();
            }
            audioBuffer.clear();
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void sendMessage(PluginCall call) {
        String message = call.getString("message");
        try {
            if (geminiClient != null) {
                geminiClient.sendMessage(message);
            }
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void sendText(PluginCall call) {
        String text = call.getString("text", "");
        try {
            if (geminiClient != null) {
                geminiClient.sendMessage(text);
            }
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void sendAudioChunk(PluginCall call) {
        String base64 = call.getString("base64", "");
        try {
            if (geminiClient != null) {
                // Audio chunk send karo agar GeminiLiveClient mein method hai
            }
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void sendVideoFrame(PluginCall call) {
        String base64 = call.getString("base64", "");
        try {
            if (geminiClient != null) {
                // Video frame send karo agar GeminiLiveClient mein method hai
            }
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void sendToolResponse(PluginCall call) {
        String id = call.getString("id");
        String name = call.getString("name");
        Object output = call.getObject("output");
        try {
            if (geminiClient != null) {
                // Tool response send karo agar GeminiLiveClient mein method hai
            }
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void close(PluginCall call) {
        try {
            if (geminiClient != null) {
                geminiClient.stop();
            }
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }
}