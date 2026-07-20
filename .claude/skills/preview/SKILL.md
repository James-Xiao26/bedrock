---
name: preview
description: Set up and preview the Bedrock app on the Android emulator (native Windows). Use when asked to run, launch, preview, or screenshot the app, or to confirm a change works in the real app on an emulator/device.
---

# Preview Bedrock on the Android emulator (native Windows)

Bedrock is Flutter UI + a large Kotlin engine (blocking, night clock, DND, billing) talking over `MethodChannel`/`EventChannel`.
The Kotlin half only runs on real Android, so **Flutter desktop/web is not a valid preview** — always use the Android emulator (or a physical device).

As of 2026-07-18 the build is **native Windows**, not WSL2/WSLg. The old `./tool/preview.sh` WSL flow is retired.

## Environment

- **Flutter SDK**: `C:\src\flutter` (on the user PATH). From tool shells that lack the PATH, invoke `C:\src\flutter\bin\flutter`.
- **Android SDK**: `%LOCALAPPDATA%\Android\Sdk` (Android Studio install). adb: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
- **AVD**: `Pixel_9`, API 36, **Google Play image (non-rootable — `adb root` fails, so you can't set the device clock to fake bedtime windows)**.

## For the user: launch the app

1. Android Studio → **Device Manager** → hit ▶ next to `Pixel_9`. Wait for the home screen.
2. From the project root (`C:\Users\Jimmy\dev\screentime-app`):
   ```powershell
   flutter run -d emulator-5554
   ```
   Press `r` to hot-reload, `q` to quit. (Or just open the project in Android Studio and hit ▶ Run with `Pixel_9` selected.)

## For the agent: see and drive the running emulator

Connect to an emulator the user already has up — don't boot another. Use the SDK's adb by full path.

Check it's attached:
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices   # expect "emulator-5554  device"
```

Screenshot (then Read the PNG):
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" exec-out screencap -p > "$env:TEMP\screen.png"
```

Install/refresh after a code change:
```powershell
flutter build apk --debug
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r build\app\outputs\flutter-apk\app-debug.apk
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell monkey -p app.bedrock -c android.intent.category.LAUNCHER 1
```

Interact and inspect:
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb shell input tap <x> <y>       # tap
& $adb shell input keyevent 82       # dismiss lockscreen
& $adb logcat -d -s flutter          # recent Flutter logs
```

## Gotchas

- A rogue incompatible adb can squat on port 5037 → `protocol fault (couldn't read status)`, or `adb devices` comes back empty while the emulator is clearly running. Fix:
  ```powershell
  Stop-Process -Name adb -Force
  & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" start-server
  ```
  then re-check — it re-detects `emulator-5554`.

## Related
- Product/architecture context: project `CLAUDE.md`.
- Native build notes: user memory `windows-native-build`.
