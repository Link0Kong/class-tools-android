# `V4_NEW_FEATURES_UI.md` — v4 新功能 UI 规格（投票 / 名单三分类 / 电费同期）

> 依据：`docs/design/V4_UX_AUDIT.md`（审计 P0/P1）、`docs/design/V3_GLOBAL_STYLE.md`（品牌/玻璃/圆角/字体/动效）。
> 数据层依赖：task-33 交付的 `data/vote/`（`VoteRepository`/`VoteLogic`/模型）、`ClassLeagueRepository`/`MemberCategory`、`features/power/PowerCompareLogic.kt`。本文件只给 UI 规格，不写实现代码。
> 业务契约：`web/vote/CONTRACT.md`（含 2026-11-11 18:08 可见范围增量）、`web/AGENTS.md §33`（名单三 tab、投票完整发起）、`§34/§35`（电费同期比较与 13/16 隐藏 24h）。
> 红线：任何界面/RPC 响应**不出现学号**；名单不导出；投票不导出（隐私）；点击区域 **≥48dp**（高于网页的 40px）。

---

## 0. 总览

| 功能 | 页面 | 入口 | 路由建议 | 优先级 |
|---|---|---|---|---|
| 投票 | 列表 / 发起 / 详情（管理） | 「全部工具」新增投票卡；可勾选进工作台常用工具 | `Routes.VOTE = "vote"`（列表）；发起/详情可用同页 Overlay 或 `vote_create/{id?}`/`vote_detail/{pollId}` | P0（用户点名缺） |
| 名单三分类 | 名册页扩展 | 工作台/全部工具「名单」 | 复用 `Routes` 现有 roster 入口（不新增顶层 Tab） | P0（用户点名缺） |
| 电费同期 + 隐藏 24h | 电费页 | 现有入口 | 无新路由 | P0（用户点名缺） |

**通用视觉约束**（来自 V3）：
- 页面底 `ExpressiveBackground`；卡片为 L1 平面卡或 L2 玻璃卡（列表项一律平面，hero/详情头可用 `CtGlassSurface`）；
- 圆角：hero 32 / 卡 16 / 列表 12 / 按钮 pill；间距 4dp 基准；
- 字体：标题 titleLarge/Medium、正文 bodyMedium、标签 labelSmall≥11sp；数字 tabular；
- 深色：仅用既有 48 角色 token（不新增硬编码色）；投票结果条用 primary/secondary/tertiary 序列，名字列表用 `onSurfaceVariant`；
- 动效：状态切换 ≤250ms、可中断；列表插入/移除用 `animateItemPlacement`（LazyColumn）；见 `V4_MOTION_LIGHT.md`；
- 平板：≥840dp 用 `CtAdaptiveSpec` 双栏 + 内容限宽 1200dp（投票列表/详情双栏；名册列表+详情双栏；电费页已有双栏需复核）；
- 触控：所有 chip/按钮/开关行高 ≥48dp；结果条本身不点击，选项行 ≥56dp。

---

## 1. 投票（Vote）

### 1.1 信息架构与入口
- **入口**：
  1. 「全部工具」新增「投票」工具卡（`ToolCatalog` 增加 `vote`，名称「投票」，副标「限时 · 长时 · 实时结果」，图标 🗳/`Icons.Rounded.HowToVote`）；
  2. 工作台常用工具可勾选（`HOME_TOOLS` 白名单需允许 `vote`）；
  3. 工作台可加一张「进行中的投票」小卡（P2，与通知/作业轮换卡同模式：数量 + 最近截止一条）。
- **页面结构（compact）**：列表页 → 点卡片打开详情（推荐全屏 `VoteDetailScreen`，非 BottomSheet，因为详情含管理操作、倒计时与结果长列表）；右上「＋」发起（`VoteCreateScreen`）。
- **平板**：列表 + 详情双栏（左 0.45 列表，右 0.55 详情），发起用居中 Dialog/BottomSheet 宽 ≤720dp（或全屏两栏表单，见 §1.7 方案 B）。

### 1.2 列表页 `VoteListScreen`

