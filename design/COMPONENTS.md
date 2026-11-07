# 组件 → Compose API / material3 版本对应表
### 班级工具站 Android · `android-class-tools/design/COMPONENTS.md`

> 配套：`ANDROID17_DESIGN.md`（设计语言）、`tokens.json`（token）、`SCREENS.md`（页面方案）。
> **核心结论（已核实）**：M3 Expressive 的「新组件」不在 stable `material3 1.4.0` 中——1.4.0-beta01 起移除了所有 `ExperimentalMaterial3ExpressiveApi` 公共 API（转 1.5.0-alpha 线）。因此必须选一条轨道，或双轨并存。

---

## 0. 结论与推荐

| 轨道 | 依赖 | 能拿到什么 | 拿不到什么 | 推荐度 |
|---|---|---|---|---|
| **A. Stable 轨** | BOM `2026.09.00` → material3 `1.4.0` | `MotionScheme`（需自建 expressive 实现）、15+15 字型、`Shapes` 4/8/12/16/28、`ShortNavigationBar`、`NavigationRail`/`WideNavigationRail`、新 `SearchBar` API、`SegmentedButton`、`ModalBottomSheet`、`ListItem`、`DatePicker/TimePicker`、`SwipeToDismissBox`、`PullToRefreshBox`、`SecureTextField`、`DragHandle`；M3 Expressive 视觉可用「自定义 token + 自建 spring」还原 | ButtonGroup、SplitButton、Floating/Docked Toolbar、LoadingIndicator、FABMenu、ToggleButton、WavyProgressIndicator、MaterialShapes、Flexible app bar 的公开 Composable | ★★★★★ 默认 |
| **B. Expressive 轨** | material3 `1.5.0-alpha29`（2026-09-23，非 BOM 内，需覆盖） | 上述全部 Expressive 组件；`MaterialExpressiveTheme()`、`MotionScheme.expressive()` 公开；多数组件已去掉实验注解（LoadingIndicator、MaterialShapes 仍 `@ExperimentalMaterial3ExpressiveApi`） | 版本稳定性（alpha 线，API 可能再变）；不属于 stable BOM | ★★☆☆☆ 可选/演示 |
| **C. 混合轨（推荐落地）** | A 为默认 + 在 `ui/expressive/` 内以 gradle property 切换 B | 抽签/电费 hero、工具栏等「表达性时刻」用 B；其余 A | 构建复杂度；两套主题入口 | ★★★★☆ 若产品坚持 Expressive 组件则选 C |

**推荐**：先按 **A** 完成全部页面（视觉 95% 还原，交互一致），把 Expressive 独有组件的替代实现放进 `ui/components/`；若验收明确要求「原生 FloatingToolbar/ButtonGroup/LoadingIndicator」，再开 **C**（`-Pexpressive=true` 切到 1.5.0-alpha29，代码只改 import + 主题入口）。

---

## 1. 版本矩阵与依赖

### 1.1 已核实版本事实

