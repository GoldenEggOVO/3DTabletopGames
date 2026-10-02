# Editable languages

The layout follows 3D Casino. First startup creates `plugins/3dtabletop/languages/en_US.yml` and `languages/zh_CN.yml`. Set `language: en_US` (default) or `language: zh_CN`; this is a server-wide setting.

## Edit and reload

Each message has an independent stable key, without a `messages:` wrapper:

```yaml
"menu.create": "Create Room"
"menu.close": "<dark_gray>[ <red>Close Menu <dark_gray>]"
"chat.joined": "<green>{player} joined the room</green>"
```

Equivalent nested YAML is supported. Flat keys also preserve a parent message and child messages simultaneously, such as `room.roster` and `room.roster.empty`. `rules.*` includes individually editable rule text and compatibility phrases; keep the keys and suffixes unchanged.

Copy a generated file to `languages/my_language.yml`, edit its values, then set `language: my_language`. Existing files are never overwritten. Run `/3dtabletop reload-language`: console may always run it; players need `3dtabletop.use`, `3dtabletop.admin` and any configured authentication check. The command reads only the language selection from disk; other configuration changes require a restart.

Successful reload closes and invalidates old menu sessions. Labels and private hand hints refresh at the next display update without ending a game. Any warning rejects a reload and keeps the previous language. Console diagnostics identify the filename and message key.

## Validation and fallback

Layers are bundled English, editable `en_US.yml`, then the selected file. Missing keys fall back normally. Invalid YAML, non-string values, unknown keys, unknown placeholders and invalid styles warn; valid entries still load at startup. Files remain unchanged. Locale filenames accept letters, digits, `_` and `-`, start with a letter, and have at most 64 characters.

Keep placeholder names such as `{player}`, `{game}`, `{number}`. Translations may omit a parameter but cannot invent one. Player names and ordinary parameter values remain literal text. Colors, decorations, gradients, rainbow, reset and newline use the same renderer as Casino. Legacy `&a`, `§a` and hex colors remain supported. Language files cannot define callbacks, clicks or hover commands.

## Upgrade from lang

Old `plugins/3dtabletop/lang/*.yml` files migrate once to `languages/`; `en.yml` becomes `en_US.yml`. Valid files are converted to independent flat keys while retaining custom translations. Files with validation warnings are copied unchanged for diagnosis and compatibility. Originals remain intact. Existing destinations win and are never overwritten. `language: en` remains an alias for `en_US`. Legacy `messages:` and `translations:` sections remain readable. The JAR's `language-compatibility.yml` is internal and is not a selectable language.

## Menu layouts

Custom `menus/*.yml` files retain their exact order, widths and captions. Hardcoded custom captions override generated labels; use `@label@` to follow language messages. Table labels fit estimated default Minecraft font metrics; custom client fonts need in-game acceptance.
