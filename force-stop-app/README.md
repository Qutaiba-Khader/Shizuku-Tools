# Force Stop App

A minimal Shizuku-powered Android app that instantly force-stops the current foreground app.

## Features

- **App launcher** - Tap to force-stop whatever is in the foreground
- **Home screen shortcut** - Long-press the app icon, drag "Force Stop" to home
- **Home screen widget** - 1x1 red X button widget
- **Quick Settings tile** - "Force Stop" tile in the notification pull-down panel

All four methods do the same thing: detect the foreground app and force-stop it.

## How It Works

1. Transparent activity launches (invisible, excluded from recents)
2. Detects the foreground app using multiple fallback methods:
   - `mCurrentFocus` from window manager (primary)
   - `mFocusedApp` from window manager
   - `mResumedActivity` from activity manager
   - Recent tasks list
3. Force-stops the detected app via `IActivityManager.forceStopPackage()` (binder call) with shell fallback to `am force-stop`
4. Shows a toast confirmation and exits

## Safety

The app will **not** force-stop:

- Launchers (Samsung One UI, stock Android, Google TV)
- SystemUI
- Settings
- Shizuku itself

## Requirements

- Android 8.0+ (API 26)
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
