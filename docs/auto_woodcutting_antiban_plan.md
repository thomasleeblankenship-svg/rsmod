# Auto Woodcutting — Antiban System Plan

_Status: plan only — nothing in this document is implemented yet._

This plan describes a **server-side** auto-woodcutting feature for RS Mod and the "antiban"
layer that governs it. Because the server owns the feature, "antiban" here means two things:

1. **Humanization** — the automated loop behaves like a varied, imperfect human (reaction
   variance, fatigue, breaks, idle actions, imperfect tree choice), configurable per player.
2. **Server-enforced rules** — limits the server owner sets that players can *see* but cannot
   change (session caps, presence checks, random events, safety aborts, XP-rate ceiling).

Every feature below is either **Locked** (visible, read-only, owner-controlled) or
**Configurable** (player-editable, but always clamped to the bounds the locked policy allows).

---

## 1. What already exists (reuse, do not duplicate)

| Need | Existing system | Location |
|---|---|---|
| Chop logic, axe choice, success rolls, tree depletion | `Woodcutting` plugin script | `content/skills/woodcutting/.../scripts/Woodcutting.kt` |
| Starting a chop on a tree | `ProtectedAccess.opLoc1(loc)` — routes into the existing `onOpLoc1(content.tree)` handler | `api/player/.../protect/ProtectedAccess.kt:761` |
| Successful-cut hook | `Woodcutting.CutLogs` unbound event (already published) | `Woodcutting.kt` |
| Random events | `RandomEventTrigger.tryTriggerMysteriousOldMan()` — wired into Fishing, Mining, Smithing, **not yet Woodcutting** | `api/player/.../randomevent/RandomEventTrigger.kt` |
| Timed/deferred actions | `queue` / `weakQueue` / `strongQueue` | `ProtectedAccess.kt:1759-1775` |
| Facing / walking | `faceSquare`, `faceLoc`, `walk` | `ProtectedAccess.kt` |
| Player settings storage | `VarpBuilder` / varbit builders (`temporary = false` ⇒ saved on logout) | `api/type/type-builders/.../varp`, `.../varbit` |
| Settings UI without new cache interfaces | `menu(title, choices)` dialogue, `countDialog` | `ProtectedAccess.kt:2735, 2835` |
| Chat commands | `onCommand(...)` | `content/other/commands/AdminCommands.kt` |
| Server-wide configuration | `RealmConfig` (DB-backed, hot-updatable) | `api/realm/.../Realm.kt` |
| Seeded randomness | `GameRandom` | `api/random` |

**Key rule:** the auto loop never re-implements chopping. It only *decides* what a human would
do next (which tree, when, how long to pause) and then issues the same interactions a real
click would (`opLoc1`, walk, drop). All XP, success rates, depletion and messages stay in
`Woodcutting.kt`.

---

## 2. Architecture

```
content/skills/woodcutting           (existing — unchanged except random-event wiring)
api/antiban                          (NEW — generic, skill-agnostic engine)
   ├─ policy/     AntibanPolicy        locked rules, bounds for every configurable value
   ├─ settings/   AntibanSettings      per-player configurable values (varp-backed)
   ├─ model/      ReactionModel, FatigueModel, BreakScheduler, Distributions
   ├─ idle/       IdleAction registry  cosmetic humanizing actions
   └─ session/    AutoSession          state, quotas, audit log
content/skills/woodcutting-auto      (NEW — woodcutting-specific driver + menu + command)
   ├─ AutoWoodcuttingScript            state machine, tree selection, inventory handling
   ├─ AntibanMenuScript                settings menu + "Locked rules" page
   └─ configs/                         varps, varbits, queues
```

`api/antiban` is skill-agnostic so Fishing/Mining could adopt it later without copying code.
`content/skills/woodcutting-auto` depends on `api/antiban` and on the woodcutting module's
public helpers (`Woodcutting.findAxe`, tree content group).

### 2.1 Auto-session state machine

