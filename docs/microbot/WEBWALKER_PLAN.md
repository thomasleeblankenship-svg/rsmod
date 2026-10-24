# Webwalker and pathing: split between Claude 1 and Claude 2

User request (2026-10-23): "delegate between claude 1 and 2 finishing webwalker and pathing
correctly". This file is the plan; claims, requests and the change log stay in
[`COORDINATION.md`](COORDINATION.md).

## Where we are

- `util/walker/Rs2Walker` (Claude 1) walks with `MobileRoutePlanner` (a pinned same-plane
  collision snapshot plus the loaded scene) and `ObjectTransports` (requirement-free stairs and
  ladders from `transports.tsv`). There are no teleports, ships, agility shortcuts, quest-gated
  doors or long cross-region routes beyond the snapshot.
- Upstream Microbot (`a2114401`) walks with `shortestpath/**`: about 10,000 lines, a whole-world
  `collision-map.zip`, and transport tables (stairs, doors, shortcuts, boats, minecarts, fairy
  rings, spirit trees, teleport items and spells) with skill, quest, varbit and item requirements.
- Hub plugins call `Rs2Walker.walkTo` 335 times, `walkFastCanvas` 106, `setTarget` 41,
  `walkMiniMap` 12, `walkWithState` 7, `getWalkPath` 7, and `ShortestPathPlugin.getPathfinderConfig`
  / `getPathfinder` / `getTransports` / `exit`, `Restriction`, `WorldPointUtil`, `Pathfinder`.
- Phone reports (build 28): walks reach the bank but in short stop-start steps; chopping is slow
  between actions. The Lumbridge stairs have not been retested since `ba30cb9`.

## Split

The line is the same as everywhere else: **Claude 2 computes (reads and data, no input);
Claude 1 acts (every click, tap, key and transport interaction).**

### Claude 2: pathfinding and data, in new `microbot/shortestpath/**`

1. Port the upstream pathfinder core, headless: `WorldPointUtil`, `PrimitiveIntHashMap`,
   `pathfinder/{CollisionMap, SplitFlagMap, VisitedTiles, PathEdge, PathSmoother, Pathfinder,
   PathfinderConfig}`, `Transport`, `TransportType`, `TransportItemRequirement`, `Restriction`,
   and the resource tables (`collision-map.zip`, `transports.tsv`, the other `*.tsv`). Not ported:
   the panels and overlays (`ShortestPathPanel`, `PohPanel`, `Path*Overlay`), live collision
   capture (`pathfinder/live/**`) and the purchasable-item catalogue, unless a later step needs them.
2. Requirement checks (skills, quests, varbits, inventory, equipment) read through the existing
   caches on the client thread; the search itself runs off the client thread.
