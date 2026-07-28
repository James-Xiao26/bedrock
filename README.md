# Bedrock

An iOS-Downtime-style app blocker for Android.

You set a schedule for each day of the week. During the scheduled window every app is blocked except the always-allowed set and your own allowlist - but the phone stays otherwise normal (home screen and allowed apps work as usual).
Opening a blocked app shows a lightweight blocker screen; entering your passcode grants that one app more time (5 min, 15 min, or the rest of the window).

Separately, and around the clock, the infinite-scroll feeds inside Instagram and YouTube (Reels, Shorts, the home feed) are blocked while DMs and search keep working.

The passcode is reusable and viewable in Settings before the daily cutoff, then hidden.
If you forget it after cutoff, a $1 purchase rotates and reveals a fresh code.

A blocked feed does **not** accept the passcode. It can't: feed blocking runs outside downtime, when the code is viewable in Settings, so honouring it there would make the blocker a two-tap round trip.
The way past a feed block is the $1 bypass or copying out the acknowledgement note, either of which buys 15 minutes.

Loosening never happens on impulse. Loosening your schedule only takes effect tomorrow, never tonight, and switching feed blocking off in Settings lands 24 hours later (switching it back on is instant).
Stats track "clean days": a window with no passcode grant, and your streak of consecutive clean windows.

Settings and the Play Store are never blocked, so you can always uninstall or force-stop the app.

## Stack

- Flutter UI + business logic (`lib/`)
- Native Kotlin enforcement engine, per-app blocker screen (Jetpack Compose), and billing (`android/app/src/main/kotlin/app/bedrock/`)
- All data on-device; no accounts, no backend

## Development

```sh
flutter pub get
flutter analyze
flutter test
flutter build apk --debug
```

Kotlin unit tests:

```sh
cd android && ./gradlew :app:testDebugUnitTest
```

See `CLAUDE.md` for architecture conventions and toolchain notes.
