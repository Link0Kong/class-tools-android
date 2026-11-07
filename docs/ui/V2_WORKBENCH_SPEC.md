# V2_WORKBENCH_SPEC.md — v2.0.0 工作台规格（对齐微信小程序 `pages/index`）

> 实现：`ui/screens/home/HomeScreen.kt` + `HomeModels.kt`（纯逻辑）
> 预览：`ui/preview/HomeWorkbenchPreviews.kt`（80–88）
> 单测：`ui/screens/home/HomeWorkbenchLogicTest.kt`（10 例）
> 接线：Lead 在 MainNavHost 传仓库参数（本文件不改 MainNavHost）

---

## 1. 对齐矩阵（小程序 → Android）

| # | 小程序 `pages/index` | Android 实现 | 差异/取舍 |
|---|---|---|---|
| 1 | 问候：`{hello}{姓名}` + 日期·周次·角色 | `GreetingHeader`：`{问候}，{姓名}` + `dateText · weekText · role`（空项省略） | 问候按时段（5–8 早上/9–11 上午/12–13 中午/14–17 下午/18–22 晚上/其余夜深） |
| 2 | 今日课程卡：多节时间线（时间/名称/时段/教室）+「上课中」pill +「课表 ›」 + 已结束提示 + 无课含下次课 + 未同步引导 | ✅ 全部实现（`TodayCoursesCard`）；状态色用 primary；pill = primaryContainer | 课程时间线不折叠，最多展示当天全部节次（通常 ≤6 行） |
| 3 | 宿舍电费卡：关注宿舍/剩余电量/状态 pill/更新；未选引导 | ✅ `PowerCard`；`hasReading=false` 显示「读取电量…」；未关注 0 请求 | 状态色：low=error / warn=tertiary / ok=primary；阈值取 `userdata.power.low`（缺省 30） |
| 4 | 作业待交卡：待交数 + 最新一条，每 5s 上浮淡出→下浮淡入轮换；无待交文案 | ✅ `HomeworkCard` + `RotatingText`（`AnimatedContent`，slide+`fade`）；过滤已完成/已过期；按截止升序 | 「最新一条」取**最近截止**（更可行动）；轮换 5s，页面不可见暂停 |
| 5 | 通知待处理卡：同上；同步中/失败/离线文案 | ✅ `NoticesCard`：LOADING/READY/OFFLINE/ERROR 四态；未处理按时间倒序；轮换同上 | `OFFLINE` = 有缓存但刷新失败；`ERROR` = 无缓存失败 |
| 6 | 常用工具：勾选 ≤4；无勾选空态「尚未勾选常用工具/去工具页勾选」；右上「全部 ›」 | ✅ `QuickToolsSection`：右上「编辑/完成」+「全部 ›」；空态卡点击去工具页；编辑态仍可在本页勾选（保留 v1 能力） | 小程序在工具页勾选；Android 两者都支持（空态引导 + 本页编辑） |
| 7 | 加载骨架屏 / 出错重试 | ✅ 卡片级 loading（读取中/同步中…）+ 失败文案；无全局骨架（v1 无骨架组件，避免新增动画开销） | 首屏无缓存时卡片显示加载行，非全屏 loading |
| 8 | 卡片入场动效 enter-1..5 | ⏳ 未做逐卡入场（避免与 pager 转场叠加抖动）；旋转卡有轮换动画 | 后续可在 pager 首帧做 stagger fade-in |

## 2. 数据来源 / 四态

| 卡片 | 数据 | 加载 | 空态 | 错误 |
|---|---|---|---|---|
| 今日课程 | `TimetableRepository.cached()/load()` → `TtCalendar.plan()` | `ttLoading` | `plan.hasData=false`→课表未同步；`plan.today.isEmpty()`→今天没课+下次课 | 无数据时引导打开课表（仓库自带缓存降级） |
| 电费 | `userdata.power.sel` + `PowerRepository.loadRow(rv)` | `powerLoading`（仅关注后请求） | 未关注→引导去选择 | 读取失败保持「读取电量…」/引导点开重试（PowerScreen 内有完整错误态） |
| 作业 | `HomeworkRepository.cached()/load()` + `marks` hwDone | `homeworkLoading` | 全部完成→「作业都完成了」 | `homeworkFailed`→「作业同步失败，点开重试」 |
| 通知 | `NoticesRepository.cached()/load()` + `marks` ntdone | LOADING | 无待处理→「暂无待处理通知」 | OFFLINE/ERROR 文案（见 §1 #5） |

