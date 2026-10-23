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
- Next: canoes, gnome gliders, minecarts, quetzals, levers, portals, then POH (routing is off on
  Claude 2's side until then).
- None of these transports has been tried on a phone yet.
