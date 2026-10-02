# Verification and acceptance

## Local build: 1.9.4-SNAPSHOT - Riichi assistance and simpler room navigation

- JDK 25 / Maven package: **549 tests passed**, zero failures, errors or skipped tests. Python resource-pack tools: **14 tests passed**.
- Main-menu room browsing is a full-width clickable header and is mutually exclusive with resume. Leave Room is direct; player undo commands, callbacks, state, timer branches, sounds and translations are removed. Saved history, replay, room recovery and rematches remain supported.
- Riichi has five private Dora slots, revealed faces plus concealed backs, with deposit/honba counts below. Four private rim controls provide automatic sorting, automatic wins, skipped calls and drawn-tile discards. Hover shows only the function description in the action bar. Controls reuse their entities and distinguish enabled/disabled states using brightness 15/7.
- Regressions cover legal action priority, pausing at wins, persistent unsorted order, owner-only displays, control reuse/removal and settled winner hands. Ron includes its physical claimed tile; multiple Ron hands reveal only after all responses settle. Tsumo cannot append a prior offered tile.
- The exact shaded JAR passed three isolated native Purpur 26.2-2622 boots in `target/standalone-smoke-20261002-152831/`. Probe coverage includes native Display metadata, five Dora slots, private assistance/counters, room recovery and unseated browser navigation; proxy players do not verify connected-client input or visuals.
- The same JAR passed three CraftEngine 26.8.2 boots in `target/standalone-smoke-20261002-153337`; all 111 registered item models loaded.
- Resource-pack assets are unchanged: 111 models and nine sounds. Client visual/input acceptance remains pending; no production deployment or public Release is claimed.

Verified JAR SHA-256: `42d6ab2210e4a08ef91c6af7cac676ffd17b45387f20c7d0b2592108221eca0e`.

## Local build: 1.8.7-SNAPSHOT — Fixed Spectator camera, longer sticks and model counts

- JDK 25 / Maven package: **510 tests passed**, zero failures, errors or skipped tests. New regressions first failed for fixed mode/target, move cancellation, read-only clicks, stick length, interrupted-session recovery, rejected mode/camera transitions, camera eye clearance and spectator-menu teleports.
- The reported 1.8.6 movement issue was traced in the actual Purpur packet listener: changing a movement destination calls `CraftPlayer.teleport(..., PLUGIN)`, which the old focus cleanup interpreted as an external teleport. Movement cancellation instead uses Paper's internal correction. The old proxy probe did not exercise that native packet path.
- Shift now temporarily enters Spectator mode and attaches a private invisible marker ArmorStand. Both position and orientation stay fixed; view-only clicks cannot play tiles. The marker eye height is zero, so camera height is center + 2.15, preserving the prior sneaking eye position rather than raising the view again. The original game mode, flight, gravity, visibility and pose are restored on release.
- Regressions cover all four seats, rejected camera attachment, restoration veto/retry, quit/close/death/external-teleport cleanup, external game-mode authority, persisted recovery, three-block-high rooms and blocked eye space. Non-internal SPECTATE teleports and camera target switches are cancelled; command/plugin teleports remain authoritative. Current client interaction delivery is a separate acceptance gate.
- Riichi sticks are **0.24 blocks** long instead of 0.12, still two entities each. The existing raised HUD and previous artwork are retained.
- Model counts were regenerated against the final compiled classes and matched the measured JSON. Native Color Eight standing faces use 80–122 entities, backs 77; a five-player starting example has 4,643 server entities and 2,283 visible to seat 0. The 30-card geometric sample has 4,910 visible entities. See the model-count report for scope, Mahjong counts and animation overhead. **No client FPS or server performance profile was collected**, and no model simplification was implemented.
- The exact shaded JAR passed three isolated Purpur **26.2-2622** boots (create, restart and legacy migration) in `target/standalone-smoke-20261001-214624/`. The real event-bus/world probe uses proxy players and checks the marker camera, zero marker eye height, movement cancellation, fixed equivalent angles, spectator-menu teleport rejection, release and cleanup. Paper normalizes yaw 180 to -180; the probe compares equivalent angles. It does not exercise connected-client physics.
- Independent code and Purpur bytecode review found no remaining important issues after the regressions above. Actual fixed-camera feel, equipment disappearance, client physics, native Shift + right-click packet delivery and Mahjong glint remain Minecraft client acceptance items. No production deployment or public Release.

