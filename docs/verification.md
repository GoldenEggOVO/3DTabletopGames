# Verification and acceptance

## Local build: 1.7.4-SNAPSHOT

- JDK 25 / Maven package: **448 tests passed**, zero failures, errors or skipped tests; Python: **four migration tests passed**. Removed-profile tests were removed with their rule implementations. New checks cover private matching glow, rightmost draws, four-seat layout, wind/score orientation, Shift invisibility restoration, menu removal, sound transitions and rejected legacy profiles without save corruption.
- The exact JAR passed three clean Purpur **26.2-2622** boots in `target/standalone-smoke-20260929-140929/` (create, restart, legacy-folder migration). Native Display, HUD, private controls and four-profile menu paths passed; all **27 vanilla sound cues** were registered. Shift uses real events/world clearance with a stateful Player proxy; connected-client physics, equipment visibility and camera/audio feel remain acceptance items.
- Three further boots in `target/standalone-smoke-20260929-141136/` restored **22 rooms / 1,896 saved events / 14 game identifiers**, including all four supported Mahjong profiles. Only fixture world UUIDs were remapped. Fuzhou/Qinhuangdao saves now fail explicitly and retain original data plus an unreadable backup; they are not converted.
- The **60-second / 16-table** run in `target/soak-20260929-140929/` completed **337 actions**, 17 replay comparisons, peak **4,611** owned entities and **zero** after cleanup. No full round or undo completed in this short run; directed tests cover those paths. Entity accounting includes private matching bodies.
- Initial probe failures came from a collection/list mismatch in the new entity inventory and the previous six-profile expectation. Correcting those probe checks retained exact inventory and menu reachability assertions. Product tests passed before and after the probe corrections. Deleted rule classes were confirmed absent from the rebuilt JAR.
- Source-geometry preview checks caught and corrected reversed hand ordering and wind placement before delivery. The final preview uses production geometry with a synthetic state, approximate fonts/materials and simulated text backface culling; native glow is not drawn. Actual Minecraft visual/audio acceptance is pending. Existing table anchors and customized YAML are preserved; Mahjong tables expand to 3×3 and need surrounding clearance. No Release or production deployment.

Verified JAR SHA-256: `2d0f4c953ebdbd9b4b27a16dadb6d23a418713a5ccdabe083a561b567973ec2a`. Exact passing receipts accompany the local package in `verification.json`.


## Local build: 1.7.3-SNAPSHOT

- JDK 25 / Maven package: **452 tests passed**, zero failures, errors or skipped tests. Python: **four migration tests passed**. Added checks cover selected-tile lift/return without neighbor displacement, 17-tile four-seat layout, unseen-copy privacy/deduplication, graphical call combinations, HUD counts/deadlines/riichi sticks, and Shift entry/return/cleanup with cancelled or external teleports.
- The exact JAR passed three clean Purpur **26.2-2622** boots in `target/standalone-smoke-20260929-130010/` (create, restart and legacy-folder migration). ServerGames, ServerMenu, ServerCasino and KaMenu were absent. Real Display checks covered Mahjong lift/return/glow, owner-only remaining labels, center counts, native call-face previews and cleanup; existing Dialog/callback/permission checks passed. The first probe used bitwise `Location.equals`, which treated native teleport's `-0.0` to `0.0` yaw normalization as a failure. Diagnostic output confirmed identical position, cleared glow/text; numerical pose comparison corrected the probe without changing product code.
- Shift event checks used the real Bukkit event bus/world clearance and a stateful **Player proxy**, not a connected client. They covered entry, fixed position with mouse aim, return, external teleport precedence and clearing focus after a cancelled return. Native player physics, client input feel and anti-cheat behavior remain client acceptance items.
- Three further boots in `target/standalone-smoke-20260929-130232/` restored **24 rooms / 1,976 saved events / 14 game identifiers**, including all six Mahjong profiles and legacy Aeroplane. Seeds, options, hands, anchors and action histories retained their meanings; only fixture world UUIDs were remapped.
- The **60-second / 18-table** run in `target/soak-20260929-125423/` completed **369 actions** and 19 replay comparisons. Peak owned entities: **6,859**; after cleanup: **zero**. No complete round or undo cycle finished in this short run; directed tests cover those paths. Entity inventory includes new HUD and private remaining labels, with exact-set comparisons.
- No game rules, room schema or custom YAML were reset. The model preview uses production Display geometry with a synthetic demonstration state and approximate fonts/materials; it is not a Minecraft screenshot. See the [client checklist](acceptance.zh-CN.md) for legibility, glow, Shift camera/return and actual operation acceptance. No Release or production deployment.