**布局**（LazyColumn，`contentPadding` 16/24；顶部 `CtTopBar(title="投票", onBack, actions=[刷新])`）：
1. **筛选行**：`CtSegmentedButtons`（进行中 / 已结束）或 `CtFilterChipRow`；右侧「全部/我发起」chips（P1）；排序默认「最新创建」，下拉菜单「截止最近」。
2. **投票卡片**（`CtPressableCard` 或 `Surface(onClick)`，圆角 16dp，padding 14–16dp）：
   - 第一行：标题（titleMedium，2 行省略） + 右侧状态胶囊（进行中=primaryContainer「进行中」/已结束=surfaceContainerHigh「已结束」/「我已投」=secondaryContainer）；
   - 第二行：规则标签（`CtPill` 小号，wrap `FlowRow`）：`单选/多选 · 匿名/实名 · 班委可见/团员可见/指定 N 人 · 允许改票`；
   - 第三行：发起人姓名（无学号）+ 截止信息（「剩 02:15:30」/「11-12 20:00 截止」/「不限时」）+ 「N 人已投」；
   - 倒计时：每秒刷新，但列表页**每秒只重绘倒计时文本**（`produceState` + 独立小 Composable，避免整卡重组）；
   - 进行中卡片左侧 3dp 竖条（primary）；已结束卡片 0.6 alpha 文本。
3. **空态**：`还没有投票` + 说明「发起一次班会快投，或设置截止日期」+ 主按钮「发起投票」。
4. **错误/离线**：列表保留上次数据；顶部 `OfflineBanner`（「离线 · 12:30 缓存」+ 重试）；首次失败给全屏错误态 + 「重试」。
5. **刷新**：`PullToRefreshBox`；可见时 15s 静默轮询（进行中才轮询；已结束不轮询）。
6. **未处理数量徽标**：进行中且未投的投票数（P2，可在全部工具卡上显示）。

### 1.3 详情页 `VoteDetailScreen`

**结构**（`LazyColumn` + 固定底部动作条）：
1. hero 头（可用 `CtGlassSurface` 32dp 圆角）：标题（headlineSmall，3 行）+ 发起人 + 规则/范围标签行 + 倒计时（titleMedium tabular，`剩 hh:mm:ss`/`已截止`）；
2. 说明（bodyMedium，可折叠 >6 行）；
3. 选项区：单选 = `RadioButton` 行；多选 = `Checkbox` 行；每行：选项文字（2 行）+ 补充说明（labelSmall）+ 可选配图（16:9 缩略图，160dp 高，点击系统查看大图）；行高 ≥56dp；勾选态整行 primaryContainer 浅底 + 1dp primary 描边；
4. 结果区（可显示时）：
   - 每个选项：`LinearProgressIndicator`/Canvas 条（高 8dp 圆角）+ 选项名 + 百分比（tabular）+ 票数；
   - 最高票选项用 primary，其它用 secondary/tertiary/surfaceContainerHigh 序列（16 色板可复用 `TimetablePalette` 或品牌 4 色循环，需保证深色对比 ≥3:1）；
   - 实名 + 结果可见：投票人姓名 chips（`FlowRow`，只显示姓名，**无学号**）；匿名：显示「匿名投票 · 不显示投票人」；
   - 结果隐藏：`结果将在投票后/截止后可见` 的空态块 + 已投人数；
5. 底部固定动作条（`Surface` + `navigationBarsPadding`）：
   - 未投票：主按钮「提交投票」（多选显示「已选 x / 最多 y」）；
   - 已投且允许改票且未截止：按钮「改票」（改选后文案变「确认改票」）；
   - 已截止：按钮禁用/隐藏，显示「已截止」；
   - 非范围内：不出现（列表也不可见，进入应显示「投票不存在或已删除」全屏态，不泄露存在性）。
6. 右上「更多」菜单（`can_manage` 才显示）：编辑（仅 0 票，跳发起页预填）、提前结束、延长截止、删除；每项危险操作二次确认（删除 ⚠️ 红色文字 + 「不可恢复」说明）。
7. **状态机**（页面级）：
   - `LOADING → READY(hasVoted, visibleResults) → SUBMITTING → READY/ERROR`
   - `ERROR(NOT_FOUND | OUT_OF_AUDIENCE | CLOSED | NO_PERMISSION | NETWORK)`：分别文案「投票不存在/已删除」「你不在本次投票的可见范围」「投票已截止」「无权操作」「网络异常，点击重试」。
   - 轮询：详情可见时 quick 5s / long 10s；切后台停止；提交成功后立即刷新一次。
