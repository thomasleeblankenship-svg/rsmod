> Latest woodcutting source port: [mobile loading, scope and acceptance](docs/microbot/WOODCUTTING_MOBILE.md). The baseline results below predate that port.

# RuneMobile Phase 2 build report

Prepared 2026-10-22 (America/New_York). Baseline mobile adapter build;
the desktop Microbot runtime has not been ported.

## Source and tools

- Destination: `thomasleeblankenship-svg/rsmod`, branch
  `feat/android-microbot-compat`. Original rsmod history retained; its working
  tree was cleared at the user's request before importing the supplied patch.
- Integration source commit: `8aa492ccf4ed1a0fcd8e87a1854b30dbb57bc3c4`.
- RuneMobile: `audaro/runemobile`, master,
  `a8c4f22f8ac170b654793df2fd85372f31b18c64`.
- Microbot inspection reference: `chsami/Microbot`, main,
  `e01e0d77d79b354472a5737b3aaab2449eb034fc`.
- Verified Temurin JDK 21.0.8+9. SHA-256 of the Linux JDK archive:
  `f2dc5418092c43003db8f9005c4a286e1c0104fea96ccdd49e8ebd037cac9219`.
- Gradle 8.8, wrapper distribution verified with upstream pinned SHA-256.
  Client bytecode target 11; Android Java/Kotlin target 17; embedded JRE 21.
- Android CLI 19.0, SDK platform 35, build tools 35.0.0, CMake 3.22.1.
  Native app uses AGP 8.6.0's default NDK 26.1.10909125; the upstream JRE
  helper uses the separately installed NDK 27.0.12077973.
- Android Kotlin/Compose plugin 2.0.20; ABI arm64-v8a; min SDK 28, target 35.
- All tooling and caches are outside Git under `/workspace/tooling`.

## Implementation completed

Imported actual RuneMobile source plus Phase 2 while preserving destination
Git history. Corrected two calls to nonexistent `Player.isMoving()`; player
busy detection uses the real pose/idle-pose API conservatively (turning also
counts as busy). Updated stand-ins so they no longer invent that API.

Added lifecycle generation checks so queued starts cannot survive stop,
logout/world hop or plugin disable. Shutdown clears the Run setting. Added
actual-interface tests for explicit start, stop/disable races, disconnect/hop,
script swapping, invalid selection, fail-closed exceptions and movement.

Added writable-cache/proxy/TLS/tool activation and a build-helper correction
for pinned Lombok lookup outside read-only `~/.gradle`. Preserved all artifact
checksum and TLS checks. Prepared separate Claude runtime/API assignments.

## Commands and results

All commands use `/workspace/rsmod` unless noted; first run
`source scripts/activate-java.sh` in each shell. Refer to
`docs/microbot/build-logs/` for captured evidence.

| Command/check | Exit / result |
| --- | --- |
| `./scripts/test-portable.sh` | 0; 25 passed, stand-in tests only |
| `./gradlew --no-daemon --max-workers=4 :runelite-api:compileJava` | 0; real API module built |
| `python3 scripts/check-api-surface.py` | 0; real API classes, client/library stand-ins, target 11 |
| `./gradlew --no-daemon --max-workers=4 :client:compileJava` | 0; actual full client and Phase 2 compile |
| `./gradlew --no-daemon --max-workers=4 :client:test --tests net.runelite.client.plugins.mobilemicrobot.MobileMicrobotPluginTest` | 0; 9 tests, zero failures/errors/skips; mocked services, not a game |
| `tools/stage-android-jre.sh` | 0; pinned checksum, bionic/aarch64/soname checks and native xawt build passed |
| `./gradlew --no-daemon --max-workers=4 :client:shadowJar` | 0 |
| `(cd ios/app && ./build-probes.sh)` | 0; upstream checksum verification retained |
| `android/dev-tools/gpu/stage-android-gpu-jar.sh` | 0; stage verdict GREEN |
| `android/dev-tools/gpu/stage-rlhd-zone-jar.sh` | 0; stage verdict GREEN |
| `tools/stage-mobile-runtime.sh --no-build --android-only` | 0; runtime tag generated |
| `(cd android && ./gradlew --no-daemon --max-workers=4 :app:assembleRelease)` | 0; release-variant APK assembled |
| `./scripts/build-android.sh /workspace/rsmod` | 0; complete ordered staging/build helper exercised again |
| `android/dev-tools/ci/release-gate.sh --contract-only` | 0; shared bridge/auth/hub-pin preflight passed |
| `(cd android && ./gradlew --no-daemon --max-workers=4 :app:lintRelease :app:testReleaseUnitTest)` | 0; lint had 47 warnings, 7 informational findings, zero errors; unit test task NO-SOURCE, zero Android tests |
| `apksigner verify --verbose --print-certs <apk>` | 0; APK v2 signature verified, local Android Debug certificate |
| `zipalign -c -P 16 -v 4 <apk>` | 0; static alignment check passed |
| APK ZIP/content inspection | 15 Phase 2 classes match actual compiled classes; JRE assets and both ARM64 native libraries present; no duplicated jniLibs JRE |

