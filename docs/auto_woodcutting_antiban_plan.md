# Auto Woodcutting — Antiban System Plan

_Status: plan only — nothing in this document is implemented yet._

This plan describes a **server-side** auto-woodcutting feature for RS Mod and the "antiban"
layer that governs it. Because the server owns the feature, "antiban" here means two things:

1. **Core antiban (locked)** — always-on human behaviour built into the engine: reaction
   distributions, a pattern breaker, attention states, AFK drift, fatigue, camera movement,
   tab checking, misclicks, path variance and more. Players can see these but cannot turn
   them off.
2. **Server rules (locked)** — limits the server owner sets (session caps, presence checks,
   safety stops, XP-rate ceiling).
3. **Configurable antiban** — 52 player options that tune how strongly the core behaviours
   fire, plus extra behaviours on top. Every value is clamped to the locked floors.

---

## 1. What already exists (reuse, do not duplicate)

| Need | Existing system | Location |
|---|---|---|
| Chop logic, axe choice, success rolls, tree depletion | `Woodcutting` plugin script | `content/skills/woodcutting/.../scripts/Woodcutting.kt` |
| Starting a chop on a tree | `ProtectedAccess.opLoc1(loc)` — routes into the existing `onOpLoc1(content.tree)` handler | `api/player/.../protect/ProtectedAccess.kt:761` |
| Successful-cut hook | `Woodcutting.CutLogs` unbound event (already published) | `Woodcutting.kt` |
| Random events | `RandomEventTrigger.tryTriggerMysteriousOldMan()` — wired into Fishing, Mining, Smithing, **not yet Woodcutting** | `api/player/.../randomevent/RandomEventTrigger.kt` |
| Timed/deferred actions | `queue` / `weakQueue` / `strongQueue` | `ProtectedAccess.kt:1759-1775` |
| Facing / walking | `faceSquare`, `faceDirection`, `faceLoc`, `walk` | `ProtectedAccess.kt:890-902` |
| Camera control (A8) | `camForceAngle`, `camLookAt`, `camMoveTo`, `camReset` | `ProtectedAccess.kt:2991-3035`, `api/player-output/.../Camera.kt` |
| Side-tab switching (A9) | `toplevelSidebuttonSwitch(side)` | `ProtectedAccess.kt:3022` |
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
   ├─ model/      ReactionModel, FatigueModel, AttentionModel, BreakScheduler, Distributions
   ├─ guard/      PatternGuard         entropy/autocorrelation pattern breaker (A2)
   ├─ core/       CoreBehaviour registry  always-on A-features, each with a floor
   ├─ idle/       IdleAction registry  configurable extra humanizing actions
   └─ session/    AutoSession          state, quotas, audit log
content/skills/woodcutting-auto      (NEW — woodcutting-specific driver + menu + command)
   ├─ AutoWoodcuttingScript            state machine, tree selection, inventory handling
   ├─ AntibanMenuScript                settings menu + "Core antiban" + "Server rules" pages
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

There are two locked groups:

- **3.1 Core antiban (A1–A18):** human-like behaviours built into the engine. They are
  **always on** for every auto player. Players see each one on the **"Core antiban"** page
  with its live status (for example "Attention: Distracted", "Last camera move: 41 s ago").
  Players can make some of them *stronger* through §4, but can never turn them off or set
  them below the floor.
- **3.2 Server rules (R1–R14):** limits the owner sets (session caps, safety stops).

### 3.1 Core antiban — always on

Every behaviour below is client-visible or timing-based and uses APIs the server already has:
`camForceAngle` / `camLookAt` / `camReset`, `toplevelSidebuttonSwitch`, `faceSquare` /
`faceDirection`, `anim`, `walk`, `opLoc1`, and the emotes module.

#### Timing & attention

