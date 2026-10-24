# GE Firemaker: mobile port

Runs from the Microbot Mobile screen ("F2P: GE Firemaker") through `HubPluginHost`, which gives it its
input session and ends it on Stop, logout, hop, backgrounding or another script starting. The Grand
Exchange worksites are free-to-play; forester campfires are only used when one is already there.

Changes for mobile:

- The click on the fire, which comes right after selecting a log, is retried until accepted (mobile
  takes one action per game tick). The script only counts itself as burning once the click took,
  instead of waiting out the 20 s stall timer after a refused click.
- Space on the burn menu (older game versions) is retried until accepted.
- The burn-wait loop pauses between "Click here to continue" attempts instead of spinning.
- The fire lookup uses the mobile client-thread read (`MobileActions.read`).
- `Microbot.enableAutoRunOn` is global; the value found at start is put back on stop.
- Running out of logs or a tinderbox, or an error, turns the plugin off (not just the loop) and stops
  the bank trip there, so the Microbot screen shows it stopped.
- Debug messages go to the debug log instead of stdout every second.
- Stopping no longer assumes an overlay manager (start already allowed it to be missing).

Not verified on a device. Device checks: start at the Grand Exchange with logs and a tinderbox in
the bank, let it light a fire and burn two inventories, and Stop mid-burn.
