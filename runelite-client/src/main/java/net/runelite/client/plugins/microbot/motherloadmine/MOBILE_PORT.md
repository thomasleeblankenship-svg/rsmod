# Motherlode Mine: mobile port

Runs from the Microbot Mobile screen ("Members: Motherlode Mine") through `HubPluginHost`, which gives it
its input session and ends it on Stop, logout, hop, backgrounding or another script starting.

Changes for mobile:

- The plugin can load. Upstream's script injected `Rs2TileObjectCache` and `Rs2PlayerCache` through its
  constructor; the mobile read caches have no injectable constructor (they belong to the runtime), so
  Guice could not create the plugin and the Microbot screen would only say "Not available". The script
  now takes both caches from `Microbot` when it starts. `HubKindInjectionTest` guards every Hub script on
  the screen against this.
- Client reads that waited on the client thread with no timeout (`ClientThread.invoke`) use
  `MobileActions.read` (bounded): Smithing XP while hammering struts, the player's tile, the camera
  target and the floor-height check.
- Overlay add and remove are null-safe.

Unchanged: upstream's own error handling (an error resets the current step and the loop carries on), the
mining spots, sack handling and strut repair.

Not verified on a device. Device checks: start in the mine with a pickaxe, mine and deposit until the sack
empties once, repair a strut, and Stop mid-mining.
