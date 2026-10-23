# Claude 1 ↔ Claude 2 coordination log

This is the single shared channel between the two Claude sessions. Neither session can
message the other, so this file is the only place where claims and changes become
visible. **The user's rule: update after every change, never overlap.**

## Protocol (both sessions, every time)

1. **Before any work:** `git pull --no-rebase origin feat/android-microbot-compat`, then
   read this whole file.
2. **Claim before editing.** Add a row to *Active claims*, listing the exact files or
   directories, and push that commit **before** touching code. If a file is already
   claimed by the other session, do not edit it. Write a request under *Requests* instead.
3. **Ownership defaults** (see the table below) apply when nothing is claimed. Editing a
   file the other session owns needs a claim that names the owner's consent, recorded
   under *Requests*.
4. **After every pushed change:** append one entry to *Change log*: the commit, files,
   what changed, how it was verified, and anything the other session must do. Then
   release your claim, in the same push.
5. **Push conflicts:** if the shared branch moved, merge it (never rebase or force-push),
   re-read this file, re-run the focused tests, then push.
6. Keep entries factual: no claims of phone or live-game success without a user report.

Focused verification command (expect 0 failures and 0 skips):

```bash
./gradlew --no-daemon --max-workers=2 :client:compileJava :client:checkstyleMain :client:pmdMain \
  :client:test --rerun --tests 'net.runelite.client.plugins.microbot.*' \
  --tests net.runelite.client.callback.ClientThreadOptionalTest \
  --tests 'net.runelite.client.plugins.mobilemicrobot.*' --tests 'net.runelite.client.headless.*'
```

## Ownership defaults

| Area | Owner |
| --- | --- |
| `microbot/api/**`, `util/player/**`, `util/inventory/**` read paths, `claude2-api-report.md` | Claude 2 |
| `Script`, `Microbot`, `MicrobotRuntime*`, `MicrobotScriptAdapter`, `statemachine/**`, `util/Global.java`, `agentserver/**`, `callback/ClientThread.java` | Claude 1 |
| Former Codex scope: `mobilemicrobot/**`, `headless/**` (JavaBridge, ScriptInputSession), `microbot/mobile/**`, `util/walker/**`, `util/bank/**`, woodcutting port, CI/Gradle/Android, consolidated reports (`API_SUPPORTED.md`, `SCRIPT_COMPATIBILITY.md`, `api-supported.json`, handoffs) | Claude 1 |
| `util/inventory/**` **action** paths (drop/use dispatch from the woodcutting port) | Claude 1 (claim before touching; Claude 2 owns the read paths in the same files) |
| Shared test fixtures (`RuntimeFixture`, `ReadApiFixture`, `SceneWalkFixture`) | Claim before editing |

## Active claims

| Session | Files / directories | Purpose | Claimed at commit |
| --- | --- | --- | --- |
| Claude 1 | `runelite-client/src/main/java/net/runelite/client/plugins/PluginDescriptor.java`, `.../ui/overlay/Overlay.java`, new `microbot/PluginConstants.java`, new Hub plugin directories under `microbot/` (`autochompykiller`, `fishingtrawler`, `sandcrabs`, `birdhunter`, `salamanders`, `HueycoatlPrayer`, `microhunter`, `gotr`, `ourania`, `blackjack`, `sailing`, `crafting`, `geflipper`, `drofirecape`, `barrows`), `docs/microbot/HUB_PORTS.md` | User asked to load the 15 most recently updated Microbot Hub scripts: shared Hub base first (descriptor fields, `PluginConstants`, `Overlay.setNaughty`), then ports in order of fewest compile errors | this commit |

## Requests

Ask the other session for a change in a file it owns or has claimed. The owner answers
here, or makes the change and logs it.

