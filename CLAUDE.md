# Bedrock

A bedtime lockdown app: users set per-weekday bed/wake times; during that window the phone becomes a dim night clock and all other apps are blocked.
Android-first (Google Play), iOS later via the Screen Time API.
Full product plan and locked decisions: `~/.claude/plans/i-want-to-create-modular-cookie.md`.

## Architecture stance

Kotlin owns everything that must be true at 3 AM: session state machine, config store, alarms, blocking, night clock UI (Jetpack Compose), DND, billing, widget, stats writes.
Dart/Flutter owns everything the user does while awake: onboarding, schedule/settings editors, stats screens.
Kotlin is the single writer of canonical state; Dart is a client over `MethodChannel("bedrock/engine")` and `EventChannel("bedrock/events")` and never persists engine-critical state.
`lib/src/engine/` is the only Dart code allowed to touch platform channels.
Native code lives under `android/app/src/main/kotlin/app/bedrock/` (engine/, blocking/, service/, ui/, controllers/, billing/, data/, widget/, channel/).

## Toolchain (user-local, no sudo on this box)

- Flutter: `~/development/flutter` (stable)
- JDK 17: `~/development/jdk-17.0.19+10`
- Android SDK: `~/development/android-sdk`
- `unzip`/`zip` are python3 shims in `~/.local/bin` (preserve permissions/symlinks); extend the shim instead of apt-installing.
- Env vars are exported in `~/.bashrc`; when running Bash non-interactively, export `JAVA_HOME`, `ANDROID_HOME`, and `PATH` explicitly.

## Commands

- Fetch deps: `flutter pub get`
- Lint: `flutter analyze` (must stay at zero issues)
- Dart tests: `flutter test`
- Kotlin unit tests: `cd android && ./gradlew :app:testDebugUnitTest`
- Debug build: `flutter build apk --debug`
- Emulator AVD: `bedrock_api35` (API 35, Pixel 7 profile); needs /dev/kvm access

## Conventions

- Play Store policy constraints are load-bearing: never block Settings/Play Store, no window-content retrieval in the accessibility service, prominent-disclosure before enabling it.
- The session state machine and freeze-rule classifier must stay pure (no Android deps) for exhaustive JUnit coverage.
- Milestones M1-M8 are tracked in the plan file; each must be independently testable on a device/emulator before moving on.
