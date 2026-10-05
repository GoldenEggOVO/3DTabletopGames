# Changelog

## 1.10.23-SNAPSHOT — 发布前源码整理（未发布）

- 清理旧菜单入口、九个废弃翻译键、旧麻将渲染兼容分支，以及国际象棋未使用的悔棋备份；保留存档回放和重复局面判定。
- 回放记录集中管理并缓存 UTF-8 大小，避免每次出牌重复统计整个历史；复用未改变的私有牌面，减少实体重新创建。
- 整理麻将拆牌算法的命名与控制流程，修复资源包要求和桌子摆放错误被通用提示覆盖的问题。
- 统一当前源码、测试、打包文件与说明中的项目名称，保留第三方版权与来源说明。
- 632 项 Java 测试、39 项资源检查通过；与上一版规则对比 98 局、15,451 步及 7,570 项麻将求解结果一致。客户端画面与 FPS 仍需验收。

## 1.10.8-SNAPSHOT

- 原版彩八升级为 32×48 B 版图案，每面 24 个薄头颅；54 种显示图案共 624 张签名纹理，牌背仍为白边＋蓝背两个方块。
- 彩八相邻立牌只额外分开约 0.1 像素，避免下一张蓝背与上一张白边共面。斗地主、骗子酒馆和德州扑克使用相同紧凑间距；平放牌保持原有分层。
- 增加全部 17 种桌游的原版桌体和整桌模型统计，包含四种麻将规则及可变人数，验证关闭桌子后实体清理。
- 未发布 Release；客户端接缝、观感和 FPS 待验收。

## 1.10.7-SNAPSHOT

- 原版彩八采用 B 版 24×32 图案，每张 12 个薄头颅，公开牌背为白边＋蓝背两个方块；补齐选色后的四种彩色 8。
- 原版圆桌改为平整相邻行块，角点内接圆形，移除从中心延伸的径向叠面；细分轮廓，木圈完整包围绿面。
- 四子棋支架避开桌沿，预选框四边形成正方形；国际象棋王的十字与马的头颈改为相接部件。
- 删除旧彩八方块牌面生成代码；麻将、资源包和游戏规则保持现有体验。未发布 Release。

## 1.10.6-SNAPSHOT

- 原版扑克升级至 32×48 像素、24 个薄头颅；J／Q／K 使用原版配色的僵尸、骷髅、苦力怕，大小王使用凋零和完整 JOKER 字样。
- 公开牌背仅用两个原版方块组成白边＋蓝背；保留私有手牌、尺寸与选择体验。
- 修正 MineSkin 无限小时额度被当作耗尽的问题，继续按实际速率限制续传。
- 本次为本地验收开发版，资源包不变，未发布 Release。
## 1.10.5-SNAPSHOT — 原版扑克头颅试验（未发布）

- 原版标准扑克牌改为六个薄头颅，采用 16×24 像素图案，移除大量文字像素几何；大小王沿用短文字模型。
- 保留 Casino 白底、红黑花色和双角标，数字牌中央使用大数字；保持座位朝向、私有手牌和选牌操作。
- 离线生成带签名的 Mojang 纹理，目录内置于插件；玩家与服务器运行时不需要 MineSkin API Key。
- 清除旧扑克文字像素渲染代码，原始图案移到离线制作工具目录，保留素材来源检查。
- 资源包模型、麻将、Color Eight 和规则不变，继续使用 1.10.4 资源包。客户端接缝、光照和 FPS 待验收，未发布 Release。

## 1.10.4-SNAPSHOT — 桌面交互与原版卡牌减实体（未发布）

