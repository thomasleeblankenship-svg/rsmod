# Auto Essence Mining: mobile port

Runs from the Microbot Mobile screen ("F2P: Essence Mining") through `HubPluginHost`, which gives it
its input session and ends it on Stop, logout, hop, backgrounding or another script starting.
Aubury's teleport, the rune essence mine and Varrock East bank are free-to-play (free-to-play
accounts mine rune essence).

Upstream was already close to mobile-ready: every loop step checks for stop, lookups run on the
client thread, and walking uses short minimap steps. Changes for mobile:

- The per-loop logging (about 60 lines on a 100 ms loop) is at debug level instead of info, so it
  no longer floods the device log. Start, shutdown and "no pickaxe" stay at info.
- An error turns the plugin off instead of being logged and retried every 100 ms, so the Microbot
  screen shows it stopped.
- No usable pickaxe in the bank, inventory or equipment turns the plugin off instead of reopening
  the bank every 100 ms.
- Shutdown also runs the base `Script.shutdown()` (start announcement and heartbeat reset). The
  script's own non-interrupting cancel still runs first.
- The overlay's Mining XP is read through `MobileActions.read`.
- Stopping no longer assumes an overlay manager (start already allowed it to be missing).

Not verified on a device. Device checks: start in Varrock with a pickaxe in the bank, run two
trips, and Stop inside the mine.
