# Android validation

The debug build from `024f3872` fixes full-batch dropping, independent axe
protection and Lumbridge floor/stair resolution. Auto-hop remains disabled and
bank selection remains automatic. Loading and log extraction instructions:
[WOODCUTTING_MOBILE.md](WOODCUTTING_MOBILE.md).

Source validation: 222 focused client tests, zero failures/errors/skips,
Checkstyle and PMD passed. The bridge/auth contract preflight passed.
[Focused check log](build-logs/drop-stairs-checks.log).

User phone testing exposed the original drop/stair bugs. The new fixes require
phone acceptance; cloud tests do not demonstrate live-game success.
Local debug APK verification passed: signature v2, 16 KB native-library ZIP
alignment, debuggable flag, no bundled tokens, all 133 selected compiled
Microbot/mobile/input classes, collision resource and source commit match.
Artifact identity and APK verification: [apk-artifact.json](apk-artifact.json).

The original baseline checks below describe earlier validation unless repeated above.

## Verified

- JDK 21.0.8+9, Gradle 8.8, SDK platform/build-tools 35, CMake 3.22.1 installed.
- NDK 27.0.12077973 used for JRE helper; AGP 8.6.0 default NDK 26.1.10909125
  used for the app's native libraries. Kotlin 2.0.20; embedded JRE 21.
- Checksum-verified bionic ARM64 JRE, shaded client, probes, GPU and 117HD
  variants, runtime tag and APK assembly passed.
- APK v2 signature verified (local Android Debug certificate), ZIP alignment
  verified, package metadata confirms arm64-v8a / minimum SDK 28 / target 35.
- All 15 packaged mobile scripting classes match the actual compiled classes.
- JRE stays in assets; no JRE duplication in jniLibs.
- Shared bridge/auth contract and hub-pin preflight passed.
- Android lint passed: zero errors, 47 warnings, 7 informational findings.
- Android unit-test task was NO-SOURCE: no Android unit tests executed.

Full evidence: [BUILD_REPORT.md](../../BUILD_REPORT.md).
Artifact identity: [apk-artifact.json](apk-artifact.json).

## Reproduce

```bash
cd /workspace/rsmod
source scripts/activate-java.sh
./scripts/build-android.sh /workspace/rsmod --debug
```

The helper performs the upstream documented ordered stages: JRE, shadowJar,
probes, GPU, 117HD, runtime tag, then Android assembleDebug (`--debug`); the default remains assembleRelease. It selects the
pinned Lombok from the active writable Gradle cache. Retain upstream checksum
verification; see [Android README](../../android/README.md) for architecture.
Use the saved environment install script to provision tools in a fresh machine.

## Device acceptance remaining for this build

- Install and launch on ARM64 Android 9+, verify login/render/input and plugin discovery.
- Explicit start/stop, script swap, exception cleanup, disable and rapid enable/disable.
- Logout/reconnect/hop: no unexpected auto-resume or queued script starts.
- Confirm interactions by game state, not click enqueue return values.
- Verify manual takeover and cancellation of pending generated taps on the phone.
- Background/foreground, VM/service restart, termination and held-key release.
- Run an actual upstream script after the runtime/API port, recording unchanged core logic.
- Measure FPS/CPU/memory/thermals over long sessions; no inferred background support.

No compatible device was supplied. In this instance ADB aborts while trying to
create `/home/agent/.android` on the read-only filesystem; `/dev/kvm` is absent.
No installation or launch success is claimed. Use an external test phone or
compatible device-enabled environment for these checks.
