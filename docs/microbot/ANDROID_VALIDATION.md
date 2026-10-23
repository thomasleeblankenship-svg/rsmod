# Android validation remaining

No APK was produced. SDK, NDK, adb, emulator and `/dev/kvm` were absent in
the inspected environment. Android runtime/JNI checks have not run.

After the client build gate passes, supply Android SDK 35, build tools 35,
NDK (upstream documents 27.0.12077973), CMake 3.22.1 and an ARM64 Android 9+
device/emulator. See the complete upstream [Android README](../../android/README.md).
AGP is 8.6.0, Kotlin 2.0.20, Gradle 8.8, Android Java/Kotlin target 17;
embedded bionic JRE is 21, Java client target is 11.

From the repository root, follow the ordered stages:

```bash
source scripts/activate-java.sh
tools/stage-android-jre.sh
./gradlew --max-workers=4 :client:shadowJar
(cd ios/app && ./build-probes.sh)
android/dev-tools/gpu/stage-android-gpu-jar.sh
android/dev-tools/gpu/stage-rlhd-zone-jar.sh
tools/stage-mobile-runtime.sh --no-build --android-only
(cd android && ./gradlew --max-workers=4 :app:assembleRelease)
```

These commands have NOT been executed through APK assembly in this task.
Keep all JRE .so files in assets, not jniLibs; retain all upstream checksum checks.

Device acceptance (all unrun):

- Install and launch APK, verify game login/render/input and plugin discovery.
- Explicit start/stop, swap, exception cleanup, plugin disable and rapid enable/disable.
- Logout/reconnect/hop: no unexpected auto-resume or queued script starts.
- Confirm interactions by game state, not click enqueue return values.
- Confirm manual input immediately takes priority and cancels pending generated taps.
- Background/foreground, VM/service restart, app termination and held-key release.
- Test actual upstream script only after runtime port; record source SHA and unchanged core logic.
- Measure FPS/CPU/memory/thermals over long sessions; do not infer background support.
