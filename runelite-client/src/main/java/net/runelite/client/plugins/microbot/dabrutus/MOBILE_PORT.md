# Da Brutus: mobile port

Runs from the Microbot Mobile screen ("F2P: Da Brutus (Brutus boss)") through `HubPluginHost`, which gives it its
input session and ends it on Stop, logout, hop, backgrounding or another script starting.

Source: the SlugBot plugin Da-Brutus 0.6.0 (`morrob955/iAmDaTrainer-Scripts`, `Da Brutus/`), which compiles and passes
its tests unchanged against the upstream Microbot 2.6.47 release jar (that build lives in
`~/Downloads/microdata/microbot-dabrutus`). It also compiles unchanged against this client.

Changes for mobile:

- No setup window (Android has no Swing). The window's choices are settings in a new "Goal and persona" section:
  Attack/Strength/Defence/Ranged/Magic goals, what to do when the goal is reached, the lamp skill and the persona
  strength. The script starts with them as soon as the account profile is known, and saves them where the desktop
  window keeps its last choices. `SetupDialog` is removed; the persona re-roll (a window button) is not offered.
- The 40 unit tests are converted from JUnit 5 to JUnit 4 (this client's test runner); the assertions are unchanged,
  except that two exact `double` comparisons take JUnit 4's required delta (`0.0`).
- PMD clean-ups with no behaviour change: an unused paint font helper and two unused locals in `planRanged` removed,
  two unnecessary casts dropped, and single-character `append` strings made chars.

Unchanged: the quest, fight, dodging, banking, GE and paint code.

Not verified on a device. Watch first:

- **Dodges.** The dodge thread clicks through `Rs2Walker.walkFastLocal`, which on mobile waits up to 1.2 s when the
  loop has already used this tick's action, so a dodge can land a tick later than on desktop.
- GE searches type item names with `Rs2Keyboard.typeString`.
- The paint overlay's size and position over the relocated chat.