| # | Feature | What it does | Locked floor |
|---|---|---|---|
| A1 | **Human reaction engine** | Every delay (next click, next drop, walk start) is sampled from a skewed log-normal distribution with a long tail. The engine never uses a fixed delay. | Tail always present; mean ≥ 2 ticks |
| A2 | **Pattern breaker** | Remembers the last 64 delays and actions. If their variance or entropy falls below a threshold, or a repeating cycle appears (autocorrelation check), the engine adds a disruption: a pause, an extra action, or a different order. | Always on |
| A3 | **Attention-state model** | A Markov chain with 4 states: **Focused → Normal → Distracted → AFK**, re-rolled every 30–90 s. Each state changes reaction speed, misclick rate and idle-action rate. | All 4 states reachable |
| A4 | **AFK drift** | When Distracted or AFK, the player sometimes misses that the tree fell and stands still in the idle pose for 5–60 s, like someone who looked away from the screen. | ≥ 1 per 30 min |
| A5 | **Fatigue** | Tiredness builds over the session (see §5). As it grows, reactions get slower and misclicks, hesitations and AFK drift become more common. Breaks reduce it. | Cannot be disabled |
| A6 | **Warm-up & cool-down** | Slower and sloppier for the first 2–5 min of a session and in the minutes before a long break. | Always on |
| A7 | **Daily personality drift** | Each player gets a unique behaviour fingerprint from a seed, which drifts a little each day. No two auto players share timing, and one player's timing slowly changes over time. | Always on |

#### Camera & interface

| # | Feature | What it does | Locked floor |
|---|---|---|---|
| A8 | **Camera movement** | Random rotations (`camForceAngle`), sometimes looking at the target tree or a passing player (`camLookAt`), plus small pitch changes. Rate and speed vary, and `camReset` returns control to the player. | ≥ 1 move per 3–8 min |
| A9 | **Tab checking** | Switches to the **Skills** tab to "check" Woodcutting XP and back to Inventory (`toplevelSidebuttonSwitch`). Occasionally opens Equipment or another random tab instead. | ≥ 1 per 10–25 min |
| A10 | **Level-up reaction** | After a level-up, pauses 3–15 s, may open the Skills tab, and has a small chance of a cheer emote. | Always on |

#### Movement & clicking

| # | Feature | What it does | Locked floor |
|---|---|---|---|
| A11 | **Misclick & correction** | Sometimes clicks the wrong neighbouring tile or tree first, then corrects 1–3 ticks later. | ≥ 0.5 % of clicks |
| A12 | **Spam-click & early-click** | Re-clicks the tree it is already chopping (an impatient human), or clicks a stump just before it respawns and then re-clicks. | Always on |
| A13 | **Imperfect tree choice** | Sometimes picks the 2nd- or 3rd-best tree instead of the best one, using a softmax over the scores. | ≥ 3 % sub-optimal |
| A14 | **Path humanization** | Walk targets are offset by a few tiles, with occasional overshoot and a corrective step. Two trips to the same tree never use the same path. | Always on |
| A15 | **Drop rhythm** | Each drop gets its own timing jitter, slots are sometimes skipped and gone back to, and the dropping order changes. Never perfectly even. | Always on |

#### Reacting to the world

| # | Feature | What it does | Locked floor |
|---|---|---|---|
| A16 | **Nearby-player awareness** | When another player comes close, may briefly face them, pause, or move the camera toward them. | Always on |
| A17 | **Chat & event interrupts** | Pauses when the player gets a private message, when chat mentions their name, or when a random event NPC appears. | Always on |
| A18 | **Mandatory breaks** | Micro-breaks and long breaks always happen (see R3 for the floors). During a break the engine runs idle behaviours (camera, tabs, small wanders) instead of freezing. | Always on |

Every A-feature reads its intensity from `AntibanSettings` (§4), clamped to the floor above.
The engine uses `max(playerValue, floor)`, so players can only make behaviour **more** human,
never less.

### 3.2 Server rules

Values come from `AntibanPolicy`, sourced from `RealmConfig` so the owner can change them
without a restart. Shown on the **"Server rules"** page.

