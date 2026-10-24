# Bank Seller (KSP): mobile port

Runs from the Microbot Mobile screen ("F2P: Bank Seller") through `HubPluginHost`, which gives it its
input session and ends it on Stop, logout, hop, backgrounding or another script starting. The Grand
Exchange is free-to-play; new free-to-play accounts are trade-restricted, and the script already
detects the GE's restriction notice and banks those items again.

Bank Seller sells everything sellable in the bank. Its own protections are unchanged: starting
inventory and equipment, coins and platinum tokens, items with an offer at start, and offers it
did not place are never sold, aborted or collected.

Upstream already did its client reads inside one helper (`clientValue`) and checked for stop in
every loop. Changes for mobile:

- `clientValue` uses `MobileActions.read`, the client-thread read with a timeout, instead of
  `ClientThread.invoke`, which waits without one.
- Widget clicks in the offer flow (price, quantity, All, Confirm, Back, the startup close button)
  are retried for up to 2 s until accepted. Mobile takes one action per game tick, so a click in the
  same tick as the previous one was refused and failed the whole offer attempt. The startup close
  button is now read on the client thread and clicked from the script thread, so the retry never
  waits on the client thread.
- Enter (price and quantity) and Escape (backing out) are retried until accepted. The GE price
  warning's "1" key was already retried by its loop.
- An error stops the plugin with a chatbox message instead of being logged and retried every second.
- `Microbot.enableAutoRunOn` is global; the value found at start is put back on stop, as upstream
  already did for the stamina-potion setting.

Not verified on a device. Device checks: at the Grand Exchange on a test account with a few cheap
items in the bank, run to completion; Stop mid-offer and confirm the setup is backed out.
