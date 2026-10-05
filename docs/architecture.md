# Architecture

## Repository layout

- `src/main/java/dev/tabletop3d/`: plugin lifecycle, rooms, menus and physical table rendering.
- `src/main/java/dev/tabletop3d/ui/`: shared Component formatting and label fitting, adapted from 3DCasinoGames.
- `src/main/java/dev/tabletop3d/rules/`: bundled rules and preserved upstream sources.
- `src/main/resources/`: metadata, configuration, language, Dialog layouts and notices.
- `src/test/`: Java behavior tests.
- `tools/standalone-probe/`: isolated loopback server probe.
- `tools/`: resource validation and local packaging utilities.
- `docs/`: installation, migration, language, architecture and verification guides.
- `target/`, `deliverables/`: ignored generated artifacts and local acceptance packages.

## Rules, rooms and rendering

`Room` stores authoritative state, seats, seed and action history. `Tabletop3D` owns local room/occupancy management. `GameWorld` routes physical input; `TableView` displays the state and does not apply game moves. `TableModels` describes native BlockDisplay parts. Boards also use map surfaces and TextDisplay labels.

Connect Four uses five strips per vertical disc, a rack, and a 12-tick vertical drop with a small contact bounce. Restored discs spawn directly at their rule positions. Reversi uses six blocks per two-sided piece; a 10-tick rotation changes the upper face without replacing the entities. Rapid updates start from the current visual angle and converge on the newest owner. No animation state is persisted.

The Tabletop renderer has its own plugin lifecycle. The text utilities are included in the Tabletop JAR; no dependency on Casino or a shared runtime plugin is introduced. Room data stays at schema 1.

`TableView` caches display inputs (board identity/revision, phase, seats, capacity, result and language generation) before building title Components. Dice labels refresh when their state changes. A bounded set contains only currently moving/flipping pieces; completion and removal take pieces out of that set. The existing two-server-tick rendering cadence and animation lengths are unchanged. These changes reduce idle work; they are not a measured server-capacity claim.

## Dialog and text

`GameMenus` owns single-use sessions bound to player, world and expiry. `GameMenuLayouts` binds only server actions to editable layouts. `BoardWindow` renders native Paper Dialog; permissions are checked again on callbacks.

`Language.component(key, pairs)` formats named UI messages through `ui.MessageText`. `RoomText` supplies shared game/seat/phase/roster/outcome presentation. Dynamic arguments are Components/literal values. Current semantic templates are loaded from `languages/`; unknown keys and incompatible placeholders are reported. Styling supports MiniMessage and standard color codes. `ui.LabelLayout` fits table text without discarding the lower instruction lines.

## Extension boundaries

CraftEngine is the optional resource-model bridge. Vanilla rendering and native Paper Dialog menus run independently. No AuthMe, Geyser, Casino, ServerGames or ServerMenu runtime dependency is required.

## Input validation and hover state

Room callbacks recheck live registry identity before backend mutations. Piece-source menus retain their rendered revision; stale selections rebuild from current legal actions. A leave confirmation is tied to the room it displayed. This does not change persisted room identity or schema.

Table geometry remains fixed, while hover labels use the current cells cached at each board revision. Connect Four normalizes hover to a column, reuses four private marker entities within unchanged state and resends unchanged feedback at most every ten pointer updates. Go dead marks are separate removable parts of the existing stone. Disc meshes remain three parts; no model entity budget increase is required for that change.

## Card faces, board maps and sounds

`HandModels` owns the active native Mahjong artwork and pip coordinates. Card faces use signed head textures through `PlayingCardHeads` and `ColorEightHeads`; the retired `HandArt` raster renderer is removed. Public card backs remain two native blocks.

`TableMaps` creates the static board map tiles once per world and game, caches immutable item lists, and saves their IDs under the existing `v1.<world UUID>.<game>.<tile index>` paths. The index and room schemas remain unchanged.

`TableSounds` classifies committed actions before playback. Doudizhu combinations and Liars Bar challenges have separate cue selection methods. Playback chooses Mahjong-specific resources first, then shared resources, with native sounds for players without the pack. Rendering and history replay do not trigger sounds.
