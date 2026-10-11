# 3DTabletopGames

[English](README.md) | [简体中文](README.zh-CN.md)

Playable 3D tabletop games for Minecraft, with physical pieces, multiplayer rooms, private hands and native Dialog menus.

Requires **Paper / Purpur 26.2 and Java 25**. Runs independently without client mods or other custom plugins. CraftEngine and the tabletop resource pack are optional. Current source: **1.10.38-SNAPSHOT**, an unpublished development build.

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
| `rendering.mode` | `auto` | Mixed rendering with enabled CraftEngine, otherwise vanilla; optional overrides: `vanilla`, `mixed`, `resource-pack` |
| `rendering.resource-pack.source` | `local` | Editable local assets, or `external` ZIP delivery |
| `rendering.resource-pack.url` | Empty | External-mode URL for the ZIP matching the JAR |
| `rendering.resource-pack.local.port` | `8137` | Local resource-pack download port |
| `rendering.resource-pack.local.public-url` | Empty | Optional public base URL; empty uses the player's connection hostname and download port |

Set `language: zh_CN` for Simplified Chinese. Edit the generated `languages/en_US.yml` or `languages/zh_CN.yml`, or copy a catalogue to another language name. Preserve message keys and placeholders such as `{player}`. MiniMessage colors and legacy color codes are supported; language files cannot define callbacks or commands.

Run `/3dtabletop reload-language` after editing translations or the selected language. Other settings require a restart. Missing entries in the standard English and Chinese catalogues use their bundled translations without rewriting your files. Other languages fall back to English. Invalid reloads keep the previous language. Menu layouts are defined in code, while captions and descriptions come from language files. An obsolete `menus/` directory can be removed after backup.

`rooms.json` stores rooms, table locations and action history. `table-map-ids.yml` stores the plugin's map allocations. Preserve both when upgrading; do not edit generated records while the server is running. Legacy `returns` coordinates are retained for compatibility; the current spectator flow no longer records unused return locations.

## Optional resource pack

1. Install CraftEngine when using resource models. During plugin loading, bundled registrations fill missing files under the engine's data directory at `resources/tabletop3d/`, normally `plugins/CraftEngine/resources/tabletop3d/`. Existing files are preserved; no manual registration archive extraction is needed.
2. Set the following section in `plugins/3dtabletop/config.yml`, then restart:

```yaml
rendering:
  mode: auto
  resource-pack:
    source: local
    local:
      bind-address: '0.0.0.0'
      port: 8137
      public-url: ''
```

3. Allow players to reach the download port, TCP 8137 by default. For a proxy, port mapping or HTTPS, set `public-url` to the public HTTP(S) base URL, such as `https://games.example.org`. Reverse proxies must forward `/tabletop3d/` to the local download service. Restart after changing the bind address, port or public URL.

When local hosting is first enabled, the plugin extracts its bundled defaults into `plugins/3dtabletop/resource-pack/` and generates `plugins/3dtabletop/resource-pack.zip`. Plugin updates fill missing default files and preserve existing custom images. The download endpoint only serves generated archives; it cannot browse plugin data or raw files.

**Routine asset changes need no compilation, Python, Maven or replacement JAR:**

1. Replace the image under the same name and path, such as `assets/tabletop3d/textures/item/color_eight/color_eight_blue_2_face.png`, in the server's resource folder.
2. Run `/3dtabletop reload-pack` as an administrator or from the console.
3. The plugin validates and packages assets in the background, updates the download hash and URL, then requests the new pack from online players who enabled it. Invalid images or JSON retain the previous pack. Unchanged content is not resent. Rooms and player preferences are preserved.

Existing configurations that only contain `url` keep external delivery. Add `source: local` explicitly and restart to switch to this workflow. External delivery accepts `source: external` and the matching delivered ZIP's `url`, using the JAR's bundled checksum and an existing HTTP(S) host.

The default `auto` detects CraftEngine at startup: enabled selects `mixed`, while absent or disabled selects `vanilla`. Existing explicit mode settings remain unchanged; set an older configuration to `auto` to opt in. Restart after installing or removing CraftEngine to detect it again.

`vanilla` needs neither CraftEngine nor the pack. `mixed` offers a per-player pack switch and uses vanilla rendering while the pack is unavailable. `resource-pack` requires successful pack loading and ready model registrations before play. These explicit modes remain available to override automatic selection. Other plugins' packs are not removed or replaced. Game logic, new resource identifiers and native head textures still require a plugin update; this reload command handles client-pack textures, models and sounds.

## Updating

Stop the server and back up the complete plugin data directory before replacing the JAR. Preserve current configuration, custom translations, rooms and other plugins. Current schema 1 room files remain supported; retired game formats are not automatically converted. Validate a backup against the candidate JAR before updating:

```sh
java -cp target/3dtabletop-1.10.38-SNAPSHOT.jar dev.tabletop3d.room.RoomReplayVerifier /backup/3dtabletop/rooms.json
```

When resource rendering is enabled, update the matching ZIP and its URL as well. After startup, check restored rooms, menus and table actions before removing the backup.

