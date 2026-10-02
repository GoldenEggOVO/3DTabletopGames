# Doudizhu

Date: 2026-10-02. Parent: [models and card games](2026-10-02-models-and-card-games-design.md).

## Defined rules

Implement a classic three-player, 54-card game. Deal 17 cards per player; three bottom cards stay private until the landlord is determined. Select the first bidder using the persisted game seed. Each seat bids once, choosing pass or a score of 1, 2, or 3 above the current highest score. A bid of 3 ends bidding immediately. If every player passes, shuffle and deal again deterministically. Do not impose forced bidding based on particular hands.

The highest bidder becomes landlord, takes the bottom cards, and leads. Others are farmers and share victory. A player must lead when the trick has no active combination. Otherwise they may pass or play a strictly larger compatible combination. After both other players pass, the last successful player leads a new trick.

Support singles, pairs, triples, triple with single/pair, straights of at least five ranks, consecutive pairs of at least three ranks, consecutive triples of at least two ranks, airplanes with single/pair wings, four with two singles/two pairs, bombs, and the two-joker rocket. Sequences use ranks 3 through A; exclude 2 and jokers. Ordinary comparisons require equal combination type and length; compare the main group, not the attachments. Rocket beats everything; bombs beat ordinary combinations and compare by rank.

Wing policy is explicit: airplane single wings may contain a pair but cannot include a main triple rank or both jokers together; pair wings use distinct ranks outside the main triples. Four-with-two singles may be a pair, but cannot be the two-joker rocket. Four-with-two pairs requires two distinct pair ranks. A combination that admits multiple decompositions is accepted if any valid decomposition beats the previous play.

The first empty hand ends the game. Base score is the winning bid; each bomb or rocket doubles it. Double for spring when farmers have made no successful play and landlord wins, or landlord has made only its initial successful play and farmers win. The landlord gains/loses twice the base result, and each farmer receives the opposite single result. Scores are match results, not an economy balance.

These are the plugin's selected rules. The [publisher rule reference](https://www.80166.com/guide/rule/ddz.html) informs common combinations and comparison; its forced bids, regional doubling, and all-pass dissolution are not adopted.

## Table interaction

Sort by game rank and stable card identity. Click to select/deselect cards; keep selections local to their owner and clear stale selections after the room revision changes. The table provides bidding controls during bidding and Play, Pass, and Clear selection during play. Lead turns never offer Pass. Invalid selected groups show a localized reason without changing state.

Publish landlord status, bottom cards after bidding, each seat's remaining count, latest played groups, current turn, and multiplier. Only the owner sees their private hand. Grouped plays are arranged separately by seat so passes do not erase the last active combination.

## Implementation and acceptance

Use a pure `DoudizhuGame` and a separate named combination classifier. Validate an explicit submitted selection directly; do not enumerate every subset of a 20-card hand just to process a click. Supply bounded legal candidates for the existing bot flow, based only on the bot's hand and public state.

Test every supported shape, invalid sequences/attachments, ambiguity, comparison, bid termination, all-pass redeal, trick reset, team victory, scoring, bots, and duplicate-card rejection. Verify seeded replay through bidding and redeals, private-state filtering, multi-card selection after refresh, and native/packed displays. Existing game rules and stored rooms continue to load unchanged.
