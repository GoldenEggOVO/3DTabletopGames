# 从当前 1.8.10 测试版升级

1.9 只读取当前 `3dtabletop` 数据目录。启动时不再搬运 ServerBoards、转换旧语言格式或替换旧菜单；AuthMe、Geyser/Floodgate 接口已移除，CraftEngine 仍可选。

## 停服后准备

先干净停服并备份完整插件数据。解压源码包，使用 Python 3.11 及以上版本并安装 PyYAML：

```sh
python tools/upgrade_current_data.py /backup/3dtabletop /staging/3dtabletop
```

工具生成独立的新目录，不修改输入。当前彩八 `rulesVersion: 2` 的类型由 `lastcard` 改为 `color-eight`，牌 ID、随机种子、座位、位置、修订号和动作顺序保留。仅移除已失效的结束选项、转换显示用结果标识。已接受的牌堆耗尽 draw/pass 历史仍按当时的结果回放。

语言统一放在 `languages/`，使用完整模板和明确键名。能够对应的自定义翻译和菜单文字保留；无法对应的自定义短语保存在 `upgrade-report.json` 以及复制的原文件中，请据此补到完整模板。未知规则版本会在生成输出前停止；这种旧对局需用原插件完成。

用将要安装的实际 JAR 校验转换后的存档：

```sh
java -cp target/3dtabletop-1.9.0-SNAPSHOT.jar dev.tabletop3d.RoomReplayVerifier /staging/3dtabletop/rooms.json
```

通过回放和语言校验后再安装新数据目录与 shaded JAR，更新对应的独立资源包及 URL／SHA-1，启动服务器。保留原备份以便回滚。其他插件与资源包独立保留。
