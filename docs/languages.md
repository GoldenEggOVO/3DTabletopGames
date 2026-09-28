# Languages and display text

## Files and compatibility

Language files remain in `plugins/3dtabletop/lang/` to preserve existing installations. Select `language: en` or another lowercase file code, then restart. A missing entry falls back to the bundled English catalog. Existing files are never overwritten.

Each file may contain `translations`, `messages`, or both. Menus, chat notifications, action hints, room/seat status, game names, rule summaries and outcome wrappers use named messages. Raw rule-engine descriptions, move reports, errors and persisted reason strings retain a compatibility adapter; rule state is never translated or rewritten.

```yaml
messages:
  'menu.title': '3D Tabletop Games'
  'menu.leave.confirm': '<red>Confirm Leave</red>'
  'table.title': '<gold>{game} · Table {number}</gold>'
  'table.turn': '<white>Turn: {player}</white>'
  'table.hint': 'Aim · Click to play'
  'chat.joined': '<green>{player} joined the room</green>'
  'room.bot': 'Practice {number}'
  'result.winner': '{player} wins'
translations:
  '创建房间': 'Create Room'
```

New keys are listed in the bundled `src/main/resources/lang/en.yml`. On an older install, add the desired keys to a `messages` section; there is no need to replace the existing file. Layers are bundled English, local English, then the selected language. Within each file, explicit `messages` values win over derived legacy templates.

Customized legacy translations such as `确认离开` or `加入了房间` are mapped to named templates at load time, before player names are inserted. Unchanged stock entries in local English retain the improved bundled templates. Explicit entries in the selected language override lower layers, even when they happen to equal English. Internal mappings live in the JAR's `lang/legacy.yml`; administrators edit their selected language, not that internal mapping. A malformed language file prevents startup with a diagnostic; it is never silently replaced.

Keep parameter names (`{player}`, `{game}`, `{number}`) intact. Named parameters are inserted as literal text or prebuilt Components. Human names are preserved in menus, table rosters, winner announcements and chat, including names that contain translation words or formatting markers. Bot labels use the stored seat's bot flag, not a guess based on its name. `rules.footer` accepts `{seconds}` from the configured reconnect grace period.

## Styling

The shared renderer supports MiniMessage colors, decorations, gradients, rainbow, reset and newline. Legacy `&a`, `§a`, `&#12abef` and expanded hex codes are converted with legacy decoration-reset semantics. Click/hover commands are not interpreted from language templates; menu actions are owned by the server.

Menu layouts in `menus/*.yml` retain `Title`, `Body`, `Bottom`, `@title@`, `@description@` and `@label@`. New layouts use MiniMessage. Existing custom templates keep their exact files, order, widths and captions. A hardcoded template caption overrides the generated label: use `@label@` to follow the language file.

```yaml
text: '<dark_gray>[ <red>@label@ <dark_gray>]'
```

Table labels use estimated default-font widths to fit the available space. Long lines may shrink or receive an ellipsis; all status lines are retained. Custom client fonts require in-game acceptance.
