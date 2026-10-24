# GE Cooker: mobile port

Runs from the Microbot Mobile screen ("F2P: GE Cooker") through `HubPluginHost`, which gives it its
input session and ends it on Stop, logout, hop, backgrounding or another script starting. The Grand
Exchange worksites are free-to-play; forester campfires are only used when one is already there.

Changes for mobile:

- Space (cook all) is pressed only once the "How many would you like to cook?" menu is up, and is
  retried until accepted, since mobile takes one action per game tick and can refuse a key.
- `Microbot.enableAutoRunOn` is global; the script turns it off while running and now puts back the
  value it found when it stops.
- Running out of raw food, a tinderbox or logs turns the plugin off (not just the loop) and stops
  the bank trip there, so the Microbot screen shows it stopped and no further withdrawals run.
- An error turns the plugin off. Before, an uncaught exception silently ended the schedule while
  the plugin still looked running.
- Debug messages go to the debug log instead of stdout every second.
- Stopping no longer assumes an overlay manager (start already allowed it to be missing).

Not verified on a device. Device checks: start at the Grand Exchange with raw food, logs and a
tinderbox in the bank, let it light a fire and cook two inventories, and Stop mid-cook.
