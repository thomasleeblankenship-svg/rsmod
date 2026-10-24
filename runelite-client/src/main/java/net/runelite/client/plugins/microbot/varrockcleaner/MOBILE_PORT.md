# Varrock Cleaner: mobile port

Runs from the Microbot Mobile screen ("F2P: Varrock Museum Cleaner") through `HubPluginHost`, which
gives it its input session and ends it on Stop, logout, hop, backgrounding or another script starting.
The museum dig site is free-to-play.

Changes for mobile:

- The storage-crate step no longer presses "2" blindly right after clicking the crate. It waits for
  the "Select an option" menu and presses option 2 (upstream's key) once the menu is up, retrying
  until the key is accepted, since mobile takes one action per game tick. With no menu it carries on.
- After "Take" on the specimen rocks it waits for the take to run (inventory full, or the player idle
  for 1.8 s) instead of clicking the rocks again every 100 ms loop.
- An error stops the script instead of being logged and retried every 100 ms.

Unchanged: the state order (take, clean, crate, drop), the rare-find list and the kept tools.

Not verified on a device. Device checks: start in the museum's cleaning area with a trowel, rock pick
and specimen brush, let it run two inventories, and Stop during cleaning.
