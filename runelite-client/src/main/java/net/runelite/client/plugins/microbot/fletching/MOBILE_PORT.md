# Fletcher: mobile port

Runs from the Microbot Mobile screen ("Members: Fletcher") through `HubPluginHost`, which gives it its
input session and ends it on Stop, logout, hop, backgrounding or another script starting. Fletching is a
members skill.

Changes for mobile:

- No crash after banking. The anti-ban template can turn on upstream's "natural mouse" move to the
  bank's close button, which dereferences `Microbot.naturalMouse`; that is null on mobile. The move is
  skipped when there is no natural mouse.
- The make-menu key is pressed only once the fletching menu is up, and only after the combine was
  accepted (mobile takes one action per game tick); otherwise the next loop tries again.
- The Fletching level (progressive modes) is read on the client thread.
- Every stop (missing knife, logs or strings, a wrong configuration, an error) turns the plugin off, not
  just the loop, so the Microbot screen shows it stopped. Before, errors were printed and retried.
- Stopping no longer assumes an overlay manager or a started script.

Not verified on a device. Device checks: at a bank with a knife and logs, cut one inventory of longbows
(u), string one inventory, run progressive mode at a low level, and Stop mid-inventory.
