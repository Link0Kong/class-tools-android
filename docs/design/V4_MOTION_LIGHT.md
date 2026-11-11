# `V4_MOTION_LIGHT.md` — 光场 / 液态玻璃动效规格（ColorOS 光场 × M3 Expressive × Compose）

> 目的：把「光随操作流动」的观感翻译成可实现的 Compose 动效规格，映射到现有 `CtMotion` / `CtGlass` / `CtFloatingDock`，并提供性能与无障碍底线。
> 相关：`docs/design/V4_RESEARCH_AND_DEMOS.md`（Lead 的调研与底栏真实模糊实现路径）、`docs/design/V3_GLOBAL_STYLE.md`（玻璃层级/参数）、`docs/design/V4_UX_AUDIT.md`（G09/G10/G22 动效问题）、`docs/design/V4_NEW_FEATURES_UI.md`（投票/名单/电费动效）。
> 外部参考（已核对摘要）：
> - ColorOS 16「光场设计」：图标液态玻璃通透材质、**边缘光影随操作流动**、文件夹打开「**水滴击湖三段式**」曲线、壁纸主色浸染、夜间亮度曲线降低 12% —— [PChome 报道](https://article.pchome.net/news/7142.html)、[IT之家](https://m.ithome.com/html/888281.htm)；
> - Material 3 Expressive Motion：spring physics（damping/stiffness 复合 token，spatial/effects × fast/default/slow，**可中断、速度感知**）—— [M3 Motion](https://m3.material.io/styles/motion/overview/specs)、[M3 Expressive Motion blog](https://m3.material.io/blog/m3-expressive-motion-theming)；
> - Compose 动画性能：**phase deferral** —— 动画属性不要在 composition 读取，行内用 `graphicsLayer{}` / lambda modifier，避免每帧重组 —— [官方参考](https://developer.android.com/agents/skills/jetpack-compose/jetpack-compose-performance/references/animation)；
> - Android Liquid Glass 实现参考（真实背景模糊 + 折射 + 放大）：[SidZadaun02/AppleLiquidGlassForAndroid](https://github.com/SidZadaun02/AppleLiquidGlassForAndroid)、[prismalagsl](https://github.com/styropyr0/prismalagsl/blob/163376d4449e915bc2ebe8f4d12c00b289647bb4/README.md)。

---

## 1. 三条设计原则

1. **光跟着操作走（Light follows interaction）**：高光角度/镜面反射位置由触摸点或滚动方向驱动，松手后 spring 回正；不做无意义的环境光摇摆（省电、防眩晕）。
2. **运动用物理，不用曲线硬切**：空间位置/缩放/形变用 spring（`CtMotion.spatial*`），颜色/透明度用 effects spring；所有动画可中断（手势接管时无跳变）。
3. **每帧只画，不重组**：动画值只在 `graphicsLayer{}` / `drawWithCache` / lambda modifier 中读取；列表滚动/页面不可见时暂停；全页玻璃层 ≤2 层。

---

## 2. 光场元素与实现映射

| 光场元素 | 观感目标 | Compose 实现 | 规格 |
|---|---|---|---|
| **L1 边缘光（Edge light）** | 卡片/浮岛边缘有一条随操作流动的高光 | `Modifier.ctGlassRefraction` 的 1dp 亮边 + 顶部 specular 渐变；拖动时按方向旋转渐变角度 | 角度 = 触摸位移方向（-45°..45°），spring 平滑；静止回 0°；alpha 0.3–0.55 |
| **L2 镜面高光（Specular follow）** | 光点跟随手指在玻璃上移动 | `drawWithCache` + `Brush.radialGradient(center = pointer)`；指针位置由 `pointerInput` 写入 `mutableStateOf<Offset>`，**仅在需要时读**（lambda modifier） | 高光半径 = 卡片短边 40%；alpha 0.18–0.35；松手 240ms 淡出；仅 hero/玻璃卡启用，列表项禁用 |
| **L3 水滴击湖（Water-drop ripple）** | 点击/切换时以触点为中心扩散一圈光，三段式 | 自定义 `Modifier.drawWithContent`: ①impact：圆环 scale 0.6→1.0，alpha 0.55→0，90ms（fast spatial）；②ripple：半径 ×2.2，alpha 0.25→0，effectsSlow；③settle：整体 spring 回位 | 总时长 ≈ 320ms；触点由点击位置给出；同一点击 500ms 内只触发一次（防连击） |
| **L4 壁纸浸染（Tint infusion）** | 页面背景有品牌色/壁纸色薄雾 | 现有 `ExpressiveBackground` + `CtGlass.tint`；可加 1 个极低 alpha radial 渐变 | 不新增图片资源；深色用亮品牌色 8–10% alpha |
| **L5 夜间亮度（Night dim）** | 夜间整体亮度降低、玻璃更暗 | 跟随系统深色 + 手动「深色」开关；页面亮度不做强制覆盖（系统亮度优先） | 深色下玻璃填充 alpha 降低（已有 token） |
| **L6 动态景深（Depth parallax）** | 滚动时前景/背景轻微视差 | 仅 hero 背景 0.02–0.04 视差（`graphicsLayer translationY = scroll * -0.03f`）；列表项禁用 | 可选；低端机可关 |

> 本轮不追求真实折射（RenderEffect）全站化：**Haze 只在浮岛/hero 两层**（Lead 已接 Haze），其余用「半透明 + 渐变高光」模拟。

---

## 3. 动效目录（token 化）

以下 `token` 全部映射到现有 `CtMotion`，新增部分建议加在 `ui/theme/Motion.kt`（`CtMotion` 内）：

| 名称 | 触发 | 变化 | 规格（Compose） | 时长感 | Haptic | 无障碍降级 |
|---|---|---|---|---|---|---|
| `CtMotion.dockPill` | 底栏切换/跟手 | 药丸 x 位移 + 拉伸 | 已实现：跟手 raw + `spatialFast` 吸附；拉伸 1+0.08·\|offset\| | 200ms 等效 | 轻触 | 直接跳到位置（无吸附动画） |
| `CtMotion.tabPager` | 顶层 Tab 滑动 | 页面 8% 位移 + alpha .72 + scale .98 | 已实现（`CtDockLogic.pageTransform`） | 跟手 | — | 关闭滑动（仅点击） |
| `CtMotion.glassPress` | 卡片/按钮按下 | scale 1→0.97、边缘光增强、高光出现 | `graphicsLayer` + `effectsFast` | 120ms | 轻触 | 仅 alpha 变化 |
| `CtMotion.glassRipple` | 点击玻璃卡/底栏 item | 触点扩散光（L3） | 见 §2 L3 | 320ms | 轻触 | 关闭扩散，仅选中态 |
| `CtMotion.specularFollow` | 手指在 hero 上拖动 | 高光中心跟手 | `pointerInput` → `mutableStateOf<Offset>`，lambda modifier 绘制 | 60fps | — | 关闭 |
| `CtMotion.edgeGlow` | 横向滑动/拖动 | 边缘光角度随方向 ±45° | spring smoothing（`spatialFast`） | 180ms | — | 关闭 |
| `CtMotion.pullRefresh` | 下拉刷新 | 指示器拉伸/回弹 | `PullToRefreshBox` 自带 + 自定义容器 scale | 系统 | — | 已有系统动效 |
| `CtMotion.stateCrossfade` | 筛选/数据刷新 | 内容 crossfade + 1–2dp 上移 | `Crossfade`/`AnimatedContent` + `effects` | 200ms | — | 直接切换 |
| `CtMotion.cardInsert` | 列表新增 | alpha 0→1 + scale .98→1 | `animateItemPlacement` + `fadeIn` | 220ms | — | 直接出现 |
| `CtMotion.countdownPulse` | 截止 <10 分钟 | 倒计时文字轻微呼吸（alpha 0.75↔1） | 无限动画仅在前台；间隔 2s | 2s 循环 | — | 关闭循环，静态红字 |
| `CtMotion.successPulse` | 提交成功/保存成功 | 图标 scale 1→1.15→1 + 光环 | `spatialFast` 两段 | 300ms | 轻触 | 仅淡入 |
| `CtMotion.errorShake` | 表单错误/校验失败 | 水平 ±4dp，2 次 | `spatialFast` + `keyframes(0,-1,1,-1,0)` | 240ms | 双震 | 不抖动，仅红色描边 |
| `CtMotion.pagePush` | 工具页 push | 8% slide + fade + 0.98→1 | 已实现 `CtPageTransitions` | 360ms | — | 仅淡入 |
| `CtMotion.pagePop` | 返回 | 反向 | 同上 | 300ms | — | 仅淡出 |
| `CtMotion.loadingShimmer` | 骨架屏 | 骨架渐变扫光 | 仅骨架组件；列表滚动时暂停 | 1.2s 循环 | — | 静态灰块 |
| `CtMotion.photoZoom` | 图片查看 | 缩略图→全屏共享元素 | 共享元素（`SharedTransitionLayout`，Compose 1.7+）或 fade+scale 0.95→1 | 300ms | — | 淡入 |

**参数映射（与现有 CtMotion 数值）**：
- `spatialFast` = spring(d=0.6, k=800) ≈ 150–220ms；
- `spatial` = spring(d=0.8, k=380) ≈ 280–360ms；
- `spatialSlow` = spring(d=0.8, k=200) ≈ 450–600ms；
- `effects` = spring(1.0, 1600) ≈ 180–220ms；`effectsFast` ≈ 110–140ms；`effectsSlow` ≈ 320–380ms；
- 曲线等价（M3 提供）：Expressive fast spatial ≈ cubic-bezier(.42,1.67,.21,.90)/350ms；default ≈ (.38,1.2,…)；若必须用 tween 才用等价曲线（不推荐用于可中断交互）。

---

## 4. 性能底线（必须遵守）

1. **Phase deferral**：不要 `val x by animateFloatAsState(...)` 后在 composition 里参与布局；用 `Modifier.graphicsLayer { translationX = x }`、`drawWithCache { … }` 或 `Modifier.offset { IntOffset(...) }`，避免每帧重组。
2. **玻璃层数 ≤2/页**：hero（L2）+ 浮岛（L3）；列表项一律不透明卡（L1）。
3. **Haze/RenderEffect 限制**：仅浮岛与 hero；滚动列表时若 overlay 仍模糊，需 `blurEnabled = false` 降级；Android 12 以下自动退化半透明。
4. **不可见即暂停**：轮询/呼吸/ shimmer 在 `Lifecycle.RESUMED` 且页面可见时才运行；切后台停。
5. **动画对象缓存**：`remember` animation/Animatable；手势期间用 `snapTo`/状态直写，松手再 spring。
6. **避免 layout-traversal 动画**：高度/宽度变化用 `animateContentSize`（已有限制）或改尺寸 transform；列表用 `animateItemPlacement`。
7. **低端机降级**：`Settings.Global.ANIMATOR_DURATION_SCALE == 0` 或设备 API < 31 → 关闭 ripple/specular/parallax，仅保留 fade ≤120ms。
8. **一次性检查**：每加入一个光场效果，用 `scripts/render_previews.ps1` 出预览并在真机/模拟器 Perfetto 看无掉帧（目标 60fps，120Hz 设备 120fps）。

---

## 5. 无障碍与「减少动效」

- 读取系统动画时长倍率（Compose `LocalMotionDurationScale` 或 `Settings.Global.ANIMATOR_DURATION_SCALE`）；倍率为 0 → 全部动画降为 `fade` ≤120ms 或直接切换，**不运行** ripple/shimmer/countdownPulse。
- 动效不承载唯一信息：成功/失败同时有文字 + 图标；倒计时同时有文字。
- 触觉可选（系统关闭时静默）；不滥用连续震动。
- 光场高光不闪烁（频率 ≤1 次/秒），不对文字区域叠加高 alpha 光斑。

---

## 6. 落地顺序与验收

| 阶段 | 内容 | 验收 |
|---|---|---|
| M1（v4.0） | 底栏光场（L1 边缘光 + 按压）、Tab/push 统一 token、状态 crossfade、危险操作反馈、`CtMotion` 新 token 加入 `Motion.kt` | 预览 110–116 系列 + 新增 4 张动效静帧；无 >400ms 阻塞动画 |
| M2（v4.1） | 玻璃卡 specularFollow + ripple（仅 hero/底栏）、投票/名单状态过渡、countdown pulse（前台） | 真机 60fps；减少动效开启后全部降级 |
| M3（v4.x） | 共享元素图片查看、滚动景深（可选）、夜间亮度曲线（跟随系统） | Perfetto 无 jank；可配置开关 |

## 7. 需 Lead / 设计确认
1. 光场强度：`specularFollow` 与 `glassRipple` 是否按「全局开启/仅 hero+底栏」？（推荐仅 hero+底栏，列表禁用）
2. 「夜间亮度曲线降低 12%」是否要在 App 内叠加暗化（会影响截图/对比度）还是完全跟随系统？（推荐完全跟随系统，页面不强制）
3. `CtMotion` 新 token 是否允许改 `ui/theme/Motion.kt`（在 v3 冻结后需 Lead 同意；否则放 `ui/components/glass` 私有常量）。
4. 共享元素（SharedTransitionLayout）需 Compose 版本确认；若版本不支持则用 fade+scale 替代。

