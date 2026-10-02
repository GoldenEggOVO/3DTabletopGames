# Texas Hold'em

Date: 2026-10-02. Parent: [models and card games](2026-10-02-models-and-card-games-design.md).

## Defined match

Implement two to six players, a standard 52-card deck, and no-limit betting. Every seat starts with 1,000 match chips; fixed small/big blinds are 5/10. Chips exist only in the room's persisted match state. Dealer position rotates between hands, busted seats become spectators, and the last seat with chips wins. No rebuy, ante, blind increase, or rake is included in this first mode.

Deal two private hole cards. Use pre-flop, three-card flop, one-card turn, and one-card river betting rounds, with a burned card before each community stage. Pre-flop action starts after the big blind; later rounds start at the first active seat after the dealer. Heads-up dealer posts the small blind and acts first pre-flop and last post-flop.

Legal actions are fold, check, call, bet/raise to an integer total, and all-in. Check is legal only when nothing is owed. Minimum opening bet is the big blind; a full raise must increase the current wager by at least the last full bet/raise amount. A short all-in is allowed, but does not itself reopen raising for players who have already acted. Cumulative increases reopen raising when the amount a returning player faces reaches a full raise relative to their last action. Reject out-of-turn actions and invalid amounts without taking chips.

A betting round ends only when every non-folded, non-all-in seat has acted and matched the wager. If all but one seat fold, award the pot without revealing its hand. If no further meaningful betting is possible, complete the remaining board and resolve showdown. Do not force an extra wager from a lone actionable player once outstanding calls are settled.

Evaluate the best five cards out of seven with full kicker comparison, including the A-2-3-4-5 straight. Use contributions to create main/side pots; only eligible non-folded contributors may win each pot. Return unmatched excess contributions. Split ties equally; distribute odd chips clockwise starting after the dealer. Conserve total chips at every transition.

At showdown, reveal the non-folded hands needed for settlement. Folded hands remain private. Between hands display the result and a table Continue action for the next funded dealer; a bot in that seat continues through the normal bot action flow. Dealer/blind advancement uses the next funded seats and the heads-up rule once two remain.

The [PokerStars rules reference](https://www.pokerstars.com/poker/games/texas-holdem/) supports the basic sequence, actions, rankings, and no-limit minimum raises. Match length, initial chips, blind sizes, and continuation control are the plugin's selected defaults.

## Table interaction

Place Fold, Check/Call, Raise, and All-in controls flat in front of the owner's hand. Disabled actions use brightness, with localized hover descriptions. Raise opens a focused native amount input, labelled as the total wager for this street; preview the additional chips and validate the submitted amount again against the current room revision. Repeated clicks on action controls must not apply to a later turn or street.

The center shows five community-card slots, total pot, side-pot results when relevant, and street. Each seat has a chip total, current contribution, dealer/blind marker, and green current-turn indicator. Use a compact chip-stack model plus numeric text instead of one entity per chip. Opponents and spectators see only backs before authorized showdown.

## Implementation and acceptance

Keep `TexasHoldemGame`, hand evaluation, and pot settlement pure and separate. Use existing hand privacy and accepted-action persistence. Bots act using their own hole cards and the visible board; betting suggestions never depend on another seat's hidden cards.

Test heads-up and multiplayer order, dealer/blind transitions, minimum/short/cumulative raises, checks/calls, all-ins, folded contributions, unmatched refunds, multiple side pots, tied hands, odd chips, wheel straights, shared-board ties, busts, match victory, and total-chip conservation. Exhaustively validate the five-card hand evaluator against known category counts, then verify seven-card selection and edge cases. Check replay across multiple hands and private-state filtering.

Verify amount-input callbacks reject stale/double submissions, controls match legal actions, mixed rendering does not leak hole cards, and restart restores contributions, chips, dealer, and current street exactly.
