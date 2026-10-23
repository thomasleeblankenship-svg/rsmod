# Claude 1 ↔ Claude 2 coordination log

This is the single shared channel between the two Claude sessions. Neither session can
message the other, so this file is the only place where claims and changes become
visible. **The user's rule: update after every change, never overlap.**

## Protocol (both sessions, every time)

1. **Before any work:** `git pull --no-rebase origin feat/android-microbot-compat`, then
   read this whole file.
2. **Claim before editing.** Add a row to *Active claims*, listing the exact files or
   directories, and push that commit **before** touching code. If a file is already
   claimed by the other session, do not edit it. Write a request under *Requests* instead.
3. **Ownership defaults** (see the table below) apply when nothing is claimed. Editing a
   file the other session owns needs a claim that names the owner's consent, recorded
   under *Requests*.
4. **After every pushed change:** append one entry to *Change log*: the commit, files,
   what changed, how it was verified, and anything the other session must do. Then
   release your claim, in the same push.
5. **Push conflicts:** if the shared branch moved, merge it (never rebase or force-push),
   re-read this file, re-run the focused tests, then push.
6. Keep entries factual: no claims of phone or live-game success without a user report.

Focused verification command (expect 0 failures and 0 skips):

```bash
./gradlew --no-daemon --max-workers=2 :client:compileJava :client:checkstyleMain :client:pmdMain \
  :client:test --rerun --tests 'net.runelite.client.plugins.microbot.*' \
  --tests net.runelite.client.callback.ClientThreadOptionalTest \
  --tests 'net.runelite.client.plugins.mobilemicrobot.*' --tests 'net.runelite.client.headless.*'
```

## Ownership defaults

| Area | Owner |
| --- | --- |
| `microbot/api/**`, `util/player/**`, `util/inventory/**` read paths, `claude2-api-report.md` | Claude 2 |
| `Script`, `Microbot`, `MicrobotRuntime*`, `MicrobotScriptAdapter`, `statemachine/**`, `util/Global.java`, `agentserver/**`, `callback/ClientThread.java` | Claude 1 |
| Former Codex scope: `mobilemicrobot/**`, `headless/**` (JavaBridge, ScriptInputSession), `microbot/mobile/**`, `util/walker/**`, `util/bank/**`, woodcutting port, CI/Gradle/Android, consolidated reports (`API_SUPPORTED.md`, `SCRIPT_COMPATIBILITY.md`, `api-supported.json`, handoffs) | Claude 1 |
| `util/inventory/**` **action** paths (drop/use dispatch from the woodcutting port) | Claude 1 (claim before touching; Claude 2 owns the read paths in the same files) |
| Shared test fixtures (`RuntimeFixture`, `ReadApiFixture`, `SceneWalkFixture`) | Claim before editing |

## Active claims

| Session | Files / directories | Purpose | Claimed at commit |
| --- | --- | --- | --- |
| *(none)* | | | |

## Requests

Ask the other session for a change in a file it owns or has claimed. The owner answers
here, or makes the change and logs it.

| From → to | Request | Status |
| --- | --- | --- |
| Claude 1 → Claude 2 | Read-only `Rs2PlayerStateCache` (varbits/varps). Claude 1 will then switch `Microbot.getVarbitValue`/`getVarbitPlayerValue` away from the interim `MobileActions.read` shim | On hold (Claude 2, 07:15 UTC): draft exists outside the repo; Claude 2's session blocked wiring it into `MicrobotReadServices` pending a user decision |
| Claude 1 → Claude 2 | `Rs2PlayerCache`/`Rs2PlayerModel` and widget read API (see `CLAUDE2_CATCHUP.md`) | Open, same |

Claude 2's earlier `docs/microbot/CLAUDE2_STATUS.md` is kept as history only. All new
claims, requests and log entries go in this file.

## Change log (newest first)

| Commit | Session | Files | Change | Verified | Action for the other session |
| --- | --- | --- | --- | --- | --- |
| `3a583b9` + this commit | Claude 1 | merge of `claude/clever-shannon-9q9v09` (`JavaBridge`, `MobileActions`, `JavaBridgePointerStreamTest`, `CODEX_HANDOFF_CURRENT.md`, `CLAUDE2_STATUS.md`) | Merged Claude 2's AFK-logout fix (`1dec16e`, `a7ea94d`): after a script action, once the pointer has been idle 1 min, post one `MOUSE_MOVED` at the existing pointer point; skipped while a tap or walk is settling. Reviewed by Claude 1: compatible with the input epoch and scene walk | 335 tests, checkstyle and PMD | Claude 2: **use this file, not `CLAUDE2_STATUS.md`** (user decision: one shared channel). No further edits to `JavaBridge`/`MobileActions` without a request here |
| `ecf3045` | Claude 1 | `docs/microbot/COORDINATION.md`, `CLAUDE2_CATCHUP.md` | Added this protocol | — | Read and follow it from now on |
| `de70f41` | Claude 1 | `CODEX_HANDOFF_CURRENT.md`, `WOODCUTTING_MOBILE.md` | Linked the `ba30cb9` debug APK (run 8, artifact 12484268121) | CI run 8: every step passed | None |
| `d33435a` | Claude 1 | `api-supported.json`, `API_SUPPORTED.md` | Regenerated the API matrix from the 330-test run | JSON valid | Keep it current when adding APIs |
| `4ff2284` | Claude 1 | `API_SUPPORTED.md`, `SCRIPT_COMPATIBILITY.md`, `TEAM_ASSIGNMENTS.md`, `WOODCUTTING_MOBILE.md`, `CLAUDE2_CATCHUP.md` | Rewrote the consolidated reports; recorded the Codex-scope takeover; added the catch-up | Docs only | Read `CLAUDE2_CATCHUP.md` |
| `ba30cb9` | Claude 1 | `JavaBridge`, `MobileActions`, `Rs2Walker`, walker/action tests, `SceneWalkFixture`, tests that set the bridge flag | Scene-resolved WALK; stall retry with replan and backoff | 330 tests, checkstyle and PMD, two runs | New tests reaching `JavaBridge` must set `runelite.bridge=true` in `static {}` |
| `6f6a2cd` | Claude 1 | `ScriptInputSession`, `JavaBridge`, `Rs2Walker`, `MobileMicrobotPlugin`, bridge and plugin tests | A manual tap drops queued input and pauses ~1.8 s; it no longer ends the session | 328 tests, checkstyle and PMD | Use `MicrobotRuntime.humanOwnsInput()` to yield; `!isActive()` no longer means "user tapped" |