| # | Rule | Default | Behaviour |
|---|---|---|---|
| R1 | **Max continuous session** | 3h | Hard stop + forced long break when reached. |
| R2 | **Daily auto quota** | 6h / account / day | Tracked in a persistent varp with a day stamp; resets at server midnight. |
| R3 | **Mandatory break floor** | ≥ 1 micro-break / 20 min, ≥ 1 long break / 90 min | Players can make breaks *more* frequent/longer, never less. |
| R4 | **Reaction-time floor** | ≥ 2 ticks after depletion | No frame-perfect re-clicks; applied after all player-chosen delays. |
| R5 | **XP-rate ceiling** | ≤ 85 % of expected manual rate | Measured over a rolling 15-min window; if exceeded, the engine injects extra delay. |
| R6 | **Presence checks** | every 25–45 min (random) | Dialogue "Are you still there?" with a random answer order; 60 s to respond or auto stops. |
| R7 | **Random events always on** | — | Woodcutting rolls `RandomEventTrigger`; auto pauses until the event is resolved; ignoring it stops the session. |
| R8 | **Area whitelist** | owner-defined woodcutting zones | Auto only starts inside one; leaving its radius stops the session. |
| R9 | **Danger abort** | always | Stop on: taking damage, being targeted by an NPC/player, entering the Wilderness, HP below 50 %. |
| R10 | **No teleports** | always | Banking only via walking to owner-approved banks. |
| R11 | **Core antiban cannot be disabled** | always | Enforces the A1–A18 floors; saved settings below a floor are raised to it when read. |
| R12 | **Transparency** | always | Other players see an "is auto-chopping" marker (examine text / periodic public chat line — final form decided in Phase 3). |
| R13 | **Audit log** | always | Start/stop/reason/duration/XP logged server-side for admins. |
| R14 | **Idle-logout interplay** | always | Auto activity does **not** reset the idle-logout timer; only passed presence checks do. |

### 3.3 Bounds enforced on every configurable value

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
| C3 | Mean / spread | mean 2–12 ticks, spread 0–6 ticks (clamped by R4) |
| C4 | Hesitation chance | 0–15 % chance to pause 1–4 ticks between swings |
| C5 | Misclick rate (A11) | 0.5–5 % chance to click a wrong nearby tile/loc first, then correct (≥ A11 floor) |

### 4.2 Breaks
| # | Option | Choices / range |
|---|---|---|
| C6 | Micro-break frequency | every 5–20 min (≥ R3 floor) |
| C7 | Micro-break length | 5–90 s, random within range |
| C8 | Long-break frequency | every 30–90 min (≥ R3 floor) |
| C9 | Long-break length | 2–20 min |
| C10 | Break scheduling mode | Fixed interval · Uniform random · Poisson (memoryless) · "Human" (Poisson + more breaks as fatigue rises) |
| C11 | During-break behaviour | Stand still · Wander 1–5 tiles · Face random direction · Idle-action burst |

### 4.3 Fatigue
| # | Option | Choices / range |
|---|---|---|
| C12 | Fatigue curve | Linear · Exponential · Sigmoid (slow start, sudden tiredness) |
| C13 | Fatigue sensitivity | 0.5×–2× (cannot be 0 — R11) |
| C14 | Recovery rate during breaks | 0.5×–2× |
| C15 | Fatigue effects (multi-select) | slower reactions · more hesitation · more misclicks · more idle actions · worse tree choice |

### 4.4 Tree selection & movement
| # | Option | Choices / range |
|---|---|---|
| C16 | Tree strategy | Nearest · Distance-weighted random · Sticky favourite · Least contested (fewest players) · Round-robin |
| C17 | Sub-optimal choice chance (A13) | 3–25 % chance to pick the 2nd/3rd best tree (≥ A13 floor) |
| C18 | Path variance (A14) | 1–4 tile random offset on walk destinations, overshoot chance 0–20 % |
| C19 | Tree-type filter | multi-select from trees the player can cut, within the whitelisted area |
| C20 | Anticipation | Off · Pre-walk toward next tree when current one is "about to" fall (chance-based, never informed by hidden state) |

### 4.5 Inventory
| # | Option | Choices / range |
|---|---|---|
| C21 | Full-inventory action | Drop · Bank (walk) · Stop |
| C22 | Drop pattern | Row-by-row · Column · Zig-zag · Random order · Mixed (random per trip) |
| C23 | Drop-speed variance (A15) | jitter 0–2 ticks between drops, skip-and-return chance 0–10 % |
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
| C30 | XP summary — chat message with current XP / XP to next level |
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
| C38 | Personal safety (stricter only) | stop if > N players nearby · stop at HP < X % (X ≥ R9 value) |
| C39 | Notifications verbosity | Silent · Important (stops, level-ups) · Verbose (breaks, checks, tree switches) |
| C40 | Session summary on stop | On/Off — XP, logs, duration, breaks taken, stop reason |

