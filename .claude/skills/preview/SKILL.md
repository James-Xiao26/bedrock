---
name: preview
description: Set up and preview the Bedrock app on the Android emulator under WSL2. Use when asked to run, launch, preview, or screenshot the app, or to confirm a change works in the real app on an emulator/device.
---

# Preview Bedrock on the Android emulator (WSL2)

Bedrock is Flutter UI + a large Kotlin engine (blocking, night clock, DND, billing) talking over `MethodChannel`/`EventChannel`.
The Kotlin half only runs on real Android, so **Flutter desktop/web is not a valid preview** - always use the Android emulator (or a physical device).

## Division of labor (important)

There are two actors, and they do different things:

- **The user** hosts the emulator for interactive/live work. They run `./tool/preview.sh` from a **real WSL terminal**; WSLg renders the emulator window on their Windows desktop with the fast `host` GPU and `flutter run` hot-reload. This is the best experience for actually using the app.
- **The agent (Claude Code) drives an already-running emulator over `adb`.** Short-lived `adb` commands (screencap, install, logcat, input, shell) work reliably. When the user has an emulator up, connect to it - do not boot another.
- **The agent can also self-host an emulator for a one-off screenshot** when none is running, but **only inside a single Bash call** (see the recipe below). The agent must NOT launch the emulator as a background/`run_in_background`/`nohup` process: the emulator double-forks and detaches, and the harness kills its process group the moment the launching call returns, so those all die with empty output / exit 1. The working pattern is: run the emulator in the **foreground** (sandbox left ON - disabling it breaks the launch) with a **background watcher subshell** that waits for boot, screenshots, and then `adb emu kill`s it, all before the foreground call returns. This is ephemeral (one boot per call, ~2-3 min, headless software-rendered so slow) - fine for a quick screenshot, not for interactive work.

So the loop is: for live work, user runs `./tool/preview.sh` and leaves it open → agent connects via `adb`. For a quick unattended screenshot, agent self-hosts within one call.

## Environment (already fixed, should persist)

These one-time fixes live on disk; re-verify only if the emulator won't boot:

- **KVM**: `/dev/kvm` must exist and the user must be in the `kvm` group (`groups | grep kvm`). Without it the emulator has no hardware acceleration.
- **Qt window libs**: the emulator's xcb Qt plugin needs `libSM.so.6` + `libICE.so.6`, which this minimal WSL distro lacks (no sudo). They were copied into `~/development/android-sdk/emulator/lib64/qt/lib/` and `.../lib64/`. Source copies also live in `~/.local/lib/`.
- **Toolchain env** (non-interactive shells must export these):
  ```
  export JAVA_HOME=~/development/jdk-17.0.19+10
  export ANDROID_HOME=~/development/android-sdk
  export PATH=~/development/flutter/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH
  ```
- **Emulator resources** (tuned for the 10GB WSL cap on a 13.8GB host; set in both `tool/preview.sh` and the AVD `config.ini`): 3GB RAM, 6 cores, 512MB app heap, `-gpu host`. Do not raise these - `flutter run`'s Gradle+Dart tooling needs the remaining ~4GB, and Windows only has ~3.8GB outside WSL.

## For the user: launch the app

```
cd ~/projects/screentime-app
./tool/preview.sh
```
This clears stale locks, cold-boots the `bedrock_api35` AVD on the host GPU, waits for boot, then runs `flutter run` (press `r` to hot-reload, `q` to quit).
If you get a black screen or a GPU crash, fall back to software rendering: `./tool/preview.sh swiftshader_indirect`.

## For the agent: see and drive the running emulator

First check whether an emulator is already up (verify with `adb devices`, NOT `pgrep`):

```bash
export ANDROID_HOME=~/development/android-sdk
export PATH=~/development/flutter/bin:$ANDROID_HOME/platform-tools:$PATH
adb start-server >/dev/null 2>&1
adb devices                       # expect an "emulator-5554  device" line
```

If none is running and you just need a screenshot, self-host within one call (foreground emulator + background watcher; keep the Bash sandbox ON; give it ~300s):

```bash
export ANDROID_HOME=~/development/android-sdk; export PATH=$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH
J="$CLAUDE_JOB_DIR/tmp"
find "$HOME/.android/avd/bedrock_api35.avd" -name '*.lock' -exec rm -rf {} + 2>/dev/null
adb start-server >/dev/null 2>&1
(
  adb wait-for-device
  for i in $(seq 1 60); do [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break; sleep 3; done
  adb shell monkey -p app.bedrock -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 25                        # software rendering is slow; wait for the real UI past the Flutter splash
  adb exec-out screencap -p > "$J/app.png" 2>/dev/null
  adb emu kill >/dev/null 2>&1
) &
cd ~
timeout 290 emulator -avd bedrock_api35 -no-window -gpu swiftshader_indirect -cores 6 -memory 3072 -no-snapshot -no-boot-anim -no-audio > "$J/emu.log" 2>&1
```
Then Read `$CLAUDE_JOB_DIR/tmp/app.png`. Note: the first frame is the Flutter launch splash (blue logo on white) - wait long enough (20-25s+ headless) for the real Dart UI to paint before capturing.

Take a screenshot (then Read the PNG to view it):
```bash
adb exec-out screencap -p > "$CLAUDE_JOB_DIR/tmp/screen.png"
```

Install/refresh the app after a code change (build is finite, not a persistent process, so it works from the agent):
```bash
cd ~/projects/screentime-app
flutter build apk --debug
adb install -r build/app/outputs/flutter-apk/app-debug.apk
adb shell monkey -p app.bedrock -c android.intent.category.LAUNCHER 1   # verify the package name
```
Interact and inspect:
```bash
adb shell input tap <x> <y>            # tap
adb shell input keyevent 82            # dismiss lockscreen
adb logcat -d -s flutter               # recent Flutter logs
```

Note: `pgrep -f qemu-system` gives false positives (it matches your own command line). Verify a real emulator via `adb devices`, not `pgrep`.

## Related
- Product/architecture context: project `CLAUDE.md`.
- Toolchain details: user memory `dev-environment-setup`, `android-emulator-wsl-preview`.
