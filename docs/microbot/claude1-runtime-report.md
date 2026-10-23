# Claude 1 runtime report (M2)

Scope: Microbot `Script`/`IScript` lifecycle, scheduler, cancellation, heartbeat, blocking-event
gate, the `Microbot` service subset, the state-machine framework, and a `MobileScript` adapter.
Branch: `claude/laughing-volta-i6l45s`, based on `feat/android-microbot-compat` at `ee9bdc7`.

## Status

**Update after integrating Claude 2 (branch head after `2558ad2`).** `repo.runelite.net` is
now reachable, and the real Gradle build ran on the integrated tree:

| Command | Result |
| --- | --- |
| `./gradlew --no-daemon --max-workers=2 :client:compileJava` (merged `2558ad2`, before the integration commit) | exit 0 |
| `./gradlew --no-daemon --max-workers=2 :client:compileJava :client:test --tests 'net.runelite.client.plugins.microbot.*' --tests net.runelite.client.callback.ClientThreadOptionalTest --tests 'net.runelite.client.plugins.mobilemicrobot.*' --tests net.runelite.client.headless.JavaBridgeScriptInputTest` | exit 0. **114 tests, 0 failures/errors/skips** (JUnit XML): ClientThreadOptional 5, GlobalWait 6, MicrobotRuntime 12, MicrobotScriptAdapter 11, ScriptLifecycle 13, StateMachineScript 8, StateMachineExampleIntegration 2, plus Claude 2's NpcQuery 9, SceneQuery 6 and PlayerInventoryRead 11, and Codex's MobileMicrobotPlugin 17 and JavaBridgeScriptInput 14 |
| Same microbot + ClientThread tests with `--rerun`, 3 more times | exit 0 each time |
| `:client:checkstyleMain` | **not run**: `com.puppycrawl.tools:checkstyle:8.3` failed to resolve |

The unchanged upstream `StateMachineExampleScript` (SHA-256 `aed85889…245dbd`, the same as
upstream) now sits in `src/main/java` and compiles against the real client.
`StateMachineExampleIntegrationTest` runs it through `MicrobotScriptAdapter`,
`MicrobotRuntime` and Claude 2's real caches, on an empty logged-in scene made of Mockito
RuneLite doubles with a dedicated fake client thread. It completes the full
SCAN_NPCS → … → REPORT → SCAN_NPCS cycle with no ERROR state. After logout it stops stepping,
and stop leaves nothing running. **This is still not live-game, device or APK evidence.**

The earlier pre-check (plain `javac` with out-of-repo shadows) is superseded by the Gradle runs
above.

## Provenance

Upstream: `chsami/Microbot` at `e01e0d77d79b354472a5737b3aaab2449eb034fc`, BSD 2-Clause
(the RuneLite license; see the repository `LICENSE`). The paths below are relative to
`runelite-client/src/main/java/net/runelite/client/plugins/microbot/`.

### Copied byte-for-byte (verified with `cmp` against the upstream checkout)

| File | SHA-256 |
| --- | --- |
| `IScript.java` | `42e14b2a0944a5f845107cc5279d5c3de022bfdfee655e30bd457c8bef408ffd` |
| `ScriptStarted.java` | `2fc7532c52e917be53f23b6931415e9de6729e95a069c0d0132a1d8177cee43b` |
| `BlockingEvent.java` | `89326eb62145c4a158046963a9345e9bbac73d96094bb98364244d70e3765408` |
| `BlockingEventPriority.java` | `19cf68a55f803ba6310f62dfcc4bd80160d2547f31786e60357f53d97a99cd93` |
| `statemachine/PendingTransition.java` | `a132c707bbd43e4ae96181876de24f18b954424c73aa4c980e544e9b57f384d5` |
| `statemachine/StateMachineScript.java` | `183feb34d970d9eb9f5a618bf25cc168cf82d8286f00fa43b813affee9bc9f1d` |
| `statemachine/StateSnapshot.java` | `896193902b60bfc38b6fd3c93f017d00a007e17dc41bb960c9189ace2a18e76b` |
| `statemachine/Transition.java` | `9186cb0b4472863e5335fd3541fdfc63035dd87e9d2b7a6c06cdf5153819d8cd` |
| `agentserver/handler/ScriptHeartbeatRegistry.java` | `1d1791a5e5b74d3d489fde29a45f1530db642e8be5226dabf42b2e8c223169e8` |
| `util/GameTickBroadcaster.java` | `0beb091eb566a58848460fc1d11921534dcbe19b0a1d3e28dedc3782b6d2a1d2` |

