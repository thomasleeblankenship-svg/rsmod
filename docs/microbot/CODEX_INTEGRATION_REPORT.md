# Codex integration status

The mobile plugin installs `MicrobotRuntime` with `List.of(readServices)` on the client thread at startup and uninstalls it after stopping its script at shutdown. The script selector includes `STATE_MACHINE_EXAMPLE`, backed by the unchanged upstream example in `runelite-client/src/main/java/net/runelite/client/plugins/microbot/statemachineexample/`. Real native input calls `MicrobotRuntime.noteHumanInput()` through `ScriptInputSession`; cancelled sessions prevent queued script taps and background scripts from resuming without a new start.

Merged Claude runtime and read-service updates through 311d064f. Cache handles return empty after uninstall; all non-logged-in game states clear inventory and player snapshots. The three cache getters are present on `Microbot`.

Validation passed: real Gradle client compilation; 145 focused runtime, read API, upstream example, plugin lifecycle and input bridge tests (zero failures or skips); `:client:checkstyleMain`; and `:client:pmdMain`. The Checkstyle tool is available here, with only the documented formatting rules exempted for copied Microbot sources. Portable checks separately pass 25 tests and are not evidence of runtime compatibility. No live game or physical Android device was available. Previously published APK artifacts predate this integration; this source update does not establish device validation.
