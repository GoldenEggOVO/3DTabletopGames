# Board and playing-card models

Date: 2026-10-02. Parent: [models and card games](2026-10-02-models-and-card-games-design.md).

## Appearance and coverage

Use original pixel textures, solid dark wooden rims, readable colors, and softened outer shapes. Keep the current board footprint, grid spacing, seating, and click positions. Decorations must not cover legal-move highlights or labels.

| Game | Resource-pack furniture | Pieces |
|---|---|---|
| Chess | Wooden table with a contrasting 8x8 board | Light and dark pawn, knight, bishop, rook, queen, king; distinct silhouettes |
| Connect Four | Vertical rack with open holes, supporting feet, and a low base | Red and yellow round discs; preserve drop animation |
| Xiangqi | Warm wooden board, river, and palace lines | Round engraved pieces with readable red/dark characters |
| Gomoku and Go | Low wooden board with grid and appropriate star points | Rounded black/white stones; cover Go 9, 13, and 19 |
| Reversi | Muted green board on a wooden table | One double-sided disc model; preserve flips |
| Draughts | Contrasting checkerboard | Light/dark discs and visibly crowned kings |
| Chinese Checkers | Six-camp star board | Six colored rounded pieces |
| Ludo | Colored camp/path board | Four colored pawns, die, and existing position markers |

Chess pieces remain genuine 3D meshes. Connect Four holes use geometry rather than an opaque picture of holes. Board markings and ornamental details use textures rather than extra display entities.

Existing Mahjong and Color Eight designs are the baseline, not replacement targets. Hidden restore-only game providers do not gain new catalogue entries in this project.

## Shared playing cards and new furniture

Provide 52 standard faces, two distinguishable jokers, and a consistent opaque card back. Use cream borders, a dark outline, readable corner ranks, familiar red/black suits, and original pixel illustrations. Card faces, backs, and edges are part of a single model. The front, back, top, and bottom orientations are explicit and covered by asset checks.

Doudizhu uses a round green-felt table with three seats and space for bottom cards and played groups. Liar's Bar uses a compact round dark-wood table with a central declared-rank plaque, face-down play area, and an original small roulette indicator. Texas Hold'em uses a rounded oval felt table with six seat positions, five community-card spaces, dealer/blind markers, and pot space.

Native rendering must also provide readable standard playing cards and the new tables. Its artwork may be simpler but must preserve rank/suit identification, privacy, selection, and usable click areas.

## Rendering boundary

Extend the existing `TableAudience` behavior to board furniture and tokens. Each public table has native and packed layers only when there are viewers for that layer. Viewers see the appropriate layer; zero viewers remove its entities. Interaction positions remain shared and do not depend on a particular visible display entity.

Target one ItemDisplay per resource-pack piece/card and one furniture model per static assembly where practical. A multipart mesh is still one display entity. Common highlights, text, and interaction entities are counted separately. Preserve animation transforms across updates; a model refresh must not reset an in-flight drop or flip.

Keep per-seat private cards separate from public backs. A spectator or opponent must never receive a private face during spawning, switching modes, or reconnecting. Remove all layer registrations and private entities when a room ends.

Use descriptive model IDs and a catalog with explicit categories. Extend the build generator, CraftEngine registrations, standalone ZIP, and validators together. Avoid retaining the old literal total of 111 as a requirement. Missing new assets disable resource-pack entry with a useful diagnostic rather than displaying untextured items.

## Acceptance

- Catalogue games render in all three modes, including mixed viewers at one table.
- Packed chess has recognizable pieces; Connect Four remains transparent through holes.
- Native and packed moves use the same click targets and legal actions.
- Reversi flips, Connect Four drops, Ludo moves, and selected-cell highlights retain current timing and behavior.
- Model/texture references and all registrations resolve; no namespace collisions occur.
- Track entity counts for initial and busy tables, removal when a layer becomes unused, and repeated mode switching.
- Provide model previews and verify actual placement/orientation on the test server. Client screenshots are required for final visual acceptance.