The complete release-gate script (including shellcheck and device smoke) was
not run; its contract preflight and the separate lint/build checks above ran.
Build warnings are recorded; none were suppressed to obtain a passing result.

The first Gradle attempt lacked a writable cache, then Java needed explicit
proxy routing and the system Java trust store. Initial Maven requests to
`repo.runelite.net` returned HTTP 403. That host was saved in the network draft;
subsequent real dependency resolution and builds succeeded. SDK CLI's latest
launcher tried writing outside the workspace; verified CLI 19.0 and a writable
Android user directory resolved SDK installation.

Upstream resource processing does not declare Git HEAD as a task input. We
explicitly refreshed `:client:processResources --rerun-tasks` after committing
the source so the packaged build identity refers to the integration commit,
then reran focused tests and the complete staging/build helper.

## Artifact and limitations

APK: `/workspace/artifacts/runemobile-phase2-arm64.apk` (about 102 MiB).
Exact bytes, final SHA-256, package metadata and verification status are in
[apk-artifact.json](docs/microbot/apk-artifact.json). This is locally debug-signed
even though it is a release build variant; it does not carry audaro's official
release key. No private signing material is included in source or artifacts.

Static APK verification does not establish installation or runtime behavior.
No phone installation/launch, game-action confirmation, Android lifecycle,
manual takeover, performance, thermal or long-session test has run. No compatible
device was provided. ADB currently aborts while trying to create
`/home/agent/.android` on the read-only filesystem; `/dev/kvm` is absent.
Use a separate Android test device/environment for remaining acceptance checks.

Pending-tap cancellation and manual-input arbitration remain unsupported in
the Phase 2 adapter. Its click return means requested input, not action success.
Navigation remains a short-range minimap tap, not route planning.

Actual upstream Microbot scripts and desktop JARs remain unsupported. The source
audit found 20 differing API Java files, one desktop-only API file, and no local
mixins source module in either inspected checkout. The conservative lexical
closures contain 1,709 classes for Script/Microbot and 1,715 for the selected
read-only example; this is not a compiler-complete dependency graph or proof
that all those classes must be ported.

See [TEAM_ASSIGNMENTS.md](TEAM_ASSIGNMENTS.md): Codex owns packaging/integration,
Claude 1's prepared prompt covers runtime, and Claude 2's covers APIs/example.
The external Claude sessions have not been launched or messaged by Codex.

## Reuse and persistence

Saved environment draft fields: `install_script`, `start_skill`, and additive
custom network host `repo.runelite.net` (existing package-manager presets kept).
The complete install script was exercised successfully. Saving a draft does not
publish the snapshot. Review/save it in environment settings, then publish the
environment when desired; restoration in a new task has not been independently
verified. Feature commits are local and have not been pushed. Keep the source
archive or push the branch through an explicitly authorized workflow before
relying on remotely reproducible local-only commits.