```
           ┌──────────────────────────────────────────────────────────┐
           ▼                                                          │
 IDLE ─► SELECT_TREE ─► WALK ─► CHOP ──(tree depleted)──► REACT ──────┤
                          ▲       │                                   │
                          │       ├─(inv full)─► INVENTORY ─(drop/bank)┤
                          │       ├─(break due)─► BREAK ───────────────┤
                          │       ├─(presence check due)─► CHECK ──────┤
                          │       └─(idle action roll)─► IDLE_ACTION ──┘
                          │
   any state ─(locked abort: combat, area exit, quota, failed check, random event ignored)─► STOPPED
```

Each tick costs O(1) per auto player; tree search reuses `LocRepository` zone lookups within a
bounded radius so it cannot blow the tick budget.

---

## 3. Locked features (visible, players cannot change)

Shown in the menu under **"Locked rules"** with the current value and a one-line reason.
Values come from `AntibanPolicy`, sourced from `RealmConfig` so the owner can change them
without a restart.

| # | Rule | Default | Behaviour |
|---|---|---|---|
| L1 | **Max continuous session** | 3h | Hard stop + forced long break when reached. |
| L2 | **Daily auto quota** | 6h / account / day | Tracked in a persistent varp with a day stamp; resets at server midnight. |
| L3 | **Mandatory break floor** | ≥ 1 micro-break / 20 min, ≥ 1 long break / 90 min | Players can make breaks *more* frequent/longer, never less. |
| L4 | **Reaction-time floor** | ≥ 2 ticks after depletion | No frame-perfect re-clicks; applied after all player-chosen delays. |
| L5 | **XP-rate ceiling** | ≤ 85 % of expected manual rate | Measured over a rolling 15-min window; if exceeded, the engine injects extra delay. |
| L6 | **Presence checks** | every 25–45 min (random) | Dialogue "Are you still there?" with a random answer order; 60 s to respond or auto stops. |
| L7 | **Random events always on** | — | Woodcutting rolls `RandomEventTrigger`; auto pauses until the event is resolved; ignoring it stops the session. |
| L8 | **Area whitelist** | owner-defined woodcutting zones | Auto only starts inside one; leaving its radius stops the session. |
| L9 | **Danger abort** | always | Stop on: taking damage, being targeted by an NPC/player, entering the Wilderness, HP below 50 %. |
| L10 | **No teleports** | always | Banking only via walking to owner-approved banks. |
| L11 | **Fatigue always simulated** | always | Players tune its sensitivity within bounds, but cannot disable it. |
| L12 | **Transparency** | always | Other players see an "is auto-chopping" marker (examine text / periodic public chat line — final form decided in Phase 3). |
| L13 | **Audit log** | always | Start/stop/reason/duration/XP logged server-side for admins. |
| L14 | **Idle-logout interplay** | always | Auto activity does **not** reset the idle-logout timer; only passed presence checks do. |

### 3.1 Bounds enforced on every configurable value

Every configurable value in §4 has a `min`/`max` in `AntibanPolicy`. Settings are clamped at
**read time** (not only at write time), so lowering a bound in `RealmConfig` immediately
affects players with old saved values. The menu shows clamped values with a 🔒 marker and the
bound that applies.

---

## 4. Configurable features (players can change)

Grouped as they appear in the menu. Each group has a preset selector plus "Custom".

### 4.1 Reaction & timing
| # | Option | Choices / range |
|---|---|---|
| C1 | Reaction profile | Snappy · Average · Relaxed · Sluggish · Custom |
| C2 | Custom reaction distribution | Gaussian · Log-normal · Exponential-tail · Bimodal ("usually quick, sometimes distracted") |
| C3 | Mean / spread | mean 2–12 ticks, spread 0–6 ticks (clamped by L4) |
| C4 | Hesitation chance | 0–15 % chance to pause 1–4 ticks between swings |
| C5 | Misclick simulation | 0–5 % chance to click a wrong nearby tile/loc first, then correct |

