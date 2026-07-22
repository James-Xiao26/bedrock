---
name: preview
description: Set up and preview the Bedrock app on a connected Android phone (native Windows). Use when asked to run, launch, preview, or screenshot the app, or to confirm a change works in the real app on the phone/emulator.
---

# Preview Bedrock on Android (native Windows)

Bedrock is Flutter UI + a large Kotlin engine (blocking, night clock, DND, billing) talking over `MethodChannel`/`EventChannel`.
The Kotlin half only runs on real Android, so **Flutter desktop/web is not a valid preview** — always use a physical device (or the Android emulator).

**Default target as of 2026-07-22: the user's physical phone** — Motorola moto g power 5G (2024), serial `ZY22J95NQJ`. The emulator is a fallback only. The user wants changes shown on the phone, with hot reload.

As of 2026-07-18 the build is **native Windows**, not WSL2/WSLg. The old `./tool/preview.sh` WSL flow is retired.

## Showing a code change on the phone (do this)

**The agent must NOT run `flutter run` itself.** The user keeps the session in their own terminal so they control hot reload. When a change is ready to preview, give the user the command to run (or, if a session is already up, just tell them to press `r`) — do not launch it in the background yourself.

Command to hand the user (debug session with hot reload):
```powershell
flutter run -d ZY22J95NQJ
```
`r` hot-reload, `R` hot-restart, `q` quit. Debug builds also enable the `00000` passcode backdoor and demo session.

**Never use `flutter install`.** It reuses a cached `app-release.apk` and silently ships a STALE build (a ~3s install with no Gradle compile is the tell — the new code is not in it). If a release APK is genuinely needed, tell the user to force a rebuild (`flutter build apk --release`) before installing — never trust a fast `flutter install`.

## Environment

- **Flutter SDK**: `C:\src\flutter` (on the user PATH). From tool shells that lack the PATH, invoke `C:\src\flutter\bin\flutter`.
- **Android SDK**: `%LOCALAPPDATA%\Android\Sdk` (Android Studio install). adb: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
- **Phone (default)**: Motorola moto g power 5G (2024), serial `ZY22J95NQJ`, over USB debugging.
- **AVD (fallback)**: `Pixel_9`, API 36, **Google Play image (non-rootable — `adb root` fails, so you can't set the device clock to fake bedtime windows)**.

## For the user: launch on the phone (default)

The phone is the preferred target - and better than the emulator for anything time-based, since the `Pixel_9` AVD is a non-rootable Play image whose clock can't be faked, while a real phone just uses real times.

1. First time only: on the phone, Settings → About phone → tap **Build number** 7x → back → **Developer options** → turn on **USB debugging**. Plug in with a **data** USB cable → tap **Allow** on the "Allow USB debugging?" dialog (check "Always allow"). If no dialog, set the USB notification to **File Transfer (MTP)**.
2. Confirm it's seen (serial `ZY22J95NQJ`, not `emulator-5554`):
   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
   ```
3. From the project root (`C:\Users\Jimmy\dev\screentime-app`), run with hot reload:
   ```powershell
   flutter run -d ZY22J95NQJ
   ```
   `r` hot-reload, `R` hot-restart, `q` quit. First launch: grant accessibility/DND/overlay/usage in the app's onboarding by hand.

This is dev sideloading over USB - separate from Play closed-testing (testers install the signed release via the Play opt-in link).

## For the user: emulator (fallback only)

If the phone isn't available: Android Studio → **Device Manager** → ▶ next to `Pixel_9`, wait for the home screen, then `flutter run -d emulator-5554` (or hit ▶ Run in Android Studio with `Pixel_9` selected). Note the AVD clock can't be faked, so bedtime-window testing needs the phone.

## For the agent: see and drive the phone

Use the SDK's adb by full path. The phone serial is `ZY22J95NQJ` (emulator would be `emulator-5554`).

Check it's attached:
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices   # expect "ZY22J95NQJ  device"
```

Screenshot (PowerShell `>` corrupts the PNG with a BOM — run the redirect from the **Bash** tool, or pull a file):
```bash
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" -s ZY22J95NQJ exec-out screencap -p > "$TEMP/screen.png"
```

Refresh after a code change: **the user drives this.** Ask them to press `r` in their running `flutter run` session (or to start one with the command above). The agent does not run `flutter run` or `flutter install`. The agent may still screenshot/tap/inspect the phone once the user's session is live.

Interact and inspect:
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb -s ZY22J95NQJ shell input tap <x> <y>     # tap
& $adb -s ZY22J95NQJ shell input keyevent 82     # dismiss lockscreen
& $adb -s ZY22J95NQJ logcat -d -s flutter        # recent Flutter logs
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