- 修复德州扑克 Raise 对话框的非法输入名称，当前玩家可以打开加注输入。
- 潜行＋右键直接命中整张桌体、桌沿和骰子托盘；潜行挥手不再抢占右键防抖。
- 原版标准扑克使用 Casino 图案的 64×86 文字像素贴面，每张由牌体和四层贴面组成，共 5 个实体；保留私有牌面与选牌操作。
- 原版完整牌背从每张 327 个实体降至 5 个；51 张公开牌背由 16,677 个降至 255 个。
- 分离原版牌面笔画与圆桌叠面，修复彩八手牌、棋盘桌沿和死子标记的共面闪烁。
- 实体数量下降已验证；字体像素仍有绘制成本，客户端画面与 FPS 需实测。未发布 Release。

## 1.10.3-SNAPSHOT — 封闭模型与统一桌面操作（未发布）

- 资源桌边、棋子和筹码改用连续封闭实体网格，移除圆面透明裁切造成的连接空隙；四子棋采用实体圆孔框架。
- 棋盘表面使用圆角几何，与桌沿轮廓一致；Ludo 骰子托盘补齐资源包模型。
- 彩八 Pass 平放在手牌与玩家之间的桌沿。
- 快艇骰子只更新被保留／取消保留的骰子，其他骰子不重启动画；移除独立 Score Sheet 菜单。
- 所有游戏通过共享命中路径使用 Shift＋右键打开桌面菜单；修复麻将退出固定镜头时重复打开菜单。
- 原版标准扑克牌复制 Casino Blackjack 的完整像素立体图案，保持私有手牌与点击位置；大小王保留现有模型。完整牌背每张327个实体，数量明显增加，客户端流畅度仍需验收。

## 1.10.2-SNAPSHOT — 双桌计分与卡牌音效（未发布）

- 斗地主桌布重新绘制三席装饰及中央地主牌区域，实际更新资源包贴图。
- 快艇骰子拆为相邻的计分桌与骰子桌，计分表展示所有玩家、小计、奖励与总分，黄色栏指示当前玩家。
- 修复资源桌、保留槽及资源骰子面的额外旋转，调整座位与桌间避让。
- 斗地主14种牌型、叫／抢地主、不出／要不起分别提示；使用提供的六段语音，其余牌型使用原创短音型。
- 骗子酒馆先质疑，再播放空枪或实弹；原版玩家使用对应音效，混合模式逐玩家分发。
- 彩八摸牌、跳过、选色及快艇特殊评分使用不同原版提示；未创建 Release。

## 1.10.1-SNAPSHOT — 当前功能代码整理（未发布）

- 移除旧独立世界、停用飞行棋、旧手牌与快艇骰子通用模型，保留当前17个游戏和直接桌面操作。
- 删除无调用的棋谱、赛事、跳棋长跳／评分／序列化代码和Commons Lang依赖。
- 移除旧游戏别名、旧地区选项前缀与1.8.10转换工具；彩八回放统一使用实时规则校验。
- 删除17个废弃翻译键，更新当前格式升级说明、许可证目录和交付脚本。
- 当前房间格式、种子、合法历史、菜单和资源包三种模式保持；旧飞行棋与非法耗尽牌堆历史不再恢复。



## 1.10.0-SNAPSHOT (unreleased)

- Separate flat and upright playing-card layers and keep hover targets stationary; selecting a hovered card no longer adds another lift.
- Put playing-card controls between the player and their hand, centre Doudizhu bottom cards, remove hand-count captions, and move seat information inward.
- Give native playing cards a thin border, cream face, red/black indices and an inverted lower corner using five displays per face.
- Add optional packed boards and pieces for the other board games, including 3D chess pieces and a Connect Four rack with transparent holes.
- Add original shared playing cards and dedicated Doudizhu, Liar's Bar and Texas Hold'em tables.
- Add private multi-card selection, bidding/challenge/betting controls, compact poker chips and dealer markers, bots and deterministic room replay for all three new games.
- Preserve current Mahjong/Color Eight interaction and per-player native/packed/mixed rendering. No economy integration or Release publication.


## 1.9.5-SNAPSHOT

