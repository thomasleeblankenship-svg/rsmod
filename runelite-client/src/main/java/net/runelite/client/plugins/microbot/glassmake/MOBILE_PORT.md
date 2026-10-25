# Glass Make (Superglass Make): mobile port

Runs from the Microbot Mobile screen ("Members: Glass Make") through `HubPluginHost`, which gives it its
input session and ends it on Stop, logout, hop, backgrounding or another script starting. Needs the Lunar
spellbook and Superglass Make's runes.

Changes for mobile:

- No endless loops. Upstream opened the bank in `while (!isOpen)`, waited for it to close the same way,
  and looted molten glass until none was left, none of which stopped with the script or gave up. Opening
  now tries up to 10 times, closing waits at most 3 s, and the pickup tries at most 30 times; all stop
  with the script.
- Out of materials turns the plugin off. Upstream idled until stopped by hand, still showing as running.
- The spell is cast only after a bank trip that worked; otherwise the next loop banks again.
- An error turns the plugin off, so the Microbot screen shows it stopped.
- `Microbot.enableAutoRunOn` is global; the value found at start is put back on stop.
- Stopping no longer assumes an overlay manager (start already allowed it to be missing).

Not verified on a device. Device checks: at a bank on the Lunar spellbook with seaweed, sand and runes,
make two inventories of glass, let it run out of seaweed and confirm it stops.
