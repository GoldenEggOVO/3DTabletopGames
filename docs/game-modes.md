# Game modes and room rules

[English](game-modes.md) | [简体中文](game-modes.zh-CN.md)

This page describes **1.7.1-SNAPSHOT**, an unpublished development build. It has not been deployed to production servers. Local artwork and model previews do not replace Minecraft client acceptance; see [verification](verification.md) for evidence and status.

## Set up a game

1. Open `/3dtabletop` and choose a game from the catalog.
2. On **Game Setup**, choose **With Friends** or **With Bots**, then the player count where available. Choose Mahjong's regional profile or Go's board size here as well.
3. Open **Detailed Rules** when you want to change the defaults. Click settings to cycle their values and use next/previous for additional pages. **Back** retains your choices and returns to basic setup; **Rules** opens the game explanation.
4. Choose **Create Room** for friends, or **Start with Bots**. Friends join the room and ready up; the host can fill remaining seats with bots after the other humans are ready.

**Rules lock when the room is created.** Joining players use those rules; undo and rematch retain them. To change rules, create another room. `/3dtabletop create <kind> [players]` creates a room with default rules; use Game Setup to customize them.

The lobby puts readiness, the host's bot start and room details first. During play, return to the physical table or open the hand/board controls. Room options contain full details, rules, public hand information, undo requests and leaving. Private-hand controls show your cards, current state and legal actions. Returning from a room opens the game catalog; returning from the room browser opens your current room when seated, otherwise the catalog.

| Game | Players | Setup choices |
| --- | --- | --- |
| Xiangqi | 2 | Friends or bots |
| Chess, Draughts, Reversi, Connect Four | 2 | Friends or bots; host plays first, second or randomly |
| Gomoku | 2 | First move; black's double-three, double-four and overline restrictions |
| Go | 2 | 9×9, 13×13 or 19×19 board |
| Ludo | 2–4 | First pawn, blocking, reaching home, finish condition |
| Chinese Checkers | 2, 3, 4 or 6 | Jump own pieces, other camps, finish condition |
| Last Card | 2–4 | First place or all places |
| Mahjong | 4 | Six regional profiles and their room rules |

Yacht and Aeroplane remain unavailable for creating new rooms. Existing supported historical room records can still be restored.

## Optional board rules

These defaults and available values match the current [room option definitions](../src/main/java/dev/tabletop3d/rules/GameOptions.java).

| Game | Setting | Default | Available values / effect |
| --- | --- | --- | --- |
| Chess, Draughts, Reversi, Connect Four, Gomoku | Host Plays | First | First / Second / Random |
| Gomoku | Double Three | Allowed | Allowed / Forbidden for Black |
| Gomoku | Double Four | Allowed | Allowed / Forbidden for Black |
| Gomoku | Overline | Allowed | Allowed / Forbidden for Black |
| Ludo | First Pawn | Starts on the Track | Starts on the Track / Up to Three Starting Rolls / Roll a Six |
| Ludo | Blocking | Off | Off / Any Pawn Blocks Passage |
| Ludo | Reaching Home | Exact Roll | Exact Roll / Overshoot Allowed |
| Ludo | Game Finish | First Place | First Place / All Places |
| Chinese Checkers | Jump Own Pieces | Allowed | Allowed / Forbidden |
| Chinese Checkers | Other Camps | Allowed | Allowed / Forbidden |
| Chinese Checkers | Game Finish | First Place | First Place / All Places |
| Last Card | Game Finish | First Place | First Place / All Places |

### First move and the host

**Host Plays** changes which player occupies the game's opening seat. Chess still starts with White, and Gomoku/Reversi still start with Black. The host may therefore move to the other physical side of the table. Host rights remain attached to the person who created the room, including the right to fill bot seats. Random is chosen anew for a rematch and retained when that game is recovered.

### Gomoku

