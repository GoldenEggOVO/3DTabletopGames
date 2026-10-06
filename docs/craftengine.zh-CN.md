# CraftEngine 与独立桌游资源包

适用于本地验收版 **1.10.28-SNAPSHOT**：Paper／Purpur 26.2、Java 25。麻将、彩八、新卡牌游戏、快艇骰子和其他棋盘均提供资源包显示。CraftEngine 26.8.2 的物品注册与 ItemDisplay 已在隔离服务器验证；客户端画面、音效和加载仍须按文末清单验收。

## 安装

1. 备份 `plugins/3dtabletop/`，停服后替换 `3dtabletop-1.10.28-SNAPSHOT.jar`，只保留一个版本。
2. 若选择资源包或混合模式，安装 CraftEngine。将交付的 `craftengine-registration.zip` 解压到服务器根目录，最终路径为 `plugins/CraftEngine/resources/tabletop3d/configuration/items.yml` 和同目录包的 `pack.yml`。已有同名目录先备份。该配置注册 234 个 `tabletop3d:*` 自定义物品，物品使用独立 ZIP 中的 `item_model`。
3. 将 `tabletop-resource-pack.zip` 放到你已有的静态文件托管服务，取得客户端可直接下载的 HTTP／HTTPS 地址。不要使用需要登录的网页或分享页。保留 ZIP 文件内容不变。
4. 修改 **`plugins/3dtabletop/config.yml`**，参考下例，只填实际 URL。校验值内置在插件中，请上传同一交付包中的 ZIP；每次请求的 UUID 自动生成。旧配置的 `sha1`、`uuid` 可以删除，插件不再读取。
5. 重启服务器。先以默认 `vanilla` 验证旧玩法，再切换 `mixed` 进行客户端验收。

CraftEngine 负责注册模型物品，3dtabletop 负责发送这个独立 ZIP、接收加载结果及切换显示。此包不需要合并进 CraftEngine 的全局包，也不需要修改其他资源包的发送配置。Minecraft 26.2 的包格式为 88.0；纹理位于独立的 `tabletop3d` 命名空间，不覆盖原版 `paper` 或其他插件的物品模型。

## 三种模式

```yaml
rendering:
  mode: mixed # vanilla / resource-pack / mixed；修改后重启
  resource-pack:
    url: 'https://YOUR-HOST/tabletop-resource-pack.zip'
```

| 模式 | 菜单开关 | 麻将／彩八 | CraftEngine |
| --- | --- | --- | --- |
| `vanilla`（默认） | 无 | 原版实体与原版音效 | 不需要 |
| `resource-pack` | 无 | 本包成功加载并且物品注册就绪后才能进入／行动 | 必须 |
| `mixed` | 主菜单第一页最上方 | 玩家自主选择；下载中、拒绝或失败时仍用原版 | 开启包显示时必须 |

偏好保存到玩家 PDC；重新登录仍会等待本次加载成功，不会把以前的成功当成本次已加载。关闭只移除桌游自己的资源包；其他插件发送的包保持原状。每次发送使用独立请求 UUID，以忽略关闭／重试前的旧回调。开关启用为绿色、加载为青色、停用为金色。

制作资源包时，`tools/build-resource-pack.py` 自动写入 `src/main/resources/resource-pack.sha1`；随后再编译插件。交付打包会核对 JAR 内的校验值与 ZIP，避免两者版本不匹配。

附近 24 格内包括旁观者。有人使用原版，就保留原版公开模型；所有附近观看者都使用资源包时，会删除原版公开层。每个玩家只接收自己的显示层，混合模式的服务器可能同时持有两套公开模型。私人手牌、私人宝牌指示仍只显示给本人。原版模式保留原有观看范围；24 格需求范围仅用于资源包／混合模式。

## 图片、桌布与声音

麻将牌面使用指定的透明图案，桌布和选牌、摸牌、打牌、副露、杠、立直、翻宝牌、和牌及倒计时共 9 个短音效保持原样。原版观看者继续听原版声音，包观看者听对应自定义音效，每人只收到对应显示模式的音效。

