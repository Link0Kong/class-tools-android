# Android 17 / Material 3 Expressive 设计语言规格
### 班级工具站 Android 原生 App · `android-class-tools/design/ANDROID17_DESIGN.md`

| 项 | 值 |
|---|---|
| 文档版本 | 1.0（2026-11-07） |
| 目标平台 | Android 17（API 37），最低 minSdk 26（Android 8.0），targetSdk 37 |
| UI 技术栈 | Jetpack Compose + androidx.compose.material3 |
| 设计基调 | 品牌暖白/雾青/灰蓝/暖沙 + M3 Expressive（表达性、自适应、有生命感的动效、克制的玻璃/模糊） |
| 配套文件 | `tokens.json`（机器可读 token）、`COMPONENTS.md`（Compose API/版本矩阵）、`SCREENS.md`（逐页方案）、`README.md`（索引） |

**事实分级图例**
- ✅ **已核实**：有官方源码/官方文档/官方发布页 URL 佐证（见 §10）。
- 🟡 **设计建议**：本规格的设计决策，供实现选用；可按产品取舍。
- ⚠️ **未核实**：检索受限或仅有媒体转述，给出保守方案；实现前需复核。

> 文中所有 Material 3 数值（spring、字号、圆角、组件高度）优先取自 **androidx 官方源码与发布页**，不是凭记忆；版本差异已标注。

---

## 0. TL;DR —— 10 条可执行结论

1. **版本基线**：`material3 1.4.0`（stable，2025-09-24 发布）是当前稳定版本；BOM `2026.09.00` 映射 material3 1.4.0。M3 Expressive **独立新组件**（ButtonGroup / SplitButton / FloatingToolbar / LoadingIndicator / FABMenu / ToggleButton / WavyProgress / MaterialShapes / Flexible app bar）**不在 1.4.0**，在 `1.5.0-alpha29`（2026-09-23，最新 alpha）中，且大部分已转正、LoadingIndicator 与 MaterialShapes 仍为实验 API。→ 详见 `COMPONENTS.md`，1.4.0 与 1.5.0-alpha29 两条轨道。
2. **1.4.0 已可用于「表达性观感」**：`MotionScheme` 接口、15 个 baseline + 15 个 emphasized 字型、`ShortNavigationBar`/`WideNavigationRail`、新 SearchBar API 均可用；但 `MotionScheme.expressive()` 在 1.4.0 是 `internal`，需**自建 ExpressiveMotionScheme**（数值已给出，可直接抄）。
3. **动效采用 M3 处方 spring**：spatial fast `0.6/800`、default `0.8/380`、slow `0.8/200`；effects fast `1.0/3800`、default `1.0/1600`、slow `1.0/800`（dampingRatio/stiffness，mass=1）。✅ 源码核实。
4. **品牌色不能直接当 primary 承载白字**：`#4E9082` 与白字对比度仅 **3.73:1**（<4.5 AA）。方案：M3 `primary` 用同色相 HCT tone-40 `#24695C`（白字 6.46:1 AA）；`#4E9082` 保留为 **brandMist 强调色**，只用于大标题/图示/数据可视化等 ≥3:1 场景。✅ 对比度实测。
5. **浅/深色两套 token 已产出**（HCT tonal palette + M3 标准 role→tone 映射），`tokens.json` 可直接生成 Compose `ColorScheme`；所有正文配对 ≥4.5:1。
6. **字体**：Latin/数字用 **Roboto Flex 可变字体**（wght/wdth/GRAD/opsz 轴），中文**不打包**、走系统字体回退（Noto Sans CJK/思源黑体）；强调字阶用官方 emphasized 数值（BodyLargeEmphasized 16sp/24/w500/0.15，LabelLargeEmphasized 14sp/20/w700…）。
7. **形状**：4/8/12/16/28 为公开 scale；卡片 **16dp**（= web 站 16px 圆角语义），对话框/底表 28dp，按钮/搜索栏/Search 全圆角，FAB 16/28dp；需要 20/32/48dp 的 expressive 形状时用 1.5.0 或自建 `RoundedCornerShape`。
8. **Android 17 硬约束**：targetSdk 37 时，**sw>600dp 大屏设备忽略** `screenOrientation`/`setRequestedOrientation`/`resizeableActivity`/`min-maxAspectRatio`；必须支持横竖屏与可调整窗口（✅ 官方博客 2026-02-13）。边到边（Android 15 target 35 起强制，target 36 起禁用退出，17 延续）与预测返回（API 33 引入，target 36+ 默认系统动画）必须显式适配。
9. **玻璃/模糊克制使用**：Android 17 系统层（通知栏/控制中心）用细腻高斯模糊/毛玻璃（媒体转述，⚠️ 官方设计规格未逐条公开）；App 内只在 **顶部工具栏折叠后、底表/Dialog 背板、1 个装饰性 hero** 三处出现，`Modifier.blur` 半径 ≤24dp、仅 API 31+、低内存设备自动降级为半透明 scrim。
10. **无障碍硬线**：触控目标 ≥48dp（Expressive 组件 56/64dp 天然达标）、正文 4.5:1、大字号 3:1、字体缩放 200% 不截断、全量 TalkBack 描述、尊重系统「移除动画」。

---

## 1. 设计原则

| # | 原则 | 落地要求（可检查） |
|---|---|---|
| P1 | **表达性有主次（Expressive, not everywhere）** | 每个屏幕最多 1 个「表达性时刻」（大标题/形状变形/FAB menu/数字滚动）；其余交互用 standard 动效与静态表面。禁止一屏 3 个以上 spring overshoot 动画。 |
| P2 | **自适应优先（Adaptive first）** | 所有页面在 compact(<600dp)/medium(600–839dp)/expanded(≥840dp) 三档下都有明确布局，不允许「手机布局按比例放大」。Android 17 target 37 已不允许锁定方向/尺寸。 |
| P3 | **有生命感的动效（Motion with physics）** | 位置/尺寸/形状变化走 spatial spring；颜色/透明度走 effects spring（临界阻尼，不 overshoot）；能手势驱动的（返回、滑动、拖动排序）不用固定时长动画。 |
| P4 | **层级靠色调，不靠重阴影** | 表面层级主要用 surfaceContainer* 色调 + 1dp outlineVariant 描边；阴影只用于 FAB、悬浮工具栏、Dialog/Sheet、拖拽幽灵。禁止「每张卡片都投影」。 |
| P5 | **玻璃=状态提示，不是装饰** | 模糊只用来表达「浮在内容之上且内容仍可感知」（折叠 app bar、底表背板）；静态卡片不做毛玻璃。 |
| P6 | **数据可信（Trust & sync visibility）** | 每个同步类页面有明确的 同步中/已同步/离线/待补发 状态位；空态必须给出「下一步动作」；错误态必须可重试且不丢本机数据。 |
| P7 | **一屏一任务（Tool-first）** | 站点是「工具站」不是内容社区：进入工具后主操作按钮（抽签/分组/保存）在拇指区且始终可达；说明文字用标签/占位符，不写解释段落（继承 web 设计红线）。 |
| P8 | **隐私可见** | 页面只显示姓名/角色，不显示学号；电费关注宿舍只显示规范楼栋+房间号；导出卡不含手机号/学号/宿舍号（继承 web/AGENTS.md 红线）。 |

**品牌延续映射（web/小程序 → Android）**

| 站点元素 | Web/小程序实现 | Android 对应 |
|---|---|---|
| 暖白底 `#F7F8F8` | body background | `ColorScheme.surface`（tone98 = `#F8F9F9`）+ brandWarmWhite 用于导出/分享卡背景 |
| 雾青 `#4E9082` | 主 CTA/选中/结果数字 | `brandMist`（hero/图示/大数字）+ M3 `primary #24695C`（可承载白字的 CTA） |
| 灰蓝 `#6E8CA8` | 次级强调/水量/信息 | `secondary` 色系（tone40 `#43617C` / tone80 `#ABCAE8`） |
| 暖沙 `#B98F63` | 暖色标签/电费提醒/参数底 | `tertiary` 色系（tone40 `#7B5730` / tone90 `#FFDCBC`） |
| 卡片圆角 16px | `.card{border-radius:16px}` | `MaterialTheme.shapes.large = 16dp` |
| 顶部 3/4 格工具切换（无底部栏） | web `.tabs` 5 列 grid | **原生采用 M3 自适应导航**（compact=ShortNavigationBar / medium=NavigationRail / expanded=WideNavigationRail），见 §6.1；这是对 web 红线的有意偏离（web 红线只约束网页/小程序） |
| 液体滑块/跟随手势 | CSS+JS | Compose `animateFloatAsState` + `AnimatedContent`（弹性）+ 手势拖动 |

