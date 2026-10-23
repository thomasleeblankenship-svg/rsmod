> Supplied Phase 2 reference. For current evidence see [BUILD_REPORT.md](../BUILD_REPORT.md).

# RuneMobile + Microbot-style integration — Phase 2

**Status: enhanced source patch, successfully compiled/tested against local stubs; NOT an Android-built APK or full upstream Microbot port.**

Target: [audaro/runemobile](https://github.com/audaro/runemobile) (`master`), reference: [chsami/Microbot](https://github.com/chsami/Microbot) (`main`).

## What is genuinely implemented

- RuneLite plugin with mobile-config script selection and Run toggle, usable from RuneMobile's **existing plugin config UI**.
- Independent, explicitly bounded game-tick scripting API with the `MobileScript` lifecycle.
- Instance-scoped Microbot-inspired `Rs2Inventory`, `Rs2Player`, `Rs2GameObject`, and **same-scene/minimap-only** `Rs2Walker` facades. These are in `mobilemicrobot`, NOT source/binary-compatible substitutions for desktop `net.runelite.client.plugins.microbot.*` classes.
- Diagnostics, scene scanner, and small woodcutting example, with one generated input per script tick context.
- Auto-stop on full inventory for the woodcutting example; stop after login screen/disconnection and don't auto-resume when RuneMobile reauthenticates.
- JSON status, list, start/stop facade (`MobileScriptBridge`), callable from the embedded Java VM. The Android native `JvmLauncher` and C++ bindings **are not patched**; existing mobile plugin config is the user-facing control plane.
- Upgrade-safe install, a reproducible upstream Android build wrapper, and 20 locally passing smoke tests using generated *test-only* RuneLite stand-ins.

## Still NOT finished (do not confuse with full Microbot support)

- Whole-source alignment of Microbot's heavily modified RuneLite fork and its module graph.
- Original desktop Microbot `Script`, `Microbot` singleton, caches, blocking event manager, pathfinding, banking, menus, NPC interaction and anti-detection modules.
- Loading existing compiled Microbot script JARs. `MobileScript` is a new interface and desktop Microbot scripts will **not run unchanged**.
- A custom native Kotlin Compose script overlay or full reverse-JNI binding to `MobileScriptBridge`.
- ARM64 Android SDK/NDK/JRE, full Gradle compile, GPU/HD runtime build, APK install, live device input verification.

## How to install

```bash
git clone https://github.com/audaro/runemobile.git
cd runemobile
/path/to/runemobile-microbot-phase2/install.sh "$PWD"
# If the older starter was already installed:
/path/to/runemobile-microbot-phase2/install.sh --upgrade "$PWD"
```

Backups made by `--upgrade` go in `runelite-client/build/`, outside Java source scanning.

## Validation

Portable synthetic compile + 20 behavioral tests (runs on Java 21, no external dependencies):

```bash
./scripts/test-portable.sh
```

These tests exercise the *local adapter and state control*. They do not validate actual RuneLite binary compatibility, navigation, or input mapping on an Android device. A local `javac` success against simplified stand-ins is not a claim of a real RuneLite compile.

On a machine with the RuneMobile checkout and its tools/dependencies:

```bash
./scripts/build-android.sh /path/to/runemobile --compile-only
./scripts/build-android.sh /path/to/runemobile
```

Follow RuneMobile's exact required staged GPU/117HD/probes/JRE build. Building the full APK requires Android SDK, NDK and downloaded verified OpenJDK JRE/toolchains; these are unavailable in this execution environment.

## Runtime testing procedure

1. Open your *self-built* APK on an Android 9+ ARM64 phone/emulator.
2. In RuneMobile's Plugins list, enable **Microbot Mobile (phase 2)**.
3. Select `Diagnostics`, toggle Run, verify tick/status output. Then select `Scene scan` (read-only) and verify a scene count.
4. Log out and back in: the Run toggle should remain **off**.
5. In a controlled test environment, stand near one accessible tree with an axe and one free inventory slot, enable Woodcutting. Stop from config. If full, it should stop itself.
6. Disable the plugin and verify `MobileScriptBridge` becomes not-ready. Do not assume a successful in-game action from the `click()` return value; it means input was enqueued.
7. Check long-session memory/thermal behavior, native focus, disconnect/reconnect, screen off/on, client update/revision, and any stale menu clicks.

## Safety & provenance

An unofficial mobile client and scripting on live OSRS can violate Jagex rules and risk account sanctions. No detection-evasion or antiban guarantee is made. Never sideload unknown JARs or accept arbitrary remote code over JNI. No remote server, key storage, or script download code is included.

See `COMPATIBILITY.md` for the Microbot differences and migration matrix.
