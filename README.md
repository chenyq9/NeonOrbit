# Neon Orbit

A production-minded Android arcade game prototype built from scratch with Kotlin + Jetpack Compose.

## Game

**One tap, two orbits.** Tap anywhere during play to switch between the inner and outer ring. Avoid red signal hazards and collect cyan energy shards. Fill the energy ring to trigger **OVERLOAD**, a short invulnerable window that lets you smash hazards for bonus points.

The simulation is deterministic and separated from rendering. The game canvas is resolution-independent and uses procedural vector drawing, particles, glow layers, screen shake, dynamic difficulty and 60/120 Hz frame timing. No Internet permission, analytics SDK, ads, account system, or remote backend are included.

## Tech baseline

- Android Gradle Plugin 9.4.0
- Gradle 9.6.1
- compileSdk / targetSdk 37
- minSdk 26
- Kotlin / Compose compiler 2.3.21
- Jetpack Compose BOM 2026.08.00
- AndroidX Core 1.19.0
- Activity Compose 1.13.0
- Lifecycle Runtime Compose 2.10.0

## Open and run

1. Open this folder in Android Studio **Quail 4 (2026.1.4)** or newer.
2. Install Android SDK Platform 37 if prompted.
3. Let Gradle sync.
4. Run the `app` configuration on a portrait Android device or emulator (API 26+).

The environment that generated this archive did not include an Android SDK, so an APK/AAB is not bundled. The pure Kotlin game engine was syntax-checked separately; do a normal Android Studio Gradle sync/build before store submission.

### Gradle bootstrap note

The archive includes a small source-visible bootstrap JAR at `gradle/wrapper/gradle-wrapper.jar` because this generation environment could not fetch Gradle's official binary wrapper JAR. It verifies the official Gradle 9.6.1 distribution SHA-256 before running Gradle. Its Java source is included under `tools/wrapper-bootstrap/`. After the first successful sync/build, replace it with the official wrapper by running:

```bash
./gradlew wrapper --gradle-version 9.6.1
```

This is recommended before committing the project to a production repository or enabling Gradle wrapper validation in CI.

## Release build

Create your own signing key and configure signing in Android Studio / CI, then:

```bash
./gradlew bundleRelease
```

Upload the generated AAB from `app/build/outputs/bundle/release/` to Play Console internal testing first.

## Controls

- Tap game area: switch orbit
- Pause button: pause/resume
- Settings: sound, haptics, reduced effects

## Store readiness included

- Adaptive launcher icon + Play Store 512px icon
- 1024×500 feature graphic
- English + Simplified Chinese UI strings
- Local best score + settings persistence
- Offline-only privacy policy and Data safety notes
- Release / QA checklist
- R8 + resource shrinking for release
- Unit tests for core game-state behavior
- Original locally generated SFX

## Still required before actual publication

- Run on several physical devices and complete the QA checklist.
- Capture real in-game screenshots from release builds.
- Create a Play App Signing key / upload key.
- Confirm final package ID ownership and store developer identity.
- Complete Play Console content rating and target audience forms.
- Re-check current Google Play policy immediately before submission.
