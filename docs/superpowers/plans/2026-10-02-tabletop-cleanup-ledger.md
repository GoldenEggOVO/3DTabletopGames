# 实施记录

- 基准：faea2aa，用户批准全部实施并移除 AuthMe、Geyser/Floodgate。
- 复用现有隔离工作树 .worktrees/color-eight，初始仅设计文档未提交，产品源码干净。
- 决定：按顺序在当前会话实施；用户“允许全部进行”包含设计后的实施步骤，因此不再重复请求计划批准。
- 校验：自动回合仍走 apply；横牌元数据只包含公开牌；当前历史转换必须先证明回放等价。

- Task 1: RED missing auto-discard API; GREEN 30 focused tests. Tick uses normal apply/revision/save path; legal win choices pause human riichi automation, skip submits legal drawn-tile discard.
- Task 2: RED missing coloured north texture; GREEN 31 Java focused tests and 9 asset checks. Added public meld identities, sideways declarations/calls and stacked added kan; flower row separated from melds.
- Task 3: AuthMe/Geyser and startup migration removed; current ColorEight name/ID canonical. Public-lobby foreign-provider reflection/labels removed; existing native labels remain owned by the board renderer.
- Task 4: RED catalogue hash keys and missing offline converter; GREEN complete named catalogues and 3 converter behavioral tests. Custom names literal; unknown custom phrases retained in report and source copy.
- Task 5: RoomStore round-trip/schema/recovery checks GREEN 10/10; full suite before formatting GREEN 537/537. Main callbacks expanded into readable blocks, storage and turn policy independent.
- Ruling: Keep previously accepted exhausted-deck draw/pass replay semantics — current 1.8.10 files can contain these records and must restore identically — deleting it would break those rooms; live action legality remains strict.
- Ruling: Unmapped custom phrase translations are retained for manual placement into complete templates — fragments cannot be safely joined into an arbitrary new sentence — missing custom phrasing falls back to the complete builtin template until reviewed.
- Ruling: Remove disabled foreign-provider probe branches and label code — current plugin only owns its own tables — external-provider features would require explicit new implementation if requested.
- Final review: one important finding, regional concealed kan was duplicated as flowers and exposed two faces. RED reproduced eight tiles; GREEN now renders four backs and one explicit flower when present. One fix pass completed; no minor findings.
- Final ruling: client appearance/audio/FPS require in-game acceptance. Real CraftEngine registration, server lifecycle and official client transform parsing verify their respective layers but cannot certify the user's screen or listening experience.

- Final verification: 540 Java tests, 9 pack checks, 3 converter checks; exact JAR SHA256 34372b0bd8a58a28c573ebc1a19210f54ab8ad077eedecd1ba01d5cd8dd8f465 passed native and CraftEngine three-boot recovery. Official 26.2 transform parser passed.
- Deployment: authorized test server 5.104.84.28:25589 running 1.9.0, 111 CE models, HTTPS200/hash verified, 657 semantic keys each locale and language reload passed. Full backup tabletop-190-20261002-164054; protected files unchanged. Client acceptance pending; branch/worktree retained, no Release.
