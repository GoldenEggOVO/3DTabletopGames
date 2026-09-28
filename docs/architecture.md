# Architecture

## Repository layout

- `src/main/java/dev/tabletop3d/`: plugin lifecycle, rooms, menus and physical table rendering.
- `src/main/java/dev/tabletop3d/ui/`: shared Component formatting and label fitting, adapted from 3DCasinoGames.
- `src/main/java/dev/tabletop3d/rules/`: bundled rules and preserved upstream sources.
- `src/main/resources/`: metadata, configuration, language, Dialog layouts and notices.
- `src/test/`: Java behavior tests. `tests/`: Python migration tests.
- `tools/standalone-probe/`: isolated loopback server probe.
- `tools/`: migration and local packaging utilities.
- `docs/`: installation, migration, language, architecture and verification guides.
- `target/`, `deliverables/`: ignored generated artifacts and local acceptance packages.

## Rules, rooms and rendering

`Room` stores authoritative state, seats, seed and action history. `Tabletop3D` owns local room/occupancy management. `GameWorld` routes physical input; `TableView` displays the state and does not apply game moves. `TableModels` describes native BlockDisplay parts. Boards also use map surfaces and TextDisplay labels.

Connect Four uses five strips per vertical disc, rack supports, and a 12-tick vertical drop with a small contact bounce. Restored discs spawn directly at their rule positions. Reversi uses four blocks per two-sided piece; a 10-tick rotation changes the upper face without replacing the entities. Rapid updates start from the current visual angle and converge on the newest owner. No animation state is persisted.

Both renderers keep their own plugin lifecycle. The text utilities are included in the Tabletop JAR; no dependency on Casino or a shared runtime plugin is introduced. Room data stays at schema 1.

## Dialog and text

`GameMenus` owns single-use sessions bound to player, world and expiry. `GameMenuLayouts` binds only server actions to editable layouts. `BoardWindow` renders native Paper Dialog; permissions are checked again on callbacks.

`Language.component(key, pairs)` formats named UI messages through `ui.MessageText`. Dynamic arguments are Components/literal values. `Language.text` remains the compatibility boundary for old source phrases and rule descriptions. `ui.LabelLayout` fits table text without discarding the lower instruction lines.

## Extension boundaries

AuthMe and ServerMenu command forwarding are optional. Rules, occupancy and saves do not use ServerGames services. Model work in this batch is limited to Connect Four and Reversi; other game geometry remains intact.
