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

## Batch 2 (user request: 10 more recently updated user-submitted scripts plus AI Wintertodt)

Every Hub plugin is user-submitted, so these are Hub ranks 16–25 by last update, plus
`mke_wintertodt`, which the Hub lists as "AI-Wintertodt" ("Advanced Wintertodt bot with
human-like behavior"). Errors were measured the same way, after the shared base.

| Plugin | Last update | Java lines | Errors |
| --- | --- | --- | --- |
| mining | 2026-10-18 | 1,633 | 32 |
| toweroflife_creaturecreation | 2026-10-18 | 620 | 47 |
| cluesolver | 2026-10-19 | 2,757 | 53 |
| sulphurnaguafigther | 2026-10-19 | 1,262 | 71 |
| shootingstar | 2026-10-17 | 3,333 | 78 |
| nmz | 2026-10-21 | 854 | 90 |
| drokbd | 2026-10-18 | 2,997 | 168 |
| mke_wintertodt (AI-Wintertodt) | 2026-10-13 | 11,810 | 254 |
| drozulrah | 2026-10-18 | 7,513 | 273 |
| aiofighter | 2026-10-21 | 5,258 | 385 |
| slayer | 2026-10-20 | 7,164 | 408 |

`mke_wintertodt`'s "human-like behavior" is anti-detection and falls under the policy below.
Batch 2 is ported after batch 1, in order of fewest errors; ports that share APIs with batch 1
come almost for free.

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
| Hub base | Done (`PluginDescriptor` metadata, `PluginConstants`, `Overlay.setNaughty`) |
| autochompykiller | Claude 1 half done; 5 compile errors left, all Claude 2 reads |
| the other 14 | Not started |
| batch 2 (11) | Queued after batch 1 |
