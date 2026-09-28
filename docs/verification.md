# Verification and acceptance

## Local build: 1.5.0-SNAPSHOT

The exact JAR hash and detailed results accompany the local delivery in `verification.json` and `SHA256SUMS.txt`.

- JDK 25 / Maven: 167 Java tests passed, zero failures, errors or skipped tests.
- Python: four migration tests passed.
- Model tests check vertical column drops and private landing previews visible beyond both rack faces, final positions after restoration/wins, Reversi entity identity and interrupted flips, Go dead-group marker reuse, opposing knight headings and three-part non-overlapping discs.
- Text tests check MiniMessage, legacy colors/hex/reset, literal names through menus/rosters/outcomes, bot metadata, old-file preservation, language-layer precedence, named action labels and long-label fitting.
- Idle-table tests run 100 view updates without reading rule state or resending labels. Seat/phase changes without a revision, language changes, dice completion and close during animation are also covered. No production load benchmark was performed.
- New menu/lifecycle regressions cover all 25 rooms across pages, rules and move entrypoints, direct drops, stale source/room/leave callbacks, revoked permissions and historical stock-caption migration. Existing rules, recovery, single-use callbacks, Tab completion and migration tests remain included.

Clean Purpur **26.2-2622** passed all three boots on **2026-09-28**, including real Display model checks and native Dialog pagination/rules navigation for 25 synthetic rooms on every boot. The first boot played and persisted a Connect Four drop through the new menu controls. ServerGames, ServerMenu, ServerCasino and KaMenu were absent. The probe used simulated Player calls, not a connected client. The isolated offline fixture logged environment warnings for Windows metrics and a blocked external Minecraft public-key lookup; plugin startup and all probe checks completed.

Verified JAR SHA-256: `9616ddfaaef2004756e31db745c9dbc197ece8228789992636c2f359a52e33db`.

Evidence directory: `target/standalone-smoke-20260928-023833/` (local, ignored). A passing unit/runtime test is not a claim of client acceptance.

## Reproduce

```sh
mvn -B -ntp package
python -m unittest discover -s tests -p "test_*.py"
python tools/standalone-probe/run_standalone.py --server-dir /path/to/prepared-purpur --maven-repo /path/to/maven/repository
python tools/package_standalone.py target/standalone-smoke-YYYYMMDD-HHMMSS/receipt.json
```

The server directory must contain the legally obtained Purpur 26.2 `purpur-2622.jar` and its prepared `libraries`, `versions` and optional `cache` folders. The script copies them into a fresh ignored `target/` directory and explicitly accepts the Minecraft EULA for that local fixture. Read/accept the EULA before running it. It binds to `127.0.0.1:25617`, runs in offline mode, installs only Tabletop plus the test probe, and stops each boot cleanly. Never point it at a production directory or expose its port.

Three boots cover clean creation, restart recovery and copying a legacy ServerBoards folder. The probe additionally exercises real BlockDisplay transforms and removal for Connect Four, Reversi, Go and Chess, Dialog construction/callbacks using a simulated Player, permission denial, and duplicate command suggestions. It does not connect a Minecraft client.

The packager requires a passing three-boot receipt whose SHA-256 matches the exact local JAR. Corresponding source comes from Git-tracked files; stage/commit intended new source before packaging. CI builds/tests and uploads development artifacts only; it does not create tags or Releases.

## Client acceptance checklist

- Connect Four: round discs, rack supports and private green landing outline visible from either side; move between columns, fill a column, observe the drop and then win a game.
- Reversi: flip several stones, observe both colors and interrupted consecutive updates; confirm the visible final face matches the game.
- Go/Chess: repeatedly mark/unmark a dead group without stone jumps; inspect opposing knight headings and stone/base seams.
- Text: default English, custom MiniMessage, old `&` colors, long names/translations, leave-confirmation label and menu title.
- Flow: room pages, rules/help, direct menu placements, create/join/ready, bots, legal and denied actions, spectate, undo, rematch, leave, restart and resume.

Actual client visuals, smoothness, font appearance and input feel remain pending maintainer acceptance. Production deployment and Release publication are not part of this delivery.