### 4.2 Breaks
| # | Option | Choices / range |
|---|---|---|
| C6 | Micro-break frequency | every 5–20 min (≥ L3 floor) |
| C7 | Micro-break length | 5–90 s, random within range |
| C8 | Long-break frequency | every 30–90 min (≥ L3 floor) |
| C9 | Long-break length | 2–20 min |
| C10 | Break scheduling mode | Fixed interval · Uniform random · Poisson (memoryless) · "Human" (Poisson + more breaks as fatigue rises) |
| C11 | During-break behaviour | Stand still · Wander 1–5 tiles · Face random direction · Idle-action burst |

### 4.3 Fatigue
| # | Option | Choices / range |
|---|---|---|
| C12 | Fatigue curve | Linear · Exponential · Sigmoid (slow start, sudden tiredness) |
| C13 | Fatigue sensitivity | 0.5×–2× (cannot be 0 — L11) |
| C14 | Recovery rate during breaks | 0.5×–2× |
| C15 | Fatigue effects (multi-select) | slower reactions · more hesitation · more misclicks · more idle actions · worse tree choice |

### 4.4 Tree selection & movement
| # | Option | Choices / range |
|---|---|---|
| C16 | Tree strategy | Nearest · Distance-weighted random · Sticky favourite · Least contested (fewest players) · Round-robin |
| C17 | Sub-optimal choice chance | 0–25 % chance to pick the 2nd/3rd best tree |
| C18 | Path variance | 0–3 tile random offset on walk destinations |
| C19 | Tree-type filter | multi-select from trees the player can cut, within the whitelisted area |
| C20 | Anticipation | Off · Pre-walk toward next tree when current one is "about to" fall (chance-based, never informed by hidden state) |

### 4.5 Inventory
| # | Option | Choices / range |
|---|---|---|
| C21 | Full-inventory action | Drop · Bank (walk) · Stop |
| C22 | Drop pattern | Row-by-row · Column · Zig-zag · Random order · Mixed (random per trip) |
| C23 | Drop-speed variance | 0–2 ticks between drops |
| C24 | Early-drop threshold | drop at 20–28 logs (random each trip) |
| C25 | Bank behaviour | Deposit-all · Deposit item-by-item; random bank-booth choice |

### 4.6 Idle actions (cosmetic humanization)
Each has a weight 0–10; the engine rolls between swings and during breaks.

| # | Action |
|---|---|
| C26 | Examine a nearby loc/obj |
| C27 | Face a random nearby tile |
| C28 | Perform a random (unlocked) emote — reuses the emotes module |
| C29 | Toggle run on/off |
| C30 | "Check XP" — chat message with current XP / XP to next level |
| C31 | Short wander and return |
| C32 | Overall idle-action rate | 0–10 per hour |

