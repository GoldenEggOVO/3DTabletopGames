# Game modes and room rules

[English](game-modes.md) | [简体中文](game-modes.zh-CN.md)

This page describes **1.8.0-SNAPSHOT**, an unpublished development build. It has not been deployed to production servers. Local artwork and model previews do not replace Minecraft client acceptance; see [verification](verification.md) for evidence and status.

Public Mahjong tiles now use the same width as hand tiles, and the central wall digits are larger again. Hover privately highlights matching own/public tiles; only the aimed hand tile rises. A draw leaves existing tiles in place and adds the new tile at the far right with a gap. Continuous clicks remain guarded across other seats’ turns, preventing a replacement tile at the same aim from being discarded. Only Mahjong tiles forbidden by kuikae after Chi/Pon dim through display brightness, preserving their materials and patterns; waiting and Riichi selection retain normal brightness. A complete Riichi shape without yaku shows a red No Yaku hint on its actual draw or pending public discard. Choose Chi/Pon/Kan first, then a complete combination. Riichi arms eligible discards; Ron and Tsumo have their own buttons. Dora indicator tiles sit at the middle of the frame, melds remain lower-right, and corner wind inscriptions stay clear of melds. The lower Shift view preserves standing face visibility and temporary invisibility. Sichuan exchange selection/removal, exchange confirmation and missing-suit choice are available directly at the table. Color Eight retains its rotating direction ring and underlined 6/9 artwork, with a new 54-card deck.

## Set up a game

1. Open `/3dtabletop` and choose a game from the catalog.
2. On **Game Setup**, choose **With Friends** or **With Bots**, then the player count where available. Choose Mahjong's regional profile or Go's board size here as well.
3. Open **Detailed Rules** when you want to change the defaults. Click settings to cycle their values and use next/previous for additional pages. **Back** retains your choices and returns to basic setup.
4. Choose **Create Room** for friends, or **Start with Bots**. Friends join the room and ready up; the host can fill remaining seats with bots after the other humans are ready.

**Rules lock when the room is created.** Joining players use those rules; undo and rematch retain them. To change rules, create another room. `/3dtabletop create <kind> [players]` creates a room with default rules; use Game Setup to customize them.

The lobby prioritizes readiness and starting. Playing rooms return to the physical table and do not offer full hand/board move selectors. Necessary Go pass/scoring actions remain separate legal-state buttons; Color Eight pass and wild-color controls are on the table. Room options offer undo only for deterministic board games; leaving remains available. Help/Details entries are removed. All Close buttons share the same bottom exit, red bracketed caption and width, including customized layouts; installed YAML bytes are preserved.

| Game | Players | Setup choices |
| --- | --- | --- |
| Xiangqi | 2 | Friends or bots |
| Chess, Draughts, Reversi, Connect Four | 2 | Friends or bots; host plays first, second or randomly |
| Gomoku | 2 | First move; black's double-three, double-four and overline restrictions |
| Go | 2 | 9×9, 13×13 or 19×19 board |
| Ludo | 2–4 | First pawn, blocking, reaching home, finish condition |
| Chinese Checkers | 2, 3, 4 or 6 | Jump own pieces, other camps, finish condition |
| Color Eight | 2–5 | First empty hand wins; fixed rules |
| Mahjong | 4 | Four regional profiles and their room rules |

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

## Color Eight (彩八): the 54-card game

