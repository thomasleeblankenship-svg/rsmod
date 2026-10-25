# Karambwan Fisher: mobile port

Runs from the Microbot Mobile screen ("Members: Karambwan Fisher") through `HubPluginHost`, which gives it
its input session and ends it on Stop, logout, hop, backgrounding or another script starting.

Changes for mobile:

- The fishing loop pauses 0.6 s between checks. Upstream spun with no sleep while the player fished,
  thousands of game-state reads a second for a whole inventory.
- The walk to the Zanaris bank waits at most 20 s. Upstream looped until arrival, forever if the walk
  failed.
- Leaving the fishing spot by the Karamja fairy ring moves on to banking only once the ring click was
  accepted (mobile takes one action per game tick); the ring lookup runs on the client thread.
- Running out of raw karambwanji, or an error, turns the plugin off (not just the loop), so the Microbot
  screen shows it stopped.
- `Microbot.enableAutoRunOn` is global; the value found at start is put back on stop.
- Stopping no longer assumes an overlay manager (start already allowed it to be missing).

Not verified on a device. Device checks: with karambwanji and a vessel, start at the DKP spot, fish one
inventory, bank in Zanaris and return by fairy ring; Stop mid-fishing.