8. **倒计时**：本地时钟显示、服务端判定提交（契约 §1.5）；进入页面记录 `clientNow → serverNow` 偏移，展示 countdown 用服务端偏移（数据层提供 `now` 字段）；到点自动切只读并刷新一次。

### 1.4 发起页 `VoteCreateScreen`（或 Dialog/BottomSheet）

**表单结构**（分节卡，`LazyColumn`，底部固定「发起投票」按钮）：
1. **基本信息**：标题 `OutlinedTextField`（1–80，右下字数 `x/80`；超限禁用提交）、说明（0–500，多行 4 行起，字数）。
2. **时限**：`CtSegmentedButtons`（限时 / 指定截止 / 不限时）；
   - 限时：5/10/30/60/120 分钟 chips（默认 30）；显示「将于 21:30 截止」；
   - 指定截止：日期 + 时间两个按钮（`DatePickerDialog`/`TimePickerDialog` 或 Compose M3 日期选择器），必须晚于当前时间 ≥1 分钟；
   - 不限时：显示「无截止时间，可手动结束」。
3. **选项编辑器**（核心，2–20 项）：
   - 每行：序号 + 文字输入（1–80）+ 补充说明（0–200，可折叠）+ 配图按钮（可选）+ 上移/下移/删除（长按拖动也可，P2）；
   - 「添加选项」按钮（最多 20；不足 2 时禁用提交并提示）；
   - **配图**：系统相册选择 → 压缩（长边 ≤640px、JPEG q≈70、单张 data URL ≤150000 字符、合计 ≤1.6MB）→ 预览缩略图 + × 移除；压缩中显示 spinner；超限提示「图片过大，已压缩/请换一张」。
4. **规则**：单选/多选分段；多选时「最多可选」数字步进（2..选项数）；匿名开关（默认开）；允许改票开关（默认开）；结果可见性分段（始终/投票后/截止后，默认投票后）。
5. **可见范围**（契约 §1.6）：
   - 4 段：全班 / 班委 / 团员 / 指定成员（默认全班）；
   - 选「指定成员」→ 展开成员选择器：搜索框（姓名）+ `CtChipGroup` 成员 chips（`selectedKeys` 用 seat，只显示姓名；≥48dp 行高；支持全选/清空；至少选 1 人，否则提交时行内错误「请选择可见成员」）；
   - 摘要行：`指定 12 人：张三、李四…`（最多显示 6 + `等 N 人`）。
6. **默认值**（契约 §3.3）：单选、匿名开、允许改票开、结果「投票后可见」、范围全班、限时 30 分钟。
7. **校验与错误**：所有错误行内（字段下方红字 + 输入框 error 边框），不用 Toast；提交按钮 disabled 时副标显示第一条错误原因；提交中按钮 spinner + 防重复点击；失败保留表单 + Snackbar + 「重试」。
8. **草稿与返回**：表单有改动时按返回/关闭弹「放弃本次投票？」（保留草稿 1 次：`rememberSaveable` + 进程内）；编辑已有投票时标题改「编辑投票」，说明「尚无投票才能编辑」。

### 1.5 平板 / 深色 / 大字体 / 无障碍
- 平板（≥840dp）：
  - 列表 + 详情双栏：左 44% 列表、右 56% 详情；未选中时右栏空态「选择左侧投票查看详情」；
  - 发起：可用「左表单 / 右预览」双栏（选项预览 + 规则摘要），内容限宽 1200dp。
- 深色：结果条/标签/输入框用 token；投票人 chips 用 surfaceContainerHigh + onSurfaceVariant；危险操作 error。
- 大字体 1.3x：标题允许 3 行、选项行 `heightIn(min=56.dp)`、底部动作条文案允许 2 行；避免固定高度截断。
- 无障碍：选项行 `selectable(role = RadioButton/Checkbox)`；倒计时用 `liveRegion=Polite` 但**每 30s 播报一次**（避免每秒打断）；结果条给 `stateDescription="张三 12 票 34%"`；管理菜单项 `contentDescription` 完整；≥48dp。

### 1.6 方案对比（推荐 A）

| 方案 | 结构 | 优点 | 缺点 | 适用 |
|---|---|---|---|---|
| **A（推荐）** | 列表页 + 全屏详情页 + 全屏发起页（平板双栏） | 详情信息/管理/结果空间足；返回栈清晰；与页签/大字体友好 | 页面多一层 | 手机 + 平板统一 |
| B | 列表 + BottomSheet 详情 + Dialog 发起 | 切换快、代码少 | 长结果/选项/成员选择在 Sheet 里局促；平板浪费空间 | 仅 P2 快投 |
| C | 单页内 Segmented（列表/发起）+ Dialog 详情 | 入口少 | 状态复杂、返回易错 | 不推荐 |

