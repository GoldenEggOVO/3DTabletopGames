# New card games (1.10.0-SNAPSHOT)

All three use original shared playing-card artwork, private hands, bots, saved seeded replay and native/packed/mixed rendering. Shift + right-click opens the room menu. Aim at a card to lift it; select multiple cards with right-click, then use Play or Clear. Poker uses flat Fold, Check/Call, Raise and All-in controls; Raise asks for the total street bet and rejects expired room actions.

## Doudizhu

Exactly three players. Deal 17 each and three hidden bottom cards. Each player bids once (pass or a higher bid from 1–3); a bid of three ends bidding immediately. If all pass, redeal. Only after bidding ends are bottom cards exposed and added to the landlord. Standard singles, pairs, triples with attachments, straights, consecutive pairs/triples, airplanes with wings, four-with-attachments, bombs and rocket are supported. Sequences exclude twos and jokers; attachments exclude the main group; the two jokers cannot be paired as airplane/four single wings. Two passes reset the trick; the leader cannot pass. Farmers share victory. Bomb/rocket and spring multiply match points.

## Liar's Bar: card mode

This is a documented adaptation, not an exact replica. Two to four players; six aces, six kings, six queens and two wild jokers. Every living player receives five cards. The table declares A/K/Q; play one to three face-down cards or challenge the latest play. The next play accepts the previous declaration. Empty hands are skipped; the last player still holding cards must challenge when a previous play exists. Only challenged cards are revealed. A truthful declaration punishes the challenger; a lie punishes its author. Each seat has a seeded secret fatal chamber among six; attempts persist across rounds. The last living seat wins. No player damage, inventory changes or world weapons are used.

## Texas Hold'em

Two to six players, 1,000 chips each and fixed 5/10 blinds. Dealer rotates; heads-up dealer is small blind and acts first preflop. Two private cards, burns and flop/turn/river, best five of seven, folded-hand privacy, all-in runout, uncalled-bet refunds, side pots and tied pots are supported. Odd chips go clockwise after the dealer. A short all-in does not reopen raising unless cumulative increases reach a full raise. Between hands, the next funded dealer uses Continue; the match ends when one seat retains all chips. Chips never connect to server money.

## Models and acceptance

Resource-pack tables have smooth solid-color wooden borders and marked play regions. Chess pieces are standing 3D models; Connect Four has transparent rack holes; stones and discs use masked round caps. New public cards use one packed entity each; native faces use two block displays and three text displays for a thin border, cream face, central index and opposite corner indices. Native backs remain one display. Private faces are hidden before spawn and shown only to their owner. Chip stacks have fixed entity counts rather than one entity per chip.

Playing-card controls lie flat between the player and the hand. Upright cards have distinct depth layers; overlapping flat cards have distinct height layers. Hover uses stationary hand targets, and selection and hover share a single lift. Seat captions omit hand counts and sit inward; Doudizhu bottom cards sit at the table centre.

Client acceptance remains required for four/six-seat readability, card overlap and selection, texture orientation, round geometry and mixed nearby viewers. No Release is published by this source update.
