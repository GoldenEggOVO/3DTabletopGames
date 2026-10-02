# Liar's Bar card mode

Date: 2026-10-02. Parent: [models and card games](2026-10-02-models-and-card-games-design.md).

## Defined adaptation

Implement only card mode, for two to four players. The [developer's official description](https://store.steampowered.com/app/3097560/Liars_Bar/) confirms face-down declarations, bluff challenges, six-chamber roulette, and resetting cards after roulette. It does not document every edge below; these are explicit rules for this plugin rather than a claim of an exact current-game replica.

Use a 20-card deck containing six Aces, six Kings, six Queens, and two Jokers. Every physical copy has its own stable identity. Each round deals five cards to each surviving player; unused cards stay hidden. Choose A, K, or Q as the table rank and display it centrally. Jokers match any declared rank.

On their turn, players select one to three cards and play them face down as the declared rank, or challenge the immediately previous play. A first-turn challenge is unavailable. Playing another group accepts the previous group; older plays cannot be challenged. A challenge reveals only the challenged group. If any card is neither the declared rank nor a Joker, its player loses the challenge; otherwise the challenger loses.

Players with empty hands are skipped for further plays but their final group can still be challenged. If only one surviving player has cards, that player must challenge the previous group; Play is unavailable. Thus a round cannot silently finish by emptying all hands.

At match creation, independently choose one fatal chamber out of six for each player, using the persisted seed. Each challenge loss advances that player's chamber once without resetting it between rounds. The fatal chamber eliminates the seat from the match. Survival still ends the current round and redeals to all surviving seats. The losing seat starts the next round if alive, otherwise the next surviving seat starts. Once only one seat remains, it wins.

Roulette is a game-state and visual effect: elimination does not damage the Minecraft player, change their inventory, or remove them from the room. Eliminated seats may watch public state.

## Table interaction and privacy

Show declared rank, live/eliminated seat status, previous group count, current turn, and public chamber-attempt counts. Private hand faces are visible only to the owner. Do not expose unchallenged groups through public info, model IDs, hover text, or bot inputs.

Click cards to select, then use Play; use a table Challenge button for the previous group. Show a short reveal and chamber result before rebuilding the next round's display. The accepted challenge resolves authoritatively once; visual delay is not a second rules event and cannot accept duplicate actions against the old group.

## Implementation and acceptance

Use an independent pure `LiarsBarGame`, shared playing-card identities/artwork, and the existing room lifecycle. Bots may bluff and challenge using their own cards and public counts, never the concealed previous group.

Test truthful/Joker/false groups, first-turn challenge, accepted old plays, multi-card bounds, duplicate IDs, empty-hand skipping, forced final challenge, survival, elimination, two-player termination, six-chamber progression, seeded replay, and private-state filtering. Verify direct controls and native/packed reveal placement without world-player damage.
