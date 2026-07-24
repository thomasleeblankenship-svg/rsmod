# RS Mod — Project Audit

_Audit date: 2026-07-23. Codebase: ~1,800 Kotlin files across `engine/` (12 modules), `api/` (~57 modules), `content/` (27 leaf modules), `server/` (5 modules). Revision target: OSRS rev 233._

---

## 1. Implemented Systems

### Engine (`engine/`)
| Module | Status | Notes |
|---|---|---|
| `engine/game` | Implemented | Domain core: entities (`Player`, `Npc`, `Controller`), inventories, locs, objs, queues, regions, movement, vars, and a large cache-type model (~155 files under `type/`). |
| `engine/coroutine` | Implemented | Single-threaded cooperative coroutines for scripts (`GameCoroutine`); resumption driven manually by the game thread — no thread pool, no sync overhead. |
| `engine/events` | Implemented | `EventBus` with unbound (list), keyed (O(1)), and suspend event maps. |
| `engine/objtx` | Implemented | Transactional inventory engine with rollback (`Transaction`); best-tested module (21 test files + JMH benchmarks). |
| `engine/routefinder` | Implemented | Pathfinding — see §9. |
| `engine/interact` | Implemented | Pure state-machine for interaction step sequencing. |
| `engine/map` | Implemented | Bit-packed coordinate primitives (`CoordGrid`, `ZoneKey`, etc.), zero-allocation. |
| `engine/plugin`, `engine/module`, `engine/annotations` | Implemented | Plugin contracts, Guice DSL helpers, DI qualifiers — minimal by design. |
| `engine/utils-bits`, `engine/utils-sorting` | Implemented | Bit packing, in-place quicksort. |

Note: the tick driver lives in `server/app` (`GameService`, 600 ms loop) and the tick pipeline in `api/game-process` (`GameCycle`), not in `engine/`. `engine/game/GameProcess.kt` is just the interface.

### API (`api/`)
- **Networking** (`api/net`): rsprot-based protocol layer; JS5 fully wired; complete password login with bcrypt (`api/pw-hash`), TOTP 2FA (`api/totp`), realm modes. ~34 message handlers. See §8.
- **Tick pipeline** (`api/game-process`): full pre/post-tick order (world → npc → player shuffle/input/route → npc main → controller → player main → logout → login). NPC per-tick order: coroutines → reveal → hunt → regen → ai timers/queues → queues → timers → modes → interactions.
- **Combat** (`api/combat/*`): accuracy + max-hit fully implemented for melee/ranged/magic across PvN, NvP, PvP, NvN, with attribute collectors and damage reduction. `PlayerAttackManager` handles hit queueing, retaliation, XP, animations, projectiles. Special attacks (`api/specials`) substantial. Strongest test coverage in the repo.
- **Persistence** (`api/db`, `api/db-gateway`, `api/account`): SQLite + Flyway (V1–V6 migrations). Stores accounts (bcrypt hash, 2FA, mod group), characters (coords, varps as JSON), stats, inventories. `db-gateway` decouples the game thread from JDBC via an async request/response synchronizer.
- **Type system & scripting** (`api/type/*`, `api/script`): type-safe cache references (`*References` + `*ReferenceResolver` per type), builders/editors for creating and patching cache types, Kotlin DSL builders. Content registers via event-extension helpers on the `EventBus`. This is the framework's core strength.
- **Feature layers**: shops, spells (+autocast, +runes), stats, music, death, hunt, inv transactions (`invtx`), obj charges, cheat commands — implemented with scattered refinement TODOs.

