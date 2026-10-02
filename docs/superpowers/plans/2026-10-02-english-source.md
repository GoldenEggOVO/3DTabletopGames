# English Source Implementation Plan

> For agentic workers: implement the bounded independent tasks with superpowers:dispatching-parallel-agents, then perform a combined review and verification.

**Goal:** Keep executable source, comments, diagnostics and configuration instructions in English, with user-facing language in languages/*.yml.

**Architecture:** Retain semantic language keys and the existing gameplay/state contracts. English diagnostics stay in rules; rendered messages continue through Language. Fixed artwork glyphs are Unicode symbols referenced by descriptive English constants, not translated into different game pieces.

**Tech Stack:** Java 25, Paper/Purpur 26.2, Maven, Python, existing CraftEngine resources.

**Spec:** The user request in this chat: write code in English and keep language text in translation files.

## Constraints

- Only this Tabletop worktree; preserve existing tests, rules, persisted action IDs, layouts and glyph artwork.
- Do not translate Unicode piece artwork into English words or remove non-English input coverage.
- Retain Chinese documentation and language catalogs; historical language conversion maps are translation data.
- No Release. Deliver and update the already authorized independent test server after verification.

## Tasks

- [x] Rules: translate diagnostics and unused human-readable summaries to English; keep semantic message keys and canonical piece/action values.
- [x] Runtime: translate comments/logging; route remaining displayed prose through existing semantic catalogs.
- [x] Artwork and tests/tools: name fixed game glyphs in English; keep Unicode testing via catalog fixtures or explicit Unicode notation; translate Python comments/errors.
- [ ] Verify: no Han prose in executable source; full Maven/Python suites, glyph/persistence regressions, local package and test-server boot/config checks.

## Review Focus

- Canonical piece values and glyph appearance must not change.
- Both English and Chinese UI text must still come from their catalog.
- Replay action IDs must remain stable.
- Legacy offline conversion must retain exact matching of historical translations.
- Do not remove Unicode regression coverage to make a source scan pass.

## Progress

- Source-language guard: RED before cleanup, GREEN after cleanup.
- HUD bot-name regression: RED with internal name leaking, GREEN with RoomText.player.
- Combined Maven package: 546 tests, zero failures/errors/skips; Python: 13 passed.
- Independent review found the HUD bot-name leak; fixed with a behavioral regression.
- All Unicode piece values, bitmap strokes and model geometry preserved.

- Native and CraftEngine three-boot probes passed for the final JAR; VPS deployment pending.