Verified JAR SHA-256: `779e4ef07aa605efdac24cc526d5e16d2e801dd5776763f99c801ee4f3d9b9e8`.

## Local build: 1.8.6-SNAPSHOT — Closer locked Mahjong camera and raised HUD

- JDK 25 / Maven package: **499 tests passed**, zero failures, errors or skipped tests. Before implementation, regressions failed at the old camera radius (1.65 rather than 0.65) and floating HUD height (0.85 rather than 1.25).
- Four-seat regressions verify the exact 1-block inward and 0.2-block downward move, initial table aim and restoration of entry pose, gravity and visibility. Existing movement checks keep XYZ fixed while allowing yaw/pitch, including attempted horizontal and vertical movement. Shift menu and Dialog input-latch regressions remain passing.
- Round/current-player/countdown and last-discard displays are raised by 0.4 blocks; flat remaining-count and wind inscriptions retain their surface heights. The user explicitly accepted standing hands and apron indicators behind this closer camera.
- Independent review found no important code issues; current mode documentation was updated to remove obsolete standing-hand visibility promises.
- The exact shaded JAR passed three isolated Purpur **26.2-2622** boots (create, restart and legacy migration) in `target/standalone-smoke-20261001-205055/`. The real event-bus focus probe checks the exact closer coordinates, position locking, free yaw and restoration with proxy players. This does not verify native client physics or camera feel. Minecraft client acceptance remains pending. No production deployment or public Release.

Verified JAR SHA-256: `f197f64b1f8977cbce305a96b19869308092a714d4fdc2abc4053f228275797f`.

## Local build: 1.8.5-SNAPSHOT — Shared room browser and table menu gestures

- JDK 25 / Maven package: **498 tests passed**, zero failures, errors or skipped tests. Missing shared browsing, duplicate setup browsing, ordinary Color Eight menu clicks and focused Mahjong menu release were observed failing before the changes.
- Regressions cover a conditional catalog entry, mixed game/code labels, game setup pages without duplicate browsing, right-click event routing, Mahjong focused left-click tile play, waiting-room menus and release before menu opening. Dialog input reset cannot rearm held Shift; closing synchronizes restored client input without entering focus, including releasing Shift inside the menu. Automatic menus still restore gravity, visibility and the entry pose. Existing per-game pagination, stale callbacks and room gates continue passing.
- Independent review identified and corrected the waiting-room route and the client's Dialog key reset sequence. No remaining important review findings.
- The exact shaded JAR passed three isolated Purpur **26.2-2622** boots (create, restart and legacy migration) in `target/standalone-smoke-20261001-164936/`. Each boot exercises the shared mixed-game browser, pagination, game/code labels and setup pages without duplicate browsing through native Dialog callbacks. Existing model, privacy, sound and focus checks also pass. Minecraft client gesture feel and existing Mahjong glint appearance remain client acceptance items. No production deployment or public Release.

Verified JAR SHA-256: `82feec55c3f70935db606128cd3545d2c5759e19268bdbe59a33aa24ff1d1ac6`.

## Local build: 1.8.4-SNAPSHOT — Private inset Dora indicators and opaque foil substrate

