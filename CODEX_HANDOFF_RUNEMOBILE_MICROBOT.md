# CODEX HANDOFF — RuneMobile + Desktop Microbot API Compatibility (Android)

**Prepared:** 2026-10-22  
**Project goal:** Make [audaro/runemobile](https://github.com/audaro/runemobile) run Microbot scripts on-device on Android, with the greatest practical source compatibility with [chsami/Microbot](https://github.com/chsami/Microbot). **Do the engineering, do not only generate a plan.**

> **Truth in status:** The attached Phase 2 package is a Microbot-*inspired* RuneLite plugin, not the real desktop Microbot runtime. It has 20 local stub-based portable tests passing. It has **not** been verified with RuneMobile's complete Gradle project, an Android APK, an Android device, or unmodified desktop script JARs. Treat those as open work.

## Files provided to Codex

- `runemobile-microbot-phase2.zip`: patch from prior assistant, containing Java source under `net.runelite.client.plugins.mobilemicrobot`, `install.sh`, `scripts/test-portable.sh`, `scripts/build-android.sh`, plus tests and documentation.
- `runemobile-microbot-starter.zip`: superseded first revision. Do **not** install over Phase 2.
- `CODEX_HANDOFF_RUNEMOBILE_MICROBOT.md` (this file): work instructions and acceptance criteria.

## Repository state observed (recheck, do not assume unchanged)

- RuneMobile: `audaro/runemobile`, branch `master`, previously observed SHA `a8c4f22f8ac170b654793df2fd85372f31b18c64`.
- Microbot: `chsami/Microbot`, branch `main`, previously observed SHA `e01e0d77d79b354472a5737b3aaab2449eb034fc`.
- Refs were observed on 2026-10-22 and may have advanced. Record actual checked-out SHAs before changing anything.
- RuneMobile embeds an ARM64 OpenJDK 21 JVM inside the Android app and runs RuneLite using a headless Java-side bridge.
- RuneMobile Java bridge: `runelite-client/src/main/java/net/runelite/client/headless/JavaBridge.java`. `JavaBridge.onClick(int,int,int)` feeds engine-targeted input; its in-game left-click path is a **state machine** and should not be replaced by uncoordinated raw mouse injection.
- RuneMobile mobile plugin config bridge: `runelite-client/src/main/java/net/runelite/client/headless/MobileUiBridge.java`. Plugin manager, config manager, event bus, and client thread are already available here. Prefer reusing RuneMobile's existing plugin-config UI before writing a new Kotlin/JNI UI.
- RuneMobile Android project: `android/`; authoritative instructions: `android/README.md`.
- Microbot core: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/Microbot.java`, `Script.java`, and `util/` classes. Microbot's `Script` depends on many internal components, not just RuneLite interfaces; copying a handful of files does **not** establish parity.

## Instructions to Codex

You are taking over implementation as lead Java/Kotlin/Android engineer. Your objective is a **buildable Android APK** integrating Microbot scripting with RuneMobile, with the ability to run **at least one real, upstream Microbot script without rewriting its core logic**, followed by extending API coverage. Work directly in a local RuneMobile checkout; use a separate branch and incremental commits. If real full parity cannot be reached, ship a working, clearly scoped subset and accurate compatibility report. Never report success based solely on mocks or stub compilation.

### Immediate actions (do these first)

1. Inspect supplied ZIP and `README.md`, `docs/ARCHITECTURE.md`, and `docs/COMPATIBILITY.md` before modifying code. Review code for bugs and unsupported assumptions.
2. Clone/inspect both upstream repos. Record their exact commit hashes and Java/Gradle/Android versions. Read RuneMobile `android/README.md` fully. Consult upstream `AGENTS.md` files if present and follow repository instructions.
3. Create a feature branch, for example `feat/android-microbot-compat`. Preserve upstream RuneMobile without committing downloaded JRE binaries, SDK files, credentials, signed keystores, or private configs.
4. Install the Phase 2 patch, not the superseded starter. If a previous patch exists, use `install.sh --upgrade <path>` only after checking the files; it moves recognized previous sources to `runelite-client/build/`.
5. Execute tests and **real Gradle compilation**. Fix all compilation errors against the **actual** RuneMobile code before proceeding. Document every error and change.
6. Compare API/client differences between the forks. Build a transitive dependency graph and prioritize the minimal real Microbot runtime needed to execute an upstream example script.
7. Implement source-level compatibility iteratively rather than fake `net.runelite.client.plugins.microbot.*` shims that claim completeness. For each implemented API, add a real test and track unsupported behavior.

### Requested end state

**Runtime:** Port or adapt Microbot's script lifecycle, scheduler/cancellation, `Microbot` singleton where applicable, event/state propagation, heartbeat, and client-thread-safe access. Correctly handle startup, pause/stop, exceptions, login/logout, world-hop, disconnect, disable-plugin, Android pause/resume, and app termination. Ensure input queue drains or cancels safely on stop/logout and scripts never secretly auto-resume.

**Microbot API:** Enable practical support for the most common APIs used by actual scripts, prioritizing `Rs2Player`, `Rs2Inventory`, `Rs2GameObject`, `Rs2Npc`, `Rs2Walker`, menus, widget interactions, bank, equipment, item/scene caches, and action-based input. Carefully audit signature and semantic compatibility, not just class names. Cross-check RuneLite game revision and cache changes.

**Input:** Route interactions through RuneMobile-supported input facilities and correct client-thread scheduling. Do not block the game thread with sleep or pathfinding. Preserve RuneMobile's `JavaBridge` tap-settle behavior and avoid duplicate clicks. Provide a manual-user-input override/stop priority. Use explicit state/acknowledgement for action results; do not infer game success from an enqueued click.

**Navigation:** Move beyond the current `Rs2Walker`'s 15-tile minimap-only behavior. Add route planning, collision awareness and game-action confirmations as needed. Address doors/transports in stages, clearly distinguishing available and unsupported modes. Be prepared to port Microbot shortest-path / walker dependencies; do not claim whole-world pathing until tested.

**Scripts:** Aim for **source compatibility first**: compile a selected upstream example script against the Android build unchanged (except a lightweight platform adapter if strictly necessary), execute it in a controlled environment, and document its API coverage. **Binary JAR compatibility** is a separate milestone: arbitrary precompiled desktop JARs may require matching RuneLite/Microbot revision, Java bytecode version and dependencies. Never automatically load arbitrary downloaded JARs or silently run untrusted code.

**Android controls:** Use existing RuneMobile Plugins UI/config for early success. If needed, expose a read-only bridge for list, status, error, and explicit start/stop; only add Kotlin Compose + C++ JNI wiring after core runtime passes tests. Provide a visible stop/kill switch and logs.

**Packaging:** Make a reproducible build for ARM64 Android. Output installable APK **if toolchains and signed artifacts can actually be built**; otherwise output a clean source patch/branch, build logs, exact failure diagnostics and verified next commands. Do not assert an APK exists without checking its actual file and installability.

### Detailed test and acceptance gates

- `./scripts/test-portable.sh` continues to pass its **20 local stand-in tests**. These are a baseline only.
- `./gradlew :client:compileJava` completes against real RuneMobile source; then unit/integration tests pass. Record whether actual APIs match the current patch and fix genuine API differences.
- A selected **actual upstream Microbot script** compiles using expected package/class signatures and can be enabled/disabled with real runtime semantics. Capture class name, commit/version, specific compile invocation and runtime test evidence.
- Test script state transitions: start, stop, script swap, logout, reconnect, world-hop, plugin disable, crash/fail-close, touch/manual takeover, background/foreground, service/VM restart. Check no double-input, deadlocks, cached stale scene references, unexpected auto-restart, or unreleased keys.
- Confirm game actions **by observing game state after action**, not only Java click enqueue return values.
- Android native/JNI build stages, APK assembly, install and device launch pass if SDK/NDK/JRE and a compatible ARM64 emulator/device are available.
- Add automated integration tests and a machine-readable compatibility matrix (`API_SUPPORTED.md`) marking **native / adapted / partial / unsupported** with tests; do not globally label Android as "Microbot compatible" until evidence supports it.
- Track performance: CPU, FPS, memory, thermals, lifecycle stability for long sessions. Do not promise screen-off/background operation unless measured and Android lifecycle behavior supports it.

### RuneMobile's documented Android build sequence

Run from RuneMobile repository root (after addressing toolchains and actual patch integration):

```bash
tools/stage-android-jre.sh
./gradlew :client:shadowJar
( cd ios/app && ./build-probes.sh )
android/dev-tools/gpu/stage-android-gpu-jar.sh
android/dev-tools/gpu/stage-rlhd-zone-jar.sh
tools/stage-mobile-runtime.sh --no-build --android-only
( cd android && ./gradlew :app:assembleRelease )
```

**Do not substitute bare `./gradlew assemble`**. README says Android boot expects staged GPU/117HD/probes artifacts with proper runtime-tag hashes. See `android/README.md` for current guidance and `android/dev-tools/ci/release-gate.sh` if available. The JRE's `.so` libraries must be staged under Android app assets (not duplicated into `jniLibs/`) per RuneMobile documentation.

### Starter patch installation commands

```bash
# Put the two ZIPs and this handoff somewhere accessible to Codex.
unzip runemobile-microbot-phase2.zip

git clone https://github.com/audaro/runemobile.git
cd runemobile
git checkout -b feat/android-microbot-compat

/path/to/runemobile-microbot-phase2/install.sh "$PWD"
./gradlew :client:compileJava

# Run portable tests separately, from the extracted patch directory:
cd /path/to/runemobile-microbot-phase2
./scripts/test-portable.sh
```

Optional build helper in patch:

```bash
./scripts/build-android.sh /path/to/runemobile --compile-only
./scripts/build-android.sh /path/to/runemobile
```

The helper depends on SDK/NDK/JRE and upstream stage scripts. Fix it if latest RuneMobile has changed rather than pretending success.

### Dependencies and integration risks

1. `Microbot.java` and `Script.java` reference player/item/npc/object caches, blocking event manager, `ScriptHeartbeatRegistry`, `SessionFatigue`, `InputArbiter`, `Rs2Walker`, `Rs2PathApi`, shared services and Swing-era UI code. Port transitive dependencies carefully.
2. RuneMobile embeds its JVM inside Android; native Kotlin runs in Android/ART and cannot call arbitrary Java desktop APIs without bridges. Do not assume AWT `Robot`, Swing windows, desktop file pickers or OS-specific paths work.
3. Microbot's desktop fork may have RuneLite API additions and mixin changes not in RuneMobile; merging only `runelite-client/plugins/microbot/` is insufficient. Keep fork's headless/GPU/117HD and user-input modifications intact.
4. RuneMobile's `MobileUiBridge` already provides a plugin UI surface, minimizing early JNI work. A custom Kotlin overlay is a nice-to-have after core script functionality.
5. Avoid broad security-evasion, spoofing or anti-detection claims. Unofficial clients and botting may violate OSRS rules and risk account sanctions. Use explicitly authorized test environments.

## Milestone sequence (do not skip the build gate)

- **M0 — Inventory/source alignment:** Pin SHAs; inspect all repository agent guidelines, Gradle, SDK/JRE, existing patch. Produce real compatibility/diff list.
- **M1 — Real RuneMobile client compile:** Patch compiles with actual RuneLite APIs; portable tests + direct project tests pass. Fix input state issues; preserve manual stop and disconnect behavior.
- **M2 — Microbot core runtime:** Script lifecycle, scheduler, singleton/service wiring, event/input arbitration; deterministic tests for cancellation and recovery.
- **M3 — API compatibility:** Inventory/player/objects/NPCs/menu/walker/widgets/banking minimum APIs needed by one upstream script; documented gaps.
- **M4 — Real script:** Compile and run at least one upstream Microbot script with traceable tests; no misleading claims about arbitrary JARs.
- **M5 — Android APK:** Stage JRE/GPU/117HD/probes/runtime, build, install on ARM64 emulator/device, verify taps/actions, start/stop and lifecycle.
- **M6 — Expand parity:** Broader script compatibility matrix, fuller navigation, logs/config/UI and performance tuning. Full parity is accepted only when proven per API and script.

## Deliverables Codex should produce

1. Local feature branch/patch with working source, unit and integration tests, and minimized changes to RuneMobile.
2. `BUILD_REPORT.md` with commands actually executed, exit codes/results, exact SHAs, SDK/NDK/JDK versions, APK SHA-256 if produced, blockers and failure log excerpts.
3. `API_SUPPORTED.md` with per-module status and tested methods.
4. `SCRIPT_COMPATIBILITY.md` with script names, upstream commit and run/compile evidence. Clearly separate **source compatibility** from **binary JAR compatibility**.
5. Android test checklist/results and instructions to reproduce the APK build.
6. Actual APK with verified path if successfully built; otherwise a source archive plus precise unresolved blockers. Never invent verification results.

**Operating rule:** Be implementation-driven. Investigate, change code, compile, test, fix, and repeat. If a dependency or Android toolchain blocks one milestone, complete independent work and leave a clear evidence-backed blocker. No unsupported "fully working" claims.
