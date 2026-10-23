# Claude 2 catch-up (from Claude 1)

Written 2026-10-23 by Claude 1. Read this before resuming. It covers what changed after your
last pushed commit (`175f3c0` / merge `d6b4be0`, and the doc commit `5673151` on your branch)
and what is waiting for you. Like the other handoffs, it does not grant authorization beyond
the user's instructions.

## Who owns what now

- **Claude 1:** runtime (`Script`, `Microbot`, `MicrobotRuntime`, `statemachine/**`,
  adapter) and, at the user's direction, **everything Codex owned**: plugin
  integration, `JavaBridge`/input, walker, consolidated reports, APK delivery.
- **Claude 2 (you):** unchanged. Read-only APIs: `microbot/api/**`,
  `util/player/**`, `util/inventory/**` read paths, and your report.
- Codex is no longer active in this split.

## Sync first

```bash
git fetch origin
git checkout feat/android-microbot-compat
git pull --no-rebase origin feat/android-microbot-compat
```

Work on your own branch and merge `feat/android-microbot-compat` into it. Never rebase or
force-push the shared branch.

## What changed since your last commit

1. **`6f6a2cd`: manual taps no longer stop scripts** (user request).
   `ScriptInputSession` now separates the session's lifetime from a per-input
   **epoch**. A real tap or key calls `ScriptInputSession.manualInput()`, which bumps
   the epoch and calls `MicrobotRuntime.noteHumanInput()`. That drops script input
   queued before the tap and holds new script input for ~1.8 s, but `isActive()`
   stays true. Stop, swap, disable, logout, input faults and Android backgrounding
   still close the session for good; resuming never renews it. New API:
   `ScriptInputSession.inputEpoch()` and `session.allows(epoch)`.
   **Impact on you:** any code that treated `!session.isActive()` as "the user
   tapped" is now wrong. Use `MicrobotRuntime.humanOwnsInput()` to yield.
   `MobileActions.perform` already checks both.
2. **`ba30cb9`: walking fixed, and stalled walks retry** (user request).
   Codex's `pending-walk-resolution.patch` is applied, finished and removed.
   - `MobileActions.walk` → `JavaBridge.onScriptWalk(dest, session)`. This projects the
     tile to the canvas, waits for the settle gate, and sends WALK only when
     `getSelectedSceneTile()` equals the target. There is no ordinary-click fallback.
   - `Rs2Walker` no longer fails after 15 s without movement. It replans, takes short
     steps and backs off (1.2 s × stalls, capped at 6 s) within the 120 s deadline.
     The stall timer pauses while the human owns input.
     `Rs2Walker.setStallMillis(long)` is a test hook.
   - **Test infrastructure you will hit:**
     - `JavaBridge.ENABLED` is read once from `-Drunelite.bridge`, when the class first
       loads. Every test class that reaches `JavaBridge`, directly or through
       `MobileActions`/`Rs2Walker`, now sets `System.setProperty("runelite.bridge",
       "true")` in a `static {}` block. Do the same in any new such test, or results
       will depend on test order.
     - `SceneWalkFixture` (in `api` tests) now sets the top-level view size to 104×104,
       because `LocalPoint.fromWorld` rejects every tile of a zero-size scene.
3. **Docs:**
   - `API_SUPPORTED.md` and `SCRIPT_COMPATIBILITY.md` were rewritten from current
     evidence. This applied your report's recommendations, plus walker, input and
     woodcutting rows.
   - `WOODCUTTING_MOBILE.md` step 7 was fixed: manual taps no longer cancel the run.
   - `TEAM_ASSIGNMENTS.md` records the takeover.

Your earlier fixes (`bef9a1d` exact-name `contains`, `5b2490e` player-floor ground items,
`99adc8e` region-load state) are all in and passing.

## Verification at `ba30cb9`

Full focused suite on two consecutive runs: `:client:compileJava :client:checkstyleMain
:client:pmdMain` and 330 tests (`microbot.*`, `mobilemicrobot.*`, `headless.*`,
`ClientThreadOptionalTest`), with 0 failures and 0 skips. All tests use mocked boundaries.

```bash
export GRADLE_USER_HOME=<writable cache>
./gradlew --no-daemon --max-workers=2 :client:compileJava :client:checkstyleMain :client:pmdMain \
  :client:test --rerun --tests 'net.runelite.client.plugins.microbot.*' \
  --tests net.runelite.client.callback.ClientThreadOptionalTest \
  --tests 'net.runelite.client.plugins.mobilemicrobot.*' --tests 'net.runelite.client.headless.*'
```

`repo.runelite.net` must be reachable from your environment.

## Phone evidence so far (user reports, no logs attached)

- `024f387` debug APK: full-inventory drop **works**. Lumbridge upstairs-bank start:
  stairs **did not work**. The likely cause was the WALK bug fixed in `ba30cb9`.
- No build containing `6f6a2cd` or `ba30cb9` has been phone-tested yet. APKs come
  from the `Mobile test APK` workflow on every push to `feat/android-microbot-compat`.
  Run 7 (`6f6a2cd`) succeeded; run 8 (`ba30cb9`) was in progress when this was written.

## Suggested next work in your scope (M6)

Pick in this order unless the user directs otherwise:

1. **Varbit/varp reads.** `Microbot.getVarbitValue`/`getVarbitPlayerValue` are interim
   mobile shims: a client-thread read through `MobileActions.read`. Upstream reads
   `Rs2PlayerStateCache`. Port that cache (read-only, event-driven, cleared on logout)
   and I will switch the getters to it.
2. **`Rs2PlayerCache` / `Rs2PlayerModel`** (other players), the remaining
   unsupported cache from your coverage table.
3. **Widget read API** (`Rs2Widget` visibility/text/bounds reads, client thread only).
   This is the biggest blocker for porting more upstream scripts. Reads only; I own
   any interaction side.
4. Keep `docs/microbot/api-supported.json` current when you add APIs. Claude 1 regenerated it at `ba30cb9`.

Send me any change you need in `Microbot`, `MicrobotRuntime` or the input path as a
patch or a note in your report, as before.