- Place Riichi assistance controls in a horizontal row on the owner's front-right apron, below the playing surface; preserve private visibility, brightness and action-bar descriptions.

## 1.9.4-SNAPSHOT

- Make Browse Rooms a full-width catalogue text link, mutually exclusive with Resume Current Game.
- Put Leave Room directly in the room menu and remove player undo commands, negotiations, timers and state.
- Show five private Dora slots with concealed backs and riichi deposit/honba counters below them.
- Add owner-only Riichi sort, auto-win, no-call and drawn-tile discard controls on the right rim. Brightness indicates state; action-bar hover text explains only the function.
- Reveal settled winners' hands face-up, including the claimed winning tile for every Ron winner.

## 1.9.3-SNAPSHOT

- Use solid dark table edges and a raised open Mahjong rim in the resource pack.
- Place the Color Eight Pass control flat on the table in front of the hand, with a matching hit area.
- Keep full-width clickable catalogue headers above two columns of game buttons in row order.

## 1.9.2-SNAPSHOT

- Use English source comments, diagnostics and internal rule summaries; keep translated UI messages in language catalogs.
- Reference fixed piece artwork through named Unicode symbols without changing shapes or saved action IDs.
- Preserve non-English test coverage using catalog fixtures and add a source-language guard.
- Localize remaining dice and aeroplane coordinate hints.

## 1.9.1-SNAPSHOT

- Keep navigation buttons localized even when an editable layout contains a literal caption.
- Distinguish resource-pack enable, loading and disable buttons with green, aqua and gold.
- Remove configurable SHA-1/UUID; generate and bundle the pack checksum and create request IDs internally. Package validation ensures the JAR matches its resource-pack ZIP.
- Correct the default English locale.

## 1.9.0-SNAPSHOT

- Automatically discard the newly drawn Riichi tile after declaration; stop for ron/tsumo and resume after declining a win.
- Add a green Color Eight active-seat bar and name in both rendering modes.
- Add jade Mahjong backs, sideways declaration/called tiles, concealed-kan end backs and added-kan stacking with shared native/pack placement.
- Replace hashed/phrase translations with complete semantic templates; canonicalize `ColorEightGame` and `color-eight`.
- Extract room storage and automatic turn policy, remove AuthMe/Geyser, startup migration and obsolete foreign-provider code, and expand dense control flow for readability.
- Provide a separate offline data converter preserving current history and custom text, with explicit replay validation before installation.

## 1.8.10-SNAPSHOT

- Correct resource-pack facing through the supported FIXED item display context; Minecraft ignores the previous model `display.none` rotation.
- Redraw both Color Eight direction rings as continuous curved silhouettes with filtered edges, eliminating the separate arc/head seams.
- Verify the generated face transforms using Minecraft 26.2's actual model deserializer, plus 538 Java tests, eight asset checks and exact-JAR startup probes. Language files are unchanged.

## 1.8.9-SNAPSHOT

- Use Casino-style independent language keys in `languages/en_US.yml` and `languages/zh_CN.yml`; migrate legacy translations without overwriting custom files, and add transactional `/3dtabletop reload-language`.
- Correct resource-pack Mahjong faces, Color Eight card faces and direction arrows; restore Concept B pixel artwork and round furniture with exterior polygon faces.
- Place Mahjong winds at central panel corners, move scores inward and use green turn bars with separate outer Riichi stick slots.
- Hold the sprint key (default Ctrl) for the fixed Mahjong camera; retain Shift + right-click menus and show the pack toggle only on the main catalog page.
- Verify 537 Java tests, seven asset checks and exact-JAR native recovery/CraftEngine registration. Minecraft client visual acceptance remains separate.

## 1.8.8-SNAPSHOT

