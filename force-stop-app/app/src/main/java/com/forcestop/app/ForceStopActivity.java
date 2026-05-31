package com.forcestop.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import rikka.shizuku.Shizuku;

public class ForceStopActivity extends Activity {

    private static final int SHIZUKU_CODE = 100;

    private final Shizuku.OnRequestPermissionResultListener permListener = (requestCode, grantResult) -> {
        if (requestCode == SHIZUKU_CODE && grantResult == PackageManager.PERMISSION_GRANTED) {
            detectAndAct();
        } else {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Shizuku.addRequestPermissionResultListener(permListener);

        if (!Shizuku.pingBinder()) {
            ForceStopHelper.showToast(this, "Shizuku not running");
            finish();
            return;
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            detectAndAct();
        } else {
            Shizuku.requestPermission(SHIZUKU_CODE);
        }
    }

    private void detectAndAct() {
        new Thread(() -> {
            String pkg = ForceStopHelper.findForegroundPackage();
            runOnUiThread(() -> {
                if (pkg == null) {
                    ForceStopHelper.showToast(this, "No foreground app found");
                    finish();
                    return;
                }

                SharedPreferences prefs = getSharedPreferences(
                        SettingsActivity.PREFS_NAME, MODE_PRIVATE);
                boolean confirm = prefs.getBoolean(SettingsActivity.KEY_CONFIRM, true);

                if (confirm) {
                    showConfirmDialog(pkg, prefs);
                } else {
                    new Thread(() -> {
                        ForceStopHelper.forceStop(this, pkg);
                        runOnUiThread(this::finish);
                    }).start();
                }
            });
        }).start();
    }

    private void showConfirmDialog(String pkg, SharedPreferences prefs) {
        String appLabel = ForceStopHelper.getAppLabel(this, pkg);
        String position = prefs.getString(SettingsActivity.KEY_POSITION, "center");

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.DialogTheme)
                .setIcon(R.drawable.ic_dialog_close)
                .setTitle("Force Stop")
                .setMessage("Stop \"" + appLabel + "\"?")
                .setPositiveButton("Stop", (d, which) -> {
                    new Thread(() -> {
                        ForceStopHelper.forceStop(this, pkg);
                        runOnUiThread(this::finish);
                    }).start();
                })
                .setNegativeButton("Cancel", (d, which) -> finish())
                .setOnCancelListener(d -> finish())
                .create();

        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            int gravity;
            switch (position) {
                case "top": gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL; break;
                case "bottom": gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL; break;
                default: gravity = Gravity.CENTER; break;
            }
            window.setGravity(gravity);
            WindowManager.LayoutParams lp = window.getAttributes();
            if ("top".equals(position)) lp.y = 100;
            else if ("bottom".equals(position)) lp.y = 100;
            else lp.y = 0;
            window.setAttributes(lp);
        }

        Button stopBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (stopBtn != null) stopBtn.setTextColor(Color.parseColor("#E53935"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Shizuku.removeRequestPermissionResultListener(permListener);
    }
}
