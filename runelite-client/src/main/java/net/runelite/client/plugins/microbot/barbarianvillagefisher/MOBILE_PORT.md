# Barbarian Village Fisher: mobile port

Runs from the Microbot Mobile screen ("F2P: Barbarian Village Fisher") through `HubPluginHost`, which
gives it its input session and ends it on Stop, logout, hop, backgrounding or another script
starting. Bait and fly fishing at Barbarian Village, the village fire and Edgeville bank are all
free-to-play.

Changes for mobile:

- Space (cook all) is pressed only once the "How many would you like to cook?" menu is up, and is
  retried until accepted, since mobile takes one action per game tick and can refuse a key.
- The fishing-spot and fire lookups run on the client thread (`MobileActions.read`).
- `Microbot.enableAutoRunOn` is global; the value found at start is put back on stop.
- A missing rod or bait in the bank, or an error, turns the plugin off (not just the loop), so the
  Microbot screen shows it stopped. Before, an uncaught exception silently ended the schedule.
- Debug messages go to the debug log instead of stdout every second.
- Stopping no longer assumes an overlay manager (start already allowed it to be missing).

Not verified on a device. Device checks: run each mode (drop, bank, cook and bank, cook and drop)
for one inventory with a rod and bait or feathers, and Stop while fishing.
