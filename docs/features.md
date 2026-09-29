# Feature inventory

| Area | Supported behavior | Verification |
| --- | --- | --- |
| Games | Xiangqi, Chess, Gomoku, Ludo, Chinese Checkers, Draughts, Reversi, Go 9/13/19, Connect Four, Last Card, six Mahjong profiles; legacy Aeroplane recovery | Rule test suites |
| Rooms | Paginated browsing, create, join, seats, readiness, bots, spectate, return, leave | Room, occupancy and menu tests |
| Recovery | Seed/history replay, world anchors, offline seats, migration | Recovery tests and isolated three-boot probe |
| Agreements | Undo negotiation and rematches | RoundActions tests |
| Menus | Native Dialog, rules/help, move controls, YAML layouts, one-use world/player-bound callbacks | Window/NativeMenu tests and runtime probe |
| Input | Ray targeting, legal-move pointers, private landing previews, direct board/menu moves | Geometry and input tests; client acceptance required |
| Models | Connect Four drops, Reversi flips, reusable Go dead marks, opposing knight headings | TableView tests and real Display probe |
| Language | Default English, custom YAML, named UI messages, legacy translations | Language/MessageText/LabelLayout tests |
| Access | Use/admin permissions, optional AuthMe, contextual Tab | Permissions and completion tests |
| Game setup | Friends/bots, player count, regional rules, persistent per-room settings | GameOptions/RoomOptions/MenuFlow tests |
| Private hands | Owner-only faces, public backs, exposed melds, discard details and scores | HandTable/HandMenu tests and real server visibility metadata |
| Dice | Separate stand, travel/bounce/tumble, final face, action lock and clean removal | DiceMotion/DiceTray/DiceRollGate tests and runtime probe |

Xiangqi and legacy Aeroplane use the documented bundled variants. Go uses area scoring, 7.5 komi and negotiated dead-stone marking. Bots are basic assistance. Yacht remains disabled. See [game modes](game-modes.md) and [Mahjong house rules](mahjong.md) for the precise new rules and their reference differences.

See [verification](verification.md) for current evidence and the outstanding client checklist.
