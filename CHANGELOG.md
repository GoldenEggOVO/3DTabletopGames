# Changelog

## 1.5.0-SNAPSHOT — local acceptance build

- Browse every room across pages, with available seats first, persistent create/help controls and clear Join / Resume / View labels.
- Add rules/help from each game and room options, plus menu-based moves from an active room. Single placement/drop/dead-mark choices execute directly; column labels start at 1.
- Preview the legal Connect Four landing slot privately on both rack faces; explain full columns and waiting states, and throttle unchanged hints.
- Read current pieces for hover text and refresh dice feedback when rolling finishes.
- Reuse Go stones when marking/unmarking dead groups, face chess knights toward their opponent, and remove overlapping disc surfaces without adding model entities.
- Reject callbacks for removed rooms, refresh outdated piece choices, recheck permissions and prevent an old leave confirmation from leaving a newer room.
- Let three exact historical stock room captions follow the selected named language without rewriting customized menu files.
- Preserve schema 1 room data, existing rules and all optional integration boundaries. No new runtime dependency or Release.

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