---

## 2. 色彩体系

### 2.1 动态取色策略（三层，默认第 1 层）

| 层 | 机制 | 适用 | 决策 |
|---|---|---|---|
| L1 **品牌固定方案（默认）** | 由品牌 4 色 HCT tonal palette 生成 fixed light/dark ColorScheme（本文件 §2.2/2.3） | 所有用户、所有设备 | ✅ 默认开启。保证「班级工具」跨端一致，QA 可复现 |
| L2 **品牌种子动态（可选开关）** | 用户开启「跟随壁纸」后：`dynamicLightColorScheme(context)`（API 31+）的 surface/neutrals 替换品牌 neutral；primary/secondary/tertiary 仍用品牌值（`colorScheme.copy(primary=…, onPrimary=…, …)`） | Android 12+、设置→外观 | 🟡 推荐后续版本提供；默认关 |
| L3 **完全动态色** | 全部角色用 `dynamicLightColorScheme/dynamicDarkColorScheme` | 仅作为 L2 降级 | 🟡 不作为默认：会丢失品牌识别，且无法保证班级工具的功能色（电费/水量/错误）语义 |
| 降级 | API <31 无动态色 | — | 自动回落到 L1（同一套 token，无需分支） |

**生成方法（可复现）**：seed `#4E9082`（primary）、`#6E8CA8`（secondary）、`#B98F63`（tertiary）、`#F7F8F8`（neutral）→ Material Color Utilities HCT `TonalPalette` → M3 标准 role→tone 映射（primary=40/onPrimary=100/container=90/onContainer=10/…/surface=Neutral98/…；dark 镜像 tone 80/20/30/90，surface=Neutral6 等）。✅ 映射与 material3 1.4.0 `ColorLightTokens.kt` 一致（源码核实）。
- 🔎 备注：`#F7F8F8` 的 HCT chroma 仅 3.4（近乎中性），所以 surface 系是极淡的冷灰绿而不是明显米色；小程序里偏暖的 `#F4F0E6` 属于小程序品牌色，**Android 端暖沙感由 tertiary 容器（`#FFDCBC` 系）承担**，不再染整页底色。

### 2.2 Light 色彩 token（`ColorScheme` 全量）

| Compose 字段 | Hex | 用途/备注 |
|---|---|---|
| primary | `#24695C` | 主 CTA 填充、选中态、进度 |
| onPrimary | `#FFFFFF` | 6.46:1 AA |
| primaryContainer | `#ACF0DF` | 次级按钮/选中容器/工具卡强调底 |
| onPrimaryContainer | `#00201B` | 13.32:1 AAA |
| inversePrimary | `#90D3C4` | 反向表面上的主色 |
| secondary | `#43617C` | 次级动作/水量/信息 |
| onSecondary | `#FFFFFF` | 6.48:1 |
| secondaryContainer | `#CCE5FF` | 信息卡/水量卡底 |
| onSecondaryContainer | `#001D31` | 13.29:1 |
| tertiary | `#7B5730` | 暖沙强调（参数区、提醒、徽章） |
| onTertiary | `#FFFFFF` | 6.47:1 |
| tertiaryContainer | `#FFDCBC` | 暖沙容器（电费提醒、抽签「已剔除」） |
| onTertiaryContainer | `#2C1700` | 13.19:1 |
| error | `#9A433E` | 危险按钮/错误文字 |
| onError | `#FFFFFF` | 6.47:1 |
| errorContainer | `#FFDAD7` | 错误提示底 |
| onErrorContainer | `#400104` | 13.28:1 |
| surface | `#F8F9F9` | 页面底色（≈品牌暖白） |
| onSurface | `#191C1C` | 正文 16.26:1 AAA |
| surfaceVariant | `#D7E6E2` | 输入框/搜索栏底（M3 语义，非 deprecated 用途） |
| onSurfaceVariant | `#3C4947` | 次要文字 8.90:1 |
| surfaceTint | `#24695C` | 高度染色（M3 elevation tint） |
| outline | `#6C7A77` | 输入框描边、分隔线（强） |
| outlineVariant | `#BBCAC6` | 卡片 1dp 描边、表格线 |
| inverseSurface | `#2E3131` | Tooltip/Snackbar 底 |
| inverseOnSurface | `#F0F1F1` | 上述底上的字 |
| scrim | `#000000` | 遮罩（38% alpha 使用） |
| surfaceDim | `#D9DADA` | 非激活区域 |
| surfaceBright | `#F8F9F9` | — |
| surfaceContainerLowest | `#FFFFFF` | 卡片（在有底色页上） |
| surfaceContainerLow | `#F3F4F4` | 列表底/输入区 |
| surfaceContainer | `#EDEEEE` | 默认卡片底（M3 filled card） |
| surfaceContainerHigh | `#E7E8E8` | 悬浮工具栏底 |
| surfaceContainerHighest | `#E1E3E3` | 强调容器/搜索栏 |
| primaryFixed / primaryFixedDim | `#ACF0DF` / `#90D3C4` | 不随明暗变化的品牌固定色 |
| onPrimaryFixed / onPrimaryFixedVariant | `#00201B` / `#005045` | — |
| secondaryFixed / secondaryFixedDim | `#CCE5FF` / `#ABCAE8` | — |
| onSecondaryFixed / onSecondaryFixedVariant | `#001D31` / `#2B4963` | — |
| tertiaryFixed / tertiaryFixedDim | `#FFDCBC` / `#EDBE8E` | — |
| onTertiaryFixed / onTertiaryFixedVariant | `#2C1700` / `#60401B` | — |
| background / onBackground | `#F8F9F9` / `#191C1C` | 与 surface 相同（M3 1.4 仍保留字段） |

### 2.3 Dark 色彩 token

| Compose 字段 | Hex | 备注 |
|---|---|---|
| primary | `#90D3C4` | 7.66:1 |
| onPrimary | `#00382F` | — |
| primaryContainer | `#005045` | — |
| onPrimaryContainer | `#ACF0DF` | 7.29:1 |
| inversePrimary | `#24695C` | — |
| secondary | `#ABCAE8` | — |
| onSecondary | `#12334B` | — |
| secondaryContainer | `#2B4963` | — |
| onSecondaryContainer | `#CCE5FF` | — |
| tertiary | `#EDBE8E` | — |
| onTertiary | `#462A07` | — |
| tertiaryContainer | `#60401B` | — |
| onTertiaryContainer | `#FFDCBC` | — |
| error | `#FFB3AD` | — |
| onError | `#5E1615` | — |
| errorContainer | `#7C2C29` | — |
| onErrorContainer | `#FFDAD7` | — |
| surface | `#111414` | 深色页面底（暖黑白） |
| onSurface | `#E1E3E3` | 14.37:1 |
| surfaceVariant | `#3C4947` | — |
| onSurfaceVariant | `#BBCAC6` | 10.91:1 |
| surfaceTint | `#90D3C4` | — |
| outline | `#869491` | — |
| outlineVariant | `#3C4947` | — |
| inverseSurface | `#E1E3E3` | — |
| inverseOnSurface | `#2E3131` | — |
| scrim | `#000000` | — |
| surfaceDim / surfaceBright | `#111414` / `#373A3A` | — |
| surfaceContainerLowest … Highest | `#0C0F0F` / `#191C1C` / `#1D2020` / `#282A2B` / `#2E3131` | 层级随容器升高逐级变亮 |
| primaryFixed 系列 | 同 Light | M3 规定 fixed 角色明暗同值 |
| secondaryFixed / tertiaryFixed 系列 | 同 Light | — |

### 2.4 品牌强调色与语义扩展（自定义 token，`BrandColors`）