## Source layout

- `src/main/java/dev/tabletop3d/`: rules grouped by game; shared room, menu, rendering and persistence code grouped by responsibility.
- `src/main/resources/`: plugin metadata, configuration defaults, translations and bundled notices/textures.
- `src/test/`: automated Java checks.
- `resource-pack/pack.mcmeta` and `resource-pack/assets/`: complete, editable Minecraft pack files.
- `resource-pack/licenses/`: notices shipped inside the client pack.
- `resource-pack/craftengine/`: default CraftEngine item registrations bundled in the JAR and installed automatically.
- `resource-pack/native-cards/`: approved vanilla head source images, independent of the client pack.
- `resource-pack/originals/`: original artwork, SVGs and recordings retained for attribution and editing.
- `resource-pack/sources.json`: asset provenance, hashes and license notices.
- `tools/native_heads/`: native card skin slicing and optional MineSkin texture generation.
- `tools/packaging/`: source, verified standalone and CraftEngine delivery packaging.
- `tools/preview/`: offline native Mahjong and resource model previews.
- `tools/verification/`: pack geometry statistics and isolated server/client-parser probes.
- `tools/tests/`: offline Python regression checks.
- `target/`: generated JARs, packs, previews and test outputs; not source.
- `docs/THIRD_PARTY.md`: third-party attribution and source notices.

## Build

Updates are built, tested and packaged locally before source is pushed. GitHub Actions is disabled; pushing does not run remote builds or publish a Release.

On Windows, double-click `build.cmd` at the repository root. It locates Python 3.12+, JDK 25 and Maven 3.9+, packages the resource pack, compiles and runs the Java tests, then verifies that the ZIP and JAR checksums match. Success prints both output paths; failure stops the build and leaves the error visible in the window.

The launcher prefers Maven 3.9.11 under the workspace's adjacent `.tools/` directory. When that directory also contains an `m2/` cache, the build reuses it offline. Elsewhere, provide Maven through `PATH` and JDK 25 through `JAVA_HOME` or `PATH`. Outputs remain under `target/`; running the launcher does not deploy them to a server.

Use **JDK 25 and Maven 3.9+**:

```sh
python tools/packaging/package_resource_pack.py
mvn -B -ntp package
```

Maven runs Java tests and writes results to `target/surefire-reports/`. Install the shaded `target/3dtabletop-*.jar`.

The pack files are maintained directly; models and audio are not generated during builds. Packaging needs **Python 3.12+** and its standard library. Package the assets before compiling the plugin so the JAR contains the matching checksum:

```sh
python tools/packaging/package_resource_pack.py
mvn -B -ntp package
python -m unittest discover -s tools/tests -t . -p "test_*.py"
python -m compileall -q tools
python tools/packaging/package_source.py
```

Outputs include `target/tabletop-resource-pack.zip`, `target/resource-pack-manifest.json`, the plugin JAR and `target/3dtabletop-source.zip`. Identical pack files produce an identical ZIP in the same Python environment. The client pack ZIP includes only `pack.mcmeta`, `assets/` and `licenses/`. Maven bundles CraftEngine registrations directly from their source directory into the JAR; they are excluded from the client ZIP. The source ZIP contains Git-tracked files only.

After a passing three-boot check, use `python tools/packaging/package_standalone.py <native-receipt.json>` for a native delivery, or `package_craftengine.py <native-receipt.json> <craftengine-receipt.json>` for a resource-pack delivery. Both receipts must match the current JAR. Optional `--soak <receipt.json>` and `--snapshot <receipt.json>` retain continuous-game and recovery checks; `--evidence <log-file>` adds current logs to a resource-pack delivery. Outputs go to `deliverables/`; legacy logs and previews are not required.

Asset and head-export tests need Pillow. Optional native head upload needs its own MineSkin credentials; neither ordinary packaging nor the server needs them. Resource model previews: `python tools/preview/resource_pack.py` (Pillow required).

### Editing the resource pack

Edit model JSONs under `resource-pack/assets/tabletop3d/models/item/`, item definitions under `items/`, images under `textures/item/` and OGG samples under `sounds/`. Sound events are in `assets/tabletop3d/sounds.json`. To add an item, match its definition path to CraftEngine's `item_model` and its `model.model` to the model path. Keep the logical item ID in the CraftEngine registration and `CraftEngineModels.IDS` consistent. Run the packager and tests after editing, then compile and ship the matching JAR and ZIP together. Packing does not redraw, resize or transcode any source file.

After editing images, Windows users can double-click `build.cmd` to rebuild. Alternatively, run these commands from the repository root, proceeding only when each step succeeds. They require Python 3.12+, JDK 25 and Maven 3.9+:

```sh
python tools/packaging/package_resource_pack.py
mvn -B -ntp package
```

The first step creates `target/tabletop-resource-pack.zip` and updates its checksum. The second runs Java tests and embeds both the default ZIP and its checksum into `target/3dtabletop-<version>.jar`. This builds source deliveries. Servers with local hosting enabled edit their server resource folder and run `/3dtabletop reload-pack` for routine asset changes. External hosting still uses the ZIP matching the JAR. Editing an existing PNG does not require changing CraftEngine registrations.

