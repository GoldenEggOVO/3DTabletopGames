# Upgrade from the current 1.8.10 test build

1.9 reads the current `3dtabletop` directory directly. Automatic ServerBoards copying, legacy language aliases, old stock-menu replacement, AuthMe and Geyser/Floodgate integrations have been removed. CraftEngine remains optional.

## Prepare offline

Stop the server cleanly and back up its complete plugin data directory. From the source package, run Python 3.11+ with PyYAML installed:

```sh
python tools/upgrade_current_data.py /backup/3dtabletop /staging/3dtabletop
```

The output must be a new separate directory. The tool never edits its input. It converts current Color Eight `rulesVersion: 2` records from `lastcard` to `color-eight`, retaining physical IDs, seed, seats, anchors, revision and every action. Only obsolete finish options and display reason labels change. Already accepted exhausted-deck draw/pass records retain their recorded meaning during replay.

Language files become complete named templates under `languages/`. Matched custom values and custom menu captions remain. Unmatched customized fragments remain in `upgrade-report.json` and the copied original files; review them and fill the corresponding complete templates. Unknown rules versions stop conversion before output is created. Finish such older games with their original build.

Validate staged rooms against the exact new JAR:

```sh
java -cp target/3dtabletop-1.9.0-SNAPSHOT.jar dev.tabletop3d.RoomReplayVerifier /staging/3dtabletop/rooms.json
```

Only install the staged directory after passing replay and language validation. Replace the old plugin JAR with the shaded new JAR, install the matching independent resource pack and URL/SHA-1, then start the server. Keep the original backup for rollback. Other plugins and resource packs remain separate.
