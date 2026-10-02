# CraftEngine 桌游显示实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现已审阅的三种模式、双显示、麻将中央面板、原版精简和独立资源包。

**Architecture:** 同一逻辑牌位驱动两个显示后端；公开实体按附近观看者需求创建，私人实体按个人已加载状态创建。CraftEngine 提供自定义物品，Paper 按独立 UUID 管理资源包加载；规则引擎不变。

**Tech Stack:** Java 25、Paper 26.2、CraftEngine 26.8.2 可选集成、JUnit/Mockito/MockBukkit、Python/Pillow 资源生成、OGG 音效。

**Spec:** `docs/superpowers/specs/2026-10-02-craftengine-tabletop-design.md`

## 全局约束

- `vanilla` / `resource-pack` / `mixed`，默认 `vanilla`；纯模式无开关。
- 本批适配麻将和 Color Eight；独立 `tabletop3d` 命名空间，其他包不移除。
- 公共模型先不可见再开放；私人牌面仅属于自己的玩家可见。
- 固定 Shift 镜头、游戏规则、排序、点击、房间和计分保持现有语义。
- 交付版本 `1.8.8-SNAPSHOT`，本地验收，不发布 Release。

## 重点回归

- 开启后立刻关闭，延迟成功回调不能重新开启模型。
- 第三方包状态事件不能改变桌游显示或移除第三方包。
- 无原版玩家但有原版旁观者，原版实体不能被移除。
- 显示方式变化而房间 revision 未变，仍须重建对应私人显示。
- CraftEngine 未安装/未完成注册，纯原版继续运行，纯包禁止游戏操作。

## 任务 1：资源包状态与 CraftEngine 适配

**文件：** 新建 `TabletopPack.java`、`CraftEngineModels.java`；修改 `Tabletop3D.java`、`config.yml`、`plugin.yml`；测试 `TabletopPackTest.java`。
**接口：** `TabletopPack.packed(Player)`、`canPlay(Player,String)`、`toggle(Player)`、`button(Player)`、`item(String)`；`CraftEngineModels.item(String): ItemStack`，延迟取得 CE 注册物品。

- [ ] 写测试：混合加载成功前原版，成功后包；失败回退；关闭后旧回调无效；第三方 UUID 忽略；纯模式入口限制。
- [ ] 运行定向测试确认失败，再实现状态、PDC 偏好、发送/移除和 CE 就绪检测。
- [ ] 加入菜单顶部开关及加入、恢复、行动限制；定向测试通过后提交。

## 任务 2：牌、桌体和按钮的按需双显示

**文件：** 修改 `HandTable.java`、`TableView.java`、`TurnRing.java`、`GameWorld.java`；新建 `TableAudience.java`；测试 `PackRenderingTest.java`。
**接口：** `TableAudience.refresh()`、`packed(Player)`、`viewers(boolean)`；渲染方使用任务 1 的 `item` 和 `packed`。

- [ ] 写混合观看、旁观者、零观看者删除、私人切换及零泄漏测试，运行确认失败。
- [ ] 同一 Spec 生成原版/单 ItemDisplay 模型；重建与动画复用原逻辑，点击使用所选后端或逻辑位置。
- [ ] 家具和按钮随观看者显隐/删除；检查不变 revision 的切换及静止实体无更新；通过后提交。

## 任务 3：麻将中央面板

**文件：** 修改 `MahjongTableHud.java`；更新 `MahjongTableHudTest.java`。
**接口：** 使用 `TableAudience` 和 `TabletopPack.item`，中央文字按个人座位定向。

- [ ] 写四边分数、中央个人朝向、余 0 红字、立直棒预留位置测试，确认失败。
- [ ] 实现约 1.10 格面板、分数迁移、局数/余牌私有定向、双方位间立直棒和行动提示。
- [ ] 验证与牌河不重叠，测试通过后提交。

## 任务 4：原版实体精简

**文件：** 修改 `HandModels.java`、`RoundCardTable.java`、`TurnRing.java`；更新 `HandModelsTest.java`、`HandArtTest.java`、模型统计。

- [ ] 写保留轮廓与语义、圆角/圆环数量下降的回归，确认失败。
- [ ] 减少重复圆角与圆环单元，精简牌背、圆桌及方向环，保持牌大小/摆放。
- [ ] 生成对照图、统计实际数量，测试通过后提交。

## 任务 5：独立图片与模型资源

**文件：** 新建 `resource-pack/` 素材/来源清单、`tools/build-resource-pack.py`、`craftengine/` 注册和托管配置片段；资源验证测试。

- [ ] 先定义所有模型 ID、纹理引用与独立命名空间校验。
- [ ] 选择默认麻将牌面和桌布，核对图集映射；制作 Color Eight 清晰图案、圆角模型与圆桌。
- [ ] 打包独立 ZIP，检查每个模型及纹理引用；生成预览和摘要，通过后提交。

## 任务 6：按观看者播放音效

**文件：** 修改 `TableSounds.java`、`Tabletop3D.java`；更新 `TableSoundsTest.java`；添加小范围 OGG 及来源。

- [ ] 写原版/资源包玩家各收到一种声音、宝牌翻开事件及音量开关测试，确认失败。
- [ ] 核对选牌/打牌/副露/杠/立直/宝牌/和牌/倒计时声音，转换 OGG；绑定真实事件。
- [ ] 验证无重复播放、音频可解码、原版回退，通过后提交。

## 任务 7：完整验证与本地交付

**文件：** 版本元数据、安装说明、`docs/model-counts.zh-CN.md`、交付证据。

- [ ] Maven 完整 package，检查失败与跳过数量；资源构建/引用检查。
- [ ] 独立 Purpur 启动与重启验证；CE 缺失和 CE 就绪分别记录。
- [ ] 整体代码审阅，修复影响行为的发现；保存客户端待验清单。
- [ ] 本地提供 JAR、独立 ZIP、CE 配置、安装说明、预览、来源与实体统计，不发布 Release。

## 验证命令

使用工作区 Maven 与缓存：`D:/chatGPT/minecraft/.tools/apache-maven-3.9.11/bin/mvn.cmd -o -Dmaven.repo.local=D:/chatGPT/minecraft/.tools/m2 test`，定向测试增加 `-Dtest=<TestClass>`；交付使用 `package`。

执行记录放在本工作区的 `target/craftengine-work/progress.md`。用户已经明确要求按审阅过的 plan 开始实现，按本会话直接执行，不再次要求阶段授权。
