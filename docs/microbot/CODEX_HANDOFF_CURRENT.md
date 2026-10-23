# RuneMobile / Auto Woodcutting handoff

Updated 2026-10-23 UTC. This file describes repository state and follow-up work;
it does not grant additional authorization beyond the user's instructions.

## Repository and user goal

- Repository: https://github.com/thomasleeblankenship-svg/rsmod
- Branch: `feat/android-microbot-compat`. Keep this branch; do not force-push or change main.
- Latest implementation commit: `024f3872c04ca9c8ce3eaa077903f349d459c8a8`.
- User has authorized continuing fixes, commits and pushes on this branch.
- Before more work, run `git pull --no-rebase origin feat/android-microbot-compat`.
  Preserve other contributors' changes.
- Original goal: port uploaded desktop Auto Woodcutting v1.8.7 to mobile,
  including banking, Forestry, firemaking and fletching, with a downloadable APK.
- User preferences: **auto-hop off**, **automatic bank selection only**.
- User's latest request is to save a new handoff before usage runs out.

## Current live-test reports and fixes

The user tested the previous APK (`ce1baea`, GitHub run 39902376810). Reports:
full inventory drops one item, cuts a tree, drops one item and repeats; protect
an inventory axe; starting upstairs at Lumbridge bank attempts a ground route
instead of descending. User also requested a debuggable build to read private logs.

Implementation commit `024f3872` addresses these:

1. `Rs2Inventory.dropAllExcept` stays inside a synchronous batch until all
   eligible items are gone, refreshing the actual inventory after every drop.
   It does not return success just because one slot became free. Each dispatched
   drop needs observed quantity reduction. A bounded 56-drop cap handles some
   additional logs from an already-running chop; continued refill stops with a
   diagnostic. Failure and completion are logged.
2. `MobileActions` opens the inventory with client script 915/tab 3, waiting
   for visible widgets. Corrected fallback mapping from item definition actions
   to inventory widget operations: definition indices 0..4 map to widget
   ordinals 2,3,4,6,7. In the actual pinned injected client, ordinal 5 is Use,
   not Drop. Prefer live widget actions; bank contexts do not use this fallback.
3. Axes are protected in both the batch filter and direct Drop dispatch,
   independently of configured keep names. Woodcutting axe recognition handles
   name tokens and known leagues tool IDs; pickaxes are not woodcutting axes.
   Preflight checks inventory and equipment consistently.
4. Walker, object snapshots and interaction guards use the local player's
   actual floor instead of a stale world-view plane. Lumbridge stair matching
   resolves nearby live staircase IDs/actions (including legacy IDs), then
   dispatches the actual resolved ID. It must observe each floor transition
   before starting ground travel.
5. Build helper accepts `--debug`; GitHub mobile workflow builds/uploads a
   debuggable APK and checks its debug flag and absence of bundled tokens.json.

**These fixes have not been retested on a phone. Do not claim live-game success.**

## Validation completed

- 222 focused Gradle client tests: zero failures, errors or skips.
- `:client:checkstyleMain` and `:client:pmdMain` passed.
- Bridge/auth contract-only release preflight passed.
- Full local ordered Android **debug** assembly passed.
- APK v2 signature and 16 KB native-library ZIP alignment passed.
- Package dev.audaro.runemobile, version 0.2.6/code 206, ARM64, Android 9+
  (min SDK28, target35), application-debuggable confirmed.
- All 133 selected Microbot/mobile/input class files in packaged GPU jar match
  compiled classes; collision resource matches source; embedded commit 024f387.
- No bundled tokens.json.
- New integration tests exercise all 27 logs plus an axe, hidden inventory,
  rebuilt item widgets, changed slots and cancellation; real walker/read/action
  adapters require two Lumbridge descents before ground travel.
- Initial combined rerun exposed test-order initialization of JavaBridge's
  static flag. New integration tests now set runelite.bridge=true before loading
  it. Final full focused suite passed; this was not a live input failure.

Evidence: `docs/microbot/build-logs/drop-stairs-checks.log`,
`woodcutting-debug-build.log`, `debug-apk-signature.log`,
`debug-apk-alignment.log`, `debug-apk-badging.log`.

## APK and browser delivery (next step)

Verified local APK:
`/workspace/artifacts/runemobile-woodcutting-debug-024f387-arm64.apk`

- Bytes: 117855166
- SHA256: 5957ed9940c084fa8db0962bbb9911eec3cbbbe70f63f5f92f25782b9cf1058e
- Alias: `/workspace/artifacts/runemobile-mobile-debug-arm64.apk`
- Metadata: `docs/microbot/apk-artifact.json`.

