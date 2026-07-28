# Bedrock

A social media and app blocker: users set per-weekday downtime windows; during that window chosen apps are blocked, and inside Instagram and YouTube the infinite-scroll feeds (Reels, Shorts, home feed) are blocked while DMs and search stay usable.
Android-first (Google Play), iOS later via the Screen Time API.
Full product plan and locked decisions: `~/.claude/plans/i-want-to-create-modular-cookie.md`.

## Architecture stance

Kotlin owns everything that must be true at 3 AM: session state machine, config store, alarms, blocking, night clock UI (Jetpack Compose), DND, billing, widget, stats writes.
Dart/Flutter owns everything the user does while awake: onboarding, schedule/settings editors, stats screens.
Kotlin is the single writer of canonical state; Dart is a client over `MethodChannel("bedrock/engine")` and `EventChannel("bedrock/events")` and never persists engine-critical state.
`lib/src/engine/` is the only Dart code allowed to touch platform channels.
Native code lives under `android/app/src/main/kotlin/app/bedrock/` (engine/, blocking/, service/, ui/, controllers/, billing/, data/, widget/, channel/).

## Toolchain (native Windows; PowerShell is the primary shell)

- Flutter: `C:\src\flutter` (`flutter` is on PATH)
- JDK 21, bundled with Android Studio: `C:\Program Files\Android\Android Studio\jbr`
- Android SDK: `C:\Users\Jimmy\AppData\Local\Android\sdk` (also in `android/local.properties`)
- `java` on PATH is an unrelated Java 8; Gradle needs `JAVA_HOME` set to the JBR above for every invocation.
- Gradle output goes to `build/app/`, not `android/app/build/`, because Flutter redirects the build directory.

## Commands

- Fetch deps: `flutter pub get`
- Lint: `flutter analyze` (must stay at zero issues)
- Dart tests: `flutter test`
- Kotlin unit tests (PowerShell): `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; cd android; .\gradlew.bat :app:testDebugUnitTest`
- Debug build: `flutter build apk --debug`
- Preferred run target is the physical phone (`flutter run -d ZY22J95NQJ`); the agent hands the command over rather than running it.

## Conventions

- Play Store policy constraints are load-bearing: never block Settings/Play Store, prominent-disclosure before enabling the accessibility service.
- Release builds retrieve window content (`canRetrieveWindowContent="true"` in `src/main/res/xml/accessibility_service_config.xml`), because in-app feed blocking needs it to tell a feed from DMs.
  There is no debug overlay any more; debug and release run the same accessibility surface.
  Four things must say the same thing and change together: that config, `accessibility_service_description` in `res/values/strings.xml`, the `_PrivacyNote` disclosure in `onboarding_flow.dart`, and the Play Console accessibility declaration.
  Only packages with a `FeedRules` entry may ever have their window content read; `FeedDetector` returns before requesting the window for everything else, and that is what makes the disclosure true.
- The session state machine and freeze-rule classifier must stay pure (no Android deps) for exhaustive JUnit coverage.
- Milestones M1-M8 are tracked in the plan file; each must be independently testable on a device/emulator before moving on.
