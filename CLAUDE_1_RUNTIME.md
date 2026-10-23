# Assignment for Claude 1: M2 runtime

Work as the runtime engineer for RuneMobile/Microbot. This is an implementation
task. Read `CODEX_HANDOFF_RUNEMOBILE_MICROBOT.md`, `BUILD_REPORT.md`,
`CLAUDE_STAGE_M2.md`, and upstream agent instructions before changing code.
Codex cannot send this prompt to your session; the user must provide it and
the same source snapshot. Do not assume another agent has started or finished.

## Own

- Actual Microbot `Script`, `IScript`, lifecycle/events, heartbeat, scheduler,
  cancellation, exception handling and the subset of `Microbot` service wiring
  needed by the first script. Keep provenance, licenses and adaptation notes.
- State-machine framework (`statemachine/**`), porting real upstream source.
- A mobile adapter implementing `MobileScript`, without changing the existing
  plugin factory or `ScriptKind` in the shared checkout.
- Runtime tests and `docs/microbot/claude1-runtime-report.md`.

## Do not own

- Android/native/Gradle/build tools, global build/API/script reports: Codex owns.
- `microbot/api/**`, `microbot/util/player/**`, `microbot/util/inventory/**`,
  the upstream example script/config: Claude 2 owns.
- Existing `mobilemicrobot` plugin sources/tests: propose a small integration
  patch for Codex rather than overwriting concurrent changes.

## Contract with Claude 2

The target is the unchanged upstream
`statemachineexample.StateMachineExampleScript` at Microbot SHA
`e01e0d77d79b354472a5737b3aaab2449eb034fc`. You own `Microbot.java` and
the scheduling base classes. Claude 2 owns API implementations and should return
a service-provider interface specifying cache/query getters and initialization.
Add singleton getters only after reviewing that interface; do not independently
create duplicate cache/model classes or implement the utilities Claude 2 owns.
If both sessions cannot communicate, give Codex the proposed interface in your
report; continue scheduler tests with an injected test provider while integration
waits. Label mocked-provider results accurately.

## Acceptance

- Background scheduled work never blocks RuneLite's client thread.
- Client/game reads happen on the client thread or through immutable snapshots.
- Explicit start, stop, pause, crash, logout, world hop, disconnect, plugin disable
  and restart behavior; no silent auto-resume or stale queued starts.
- Deterministic tests for cancellation, scheduler cleanup, heartbeat and exceptions.
- Real `:client:compileJava` and focused tests pass after integration.
- No arbitrary JAR loader or remote agent server. Never claim desktop parity
  from a handful of same-named stubs. Report unsupported desktop services.

Use a separate isolated checkout/branch, for example `feat/microbot-runtime`;
do not switch Codex's active checkout. Return focused commits or a patch plus
exact commands, results, dependencies and changes Codex must integrate.
