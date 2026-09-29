# 3DTabletopGames

[English](README.md) | [简体中文](README.zh-CN.md)

**带实体棋子、多人房间和原生 Dialog 菜单的 Minecraft 3D 桌游插件。**

支持 **Paper／Purpur 26.2、Java 25**。无需资源包或客户端模组，可独立安装；AuthMe 为可选接入。

## 安装

1. 从 [Releases](https://github.com/GoldenEggOVO/3DTabletopGames/releases) 下载已发布版本。
2. 完全停服并备份插件数据与世界。将 JAR 放入 `plugins/`，只留一个版本，不安装 `original-*.jar`。
3. 启动后使用 `/3dtabletop` 创建房间、邀请玩家或添加陪练，在实体桌面操作。

当前源码为 **1.7.1-SNAPSHOT 开发版**，尚未发布 Release，本次未部署正式服。新增原生方块拼出的原创卡牌与麻将图案，放大并竖立 Last Card 手牌，加入悬停让位、摸牌动画，并整理创建与房间菜单。旧房间保留原有规则和位置。详见 [菜单与模式](docs/game-modes.zh-CN.md)、[麻将房规](docs/mahjong.zh-CN.md) 和 [验证记录](docs/verification.md)。Actions 产物也是开发构建；本地模型预览不能代替 Minecraft 客户端画面与操作验收。

## 游戏与功能

支持象棋、国际象棋、五子棋、黑白棋、四子棋、9／13／19 路围棋、中国跳棋、西洋跳棋、英国十字戏、Last Card 和麻将。麻将包括广东、福州、四川、秦皇岛、台湾及日本立直六套明确房规，局内点数不接经济系统。保留房间、座位、准备、陪练、观战、恢复、退出、协商悔棋、再来一局和实体棋盘交互。历史 Yacht 规则仍未启用。

- Last Card 手牌使用更大的竖立牌面；瞄准时该牌抬高，邻牌稍微让位。轮到自己且允许摸牌时，右键中央牌堆，新摸到的牌从牌堆移动到手中；初次显示与恢复不重播动画。
- 卡牌和麻将牌采用原创像素图案，无需资源包。暗手正面只向本人显示，其他人只见牌背和公开牌。
- 象棋棋面保留中文，包括“砲”；Ludo 小人移除浮动编号，骰子移除额外的假影模型。
- 创建页先选模式、人数和麻将地区，详细规则单独打开；等候页、对局页与手牌页各保留当前需要的主要操作。

## 指令与权限

主入口 `/3dtabletop`；常用子指令为 `create`、`join`、`ready`、`bots`、`resume`、`leave`、`undo`、`rematch`、`move`、`rules`。控制台使用 `3dtabletop status`。Tab 根据上下文补全游戏、人数、房间和合法动作，并隐藏重复根指令候选。

`3dtabletop.use` 默认开放，`3dtabletop.admin` 默认 OP；安装 AuthMe 时还须先登录。观战通过菜单进入。完整列表见 [英文 README](README.md#commands-and-permissions)。

## 配置、语言与升级

`config.yml` 保留 `language`、`max-rooms`、`reconnect-seconds`、`idle-room-minutes`、`turn-seconds`。语言默认 `en`，文件位于 `plugins/3dtabletop/lang/`，修改后重启。

语言文件的 `messages` 支持 MiniMessage 和安全占位符；旧 `translations`、`&`／`§` 颜色码及 `menus/*.yml` 继续兼容。已有文件不覆盖，新消息由内置英文补齐，可手动添加同名 `messages` 项覆盖。旧翻译中的自定义内容在加载时映射到消息模板，再插入玩家名字。详见 [语言说明](docs/languages.md)。规则引擎的原始描述、动作报告和已保存的结算原因继续通过兼容层显示，不改写存档。

房间 JSON schema 1、世界 UUID、棋桌坐标、座位、随机种子和动作历史不变。动画是显示效果，不写入存档；重启直接恢复最终棋盘。升级前备份，停服替换 JAR 即可。ServerBoards 旧目录复制迁移见 [中文迁移文档](docs/migration.zh-CN.md)。旧 ServerGames／ServerMenu 的 `/boards` 转发须在其项目另行更新。

原样保留的 1.7.0 默认 `setup.yml`、`room.yml`、`hand.yml` 通过内容指纹匹配后，仅在内存中采用新版布局，磁盘文件字节不改。自定义 YML 继续使用原顺序与样式；希望采用新版布局时，先备份再手动合并内置模板，详见迁移文档。

## 构建与验收

```sh
mvn -B -ntp package
python -m unittest discover -s tests -p "test_*.py"
```

使用 JDK 25、Maven 3.9+。服务端探针需要准备本地 Purpur 26.2 缓存，详见 [验证说明](docs/verification.md)。自动检查不代替客户端画面与操作体验验收，可按 [中文客户端验收单](docs/acceptance.zh-CN.md) 检查。本次先交付本地 JAR，源码可同步 GitHub，用户明确同意后才发布 Release。

[架构](docs/architecture.md) · [功能清单](docs/features.zh-CN.md) · [更新日志](CHANGELOG.md) · [第三方来源](THIRD_PARTY.md)
