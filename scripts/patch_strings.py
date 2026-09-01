path = "android/app/src/main/res/values/strings.xml"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

new_line = (
    '    <string name="accessibility_service_description">'
    'Lets MYRAA see your screen and perform taps, swipes, and typing on your behalf, '
    'only while you have this turned on.</string>\n'
)

content = content.replace("</resources>", new_line + "</resources>")

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("strings.xml patched.")