| 事实 | 证据 |
|---|---|
| material3 stable **1.4.0**，发布 **2025-09-24** | Compose Material3 发布页 |
| 1.4.0 引入 `MotionScheme`（组件动效统一走 MotionScheme）；新增 SearchBar 拆分 API / `SecureTextField` / `HorizontalCenteredHeroCarousel` / text autoSize | 同上（1.4.0 release notes） |
| **1.4.0-beta01（2025-07-30）移除了所有 `ExperimentalMaterial3ExpressiveApi` / `ExperimentalMaterial3ComponentOverrideApi` 公共 API，转 1.5.0-alpha** | 同上 |
| 1.5.0-alpha 线最新 **1.5.0-alpha29（2026-09-23）**；无 1.5.0-beta/rc（截至快照） | 同上 |
| BOM **2026.09.00** → `androidx.compose.material3:material3:1.4.0`；`material3-adaptive-navigation-suite:1.5.0-beta01` | BOM 与库版本对应表 |
| material3 1.4.0 的 `MotionScheme.Companion.standard()/expressive()` 为 **internal**；1.5.0-alpha29 为 **public** | material3 1.4.0 / 1.5.0-alpha29 sources jar 源码核对 |
| 1.4.0 公开 `ShortNavigationBar`/`NavigationRail`/`WideNavigationRail`/`MotionScheme` 接口/`Typography` 的 15 个 `*Emphasized` 样式；公开 `Shapes` 只有 extraSmall/small/medium/large/extraLarge（4/8/12/16/28） | 1.4.0 sources 核对 |
| 1.4.0 **没有** ButtonGroup / FloatingToolbar / DockedToolbar / LoadingIndicator / MaterialShapes / SplitButton / ToggleButton / WavyProgressIndicator / FloatingActionButtonMenu / FlexibleTopAppBar（`MediumFlexibleTopAppBar` 等在 1.4.0 为 internal） | 1.4.0 源文件清单核对 |
| 1.5.0-alpha29 大部分 Expressive 组件已无实验注解；`LoadingIndicator`、`MaterialShapes` 仍 `@ExperimentalMaterial3ExpressiveApi` | 1.5.0-alpha29 sources 核对 |
| Compose 库（2026 年版本）要求 compileSdk 37 系列（部分 alpha 曾要求 37.1，后又移除；构建报错则装 `platforms;android-37.1`） | 发布页 1.5.0-alpha/A2UI 版本说明 |
| `material-icons` 库自 1.4.0 起不再随 material3 发布，推荐 **Material Symbols**（矢量 XML 从 fonts.google.com/icons 下载） | 1.4.0 release notes |

### 1.2 轨道 A（默认）：BOM 2026.09.00 + material3 1.4.0

```kotlin
// gradle/libs.versions.toml 或 build.gradle.kts
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.material3:material3:1.4.0")            // BOM 已含
    implementation("androidx.compose.material3:material3-window-size-class:1.4.0")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.5.0-beta01")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.10.0+")            // enableEdgeToEdge
    implementation("androidx.navigation:navigation-compose:2.9.0+")         // 预测返回集成
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0+")

    // 可选：自建 Expressive 形状 morph（material3 1.4.0 已不依赖 graphics-shapes）
    implementation("androidx.graphics:graphics-shapes:1.0.1")

    // 可选：自适应布局 API（currentWindowAdaptiveInfo）
    implementation("androidx.compose.material3.adaptive:adaptive:1.0.0")
}
```
- **不要**再引 `androidx.compose.material:material-icons-core/extended`（1.4.0 起不推荐且体积大）；图标统一用 Material Symbols 矢量 XML + `Icons` 自定义 `ImageVector`。
- 主题入口：`MaterialTheme(colorScheme = AppColorScheme, typography = AppTypography, shapes = AppShapes, motionScheme = AppExpressiveMotionScheme)`。
- `MotionScheme` 自建实现（1.4.0 唯一可行方式）：
```kotlin
@Immutable
object AppExpressiveMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spring(0.8f, 380f)
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = spring(0.6f, 800f)
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = spring(0.8f, 200f)
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = spring(1f, 1600f)
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = spring(1f, 3800f)
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = spring(1f, 800f)
}
```

### 1.3 轨道 B：material3 1.5.0-alpha29（覆盖 BOM）

```kotlin
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3:1.5.0-alpha29") // 显式覆盖 BOM 的 1.4.0
    implementation("androidx.compose.material3:material3-window-size-class:1.5.0-alpha29")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.5.0-beta01")
    implementation("androidx.graphics:graphics-shapes:1.0.1")            // MaterialShapes 依赖的几何库
}
```
主题入口：`MaterialExpressiveTheme(colorScheme = …, motionScheme = MotionScheme.expressive(), shapes = AppShapes, typography = AppTypography)`；`LoadingIndicator`/`MaterialShapes` 需 `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`。

**轨道 B 风险表**