### 4.9 Core antiban intensity (tunes A1–A18, never below the floors)
| # | Option | Choices / range |
|---|---|---|
| C41 | Camera activity (A8) | Low · Medium · High · Custom (moves per hour 8–40, rotation speed range, pitch-change chance) |
| C42 | Camera targets (A8) | multi-select: random angle · current tree · next tree · nearby players · back to default |
| C43 | Tab-check frequency (A9) | every 3–25 min (≥ A9 floor) |
| C44 | Tab pool (A9) | multi-select: Skills · Inventory · Equipment · Prayer · Magic · Friends · Quest list |
| C45 | Attention profile (A3) | Steady · Normal · Easily distracted · Custom (edit Markov transition weights) |
| C46 | AFK drift (A4) | frequency 1–6 per hour, length 5–120 s |
| C47 | Pattern-breaker sensitivity (A2) | Normal · High · Paranoid (stricter thresholds, more disruptions) |
| C48 | Spam-click tendency (A12) | 0–30 % of swings get an extra re-click |
| C49 | Warm-up / cool-down length (A6) | 2–10 min |
| C50 | Nearby-player reaction (A16) | chance 10–80 %; reactions: face · pause · camera look · emote (wave) |
| C51 | Interrupt pause length (A17) | 2–20 s after a PM, name mention or random event |
| C52 | Level-up reaction (A10) | pause 3–30 s; cheer-emote chance 0–50 %; open Skills tab chance 0–100 % |

---

## 5. Behaviour model (the "complex" core)

All randomness goes through `GameRandom`, seeded per session from the player's behaviour seed
(C34) so tests are deterministic.

- **Reaction delay** (ticks):
  `delay = max(R4_floor, round(sample(dist, mean, spread) × (1 + α·F)))`
  where `F ∈ [0,1]` is fatigue and `α` comes from fatigue sensitivity (C13) if
  "slower reactions" (C15) is enabled.
- **Fatigue**: updated each minute.
  - Working: `F += r·g(F)` where `g` is the chosen curve (linear `1`, exponential `(1-F)`, sigmoid
    `F(1-F)` with a small floor so it starts).
  - Break: `F *= exp(-k·t)`, `k` from recovery rate (C14).
- **Break scheduling**: Poisson process with rate `λ = 1/meanInterval`; "Human" mode uses
  `λ(F) = λ₀(1 + βF)`. Locked floors (R3) and the session cap (R1) override any sample.
- **XP ceiling (R5)**: a rolling window tracks XP/hour; when over the ceiling, an extra delay is
  added to the next reaction, proportional to the excess.
- **Tree choice**: candidates within radius are scored by distance, player count, and type
  filter; strategy (C16) picks a scoring function; C17 adds a softmax-style chance of choosing a
  lower-ranked tree; fatigue (C15) widens that chance.
- **Idle actions**: weighted random selection (C26–C31), rate from C32 scaled by fatigue.
- **Attention (A3)**: a 4-state Markov chain `{Focused, Normal, Distracted, AFK}` with a
  transition matrix from C45. Fatigue shifts probability toward Distracted/AFK:
  `P'(i→j) ∝ P(i→j) · (1 + γF)` for the worse states, then rows are normalized. Each state has
  its own reaction-mean multiplier, misclick multiplier and idle-action multiplier.
- **Pattern breaker (A2)**: a ring buffer of the last 64 `(action, delay)` pairs. Each minute it
  computes the variance of the delays, the Shannon entropy of the action sequence, and the
  autocorrelation for lags 2–16. If variance < `v_min`, entropy < `h_min`, or any
  autocorrelation > `ρ_max` (thresholds from C47, never looser than the locked defaults), the
  next decision is disrupted with a random choice of pause, idle action, camera move or tab
  check.
