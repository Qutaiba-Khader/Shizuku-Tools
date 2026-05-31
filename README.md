# Shizuku Tools

A collection of small Android utility apps powered by [Shizuku](https://github.com/RikkaApps/Shizuku). These apps use Shizuku to perform system-level actions without root access.

## Apps

### Force Stop

Force-stop the current foreground app instantly with a single tap. Includes a home screen widget, Quick Settings tile, and configurable confirmation dialog.

- **[Source Code](force-stop-app/)**
- **[Download APK](releases/ForceStop-v1.0-debug.apk)**
- **[Documentation](force-stop-app/README.md)**

## Requirements

- Android 8.0+ (API 26)
- [Shizuku](https://shizuku.rikka.app/) installed and running (or a compatible fork like [thedjchi Shizuku](https://github.com/thedjchi/Shizuku))
- Grant Shizuku permission to the app on first launch

## Building

Each app is a standalone Gradle project. To build:

```bash
cd force-stop-app
./gradlew assembleDebug
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

## License

MIT
