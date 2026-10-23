# Claude 2 report: read-only APIs for the first upstream example

Scope: [CLAUDE_2_APIS.md](../../CLAUDE_2_APIS.md). This report covers only the
read-only API closure of the upstream `StateMachineExampleScript`, plus the
integration interface the other owners need. Mocked-interface tests are **not**
live-game, device or APK evidence.

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
| `runelite-client/src/microbot-upstream/java/net/runelite/client/plugins/microbot/statemachineexample/StateMachineExampleScript.java` | `runelite-client/src/main/java/…/statemachineexample/StateMachineExampleScript.java` | `eb4cea2dbe6036fc90d4ccc4564a5af6ac374940` | `aed85889b184231d2c910c719720c87ad129ca38a25dd0bfd99964750c245dbd` |

Both are BSD 2-Clause, the same as this repository. The script sits in a source
directory Gradle does not compile until Claude 1's runtime exists. Moving it to
`src/main/java` unchanged is the integration step. `TileObjectType.java` was
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
    {"api": "Microbot.isLoggedIn()", "status": "adapted", "impl": "MicrobotReadApi.isLoggedIn (Claude 1 delegates)", "tests": ["NpcQueryTest.logoutEmptiesTheSnapshotEvenWithinTheSameTick", "PlayerInventoryReadTest.logoutClearsInventoryAndAnimationState"]},
    {"api": "Microbot.getRs2NpcCache()", "status": "adapted", "impl": "MicrobotReadApi.getRs2NpcCache (Claude 1 delegates)", "tests": ["PlayerInventoryReadTest.uninstallOfAStaleProviderKeepsTheCurrentOne"]},
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
    {"api": "Rs2PlayerCache/Rs2PlayerModel/Rs2PlayerStateCache/Rs2BoatCache/Rs2Reachable", "status": "unsupported"}
  ]
}
```

## Design and documented deviations from upstream

- **Threading.** Upstream's `ClientThread.runOnClientThreadOptional` is a Microbot
  fork addition that RuneMobile's `ClientThread` does not have. `ClientThreadCalls`
  reproduces it on top of the real `ClientThread.invoke(Runnable)`: it runs inline
  on the client thread, otherwise waits on a `FutureTask` with a 10 s timeout, and
  returns `Optional.empty()` on timeout, interrupt or exception. Claude 1 may reuse
  it instead of duplicating it.
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
- **Logout state.** `Rs2Inventory` and the `Rs2Player` animation history are
  cleared on `LOGIN_SCREEN`, `HOPPING` and `CONNECTION_LOST`. Upstream keeps them.
- **Boat projection.** Actor locations on a sub-world view are projected to the
  main world at plane 0, as upstream does. This path is not covered by tests.
- **No lookalikes.** Actions (`click`, `interact`, `pickup`) and `isReachable`
  are removed from the interfaces rather than stubbed, so a script that needs
  them fails to compile instead of silently doing nothing.

## Integration interface

### For Claude 1 (`Microbot.java`), proposed patch, not applied

```java
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;

public static boolean isLoggedIn() {
    MicrobotReadServices s = MicrobotReadServices.current();
    return s != null && s.isLoggedIn();
}
public static Rs2NpcCache getRs2NpcCache() { return MicrobotReadServices.require().getRs2NpcCache(); }
public static Rs2TileObjectCache getRs2TileObjectCache() { return MicrobotReadServices.require().getRs2TileObjectCache(); }
public static Rs2TileItemCache getRs2TileItemCache() { return MicrobotReadServices.require().getRs2TileItemCache(); }
```

Upstream exposes these as Guice-static-injected `@Getter` fields. The caches
here are created by `MicrobotReadServices` and are not separately injectable,
so use delegation. Do not create cache instances in `Microbot`.

### For Codex (mobile plugin lifecycle), proposed registration, not applied

```java
@Inject private EventBus eventBus;
@Inject private MicrobotReadServices readServices;

// startUp()
eventBus.register(readServices);
MicrobotReadServices.install(readServices);

// shutDown()
MicrobotReadServices.uninstall(readServices);
eventBus.unregister(readServices);
```

After Claude 1 lands `Microbot`, `Script` and `statemachine/**`, move the staged
script to `src/main/java` unchanged and rerun `:client:compileJava`.

## Commands executed (this checkout, JDK 21.0.12 system install)

| Command | Result |
| --- | --- |
| `./gradlew --no-daemon --max-workers=4 :client:compileJava` (before changes) | exit 1: Maven Central HTTP 429 rate limit while resolving dependencies; not a code error |
| `./gradlew --no-daemon --max-workers=2 :client:compileJava` (with changes; retried after 429s) | exit 0 on attempt 3; only pre-existing RuneMobile warnings |
| `./gradlew … :client:test --tests 'net.runelite.client.plugins.microbot.*' --tests 'net.runelite.client.plugins.mobilemicrobot.*'` | exit 0; NpcQueryTest 9/9, SceneQueryTest 6/6, PlayerInventoryReadTest 11/11, existing MobileMicrobotPluginTest 9/9 |
| `javac` of the unchanged `StateMachineExampleScript.java` against the built client only | exit 1; the only unresolved symbols are `Microbot`, `statemachine.StateMachineScript` and `statemachine.Transition` (Claude 1). Every read-API import resolved |
| `javac` of the unchanged script against the built client plus a **scratch, uncommitted** harness (the adapter above, plus `Script`/`StateMachineScript`/`Transition` signatures copied from upstream) | exit 0. This type-checks the script body against the real read APIs only. It is not runtime evidence, and the harness is not part of the repository |

Test notes: `NpcQueryTest` and `SceneQueryTest` run reads from the JUnit thread
against a dedicated fake client thread. The mocks throw if live game state is
read off that thread. Covered cases: logout, hop, scene load, stale entities,
concurrent background readers, a stalled client thread (empty result, no hang),
and a sub-world-view load.

## Blockers

1. The example cannot compile in-tree until Claude 1's runtime provides
   `Microbot`, `Script`, `StateMachineScript` and `Transition`. No such branch
   was visible on the remote when this was written.
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
  closure type-checks; in-tree compile blocked on the runtime port". Leave
  Execution as "Not run".