`ScriptHeartbeatRegistry` keeps its upstream package name for source compatibility. It is a
plain in-memory map. **No agent server, HTTP endpoint or remote control was ported.**

### Adapted (upstream hashes; every change is marked `Mobile:` or listed in the class Javadoc)

| File | Upstream SHA-256 | Adaptation |
| --- | --- | --- |
| `Script.java` | `a5d0d26f3c1a36f01ed099c725b1e0379473b58777d9625026fba31a156cb3ab` | Same fields, signatures and executor. The pool drops cancelled tasks and lets idle threads expire. The guard refuses to run when the runtime is not installed, and stops the script when game ticks stall while logged in. Human takeover goes through `MicrobotRuntime`. |
| `util/Global.java` | `b20c9dc72863204a208c61162e7afbf16fdc20a45e324898321da2567318fed0` | Same wait bodies. Waits refuse when the runtime is not installed, and the pool uses daemon threads. Methods that need `Rs2Random`/`SessionFatigue` are not ported. |
| `BlockingEventManager.java` | `aab6a031f76baf47f08e92aba794e7a33f4ac247f58afd19e7c03fc2afa3b6ba` | No default events are pre-registered. |
| `Microbot.java` | `bd3069cf6aaa23a906daf632ce3262a6545a76a30a95f6e1bce2631ef45c55b8` | Subset only (see below). `isLoggedIn()` reads a client-thread snapshot. |
| `callback/ClientThread.java` (RuneLite) | Microbot fork: `511fc5753b0385de00d3a5db6e7c54edf6b76fa0a8d00626c1d18933dfc17919` | Adds `isClientThread()` and `runOnClientThreadOptional(Callable[, long])`. A timed-out or interrupted task is cancelled, so it never runs later. `runOnSeperateThread`, `scheduledFuture` and `invoke(Supplier)` are not added (`invoke(Supplier)` would make existing lambdas ambiguous). |

### New mobile-only classes

- `MicrobotRuntime`: explicit `install(...)`/`uninstall()` in place of Guice static injection.
  It publishes three snapshots: logged in (`GameStateChanged`), last game tick (`GameTick`) and
  human input (`noteHumanInput()`). It starts the per-install `BlockingEventManager` and
  `GameTickBroadcaster`, and tears both down again.
