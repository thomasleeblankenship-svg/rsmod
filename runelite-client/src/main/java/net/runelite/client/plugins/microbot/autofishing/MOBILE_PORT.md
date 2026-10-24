# Auto Fishing: mobile port

Runs from the Microbot Mobile screen ("F2P: Auto Fishing") through `HubPluginHost`, which gives it
its input session and ends it on Stop, logout, hop, backgrounding or another script starting.

Free-to-play: shrimp and anchovies, sardine, herring, trout and salmon, pike, tuna and swordfish,
and lobster can be caught on a free world. The travel target is upstream's straight-line nearest
location for the fish and has no members flag, so on a free world start next to a free-to-play
spot (for example Lumbridge Swamp, Draynor, Barbarian Village or Musa Point). The script fishes any
matching spot in view without travelling.

Changes for mobile:

- No logout. Running out of bait or feathers, or not getting the gear from the bank, stops the
  script while logged in. The plugin is turned off (not just the loop), so the Microbot screen
  shows it stopped. Having no fire to cook on stops it the same way.
- The wait while a spot is being fished ends as soon as the script is stopped. Before, Stop could
  wait for the spot to move.
- Space on the cook menu is retried until accepted (mobile takes one action per game tick).
- The fishing spot's options are read on the client thread, and so is the plugin's start XP
  (upstream read it off-thread from `startUp`).
- Stopping no longer assumes an overlay manager (start already allowed it to be missing).

Unchanged: upstream's error recovery state (reset and re-evaluate), banking with the fish barrel,
and eel processing.

Not verified on a device. Device checks: net shrimp at Lumbridge Swamp with drop and with bank,
fly fish at Barbarian Village with cooking on, and Stop while fishing.
