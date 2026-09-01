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
                "    @Override\n"
                "    public void onCreate(android.os.Bundle savedInstanceState) {\n"
                "        registerPlugin(ai.myraa.mobile.plugins.ScreenSharePlugin.class);\n"
                "        registerPlugin(ai.myraa.mobile.plugins.DeviceControlPlugin.class);\n"
                "        registerPlugin(ai.myraa.mobile.plugins.MyraaNativePlugin.class);\n"
                "        super.onCreate(savedInstanceState);\n"
                "    }\n"
                "}\n"
            ),
        )

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print(f"Patched {path}")
