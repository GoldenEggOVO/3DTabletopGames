# 原版卡牌头颅试验

## 模型方案

标准扑克牌的 52 个正面和牌背，每张由 2 列、3 行的薄头颅组成，正面合计 16×24 像素。保留 Casino 的白底、红黑配色和双角标；为保证辨认，数字牌中央改用大数字。大小王沿用原来的短文字模型。

生成器导出合法的 64×64 皮肤图片，并清空头颅外层。相同切片去重后，目前共有 193 张皮肤需要生成纹理。

本地 PNG 需要先经 MineSkin 取得带签名的 Mojang 纹理属性，插件将完整目录内置到 JAR。玩家使用原版客户端即可显示；客户端需要能访问 Mojang 纹理服务，不需要 MineSkin 账号、API Key 或资源包。六块图片可以完整重组，但实际光照、接缝和 FPS 仍需游戏内测试。

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

- `cards/`：低分辨率卡牌正面。
- `skins/`：可上传的皮肤图片，文件名是图片的 SHA-256。
- `manifest.json`：每张牌的六块切片位置和皮肤文件引用。
- `preview.png`：Casino 原图与六头颅候选图对比。

上传时只使用当前 `manifest.json` 引用的唯一图片。重复导出到同一目录时，旧素材可能仍在目录中。

生成器使用官方 V2 队列，遵守响应中的限额与等待时间，进度保存到素材目录的 `generation-cache.json`。中断后使用同一命令续传，不重复已完成的纹理。仅当全部纹理完成时，才写出 `playing-card-heads.json`；缓存与 Key 不随源码或插件交付。

资源包玩家仍使用一张牌一个 ItemDisplay 的模型。原版标准扑克改用头颅，保留现有点击位置、私有手牌、选中抬起与悬停行为。此方案每张由 5 个实体增至 6 个实体，但不再生成大量文字像素；性能需要游戏内对比。资源包 ZIP 无需更新，1.10.5 可以继续使用 1.10.4 的 ZIP。