**推荐 A**：投票包含「20 选项 + 配图 + 成员选择 + 实时结果」，Sheet 放不下；全屏页配合 `CtTopBar` 与返回栈最稳。平板用 A 的双栏变体。

### 1.7 Compose 结构与复用
```
VoteListScreen(onBack, repository, session?, onOpenDetail, onOpenCreate)
VoteDetailScreen(onBack, pollId, repository, onEdit, ...)
VoteCreateScreen(onBack, repository, editingPoll?, memberSource, leagueSource, onCreated)
```
- 组件：`CtTopBar`、`CtSegmentedButtons`、`CtFilterChipRow`、`CtChipGroup`、`CtBottomSheet`（成员选择/规则说明）、`Surface(onClick)`（卡片）、`CtPressableCard`、`LinearProgressIndicator`/Canvas（结果条）、`CtLoadingIndicator`、`OutlinedTextField`、`DatePickerDialog`/`TimePickerDialog`、`CtGlassSurface`（仅 hero）、`CtMotion`（过渡）。
- 状态建议：`VoteListUiState`（Loading/Ready(filter, items, refreshing)/Error/Offline）；`VoteDetailUiState`（Loading/Ready/Submitting/Error/NotFound）；`VoteCreateUiState`（Editing(valid, errors, dirty)/Submitting/Error）。
- 纯逻辑放 `features/vote/VoteUiLogic.kt`：倒计时文案、百分比取整（总和=100）、范围标签、校验错误排序、选项上下移、成员摘要文案（可单测 ≥12）。

### 1.8 与 web/mp 对齐 & 差异
- 对齐：字段/默认值/范围/结果可见性/管理动作/倒计时/无 sid 全部按契约；
- Android 差异（有意）：① 触控 ≥48dp（web ≥40px）；② 不做「图片导出」；③ 无分享到微信（用系统分享可选，P2）；④ 轮询在页面不可见时停止（web 详情 5s；Android 同但后台停）；⑤ 配图压缩目标 ≤640px/150k（契约同）。

---

## 2. 名单三分类（Roster / 名册）

### 2.1 与现有 RosterScreen 的关系
现状：`RosterScreen` 顶层是**名册 / 点名**两个 tab（`RosterTab.ROSTER/ROLL`），名册内 filter 有「全部 / 班委 / 点名名单」（`RosterFilter`），**无团员**。
目标（对齐网页/小程序「名单」工具）：**班委 / 团员 / 全班** 三分类，计数徽标；管理仅班委 tab。

**方案对比：**
| 方案 | 结构 | 优点 | 缺点 | 结论 |
|---|---|---|---|---|
| **A（推荐）** | 保留顶层「名册/点名」；名册内加 3 分类分段（班委/团员/全班），点名流程不变 | 改动小、点名工作流不打断；现有 filter 平滑替换；与 v3.6 心智兼容 | 分类是二级 tab（浏览深度 +0） | ✅ |
| B | 顶层改为 3 分类 tab，点名改为名册页「开始点名」按钮进入二级页 | 与 web 结构最像、一屏一分类 | 点名变成二级页，流程更重；改动大 | 备选 |
| C | 4 个顶层 tab（班委/团员/全班/点名） | 直达 | 顶栏拥挤（手机 4 tab 文字截断）、点名与分类不同维 | 不推荐 |

**推荐 A**：顶层仍「名册 / 点名」，名册内 `CtSegmentedButtons(班委 / 团员 / 全班)`，每段带计数；`RosterFilter` 的「全部/班委/点名名单」迁移为「三分类 + 点名名单用点名 tab 表达」。

