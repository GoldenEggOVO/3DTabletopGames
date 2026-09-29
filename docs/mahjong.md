# Six Mahjong house profiles

[English](mahjong.md) | [简体中文](mahjong.zh-CN.md)

Applies to **1.7.0-SNAPSHOT**. Mahjong uses four seats; bots can fill empty seats. Choose **Mahjong** in the menu, then a regional profile, match length and regional settings. Rules lock when the room is created and are preserved with its replay history. Match points are separate from server currency.

These are this project's defined casual house profiles. Regional Mahjong has many variants; this page specifies the plugin's behavior. A reference plugin was used only to check configuration names, default values and publicly listed scoring-pattern names. Its implementation, models and language resources were not copied, and its saved games are not compatible.

## Controls and shared settings

- Click your own tiles or open **Choose a Move in Menu** to discard, chi, pon, kan, win or pass. Actions with several choices, such as different sequences, open a selection menu.
- Opponents see your tile backs. Spectating and **Public Table Details** show public information only. Concealed kongs whose faces are hidden by the regional profile are also shown as backs.
- In Sichuan, select three tiles of the same suit and confirm the exchange, then choose your missing suit. Illegal combinations cannot be selected.
- Each physical discard area shows its latest 18 tiles; public details retain the complete river. Exposed melds and flowers have a physical display limit of 28 tiles per player.
- **Single Hand** plays one hand. **East Round** ends after four dealer rotations; **East + South Rounds** after eight. Dealer continuations do not count as rotations. Use **Start Next Hand** to begin the next hand.
- Starting points: **25,000 / 30,000 / 35,000**. The highest final score wins; tied leaders produce a draw. Bots provide simple legal-play assistance rather than competitive AI.
- Multiple discard wins are confirmed in turn order, nearest to the discarder first. Wins take priority over pon/kan, which take priority over chi. This order is independent of client response latency.

## Profile overview

| Profile | Set size / usual hand size | Main rules | Default scoring |
| --- | --- | --- | --- |
| Guangdong | 136 / 13 | Four sets and a pair, seven pairs, thirteen orphans; no chi | 1,000 for a discard win; 500 from each opponent for self-draw |
| Fuzhou | 144 / 16 | Eight replacement flowers, gold indicator and wildcards, three-gold, rob-gold and gold-pair wins | 100 per flower unit, 3 base units; ordinary discard win ×1, self-draw ×2 |
| Sichuan | 108 / 13 | Exchange three, missing suit, blood battle, multiple winners, kong payments and exhaustive-draw settlement | Base 100; cap 4 fan |
| Qinhuangdao | 108 / 124 / 136; hand 13 | Indicator or fixed wildcard, standard hands and seven-pairs tiers, multipliers and selectable discard-win policy | 124 tiles, base 100, multiplier cap 64 |
| Taiwan | 144 / 16 | Five sets and a pair, flower replacement, tai scoring, dealer continuations and nearest-winner priority | Base 100, 100 per tai, cap 16 tai, minimum 0 tai |
| Japanese Riichi | 136 / 13 | Yaku requirement, riichi, furiten, han/fu and dora | 25,000 points; 3 red fives and open tanyao enabled |

## Guangdong

Standard hands, seven pairs and thirteen orphans can be enabled separately; at least one winning shape must remain enabled. Seven pairs may count four natural identical tiles as two pairs. All three shapes use the same simplified payment amounts, with no additional fan table. The discard-win payment and self-draw payment per opponent are configurable. The default match is a single hand.

## Fuzhou

This profile uses 144 tiles and five sets plus a pair. Only the eight flower tiles are replaced; honors remain part of ordinary hands, and the white dragon does not stand in for the gold indicator. The revealed indicator's own type becomes gold. Gold in the concealed hand can substitute in pairs, sequences and triplets.

For each payer, flower units equal:

**base units + concealed gold count + actual flower count + open kongs × open-kong units + concealed kongs × concealed-kong units + dealer-continuation units when the dealer is involved.**

Multiply those units by points per flower and the winning-pattern multiplier.

