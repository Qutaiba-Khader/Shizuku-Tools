package com.forcestop.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

import rikka.shizuku.Shizuku;

public class SettingsActivity extends Activity {

    public static final String PREFS_NAME = "forcestop_prefs";
    public static final String KEY_CONFIRM = "confirm_enabled";
    public static final String KEY_POSITION = "dialog_position";
    public static final String KEY_TOAST = "toast_enabled";
    public static final String KEY_VIBRATE = "vibrate_enabled";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        setupShizukuStatus();
        setupConfirmToggle(prefs);
        setupToastToggle(prefs);
        setupVibrateToggle(prefs);
        setupPositionRadio(prefs);
        setupVersion();
    }

    private void setupShizukuStatus() {
        View dot = findViewById(R.id.status_dot);
        TextView status = findViewById(R.id.text_shizuku_status);

        boolean running = false;
        boolean granted = false;
        try {
            running = Shizuku.pingBinder();
            if (running) {
                granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            }
        } catch (Exception ignored) {
        }

        if (running && granted) {
            dot.setBackgroundResource(R.drawable.status_dot_green);
            status.setText("Running • Permission granted");
        } else if (running) {
            dot.setBackgroundResource(R.drawable.status_dot_red);
            status.setText("Running • Permission not granted");
        } else {
            dot.setBackgroundResource(R.drawable.status_dot_red);
            status.setText("Not running");
        }
    }

    private void setupConfirmToggle(SharedPreferences prefs) {
        Switch sw = findViewById(R.id.switch_confirm);
        View posSection = findViewById(R.id.position_section);
        View dialogLabel = findViewById(R.id.label_dialog);

        boolean enabled = prefs.getBoolean(KEY_CONFIRM, true);
        sw.setChecked(enabled);
        setDialogSectionEnabled(posSection, dialogLabel, enabled);

        sw.setOnCheckedChangeListener((v, checked) -> {
            prefs.edit().putBoolean(KEY_CONFIRM, checked).apply();
            setDialogSectionEnabled(posSection, dialogLabel, checked);
        });
    }

    private void setDialogSectionEnabled(View posSection, View dialogLabel, boolean enabled) {
        float alpha = enabled ? 1f : 0.35f;
        posSection.setAlpha(alpha);
        dialogLabel.setAlpha(alpha);
        RadioGroup rg = posSection.findViewById(R.id.radio_position);
        for (int i = 0; i < rg.getChildCount(); i++) {
            rg.getChildAt(i).setEnabled(enabled);
        }
    }

    private void setupToastToggle(SharedPreferences prefs) {
        Switch sw = findViewById(R.id.switch_toast);
        sw.setChecked(prefs.getBoolean(KEY_TOAST, true));
        sw.setOnCheckedChangeListener((v, checked) ->
                prefs.edit().putBoolean(KEY_TOAST, checked).apply());
    }

    private void setupVibrateToggle(SharedPreferences prefs) {
        Switch sw = findViewById(R.id.switch_vibrate);
        sw.setChecked(prefs.getBoolean(KEY_VIBRATE, true));
        sw.setOnCheckedChangeListener((v, checked) ->
                prefs.edit().putBoolean(KEY_VIBRATE, checked).apply());
    }

    private void setupPositionRadio(SharedPreferences prefs) {
        RadioGroup rg = findViewById(R.id.radio_position);
        String pos = prefs.getString(KEY_POSITION, "center");
        switch (pos) {
            case "top": rg.check(R.id.radio_top); break;
            case "bottom": rg.check(R.id.radio_bottom); break;
            default: rg.check(R.id.radio_center); break;
        }
        rg.setOnCheckedChangeListener((group, checkedId) -> {
            String p = "center";
            if (checkedId == R.id.radio_top) p = "top";
            else if (checkedId == R.id.radio_bottom) p = "bottom";
            prefs.edit().putString(KEY_POSITION, p).apply();
        });
    }

    private void setupVersion() {
        TextView tv = findViewById(R.id.text_version);
        try {
            String ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            tv.setText("Version " + ver);
        } catch (Exception ignored) {
        }
    }
}
