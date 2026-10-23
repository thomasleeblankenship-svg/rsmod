# Player, widget and loot helper port

Implemented the uploaded woodcutting script's required player reads, animation/XP
waits, production widget selection, script-owned keyboard calls, special attack
toggle, named ground-item pickup, carried rune-pouch reads and continue dialogue.
Live engine reads and widget identity extraction go through `MobileActions.read`;
input goes through the host's revocable action session. Blocking waits refuse the
client thread and terminate on interruption, cancellation or a bounded timeout.
Pickup and special-attack results require observed state changes rather than
returning success just because an action was dispatched.

Widget processing searches stay within the production interface. A matching
non-actionable text child selects an actionable ancestor only in that interface.
Named-loot selection respects the ownership flag and quantity requirement. Rune
pouch reads require a real carried normal/divine pouch and read only its three or
four current slots, ignoring banked/noted pouches and stale extra varbits.

The uploaded player-detection option invokes logout and relies on a separate
upstream auto-login/random-world plugin for hopping. That plugin is not in this
ZIP and this helper does not invent automatic login or world hopping. The
`Rs2Antiban` compatibility surface supplies actual interruptible pacing and
cooldown state; it does not port the desktop fatigue, mouse-profile or detection
avoidance engine. No detection-avoidance or live-game behavior is claimed.

Added focused tests for game-markup text matching, loot ownership/quantity filters
and defensive copying of named-loot criteria. Root integration must run these
alongside the real Gradle client build. No phone or live game was available.
