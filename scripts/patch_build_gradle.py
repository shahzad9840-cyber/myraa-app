path = "android/app/build.gradle"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

dependency_line = '    implementation "com.squareup.okhttp3:okhttp:4.12.0"\n'

if "okhttp" not in content:
    # Insert right after the opening of the dependencies { block.
    idx = content.find("dependencies {")
    if idx == -1:
        raise SystemExit("Could not find dependencies block in app/build.gradle")
    insert_at = idx + len("dependencies {")
    content = content[:insert_at] + "\n" + dependency_line + content[insert_at:]

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("app/build.gradle patched with OkHttp dependency.")
