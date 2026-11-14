# Round 59 报告 · v4.49.0（点名拆为独立工具 + 名单三类人群滑动）

> 用户要求：① 把点名工具拆出来；② 在名单中不同人群的名单添加滑动效果。
> 本轮把「点名」从「名单」拆成独立工具卡/页面，名单页只保留名册，并给「班委/团员/全班」三类人群加左右滑动切换与动画。

## 1. 拆分结果

| 项 | 拆分前 | 拆分后 |
|---|---|---|
| 全部工具卡片 | 「名单」= 名册+点名 | **「名单」= 名册**；新增 **「点名」** 独立卡（班级事务分类，`Icons.Rounded.HowToReg`） |
| 页面标题 | 名单 · 点名（名册↔点名外层 pager） | 名单 · 名册（只剩名册）；点名独立页标题「点名」 |
| 点名状态 | `userdata.roster`（rollSel/rollSt/rollNames） | 不变，复用 `RosterLogic`/`RosterCodec`（按姓名迁移/同步） |
| 代码结构 | `RosterScreen.kt` 1012 行含两页 | `RosterScreen.kt` 596 行（名册）+ 新 `RollCallScreen.kt` 565 行 + 共用 `RosterShared.kt` 251 行 |

**点名独立页**保留：点名名单、到/假切换、已处理沉底、全选、沿用抽签名单、清空（确认+撤销）、重置本轮（确认+撤销）、搜索（姓名/座号）、筛选（全部/未点/到/假）、统计卡、空/加载/错误/刷新错误条、隐私（不显示学号、不导出）。

**名单页**保留：三类人群、搜索、只看点名名单筛选、勾选加入点名名单、全选点名、下拉刷新、空/加载/错误、刷新失败条；删除「名册/点名」外层切换、名单页「清空」按钮（已归点名页），「已加入点名 N」改为点名名单总人数。

## 2. 三类人群左右滑动

- `班委 / 团员 / 全班` 改成 `HorizontalPager`：跟手翻页 + 默认 spring（约 300ms）吸附；
- 分段 pill 与 pager 双向联动：点击 `animateScrollToPage`；选中态由 `currentPage` 驱动；底部指示条由 `currentPage + currentPageOffsetFraction` 跟手（clamp）；
- `reduceMotion`：禁用拖拽、点击瞬时 `scrollToPage`；
- 搜索状态跨分类共享；团员名单仍按切到团员页懒加载（失败降级缓存）；
- 每页保留完整列表/空/加载/错误内容与下拉刷新。
- 预览图（滑动中间态，左班委/右团员同时入镜）：`artifacts/previews/v4490/ToolV6RosterCategorySwipeMidPreview_0.png`；
  点名页：`ToolV6RollCallLightPreview_0.png`、`ToolV6RollCallDarkPreview_0.png`、`ToolV6RollCallEmptyPreview_0.png`。

## 3. 接线

- `ToolCatalog`：roster 卡片副标题改为「班委 · 团员 · 全班」；新增 `id="roll"`「点名」卡片（HowToReg/clay/CLASS）。
- `MainNavHost`：新增 `"roll" -> RollCallScreen(onBack = { popTool(toolId) }, session, classMembersRepository, userDataRepository)`；`"roster"` 调用不变（classLeagueRepository 仍用于团员懒加载）。
- E2E：`RosterTitle` 改为「名单 · 名册」；移除名单页「点名」页签步骤；新增 `tool_roll` 步骤（卡片「点名」+ 标题/空/错误态断言，截图 33_roll.png）；`roster_categories` 仍断言班委/团员/全班。
- 旧 `WaterRosterPreviews` 第 30/31 张点名预览已迁移到 `RollCallScreen`，不再渲染成名册页。

## 4. 验证

- 全量单测：**1177 / 0 failures / 0 errors**（93 suites；含 RosterLogic 15/0、通知分页/JW 等既有测试）。
- 设备手工检查（debug）：名单页标题「名单 · 名册」、三分类 pill + 指示条；点名页独立打开正常（dev 预览身份下为空/错误态属预期）；预览渲染器输出 10 张 v6 预览 + 5 张 V4Polish 名单预览全部正常。
- 快速 E2E：**45 步 PASS / 0 WARN / 0 FAIL（1028.2s）**；导航探针 **10/10**；logcat 0 FATAL/ANR；新增 tool_roll 步骤截图 `33_roll.png`，深色/浅色恢复通过。
- Release v4.49.0：**3,158,217 B** / SHA-256 `54AB432F53F8AED47EBA1BB86C4B6D132356BB32ADCDB9EA964335730F09C8A7`（vc67）；
  `pm clear` 后 371ms / 274ms，稳定 **288 / 270 / 291ms**；logcat 0 FATAL/ANR；versionName=4.49.0。

## 5. 已知边界

- 点名页按任务要求只保留顶栏刷新（未加下拉刷新）；需要可后续补。
- 团员名单仍「滑到团员分类时懒加载」，首次滑动 settle 前可能短暂显示「团员名单为空」再加载（与拆分前一致）。
- 名单页下拉刷新包 pager，纯空态页下拉不触发（顶栏刷新可用）。
- 工作区同时包含并行会话的「教务」v4.48 改动（随本版一起编译，非本轮范围）。
