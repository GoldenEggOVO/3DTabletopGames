# Tabletop models and new card games

Date: 2026-10-02. Baseline: `1.9.5-SNAPSHOT`, commit `0754d18876eafafc9d0aad2e57a260b751ce4411`.

Status: written design for user review. No product implementation has started.

## Agreed scope

The user approved resource-pack models for the existing tabletop games and the addition of Doudizhu, Liar's Bar (card mode), and Texas Hold'em. The latest instruction prioritizes models and these games. The separate final code cleanup remains deferred. Existing Mahjong and Color Eight visuals and controls must retain their approved behavior.

This architectural expansion has four independently verifiable subprojects, with individual specifications:

1. [Board and playing-card models](2026-10-02-board-and-playing-card-models-design.md).
2. [Doudizhu](2026-10-02-doudizhu-design.md).
3. [Liar's Bar card mode](2026-10-02-liars-bar-design.md).
4. [Texas Hold'em](2026-10-02-texas-holdem-design.md).

Implement models first, then the games in this order. Each subproject receives its own implementation plan and verification checkpoint. Only changes needed to support these features belong in this batch; broad rewrites are excluded.

## Shared experience

- Keep vanilla, resource-pack, and mixed rendering modes, including the existing first-page resource-pack switch.
- Keep direct world interaction. Card selection and game actions appear at the table, without adding generic menu entries to play from a hand.
- Large hands use overlapping cards and the existing modest hover lift. Selected cards have a distinct selection indicator, independent of hover.
- Preserve private hands, rooms, host/ready/start flow, bots, reconnect, and persisted game recovery.
- All new player text uses semantic keys in `languages`; source identifiers, comments, and diagnostics stay English.
- Newly added games use original visual assets in the `tabletop3d` namespace.

## Verification and delivery

Verify pure rules independently of Paper, then verify selection, mixed rendering, private information, accepted-action replay, and full game completion. Resource validation must check model IDs, textures, orientations, and CraftEngine registrations. Compare representative native and resource-pack entity counts; do not claim an FPS improvement without client measurements.

Provide local artifacts and Chinese test notes. Apply finished, verified changes to the previously authorized independent test server using a backup and clean shutdown. Client appearance and interaction remain a separate acceptance step.

After the requested implementation is complete, synchronize the reviewed source to the existing GitHub repository and verify the remote commit. Do not create a Release or release tag.
