# 原版卡牌头颅试验

## 模型方案

54 种正面（含大小王）采用 4 列、6 行共 24 个薄头颅，合计 32×48 像素。数字牌恢复花色排列；J／Q／K 分别采用原版配色的僵尸、骷髅、苦力怕，上下旋转对称；大小王采用凋零和完整 JOKER 字样。保持现有牌宽、高度与交互位置，因此 2:3 的像素图片显示于原有 3:4 牌面轮廓中。

公开牌背仅用两个 BlockDisplay：白色薄牌体与蓝色内嵌面。表面留出微小高度差，避免共面闪烁。自己的公开牌背继续隐藏，私有正面只向本人发送。

1,296 个正面切片去重后，需要 418 张 64×64 皮肤纹理；头颅外层透明，牌背无需皮肤。生成后带签名的 Mojang 纹理属性内置到 JAR。客户端需要能访问 Mojang 纹理服务，不需要 MineSkin 账号、API Key 或资源包。实际光照、接缝和 FPS 仍需游戏内测试。

## 获取 MineSkin API Key

1. 打开 [MineSkin 账号页面](https://account.mineskin.org/)，登录或注册。
2. 进入 [API Keys 页面](https://account.mineskin.org/keys/)，创建 Key。用途可以填写 `Tabletop card textures`；如页面要求应用网址，可填写本项目的 GitHub 地址。
3. 将 Key 单独保存在本地文件，例如 `D:\chatGPT\minecraft\.private\mineskin-api-key.txt`，只保存 Key 本身。
4. 告知助手文件路径即可。Key 只在生成素材时使用；最终插件使用生成的公开纹理数据。

官方 V2 API 要求 Key，并有生成速率限制。后续按返回的等待时间逐个生成，保存已完成结果以便续传。只上传卡牌皮肤图片。无需在服务器中配置 MineSkin Key。

官方说明：[Getting Started](https://docs.mineskin.org/docs/guides/getting-started/)。

## 本地生成与检查

使用带有 Pillow 的 Python：

```powershell
python tools/build-native-card-heads.py --output target/native-card-heads
python -m unittest discover -s tools -p 'test_*native_card_heads.py'
python tools/generate-native-card-heads.py --skins target/native-card-heads --api-key-file D:/chatGPT/minecraft/.private/mineskin-api-key.txt --output src/main/resources/playing-card-heads.json
```

输出包括：

- `cards/`：批准的 32×48 卡牌正面。
- `skins/`：可上传的皮肤图片，文件名是图片的 SHA-256。
- `manifest.json`：每张牌的 24 块切片位置和皮肤文件引用。
- `preview.png`：实际像素牌面放大预览。

上传时只使用当前 `manifest.json` 引用的唯一图片。重复导出到同一目录时，旧素材可能仍在目录中。

生成器使用官方 V2 队列，遵守响应中的限额与等待时间，进度保存到素材目录的 `generation-cache.json`。中断后使用同一命令续传，不重复已完成的纹理。仅当全部纹理完成时，才写出 `playing-card-heads.json`；缓存与 Key 不随源码或插件交付。

资源包扑克仍每张一个 ItemDisplay，原版正面为 24 个头颅、公开牌背为两个方块。保留现有尺寸、点击位置、私有手牌、选中与悬停体验。资源包 ZIP 无需更新，1.10.6 继续使用 1.10.4 的 ZIP。

MineSkin 无限小时额度仍有分钟速率限制。生成器忽略明确为 `limit: 0` 的无限窗口，继续遵守非零额度、`next.relative` 与 HTTP 429 的等待时间。官方说明：[速率限制](https://docs.mineskin.org/docs/guides/rate-limits/)。
