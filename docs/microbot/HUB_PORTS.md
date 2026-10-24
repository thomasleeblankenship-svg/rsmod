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

## Batch 3 (user request, 2026-10-23: 10 more by latest Hub commits)

Hub ranks 26–36 by the last commit touching each plugin directory (Hub `ee6e066`). **Rank 35,
`playermonitor`, is excluded.** It logs out, hops or uses an item when another player comes near,
which serves only to avoid being seen and reported, so it falls under the policy below. Rank 36,
`woodcutting`, takes its place. Errors were measured on 2026-10-23 against the current client.

| Plugin | Last update | Java lines | Errors |
| --- | --- | --- | --- |
| woodcutting | 2026-10-15 | 2,015 | 8 |
| AmmoniteCrabs | 2026-10-16 | 704 | 15 |
| scurrius | 2026-10-15 | 758 | 25 |
| virewatch | 2026-10-16 | 1,036 | 28 |
| tutorialisland | 2026-10-16 | 1,148 | 35 |
| farmtreerun | 2026-10-17 | 2,071 | 54 |
| mahoganyhomez | 2026-10-17 | 2,726 | 54 |
| thieving | 2026-10-16 | 2,179 | 65 |
| magetrainingarena | 2026-10-15 | 1,348 | 82 |
| moonsofperil | 2026-10-16 | 2,191 | 93 |

## Widest remaining gaps (all 33 pending plugins, 2026-10-23)

Number of plugins blocked by each gap. Packages missing entirely: `util.prayer` (13), `util.magic`
(13), `inventorysetups`/`Rs2InventorySetup` (11), `util.security` (`Login`, 10), `util.tabs` (9),
`globval.enums` (6), `util.menu` (6), `util.npc.Rs2NpcModel` (legacy NPC model, 6). Members:
`Microbot.hopToWorld` (11), `Rs2Bank.withdrawX` (10) / `depositAll` overloads (9) / `walkToBank`
(8) / `withdrawOne` (7) / `walkToBankAndUseBank` overloads (7), `Rs2Combat.inCombat` (9),
`Microbot.getMouse` (8), `Rs2Player.eatAt` (8), `Rs2Dialogue.clickOption` (7),
`Rs2Keyboard.typeString` (7). Full list: regenerate with `hubcheck.sh` (see *Measured gap*).

## Inventory setups (done 2026-10-23)

Done: setups load from the desktop plugin's config keys and `Rs2InventorySetup` loads them through
the mobile bank actions. Setups cannot yet be created on the phone; the plan was:

`sandcrabs` and ten others use upstream's `inventorysetups` (about 12,300 lines, mostly the
Inventory Setups panel UI) through `InventorySetup` and `Rs2InventorySetup`. The plan: the setup
data model and its config loading (reads) from Claude 2, then `Rs2InventorySetup`'s bank
withdraw, equip and check actions from Claude 1 over the new `Rs2Bank` actions. The panel UI is
not ported.

World hopping is done (2026-10-23): `Microbot.hopToWorld` and `Login.getRandomWorld`/`getNextWorld`.
`new Login(...)` (auto-login with stored credentials) is not ported.

## Free-to-play test set (2026-10-23)

For testing on a free account, in rough order of simplicity. All are Hub copies unless noted, and
none has run on a phone yet.

| Plugin | Where / what | Needs |
| --- | --- | --- |
| Auto Woodcutting (built in, Microbot Mobile → Script) | Any F2P trees; drop or bank | An axe |
| barbarianvillagefisher | Fly or bait fishing at Barbarian Village | Rod and feathers or bait |
| autofishing | Fishing with banking (choose an F2P fish) | The tool and bait for that fish |
| smelting | Furnace smelting (bronze, iron, steel and so on) | Ores in the bank, near a furnace |
| gefiremaker | Firemaking on the campfires around the Grand Exchange | Logs and a tinderbox |
| varrockanvil | Smithing at the Varrock West anvils | Bars and a hammer |
| chillRunecraft | F2P runecrafting | Essence, and Rune Mysteries for the altars |
| banksbankstander | Bank-standing skills: combine or use items from the bank (configured by id or name) | The items in the bank, standing at a bank |

banksbankstander's quick mode (one action on every inventory slot, as when cleaning herbs) issues one
action per game tick on mobile, so it is slower than on desktop.
`autoessencemining` needs the API's `ObjectID1` public and slot-to-slot item moves.

## KSP Chopper (2026-10-24)

KSP's **Chopper** (`kspwillowchopper`, v0.1.3, from `KSPOG/ksppluginsrelease@65c0e59`, ported
with the permission the repository owner reported) is in the client, unchanged, under
`plugins/microbot/kspwillowchopper/`, with KSP's `KspBreakAwareScript`. It chops trees with
banking, deposit boxes, dropping or firemaking, and handles Forestry events. The unmodified
original is kept in `third_party/ksp/` (see its `NOTICE.md`). Like the Hub plugins it is hosted
by `HubPluginHost` and disabled by default.

