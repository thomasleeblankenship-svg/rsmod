# Cannonball Smelter: mobile port

Runs from the Microbot Mobile screen ("Members: Cannonball Smelter") through `HubPluginHost`, which gives it
its input session and ends it on Stop, logout, hop, backgrounding or another script starting.

Changes for mobile:

- No crash on every smelt. After starting each smelt upstream moved the "natural mouse" off screen;
  `Microbot.naturalMouse` is null on mobile, so that threw every time. The move is skipped.
- The furnace's make click is retried until accepted (mobile takes one action per game tick).
- Every stop (no steel bars, no mould, a failed withdraw, an error) turns the plugin off and ends that
  step. Upstream kept going after some of them (withdrawing after "could not find ammo mould").
- `Microbot.enableAutoRunOn` and `Microbot.runEnergyThreshold` are global; the script sets both while it
  runs and now puts back the values it found.
- The per-loop timing line (stdout every 100 ms) is gone.
- Stopping no longer assumes an overlay manager (start already allowed it to be missing).

Not verified on a device. Device checks: at Edgeville furnace with steel bars and an ammo mould in the
bank, smelt two inventories; let it run out of bars and confirm it stops.
