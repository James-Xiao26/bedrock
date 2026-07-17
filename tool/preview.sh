#!/usr/bin/env bash
# Preview Bedrock on the Android emulator (WSL2 + WSLg).
# Run this from a normal WSL terminal (NOT through an agent/tool sandbox):
#     ./tool/preview.sh
# The emulator window opens on your Windows desktop; the app hot-reloads in place.
set -euo pipefail

export JAVA_HOME="$HOME/development/jdk-17.0.19+10"
export ANDROID_HOME="$HOME/development/android-sdk"
export PATH="$HOME/development/flutter/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

AVD="bedrock_api35"

# GPU backend. "host" uses your real GPU via WSL's /dev/dxg passthrough (fast).
# If you ever get a black screen or crash, fall back with: ./tool/preview.sh swiftshader_indirect
GPU="${1:-host}"

echo "==> Clearing any stale emulator locks/processes"
pkill -9 -f qemu-system 2>/dev/null || true
find "$HOME/.android/avd/$AVD.avd" -name '*.lock' -exec rm -rf {} + 2>/dev/null || true

if ! adb devices | grep -qw device; then
  echo "==> Booting emulator '$AVD' (GPU=$GPU; a window should appear on your desktop)"
  # Resources tuned for a 10GB WSL cap: 3GB RAM + 6 cores for the emulator,
  # leaving ~4GB in WSL for the Gradle/Dart build tooling during `flutter run`.
  nohup emulator -avd "$AVD" -gpu "$GPU" -cores 6 -memory 3072 -no-snapshot -no-boot-anim \
    > /tmp/bedrock-emulator.log 2>&1 &
  echo "    (emulator log: /tmp/bedrock-emulator.log)"

  echo "==> Waiting for device to come online"
  adb wait-for-device
  echo "==> Waiting for Android to finish booting"
  until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    sleep 2
  done
  adb shell input keyevent 82 >/dev/null 2>&1 || true   # dismiss lock screen
fi

echo "==> Launching the app (press 'r' to hot-reload, 'q' to quit)"
cd "$(dirname "$0")/.."
flutter run