| 风险 | 说明 | 缓解 |
|---|---|---|
| API 变更 | 1.5.0 处于 alpha，非实验 API 也可能改名/改签名 | 只在 `ui/expressive/` 引用；CI 锁版本；升级前跑截图 diff |
| 无 BOM 保障 | 覆盖 1.4.0 后，其它 compose 组件仍由 BOM 2026.09.00 管；material3 相关（window-size-class）要手动对齐 | 用 `dependencyInsight` 检查全部 material3 构件同版本 |
| 行为差异 | 1.5.0 组件默认尺寸走 Expressive 档（button 56dp 等） | 用 `tokens.json` 覆写 `ButtonDefaults`，避免全 App 视觉漂移 |
| 发布渠道 | 内部 APK/班级内分发，不是 Play 商店 | 可接受；若上架 Play 建议回 A |
| 实验注解 | LoadingIndicator/MaterialShapes 仍实验 | 已隔离在 expressive 层 |

---

## 2. 组件映射总表

图例：✅ 可用（隐式 stable）；🧪 可用但需 `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`；❌ 不存在；🚧 存在但 internal（不可调用）。

| # | 设计组件（本项目） | Compose API（Android） | material3 1.4.0 | material3 1.5.0-alpha29 | 实验性 | 1.4.0 降级方案（推荐） |
|---|---|---|---|---|---|---|
| 1 | 顶部 app bar（small/pinned） | `TopAppBar` + `TopAppBarDefaults.pinnedScrollBehavior()` | ✅ | ✅ | 否 | — |
| 2 | 中/大折叠标题 | `MediumTopAppBar` / `LargeTopAppBar` + `enterAlwaysScrollBehavior()` | ✅ | ✅ | 否 | — |
| 3 | 可变高 AppBar（Expressive） | `MediumFlexibleTopAppBar` / `LargeFlexibleTopAppBar` | 🚧 internal | ✅（公开，含 `subtitle`/`collapsedHeight`/`expandedHeight`） | 否（1.5.0） | 用 `MediumTopAppBar` + 自定义 `subtitle` 行；或 1.5.0 时切换 |
| 4 | 底部 app bar | `BottomAppBar` / `FlexibleBottomAppBar` | ✅ / 🚧 | ✅ / ✅ | 否 | `BottomAppBar` |
| 5 | 底部导航（3 目的地） | `ShortNavigationBar` + `ShortNavigationBarItem(selected,onClick,icon,label,iconPosition,colors)` | ✅（public，实测 1.4.0） | ✅ | 否 | 亦可用 baseline `NavigationBar` + `NavigationBarItem` |
| 6 | 侧边导航（medium） | `NavigationRail` + `NavigationRailItem` | ✅ | ✅ | 否 | — |
| 7 | 展开侧边导航（expanded） | `WideNavigationRail` + `WideNavigationRailState` + `WideNavigationRailItem` | ✅ | ✅ | 否 | — |
| 8 | 自适应导航脚手架 | `NavigationSuiteScaffold`（adaptive-navigation-suite） | ✅（1.5.0-beta01 artifact 搭配 1.4.0） | ✅ | 视版本 | 手写 `when(windowSizeClass)` 切换 5/6/7 |
| 9 | 主按钮/次按钮/文字按钮/危险按钮 | `Button` / `FilledTonalButton` / `OutlinedButton` / `TextButton` + `ButtonDefaults` | ✅ | ✅（默认 56dp Expressive 高度） | 否 | 1.4.0：`heightIn(min=56.dp)` + `contentPadding` 自定，得到 Expressive 视觉 |
| 10 | 连接按钮组 | `ButtonGroup` + `ButtonGroupDefaults.OverflowIndicator` | ❌ | ✅（含 `linkedButton`/`overflowIndicator`，`expandedRatio`） | 否（1.5.0） | `SingleChoiceSegmentedButtonRow`（见 #12）或 `Row`+`FilterChip` |
| 11 | 分裂按钮（导出/更多） | `SplitButtonLayout(leadingButton, trailingButton, spacing)` + `SplitButtonDefaults.*Shapes/...` | ❌ | ✅ | 否（1.5.0） | `FilledTonalButton` + `IconButton`（间距 2dp、圆角 16dp） |
| 12 | 分段选择（周/日、24h/7d/30d） | `SingleChoiceSegmentedButtonRow` + `SegmentedButton` + `SegmentedButtonDefaults.itemShape(index,count)` | ✅ | ✅ | 否 | — |
| 13 | 开关按钮（ToggleButton） | `ToggleButton(checked,onCheckedChange,buttonSize,shapes,colors,…)` | ❌ | ✅ | 否（1.5.0） | `FilterChip` 或 `FilledTonalIconToggleButton`（1.4.0 有 `IconToggleButton`） |
| 14 | 浮动工具栏 | `HorizontalFloatingToolbar(expanded,colors,contentPadding,scrollBehavior,shape,leadingContent,trailingContent,content)` / `VerticalFloatingToolbar` | ❌ | ✅ | 否（1.5.0） | 自建 `Surface`（`surfaceContainerHigh`、64dp 高、full 圆角、elev3、8dp 外边距）+ `AnimatedVisibility` + `horizontalEnterTransition` 等价实现 |
| 15 | 停靠工具栏 | `DockedToolbar` | ❌ | ✅ | 否（1.5.0） | 自建 `Surface` + `Row`（顶部 16dp 圆角，64dp 高，避让 navigationBars） |
| 16 | 悬浮按钮菜单 | `FloatingActionButtonMenu(expanded, button, content)` + `FloatingActionButtonMenuScope` | ❌ | ✅ | 否（1.5.0） | `Column` + `AnimatedVisibility` + stagger 20ms；或 `DropdownMenu` |
| 17 | FAB | `FloatingActionButton` / `SmallFloatingActionButton` / `LargeFloatingActionButton` / `ExtendedFloatingActionButton` | ✅ | ✅ | 否 | — |
| 18 | 加载指示器（Expressive 形状） | `LoadingIndicator(progress)` / `ContainedLoadingIndicator` + `LoadingIndicatorDefaults` | ❌ | ✅ | 🧪 1.5.0-alpha29 仍是 `@ExperimentalMaterial3ExpressiveApi` | `CircularProgressIndicator`（48dp/4dp）或自绘 5 点波形（effectsDefault 循环） |
| 19 | 波浪进度条 | `LinearWavyProgressIndicator` / `CircularWavyProgressIndicator` | ❌ | ✅ | 否（1.5.0；`WavyProgressIndicatorDefaults` 含 amplitude/wavelength/waveSpeed） | `LinearProgressIndicator` / `CircularProgressIndicator` |
| 20 | 线性/环形进度 | `LinearProgressIndicator` / `CircularProgressIndicator` | ✅ | ✅ | 否 | — |
| 21 | 底部弹层 | `ModalBottomSheet` + `rememberModalBottomSheetState` + `DragHandle` | ✅ | ✅（`BottomSheet` 新 API 亦可用） | 否 | — |
| 22 | 卡片 | `Card` / `ElevatedCard` / `OutlinedCard` + `CardDefaults.cardColors/shape` | ✅ | ✅ | 否 | — |
| 23 | 列表项 | `ListItem(headlineContent,supportingContent,leadingContent,trailingContent,overlineContent,colors,tonalElevation)` | ✅ | ✅（Expressive list item 变体已非实验，高度随行数） | 否 | 用 `ListItem` + 自定高度（56/72/88dp） |
| 24 | Chip | `AssistChip` / `FilterChip` / `InputChip` / `SuggestionChip` | ✅ | ✅ | 否 | — |
| 25 | 搜索栏 | `SearchBar(state,inputField,…)` / `ExpandedFullScreenSearchBar` / `ExpandedDockedSearchBar` + `rememberSearchBarState()` | ✅（1.4.0 新 API，`@OptIn(ExperimentalMaterial3Api)`） | ✅ | 1.4.0 需 OptIn | 自定义 `TextField`（56dp/full 圆角）+ 结果页 |
| 26 | 对话框 | `AlertDialog` / `BasicAlertDialog` + `DialogProperties` | ✅ | ✅ | `BasicAlertDialog` 需 OptIn | — |
| 27 | 全屏对话框（国标表） | `Dialog` + `Scaffold` 自建 | ✅ | ✅ | 否 | — |
| 28 | 日期/时间选择 | `DatePickerDialog` / `TimePicker` / `DatePicker` | ✅ | ✅（1.5 有 Expressive TimePicker 变体） | `TimePicker` 需 OptIn | — |
| 29 | 下拉/暴露菜单 | `ExposedDropdownMenuBox` / `DropdownMenu` / `DropdownMenuItem` | ✅ | ✅ | 部分需 OptIn | — |
| 30 | 开关/复选/单选/滑块 | `Switch` / `Checkbox` / `RadioButton` / `Slider` | ✅ | ✅ | 否 | — |
| 31 | Snackbar | `SnackbarHost` / `SnackbarHostState` / `Snackbar` | ✅ | ✅ | 否 | — |
| 32 | 下拉刷新 | `PullToRefreshBox` | ✅（1.4.0，需 OptIn） | ✅ | 1.4.0 需 OptIn | 自建 `SwipeRefresh` 风格或 重新加载按钮 |
| 33 | 滑动删除 | `SwipeToDismissBox` | ✅ | ✅ | 否 | — |
| 34 | 工具提示 | `TooltipBox` / `PlainTooltip` / `RichTooltip` | ✅ | ✅ | 1.4 需 OptIn | — |
| 35 | 徽章 | `Badge` / `BadgedBox` | ✅ | ✅ | 否 | — |
| 36 | 文本输入 | `OutlinedTextField` / `TextField` + `TextFieldDefaults` | ✅ | ✅ | 否 | — |
| 37 | 安全文本输入 | `SecureTextField` / `OutlinedSecureTextField` | ✅（1.4.0 新） | ✅ | 否 | 本 App 不需要（无密码输入） |
| 38 | 形状 morph（FAB↔菜单、hero） | `MaterialShapes`（`Cookie9Sided`/`Circle`/`Clover4Leaf`… + `MaterialShape.toShape(progress)`） | ❌ | ✅ | 🧪 1.5.0-alpha29 仍实验 | `androidx.graphics.shapes.RoundedPolygon` + `Morph`（graphics-shapes 1.0.1）+ `animateFloatAsState` |
| 39 | 滚动容器/列表 | `LazyColumn` / `LazyVerticalGrid` / `LazyRow` + `rememberLazyListState` | ✅（compose foundation） | ✅ | 否 | — |
| 40 | 拖拽排序 | `LazyListState.animateItem()` + `detectDragGesturesAfterLongPress` 自建 | ✅（1.4.0 `animateItem`） | ✅ | 否 | — |
| 41 | 共享元素转场 | `SharedTransitionLayout` + `Modifier.sharedBounds/sharedElement`（compose-animation 1.7+） | ✅（随 BOM） | ✅ | 否（1.7 起稳定） | — |
| 42 | 预测返回 | `PredictiveBackHandler`（activity-compose/导航） | ✅ | ✅ | 否 | `BackHandler`（无预览动画） |
| 43 | 边到边 | `enableEdgeToEdge()` + `WindowInsets.safeDrawing/ime` | ✅（activity-compose 1.8+） | ✅ | 否 | — |
| 44 | 窗口尺寸类 | `calculateWindowSizeClass(activity)`（material3-window-size-class）或 `currentWindowAdaptiveInfo()`（material3-adaptive） | ✅ | ✅ | 视 API | 手写 `LocalConfiguration` 宽度判断（不推荐） |
| 45 | 图标 | **Material Symbols** 矢量 XML（`ImageVector.vectorResource` / `painterResource`） | ✅（不随 material3 发布） | ✅ | 否 | 小程序 `images/tool-*.png` 可作占位但像素会糊（1x），建议全部换 Symbols |