- Add optional CraftEngine item rendering and an isolated Mahjong/Color Eight resource pack, with vanilla, resource-pack and mixed per-player modes.
- Remove unused public backends based on nearby viewers, including spectators; keep private hands private and reject stale pack callbacks.
- Move Mahjong scores into the larger central panel, orient round/remaining text per viewer and turn zero remaining red.
- Reduce native round-table, rounded-card and circle geometry; add detailed pack artwork, tablecloth and nine event-bound sound effects.
- Refresh open pack menus on terminal loading status and show the Mahjong active edge; validate native three-boot recovery and 111 CraftEngine item displays. Client acceptance remains pending.

## 1.8.7-SNAPSHOT

- Replace the Mahjong Shift view with a private marker camera in temporary Spectator mode. Lock position and orientation, hide the player and equipment, and make the view read-only until release. Preserve the previous eye position.
- Cancel movement through Paper's internal correction rather than changing the move destination, which could trigger a plugin teleport and end the previous lock.
- Restore mode, flight, pose, gravity and invisibility; retain camera state when another plugin vetoes mode restoration, and recover interrupted sessions from player data. Roll back a rejected camera connection.
- Block spectator-menu teleports and target switching during focus; keep command/plugin teleports authoritative. Check camera eye clearance separately from player return clearance.
- Double Riichi stick length from 0.12 to 0.24 blocks without adding model entities.
- Document measured Mahjong and Color Eight display counts, dense-hand rendering costs and the absence of client FPS measurements. No model simplification, production deployment or public Release.

## 1.8.6-SNAPSHOT

- Move the Mahjong Shift camera exactly 1 block toward the table center and 0.2 blocks down, to a seat-relative radius of 0.65 and height of 0.88. Keep position locked with free mouse aiming, temporary invisibility and restoration on release. Standing hands and apron indicators may lie behind this closer camera.
- Raise floating round, current-player, countdown and last-discard information by 0.4 blocks. Keep the flat center counter and wind inscriptions at their existing surface positions.

## 1.8.5-SNAPSHOT

- Show one shared Browse Rooms entry in the main catalog whenever any room exists; list rooms from every game with their game name and short code. Remove the duplicate browser from each game setup.
- Require Shift + right-click to open Color Eight and Mahjong table menus. Mahjong exits its close table view before opening the menu; releasing and pressing Shift again restores the view. Focused left-click tile play remains available.
- Support the same gesture while waiting for a Mahjong round. Ignore Dialog key resets when tracking held Shift, restore focus state when a menu opens, and synchronize restored input after closing without moving the camera.
- Preserve custom menu styles, room callbacks, privacy, rules and existing layouts. Local acceptance build only; no production deployment or public Release.

## 1.8.4-SNAPSHOT

- Render each inset Dora indicator group only for its seated owner; keep indicator updates and cleanup independent from hand selection.
- Replace the transparent native foil substrate with an opaque smooth-quartz face behind the original strokes. Native glint metadata remains enabled; the reported missing client glint still requires an in-game check of this build.

## 1.8.3-SNAPSHOT

- Refine native stroke artwork for character, wind, dragon and flower tile inscriptions.
- Inset public Dora indicators into the center of each wooden front apron, facing that seat; preserve physical indicator IDs and rules.
- Fill each six-tile river row left-to-right, starting near the center and progressing toward its owner.
- Add native enchanted ItemDisplay overlays to Riichi Dora and red fives in the owner hand and public rivers/melds; update when indicators change and never reveal hidden Ura Dora or opponents’ hands.
- Keep the Mahjong Shift camera outside the inset indicator faces while preserving readable standing hands and temporary invisibility.
- Dim unplayable Color Eight cards through brightness without changing their artwork; reduce hover lift from 0.27 to 0.085 blocks.
- Arrange Color Eight hands in a parallel diagonal overlap from left to right, each later card slightly in front, retaining bounded hand width.
- Require a legal hand play when no draw supply remains, including timeout; allow pass only for unplayable hands. Retain discard recycling and replay of already recorded exhaustion passes.

