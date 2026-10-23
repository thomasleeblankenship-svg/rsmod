# Auto Woodcutting on mobile

This is a source port of the uploaded Auto Woodcutting v1.8.7 (source reference
`080d9674fe87f282c958c25b5bb8f6b96665fe36`), bundled into RuneMobile. It is not a
ZIP/JAR loader. The previous APK built from `131c232` does not include this port.
The current build adds a full inventory drop reset, axe protection and corrected Lumbridge staircase resolution.

## Install and load

1. Open the repository's [Mobile test APK workflow](https://github.com/thomasleeblankenship-svg/rsmod/actions/workflows/mobile-test-apk.yml).
2. Open [the latest debug build](https://github.com/thomasleeblankenship-svg/rsmod/actions/runs/40005552665) (run 50, `789a426`: everything below plus the Hub plugins and the pathfinder webwalker). Under **Artifacts**, download `runemobile-mobile-debug-arm64` while signed into GitHub.
3. Unzip the download, transfer `runemobile-mobile-debug-arm64.apk` to an ARM64 Android 9+ phone, and install it. The APK is locally/debug signed; an existing app signed with another key may require uninstalling first, which removes its local data.
4. In the app's plugin panel, enable **Microbot Mobile (phase 2)** and open its settings.
5. Set **Script** to **Auto Woodcutting (mobile port)**. Configure **Tree**, **Distance to Stray**, **Primary action**, retained items and Forestry options. The settings labelled **Example woodcutting tree** and **Example tree distance** belong to the old example script.
6. For banking, choose **Primary action → Bank logs**. Bank selection is automatic; there is no manual bank selector. Start near the desired trees with a usable axe and enough free inventory space, then enable **Run script**.
7. Disable **Run script** to stop. A manual tap no longer cancels the run: it drops any queued script tap and pauses the script for about 1.8 s, then the script carries on (builds from `6f6a2cd` onward). Backgrounding the app, signing out, hopping or disconnecting still end the run and require a new start.

Auto-hop is disabled by request. The port has no Hop setting, and it never reads a saved desktop `Hop=true` value.

## Ported workflows

- Tree/skill/axe checks, visible tree interactions, hardwood patches and progressive tree selection.
- Inventory dropping/retention, confirmed bank deposits and withdrawals, and log-basket handling.
- Firemaking/campfires, fletching, bow-stringing and their secondary reset actions.
- Nest/seed looting, special attack and all nine uploaded Forestry event handlers.
- Collision-aware same-plane travel across regions, local door/gate recovery and explicit Lumbridge castle stairs in both directions.
- Automatic bank selection covering Lumbridge upstairs, Draynor, Falador and Varrock. Access requirements and floor distance affect selection. Walking and staircase transitions require observed arrival before proceeding.

Script-thread waits are bounded and cancelled with the owning input token. Client API reads and exact live-target menu actions run on the client thread. A dispatch return is not treated as successful banking, production or arrival; workflows check state changes. Slot-based actions bind the expected item ID, preventing a replacement item from being selected after a delayed dispatch.

## Current limits and acceptance

This is a mobile test port, not verified desktop feature parity. Ships, teleports,
other ladder/stair transports, instanced cross-region routing and the desktop
anti-ban/fatigue system are not ported. Routes needing unsupported transports
stop with a diagnostic rather than pretending to arrive. The same-plane collision
map is a pinned upstream snapshot; changing world geometry can make routes fail.

User phone testing exposed the inventory reset and Lumbridge floor bugs addressed here. A user phone test of the `024f387` build reported that full-inventory drop works and the Lumbridge stairs did not. Walking was then changed to scene-resolved dispatch with stall retry (`ba30cb9`), which still requires a phone retest. Automated tests validate client-thread
marshalling, cancellation, exact inventory/bank parameters, bank selection,
collision routes, Lumbridge staircase planning, host lifecycle and portions of
Forestry logic. They do not prove any game interaction succeeds on-device.

First device acceptance should check one full inventory and automatic bank-return
cycle near each town, including the Lumbridge stairs, then each enabled production
and Forestry workflow. Stop, manual takeover, logout and background/resume must
leave no queued script input or automatic restart.

Original upload fingerprints and adaptation details are in
[the source manifest](../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/woodcutting/UPLOADED_SOURCE_MANIFEST.json)
and [port notes](../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/woodcutting/MOBILE_PORT.md).

## Cloud validation

The integrated source passes 222 focused Gradle client tests (zero failures,
errors or skips), Checkstyle and PMD. The bridge/auth contract preflight passes.
The older Phase 2 portable harness separately passes 25 tests; its stand-ins do
not exercise the uploaded woodcutting port. The GitHub APK workflow repeats the
real focused checks before building and uploading its APK.

The debug workflow checks signing, native-library alignment, debuggability and
absence of bundled `tokens.json`. GitHub builds have their own signing key and
SHA; use the checksum file inside the artifact.

## Debug logs and fixes to retest

With **Primary action → Drop logs**, a full inventory must drain all eligible
items before chopping resumes. Axes are protected independently of **Items to
keep**. The test exercises 27 logs plus an axe, hidden inventory, rebuilt slot
actions, changing slots and cancellation. Drop dispatch now maps definition
operations to the inventory widget's actual operation numbers. Each drop is
confirmed against a fresh inventory read; diagnostics record failures.

Lumbridge stairs resolve live object IDs/actions using the player's actual
floor. The integration test starts upstairs with a stale world-view plane and
requires both observed descents before ground travel.

This build is debuggable. With Android platform-tools, USB debugging enabled
and the phone authorized, read the private client log using:

```sh
adb exec-out run-as dev.audaro.runemobile cat files/.runelite/logs/client.log > client.log
```

To list available logs:

```sh
adb shell run-as dev.audaro.runemobile ls files/.runelite/logs
```

These commands require the debug APK; a release APK rejects `run-as`.

[Download the verified GitHub debug artifact ZIP](https://github.com/thomasleeblankenship-svg/rsmod/actions/runs/40005552665/artifacts/12525165353) (run 50) while signed into GitHub.
