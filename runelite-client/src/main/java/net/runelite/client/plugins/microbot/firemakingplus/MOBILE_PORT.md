# Auto Firemaking Plus: mobile port

Runs from the Microbot Mobile screen ("F2P: Firemaking Plus") through `HubPluginHost`, which gives it
its input session and ends it on Stop, logout, hop, backgrounding or another script starting. Line
firemaking and lighting your own fire are free-to-play; forester campfires are only used when one is
already there. Pick a free-to-play log (normal, oak, willow, maple or yew) on a free world.

Changes for mobile:

- Teleports are restored on every stop. Upstream's own stops (target reached, no tinderbox, no logs)
  called `super.shutdown()`, skipping the override that turns `Rs2Walker.disableTeleports` back off,
  so every later script walked without teleports. They now run the full shutdown.
- Those stops, and an error, turn the plugin off (not just the loop), so the Microbot screen shows it
  stopped. Before, errors were logged and retried every tick.
- The campfire click after selecting a log, and Space on the burn menu, are retried until accepted
  (mobile takes one action per game tick).
- A refused tinderbox-on-log combine is retried next tick. Before, the campfire method took it as
  "can't light here" and walked away, and the line method waited 10 s for XP.
- Progressive mode skips members-only logs (teak, mahogany, magic, redwood) on a free world. Before,
  at Firemaking 35 it picked teak and stopped with "No Teak logs in the bank!". Covered by `LogsTest`.
- XP and level reads use `MobileActions.read`.
- `Microbot.enableAutoRunOn` is global; the value found at start is put back on stop.
- Stopping no longer assumes an overlay manager (start already allowed it to be missing).

Unchanged: league mode's idle-logout arrow key, the overlay pause button, and both methods' logic.

Not verified on a device. Device checks: run the line method for two inventories next to a bank,
run the campfire method at a bank, set a target level one above current and confirm it banks and
stops, then start another script and confirm its teleports work.