## 1.8.2-SNAPSHOT

- Dim Mahjong tiles only when Chi/Pon kuikae forbids discarding them, using native Display brightness (15 to 7) without replacing materials or artwork.
- Restore normal brightness after the discard; waiting turns, Riichi selection and other action restrictions retain normal brightness and legal-action enforcement.

## 1.8.1-SNAPSHOT

- Round the card table with 64 sides made from centered, rotated native cuboids, retaining its three-block diameter.
- Replace card pixels with original native stroke digits and icons, rounded card edges and separated 6/9 underlines.
- Show four pure-color round buttons above Wild Eight; the selected color still becomes the played Eight background.
- Refine Mahjong circle rings, bamboo joints, one-bamboo bird, white-dragon frame and flowers; retain tile dimensions, red fives and honor/character ink.
- Preserve owner-only controls, dense-hand aiming, hover lift, gray disabled pieces and game rules. Local acceptance build; Minecraft client visuals pending.

## 1.8.0-SNAPSHOT

- Replace Last Card rules with Color Eight: 54 cards, 2–5 players, Wild Eight, Draw One for every opponent, Skip, Reverse and Swap Hands; first empty hand wins before the last card effect.
- Add original Concept B native card art, colored played Eights, owner-only color buttons, fixed-width overlapping hands and one-card hover lift.
- Add a reusable native round card table with a three-block diameter; retain the rotating direction ring.
- Use a fixed 30-second human turn, including offline turns; drawing/choosing color does not reset the clock.
- Archive old Last Card histories with a full save backup before restoring other games. New card histories use rules version 2; command id remains `lastcard`.
- New language keys avoid retired names and penalty text from old installed language files without overwriting edits.
- Local acceptance build only; client visual/interaction acceptance pending.


## 1.7.5-SNAPSHOT — development build, no Release

- Restore full-size public Mahjong tiles, enlarge wall digits and put dora indicators at the frame midpoint; keep wind inscriptions clear of melds.
- Lower Shift focus while preserving standing hand-face readability and invisibility restoration.
- Keep old hand positions stable on draws, leave a rightmost draw gap and guard replacement discards during continuous clicks across turns.
- Gray unavailable tiles, show red No Yaku on complete Riichi shapes and offer two-stage Chi/Pon/Kan, armed Riichi, Ron and Tsumo buttons.
- Preserve physical Sichuan exchange/missing-suit preparation and necessary Go/Last Card state actions.
- Remove full hand/board move-menu entry points and chance/hand undo requests; standardize every Close button without rewriting installed YAML.

## 1.7.4-SNAPSHOT — development build, no Release

- Enlarge Mahjong tables to 3×3, add seat wind inscriptions and inward-facing scores, enlarge the wall counter and place melds at each player's lower-right corner.
- Privately highlight matching own/public tiles from either hover target; keep newly drawn tiles rightmost and color Remaining zero red.
- Move Shift focus closer while temporarily hiding the player and equipment; restore prior visibility and gravity on exit.
- Add distinct vanilla Mahjong draw/discard/call/riichi/win sounds.
- Remove Rules & Help, Room Details and Public Table Details menu entries while retaining setup rule editing.
- Remove Fuzhou and Qinhuangdao Mahjong profiles; retain Riichi, Guangdong, Sichuan and Taiwan. Removed-profile saves are rejected and backed up rather than converted.


## 1.7.3-SNAPSHOT — development build, no Release

- Raise and highlight only the selected Mahjong tile, keep neighbors still and fit up to 17 tiles in one row.
- Show private unseen-copy counts from the owner’s hand and public tiles, with physical-ID deduplication and no concealed-opponent reads.
- Add four-way drawable wall counts, shared turn-deadline countdowns, round/turn/last-discard labels and red-dot riichi sticks.
- Hold Shift for a fixed elevated table position with free mouse aim; release to restore the entry pose. Clear focus on menus, leaving, death, external teleports and shutdown.
- Preview native tile combinations above private Chi/Pon/Kan buttons and expand multiple choices directly on the table, revalidating current legal actions.
- Preserve rules, schema 1 room data, custom YAML and resource-pack-free play. Source and local acceptance build only; no Release.