- **Camera & tabs (A8, A9)**: each runs on its own Poisson timer whose rate is
  `max(floor, playerRate) × attentionMultiplier`. Camera moves sample a target from C42 and a
  rotation speed from a range, so no two moves are the same.
- **Composite delay**: the final delay before any action is
  `max(R4_floor, base(A1) × attention(A3) × fatigue(A5) × warmup(A6)) + xpCeilingPenalty(R5)`.

---

## 6. Storage

- **Player settings**: new non-temporary varps, bit-packed via varbits (≈ 52 options fit in
  ~10 varps). 3 profile slots ⇒ ~30 varps plus an "active profile" varbit.
- **Quota/session tracking**: day stamp + minutes-used varps (persistent); current-session state
  in temporary varps.
- **Policy**: `AntibanPolicy` fields added to `RealmConfig` (DB-backed), with defaults from §3.

The exact varp count is finalized in Phase 2 once options are packed.

---

## 7. Player entry points

- `::autowc` command — opens the start dialogue (start / settings / core antiban / server rules / last summary).
- `::autowc stop` — stops immediately.
- Settings menu built from `menu(...)` pages and `countDialog` for numeric ranges; no new cache
  interfaces are required.

---

## 8. Implementation phases (one commit per feature)

| Phase | Deliverable | Tests |
|---|---|---|
| 0 | Wire Woodcutting into `RandomEventTrigger` (prerequisite for R7) | Integration: event can spawn while chopping |
| 1 | `api/antiban` core: `AntibanPolicy`, distributions, `FatigueModel`, `BreakScheduler`, clamping | Unit: deterministic distribution samples, clamping, fatigue curves |
| 2 | Persistent settings varps/varbits + `AntibanSettings` + profile slots | Unit/integration: save → logout → load round-trip |
| 3 | `woodcutting-auto` state machine (nearest tree, drop), `::autowc`, transparency marker | Integration: auto chops via existing `Woodcutting` script and gains XP |
| 4 | Core timing: reaction engine, attention model, AFK drift, warm-up, pattern breaker (A1–A4, A6, A7) | Unit: Markov transitions, entropy/autocorrelation detection on a synthetic repeating sequence |
| 4b | Core camera & tabs, level-up reaction (A8–A10, C41–C44, C52) | Integration: camera/tab packets sent at least at floor rate |
| 4c | Core clicking & movement: misclick, spam-click, imperfect choice, path & drop humanization (A11–A15) | Integration: misclick then correction still chops the right tree |
| 4d | World reactions: nearby players, chat/event interrupts (A16, A17) | Integration: PM pauses auto |
| 5a | Breaks + fatigue integration (A5, A18, C6–C15, R1, R3, R11) | Integration: break floors respected, session cap stops auto |
| 5b | Locked safety: presence checks, random-event pause, danger abort, area whitelist, quota, audit log (R2, R6–R10, R13, R14) | Integration: each abort reason |
| 6 | Tree strategies, sub-optimal choice, path variance, anticipation (C16–C20) | Unit: strategy scoring; integration: tree switching |
| 7 | Inventory strategies + banking (C21–C25) | Integration: each drop pattern, bank trip |
| 8 | Idle actions, personalities, behaviour seed, time-of-day (C26–C36) | Unit: weighted selection; integration: emote/examine fire |
| 9 | Goals, notifications, session summary, XP ceiling (C37–C40, R5) | Integration: each stop condition; XP ceiling throttles |
| 10 | Menu polish + "Core antiban" (live status) and "Server rules" pages, intensity options C45–C51; docs update (`master_roadmap.md`, this file) | Konsist + doc tests |

Each phase: `gradlew compileKotlin`, `gradlew spotlessKotlinApply`, `gradlew test`,
`gradlew integration` before committing.

---

## 9. Open decisions for the owner

1. **Who can use auto?** All players, members only, or a rank/donor gate?
2. **Default locked values** — are the §3 defaults acceptable (3h session, 6h/day, 85 % XP)?
3. **Transparency marker (R12)** — examine text, overhead text, or a public chat line?
4. **Offline auto** — must the player stay logged in (planned default), or is offline auto wanted?
5. **Expansion** — should Fishing/Mining adopt `api/antiban` after Woodcutting ships?
