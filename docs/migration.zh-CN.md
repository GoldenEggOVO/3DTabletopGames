# 升级至 3dtabletop 1.7.5-SNAPSHOT

## 1.7.5 实时交互与可读性

规则、房间 schema、实体牌 ID、种子和历史不变。公开牌与桌心数字放大，宝牌指示牌及风位移位；升级后在隔离服检查布局与 Shift 视角。完整手牌／棋盘选择菜单入口及手牌／随机游戏的悔棋申请移除，围棋和 Last Card 必要状态按钮及四川麻将桌面准备操作保留。Close 按钮渲染统一，自定义模板的其他内容保留，磁盘 YAML 不被覆盖。新增无役及立直选牌消息使用内置英文回退，可手动添加语言覆盖。

## 1.7.4 桌面与模式调整

麻将桌在原有锚点周围扩大至 3×3；升级前检查周围地形与其他桌子的间距，旧锚点不会自动移动。同牌高亮仅本人可见，新摸牌居右，Shift 靠近桌面时临时隐藏玩家，退出恢复原可见状态。自定义菜单中的帮助／详情入口也不再提供，创建时的规则设置保留，已安装 YAML 不被覆盖。

麻将仅保留日麻、广东、四川和台湾。若房间文件包含福州或秦皇岛模式，插件会拒绝读取并生成 unreadable 备份，不自动换规则、不覆盖原始文件。请先在旧版本结束／移除这两种房间，再备份升级。本版仍为开发快照，未发布 Release。


## 1.7.3 麻将桌面布局与视角

先停隔离测试服并备份，再替换 JAR。保留配置、世界、语言、菜单以及 schema 1 房间数据；规则、选项、种子和动作历史不变，无需转换或清空。最近弃牌信息通过存档动作重放恢复，无需资源包。

