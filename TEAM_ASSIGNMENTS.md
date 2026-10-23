# Three-way engineering split

| Owner | Stage | Assignment |
| --- | --- | --- |
| Codex, current session | Android setup/staging/build, APK checks, integration and consolidated reports | Current checkout `/workspace/rsmod`, branch `feat/android-microbot-compat` |
| Claude 1, user-managed session | M2 runtime and state-machine port | [CLAUDE_1_RUNTIME.md](CLAUDE_1_RUNTIME.md) |
| Claude 2, user-managed session | Read-only APIs and first unchanged upstream example | [CLAUDE_2_APIS.md](CLAUDE_2_APIS.md) |

The Claude assignments are prepared; neither Claude session is connected to or
launched by Codex. Give each session its assignment and the same source snapshot.
Use separate isolated checkouts/branches. Each task is already isolated; an extra
Git worktree is unnecessary unless the user requests one.

Claude 2 defines the read-service interface; Claude 1 integrates it into the
singleton/runtime; Codex registers the adapter in the mobile plugin, reruns
client tests, regenerates every affected staged jar/runtime tag, and rebuilds
the APK. Each Claude returns commits/patches plus its own narrow report.
Codex consolidates the global build/API/script reports and owns the final merge.
No agent should edit another owner's files directly in a shared checkout.

The current Phase 2 APK is a baseline mobile scripting build. It does not
demonstrate source or binary compatibility with upstream Microbot scripts.

## Update, 2026-10-23

At the user's direction, Claude 1 has taken over Codex's remaining scope (integration,
consolidated reports, APK delivery via the `Mobile test APK` workflow) in addition to
the runtime. Claude 2's read-API scope is unchanged. Current status lives in
[the current handoff](docs/microbot/CODEX_HANDOFF_CURRENT.md).

All cross-session claims, requests and change notes go in
[docs/microbot/COORDINATION.md](docs/microbot/COORDINATION.md).