斗地主语音与旧牌型音效、骗子酒馆旧质疑与实弹录音均已删除。当前资源包包含 27 个声音事件：麻将共用的九项录音、用户提供的统一出牌录音，以及 Kenney CC0 的落子、翻面、骰子、筹码和菜单音效。没有额外的‘我的回合’提示音。原版观看者使用对应原版声音；骗子酒馆轮盘仍按实际结果区分空枪与实弹。

快艇骰子使用相邻的计分桌与骰子桌；各玩家得分逐列显示，黄色栏表示当前玩家。小计、63分阈值、35分奖励和总分按现有规则实时更新。骰子、保留槽和点击区域共用世界坐标；点数与规则骰面一致。斗地主桌布的三张地主牌槽居中，三席装饰重新绘制。

```yaml
sounds:
  enabled: true
  volume: 1.0 # 0.0～1.0，作用于两种声音
```

麻将牌面和花牌采用 lietxia/mahjong_graphic 的透明素材，保留 PNG 与 SVG 原图及其允许修改、商用的许可说明。资源包扑克采用新像素布局；象棋棋面采用用户指定的维基共享资源矢量图，使用每张的第一枚红子和第三枚黑子；原 SVG 与离线渲染 PNG 均保留。彩八仍为本项目绘制的卡面。来源和原始文件 SHA-256 见 `resource-pack/sources.json`；剩余第三方桌布与音效的来源记录不构成再分发许可，本地验收包尚未作为公开 Release 发布。

中央面板约 1.10 格宽：四方分数移入面板，局数与余牌文字各自朝向观看者，余 0 红色，立直棒位于方位之间，青色边缘条提示当前行动方；暂停／结束时隐藏。原版继续使用原版桌面，只有包观看者看到默认桌布。见 [实体数量](native-table-counts.zh-CN.md)。

## 构建

Java 插件：JDK 25 下运行 `mvn package`。资源生成使用 Python、Pillow、SoundFile／libsndfile：

```sh
python tools/build-resource-pack.py
python tools/test_resource_pack.py
```

生成 `target/tabletop-resource-pack.zip`、`target/resource-pack-manifest.json` 与 `target/pack-preview/`。脚本真实解码 MP3 再编码为单声道 OGG，并检查解码长度；不会把 MP3 直接改扩展名。Windows 使用系统字体，Linux 使用 DejaVu。麻将与象棋字形直接来自素材，不再依赖系统中文字体。

麻将原始 PNG 与 SVG 位于 `resource-pack/textures/mahjong/tiles/`；象棋原图位于 `resource-pack/textures/xiangqi/`。`tools/face_art.py` 负责这些资源包牌面和扑克像素图案。象棋 PNG 已离线渲染并保存在源码中，正常构建无须 Node.js；修改 SVG 后可使用安装了 `@napi-rs/canvas` 的 Node.js 执行 `node tools/rasterize-xiangqi.js` 更新 PNG。原版头颅纹理不使用这些新牌面。

## 客户端待验清单

- 两位玩家分别开／关资源包：本人暗手不泄漏，牌河与选牌高亮一致；第三方资源包保持加载。
- 包打开后立刻关闭、拒绝下载、失败重试、退出重进；原版回退和顶部按钮正确。
- 四个座位的局数／余牌朝向、中央分数、余 0 红字、立直棒、私人宝牌位置与附魔流光。
- 麻将牌面、花牌、桌布、彩八圆角、四色无数字按钮、彩八出牌后的颜色、灰牌亮度、小幅抬牌。
- 按住疾跑键（默认 Ctrl）的固定镜头与松开恢复、连续点击不误打刚摸的牌、副露位置及牌河每行六张。
- 自定义音效每次事件只响一次，声音开关／音量可控；对比大量手牌时的帧率和实体量。

自动测试和服务器探针不能替代上述客户端验收。交付不修改生产服务器、不发布 Release。

接口参考：[CraftEngine 物品模型](https://xiao-momi.github.io/craft-engine-wiki/configuration/item/models/)、[Minecraft Java 26.2](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2)。
