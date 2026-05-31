# Force Stop App

A minimal Shizuku-powered Android app that instantly force-stops the current foreground app.

## Features

- **App launcher** — tap to force-stop whatever is in the foreground
- **Home screen shortcut** — long-press the app icon, drag "Force Stop" to home
- **Home screen widget** — 1x1 red X button widget
- **Quick Settings tile** — "Force Stop" tile in the notification pull-down panel
- **Settings** — long-press app icon → "Settings", or via App Info → App Preferences

## Settings

| Setting | Default | Description |
|---------|---------|-------------|
| Show Confirmation | On | Ask before force stopping (off = instant kill) |
| Show Toast | On | Show "Stopped: AppName" notification |
| Vibrate | On | Haptic feedback after stopping |
| Dialog Position | Center | Top / Center / Bottom placement of confirmation dialog |
| Shizuku Status | — | Live indicator showing Shizuku running state and permission |

## How It Works

1. Transparent activity launches (invisible, excluded from recents, no window preview)
2. Detects the foreground app using 7 fallback methods:
   - `mFocusedActivity` from activity manager (primary, Android 12+)
   - `mFocusedWindow` from window manager
   - `mCurrentFocus` from window manager
   - `mFocusedApp` from window manager
   - `topResumedActivity` / `mResumedActivity`
   - `dumpsys activity top` task name
   - Recent tasks (3 sub-patterns: A=uid:pkg, baseActivity, realActivity)
3. Multi-pattern regex parser extracts package names from various Android dumpsys formats
4. If confirmation is enabled, shows a centered white dialog with red X icon
5. Force-stops via `IActivityManager.forceStopPackage()` (binder call) with shell fallback to `am force-stop`
6. Optional toast and haptic feedback, then exits

## Safety

The app will **not** force-stop:

- Launchers (Samsung One UI, stock Android, Google TV)
- SystemUI
- Settings
- Shizuku itself

## Known Limitation and Planned Fix

**Issue:** The transparent Activity can steal focus from the target app before detection runs. This race condition may cause some apps to not be detected.

**Planned fix — Service-based architecture:**

1. Widget/Shortcut/QS tile starts a background Service (not an Activity)
2. Service runs detection — it never takes focus, so the target app remains the true foreground app
3. If no confirmation needed: force-stop, toast, finish
4. If confirmation needed: Service passes the detected package name to the transparent Activity via intent extra; Activity only shows the dialog (no detection needed)

This eliminates the race condition entirely since Services do not participate in the window focus system.

## Requirements

- Android 8.0 or higher (API 26)
- Shizuku running with ADB or root privileges
- Tested on Samsung Galaxy S25 Ultra (Android 16, One UI 8.5) with Shizuku 13.6.0 (thedjchi fork)

## Building

```bash
./gradlew assembleDebug
```

## Tech Stack

- Java
- Shizuku API 13.1.5
- compileSdk 36 (Android 16)
- Gradle 8.11.1 / AGP 8.7.3
