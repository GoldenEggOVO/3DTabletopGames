# 3DTabletopGames

[![Build](https://github.com/GoldenEggOVO/3DTabletopGames/actions/workflows/ci.yml/badge.svg)](https://github.com/GoldenEggOVO/3DTabletopGames/actions/workflows/ci.yml)

[English](README.md) | [简体中文](README.zh-CN.md)

**Playable 3D board games for Minecraft, with physical pieces, multiplayer rooms and native Dialog menus.**

Run independently on **Paper / Purpur 26.2 with Java 25**. No resource pack, client mod, ServerGames, ServerMenu, Casino or KaMenu is required. AuthMe is optional.

## Install

1. Download a published JAR from [Releases](https://github.com/GoldenEggOVO/3DTabletopGames/releases).
2. Stop your server and back up plugin data and worlds. Keep one plugin JAR in `plugins/`; never install `original-*.jar`.
3. Start the server and run `/3dtabletop`. Create a room, invite players or fill seats with bots, then play on the physical table.

The current source is **1.7.0-SNAPSHOT**, a development build with game setup/rule selection, animated dice stands, Last Card and six Mahjong house profiles. Existing rooms retain their rules and positions. It has **not been published as a Release**. See [game modes](docs/game-modes.md), [Mahjong rules](docs/mahjong.md) and [verification](docs/verification.md) for the exact scope and acceptance status.

Upgrading from ServerBoards? Read [migration](docs/migration.md) first. Existing `3dtabletop` configurations, menu layouts and schema 1 room files remain usable.

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
| Last Card | 2–4 | Colored cards, private hands and a shared discard pile |
| Mahjong | 4 | Standing private tiles, public rivers and exposed melds |

- Rooms, seats, ready checks, bots, spectating, undo agreements and rematches.
- Saved world anchors, seeds and move history; room recovery after restarting.
- Direct board interaction, legal-move pointers and private hover/Connect Four landing previews.
- Game → mode/rules → create/start; paginated room browsing and private hand controls.
- Six Mahjong profiles: Riichi, Guangdong, Fuzhou, Sichuan, Qinhuangdao and Taiwan. Scores are match points only.
- Native Paper Dialog with editable YAML layouts and English by default.
- MiniMessage styling, legacy color compatibility and editable language files.

Rules and variant details: [feature inventory](docs/features.md). Historical Yacht sources remain disabled. Last Card is a 52-card variant, not a branded UNO implementation. Regional Mahjong follows documented house rules and is not a drop-in replacement for MahjongCraft.

## Commands and permissions

| Command | Purpose |
| --- | --- |
| `/3dtabletop [menu]` | Open the native menu |
| `/3dtabletop create <kind> [players]` | Create a room |
| `/3dtabletop join <room-prefix>` | Join an open room |
| `/3dtabletop ready` / `bots` | Toggle readiness / fill bot seats as host |
| `/3dtabletop resume` / `leave` | Return to your table / leave the room |
| `/3dtabletop undo` / `rematch` | Request an undo / ready for a rematch |
| `/3dtabletop move <action>` | Play a legal rule action, e.g. `drop:3` |
| `/3dtabletop rules [kind]` | Show rules |
| `3dtabletop status` | Inspect room count from the console |

Tab completion provides subcommands, games, player counts, room prefixes and legal actions. `3dtabletop.use` defaults to everyone. `3dtabletop.admin` defaults to operators and controls protected-world administration; it is not a bypass for game rules. With AuthMe installed, players must also be logged in.

Game IDs, configuration and integration details: [installation](docs/installation.md).

## Languages and menus

Edit `plugins/3dtabletop/lang/en.yml`, or copy it to `lang/<code>.yml` and set `language: <code>` in `config.yml`. Restart after editing. Existing files are preserved and missing entries fall back to bundled English.

Menus, chat, action hints and room presentation use named keys and literal parameters. Legacy `translations` remain supported for rule text and existing menu customizations. Menu layouts live in `menus/*.yml`. See [languages and text styling](docs/languages.md).

## Build and documentation

```sh
mvn -B -ntp package
python -m unittest discover -s tests -p "test_*.py"
python tools/package_source.py
```

Use JDK 25, Maven 3.9+ and Python 3.12+ for tools. The plugin build downloads public dependencies and requires no other local plugin modules. Install the shaded `target/3dtabletop-*.jar`.

- [Installation](docs/installation.md) · [Migration](docs/migration.md) · [Languages](docs/languages.md)
- [Architecture](docs/architecture.md) · [Features](docs/features.md) · [Verification](docs/verification.md)
- [Changelog](CHANGELOG.md) · [Contributing](CONTRIBUTING.md) · [Third-party materials](THIRD_PARTY.md)

Licensed under [GPL-3.0-or-later](LICENSE). Original source notices are retained. Runtime worlds, private configuration, credentials and third-party server binaries are excluded from source packages.