- JDK 25 / Maven package: **485 tests passed**, zero failures, errors or skipped tests. Privacy and opaque substrate regressions were first observed failing against the old behavior.
- Dora panels now belong to each seated player's private view, with no public indicator entities. Regressions cover all four apron poses, spectator exclusion, independent owner updates after another indicator is added, missing-entity repair and removal without affecting another player's panel.
- Preserve indicator aiming, matching tile highlights and remaining-count hints for the owner. Independent review identified and corrected the lost hover path; a four-seat ray regression rejects other players aiming at the same private panel.
- The user reports that 1.8.3's native glint is not visible, while ordinary enchanted items are visible. The earlier metadata/alpha investigation did not establish successful client rendering. Inspection of the local 26.2 client renderer confirms foil submission and UV preservation but does not prove the reported missing-glint cause.
- Change only the foil substrate from translucent white stained glass to opaque smooth quartz, keeping the thin face outside the original body and behind the original strokes. This bypasses the translucent item path without adding a resource pack. This is a correction candidate; **actual client glint visibility remains unverified**.
- The exact shaded JAR passed three isolated Purpur **26.2-2622** boots (create, restart and legacy migration) in `target/standalone-smoke-20261001-161839/`. All three check live private indicator entities, opaque enchanted ItemDisplay metadata and cleanup with proxy players. These checks do not render the client glint. No production deployment or public Release.

Verified JAR SHA-256: `020b7158784064e6073b21916cd1257208eec376a379eb5967b73b5fde1d216e`.

## Local build: 1.8.3-SNAPSHOT — Mahjong artwork and bonus glint; Color Eight interaction

- JDK 25 / Maven package: **483 tests passed**, zero failures, errors or skipped tests.
- Regressions cover four outward-facing inset indicator groups, six-tile river rows from the center toward each owner, native rotated character/honor/flower strokes and a focus camera outside the indicator faces. Only kuikae dims Mahjong tiles; glyph colors remain intact.
- Bonus hints use only public Dora indicators and red fives in Riichi. Owner-only enchanted ItemDisplays do not reveal other hands or hidden Ura Dora. Indicator changes remove obsolete overlays; concealed backs and indicator views remain unmarked. White stained glass sits between the tile body and original strokes to provide complete alpha coverage for the client's native glint.
- Color Eight regressions check parallel diagonal overlap at every seat for 2–5 players, all 54 cards individually aimable, the 0.085-block lift, original artwork retained at brightness 7/7, four private color buttons, exhausted draw supply, timeout legal plays and historical exhaustion-pass replay.
- Independent review caught ordinary glass alpha holes that prevented full-face glint and a Shift camera inside the new indicator faces; both were corrected. No remaining important review findings.
- The exact shaded JAR passed three clean Purpur **26.2-2622** boots (create, restart, legacy-folder migration) in `target/standalone-smoke-20261001-153306/`. All boots also passed `BOARDS_DORA_GLINT_PASS`, checking live owner-only ItemDisplay metadata and cleanup with proxy players. No connected Minecraft client is implied.
- `mahjong-layout-preview.png` uses production stroke geometry and river poses with a schematic apron example; `color-eight-layout-preview.png` uses production artwork and parallel hand poses with approximate projection. Neither is a Minecraft screenshot. Actual glint/material appearance, Shift camera feel and dense-hand aiming remain client acceptance items. No production deployment or public Release.

Verified JAR SHA-256: `288eb361ec152c834c16fe70dc10e34e4e2292ae66a1166743a8ed3f193ac5e5`.

## Local build: 1.8.2-SNAPSHOT — Mahjong kuikae brightness

- JDK 25 / Maven package: **474 tests passed**, zero failures, errors or skipped tests.
- The new table regression uses a real Riichi game to perform Chi, checks both kuikae-forbidden tile types at brightness 7/7 with every original model material retained, then confirms brightness 15/15 returns after a legal discard. Rule assertions also cover Pon, other seats and consumed physical tile IDs.
- Waiting turns and Riichi selection stay normally lit; ineligible actions are still rejected. Existing Color Eight gray-material behavior is unchanged. Independent code review found no blocking issues.
- The exact shaded JAR passed three clean Purpur **26.2-2622** boots (create, restart, legacy-folder migration) in `target/standalone-smoke-20261001-142814/`. No production deployment or public Release.
- Minecraft client appearance of the chosen dim brightness remains pending; automated Display API checks do not confirm client visuals.

