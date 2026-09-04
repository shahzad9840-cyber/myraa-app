package ai.myraa.mobile.plugins;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A single screen listing every permission MYRAA can use, with live
 * granted/not-granted status and a one-tap grant button for each.
 * Standard runtime permissions show the normal system dialog; the
 * Accessibility row opens its dedicated Settings screen instead, since
 * Android does not allow that one to be granted via a permission
 * dialog at all.
 *
 * Reachable via the androidAction OPEN_PERMISSIONS_SCREEN, so the
 * user can just ask MYRAA to open it by voice.
 */
public class PermissionsActivity extends Activity {

    private static class PermRow {
        String label;
        String description;
        String manifestPermission;      // null for special-access rows
        int requestCode;
        Runnable specialAccessLauncher; // non-null for special-access rows
        BooleanSupplier specialAccessChecker;

        TextView statusView;
        Button actionButton;
    }

    private final List<PermRow> rows = new ArrayList<>();
    private LinearLayout container;
    private int nextRequestCode = 9001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildLayout());
        buildRows();
        renderRows();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Covers returning from the Accessibility Settings screen, and
        // catches any runtime permission result too.
        refreshStatuses();
    }

    // ---------------- UI construction (built in code - no layout XML needed) ----------------

    private View buildLayout() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0D0D14"));

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        outer.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("MYRAA Permissions");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        outer.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Grant everything MYRAA needs to work fully, in one place.");
        subtitle.setTextColor(Color.parseColor("#AAAAAA"));
        subtitle.setTextSize(13);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.bottomMargin = dp(20);
        subtitleParams.topMargin = dp(4);
        outer.addView(subtitle, subtitleParams);

        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        outer.addView(container);

        scroll.addView(outer);
        return scroll;
    }

    private void buildRows() {
        addRuntimeRow("Microphone", "Voice conversation with MYRAA.", Manifest.permission.RECORD_AUDIO);
        addRuntimeRow("Camera", "Live camera view and photo capture.", Manifest.permission.CAMERA);
        addRuntimeRow("Location", "Maps, navigation, and location-aware answers.", Manifest.permission.ACCESS_FINE_LOCATION);
        addRuntimeRow("SMS", "Sending text messages directly.", Manifest.permission.SEND_SMS);
        addRuntimeRow("Phone calls", "Placing calls directly.", Manifest.permission.CALL_PHONE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addRuntimeRow("Notifications", "Showing MYRAA's own status notifications.", Manifest.permission.POST_NOTIFICATIONS);
        }
        addSpecialRow(
            "Accessibility",
            "Lets MYRAA read the screen and tap/scroll on your behalf.",
            () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
            () -> DeviceControlAccessibilityService.instance != null
        );
    }

    private void addRuntimeRow(String label, String description, String manifestPermission) {
        PermRow row = new PermRow();
        row.label = label;
        row.description = description;
        row.manifestPermission = manifestPermission;
        row.requestCode = nextRequestCode++;
        rows.add(row);
    }

    private void addSpecialRow(String label, String description, Runnable launcher, BooleanSupplier checker) {
        PermRow row = new PermRow();
        row.label = label;
        row.description = description;
        row.specialAccessLauncher = launcher;
        row.specialAccessChecker = checker;
        rows.add(row);
    }

    private void renderRows() {
        container.removeAllViews();
        for (PermRow row : rows) {
            container.addView(buildRowView(row));
        }
    }

    private View buildRowView(PermRow row) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundColor(Color.parseColor("#1A1A24"));
        int pad = dp(14);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(10);
        card.setLayoutParams(cardParams);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textColParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(textColParams);

        TextView label = new TextView(this);
        label.setText(row.label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(15);
        textCol.addView(label);

        TextView desc = new TextView(this);
        desc.setText(row.description);
        desc.setTextColor(Color.parseColor("#888888"));
        desc.setTextSize(11);
        textCol.addView(desc);

        TextView status = new TextView(this);
        status.setTextSize(11);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(2);
        textCol.addView(status, statusParams);
        row.statusView = status;

        card.addView(textCol);

        Button action = new Button(this);
        action.setAllCaps(false);
        action.setTextSize(12);
        action.setOnClickListener(v -> onRowTapped(row));
        card.addView(action);
        row.actionButton = action;

        return card;
    }

    // ---------------- Status + actions ----------------

    private boolean isGranted(PermRow row) {
        if (row.specialAccessChecker != null) return row.specialAccessChecker.getAsBoolean();
        return ContextCompat.checkSelfPermission(this, row.manifestPermission) == PackageManager.PERMISSION_GRANTED;
    }

    private void refreshStatuses() {
        for (PermRow row : rows) {
            boolean granted = isGranted(row);
            row.statusView.setText(granted ? "Granted" : "Not granted");
            row.statusView.setTextColor(granted ? Color.parseColor("#4CD97B") : Color.parseColor("#E0637A"));
            row.actionButton.setText(granted ? "Granted" : "Grant");
            row.actionButton.setEnabled(!granted);
        }
    }

    private void onRowTapped(PermRow row) {
        if (row.specialAccessLauncher != null) {
            row.specialAccessLauncher.run();
            return;
        }
        ActivityCompat.requestPermissions(this, new String[]{row.manifestPermission}, row.requestCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshStatuses();
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
