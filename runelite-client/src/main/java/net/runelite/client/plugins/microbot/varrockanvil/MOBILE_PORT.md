# Varrock Anvil: mobile port

Runs from the Microbot Mobile screen ("F2P: Varrock Anvil") through `HubPluginHost`, which gives it
its input session and ends it on Stop, logout, hop, backgrounding or another script starting. The
Varrock West bank and anvil are free-to-play; bronze through runite bars can be smithed on a free
world (the bar list is upstream's and is not filtered by membership).

Changes for mobile:

- No logout. Like the woodcutting port, mobile scripts never log out: the "Logout On Complete"
  setting is hidden and not read, and running out of bars or a hammer just stops the script.
- Stopping on its own (out of bars, no hammer, or an error) turns the plugin off, not just the loop,
  and ends the bank trip there, so the Microbot screen shows it stopped. Before, an uncaught
  exception silently ended the schedule while the plugin still looked running.
- The anvil's "All" and item clicks, which fall within one game tick of each other, are each retried
  until accepted (mobile takes one action per game tick); the XP wait only starts once the item
  click took.
- `Microbot.enableAutoRunOn` is global; the value found at start is put back on stop.
- Debug messages go to the debug log instead of stdout every second.
- Stopping no longer assumes an overlay manager (start already allowed it to be missing).

Not verified on a device. Device checks: start at Varrock West bank with bars and a hammer in the
bank, smith two inventories, let it run out of bars and confirm it stops while logged in.
