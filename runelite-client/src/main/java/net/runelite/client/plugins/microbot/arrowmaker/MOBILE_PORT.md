# Arrow Maker: mobile port

Runs from the Microbot Mobile screen ("Members: Arrow Maker") through `HubPluginHost`, which gives it its
input session and ends it on Stop, logout, hop, backgrounding or another script starting.
Mostly members: bolts, darts, gem tips and dragon items need a members world; headless and bronze-iron
arrows can be made on F2P.

Changes for mobile:

- The "1" key is pressed only once the make menu is up after an accepted combine (upstream pressed it
  whether or not the menu appeared); a refused combine is retried on the next pass.
- The 12-14 s wait after a make ends early on Stop or when a material runs out.
- When none of the selected items has materials left, the plugin turns off (upstream idled forever).
- An error turns the plugin off instead of being logged and retried every tick.
- Overlay remove is null-safe (add already was).

Unchanged: the item lists and the per-item options.

Not verified on a device. Device checks: make headless arrows from shafts and feathers until one runs
out; bolts with tips; Stop mid-make.
