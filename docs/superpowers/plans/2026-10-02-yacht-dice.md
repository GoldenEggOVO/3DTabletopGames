# Yacht Dice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable Yacht Dice with five small animated dice, reversible keep slots, and direct table controls.

**Architecture:** Preserve the seeded YachtGame rules and replay format. A dedicated YachtTable owns the five reusable dice, tray, category controls and ray hits; TableView delegates rendering and existing GameWorld gates enforce turn identity and animation locks. Native and packed layers follow TableAudience.

**Tech Stack:** Java 25, Paper 26.2, JUnit/Mockito, Python resource-pack exporter, optional CraftEngine.

**Spec:** User screenshots and requested roll/keep/unkeep interaction; existing 12-category rules with three rolls, upper bonus 35 at 63, small straight 15, large straight 30 and Yacht 50. Preserve 2–4 player capacity.

## Global Constraints

- English source and semantic localization keys; Chinese delivery notes.
- Keep current rooms/replay and other games unchanged.
- Reuse entities while rolling; idle ticks do not resend poses.
- World ray hits follow settled dice slots; animation rejects all moves.
- Test server deployment uses backup and clean stop; source sync has no Release.

## Review Focus

- Held dice retain their values and exact slot positions through rerolls.
- Score selection and holds are rejected during animation, on stale revisions and for other players.
- Final roll still permits keep/unkeep without granting a fourth roll.
- Audience changes rebuild only the needed model layer and preserve animation progress.
- Close removes all displays/interaction entities without leftovers.

### Task 1: Enable catalog and retain rules

- [ ] Add tests for catalog visibility, post-third-roll hold/unhold, scoring once and turn reset.
- [ ] Run focused rules tests and confirm failure for the missing behavior.
- [ ] Enable Yacht and permit hold/unhold after the third roll; preserve seeded dice generation.
- [ ] Run rules tests.

### Task 2: Physical dice table

**Files:** YachtDie.java, YachtTable.java, TableView.java, GameWorld.java, languages/en_US.yml and zh_CN.yml; YachtTableTest.java.

- [ ] Write tests asserting the five .18-block dice settle in one row, keep/unkeep moves between fixed positions, held dice do not animate, native dice are reused, ray reach is bounded and close removes every part.
- [ ] Run tests and confirm the renderer is missing.
- [ ] Implement shared die geometry and YachtTable with roll button, twelve score buttons, score-sheet button, fixed slots and bounded throw animation.
- [ ] Delegate TableView sync/tick/hit/cursor and wire direct scoring/menu actions through existing guards.
- [ ] Add semantic localized controls and score categories, update rules hint.
- [ ] Run focused tests and full Maven package.

### Task 3: Pack and delivery

- [ ] Export yacht_table and yacht_die models, assert six face mappings and named model catalog parity.
- [ ] Run asset checks and full package with bundled new pack hash.
- [ ] Run fresh standalone and soak probes; review the diff and fix concrete findings.
- [ ] Package Chinese delivery notes, back up and update the independent test server, verify runtime and pack digest.
- [ ] Commit scoped files and push development branch; verify matching CI. Do not publish a Release.

## Execution ledger

- Catalog and third-roll tests reproduced the missing functionality before implementation.
- Native row/slot tests reproduced the absent physical renderer, then passed.
- Language reload preserves active throw frames; mixed-mode switching reuses the authoritative result.
- All 592 Java tests passed; 15 pack contract checks and 4 upgrade tests passed.
- Reviewer found language reload cancellation and category key migration; both were fixed with regressions.
- Ruling: preserve 2–4 seats and the existing seeded 12-category rules rather than restrict capacity to the reference game.
- Ruling: keep snapshot version 1.10.0-SNAPSHOT and use a new immutable `-yacht` pack URL; the installed hash distinguishes this acceptance build.
