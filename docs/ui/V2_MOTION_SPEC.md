# V2_MOTION_SPEC.md — v2.0.0 动效规格（浮岛底栏 / Tab 滑动 / 工具页 push）

> 组件实现：`ui/components/dock/CtFloatingDock.kt`、`CtTabPager.kt`（含 `CtPageTransitions`）、`DockLogic.kt`
> 动效 token：`ui/theme/Motion.kt`（`CtMotion`，M3 Expressive official springs，未改数值）
> 接线：Lead 在 `MainNavHost`（本文件只写规格与 API，不改 MainNavHost）

---

## 1. 浮岛底栏（ColorOS 17 Floating Island × M3 Expressive floating toolbar）

### 1.1 视觉规格

| 项 | 值 | 说明 |
|---|---|---|
| 形态 | 悬浮胶囊 | 左右外距 20dp；底部离导航栏 insets 再 +10dp；最大宽 480dp 居中（平板不拉满） |
| 高度 | 60dp | item 高度 56dp + 上下留白（视觉观感悬浮） |
| 圆角 | 32dp | 接近全圆胶囊 |
| 底色 | `surfaceContainer` @ alpha 0.82 | 半透明玻璃感；低性能/高对比偏好时 `translucent=false` → 不透明 |
| 描边 | `outlineVariant` @ alpha 0.55，1dp | 顶部高光/轮廓 |
| 阴影 | elevation 12dp | 悬浮层 |
| 选中底 | `secondaryContainer`，pill 形状，宽 = item 宽 - 10dp、高 44dp | 「弹性药丸」 |
| 未选中 | `onSurfaceVariant` 图标/文字 | |
| 选中 | `onSecondaryContainer` 图标/文字 | |

### 1.2 动效（全部 spring，无 tween 硬切）

| 状态 | 规格 | 实现 |
|---|---|---|
| 药丸吸附（松手/点击切换） | `CtMotion.spatialFast`（d=0.6, k=800，轻微 overshoot） | `animateFloatAsState(snappedIndex)` |
| 药丸跟手（滑动中） | **无动画**，位置 = `selectedIndex + pagerOffsetFraction` 直接映射 x（跟手 1:1） | `indicatorPosition = rawPosition` |
| 药丸拉伸 | 水平 ×(1+0.08·|offset|)，垂直 ×(1−0.04·|offset|) | `CtDockLogic.pillScale` + 拖动中 `CtMotion.fadeIn` spec |
| 图标/文字颜色 | `CtMotion.effects`（1.0/1600，无 overshoot） | `animateColorAsState(CtMotion.effectsSpec())` |
| 按压反馈 | 选中 0.96 / 未选中 0.90 / 拖动中 0.94 缩放 | `pressScale` + `CtMotion.spatialFast` |
| 切换触感 | `HapticFeedbackType.LongPress` 轻触（仅切换时） | `LocalHapticFeedback` |

### 1.3 交互/无障碍
- 每个 item `selectable(role = Role.Tab)` + `selectableGroup`；触控高度 60dp（≥48dp 硬线）。
- `contentDescription = "工作台，第 1 个 Tab，共 3 个，已选中"`（`CtDockLogic.tabAnnouncement`）。
- 图标自身 `contentDescription=null`（语义由父节点提供，避免重复播报）。

### 1.4 API
```kotlin
@Composable
fun CtFloatingDock(
    destinations: List<CtDockDestination>,   // key/label/icon/contentDescription
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pagerOffsetFraction: Float = 0f,         // PagerState.currentPageOffsetFraction，跟手药丸
    translucent: Boolean = true,
    showLabels: Boolean = true,
)
```

接线（MainNavHost）：
```kotlin
val pagerState = rememberCtTabPagerState(pageCount = 3)
Scaffold(
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    bottomBar = {
        CtFloatingDock(
            destinations = TopLevelDestination.entries.map {
                CtDockDestination(it.route, if (it == TopLevelDestination.AllTools) "工具" else it.label, it.icon)
            },
            selectedIndex = pagerState.currentPage,
            pagerOffsetFraction = pagerState.currentPageOffsetFraction,
            onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
        )
    },
) { padding -> CtTabPager(pagerState, Modifier.padding(padding)) { page -> ... } }
```
> 平板/折叠屏（≥840dp）：保留现有 NavigationRail；浮岛底栏仅 compact/medium 使用（Lead 接线时判断窗口宽度）。

