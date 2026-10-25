# Sandstone Miner: mobile port

Runs from the Microbot Mobile screen ("Members: Sandstone Miner") through `HubPluginHost`, which gives it
its input session and ends it on Stop, logout, hop, backgrounding or another script starting.
Members only: the Desert Quarry is in members' Kharidian Desert.

Changes for mobile:

- No world hopping. The "Hop world Long" and "Hop world Nearby" options are ignored (a log line says so
  at start); the script keeps mining when another player is near.
- No logout at low health. Below 25% the script still walks to a bank, then turns the plugin off.
- Bounded loops: the grinder deposit wait gives up after 30 s and is retried on the next pass (upstream
  waited forever); dropping empty waterskins stops after one inventory's worth and on Stop.
- In turbo mode with no sandstone rock in reach the loop waits a tick instead of spinning.
- The grinder lookup runs on the client thread, like the rock lookups.
- An error turns the plugin off instead of being logged and retried every tick.
- `Microbot.enableAutoRunOn` is global; the value found at start is put back on stop.
- Overlay remove is null-safe (add already was). Stdout logging removed.

Unchanged: the mining spot, the grinder, turbo mode and Humidify.

Not verified on a device. Device checks: mine one inventory and deposit at the grinder, with and without
turbo; Humidify with a Lunar spellbook; Stop mid-mining.
