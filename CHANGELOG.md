# Changelog

## 1.4.1-SNAPSHOT — local acceptance build

- Skip title construction and rule reads while table display state is unchanged; only tick moving or flipping pieces.
- Keep immediate refresh for seat/phase changes, completed dice rolls and reconstructed boards.
- Move menus, chat, action hints, rule summaries and room presentation to named messages with literal parameters.
- Preserve human names containing translation words or formatting markers; identify bots from seat metadata.
- Derive named templates from customized legacy translations at language load time; explicit named values take precedence.
- Preserve existing language/menu files and schema 1 room records. No rule changes or additional runtime dependencies.

## 1.4.0-SNAPSHOT — local acceptance build

- Organize bilingual entry documentation, architecture, language, migration and verification guides.
- Add build/test CI and corresponding-source packaging; CI does not publish Releases.
- Add MiniMessage display formatting with legacy colors and literal named parameters.
- Add named messages for common navigation, leave confirmation and table status; preserve legacy language files.
- Fit long table labels within a bounded area while keeping instruction lines.
- Replace Connect Four cubes with stepped round discs, reinforce the rack and animate vertical column drops.
- Reuse Reversi piece entities and animate two-sided flips.
- Keep schema 1 room data, existing rules, commands, permissions and optional integrations.

## 1.3.1

- Translate the leave-confirmation button, set the default menu title to 3D Tabletop Games, and hide the duplicate namespaced root suggestion.

## 1.3.0

- Rename the runtime namespace to `3dtabletop` and Java package to `dev.tabletop3d`.
- Add default-English YAML translations and legacy ServerBoards folder migration.

Earlier history is available through repository tags and commits.
