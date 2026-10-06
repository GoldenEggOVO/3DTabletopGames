# 按游戏阅读源码

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

插件入口、房间、菜单、渲染及输入处理仍在 `dev/tabletop3d/`，因为这些代码由多个游戏共用。本次分类保持玩法、语言、模型、游戏标识和房间存档格式不变。
