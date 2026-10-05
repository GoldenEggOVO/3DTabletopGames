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

Fixed labels share immutable Components within the active catalogue. Parameterized text is rendered separately, so player names and current values never enter the fixed-label cache. A successful language reload swaps the catalogue and cache together; a rejected reload keeps both unchanged.

## Extension boundaries

CraftEngine is the optional resource-model bridge. Vanilla rendering and native Paper Dialog menus run independently. No AuthMe, Geyser, Casino, ServerGames or ServerMenu runtime dependency is required.

`CraftEngineModels` checks the current model definitions every two seconds. Unchanged definitions reuse their built item; a replaced definition rebuilds only that item, and a missing definition disables packed rendering until registration completes. Returned items are independent copies. Reflection stays isolated to the optional public API bridge.

`TableAudience` keeps immutable native, packed and shared viewer sets. On a viewer change it sends only the necessary show/hide updates for that player's changed layers. Other viewers and shared parts keep their current visibility.

The bundled chess rules no longer maintain unused Polyglot opening-book hashes, board event observers or deprecated FEN aliases. Position history and internal move backups remain necessary for repetition detection and legal-move analysis.

## Reading the rendering and input code

Start with `GameWorld.worldClick`: it validates the player and table, handles the menu gesture and then dispatches to `pickCell`. Yacht controls, Mahjong controls and private hand cards have separate handlers; board source/destination selection remains in the shared path. Keep this dispatch order when changing input behavior.

`TableView.sync` coordinates revision checks, reusable tokens, token moves/creation and the final title update. `reuseTokens` retains eligible existing pieces; `moveOrCreateTokens` handles the remaining pieces. Furniture construction is separated into the table frame, Connect Four rack and board map surface. These helpers preserve entity creation order and the existing animation cadence.

`BoardBots.choose` dispatches to named per-game policies without changing their random choices. `TableArt` draws static board pixels; `HandModels.glyphStrokes` contains Mahjong artwork coordinates, separate from stroke construction. `TableModels` builds piece meshes. Geometry values remain in those drawing methods so the artwork can be read and adjusted without tracing a new configuration layer.

Use descriptive player, material, graphics and model-part names, four-space indentation and explicit control-flow blocks. Coordinates such as `x`, `y` and `z` retain their conventional names. Player labels, action IDs, map-index keys and saved state are independent of these private implementation names.

## Input validation and hover state

Room callbacks recheck live registry identity before backend mutations. Piece-source menus retain their rendered revision; stale selections rebuild from current legal actions. A leave confirmation is tied to the room it displayed. This does not change persisted room identity or schema.

Table geometry remains fixed, while hover labels use the current cells cached at each board revision. Connect Four normalizes hover to a column, reuses four private marker entities within unchanged state and resends unchanged feedback at most every ten pointer updates. Go dead marks are separate removable parts of the existing stone. Disc meshes remain three parts; no model entity budget increase is required for that change.

## Card faces, board maps and sounds

`HandModels` owns the active native Mahjong artwork and pip coordinates. Card faces use signed head textures through `PlayingCardHeads` and `ColorEightHeads`; the retired `HandArt` raster renderer is removed. Public card backs remain two native blocks.

`TableMaps` creates the static board map tiles once per world and game, caches immutable item lists, and saves their IDs under the existing `v1.<world UUID>.<game>.<tile index>` paths. The index and room schemas remain unchanged.

`TableSounds` classifies committed actions before playback. Doudizhu combinations and Liars Bar challenges have separate cue selection methods. Playback chooses Mahjong-specific resources first, then shared resources, with native sounds for players without the pack. Rendering and history replay do not trigger sounds.