| Token | Light | Dark | 用途 | 约束 |
|---|---|---|---|---|
| brandMist | `#4E9082` | `#4E9082` | 品牌主视觉：hero 大数字、抽签结果数字、图示、图表主色、品牌圆点 | **禁止**用于白字按钮/小正文（对白 3.73:1，仅够大字号/图形 3:1） |
| brandBlue | `#6E8CA8` | `#6E8CA8` | 品牌次视觉、水量/信息、图表第二序列 | 同上（对白 3.51:1），仅大字号/图形 |
| brandSand | `#B98F63` | `#B98F63` | 品牌暖沙、参数区/徽章/图表第三序列 | 对深色 `#2C1700` 5.83:1 |
| brandWarmWhite | `#F7F8F8` | — | 导出/分享卡底、品牌页脚 | — |
| brandInk | `#23302E` | — | 导出卡文字（与 web 导出卡 v2 一致） | — |
| brandInk2 / brandInk3 | `#5A6A67` / `#8C9A97` | — | 导出卡次要/三级文字 | 仅导出卡用 |
| success | `#3E7B5E` | `#8FCFB4` | 已同步/已完成/到课 | 对 surface 4.74:1 AA |
| warning | `#8A6430` | `#F0C88E` | 低电提醒/待补发/慢更新 | 与 tertiary 区分：warning 用于「需要动作」 |
| info | `#4C6B85` | `#A8C6E2` | 同步中/离线/说明 | 与 secondary 同色相 |
| dangerSoft | `#C0605A` | `#F0B4AA` | 弱危险（web danger 色），仅图标/小标签 | 正文危险用 `error` |
| powerPositive | `#3E7B5E` | `#8FCFB4` | 电费：电量充足 | — |
| powerWarn | `#B98F63` | `#EDBE8E` | 电费：偏低 | — |
| powerLow | `#C0605A` | `#F0B4AA` | 电费：低电 | — |
| waterAccent | `#4C6B85` | `#A8C6E2` | 水量：蓝色系 | — |

### 2.5 玻璃/模糊材质 token（自定义 `GlassTokens`）

| Token | Light | Dark | 说明 |
|---|---|---|---|
| glassContainer | `rgba(248,249,249,0.72)` | `rgba(25,28,28,0.66)` | 毛玻璃底（叠加在模糊之上） |
| glassBorder | `rgba(255,255,255,0.55)` | `rgba(255,255,255,0.12)` | 1dp 高光描边 |
| glassTint | `rgba(78,144,130,0.08)` | `rgba(144,211,196,0.10)` | 品牌色调薄涂层 |
| glassScrim | `rgba(0,0,0,0.38)` | `rgba(0,0,0,0.50)` | Dialog/Sheet 背板遮罩 |
| blurRadiusContent | 16dp | 16dp | 折叠 app bar/悬浮工具条 |
| blurRadiusModal | 24dp | 24dp | Dialog/ModalBottomSheet 背景（API 31+） |
| glassElevation | 0 8 24 -8 rgba(25,28,28,.18) | 0 8 24 -8 rgba(0,0,0,.55) | 玻璃层阴影（克制） |

**性能红线**：同一屏最多 2 层模糊；模糊半径 ≤24dp；不在滚动列表项上用模糊；`ActivityManager.isLowRamDevice` 为 true 时全部降级为 `glassScrim`/半透明纯色。

### 2.6 对比度审计（实测，WCAG 2.1）

| 配对 | Light | Dark | 判定 |
|---|---|---|---|
| primary / onPrimary | 6.46 | 7.66 | AA ✅ |
| primaryContainer / onPrimaryContainer | 13.32 | 7.29 | AAA ✅ |
| secondary / onSecondary | 6.48 | 7.71 | AA ✅ |
| tertiary / onTertiary | 6.47 | 7.75 | AA ✅ |
| error / onError | 6.47 | 7.68 | AA ✅ |
| surface / onSurface | 16.26 | 14.37 | AAA ✅ |
| surface / onSurfaceVariant | 8.90 | 10.91 | AAA ✅ |
| surface / primary（主色作图标/文字） | 6.13 | 10.85 | AA ✅ |
| surface / success | 4.74 | 10.36 | AA ✅ |
| brandMist `#4E9082` / white | **3.73** | 3.73 | 仅大字号/图形（≥18.7sp bold 或 24sp）⚠️ |
| brandBlue `#6E8CA8` / white | **3.51** | 3.51 | 仅大字号/图形 ⚠️ |
| brandSand / `#2C1700` | 5.83 | 5.83 | AA ✅ |

**规则**：`brandMist`/`brandBlue` 永远作为「大数字、图标、图形、>=24sp 标题」的前景或背景；凡是 12–16sp 正文/按钮文字，一律用 `primary`/`onSurface`/`onSurfaceVariant`。`glassContainer` 上的文字必须额外叠加一层 40% 实色底（保证任意模糊背景上对比度稳定）。

---

## 3. 字体

### 3.1 字体家族与 Roboto Flex 可变轴

| 用途 | 字体 | 说明 |
|---|---|---|
| Latin/数字/标点/符号 | **Roboto Flex**（OFL，可变字体，打包 1 个 ttf/woff2） | 通过 `variationSettings` 控制 wght/wdth/GRAD/opsz；display/headline/title 用 `wght 400–500`，emphasized 用 `wght 500–700` |
| 中文 | **系统字体回退**（Android 17：Noto Sans CJK / 厂商思源黑体、MiSans 等） | 不打包中文字体（体积 10MB+）；缺失字形由平台 fallback 链补齐 |
| 可选品牌中文 | Noto Sans SC 子集（3500 常用字+标点，pyftsubset 后约 1.5–2.5MB） | 🟡 仅在「品牌一致性验收」要求时启用；CJK 专用 family + locale 判断分发 |
| 等宽数字 | Roboto Flex + `FontFeature("tnum")` | 金额/电量/水量/序号/倒计时一律 tabular figures |

**Roboto Flex 轴用法（🟡 推荐值）**：`wght` 400（body）/500（title, emphasized display）/700（label emphasized）；`GRAD` +0（深色底可 +25 补偿视觉膨胀）；`wdth` 100（默认，不使用压缩）；`opsz` 跟随字号自动（8–144）。✅ Roboto Flex 为 Google Fonts 可变字体，轴定义见 `fonts.google.com/specimen/Roboto+Flex`。
> 实现提示：`Font(R.font.roboto_flex, variationSettings = FontVariation.Settings(FontVariation.weight(500)))`；一个文件多权重，包体只 +~0.9MB。

### 3.2 中文覆盖规则（🟡）
1. **字距**：TypeScale 的 `letterSpacing` 是拉丁字距；CJK 文本置 `0.sp`（数字/英文混排时按整段样式保留拉丁字距会显松）。
2. **行高**：CJK 在 body/title 级别 `+2sp`（如 bodyLarge 24→26）避免中文拥挤；display/headline 可保持。
3. **标点**：开启 `TextStyle(lineBreak = LineBreak.Paragraph)`，中文标点挤压交给系统；不要关闭 `hyphens`（本 App 不做英文断词）。
4. **字重**：中文回退字体多数只有 400/500/700 三档，`wght 600` 在部分设备会落到 500 或 700；设计只用 400/500/700。
5. **验收**：在 Pixel（Noto Sans CJK）、小米（MiSans）、华为（HarmonyOS Sans）三台设备上检查 title 不换行溢出。

### 3.3 类型标尺（30 styles，material3 1.4.0 `TypeScaleTokens` 实测值）

**Baseline（15）**

| Style | Size | Line | Tracking | Weight | 典型用途 |
|---|---|---|---|---|---|
| displayLarge | 57 | 64 | -0.2 | 400 | 首页大数字（电量/水量/抽签总数） |
| displayMedium | 45 | 52 | 0 | 400 | 抽签结果大数字 |
| displaySmall | 36 | 44 | 0 | 400 | 体测总分/结算数字 |
| headlineLarge | 32 | 40 | 0 | 400 | 页面大标题（Folded 时收缩） |
| headlineMedium | 28 | 36 | 0 | 400 | 页面标题 |
| headlineSmall | 24 | 32 | 0 | 400 | 卡片组标题 |
| titleLarge | 22 | 28 | 0 | 400 | App bar 标题、详情页标题 |
| titleMedium | 16 | 24 | 0.2 | 500 | 列表主标题、工具名 |
| titleSmall | 14 | 20 | 0.1 | 500 | 卡片小标题 |
| bodyLarge | 16 | 24 | 0.5 | 400 | 正文（通知正文/作业内容） |
| bodyMedium | 14 | 20 | 0.2 | 400 | 次要正文/参数标签值 |
| bodySmall | 12 | 16 | 0.4 | 400 | 辅助说明、时间戳 |
| labelLarge | 14 | 20 | 0.1 | 500 | 按钮文字、Tab |
| labelMedium | 12 | 16 | 0.5 | 500 | Chip、徽章 |
| labelSmall | 11 | 16 | 0.5 | 500 | 角标、极简标签 |

**Emphasized（15，Expressive；用于「表达性时刻」）**

