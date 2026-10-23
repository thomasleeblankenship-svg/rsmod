# Script compatibility

Pinned Microbot: `e01e0d77d79b354472a5737b3aaab2449eb034fc`.
Pinned RuneMobile: `a8c4f22f8ac170b654793df2fd85372f31b18c64` (per `BUILD_REPORT.md`).

Source compatibility and binary (JAR) compatibility are separate. **There is no binary
JAR compatibility.** By design, there is no dynamic JAR loading and no automatic script
download.

| Script | Provenance | Source compilation | Automated execution (mocked engine) | Phone / live game |
| --- | --- | --- | --- | --- |
| `statemachineexample.StateMachineExampleScript` + config | Upstream Microbot, copied unchanged (SHA-256 `aed85889b184231d2c910c719720c87ad129ca38a25dd0bfd99964750c245dbd`) | Compiles in-tree against the real client, Microbot runtime and read APIs | `StateMachineExampleIntegrationTest`, `UpstreamExampleIntegrationTest`: full scan → report cycle; logout stops it stepping; stop cleans up | Selectable as `STATE_MACHINE_EXAMPLE`; not phone-tested |
| Auto Woodcutting v1.8.7 (mobile port) | Uploaded desktop source, ported (see [WOODCUTTING_MOBILE.md](docs/microbot/WOODCUTTING_MOBILE.md)) | Compiles; **adapted**, not unchanged | Drop, bank, walker, Lumbridge stairs, Forestry and host-lifecycle tests | User report on the `024f387` APK: full-inventory drop works; Lumbridge stairs did not. Walking was reworked in `ba30cb9`; needs a retest |
| DiagnosticsScript, SceneScanScript, WoodcuttingScript | Phase 2 mobile examples | Compile | Plugin lifecycle tests | Not phone-tested |

The per-API coverage behind these rows is in [API_SUPPORTED.md](API_SUPPORTED.md),
[the runtime report](docs/microbot/claude1-runtime-report.md) and
[the read-API report](docs/microbot/claude2-api-report.md). Automated results use Mockito
doubles of RuneLite interfaces and are not live-game evidence. Any other upstream script
compiles only if every API it calls is in the supported subset. Most desktop scripts use
input, walker or UI helpers that are not ported, so they won't.