### 2.2 页面规格（名册页）
- 顶部 `CtTopBar(title="名单 · 点名", subtitle="70 人 · 班委 8 · 团员 50")`（计数来自数据层，团员仅计数不暴露 sid）。
- 顶层分段：名册 / 点名（保留，带计数徽标：名册 70、点名 N）。
- 名册内：
  1. 三分类分段（班委 / 团员 / 全班），每项显示计数；默认「全班」；
  2. 搜索框（姓名/座号；带清除 ×；空结果「未找到 · 清除搜索」）；
  3. 列表行（≥56dp）：座号（等宽 tabular 36dp）+ 姓名 + 角色/徽标（班委 tab 显示 role badge；团员 tab 可显示团员徽标）+ 行尾「加入点名」勾选（44×44dp 命中区）；
  4. 班委 tab：管理员可见「管理」入口（v4 先只读；管理=网页的编辑角色，需数据层支持后再开；
     权限沿用 `session.isAdmin`）；
  5. 团长/团员 tab 来源 `ClassLeagueRepository`（`class_league` RPC，无 sid）；加载/失败/空态各自独立（团员名单失败不影响全班）；
  6. 底部/顶部操作：全选加入点名（保留）、沿用抽签名单（保留）。
- 点名 tab：保持现状（到/假、已处理沉底、重置本轮、清空点名名单），仅把「到/假」按钮提升到 ≥48dp 视觉。
- 隐私：不显示学号；页面页脚「仅班级内可见 · 不导出」；不新增分享/导出入口。
- 平板：左列表（44%）+ 右「点名名单/统计」侧栏（56%）；≥840dp 名册与点名可并排（提名册左、点名右，减少切换）。
- 深色/大字：列表行 `heightIn(min=56.dp)`、姓名 2 行内；角色 badge `labelSmall`；对比 ≥4.5。

### 2.3 数据/状态
- `MemberCategory`（COMMITTEE/LEAGUE/ALL）；`MemberEntry(seat, name, role, badge)`；计数：班委 = role 非空人数，团员 = `class_league` 人数，全班 = 70。
- 状态：`Loading / Ready(list, filter, search, selectedSeats) / Error(retry) / Offline(cached)`；切换分类保留搜索词（清空? 推荐保留并显示「在 X 中搜索」）。
- 点名名单 `rollSel` 与分类无关（可跨分类），勾选后点名 tab 显示人数徽标。

### 2.4 复用组件
`CtSegmentedButtons`（顶层+三分类）、`CtFilterChipRow`/`CtChipGroup`、`CtTopBar`、`CtPill`（badge）、`LazyColumn` + `animateItemPlacement`、`CtBottomSheet`（管理说明/批量操作）、`CtLoadingIndicator`。

---

## 3. 电费：自然周期同期比较 + 13/16 号楼隐藏 24h

### 3.1 同期比较徽标
**口径（数据层 `PowerCompareLogic`，与 web AGENTS §34 一致）**：
- 24h：今日 00:00–现在 vs 昨日 00:00–昨日同一时刻；
- 7d：本周一–现在 vs 上周一–上周同一时刻；
- 30d：本月 1 日–现在 vs 上月 1 日–上月同一日同一时刻；
- 当前周期用电 = 窗口内真实采样的下降累计（不插值）；**两侧真实采样覆盖 ≥ 窗口 35% 才可比较**，否则不显示（数据积累中）；
- 历史不足时静默隐藏，不显示「0%」或「—」。

**UI 规格**：
| 项 | 规格 |
|---|---|
| 位置 | 对应区间 tile 的数值右侧/下方（推荐 tile 内右侧小徽标），hero 不放大段比较（避免信息过载） |
| 文案 | `较昨日同期 +12%` / `较上周同期 −8%` / `较上月同期 +3%`（+ 表示用电更多；颜色：+ 用 tertiary/error 温和提示，− 用 primary/success；不可仅靠颜色 → 带箭头/文字） |
| 格式 | 百分比四舍五入到整数；`<1%` 显示「基本持平」；只有单侧覆盖不足 → 隐藏 |
| 点击 | 徽标可点 → BottomSheet 说明计算区间（`今日 00:00–18:20 vs 昨日同期` + 覆盖度），提升可信度 |
| 数据不足 | tile 保持原「近 N 天用电」；不显示徽标；可选副标「比较数据积累中」 |
| 平板 | tile 2×2 保持；徽标不与数值争宽（右对齐、`labelSmall`） |
| 深色 | 徽标容器 `surfaceContainerHigh` + 内容色；对比 ≥4.5 |

**状态机**：`COMPARE_HIDDEN(insufficient) → COMPARE_READY(deltaPercent, label) → COMPARE_ERROR(fallback hidden)`；切换区间/宿舍/刷新时先显示上次值 200ms 再更新（避免闪烁）。

