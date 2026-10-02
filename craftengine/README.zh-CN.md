# 桌游物品注册

`resources/tabletop3d/` 放入 `plugins/CraftEngine/resources/`，不要覆盖 CraftEngine 的全局配置。安装 CraftEngine 26.8.2 后重启，注册 111 个自定义物品。

模型、图片和音效位于独立的 `tabletop-resource-pack.zip`；将 ZIP 单独静态托管，并在 `plugins/3dtabletop/config.yml` 填入 URL、SHA-1 与模式。CraftEngine 自己的其他资源包照常使用。完整配置与验收见 [安装说明](../docs/craftengine.zh-CN.md)。

本目录不含 CraftEngine 插件二进制，不自动部署或上传资源包。