| Style | Size | Line | Tracking | Weight | 用途 |
|---|---|---|---|---|---|
| displayLargeEmphasized | 57 | 64 | 0 | 500 | 电量/水量 hero |
| displayMediumEmphasized | 45 | 52 | 0 | 500 | 抽签结果 |
| displaySmallEmphasized | 36 | 44 | 0 | 500 | 体测总分 |
| headlineLargeEmphasized | 32 | 40 | 0 | 500 | 工作台问候/首页标题 |
| headlineMediumEmphasized | 28 | 36 | 0 | 500 | 工具页标题 |
| headlineSmallEmphasized | 24 | 32 | 0 | 500 | 分组结果组头 |
| titleLargeEmphasized | 22 | 28 | 0 | 500 | 完成态标题 |
| titleMediumEmphasized | 16 | 24 | 0.15 | 700 | 选中项强调 |
| titleSmallEmphasized | 14 | 20 | 0.1 | 700 | 小节强调 |
| bodyLargeEmphasized | 16 | 24 | 0.15 | 500 | 关键正文（通知要点首句） |
| bodyMediumEmphasized | 14 | 20 | 0.25 | 500 | — |
| bodySmallEmphasized | 12 | 16 | 0.4 | 500 | — |
| labelLargeEmphasized | 14 | 20 | 0.1 | 700 | 主按钮/关键 Tab |
| labelMediumEmphasized | 12 | 16 | 0.5 | 700 | 强调 Chip |
| labelSmallEmphasized | 11 | 16 | 0.5 | 700 | 强调角标 |

**表达性使用配额**：每屏 ≤2 处 emphasized；只允许出现在：首页问候、抽签结果数字、电量/水量 hero、体测总分、空态标题、主 CTA 文案。

### 3.4 字号缩放与多语言
- 系统字体缩放 85%–200%：所有页面用 `sp` 而不是固定 dp 文字；卡片高度不写死（用 `heightIn(min=…)`），`maxLines` + `overflow=Ellipsis` 兜底；**验收 200% 时抽签参数表单可完整操作**。
- 单行数字/单位混排（如 `123.45 度`）用 `AnnotatedString` + 较小 unit 样式，避免缩放时折行错位。

---

## 4. 形状

### 4.1 Shape scale（material3 1.4.0 `ShapeTokens` 公开值）

| Token | dp | 用途 |
|---|---|---|
| extraSmall | 4 | Snackbar、小徽章、进度条轨道端 |
| small | 8 | Chip、输入框内小容器、日历格 |
| medium | 12 | 列表项/内嵌面板/BottomSheet 内部块 |
| large | 16 | **卡片（继承 web 16px 圆角）**、FAB、Toast、导出卡内块 |
| extraLarge | 28 | Dialog、ModalBottomSheet 顶部、日期选择器 |
| full | 50% | 按钮、IconButton、SearchBar、Chip（全圆 chic）、加载指示器、头像 |
| （expressive 增补，1.4.0 内部/1.5.0 公开）largeIncreased 20 / extraLargeIncreased 32 / extraExtraLarge 48 | 20/32/48 | hero 卡（32）、LoadingIndicator 容器（48）、Shape morph 目标 |

### 4.2 组件圆角映射（🟡 本项目最终值）

| 组件 | 圆角 |
|---|---|
| 页面卡片 / 工具卡 / 列表容器 | 16dp |
| Hero 大卡（电费/水量/抽签结果） | 32dp（medium/expanded 可 36dp） |
| 按钮/IconButton/FAB（small/medium） | full（FAB 用 16dp round-square；large FAB 28dp） |
| FAB menu 子按钮 | full；展开容器 28dp |
| 悬浮工具栏（FloatingToolbar） | full（容器 64dp 高 → 32dp 半径） |
| DockedToolbar | 顶部 16dp（`RoundedCornerShape(topStart=16, topEnd=16)`） |
| SegmentedButton | full |
| Chip / FilterChip | 8dp（M3 规范） |
| TextField（filled/outlined） | 4dp / 4dp（M3 1.4） |
| SearchBar | full（56dp 高 → 28dp 半径） |
| Dialog | 28dp |
| ModalBottomSheet | 顶部 28dp |
| Snackbar | 4dp |
| 导出卡（Android Canvas） | 主卡 22dp、参数面板 14dp、chip 15dp（沿用导出卡 v2 规格） |

### 4.3 形状变形规则（Shape morphing）
1. **可变形场景**：FAB↔FAB menu 容器、按钮↔ButtonGroup、播放/暂停、抽签「开始→结果」容器、LoadingIndicator 容器、底表拖拽柄。
2. **变形原则**：形状差异只在相同「视觉体量」之间做（高 56↔56、64↔64）；不同体量先缩放再变形；**不要**让卡片在滚动中持续变形。
3. **实现**：material3 1.5.0 用 `MaterialShapes` + `MaterialShape.toShape(progress)`；1.4.0 用 `animateFloatAsState(spring(defaultSpatial))` 驱动 `RoundedCornerShape` 的 corner 值（或单独引入 `androidx.graphics:graphics-shapes:1.0.1` 做多边形 morph）。
4. **时长/曲线**：形状变形一律 spatial default spring（0.8/380），不做 easing/duration。
5. **减弱动效**：系统「移除动画」开启时，形状直接跳变（不做补间）。

---

## 5. 动效

### 5.1 Spring token（✅ androidx 源码 `ExpressiveMotionTokens.kt` / `MotionScheme.kt` 实测）

| Token | dampingRatio | stiffness | 估算稳定时间（2% 容差，mass=1）* | 用途 |
|---|---|---|---|---|
| spatialFast | 0.6 | 800 | ≈230ms | 按压回弹、FAB 展开、Tab 指示器、sheet 收起 |
| spatialDefault | 0.8 | 380 | ≈250ms | 页面/容器转场、列表增删、形状变形 |
| spatialSlow | 0.8 | 200 | ≈345ms | 底表展开、大面积 hero 变化、共享轴转场 |
| effectsFast | 1.0 | 3800 | ≈63ms | 颜色/透明度快速切换（按下高亮） |
| effectsDefault | 1.0 | 1600 | ≈98ms | 常规颜色/alpha |
| effectsSlow | 1.0 | 800 | ≈138ms | 大底色渐变、主题切换 |

\* 估算公式 `t≈3.9/(ζ·√k)`，仅用于 QA 预期；Compose 会按实际速度积分。
> 同一个 `MotionScheme` 里 spatial 允许 overshoot（ζ<1），effects 必须临界阻尼（ζ=1，不 overshoot）。

**material3 版本落地**
- 1.4.0（stable）：`MotionScheme` 接口公开，但 `MotionScheme.Companion.expressive()` 为 `internal` → **App 内自建**：
  ```kotlin
  object ExpressiveMotionScheme : MotionScheme {
      override fun <T> defaultSpatialSpec() = spring<T>(dampingRatio = 0.8f, stiffness = 380f)
      override fun <T> fastSpatialSpec()    = spring<T>(dampingRatio = 0.6f, stiffness = 800f)
      override fun <T> slowSpatialSpec()    = spring<T>(dampingRatio = 0.8f, stiffness = 200f)
      override fun <T> defaultEffectsSpec() = spring<T>(dampingRatio = 1f,   stiffness = 1600f)
      override fun <T> fastEffectsSpec()    = spring<T>(dampingRatio = 1f,   stiffness = 3800f)
      override fun <T> slowEffectsSpec()    = spring<T>(dampingRatio = 1f,   stiffness = 800f)
  }
  MaterialTheme(colorScheme = AppColors, typography = AppType, shapes = AppShapes, motionScheme = ExpressiveMotionScheme) { … }
  ```
- 1.5.0-alpha29：`MaterialExpressiveTheme(motionScheme = MotionScheme.expressive())` 直接可用（✅ 源码核实 public）。

### 5.2 场景 → 动效映射（🟡 本项目规范）

