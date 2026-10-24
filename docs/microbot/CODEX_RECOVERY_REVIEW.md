# Codex review: input and walking recovery

Review completed against `0e137e9c` on `feat/android-microbot-compat`.
Scope: independent source inspection and existing regression tests; no runtime,
walker, transport, bank or shared-fixture edits.

## Follow-up: combined manual-input recovery coverage

The user confirmed Lumbridge banking is already solved. This follow-up adds
regression coverage only; it changes no bank, walker, runtime or shared-fixture
implementation.

New `MobileBankRecoveryIntegrationTest` uses the real installed read services,
script session, JavaBridge scene-picking gate, walker, and bank adapters against
a simulated renderer/game engine:

- A native contact during a bank approach keeps the same session active and
  enters the human-input pause. The run subsequently resumes, observes both
  upstairs transitions, opens the bank, deposits all 27 logs once, retains the
  axe, closes the bank, descends both floors and returns to its start tile.
- Explicit Stop during that pause ends the trip promptly, leaves all 27 logs
  and the axe intact, clears the walking target, and never opens/deposits at
  the bank.

The contact enters through `JavaBridge.armTap` and cancels the arm (as when a
contact becomes a drag). This exercises manual-input arbitration; it does not
simulate a completed Android touchscreen tap. Existing bridge tests separately
cover tap/key dispatch and epoch invalidation. No phone success is inferred.

Both new tests passed. Against base `cc81c24f` plus this test, the full shared
focused check passed: **550 tests, zero failures/errors/skips**, client
compilation, Checkstyle and PMD. Local log:
`/workspace/tooling/codex-recovery-full-checks.log`. This closes the combined automated acceptance gap described in
finding 3 below; that finding describes the earlier reviewed revision.

## Follow-up: interrupted-trip lifecycle coverage

Two more cases in `MobileBankRecoveryIntegrationTest` cover lifecycle changes
while the real-adapter bank approach is active:

- Background/foreground through `JavaBridge.setScriptInputSuspended`: the old
  session becomes inactive, the trip ends, no bank opens or deposits occur,
  inventory remains intact, and foregrounding cannot send input under that
  old permission.
- Logout/reconnect through the real `MobileMicrobotPlugin` event handler: the
  session closes, Run script is cleared through the configuration manager,
  the trip ends without banking and reconnecting does not renew the permission
  or turn Run script back on. Logout invalidates read caches, so the test also
  inspects the simulated engine inventory instead of assuming cached counts.

These tests use an injected owning session and the real plugin's logout hook;
its startup/scheduler is not launched. They exercise the Java lifecycle bridge
and event handler, not an Android process being backgrounded or killed.

The four combined-trip cases passed. Full shared focused verification against
base `61100b9d` plus these tests passed: **552 tests, zero failures/errors/skips**,
client compilation, Checkstyle and PMD. Local log:
`/workspace/tooling/codex-lifecycle-full-checks.log`.

## Result

All 63 selected tests passed, with zero failures, errors or skips. Compilation
completed. This review did not build an APK or exercise a phone/live game.

| Area | Evidence | Practical limit |
| --- | --- | --- |
| Manual tap resumes the same run | `JavaBridgeScriptInputTest`: native touch drops pending input without closing the session; old queued taps/keys cannot execute; later script input can continue | Tests use mocked client and actual AWT queue, not Android touch delivery |
| Explicit Stop/background/logout | Same suite checks revoked sessions, queued input, balanced release and no renewal after backgrounding | Device lifecycle still needs a phone check |
| Stalled walking | `MobileWalkerIntegrationTest`: observes a stalled retry, then arrival after unfreezing; Stop interrupts a retry | Tests shorten the stall window to 300 ms; production retries remain bounded by the overall 120-second deadline |
| Lumbridge upstairs departure | Integration test requires observed 2→1→0 transitions before ground travel; an accepted stair click alone cannot count as arrival | Live staircase geometry/actions have not been established by this review |
| Bank operations | Bank read/action/workflow suites: observed deposits and withdrawals, axe retention, cancelled dispatch, bank memory reset | Not one combined Lumbridge walk→open→deposit→return test |
| Pathfinder transport execution | 19 `PathfinderLegTest` cases passed | Simulated boundaries, and only transport kinds present in this revision |

Counts: JavaBridgeScriptInputTest 21; MobileWalkerIntegrationTest 7;
PathfinderLegTest 19; Rs2BankReadTest 5; MobileBankWorkflowTest 2;
Rs2BankActionTest 9.

## Findings for the owners

1. **The consolidated handoff contradicts its newest APK section.** Its top
   section describes run 52/source ba0f905 as including the walking changes,
   but later sections still say manual-tap resume, walking retries and idle
   refresh are not in any published APK, and old loading instructions say taps
   cancel the run. Those are historical statements, not the current source
   behavior. Claude 1 should reconcile these statements against the artifact's
   SOURCE_COMMIT.txt before sending device instructions.
2. **Retrying does not mean unlimited walking.** The walker replans/backoffs
   after a stall, but retains a 120-second operation deadline. Bank callers can
   still return failure and the woodcutting script can stop after that deadline.
   This matches the documented bounded-retry design; communicate it explicitly
   if the user expects a run to survive a permanently blocked route.
3. **Combined recovery/banking remains an acceptance gap.** Current cases
   separately prove tap/session behavior, stairs, and inventory banking. Add a
   future combined test for manual input during a bank approach, resume, both
   Lumbridge transitions, observed bank opening/deposit and return. No combined
   success is inferred from the 63 passing component/integration tests.
4. **Repeated client starts remain unexplained.** No original startup/crash log
   was available for the earlier three-start report. These tests cannot identify
   a native crash, VM exit or process restart cause.

No runtime defect was established by this selected passing suite. No style or
PMD result is claimed for this review; runtime source was not changed.

## Suggested phone sequence

1. Start upstairs at Lumbridge bank with a usable axe, select the woodcutting
   script, and verify two descents before walking to trees.
2. Tap a reachable ground tile during a walk: the run must remain enabled,
   yield to the touch and resume after the human-input window.
3. While movement is blocked, observe a retry rather than a stop at 15 seconds;
   press Stop during recovery and verify input ends promptly.
4. Re-enable, fill inventory, bank automatically, retain the axe and return.
5. Background during movement: foregrounding must not resume that old run.
6. If the client restarts again, preserve client.log plus Android logcat around
   the process exit before attributing the cause.

## Reproduce this review

```sh
source scripts/activate-java.sh
./gradlew --no-daemon --max-workers=2 :client:test \
  --tests net.runelite.client.headless.JavaBridgeScriptInputTest \
  --tests net.runelite.client.plugins.microbot.api.MobileWalkerIntegrationTest \
  --tests net.runelite.client.plugins.microbot.util.walker.PathfinderLegTest \
  --tests 'net.runelite.client.plugins.microbot.util.bank.*'
```

Local log: `/workspace/tooling/codex-recovery-review.log`.
The prior unfinished Codex walking experiment is preserved in Git stash with
message `Preserve prior unfinished scene walking experiment before updated
handoff`. The shared implementation supersedes it; do not reapply it blindly.
