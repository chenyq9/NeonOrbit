# Validation performed during generation

The generation environment did not contain an Android SDK, so a full Android Gradle build could not be executed here. The following checks were performed instead:

- Parsed every generated XML resource successfully.
- Compiled the rendering-independent `GameModels.kt` + `GameEngine.kt` with the locally available Kotlin/JVM compiler to catch Kotlin syntax/type errors in the core simulation.
- Ran a 120-second automated gameplay simulation across 20 deterministic random seeds. The autoplayer survived every run; maximum concurrent hazards remained at 4 and particles peaked at 46 in the latest run, confirming bounded object counts and no generated two-lane deadlocks in that sample.
- Compiled the included Gradle bootstrap JAR from its source with Java 17.
- Added the published SHA-256 for the Gradle 9.6.1 binary distribution to `gradle-wrapper.properties`.
- Parsed / visually inspected generated Play Store icon and feature graphic.

## Still required on a real Android toolchain

Run these before release:

```bash
./gradlew testDebugUnitTest
./gradlew lintRelease
./gradlew bundleRelease
```

Then test the signed release build on physical API 26, API 36, and API 37 devices, including at least one 120 Hz phone.
