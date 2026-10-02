# board-and-playing-card-models Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Add resource-pack models and audience layers for all catalog board games, plus original standard playing cards.

**Architecture:** Reuse TableGeometry coordinates, TableAudience visibility, and native meshes. Keep model generation and the runtime model catalog aligned.

**Tech Stack:** Java 25, Paper 26.2, JUnit Jupiter, Python/Pillow, CraftEngine public API.

**Spec:** ../specs/2026-10-02-board-and-playing-card-models-design.md

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

**Files:** Modify tools/build-resource-pack.py, tools/test_resource_pack.py, src/main/java/dev/tabletop3d/{CraftEngineModels,TableView,TableAudience,TabletopPack}.java; create PackedBoardModels.java and tools/build_board_models.py; test PackedBoardModelsTest.java and TableAudienceTest.java.

**Interfaces:** PackedBoardModels.supported(String), table(String), piece(String, Cell, Map<String,String>); one model per furniture assembly or token. TableAudience.add(Entity, boolean) covers native map frames.

- [ ] Write focused behavior tests: tools/test_resource_pack.py verifies new tables, chess types, all 54 playing faces, opaque backs, open rack holes, and resolved textures. PackedBoardModelsTest checks every supported kind and all colored-piece mappings. Existing Reversi/Connect Four animation tests must remain green.
- [ ] Run the focused tests and record the expected failure before product changes.
- [ ] Implement the named components and integrate table controls, persistence, bots and translations required by the specification.
- [ ] Run focused tests, then the Java suite; asset tasks also run Python contract tests.
- [ ] Review the scoped diff and commit only the task's files.

## Commands and evidence

Run Maven offline with D:/chatGPT/minecraft/.tools/apache-maven-3.9.11/bin/mvn.cmd, local repository D:/chatGPT/minecraft/.tools/m2 and worktree target/test-tmp. Expect no test failures/errors/skips. Run asset tools with the bundled Python that includes Pillow. Record red/green checks, changes and rulings in target/models-games-ledger.md.

After all four projects, run the independent review and native/CraftEngine runtime probes, create a local package, deploy to the authorized test server with backup/clean stop, and verify the GitHub commit. Report server checks separately from client visual acceptance.

