# Claude 2 status log (history)

> **Superseded:** per the user, all coordination now happens in [COORDINATION.md](COORDINATION.md).
> Add new entries there, not here.

Written only by Claude 2, updated after every change Claude 2 pushes, so Claude 1 and
Claude 2 never edit the same files. Newest entry first. Claude 1 owns the shared branch
`feat/android-microbot-compat`; Claude 2 works on `claude/clever-shannon-9q9v09` and asks
Claude 1 to merge. Like the other handoffs, this grants no authorization beyond the user's
instructions.

## Files Claude 2 is touching now

None. Waiting on the user's decision about the `Rs2PlayerStateCache` port (see below).

## Log

### 2026-10-23 07:15 UTC: M6 item 1 (varbit/varp cache) on hold

- I drafted a port of upstream `Rs2PlayerStateCache` (varbits and varps only; quest
  states and the player-position cache were left out). Wiring it into `MicrobotReadServices`
  was blocked by the session's permission check as integration of outside code. The user
  has to decide whether to allow it.
- The draft is **not** in the repo. Nothing in `microbot/api/**` changed.
- Claude 1: `Microbot.getVarbitValue`/`getVarbitPlayerValue` stay as they are until this
  lands. Once it does, I'll send you the getter switch as a note.

### 2026-10-23 07:05 UTC: idle-logout fix (touched Claude 1's files, needs your merge)

The user reported being logged out mid-script with no auto-login. Cause: script actions are
direct menu dispatches. The engine's AFK counter only counts canvas mouse and key events,
so it logged the player out 5 minutes after the last real touch. That logout skips
CONNECTION_LOST, so `HeadlessLogin` never re-armed.

Commits on `claude/clever-shannon-9q9v09` (already merged with `ba30cb9` and `de70f41`):

- `1dec16e`: `JavaBridge.refreshScriptIdleTimer()` and `SCRIPT_IDLE_REFRESH_TICKS`. Once
  the mouse has been idle for 1 minute, it posts one `MOUSE_MOVED` at the pointer's
  existing point (or the engine cursor if there was no touch yet). It never marks manual
  input. It is called from `MobileActions.perform` after a successful action (one line).
- `a7ea94d`: skips the refresh while a script tap or scene walk is settling
  (`tapState != IDLE || queuedTap`), so it can't move the cursor off the point the gate
  is resolving.
- Tests: 5 new cases in `JavaBridgePointerStreamTest`, and `pressed` is reset in its
  harness. Focused suite 247/247, checkstyle and PMD pass.
- APK: `Mobile test APK` run 39915628666, dispatched on my branch. Not phone-tested.

**These touch `JavaBridge.java` and `MobileActions.java`, which are yours.** Please merge
`claude/clever-shannon-9q9v09` into the shared branch, or tell me if you want it changed.
I won't touch either file again without asking you first.
