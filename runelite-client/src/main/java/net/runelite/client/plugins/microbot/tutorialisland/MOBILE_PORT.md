# Tutorial Island: mobile port

Runs from the Microbot Mobile screen ("Tutorial Island") through `HubPluginHost`, which gives it its
input session and ends it on Stop, logout, hop, backgrounding or another script starting. Ported on
the user's request (2026-10-24), lifting its earlier exclusion.

What it does, as upstream: plays Tutorial Island from the name screen to the mainland. It picks a
random available display name, randomises the character's look (and sometimes gender and pronouns),
then follows each guide. Options (all off by default): turn music off, hide roofs, enable shift-drop
and turn off level-up popups; none of them touch a confirmation or safety prompt. Its anti-ban flags
(`naturalMouse`, `moveMouseRandomly`, `simulateMistakes`) are inert on mobile.

Changes for mobile:

- Live widget and actor reads go through the client thread: the name box and the name-check reply,
  the pronoun dropdown, the gender buttons, the bank-space prompt, the "Close" buttons of the bank
  and equipment-stats interfaces, and the rat the player is fighting.
- The ladder, anvil and tree interactions run from the script thread. Upstream ran them inside a
  client-thread task, where a mobile tap cannot be waited for.
- The gender switch waits on a fresh read. Upstream waited on a value read before the click, so it
  always ran out its timeout.
- The stage is worked out from one read of the progress varbit per pass instead of up to 18
  client-thread reads (`statusFor`).
- Missing widgets and a missing NPC end the pass instead of throwing.
- Stopping restores `Microbot.enableAutoRunOn` (the script turns it off; the flag is global) and no
  longer assumes an overlay manager (start already allowed it to be missing).

Not verified on a device. Device checks: start on a new account at the name screen and let it run to
the mainland; Stop once during character creation and once in the mining area and restart; confirm
another script's auto-run still works afterwards.
