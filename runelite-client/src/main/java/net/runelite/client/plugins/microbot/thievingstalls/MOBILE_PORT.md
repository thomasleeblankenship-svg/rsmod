# Stall Thieving: mobile port

Runs from the Microbot Mobile screen ("Members: Stall Thieving") through `HubPluginHost`, which gives it
its input session and ends it on Stop, logout, hop, backgrounding or another script starting.

Changes for mobile:

- No world hopping. At the Fortis gem stall upstream hopped when another player was near, or when the
  stall was missing or had no "Steal-from" (emptied). Mobile scripts do not hop, so the script stays and
  waits for the stall to restock.
- Stall lookups run on the client thread (`MobileActions.read`).
- An error turns the plugin off instead of being logged and retried every 100 ms, so the Microbot screen
  shows it stopped.
- `Microbot.enableAutoRunOn` is global; the value found at start is put back on stop.
- Stopping no longer assumes an overlay manager (start already allowed it to be missing).

Unchanged: the stall spots, safespots, and the bank or drop strategies.

Not verified on a device. Device checks: Ardougne baker's stall with dropping, the silk stall with
banking, and Stop mid-steal.
