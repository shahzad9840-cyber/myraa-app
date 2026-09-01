import os
import glob

candidates = glob.glob("android/app/src/main/java/**/MainActivity.*", recursive=True)
if not candidates:
    raise SystemExit("MainActivity not found - check android/app/src/main/java path")

path = candidates[0]
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

is_kotlin = path.endswith(".kt")

if is_kotlin:
    if "class MainActivity" in content and "onCreate" not in content:
        content = content.replace(
            "class MainActivity : BridgeActivity() {}",
            (
                "class MainActivity : BridgeActivity() {\n"
                "    override fun onCreate(savedInstanceState: android.os.Bundle?) {\n"
                "        registerPlugin(ai.myraa.mobile.plugins.ScreenSharePlugin::class.java)\n"
                "        registerPlugin(ai.myraa.mobile.plugins.DeviceControlPlugin::class.java)\n"
                "        super.onCreate(savedInstanceState)\n"
                "    }\n"
                "}\n"
            ),
        )
else:
    if "class MainActivity" in content and "onCreate" not in content:
        content = content.replace(
            "public class MainActivity extends BridgeActivity {}",
            (
                "public class MainActivity extends BridgeActivity {\n"
                "    private static final int RECORD_AUDIO_REQUEST_CODE = 9001;\n"
                "    private android.webkit.PermissionRequest pendingWebPermissionRequest;\n"
                "\n"
                "    @Override\n"
                "    public void onCreate(android.os.Bundle savedInstanceState) {\n"
                "        registerPlugin(ai.myraa.mobile.plugins.ScreenSharePlugin.class);\n"
                "        registerPlugin(ai.myraa.mobile.plugins.DeviceControlPlugin.class);\n"
                "        registerPlugin(ai.myraa.mobile.plugins.MyraaNativePlugin.class);\n"
                "        super.onCreate(savedInstanceState);\n"
                "        requestMicUpfront();\n"
                "    }\n"
                "\n"
                "    @Override\n"
                "    public void onResume() {\n"
                "        super.onResume();\n"
                "        // Re-assert our WebChromeClient every resume, in case anything\n"
                "        // else in the WebView setup replaces it after onCreate.\n"
                "        setupWebMediaPermissions();\n"
                "    }\n"
                "\n"
                "    private void requestMicUpfront() {\n"
                "        boolean alreadyGranted = androidx.core.content.ContextCompat.checkSelfPermission(\n"
                "            this, android.Manifest.permission.RECORD_AUDIO)\n"
                "            == android.content.pm.PackageManager.PERMISSION_GRANTED;\n"
                "        if (!alreadyGranted) {\n"
                "            androidx.core.app.ActivityCompat.requestPermissions(\n"
                "                this,\n"
                "                new String[]{ android.Manifest.permission.RECORD_AUDIO },\n"
                "                RECORD_AUDIO_REQUEST_CODE\n"
                "            );\n"
                "        }\n"
                "    }\n"
                "\n"
                "    private void setupWebMediaPermissions() {\n"
                "        final android.webkit.WebView webView = getBridge().getWebView();\n"
                "        webView.setWebChromeClient(new android.webkit.WebChromeClient() {\n"
                "            @Override\n"
                "            public void onPermissionRequest(final android.webkit.PermissionRequest request) {\n"
                "                runOnUiThread(() -> {\n"
                "                    boolean wantsAudio = false;\n"
                "                    for (String resource : request.getResources()) {\n"
                "                        if (android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {\n"
                "                            wantsAudio = true;\n"
                "                        }\n"
                "                    }\n"
                "                    if (!wantsAudio) {\n"
                "                        request.deny();\n"
                "                        return;\n"
                "                    }\n"
                "                    boolean alreadyGranted = androidx.core.content.ContextCompat.checkSelfPermission(\n"
                "                        MainActivity.this, android.Manifest.permission.RECORD_AUDIO)\n"
                "                        == android.content.pm.PackageManager.PERMISSION_GRANTED;\n"
                "                    if (alreadyGranted) {\n"
                "                        request.grant(new String[]{ android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE });\n"
                "                    } else {\n"
                "                        pendingWebPermissionRequest = request;\n"
                "                        androidx.core.app.ActivityCompat.requestPermissions(\n"
                "                            MainActivity.this,\n"
                "                            new String[]{ android.Manifest.permission.RECORD_AUDIO },\n"
                "                            RECORD_AUDIO_REQUEST_CODE\n"
                "                        );\n"
                "                    }\n"
                "                });\n"
                "            }\n"
                "        });\n"
                "    }\n"
                "\n"
                "    @Override\n"
                "    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {\n"
                "        super.onRequestPermissionsResult(requestCode, permissions, grantResults);\n"
                "        if (requestCode == RECORD_AUDIO_REQUEST_CODE && pendingWebPermissionRequest != null) {\n"
                "            boolean granted = grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;\n"
                "            if (granted) {\n"
                "                pendingWebPermissionRequest.grant(new String[]{ android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE });\n"
                "            } else {\n"
                "                pendingWebPermissionRequest.deny();\n"
                "            }\n"
                "            pendingWebPermissionRequest = null;\n"
                "        }\n"
                "    }\n"
                "}\n"
            ),
        )

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print(f"Patched {path}")
                
