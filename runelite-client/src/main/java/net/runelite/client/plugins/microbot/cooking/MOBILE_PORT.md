# Auto Cooking: mobile port

Runs from the Microbot Mobile screen ("F2P: Auto Cooking") through `HubPluginHost`, which gives it
its input session and ends it on Stop, logout, hop, backgrounding or another script starting. Both
activities were adapted: Cooking (`AutoCookingScript`) and Burn baking (`BurnBakingScript`).

Free-to-play: with "Use nearest cooking location" on, a free world now only considers the locations
upstream lists under "Free-to-play" in `CookingLocation` (Falador, Draynor, Al Kharid, Edgeville,
Varrock east and west, Barbarian Village, Port Sarim, Rimmington, Lumbridge). Before, it could pick a
members range such as Catherby. Covered by `CookingLocationTest`.

Changes for mobile:

- Burn baking no longer crashes. Upstream called `Microbot.naturalMouse.moveOffScreen()` after each
  cook and combine; there is no natural mouse on mobile, so every call threw. They are removed.
- No logout. Burn baking used to log out at its target level; it now banks and stops while logged
  in.
- Space (cook all, make all) is retried until accepted, since mobile takes one action per game tick.
  Upstream's press, short sleep and release pairs are replaced by that retry. Cooking only waits for
  the cook menu once the use-on-range click was accepted. Burn baking's bread "Make" click is
  retried too.
- Scene reads run on the client thread: range, fire and banker lookups, NPC compositions, local to
  world conversions, run energy and Cooking level.
- Burn baking's walk waits end as soon as the script is stopped.
- Any stop (target reached, nothing to cook, requirements not met, an error) turns the plugin off,
  not just the loop, so the Microbot screen shows it stopped. Before, errors were logged and retried.
- `Microbot.enableAutoRunOn` is global; each activity puts back the value it found at start.
- Burn baking's stdout lines go to the debug log.
- Stopping no longer assumes an overlay manager (start already allowed it to be missing).

Not verified on a device. Device checks: cook shrimp at Lumbridge with "nearest" on, cook trout on
the Barbarian Village fire, run burn baking from level 1 for two inventories of bread, and Stop
while cooking.
