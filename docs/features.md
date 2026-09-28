# Feature inventory

| Area | Supported behavior | Verification |
| --- | --- | --- |
| Games | Xiangqi, Chess, Gomoku, Aeroplane, Chinese Checkers, Draughts, Reversi, Go 9/13/19, Connect Four | Rule test suites |
| Rooms | Paginated browsing, create, join, seats, readiness, bots, spectate, return, leave | Room, occupancy and menu tests |
| Recovery | Seed/history replay, world anchors, offline seats, migration | Recovery tests and isolated three-boot probe |
| Agreements | Undo negotiation and rematches | RoundActions tests |
| Menus | Native Dialog, rules/help, move controls, YAML layouts, one-use world/player-bound callbacks | Window/NativeMenu tests and runtime probe |
| Input | Ray targeting, legal-move pointers, private landing previews, direct board/menu moves | Geometry and input tests; client acceptance required |
| Models | Connect Four drops, Reversi flips, reusable Go dead marks, opposing knight headings | TableView tests and real Display probe |
| Language | Default English, custom YAML, named UI messages, legacy translations | Language/MessageText/LabelLayout tests |
| Access | Use/admin permissions, optional AuthMe, contextual Tab | Permissions and completion tests |

Xiangqi and Aeroplane use the documented bundled variants. Go uses area scoring, 7.5 komi and negotiated dead-stone marking. Bots are basic assistance. Consult the in-game rule panel for details. Yacht source is historical and disabled; no cards or Mahjong are enabled.

See [verification](verification.md) for current evidence and the outstanding client checklist.