### 3.2 13/16 号楼隐藏 24h
**规则**：楼栋 ∈ `PW_SLOW_BLD`（13号楼/16号楼）→ **区间选择器不显示 24h**；当前选中慢楼栋时自动落到 7d（并默认展示「日更」徽标）；从慢楼栋切回实时房时**恢复进入前的档位**（若之前是 24h 则恢复 24h）。

**UI**：
- 区间 `CtSegmentedButtons` 动态选项：实时房 = 24h/7d/30d；慢楼栋 = 7d/30d（两个选项时按钮加宽、仍 ≥48dp）；
- 自动落 7d 时给一次性 Snackbar/说明「13/16 号楼为日更数据，已切到 7 天」（可关闭，P2）；
- 「日更」徽标（已存在）放 hero 更新行；24h 数据仍内部拉取（日更检测/估算用），只是不展示趋势；
- 无障碍：区间按钮隐藏 24h 后 TalkBack 不播报不存在项；自动切换用 `liveRegion` 播报新档位；
- 平板：区间仍一行；如后续加档位（如「自定义」）预留横向滚动。

**状态机**：`range = 24h|7d|30d`；`effectiveRanges(building)`；`onSelectBuilding(building)`：若当前 range 不在 effectiveRanges → 暂存 previousRange 并切 7d；`onSelectRange(range)` 手动；切回实时楼栋 → 若 previousRange 存在且当前仍是自动落下的 7d → 恢复 previousRange，否则尊重用户手动选择。

### 3.3 方案对比（推荐）
| 功能 | 方案 | 结论 |
|---|---|---|
| 同期比较 | A. tile 内徽标（推荐）；B. 单独「比较」卡；C. hero 下文字 | A：信息密度高、与网页一致 |
| 隐藏 24h | A. 隐藏 chip（推荐，与 web/mp 一致）；B. 禁用 chip + 说明；C. 不隐藏仅默认 7d | A：避免用户点进无效档；B 占位反而困惑 |
| 自动恢复 | A. 记住进入前档位（推荐）；B. 总是默认 7d | A 不打扰用户；B 简单但丢偏好 |

---

## 4. 组件复用与新增建议

| 需求 | 复用 | 需新增（建议归属） |
|---|---|---|
| 玻璃 hero | `CtGlassSurface`（`ui/components/glass`） | — |
| 分段筛选 | `CtSegmentedButtons`/`CtFilterChipRow`/`CtChipGroup` | — |
| 底部动作条 | `Surface` + `navigationBarsPadding`（已用于 Fit/Group） | `CtActionBar`（统一安全区 + 主/次按钮布局，P1） |
| 倒计时 | `LaunchedEffect` + `produceState` | `rememberCountdown(deadline, serverNow)` 通用工具（可单测，建议 features/common） |
| 结果条 | `LinearProgressIndicator` 或 Canvas | `CtPercentBar`（label + value + pct，深色/大字/a11y） |
| 选项编辑 | 现有 `OutlinedTextField` + 行容器 | `VoteOptionEditorRow`（页面内私有） |
| 成员多选 | `CtChipGroup` + `CtBottomSheet` + 现有 `ClassMembersRepository` | 名单选择器通用化（GroupScreen 已有类似；建议抽 `MemberPickerSheet` 复用） |
| 图片压缩 | 无现成 | `features/vote/VoteImageCompressor`（Bitmap 采样 ≤640 + JPEG + Base64 上限），纯逻辑可测 |
| 危险操作确认 | `AlertDialog` 模式（各页已用） | `CtDangerDialog`（统一文案/样式/红色按钮，P1） |
| 状态页 | 各页自建 | `CtStateView`（Loading/Empty/Error/Offline + retry，P1，全项目收益最大） |

---

## 5. Lead 接线点 / 数据层现状（task-33 已交付）

> 数据层契约与实现见 `docs/api/VOTE_LEAGUE_POWER_IMPL.md`；下列符号与签名以该文档/源码为准（2026-11-11 已落地未接线）。

1. **路由（建议）**：
   - `Routes.VOTE = "vote"`；`Routes.VOTE_DETAIL = "vote/{pollId}"`；`Routes.VOTE_CREATE = "vote/create"`（或发起/详情用 Overlay，则只需 VOTE 一项）；
   - `Routes.ROSTER` 保持；无新增路由。
