# 3DTabletopGames

[![Build](https://github.com/GoldenEggOVO/3DTabletopGames/actions/workflows/ci.yml/badge.svg)](https://github.com/GoldenEggOVO/3DTabletopGames/actions/workflows/ci.yml)

[English](README.md) | [简体中文](README.zh-CN.md)

Playable 3D tabletop games for Minecraft, with physical pieces, multiplayer rooms, private hands and native Dialog menus.

Requires **Paper / Purpur 26.2 and Java 25**. Runs independently without client mods or other custom plugins. CraftEngine and the tabletop resource pack are optional. Current source: **1.10.30-SNAPSHOT**, an unpublished development build.

## Install

1. Download a published shaded JAR from [Releases](https://github.com/GoldenEggOVO/3DTabletopGames/releases), or [build from source](#build).
2. Stop the server and back up worlds and `plugins/3dtabletop/`.
3. Put one `3dtabletop-*.jar` in `plugins/`. Do not install `original-*.jar`.
4. Start the server and run `/3dtabletop`. Choose a game, customize its rules, then create a room for friends or bots.

Sneak and right-click anywhere on a table to open its room menu. Choose pieces, cards, dice and scoring cells directly on the table. Mahjong also supports holding the sprint key (Ctrl by default) for a fixed table view; release it to return.

## Games

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

Rooms support readiness, bots, spectating, rematches and restart recovery. Rules lock when a room is created; create another room to change them. Mahjong offers Riichi, Guangdong, Sichuan and Taiwan profiles with explicit house rules. Poker chips and Mahjong scores are match points, with no economy integration. In-game rule descriptions are available through the menu and `/3dtabletop rules`.

Vanilla poker and Color Eight faces use 24 thin player heads for 32×48 artwork, while concealed public backs use two blocks. Signed textures are bundled; no runtime MineSkin API key is needed. Resource-pack cards use one display per card. Only the owner sees a concealed hand's faces.

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

Game IDs: `xiangqi`, `gomoku`, `chess`, `ludo`, `checkers`, `draughts`, `reversi`, `go`, `go9`, `go13`, `connectfour`, `color-eight`, `mahjong`, `yacht`, `doudizhu`, `liars-bar`, `texas-holdem`. `checkers` selects Chinese Checkers; `go` selects 19×19 Go.

Example: `/3dtabletop create connectfour`, then `/3dtabletop bots`. Command creation uses default rules; use the menu to customize rules. Tab completion suggests games, player counts, rooms and legal actions. `3dtabletop.use` defaults to everyone. `3dtabletop.admin` defaults to operators and controls administration and language reloads.

## Configuration and languages

Settings live in `plugins/3dtabletop/config.yml`:

| Key | Default | Purpose |
| --- | --- | --- |
| `language` | `en_US` | Language catalogue under `languages/` |
| `max-rooms` | `12` | Maximum rooms |
| `reconnect-seconds` | `120` | Offline return grace period |
| `idle-room-minutes` | `30` | Idle room timeout |
| `turn-seconds` | `60` | Default turn timeout; games may apply their own limits |
| `sounds.enabled` | `true` | Enable game and interface sounds |
| `sounds.volume` | `1.0` | Volume multiplier from 0 to 1 |
| `rendering.mode` | `vanilla` | `vanilla`, `resource-pack` or `mixed` |
| `rendering.resource-pack.url` | Empty | Direct download URL for the matching resource ZIP |

Set `language: zh_CN` for Simplified Chinese. Edit the generated `languages/en_US.yml` or `languages/zh_CN.yml`, or copy a catalogue to another language name. Preserve message keys and placeholders such as `{player}`. MiniMessage colors and legacy color codes are supported; language files cannot define callbacks or commands.

Run `/3dtabletop reload-language` after editing translations or the selected language. Other settings require a restart. Missing translations fall back to English; invalid reloads keep the previous language. Menu layouts are defined in code, while captions and descriptions come from language files. An obsolete `menus/` directory can be removed after backup.

`rooms.json` stores rooms, table locations and action history. `table-map-ids.yml` stores the plugin's map allocations. Preserve both when upgrading; do not edit generated records while the server is running.

## Optional resource pack

1. Install CraftEngine when using resource models. Extract the matching `craftengine-registration.zip` into the server root. Registration files belong under `plugins/CraftEngine/resources/tabletop3d/`.
2. Host the matching `tabletop-resource-pack.zip` at a direct HTTP/HTTPS download URL. Keep the delivered ZIP unchanged.
3. Set the following section in `plugins/3dtabletop/config.yml`, then restart:

```yaml
rendering:
  mode: mixed
  resource-pack:
    url: 'https://YOUR-HOST/tabletop-resource-pack.zip'
```

`vanilla` needs neither CraftEngine nor the pack. `mixed` offers a per-player pack switch and uses vanilla rendering while the pack is unavailable. `resource-pack` requires successful pack loading and ready model registrations before play. The plugin manages its pack checksum and request identity; only configure the URL. Use the ZIP delivered with the exact JAR. Other plugins' resource packs are not removed or replaced.

## Updating

Stop the server and back up the complete plugin data directory before replacing the JAR. Preserve current configuration, custom translations, rooms and other plugins. Current schema 1 room files remain supported; retired game formats are not automatically converted. Validate a backup against the candidate JAR before updating:

```sh
java -cp target/3dtabletop-1.10.30-SNAPSHOT.jar dev.tabletop3d.RoomReplayVerifier /backup/3dtabletop/rooms.json
```

When resource rendering is enabled, update the matching ZIP and its URL as well. After startup, check restored rooms, menus and table actions before removing the backup.

## Source layout

- `src/main/java/dev/tabletop3d/`: rules grouped by game; shared room, menu, rendering and persistence code grouped by responsibility.
- `src/main/resources/`: plugin metadata, configuration defaults, translations and bundled notices/textures.
- `src/test/`: automated Java checks.
- `resource-pack/textures/`: artwork grouped by game; `native-playing-cards/` contains vanilla head source art.
- `resource-pack/sounds/`: audio grouped by purpose and `events.json` mappings.
- `resource-pack/craftengine/`: custom item registrations.
- `resource-pack/sources.json`: asset provenance, hashes and license notices.
- `tools/`: build, packaging and verification scripts.
- `target/`: generated JARs, packs, previews and test outputs; not source.
- `docs/THIRD_PARTY.md`: third-party attribution and source notices.

## Build

Use **JDK 25 and Maven 3.9+**:

```sh
mvn -B -ntp package
```

Maven runs Java tests and writes results to `target/surefire-reports/`. Install the shaded `target/3dtabletop-*.jar`.

To regenerate the optional pack, use **Python 3.12+**, Pillow and SoundFile/libsndfile. Build Java first to export model geometry, then generate the pack and rebuild the plugin to embed its checksum:

```sh
python tools/build-resource-pack.py
python -m unittest discover -s tools -p "test_*.py"
mvn -B -ntp package
python -m compileall -q tools
python tools/package_source.py
```

Outputs include `target/tabletop-resource-pack.zip`, `target/resource-pack-manifest.json`, previews and `target/3dtabletop-source.zip`. The source archive contains tracked files; runtime worlds, credentials, caches and server binaries do not belong in the repository.

`tools/standalone-probe/run_standalone.py` checks isolated startup, gameplay and restart recovery. Supply `--server-dir` with a prepared Purpur 26.2 cache and already accepted EULA; use `--maven-repo` for dependencies. Optional CraftEngine arguments enable resource-model checks. `run_soak.py` checks repeated gameplay and entity cleanup. Keep receipts and logs in local build outputs.

Automated tests and previews do not replace client acceptance. Check private hands from multiple seats and a spectator, pack switching and failed downloads, hover/click alignment, dice movement, sounds, restart recovery and frame-time performance in Minecraft.

## License

Licensed under [GPL-3.0-or-later](LICENSE). See [third-party materials](docs/THIRD_PARTY.md) and [asset sources](resource-pack/sources.json) for separate notices and asset terms.
