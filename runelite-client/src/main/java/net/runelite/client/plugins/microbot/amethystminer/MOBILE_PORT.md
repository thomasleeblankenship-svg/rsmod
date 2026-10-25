# Amethyst Miner: mobile port

Runs from the Microbot Mobile screen ("Members: Amethyst Miner") through `HubPluginHost`, which gives it its
input session and ends it on Stop, logout, hop, backgrounding or another script starting. Needs 92 Mining
and the Mining Guild.

Changes for mobile:

- No pickaxe in the inventory (with "pickaxe in inventory" on) shows upstream's message instead of
  throwing first; no chisel when chiselling sends it to the bank instead of throwing.
- Client reads run on the client thread: the interaction check, the Crafting level, and the player's
  tile for choosing the nearest vein (read once, instead of a wait without timeout per comparison).
- Chiselling presses the make option only once the menu is up after an accepted combine.
- An error turns the plugin off instead of being logged and retried every tick.
- Overlay add and remove are null-safe.

Not verified on a device. Device checks: in the Mining Guild with a pickaxe, mine and bank one inventory;
mine and chisel one inventory; Stop mid-mining.
