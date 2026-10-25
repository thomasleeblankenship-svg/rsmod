# Lunar Tanner: mobile port

Runs from the Microbot Mobile screen ("Members: Lunar Tanner") through `HubPluginHost`, which gives it its
input session and ends it on Stop, logout, hop, backgrounding or another script starting.
Members only: Tan Leather is a Lunar spell.

Changes for mobile:

- After a cast the script waits up to 3 s for the leather to appear. Upstream spun on the inventory
  count with no sleep and no limit, which never ended when the cast failed.
- Five failed casts in a row (missing runes, not on the Lunar spellbook) turn the plugin off.
- A failed bank open is retried on the next pass; upstream stayed in its banking state and never banked
  again.
- Running out of hides, and any error, turn the plugin off (upstream only stopped the loop, or printed
  the error and retried).
- The loop runs every 600 ms (one game tick) instead of every 50 ms.
- Profit prices fall back to 0 when the item search finds nothing, instead of failing at start; the
  hides-per-hour figure no longer divides by zero.
- Overlay remove is null-safe (add already was).

Unchanged: the hide list, banking, and the profit overlay.

Not verified on a device. Device checks: at a bank with runes on the Lunar spellbook, tan one inventory
of green dragonhide; run out of hides; Stop mid-cast.