---

## 3. 关键组件落地要点

### 3.1 自适应导航（#5/#6/#7/#8）
- compact：`ShortNavigationBar`（1.4.0 公开）或 `NavigationBar`；每次 `onClick` 用 `navController.navigate(route) { popUpTo(startDestination){saveState=true}; launchSingleTop=true; restoreState=true }`。
- medium：`NavigationRail`；expanded：`WideNavigationRail`（支持展开/收起，`rememberWideNavigationRailState()`）。
- 若用 `NavigationSuiteScaffold`（adaptive-navigation-suite 1.5.0-beta01），注意它内部会按尺寸切换以上布局；本项目 IA 只有 3 个目的地，手写切换更可控。
- 预测返回：使用 Navigation Compose 时自动接入系统动画；自定义 STEP 工作流见 `SCREENS.md §3`。

### 3.2 浮动工具栏 / DockedToolbar（轨道 B 才有）
- `HorizontalFloatingToolbar(expanded=false)` 与 `scrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior()` 可实现「滚动隐藏」；`expanded` 用于「展开成工具栏」的 morph（配合 FAB）。
- 本项目用途：
  - 抽签 STEP1 结果区底部：`[导出图片] [下一步：分组]`（两按钮 → 用普通 Button 行即可，不必上 floating toolbar）。
  - 工具页目录：`[搜索] [排序] [编辑首页]` 三个 icon → 适合 FloatingToolbar（1.5.0）或自建 Surface（1.4.0）。
  - 课表：`[本周] [上一周] [下一周] [日/周] [导出]` → 放 app bar actions，不放 floating toolbar（避免遮挡内容）。

