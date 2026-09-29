# Game setup and dice tray implementation plan

> Execution: implement the approved user request in this session; use focused parallel workers for independent rules and dice rendering, then integrate and review. Preserve all existing work.

**Goal:** Select a game, configure mode/room rules, then create it; replace stationary dice with a linked rolling tray.

**Architecture:** Immutable Ludo options belong to each room and its replay factory. Setup menus retain native Dialog and single-use callbacks. A separate DiceTray owns bounded display animation; the server still determines and persists the dice result.

**Stack:** Java 25, Paper/Purpur 26.2, native Display/Dialog, English YAML, JUnit/MockBukkit.

## Confirmed design

- Catalog opens a game setup page, with friends/bots, seat count, game-specific options, rules summary, create/start and a separate room browser.
- Ludo options: first winner/all places, blocking off/on, exact/over-OK finish, automatic first pawn/six required. Default values preserve 1.6.1 play. User explicitly selected single-piece blocking: cannot pass any occupied intermediate cell, may land on occupied destination. No safe-square or two-pawn blockade variant.
- First-pawn automatic deployment remains at game start only; a captured pawn still needs six. This preserves the previously selected behavior and existing replay.
- Options lock when the room is created; players inspect them before readiness. Rematch and undo retain them; old rooms without options get exact legacy defaults. Saved all-place rankings are reconstructed from original history.
- Native menus remain English by default and YAML editable. New setup template is added without overwriting existing custom menus.
- Dice tray is a small separate stand beside dice-based tables, with rim and inset rolling surface. A roll travels, tumbles, bounces, slows and settles showing the authoritative upper face. No external physics engine/resource pack. Native impact sounds follow landings; animation state is transient.
- New placement checks include the tray. Existing table anchors never move; rooms without a saved sideTray flag use a compact side stand that fits the historical spacing. Closed/replaced views remove all tray entities and cancel animation. All action paths respect the rolling gate, including bots/menu/commands.
- Only server-boards and its delivery/tests; no Casino/shared module edits, production or Release.

## User scope additions during implementation

- Extend verified Worldwide Games options to the existing matching games: Gomoku forbidden moves; Chinese Checkers own-piece jumps, other camps and placements; first/second/random host for two-player games. Keep the existing Chinese Checkers geometry and Go/Xiangqi rules.
- Add Last Card (an original 52-card UNO-like game based on the selected reference rules), with first/all places and owner-only hands.
- Add Mahjong profiles: Guangdong, Fuzhou, Sichuan, Qinhuangdao, Taiwan and Riichi, with editable room rules. The user supplied a MahjongCraft JAR strictly as a behavioral reference: read configuration and public option names only; do not copy implementation or assets. Use independent rules and original native models.
- Mahjong foundation owns physical tile identity, bounded wall/replacements, decompositions and serialized response priority. Regional scoring and flow must be tested separately; incomplete profiles cannot be presented as fully implemented.
- Hidden hands never enter BoardGame.cells/publicInfo. HandGame exposes hands only to authorized server code. Public models contain backs/counts; owner faces are hidden by default before spawn and shown only to the owning player. Commands and native callbacks enforce player/seat/revision permissions.
- The rule version, complete chosen options and room owner are persisted. Rematches preserve choices and correctly seat a host who selected second/random. Existing source and runtime data remain usable.

## Tasks

- [x] Rules: LudoOptions and LudoGame overload; tests for path blocking, finish options, all-place ordering/skipped finished seats, default replay compatibility.
- [x] Dice: standalone DiceMotion/DiceTray with transform/reach/face/cleanup tests; no edits to rule/menu files by dice worker.
- [x] Integration: room options serialization, replay/undo/rematch, setup menu and rules/rank text, tray placement and global rolling gate. Tests for old/new restore and stale setup callbacks.
- [x] Automated acceptance: full Java/Python suite, clean runtime restart with old/new data and native menus/tray, source geometry previews. Final evidence is in docs/verification.md. Source sync and the local package use the verified JAR; no Release.

## Review focus

- Finishing first must not stop all-place mode; the last remaining seat receives last place.
- Blocking must account for intermediate cells and a pawn's own home lane, without blocking itself at the source or its legal destination.
- Configuration cannot change after creation, or mutate existing saved history during defaults/migration.
- A roll with no legal move can advance the logical turn while the tray still rolls; no actor may move/roll early through another entrypoint.
- Dice animation must not reveal a wrong settled face, survive view close, overlap an existing board/seat, or replay merely because a revision changed.