轮换：`HomeWorkbenchLogic.ROTATION_INTERVAL_MS = 5000`；`LaunchedEffect(size, visible)` 内 `delay(5000)` 取 `nextRotationIndex`；`rotationEnabled && isPageActive && lifecycleResumed` 才运行（切后台/滑到别的 Tab 暂停）。

## 3. 自适应（≥600dp 双栏）

- compact：单列，页边距 16dp；
- medium（600–839dp）：单列 + 24dp，内容限宽 840dp；
- expanded（≥840dp）：**双栏**——左栏（问候+今日课程+电费，55%/58%），右栏（作业+通知+常用工具），页边距 32dp，内容限宽 1200dp 居中；
- 工具网格列数：`CtAdaptive.gridColumns(spec)`（2/3/4）已用于常用工具/编辑网格；
- 大字体 fontScale 1.3 预览（86）验证卡片可换行、不裁切。

## 4. 公开 API / 接线点

```kotlin
@Composable
fun HomeScreen(
    session: Session,
    onOpenTool: (AppTool) -> Unit,
    onHomeToolsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    quickToolIds: List<String> = ToolCatalog.defaultQuickIds,
    homeworkRepository: HomeworkRepository? = null,
    timetableRepository: TimetableRepository? = null,
    marksRepository: MarksRepository? = null,
    // ↓ v2 新增（全部可选，不传保持 v1 行为）
    powerRepository: PowerRepository? = null,
    noticesRepository: NoticesRepository? = null,
    userDataRepository: UserDataRepository? = null,   // 读 userdata.power.sel/low
    onOpenAll: () -> Unit = {},                        // 「全部 ›」/空态「去勾选」→ 工具 Tab
    isPageActive: Boolean = true,                      // pager 页可见性（轮换暂停）
    rotationEnabled: Boolean = true,
    nowMillis: Long = System.currentTimeMillis(),
    // 预览/测试注入
    initialTtPlan: TtPlan? = null,
    initialHomeworkRows: List<HomeworkRow> = emptyList(),
    initialNoticesData: NoticesData? = null,
    initialPowerSel: String? = null,
    initialPowerRow: PowerRow? = null,
    forceEmptyQuickTools: Boolean = false,
)
```

MainNavHost 建议：

```kotlin
HomeScreen(
    session = session,
    quickToolIds = quickToolIds,
    onOpenTool = ::openTool,
    onHomeToolsChange = { ids -> scope.launch { /* HomeToolsEditor.apply */ } },
    homeworkRepository = container.homeworkRepository,
    timetableRepository = container.timetableRepository,
    marksRepository = container.marksRepository,
    powerRepository = container.powerRepository,
    noticesRepository = container.noticesRepository,
    userDataRepository = container.userDataRepository,
    onOpenAll = { navController.navigate(Routes.ALL) { launchSingleTop = true } },
    isPageActive = pagerState.currentPage == 0 && !pagerState.isScrollInProgress,
)
```
> `quickToolIds` 目前 MainNavHost 用 `ids.ifEmpty { ToolCatalog.defaultQuickIds }` 兜底；若要展示小程序「无勾选空态」，请把兜底移到 `ToolCatalog.quickTools`（或传 `quickToolIds = ids` 并让 Home 用 `forceEmptyQuickTools=false` 时仍空）。

## 5. 验证
- 预览 80–88：有课/上课中/今天没课/未同步/深色/平板双栏/大字体/空态/平板深色。
- 单测 10 例：问候分档、日期周次文案、待交过滤排序、轮换标题、通知待处理过滤、电费三态、轮换 wrap/暂停、今日课程 ongoing。
- 手动：切后台/切 Tab 轮换暂停；未关注宿舍不请求电费（0 请求）；空勾选显示引导；平板双栏。