## 1.7.2-SNAPSHOT — development build, no Release

- Highlight Mahjong tiles in place without raising or separating them. Display owner-only legal call buttons above the hand, preserving every combination in the existing choice dialog.
- Skip a legal response through the rules engine; dismiss optional self-turn prompts without consuming the turn. Re-entering the table can show the still-legal prompt again.
- Replace fixed Last Card arrows with a slowly rotating native curved ring. Reverse changes its direction; ended/paused rooms and pending undo stop the animation.
- Underline central and corner 6/9 artwork without changing the existing 1–8 deck or saved rules.
- Preserve room data, custom language/menu files and resource-pack-free play. Local acceptance only; no Release.

## 1.7.1-SNAPSHOT — development build, no Release

- Build original Last Card and Mahjong face patterns from native block displays; no resource pack or client mod is required. Keep concealed faces visible only to their owner.
- Enlarge and stand Last Card hands upright. Hover lifts the selected card and moves its neighbors aside; right-clicking the central deck performs a legal draw. Newly drawn cards animate from the deck, while initial display and room recovery appear at rest.
- Keep Chinese Xiangqi inscriptions, including 砲. Remove floating Ludo pawn numbers and the dice stand's artificial shadow mesh.
- Separate basic setup from detailed rule settings. Focus the lobby on readiness and starting, active rooms on returning to the table and playing, and private-hand pages on the owner's cards and current actions.
- Apply the revised setup, room and hand layouts in memory only when installed templates match the original 1.7.0 stock content. Leave all installed file bytes unchanged; retain customized ordering and styles.
- Preserve schema 1 room data and existing game rules. Local previews still require Minecraft client acceptance; no Release or production deployment.

## 1.7.0-SNAPSHOT — development build, no Release

- Select a game, friends/bots and immutable room rules before creating it. Add matching game options for Ludo, Gomoku, Chinese Checkers and starting sides.
- Add a separate native dice stand with traveling, tumbling, bouncing and authoritative face settling. New table placement includes its footprint; legacy anchors use a compact stand.
- Add original Last Card rules/models with private hands, public color/draw-penalty feedback and first/all-place finishes.
- Add six independently implemented Mahjong house profiles, region-specific settings, private tile models, public table details and match scoring. Document regional differences and source boundaries.
- Persist rule options, rule version, host identity and dice stand layout. Keep legacy room replay, world anchors, language overrides and custom Dialog layouts.
- Extend menu, privacy, rule, model, recovery and isolated-server probes. No production deployment or automatic Release.

## 1.6.1-SNAPSHOT — local acceptance build

- Add quiet native sound profiles for every available game and restored Aeroplane rooms, with distinct captures, Reversi flips, dice rolls, Ludo home arrivals and Go scoring controls.
- Add start, win/draw and completed-undo cues; notify only the nearby eligible human whose turn has begun.
- Trigger move sounds only after successful actions, including bots and menu moves. Rendering, rejected moves, metadata refreshes and startup replay remain silent.
- Add `sounds.enabled` and `sounds.volume` (0–1), covering selection and private turn feedback as well as shared table sounds. Existing configurations use defaults without being overwritten.
- No resource pack, dependency, room schema change, Release or production deployment.

## 1.6.0-SNAPSHOT — local acceptance build