Verified JAR SHA-256: `ecdcb24e942afdefe676f7aaffa18cf7bf14d30c50c42df1bc6a4873be55c440`. Exact passing receipts accompany the local package in `verification.json`.

## Local build: 1.7.2-SNAPSHOT

- JDK 25 / Maven: **420 tests passed**, zero failures, errors or skipped tests. Python: **four migration tests passed**. New checks cover stationary Mahjong glow, private legal call buttons, reach/occlusion, response pass versus local dismissal, multi-choice actions, stale actions, pending undo, card-hand preservation, rotating/reversing ring geometry and 6/9 underlines.
- The exact JAR passed three isolated Purpur **26.2-2622** boots in `target/standalone-smoke-20260929-012122/`. Native checks confirmed a real Mahjong game can reach an owner call and display private temporary buttons, hover toggles glow without changing tile positions, and the 36-part Last Card direction ring moves. Existing menu callback, permission and private-hand checks also passed. No ServerGames, ServerMenu, ServerCasino or KaMenu was installed.
- Three more boots restored **24 rooms / 1,976 events / 14 game identifiers** in `target/standalone-smoke-20260929-012358/`, including all six Mahjong profiles and legacy Aeroplane. Seeds, options, hand contents, anchors and action history kept their meanings.
- A **60-second / 18-table** run in `target/soak-20260929-012347/` completed **368 actions**, 20 replay comparisons and one Connect Four round. Peak owned entities: **6,600**; after cleanup: **zero**. No undo cycle completed in this short run; directed tests cover it. The first run exposed a probe inventory omission for the new 36 ring segments; the probe now inventories the ring and private buttons explicitly, retaining exact entity-set and cleanup checks.
- No card-deck or rule changes: 6/9 art is underlined, while the current Last Card deck remains 1–8. Button labels use editable `table.mahjong.*` language keys. Saved data and custom menus are preserved.
- Source-geometry previews are approximate, not Minecraft screenshots. Native client glow, label legibility, animation smoothness, sound and client frame rate still require the [client checklist](acceptance.zh-CN.md). No Release or production deployment.

Verified JAR SHA-256: `0e3ddd7b43a4e1c0dbab99c4971811048cf9bfdef53ce3c93e228dc81c0e29f6`. Exact passing receipts accompany the local package in `verification.json`.

## Local build: 1.7.1-SNAPSHOT

- JDK 25 / Maven: **407 tests passed**, zero failures, errors or skipped tests. Python: **four migration-tool tests passed**. Added regressions cover original face geometry, private hands, hover/draw transitions, deck reach and occlusion, Chinese Xiangqi glyphs, Ludo label/shadow removal, layered menu callbacks and preservation of customized templates.
- The exact JAR passed three clean Purpur **26.2-2622** boots (create, restart and legacy-folder migration) in `target/standalone-smoke-20260928-213839/`. ServerGames, ServerMenu, ServerCasino and KaMenu were absent. Native Display checks included owner-only faces, spectator exclusion, real entity hover/return, four direction arrows and cleanup. Native Dialog checks covered rule pages, 25-room pagination, callbacks and permission rejection through simulated Player calls.
- A second three-boot run restored **24 rooms / 1,976 saved events / 14 game identifiers** in `target/standalone-smoke-20260928-214026/`, covering legacy Aeroplane, non-default rules, Last Card and all six Mahjong profiles. Room options, saved anchors, private hands and public tiles matched replay after restart and legacy-folder copying. Only synthetic fixture world UUIDs were remapped.
- The exact JAR completed **60 seconds / 18 tables / 379 actions**, 20 replay comparisons and one Connect Four round in `target/soak-20260928-213850/`. Peak owned entities: **6,595**; after cleanup: **zero**. No undo cycle completed in this short run; directed tests cover undo and recovery. This is lifecycle evidence, not a sustained performance benchmark.
- Detailed face art increases entity counts. Horizontal pixel runs are merged vertically, common backgrounds use one plane and unchanged cards reuse their entities. Client frame rate, legibility, hover feel and animation smoothness remain pending Minecraft acceptance.
- Room schema, rule options and history semantics are unchanged. Exact previous stock setup/room/hand templates adopt new layouts in memory; customized templates and installed bytes remain untouched.
- All bundled sounds remain registered (12 games / 20 cues). No connected Minecraft client or audio acceptance is claimed. PNGs are source-geometry previews with approximate material colors/fonts; the face contact sheet is original artwork, not a game screenshot. No Release or production deployment.

