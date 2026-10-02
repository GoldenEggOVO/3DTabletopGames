# CraftEngine 与独立桌游资源包

适用于本地验收版 **1.8.8-SNAPSHOT**：Paper／Purpur 26.2、Java 25。本次接入麻将和彩八，其他游戏保留原版显示。CraftEngine 26.8.2 的物品注册与 ItemDisplay 已在隔离服务器验证；客户端画面、音效和加载仍须按文末清单验收。

## 安装

1. 备份 `plugins/3dtabletop/`，停服后替换 `3dtabletop-1.8.8-SNAPSHOT.jar`，只保留一个版本。
2. 若选择资源包或混合模式，安装 CraftEngine。将交付的 `craftengine-registration.zip` 解压到服务器根目录，最终路径为 `plugins/CraftEngine/resources/tabletop3d/configuration/items.yml` 和同目录包的 `pack.yml`。已有同名目录先备份。该配置注册 111 个 `tabletop3d:*` 自定义物品，物品使用独立 ZIP 中的 `item_model`。
3. 将 `tabletop-resource-pack.zip` 放到你已有的静态文件托管服务，取得客户端可直接下载的 HTTP／HTTPS 地址。不要使用需要登录的网页或分享页。保留 ZIP 文件内容不变。
4. 修改 **`plugins/3dtabletop/config.yml`**，参考下例，填入实际 URL。SHA-1 使用交付的 `resource-pack-manifest.json` 中的 `sha1`。改变 ZIP 后重新计算 SHA-1。
5. 重启服务器。先以默认 `vanilla` 验证旧玩法，再切换 `mixed` 进行客户端验收。

CraftEngine 负责注册模型物品，3dtabletop 负责发送这个独立 ZIP、接收加载结果及切换显示。此包不需要合并进 CraftEngine 的全局包，也不需要修改其他资源包的发送配置。Minecraft 26.2 的包格式为 88.0；纹理位于独立的 `tabletop3d` 命名空间，不覆盖原版 `paper` 或其他插件的物品模型。

## 三种模式

```yaml
rendering:
  mode: mixed # vanilla / resource-pack / mixed；修改后重启
  resource-pack:
    url: 'https://YOUR-HOST/tabletop-resource-pack.zip'
    sha1: '从 resource-pack-manifest.json 复制 40 位 sha1'
    uuid: 40ae45a0-4d81-4c07-8e68-807e85168a09
```

| 模式 | 菜单开关 | 麻将／彩八 | CraftEngine |
| --- | --- | --- | --- |
| `vanilla`（默认） | 无 | 原版实体与原版音效 | 不需要 |
| `resource-pack` | 无 | 本包成功加载并且物品注册就绪后才能进入／行动 | 必须 |
| `mixed` | 所有游戏菜单最上方 | 玩家自主选择；下载中、拒绝或失败时仍用原版 | 开启包显示时必须 |

偏好保存到玩家 PDC；重新登录仍会等待本次加载成功，不会把以前的成功当成本次已加载。关闭只移除桌游自己的资源包；其他插件发送的包保持原状。每次发送使用独立请求 UUID，以忽略关闭／重试前的旧回调。配置中的 UUID 是这个包的命名空间标识。

附近 24 格内包括旁观者。有人使用原版，就保留原版公开模型；所有附近观看者都使用资源包时，会删除原版公开层。每个玩家只接收自己的显示层，混合模式的服务器可能同时持有两套公开模型。私人手牌、私人宝牌指示仍只显示给本人。原版模式保留原有观看范围；24 格需求范围仅用于资源包／混合模式。

## 图片、桌布与声音

麻将使用用户指定目录中的默认牌面、默认桌布，以及选牌、摸牌、打牌、副露、杠、立直、翻宝牌、和牌和倒计时共 9 个短音效。原版观看者继续听原版声音，包观看者听对应自定义音效，每人只收到一种。

```yaml
sounds:
  enabled: true
  volume: 1.0 # 0.0～1.0，作用于两种声音
```

彩八为本项目绘制的高清卡面；花牌为本项目绘制。来源和原始文件 SHA-256 见 `resource-pack/sources.json`。第三方素材的来源记录不构成再分发许可；本地验收包尚未作为公开 Release 发布。

中央面板约 1.10 格宽：四方分数移入面板，局数与余牌文字各自朝向观看者，余 0 红色，立直棒位于方位之间，青色边缘条提示当前行动方；暂停／结束时隐藏。原版继续使用原版桌面，只有包观看者看到默认桌布。见 [实体数量](model-counts.zh-CN.md)。

## 构建

Java 插件：JDK 25 下运行 `mvn package`。资源生成使用 Python、Pillow、SoundFile／libsndfile：

```sh
python tools/build-resource-pack.py
python tools/test_resource_pack.py
```

生成 `target/tabletop-resource-pack.zip`、`target/resource-pack-manifest.json` 与 `target/pack-preview/`。脚本真实解码 MP3 再编码为单声道 OGG，并检查解码长度；不会把 MP3 直接改扩展名。Windows 使用系统字体，Linux 使用 DejaVu；中文花牌字形还需可用中文字体。

## 客户端待验清单

- 两位玩家分别开／关资源包：本人暗手不泄漏，牌河与选牌高亮一致；第三方资源包保持加载。
- 包打开后立刻关闭、拒绝下载、失败重试、退出重进；原版回退和顶部按钮正确。
- 四个座位的局数／余牌朝向、中央分数、余 0 红字、立直棒、私人宝牌位置与附魔流光。
- 麻将牌面、花牌、桌布、彩八圆角、四色无数字按钮、彩八出牌后的颜色、灰牌亮度、小幅抬牌。
- 按住疾跑键（默认 Ctrl）的固定镜头与松开恢复、连续点击不误打刚摸的牌、副露位置及牌河每行六张。
- 自定义音效每次事件只响一次，声音开关／音量可控；对比大量手牌时的帧率和实体量。

自动测试和服务器探针不能替代上述客户端验收。交付不修改生产服务器、不发布 Release。

接口参考：[CraftEngine 物品模型](https://xiao-momi.github.io/craft-engine-wiki/configuration/item/models/)、[Minecraft Java 26.2](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2)。
