# Claude 2 report: read-only APIs for the first upstream example

Scope: [CLAUDE_2_APIS.md](../../CLAUDE_2_APIS.md). This report covers only the
read-only API closure of the upstream `StateMachineExampleScript`, plus the
integration interface the other owners need. Mocked-interface tests are **not**
live-game, device or APK evidence.

## Current status (read this first)

**Update 2026-10-24: Hub read API complete.** Every read that a Hub plugin calls is now
present at the arity it calls (checked against `UTIL_GAP.md` and the Hub sources), except
the items handed to Claude 1 or not portable, listed at the end of this update. Details and
commits are in the [COORDINATION.md](COORDINATION.md) change log.

- **Read batches 10 to 15** (plus `checkIdleLogout`, `sellLoot`): inventory, item model,
  player, NPC, game object, dialogue, tile, widget, UI helper, rune pouch, equipment,
  combat range (with upstream's `util/combat/weapons/**` data), bank read block, ground
  items, reachability, death recovery and deposit box reads. New read-only classes:
  `util/models/RS2Item`, `util/walker/Rs2MiniMap`, `util/inventory/Rs2Gembag`,
  `util/item/Rs2ExplorersRing`, `util/reachable/Rs2Reachable`, `util/death/Rs2Death`.
- **Take-over by agreement with Claude 1** (reads and actions): `util/menu/NewMenuEntry`
  (data only), `util/settings/Rs2Settings`, `util/grandexchange/**` (types, offer reads,
  GE Tracker/OSRS Wiki price lookups, buy/sell/collect/abort). Every click is one
  `MobileActions` dispatch; keys go through `Rs2Keyboard`.
- **Visibility as upstream Microbot:** `bosstimer.Boss`/`RespawnTimer`,
  `grounditems.GroundItem` (plus upstream's `location`/`height` fields),
  `mining.MiningAnimation` (and `MINING_ANIMATIONS`), `gameval.ObjectID1` (generated;
  keep it public on regeneration).
- **Documented deviations** (each in its Javadoc): no humanised jitter in
  `Rs2UiHelper.getClickingPoint`; `disableWorldSwitcherConfirmation` never clicks; no
  bank PIN entry in `openExchange`; GE price lookups refused on the client thread;
  `Rs2Reachable` returns a packed-point set (no fastutil) and keys its cache by start;
  `Rs2GameObject.canReach` ends the path at the nearest reachable tile (no `pathTo`);
  gem bag Fill/Empty measured on the next inventory event; rune pouch combination mode
  also counts the exact rune; `Rs2NpcModel.matches` is case-insensitive both ways.
- **Not done here:** `Rs2PathApi.getPathfinderConfig`/`getActiveRoute` and
  `Rs2Camera.isTrackingNpc` (Claude 1: walker route API, camera tracker); the actions in
  the new gem bag and explorer's ring files (Claude 1); `Rs2Reflection.getGroundItemActions`
  (reflection into the obfuscated client, not portable).
- **Validation:** 780 tests in the standard verify, Checkstyle and PMD clean. Mocked
  interfaces only; nothing here has been run on a phone.

**Update 2026-10-23 (M6).** Two more upstream caches are ported, both owned by
`MicrobotReadServices` and listed in the coverage table below:

- `Rs2PlayerStateCache` (varbits/varps), `7afa391`. Claude 1 switched
  `Microbot.getVarbitValue`/`getVarbitPlayerValue` to it in `38887ac`.
- `Rs2PlayerCache`/`Rs2PlayerModel` (other players), `609ac9e`. It waits for
  Claude 1's `Microbot.getRs2PlayerCache()` getter.

Cross-session claims and change notes are in [COORDINATION.md](COORDINATION.md).
Everything below this update describes the original read-only stage.

Base: `feat/android-microbot-compat` `239cdf5` plus this review. The rest of
this report records the read-only stage. Where it differs, this section and
[CODEX_HANDOFF_CURRENT.md](CODEX_HANDOFF_CURRENT.md) take precedence.

- **Done in this stage.** The unchanged upstream `StateMachineExampleScript`
  compiles in the client and is selectable in the mobile plugin. The read
  services run under Claude 1's `MicrobotRuntime`.
- **Scope has grown since.** To port Auto Woodcutting, Codex added action
  methods on top of these APIs, all routed through `MobileActions`: NPC and
  object `click`, inventory `interact`/`use`/`combine`/`drop`/`dropAllExcept`,
  plus `Rs2Player` waits, skills, quests and logout-on-player-detection. So the
  JSON table's `unsupported` entries for actions are out of date. The
  read-API semantics in the table still hold.
- **Review of those additions (Claude 2):**
  - *Drop batch.* `dropSlot` retries dispatch until `MobileActions.inventory`
    returns `true`. `true` means the menu action was sent, and every `false`
    path exits before sending. The expected-item-id check and the
    one-action-per-tick guard stop a retry from dropping a replaced or already
    emptied slot. No change needed.
  - *Fixed: `Rs2Inventory.contains(String...)`.* It matched names as
    substrings. Upstream matches exactly, so `contains("Logs")` was also true
    for `"Oak logs"`, `"Willow logs"` and others. That affected the
    woodcutting burn and Forestry egg checks, which pass tree log names. It is
    now exact by default, as upstream, with upstream's
    `contains(boolean exact, String...)` and `contains(String, boolean)`
    overloads. `hasItem`/`get` by name stay substring matches, as upstream.
  - *Fixed: floor selection.* Codex made the object cache scan the local
    player's floor because the world view's plane lags stair transitions. The
    ground-item cache still used the view's plane. Both now share
    `MicrobotReadServices.scanPlane`.
  - The `LOADING` rule (state survives region loads) is intact.
- **Validation of this review:** the handoff's command set gives 224 tests, 0
  failures/errors/skips, and `:client:checkstyleMain` and `:client:pmdMain`
  pass. Mocked interfaces only: no phone retest has been done.

## Webwalker pathfinding (Claude 2 half)

Upstream `shortestpath/**` at Microbot `a2114401` is ported headless into
`microbot/shortestpath/**` (commit `5bb25e7`). Entry point
`MobilePathfinder.find(start, goal)` returns an immutable `PathResult`. Contract,
adaptations and the open items for the walker are in the coordination change
log. Five route tests on the real collision map are planning evidence only; the
walker does not execute these routes yet.

## Source identity

| Item | Value | How checked |
| --- | --- | --- |
| Microbot | `chsami/Microbot` `e01e0d77d79b354472a5737b3aaab2449eb034fc` | `git fetch --depth 1 origin e01e0d77…` then `git rev-parse FETCH_HEAD`, read-only partial checkout outside the repo |
| RuneMobile | `audaro/runemobile` `a8c4f22f8ac170b654793df2fd85372f31b18c64` (per `BUILD_REPORT.md`) | **Not independently verifiable here**: Codex imported RuneMobile without its Git history. Signatures were checked against the imported `runelite-api`/`runelite-client` source and compiled against it |
| Integration base | `feat/android-microbot-compat` `ee9bdc72d2e66f422b6b049a42c59be5f770fd92` | `git log` |
| Work branch | commits `bc15c46` (APIs + tests), `674f9bd` (upstream copies), then this report; pushed to remote branch `claude/dazzling-cannon-6cy3wc`, which fast-forwards from `feat/android-microbot-compat` (local name `feat/microbot-read-apis`) | `git push` |

Upstream guides read: root `AGENTS.md`, `microbot/AGENTS.md`,
`statemachine/AGENTS.md`, `api/QUERYABLE_API.md` (headings), and
`docs/entity-guides/{README,items,movement,worlds}.md`. Rules applied: caches via
the singleton only, no client-thread blocking, snapshots re-queried rather than
held, and the multi-tile/boat tile-loop gotchas.

## Copied upstream source (unchanged)

| Path in this repo | Upstream path at `e01e0d7` | Git blob | SHA-256 |
| --- | --- | --- | --- |
| `runelite-client/src/main/java/net/runelite/client/plugins/microbot/statemachineexample/StateMachineExampleConfig.java` | same path | `adae6de7c4acb389aaa1b191787f00374737ac8a` | `2f0f5e970bc48b1462a8a0ec1bd8d425ae17246ec9e823b33e5d40cf59473373` |
| `runelite-client/src/main/java/net/runelite/client/plugins/microbot/statemachineexample/StateMachineExampleScript.java` | same path | `eb4cea2dbe6036fc90d4ccc4564a5af6ac374940` | `aed85889b184231d2c910c719720c87ad129ca38a25dd0bfd99964750c245dbd` |

Both are BSD 2-Clause, the same as this repository. The script was first staged
outside the compiled tree; Claude 1 moved it into `src/main/java` unchanged once
the runtime and cache getters existed (hash re-checked on `91b7304`). `TileObjectType.java` was
also copied unchanged with a provenance header added. All other ported files are
adaptations and carry an `Adapted from …e01e0d7…` header that names the upstream file.

## What the example actually calls

From the script body: `Microbot.isLoggedIn()`;
`Microbot.getRs2NpcCache() / getRs2TileObjectCache() / getRs2TileItemCache()`
`.query()` then `.within(int)`, `.where(Predicate)`, `.nearest()`, `.toList()`;
`Rs2NpcModel.getName()`, `Rs2TileObjectModel.getName()`,
`Rs2TileItemModel.getName()`, `Rs2TileItemModel::isLootAble`;
`Rs2Player.isAnimating()`, `Rs2Player.isMoving()`, `Rs2Inventory.count()`;
plus the runtime types `StateMachineScript`, `Transition`, `Script`
(`scheduledExecutorService`, `mainScheduledFuture`) owned by Claude 1.

## Method coverage (machine-readable)

Status values: `native` (upstream semantics, same signature), `adapted`
(same name, documented difference), `unsupported` (not ported).

```json
{
  "upstream": "chsami/Microbot@e01e0d77d79b354472a5737b3aaab2449eb034fc",
  "evidence": "mocked RuneLite interfaces + real cross-thread hop; not live game",
  "methods": [
    {"api": "Microbot.isLoggedIn()", "status": "adapted", "impl": "Claude 1 MicrobotRuntime snapshot; used by Rs2Inventory fallback", "tests": ["NpcQueryTest.logoutEmptiesTheSnapshotEvenWithinTheSameTick", "PlayerInventoryReadTest.logoutClearsInventoryAndAnimationState"]},
    {"api": "Microbot.getRs2NpcCache()", "status": "adapted", "impl": "MicrobotReadApi.getRs2NpcCache (Claude 1 delegates)", "tests": ["PlayerInventoryReadTest.aSecondProviderCannotStartWhileOneIsInstalled"]},
    {"api": "Microbot.getRs2TileObjectCache()", "status": "adapted", "impl": "MicrobotReadApi.getRs2TileObjectCache", "tests": []},
    {"api": "Microbot.getRs2TileItemCache()", "status": "adapted", "impl": "MicrobotReadApi.getRs2TileItemCache", "tests": []},
    {"api": "Rs2NpcCache.query()/getStream()", "status": "adapted", "tests": ["NpcQueryTest.*"]},
    {"api": "Rs2TileObjectCache.query()/getStream()", "status": "adapted", "tests": ["SceneQueryTest.multiTileGameObjectIsListedOnceAtItsSceneMinTile", "SceneQueryTest.onlyTheCurrentPlaneIsScanned"]},
    {"api": "Rs2TileItemCache.query()/getStream()", "status": "adapted", "tests": ["SceneQueryTest.groundItemLootabilityFollowsOwnership", "SceneQueryTest.groundItemSnapshotDoesNotSeeLaterPickupUntilNextTick"]},
    {"api": "IEntityQueryable.within(int)", "status": "native", "tests": ["NpcQueryTest.withinAndNearestUseChebyshevDistanceOnThePlayersPlane", "NpcQueryTest.noLocalPlayerYieldsEmptyWithinAndNullNearest"]},
    {"api": "IEntityQueryable.within(WorldPoint,int)", "status": "native", "tests": ["NpcQueryTest.withinAndNearestUseChebyshevDistanceOnThePlayersPlane"]},
    {"api": "IEntityQueryable.where(Predicate)", "status": "native", "tests": ["NpcQueryTest.filtersMatchUpstreamNameAndIdRules", "SceneQueryTest.namesResolveImpostorsStripColourTagsAndFilterTheNullName"]},
    {"api": "IEntityQueryable.withName/withNameContains/withNames/withId/withIds", "status": "native", "tests": ["NpcQueryTest.filtersMatchUpstreamNameAndIdRules"]},
    {"api": "IEntityQueryable.fromWorldView()", "status": "native", "tests": []},
    {"api": "IEntityQueryable.first()/toList()/count()", "status": "native", "tests": ["NpcQueryTest.filtersMatchUpstreamNameAndIdRules"]},
    {"api": "IEntityQueryable.nearest()/nearest(int)/nearest(WorldPoint,int)", "status": "adapted", "note": "never returns an entity on another plane, even unbounded", "tests": ["NpcQueryTest.withinAndNearestUseChebyshevDistanceOnThePlayersPlane"]},
    {"api": "IEntityQueryable.firstReachable/nearestReachable*/interact*/ *OnClientThread", "status": "unsupported"},
    {"api": "Rs2NpcModel.getName/getId/getIndex/getWorldLocation/getLocalLocation/getWorldView/getNpc", "status": "adapted", "note": "client-thread snapshot; does not implement NPC/Actor", "tests": ["NpcQueryTest.staleEntitiesKeepCaptureTickValuesUntilRequeried"]},
    {"api": "Rs2NpcModel.click/interaction/combat/LOS/head-icon helpers", "status": "unsupported"},
    {"api": "Rs2TileObjectModel.getName/getId/getPlane/getTileObjectType/getWorldLocation/getLocalLocation/getWorldView", "status": "adapted", "note": "name lazy, resolved on client thread, memoized per snapshot", "tests": ["SceneQueryTest.namesResolveImpostorsStripColourTagsAndFilterTheNullName"]},
    {"api": "Rs2TileObjectModel.click/canvas/TileObject delegation", "status": "unsupported"},
    {"api": "Rs2TileItemModel.getName/getId/getQuantity/getOwnership/isPrivate/getDespawnTime/getVisibleTime/isStackable/isNoted/isOwned", "status": "adapted", "tests": ["SceneQueryTest.groundItemLootabilityFollowsOwnership"]},
    {"api": "Rs2TileItemModel.isLootAble()", "status": "native", "tests": ["SceneQueryTest.groundItemLootabilityFollowsOwnership"]},
    {"api": "Rs2TileItemModel.pickup/click/isDespawned/willDespawnWithin/value helpers/isTradeable/isMembers", "status": "unsupported"},
    {"api": "Rs2Player.isAnimating()/isAnimating(int)", "status": "native", "tests": ["PlayerInventoryReadTest.isAnimatingUsesCurrentAnimationOrRecentLocalAnimationEvent", "PlayerInventoryReadTest.otherPlayersAnimationsAreIgnored"]},
    {"api": "Rs2Player.isMoving()", "status": "native", "tests": ["PlayerInventoryReadTest.isMovingComparesPoseWithIdlePose"]},
    {"api": "Rs2Player.getAnimation()/getPoseAnimation()/getLastAnimationID()/handleAnimationChanged", "status": "native", "tests": ["PlayerInventoryReadTest.isMovingComparesPoseWithIdlePose"]},
    {"api": "Rs2Player (all other methods)", "status": "unsupported"},
    {"api": "Rs2Inventory.count()/count(Predicate)/count(int)/count(String)/count(String,boolean)", "status": "native", "note": "occupied slots, not summed quantity", "tests": ["PlayerInventoryReadTest.countIsOccupiedSlotsNotSummedStackQuantity"]},
    {"api": "Rs2Inventory.items()/items(Predicate)/all()/storeInventoryItemsInMemory", "status": "adapted", "note": "snapshot cleared on logout/hop/connection loss", "tests": ["PlayerInventoryReadTest.emptySnapshotFallsBackToTheLiveContainerWhenLoggedIn", "PlayerInventoryReadTest.otherContainersAreIgnored"]},
    {"api": "Rs2Inventory (all interactions, selection, drop, bank)", "status": "unsupported"},
    {"api": "Rs2ItemModel read getters", "status": "adapted", "note": "constructor takes linked composition; no lazy load, no setQuantity"},
    {"api": "Rs2PlayerStateCache.getVarbitValue/getVarpValue", "status": "native", "note": "cached only while LOGGED_IN (zero included); VarbitChanged updates; login screen, hop and connection loss flush; LOADING keeps; unreachable client thread returns 0", "tests": ["Rs2PlayerStateCacheTest.*"]},
    {"api": "Rs2PlayerStateCache quest states / local-player position", "status": "unsupported", "note": "quests need the Quest Helper module, which mobile does not ship"},
    {"api": "MicrobotReadApi.getRs2PlayerStateCache()", "status": "adapted", "note": "Microbot.getVarbitValue/getVarbitPlayerValue delegate here (Claude 1, 38887ac)", "tests": ["Rs2PlayerStateCacheTest.servicesExposeOneCache"]},
    {"api": "Rs2PlayerCache.query()/getStream()", "status": "adapted", "note": "per-tick snapshot across loaded world views, local player included", "tests": ["PlayerQueryTest.*"]},
    {"api": "Rs2PlayerModel.getId/getName/getCombatLevel/getAnimation/isFriend/isClanMember/isFriendsChatMember/getSkullIcon/getOverheadIcon/getPlayer + actor getters", "status": "adapted", "note": "captured on the client thread at snapshot; upstream hops per call", "tests": ["PlayerQueryTest.modelCapturesRelationshipAndOverheadStateOnTheClientThread"]},
    {"api": "Rs2PlayerModel.click/isSalvaging, Rs2PlayerCache.getPlayersStream (deprecated static)", "status": "unsupported", "note": "upstream click always returns false; omitted per IEntity"},
    {"api": "Rs2BoatCache/Rs2Reachable", "status": "unsupported"}
  ]
}
```

## Design and documented deviations from upstream

- **Threading.** Every read goes through `MicrobotReadServices.read`, which calls
  Claude 1's port of upstream `ClientThread.runOnClientThreadOptional(Callable, long)`:
  inline on the client thread, otherwise a bounded wait (10 s default) that returns
  `Optional.empty()` on timeout, interrupt or exception. The earlier
  `ClientThreadCalls` duplicate was removed once that port landed.
- **Lifecycle.** `MicrobotReadServices` implements Claude 1's
  `MicrobotRuntimeService`. `startUp()` subscribes it to the `EventBus` and makes it
  the installed provider (a second provider is refused). `shutDown()` unsubscribes,
  drops every snapshot and clears inventory and animation state.
- **Snapshots.** Each cache rebuilds an unmodifiable list on the client thread at
  most once per `(tickCount, generation)`. Upstream keys on the tick alone.
  `MicrobotReadServices` bumps the generation on every `GameStateChanged`,
  `WorldViewLoaded` and `WorldViewUnloaded`, so a logout, hop or region load
  never serves the previous scene. Outside `LOGGED_IN` the snapshot is empty.
  Models capture id, name, locations and world view on the client thread, so
  background reads never touch live objects. Tile-object names are resolved
  lazily, on the client thread. Models do not implement the RuneLite
  `NPC`/`TileObject`/`TileItem` interfaces, and the live object is available
  via `getNpc()`/`getTileObject()`/`getTileItem()` for client-thread use only.
- **World views.** Upstream scans only ids added by `WorldViewLoaded`. The
  top-level view id is always included here, so a plugin started after login
  still sees the main scene.
- **Plane and distance.** `WorldPoint.distanceTo` returns `MAX_VALUE` across
  planes, which matches upstream. Additionally, `nearest()` with no bound never
  returns another plane's entity. Upstream could, but only when called with no
  `within` filter. The example always applies `within` first, so its behaviour
  is unaffected. Caches scan only the world view's current plane, as upstream does.
- **Ground items.** Visibility is what the client has loaded on the tile, as
  upstream. Lootable means ownership is not `OWNERSHIP_OTHER`. `getWorldView()`
  is the world view the item was scanned from, whereas upstream always returns
  the top-level view.
- **Logout state.** World-view ids, `Rs2Inventory` and the `Rs2Player` animation
  history are cleared on every game state except `LOGGED_IN` and `LOADING`
  (logout, hop, connection loss, login). Upstream keeps inventory and animation
  history. `LOADING` (a region load while logged in) only invalidates snapshots:
  clearing there would drop loaded boat world views that get no new
  `WorldViewLoaded`, and empty the inventory on every region crossing.
- **Boat projection.** Actor locations on a sub-world view are projected to the
  main world at plane 0, as upstream does. This path is not covered by tests.
- **Player-state cache.** Same rules as upstream, routed by
  `MicrobotReadServices` instead of self-registering on the event bus, and cleared
  on `shutDown()`. A miss reads through `services.read`, so it has the same bounded
  wait; on timeout it returns 0 and caches nothing.
- **Player cache.** Same snapshot rules as the NPC cache. Relationship and overhead
  state is captured once per snapshot rather than read live on each call.
- **No lookalikes.** Actions (`click`, `interact`, `pickup`) and `isReachable`
  are removed from the interfaces rather than stubbed, so a script that needs
  them fails to compile instead of silently doing nothing.

## Integration interface

### For Claude 1 (`Microbot.java`): applied by Claude 1 in `d6ce8a0`/`311d064`

`isLoggedIn()` already exists in Claude 1's `Microbot`. Add only the three cache
getters. They return `null` when the runtime is not installed, like Claude 1's
other getters:

```java
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;

public static Rs2NpcCache getRs2NpcCache() {
    MicrobotReadServices s = MicrobotReadServices.current();
    return s == null ? null : s.getRs2NpcCache();
}
public static Rs2TileObjectCache getRs2TileObjectCache() {
    MicrobotReadServices s = MicrobotReadServices.current();
    return s == null ? null : s.getRs2TileObjectCache();
}
public static Rs2TileItemCache getRs2TileItemCache() {
    MicrobotReadServices s = MicrobotReadServices.current();
    return s == null ? null : s.getRs2TileItemCache();
}
```

The caches are created by `MicrobotReadServices` and are not separately
injectable, so delegate rather than adding fields. Do not create cache
instances in `Microbot`.

### For Codex (mobile plugin lifecycle): applied by Codex in `adc4e7d`

```java
@Inject private EventBus eventBus;
@Inject private MicrobotReadServices readServices;

// startUp()
MicrobotRuntime.install(client, clientThread, eventBus, configManager, List.of(readServices));

// shutDown(), after active scripts have stopped
MicrobotRuntime.uninstall();
```

Codex's version installs on the client thread in `startUp()` and uninstalls
after `stopActive(...)` in `shutDown()`. The example is selectable through
`ScriptKind` and runs via Claude 1's `MicrobotScriptAdapter`.

## Commands executed (this checkout, JDK 21.0.12 system install)

| Command | Result |
| --- | --- |
| `./gradlew --no-daemon --max-workers=4 :client:compileJava` (before changes) | exit 1: Maven Central HTTP 429 rate limit while resolving dependencies; not a code error |
| `./gradlew --no-daemon --max-workers=2 :client:compileJava` (with changes; retried after 429s) | exit 0 on attempt 3; only pre-existing RuneMobile warnings |
| `./gradlew … :client:test --tests 'net.runelite.client.plugins.microbot.*' --tests 'net.runelite.client.plugins.mobilemicrobot.*'` (before Claude 1's runtime) | exit 0; NpcQueryTest 9/9, SceneQueryTest 6/6, PlayerInventoryReadTest 11/11, MobileMicrobotPluginTest 9/9 |
| Same plus `--tests 'net.runelite.client.callback.*' --tests 'net.runelite.client.headless.*'`, on `feat/android-microbot-compat` `2558ad2` with the runtime-lifecycle change | exit 0; NpcQueryTest 9/9, SceneQueryTest 6/6, PlayerInventoryReadTest 10/10 (installed through the real `MicrobotRuntime` and `EventBus`), Claude 1's suites 55/55, MobileMicrobotPluginTest 17/17, headless bridge suites all passing |
| Same suites on `91b7304` (Claude 1 getters, Codex plugin wiring) plus the `LOADING` fix | exit 0; 115 tests, 0 failures/errors/skips, including Claude 1's `StateMachineExampleIntegrationTest` 2/2 and Codex's `UpstreamExampleIntegrationTest` 3/3, which compile and run the unchanged example against mocked interfaces |
| `javac` of the unchanged `StateMachineExampleScript.java` against the built client only (before the runtime landed) | exit 1; the only unresolved symbols are `Microbot`, `statemachine.StateMachineScript` and `statemachine.Transition` (Claude 1). Every read-API import resolved |
| `javac` of the unchanged script against the built client plus a **scratch, uncommitted** harness (the adapter above, plus `Script`/`StateMachineScript`/`Transition` signatures copied from upstream) | exit 0. This type-checks the script body against the real read APIs only. It is not runtime evidence, and the harness is not part of the repository |

Test notes: `NpcQueryTest` and `SceneQueryTest` run reads from the JUnit thread
against a dedicated fake client thread. The mocks throw if live game state is
read off that thread. `ClientThread` is a real instance (Mockito spy) bound to a
mocked `Client`, so the production `runOnClientThreadOptional` runs. Covered cases: logout, hop, scene load, stale entities,
concurrent background readers, a stalled client thread (empty result, no hang),
a sub-world-view load, and a region load (`LOADING`) that keeps inventory,
animation history and sub-world views while a login transition clears them.

## Blockers

1. Resolved: the unchanged example compiles in-tree and runs in mocked-interface
   integration tests.
2. No live game or device run has been done. The cache refresh cost per tick on
   ARM64 is not measured.
3. The RuneMobile SHA is taken from Codex's report, not from Git history.

## Recommendations for Codex's consolidated reports (not applied)

- `API_SUPPORTED.md`: add rows "Microbot queryable caches (NPC, tile object,
  tile item): adapted, read-only, mocked-interface tests"; "Rs2Player
  (isAnimating/isMoving/getAnimation): native subset"; "Rs2Inventory (count/items):
  native subset, occupied-slot semantics". Keep NPC/widget/bank interaction
  rows as unsupported.
- `SCRIPT_COMPATIBILITY.md`: for `StateMachineExampleScript`, change "Not
  ported or compiled" to "Copied unchanged (sha256 `aed85889…`); read-API
  closure and in-tree compile pass; runs end to end in mocked-interface
  integration tests". Keep live-game Execution as "Not run".