Verified JAR SHA-256: `930369d978f09aac463690f5b447407e216fad71a52cf44d0ebb00719a9cffb5`. Exact runtime receipts accompany the local delivery in `verification.json`.

## Local build: 1.7.0-SNAPSHOT

- JDK 25 / Maven: **383 tests passed**, zero failures, errors or skipped tests. Python: **four migration-tool tests passed**. New regressions cover room options, host/seat order and cancelled teleports, menu callbacks, private hands, Last Card, six Mahjong profiles, Ludo/Gomoku/Chinese Checkers options, dice timing and replay.
- Independent Mahjong review exercised **300 games / 27,090 actions** across six profiles, checking unique physical tile ownership, tile counts, scores plus Riichi sticks and finite progress. The random run included one Riichi declaration; it supplements the directed state/scoring tests and is not exhaustive hand enumeration. An additional independent scoring review passed seven edge cases alongside the 20 Riichi scoring tests.
- The exact JAR passed clean create/restart/legacy-folder migration on Purpur **26.2-2622**, in `target/standalone-smoke-20260928-193723/`. ServerGames, ServerMenu, ServerCasino and KaMenu were absent. Real Display checks covered owner-only card/tile faces, spectator exclusion and cleanup, plus the existing board models and dice. Native Dialog callbacks, setup/profile navigation, pagination and permission rejection used simulated Player calls.
- A second three-boot run restored **24 rooms / 1,976 saved events / 14 game identifiers** in `target/standalone-smoke-20260928-193917/`. It combined historical rooms (including legacy Aeroplane), non-default room rules, Last Card and all six Mahjong profiles. Options, anchors, side-tray selection, private hands, public rivers and melds matched replay after restart and legacy-folder copying. Synthetic world UUIDs were remapped only for the isolated fixture.
- The exact JAR passed a **60-second, 18-table** run in `target/soak-20260928-193723/`, with **370 actions**, 19 replay comparisons, a peak of 3,190 owned entities and **zero after cleanup**. No round or undo cycle completed in this short run; those paths have directed tests. This is a lifecycle check, not a sustained performance benchmark. Every test server exited normally.
- Sound registry/API checks dispatched 20 cues; Last Card and Mahjong action selection is also covered by unit tests. No connected client was used. The local fixture logged Windows performance-counter warnings and a blocked external Minecraft public-key request; plugin startup and probes completed.
- Source geometry previews show the side dice stand, Last Card and Mahjong. Materials/fonts are approximations. Actual Minecraft appearance, private visibility across real clients, clicking, animation feel and audible sound balance remain on the [client checklist](acceptance.zh-CN.md).
- The supplied reference JAR was inspected only for configuration and public rule names. Its code, assets and runtime were not incorporated. Regional defaults and differences are explicit in [Mahjong rules](mahjong.md).

Verified JAR SHA-256: `1f88aceafc30858f90a34a96bfd29a14bf3af60ed03d95467cea7392555b84a7`. Matching receipts and source commit accompany the local development package. **No Release or production deployment.**

## Local build: 1.6.1-SNAPSHOT

- Java: 200 tests passed, zero failures, errors or skipped tests. Python: four migration-tool tests passed.
- Nine sound regressions cover all catalog games and legacy flight, captures including en passant and stacked Ludo pawns, home arrivals, Reversi flips, Go scoring controls, successful human/bot actions, rejection/refresh/replay silence, one-time results, undo, volume boundaries and private turn eligibility/world/distance.
- Clean Purpur 26.2-2622 passed creation, restart and legacy-folder migration in `target/standalone-smoke-20260928-150725/`. On every boot the sound probe exercised 12 game profiles and resolved/dispatched all 18 cues through the real native sound registry/API. Native Dialog callbacks, permission rejection and model checks remained green. ServerGames, ServerMenu, ServerCasino and KaMenu were absent.
- The exact JAR passed a 60-second, 11-game run in `target/soak-20260928-150725/`, with 13 replay comparisons, peak 1,683 owned entities and zero after cleanup. All test servers stopped normally. This is a short lifecycle check, not a long-duration performance benchmark.
- Existing config files use defaults for omitted sound keys. Room schema, rules, menus and saved history are unchanged. Independent read-only review found no blocking issue.
- Server dispatch tests cannot establish audible client output, sound balance or synchronization with rendered animations. These remain on the [client checklist](acceptance.zh-CN.md); see [sound configuration](sounds.zh-CN.md).

