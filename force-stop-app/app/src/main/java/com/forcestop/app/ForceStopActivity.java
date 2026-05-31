package com.forcestop.app;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;

import rikka.shizuku.Shizuku;

public class ForceStopActivity extends Activity {

    private static final int SHIZUKU_CODE = 100;

    private final Shizuku.OnRequestPermissionResultListener permListener = (requestCode, grantResult) -> {
        if (requestCode == SHIZUKU_CODE && grantResult == PackageManager.PERMISSION_GRANTED) {
            doForceStop();
        } else {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Shizuku.addRequestPermissionResultListener(permListener);

        if (!Shizuku.pingBinder()) {
            android.widget.Toast.makeText(this, "Shizuku not running", android.widget.Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            doForceStop();
        } else {
            Shizuku.requestPermission(SHIZUKU_CODE);
        }
    }

    private void doForceStop() {
        new Thread(() -> {
            ForceStopHelper.forceStopForeground(ForceStopActivity.this);
            runOnUiThread(this::finish);
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Shizuku.removeRequestPermissionResultListener(permListener);
    }
}
