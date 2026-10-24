# Claude 2 plan: remaining read-API work

Working plan for Claude 2 (read APIs). It is kept next to
[COORDINATION.md](COORDINATION.md), which stays the source of truth for claims and the
change log. Each step is claimed there before any edit and logged when it lands.

Status as of 2026-10-24: read batches 10 to 16m and 16o are in (943 tests in the standard
verify, Checkstyle and PMD clean). Details are in
[claude2-api-report.md](claude2-api-report.md).

## Next steps, in order

| Step | Scope | Waits on | Done when |
| --- | --- | --- | --- |
| 16l | **Done (`b9894ee`).** `util/farming/Rs2Farming`, `util/farming/SpiritTree` | — | Done |
| 16m | **Done (`e51b2f0`).** `util/inventory/Rs2FuzzyItem` | — | Done |
| 16n | `util/world/**` read half (world lists, population, access checks), only if Claude 1 hands it over | Claude 1's answer to the `util/world` question | Agreed split recorded in COORDINATION, reads tested |
| 16o | **Done (`4c27286`).** Gap re-run; ported the last unblocked gaps (`util/shop/Actions`, `util/player/NameGenerator`). What remains is walker-routed, inherited, blocked on batch AI, or listed below | — | Done |
| 16p | Final report update: coverage numbers, deviations, what is left | 16o | Report and COORDINATION agree |

## Rules for every step

1. Pull first, post the claim row and push it before editing.
2. Port upstream behaviour exactly; move client reads to `MobileActions.read`; add class
   docs; never guess where upstream depends on something missing locally (ask instead).
3. Compile, lint, run the standard verify; one commit per batch.
4. Log the batch, release the claim, pull, re-verify if the merge brought code, push to
   `feat/android-microbot-compat` and `claude/dazzling-cannon-6cy3wc`.

## Not ported, by design

- `Rs2UiHelper.isClientVersionCompatible`: no Microbot version property here.
- Anti-ban idle tracking (`Rs2Antiban.isIdle`/`isIdleTooLong`, `Category.isBusy`): the
  anti-ban plugin stays inert by Claude 1's policy.
- Persisted bank mirror (`Rs2BankData`): the bank opening time and mirror check are
  per-session.
- Walker-routed reads (nearest bank or spellbook switch, bank paths) and `input/**`:
  Claude 1's walker and runtime.
