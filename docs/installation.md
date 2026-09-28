# Installation and configuration

## Requirements

Paper or Purpur **26.2**, Java **25**. Lower versions have not been validated for Tabletop. Casino's wider compatibility range does not apply to this plugin.

Stop the server before replacing the plugin. Back up worlds and `plugins/3dtabletop/`. Keep one `3dtabletop` JAR. Use `/3dtabletop` independently of other plugins. No resource pack is needed.

## Commands and games

See the [command table](../README.md#commands-and-permissions). Game IDs:
`xiangqi`, `gomoku`, `chess`, `aeroplane`, `checkers`, `draughts`, `reversi`, `go`, `go9`, `go13`, `connectfour`.

`go` is 19×19. Example: `/3dtabletop create connectfour` followed by `/3dtabletop bots` to fill empty seats. A move such as `drop:3` uses zero-based column indices. Menu actions recheck player permissions, room state, turn and revision.

## config.yml

| Key | Default | Meaning |
| --- | --- | --- |
| `language` | `en` | Language file name under `lang/`, without `.yml` |
| `max-rooms` | `12` | Maximum rooms |
| `reconnect-seconds` | `120` | Offline return grace period |
| `idle-room-minutes` | `30` | Idle room timeout |
| `turn-seconds` | `60` | Turn timeout before basic bot assistance |

Restart after configuration or language changes. Layout files are read when opening a menu. Preserve custom layouts and language files during updates.

## Optional integrations

AuthMe adds a login gate when present. ServerMenu may provide an external entry by dispatching `/3dtabletop menu`; Tabletop offers a return button when `servermenu:servermenu` is available. Neither ServerMenu nor ServerGames is required.

Old ServerGames 2.0.3 and ServerMenu 0.7.1 forward to `serverboards:boards`, which is no longer registered. Update those forwarders separately. Tabletop does not register the old namespace or a GameCoordinator provider.

## Local acceptance build

Install `3dtabletop-1.4.1-SNAPSHOT.jar` on an isolated server first. Check Connect Four drops, Reversi flips, both viewing sides, custom text, direct clicks and restart recovery. Preserve your backup when comparing with a published build.
