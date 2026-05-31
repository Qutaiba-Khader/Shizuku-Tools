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
        // Method 1: focused activity from activity manager (most direct on Android 12+)
        String pkg = fromFocusedActivity();
        if (pkg != null) return pkg;

        // Method 2: mFocusedWindow from window manager (reliable on One UI)
        pkg = fromFocusedWindow();
        if (pkg != null) return pkg;

        // Method 3: mCurrentFocus from window manager
        pkg = fromCurrentFocus();
        if (pkg != null) return pkg;

        // Method 4: mFocusedApp from window manager
        pkg = fromFocusedApp();
        if (pkg != null) return pkg;

        // Method 5: topResumedActivity / mResumedActivity
        pkg = fromResumedActivity();
        if (pkg != null) return pkg;

        // Method 6: top running activity via dumpsys activity top
        pkg = fromActivityTop();
        if (pkg != null) return pkg;

        // Method 7: recent tasks (our activity is excluded from recents)
        return fromRecentTasks();
    }

    private static String fromFocusedActivity() throws Exception {
        String output = execShell("dumpsys activity activities | grep -i 'mFocusedActivity\\|mLastFocusedRootTask'");
        return extractPackageFromLine(output);
    }

    private static String fromFocusedWindow() throws Exception {
        String output = execShell("dumpsys window | grep -E 'mFocusedWindow|mInputMethodTarget|mTopFullscreenOpaqueWindowState'");
        return extractPackageFromLine(output);
    }

    private static String fromCurrentFocus() throws Exception {
        String output = execShell("dumpsys window | grep mCurrentFocus");
        return extractPackageFromLine(output);
    }

    private static String fromFocusedApp() throws Exception {
        String output = execShell("dumpsys window | grep mFocusedApp");
        return extractPackageFromLine(output);
    }

    private static String fromResumedActivity() throws Exception {
        String output = execShell("dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity|resumedActivity'");
        return extractPackageFromLine(output);
    }

    private static String fromActivityTop() throws Exception {
        // dumpsys activity top prints "TASK <pkg> id=..." at the start
        String output = execShell("dumpsys activity top | head -5");
        Pattern p = Pattern.compile("TASK\\s+([\\w.]+)");
        Matcher m = p.matcher(output);
        while (m.find()) {
            String found = m.group(1);
            if (isValidTarget(found)) return found;
        }
        return null;
    }

    private static String fromRecentTasks() throws Exception {
        String output = execShell("dumpsys activity recents");
        // Pattern 1: Recent #N: Task{hash #id type=standard A=uid:pkg ...}
        Pattern p = Pattern.compile("A=\\d+:([\\w.]+)");
        Matcher m = p.matcher(output);
        while (m.find()) {
            String found = m.group(1);
            if (isValidTarget(found)) return found;
        }
        // Pattern 2: baseActivity=ComponentInfo{pkg/activity}
        p = Pattern.compile("baseActivity=ComponentInfo\\{([\\w.]+)/");
        m = p.matcher(output);
        while (m.find()) {
            String found = m.group(1);
            if (isValidTarget(found)) return found;
        }
        // Pattern 3: realActivity=pkg/.activity
        p = Pattern.compile("realActivity=([\\w.]+)/");
        m = p.matcher(output);
        while (m.find()) {
            String found = m.group(1);
            if (isValidTarget(found)) return found;
        }
        return null;
    }

    private static String extractPackageFromLine(String output) {
        if (output == null || output.isEmpty()) return null;

        // Try multiple patterns to extract package name from dumpsys output

        // Pattern: pkg/activity (e.g., dev.imranr.obtainium/.pages.home.HomePage)
        Pattern p = Pattern.compile("\\s([\\w.]{3,})/[\\w.]*");
        Matcher m = p.matcher(output);
        while (m.find()) {
            String found = m.group(1);
            if (isValidTarget(found)) return found;
        }

        // Pattern: ActivityRecord{hash u0 pkg/activity tN}
        p = Pattern.compile("ActivityRecord\\{[^}]*?\\s([\\w.]{3,})/");
        m = p.matcher(output);
        while (m.find()) {
            String found = m.group(1);
            if (isValidTarget(found)) return found;
        }

        // Pattern: Window{hash u0 pkg/activity}
        p = Pattern.compile("Window\\{[^}]*?\\s([\\w.]{3,})/");
        m = p.matcher(output);
        while (m.find()) {
            String found = m.group(1);
            if (isValidTarget(found)) return found;
        }

        // Pattern: Window{hash u0 pkg} (without activity, just package)
        p = Pattern.compile("Window\\{[^}]*?\\s([\\w.]{3,})\\}");
        m = p.matcher(output);
        while (m.find()) {
            String found = m.group(1);
            if (isValidTarget(found) && found.contains(".")) return found;
        }

        // Pattern: any word.word.word pattern that looks like a package
        p = Pattern.compile("\\b([a-z][\\w]*\\.[\\w.]*[\\w])\\b/");
        m = p.matcher(output);
        while (m.find()) {
            String found = m.group(1);
            if (isValidTarget(found) && found.chars().filter(c -> c == '.').count() >= 1)
                return found;
        }

        return null;
    }

    private static boolean isValidTarget(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        if (OWN_PACKAGE.equals(pkg)) return false;
        if (!pkg.contains(".")) return false;
        // Skip common system/framework packages that aren't real apps
        if (pkg.equals("android")) return false;
        if (pkg.startsWith("com.android.internal")) return false;
        return true;
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
