# Auto Runecraft (ChillX): mobile port

Runs from the Microbot Mobile screen ("F2P: Auto Runecrafting") through `HubPluginHost`, which gives
it its input session and ends it on Stop, logout, hop, backgrounding or another script starting.

Changes for mobile and free-to-play:

- Rune essence. Upstream only withdrew and crafted pure essence, which free-to-play accounts cannot
  use, so on a free world it never crafted. It now withdraws pure essence on a members world when the
  bank has it, otherwise rune essence, and crafts whichever essence is carried.
- The Nature altar is members-only; on a free world the script stops with a message instead of
  walking there. Air, Water, Earth, Fire and Body are free-to-play.
- Each step advances only once its click was accepted (mobile takes one action per game tick):
  entering the ruins (and only once the altar is loaded), crafting (the run is counted only once
  the essence is gone) and leaving through the portal. Before, a refused click moved the script on
  to the next step anyway, counting a run that never happened.
- Runecraft XP is read on the client thread (`MobileActions.read`).
- Missing essence or talisman/tiara, or an error, turns the plugin off (not just the loop), so the
  Microbot screen shows it stopped. Before, errors were logged and retried every second.
- `Microbot.enableAutoRunOn` is global; the value found at start is put back on stop.
- The per-loop timing line goes to the debug log instead of stdout.
- Stopping no longer assumes an overlay manager (start already allowed it to be missing).

Not verified on a device. Device checks: on a free world with rune essence and an air talisman in
the bank, run two trips to the Air altar; repeat with a tiara worn; Stop inside the altar.