### 4.7 Personality & profiles
| # | Option | Choices / range |
|---|---|---|
| C33 | Personality preset | Focused · Casual · Distracted · Tired-worker · Weekend-warrior · Custom |
| C34 | Behaviour seed | Re-roll — gives this player a unique, persistent variation on top of the preset so no two players behave identically |
| C35 | Time-of-day profile | Off · On (more fatigue/breaks during the player's configured "late" hours) |
| C36 | Saved profiles | 3 slots: save / load / rename / reset to defaults |

### 4.8 Goals & notifications
| # | Option | Choices / range |
|---|---|---|
| C37 | Stop conditions (multi) | at level X · after XP Y · after N logs · after T minutes · when no valid trees |
| C38 | Personal safety (stricter only) | stop if > N players nearby · stop at HP < X % (X ≥ L9 value) |
| C39 | Notifications verbosity | Silent · Important (stops, level-ups) · Verbose (breaks, checks, tree switches) |
| C40 | Session summary on stop | On/Off — XP, logs, duration, breaks taken, stop reason |

---

## 5. Behaviour model (the "complex" core)

All randomness goes through `GameRandom`, seeded per session from the player's behaviour seed
(C34) so tests are deterministic.

- **Reaction delay** (ticks):
  `delay = max(L4_floor, round(sample(dist, mean, spread) × (1 + α·F)))`
  where `F ∈ [0,1]` is fatigue and `α` comes from fatigue sensitivity (C13) if
  "slower reactions" (C15) is enabled.
- **Fatigue**: updated each minute.
  - Working: `F += r·g(F)` where `g` is the chosen curve (linear `1`, exponential `(1-F)`, sigmoid
    `F(1-F)` with a small floor so it starts).
  - Break: `F *= exp(-k·t)`, `k` from recovery rate (C14).
- **Break scheduling**: Poisson process with rate `λ = 1/meanInterval`; "Human" mode uses
  `λ(F) = λ₀(1 + βF)`. Locked floors (L3) and the session cap (L1) override any sample.
- **XP ceiling (L5)**: a rolling window tracks XP/hour; when over the ceiling, an extra delay is
  added to the next reaction, proportional to the excess.
- **Tree choice**: candidates within radius are scored by distance, player count, and type
  filter; strategy (C16) picks a scoring function; C17 adds a softmax-style chance of choosing a
  lower-ranked tree; fatigue (C15) widens that chance.
- **Idle actions**: weighted random selection (C26–C31), rate from C32 scaled by fatigue.

---

## 6. Storage

- **Player settings**: new non-temporary varps, bit-packed via varbits (≈ 40 options fit in
  ~8 varps). 3 profile slots ⇒ ~24 varps plus an "active profile" varbit.
- **Quota/session tracking**: day stamp + minutes-used varps (persistent); current-session state
  in temporary varps.
- **Policy**: `AntibanPolicy` fields added to `RealmConfig` (DB-backed), with defaults from §3.

The exact varp count is finalized in Phase 2 once options are packed.

---

## 7. Player entry points

- `::autowc` command — opens the start dialogue (start / settings / locked rules / last summary).
- `::autowc stop` — stops immediately.
- Settings menu built from `menu(...)` pages and `countDialog` for numeric ranges; no new cache
  interfaces are required.

---

## 8. Implementation phases (one commit per feature)

| Phase | Deliverable | Tests |
|---|---|---|
| 0 | Wire Woodcutting into `RandomEventTrigger` (prerequisite for L7) | Integration: event can spawn while chopping |
| 1 | `api/antiban` core: `AntibanPolicy`, distributions, `FatigueModel`, `BreakScheduler`, clamping | Unit: deterministic distribution samples, clamping, fatigue curves |
| 2 | Persistent settings varps/varbits + `AntibanSettings` + profile slots | Unit/integration: save → logout → load round-trip |
| 3 | `woodcutting-auto` state machine (nearest tree, drop), `::autowc`, transparency marker | Integration: auto chops via existing `Woodcutting` script and gains XP |
| 4 | Breaks + fatigue integration (C6–C15, L1, L3, L11) | Integration: break floors respected, session cap stops auto |
| 5 | Locked safety: presence checks, random-event pause, danger abort, area whitelist, quota, audit log (L2, L6–L10, L13, L14) | Integration: each abort reason |
| 6 | Tree strategies, sub-optimal choice, path variance, anticipation (C16–C20) | Unit: strategy scoring; integration: tree switching |
| 7 | Inventory strategies + banking (C21–C25) | Integration: each drop pattern, bank trip |
| 8 | Idle actions, personalities, behaviour seed, time-of-day (C26–C36) | Unit: weighted selection; integration: emote/examine fire |
| 9 | Goals, notifications, session summary, XP ceiling (C37–C40, L5) | Integration: each stop condition; XP ceiling throttles |
| 10 | Menu polish + "Locked rules" page; docs update (`master_roadmap.md`, this file) | Konsist + doc tests |

Each phase: `gradlew compileKotlin`, `gradlew spotlessKotlinApply`, `gradlew test`,
`gradlew integration` before committing.

---

## 9. Open decisions for the owner

1. **Who can use auto?** All players, members only, or a rank/donor gate?
2. **Default locked values** — are the §3 defaults acceptable (3h session, 6h/day, 85 % XP)?
3. **Transparency marker (L12)** — examine text, overhead text, or a public chat line?
4. **Offline auto** — must the player stay logged in (planned default), or is offline auto wanted?
5. **Expansion** — should Fishing/Mining adopt `api/antiban` after Woodcutting ships?
