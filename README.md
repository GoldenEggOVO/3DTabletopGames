# 3dtabletop 1.3.0

独立的 3D 实体棋盘与房间插件，适用于 Java 25、Paper／Purpur 26.2。Java 包为 `dev.tabletop3d`。可单独安装；不依赖 ServerGames、ServerMenu、ServerCasino 或 KaMenu。菜单使用 Paper 原生 Dialog，棋盘仍可直接点击。

## 安装与升级

停服后将 `3dtabletop-1.3.0.jar` 放入 `plugins/`，删除旧的 ServerBoards JAR，同一服务器仅留一个版本。不要安装 `original-*.jar`。首次启动会生成 `plugins/3dtabletop/`。若存在 `plugins/ServerBoards/` 且新目录尚不存在，插件会复制原配置、菜单、`rooms.json` 等文件到新目录，并保留旧目录和 `migration-from-serverboards.txt` 标记。升级前先备份旧目录；两个目录都存在但没有迁移标记时，插件会拒绝启动，避免覆盖数据。详见 [MIGRATION.md](MIGRATION.md)。

## 指令与权限

| 指令 | 用途 |
| --- | --- |
| `/3dtabletop`、`/3dtabletop menu` | 打开原生 Dialog 列表 |
| `/3dtabletop create <kind> [人数]` | 建桌 |
| `/3dtabletop join <房间 UUID 前缀>` | 加入房间 |
| `/3dtabletop ready`、`bots` | 准备、房主补机器人开局 |
| `/3dtabletop move <规则动作>` | 直接落子，例如四子棋 `drop:3` |
| `/3dtabletop resume`、`leave` | 返回棋桌、退出 |
| `/3dtabletop undo`、`rematch` | 协商悔棋、再来一局 |
| `/3dtabletop rules [kind]` | 查看规则 |
| `/3dtabletop status` | 控制台查看房间数量 |

Tab 会补全子指令、游戏 ID、人数、开放房间和可落子动作。`kind` 支持 `xiangqi`、`gomoku`、`chess`、`aeroplane`、`checkers`、`draughts`、`reversi`、`go`、`go9`、`go13`、`connectfour`。`3dtabletop.use` 默认开放给玩家，控制菜单和对局操作；`3dtabletop.admin` 默认仅 OP。安装 AuthMe 时，玩家还须先登录。旧 `/boards`、`serverboards:boards` 和 `serverboards.use` 已移除。

## 配置与语言

`plugins/3dtabletop/config.yml` 的 `language: en` 选择语言文件；英文为默认值。`plugins/3dtabletop/lang/en.yml` 是可编辑的完整语言表。可复制为 `lang/<code>.yml`，编辑 `translations` 中的显示文字，再将 `language` 设为 `<code>` 并重启。自定义文件缺少的项会回退到英文。菜单、聊天提示、实体棋盘标牌与棋子文字均使用该语言表；玩家姓名、房间 ID 和持久化动作不应翻译。`menus/*.yml` 仍可编辑 Dialog 外观与按钮文字，旧中文模板会在显示时按语言表转换，按钮动作由服务端校验。

其余配置：`max-rooms`、`reconnect-seconds`、`idle-room-minutes`、`turn-seconds`。房间、座位、世界 UUID、棋桌坐标、随机种子、动作历史保存在 schema 1 `rooms.json`；重启时由历史重放恢复。退出、离线保留、观战、悔棋和再来一局等功能见 [FEATURES.md](FEATURES.md)。

## 可选接入与兼容边界

插件自身不调用 ServerGames 的服务，也不要求 ServerMenu。当前 ServerGames 2.0.3 的 `/sg menu` 和 ServerMenu 0.7.1 的棋牌页面仍指向旧 `serverboards:boards`，因此安装这些旧版本时，其入口不会打开本版本。它们需要在各自项目中把转发目标改为 `/3dtabletop menu`；本仓库没有修改这两个插件。独立服始终可用 `/3dtabletop` 完成全部操作。实体资源键改为 `3dtabletop:board-cell`；旧棋盘实体会按原有历史重建。

## 构建与验收

在工作区根目录用 Java 25 和自带 Maven 执行：

```powershell
& .\.tools\apache-maven-3.9.11\bin\mvn.cmd -o "-Dmaven.repo.local=$PWD\.tools\m2" -f server-boards/pom.xml package
python -X utf8 server-boards/probe/run_standalone.py
```

探针在仅装本插件与验收探针的本地 Purpur 26.2 服测试创建、权限、Dialog 回调、四子棋落子、实体生成、重启恢复和旧目录迁移。客户端实际画面与手感由服主验收。许可证、第三方来源与原项目归属见 `LICENSE` 及 JAR 内的 `META-INF/` 声明。