本版将 1.7.2 的原地高亮改为选中牌高亮并抬起，邻牌不动。新增余牌、局数、倒计时、弃牌与立直棒文字使用 `table.mahjong.*` 英文回退，可在语言 YAML 覆盖，不替换自定义文件。Shift 临时进入桌面高位视角，松开后在返回位置可用且传送获准时返回。详见[桌面信息与视角](game-modes.zh-CN.md#麻将桌面信息与视角)及[客户端验收单](acceptance.zh-CN.md)。本版仅本地交付，未发布 Release。

## 1.7.2 麻将桌面操作与方向环

隔离测试服停服备份后替换 JAR，保留全部配置、语言、菜单和房间数据。schema、种子、规则选项、手牌与动作历史没有变化，无需迁移。新增 `table.mahjong.*` 按钮标签使用内置英文回退，可在语言 YML 中覆盖。仍免资源包，本次不发布 Release。

麻将仅原地高亮；本人手牌上方显示当前合法操作，多种吃／杠组合进入选择页。“跳过”在响应阶段执行规则允许的 pass，在自己回合只收起可选操作提示，仍可出牌；离开视距再回桌可重新显示合法提示。Last Card 中央方向环随出牌方向转动；6／9图案增加下划线，当前1–8牌库及旧局规则不改。更新后按客户端验收单检查。

## 1.7.1 牌面与菜单整理

本版仍为开发构建，未发布 Release，本次未部署正式服。先在隔离测试服停服备份，再替换 JAR；保留配置、语言、菜单、世界和 `rooms.json`。

本次调整显示与菜单体验：Last Card 和麻将采用原生方块拼出的原创牌面，无需资源包；Last Card 手牌放大竖立，增加悬停让位与实时摸牌动画。右键中央牌堆仍须满足合法摸牌条件，恢复时手牌直接到位，不重播旧摸牌。象棋保留中文棋面，包括“砲”；Ludo 移除小人浮动编号，骰子移除额外假影模型。

schema 1、`rulesVersion: 1`、已保存的规则选项、房间 ID、世界坐标、座位、种子和动作历史的含义不变，现有游戏规则不变，无需转换房间数据。

基础设置与详细规则分为两页，等候、对局与手牌页简化。仅当 `menus/setup.yml`、`menus/room.yml`、`menus/hand.yml` 的 SHA-256 内容指纹匹配 1.7.0 原始默认模板时，才会**在内存中**采用新版布局。匹配时统一换行并忽略文件首尾空白；磁盘文件的字节不会被这一处理改动。

自定义 YML 继续使用原顺序和样式。需要新版外观时，先备份已有文件，再手动合并新版 JAR 内对应的 `menus/` 模板。缺失语言键使用内置英文回退。部署前分别检查默认模板和自己的定制布局；本地模型预览不能替代 [Minecraft 客户端验收](acceptance.zh-CN.md)。

## 1.7.0 游戏模式与手牌桌

停服备份整个 `plugins/3dtabletop/` 后替换 JAR。保留现有配置、语言、菜单和房间文件；新增的 `menus/setup.yml`、`menus/hand.yml` 会补齐，已有文件不覆盖，新语言键由内置英文回退。

房间仍使用 schema 1，新增 `rulesVersion: 1`、`options`、`owner`、`sideTray`。旧房间缺少这些字段时继续使用原规则、原世界和原坐标；旧骰子桌使用紧凑投骰台，新建骰子桌才使用独立大投骰台。新房间的规则在创建时锁定，重启、悔棋和重赛沿用相同设置。先手设置改变座位时，房主权限仍属于创建者。

新增 `lastcard` 和四人 `mahjong`，后者默认日本立直，其他地区通过设置页选择。它们采用自己的房间历史，不导入 MahjongCraft 或旧卡牌／麻将插件存档。不要把参考 JAR 放进本插件目录。

升级后分别检查旧房间恢复、带自定义规则的新房间重启恢复、私人手牌可见性和菜单回调。若需要退回旧版，停服后恢复升级前备份；旧版不理解新增游戏与规则字段，不能直接拿新存档降级。具体房规见 [游戏模式](game-modes.zh-CN.md) 与 [六种麻将](mahjong.zh-CN.md)。

## 已有 3dtabletop 安装

从 1.3.x、1.4.x 或 1.5.0 验收版升级时，停服备份后替换 JAR，保留 `config.yml`、`lang/`、`menus/` 和 `rooms.json`，无需转换数据。旧语言文件不会覆盖；新命名消息由内置英文补齐，也可按 [语言说明](languages.md) 添加到已有 `messages` 中。客户端画面与交互需用本地包重新验收。

新棋桌间距检查只在创建时执行。旧存档中相邻较近的桌子仍按原坐标恢复，不会被移动或清空。

## ServerBoards 数据目录

1. 完全停服，备份 `plugins/ServerBoards/`，记录原 `rooms.json` 的 SHA-256，移除旧 ServerBoards JAR。
2. 安装 `3dtabletop-1.7.1-SNAPSHOT.jar` 并启动。若新目录不存在，插件会将旧目录**复制**为 `plugins/3dtabletop/`，添加 `migration-from-serverboards.txt`；旧目录不删除。
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

已有完整锚点的旧 Boards 文件使用 `{}`。脚本检查房间、人数、重复占座、坐标，并使用即将安装的 JAR 逐条重放动作；失败时拒绝产生候选文件，不改源文件或覆盖输出。通过 `--jar` 明确指定本次准备安装的构建：

```powershell
python server-boards/tools/server_boards_migrate.py old-rooms.json anchors.json candidate-rooms.json --jar server-boards/target/3dtabletop-1.7.1-SNAPSHOT.jar
python server-boards/tools/server_boards_migrate.py old-rooms.json anchors.json candidate-rooms.json --jar server-boards/target/3dtabletop-1.7.1-SNAPSHOT.jar --check
```

两次命令打印源与输出的 SHA-256。`--check` 确认差异仅为指定锚点及缺失时补齐的空 `returns`。某房间无法重放时，保留原始备份和该房间记录，检查报告中的房间 ID 与动作序号，不要改写动作历史。

将通过校验的 `candidate-rooms.json` 放到**已停止**的隔离测试服 `plugins/3dtabletop/rooms.json`；世界 UUID 必须匹配。启动、执行 `/3dtabletop status` 和 `/3dtabletop resume`，对照房间数、座位、棋盘与历史，再正常重启复验。生产服迁移需另行授权，并遵循同样的停服、备份、校验、替换流程。
