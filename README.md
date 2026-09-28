# Solo Mode Roller

A personal-use Android app for the *Blade Runner RPG*'s ["Solo Mode"](docs/superpowers/specs/2026-09-28-solo-mode-roller-design.md) supplement. Lets you roll on any of the booklet's 22 rollable tables (grouped into 9 sections) without flipping through the PDF, and keeps a running log of your rolls for the session.

Kotlin + Jetpack Compose, no network access, no accounts, no persistence beyond the running process. Not published to the Play Store — built to sideload onto your own phone.

## Requirements

- JDK 17 or 21
- Android SDK: `platform-tools`, a `platforms;android-3x` matching `compileSdk` in `app/build.gradle.kts`, and the matching `build-tools`
- `ANDROID_HOME` / `JAVA_HOME` set in your shell

No Android Studio required — this was built entirely with the command-line SDK tools and Gradle.

## Build

```
./gradlew assembleDebug
```

Produces `app/build/outputs/apk/debug/app-debug.apk`.

## Install

With a phone connected via USB (USB debugging enabled):

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or copy the APK to the phone and open it to sideload.

## Test

```
./gradlew :app:testDebugUnitTest
```

Includes an exhaustive check that every table's dice ranges cover every possible roll exactly once (`TablesJsonCoverageTest`).

## Project structure

```
app/src/main/java/com/oscarriva/solomoderoller/
  data/   table data model + JSON parsing (app/src/main/assets/tables.json)
  logic/  dice rolling and row resolution
  model/  session roll-history state
  ui/     Compose screens and noir theme
```

## Source material

The source PDF (`Blade Runner - Solo Mode.pdf`) is not checked into this repo — it's copyrighted RPG content kept locally only. The transcribed table data in `app/src/main/assets/tables.json` is included for the app to function, since the app has no purpose without it.

## Docs

- [Design spec](docs/superpowers/specs/2026-09-28-solo-mode-roller-design.md)
- [Implementation plan](docs/superpowers/plans/2026-09-28-solo-mode-roller.md)