3. A `ShortestPathPlugin` static facade with upstream's names (`getPathfinderConfig`,
   `getPathfinder`, `getTransports`), no panel or overlay. `exit()` calls
   `Rs2Walker.setTarget(null)` (Claude 1's).
4. One entry point for the walker, for example
   `PathResult MobilePathfinder.find(WorldPoint start, WorldPoint goal)`: the tile path, the
   transport (with its required action, object id and destination) at each transport index, and a
   status (found, partial, unreachable, cancelled, timed out).
5. Route tests over the real collision map, no game: Lumbridge castle upstairs bank to Lumbridge
   trees and back (both staircases), Draynor bank to the willows, Varrock West bank to the Grand
   Exchange, Falador East bank to the park. Each asserts the transports used and that no tile
   crosses a blocked edge.

### Claude 1: execution, in `util/walker/**` and `headless/JavaBridge.java`

1. **Now, from the phone report:** longer steps. Walk with a minimap tap when the waypoint is
   further than a few tiles (the minimap stays visible however the camera is), keeping the
   scene-tile tap for short and exact steps. Re-click only when close to the last clicked target or
   stopped. Return to long steps after a stall recovery once the player is moving again (today a
   single slow moment leaves the rest of the walk in 3-tile steps).
2. Execute `PathResult`: walk the tiles, interact with each transport (stairs, ladders, doors,
   gates, shortcuts) and require observed arrival before the next leg; stall recovery, door
   reopening and cancellation as today.
3. Switch `Rs2Walker.walkTo` to Claude 2's pathfinder behind a setting, with today's planner as
   the fallback until the phone confirms the new route. `ObjectTransports` is retired after that.
4. Upstream's walker surface that Hub plugins use: `walkFastCanvas`, `walkMiniMap`,
   `walkFastLocal`, `walkCanvas`, `walkWithState`/`walkWithStateUntil`, `getWalkPath`,
   `clearWalkingRoute`, `getTotalTiles`, `getClosestTileIndex`, `getCurrentTarget`,
   `getTransportsForDestination`, `getMissingTransportItemIdsWithQuantities`.
5. Teleports (items, spells, fairy rings and the like) come last: they need item use and casting,
   which wait on the magic actions.

## Interface rules

- Claude 2 owns `microbot/shortestpath/**`; Claude 1 owns `util/walker/**`. Neither edits the
  other's files without a claim approved in `COORDINATION.md`.
- `PathResult` is the contract. A change to it is a request in `COORDINATION.md` first.
- No anti-detection behaviour in either half: no humanised paths, randomised click offsets or
  deliberate pauses.

## Done means

- The five route tests pass, and the walker tests pass with the new pathfinder switched on.
- A phone walk from the Lumbridge upstairs bank to the trees and back, and one Draynor bank cycle,
  each with long steps, no stall retries, and observed arrival at both staircases.

## Progress (Claude 1, execution)

- Done: minimap walking and longer steps; `PathResult` execution behind the *Use pathfinder*
  setting (default on, same-plane planner as fallback); stairs, doors and shortcuts; boats,
  ships, travel NPCs and charter ships; teleport items (worn or carried, by catalogue ids);
  teleport spells; fairy rings (Dramen or Lunar staff wielded unless the Lumbridge elite diary is
  done, then the previous weapon again; "last-destination" when it names the code, else the three
  dials and Confirm); spirit trees ("Travel", then the menu entry, else its shortcut key).
- Done (step 7): teleport levers and portals (one click, landing within 8 tiles), minecarts with a
  fixed destination (Keldagrim trapdoor and the like) and magic carpets as travel steps. No
  transport may land in the Wilderness unless the walk's goal is there, whatever the pathfinder
  returns.
- Done (step 8): gnome gliders ("Glider" on the gnome, then the destination's button on the
  glider map, as upstream).
- Done (step 9): hot air balloons ("Use" on the basket, then the destination's button on the
  balloon map, as upstream's `Rs2HotAirBalloon`). Gliders and balloons share one table of
  destination maps.
- Done (step 10): the Lovakengj minecarts, whose destination list (947:9) is answered like the
  spirit tree menu (the entry by name, else its shortcut key).
- Next: quetzals and magic mushtrees (each needs its destination interface), canoes, then POH (routing is off on Claude 2's side
  until then). Wilderness obelisks are not planned: their destinations are random and in the
  Wilderness.
- None of these transports has been tried on a phone yet.

## Finishing split (user request 2026-10-24: "the walker is almost complete delegate the work between both of you")

Same line as before: **Claude 2 computes (paths, data, requirements), Claude 1 acts (every click
and executor).** Hub use counts are calls across the Hub sources.

### Claude 2: reads, data and pathfinding

| # | Work | Notes |
| --- | --- | --- |
| C2-1 | **POH routing**: turn on `POH` in `PathfinderConfig` with real requirement checks (house teleport tab, spell or construction cape; the house's portals, nexus, jewellery box, fairy ring and spirit tree read from varbits), and say in `PathResult` which POH object and option each step uses | Claude 1 executes it (C1-4). Propose any `PathResult` change in `COORDINATION.md` first |
| C2-2 | **Path reads for the walker API**, in a new `shortestpath/` facade (yours): `getWalkPath(WorldPoint)` (7 Hub uses), `getTotalTiles` (4), `getClosestTileIndex` (2), `getTransportsForDestination` (1), `getMissingTransportItemIdsWithQuantities` (1), `isWalkableInCollisionMap` (1), `getTile` (1) | Claude 1 adds the one-line `Rs2Walker` statics that call them |
| C2-3 | **Contract route tests** over the real collision map for every transport the walker executes: fairy ring (staff requirement), spirit tree, glider, balloon (logs), Lovakengj minecart, magic carpet (coins), portal, lever (Wilderness avoided by default), teleport item and spell. Each asserts the `type`, `action`, `objectId` and `displayInfo` the executor relies on | Catches data or merge changes that would break an executor silently |
| C2-4 | **Data for the last three transports**: quetzals (destination names, Varlamore unlock varplayers), magic mushtrees (destination text), canoes (station, destination, Woodcutting level, axe). Confirm `PathResult` carries what an executor needs | Claude 1 then writes the executors (C1-2) |
| C2-5 | **Search cost on a phone**: time a long route (Lumbridge to Varrock, Draynor to Falador) on the slowest path, and cache or cut off so `walkTo` starts moving within a tick or two | |

### Claude 1: execution and the walker API

| # | Work | Notes |
| --- | --- | --- |
| C1-1 | **Done** — **Missing walker actions**: `walkMiniMap` (12 Hub uses), `walkFastLocal` (12), `walkCanvas` (9), `walkWithState` (7), `walkWithStateUntil` (3), `clearWalkingRoute` (6), `getCurrentTarget` (1) | First, as it unblocks the most plugins |
| C1-2 | **Executors** for quetzals (Renu's map), magic mushtrees (destination menu) and canoes (the station steps and destination map) | After C2-4 |
| C1-3 | The `Rs2Walker` statics for C2-2's reads | Right after C2-2 lands |
| C1-4 | **POH execution**: house teleport, then the portal, nexus, jewellery box, fairy ring or spirit tree inside | After C2-1 |
| C1-5 | **Retire `ObjectTransports`** and the same-plane planner fallback once a phone walk confirms the pathfinder route; keep a phone checklist per transport | Needs phone results from the user |
| C1-6 | `JavaBridgeKeyTimingTest` intermittent failure (not walker): spans of 1, 3, 41 and 55 ms in full-suite runs suggest concurrent key-pump chains | Own change |

Wilderness obelisks stay out (random Wilderness destinations), as does any transport that lands
in the Wilderness unless the goal is there.

