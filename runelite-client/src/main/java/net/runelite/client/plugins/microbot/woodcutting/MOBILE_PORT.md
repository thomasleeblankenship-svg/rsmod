# Uploaded Auto Woodcutting adaptation

Provenance: user-provided `microbot-autowoodcutting-src.zip`, desktop v1.8.7,
source commit `080d9674fe87f282c958c25b5bb8f6b96665fe36`. The accompanying
`UPLOADED_SOURCE_MANIFEST.json` records SHA-256 of every original file;
`SOURCE_COMMIT.txt` is the supplied source reference. Original upload remains
outside this checkout; executable files here contain mobile adaptations.

The desktop script, complete configuration, location catalogue, overlay, and
all nine Forestry event algorithms are included. Desktop plugin registration
is replaced by an explicitly owned mobile host. It registers/unregisters its
own event subscriptions, overlay and Forestry blockers and seeds entities
already loaded when the script starts. The mobile script adapter must bind an
input session before calling `startMobile()` and starting the script loop.

Adaptations affecting behavior:

- Auto-hop is disabled by explicit user request. There is no exposed Hop
  setting, no player-detection logout branch, and persisted desktop `Hop=true`
  values are never read.
- Every Forestry validation/loop and blocking wait observes cancellation of
  its owning host; a stopped script cannot keep issuing event actions.
- Game entities are looked up through the mobile read caches and interaction
  helpers; reads and actions use the installed client-thread service.
- Player origin and transient state reset on each start. Firemaking tile
  selection is bounded to eight tiles instead of unbounded recursion.
- An uncaught script error shuts the loop down rather than endlessly logging
  and retrying it. Membership-only locations require a members world.
- `ConfigInformation`, unsupported desktop-only PluginDescriptor metadata,
  and `setNaughty()` are display/registration differences. The option-bearing
  configuration and overlay contents are retained.

Inclusion and compilation do not establish live-game compatibility. Test
results and any remaining API limitations belong in the top-level integration
report. Device acceptance must separately exercise chopping, inventory reset,
banking/travel, each production workflow, each Forestry event, manual
interruption, and background/foreground transitions. No phone was available
for these checks in this environment.
