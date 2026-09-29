# Four Mahjong house profiles

[English](mahjong.md) | [简体中文](mahjong.zh-CN.md)

Applies to **1.7.3-SNAPSHOT**. Mahjong uses four seats; bots can fill empty seats. Choose **Mahjong** in the menu, then a regional profile, match length and regional settings. Rules lock when the room is created and are preserved with its replay history. Match points are separate from server currency.

These are this project's defined casual house profiles. Regional Mahjong has many variants; this page specifies the plugin's behavior. A reference plugin was used only to check configuration names, default values and publicly listed scoring-pattern names. Its implementation, models and language resources were not copied, and its saved games are not compatible.

## Existing room records

Fuzhou (`fuzhou`) and Qinhuangdao (`qinhuangdao`) have been removed. If `rooms.json` contains either profile, restore fails with `Unknown mahjong profile` and the plugin stops loading the entire room file. It does not convert the rules or save a partially restored file. The original file remains unchanged, and recovery attempts to copy it to `rooms.unreadable-<timestamp>.json`. Back up data before upgrading and finish these old rooms with a compatible version; do not relabel an old replay as another profile.

## Controls and shared settings

- Click your own tiles or open **Choose a Move in Menu** to discard, chi, pon, kan, win or pass. Actions with several choices, such as different sequences, open a selection menu.
- Opponents see your tile backs. Spectating shows public information only. Concealed kongs whose faces are hidden by the regional profile are also shown as backs.
- In Sichuan, select three tiles of the same suit and confirm the exchange, then choose your missing suit. Illegal combinations cannot be selected.
- Each physical discard area shows its latest 18 tiles. Exposed melds and flowers have a physical display limit of 28 tiles per player.
- **Single Hand** plays one hand. **East Round** ends after four dealer rotations; **East + South Rounds** after eight. Dealer continuations do not count as rotations. Use **Start Next Hand** to begin the next hand.
- Starting points: **25,000 / 30,000 / 35,000**. The highest final score wins; tied leaders produce a draw. Bots provide simple legal-play assistance rather than competitive AI.
- Multiple discard wins are confirmed in turn order, nearest to the discarder first. Wins take priority over pon/kan, which take priority over chi. This order is independent of client response latency.

## Profile overview

| Profile | Set size / usual hand size | Main rules | Default scoring |
| --- | --- | --- | --- |
| Guangdong | 136 / 13 | Four sets and a pair, seven pairs, thirteen orphans; no chi | 1,000 for a discard win; 500 from each opponent for self-draw |
| Sichuan | 108 / 13 | Exchange three, missing suit, blood battle, multiple winners, kong payments and exhaustive-draw settlement | Base 100; cap 4 fan |
| Taiwan | 144 / 16 | Five sets and a pair, flower replacement, tai scoring, dealer continuations and nearest-winner priority | Base 100, 100 per tai, cap 16 tai, minimum 0 tai |
| Japanese Riichi | 136 / 13 | Yaku requirement, riichi, furiten, han/fu and dora | 25,000 points; 3 red fives and open tanyao enabled |

## Guangdong

Standard hands, seven pairs and thirteen orphans can be enabled separately; at least one winning shape must remain enabled. Seven pairs may count four natural identical tiles as two pairs. All three shapes use the same simplified payment amounts, with no additional fan table. The discard-win payment and self-draw payment per opponent are configurable. The default match is a single hand.

## Sichuan

Each player selects three tiles of one suit, then all selections are exchanged together. Choose random, clockwise, counterclockwise or opposite-seat exchange. Players then declare a missing suit. Tiles of that suit must be discarded first, and the hand cannot win until that suit is cleared. Winners leave the current hand and make no further payments. The hand ends after three players win or the wall is exhausted.

Base pattern multipliers are: ordinary hand ×1; all triplets ×2; exposed single wait, pure suit, seven pairs or terminals in every group ×4; all triplets using only 2/5/8, pure-suit triplets or seven pairs with one natural quad ×8; pure-suit seven pairs ×16. Roots and event bonuses add fan before the cap. Self-draw can add one base payment or one fan.

Kong payments are house rules: for a kong claimed from a discard, the discarder pays two bases and each other unfinished player pays one; an added kong costs each unfinished opponent one base, and a concealed kong costs each two bases. Options cover passed-win restrictions, forced wins in the last four tiles, transfer of kong income after dealing in, and kong-tax refunds for players who finish an exhausted wall without a ready hand. Exhaustive draws also settle missing-suit penalties (*hua zhu*) and payments from non-ready to ready hands (*cha da jiao*). The menu controls base points, the fan cap and the listed optional switches.

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
- [New Mahjong Online terminology](https://new.mjonline.com.tw/aboutgame_d.html): five-set hands and the meaning of eyes.

Automated tests cover rules, physical-tile conservation, point transfers, replay and server-side display metadata. Actual Minecraft models, text size, click feel and sound still require user acceptance in the client.
