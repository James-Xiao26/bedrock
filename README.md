# Bedrock

Put your phone to bed.

Bedrock turns your phone into a plain alarm clock between your bedtime and wake time.
You pick a schedule for each day of the week; when bedtime hits, the screen becomes a dim night clock and every app you did not allowlist is blocked until morning.
Calls always work, an optional alarm wakes you, and a wind-down phase dims the screen before bed so the cutoff never surprises you.

Loosening your schedule for tonight is not allowed - changes that make tonight easier only apply tomorrow.
Normal mode offers a deliberately slow escape hatch; hardcore mode's only exit is a $1 emergency bypass.
Your streak records every night you kept, escaped, or broke.

## Stack

- Flutter UI + business logic (`lib/`)
- Native Kotlin enforcement engine, night clock (Jetpack Compose), alarms, and billing (`android/app/src/main/kotlin/app/bedrock/`)
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
