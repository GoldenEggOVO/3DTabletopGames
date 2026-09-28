# Verification and acceptance

## Local build: 1.5.1-SNAPSHOT

The exact JAR hash and detailed results accompany the local delivery in `verification.json` and `SHA256SUMS.txt`.

- JDK 25 / Maven: 177 Java tests passed, zero failures, errors or skipped tests.
- Python: four migration tests passed.
- Model tests check vertical column drops and private landing previews visible beyond both rack faces, final positions after restoration/wins, Reversi entity identity and interrupted flips, Go dead-group marker reuse, opposing knight headings and three-part non-overlapping discs.
- Text tests check MiniMessage, legacy colors/hex/reset, literal names through menus/rosters/outcomes, bot metadata, old-file preservation, language-layer precedence, named action labels and long-label fitting.
- Idle-table tests run 100 view updates without reading rule state or resending labels. Seat/phase changes without a revision, language changes, dice completion and close during animation are also covered. No production load benchmark was performed.
- New menu/lifecycle regressions cover all 25 rooms across pages, rules and move entrypoints, direct drops, stale source/room/leave callbacks, revoked permissions and historical stock-caption migration. Existing rules, recovery, single-use callbacks, Tab completion and migration tests remain included.
- Table protection tests cover distance, world, permission and disconnect boundaries, original collision settings and periodic updates. Input regressions cover immediate new-table menus, both upper rack faces, selection cleanup, placement spacing and action-bar feedback.

Clean Purpur **26.2-2622** passed all three boots on **2026-09-28**, including real Display model checks and native Dialog pagination/rules navigation for 25 synthetic rooms on every boot. The first boot played and persisted a Connect Four drop through the new menu controls. ServerGames, ServerMenu, ServerCasino and KaMenu were absent. The probe used simulated Player calls, not a connected client. The isolated offline fixture logged environment warnings for Windows metrics and a blocked external Minecraft public-key lookup; plugin startup and all probe checks completed.

Verified JAR SHA-256: `3fc14cbc80bc244cb77d028477b88c0c7994b1e28f656da3cfe6ded494727056`.

Evidence directory: `target/standalone-smoke-20260928-030558/` (local, ignored). A passing unit/runtime test is not a claim of client acceptance.

An additional three-boot run passed in `target/standalone-smoke-20260928-031151/`: an eleven-game snapshot containing 1,431 saved moves survived both restart and legacy-folder migration. All seven copied configuration/language/menu files matched the legacy-folder hashes. The snapshot contained bots only; its world UUID was explicitly remapped to the separate fixture world. An earlier test-runner attempt was rejected because it read a stale prior-boot marker and failed to shut down cleanly; the corrected runner reads a distinct log for every boot.

The migration CLI also converted and checked an eleven-game, 1,909-move snapshot using the exact candidate JAR. Source bytes were unchanged, output JSON retained the original data, and a repeated write was refused without changing the existing candidate (`target/migration-cli-soak/receipt.json`). A synthetic runner interruption after a real server startup also exited cleanly and reaped its own server process (`target/probe-interrupt-receipt.json`).

A **3,600-second continuous run** passed in `target/soak-20260928-025900/`: eleven simultaneous all-bot game kinds made **13,942 moves**, completed/restarted **136 rounds**, applied **197 undos** and passed **403 replay comparisons**. Every periodic entity inventory matched the live models, with a peak of 2,651 owned entities. Removing all test rooms left **zero owned entities** and the server exited with code 0. Chinese Checkers made 1,225 moves but did not finish a round within the hour; this is a lifecycle/replay check, not an assessment of bot playing strength or a production performance benchmark.

## Reproduce

```sh
mvn -B -ntp package
python -m unittest discover -s tests -p "test_*.py"
python tools/standalone-probe/run_standalone.py --server-dir /path/to/prepared-purpur --maven-repo /path/to/maven/repository
python tools/package_standalone.py target/standalone-smoke-YYYYMMDD-HHMMSS/receipt.json
```

The server directory must contain the legally obtained Purpur 26.2 `purpur-2622.jar` and its prepared `libraries`, `versions` and optional `cache` folders. The script copies them into a fresh ignored `target/` directory and explicitly accepts the Minecraft EULA for that local fixture. Read/accept the EULA before running it. It binds to `127.0.0.1:25617`, runs in offline mode, installs only Tabletop plus the test probe, and stops each boot cleanly. Never point it at a production directory or expose its port.

Three boots cover clean creation, restart recovery and copying a legacy ServerBoards folder. The probe additionally exercises real BlockDisplay transforms and removal for Connect Four, Reversi, Go and Chess, Dialog construction/callbacks using a simulated Player, permission denial, and duplicate command suggestions. It does not connect a Minecraft client.

For continuous model/replay testing, `python tools/standalone-probe/run_soak.py --seconds 3600` prepares another isolated fixture on `127.0.0.1:25618`. It runs all eleven game kinds with bots, completes and restarts rounds, periodically undoes/replays moves, compares tracked model entities with live entities and checks cleanup after removing the rooms. It uses the same prepared server/cache and EULA prerequisites as the short probe. Duration is bounded to 60–7200 seconds.

To check a snapshot from that synthetic fixture, copy its `plugins/3dtabletop/rooms.json` while the games are running, then pass the copy to `run_standalone.py --rooms-snapshot path/to/copy.json`. Only eleven-room all-bot fixtures without return locations are accepted. The runner keeps the input unchanged, maps its anchors to the fresh test world, and checks room IDs, seeds, seats, positions, saved history and rule state on both restart and legacy-folder migration. Each boot reads its own new log so a previous success marker cannot end the next boot early.

The packager requires a passing three-boot receipt whose SHA-256 matches the exact local JAR. Add `--soak path/to/soak/receipt.json` and `--snapshot path/to/snapshot/receipt.json` to include the additional checks; incomplete, failed or mismatched receipts are rejected. Corresponding source comes from Git-tracked files; stage/commit intended new source before packaging. CI builds/tests and uploads development artifacts only; it does not create tags or Releases.

## Client acceptance checklist

- Connect Four: round discs, rack supports and private green landing outline visible from either side; move between columns, fill a column, observe the drop and then win a game.
- Reversi: flip several stones, observe both colors and interrupted consecutive updates; confirm the visible final face matches the game.
- Go/Chess: repeatedly mark/unmark a dead group without stone jumps; inspect opposing knight headings and stone/base seams.
- Text: default English, custom MiniMessage, old `&` colors, long names/translations, leave-confirmation label and menu title.
- Flow: room pages, rules/help, direct menu placements, create/join/ready, bots, legal and denied actions, spectate, undo, rematch, leave, restart and resume.
- Table area: create adjacent tables, try an overlapping placement, open a new table immediately and use both upper rack faces; walk more than six blocks away or change worlds and check collision/hunger behavior.

Actual client visuals, smoothness, font appearance and input feel remain pending maintainer acceptance. Production deployment and Release publication are not part of this delivery.
