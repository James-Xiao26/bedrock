---
name: release
description: Build a release Android App Bundle (.aab) for Bedrock and ship an update to users on Google Play. Use when asked to build the aab, cut a release, bump the version, or push changes to users/testers.
---

# Release Bedrock (build the .aab, ship to users)

Produces the signed `.aab` you upload to Google Play. Every user-facing update goes out this way: bump version -> build -> upload to a Play track. See [[play-publishing]] for track/account status and [[preview]] for local device testing.

## 1. Bump the version (required for every upload)

Play rejects an `.aab` whose `versionCode` is not higher than the last upload. In `pubspec.yaml`:

```yaml
version: 0.1.2+4    # versionName+versionCode
```

Bump **`+N`** (versionCode, integer) every single upload. Bump `versionName` when it's a user-visible release. Flutter injects both into the manifest.

## 2. Preconditions (check, don't assume)

- **Signing:** `android/key.properties` must exist (gitignored). Present -> release-signed with the upload keystore (`C:/Users/Jimmy/bedrock-upload.jks`, see [[upload-keystore]]). Absent -> build silently falls back to debug signing and Play will reject it.
- **Toolchain env:** the JDK/SDK env vars from CLAUDE.md are WSL-era and wrong on this native-Windows box. Use these actual paths:
  - `JAVA_HOME = C:\Program Files\Android\Android Studio\jbr`
  - `ANDROID_HOME = C:\Users\Jimmy\AppData\Local\Android\sdk`
  - Flutter/Dart: `C:\src\flutter\bin\flutter.bat`, `...\dart.bat`

## 3. Build

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:ANDROID_HOME = "C:\Users\Jimmy\AppData\Local\Android\sdk"; & "C:\src\flutter\bin\flutter.bat" build appbundle --release
```

Output: `build/app/outputs/bundle/release/app-release.aab`.

### Known non-fatal failure: "failed to strip debug symbols"

The build exits **1** on Flutter's post-build debug-symbol stripping (`cmdline-tools component is missing` in `flutter doctor`). **The `.aab` is already assembled and signed before that step** - it's valid and uploadable. Play accepts unstripped native libs (only a non-blocking "upload debug symbols" hint). Confirm the artifact rather than trusting the exit code:

```powershell
Get-Item "build\app\outputs\bundle\release\app-release.aab" | Select-Object Length, LastWriteTime
```

Fresh timestamp + ~50 MB = good. To verify it's signed and carries the right versionCode:
```bash
python -c "import zipfile; z=zipfile.ZipFile('build/app/outputs/bundle/release/app-release.aab'); n=z.namelist(); print('signed:', any(x.startswith('META-INF/') and x.endswith(('.RSA','.EC','.DSA')) for x in n))"
```

## 4. Ship to users

Agent cannot upload (no Play Console API wired up). Hand off:

1. Play Console -> Bedrock -> **Testing > Closed testing** (or the active track) -> **Create new release**.
2. Upload `app-release.aab`.
3. Add release notes, review, **roll out**.

Users on that track get the update automatically. Promote closed -> open -> production when ready ([[play-publishing]]).

## Notes

- App icon lives in `tool/appicon/` + `pubspec.yaml` `flutter_launcher_icons:`; regenerate with `dart run flutter_launcher_icons` before building if it changed.
- Before any release, `flutter analyze` must be at zero and `flutter test` green (CLAUDE.md).
- The agent must NOT run `flutter run`/install (see [[preview]]); building the `.aab` is fine.
