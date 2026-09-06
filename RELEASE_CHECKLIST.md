# Release Checklist

## Gameplay
- [ ] First-run player understands orbit switching without reading instructions.
- [ ] Every generated hazard pattern always leaves a readable safe lane.
- [ ] No collision feels earlier than the visual hitbox.
- [ ] Difficulty ramp feels fair at 30s, 60s, and 90s.
- [ ] OVERLOAD duration and feedback are clear.
- [ ] Pause/resume never advances simulation time.

## Device QA
- [ ] API 26 device/emulator
- [ ] API 36 device/emulator
- [ ] API 37 device/emulator
- [ ] 60 Hz phone
- [ ] 120 Hz phone
- [ ] Small ~360dp-wide phone
- [ ] Tall phone / display cutout
- [ ] TalkBack basic navigation
- [ ] Reduced motion/effects setting
- [ ] Sound muted / haptics disabled
- [ ] Background app during active run, return, resume

## Performance
- [ ] Profile release build, not debug build.
- [ ] No sustained frame misses during particle bursts.
- [ ] No allocations growing unbounded across 10-minute soak test.
- [ ] Check thermal behavior on mid-range hardware.
- [ ] Check SoundPool and vibration lifecycle after repeated Activity recreation.

## Store / policy
- [ ] Replace placeholder support contact in privacy policy.
- [ ] Final package ID is owned by publisher.
- [ ] Generate upload key and enroll in Play App Signing.
- [ ] Upload Android App Bundle to Internal testing.
- [ ] Complete Data safety form: no collection/no sharing for this build.
- [ ] Complete content rating questionnaire.
- [ ] Complete target audience / Families declarations as applicable.
- [ ] Confirm current target API requirement immediately before release.
- [ ] Capture genuine gameplay screenshots on release build.
- [ ] Review app name / icon for trademark conflicts.

## Release quality
- [ ] Run unit tests: `./gradlew testDebugUnitTest`
- [ ] Run lint: `./gradlew lintRelease`
- [ ] Build: `./gradlew bundleRelease`
- [ ] Test signed release AAB via Play Internal testing.
- [ ] Verify R8 build has no startup or audio regressions.