### Content (`content/`)
- **Interfaces** (14 modules): bank (richest, 21 files), equipment, gameframe, settings, emotes, combat/prayer/journal/logout tabs, skill guides, fade overlay.
- **Areas**: Lumbridge (11 NPC scripts), multiway/singles-plus PvP zones.
- **Skills**: woodcutting (the only fully implemented skill), magic spell-attacks (combat spells only).
- **Travel**: canoe system (fully fleshed).
- **Other**: dev commands, login script, special attacks/weapons (Dark Bow, Tumeken's Shadow), windmill.

### Server (`server/`)
- `server/app`: Clikt CLI entry (`GameServer.kt`), Guice injector assembly, boot sequence with cache verification and auto re-pack/restart on type mismatch.
- `server/services`: service lifecycle framework (`ServiceManager`, scheduled services) — complete and well documented.
- `server/shared`: ClassGraph-based plugin/type loaders, shared Guice modules.
- `server/install`: first-run installer (cache download, RSA keygen, logback copy).

---

## 2. Missing Systems

- **Skills**: only woodcutting is implemented. **~19 of 23 OSRS skills have no module**: Mining, Smithing, Fishing*, Cooking*, Firemaking, Agility, Thieving, Crafting, Fletching, Herblore, Farming, Hunter, Runecraft, Construction, Slayer, Prayer (training), and the combat-stat training loops beyond combat itself. (*empty stub modules — see §3.)
- **Quests**: none. Only quest-gated dialogue hooks referenced in TODOs.
- **Minigames**: none.
- **Grand Exchange**: no content module (an `api/market` layer exists, but no GE interface/content).
- **Cities/regions beyond Lumbridge**: none scripted.
- **NPC drop tables**: `api/death/NpcDeath.kt` — `// TODO: Drop tables.`
- **Player death mechanics**: `api/death/PlayerDeath.kt` — `// TODO: Drop death invs, etc.`
- **Reconnection**: `ConnectionHandler.onReconnect()` returns `ConnectFail` (`// TODO: Reconnection`).
- **Token/JWS login**: `ConnectionHandler.tokenLogin()` returns `InvalidLoginPacket` (`// TODO: Token authentication handling`).
- **PK skulling**: `api/combat/.../PlayerCommons.kt` — `// TODO(combat): Set pk skull when applicable.`
- **Followers/pets**: `EquipmentTabScript.kt:16` — logic deferred until followers exist.

## 3. Placeholder Systems

- `content/skills/cooking` and `content/skills/fishing` — **empty modules**: only a `build.gradle.kts`, no `src/` at all.
- `engine/game/type/midi/MidiTypeList.kt` — placeholder; `// TODO(engine): Load actual midi types and remove this function.`
- `api/net` world-entity support — `WorldEntityProvider.provide()` is a bare provider with no populated suppliers.
- Hard `TODO()` stubs that **throw `NotImplementedError` at runtime if reached**:
  1. `api/game-process/.../NpcModeProcessor.kt` — 6 branches: `OpPlayer6/7/8`, `ApPlayer6/7/8`.
  2. `api/combat/combat-scripts/.../PlayerCommons.kt` — a `): Boolean = TODO()` function body.
  3. `content/areas/city/lumbridge/.../WoodsmanTutor.kt:26` — `TODO("Mastery dialogue")`, fires when a player with woodcutting 99 talks to the tutor.
  4. `server/install/.../GameServerCacheDownloader.kt:114` — `TODO("Download and extract zip file...")`; live branch, reached when an xtea URL is non-blank and not a `.json` (i.e., an archive URL). Blank and `.json` paths work.

## 4. Duplicated Logic

- **Combat matchup symmetry**: PvN/NvP/PvP/NvN each reimplement melee/ranged/magic accuracy and max-hit with parallel class structures (partially abstracted into shared `*Operations` / `InternalHelpers`). Deliberate but heavy; a candidate for further consolidation.
- **`api/combat-accuracy` and `api/combat-maxhit`** top-level modules overlap conceptually with `api/combat/combat-formulas`.
- **Duplicated extensions** `combatPlayDefendAnim` / `queueCombatRetaliate` / `resolveCombatXpMultiplier` exist in both `combat-commons` npc and player variants.
- **Type-system boilerplate**: every cache type carries a near-identical References/Resolver/Builder/Editor quadruplet across `api/type/*`. Structural, but boilerplate-heavy.
- `docs/quirks.md` documents further accepted anti-patterns (InvTransactions cached singleton, collision-map argument passing).

## 5. Unfinished Features (soft TODOs, by theme)

96 TODO-style comments total. Highlights:

- **Combat** (`TODO(combat)`, ~20): vampyre modifiers (melee/ranged/magic), wilderness area checks, slayer-task resolution, multiway logic updates + "singles plus" support (PvP), barrows-style single-target NPCs, recoils/retribution/degradation processing, ironman-blocked hitmarks, target death-flag varbit.
- **Emulation accuracy** (`TODO(emulation)`): music start/loop timing quirks, music unlock-all behavior.
- **Content** (`TODO(content)`): bank-PIN/UIM banker dialogue, Cold War quest cow states, achievement-diary overlay, Hans playtime, canoe axe-charge degradation, woodcutting guild +7 boost and shared-tree +1 boost, Tumeken's Shadow fully-charged message, mesanim corrections.
- **Engine/API refinements**: red-X interaction nuances (`NpcInteractionProcessor`), afk-check semantics in hunt, npc telejump failure policy, obj despawn duration calculation, emergency backup on failed player save (`AccountRegistry`), blowpipe dart / ToA / Dizana's quiver bonuses (`WornBonuses`), `ProtectedAccess` API cleanups, `type-script-dsl` builder ergonomics.
- `docs/quirks.md` itself opens with a TODO list of undocumented design decisions.

## 6. Compile Issues

**None.** `gradlew compileKotlin compileTestKotlin` completes cleanly (exit 0) across all modules.

One hygiene note: `server/services/build/` (stale build output) is checked into the tree.

## 7. TODO Comments

- **Total**: 96 `// TODO`-style comments (`TODO`, `FIXME`-family) across `engine`/`api`/`content`/`server`; zero `FIXME`/`XXX`/`HACK` markers — the codebase consistently uses `TODO` with scope tags: `TODO(engine)`, `TODO(combat)`, `TODO(content)`, `TODO(emulation)`.
- **Densest files**: `NpcModeProcessor.kt` (6 — all throwing stubs), `LocPluginBuilder.kt` (4), `ProtectedAccess.kt` (4), `NpcPluginBuilder.kt` (3), `WornBonuses.kt` (3), `PvNCombatScript.kt` (3).
- **Runtime-throwing `TODO()`s**: 4 sites (see §3) — these are the priority items, everything else is annotation-style.

## 8. Networking

- **Library**: [rsprot](https://github.com/blurite/rsprot) (`net.rsprot.protocol.*`) for the OSRS protocol, RSA, Huffman, and player/NPC info packing; openrs2 for cache access. `NetworkFactory.kt` extends rsprot's `AbstractNetworkServiceFactory<Player>`; port 43594; **desktop client only**.
- **JS5**: fully wired (`Js5Store`, group response provider, CRC validation during login).
- **Login**: complete password flow — bcrypt verification, TOTP 2FA, known-device handling, realm registration modes, CPU-starvation mitigation. **Gaps**: token auth and reconnection are stubs (return failure responses — graceful, non-throwing).
- **Game packets**: ~34 message handlers (op-loc/npc/obj/player, if-buttons, resume-dialogs, movement, chat).
- **Per-tick network step**: `RspCycle` carries a TODO about restructuring when the net module is "implemented properly" — the author considers the module provisional.
- **Tests**: none in `api/net`.

## 9. Pathfinding

`engine/routefinder` — mature and performance-focused:

- **Algorithm**: BFS flood-fill (not A*) over a 128×128 local search grid; frontier is a power-of-two ring buffer (size 4096) using mask arithmetic. `directions`/`distances` arrays are reused across calls and reset with `Arrays.fill` — effectively zero per-search heap allocation until the final waypoint deque.
- **Size specialization**: dedicated routines for size-1, size-2, and big entities, each with route-blocker-flag variants; 8-direction expansion with diagonal corner-cut checks.
- **Collision**: `CollisionFlagMap` — lazily allocated 8×8-zone `IntArray`s; strategies: Normal, Blocked, Indoors, Outdoors, LineOfSight.
- **Smart vs dumb**: smart BFS `findRoute` vs geometric `naiveDestination`; partial routes use `findClosestApproachPoint` (bounded 21×21 scan).
- **LOS/LOW**: integer fixed-point Bresenham-style ray cast (`LineValidator`, `RayCast`) — allocation-free.
- **Caveat**: the shared `RouteFinding` instance holds mutable buffers → **not thread-safe**; must stay confined to the game thread.
- **Tests**: 20 test files + JMH benchmarks. `api/route` bridges it into the game (per-entity route requests processed in the tick pipeline).

## 10. Plugins

- **Discovery**: ClassGraph classpath scanning — no annotations, no manifest. Two extension points, both wired in `GameServer.kt`:
  - `PluginScript` subclasses → instantiated via Guice by `PluginScriptLoader` (parallel scan on an `availableProcessors` thread pool), then `startup(ScriptContext)` invoked.
  - `PluginModule` subclasses (Guice `AbstractModule` via `ExtendedModule`) → merged into the injector at boot by `PluginModuleLoader`.
- **Type safety**: plugins reference cache types through the `api/type` reference/builder/editor system, verified at boot; mismatches trigger cache re-pack and automatic server restart (`ServerRestartException`).
- **Event binding**: scripts subscribe through typed extension helpers (`onOpLoc`, `onOpNpc`, player/ai variants) over the `EventBus`; suspending handlers run on `GameCoroutine`.
- **Constraint** (from `docs/quirks.md`): private type-reference subclasses are not allowed (must be internal/public); `.local` symbol files silently override root symbols.

## 11. Performance Notes

No serious red flags found; the codebase is visibly allocation-conscious. Observations:

- **Single-threaded tick**: the whole pipeline runs on one thread at 600 ms; overdue-cycle detection logs slow ticks (`GameService`). Fine for accuracy; the ceiling is per-tick CPU.
- `EntityList.nextFreeSlot()` — two-pass O(capacity) linear scan per spawn; only on entity allocation, acceptable.
- `findClosestApproachPoint` — bounded O(441) scan on partial routes; fine.
- Unbound event publishing iterates all subscribers per event type (list scan); keyed events are O(1).
- `PolygonMapSquareBuilder` carries a TODO asking for benchmarks before optimization.
- JMH benchmark suites exist for `objtx` and `routefinder` — the two hottest engine paths.
- Positional-bias mitigation: player iteration order shuffled per tick (`PlayerIdShuffleProcess`).

## 12. Test Coverage Summary

- **Strong**: `combat-formulas` (unit + integration), `objtx` (21 files + JMH), `routefinder` (20 files + JMH), `map`, `game-process`, `player`, `invtx`, `shops`, `repo`, `cache`, `spells-runes`.
- **None**: `api/net`, `api/db`, `api/db-gateway`, `api/account`, most `-plugin` modules, `parsers`, `death`, `specials`, `weapons`, and most of `engine/game` (14 test files vs 274 main files).
- Meta-tests exist (`konsistTest` architecture checks, `docTest`).

---

## Priority Summary

1. **Runtime-throwing stubs** (4 sites, §3) — small in count, but each is a live crash path.
2. **Reconnection + token login** — the two functional holes in an otherwise complete network layer.
3. **Death mechanics** (NPC drop tables, player death inventory) — core gameplay loop currently incomplete.
4. **Content breadth** — 19 skills, all quests, all minigames, and every city beyond Lumbridge are unbuilt; the framework to build them is in place.
5. **Test gaps** — persistence (`db`/`account`) and `net` have zero coverage despite being correctness-critical.
