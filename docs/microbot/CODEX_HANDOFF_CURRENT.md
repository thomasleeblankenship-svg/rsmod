# RuneMobile / Auto Woodcutting handoff

Updated 2026-10-23 UTC. This file describes repository state and follow-up work;
it does not grant additional authorization beyond the user's instructions.

## Latest APK (read first)

"Mobile test APK" run 52, built from `ba0f905`. Every step passed. Since run 26 it adds: 33 Hub
plugins and KSP Chopper (all disabled by default; the free-to-play test set and the KSP notes are
in `HUB_PORTS.md`), world hopping, inventory setups, magic and prayer actions, the mobile mouse
and deposit box, and the webwalker on the pathfinder (stairs, doors, shortcuts, boats, ships,
travel NPCs, charter ships, teleport items and spells, fairy rings, spirit trees). Artifact
`runemobile-mobile-debug-arm64` (id 12540392687, expires 2026-11-07), downloaded while signed into
GitHub:
https://github.com/thomasleeblankenship-svg/rsmod/actions/runs/40043132346/artifacts/12540392687
Runs 48–49 failed PMD (`MissingOverride` in `Rs2PlayerModel`), fixed in `789a426`.
Not phone-tested. Retest: the stairs from the upstairs Lumbridge bank; a tap during a run (the
script should resume); one full drop/bank cycle; then enable one Hub plugin from the plugin list
(for example Fishing Trawler) and check that it stops on logout and when the app is backgrounded.

## Latest user instructions — supersede older cancellation requirements

The user explicitly requested on 2026-10-23:

1. **Remove manual-tap cancellation.** A screen tap must no longer permanently
   stop the script or require toggling Run script again. Preserve explicit Stop,
   disable, logout and background cancellation. Work out how pending generated
   input yields to the user's tap without invalidating the entire run. Existing
   manual-input tests/docs will need updating to the intended new behavior.
2. **Retry stalled walks instead of stopping after 15 seconds.** The user
   confirmed this interpretation in the clarification response. Replan/retry
   with a delay and observed movement; keep Stop responsive and avoid a busy
   infinite dispatch loop. Do not merely report queued input as successful arrival.
3. Save a fresh handoff before usage is exhausted.

**Item 1 is implemented in source (Claude 1), not yet in any published APK.**
`ScriptInputSession` now separates the session's lifetime from a per-input epoch:

- A real tap or key bumps the input epoch and calls `MicrobotRuntime.noteHumanInput()`.
  Any script tap or key queued before it is dropped (`JavaBridge` captures the epoch
  per queued input and checks `session.allows(epoch)` at every stage), and new script
  input is refused for the 1.8 s human-input window. The session stays active, so the
  script continues by itself afterwards, without toggling Run script.
- Stop, script swap, plugin disable, logout, input faults and Android backgrounding
  still end the session for good. Foreground resume never renews it.
- `Rs2Walker` pauses its 15 s stall timer while the human owns input.
  The pending walk patch also edits `Rs2Walker`, so merge it by hand.
- Tests: `JavaBridgeScriptInputTest` and `MobileMicrobotPluginTest` now assert the new
  behaviour (a tap drops queued input and keeps the run; backgrounding still stops it
  with no auto-resume). The focused suite of 328 tests, checkstyle and PMD pass, all
  against mocked boundaries.

**Item 2 and the walking fix are implemented in source (Claude 1), not yet in any APK.**
`pending-walk-resolution.patch` was applied and finished, then removed from the tree
(it is still in Git history):

- `MobileActions.walk` uses `JavaBridge.onScriptWalk`. This projects the tile to the
  canvas, waits for the settle gate, and dispatches WALK only when the renderer's
  selected tile equals the target. It never falls back to an ordinary click. Queued
  walks follow the same input-epoch rule as taps: a manual tap drops them.
- `Rs2Walker` no longer stops after 15 s without movement. Each stall replans from the
  observed position, switches to short steps and backs off (1.2 s × stall count, capped
  at 6 s), bounded by the 120 s walk deadline. Stop or interrupt still ends it at once.
  An earlier replan happens at half the stall window.
- Fixture fixes: scene size 104×104 so `LocalPoint.fromWorld` accepts tiles, and every
  test class that reaches `JavaBridge` sets `runelite.bridge=true` in a static block,
  so `JavaBridge.ENABLED` no longer depends on test order.
- New tests: `stalledWalkRetriesInsteadOfStoppingAndThenArrives` and
  `stopEndsAStalledRetryingWalkPromptly`. The Lumbridge two-floor descent test passes
  with the scene-resolved walk.
- Full focused suite: 330 tests, checkstyle and PMD pass, on two consecutive runs. All
  of these use a mocked engine and renderer, so this is **not phone evidence**. The stairs
  were reported broken on the `024f387` APK; retest them on a new build.

Neither change is in the published debug APK; a new APK build is needed before a
phone test. The requested behavior
supersedes statements later in this file that describe cancellation in the old
implementation.

