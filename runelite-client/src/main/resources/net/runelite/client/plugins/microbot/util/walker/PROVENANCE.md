# Mobile walking collision data

`collision-map.zip` is copied unchanged from chsami/Microbot at
`e01e0d77d79b354472a5737b3aaab2449eb034fc`, under
`runelite-client/src/main/resources/net/runelite/client/plugins/microbot/shortestpath/`.
The project is BSD 2-Clause; see the repository LICENSE.

The north/east bitset decoder follows upstream FlagMap's documented layout.
Mobile routing is an independent bounded A* implementation, with live collision
snapshots for scene walking and door/gate recovery.

`transports.tsv` is copied unchanged from the same revision's
`shortestpath/transports.tsv` (SHA-256
`966e0c4b7237489f0f205162eb71c68cd2f792871d3f62b37b7ebfb1c06655b7`).
`ObjectTransports` keeps only requirement-free, floor-changing object rows: the menu
column is `option;target;objectId`, and the skill, item, currency, quest, varbit,
varplayer and members columns are all empty. That gives 2,410 staircases, ladders
and trapdoors. A live object near a row's origin matches when it exposes the exact
action and has the recorded ID or name, so updated and legacy IDs both work.
Single-floor climbs are preferred over one-click multi-floor shortcuts. A transport
whose action is absent on the live object is skipped for the rest of that walk.
Each transition requires observing the expected plane and landing before the route
continues. Same-plane transports (dungeon entrances that jump coordinates),
teleports, ships and anything with a requirement are not implemented. Missing regions
and unavailable transports report route failure rather than pretending a queued
click reached the destination.
