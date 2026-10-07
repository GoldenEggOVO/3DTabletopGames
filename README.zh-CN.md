# 3DTabletopGames

[English](README.md) | [简体中文](README.zh-CN.md)

带实体棋子、多人房间、私有手牌和原生 Dialog 菜单的 Minecraft 3D 桌游插件。

支持 **Paper／Purpur 26.2、Java 25**。可独立运行，无需客户端模组或其他自定义插件；CraftEngine 与桌游资源包为可选项。当前源码为 **1.10.30-SNAPSHOT 开发版**，未发布 Release。

## 安装

1. 从 [Releases](https://github.com/GoldenEggOVO/3DTabletopGames/releases) 下载已发布 JAR，或[自行编译](#构建)。
2. 完全停服，备份世界与 `plugins/3dtabletop/`。
3. 将一个 `3dtabletop-*.jar` 放进 `plugins/`，不安装 `original-*.jar`。
4. 启动后使用 `/3dtabletop`，选择游戏、调整规则，再创建好友或陪练房间。

所有桌子均使用潜行＋右键桌面打开房间菜单。棋子、卡牌、骰子和计分格直接在桌面操作。麻将可按住疾跑键（默认 Ctrl）进入固定看桌视角，松开恢复。

## 游戏

| 游戏 | 人数 | 桌面内容 |
| --- | --- | --- |
| 象棋、国际象棋 | 2 | 棋盘与立体棋子 |
| 五子棋 | 2 | 黑白棋子 |
| 黑白棋 | 2 | 翻面棋子与动画 |
| 四子棋 | 2 | 竖直棋盘与落子 |
| 围棋 | 2 | 9／13／19 路棋盘 |
| 中国跳棋 | 2、3、4、6 | 星形棋盘与彩色棋子 |
| 西洋跳棋 | 2 | 8×8 棋盘与升王 |
| Ludo | 2～4 | 十字棋盘、棋子与骰子 |
| 彩八 | 2～5 | 私有手牌、摸牌堆与颜色按钮 |
| 斗地主 | 3 | 叫地主、选牌与公开地主牌 |
| 骗子酒馆 | 2～4 | 暗出牌、质疑与局内轮盘 |
| 快艇骰子 | 2～4 | 五颗骰子、最多三掷、保留槽与十二项计分 |
| 德州扑克 | 2～6 | 公共牌、下注、庄家标记与筹码 |
| 麻将 | 4 | 私有手牌、牌河与副露 |

支持准备、陪练、观战、再来一局和重启恢复。房间创建后规则锁定，需要更换规则时创建新房间。麻将提供广东、四川、台湾和日本立直四套房规。麻将分数和德州筹码仅为局内点数，不接经济系统。具体规则可在菜单或 `/3dtabletop rules` 中查看。

原版扑克和彩八正面为 32×48 像素，每张使用 24 个薄头颅；公开暗牌背使用两个方块。签名纹理已内置，运行时无需 MineSkin API Key。资源包卡牌每张使用一个显示实体。暗手正面仅本人可见。

## 指令与权限

| 指令 | 用途 |
| --- | --- |
| `/3dtabletop [menu]` | 打开主菜单 |
| `/3dtabletop create <kind> [players]` | 创建房间 |
| `/3dtabletop join <room-prefix>` | 加入开放房间 |
| `/3dtabletop ready`／`bots` | 准备／房主补充陪练 |
| `/3dtabletop resume`／`leave` | 返回桌面／离开房间 |
| `/3dtabletop rematch` | 准备再来一局 |
| `/3dtabletop move <action>` | 执行合法动作，例如 `drop:3` |
| `/3dtabletop rules [kind]` | 查看规则 |
| `3dtabletop status` | 控制台查看房间数量 |

游戏标识：`xiangqi`、`gomoku`、`chess`、`ludo`、`checkers`、`draughts`、`reversi`、`go`、`go9`、`go13`、`connectfour`、`color-eight`、`mahjong`、`yacht`、`doudizhu`、`liars-bar`、`texas-holdem`。其中 `checkers` 是中国跳棋，`go` 是 19 路围棋。

例如先执行 `/3dtabletop create connectfour`，再执行 `/3dtabletop bots`。指令创建使用默认规则，自定义规则请使用菜单。Tab 会提示游戏、人数、房间和合法动作。`3dtabletop.use` 默认所有玩家可用，`3dtabletop.admin` 默认 OP，用于管理与语言重载。

## 配置与语言

配置文件位于 `plugins/3dtabletop/config.yml`：

| 配置项 | 默认值 | 用途 |
| --- | --- | --- |
| `language` | `en_US` | `languages/` 中的语言文件名 |
| `max-rooms` | `12` | 房间数量上限 |
| `reconnect-seconds` | `120` | 离线重连宽限秒数 |
| `idle-room-minutes` | `30` | 空闲房间超时分钟数 |
| `turn-seconds` | `60` | 默认回合时限，部分游戏有独立时限 |
| `sounds.enabled` | `true` | 游戏与界面音效开关 |
| `sounds.volume` | `1.0` | 0～1 的音量倍率 |
| `rendering.mode` | `vanilla` | 原版、资源包或混合显示 |
| `rendering.resource-pack.url` | 空 | 与 JAR 匹配的资源包直链 |

中文设置为 `language: zh_CN`。可编辑首次启动生成的 `languages/en_US.yml`、`languages/zh_CN.yml`，或复制一份作为其他语言。保留消息键名与 `{player}` 等参数名；支持 MiniMessage 样式及旧颜色码，语言文件不能定义回调或指令。

修改翻译或语言选项后执行 `/3dtabletop reload-language`，其他配置修改后重启。缺失翻译回退到英文，重载校验失败时保留原语言。菜单布局由代码维护，标题和说明来自翻译文件；旧 `menus/` 目录备份后可删除。

`rooms.json` 保存房间、桌子位置和动作历史；`table-map-ids.yml` 保存插件使用的地图编号。升级时保留这些文件，不在开服期间手工修改生成的数据。

## 可选资源包

1. 使用资源模型时安装 CraftEngine，将匹配的 `craftengine-registration.zip` 解压到服务器根目录。注册文件最终位于 `plugins/CraftEngine/resources/tabletop3d/`。
2. 将同一交付中的 `tabletop-resource-pack.zip` 上传到可直接下载的 HTTP／HTTPS 地址，保持 ZIP 内容不变。
3. 修改 `plugins/3dtabletop/config.yml`，然后重启：

```yaml
rendering:
  mode: mixed
  resource-pack:
    url: 'https://YOUR-HOST/tabletop-resource-pack.zip'
```

`vanilla` 无需 CraftEngine 或资源包。`mixed` 允许玩家单独开关，资源包不可用时显示原版。`resource-pack` 要求资源包加载成功、模型注册就绪后才能进行游戏。校验值和请求标识由插件管理，只需配置 URL，并使用与 JAR 匹配的 ZIP。不会移除或替换其他插件的资源包。

## 升级

停服并完整备份插件数据后替换 JAR，保留现有配置、自定义翻译、房间和其他插件。当前 schema 1 房间文件继续支持，已退役玩法的旧格式不自动转换。升级前可用候选 JAR 验证备份：

```sh
java -cp target/3dtabletop-1.10.30-SNAPSHOT.jar dev.tabletop3d.RoomReplayVerifier /backup/3dtabletop/rooms.json
```

启用资源包时同时更新匹配的 ZIP 和 URL。启动后检查房间恢复、菜单与桌面操作，再决定是否清理备份。

## 源码目录

- `src/main/java/dev/tabletop3d/`：规则按游戏分类，共用房间、菜单、渲染和存档代码按职责分类。
- `src/main/resources/`：插件信息、默认配置、翻译、内置声明与纹理。
- `src/test/`：Java 自动检查。
- `resource-pack/textures/`：按游戏分类的图片，`native-playing-cards/` 为原版头颅卡牌素材。
- `resource-pack/sounds/`：按用途分类的声音与 `events.json` 映射。
- `resource-pack/craftengine/`：自定义物品注册。
- `resource-pack/sources.json`：素材来源、摘要与许可说明。
- `tools/resource_pack/`：资源包生成、模型与图片辅助、预览及象棋 SVG 转换。
- `tools/native_heads/`：原版卡牌皮肤切片与可选 MineSkin 纹理生成。
- `tools/packaging/`：源码、已验证原版包及 CraftEngine 交付打包。
- `tools/preview/`：原版麻将模型离线预览。
- `tools/verification/`：资源包几何统计、隔离服务器与客户端解析检查。
- `tools/tests/`：离线 Python 回归检查。
- `target/`：生成的 JAR、资源包、预览和测试结果，不属于源码。
- `docs/THIRD_PARTY.md`：第三方署名和来源说明。

## 构建

使用 **JDK 25、Maven 3.9+**：

```sh
mvn -B -ntp package
```

Maven 自动运行 Java 测试，结果保存在 `target/surefire-reports/`。安装生成的 `target/3dtabletop-*.jar`。

重新生成资源包还需要 **Python 3.12+**、Pillow 和 SoundFile／libsndfile。先编译 Java 导出模型，再生成资源包，最后重新编译插件以写入匹配的校验值：

```sh
python tools/resource_pack/build-resource-pack.py
python -m unittest discover -s tools/tests -t . -p "test_*.py"
mvn -B -ntp package
python -m compileall -q tools
python tools/packaging/package_source.py
```

输出包括 `target/tabletop-resource-pack.zip`、`target/resource-pack-manifest.json`、预览及 `target/3dtabletop-source.zip`。源码包仅包含 Git 跟踪的文件，运行世界、凭据、缓存和服务端程序不应放进仓库。

`tools/verification/standalone-probe/run_standalone.py` 检查隔离启动、玩法与重启恢复。通过 `--server-dir` 指定已准备好 Purpur 26.2 缓存及已接受 EULA 的目录，`--maven-repo` 指定自定义依赖缓存，默认使用 `~/.m2/repository`；可选 CraftEngine 参数用于资源模型检查。`run_soak.py` 检查连续对局与实体清理。回执与日志保存在本地生成目录。

原版扑克切片：`python tools/native_heads/build-native-card-heads.py --output target/native-heads`；彩八切片：`python tools/native_heads/build-color-eight-heads.py --output target/color-eight-heads`。只有手动运行 `generate-native-card-heads.py` 并指定本地 `--api-key-file` 时才会访问 MineSkin，普通构建和运行不需要密钥。旧 Casino 方块卡牌导入器已移除，现有图片与回归参考数据继续保留。

自动检查和预览不能替代客户端验收。应在 Minecraft 中检查多人暗手隐私、资源包切换与下载失败、准星和点击范围、骰子移动、音量、重启恢复及帧时间表现。

## 许可

源码采用 [GPL-3.0-or-later](LICENSE)。第三方素材的独立声明见[第三方说明](docs/THIRD_PARTY.md)和[素材来源](resource-pack/sources.json)。