The board remains 15×15. By default, five or more stones in a row win for either color. Each optional restriction applies to Black; White can still win with five or more. Forbidden moves are excluded from legal choices. The double-three/four checks follow the pattern definitions and legal extensions in [RIF rules 9.2–9.3](https://gomoku.renju.net/rifrules/); tournament opening exchanges are not included.

### Ludo

Each player has four pawns. A six deploys a pawn from the yard and grants another roll. Landing on an opponent on the shared track sends that opponent's pawn back to its yard. There is no triple-six penalty.

- **Starts on the Track:** exactly one pawn per player starts out when a new game is initialized. This automatic deployment happens only at the opening; a captured pawn needs a six to return.
- **Up to Three Starting Rolls:** all pawns start in the yard. Until that player has deployed their first pawn, a turn offers up to three attempts to roll a six. Once first deployment has happened, captures do not restore this three-attempt privilege.
- **Roll a Six:** all pawns start in the yard, with one starting attempt per turn.
- **Blocking:** even one pawn blocks passage across its occupied cell, whether friendly or opposing. This also applies to occupied intermediate cells in the home lane. The starting and destination cells are excluded: landing on an occupied destination is allowed, including a capture on the shared track.
- **Exact Roll:** a pawn needs the exact remaining distance to finish. **Overshoot Allowed** brings an overshooting pawn directly home.
- **All Places:** players who bring all four pawns home receive a place and leave the turn order. The last remaining player receives the final place. Finishing on a six does not give an already placed player another turn.

The selectable starting thresholds are informed by the [Switch Ludo guide](https://gamefaqs.gamespot.com/switch/286602-clubhouse-games-51-worldwide-classics/faqs/78437/ludo). [Nintendo's update notes](https://en-americas-support.nintendo.com/app/answers/detail/a_id/49554/p/989) also document a starting-threshold reroll correction. The bullets above define this plugin's precise behavior, including its opening-only deployment policy.

### Chinese Checkers

This mode keeps the **121-hole star board, ten pieces per player and 2/3/4/6-player support**. The Nintendo Switch version described in the [Chinese Checkers guide](https://gamefaqs.gamespot.com/switch/286602-clubhouse-games-51-worldwide-classics/faqs/78437/chinese-checkers) uses a smaller three-player, six-piece setup; this plugin adopts selected options while retaining its own board.

- Disabling **Jump Own Pieces** prevents using your own piece as the bridge of a jump. Opposing pieces can still be jumped.
- Disabling **Other Camps** excludes every intermediate and final landing in camps other than your starting and target camps.
- Pieces that enter their target camp cannot leave it.
- **All Places** skips completed players while leaving their pieces on the board as possible jump bridges. The last remaining player gets the final place. Repetition and blocked-position draws still apply.

## Last Card: the 52-card game

Last Card uses an original presentation without branded card artwork. Its deck and special actions differ from UNO. The variant is informed by the [Last Card play guide](https://gamefaqs.gamespot.com/switch/286602-clubhouse-games-51-worldwide-classics/faqs/78437/last-card); the following describes the implemented room rules.

| Cards | Contents |
| --- | --- |
| Red, yellow, blue and purple | Each color has one card of each number 1–8, plus Skip, Reverse, Draw Two and Draw Three: 12 cards per color |
| Wild | Four cards that choose the next color |
| Total | 52 cards; five dealt to each player |

The first player is selected randomly. The discard pile starts empty, so the opening player may choose any card. Thereafter, play a matching color, matching number/symbol, or Wild. Playing Wild includes choosing its color.

| Action | Effect |
| --- | --- |
| Skip | Skip the next active player; with two players, the same player acts again |
| Reverse | Reverse direction; with two players, play passes to the other player |
| Draw Two / Draw Three | The next player may stack the same draw type, regardless of color, or take the accumulated penalty; +2 and +3 cannot be mixed |
| Draw | Without a penalty, draw one only when no card can be played. Drawing always ends the turn; the drawn card is not played immediately |
| Declare Last Card | When holding two cards, declare before playing down to one; forgetting adds a five-card penalty |

Under a draw penalty, only the matching draw type can be played; a Wild does not cancel it. When the deck empties, earlier discards are shuffled back into it while the top discard stays on the table.

**First Place** ends when someone empties their hand. **All Places** removes finished players from the turn order, keeps their final card's applicable effects in play, and ends when the last remaining player can be assigned the final place. The first finisher remains the winner.

## Mahjong setup

All profiles use four seats and match points. The selector offers **Riichi, Guangdong, Fuzhou, Sichuan, Qinhuangdao and Taiwan**.

| Common setting | Default | Choices |
| --- | --- | --- |
| Profile | Riichi | The six profiles above |
| Match length | Single hand for Guangdong; East round for other profiles | Single hand / East round / East–South match |
| Starting points | 25,000 | 25,000 / 30,000 / 35,000 |

After selecting a profile in basic setup, open **Detailed Rules** for that region's settings. Switching profiles resets regional choices in the draft; match length and starting points are retained when explicitly set. See [Mahjong rules and option tables](mahjong.md) for hand sizes, regional scoring, defaults, dealer continuation and rule boundaries.

## Private hands and public information

Last Card and Mahjong show hand faces only to their owner. Other players and spectators see backs/counts, public discards and exposed tiles. Open the hand controls to select legal actions; the public-table page lets everyone inspect public information. Last Card also displays the current color, direction, draw penalty and top discard. Mahjong menus show each player's match points.

Both games use original face artwork built from native block displays. Mahjong tiles show suit and honor patterns; Last Card uses larger upright cards. Aim at a card to lift it and move nearby cards aside, then use the card or hand menu to choose a legal action. While seated in a Last Card game, right-click the central deck when drawing is legal. A rejected draw leaves the game unchanged. Newly drawn cards travel from the deck into your private hand; initial display, reconnecting and saved-room recovery show cards in place.

In other games, Xiangqi keeps Chinese piece inscriptions, including 砲. Ludo pawns have no floating number labels; stacked-pawn choices still identify the pieces in the menu. The dice stand uses the die model without an extra artificial shadow mesh.

Bots are casual opponents. Mahjong bots complete the exchange without repeatedly removing selections, choose a least-held suit for the missing suit, take available wins and riichi, and favor keeping pairs and connected tiles. They inspect only their own hand.

## Physical dice stand

New Ludo rooms include a separate dice stand beside the board. Leave space for both the main table and the stand when creating the room. Click the die/stand when it is your turn to roll; wait for it to settle before moving. World input, menu/command moves, bot moves and new undo requests are blocked during the animation; retry once the die settles. The displayed result comes from the room's rule state, so the animation cannot change the outcome.

Saved older rooms retain their original world position and compact dice presentation. Recovery displays the saved result at rest instead of replaying an old throw. No resource pack is required for the stand, cards or tiles.

## Saved rooms and compatibility

- Schema 1 room files retain their IDs, seats, seeds, action history and world anchors. New records also store the selected rules, host identity and dice-stand layout.
- Missing option fields use the legacy defaults. Existing Ludo histories keep automatic first-pawn deployment, no blocking, exact arrival and first-place completion.
- Recovery and undo replay the saved game with its saved options. Rematches keep the rules and host while using a fresh shuffle/dice sequence and a fresh random first-seat choice where selected.
- Unknown rule options or unsupported rule versions are rejected instead of silently changing a saved game.
- Existing language and menu customizations are preserved; missing text falls back to bundled English.
- Original 1.7.0 stock setup, room and hand templates use the new layout in memory after content matching, without changing installed file bytes. Customized layouts keep their ordering and styles; back up and manually merge new templates when desired. This presentation update does not change schema 1 data or game rules.

See [migration](migration.md) for upgrades and older room imports. The [Nintendo game catalog](https://www.nintendo.com/jp/switch/as7ta/games/index.html) is a primary reference for the collection; the linked GameFAQs pages are independent play guides, not Nintendo's official rule specification. This plugin's documented rules and selectable values define its supported modes.