Codex web workspace/sandbox links previously failed for this user. Deliver the
**GitHub Actions artifact** instead:

https://github.com/thomasleeblankenship-svg/rsmod/actions/runs/39908268468

Artifact expected: `runemobile-mobile-debug-arm64`, containing APK,
SOURCE_COMMIT.txt and SHA256SUMS.txt. The independent GitHub run completed successfully: contract preflight, runtime
checks, build, APK verification and upload all passed. Artifact ID: 12481912474.
Direct browser download (signed-in GitHub required):

https://github.com/thomasleeblankenship-svg/rsmod/actions/runs/39908268468/artifacts/12481912474

GitHub builds use their own key/hash; local hash above is not
proof of remote APK identity.

Next actions:

1. The artifact is ready at the direct link above; no rebuild is needed for
   handoff-only documentation commits.
2. Provide that link and the instructions below to the next tester.
3. Keep artifact metadata and this handoff current after future source changes.
4. Give user download/install instructions and ask them to retest one full
   Drop logs inventory (axe retained), then starting at Lumbridge bank upstairs.
   Manual taps intentionally cancel script input. Re-enable Run script afterward.

GitHub API/gh returned Forbidden earlier, but public GitHub HTML works through
curl. Do not mistake API restrictions for a total GitHub outage. Fetch public
job HTML and parse `<check-step data-name="..." data-conclusion="...">`;
null means not completed. Run HTML contains `data-artifact-id="..."` once
uploaded. Download requires the user's signed-in GitHub browser.

## Read private logs from debug APK

With Android platform-tools, authorized USB debugging and this debug APK:

```sh
adb shell run-as dev.audaro.runemobile ls files/.runelite/logs
adb exec-out run-as dev.audaro.runemobile cat files/.runelite/logs/client.log > client.log
```

A release APK rejects run-as. If a prior install uses another signing key,
Android may require uninstalling it first, which removes its app data.
There is no connected phone or emulator available in this cloud workspace.

## Build / check commands

```sh
cd /workspace/rsmod
source scripts/activate-java.sh
./gradlew --no-daemon --max-workers=4 :client:test \
  --tests 'net.runelite.client.plugins.microbot.*' \
  --tests 'net.runelite.client.plugins.mobilemicrobot.*' \
  --tests 'net.runelite.client.headless.JavaBridge*Test' \
  --tests net.runelite.client.callback.ClientThreadOptionalTest \
  :client:checkstyleMain :client:pmdMain
android/dev-tools/ci/release-gate.sh --contract-only
# Refresh embedded HEAD only after implementation commit; upstream resource
# task does not otherwise declare Git HEAD as an input.
./gradlew --no-daemon --max-workers=4 :client:processResources --rerun-tasks
bash scripts/build-android.sh /workspace/rsmod --debug
```

Helper follows JRE -> shadowJar -> probes -> GPU -> RLHD -> runtime staging ->
assembleDebug. Never use bare assemble for a fresh scripting build.
JDK21, Gradle8.8, SDK35, NDK26.1/27.0 and CMake3.22.1 are under
`/workspace/tooling`; activation sets writable caches and verified proxy trust.
Do not print environment secrets or change trust validation.
Local verifier script: `/workspace/tooling/verify-debug-apk.py` (not tracked).

## Architecture and remaining limits

MobileMicrobotPlugin installs runtime with List.of(readServices), removes it on
stop and offers STATE_MACHINE_EXAMPLE/AUTO_WOODCUTTING. Real taps take priority
through runtime human-input notification and owning ScriptInputSession
cancellation. Script waits are off the client thread; reads and menu dispatch
run on the client thread, limited to one engine action per game tick.

MobileMicrobotConfig explicitly forwards all 27 original config methods and
3 sections, necessary because ConfigManager uses declared members for defaults
and settings UI. Preserve this when adding config.

The full desktop workflows were source-ported, but desktop parity is unproven.
Ships, teleports, other stairs/ladders, instanced cross-region routing and desktop
anti-ban/fatigue support remain unavailable. Same-plane routing uses a pinned
collision map. Forestry/production/banking workflows all require phone tests.

Original uploaded source is under
`/workspace/tooling/autowoodcutting-original/microbot-autowoodcutting-src`;
tracked fingerprints and port notes are inside the woodcutting source folder.
Other agents' earlier runtime/API reports may describe obsolete staging blockers;
current code and this handoff supersede those status statements.
