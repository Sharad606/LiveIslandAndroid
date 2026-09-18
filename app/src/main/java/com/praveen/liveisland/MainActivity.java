package com.praveen.liveisland;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int REQ_QR = 1201;
    private static final int REQ_FILE = 1202;
    private TextView status;
    private IslandPreferences prefs;
    private final Shizuku.OnRequestPermissionResultListener shizukuListener = (requestCode, grantResult) -> runOnUiThread(this::refreshStatus);

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new IslandPreferences(this);
        try { rikka.shizuku.Shizuku.addRequestPermissionResultListener(shizukuListener); } catch (Throwable ignored) {}
        setContentView(buildUi());
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 77);
        }
        ShizukuBridge.ensureBound(this, null);
        refreshStatus();
    }

    @Override protected void onDestroy() {
        try { rikka.shizuku.Shizuku.removeRequestPermissionResultListener(shizukuListener); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    @Override protected void onResume() { super.onResume(); refreshStatus(); }

    private View buildUi() {
        ScrollView scroller = new ScrollView(this);
        scroller.setBackgroundColor(Color.rgb(9,9,11));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(40));
        scroller.addView(root);

        TextView title = text("LIVE ISLAND", 30, true);
        root.addView(title);
        TextView sub = text("Camera-cutout anchored live activities for Android", 15, false);
        sub.setTextColor(Color.LTGRAY); sub.setPadding(0, dp(4), 0, dp(20)); root.addView(sub);

        status = text("Checking permissions…", 14, false);
        status.setPadding(dp(14), dp(12), dp(14), dp(12));
        status.setBackgroundColor(Color.rgb(24,24,27));
        root.addView(status, matchWrap());

        root.addView(button("1 · Allow display over other apps", v -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())); startActivity(i);
        }));
        root.addView(button("2 · Enable notification access", v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));
        root.addView(button("3 · Authorize Shizuku", v -> {
            if (!ShizukuBridge.available()) Toast.makeText(this, "Start Shizuku with Wireless debugging first.", Toast.LENGTH_LONG).show();
            else { ShizukuBridge.requestPermission(); ShizukuBridge.ensureBound(this, () -> runOnUiThread(this::refreshStatus)); }
        }));

        root.addView(button(prefs.enabled() ? "Stop Live Island" : "Start Live Island", v -> {
            if (prefs.enabled()) {
                prefs.setEnabled(false);
                stopService(new Intent(this, IslandService.class));
            } else {
                if (!Settings.canDrawOverlays(this)) { Toast.makeText(this, "Overlay permission is required.", Toast.LENGTH_LONG).show(); return; }
                prefs.setEnabled(true);
                IslandService.start(this);
            }
            recreate();
        }));

        section(root, "BANK QR");
        root.addView(button("Choose bank / UPI QR image", v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i, REQ_QR);
        }));
        CheckBox bio = new CheckBox(this); bio.setText("Require biometric before showing QR"); bio.setTextColor(Color.WHITE); bio.setChecked(prefs.qrBiometric());
        bio.setOnCheckedChangeListener((b,c) -> prefs.setQrBiometric(c)); root.addView(bio);

        section(root, "LIVE FEATURES");
        EditText timerMinutes = new EditText(this); timerMinutes.setHint("Timer minutes"); timerMinutes.setHintTextColor(Color.GRAY); timerMinutes.setTextColor(Color.WHITE);
        timerMinutes.setInputType(InputType.TYPE_CLASS_NUMBER); timerMinutes.setText("5"); root.addView(timerMinutes, matchWrap());
        root.addView(button("Start timer in island", v -> {
            int mins = 5; try { mins = Math.max(1, Integer.parseInt(timerMinutes.getText().toString())); } catch (Exception ignored) {}
            Intent i = new Intent(this, IslandService.class).setAction(IslandService.ACTION_START_TIMER).putExtra("seconds", mins * 60L);
            startForegroundServiceCompat(i);
        }));
        root.addView(button("Send a file via Bluetooth", v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i, REQ_FILE);
        }));
        root.addView(button("Open NFC / Tap animation", v -> startActivity(new Intent(this, NfcTapActivity.class))));
        root.addView(button("Authenticate with biometrics", v -> BiometricHelper.authenticate(this, "Live Island", "Authenticate", ok -> {
            IslandService.pushEvent(this, new IslandEvent(IslandEvent.Type.BIOMETRIC, ok ? "Authenticated" : "Authentication failed", ok ? "Success" : "Try again"));
        })));

        section(root, "CAMERA ALIGNMENT");
        TextView note = text("The physical display cutout is the anchor in portrait and landscape. Offsets are only for OEM fine-tuning.", 13, false);
        note.setTextColor(Color.GRAY); root.addView(note);
        LinearLayout offset = new LinearLayout(this); offset.setOrientation(LinearLayout.HORIZONTAL); offset.setGravity(Gravity.CENTER);
        Button xm = small("X −", v -> changeOffset(-1,0)); Button xp = small("X +", v -> changeOffset(1,0));
        Button ym = small("Y −", v -> changeOffset(0,-1)); Button yp = small("Y +", v -> changeOffset(0,1));
        offset.addView(xm); offset.addView(xp); offset.addView(ym); offset.addView(yp); root.addView(offset);
        root.addView(button("Reset camera alignment", v -> { prefs.setOffsets(0,0); notifyGeometry(); Toast.makeText(this,"Alignment reset",Toast.LENGTH_SHORT).show(); }));

        TextView foot = text("Music, calls, navigation, alarms, messages, downloads and Bluetooth transfer progress are driven by real Android notifications/media sessions. Status-bar icon suppression uses Shizuku and is restored when the island collapses.", 12, false);
        foot.setTextColor(Color.GRAY); foot.setPadding(0, dp(24), 0, 0); root.addView(foot);
        return scroller;
    }

    private void changeOffset(int dx, int dy) { prefs.setOffsets(prefs.xOffsetDp()+dx, prefs.yOffsetDp()+dy); notifyGeometry(); }
    private void notifyGeometry() { startForegroundServiceCompat(new Intent(this, IslandService.class).setAction(IslandService.ACTION_REPOSITION)); }

    private void refreshStatus() {
        if (status == null) return;
        boolean overlay = Settings.canDrawOverlays(this);
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        boolean notif = enabled != null && enabled.contains(getPackageName());
        String sh = !ShizukuBridge.available() ? "not running" : (ShizukuBridge.granted() ? "authorized" : "permission needed");
        status.setText("Overlay: " + (overlay ? "ready" : "permission needed") + "\nNotification listener: " + (notif ? "ready" : "permission needed") + "\nShizuku: " + sh + "\nService: " + (prefs.enabled() ? "enabled" : "stopped"));
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Throwable ignored) {}
        if (requestCode == REQ_QR) {
            prefs.setQrUri(uri.toString()); Toast.makeText(this, "QR saved. Tap the empty island to show it.", Toast.LENGTH_LONG).show();
        } else if (requestCode == REQ_FILE) {
            Intent share = new Intent(Intent.ACTION_SEND); share.setType(getContentResolver().getType(uri) == null ? "*/*" : getContentResolver().getType(uri));
            share.putExtra(Intent.EXTRA_STREAM, uri); share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Send file via Bluetooth"));
        }
    }

    private void startForegroundServiceCompat(Intent i) { if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i); }
    private void section(LinearLayout root, String s) { TextView t = text(s, 12, true); t.setTextColor(Color.GRAY); t.setPadding(0, dp(26), 0, dp(8)); root.addView(t); }
    private TextView text(String s, int sp, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(Color.WHITE); if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); return t; }
    private Button button(String s, View.OnClickListener l) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setOnClickListener(l); LinearLayout.LayoutParams p = matchWrap(); p.topMargin=dp(10); b.setLayoutParams(p); return b; }
    private Button small(String s, View.OnClickListener l) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setOnClickListener(l); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1); p.setMargins(dp(2),dp(4),dp(2),dp(4)); b.setLayoutParams(p); return b; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
