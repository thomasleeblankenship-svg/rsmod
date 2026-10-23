# Mobile adapter API coverage

This is the `mobilemicrobot` API, not desktop Microbot's package or ABI.
The full RuneMobile client compiles. Nine tests against real client interfaces pass
(with service mocks); no game/device behavior is verified.

| Module | Status | Implemented surface | Evidence / limits |
| --- | --- | --- | --- |
| Inventory | partial | occupiedSlots, freeSlots, isFull, contains, count | Portable inventory tests; read-only, no bank/drop/eat |
| Player | adapted | location, isMoving, isAnimating, isIdle | Full client compile, actual-interface test and two portable pose checks. Non-idle pose is a conservative busy heuristic, not path progress |
| Objects | partial | findNearest, interact | Portable scene/name/input-budget tests; visible game objects only, default click action, no action acknowledgement |
| Walker | partial | distanceTo, isAt, walkToVisible | Portable range/budget checks; one minimap tap within 15 tiles, no collision, routes, doors or transports |
| Script lifecycle | partial | explicit start/stop, swap, disconnect/hop stop, disable | 25 portable checks and 9 actual-interface tests; queued-start invalidation tested. No Android lifecycle integration or pending native tap cancellation |
| Input | adapted | JavaBridge.onClick | Uses existing tap-settle pipeline. Return means requested input, not game action success; manual takeover unimplemented |
| Desktop Script / Microbot runtime | unsupported | none | Upstream transitive graph is recorded, not ported |
| NPCs, widgets, menus, bank, equipment, event caches | unsupported | none | No compatibility facade supplied |
| Native Android panel / JNI facade | unsupported | none | Existing plugin config UI intended; not device-tested |

Machine-readable companion: [API matrix](docs/microbot/api-supported.json).