| 场景 | 曲线 | 说明 |
|---|---|---|
| 页面间前进/返回（同层） | 进入 spatialSlow，退出 effectsFast + `sharedAxisX` | 返回时顺序对称；Predictive Back 用手势进度驱动 |
| 卡片→详情（容器变换） | spatialDefault + `containerTransform` | 只用于「抽签结果→结果单」「通知卡→详情」 |
| 底表展开 / 收起 | 展开 spatialSlow；收起 spatialFast + effectsFast | 与 material3 1.4.0 行为变更一致（BottomSheet 收起用 fast effects） |
| 列表项增删/重排 | spatialDefault（`animateItem`） | 拖拽排序用 200ms 内跟手 + 松手 spatialFast 吸附 |
| 按压反馈 | `fastSpatial` scale 0.97 + ripple | 长按 350ms 触发拖动（继承 web/小程序习惯） |
| Tab 选中指示器 | spatialFast（液体感） | 滑块 `stretch = |目标-当前|*0.25` 的横向拉伸，松手吸附 |
| FAB menu 展开 | spatialFast + stagger 20ms/项 | 关闭时合并延迟 |
| Loading 指示器 | 无限动画（expressive loading indicator 自带 morph） | 1.4.0 降级为 `CircularProgressIndicator`，规格 48dp 容器 |
| 数字变化（电量/水量/计数） | effectsDefault + 可选 `animateIntAsState` + 数字滚动 | 抽签结果用「滚动+落定」一次（≤600ms） |
| Snackbar/Toast | 进入 fastSpatial，退出 effectsFast | 底部导航栏上方 16dp |
| 主题明暗切换 | effectsSlow 颜色过渡（surface/文字） | 不做整体截图动画 |

### 5.3 传统时长/缓动兜底（material3 `MotionTokens`，非 spring 场景，如 WebView 内容、Canvas 导出预览）
| Token | 值 |
|---|---|
| short1–4 | 50 / 100 / 150 / 200 ms |
| medium1–4 | 250 / 300 / 350 / 400 ms |
| long1–4 | 450 / 500 / 550 / 600 ms |
| extraLong1–4 | 700 / 800 / 900 / 1000 ms |
| easing emphasized | `CubicBezier(0.2, 0.0, 0.0, 1.0)` |
| easing emphasizedDecelerate | `CubicBezier(0.05, 0.7, 0.1, 1.0)` |
| easing emphasizedAccelerate | `CubicBezier(0.3, 0.0, 0.8, 0.15)` |
| easing standard | `CubicBezier(0.2, 0.0, 0.0, 1.0)` |

### 5.4 手势驱动与预测返回
- 所有「可拖动」交互（顶部工具条滑动切页、抽签 STEP 滑动、课表切周）必须**跟手**：`pointerInput` 直接写 offset，松手才用 spring 吸附（spatialFast）；禁止「先固定动画再等手势」。
- **Predictive Back**（Android 13+/target 36+ 默认系统动画）：抽签工作流返回顺序 = STEP3→STEP2→STEP1→离开工具；用 `PredictiveBackHandler` 提供 progress，页面内容做 0.96 scale + 8dp 水平位移，松手 cancel 回弹（spatialFast）。
- 根页（工作台）双击返回退出：第一次返回显示 Snackbar「再按一次退出」，2s 内第二次返回调用 `finish()`（继承 web 双击退出习惯）。
- 拖拽/滑动区域与系统返回手势冲突：距屏幕左右边缘 24dp 内不启动应用内横滑（避免抢返回手势）。

### 5.5 减弱动效（Reduce Motion）
- 读取 `LocalMotionDurationScale`/系统「移除动画」：spatial spring 直接跳到终态；Loading 用静态进度；Predictive Back 仍响应手势但不缩放内容。
- 设置页提供「减弱动效」开关（App 级覆盖），与系统设置取「或」关系。

---

## 6. 组件规范

> 每个组件给出：API（详见 `COMPONENTS.md`）、尺寸、颜色、形状、状态、动效、无障碍。单位 dp（文字 sp）。

### 6.1 导航（自适应）
**决策**：compact 用 **ShortNavigationBar**（M3 Expressive，64dp 高；1.4.0 可用）或 baseline `NavigationBar` 降级；medium 用 `NavigationRail`（80dp 宽）；expanded 用 `WideNavigationRail`（含展开态 256dp）。目的地：工作台 / 工具 / 我的（与小程序 IA 一致）。
- 选中态：active indicator = `secondaryContainer`（M3 默认），图标 `onSecondaryContainer`，标签 `labelMedium`；Expressive 允许「选中图标形状 morph」但在 1.4.0 降级为颜色+indicator。
- 高度/Insets：`NavigationBarDefaults.windowInsets` 处理手势条；内容不画到系统栏后面（edge-to-edge 下由 Scaffold 处理）。
- 动效：切换 destination 用 effectsDefault 颜色过渡 + spatialFast indicator 位移；禁止整页横向滑入（系统返回手势优先）。
- 无障碍：每个 item 有 `contentDescription`（如「工作台，已选中」），点击区域 ≥48dp。
- ⚠️ 与 web「无底部导航栏」红线的关系：该红线约束网页/小程序；Android 原生采用 M3 自适应导航是平台惯例，已在 P2/§1 说明。若产品要求 1:1 复刻 web，则 compact 改为顶部 `PrimaryTabRow` + 「全部」入口，其余不变（备选方案，见 `SCREENS.md §2`）。

### 6.2 顶部 App Bar
| 变体 | 高度 | 用途 |
|---|---|---|
| `TopAppBar`（small） | 64dp | 工具详情页、设置页（滚动时 `TopAppBarDefaults.pinnedScrollBehavior()`） |
| `MediumTopAppBar` | 112dp（折叠后 64） | 工作台、工具页、通知列表首页 |
| `LargeTopAppBar` | 152dp（折叠后 64） | 首页 hero（「班级工具」+ 问候语） |
| `MediumFlexibleTopAppBar`/`LargeFlexibleTopAppBar`（1.5.0） | 112/136、120/152 | 1.5.0 轨道可选；1.4.0 用 `MediumTopAppBar` + 自定义副标题替代 |
- 颜色：容器 `surface`（滚动后 `surfaceContainer` + 1dp `outlineVariant` 底边或模糊玻璃）；标题 `onSurface`，副标题 `onSurfaceVariant`。
- 标题：首页用 `headlineMediumEmphasized`；工具页 `titleLarge`；副标题 `bodyMedium`。
- 滚动行为：进 2 屏内容后收起大标题，标题渐变为 64dp bar 的 `titleLarge`（`TopAppBarScrollBehavior` + `enterAlwaysScrollBehavior`）。
- 动效：折叠用 spatialFast（0.6/800）；**不要**用固定 300ms。
- 无障碍：`navigationIcon` 描述「返回」；折叠后标题仍可被 TalkBack 读到。

### 6.3 浮动工具栏 / Docked Toolbar / FAB menu / Split button / Button group
| 组件 | 1.4.0 可用性 | 规格 | 降级 |
|---|---|---|---|
| FloatingToolbar（水平/垂直） | ❌（1.5.0） | 高 64dp、容器 `full` 圆角、`surfaceContainerHigh` + 1dp glassBorder、8dp 外边距；最多 5 个 icon；滚动隐藏用 `horizontalEnterTransition` | 自建 `Surface`+`Row`+`AnimatedVisibility`（高度/圆角同规格） |
| DockedToolbar | ❌（1.5.0） | 高 64dp，贴底、顶部 16dp 圆角、`surfaceContainer` | 自建 `Surface`（topStart/topEnd=16dp） |
| FAB menu | ❌（1.5.0） | 主 FAB 56dp + 子项 56dp pill（`surfaceContainerHigh`），stagger 20ms，spatialFast | 自建 `AnimatedVisibility` + `Column`，或改用 `DropdownMenu`（Badge/文字项） |
| Split button | ❌（1.5.0） | 56dp 高，主按钮 16dp 圆角 + 尾部 16dp 圆角，中间 2dp 分隔；用于「导出图片 / 更多导出格式」 | 两个 `Button`（主 `FilledTonalButton` + `IconButton`） |
| Button group | ❌（1.4.0/1.5.0 有） | 连接按钮：首/中/尾 16dp 圆角（connectedShapes），高 40（small）/56；用于抽签 STEP 排序「从大到小/从小到大/随机」 | `SegmentedButton`（1.4.0 稳定）或 `Row`+`FilterChip` |
| SegmentedButton | ✅ 1.4.0 | 40dp 高、full 圆角、选中 `secondaryContainer`；用于课表周/日、趋势 24h/7d/30d、统计今天/7天 | — |
| ToggleButton（独立开关按钮） | ❌ 1.5.0 | 40/56dp，选中 morph 成圆角矩形 | `FilterChip` / `FilledTonalIconToggleButton`（1.4.0 可用 IconToggleButton） |

