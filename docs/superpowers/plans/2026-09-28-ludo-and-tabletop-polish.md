# Ludo and tabletop polish implementation plan

> Implement inline with the executing-plans workflow; user explicitly authorized implementation and selected automatic first deployment.

**Goal:** Replace new Aeroplane tables with accessible Ludo tables and improve physical piece readability and move feedback.

**Architecture:** Add a deterministic Ludo BoardGame under `ludo`. Keep `aeroplane` exclusively for replay/restoration of existing rooms. Reuse room, Dialog, model lifecycle and input gates. No external plugin dependencies.

**Tech stack:** Java 25, Paper Display entities, native Dialog, YAML language files, JUnit/MockBukkit.

## Scope and decisions

- New catalog/commands expose Ludo, 2–4 seats. Existing flight records retain their original rules, geometry, seed and history; no destructive data migration.
- Four pawns per player. At game start only, pawn 1 starts on track; other pawns require six. Six grants another roll. Capture returns opponents to yard. No blocking, flight jumps, rebound or triple-six punishment. Exact finish, first player with four finished wins.
- References: Nintendo's 51 Worldwide Games catalog and update notes; Clubhouse Games Ludo guide by CyricZ. This is a documented fixed rules profile, not a claim of reproducing every Switch option.
- Models remain original vanilla geometry: cross board, simple rounded pawn heads and bases, more rounded disc silhouettes. Default English and editable YAML.
- Clicking an eligible Ludo pawn commits its single dice-determined move; hovering previews destination. Existing chess-style games retain explicit source/destination selection, with capture targets visible around occupied cells.
- No Casino/shared rules edits, production deployment or Release.

## Review focus

- Old `aeroplane` rooms must replay unchanged and cannot silently become Ludo.
- Opposing colors in 2-player games must match seats, pawn colors and routes.
- Capture, exact finish and six/no-move transitions must not stall the turn.
- Private previews must not mutate state, leak to spectators or survive invalidation/close.
- Dice animation, stale callbacks, permissions and duplicate packet gates must still apply.

## Tasks

- [x] Rules: create `rules/LudoGame.java`, register factory; test initial positions, six deployment, capture, exact finish, victory, deterministic replay and invalid actions.
- [x] Integration: update catalog, capacities, menus/languages, source action parsing, artwork, models and dice rendering; retain flight restoration. Test both new and legacy rooms.
- [x] UX: direct pawn moves and private source/landing hints, clearer capture targets, rounded discs; test entity reuse, cleanup and state gates.
- [x] Local verification: 191 Java tests, four Python tests, two three-boot runtime checks, 12-room snapshot restoration, final-JAR continuous-play check and source-geometry model previews. Rules and migration are documented.

Delivery uses `tools/package_standalone.py` with matching final-JAR receipts. The local package records its source commit; GitHub Actions checks that commit after sync. No Release is authorized. Connected-client visual and interaction acceptance remains with the maintainer.

Independent review found no critical or important issues. Two minor presentation issues were fixed and regression-tested: custom legacy-flight translations retain their override, and stacked Ludo pawns explicitly prompt a choice instead of promising an immediate move.