### 3.3 按钮尺寸策略（跨轨道一致）
| 轨道 | `ButtonDefaults.MinHeight` | 本规范要求 |
|---|---|---|
| 1.4.0 | 40dp（`ButtonSmallTokens.ContainerHeight`，源码核实） | 主 CTA 用 `Modifier.heightIn(min = 56.dp)` + `contentPadding = PaddingValues(horizontal = 24.dp)`，视觉 56dp |
| 1.5.0-alpha29 | 56dp（`ButtonMediumTokens.ContainerHeight`） | 直接用默认；小按钮用 `ButtonDefaults.smallContainerHeight`（若公开）或显式 40dp |
> 组件尺寸数值依据：`ButtonSmallTokens=40dp`、`ButtonMediumTokens=56dp`、`ButtonLargeTokens=96dp`、`ButtonXLargeTokens=136dp`（sources jar 核对）。

### 3.4 形状与 morph
- 1.4.0：`Shapes(large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(28.dp))`；20/32/48dp 用自建 `RoundedCornerShape`。
- 需要 Expressive 多边形 morph 时（FAB↔圆、抽签容器、Loading）：
  - 轨道 A：`androidx.graphics:graphics-shapes:1.0.1` 的 `RoundedPolygon`/`Morph` + `Modifier.clip(GenericShape)`，进度由 `animateFloatAsState(spring(spatialDefault))` 驱动。
  - 轨道 B：`MaterialShapes.Cookie9Sided` 等 + `MaterialShape.toShape(progress)`；注意 `LoadingIndicatorDefaults` 提供 `indeterminateIndicatorPolygons`。
