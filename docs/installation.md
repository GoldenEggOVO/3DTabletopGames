# Installation and configuration

## Requirements

Paper or Purpur **26.2**, Java **25**. Lower versions have not been validated for Tabletop. Casino's wider compatibility range does not apply to this plugin.

Stop the server before replacing the plugin. Back up worlds and `plugins/3dtabletop/`. Keep one `3dtabletop` JAR. Use `/3dtabletop` independently of other plugins. No resource pack is needed.

## Commands and games

See the [command table](../README.md#commands-and-permissions). Game IDs:
`xiangqi`, `gomoku`, `chess`, `ludo`, `checkers`, `draughts`, `reversi`, `go`, `go9`, `go13`, `connectfour`, `color-eight`, `mahjong`, `yacht`, `doudizhu`, `liars-bar`, `texas-holdem`.

`go` is 19×19. Example: `/3dtabletop create connectfour` followed by `/3dtabletop bots` to fill empty seats. A move such as `drop:3` uses zero-based column indices. Menu actions recheck player permissions, room state, turn and revision.

The `create` command uses standard rules; use the native game setup page to change rules before creating a room. `mahjong` always has four seats and defaults to Riichi. Color Eight supports two to five players. Tab actions never expose another player's concealed-hand actions.

Sneak-right-click a table to join or open its room menu, including the upper Connect Four rack. New table centers need at least three blocks of separation along X or Z, or three blocks vertically; this does not relocate existing saved tables. A seated, authorized player receives collision and hunger protection within six blocks of their own table. Walking away restores the original collision setting within one second; changing worlds or disconnecting restores it immediately.

New dice stands also require clear space on the table's +X side. Keep at least five blocks between adjacent dice-table centers along X; the placement check rejects obstructions instead of editing blocks. Restored tables without the new stand flag use a compact stand at their existing anchor.

## config.yml

| Key | Default | Meaning |
| --- | --- | --- |
| `language` | `en_US` | Language file name under `languages/`, without `.yml` |
| `max-rooms` | `12` | Maximum rooms |
| `reconnect-seconds` | `120` | Offline return grace period |
| `idle-room-minutes` | `30` | Idle room timeout |
| `turn-seconds` | `60` | Turn timeout before basic bot assistance |
| `sounds.enabled` | `true` | Enable native table, selection and private turn sounds |
| `sounds.volume` | `1.0` | Multiplier for the quiet presets, clamped to 0–1; 0 mutes all cues |

Restart after configuration or language changes. Layout files are read when opening a menu. Preserve custom layouts and language files during updates.

Sounds use Minecraft's **Blocks** sound category and require no resource pack. Nearby players hear table actions; turn prompts reach only the eligible seated human within eight blocks in the same world. Existing config files may omit both sound keys and use the defaults. Non-finite volume values mute sounds. Re-rendering, startup replay and rejected actions do not emit move sounds.

## Optional resource models

CraftEngine provides the optional custom item models. `rendering.mode` selects vanilla, resource-pack or mixed rendering. Current gameplay and native Dialog menus work without other plugins. See [resource-pack setup](craftengine.zh-CN.md).

## Local acceptance build

Install `3dtabletop-1.10.24-SNAPSHOT.jar` on an isolated server first. Check rule selection, private hands from every seat and a spectator, card hover and deck draws, Color Eight color changes, Mahjong calls, dice travel/landing and restart recovery. Also check Mahjong lifted selection/unseen-copy labels, all four Ctrl focus positions and return behavior, real countdowns, tabletop call combinations, riichi sticks, sound balance, existing board interactions and custom text. Preserve your backup when comparing with a published build.
