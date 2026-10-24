# Auto Smelting: mobile port

Runs from the Microbot Mobile screen ("F2P: Auto Smelting") through `HubPluginHost`, which gives it
its input session and ends it on Stop, logout, hop, backgrounding or another script starting.

Reviewed for mobile: every game read goes through the client-thread read APIs (the furnace lookup
runs on the client thread), actions go through the mobile action helpers, and the loop already
waits for each deposit, withdraw and furnace dialog to settle and retries on the next tick.

Changes for mobile:

- Stopping restores `Rs2Walker.disableTeleports`. The script turns teleports off for its own bank
  trips; the flag is global, so without this every later script walked without teleports.
- Stopping no longer assumes an overlay manager (start already allowed it to be missing).

Free-to-play notes: the bar list is upstream's and is not filtered by membership; bronze, iron,
silver, steel, gold, mithril, adamantite and runite bars can all be smelted on a free world. The coal bag is only used on members worlds; the ring
of forging and goldsmithing gauntlets are only equipped when they are already in the bank.

Not verified on a device. Device checks: pick a bar, start next to a furnace and a bank, let it run
two inventories, Stop mid-smelt, and start another script to confirm its teleports still work.
