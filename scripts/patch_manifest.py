import re

path = "android/app/src/main/AndroidManifest.xml"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

permissions = (
    '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />\n'
    '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />\n'
    '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />\n'
    '    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n'
    '    <uses-permission android:name="android.permission.RECORD_AUDIO" />\n'
    '    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />\n'
    '    <uses-permission android:name="android.permission.SEND_SMS" />\n'
    '    <uses-permission android:name="android.permission.CALL_PHONE" />\n'
)

service = (
    '        <service\n'
    '            android:name="ai.myraa.mobile.plugins.DeviceControlAccessibilityService"\n'
    '            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"\n'
    '            android:exported="false">\n'
    '            <intent-filter>\n'
    '                <action android:name="android.accessibilityservice.AccessibilityService" />\n'
    '            </intent-filter>\n'
    '            <meta-data\n'
    '                android:name="android.accessibilityservice"\n'
    '                android:resource="@xml/accessibility_service_config" />\n'
    '        </service>\n'
    '        <service\n'
    '            android:name="ai.myraa.mobile.plugins.MyraaForegroundService"\n'
    '            android:exported="false"\n'
    '            android:foregroundServiceType="mediaProjection|microphone" />\n'
)

# Insert permissions right before <application
content = content.replace("<application", permissions + "\n    <application", 1)

# Insert service block right before closing </application>
content = content.replace("</application>", service + "    </application>", 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("AndroidManifest.xml patched.")
