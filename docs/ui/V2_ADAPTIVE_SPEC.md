# V2_ADAPTIVE_SPEC.md — v2.0.0 自适应布局规格（手机 / 平板 / 折叠屏）

> 实现：`ui/adaptive/AdaptiveLayout.kt`
> 预览：`ui/preview/AdaptivePreviews.kt`（70–73）、`ui/preview/HomeWorkbenchPreviews.kt`（85/88 平板双栏）
> 接线：Lead 在 MainNavHost/各页面接入（本文件不改 MainNavHost）

---

## 1. 断点与规格

| 宽度档 | dp 范围 | 列数（工具网格） | 内容最大宽 | 页边距 | 双栏 |
|---|---|---|---|---|---|
| compact | < 600 | 2 | 840 | 16dp | 否 |
| medium | 600–839 | 3 | 840 | 24dp | 否 |
| expanded | ≥ 840 | 4 | 1200 | 32dp | **是**（主栏 55%，≥1200dp 时 58%） |

- 断点常量与 `CtTokens.Breakpoints` 对齐（未改 token 数值）。
- `CtAdaptive.specFor(widthDp)` 是唯一规格来源；页面**不要**再自己读 `LocalConfiguration.screenWidthDp`（除 `rememberCtAdaptiveSpec()`）。
- 内容限宽：`effectiveContentWidthDp = min(窗口宽 - 2*页边距, contentMaxWidth)`，平板内容居中，不拉满。
- 横屏/折叠屏：以「窗口宽度 dp」为准（与 M3 window size class 语义一致）；折叠屏展开（≥600dp）自动进入 medium/expanded。
- insets：页面内容根节点加 `statusBarsPadding()`；底栏由浮岛底栏自身处理 `navigationBars` insets（`windowInsetsPadding(WindowInsets.navigationBars)`）；Scaffold 保持 `contentWindowInsets = WindowInsets(0,0,0,0)` 避免双重留白。

## 2. API

```kotlin
enum class CtWidthClass { COMPACT, MEDIUM, EXPANDED }

data class CtAdaptiveSpec(
    val widthDp: Int,
    val widthClass: CtWidthClass,
    val contentMaxWidth: Dp,
    val horizontalPadding: Dp,
    val gridColumns: Int,
    val twoPane: Boolean,
    val mainPaneWeight: Float,
)

object CtAdaptive {
    fun widthClassOf(widthDp: Int): CtWidthClass
    fun gridColumns(widthDp: Int): Int
    fun contentMaxWidthDp(widthDp: Int): Int
    fun horizontalPaddingDp(widthDp: Int): Int
    fun twoPane(widthDp: Int): Boolean
    fun specFor(widthDp: Int): CtAdaptiveSpec
    fun effectiveContentWidthDp(widthDp: Int): Int
    fun supportsWorkbenchTwoPane(spec: CtAdaptiveSpec): Boolean
}

@Composable fun rememberCtAdaptiveSpec(): CtAdaptiveSpec

@Composable fun CtAdaptiveContent(
    spec: CtAdaptiveSpec = rememberCtAdaptiveSpec(),
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit,
)

@Composable fun CtTwoPaneRow(
    spec: CtAdaptiveSpec,
    modifier: Modifier = Modifier,
    gap: Dp = CtTokens.Spacing.cardGapExpanded,
    main: @Composable (Modifier) -> Unit,
    side: @Composable (Modifier) -> Unit,
)
```

## 3. 各页面规则

| 页面 | compact | medium | expanded | 状态 |
|---|---|---|---|---|
| 工作台 `HomeScreen` | 单列（问候/今日课程/电费/作业/通知/常用工具） | 单列 + 24dp 边距 | **双栏**：左=问候+今日课程+电费；右=作业+通知+常用工具 | ✅ 已实现（本轮） |
| 工具网格 `AllToolsScreen` | 2 列 | 3 列 | 4 列 | ⏳ `CtAdaptive.gridColumns` 已提供；其拖拽网格用固定 2 列几何，改造需 Lead 决策（见 §5） |
| 我的 `ProfileScreen` | 单列 | 居中限宽 | 居中限宽（可选 2 列卡片） | ⏳ 建议 Lead 用 `CtAdaptiveContent(spec) { ProfileScreen(...) }` 包一层（1 行接线，不改 ProfileScreen 内部） |
| 工具页（draw/tt/power/…） | 全屏 push，已各自 `≥600dp` 双栏（课表/电费/分组/体测） | 同左 | 同左 | 已有 |

## 4. 验证
- 预览：`AdaptivePreviews.kt`（380/700/1000dp 三档规格卡 + 我的平板限宽；1000dp 深色）；`HomeWorkbenchPreviews.kt` 85/88（平板双栏浅/深）。
- 单测：`ui/adaptive/AdaptiveLayoutTest.kt` 8 例（断点边界 599/600/839/840、列数、边距、限宽、双栏、effectiveContentWidth、mainPaneWeight）。
- 真机矩阵（Lead/E2E）：`docs/ADAPTIVE_TEST_MATRIX.md`（另一任务负责设备矩阵）。

## 5. 需要 Lead 接线/决策
1. `HomeScreen`：新增可选参数 `powerRepository/noticesRepository/userDataRepository/onOpenAll/isPageActive` 已就绪（不传保持兼容）；`onOpenAll` 建议路由到「工具」Tab；`isPageActive` 建议传 `pagerState.currentPage == 0 && !pagerState.isScrollInProgress`（否则轮换持续跑）。
2. `ProfileScreen` 平板限宽：在 MainNavHost 的 `ProfileScreen(...)` 外或内部包 `CtAdaptiveContent(spec) { ... }`（1 行，不改页面内部即可居中限宽）。若希望双栏（账号/设置 左右），需要改 ProfileScreen 内部（不在本轮写范围，建议下轮）。
3. `AllToolsScreen` 自适应列数：其拖拽排序依赖固定 2 列几何（`CtDockLogic` 外的 grid 命中以 item 宽/高计算）；若改 3/4 列需要同步改 `ToolOrderLogic.gridTarget` 的列数参数。建议下轮把列数参数化后再开放。
4. 横屏/折叠屏：MainNavHost 目前用 `LocalConfiguration.screenWidthDp >= 600` 判断 Rail；建议改为 `CtAdaptive.twoPane(widthDp)` / `spec.twoPane` 统一口径，并让浮岛底栏仅在 `!spec.twoPane` 显示。
5. 大字体（fontScale 1.3）与限宽叠加已由 Home 预览 86 覆盖；其他页面沿用既有 fontScale 预览（task-17 的 50–55）。
