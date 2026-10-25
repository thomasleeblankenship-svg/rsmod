# Pie Baker: mobile port

Runs from the Microbot Mobile screen ("Members: Pie Baker") through `HubPluginHost`, which gives it its
input session and ends it on Stop, logout, hop, backgrounding or another script starting.
Members only: Bake Pie is a Lunar spell (Lunar Diplomacy).

Changes for mobile:

- The steps moved from the plugin's game-tick handler (the client thread) to `PieBakerScript`, one step
  every 600 ms. Mobile actions wait for the game, which a client-thread caller would block.
- The script opens the nearest bank itself ("start at any bank", as the instructions say) and closes it
  before casting, since the spellbook is out of reach while the bank is open.
- Runes and the rune pouch stay in the inventory; everything else is deposited before withdrawing.
  Upstream deposited everything and only withdrew into an empty inventory.
- Tick-perfect mode casts at most one inventory's worth per pass and checks Stop between casts
  (upstream looped until the pies were gone, on the client thread).
- The plugin turns off when the bank has no more raw pies, the pie name is unknown, or five bank opens
  or casts fail in a row (missing runes or not on the Lunar spellbook), and after an error.
- XP reads run on the client thread; the XP-per-hour overlay no longer divides by zero in its first
  second. Overlay add and remove are null-safe.

Unchanged: the pie list, the selected-pie and tick-perfect options.

Not verified on a device. Device checks: at the Grand Exchange bank with runes (or a rune pouch) on the
Lunar spellbook, bake one inventory of berry pies; run out of raw pies; Stop mid-cast.
