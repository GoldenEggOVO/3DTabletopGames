# 按游戏和职责阅读源码

规则源码位于 `src/main/java/dev/tabletop3d/rules/`，按游戏分类：

| 目录 | 内容 |
| --- | --- |
| `connectfour/` | 四子棋 |
| `xiangqi/` | 中国象棋，`engine/` 为内置规则库 |
| `chess/` | 国际象棋，`engine/` 为内置规则库 |
| `gomoku/` | 五子棋及选项 |
| `go/` | 围棋 |
| `reversi/` | 黑白棋 |
| `draughts/` | 西洋跳棋 |
| `chinesecheckers/` | 中国跳棋及选项，`engine/` 为内置规则库 |
| `ludo/` | Ludo 及选项 |
| `coloreight/` | Color Eight |
| `doudizhu/` | 斗地主及牌型判断 |
| `liarsbar/` | 骗子酒馆 |
| `texasholdem/` | 德州扑克、牌力判断和边池计算 |
| `yacht/` | 快艇骰子 |
| `mahjong/` | 四种麻将规则、计分和牌型工具 |
| `cards/` | 斗地主、骗子酒馆和德州扑克共用的扑克牌 |

`rules/` 根目录保留共用接口、坐标、规则错误、游戏注册和选项分发。单个游戏的测试放到 `src/test/java/dev/tabletop3d/rules/` 对应目录；跨游戏测试保留在公共目录。

此前的 `upstream/` 是保留第三方来源的规则库源码，不是“上流”玩法。现在它们分别归入对应游戏的 `engine/`，原始版权和来源说明仍保留。

## 共用实现按职责分类

以下目录均位于 `src/main/java/dev/tabletop3d/`：

| 目录 | 内容和阅读入口 |
| --- | --- |
| `room/` | 房间状态 `Room`、历史 `RoomHistory`、存档 `RoomStore`、占用和回合管理 |
| `bot/` | 机器人策略 `BoardBots` |
| `menu/` | 菜单会话 `GameMenus`、固定布局 `BoardWindow`、德州加注输入和命令建议 |
| `interaction/` | 桌面输入 `GameWorld`、摆桌入口 `TableLobby`、玩家视角 `TableComfort` |
| `text/` | 翻译加载 `Language`、房间／手牌／扑克的文字呈现 |
| `ui/` | 共用文字格式和标签排版工具 |
| `render/` | `TableView`、共用手牌视图 `HandTable`、几何、观众、桌体和地图 |
| `render/cards/` | 卡牌布局、头颅牌面、圆桌和回合指示器 |
| `render/mahjong/` | 麻将模型、布局、辅助开关和桌面显示 |
| `render/dice/` | 骰子动画、托盘、快艇骰子操作和计分桌 |
| `resource/` | 可选 CraftEngine 接口、资源模型和资源包发送 |
| `audio/` | `TableSounds`：按已完成的操作选择并播放声音 |

根目录保留两个可执行入口：`Tabletop3D` 是插件入口，`RoomReplayVerifier` 是离线存档校验入口。入口名称保持一致，已有启动配置和校验命令可继续使用。

测试按所验证的实现放到对应目录；跨模块测试放在主要被测实现旁，共用测试临时目录工具位于 `support/`。本次只整理目录、引用和必要的跨包访问范围，保持玩法、语言、模型、游戏标识和房间存档格式。