Verified JAR SHA-256: `1ecf746abc3efd5229273ce2f2168cc44f04c32ebea8c2b49e2ca17088052a8a`. Matching runtime receipts and source commit accompany the local delivery in `verification.json`. No Release or production deployment.

## Local build: 1.6.0-SNAPSHOT

- Java: 191 tests passed, zero failures, errors or skipped tests. Python: four migration-tool tests passed.
- New coverage includes Ludo automatic initial deployment, captures, exact finish, no-move turn transitions, all four home lanes, deterministic replay, invalid turns and complete-turn undo preserving the dice stream.
- Presentation checks cover 2-player diagonal colors, one-click pawn moves, stacked-pawn choices, private landing previews, stable pawn entities, the new catalog and legacy flight translation overrides.
- Clean local Purpur 26.2-2622 completed create/restart/legacy-directory migration with the final JAR. Real Display checks include Ludo pawn entities and private previews, plus existing Connect Four/Reversi/Go/Chess checks. Native Dialog callbacks, pagination and permission rejection passed through simulated Player calls.
- A second three-boot run restored 12 rooms and 1,511 saved events, including both new Ludo and legacy Aeroplane, through restart and legacy-folder migration. Final evidence: `target/standalone-smoke-20260928-144809/` and `target/standalone-smoke-20260928-144930/`.
- The final JAR also passed a 60-second, 11-game continuous run with 23 Ludo actions and 13 replay comparisons. Peak owned entities: 1,664; after removing rooms: zero. No Ludo round finished during this short run. Evidence: `target/soak-20260928-144810/`.
- Existing Aeroplane remains available to restoration/replay only; the new game kind is `ludo`. Room schema 1 and existing configuration/menu files are preserved.
- A preceding gameplay build also completed 240 seconds across 11 games, including 89 Ludo actions, 11 undo cycles and 33 replays, with zero remaining entities after removal. This earlier JAR is not the final delivery; final-JAR receipts are recorded separately.
- Model PNGs are source-geometry renders using approximate material colors and fonts. No connected Minecraft client or production load acceptance is claimed. See [client checklist](acceptance.zh-CN.md) and [Ludo rules/migration](ludo.zh-CN.md).

Exact final-JAR hashes and standalone, snapshot-recovery and continuous-play receipts accompany the local delivery in `verification.json`.

Verified JAR SHA-256: `37a83052ba37297621b7b9fd479b27a2a26b62db9ab3aa861d2a01f7dad9cdae`.

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

To check a snapshot from that synthetic fixture, copy its `plugins/3dtabletop/rooms.json` while the games are running, then pass the copy to `run_standalone.py --rooms-snapshot path/to/copy.json`. Synthetic fixtures with 1–32 rooms are accepted, including legacy Aeroplane and regional Mahjong; all players must be bots without return locations. The runner keeps the input unchanged, maps its anchors to the fresh test world, and checks room IDs, seeds, seats, positions, saved history and rule state on both restart and legacy-folder migration. Each boot reads its own new log so a previous success marker cannot end the next boot early.

The packager requires a passing three-boot receipt whose SHA-256 matches the exact local JAR. Add `--soak path/to/soak/receipt.json` and `--snapshot path/to/snapshot/receipt.json` to include the additional checks; incomplete, failed or mismatched receipts are rejected. Corresponding source comes from Git-tracked files; stage/commit intended new source before packaging. CI builds/tests and uploads development artifacts only; it does not create tags or Releases.

## Client acceptance checklist

- Connect Four: round discs, rack supports and private green landing outline visible from either side; move between columns, fill a column, observe the drop and then win a game.
- Reversi: flip several stones, observe both colors and interrupted consecutive updates; confirm the visible final face matches the game.
- Go/Chess: repeatedly mark/unmark a dead group without stone jumps; inspect opposing knight headings and stone/base seams.
- Text: default English, custom MiniMessage, old `&` colors, long names/translations, leave-confirmation label and menu title.
- Flow: room pages, rules/help, direct menu placements, create/join/ready, bots, legal and denied actions, spectate, undo, rematch, leave, restart and resume.
- Table area: create adjacent tables, try an overlapping placement, open a new table immediately and use both upper rack faces; walk more than six blocks away or change worlds and check collision/hunger behavior.

Actual client visuals, smoothness, font appearance and input feel remain pending maintainer acceptance. Production deployment and Release publication are not part of this delivery.