---

## 2. Tab 滑动切换（三页：工作台 / 工具 / 我的）

### 2.1 规格

| 项 | 值 |
|---|---|
| 容器 | `HorizontalPager`（3 页，`beyondViewportPageCount=1`） |
| 页面位移 | `pageOffset × 8%` 屏宽（`CtDockLogic.PARALLAX_FRACTION=0.08`） |
| 页面透明度 | `1 → 0.72`（距离 1 页） |
| 页面缩放 | `1 → 0.98`（距离 1 页） |
| 插值 | 跟手连续插值（`pageOffset = (currentPage - index) + offsetFraction`），无额外 spring（pager 自带惯性/吸附） |
| 底栏联动 | 药丸位置 = `currentPage + offsetFraction`（1:1 跟手），松手 `spatialFast` 吸附 |
| 页面背景 | 传 `pageBackground = MaterialTheme.colorScheme.background` 防滑动缝隙透黑 |
| 嵌套 | 工具页仍为全屏 push（不走 pager）；pager 只承载三个顶层目的地的内容 |

### 2.2 API
```kotlin
@Composable fun rememberCtTabPagerState(pageCount: Int, initialPage: Int = 0): PagerState
@Composable fun CtTabPager(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    beyondViewportPageCount: Int = 1,
    pageBackground: Color = Color.Transparent,
    content: @Composable (page: Int) -> Unit,
)
@Composable fun rememberCtDockPagerState(pagerState: PagerState): CtDockPagerState
// CtDockPagerState(selectedIndex, followFraction, pageCount, continuousPosition, settlingPage)
```

### 2.3 滑动冲突约定
- 工具页内部已有横向滚动/手势的组件（课表网格横向滚动、电费趋势手势、水量月份、抽签横向步骤）不受影响：pager 仅包裹顶层三个页面容器，工具页 push 后不在 pager 内。
- 工作台内部纵向滚动与 pager 横向手势正交，无需额外处理。

---

## 3. 工具页 push 转场（NavHost）

| 项 | 进入（push） | 返回（pop） |
|---|---|---|
| 位移 | 从右滑入 8% 屏宽 | 当前页右滑 8% 退出；上一页从左滑入 4% |
| 透明度 | fadeIn 180ms | fadeOut 120ms |
| 缩放 | 0.98 → 1 | 1 → 0.98 |
| 动画 | `spring(d=0.8, k=380)`（spatial） | 同左（反向） |
| 时长感 | 300–400ms 等效（spring 自然收敛） | 同左 |

API（`CtPageTransitions`）：
```kotlin
composable(
    route = Routes.TOOL,
    enterTransition = { CtPageTransitions.pushEnter() },
    exitTransition = { CtPageTransitions.pushExit() },
    popEnterTransition = { CtPageTransitions.popEnter() },
    popExitTransition = { CtPageTransitions.popExit() },
) { ... }
```
常量：`PUSH_SLIDE_FRACTION=0.08f`、`PUSH_FROM_SCALE=0.98f`、`PUSH_DURATION_MS=360`（spring 目标时长参考）。

---

## 4. 验证
- 预览：`ui/preview/V2DockPreviews.kt`（60–65：浅/深/滑动中跟手/深色反向/平板限宽/第二项选中）。
- 单测：`ui/components/dock/DockLogicTest.kt`（9 例：连续位置/吸附阈值/拉伸/按压/转场插值/播报）。
- 真机验收（Lead/E2E）：TalkBack 读屏顺序、滑动跟手无跳变、低端机 60fps（半透明叠层避免 RenderEffect 模糊）、手势导航条不遮挡。

## 5. 遗留/取舍
1. 未使用 RenderEffect 实时模糊（Android 12+ 可做，但低端机有掉帧风险）：采用「半透明色 + 描边 + 阴影」模拟毛玻璃，`translucent=false` 可关闭。
2. 平板 ≥840dp 仍用 NavigationRail（浮岛底栏是手机形态）；如需平板浮岛可复用同一组件并设 `showLabels=true`+限宽 480dp。
3. `CtPageTransitions` 依赖 Lead 在 MainNavHost 接线；当前主树 NavHost 默认转场不变。
