# Mobile walking collision data

`collision-map.zip` is copied unchanged from chsami/Microbot at
`e01e0d77d79b354472a5737b3aaab2449eb034fc`, under
`runelite-client/src/main/resources/net/runelite/client/plugins/microbot/shortestpath/`.
The project is BSD 2-Clause; see the repository LICENSE.

The north/east bitset decoder follows upstream FlagMap's documented layout.
Mobile routing is an independent bounded A* implementation, with live collision
snapshots for scene walking and door/gate recovery. Lumbridge castle north/south
stairs use the explicit origins, arrivals, object IDs and action strings from
the same revision's `shortestpath/transports.tsv` Lumbridge staircase rows.
Staircase object IDs are resolved from the live scene and must expose the exact
requested direction; legacy spiral-stair IDs and updated named staircases are
supported near those documented origins. Player/object world-location planes
take precedence over a worldview plane that can lag during transitions.
Each transition requires observing the expected plane and landing before the
remaining route continues. Other desktop transport edges (teleports, ships,
ladders elsewhere, quests or dialogue transports) are not implemented.
Missing regions and unavailable transports report route failure rather than
pretending a queued click reached the destination.
