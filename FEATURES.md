# 3dtabletop 功能与验收清单

此清单按原 ServerBoards 源码、菜单、配置与测试逐项核对。`YachtGame` 仅为历史规则源码，不在目录中；卡牌、麻将未启用。

| 功能 | 入口或数据 | 验收 |
| --- | --- | --- |
| 象棋、五子棋、国际象棋、飞行棋、中国跳棋、西洋跳棋、黑白棋、9／13／19 路围棋、7×6 四子棋 | `GameFactory`、`/3dtabletop create`、Dialog 目录 | 各规则测试，含四子棋重力与胜负 |
| 建桌、列房、加入、人数、单人单座 | `Room`、`BoardOccupancy`、`GameMenus` | 房间及占座测试，干净服建桌 |
| 准备、机器人、回合、超时代走 | `ready`、`bots`、`move`、`tick` | 房间与规则测试，干净服四子棋落子 |
| 观战、回桌、退出、离线保留 | `GameMenus.observe`、`resume`、`leave` | 菜单与座位测试，重启恢复 |
| 协商悔棋、再来一局、历史重放 | `RoundActions`、`history` | 规则与恢复测试 |
| Paper 原生 Dialog、可编辑菜单、单次回调校验 | `BoardWindow`、`GameMenuLayouts`、`menus/*.yml` | `WindowMenuTest`、`NativeMenuTest`、干净服回调 |
| 3D 实体棋盘、模型、射线点击、指针、桌边显示 | `GameWorld`、`TableView`、`TableLobby` | 对应单测、干净服实体生成；画面由用户验收 |
| 英文默认与 YAML 语言切换 | `config.yml`、`lang/en.yml` | `LanguageTest`，菜单与桌面文字测试 |
| 权限及 AuthMe 登录门禁 | `3dtabletop.use`、`allowed` | 权限测试、干净服拒绝无权限操作 |
| 配置、房间与棋盘持久化 | `config.yml`、`rooms.json`、旧目录复制 | 数据迁移测试、干净服重启恢复 |
| Tab 补全 | `CommandSuggestions` | `CommandSuggestionsTest` |

本地 Maven 126 项测试通过，失败、错误、跳过均为 0。Purpur 26.2 三次独立启动已验证创建、重启恢复及旧目录迁移；探针使用模拟玩家触发 Dialog，实际客户端画面和手感仍由用户验收。旧 ServerGames／ServerMenu 命令转发需要另行升级，未计为本版本通过项。
