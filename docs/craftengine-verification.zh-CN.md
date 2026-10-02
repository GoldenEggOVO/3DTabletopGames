# 1.8.10-SNAPSHOT 本地验证记录

- Maven `package`：538 项 JUnit 测试，失败／错误／跳过均为 0。
- Python：8 项资源包检查通过，覆盖 111 个模型、纹理引用、有效显示模式、圆桌外围面、像素调色板、箭头透明边缘和连续性及 9 个 OGG。
- 调用本机 Minecraft 26.2 原始 `ItemTransforms`／`ItemTransform` 解析器：旧 `none` 变换被忽略，新 `fixed` 读取 Y180 后，立牌正面朝向拥有者，平放后朝上。诊断源文件为 `tools/client-transform-probe/ClientTransformProbe.java`；需提供本机客户端、公共代码和运行库 classpath，不分发 Minecraft 二进制。
- 无 CraftEngine：隔离 Purpur 26.2 创建／重启恢复／旧目录迁移三次启动全部通过。
- 有 CraftEngine：26.8.2 注册并创建、移除 111 个真实 ItemDisplay，与上项使用同一份最终 JAR。
- 语言：独立键、中文与英文占位符、旧文件转换、父键与子键并存、权限、失败保留当前语言和成功刷新菜单均有回归检查。
- 色彩、桌面轮廓与排布预览来自实际生成模型及贴图；不是 Minecraft 游戏截图。

交付内 `verification.json`、`evidence/*runtime.json`、构建日志及 SHA-1／SHA-256 可核对实际产物。实体数量历史测量见 `model-counts.zh-CN.md`；资源包圆桌保持一个 ItemDisplay，几何改为 198 个外围面，未进行客户端 FPS 测量。

未验证：Minecraft 客户端最终画面、音效听感、附魔流光和帧时间。Ctrl 固定镜头及资源包朝向需客户端验收。测试服部署回执另存工作区 `reports/`；没有合并主分支或发布 Release。