2. **AppContainer/MainNavHost**：
   - 注入 `VoteRepository`（`DefaultVoteRepository(apiClient, currentSid)`）与 `ClassLeagueRepository`（`DefaultClassLeagueRepository(apiClient)`）；
   - `MainNavHost` 传 `currentSid`/`isAdmin`；管理权限沿用服务端 `canManage(mine, isAdmin)`（`VoteLogic.canManage`）。
3. **ToolCatalog**：新增 `vote` 工具卡（全部工具/工作台可选）；`homeTools` 迁移无需变更（未知新 id 排末尾，`normalizeHome`）。
4. **电费**：`PowerScreen` 直接消费——
   - `PowerCompareLogic.compare/compareAll(points, range, now, zone)` → `PowerCompareResult(range, available, label, percent, currentUse, previousUse, currentWindow, previousWindow, currentCoverage, previousCoverage, reason, title, badgeText)`；
   - `PowerCompareLogic.showRange(range, building)` / `showRange(ranges, building)` / `normRange(range, building)` → 区间 chips 过滤与 13/16 自动落 7d；
   - `unavailableHint(result)` → 数据不足提示（不显示徽标时内部诊断用）。
5. **名单**：`RosterScreen` 加 `classLeagueRepository` 参数（可选，null 时团员 tab 显示「加载失败/重试」）；分类逻辑直接复用——
   - `MemberCategory`（COMMITTEE/LEAGUE/ALL，label「班委/团员/全班」）、`MemberCategoryCounts`（tab 计数）、`MemberCategoryBuilder.committee/all(members)`、`MemberCategoryBuilder.league(leagueMembers)` → `MemberEntry(seat, name, badge)`（**无 sid**）；
   - `ClassLeagueRepository.load(force)/cached()` → `ClassLeagueResult`；`LeagueMember(seat, name)`。
6. **投票**：UI 只消费数据层纯逻辑，不重复实现——
   - `VoteRepository.list/detail/create/submit/manage` → `VoteResult<T>`；`PollList/PollSummary/Poll/PollDetail/PollOption/PollSettings/PollVoter`；
   - `PollDraft/PollOptionDraft` 供发起页；`VoteLogic.validateDraft(draft, now)` → `List<VoteValidationError>(VoteField, message)`；
   - 倒计时/状态：`VoteLogic.countdown/formatRemaining/deadlineLabel/effectiveClosed/resultsVisible/canChangeVote/voteBlockReason/audienceTag/resultHiddenTag/percent/formatDeltaPercent/bestNow`；
   - 错误：`VoteError`（sealed，含中文文案）；提交/管理动作：`PollManageAction`（Edit/Close/Extend/Delete）。
7. **需冻结/决策**：
   - 投票详情是「全屏页」还是「BottomSheet」（推荐全屏页 + 平板双栏）；
   - 投票发起是否为独立页（推荐是）；
   - 名单顶层结构选 A（名册/点名 + 名册内三分类）还是 B（3 分类 + 点名二级）（推荐 A）；
   - 作业/通知「单条提醒」入口是否本期一起做（`data/reminder` 已具备，UI 建议 P1）；
   - 是否需要「投票结果导出」：按契约**不做**（隐私红线）；
   - 投票图片压缩放数据层还是 UI 层（建议 `features/vote` 纯逻辑，UI 只选图与预览）。

---

## 6. 验收（UI 层）

- 预览（新增 ≥12 张，放 `ui/preview/V4VoteRosterPowerPreviews.kt`）：
  投票列表（浅/深/空/已结束筛选）、投票详情（未投/已投/结果隐藏/管理菜单）、发起（基础/多选+配图/指定成员选择/时间选择）、名单三分类（班委/团员/全班/搜索空态）、电费（同期徽标 +12/−8/数据不足、13 号楼无 24h、平板双栏）；
- 单测（≥15，纯逻辑）：百分比/倒计时格式、结果条取整总和=100、范围标签、选项上移下移、成员摘要、同期比较文案/覆盖阈值、隐藏 24h 状态机、恢复档位；
- E2E：全部工具→投票→发起（限时 5 分钟）→投票→结果→管理删除；名册切三分类/搜索/加入点名；电费 13 号楼无 24h、切回 23A 恢复 24h；
- 视觉：浅/深/大字体/平板 4 套截图（`artifacts/previews/v4/`）；无学号字样（脚本断言）；触控 ≥48dp（uiautomator bounds）。