- 不要用 `AnimatedContent` 里直接换 shape 实例（会跳变）；统一走 `animateFloatAsState` 的 `cornerPercent` 或 `Morph` progress。

### 3.5 加载态
| 场景 | 轨道 A | 轨道 B |
|---|---|---|
| 首屏骨架 | `LazyColumn` 占位 + shimmer（自绘 `Brush.linearGradient` + `rememberInfiniteTransition`，effectsDefault） | 同左 |
| 按钮内联 | `CircularProgressIndicator(16dp)` + 禁用 | 同左 |
| 页面级长加载 | `CircularProgressIndicator(48dp)` | `LoadingIndicator`（48dp 容器） |
| 数字/趋势加载 | 占位块 + 保留旧值（不闪 0） | 同左 |

### 3.6 搜索（#25）
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
val state = rememberSearchBarState()
SearchBar(state = state, inputField = { SearchBarDefaults.InputField(...) })
// 展开：ExpandedFullScreenSearchBar(state, ...) / ExpandedDockedSearchBar(state, ...)
```
- 1.4.0 中 `SearchBarState` 与展开视图为独立 Composable（release notes 明确）；不要再用旧的 `DockedSearchBar`/`SearchBar` 二合一 API（已拆分）。
- 本项目用「全部工具」页顶部搜索（工具名/描述），不进入搜索页时可保持折叠条。

### 3.7 列表与拖拽（#23/#40）
- 1.4.0 `LazyColumn` 的 `Modifier.animateItem()` 已稳定；拖拽排序需自建：
  - `detectDragGesturesAfterLongPress`（长按 350ms，与 web/小程序一致）；
  - 拖动中 `graphicsLayer { translationY = offset }`（**不要**每帧重组），落点用「行高中线 + 连续 2 帧一致」判定（参考 web 全部页拖动 v2 的抗抽动逻辑）；
  - 松手 `animateItem` + spatialFast 吸附；列表自动滚动在 `pointerInput` 里用 `scrollBy`。
- `ListItem` 在 1.5.0 有 Expressive 变体（高度随文字行数，非实验），但 1.4.0 的高固定 56/72/88dp 更可控。

### 3.8 导出卡（Android Canvas）
不属于 material3 组件，但需要 Canvas/Compose 绘制：
- 使用 `Canvas` + `drawText(TextMeasurer)`（Compose `rememberTextMeasurer` + `drawText`，支持 `TextStyle`），比 `nativeCanvas` 简单；
- 版式常量严格按 `web/tools/tests/export_card_v2_spec.md`（W=760 逻辑px、DPR=2、主卡 22dp 圆角、水印 -20° alpha 0.03、页脚编号）；
- 色板用 `tokens.json` 的 brand/brandInk 系（导出卡固定浅色，**不随深色主题变化**）；
- 保存：`MediaStore`（Android 10+，无需权限）→ `Pictures/班级工具/`；分享：`FileProvider` + `Intent.ACTION_SEND`；文件名 `<类型>_<YYYYMMDD-HHMM>_<参数>.png`。
- 文字排版用 `TextMeasurer` 的 `measure(AnnotatedString, style, constraints)` 逐段测宽实现「超宽降字号/截断」。

---

## 4. 与后端/数据层的边界（组件层不关心）
- 所有组件只接收 UI state（`Loading/Empty/Error/Content` sealed class），不直接调用网络；
- 同步状态（已同步/离线/待补发）由 `SyncStatusChip` 统一呈现（`AssistChip` + `success/warning/info` 色）；
- 敏感数据（token/名单）不进 Compose state 打印、不进导出卡。

---

## 5. 变更与维护规则
1. 升级 material3 前先跑组件截图基线（Pixel 360dp/Pixel Tablet 1280dp × 明暗 = 4 组）；
2. 每次升级检查 3 件事：`MotionScheme` 数值、`Typographic` default、组件默认高度（1.4→1.5 按钮 40→56dp 会导致全 App 视觉漂移）；
3. 实验 API 只允许出现在 `ui/expressive/` 包；用 `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` 集中注解，禁止散落业务代码；
4. 若从轨道 B 回退 A，删除 `MaterialExpressiveTheme` 入口并恢复 `MaterialTheme(motionScheme = AppExpressiveMotionScheme)`，业务页面零改动（组件替换层已隔离）。

---

## 6. 来源
| 内容 | 来源 |
|---|---|
| material3 1.4.0 发布（2025-09-24）、Expressive API 移除（1.4.0-beta01）、1.5.0-alpha 系列（最新 alpha29 = 2026-09-23） | https://developer.android.google.cn/jetpack/androidx/releases/compose-material3 |
| BOM 2026.09.00 → material3 1.4.0 | https://developer.android.google.cn/jetpack/compose/bom/bom-mapping |
| 1.4.0/1.5.0-alpha29 API 与 token 数值 | Google Maven sources jar：`https://dl.google.com/dl/android/maven2/androidx/compose/material3/material3-android/1.4.0/material3-android-1.4.0-sources.jar`、`.../1.5.0-alpha29/material3-android-1.5.0-alpha29-sources.jar` |
| 组件签名（ShortNavigationBarItem/SearchBar/ListItem/FloatingToolbar/ButtonGroup/SplitButtonLayout/LoadingIndicator/FABMenu/ToggleButton/MaterialExpressiveTheme 等） | 同上 sources jar 源码 |
| Material Symbols 图标 | https://fonts.google.com/icons |
| 动效数值 | https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/ExpressiveMotionTokens.kt |
