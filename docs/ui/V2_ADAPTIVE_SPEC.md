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

---

## 6. v2.0.0 收尾（task-25）：3/4 列拖拽 + 我的双栏 + 大屏密度

### 6.1 工具网格列数参数化（`ui/screens/all/`）
- `AllToolsScreen` 读取 `rememberCtAdaptiveSpec()`：`columns = spec.gridColumns`（compact 2 / medium 3 / expanded 4）；
- 网格是 **custom Layout**（保证拖动重排时卡片节点 identity 稳定）：`itemWidth = (width - spacing*(cols-1)) / cols`；
- 拖动命中几何同步参数化（`ToolOrderLogic.gridCellOf/gridTarget(..., columns)`）：
  - 列以「列间隙中线」为分界：`column = ((pointerX + spacing/2) / (itemWidth+spacing)).toInt()`（compact 2 列与旧行为一致）；
  - 行 `row = floor(pointerY / (itemHeight+spacing))`；
  - `slot = (row*cols + column)` → 被拖卡片移动到的下标；
- 长按起点/ghost 宽度/插入位/边缘自动滚动全部按实际列数/卡宽计算；TalkBack「上移/下移」不受影响（走 `moveBy`，与列数无关）；
- 内容限宽：外层 `widthIn(max = spec.contentMaxWidth)` + `spec.horizontalPadding`，平板不再把卡片拉满整屏（P1-1）。

### 6.2 我的页平板双栏（`ui/screens/profile/ProfileScreen.kt` + `ProfileLayout.kt`）
- ≥840dp：双栏——左「账号 + 同步 + 权限与提醒」，右「数据与隐私 + 外观 + 关于 + 退出」；主栏占比 `spec.mainPaneWeight`（0.55 / ≥1200dp 0.58）；
- compact/medium：单栏（顺序账号→同步→数据与隐私→权限与提醒→外观→关于→退出）+ 内容限宽居中；
- 大屏密度：顶部留白 10→6dp、卡片间距 14→12dp、页边距按 spec（16/24/32）；
- 所有既有入口/回调签名不变（`onOpenPermissions/onOpenReminderSettings/onOpenDataPrivacy/onSyncEnabledChange/onDynamicColorChange/onSignOut`），仅新增可选 `adaptiveSpec: CtAdaptiveSpec? = null` 供预览/测试注入；
- 顺带修复「权限与提醒」卡片内重复分隔线。

### 6.3 工作台平板密度 / 顶栏 padding（`ui/screens/home/HomeScreen.kt`、`ui/components/CtTopBar.kt`）
- 工作台双栏：左右栏间距 32→20dp、顶部 10→6dp、底部 28→24dp（只调 padding/间距，不动数据逻辑）；
- `CtTopBar`：≥840dp 时左右 padding 8→4dp、上下 6→4dp、最小高 56→52dp（平板横屏留白收紧，P2）。

### 6.4 深色模式检查（v2 新组件，只读 token 未改色板）
| 组件 | 关键组合（深色） | 对比度 | 结论 |
|---|---|---|---|
| 浮岛底栏 | `onSurfaceVariant` / 半透明 surfaceContainer（叠加 background） | ≈9.5 | ✅ AA |
| 浮岛底栏选中药丸 | `onSecondaryContainer` / `secondaryContainer` | 7.25 | ✅ AA |
| 工作台卡片 | `onSurface` / `surfaceContainerLow` | 13.31 | ✅ AA |
| 工作台次要文本 | `onSurfaceVariant` / `surfaceContainerLow` | 10.11 | ✅ AA |
| 权限页状态 pill | `onSecondaryContainer` / `surfaceContainerHigh` | ≈9.7 | ✅ AA |
| 权限/提醒错误卡 | `onErrorContainer` / `errorContainer` | 7.23 | ✅ AA |
| 我的平板双栏 | 卡片色/文本与单栏相同 | 同上 | ✅ AA |

结论：v2 新组件深色对比度全部 ≥ AA；未改任何 token。唯一注意：浮岛底栏为半透明叠层，若后续在底栏下放高亮内容，可用 `translucent=false` 关闭透明。

### 6.5 预览（12 张，`ui/preview/AdaptivePolishPreviews.kt` 90–100）
全部工具 3/4 列（浅/拖动中/深色）、我的平板双栏（浅/深）、我 的中屏单栏、权限管理深色、提醒设置深色、身份页小屏 360×640、工作台 700dp 深色。

### 6.6 单测（15 例）
- `ToolOrderGridColumnsTest` 9 例：2/3/4 列格子映射、越界 clamp、3/4 列 gridTarget、last-row clamp、同格 no-op、gridRows；
- `ProfileLayoutTest` 6 例：compact/medium 单栏顺序、expanded 左右分栏、主栏占比、卡片无丢失/重复、平板密度收紧。