| Directory or file | How to customize it |
| --- | --- |
| `assets/`, `pack.mcmeta` | Edit textures, models, item definitions, sounds and pack metadata; update references when renaming or moving files |
| `craftengine/` | Keep logical item IDs and client resource paths aligned; copy registration changes to the server's CraftEngine configuration |
| `native-cards/` | Native head-card artwork; PNG edits also require regenerating signed head textures and compiling the plugin, as ordinary resource-pack packaging does not update heads |
| `originals/` | Source material backups; edits here do not automatically update the finished files under `assets/` |
| `licenses/`, `sources.json` | Attribution, origins and checksum records; keep them accurate when replacing materials |

JSON files use two-space indentation for direct editing.

Image names identify the game and part, for example `poker_spades_9_face.png`, `color_eight_yellow_6_face.png`, `xiangqi_red_pawn_face.png` and `mahjong_table_cloth.png`. `top`/`bottom` identify upper and lower surfaces, `front`/`rear` distinguish face orientations, and `shared_` marks textures reused by multiple models.

Under `textures/item/`, game-specific images live in `mahjong/`, `xiangqi/`, `chess/`, `connect_four/`, `gomoku/`, `go/`, `reversi/`, `checkers/` (Chinese Checkers), `draughts/`, `ludo/`, `color_eight/`, `doudizhu/`, `liars_bar/`, `texas_holdem/` and `yacht/`. Shared images are stored once:

| Shared directory | Contents |
| --- | --- |
| `shared/materials/` | Basic wood, card body and concrete textures |
| `shared/playing_cards/` | Card faces and backs used by the playing-card games |
| `shared/table_parts/` | Table bottoms and pedestal textures used by multiple tables |
| `shared/pieces/` | Go/Gomoku stones and gold decorations used by multiple board games |
| `shared/dice/` | Six face textures for the die shared by Ludo and Yacht Dice |

Models and item definitions follow the same game directories under `models/item/<game>/` and `items/<game>/`. Shared playing cards live in `shared/playing_cards/`, Go/Gomoku stones in `shared/pieces/`, and the Ludo/Yacht die in `shared/dice/`. For example, `items/mahjong/mahjong_m1.json` has the client ID `tabletop3d:mahjong/mahjong_m1`, referenced by CraftEngine's `item_model`. Its `model.model` points to `tabletop3d:item/mahjong/mahjong_m1`. Java code and CraftEngine's custom item keys retain the logical ID `tabletop3d:mahjong_m1`.

### Finding the code

All plugin packages are under `src/main/java/dev/tabletop3d/`; `src/test/java/` follows the same structure.

| Change | Start here |
|---|---|
| Startup and configuration | `Tabletop3D.java` |
| Commands and Tab suggestions | `command/` |
| Game rules and legal actions | `rules/<game>/`, created by `rules/GameFactory.java`; IDs, order and player counts in `rules/GameCatalog.java` |
| Room membership, turns and persistence | `room/Room.java`, `RoundActions.java`, `RoomStore.java` |
| Clicks, hovering and table access | `interaction/GameWorld.java` |
| Dialogs and menu buttons | `menu/GameMenus.java`, `BoardWindow.java`, `PokerRaiseWindow.java` |
| Board display and viewer privacy | `render/TableView.java`, `TableAudience.java` |
| Card, Mahjong and dice layouts | `render/cards/`, `render/mahjong/`, `render/dice/` |
| Resource-pack loading and CraftEngine models | `resource/` |
| Bots, sounds and translations | `bot/`, `audio/`, `text/`; player text in `src/main/resources/languages/` |

The normal action flow is input → room validation → game rules → room save and view update. Game rules do not depend on Bukkit; rendering does not determine whether a move is legal.

`tools/verification/standalone-probe/run_standalone.py` checks isolated startup, gameplay and restart recovery. Supply `--server-dir` with a prepared Purpur 26.2 cache and already accepted EULA; use `--maven-repo` for a custom dependency cache (the default is `~/.m2/repository`). Optional CraftEngine arguments enable resource-model checks. `run_soak.py` checks repeated gameplay and entity cleanup. Keep receipts and logs in local build outputs.

Native head export: `python tools/native_heads/build-native-card-heads.py --output target/native-heads`; Color Eight: `python tools/native_heads/build-color-eight-heads.py --output target/color-eight-heads`. Only `generate-native-card-heads.py` contacts MineSkin, and only when explicitly run with a local `--api-key-file`; it is not part of normal builds or runtime.

Automated tests and previews do not replace client acceptance. Check private hands from multiple seats and a spectator, pack switching and failed downloads, hover/click alignment, dice movement, sounds, restart recovery and frame-time performance in Minecraft.

## License

Licensed under [GPL-3.0-or-later](LICENSE). See [third-party materials](docs/THIRD_PARTY.md) and [asset sources](resource-pack/sources.json) for separate notices and asset terms.
