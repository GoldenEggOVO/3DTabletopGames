# 3DTabletopGames

[English](README.md) | [简体中文](README.zh-CN.md)

**带实体棋子、多人房间和原生 Dialog 菜单的 Minecraft 3D 桌游插件。**

支持 **Paper／Purpur 26.2、Java 25**。无需资源包或客户端模组，可独立安装；AuthMe 为可选接入。

## 安装

1. 从 [Releases](https://github.com/GoldenEggOVO/3DTabletopGames/releases) 下载已发布版本。
2. 完全停服并备份插件数据与世界。将 JAR 放入 `plugins/`，只留一个版本，不安装 `original-*.jar`。
3. 启动后使用 `/3dtabletop` 创建房间、邀请玩家或添加陪练，在实体桌面操作。

当前源码为 **1.5.1-SNAPSHOT 本地验收版**，尚未发布 Release。本批加入四子棋私有落点预览、房间分页和规则帮助入口、菜单直接落子，优化围棋死子标记和棋子模型，并防止旧菜单操作已关闭或后来加入的房间。继续修复离桌后碰撞与饥饿保护残留、新桌点击延迟和四子棋上半部菜单点击，阻止新建棋桌重叠；无效落子提示改用动作栏。Actions 产物也是开发构建。

## 游戏与功能

支持象棋、国际象棋、五子棋、黑白棋、四子棋、9／13／19 路围棋、中国跳棋、西洋跳棋、飞行棋。保留房间、座位、准备、陪练、观战、恢复、退出、协商悔棋、再来一局和实体棋盘交互。历史 Yacht 规则未启用，不加入卡牌或麻将。

## 指令与权限

主入口 `/3dtabletop`；常用子指令为 `create`、`join`、`ready`、`bots`、`resume`、`leave`、`undo`、`rematch`、`move`、`rules`。控制台使用 `3dtabletop status`。Tab 根据上下文补全游戏、人数、房间和合法动作，并隐藏重复根指令候选。

`3dtabletop.use` 默认开放，`3dtabletop.admin` 默认 OP；安装 AuthMe 时还须先登录。观战通过菜单进入。完整列表见 [英文 README](README.md#commands-and-permissions)。

## 配置、语言与升级

`config.yml` 保留 `language`、`max-rooms`、`reconnect-seconds`、`idle-room-minutes`、`turn-seconds`。语言默认 `en`，文件位于 `plugins/3dtabletop/lang/`，修改后重启。

语言文件的 `messages` 支持 MiniMessage 和安全占位符；旧 `translations`、`&`／`§` 颜色码及 `menus/*.yml` 继续兼容。已有文件不覆盖，新消息由内置英文补齐，可手动添加同名 `messages` 项覆盖。旧翻译中的自定义内容在加载时映射到消息模板，再插入玩家名字。详见 [语言说明](docs/languages.md)。规则引擎的原始描述、动作报告和已保存的结算原因继续通过兼容层显示，不改写存档。

房间 JSON schema 1、世界 UUID、棋桌坐标、座位、随机种子和动作历史不变。动画是显示效果，不写入存档；重启直接恢复最终棋盘。升级前备份，停服替换 JAR 即可。ServerBoards 旧目录复制迁移见 [中文迁移文档](docs/migration.zh-CN.md)。旧 ServerGames／ServerMenu 的 `/boards` 转发须在其项目另行更新。

## 构建与验收

```sh
mvn -B -ntp package
python -m unittest discover -s tests -p "test_*.py"
```

使用 JDK 25、Maven 3.9+。服务端探针需要准备本地 Purpur 26.2 缓存，详见 [验证说明](docs/verification.md)。自动检查不代替客户端画面与操作体验验收，可按 [中文客户端验收单](docs/acceptance.zh-CN.md) 检查。本次先交付本地 JAR，源码可同步 GitHub，用户明确同意后才发布 Release。

[架构](docs/architecture.md) · [功能清单](docs/features.zh-CN.md) · [更新日志](CHANGELOG.md) · [第三方来源](THIRD_PARTY.md)
