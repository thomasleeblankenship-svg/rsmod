# Microbot API coverage on mobile

Status values:

- **native:** upstream signature and semantics.
- **adapted:** same name, with a documented difference.
- **partial:** a subset of the upstream API.
- **unsupported:** absent, so callers fail to compile rather than silently doing nothing.

Evidence is Gradle tests against RuneLite interfaces with mocked boundaries. It is not
live-game evidence unless a row says so. Per-method detail is in
[the runtime report](docs/microbot/claude1-runtime-report.md) and
[the read-API report](docs/microbot/claude2-api-report.md).

| Area | Status | Surface | Evidence / limits |
| --- | --- | --- | --- |
| `Script`, `IScript`, `ScriptStarted` | adapted | Upstream fields, executor, `run()` guard, `shutdown()` | `ScriptLifecycleTest`: cancellation, cleanup, heartbeat, exceptions, stale ticks, mobile permission. No fatigue, tutorial-island, auto-run or stamina logic |
| `statemachine/**` | native | Unchanged upstream sources | `StateMachineScriptTest` |
| `Microbot` singleton | partial | Client, client-thread, event-bus and config getters; three cache getters; `isLoggedIn`; logging; varbit reads; `pauseAllScripts`; `status` | Services bound by `MicrobotRuntime`. Mouse, hopping, plugin control and popups absent |
| `MicrobotRuntime` (mobile-only) | adapted | Install/uninstall, client-thread snapshots, stale-tick stop, human-input window | `MicrobotRuntimeTest` |
| `Global` waits | adapted | `sleep*`, `sleepUntil*`, tick waits | `GlobalWaitTest`. Fatigue variants absent |
| `ClientThread.runOnClientThreadOptional` | native | Runs inline on the client thread; bounded wait elsewhere; a timed-out task never runs later | `ClientThreadOptionalTest` |
| Blocking events | partial | Manager and interfaces | No default events registered, since every upstream one performs input |
| NPC / tile-object / tile-item caches | adapted | `query()`, `within`, `where`, `nearest`, `toList`, names, lootability | `NpcQueryTest`, `SceneQueryTest`. Read-only per-tick snapshots |
| `Rs2Player` | partial | `isAnimating`, `isMoving`, `getAnimation`, `getPoseAnimation` | `PlayerInventoryReadTest` |
| `Rs2Inventory` | partial | `count` (occupied slots), `items`, `contains`, plus the woodcutting port's drop/use actions | `PlayerInventoryReadTest`, `MobileDropWorkflowTest`. **Phone: full-inventory drop works (user report)** |
| `Rs2Bank` (woodcutting port) | partial | Deposit/withdraw for the port's flows; automatic bank selection | `MobileBankWorkflowTest`. Not phone-tested |
| `Rs2Walker` | partial | Collision-aware same-plane walking; scene-resolved WALK dispatch; stall retry with replan and backoff; Lumbridge castle stairs | `MobileWalkerIntegrationTest`, `MobileActionsTest`. Phone: stairs failed on the `024f387` build; walking reworked in `ba30cb9`, retest needed. No ships, teleports, other stairs/ladders or instanced routing |
| Input | adapted | Script taps and keys through the `JavaBridge` settle gate, one action per tick | `JavaBridgeScriptInputTest`. A manual tap drops queued script input and pauses scripts for about 1.8 s; Stop, logout and backgrounding end the run |
| Widgets, menus, NPC/object interaction helpers, equipment, combat, GE, magic | unsupported | — | Not ported |
| Agent server, `microbot-cli`, JAR loading, desktop input injection | unsupported | — | Not ported, by design |

Machine-readable companion: [API matrix](docs/microbot/api-supported.json), with the same
rows, the test classes behind each one, and pass counts from the `ba30cb9` Gradle run.