| From → to | Request | Status |
| --- | --- | --- |
| Claude 1 → Claude 2 | Read-only `Rs2PlayerStateCache` (varbits/varps), then Claude 1 switches the `Microbot` getters | **Done.** Claude 2 delivered `7afa391`; Claude 1 reviewed it and switched `getVarbitValue`/`getVarbitPlayerValue` in `38887ac` |
| Claude 1 → Claude 2 | `Rs2PlayerCache`/`Rs2PlayerModel` and widget read API (see `CLAUDE2_CATCHUP.md`) | Player cache **done**: merged with `Microbot.getRs2PlayerCache()` in `aa0b32f`. Widget read API next (approved above) |
| Claude 2 → Claude 1 | Widget read API: upstream scripts call these as `Rs2Widget.*` statics, so they must go in your `util/widget/Rs2Widget.java`. May Claude 2 claim that file to **add** upstream's read-only methods: `getWidget(id)`/`(id, child)`, `isWidgetVisible(id)`, `isHidden` (2), `getChildWidgetSpriteID`, `getChildWidgetText`, `hasWidgetText` (2), `hasWidget`, `hasVisibleWidgetText`, `searchChildren` (text and sprite), `findWidget(spriteId, children)`, `is{GoldCrafting,SilverCrafting,Smithing,DepositBox}WidgetOpen`, `isWildernessInterfaceOpen`, `getWidgetsKeyMap`, `findBestMatchingWidget`, `calculateTextSimilarity`, `findSimilarWidgets`, and the waits `sleepUntilHasWidgetText`, `sleepUntilHasNotWidgetText`, `sleepUntilHasWidget`, `waitForWidget` (through `Global.sleepUntil`). All reads run on the client thread. Claude 2 will not change any existing method, any click, `handleProcessingInterface`, `enableQuantityOption` or `Rs2WidgetInspector`, and adds tests in new `test/.../microbot/util/widget/Rs2WidgetReadTest.java`. Reply *yes*, *no*, or *you do it* | **Yes (Claude 1).** Claim `util/widget/Rs2Widget.java` and the new test for exactly those read-only additions. Seven of the 15 Hub plugins call `Rs2Widget.getWidget(int)`, so please prioritise `getWidget`/`isWidgetVisible`/`hasWidget`. Release the claim when done |
| Claude 2 → Claude 1 | Accept and merge the idle-logout fix (`1dec16e`, `a7ea94d` on `claude/clever-shannon-9q9v09`). It edits your `JavaBridge.java` (new `refreshScriptIdleTimer`, `SCRIPT_IDLE_REFRESH_TICKS`) and adds one call in `MobileActions.perform`. It was made before this protocol existed, without your consent; sorry for the overlap. Change it as you see fit; I won't touch those files again | **Done (Claude 1):** reviewed and merged in `3a583b9`; 335 tests, checkstyle and PMD pass |
| Claude 2 → user | `Rs2PlayerStateCache`: Claude 2 drafted an adaptation of upstream's cache (varbits/varps only). Its permission check blocked wiring upstream-derived code until the user decides. The draft is outside the repo; nothing in `microbot/api/**` has changed | **Done:** user approved; delivered in `7afa391` and integrated by Claude 1 in `38887ac` |

Claude 2's earlier `docs/microbot/CLAUDE2_STATUS.md` is kept as history only. All new
claims, requests and log entries go in this file.

## Change log (newest first)

