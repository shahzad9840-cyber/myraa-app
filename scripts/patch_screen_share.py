# scripts/patch_screen_share.py

import re

path = "android/app/src/main/java/ai/myraa/mobile/plugins/ScreenSharePlugin.java"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# 🔍 Check if audio is already enabled
if "EXTRA_AUDIO_ENABLED" in content:
    print("✅ Audio already enabled in ScreenSharePlugin.java")
else:
    # Add audio enable flag after createScreenCaptureIntent()
    content = content.replace(
        "intent = projectionManager.createScreenCaptureIntent();",
        "intent = projectionManager.createScreenCaptureIntent();\n        intent.putExtra(MediaProjectionManager.EXTRA_AUDIO_ENABLED, true);"
    )
    print("✅ Added EXTRA_AUDIO_ENABLED to ScreenSharePlugin.java")

# 🔍 Check if audio permission check exists
if "RECORD_AUDIO" in content and "checkSelfPermission" in content:
    print("✅ Audio permission check already exists")
else:
    # Add permission check at start of start() method
    permission_check = '''        // Check audio permission
        if (getActivity().checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            getActivity().requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 1001);
            call.reject("Audio permission required. Please allow microphone access.");
            return;
        }
'''
    # Insert after method declaration
    content = content.replace(
        "public void start(PluginCall call) {",
        "public void start(PluginCall call) {\n" + permission_check
    )
    print("✅ Added audio permission check")

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("✅ ScreenSharePlugin.java patched successfully!")