Verified JAR SHA-256: `239363df8ca0630530117ecffb83a2e53b4e42754fd52d7d0596e34210e0597c`.

## Local build: 1.8.1-SNAPSHOT — Native shape refinement

- JDK 25 / Maven package: **472 tests passed**, zero failures, errors or skipped tests. Added regressions check circular rim coverage, text-free private color buttons, native tile bounds/red fives/retained glyph ink, 6/9 underlines and actual rotated-polygon overlap between different colors at the same depth.
- Existing regressions still aim at all 54 cards at each of five seats, validate owner-only controls and exercise unchanged rules and replay behavior.
- Independent review caught coplanar multi-color intersections in Swap, the bird and flowers. Their depths were separated and the overlap regression passes. Textured round-table bars have slightly different top heights to avoid coplanar faces.
- The exact shaded JAR passed three clean Purpur **26.2-2622** boots (create, restart and legacy-folder migration) in `target/standalone-smoke-20260930-121738/`, including private native hand/tile models, display cleanup and existing menu/sound checks.
- `native-shapes-preview.png` renders production HandModels, HandTable poses and RoundCardTable parts. It is a source geometry preview with approximate material colors, not a Minecraft screenshot. Client material/glow appearance, dense-hand frame rate and camera/interaction feel remain pending. No production deployment or public Release.

Verified JAR SHA-256: `f3ca28c969423b067ec16f30be1b30914305128f4c9b9f5f1916a0ecdcbcc66e`. Exact receipts accompany the local acceptance package.


## Local build: 1.8.0-SNAPSHOT — Color Eight

- JDK 25 / Maven package: **467 tests passed**, zero failures, errors or skipped tests. New regressions cover the 54-card rules, 2–5 players, one voluntary draw, final-card effect ordering, swap matching, recycling, replay version 2, private four-color selection, offline/online 30-second turns and old language overrides.
- Five-player dense-hand regression aims at all **54 cards at each of five seats**. It exposed rotated world AABB overlap; card hit detection now transforms the ray to the card's local coordinates. Mahjong retains its previous hit path.
- The exact JAR passed three clean Purpur **26.2-2622** boots in `target/standalone-smoke-20260929-224445/` (create, restart, legacy-folder migration), including native five-player hand construction, privacy, display cleanup and existing menu/sound checks. No additional plugin dependency was installed.
- Independent review caught old Last Card histories blocking unrelated room restoration and the offline 5-second card timer; both were repaired and covered by regressions. Old card histories are now fully backed up before being omitted, while other games continue restoring.
- `native-model-preview.png` is generated from production HandArt, HandTable fan poses and RoundCardTable parts; it is a source preview, not a Minecraft screenshot. Client glow, exact fonts/materials, animation, five-player aim feel and frame rate still require the client checklist. No production deployment or public Release.

Verified JAR SHA-256: `48c419b2c690fd6100bc99245660e30373934f6f53561f575605cceab96f9033`. Exact passing receipts accompany the local acceptance package.


## Local build: 1.7.5-SNAPSHOT

