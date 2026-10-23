# Assignment for Claude 2: read-only APIs and unchanged upstream example

Work as the API compatibility engineer for RuneMobile/Microbot. Read
`CODEX_HANDOFF_RUNEMOBILE_MICROBOT.md`, `BUILD_REPORT.md`, `API_SUPPORTED.md`,
`SCRIPT_COMPATIBILITY.md`, upstream root/package `AGENTS.md` and entity guides.
The user must provide this prompt and the same source snapshot to your session;
Codex cannot dispatch it to Claude directly.

## Own

- The minimal read-only API closure for the actual upstream
  `statemachineexample.StateMachineExampleScript` and its config.
- `microbot/api/**` cache/query/model implementation needed by that script,
  `microbot/util/player/**`, `microbot/util/inventory/**`, their focused tests.
- `docs/microbot/claude2-api-report.md`, including a machine-readable method
  coverage table and exact upstream provenance/hashes for copied source.

## First actions

1. Pin RuneMobile `a8c4f22f8ac170b654793df2fd85372f31b18c64` and Microbot
   `e01e0d77d79b354472a5737b3aaab2449eb034fc`; inspect real signatures.
2. Copy the selected example/config unchanged, preserving license/provenance.
3. Extract the methods it actually needs: NPC/object/ground-item cache queries,
   `within`, `where`, `nearest`, `toList`, model names/lootability, inventory
   count, player animation/movement and login guard semantics.
4. Publish a small service-provider interface for Claude 1's singleton wiring.
   You own its cache/query implementation; Claude 1 owns `Microbot.java`.
   Do not modify that singleton independently. If no inter-session messaging is
   available, give the interface and adapter patch to Codex for integration.
5. Port/adapt real source in dependency order. Do not implement no-op lookalikes
   just to make the example compile. State unsupported behavior explicitly.

## Acceptance

- Singleton-managed caches; no direct cache construction by scripts.
- Event-driven updates or documented immutable client-thread snapshots, with
  tests for logout, scene changes, stale entities and cross-thread reads.
- Player and inventory semantics checked against actual upstream methods
  (for example, distinguish occupied slots from summed stack quantities).
- Correct nearest/distance/plane filters and ground-item visibility/lootability.
- Unchanged example compiled against real RuneMobile interfaces once Claude 1's
  runtime is integrated. Until then, report dependency blockers without inventing
  fake runtime classes in Claude 1's package.
- Tests use real interface types; mock/service tests are not live-game evidence.
- No action/input/walker expansion in this stage; no arbitrary downloaded JARs.

## Do not own

Android/native/build tools and global reports (Codex), `Script`/`Microbot`/
state-machine runtime (Claude 1), existing mobile plugin factory/lifecycle
(Codex integrates small proposed registration changes).

Use a separate isolated checkout/branch, for example `feat/microbot-read-apis`.
Do not switch Codex's active checkout or edit another agent's files concurrently.
Return focused commits or a patch, source identity checks, executed tests and
the integration interface. Keep `API_SUPPORTED.md` and `SCRIPT_COMPATIBILITY.md`
changes as recommendations in your own report so Codex can consolidate them.