On mobile:

- **No breaks and no mule.** `KspBreakService`, `KspBreakConfig` and `kspmule/**` are inert
  RuneMobile stand-ins: KSP's randomized logout breaks with automatic re-login are scheduled-break
  anti-detection behaviour, and its mule hands items to another account, so neither is ported.
  The plugin's "Break Handler" and "Local Mule" config sections are therefore empty.
- **Mouse clicks are taps.** The Forestry leprechaun step taps the target's minimap point
  directly (`Microbot.getMouse().click`), without the walker's safe-disc check, so a point near
  the minimap edge could hit an orb.
- **Prompts** (the firemaking and beehive amount prompts) are answered with the space key.
- Not run on a phone yet.

## How "mobile" the ported plugins are (2026-10-23)

Compiling unchanged is not the same as working on a phone. Every ported plugin runs under the mobile
rules (explicit start, one exact menu action per game tick, stops on logout, unrequested hop,
disconnect or backgrounding, pauses on a user tap), but **none has been run on a phone yet**.
Known differences from desktop:

- **Prayer helpers** (jad, zulrah, colosseumprayer, autogauntletprayer, delveprayerhelper,
  HueycoatlPrayer, zeroprayer): each toggle opens the prayer tab first, which can cost an extra
  game tick; tight switches may land late. Needs a phone test before relying on it.
- **Overlay-heavy plugins** (theatre, zulrah, parts of others) draw desktop overlays, which may not
  suit the phone screen.
- **Inventory setups** match by item totals, not slot order; the rune pouch is checked, not filled.
- **Anti-ban settings** are inert. **Walking** (with the pathfinder setting on) executes stairs,
  doors, shortcuts, boats, ships, travel NPCs, charter ships, teleport items, teleport spells,
  fairy rings and spirit trees; not yet canoes, gliders, minecarts, quetzals, levers, portals or
  POH. None of the transports has been tried on a phone.

## Policy for anti-detection features (applies to every port)

User asked (2026-10-23) to keep anti-ban "for measure". Kept as the inert compatibility layer: scripts
compile, and the activity, play style and settings they request are recorded and readable. The active
behaviour stays unported.

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
| **Ported (33 + KSP Chopper)** | banksbankstander, smelting, gefiremaker, autofishing, barbarianvillagefisher, autochompykiller, fishingtrawler, birdhunter, toweroflife_creaturecreation, salamanders, HueycoatlPrayer, sandcrabs, AmmoniteCrabs, animatedarmour, anonymous, autogauntletprayer, baggedplants, blessedwine, butterflycatcher, chillRunecraft, colosseumprayer, delveprayerhelper, gauntlethelper, goldrush, hunterKabbits, jad, lunartablets, minnowsfishing, pumper, theatre, varrockanvil, zeroprayer, zulrah. Hub sources copied byte-identical (Hub `ee6e066`) into `plugins/microbot/<name>/` with their resources; hosted by `HubPluginHost`, disabled by default (`HubPortedPluginsTest`). Style-only checkstyle/PMD exemptions are scoped to these directories. None run on a phone yet |
| Compile but not ported | `chatbot` (AI-written replies to other players in chat: presents the bot as a person to them), `microbotdashboardplus` (desktop Swing window and Discord webhooks; no phone equivalent), `actions` (library, no plugin), `playermonitor` (anti-detection policy) |
| woodcutting (Hub) | Compiles unchanged, **not copied**: `plugins/microbot/woodcutting` is our mobile-adapted AutoWoodcutting (phone-tested), which a copy would overwrite. Batch 3's tenth slot passes to the next eligible plugin |
| shared Hub APIs | Done: `@ConfigInformation`, break-handler lock state, camera, `Rs2Random`, plugin control, `interact`/`isWearing`, `waitForWalking`, inert anti-ban types, `dropAll`, `Rs2Inventory.interact`/`wield`/`wear`, `waitForXpDrop`, `IEntity.click`/queryable `interact`, public `HunterTrap`, `Rs2Bank` withdraw/deposit/walk overloads, `Rs2Keyboard.typeString`, `Rs2Tab`, `InterfaceTab`, walker location enums, `isInArea`, prayer toggles, auto retaliate, `eatAt`/`useFood`, `toggleRunEnergy` |
| errors now (2026-10-23) | AmmoniteCrabs 5, scurrius 7, sandcrabs 10, virewatch 10, gotr 15, sulphurnaguafigther 16, cluesolver 17, tutorialisland 18, microhunter 19, nmz 19, mining 21, ourania 24, shootingstar 32, crafting 32, blackjack 37, farmtreerun 38, thieving 42, mahoganyhomez 43, moonsofperil 63, drokbd 65, magetrainingarena 69, mke_wintertodt 80, drozulrah 85, barrows 91, drofirecape 110, sailing 112, geflipper 128, slayer 160, aiofighter 237 |
| batch 2 (11) | Queued after batch 1 |
