# Migration

## Updating to 1.7.0-SNAPSHOT

Back up and stop the server before replacing the JAR. Keep existing configurations, languages, menus, worlds and `rooms.json`. No data deletion or conversion is required for existing 3dtabletop rooms.

Schema 1 rooms now add `rulesVersion: 1`, an `options` string map, `owner` and `sideTray`. Missing options retain the historical game rules. Default-valued settings may be omitted from the map; their meaning is fixed by rule version 1. The owner stays the same when starting sides swap. Existing Aeroplane histories are still replayed by their original engine.

New dice rooms use a full side stand. Old records without `sideTray` use a compact one without changing saved coordinates. Dice animations are never replayed on startup. The new `menus/setup.yml` and `menus/hand.yml` are added when missing; other custom files remain untouched. New language keys fall back to bundled English.

Verify a copy of your data on an isolated server: compare room IDs, seats, options, anchors and history; start, play and restart once. Unsupported rule versions or invalid options stop restoration and preserve the original records. A downgrade to older JARs cannot understand the new games/options; use the corresponding pre-upgrade backup instead of loading changed histories under older rules.

## From 3dtabletop 1.3.x, 1.4.x or 1.5.0 snapshots

Stop the server, back up `plugins/3dtabletop/` and worlds, and replace the JAR. Keep the existing configuration, `lang/`, `menus/` and `rooms.json`. Schema 1, room IDs, world UUIDs, coordinates, seats, seed and move history are unchanged. New models rebuild from saved rule state, with no resumed animation.

New named `messages` keys fall back to bundled English and can be added to old language files. Existing legacy translations and menu colors continue to work. Custom files are not overwritten.

The new table-spacing check applies only when creating a table. Previously saved close-together tables remain loadable at their original positions; no data rewrite is required.

## From ServerBoards

1. Stop the server, back up `plugins/ServerBoards/`, record the SHA-256 of `rooms.json`, and remove the old JAR.
2. When `plugins/3dtabletop/` does not exist, the plugin copies the old folder and writes `migration-from-serverboards.txt`. The old folder is retained.
3. Verify the copied files before gameplay changes their state. Use console `3dtabletop status`, then `/3dtabletop resume`, and restart again to verify recovery.

If both folders exist without the migration marker, startup refuses to choose between them. Back up and inspect both folders while stopped. Do not delete room records to bypass a recovery error.

Old `/boards`, `serverboards:boards` and `serverboards.use` are removed. Update permission and menu forwarders to `3dtabletop`. See [optional integrations](installation.md#optional-integrations).

## Import older ServerGames board rooms

Some old room records lack an explicit world/position. These cannot be guessed. Supply `anchors.json`, keyed by room UUID, with `world`, `x`, `y`, `z`. Already anchored data uses `{}`. Only supported board games are accepted.

```sh
python tools/server_boards_migrate.py old-rooms.json anchors.json candidate-rooms.json --jar target/3dtabletop-1.7.0-SNAPSHOT.jar
python tools/server_boards_migrate.py old-rooms.json anchors.json candidate-rooms.json --jar target/3dtabletop-1.7.0-SNAPSHOT.jar --check
```

The tool preserves the source, refuses to overwrite a candidate, prints hashes, and replays rules with the exact target JAR. Install the validated candidate only on a stopped isolated test server with matching world UUIDs. Verify seats, board, history and restart behavior before considering production migration.

[中文迁移说明](migration.zh-CN.md)
