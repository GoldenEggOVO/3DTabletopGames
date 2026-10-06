# 更新当前桌游版本

当前版本保留 `3dtabletop` 配置、`languages/` 语义翻译和 schema 1 房间存档。菜单布局由代码维护，文字和样式仍可在语言文件中修改。CraftEngine 仍是可选集成。旧 ServerBoards 导入、1.8.10 翻译转换、停用飞行棋和旧游戏别名已经移除。

1. 干净停服并备份完整插件目录。
2. 用实际候选 JAR 校验存档：

   ```sh
   java -cp target/3dtabletop-1.10.24-SNAPSHOT.jar dev.tabletop3d.RoomReplayVerifier /backup/3dtabletop/rooms.json
   ```

3. 更换为新的 shaded JAR，保留当前房间、自定义翻译与其他插件。
4. 资源包模式使用同一交付中的 ZIP；配置只需 URL，摘要和身份由插件自动提供。
5. 启动后检查恢复的房间、菜单与交互，保留完整备份用于回滚。

存档动作与实时操作使用同一校验；旧彩八牌堆耗尽后的非法 draw/pass 历史不再接受。停用游戏应先用原版本结束，不会自动转为其他游戏。若需要一次性转换更早数据，可使用旧源码修订中的工具；转换后的当前格式必须先通过验证再安装。
