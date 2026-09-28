# 升级至 3dtabletop 1.5.1-SNAPSHOT

## 已有 3dtabletop 安装

从 1.3.x、1.4.x 或 1.5.0 验收版升级时，停服备份后替换 JAR，保留 `config.yml`、`lang/`、`menus/` 和 `rooms.json`，无需转换数据。旧语言文件不会覆盖；新命名消息由内置英文补齐，也可按 [语言说明](languages.md) 添加到已有 `messages` 中。客户端画面与交互需用本地包重新验收。

新棋桌间距检查只在创建时执行。旧存档中相邻较近的桌子仍按原坐标恢复，不会被移动或清空。

## ServerBoards 数据目录

1. 完全停服，备份 `plugins/ServerBoards/`，记录原 `rooms.json` 的 SHA-256，移除旧 ServerBoards JAR。
2. 安装 `3dtabletop-1.5.1-SNAPSHOT.jar` 并启动。若新目录不存在，插件会将旧目录**复制**为 `plugins/3dtabletop/`，添加 `migration-from-serverboards.txt`；旧目录不删除。
3. 检查 `config.yml`、`menus/*.yml`、`rooms.json` 的副本及 SHA-256。旧配置未写 `language` 时自动使用英文；可手动添加 `language: en`。执行 `/3dtabletop status`，玩家用 `/3dtabletop resume` 查看座位、棋盘和历史，再正常重启检查恢复。

房间 JSON schema 1、世界 UUID、实体桌面坐标、规则动作与菜单配置保持可读。两个数据目录都存在但无迁移标记时插件会拒绝启动；先在停服状态核对并备份两边数据，手工决定使用哪一份。恢复失败时插件保留源文件并另存不可读副本，不会清空房间。原有 `/boards`、`serverboards:boards`、`serverboards.use` 不再注册；请更新自己的命令、权限与菜单转发配置。ServerGames 2.0.3 和 ServerMenu 0.7.1 的旧入口也需在各自项目更新。

## 从旧 ServerGames 棋类房间导入

旧 `plugins/ServerGames/rooms.json` 可能缺少棋桌世界和坐标，不能自动推断。仅迁移棋类房间，并为每个房间明确指定已加载世界的 UUID 与棋桌坐标。先备份源文件；不要将卡牌、麻将房间混入输入。示例 `anchors.json`：

```json
{
  "room-uuid-from-source": {
    "world": "target-world-uuid",
    "x": 100.5,
    "y": 83,
    "z": -20.5
  }
}
```

已有完整锚点的旧 Boards 文件使用 `{}`。脚本检查房间、人数、重复占座、坐标，并使用即将安装的 JAR 逐条重放动作；失败时拒绝产生候选文件，不改源文件或覆盖输出。默认读取同目录或 `target/` 下的 `3dtabletop-1.5.1-SNAPSHOT.jar`，也可传 `--jar`：

```powershell
python server-boards/tools/server_boards_migrate.py old-rooms.json anchors.json candidate-rooms.json
python server-boards/tools/server_boards_migrate.py old-rooms.json anchors.json candidate-rooms.json --check
```

两次命令打印源与输出的 SHA-256。`--check` 确认差异仅为指定锚点及缺失时补齐的空 `returns`。某房间无法重放时，保留原始备份和该房间记录，检查报告中的房间 ID 与动作序号，不要改写动作历史。

将通过校验的 `candidate-rooms.json` 放到**已停止**的隔离测试服 `plugins/3dtabletop/rooms.json`；世界 UUID 必须匹配。启动、执行 `/3dtabletop status` 和 `/3dtabletop resume`，对照房间数、座位、棋盘与历史，再正常重启复验。生产服迁移需另行授权，并遵循同样的停服、备份、校验、替换流程。
