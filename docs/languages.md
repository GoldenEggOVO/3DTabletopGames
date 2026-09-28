# Languages and display text

## Files and compatibility

Language files remain in `plugins/3dtabletop/lang/` to preserve existing installations. Select `language: en` or another lowercase file code, then restart. A missing entry falls back to the bundled English catalog. Existing files are never overwritten.

Each file may contain `translations`, `messages`, or both. The first batch migrates common navigation buttons, leave confirmation and physical-table status to named messages. Rules and other legacy pages still use `translations`; this release does not claim to have removed every source-text translation.

```yaml
messages:
  'menu.title': '3D Tabletop Games'
  'menu.leave.confirm': '<red>Confirm Leave</red>'
  'table.title': '<gold>{game} · Table {number}</gold>'
  'table.turn': '<white>Turn: {player}</white>'
  'table.hint': 'Aim · Click to play'
translations:
  '创建房间': 'Create Room'
```

New keys are listed in the bundled `src/main/resources/lang/en.yml`. On an older install, add the `messages` section manually to customize them. Known legacy equivalents such as `确认离开` still override the matching named message unless an explicit `messages` value is supplied in that file. Layers are bundled English, local English, then the selected language.

Keep parameter names (`{player}`, `{game}`, `{number}`) intact. Named parameters are inserted as literal text or prebuilt Components. A player's name cannot become a MiniMessage color or click command. This protection applies to the named interface; old assembled source-text messages remain a compatibility path.

## Styling

The shared renderer supports MiniMessage colors, decorations, gradients, rainbow, reset and newline. Legacy `&a`, `§a`, `&#12abef` and expanded hex codes are converted with legacy decoration-reset semantics. Click/hover commands are not interpreted from language templates; menu actions are owned by the server.

Menu layouts in `menus/*.yml` retain `Title`, `Body`, `Bottom`, `@title@`, `@description@` and `@label@`. New layouts use MiniMessage. Existing custom templates keep their exact files, order, widths and captions. A hardcoded template caption overrides the generated label: use `@label@` to follow the language file.

```yaml
text: '<dark_gray>[ <red>@label@ <dark_gray>]'
```

Table labels use estimated default-font widths to fit the available space. Long lines may shrink or receive an ellipsis; all status lines are retained. Custom client fonts require in-game acceptance.
