package com.forcestop.app;

import android.content.Context;
import android.os.IBinder;
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

    public static void forceStopForeground(Context context) {
        if (!Shizuku.pingBinder()) {
            showToast(context, "Shizuku not running");
            return;
        }

        if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            showToast(context, "Shizuku permission not granted");
            return;
        }

        try {
            String pkg = detectForegroundPackage();
            if (pkg == null || pkg.isEmpty()) {
                showToast(context, "No foreground app found");
                return;
            }

            if (SKIP_PACKAGES.contains(pkg) || pkg.startsWith("com.android.launcher")) {
                showToast(context, "Won't stop: " + pkg);
                return;
            }

            forceStopViaAm(pkg);
            showToast(context, "Stopped: " + pkg);
        } catch (Exception e) {
            showToast(context, "Error: " + e.getMessage());
        }
    }

    private static void forceStopViaAm(String pkg) throws Exception {
        try {
            // Try IActivityManager.forceStopPackage via ShizukuBinderWrapper first
            Class<?> iamClass = Class.forName("android.app.IActivityManager");
            Class<?> iamStub = Class.forName("android.app.IActivityManager$Stub");
            Method asInterface = iamStub.getMethod("asInterface", IBinder.class);
            Object iam = asInterface.invoke(null,
                    new ShizukuBinderWrapper(SystemServiceHelper.getSystemService("activity")));
            Method forceStop = iamClass.getMethod("forceStopPackage", String.class, int.class);
            forceStop.invoke(iam, pkg, 0);
        } catch (Exception e) {
            // Fallback to shell command
            execShell("am force-stop " + pkg);
        }
    }

    private static String detectForegroundPackage() throws Exception {
        // Method 1: mCurrentFocus from window manager (most reliable)
        String pkg = fromCurrentFocus();
        if (pkg != null) return pkg;

        // Method 2: mFocusedApp from window manager
        pkg = fromFocusedApp();
        if (pkg != null) return pkg;

        // Method 3: mResumedActivity from activity manager
        pkg = fromResumedActivity();
        if (pkg != null) return pkg;

        // Method 4: recent tasks (our activity is excluded from recents)
        pkg = fromRecentTasks();
        return pkg;
    }

    private static String fromCurrentFocus() throws Exception {
        String output = execShell("dumpsys window displays");
        // mCurrentFocus=Window{abc u0 com.example.app/com.example.app.MainActivity}
        Pattern p = Pattern.compile("mCurrentFocus=Window\\{[^}]*\\s([\\w.]+)/");
        Matcher m = p.matcher(output);
        while (m.find()) {
            String found = m.group(1);
            if (!OWN_PACKAGE.equals(found)) return found;
        }
        // Alt: mCurrentFocus=Window{abc u0 com.example.app}
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
        // Use reflection since Shizuku.newProcess is @RestrictTo
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

    private static void showToast(Context context, String msg) {
        try {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }
}
