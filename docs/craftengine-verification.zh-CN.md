# 1.9.0-SNAPSHOT 本地验证记录

- Maven `package`：540 项 JUnit 测试，失败／错误／跳过均为 0。
- Python：9 项资源包检查和 3 项离线升级回归通过，覆盖 111 个模型、有色麻将牌背、纹理引用、有效显示模式、圆桌外围面、像素调色板、箭头透明边缘和连续性及 9 个 OGG。
- 调用本机 Minecraft 26.2 原始 `ItemTransforms`／`ItemTransform` 解析器：旧 `none` 变换被忽略，新 `fixed` 读取 Y180 后，立牌正面朝向拥有者，平放后朝上。诊断源文件为 `tools/client-transform-probe/ClientTransformProbe.java`；需提供本机客户端、公共代码和运行库 classpath，不分发 Minecraft 二进制。
- 无 CraftEngine：隔离 Purpur 26.2 创建／重启恢复／当前数据离线转换后三次启动全部通过。
- 有 CraftEngine：26.8.2 注册并创建、移除 111 个真实 ItemDisplay，与上项使用同一份最终 JAR。
- 语言：完整语义键、中英文占位符一致、当前记录转换、自定义名字字面保留、失败保留当前语言和成功刷新菜单均有回归检查。升级工具保留无法映射的自定义短语及原文件，插件不再进行旧短语拼接。
- 独立审阅发现的非日麻暗杠重复展示问题已用广东／四川／台湾真实状态回归修复；暗杠保留四张牌背，花牌单独读取。
- 色彩、桌面轮廓与排布预览来自实际生成模型及贴图；不是 Minecraft 游戏截图。

交付内 `verification.json`、`evidence/*runtime.json`、构建日志及 SHA-1／SHA-256 可核对实际产物。实体数量历史测量见 `model-counts.zh-CN.md`；资源包圆桌保持一个 ItemDisplay，几何改为 198 个外围面，未进行客户端 FPS 测量。

未验证：Minecraft 客户端最终画面、音效听感、附魔流光和帧时间。Ctrl 固定镜头及资源包朝向需客户端验收。测试服部署回执另存工作区 `reports/`；没有合并主分支或发布 Release。
