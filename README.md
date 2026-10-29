# ReelCounter

An Android app that helps people see how much short-form video they watch across TikTok, Instagram
Reels, YouTube Shorts and Facebook Reels, set habits, and compare with friends. Transparency comes
first: every number is labeled Verified, Estimated, App time only, or Not tracked.

## Status

Phase 2 (project setup and UI foundation). See [docs/](docs/README.md) for the phase plan,
architecture and decisions.

## Building

Requirements: JDK 17+ and the Android SDK with platform `android-37.0` and build-tools 37.0.0.
Android Studio (current stable) bundles a suitable JDK and installs missing SDK parts on sync.

1. Point `local.properties` at your SDK, e.g. `sdk.dir=C\:/Users/you/Android/Sdk`. Android Studio
   writes this file for you; it is never committed.
2. Open this folder in Android Studio and let Gradle sync, or build from a terminal:

```bash
./gradlew assembleDebug testDebugUnitTest :core:model:test :app:lintDebug
```

3. Run the `app` configuration on a device or emulator (Android 8.0, API 26, or newer).

## Project layout

```
app/                 Application, MainActivity, app shell, navigation graph
build-logic/         Gradle convention plugins shared by every module
core/model           Pure Kotlin domain models (no Android)
core/common          Dispatchers, network monitor
core/designsystem    Theme, tokens, icons, base components
core/ui              Screen state wrappers, metric components, formatting
core/preview         Preview theme and sample data (debug builds only)
feature/*            One module per screen area; features never depend on each other
docs/                Architecture and phase deliverables
```

More detail: [docs/phase-2/README.md](docs/phase-2/README.md).
