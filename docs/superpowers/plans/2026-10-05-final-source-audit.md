# Current-source audit and cleanup

Baseline: `35053a294d8cbe4505e3db307eab0dff5e1eda6d`, 1.10.22-SNAPSHOT.
Authorized scope: the Tabletop plugin, current tooling, resources and documentation.
Review window: 2026-10-05 18:57 through 19:57 UTC.

## Constraints

- Preserve current games, input order, private visibility, geometry, sounds and stored identity.
- Keep source, diagnostics and comments in English; translate player messages in catalogues.
- Remove obsolete project branding and genuinely retired implementation paths.
- Preserve upstream copyrights and full licenses; historical release evidence stays historical.
- Source synchronization and isolated test-server deployment are authorized; no Release.

## Tasks

1. Inventory all production sources and inspect current documentation, dependencies and branding.
   Verify: canonical package/command/menu identity; no obsolete runtime imports or templates.
2. Read foundational rules, room persistence, message handling and lifecycle.
   Fix demonstrated issues with regression tests; simplify stale worker-related replay accounting
   without changing accepted history limits or replay semantics.
3. Read game implementations, optional model bridge, input routing and rendering ownership.
   Prioritize mutation-free rejection, deterministic replay, stale callbacks, cleanup and idle work.
   Record inspected paths and findings; do not invent abstractions merely to produce a diff.
4. Review the whole final diff and run package, asset checks, isolated native/packed restarts and soak.
   Verify the exact final JAR and source commit. Deploy after backup, preserve unrelated data,
   inspect actual test-server files and save a Chinese report and local delivery.

## Review focus

Translations outside catalogues; old names in active comments/notices; current docs that still
advertise removed menus/converters; repeat serialization of growing histories; language reload
atomicity; optional dependencies; deterministic card rules; hover/model reuse; entity cleanup;
preservation of current rooms and map IDs. Read upstream logic as retained third-party sources,
and keep attribution when updating project-owned adaptation comments.

## Verification

Watch new behavioral regressions fail before their fixes. Run the relevant focused test suites
after each fix, then a clean full package. Resource/model bytes should stay identical unless a
demonstrated model issue requires a change. Runtime evidence must match the final shaded JAR.

## Progress

- 18:57 UTC: started; clean worktree; 124 production Java files, 27,771 lines inventoried.
- First pass: stale current-documentation claims, old adaptation comments and worker terminology
  found. No unreferenced private methods found by lexical candidate scan; manual review continues.
- Bottom-up review: room persistence/replay, rules and options, language loading, table geometry,
  audience/privacy, input callbacks, current menus, native/packed rendering, audio and packaging.
- Removed unreachable hand/source menus and nine keys per locale, the generic legacy Mahjong
  renderer, unused chess undo snapshots and old project-owned branding. Retained legal attribution.
- RoomHistory owns and caches UTF-8 accounting; unchanged private card faces survive committed
  moves. Specific pack-required and placement errors now retain their localized messages.
- Mahjong solver control flow and names expanded; equivalent standard wins avoid special-case work.
- Red/green checks for errors, replay ownership/accounting and private face reuse passed.
- Final package: 632 Java tests, zero failures/errors/skips. Python: 39 checks passed.
- Differential checks: 98 games / 15,451 moves; 7,570 Mahjong solver shapes/waits, all equivalent.
- Native final-JAR create/restart/restart and 20-room snapshot restore passed; packed and final
  five-minute soak verification in progress. Initial sandbox loopback denial was rerun in isolation.
