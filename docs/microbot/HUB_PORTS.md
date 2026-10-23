# Microbot Hub ports: the 15 most recently updated plugins

User request (2026-10-23): load the 15 most recently updated Microbot Hub plugins.

- **Hub source:** `chsami/Microbot-Hub` at `ee6e066e04b29c1e12e554b3f73439217bdc0eed`
  (2026-10-23 07:14 +02:00), BSD 2-Clause, ranked by the last commit touching each plugin
  directory.
- **Microbot reference:** moved from `e01e0d77` (2.6.37) to **`a2114401bbbc35e9762db9e8aa666d70ffcd24d8`
  (2.6.38, main, 2026-10-23 06:34 +02:00)**. Some of these plugins declare
  `minClientVersion = "2.6.38"`. Between those commits, of the files already ported only
  `api/playerstate/Rs2PlayerStateCache` changed (a varp range guard and `isValidVarpId`). Within
  the utility library, only `util/widget/Rs2Widget` (null-text handling), `util/npc/Rs2Npc`,
  `util/bank/Rs2Bank` and `util/player/Rs2Player` changed, by a few lines each. Every
  verbatim-copied runtime file is unchanged at the new reference.

## Measured gap

Method: each plugin is compiled **unchanged** with `javac` against the built RuneMobile client,
reporting every error. The numbers below are compile errors after the shared Hub base
(`PluginDescriptor` metadata, `PluginConstants`, `Overlay.setNaughty`).

| Order | Plugin | Java lines | Errors | Main gaps |
| --- | --- | --- | --- | --- |
| 1 | autochompykiller | 847 | 16 | `WalkerState`/`walkStep`, object `interact`, `isInCombat`, `logout`, equipment slot, inventory `hasItem`/`drop` |
| 2 | fishingtrawler | 294 | 24 | camera, break handler, dialogue |
| 3 | sandcrabs | 549 | 27 | inventory setups, food, login, tabs, player model |
| 4 | birdhunter | 647 | 29 | NPC/object interaction, inventory |
| 5 | salamanders | 716 | 46 | anti-ban activity enum, time utils, traps |
| 6 | HueycoatlPrayer | 976 | 54 | `Rs2Prayer`/`Rs2PrayerEnum` |
| 7 | microhunter | 1,736 | 56 | shop, menu entries, break handler |
| 8 | gotr | 1,583 | 65 | magic, spellbook, pouches, NPC |
| 9 | ourania | 1,102 | 100 | magic, pouches, Grand Exchange, potions |
| 10 | blackjack | 6,548 | 102 | shop, camera, break preparation |
| 11 | sailing | 5,774 | 137 | boat cache, magic, menu entries, UI helper |
| 12 | crafting | 2,536 | 150 | magic, widgets (crafting interfaces), bank |
| 13 | geflipper | 4,983 | 160 | Grand Exchange, custom mouse-motion humaniser, config panel UI |
| 14 | drofirecape | 13,403 | 278 | prayer, thralls, inventory setups, input arbiter, minimap |
| 15 | barrows | 3,830 | 291 | prayer, magic, combat spells, inventory setups, path API |

## Policy for anti-detection features (applies to every port)

Claude 1 does **not** port anti-ban or "human-like input" machinery: the `Rs2Antiban`
behaviour settings (fatigue, attention span, mistakes, play-style), natural-mouse, mouse-motion
humanisers (for example geflipper's `GeflipperMouseMotion`), or session fatigue. These exist to
evade the game's bot detection. Where a plugin references them, the port removes or neutralises
those calls in a clearly marked adaptation, and the plugin is listed as **adapted**, not
unchanged.

## Ownership split (see `COORDINATION.md`)

- **Claude 2 (read paths):** `Rs2Player`, `Rs2Inventory` and `Rs2Bank` read methods; NPC,
  object and item model and queryable reads (`*OnClientThread`, `getInteracting`,
  `getCombatLevel`, `isDead`, `hasLineOfSight`, `getClickbox`, ...); `Rs2Widget` reads; dialogue
  state reads; `Rs2Tile` reads.
- **Claude 1 (actions and the rest):** walker (`WalkerState`, `walkStep`, ...), object, NPC
  and item interaction, inventory, bank and equipment actions, dialogue clicks, keyboard,
  combat toggles, prayer, magic, camera, tabs, shop, menu entries, break handler, inventory
  setups, `Microbot` members, plugin hosting and input permission for Hub plugins.

## Status

| Plugin | Status |
| --- | --- |
| all 15 | Hub base done; ports not started |