The stable room/command identifier is `lastcard`. The original native artwork uses red, blue, yellow and purple and needs no resource pack. Gameplay is informed by the [Blazing 8s FAQ](https://support-apps.discord.com/hc/en-us/articles/26501925147415-Blazing-8s-FAQ) and locally captured public client/game states. The palette and artwork are our own.

- 2–5 players; five cards each. The first seat opens. An initial colored card establishes color/rank without executing its special effect; wilds and swaps cannot open.
- Each color has 1–7, 9 and 10, plus Draw One, Skip and Reverse (48 colored cards). Four colorless Wild Eights and two colorless Swaps make 54.
- Match the active color or number/type. Wild Eight and Swap are unrestricted on your own turn. There are no interrupt plays, declaration penalties or draw stacks.
- You may draw one voluntarily. If any card in your resulting hand is legal, you may play any legal card or pass; otherwise play passes automatically. Pass before drawing draws once; pass after drawing adds no card.
- A human turn lasts 30 seconds. Timeout draws once and passes, or only passes if already drawn. Selecting a Wild Eight does not reset this clock; timeout chooses the most common colored suit in your hand.
- Wild Eight opens four private color buttons above the selected card. It stays in your hand until color submission, then the discard becomes the selected color's 8.
- Draw One gives every other player one card without skipping. Skip misses the next player. Reverse flips direction; in two-player games it is equivalent to Skip for effects and matching.
- Swap trades your remaining hand with the next player in the current direction, preserving the active color and rank/type through consecutive swaps. Play then goes to that player.
- First empty hand wins **before** resolving the final special effect. Discard recycling keeps the top card and recycles before attempting a draw.

The table is a native circle of diameter 3 blocks. Hands overlap in one bounded-width fan; only the aimed card rises, without moving neighbors. Unplayable cards are gray during your turn. Private faces/buttons stay owner-only. Right-click the deck to draw and the table Pass button to end your turn.

New records use `rulesVersion: 2`. On upgrade, old Last Card records with nonempty history are omitted from active restoration after making a complete `rooms.pre-color-eight-*.json` backup. Other games and empty Last Card lobbies continue restoring. The offline replay verifier rejects old histories rather than interpreting changed card IDs.

## Mahjong setup

All profiles use four seats and match points. The selector offers **Riichi, Guangdong, Sichuan and Taiwan**.

| Common setting | Default | Choices |
| --- | --- | --- |
| Profile | Riichi | The four profiles above |
| Match length | Single hand for Guangdong; East round for other profiles | Single hand / East round / East–South match |
| Starting points | 25,000 | 25,000 / 30,000 / 35,000 |

After selecting a profile in basic setup, open **Detailed Rules** for that region's settings. Switching profiles resets regional choices in the draft; match length and starting points are retained when explicitly set. See [Mahjong rules and option tables](mahjong.md) for hand sizes, regional scoring, defaults, dealer continuation and rule boundaries.

## Private hands and public information

Color Eight and Mahjong show hand faces only to their owner. Other players and spectators see backs/counts, public discards and exposed tiles. Choose cards and tiles directly on the table. Color Eight displays the current color, direction and top discard. Mahjong menus show each player's match points.

Both games use original face artwork built from native block displays. Mahjong tiles show suit and honor patterns; Color Eight uses upright cards. Aim at a Color Eight card to raise only that card; Mahjong raises only the selected tile. Use the physical hand and table buttons to choose legal actions. While seated in a Color Eight game, right-click the central deck when drawing is legal. A rejected draw leaves the game unchanged. Newly drawn cards travel from the deck into your private hand; initial display, reconnecting and saved-room recovery show cards in place.

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
- Original 1.7.0 stock setup, room and hand templates use the new layout in memory after content matching, without changing installed file bytes. Customized layouts keep their ordering and styles; back up and manually merge new templates when desired. Schema 1 and the four remaining Mahjong profiles keep their rules; removed-profile saves are rejected as described in migration.

See [migration](migration.md) for upgrades and older room imports. The [Nintendo game catalog](https://www.nintendo.com/jp/switch/as7ta/games/index.html) is a primary reference for the collection; the linked GameFAQs pages are independent play guides, not Nintendo's official rule specification. This plugin's documented rules and selectable values define its supported modes.

### Mahjong table information and focus

- A normal tile has four copies: own hand 2 / public 0 → Remaining 2; own 1 / public 1 → Remaining 2. Public rivers, melds, indicator tiles and offered tiles are counted once by physical ID. Red and normal fives share a type. The count is unseen copies, which can be in opponents’ hands or the dead wall; it is not a prediction of future draws. The central count is the rules engine’s actual drawable wall count.
- Hands up to 17 tiles occupy one row. Call previews and unseen-copy labels are visible only to their owner; stale choices are rechecked against current legal actions.
- The timer shares the actual human/bot/offline turn deadline and pauses for suspended play or pending undo. No second timer or rule change is introduced.
- Shift focus requires a seat, permission and proximity. Position is held but mouse aim remains free. Release restores your entry position, yaw, pitch, gravity and prior invisibility. Menus, leaving, death, external teleports and shutdown clear temporary focus. If the return space is blocked or another plugin cancels the return teleport, focus ends and normal gravity resumes without forcing a teleport. Test camera feel and anti-cheat compatibility on your isolated server.