- **Three Gold (San Jin Dao):** exactly three gold tiles can win in the initial hand or after the player's own draw. Another player's discard cannot supply the third gold. Default multiplier: ×40.
- **Rob Gold (Qiang Jin):** available only in the dedicated opening window when gold is revealed. The actual indicator tile completes the winning shape. Default: ×40.
- **Gold Pair (Jin Que):** a valid decomposition uses two gold tiles as the entire pair. Default: ×60.
- Special winning patterns use the highest applicable value and do not stack. An ordinary discard win is paid by the discarder; self-draw is paid by all three opponents.

The special-pattern switches and multipliers, base flower units, points per flower, open/concealed kong units and dealer-continuation units are configurable. Published Fuzhou variants also include versions that replace all honors as flowers; this profile uses the eight-flower treatment described above.

## Sichuan

Each player selects three tiles of one suit, then all selections are exchanged together. Choose random, clockwise, counterclockwise or opposite-seat exchange. Players then declare a missing suit. Tiles of that suit must be discarded first, and the hand cannot win until that suit is cleared. Winners leave the current hand and make no further payments. The hand ends after three players win or the wall is exhausted.

Base pattern multipliers are: ordinary hand ×1; all triplets ×2; exposed single wait, pure suit, seven pairs or terminals in every group ×4; all triplets using only 2/5/8, pure-suit triplets or seven pairs with one natural quad ×8; pure-suit seven pairs ×16. Roots and event bonuses add fan before the cap. Self-draw can add one base payment or one fan.

Kong payments are house rules: for a kong claimed from a discard, the discarder pays two bases and each other unfinished player pays one; an added kong costs each unfinished opponent one base, and a concealed kong costs each two bases. Options cover passed-win restrictions, forced wins in the last four tiles, transfer of kong income after dealing in, and kong-tax refunds for players who finish an exhausted wall without a ready hand. Exhaustive draws also settle missing-suit penalties (*hua zhu*) and payments from non-ready to ready hands (*cha da jiao*). The menu controls base points, the fan cap and the listed optional switches.

## Qinhuangdao

The default set has 124 tiles: the three suits plus four winds. Sets of 108 or 136 tiles are also available. Indicator mode uses the next tile type as the wildcard: suited 9 wraps to 1, winds cycle East–South–West–North, and dragons cycle White–Green–Red. Fixed-wildcard choices are 1 or 5 of characters, circles or bamboo.

- Ordinary hands use four sets and a pair. Seven pairs with 0 / 1 / 2 / 3 natural four-of-a-kind groups score ×4 / ×8 / ×16 / ×32. Wildcard substitutions cannot manufacture a luxury quad.
- Compatible patterns multiply up to the cap: closed hand, no wildcard, all triplets, wildcard single wait, gold-on-gold pair completion, catch-five, kong replacement win, last tile, robbing a kong and other defined patterns. One winning tile cannot complete both the pair and a sequence.
- Catch-five requires the actual 5 of characters to complete a natural 4–5–6 of characters. Wildcard single wait requires a preexisting singleton wildcard. The best valid decomposition is used.
- Heavenly and earthly wins have an independent base multiplier of 10, multiplied by 2 for each natural luxury-seven-pairs quad. Four wildcards also use an independent base of 10. Ordinary closed-hand/no-wildcard bonuses do not stack onto these special bases.
- By default, wildcards cannot be discarded or claimed from another player's discard for chi, pon or open kan. These settings can be enabled separately. Calls that would leave no legal discard are excluded.
- Discard-win policies are nearest winner only, multiple winners or self-draw only. Chi, pon, open/concealed/added kan, robbing an added kan, and dealer continuation after a dealer win or draw each have settings.

This profile adds no unconfigured wind-kong or separate kong cash transfers. Its pattern multipliers are fixed house rules for this version; the menu adjusts base points and the overall cap, not every individual pattern multiplier.

## Taiwan