### 6.4 按钮
| 类型 | 高度 | 形状 | 颜色 | 备注 |
|---|---|---|---|---|
| Filled（主 CTA） | 56dp（Expressive 档；1.4.0 默认 40dp 需手动 `heightIn(min=56.dp)`+`contentPadding`） | full | `primary`/`onPrimary` | 一屏一个主 CTA：抽签「开始抽签」、分组「开始分组」、体测「保存记录」 |
| FilledTonal | 56dp | full | `secondaryContainer`/`onSecondaryContainer` | 次动作：「下一步：分组」 |
| Outlined | 56dp | full | 透明/`onSurface`，1dp `outline` | 重置/清空 |
| Text | 40dp | full | `primary` | 弹层取消、低权重动作 |
| Elevated | 56dp | full | `surfaceContainerLow`+elev1 | 仅浮在图片/hero 上时 |
| Danger | 56dp | full | `error`/`onError` | 清空数据、退出身份（二次确认） |
| 加载态 | — | — | 文字前 `CircularProgressIndicator(16dp)`，按钮禁用（`alpha .38`），宽度锁定防跳动 | — |
- 按压：ripple + scale 0.97（spatialFast）；禁用：内容 `onSurface` 38%、容器 `onSurface` 12%。
- 触控：最小 48dp，按钮内容左右 padding 24dp（带图标 16/24）。
- 无障碍：`Modifier.semantics { role = Role.Button }`；图标+文字按钮文字必须可读；纯图标按钮必须有 `contentDescription`。

### 6.5 加载指示器（LoadingIndicator）
- 1.5.0-alpha29：`LoadingIndicator` / `ContainedLoadingIndicator`，容器 48dp，`primary` 色；形状 morph 循环（自带）。
- 1.4.0 降级：`CircularProgressIndicator`（48dp，4dp 粗，`primary`）或自定义 5 个点波形动画（effectsDefault 循环）。
- 使用规则：页面首次加载用「骨架屏」而不是 spinner（web 站是骨架屏）；按钮内联加载用 16dp spinner；下拉刷新用 `PullToRefreshBox`（1.4.0 稳定）。
- 禁止：超过 1s 的加载无反馈；骨架屏与真实布局尺寸一致（防跳动）。

### 6.6 底部弹层（ModalBottomSheet）
- 容器 `surfaceContainerLow`，顶部 28dp 圆角 + drag handle（M3 1.4 `DragHandle` 可用）；最大高度 90%；初始 half-expanded（可选）。
- 用途：名单选择器（全班 70 人气泡多选）、导出选项、体测国标表、课表课程详情、发布作业/通知表单、月份选择。
- 动效：展开 spatialSlow；收起 spatialFast + effectsFast；预测返回支持（手势下拉与系统返回合一）。
- 无障碍：`ModalBottomSheet` 默认 semantics 正确；确保 `sheetState` 的 `skipPartiallyExpanded` 按内容高度选择。

### 6.7 卡片
| 类型 | 容器色 | 描边 | 圆角 | 用途 |
|---|---|---|---|---|
| Outlined（默认） | `surfaceContainerLowest` | 1dp `outlineVariant` | 16dp | 工具卡、列表容器（继承 web 卡片风格） |
| Filled | `surfaceContainerHighest` | 无 | 16dp | 高亮信息块（同步状态） |
| Elevated | `surfaceContainerLow` + elev1 | 无 | 16dp | 浮在 hero/图片上（少用） |
| Hero | 渐变（brandMist→primary/或 surfaceContainer） | 无 | 32dp | 电费/水量/抽签结果大卡 |
- 内边距：compact 16dp；medium 20dp；expanded 24dp。卡间距 12dp（compact）/16dp。
- 点击反馈：整卡可点则 ripple，按下 scale 0.99；卡内多个按钮时整卡不可点。

### 6.8 Chip / Filter / Input / Suggestion
- 高 32dp、圆角 8dp、`labelLarge`；FilterChip 选中 `secondaryContainer` + 对勾图标；InputChip 用于已选名单（可 ×）；SuggestionChip 用于「沿用抽签名单」。
- 触控：chip 视觉高 32dp 但触控区域补到 48dp（`Modifier.minimumInteractiveComponentSize()`）。

### 6.9 列表项（ListItem）
- 高度：1 行 56dp / 2 行 72dp / 3 行 88dp；左右 padding 16dp；leading icon 24dp、trailing 24dp；分割线用 `outlineVariant` 1dp 且只在「非卡片」列表中使用。
- 作业/通知列表项：主标题 `titleMedium`、副标题 `bodyMedium`、时间 `bodySmall` + `onSurfaceVariant`；完成态：文字删除线 + `onSurfaceVariant` + 勾选图标 `success`。
- 通知「已处理/置顶」：已处理沉底并 `alpha .6`；置顶项左侧 3dp `primary` 竖条 + 「置顶」`labelSmall` 徽章。
- 滑动：SwipeToDismissBox（1.4.0 可用）只用于「隐藏」单侧动作（通知隐藏/作业隐藏），背景 `errorContainer`，阈值 40%。

### 6.10 搜索栏（SearchBar）
- 1.4.0 新 API：`SearchBar`（折叠）/`ExpandedFullScreenSearchBar`/`ExpandedDockedSearchBar` + `SearchBarState`；高 56dp、full 圆角、`surfaceContainerHighest` 底、`onSurfaceVariant` placeholder。
- 用途：工具目录搜索（全部工具）、通知/作业列表筛选（可选）。首屏不默认展开；点击后全屏搜索（expanded 时用 docked）。
- 简版降级：1.4.0 之前用 `TextField` + `Icons.Search` + full 圆角。

### 6.11 Dialog
- 圆角 28dp、`surfaceContainerHigh`、最大宽 560dp、内边距 24dp；标题 `headlineSmall`、正文 `bodyMedium`、按钮右下（Text + Filled）。
- 用途：发布作业（表单 Dialog 或 BottomSheet，建议 BottomSheet）、清空数据确认、退出身份确认、国标表（全屏 Dialog）。
- 禁止：把长表单塞进 Dialog；Dialog 内滚动超过 2 屏换 BottomSheet。

### 6.12 Snackbar / Toast
- Snackbar：`inverseSurface`/`inverseOnSurface`、4dp 圆角、单行 48dp/双行 68dp；底部导航上方 16dp；动作按钮 `inversePrimary`；显示 4s。
- 用途：已同步/已复制/再按一次退出/「最多选 4 个」。
- Toast 仅用于系统级轻提示（导出成功文件名），优先 Snackbar。

### 6.13 空态 / 错误态 / 加载态（统一组件 `EmptyState`）
| 状态 | 结构 | 规格 |
|---|---|---|
| 空 | 大图标（48dp，`onSurfaceVariant` 或品牌 emoji 占位）+ 标题 `titleMediumEmphasized` + 说明 `bodyMedium` + 1 个动作按钮 | 垂直居中，最大宽 320dp |
| 加载 | 骨架屏（卡片形状 + shimmer） | shimmer 1.2s 循环；≥3 个骨架块 |
| 错误 | 警告图标 + 「读取失败」+ 具体原因（网络/权限/服务）+ 「重试」FilledTonal 按钮 | 必须可重试；离线时提示「已显示本机缓存」 |
| 无权限 | 锁图标 + 「需要班级身份」+「去验证」 | 不应出现（身份门禁保证），但深链场景兜底 |

### 6.14 输入控件
- TextField：filled/outlined 二选一（本项目**统一 Outlined**，圆角 4dp，聚焦 2dp `primary` 描边 + `primaryContainer` 12% 底色）；错误态 `error` 描边 + supporting text。
- 数字输入（学号/范围/读数/成绩）：`KeyboardType.Number/Decimal`、`imeAction = Done`、`singleLine`；抽签范围/个数用 stepper（`IconButton` + 数字）替代纯键盘输入。
- 表单校验：失焦即校验 + 按钮禁用 + 行内错误文案（不弹 Toast）。
- 开关/复选/单选：Switch 用 `primary`；Checkbox 用 `primary`；Radio 用于性别/年级/视图。触控 48dp。
- 日期/时间：`DatePickerDialog`/`TimePickerDialog`（1.4.0 稳定）；月份选择自定义（水量抄表，弹出 BottomSheet 12 宫格）。

### 6.15 徽章 / Pills / 头像
- Badge：`error` 底白字（未读数）；小圆点用 `error`。
- Pill（身份/角色/状态）：高 24dp、full 圆角、`labelMedium`；颜色映射：角色→`tertiaryContainer`，管理员→`secondaryContainer`，已同步→`success` 12% 底 + success 文字。
- 头像：不用真实头像（隐私），用「姓名首字」圆形 `primaryContainer` + `onPrimaryContainer`。

---

## 7. Android 17 专属适配

