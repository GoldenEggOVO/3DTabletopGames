# Migration

## From 3dtabletop 1.3.x or 1.4.x snapshots

Stop the server, back up `plugins/3dtabletop/` and worlds, and replace the JAR. Keep the existing configuration, `lang/`, `menus/` and `rooms.json`. Schema 1, room IDs, world UUIDs, coordinates, seats, seed and move history are unchanged. New models rebuild from saved rule state, with no resumed animation.

New named `messages` keys fall back to bundled English and can be added to old language files. Existing legacy translations and menu colors continue to work. Custom files are not overwritten.

## From ServerBoards

1. Stop the server, back up `plugins/ServerBoards/`, record the SHA-256 of `rooms.json`, and remove the old JAR.
2. When `plugins/3dtabletop/` does not exist, the plugin copies the old folder and writes `migration-from-serverboards.txt`. The old folder is retained.
3. Verify the copied files before gameplay changes their state. Use console `3dtabletop status`, then `/3dtabletop resume`, and restart again to verify recovery.

If both folders exist without the migration marker, startup refuses to choose between them. Back up and inspect both folders while stopped. Do not delete room records to bypass a recovery error.

Old `/boards`, `serverboards:boards` and `serverboards.use` are removed. Update permission and menu forwarders to `3dtabletop`. See [optional integrations](installation.md#optional-integrations).

## Import older ServerGames board rooms

Some old room records lack an explicit world/position. These cannot be guessed. Supply `anchors.json`, keyed by room UUID, with `world`, `x`, `y`, `z`. Already anchored data uses `{}`. Only supported board games are accepted.

```sh
python tools/server_boards_migrate.py old-rooms.json anchors.json candidate-rooms.json --jar target/3dtabletop-1.5.0-SNAPSHOT.jar
python tools/server_boards_migrate.py old-rooms.json anchors.json candidate-rooms.json --jar target/3dtabletop-1.5.0-SNAPSHOT.jar --check
```

The tool preserves the source, refuses to overwrite a candidate, prints hashes, and replays rules with the exact target JAR. Install the validated candidate only on a stopped isolated test server with matching world UUIDs. Verify seats, board, history and restart behavior before considering production migration.

[中文迁移说明](migration.zh-CN.md)