A complete hand has five sets and a pair, also called the *eyes*: 16 tiles normally and 17 to win. Kongs increase the physical tile count; flowers do not occupy concealed-hand slots. “Eyes” means the pair, not permission to see another player's concealed tiles.

Payment is **base + points per tai × total tai**. A payment involving the dealer adds the dealer bonus and continuation bonus, defaulting to **1 + 2 × consecutive dealer continuations**. The hand must meet the minimum tai before dealer bonuses are added.

| Tai | Patterns |
| --- | --- |
| 1 each | Self-draw; closed hand; fully concealed self-draw; dragon triplet; seat-wind triplet; matching flower; single-type wait; kong replacement win; robbing a kong; last tile |
| 2 each | Ping hu; complete flower group; fully exposed hand |
| 4 each | All triplets; half flush; small three dragons |
| 8 each | Big three dragons; small four winds |
| 12 | Full flush |
| 16 each | Big four winds; all honors; heavenly win; earthly win |

A closed self-draw totals three tai from its three applicable conditions. Ping hu requires no honors or flowers, only sequences, and a two-sided discard win. Single-type wait means the whole hand can complete on only one tile type. Dragon-triplet points are not added to small/big three dragons; seat-wind points are not added to small/big four winds.

The default discard-win policy gives priority to the nearest winner; multiple winners are optional. Settings include minimum tai, tai cap, payment base and points per tai, dealer and continuation bonuses, and robbing an added kan. The individual pattern table uses this version's fixed defaults.

## Japanese Riichi

Winning shapes are four sets and a pair, seven pairs and thirteen orphans. A hand needs a yaku; dora alone cannot satisfy the requirement.

Supported ordinary yaku include Riichi, Double Riichi, Ippatsu, Menzen Tsumo, Tanyao, Pinfu, Iipeikou, Yakuhai, Rinshan Kaihou, Chankan, Haitei/Houtei, Sanshoku Doujun, Ittsuu, Chanta, Chiitoitsu, Toitoi, Sanankou, Honroutou, Sanshoku Doukou, Sankantsu, Shousangen, Honitsu, Junchan, Ryanpeikou and Chinitsu. Yakuman include Kokushi Musou, Suuankou, Daisangen, Shousuushii/Daisuushii, Tsuuiisou, Chinroutou, Ryuuiisou, Chuuren Poutou, Suukantsu and Tenhou/Chiihou, including the supported special-wait variants.

Han/fu calculation distinguishes open and concealed groups, triplets completed by ron, the winning tile's assignment and its wait. Red, ura and kan dora are counted. The game state checks riichi eligibility, furiten and action restrictions after riichi. Fu is rounded upward, with fixed exceptions including 25 fu for seven pairs and 20 fu for pinfu tsumo.

Settings cover 0 / 3 / 4 red fives, open tanyao, kiriage mangan, counted yakuman and double yakuman. The scoring structure follows EMA 2025 with this profile's options: a double-wind pair is worth 2 fu, and natural yakuman do not stack. Renhou and Nagashi Mangan are not included. See [verification](verification.md) for state-machine acceptance; the number of named patterns does not establish equivalence with another regional ruleset or implementation.

## References and verification limits

- [EMA 2025 Riichi rules](https://mahjong-europe.org/portal/images/docs/Riichi-rules-2025-EN.pdf): Riichi scoring reference.
- [Sichuan Sports Venue Association rules](https://www.ssva.org.cn/upload/file/2025-08-22/6389146967009551555075859.pdf): reference for Sichuan base patterns.
- [Laiyouxi Fuzhou rules](https://www.laiyouxi.com/news/145.html): gold revelation and special winning patterns; this profile's eight-flower choice is explained above.
- [Xinyue Qinhuangdao rules](https://www.xinyueyouxi.com/game/197-5): multiplier reference; stacking follows this page's house rules.
- [New Mahjong Online terminology](https://new.mjonline.com.tw/aboutgame_d.html): five-set hands and the meaning of eyes.

Automated tests cover rules, physical-tile conservation, point transfers, replay and server-side display metadata. Actual Minecraft models, text size, click feel and sound still require user acceptance in the client.
