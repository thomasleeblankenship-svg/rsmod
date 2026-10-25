# Jewellery Enchant: mobile port

Runs from the Microbot Mobile screen ("Members: Jewellery Enchant") through `HubPluginHost`, which gives
it its input session and ends it on Stop, logout, hop, backgrounding or another script starting.
Enchant spells are on the standard spellbook; members jewellery (and the members staffs) need a members
world.

Changes for mobile:

- The jewellery is clicked only once the spell is selected, on the inventory tab, by item id (upstream
  waited a fixed 200-300 ms and clicked by name). Five failed casts in a row turn the plugin off.
- Every stop (Magic level too low, out of cosmic or elemental runes, no jewellery left) turns the plugin
  off. Upstream only stopped the loop, and after running out of runes it still went on to withdraw
  jewellery.
- An error turns the plugin off instead of being printed and retried.
- Overlay remove is null-safe (add already was).

Unchanged: the jewellery list, staff equipping and rune restocking.

Not verified on a device. Device checks: at a bank with cosmic runes and a staff of water, enchant one
inventory of sapphire rings; run out of rings; Stop mid-cast.