- JDK 25 / Maven package: **467 tests passed**, zero failures, errors or skipped tests; Python: **four migration tests passed**. Regressions cover stable pre-draw tile positions, the separate rightmost draw, continuous-click protection through other turns and temporarily empty slots, illegal-tile gray/restoration, armed Riichi, two-stage calls, exact No Yaku boundaries, necessary Go/Last Card/Sichuan actions and standardized Close rendering/callbacks.
- The exact JAR passed three clean Purpur **26.2-2622** boots in `target/standalone-smoke-20260929-165916/` (create, restart, legacy-folder migration). Native Display, private hand controls, required menu actions, 25-room pagination and all **27 vanilla sound cues** passed. Shift used real events/world clearance with a stateful Player proxy; connected-client physics and camera/audio feel remain acceptance items.
- Three further boots in `target/standalone-smoke-20260929-170112/` restored **22 rooms / 1,896 saved events / 14 game identifiers**, including all four supported Mahjong profiles. Only fixture world UUIDs were remapped.
- The **60-second / 16-table** run in `target/soak-20260929-165917/` completed **331 actions**, 18 replay comparisons and one Connect Four round. Peak owned entities: **4,631**; after cleanup: **zero**. No undo cycle completed in this short run; directed tests cover those paths.
- Earlier probe assertions still expected the removed full-move menu and the previous Shift height. They were updated to check the physical Connect Four action, absence of that menu and the current focus pose. Layout review caught wind/meld overlap and concealed-row occlusion of frame indicators; geometry regressions now cover their corrected positions. Review also caught necessary state actions and empty-slot clicks omitted from the initial menu/input changes; these were repaired before the final build.
- Close rendering is uniform even for customized layouts; installed YAML bytes and other custom styling remain untouched. No room-schema or history change. The preview uses production geometry with a synthetic state, approximate fonts/materials and simulated text backface culling; native glow is not drawn. Minecraft visual/audio/interaction acceptance is pending. No Release or production deployment.

Verified JAR SHA-256: `16f07e1991d5b2ccc89b7e180a0faffa68e076fcd1ed190d25f5649e15576adf`. Exact passing receipts accompany the local package in `verification.json`.

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

For continuous model/replay testing, `python tools/standalone-probe/run_soak.py --seconds 3600` prepares another isolated fixture on `127.0.0.1:25618`. It runs all eleven game kinds with bots, completes and restarts rounds, periodically replays moves, compares tracked model entities with live entities and checks cleanup after removing the rooms. It uses the same prepared server/cache and EULA prerequisites as the short probe. Duration is bounded to 60–7200 seconds.

To check a snapshot from that synthetic fixture, copy its `plugins/3dtabletop/rooms.json` while the games are running, then pass the copy to `run_standalone.py --rooms-snapshot path/to/copy.json`. Synthetic fixtures with 1–32 rooms are accepted, including legacy Aeroplane and regional Mahjong; all players must be bots without return locations. The runner keeps the input unchanged, maps its anchors to the fresh test world, and checks room IDs, seeds, seats, positions, saved history and rule state on both restart and legacy-folder migration. Each boot reads its own new log so a previous success marker cannot end the next boot early.

The packager requires a passing three-boot receipt whose SHA-256 matches the exact local JAR. Add `--soak path/to/soak/receipt.json` and `--snapshot path/to/snapshot/receipt.json` to include the additional checks; incomplete, failed or mismatched receipts are rejected. Corresponding source comes from Git-tracked files; stage/commit intended new source before packaging. CI builds/tests and uploads development artifacts only; it does not create tags or Releases.

## Client acceptance checklist

- Connect Four: round discs, rack supports and private green landing outline visible from either side; move between columns, fill a column, observe the drop and then win a game.
- Reversi: flip several stones, observe both colors and interrupted consecutive updates; confirm the visible final face matches the game.
- Go/Chess: repeatedly mark/unmark a dead group without stone jumps; inspect opposing knight headings and stone/base seams.
- Text: default English, custom MiniMessage, old `&` colors, long names/translations, leave-confirmation label and menu title.
- Flow: room pages, rules/help, direct menu placements, create/join/ready, bots, legal and denied actions, spectate, rematch, leave, restart and resume.
- Table area: create adjacent tables, try an overlapping placement, open a new table immediately and use both upper rack faces; walk more than six blocks away or change worlds and check collision/hunger behavior.

Actual client visuals, smoothness, font appearance and input feel remain pending maintainer acceptance. Production deployment and Release publication are not part of this delivery.
