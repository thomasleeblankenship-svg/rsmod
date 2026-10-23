# Claude handoff: Microbot runtime, M2 onward

## Division of work

Codex is handling Android SDK/NDK/JRE setup, native build, staging,
packaging, artifact checks, environment configuration and build evidence.
Claude is not connected to this session; this document is a proposed task
assignment for the user to give Claude, not evidence that Claude has begun.

Claude should handle the real upstream runtime port (M2), then the read-only
API closure for one actual script (M3/M4). Keep work in a separate checkout
or branch. Do not switch the branch or overwrite files in Codex's active
checkout while builds are running. Each cloud task is already isolated;
no extra Git worktree is needed unless the user requests one.

## Current source and evidence

- Destination: `thomasleeblankenship-svg/rsmod`, feature branch
  `feat/android-microbot-compat`; original rsmod history retained.
- RuneMobile source: `audaro/runemobile` at
  `a8c4f22f8ac170b654793df2fd85372f31b18c64`.
- Microbot reference: `chsami/Microbot` at
  `e01e0d77d79b354472a5737b3aaab2449eb034fc`.
- Source has been replaced with RuneMobile plus the supplied Phase 2 plugin.
- Full `:client:compileJava` passed, using actual RuneMobile dependencies.
- `:client:test --tests net.runelite.client.plugins.mobilemicrobot.MobileMicrobotPluginTest`:
  9 passed, zero failures/errors/skips, with real interfaces mocked at boundaries.
- `scripts/test-portable.sh`: 25 passed; these are stand-in tests, not game tests.
- Shadow JAR, probes, GPU and 117HD variants, verified bionic JRE and runtime
  staging passed. APK assembly is being handled separately by Codex.
- No game/device execution or desktop script compatibility is yet verified.

## Codex-owned files: avoid changing concurrently

- `android/**`, `ios/**`, `tools/**`, Gradle manifests and version catalogs.
- `scripts/activate-java.sh`, `scripts/build-android.sh`, setup instructions.
- `BUILD_REPORT.md`, Android validation report and artifact manifests.
- Existing Phase 2 plugin lifecycle fixes and tests: coordinate any edits to
  `MobileMicrobotPlugin.java`, `MobileScriptBridge.java` and their tests through
  a reviewable commit/patch, not direct overwrites in the shared checkout.

## Claude-owned implementation

- Runtime implementation under `net.runelite.client.plugins.microbot` or a
  clearly documented mobile runtime package, plus associated tests.
- Preserve actual upstream source provenance and license notices.
- Read upstream root/package `AGENTS.md` and entity guides before porting.
- Adapt lifecycle/scheduler, cancellation, heartbeat and required singleton
  services. No sleeps or blocking work on the RuneLite client thread.
- Do not copy the entire desktop singleton and its 1,700-class lexical closure
  without isolating the actual dependencies and mobile-incompatible services.
- Start with the actual upstream
  `statemachineexample.StateMachineExampleScript`, a read-only candidate.
  Its core logic must remain unchanged; label all adapted APIs and semantics.
- No arbitrary JAR loader, remote agent server, desktop input injection, or
  completeness claims from same-named stub classes.
- Build a deterministic scheduler/cancellation test suite, then compile the
  unmodified upstream example against actual RuneMobile classes. Game/device
  execution remains a separate acceptance gate.
- Extend `API_SUPPORTED.md`, `SCRIPT_COMPATIBILITY.md` and the machine-readable
  API matrix only with evidence. Read `docs/microbot/source-audit.json` for
  initial fork differences; it is a lexical graph, not a symbol resolver.

## Integration contract

Return focused commits or a patch plus executed commands and results. Provide
an adapter implementing `MobileScript` for plugin registration; propose the
small `ScriptKind`/factory change in your patch. Codex can integrate it between
builds and rebuild the staged jars/APK. Never assume a click enqueue means a
successful game action. Preserve manual input priority and fail-closed stop,
logout, world-hop, plugin-disable and app lifecycle behavior.

In this cloud instance, activate tools with:

```bash
cd /workspace/rsmod
source scripts/activate-java.sh
./gradlew --no-daemon --max-workers=4 :client:compileJava
./gradlew --no-daemon --max-workers=4 :client:test --tests net.runelite.client.plugins.mobilemicrobot.MobileMicrobotPluginTest
```

The initial Maven 403 was resolved after adding `repo.runelite.net` to the
environment draft. Do not request new credentials for an already working Git
or Maven read. No changes have been pushed to GitHub by this session.
