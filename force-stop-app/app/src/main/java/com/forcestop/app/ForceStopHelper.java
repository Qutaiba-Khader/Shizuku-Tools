package com.forcestop.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

public class ForceStopHelper {

    private static final String OWN_PACKAGE = "com.forcestop.app";

    private static final Set<String> SKIP_PACKAGES = new HashSet<>(Arrays.asList(
            OWN_PACKAGE,
            "com.android.launcher",
            "com.android.launcher2",
            "com.android.launcher3",
            "com.sec.android.app.launcher",
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher",
            "com.android.systemui",
            "com.android.settings",
            "com.samsung.android.settings",
            "moe.shizuku.privileged.api"
    ));

    public static String findForegroundPackage() {
        if (!Shizuku.pingBinder()) return null;
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return null;

        try {
            String pkg = detectForegroundPackage();
            if (pkg == null || pkg.isEmpty()) return null;
            if (SKIP_PACKAGES.contains(pkg) || pkg.startsWith("com.android.launcher")) return null;
            return pkg;
        } catch (Exception e) {
            return null;
        }
    }

    public static String getAppLabel(Context context, String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getApplicationLabel(
                    pm.getApplicationInfo(packageName, 0)).toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    public static void forceStop(Context context, String packageName) {
        try {
            forceStopViaAm(packageName);

            SharedPreferences prefs = context.getSharedPreferences(
                    SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);

            if (prefs.getBoolean(SettingsActivity.KEY_TOAST, true)) {
                showToast(context, "Stopped: " + getAppLabel(context, packageName));
            }

            if (prefs.getBoolean(SettingsActivity.KEY_VIBRATE, true)) {
                vibrate(context);
            }
        } catch (Exception e) {
            showToast(context, "Error: " + e.getMessage());
        }
    }

    private static void vibrate(Context context) {
        try {
            Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } catch (Exception ignored) {
        }
    }

    private static void forceStopViaAm(String pkg) throws Exception {
        try {
            Class<?> iamClass = Class.forName("android.app.IActivityManager");
            Class<?> iamStub = Class.forName("android.app.IActivityManager$Stub");
            Method asInterface = iamStub.getMethod("asInterface", IBinder.class);
            Object iam = asInterface.invoke(null,
                    new ShizukuBinderWrapper(SystemServiceHelper.getSystemService("activity")));
            Method forceStop = iamClass.getMethod("forceStopPackage", String.class, int.class);
            forceStop.invoke(iam, pkg, 0);
        } catch (Exception e) {
            execShell("am force-stop " + pkg);
        }
    }

    private static String detectForegroundPackage() throws Exception {
        String pkg = fromCurrentFocus();
        if (pkg != null) return pkg;

        pkg = fromFocusedApp();
        if (pkg != null) return pkg;

        pkg = fromResumedActivity();
        if (pkg != null) return pkg;

        return fromRecentTasks();
    }

    private static String fromCurrentFocus() throws Exception {
        String output = execShell("dumpsys window displays");
        Pattern p = Pattern.compile("mCurrentFocus=Window\\{[^}]*\\s([\\w.]+)/");
        Matcher m = p.matcher(output);
        while (m.find()) {
            String found = m.group(1);
            if (!OWN_PACKAGE.equals(found)) return found;
        }
        p = Pattern.compile("mCurrentFocus=Window\\{[^}]*\\s([\\w.]+)\\}");
        m = p.matcher(output);
        while (m.find()) {
            String found = m.group(1);
            if (!OWN_PACKAGE.equals(found) && found.contains(".")) return found;
        }
        return null;
    }

    private static String fromFocusedApp() throws Exception {
        String output = execShell("dumpsys window displays");
        Pattern p = Pattern.compile("mFocusedApp=.*?\\s([\\w.]+)/");
        Matcher m = p.matcher(output);
        while (m.find()) {
            String found = m.group(1);
            if (!OWN_PACKAGE.equals(found)) return found;
        }
        return null;
    }

    private static String fromResumedActivity() throws Exception {
        String output = execShell("dumpsys activity activities");
        Pattern p = Pattern.compile("(?:mResumedActivity|topResumedActivity).*?\\{[^}]*\\s([\\w.]+)/");
        Matcher m = p.matcher(output);
        while (m.find()) {
            String found = m.group(1);
            if (!OWN_PACKAGE.equals(found)) return found;
        }
        return null;
    }

    private static String fromRecentTasks() throws Exception {
        String output = execShell("dumpsys activity recents");
        Pattern p = Pattern.compile("Recent #\\d+:.*?A=\\d+:([\\w.]+)");
        Matcher m = p.matcher(output);
        while (m.find()) {
            String found = m.group(1);
            if (!OWN_PACKAGE.equals(found)) return found;
        }
        p = Pattern.compile("baseActivity=ComponentInfo\\{([\\w.]+)/");
        m = p.matcher(output);
        while (m.find()) {
            String found = m.group(1);
            if (!OWN_PACKAGE.equals(found)) return found;
        }
        return null;
    }

    private static String execShell(String command) throws Exception {
        Method newProcess = Shizuku.class.getDeclaredMethod("newProcess",
                String[].class, String[].class, String.class);
        newProcess.setAccessible(true);
        Process process = (Process) newProcess.invoke(null,
                new String[]{"sh", "-c", command}, null, null);
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        process.waitFor();
        reader.close();
        return sb.toString();
    }

    static void showToast(Context context, String msg) {
        try {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }
}