### 7.1 targetSdk 37 的大屏/方向硬约束（✅ 已核实）
- Android 17（API 37）**移除开发者退订**：当应用 target 37 且运行在 sw≥600dp 的大屏设备上，系统**忽略**以下：`android:screenOrientation`、`Activity.setRequestedOrientation()`、`android:resizeableActivity`、`android:minAspectRatio`、`android:maxAspectRatio`。自 Android 16 起无新增变更，只是不能退出。来源：Android Developers Blog《Prepare your app for the resizability and orientation changes in Android 17》（2026-02-13）。
- 落地要求：
  1. `AndroidManifest.xml` **不写** `screenOrientation`、`resizeableActivity="false"`、`min/maxAspectRatio`；Activity 默认 `resizeableActivity=true`。
  2. 所有页面必须支持横屏与自由窗口（桌面模式/分屏/折叠展开）；用 `WindowSizeClass` 而非 `orientation` 决策布局。
  3. 配置变化不得重启丢失状态：ViewModel + `SavedStateHandle`；禁用 `android:configChanges` 暴力吞配置。
  4. 相机/媒体：本 App 不做相机预览；上传作业图片若用 CameraX 需按官方建议处理（`PreviewView` 自适应）。通知/作业附件选择用 `ActivityResultContracts.PickVisualMedia`（系统 Photo Picker，无需存储权限）。
- 测试：模拟器 Pixel Tablet / Pixel Fold + `targetSdkPreview = "CinnamonBun"`；`adb shell wm size`；Compose `DeviceConfigurationOverride` 做指定尺寸快照测试。

### 7.2 Edge-to-Edge（边到边）
- 版本边界：Android 15（target 35 起）强制边到边；target 36 起禁用退出项；Android 17 延续。→ 本 App target 37，**必然边到边**。
- 实现：
  ```kotlin
  enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
                   navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
  ```
  `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)` 或用 `Modifier.safeDrawingPadding()`；`Modifier.imePadding()` 处理键盘；列表底部 `contentPadding = paddingValues + navigationBars`。
- 系统栏图标明暗：浅色主题 `isAppearanceLightStatusBars = true`；深色 `false`；随主题切换实时更新。
- 手势导航条：底部栏/悬浮工具条要避让 `navigationBars`，但背景可延伸（`windowInsetsPadding` 只作用于内容）。
- 3 键导航：`navigationBars` 高度不同，禁止写死 dp。

### 7.3 预测返回（Predictive Back）
- Android 13（API 33）提供 `OnBackInvokedCallback`/`PredictiveBackHandler`；Android 15 将系统返回动画移出开发者选项；**target 36+ 默认启用系统预测动画**；API 37 延续并允许同一分发器注册多个 `PRIORITY_SYSTEM_NAVIGATION_OBSERVER`（用于观察返回但不消费，例如埋点）。
- 落地：
  - `AndroidManifest` `android:enableOnBackInvokedCallback="true"`（target 33+ 默认，但显式写清）。
  - Navigation Compose（2.8+）自动接入预测返回；自定义工作流（抽签 STEP）用 `PredictiveBackHandler`。
  - 不要为「记录返回日志」注册消费型 callback（会禁用系统动画）；日志用 `onCleared()` 或高优先级 observer。
- 页面返回规范：见 §5.4。

### 7.4 自适应窗口（Window Size Class）
- 断点（M3/Compose 自适应规范）：compact 0–599dp、medium 600–839dp、expanded 840dp+；更细分 1200/1600 用于「超宽」多栏。
- API：`material3-window-size-class:1.4.0` 的 `calculateWindowSizeClass(activity)`（或 1.5.0 的 `currentWindowAdaptiveInfo()`）；布局按 `WindowWidthSizeClass` 分支。
- 规则：
  - compact：单列、底部导航、全宽卡片、页面 padding 16dp。
  - medium：单列但内容最大宽 640dp 居中；导航切 `NavigationRail`；抽签 STEP1 可用左右分栏（表单 5 / 结果 7）。
  - expanded：列表-详情双栏（通知/作业：列表 360–420dp + 详情）；抽签/体测左右分栏；底部导航换 `WideNavigationRail`；内容最大宽 1200dp 居中，两侧留白。
  - 超宽（≥1600dp）：三栏（工具目录卡片 3–4 列；电费 hero + 趋势 + tiles 三栏）。
- 方向：不区分 `orientation`，只按 `WindowWidthSizeClass`；折叠屏展开/合拢视为尺寸变化，状态保持不变。
- 多窗口：`WindowSizeClass` 随窗口尺寸变化实时重排（`currentWindowAdaptiveInfo` 是 snapshot state）。

### 7.5 模糊 / 毛玻璃的 Compose 实现与性能
- `Modifier.blur(radius, edgeTreatment = BlurredEdgeTreatment.Unbounded)`：底为 `RenderEffect`，**仅 Android 12（API 31）及以上生效**；API <31 自动忽略 → 必须准备「无模糊时也成立」的视觉。
- Dialog/Popup 背后的窗口模糊：API 31+ 可用 `window.setBackgroundBlurRadius(radiusPx)`（需 `WindowManager.LayoutParams.FLAG_BLUR_BEHIND` 或 Compose Dialog 的 window attr；Compose `Dialog` 可通过 `DialogWindowProvider` 设置）——**不修改应用内容层**，成本低，是弹层玻璃的首选。
- 「内容模糊」只对自绘背景（渐变/装饰圆斑）用 `Modifier.blur`；不要对滚动中的列表做 blur（每帧 RenderEffect 很贵）。
- 滚动折叠工具栏的玻璃：`TopAppBar` 容器使用 `glassContainer` 半透明 + 1dp `glassBorder` + 可选 `Modifier.blur` 仅作用于背后的装饰层，**不要**实时截屏模糊（无官方低成本方案）。
- 性能红线：模糊半径 ≤24dp；同屏 ≤2 个模糊层；`isLowRamDevice`/`isPowerSaveMode` 时禁用模糊；模糊层不要参与动画（动画时半径保持常量）。
- ⚠️ 未核实：Android 17 系统是否新增「跨窗口/背景模糊」公开 API 给应用使用；媒体仅描述系统界面（通知栏/控制中心）使用高斯模糊。保守方案：按 API 31 `setBackgroundBlurRadius` + Compose blur 实现，不做系统级新 API 依赖。

### 7.6 其它 Android 17 行为变更（对齐检查）
- ✅ 官方行为变更页列出（target 37）：RemoteViews 内存上限（对 AppWidget，本 App 无 widget → 不适用）、后台音频强化、MessageQueue 行为变更、系统忽略方向/尺寸限制。实现前以官方页面最新列表复核。
- 通知权限：Android 13+ `POST_NOTIFICATIONS` 运行时权限；课表/作业提醒需在设置页引导授权（拒绝后仍可用站内提醒）。
- 16KB 页面大小（Android 15+ 设备）：本 App 纯 Kotlin/Compose 无 native 库 → 天然满足；若后续引入 native 库需检查 16KB 对齐。
- 预测式返回 + 键盘：`enableEdgeToEdge` + `imePadding`，输入法隐藏时返回先关 IME（系统行为，无需干预）。
- Noto 3D Emoji（Android 17 重绘约 4000 个 emoji，⚠️ 媒体转述）：**不要**用 emoji 作为关键功能图标（各版本/各厂商渲染不一致）；统一 Material Symbols 矢量图标；emoji 仅作装饰。
- Gemini 生成式 UI（Android 17 系统能力，⚠️ 媒体转述）：本 App 不依赖；通知摘要等已有云端 AI 预处理（历史能力），Android 端只渲染结果，避免把系统 AI 作为功能前置依赖。

### 7.7 深色模式与主题
- 跟随系统深色；`AppCompatDelegate.setDefaultNightMode` 无需调用（Compose 用 `isSystemInDarkTheme()`）。
- 深色下：卡片不用纯黑（surface #111414），hero 渐变用 `primaryContainer→surface`；图片/导出预览不反色（导出卡固定浅色版式，与 web 一致）。
- 高对比度（系统「高对比度文字」）：`ColorScheme` 提供高对比 variant（可选），至少保证 outline 加重、正文仍 ≥7:1。

---

## 8. 无障碍