- `MicrobotRuntimeService`: the `startUp`/`shutDown` contract for services bound to one install
  (Claude 2's caches).
- `MicrobotScriptAdapter<T extends Script>`: implements `mobilemicrobot.MobileScript`. It creates a
  fresh instance on every `onStart`, and fails the start on refusal, an exception or a missing main
  loop. `onGameTick` throws if the loop has ended, passing on its cause. `onStop` shuts down the
  script and then its executor (`shutdownNow`, which interrupts a blocked wait), and never blocks
  the caller.

## Acceptance mapping

| Requirement | How it is met | Test evidence (mocked boundary) |
| --- | --- | --- |
| Background work never blocks the client thread | All waits return at once on the client thread. `runOnClientThreadOptional` runs inline there. Adapter callbacks do no waiting. | `GlobalWaitTest.waitsReturnImmediatelyOnClientThread`, `ClientThreadOptionalTest.runsInlineOnClientThread`, `MicrobotScriptAdapterTest.stopInterruptsBlockedWaitWithoutBlockingCaller` |
| Client reads on the client thread or via snapshots | `isLoggedIn` and tick age are volatile snapshots written by client-thread subscribers. The install-time seed runs on the client thread, or is queued there. | `MicrobotRuntimeTest.loggedInSeed*`, `gameStateEventsDriveLoggedInSnapshot` |
| Start / stop / restart | Adapter start/stop. Each restart uses a fresh instance. | `startRunsFreshScriptAndStopDisposesIt`, `restartCreatesNewInstanceAndOldWorkNeverRunsAgain`, `doubleStartIsRefused` |
| Pause | `Microbot.pauseAllScripts` and human input idle the guard and cancel nothing. | `guardHonoursPauseAndInterrupt`, `guardYieldsToHumanInputUntilIdleWindowPasses`, `pausedGuardSkipsTransitionsAndActions` |
| Crash | An exception or error that kills the loop is reported on the next tick, then the script is dropped. | `crashedLoopFailsClosedOnNextTickWithCause`, `exceptionInBodyEndsUpstreamStyleLoopAndIsObservable` |
| Logout / world hop / disconnect / plugin disable | The existing plugin already calls `onStop` for these. The runtime snapshot also flips to logged out, so the example script's `isLoggedIn()` gate stops at once. | `gameStateEventsDriveLoggedInSnapshot` (runtime side). The plugin side is covered by Codex's existing `MobileMicrobotPluginTest`. |
| App backgrounded / frozen engine | With no tick for 5 s while logged in, the script stops itself. This is never a pause, so it never auto-resumes. | `staleTicksStopTheScriptInsteadOfPausingIt`, `staleTicksStopScriptAndAdapterReportsItOnResume` |
| No stale queued starts | Plugin generation checks are unchanged, plus a fresh instance per start, `shutdownNow`, a remove-on-cancel queue, and a seed task that is ignored after uninstall. | `shutdownInterruptsBlockedBodyAndClearsQueue`, `loggedInSeedIsQueuedWhenOffClientThreadAndDroppedAfterUninstall`, `timedOutTaskNeverRunsLater` |
| Heartbeat | Recorded on every guard call (including refusals) and removed on shutdown. The adapter's status shows the loop count. | `heartbeatCountsGuardCallsAndIsRemovedOnShutdown` |
| Exceptions | Guard exceptions are skipped and action exceptions go to `onError`, exactly as upstream does. A failed install rolls back. | `throwingGuardIsSkippedNotFatal`, `actionExceptionRoutesThroughOnError`, `failedServiceRollsBackInstall` |
| Real `:client:compileJava` + focused tests | **Not run: blocked by `repo.runelite.net` 403.** | — |

## Unsupported on mobile (deliberately absent, not stubbed)

- From `Script.run()`: `SessionFatigue`, the tutorial-island bypass, auto-run toggling, and
  stamina/energy item use (all need Claude 2's `Rs2Player`/`Rs2Inventory` plus input actions).
- From `Script.shutdown()`: `Rs2PathApi.exit`, `Rs2Walker.disableTeleports`,
  `Rs2Keyboard.releaseHeldKeys`, special-attack config reset, `ClientThread.scheduledFuture`.
- From `Global`: `sleep(int,int)`, `sleepGaussian`, the four `*Fatigued` waits, and
  `sleepUntilOnClientThread(BooleanSupplier)` (they need anti-ban `Rs2Random`/`SessionFatigue`,
  which are not ported).
- `InputArbiter`: replaced by a time-window `MicrobotRuntime.noteHumanInput()`. There is no
  held-button tracking or pointer-distance threshold. **The platform input layer does not call
  `noteHumanInput()` yet** (see the integration items).
- The upstream blocking events (welcome screen, level-up, bank tutorial, death, Jagex popups,
  plugin pause, chatbox, membership, world-switcher confirmation, roofs): none are registered.
- `Microbot` members other than: `pauseAllScripts`, `status`, `getClient`, `getClientThread`,
  `getEventBus`, `getConfigManager`, `getBlockingEventManager`, `getGameTickBroadcaster`,
  `isDebug`, `isLoggedIn`, `log(...)`, `logStackTrace`, and the three cache getters. Mouse, hopping, plugin control, popups,
  loot records, item containers and world/overlay services are absent, so scripts that use them
  fail to compile.
- Agent server, `microbot-cli`, JAR loading, desktop input injection: not ported.

## Contract with Claude 2 (implemented)

Claude 2's `MicrobotReadServices` (see `claude2-api-report.md`) is integrated as follows:

- `Microbot.getRs2NpcCache()`, `getRs2TileObjectCache()` and `getRs2TileItemCache()` delegate
  to `MicrobotReadServices.require()`, exactly as Claude 2 proposed. No cache instances are
  created in `Microbot`.
- `Microbot.isLoggedIn()` keeps the runtime's client-thread snapshot, rather than
  `MicrobotReadServices.isLoggedIn()`, which reads `Client#getGameState()` from the calling
  thread.
- `MicrobotReadServicesLifecycle` (a `MicrobotRuntimeService`) performs Claude 2's documented
  register/install and uninstall/unregister, so the plugin passes it to
  `MicrobotRuntime.install` instead of managing two lifecycles.
- Claude 2's `ClientThreadCalls` duplicates the `ClientThread.runOnClientThreadOptional` added
  in `3e66a23`. The two were written concurrently. Both are kept for now; consolidating them
  is a follow-up for Claude 2 or Codex.

`Rs2Player.isAnimating()/isMoving()` and `Rs2Inventory.count()` must read through
`Microbot.getClientThread().runOnClientThreadOptional(...)` or a snapshot, never through
`Client` directly from the script thread.

## Integration patch for Codex (not applied to Codex-owned files)

1. **Plugin wiring** (`MobileMicrobotPlugin`, `ScriptKind`):
   - Inject `EventBus` and `MicrobotReadServices`. In `startUp()` call
     `MicrobotRuntime.install(client, clientThread, eventBus, configManager,
     List.of(new MicrobotReadServicesLifecycle(eventBus, readServices)))`. Call
     `MicrobotRuntime.uninstall()` in `shutDown()` *after* `stopActive(...)` has run on the
     client thread.
   - Add a `ScriptKind.STATE_MACHINE_EXAMPLE` entry, plus this factory case:
     ```java
     case STATE_MACHINE_EXAMPLE:
         return new MicrobotScriptAdapter<>("State Machine Example",
             StateMachineExampleScript::new,
             s -> s.run(configManager.getConfig(StateMachineExampleConfig.class)));
     ```
     The example and config are now in `src/main/java`. `StateMachineExampleIntegrationTest`
     exercises exactly this factory/launcher pair.
2. **Manual input priority:** call `MicrobotRuntime.noteHumanInput()` from the RuneMobile touch
   path for real user taps (`JavaBridge` or `MobileUiBridge`), not for script-generated taps.
3. **Checkstyle:** the copied upstream files keep upstream's K&R/4-space style. To keep them
   byte-identical, add this suppression:
   `<suppress files="net[/\\]runelite[/\\]client[/\\]plugins[/\\]microbot[/\\]" checks="LeftCurly|RightCurly|RegexpSinglelineJava"/>`
   Checkstyle and PMD were not run on these files here.
4. **Network:** allow `repo.runelite.net` in this cloud environment, as Codex's environment does.

## Commands to run after integration

```bash
source scripts/activate-java.sh
./gradlew --no-daemon --max-workers=4 :client:compileJava
./gradlew --no-daemon --max-workers=4 :client:test \
  --tests 'net.runelite.client.plugins.microbot.*' \
  --tests net.runelite.client.callback.ClientThreadOptionalTest \
  --tests net.runelite.client.plugins.mobilemicrobot.MobileMicrobotPluginTest
./gradlew --no-daemon --max-workers=4 :client:checkstyleMain :client:pmdMain
```

## Commands actually run in this session

| Command | Result |
| --- | --- |
| `git fetch --depth 1 origin e01e0d77…` (Microbot) | 0; HEAD `e01e0d77d79b354472a5737b3aaab2449eb034fc` |
| `./gradlew --no-daemon :client:compileJava` | Exit 1. The first attempt failed on Maven Central HTTP 429 (rate limit). Four retries failed with `repo.runelite.net` HTTP 403 (network policy). |
| Local `javac --release 11` pre-check (real sources + 3 out-of-repo shadows) | 0 errors. A probe class with a deliberate error was reported, which confirms the files were type-checked. |
| JUnit 4.12 `JUnitCore` over the 6 new test classes (same pre-check classpath) | `OK (55 tests)` once. A repeated-run check was started and interrupted, so there is no result. |

Dependencies added: none. The tests use the existing `junit`, `mockito` and `hamcrest`
test dependencies.