## AFK logout while a script runs — fixed in source, not yet in an APK

User report 2026-10-23: kicked to the login screen mid-script and the client
did not log back in. Cause: script actions are exact menu dispatches, and the
pinned injected client counts only canvas mouse/key events toward its idle
counter (`tk.mouseMoved` zeroes it; `client.getMouseIdleTicks` has no setter;
`setIdleTimeout` clamps to 15000..90000 ticks). Five minutes after the last
real touch the engine logs out straight to LOGIN_SCREEN with no
CONNECTION_LOST, so HeadlessLogin's reconnect never re-arms.

Fix: `JavaBridge.refreshScriptIdleTimer()`, called from `MobileActions.perform`
after each dispatched action. Once the mouse has been idle for one minute it
posts one MOUSE_MOVED at the pointer's existing point (or the engine cursor if
there was no touch yet). No new coordinates, no manual-input mark, nothing
while a button is held or a script tap (including scene walks) is settling.
Tests: five new cases in JavaBridgePointerStreamTest.
Merged with the tap-yield and walk-retry commits; the focused suite (247
tests), checkstyle and PMD pass. Not phone-tested, and
this does not cover a script that dispatches nothing for 4+ minutes.

## Walking investigation in progress — preserve this work

Latest user log summary: client started three times around 06:04–06:05, then
stayed up. Three script stops while walking: bank stall at 06:10:50, manual tap
just before interrupted walk at 06:11:15, and progressive tree-location stall
at 06:11:38. Both stalls were 15 seconds without movement. Original raw startup
logs were not supplied in this turn, so the three starts have no confirmed cause.

Strong implementation finding: MobileActions.walk was passing scene tile x/y
to client.menuAction(WALK). Inspection of the pinned injected client bytecode
shows WALK arms viewport movement using the scene's already-picked tile; it
cannot select the desired world tile solely from those arguments. The old fake
engine test incorrectly treated those parameters as a destination, hiding this.

*Historical (superseded by the update above):* the experimental code below was kept in
`pending-walk-resolution.patch`, which has now been applied, finished and removed:

- JavaBridge.onScriptWalk projects a world tile to canvas, waits for the bridge
  settle gate and requires the renderer's selected tile to match before WALK.
  It never falls back to ordinary clicking (which might chop instead of walk).
- MobileActions.walk uses this scene-resolved path.
- Walker experiments try nearer waypoints when a long waypoint is offscreen,
  replan after 7.5 seconds and add diagnostics. These experiments STILL stop at
  15 seconds; replace that behavior to meet the new retry instruction.
- Tests gain SceneWalkFixture, a simulated renderer with the real bridge gate.

**The experimental patch has not passed tests and is not a new APK.**
First focused run failed MobileActionsTest dispatch assertions; the fixture did
not supply WorldView.getSizeX/getSizeY, so LocalPoint.fromWorld rejects every
point as outside a zero-size scene. Set fixture sizes (32) before rerunning.
Also corrected use of LocalPoint.fromWorld(view,x,y), avoiding its WorldPoint
variant's stale view-plane check; that correction was made during the first
run and needs fresh validation. Dedicated bridge tests for wrong selected tile,
settle timeout, Stop and manual input should be added. Test log:
`/workspace/tooling/walk-resolution-checks.log` (not passing).

Tracked modified files in that patch: JavaBridge.java, MobileActions.java,
Rs2Walker.java, MobileActionsTest.java, MobileWalkerIntegrationTest.java;
new test helper SceneWalkFixture.java. Do not overwrite them on resuming.
Implementation changes have not been committed so CI continues to build the
last verified source. Documentation commits alone do not trigger an APK build.

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

**Phone retest, user-reported 2026-10-23:** with the `024f387` debug APK, the user
reports that dropping a full inventory now works (fix 1). This is the user's report; no
logs or recording were attached. The user did not separately report whether the axe was
kept (fix 3) or how the upstairs Lumbridge bank start behaved (fix 4), so both remain
unconfirmed on a phone. Later walking-stall reports are tracked in the walking
investigation above.

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

**Newest GitHub debug APK (source `d6b4be0`):** run 39911072637 succeeded
(contract preflight, build, APK verification and upload). It adds two Claude 2
fixes on top of `024f387`: exact `Rs2Inventory.contains(name)` matching
(upstream semantics, so `"Logs"` no longer matches `"Oak logs"`) and
ground-item scans on the player's actual floor. It does **not** contain the
pending walk-resolution patch or the tap/retry changes requested above.
Artifact `runemobile-mobile-debug-arm64`, ID 12483336024, digest
`sha256:f72e0069b3fd3cc60c78f5f6d5f0b2a60cdba91e175eb1827bee0c0685fb5210`,
expires 2026-11-06. Direct browser download (signed-in GitHub required):

https://github.com/thomasleeblankenship-svg/rsmod/actions/runs/39911072637/artifacts/12483336024

The `024f387` details below remain the last locally verified build.

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