| 项 | 要求 | 验收方法 |
|---|---|---|
| 触控目标 | ≥48×48dp（视觉可 32dp，用 `minimumInteractiveComponentSize()` 补足）；列表项整行可点 | TalkBack + 手动点测边角 |
| 对比度 | 正文 ≥4.5:1，大字号（≥18.7sp bold / 24sp）≥3:1，图形/图标 ≥3:1 | 本文件 §2.6 表；新增色必须先过对比度脚本 |
| 文字缩放 | 系统字体 200% 无截断/无遮挡；`maxLines` + ellipsis；按钮高度自适应 | Android 设置→字体大小最大档 |
| 屏幕阅读器 | 所有 icon-only 按钮有 `contentDescription`；装饰图 `contentDescription=null`；状态变化用 `liveRegion`；数字带单位朗读（「剩余 123.45 度电」） | TalkBack 全流程走查 |
| 焦点顺序 | 用 `isTraversalGroup` 控制卡片内顺序；BottomSheet 打开时焦点在表内 | TalkBack 焦点框 |
| 色觉 | 状态不只用颜色：低电=颜色+图标+文字；已处理=删除线+图标；抽签选中=对勾+描边 | 灰度截图检查 |
| 减少动态 | 系统「移除动画」时空间动画跳变；App 内开关 | 开发者选项→Animator duration scale 0 |
| 键盘/外接 | 全键盘可操作（Tab/Enter/Esc）；`Esc` 关闭 Dialog；焦点态可见（2dp outline） | Chromebook/外接键鼠 |
| 屏幕方向 | 横竖屏、分屏、自由窗口内容完整、可操作 | 见 §7.1 测试矩阵 |

---

## 9. 交付验收清单（Design QA）

**Token 一致性**
- [ ] `tokens.json` 生成的 `ColorScheme` 与本文件 §2.2/2.3 逐字段相等（写一个单测：`assertEquals(expected, actual)`）。
- [ ] 所有文字组合对比度脚本检查通过（正文 4.5、大字 3.0、图形 3.0）。
- [ ] 明/暗两套截图各 1 组（工作台/抽签/电费/通知/设置）。
- [ ] 任意页面不出现 `#4E9082` 小字号白字。

**动效**
- [ ] 页面转场、底表、列表重排、按压、加载、返回全部走 §5.2 映射（录屏 120fps 抽帧检查 overshoot 方向）。
- [ ] 系统「移除动画」下无补间动画。
- [ ] 拖拽/滑动全程跟手（无「先动画后手势」）。

**自适应**
- [ ] compact(360dp)/medium(800dp)/expanded(1280dp) 三档截图；横屏、分屏、桌面窗口。
- [ ] target 37 下强制横屏/竖屏锁定代码已删除（`grep screenOrientation` 为空）。
- [ ] 200% 字体档 + 横屏 + 平板三叠加速度计。

**Android 17**
- [ ] `enableEdgeToEdge` 全页面；系统栏图标明暗正确；列表底部不被手势条遮挡。
- [ ] 预测返回：抽签 STEP3→STEP2→STEP1→退出；根页双击退出提示。
- [ ] 弹层返回先关弹层（Dialog/Sheet）。
- [ ] 模糊在 API 31 与 API 30 模拟器上分别验证（降级不破版式）。
- [ ] 深色模式全页面（含导出卡预览不变色）。

---

## 10. 来源与核实状态

### 10.1 已核实（官方源码/文档/发布页）
| 事实 | 来源 URL |
|---|---|
| Android 17 = API 37；target 37 大屏忽略方向/尺寸限制，且 Android 16 起无新增变更只禁止退出 | https://developer.android.google.cn/blog/posts/prepare-your-app-for-the-resizability-and-orientation-changes-in-android-17 |
| Android 17 行为变更（RemoteViews 内存、后台音频、MessageQueue、忽略方向/尺寸限制） | https://developer.android.google.cn/about/versions/17/behavior-changes-17 |
| material3 1.4.0 stable（2025-09-24）：引入 MotionScheme；1.4.0-beta01 起移除 `ExperimentalMaterial3ExpressiveApi` 公共 API，Expressive 组件转到 1.5.0-alpha；1.5.0-alpha29（2026-09-23）为最新 alpha | https://developer.android.google.cn/jetpack/androidx/releases/compose-material3 |
| BOM 2026.09.00 → material3 1.4.0（最新 BOM）；adaptive-navigation-suite 1.5.0-beta01 | https://developer.android.google.cn/jetpack/compose/bom/bom-mapping |
| M3 Expressive spring 数值（spatial 0.6/800、0.8/380、0.8/200；effects 1.0/3800、1.0/1600、1.0/800） | https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/ExpressiveMotionTokens.kt |
| `MotionScheme` 6 个 spec 接口；1.4.0 中 `standard()/expressive()` 为 internal；1.5.0-alpha29 为 public | https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/MotionScheme.kt ；material3 1.4.0 / 1.5.0-alpha29 sources jar（dl.google.com maven） |
| 30 个类型样式（15 baseline + 15 emphasized）及 size/line/tracking/weight | https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/TypeScaleTokens.kt |
| Shape scale：4/8/12/16/28（公开）+ 20/32/48（expressive 内部） | https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/ShapeTokens.kt |
| 组件尺寸：按钮 medium 56dp、floating/docked toolbar 64dp、loading indicator 48dp、nav bar 64/80dp、search bar 56dp、list 56/72/88dp、snackbar 48/68dp、medium flexible app bar 112/136dp、large 120/152dp、FAB menu 项 56dp | material3 1.5.0-alpha29 sources jar 内 `tokens/*Tokens.kt`（同上 dl.google.com maven 路径） |
| M3 字体：30 个类型样式、可变字体用于表达性、emphasized 字阶 | https://m3.material.io/styles/typography/overview |
| M3 动效物理系统（spring token、空间/效果双域） | https://m3.material.io/styles/motion/overview/specs |
| Roboto Flex 可变字体 | https://fonts.google.com/specimen/Roboto+Flex |
| 边到边强制时间线（Android 15 target 35 起强制，target 36 起禁用退出，17 延续） | https://developer.android.google.cn/develop/ui/views/layout/edge-to-edge （另有 AOSP 行为梳理：https://github.com/Gracker/android-internals-wiki） |
| 预测返回：API 33 引入、Android 15 移出开发者选项、target 36+ 默认、API 37 延续 | https://developer.android.google.cn/guide/navigation/custom-back/predictive-back-gesture |
| 自适应：WindowSizeClass + `currentWindowAdaptiveInfo()` + 列表-详情多窗格 | https://developer.android.google.cn/develop/ui/compose/layouts/adaptive |
| ColorScheme role→tone 映射（primary40/onPrimary100/…/surface Neutral98 等） | material3 1.4.0 sources jar `tokens/ColorLightTokens.kt`、`ColorDarkTokens.kt` |

### 10.2 媒体/二手来源（仅参考，未作为强制规格）
| 事实 | 来源 |
|---|---|
| Android 17 视觉：M3 Expressive 深度迭代；通知栏/控制中心高斯模糊/毛玻璃；更柔和过渡；Gemini 生成式 UI；Noto 3D emoji（约 4000 个） | https://www.techrepublic.com/article/news-android-17-gemini-ai-privacy-sharing/ ；https://news.17173.com/content/05142026/170937020.shtml ；https://m.thepaper.cn/newsDetail_forward_33169132 |
| Android 17 发布会时间（2026-05-12 The Android Show / I/O） | 同上 |
| M3 Expressive 组件清单与弹簧含义（社区整理） | https://github.com/athevon/genjutsu/blob/ab8b2fdf73f64be4e38e1099b6b7fcfcb667f2b6/skills/_jutsu/compose-graphics/references/m3-expressive-deep.md |

### 10.3 未核实 / 保守方案
| 事项 | 处理 |
|---|---|
| Android 17 是否给应用开放新的系统级模糊 API | 未核实。按 API 31 `Window.setBackgroundBlurRadius` + Compose `Modifier.blur` 实现 |
| `1.5.0-alpha29` 是否要求 compileSdk 37.1 | 未核实（不同 alpha 版本要求变过：曾要求 37，后又移除）。构建时若报错则安装 `platforms;android-37.1` 并升 compileSdk |
| 1.4.0 的 `MaterialTheme(motionScheme=…)` 参数是否需 OptIn | 未核实（1.4.0 sources 中该参数未见实验注解；若编译报错加 `@OptIn(ExperimentalMaterial3Api::class)` 即可） |
| 系统「Noto 3D emoji」具体覆盖 | 未核实。设计不依赖 emoji 图形 |
| Android 17 系统界面动效的具体参数 | 未核实。本规格以 M3 公开 spring token 为准 |

---

**变更记录**
- 2026-11-07 v1.0 初稿：完成色彩/字体/形状/动效/组件/Android 17 适配/无障碍与验收清单；核实 material3 版本边界（1.4.0 stable vs 1.5.0-alpha29）并纠正「1.4.0 含 Expressive 实验 API」的常见误解。
