# Script compatibility

Pinned Microbot: `e01e0d77d79b354472a5737b3aaab2449eb034fc`.
Pinned RuneMobile: `a8c4f22f8ac170b654793df2fd85372f31b18c64`.

| Script | Provenance | Source compilation | Execution |
| --- | --- | --- | --- |
| DiagnosticsScript | Supplied Phase 2 | Full RuneMobile client compile | Portable stand-in client, not RuneMobile runtime |
| SceneScanScript | Supplied Phase 2 | Full RuneMobile client compile | Portable stand-in scene, not RuneMobile runtime |
| WoodcuttingScript | Supplied Phase 2 | Full RuneMobile client compile | Not game/device-tested |
| net.runelite.client.plugins.microbot.statemachineexample.StateMachineExampleScript | Actual pinned upstream Microbot | Not ported or compiled | Not run |

The read-only upstream state-machine example is the initial candidate for later
runtime integration. Its lexical source closure reaches 1,715 classes, including
the singleton, runtime, caches, query models and state-machine infrastructure.
The graph over-approximates wildcard/same-package references and is not a complete
Java dependency resolver. See `docs/microbot/source-audit.json`.

No upstream desktop script is currently supported unchanged. Binary JAR compatibility,
dynamic JAR loading, and automatic script downloads are not implemented.
M1 compilation and focused client-interface tests have passed. M2 runtime port
is assigned in CLAUDE_STAGE_M2.md; Claude has not been launched from this session.
