# Android validation

Latest mobile woodcutting APK built from `ce1baea802a7c0adc29f47f51d2d39ab54ba19cc`.
Auto-hop is disabled and bank selection is automatic, including Lumbridge
stairs and the Draynor/Falador/Varrock bank catalogue. Full loading instructions,
ported workflows and limitations: [WOODCUTTING_MOBILE.md](WOODCUTTING_MOBILE.md).

Latest checks passed: 211 focused client tests (zero failures/errors/skips),
Checkstyle, PMD and bridge/auth contract preflight; fresh Android release assembly,
APK v2 signature and ZIP alignment. All 131 selected Microbot/mobile/input classes
(including the actual JavaBridge) match compiled client classes, the collision map
matches the pinned resource, and the packaged commit is `ce1baea`.

Local artifact: `/workspace/artifacts/runemobile-woodcutting-ce1baea-arm64.apk`.
The browser download is a separate GitHub build of the same source; its APK hash
and test signing key can differ from this cloud artifact. No phone/live-game test
has run. Artifact metadata: [apk-artifact.json](apk-artifact.json).

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
./scripts/build-android.sh /workspace/rsmod
```

The helper performs the upstream documented ordered stages: JRE, shadowJar,
probes, GPU, 117HD, runtime tag, then Android assembleRelease. It selects the
pinned Lombok from the active writable Gradle cache. Retain upstream checksum
verification; see [Android README](../../android/README.md) for architecture.
Use the saved environment install script to provision tools in a fresh machine.

## Device acceptance remaining (all unrun)

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