| Commit | Session | Files | Change | Verified | Action for the other session |
| --- | --- | --- | --- | --- | --- |
| `aa0b32f` | Claude 1 | merge of `609ac9e`; `Microbot.java`, `MicrobotRuntimeTest`, `API_SUPPORTED.md`, `api-supported.json` | Merged `Rs2PlayerCache`; added `Microbot.getRs2PlayerCache()`; report rows for both player caches | 360 tests, checkstyle and PMD | Widget claim approved (see *Requests*). Hub plugins will need more read APIs; Claude 1 will post a request list here |
| this commit | Claude 2 | `claude2-api-report.md` | Added M6 status and coverage rows for `Rs2PlayerStateCache` and `Rs2PlayerCache`/`Rs2PlayerModel` (supported and unsupported parts), plus design notes. Claim released | JSON block parses | Claude 1: copy the rows into `API_SUPPORTED.md`/`api-supported.json` when you merge `609ac9e` |
| `609ac9e` (on `claude/clever-shannon-9q9v09`) | Claude 2 | new `microbot/api/player/{Rs2PlayerCache,Rs2PlayerQueryable,models/Rs2PlayerModel}.java`; `MicrobotReadServices.java`, `MicrobotReadApi.java`; `ReadApiFixture.java` (new `players` list, `player(...)` helper, `topLevel.players()` stub, no other change); new test `test/.../microbot/api/PlayerQueryTest.java` (in `api`, not `api/player`, so it can use the package-private fixture) | Ported upstream `Rs2PlayerCache`: per-tick `TickSnapshotCache` of players across loaded world views (local player included, as upstream), `query()` via `AbstractEntityQueryable`; `getRs2PlayerCache()` on services and API. `Rs2PlayerModel` captures id, combat level, animation, friend/clan/friends-chat flags, skull and overhead icon on the client thread at snapshot (upstream hops per call). `click()` omitted per `IEntity`; salvage detection and the deprecated static stream not ported. Claim released | 6 new tests (filters/distance/plane, client-thread capture, same-tick reuse and rescan, logout, stalled client thread). Protocol command: compile, checkstyle, PMD, 353 tests, 0 failures/skips | Claude 1: merge the branch; add `Microbot.getRs2PlayerCache()` (same shape as `getRs2NpcCache()`) and the report rows |
| `38887ac` | Claude 1 | `Microbot.java`, `MicrobotRuntimeTest` | `getVarbitValue`/`getVarbitPlayerValue` read `Rs2PlayerStateCache` (0 when not installed); added `getRs2PlayerStateCache()`. The interim `MobileActions.read` shim is gone | 354 tests, checkstyle and PMD | None |
| `a5b41bc` | Claude 1 | `util/walker/**` (new `ObjectTransports`, `transports.tsv`; removed `LumbridgeStairs`), walker tests, `API_SUPPORTED.md` | Generic floor-changing transports: 2,410 requirement-free staircases, ladders and trapdoors from unchanged upstream `transports.tsv`. Unusable transports are skipped; bank selection's vertical check must land near the destination. Claude 1's walker claim is released | 342 tests, checkstyle and PMD | `Rs2Walker.canReachVertical` is now generic, not Lumbridge-only |
| `42fd967` | Claude 2 | `COORDINATION.md` (claim), and by mistake fast-forwarded `7afa391` | Claimed the `Rs2PlayerCache` files. Pushing my branch to post the claim also put `7afa391` (`Rs2PlayerStateCache`) on the shared branch before Claude 1's merge. Fast-forward only; nothing of yours was changed | `7afa391` passed the protocol command (347 tests) | Claude 1: no merge needed for `7afa391`; only the `Microbot` getter switch and report rows. Revert it if you object. From now on Claude 2 posts docs-only commits for claims |
| `7afa391` | Claude 2 | new `microbot/api/playerstate/Rs2PlayerStateCache.java` + test; `MicrobotReadServices.java`, `MicrobotReadApi.java` | Ported upstream `Rs2PlayerStateCache` (varbits/varps). `MicrobotReadServices` owns it, exposes `getRs2PlayerStateCache()` (also on `MicrobotReadApi`), routes `GameStateChanged`/`VarbitChanged` to it and clears it in `shutDown()`. A miss reads on the client thread through `services.read` and caches the value (zero included) only while `LOGGED_IN`; change events are cached only while `LOGGED_IN`; login screen, hop and connection loss flush; `LOADING` keeps. Unreachable client thread returns 0. Not ported: quest states (need Quest Helper) and the player-position cache. Claim released | 12 new tests in `Rs2PlayerStateCacheTest` (upstream's cases plus routing, region load, shutdown, unreachable thread). Protocol command: compile, checkstyle, PMD, 347 tests, 0 failures/skips | Claude 1: merge `claude/clever-shannon-9q9v09`; switch `Microbot.getVarbitValue` → `getRs2PlayerStateCache().getVarbitValue(id)` and `getVarbitPlayerValue` → `getVarpValue(id)` (upstream's bodies, via `MicrobotReadServices.require()`); add the API to `API_SUPPORTED.md`/`api-supported.json` |
| `3a583b9` + merge commit | Claude 1 | merge of `claude/clever-shannon-9q9v09` (`JavaBridge`, `MobileActions`, `JavaBridgePointerStreamTest`, `CODEX_HANDOFF_CURRENT.md`, `CLAUDE2_STATUS.md`) | Merged Claude 2's AFK-logout fix (`1dec16e`, `a7ea94d`): after a script action, once the pointer has been idle 1 min, post one `MOUSE_MOVED` at the existing pointer point; skipped while a tap or walk is settling. Reviewed by Claude 1: compatible with the input epoch and scene walk | 335 tests, checkstyle and PMD | Claude 2: **use this file, not `CLAUDE2_STATUS.md`** (user decision: one shared channel). No further edits to `JavaBridge`/`MobileActions` without a request here |
| `7cbd93c` | Claude 2 | `COORDINATION.md` | Logged Claude 2's changes and requests; Claude 2 now uses this file only | — | See *Requests* |
| `a7ea94d` | Claude 2 | `JavaBridge.java`, `JavaBridgePointerStreamTest` | Idle refresh is skipped while a script tap or scene walk is settling, so it can't move the cursor off the point the gate is resolving | 247 tests, checkstyle and PMD (merged with `ba30cb9`) | Part of the merge request above |
| `1dec16e` | Claude 2 | `JavaBridge.java`, `MobileActions.java`, `JavaBridgePointerStreamTest`, `CODEX_HANDOFF_CURRENT.md` | Fixes the AFK logout during scripts: menu dispatches never reset the engine's idle counter, so the user was logged out 5 min after the last touch, and the logout skipped CONNECTION_LOST, so autologin never ran. Once idle for 1 min, one `MOUSE_MOVED` is posted at the existing pointer point; manual input is not marked | 5 new tests; focused suite, checkstyle and PMD. APK: `Mobile test APK` run 39915628666 on Claude 2's branch. Not phone-tested | Merge (see *Requests*) |
| `ecf3045` | Claude 1 | `docs/microbot/COORDINATION.md`, `CLAUDE2_CATCHUP.md` | Added this protocol | — | Read and follow it from now on |
| `de70f41` | Claude 1 | `CODEX_HANDOFF_CURRENT.md`, `WOODCUTTING_MOBILE.md` | Linked the `ba30cb9` debug APK (run 8, artifact 12484268121) | CI run 8: every step passed | None |
| `d33435a` | Claude 1 | `api-supported.json`, `API_SUPPORTED.md` | Regenerated the API matrix from the 330-test run | JSON valid | Keep it current when adding APIs |
| `4ff2284` | Claude 1 | `API_SUPPORTED.md`, `SCRIPT_COMPATIBILITY.md`, `TEAM_ASSIGNMENTS.md`, `WOODCUTTING_MOBILE.md`, `CLAUDE2_CATCHUP.md` | Rewrote the consolidated reports; recorded the Codex-scope takeover; added the catch-up | Docs only | Read `CLAUDE2_CATCHUP.md` |
| `ba30cb9` | Claude 1 | `JavaBridge`, `MobileActions`, `Rs2Walker`, walker/action tests, `SceneWalkFixture`, tests that set the bridge flag | Scene-resolved WALK; stall retry with replan and backoff | 330 tests, checkstyle and PMD, two runs | New tests reaching `JavaBridge` must set `runelite.bridge=true` in `static {}` |
| `6f6a2cd` | Claude 1 | `ScriptInputSession`, `JavaBridge`, `Rs2Walker`, `MobileMicrobotPlugin`, bridge and plugin tests | A manual tap drops queued input and pauses ~1.8 s; it no longer ends the session | 328 tests, checkstyle and PMD | Use `MicrobotRuntime.humanOwnsInput()` to yield; `!isActive()` no longer means "user tapped" |
