# Plugin Responsibility Packages

**Goal:** Classify the remaining Java implementation and tests by responsibility.

**Scope:** Preserve game algorithms, assets, language, callbacks, permissions, entity behavior and
room/map schemas. Retain the plugin and offline replay entry points at their existing names.
Continue the previously authorized directory organization, inline, without subagents.

## Layout

- `room/`: room state, history, persistence, occupancy, round actions and turn policy.
- `bot/`: bot action selection.
- `menu/`: native dialogs, menu sessions, poker raise input and command suggestions.
- `interaction/`: table/world input, placement orchestration, lobby and focus comfort.
- `text/`: language catalogues and shared player-facing presentation.
- `render/`: common table views, geometry, audiences, board maps and furniture.
- `render/cards/`, `render/mahjong/`, `render/dice/`: corresponding artwork and physical views.
- `resource/`: optional CraftEngine bridge and resource-pack delivery.
- `audio/`: committed-action sound selection and playback.
- Keep existing `rules/<game>/` and `ui/` utilities; mirror related tests in the new packages.
- Keep Tabletop3D and RoomReplayVerifier at the root as existing executable entry points.

## Tasks

- [x] Inventory declarations and references against clean baseline 8ca2eacd.
- [x] Move files and update imports, reflection probes and packaging references.
- [x] Expose only the declarations required by actual callers across package boundaries.
- [x] Compare normalized Java content and verify access changes separately.
- [x] Run all Java/Python tests and inspect the final JAR for retired class paths.
- [x] Verify native/packed startup, previous-build room recovery and entity cleanup.
- [x] Update source guides, review the scoped diff and sync source without publishing a Release.
- [x] Deliver locally and back up/update the authorized isolated test server.

## Rulings

- Existing method bodies and state ownership are preserved. Package visibility changes are recorded
  individually; no speculative interfaces, reflection wrappers or duplicate compatibility classes.
- Existing tests and bytecode/runtime checks verify relocation; no tests that merely mirror paths.
- The user authorized classification and continuation of local delivery/source sync/test updates.

## Progress

Moved 49 implementation files and 63 test files. All 222 normalized Java bodies match baseline 8ca2eacd; 1,342 private declarations remain unchanged. The access audit records 394 production and 32 test declarations that cross the new boundaries. Root executable names and absolute resource lookups are preserved.

Java: 632 tests, zero failures/errors/skips. Python: 39 tests passed. Native and packed modes each passed three boots and recovered the previous build's 20 rooms, 17 game kinds and 416 saved actions. The 60-second soak completed 21 replay checks and left zero owned entities. The final JAR contains no retired flat implementation paths; 16 unchanged non-code resources match the previous artifact.

Source ea69e6cb synchronized to feat/craftengine-tabletop; GitHub run 37407982218 passed. The authorized server 25589 was fully backed up at /var/opt/minecraft/crafty/test-deployment-backups/tabletop-types125-20261006-031443 and updated to 1.10.25. Startup, 234 models, Chinese reload, HTTPS digest and protected data checks passed. Configuration, language files, resource assets and map IDs were preserved. No Release was published; no Minecraft client or FPS measurement was performed.
