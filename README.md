# 3DTabletopGames

Optional CraftEngine rendering for board games, Mahjong and card games: vanilla, resource-pack, or mixed per-player display. Vanilla remains the default. See [installation](docs/craftengine.zh-CN.md) and [model counts](docs/model-counts.zh-CN.md).

[![Build](https://github.com/GoldenEggOVO/3DTabletopGames/actions/workflows/ci.yml/badge.svg)](https://github.com/GoldenEggOVO/3DTabletopGames/actions/workflows/ci.yml)

[English](README.md) | [简体中文](README.zh-CN.md)

**Playable 3D board games for Minecraft, with physical pieces, multiplayer rooms and native Dialog menus.**

Run independently on **Paper / Purpur 26.2 with Java 25**. Resource packs, client mods and other custom plugins are optional.

## Install

1. Download a published JAR from [Releases](https://github.com/GoldenEggOVO/3DTabletopGames/releases).
2. Stop your server and back up plugin data and worlds. Keep one plugin JAR in `plugins/`; never install `original-*.jar`.
3. Start the server and run `/3dtabletop`. Create a room, invite players or fill seats with bots, then play on the physical table.

The current source is **1.10.30-SNAPSHOT**, an unpublished development build. It adds optional original resource-pack boards and pieces, a shared 54-card artwork catalogue, Doudizhu (3 players), Liar's Bar card mode (2–4 players), and Texas Hold'em (2–6 players). New games use private hands and direct table controls; poker chips are local match points with no economy integration. Vanilla remains usable without CraftEngine or a pack. Existing Mahjong and Color Eight interactions remain available. Local geometry previews and server probes do not replace Minecraft client acceptance. See [new card games](docs/new-card-games.md) and [verification](docs/verification.md).

Vanilla Color Eight uses 24 thin heads per 32×48 face and two blocks per public back; its B-style art, selection and private hands remain intact. Upright card games share a compact 0.003025-block depth step; flat cards retain their existing layers. See [all native table counts](docs/native-table-counts.zh-CN.md). Connect Four support and preview bounds, the chess king cross and knight joints avoid overlapping parts.

Vanilla poker faces use 24 thin player heads in a 4×6 grid for 32×48 artwork. J/Q/K show mirrored zombie, skeleton and creeper heads in Minecraft's original colors; both Jokers show a Wither and the full JOKER label. Public backs use two blocks: a white border and blue inset. Signed Mojang textures are bundled, with no runtime MineSkin API key. Packed cards remain one display each, and the matching delivered resource ZIP remains compatible. Client seams, lighting and FPS still require acceptance. See [native card heads](docs/native-card-heads.zh-CN.md).

This build uses the current `3dtabletop` format. Read [updating](docs/migration.md) before installation. Current configurations and schema 1 room files remain usable. Menu layouts are defined in code.

Public Mahjong tiles now use the same width as hand tiles, and the central wall digits are larger again. Hover privately highlights matching own/public tiles; only the aimed hand tile rises. A draw leaves existing tiles in place and adds the new tile at the far right with a gap. Continuous clicks remain guarded across other seats’ turns, preventing a replacement tile at the same aim from being discarded. Only Mahjong tiles forbidden by kuikae after Chi/Pon dim through display brightness, preserving their materials and patterns; waiting and Riichi selection retain normal brightness. A complete Riichi shape without yaku shows a red No Yaku hint on its actual draw or pending public discard. Choose Chi/Pon/Kan first, then a complete combination. Riichi arms eligible discards; Ron and Tsumo have their own buttons. Dora indicator tiles are inset into the center of each wooden front apron, facing its seat. Rivers fill six tiles left-to-right from the inner row toward the owner. Riichi Dora and red fives receive native enchanted item overlays in owner-only hands and public rivers/melds; hidden Ura Dora stays unmarked. Melds remain lower-right, and corner wind inscriptions stay clear of melds. Holding the sprint key (Ctrl by default) temporarily enters Spectator mode with a private fixed camera. Position and orientation stay fixed, and the player and equipment do not enter the view. This view is read-only; release restores the entry game mode, pose, flight, gravity and invisibility before playing. The eye position keeps the previous 1-block inward and 0.2-block downward adjustment; standing hands and inset indicators may lie behind the camera. Floating round, turn, countdown and last-discard information is raised by 0.4 blocks. Sichuan exchange selection/removal, exchange confirmation and missing-suit choice are available directly at the table. Color Eight uses a 54-card deck, bounded-width parallel hands overlapping along one diagonal, a rotating direction ring and underlined 6/9. Color Eight faces use 24 thin heads in a 4×6 grid for the existing 32×48 B-style artwork; public backs use two blocks; Wild Eight choices are four pure-color round buttons. The native round table uses level adjoining rows inside a circular boundary. Mahjong circles, bamboo joints, the one-bamboo bird, flowers, character tiles and honor inscriptions use native outlines and angled strokes. Unplayable Color Eight cards retain their colors at lower brightness, and hovering lifts the selected card only 0.085 blocks. When neither the deck nor recycling can supply a draw, a playable hand must play rather than pass; an entirely unplayable hand may pass, and timeout selects a legal play.

## Games and features

| Game | Players | Physical presentation |
| --- | --- | --- |
| Xiangqi, Chess | 2 | 3D pieces on a mapped board |
| Gomoku | 2 | Black and white stones |
| Reversi | 2 | Two-sided pieces with flip animation |
| Connect Four | 2 | Upright rack, round stepped discs and column drops |
| Go | 2 | 9×9, 13×13 and 19×19 boards |
| Chinese Checkers | 2, 3, 4 or 6 | Star board and colored pieces |
| Draughts | 2 | 8×8 board with crowned kings |
| Ludo | 2–4 | Cross board, pawn figures, private move previews and a physical die |
| Color Eight | 2–5 | Native round table, overlapping private hands, wild-color buttons, draw/discard piles |
| Doudizhu | 3 | Private multi-card selection, bidding controls, public bottom cards after bidding |
| Liar's Bar | 2–4 | Face-down plays, challenges and match-local roulette |
| Yacht Dice | 2–4 | Five dice, three rolls, keep slots and twelve scoring categories |
| Texas Hold'em | 2–6 | Community cards, betting controls, dealer button and compact chip stacks |
| Mahjong | 4 | Original tile patterns, standing private hands, public rivers and exposed melds |

- Rooms, seats, ready checks, bots, spectating and rematches.
- Saved world anchors, seeds and move history; room recovery after restarting.
- Direct board interaction, legal-move pointers and private hover/Connect Four landing previews.
- Game → basic setup → optional detailed rules → create/start; lobby and playing pages.
- Aim at a Color Eight card to raise only that card above its overlapping neighbors. Right-click the central deck when drawing is legal; newly drawn cards travel into your hand. Initial and recovered hands appear in place.
- Chinese Xiangqi piece inscriptions, including 砲; Ludo pawns without floating numbers and dice without an artificial shadow mesh.
- Four Mahjong profiles: Riichi, Guangdong, Sichuan and Taiwan. Scores are match points only.
- Native Paper Dialog with fixed layouts, editable translations and English by default.
- MiniMessage styling, legacy color compatibility and editable language files.

Rules and variant details: [feature inventory](docs/features.md). Yacht Dice is available for 2–4 players with five dice, three rolls, reversible keep slots and direct scoring. Color Eight follows the documented 54-card shedding rules with original art; the command identifier remains `color-eight`. Regional Mahjong follows documented house rules and is not a drop-in replacement for MahjongCraft.

## Commands and permissions

| Command | Purpose |
| --- | --- |
| `/3dtabletop [menu]` | Open the native menu |
| `/3dtabletop create <kind> [players]` | Create a room |
| `/3dtabletop join <room-prefix>` | Join an open room |
| `/3dtabletop ready` / `bots` | Toggle readiness / fill bot seats as host |
| `/3dtabletop resume` / `leave` | Return to your table / leave the room |
| `/3dtabletop rematch` | Ready for a rematch |
| `/3dtabletop move <action>` | Play a legal rule action, e.g. `drop:3` |
| `/3dtabletop rules [kind]` | Show rules |
| `3dtabletop status` | Inspect room count from the console |

Tab completion provides subcommands, games, player counts, room prefixes and legal actions. `3dtabletop.use` defaults to everyone. `3dtabletop.admin` defaults to operators and controls protected-world administration; it is not a bypass for game rules.

Game IDs, configuration and integration details: [installation](docs/installation.md).

## Languages and menus

Edit `plugins/3dtabletop/languages/en_US.yml` or another complete named catalogue. Select it with `language: <code>` and run `/3dtabletop reload-language`. See [languages](docs/languages.md).

Menus, chat, action hints and room presentation use named keys and literal parameters. Menu layouts are fixed in code; each message is translated separately in `languages/*.yml`. Obsolete `menus` templates are no longer loaded or created. See [languages and text styling](docs/languages.md).

Edit semantic language keys to change captions and styling. Menu ordering and callbacks are maintained in code; retired-format conversion tools are no longer bundled.

## Build and documentation

```sh
mvn -B -ntp package
python -m unittest discover -s tools -p "test_*.py"
python tools/package_source.py
```

Use JDK 25, Maven 3.9+ and Python 3.12+ for tools. The plugin build downloads public dependencies and requires no other local plugin modules. Install the shaded `target/3dtabletop-*.jar`.

- [Installation](docs/installation.md) · [Migration](docs/migration.md) · [Languages](docs/languages.md)
- [Architecture](docs/architecture.md) · [Features](docs/features.md) · [Verification](docs/verification.md)
- [Changelog](CHANGELOG.md) · [Contributing](CONTRIBUTING.md) · [Third-party materials](THIRD_PARTY.md)

Licensed under [GPL-3.0-or-later](LICENSE). Original source notices are retained. Runtime worlds, private configuration, credentials and third-party server binaries are excluded from source packages.

Native display counts and their measurement scope: [model count report](docs/model-counts.zh-CN.md).
