# Game Package Organization

**Goal:** Group current rule implementations, options, helpers and tests by game.

**Scope:** Package/path/import changes only. Keep game IDs, stored room schema, public rules,
translations, model IDs and player interactions unchanged. Reuse the active worktree and execute
the work inline, without agents. Existing authorization permits source sync and isolated test
server updates; no Release.

## Layout

- Keep BoardGame, HandGame, SelectedHandGame, Cell, GameFactory, GameOptions and rule messages
  at rules/ as shared contracts and registration.
- Create connectfour/, xiangqi/, chess/, gomoku/, ludo/, chinesecheckers/, draughts/, reversi/,
  go/, coloreight/, doudizhu/, liarsbar/, texasholdem/ and yacht/; use existing mahjong/.
- Put shared PlayingCard under cards/. Poker evaluators and pots belong to texasholdem/.
- Move MahjongGame into mahjong/ with its existing helpers.
- Move embedded third-party rules into chess/engine/, xiangqi/engine/ and
  chinesecheckers/engine/; preserve original notices and attribution.
- Mirror single-game tests; shared contracts and cross-game tests stay at rules/.
- Rendering and room integration remain shared at the plugin level in this bounded pass.

## Tasks and checks

- [x] Record a complete old/new class mapping; move source and tests, update imports and probes.
- [x] Compare normalized Java bodies before/after to prove rule bodies are unchanged.
- [x] Compile and run all existing tests; resolve package access or stale reflection references.
- [x] Update current architecture and contributor docs with the package map.
- [x] Verify actual native/packed startup and synthetic room restore with the final artifact.
- [ ] Review scoped diff, sync source, package delivery and back up/update the named test server.

## Progress

Initial state: clean worktree at 31bab882; current acceptance baseline 1.10.23-SNAPSHOT.

Moved 65 of 222 Java files. Normalized body comparison against 31bab882 passed; only PlayingCard.identity and PlayingCard.selected gained public access for game package callers. Java: 632 tests, no failures/errors/skips. Python: 39 tests passed. Fresh output directories prevent retired class paths entering the JAR. Native three-boot restore passed for 20 rooms from the previous build.

Packed three-boot restore also passed. A 60-second soak completed 21 replays, with zero remaining owned entities. Languages, head texture catalogues and resource-pack digest match the previous JAR byte-for-byte.
