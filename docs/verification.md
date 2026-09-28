# Verification and acceptance

## Local build: 1.4.1-SNAPSHOT

The exact JAR hash and detailed results accompany the local delivery in `verification.json` and `SHA256SUMS.txt`.

- JDK 25 / Maven: 150 Java tests passed, zero failures, errors or skipped tests.
- Python: four migration tests passed.
- Model tests check vertical column drops, final positions after restoration/wins, Reversi entity identity, interrupted flip convergence and final owner orientation.
- Text tests check MiniMessage, legacy colors/hex/reset, literal names through menus/rosters/outcomes, bot metadata, old-file preservation, language-layer precedence, named action labels and long-label fitting.
- Idle-table tests run 100 view updates without reading rule state or resending labels. Seat/phase changes without a revision, language changes, dice completion and close during animation are also covered. No production load benchmark was performed.
- Existing rules, room recovery, permissions, single-use callbacks, Tab completion and migration tests remain included.

Clean Purpur **26.2-2622** passed all three boots on **2026-09-28**, including real Display model checks on every boot. ServerGames, ServerMenu, ServerCasino and KaMenu were absent. The probe used simulated Player calls, not a connected client. The isolated offline fixture logged environment warnings for Windows metrics and a blocked external Minecraft public-key lookup; plugin startup and all probe checks completed.

Verified JAR SHA-256: `427c280429dc84be453602365a6f109088e3b0bec6587c9deeee66521da5d7fc`.

Evidence directory: `target/standalone-smoke-20260928-014151/` (local, ignored). A passing unit/runtime test is not a claim of client acceptance.

## Reproduce

```sh
mvn -B -ntp package
python -m unittest discover -s tests -p "test_*.py"
python tools/standalone-probe/run_standalone.py --server-dir /path/to/prepared-purpur --maven-repo /path/to/maven/repository
python tools/package_standalone.py target/standalone-smoke-YYYYMMDD-HHMMSS/receipt.json
```

The server directory must contain the legally obtained Purpur 26.2 `purpur-2622.jar` and its prepared `libraries`, `versions` and optional `cache` folders. The script copies them into a fresh ignored `target/` directory and explicitly accepts the Minecraft EULA for that local fixture. Read/accept the EULA before running it. It binds to `127.0.0.1:25617`, runs in offline mode, installs only Tabletop plus the test probe, and stops each boot cleanly. Never point it at a production directory or expose its port.

Three boots cover clean creation, restart recovery and copying a legacy ServerBoards folder. The probe additionally exercises real BlockDisplay transforms and removal for Connect Four and Reversi, Dialog construction/callbacks using a simulated Player, permission denial, and duplicate command suggestions. It does not connect a Minecraft client.

The packager requires a passing three-boot receipt whose SHA-256 matches the exact local JAR. Corresponding source comes from Git-tracked files; stage/commit intended new source before packaging. CI builds/tests and uploads development artifacts only; it does not create tags or Releases.

## Client acceptance checklist

- Connect Four: round discs, rack supports, both sides visible; click each column, observe a vertical drop and small bounce, then win a game.
- Reversi: flip several stones, observe both colors and interrupted consecutive updates; confirm the visible final face matches the game.
- Text: default English, custom MiniMessage, old `&` colors, long names/translations, leave-confirmation label and menu title.
- Flow: create/join/ready, bots, legal and denied actions, spectate, undo, rematch, leave, restart and resume.

Actual client visuals, smoothness, font appearance and input feel remain pending maintainer acceptance. Production deployment and Release publication are not part of this delivery.
