# 桌游成品整理实施计划

> 执行方式：superpowers:executing-plans，在当前隔离工作树逐项实施。用户已允许全部进行，不重复要求阶段批准。

**目标：** 以 1.8.10 为基准加入立直自动摸切和 Color Eight 回合条，修正麻将牌背与横牌，整理翻译、命名、房间职责和旧兼容。

**架构：** 保持纯规则与 Bukkit 展示分离。自动决策从 tick 分离，存档从主插件分离；翻译使用语义键和完整模板，历史格式转换由独立工具负责。

**技术：** Java 25、Paper/Purpur 26.2、CraftEngine 26.8.2、JUnit、Pillow。

**设计：** ../specs/2026-10-02-tabletop-current-baseline-cleanup-design.md

## 全局约束

- 仅桌游插件；不修改 Casino。现有玩法、菜单、权限、私有手牌、模型朝向、热重载和三种渲染模式保持。
- 移除 AuthMe、Geyser/Floodgate；保留 CraftEngine 可选支持。
- 当前存档和自定义文件先备份，再转换并验证；不发布 Release。

## 审阅重点

- 立直玩家遇到和牌机会不能被普通超时逻辑替代操作。
- 已接受的立直宣言、被鸣牌的弃牌与加杠必须保持正确横牌位置。
- 语言整理不能修改玩家名字或丢失自定义值。
- 当前 Color Eight 房间中旧耗尽牌堆历史必须仍能恢复相同状态。
- 资源包模式不能暴露别人的手牌或产生额外重复实体。

## 任务 1：自动回合与 Color Eight 指示

文件：新增 TurnPolicy.java、CardTurnIndicator.java，修改 Tabletop3D.java、HandTable.java、MahjongControls.java、MahjongGame.java，测试 TurnPolicyTest、RiichiGameTest、HandTableTest。

接口：TurnPolicy.choose(Room,long,long) 返回待提交 action 或 null；MahjongGame.riichiAutoDiscard(int) 返回唯一摸切动作；CardTurnIndicator.sync() 按当前座位更新。

- [ ] 写测试：已立直普通回合只摸切；ron/tsumo 不自动操作；未立直和其他麻将不受影响；游戏结束／暂停不显示回合条。
- [ ] 执行测试确认 RED，实现并确认 GREEN。
- [ ] 接入 tick 的正常动作路径；自摸“跳过”直接提交合法摸切动作。

## 任务 2：麻将牌背与横牌

文件：MahjongGame.java、MahjongPresentation.java、HandTable.java、tools/build-resource-pack.py，测试麻将规则布局及 Python 模型检查。

接口：规则提供仅公开的弃牌／副露横牌标识和叠放元数据；展示使用这些元数据，不推测隐藏手牌。

- [ ] 先测试资源模型 north 有色牌背、south 牌面以及 mahjong_back 两面一致；先验证失败，再修正。
- [ ] 验证立直宣言横牌、鸣牌来源方向、暗杠两侧牌背、加杠叠放与牌组间距。
- [ ] 两种渲染使用同一组公开布局；回归 FIXED 朝向。

## 任务 3：移除接口与旧适配、规范命名

文件：Tabletop3D.java、plugin.yml、GameMenus.java、GameMenuLayouts.java、GameFactory.java、GameOptions.java、ColorEightGame.java；删除 DataMigration.java，替换针对取消格式的测试。

- [ ] 测试当前权限不依赖 AuthMe，源／元数据无 Geyser/Floodgate；实现并验证。
- [ ] 将 LastCardGame 改为 ColorEightGame，并在数据边界统一当前游戏 ID；转换工具保留当前历史含义。
- [ ] 移除菜单摘要及历史标签替换、旧 Last Card 罚抽选项和正常启动的旧目录搬运。

## 任务 4：完整语义翻译

文件：Language.java、规则消息类型／各游戏描述、RoomText.java、HandText.java、GameWorld.java、TableView.java、菜单与主插件展示调用、languages/*.yml、独立升级工具。

- [ ] 写回归：无哈希键、无短语正则翻译、名字字面保留、完整模板、中英键及占位符一致、失败热重载保留当前目录。
- [ ] 规则状态与原因采用稳定语义 ID／参数，展示层使用 Language.component；不在规则中读取 Bukkit 语言状态。
- [ ] 先生成旧键到新键的独立转换清单；自定义片段留在报告，输出明确完整模板。
- [ ] 清除 language-compatibility.yml 和运行时旧格式适配；保留英语回退与 MiniMessage 校验。

## 任务 5：房间职责和代码可读性

文件：新增 RoomStore.java、当前记录读取／写入测试；整理 Tabletop3D、GameMenus、HandTable 中密集路径。

- [ ] 以当前记录做完整 round-trip 和确定性回放，再提取存储职责。
- [ ] 使用明确变量、具名常量、短方法；不重写上游棋类算法。
- [ ] 去掉重复计算但不降低点击时 revision 校验、存档恢复或手牌隐私保证。

## 任务 6：交付、审阅与部署

- [ ] Java 与 Python 全部行为检查，原版及 CraftEngine 当前数据三次启动／恢复，客户端解析器朝向验证。
- [ ] 更新版本 1.9.0-SNAPSHOT 和文档，产出本地独立交付包；提交明确范围。
- [ ] 根据 executing-plans 技能，执行一次独立整体代码审阅并修复有实际影响的问题。
- [ ] 备份并停止独立测试服，转换语言／当前数据、安装 JAR／资源包、启动并核对保护数据和 HTTPS 摘要。客户端效果与服务器验证分别报告。
