# 3dtabletop 功能与验收清单

此清单按 3dtabletop 源码、菜单、配置与测试逐项核对。`YachtGame` 仅为历史规则源码，不在目录中；当前支持 Last Card 和四种麻将房规。

| 功能 | 入口或数据 | 验收 |
| --- | --- | --- |
| 象棋、五子棋、国际象棋、英国十字戏、中国跳棋、西洋跳棋、黑白棋、9／13／19 路围棋、7×6 四子棋 | `GameFactory`、`/3dtabletop create`、Dialog 目录 | 各规则测试，含四子棋重力与胜负 |
| 建桌、列房、加入、人数、单人单座 | `Room`、`BoardOccupancy`、`GameMenus` | 房间及占座测试，干净服建桌 |
| 准备、机器人、回合、超时代走 | `ready`、`bots`、`move`、`tick` | 房间与规则测试，干净服四子棋落子 |
| 观战、回桌、退出、离线保留 | `GameMenus.observe`、`resume`、`leave` | 菜单与座位测试，重启恢复 |
| 协商悔棋、再来一局、历史重放 | `RoundActions`、`history` | 规则与恢复测试 |
| Paper 原生 Dialog、可编辑菜单、单次回调校验 | `BoardWindow`、`GameMenuLayouts`、`menus/*.yml` | `WindowMenuTest`、`NativeMenuTest`、干净服回调 |
| 3D 实体棋盘、模型、射线点击、指针、桌边显示 | `GameWorld`、`TableView`、`TableLobby` | 对应单测、干净服实体生成；画面由用户验收 |
| 英文默认与 YAML 语言切换 | `config.yml`、`languages/en_US.yml` | `LanguageTest`，菜单与桌面文字测试 |
| 权限及 AuthMe 登录门禁 | `3dtabletop.use`、`allowed` | 权限测试、干净服拒绝无权限操作 |
| 配置、房间与棋盘持久化 | `config.yml`、`rooms.json`、旧目录复制 | 数据迁移测试、干净服重启恢复 |
| Tab 补全 | `CommandSuggestions` | `CommandSuggestionsTest` |
| 游戏模式、先后手、可选规则 | `GameOptions`、原生设置页、不可变房间规则 | 菜单、座位调整、保存与重放测试 |
| Last Card、四地区麻将、局内计分 | `HandGame`、各地区规则、私有手牌与公开桌面 | 牌型、回合、物理牌与点数守恒、回放测试 |
| 牌背和暗杠隐私 | 隐藏后生成的私人 Display，牌背及匿名暗杠 ID | `HandTableTest`、`HandMenuTest`、服务器元数据探针 |
| 独立投骰台 | 位移、翻滚、反弹、点数停稳、动画期间锁操作 | 动画、放置、共享入口与服务器模型测试 |

本批验证结果见 [verification.md](verification.md)。模型自动验收覆盖四子棋竖直落子、重启静止恢复、黑白棋实体复用与翻面结束状态；实际客户端画面与手感由用户验收。

新规则的完整范围及地区取舍见 [游戏模式](game-modes.zh-CN.md) 和 [麻将房规](mahjong.zh-CN.md)。历史飞行棋继续按原规则恢复；没有重开停用的 Yacht 或斗地主。

## 1.5.0-SNAPSHOT 操作改进

- 房间每页 8 项，优先显示自己的房间及可加入的空位房间；每页保留创建和规则帮助。
- 对局菜单提供菜单操作入口；落子、四子棋选列、围棋死子标记单个选项可直接执行。移动棋子的游戏保留选棋与选目标两步。
- 四子棋显示当前合法落点的私人绿色预览，棋架两侧均有突出表面；满列和等待状态有独立提示。
- 围棋反复标记死子只增减红色标记，国际象棋双方的马朝向对面；圆形棋子保持三个实体并避免表面重叠。
- 旧菜单无法操作已删除房间，旧退出确认不会退出后来加入的新房间。

这些变化不修改规则、存档格式或房间数据。自动验收范围见 [验证说明](verification.md)，客户端画面与手感仍需要实际确认。
