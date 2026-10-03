# texas-holdem Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Implement a two-to-six-player no-limit match with room-local chips and correct settlement.

**Architecture:** TexasHoldemGame owns betting and match progression; PokerHands evaluates cards; PokerPots settles contributions. Shared table renders private hands and public board.

**Tech Stack:** Java 25, Paper 26.2, JUnit Jupiter, Python/Pillow, CraftEngine public API.

**Spec:** ../specs/2026-10-02-texas-holdem-design.md

## Global Constraints

- Preserve all approved Mahjong/Color Eight controls and existing saved rooms.
- Keep vanilla/resource-pack/mixed modes and private hand isolation.
- Source and diagnostics are English; player copy uses semantic language keys.
- User explicitly requested direct implementation; execute inline without additional design/plan approval stops.
- Broad final cleanup is deferred; GitHub source synchronization is authorized; no Release or release tag.

## Review Focus

- Mixed viewers entering/leaving during animations: preserve visible pose and remove unused layers.
- Duplicate/stale clicks: at most one accepted event for the captured revision.
- Spectators and bots: never expose concealed cards.
- Restart mid-phase: seed plus accepted history restores the same board.
- Large hands and chip amounts: no exponential selection scan or entity-per-chip rendering.

## Task 1: Implement and verify the feature

**Files:** Create rules/{TexasHoldemGame,PokerHands,PokerPots}.java and corresponding tests; extend PlayingCardTable, GameWorld, GameMenus amount input, factory, catalog, bots, RoomText and languages.

**Interfaces:** TexasHoldemGame(int players, long seed); fold/check/call/raise:<street total>/all-in/continue actions. PokerHands evaluates best five of seven; PokerPots settles eligible contributions and odd chips.

- [ ] Write focused behavior tests: TexasHoldemGameTest: 1000 chips each, 5/10 blinds, heads-up order, bet/raise minima, short and cumulative all-ins, no premature street advancement, five board cards, dealer rotation, stale callbacks. PokerHandsTest verifies category counts and wheel/kickers; PokerPotsTest verifies refunds, ties, side pots and conservation.
- [ ] Run the focused tests and record the expected failure before product changes.
- [ ] Implement the named components and integrate table controls, persistence, bots and translations required by the specification.
- [ ] Run focused tests, then the Java suite; asset tasks also run Python contract tests.
- [ ] Review the scoped diff and commit only the task's files.

## Commands and evidence

Run Maven offline with D:/chatGPT/minecraft/.tools/apache-maven-3.9.11/bin/mvn.cmd, local repository D:/chatGPT/minecraft/.tools/m2 and worktree target/test-tmp. Expect no test failures/errors/skips. Run asset tools with the bundled Python that includes Pillow. Record red/green checks, changes and rulings in target/models-games-ledger.md.

After all four projects, run the independent review and native/CraftEngine runtime probes, create a local package, deploy to the authorized test server with backup/clean stop, and verify the GitHub commit. Report server checks separately from client visual acceptance.