- Replace new Aeroplane Chess creation with Ludo for 2–4 players: automatic first deployment, six to deploy/roll again, captures, no blocking and exact finish. Preserve legacy Aeroplane rules and saved rooms.
- Add original cross-board artwork and compact pawn models, private movable-pawn markers and destination previews. Single pawns move on one click; stacked pawns use a labeled choice dialog.
- Preserve the dice stream when undoing a full Ludo turn. Add rules, commands, tab completion and editable English messages.
- Round Xiangqi/Chinese Checkers bases and make occupied capture destinations visible around pieces; retain lightweight Go meshes.
- Keep existing translation overrides intact and label restored Aeroplane rooms as Legacy. See [rules and migration](docs/ludo.zh-CN.md).
- Source and local acceptance artifacts only; no Release or production deployment.

## 1.5.1-SNAPSHOT — local acceptance build

- Restore the player's original collision setting when leaving the table area, changing worlds, disconnecting or disabling the plugin; limit hunger protection to authorized seated players within six blocks of their table.
- Clear private selections when suspending a board view, including external menu transitions.
- Make new tables immediately reachable by sneak-right-click and accept menu clicks on the upper Connect Four rack from either side.
- Reject overlapping new table placement before changing room anchors; keep existing saved layouts readable.
- Show unavailable placements and full columns in the action bar instead of filling chat.
- Add an isolated eleven-game continuous-play probe and multi-game snapshot recovery checks, with optional soak evidence in local delivery records.
- Run the Java compiler in a separate process for each compilation to avoid a reproduced JDK 25 compiler state failure. Runtime dependencies and schema 1 data are unchanged.

## 1.5.0-SNAPSHOT — local acceptance build

- Browse every room across pages, with available seats first, persistent create/help controls and clear Join / Resume / View labels.
- Add rules/help from each game and room options, plus menu-based moves from an active room. Single placement/drop/dead-mark choices execute directly; column labels start at 1.
- Preview the legal Connect Four landing slot privately on both rack faces; explain full columns and waiting states, and throttle unchanged hints.
- Read current pieces for hover text and refresh dice feedback when rolling finishes.
- Reuse Go stones when marking/unmarking dead groups, face chess knights toward their opponent, and remove overlapping disc surfaces without adding model entities.
- Reject callbacks for removed rooms, refresh outdated piece choices, recheck permissions and prevent an old leave confirmation from leaving a newer room.
- Let three exact historical stock room captions follow the selected named language without rewriting customized menu files.
- Preserve schema 1 room data, existing rules and all optional integration boundaries. No new runtime dependency or Release.

## 1.4.1-SNAPSHOT — local acceptance build

- Skip title construction and rule reads while table display state is unchanged; only tick moving or flipping pieces.
- Keep immediate refresh for seat/phase changes, completed dice rolls and reconstructed boards.
- Move menus, chat, action hints, rule summaries and room presentation to named messages with literal parameters.
- Preserve human names containing translation words or formatting markers; identify bots from seat metadata.
- Derive named templates from customized legacy translations at language load time; explicit named values take precedence.
- Preserve existing language/menu files and schema 1 room records. No rule changes or additional runtime dependencies.

## 1.4.0-SNAPSHOT — local acceptance build

- Organize bilingual entry documentation, architecture, language, migration and verification guides.
- Add build/test CI and corresponding-source packaging; CI does not publish Releases.
- Add MiniMessage display formatting with legacy colors and literal named parameters.
- Add named messages for common navigation, leave confirmation and table status; preserve legacy language files.
- Fit long table labels within a bounded area while keeping instruction lines.
- Replace Connect Four cubes with stepped round discs, reinforce the rack and animate vertical column drops.
- Reuse Reversi piece entities and animate two-sided flips.
- Keep schema 1 room data, existing rules, commands, permissions and optional integrations.

## 1.3.1

- Translate the leave-confirmation button, set the default menu title to 3D Tabletop Games, and hide the duplicate namespaced root suggestion.

## 1.3.0

- Rename the runtime namespace to `3dtabletop` and Java package to `dev.tabletop3d`.
- Add default-English YAML translations and legacy ServerBoards folder migration.

Earlier history is available through repository tags and commits.
