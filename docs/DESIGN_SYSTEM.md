# 班级工具站 · Android 统一设计规范（DESIGN_SYSTEM）

> 版本：v1.0（2026-11-19「系统性视觉/动效重构」）　适用：`android-class-tools` 全部页面
> 品牌与基础令牌在 `ui/theme/CtDesign.kt`。深色最终值由 `ui/theme/DarkComfort.kt` 解析。[九套预设、材质与验收记录](../design/DARK_COMFORT_2026-11-26.md)
> 组件库：`ui/components/CtLayout.kt`（卡片/区块标题/列表行/按钮/输入/空态/骨架/弹层容器）
> 参考资料：Material 3 Expressive（motion physics / 形状变形 / 扩展色板 / 可变字体轴）、
> Android 官方 design/ui 指南、HarmonyOS「One Harmonious Universe」（以人为本、轻拟物、卡片式、引力动效、多设备一致）、
> 本地鸿蒙端 `harmony-class-tools/entry/src/main/ets/ui/**`（Tokens / Motion / PageHeader / ToolTile / SectionHeader / AppShell / HomeScreen）。

---

## 0. 五条硬规则（提交前自查）

1. **颜色/圆角/字号/间距/动效时长只能来自令牌**。业务 UI（`ui/components`、`ui/screens`、`ui/navigation`）里不允许出现
   `Color(0x…)`、`Color.White/Black`、`RoundedCornerShape(数字)`、`fontSize = 数字.sp`、`数字.sp` 字面量。
   自查脚本见 §7（当前结果 **0 处**）。
2. **每个页面元素都必须是组件库里的一种**：卡片 = `CtCard`/`CtGlassCard`（同名旧入口）或 `HomeInfoCard`；
   区块标题 = `CtSectionTitle`（13 Medium 次级色）；列表行 = `CtListRow`；按钮 = `CtButton`（Filled 56 胶囊 / Tonal / Text）；
   输入 = `CtTextField`（14 圆角 tonal 底无边框）；空态 = `CtEmptyState`（48dp 线性图标 + 标题 + 说明 + 主操作）；
   加载 = `HarmonySkeleton*` / `CtSkeletonPage`（三档灰 + 900ms 呼吸）。
3. **深色材质默认实色**。仅开启玻璃时，共享卡片才按文字对比度调整底色不透明度和背景模糊。弹窗、输入和吸顶栏使用实色。卡片内容保持独立绘制。
4. **动效统一**：时长 120 / 200 / 300 / 400ms；弹簧由 `CtMotion` 提供；系统「移除动画」时全部降级为瞬时
   （`rememberReduceMotion()` / `rememberStaggeredEntry` / `ctPressFeedback` 已内置）。
5. **深色完整且 AA**：所有正文/次级/说明文字对所在底色 ≥4.5:1（§2.4 有逐对实测值，`CtDesignTokensTest` 锁定）。

---

## 1. 单一令牌源结构

```
ui/theme/
  CtDesign.kt        ★ 唯一令牌源：CtPalette / CtAccents / CtSemantic / CtType / CtRadius / CtSpace /
                       CtSize / CtMotionTokens / CtElevation / CtColor(明暗语义色包) / 旧名兼容别名
  DesignSystem.kt    组合入口：CtTypeScale（字体）/ CtShape（形状）/ ctAccentsOf / uiAccentOf /
                       rememberCtColors / rememberCtAccents / harmonyTint / 旧名 HarmonyColors 等
  Motion.kt          弹簧：CtMotion.spatial / spatialFast / spatialSlow / effects …（由 CtMotionTokens 构造）
  CtMotionPrefs.kt   「移除动画」检测 + 统一按压反馈 + 列表错峰入场 + 计数器动画
  Color.kt           M3 48 角色（明/暗）← CtPalette 槽位映射；CtBrandColors（品牌语义色）
  Type.kt            M3 Typography（15 baseline + 15 emphasized）← CtType
  Shape.kt           M3 Shapes（4/10/12/20/28）
  Theme.kt           ClassToolsTheme：注入 ColorScheme/Typography/Shapes + LocalCtColors/LocalCtAccents/…
  Tokens.kt          旧令牌名 CtTokens（**全部委托**到 CtDesign，仅为兼容既有调用）
```

命名迁移：旧文件 `Harmony.kt` 已并入 `CtDesign.kt`；**旧名 `HarmonyTokens` / `HarmonyMotion` / `CtText` /
`HarmonyColors` / `CtShapeTokens` / `CtTokens` 全部保留**为同值别名，页面零改动即可继续工作。

---

## 2. 令牌表

### 2.1 颜色 · 明/暗语义槽位（`CtColor`）

| 槽位 | 浅色 | 深色（默认预设、关闭动态色） | 用途 |
|---|---|---|---|
| background | `#F2F3F4` | `#181D20` | 页面底（唯一背景） |
| surface | `#FFFFFF` | `#202629` | 卡片/弹层底 |
| containerLow | `#FFFFFF` | `#232C2E` | 卡片（surfaceContainerLow） |
| container | `#F4F6F6` | `#283133` | 输入框、编号/胶囊底 |
| containerHigh | `#EDF0F1` | `#2D383A` | 选中项底、内嵌块 |
| containerHighest | `#E6EAEC` | `#354043` | 最高一层（对话框/菜单） |
| bar | `#FBFCFC` | `#1D2427` | 浮岛底栏 |
| textPrimary | `#1C2629` | `#DCE5E3` | 正文/标题 |
| textSecondary | `#4F5A5D` | `#CEDAD7` | 次级文字、区块标题 |
| textTertiary | `#61696C` | `#C5D2CF` | 说明文字、图标 |
| divider | `#E2E7E8` | `#3B4A4B` | 1dp 描边、分割线 |
| outline | `#98A4A8` | `#9CAEAC` | 控件边框（focus 前的弱边） |
| primary / onPrimary | `#24695C` / `#FFFFFF` | `#B0D8CE` / `#162E29` | 主操作、选中态（M3 品牌雾青） |
| error / onError | `#9A433E` / `#FFFFFF` | `#FFC3BB` / `#690005` | 危险操作 |
| heroPaper | `#F1F6F2` | `#2D383A` | 工作台 hero / 身份卡（鸿蒙纸面） |
| heroBorder / heroDivider | `#DCE7E2` / `#D8E4DE` | `#3B4A4B` / `#3B4A4B` | hero 发丝边/分割线 |
| heroTitle / heroSub | `#303A35` / `#5C6860` | `#DCE5E3` / `#CEDAD7` | hero 主/副文字 |
| heroAccent / heroLink | `#3F6F66` / `#4E6D8C` | `#B0D8CE` / `#B5D5DF` | 「今日」胶囊文字 / 文字链接 |
| stateLayer | `#1C2629`（8%） | `#DCE5E3`（8%） | M3 按压层 |

### 2.2 扩展色板 · 强调色（`CtAccents`，tintRef 图形档 / textRef 文字档）

| 族 | 浅色 tintRef | 浅色 textRef（对白 ≥4.5） | 深色 textRef（对深 ≥7） |
|---|---|---|---|
| teal 雾青 | `#4E9082` | `#37655B` | `#93B8AE` |
| blue 灰蓝 | `#6E8CA8` | `#4D6276` | `#9FB6D2` |
| sand 暖沙 | `#B98F63` | `#826445` | `#D2B08A` |
| leaf 叶绿 | `#5F8F5B` | `#426440` | `#A6C4A0` |
| plum 梅紫 | `#8A6A92` | `#614A66` | `#C7A9D0` |
| clay 陶红 | `#B06A55` | `#7B4A3B` | `#E0B4A4` |
| amber 琥珀 | `#C29A3B` | `#886C29` | `#DDC188` |
| water 水量蓝 | `#4A88A8` | `#356B85` | `#9CC4D8` |

**数据层不存颜色**：工具目录 `AppTool.accentFamily` 只存族名字符串（`teal/blue/sand/leaf/plum/clay/amber/water`），
页面用 `uiAccentOf(tool)` 解析成明暗两档的实际色值。新增工具只需给族名。

语义色（`CtSemantic`）：success `#3E7B5E`/`#8FCFB4`、warning `#8A6430`/`#F0C88E`、
info `#4C6B85`/`#A8C6E2`、dangerSoft `#C0605A`/`#F0B4AA`。

### 2.3 形状 / 间距 / 尺寸 / 阴影

| 令牌 | 值 | 说明 |
|---|---|---|
| CtRadius.card | 20dp | 卡片（M3 Expressive；鸿蒙 18 上移一档） |
| CtRadius.cardCompact | 18dp | 小磁贴/编辑格（鸿蒙原值） |
| CtRadius.hero | 28dp | hero / 大卡 |
| CtRadius.input | 14dp | 输入框、内嵌块 |
| CtRadius.sheetTop / dialog | 28dp | 底部弹层 / 对话框 |
| CtRadius.tile / chip | 12dp / 10dp | 图标块 / 小徽标 |
| CtRadius.pill | 999dp | 胶囊（按钮/标签） |
| CtSpace | 2/4/8/12/16/20/24/32/40 | 唯一间距刻度 |
| 页面边距 | 16 / 24 / 32dp | 手机 / 折叠 / 平板 |
| CtSize.buttonHeight | 56dp | Filled 按钮（Tonal/Text 同为 56 或 ≥48 触控） |
| CtSize.topBarHeight | 56dp | 工具页顶栏 |
| CtSize.tileMinHeight / glyphBox / glyph | 96 / 40 / 24dp | 工具磁贴 |
| CtSize.rowMinHeight | 56dp | 列表行 |
| CtSize.emptyIcon | 48dp | 空态图标 |
| CtElevation | card 1dp / hero 3dp / floating 6dp / dragged 12dp | 极浅投影（深色为 0，靠描边分层） |

### 2.4 对比度（WCAG 2.1，实测锁定在 `CtDesignTokensTest`）

| 组合 | 比值 |
|---|---|
| 浅 textPrimary / surface | 15.4 |
| 浅 textSecondary / surface | 8.6 |
| 浅 textTertiary / surface | 6.2 |
| 浅 onPrimary / primary | 6.1 |
| 深 textPrimary / surface | 14.2 |
| 深 textSecondary / surface | 7.7 |
| 深 textTertiary / surface | 6.2（最暗容器最高层 4.95） |
| 深 onPrimary / primary | 10.9 |
| hero 主/副文字（浅/深） | 10.8 / 5.3 · 12.6 / 7.2 |

### 2.5 字体（`CtType` → `CtTypeScale`，M3 Expressive 层级 + 可变字重轴）

| 语义 | 字号/行高/字重 | 用途 |
|---|---|---|
| display | 28 / 36 / Bold | 页面主标题（工作台/全部/我的 页首 `CtPageTitle`） |
| title | 24 / 32 / Bold | hero 问候、身份卡姓名 |
| headline | 20 / 27 / Bold | 工具页顶栏、弹层大标题 |
| title16 | 16 / 24 / Medium | 区块主标题、列表行标题 |
| body | 15 / 22 / Normal | 正文 |
| label | 13 / 19 / Medium | 区块标题（次级色）、按钮、文字操作 |
| caption | 12 / 17 / Normal | 说明、徽标 |
| micro | 11 / 15 / Medium | 底栏文案等装饰性小字（仅限非正文） |

字重轴：Bold 700 / SemiBold 600 / Medium 500 / Normal 400（`CtType.weight*`）。
M3 Typography（`MaterialTheme.typography`）已按同一张表重建（displayLarge 36 … labelSmall 11）。

---

## 3. 组件规范

### 3.1 卡片 `CtCard` / `CtGlassCard`（同名旧入口）/ `HomeInfoCard`
- **底** surfaceContainerLow，**20dp 圆角**，**0.5dp（浅）/1dp（深）outlineVariant 描边**，**1dp 极浅投影**（深色 0）。
- 内边距 16dp；可点击卡片：按压 spring scale **0.97** + 8% tonal 按压层（`ctPressFeedback`）。
- 列表中的卡片可传 `entranceIndex` 参与错峰入场。

### 3.2 区块标题 `CtSectionTitle`
- **13sp Medium 次级色**（`CtTypeScale.sectionTitle` = `label`），左侧留 4dp，右侧可挂文字动作（`CtTextAction`）。
- 所有页面的「数据与同步」「通知与权限」「外观」「关于与帮助」「常用工具」等分组都用它。

### 3.3 列表行 `CtListRow` + `CtRowDivider`
- 行高 **≥56dp**；左侧 **40dp 圆角图标块**（`CtIconBadge`，tint 12% 底 + 22dp 图标）；
- 标题 16 Medium + 副标题 12 次级色；尾部默认 `›` 箭头（可传 `trailing` 放 Switch/值/按钮）。
- 行间分割线 0.5dp，左侧内缩 16dp 与正文对齐。

### 3.4 按钮 `CtButton`（三档）
| 档位 | 外观 | 用途 |
|---|---|---|
| Filled | 主色胶囊，高 **56dp**，通栏 | 每屏唯一的主动作（「下一步」「开始抽签」「完成」） |
| Tonal | 主色淡底胶囊 | 次要动作（「打开课表」「去勾选」） |
| Text | 主色文字，≥48dp 触控 | 行内动作（「编辑」「全部 ›」） |

### 3.5 输入 `CtTextField`
- **14dp 圆角 tonal 底、无边框**；聚焦时底色升一档（container→containerHigh）并淡入 1dp 主色描边（200ms spring）。
- 错误态 = error 色描边；支持前后图标、单行/多行、密码遮罩。

### 3.6 空态 `CtEmptyState`
- **48dp 线性图标**（放在 20dp 圆角 container 底块中）+ 标题 16 + 说明 12 + 可选 Tonal 主操作（通栏）。
- `compact = true` 用于卡内空态（如工作台卡内「今天没课」）。

### 3.7 加载骨架 `HarmonySkeletonBlock/TextBlock/Row` · `CtSkeletonPage`
- 三档灰（浅 `#E9EFF1 / #EFF3F4 / #F5F7F8`，深 `#273235 / #222C2F / #1D2629`）；
- 900ms 线性往返呼吸（1 ↔ 0.55），系统「移除动画」时固定 1；
- **骨架与真实内容同高**（行骨架 = 40dp 图标块 + 两行文本 ≈ 56dp），避免加载完成时卡片高度跳变。

### 3.8 弹层
- 底部弹层：`CtBottomSheet`（M3 ModalBottomSheet，顶部 28dp 圆角，surfaceContainerLow）；
- 对话框：M3 `AlertDialog`（28dp 圆角，containerHigh）；
- 内容布局统一 `CtSheetContainer`：大标题 20 Bold + 说明 12 + 内容 + 底部动作条。

### 3.9 顶栏 `CtTopBar`
- 56dp 高；返回按钮 44dp 圆形热区（图标 22dp）；标题 20 Bold；右侧动作。
- 背景透明 → 页面滚动时（调用方给 `progress`）淡入一层 bar 底色 + 发丝线（与底部浮岛同一材质语言）。

### 3.10 浮岛底栏 `CtFloatingDock`
- `BAR_LIGHT` 实底（深色 surface）+ 0.5dp 发丝线，**无黑影、无玻璃高光**；
- 选中药丸：`spatialFast` spring 位移吸附（拖动跟手拉伸 8%、松手回弹）；
- 选中项主色、未选中 onSurfaceVariant，文案 11 Medium（`CtTypeScale.micro`）。

### 3.11 工具磁贴 `ToolCard` / `AllToolCard` / `QuickToolTile`
- 96dp 最小高、40dp 淡色圆角（12dp）图标块、名称 15/16 Medium、副标题 12 最多两行；
- 按压 scale 0.97 + 8% 按压层；编辑态右上 22dp 主色 ✓ 角标。

---

## 4. 动效系统

### 4.1 时长与曲线（`CtMotionTokens`）

| 令牌 | 值 | 用途 |
|---|---|---|
| fastMs | 120ms | 按压回弹、淡出、勾选 |
| shortMs | 200ms | 淡入、指示器、状态切换 |
| standardMs | 300ms | 弹层展开、容器变换交接点 |
| slowMs | 400ms | 页面转场（NavHost 非工具页） |
| ease | cubic-bezier(.4,0,.2,1) | 标准（M3 Emphasized / HarmonyOS ease） |
| staggerStepMs | 40ms | 列表错峰间隔 |

### 4.2 弹簧（M3 Expressive motion physics，mass=1）

| 名称 | damping / stiffness | 用途 |
|---|---|---|
| `CtMotion.spatial` | 0.8 / 380 | 默认空间变化（尺寸、位移） |
| `CtMotion.spatialFast` | **0.75 / 600** | 按压、指示器、**工具卡⇄工具页容器变换** |
| `CtMotion.spatialSlow` | 0.9 / 200 | 大面积变化、弹层（不过冲） |
| `CtMotion.effects` | 1.0 / 1600 | 颜色/透明度（临界阻尼） |

### 4.3 页面与容器变换（`ui/navigation/ToolTransition.kt`）

**旧实现（已删除）**：返回时 `View.draw()` 截整个界面为 Bitmap，再把它缩回卡片 —— 「快照遮底」补丁，
在 WebView/SurfaceView 页面会空白，且动作与页面本身脱节。

**现实现（共享容器变换 container transform）**：
1. 卡片通过 `Modifier.ctToolTransitionSource(toolId)` 把自身根坐标 bounds 登记到 `ToolTransitionAnchors`；
2. 进入：覆盖层从**卡片 bounds** 经 `CtMotion.spatialFast` 长出全屏 —— 位置/尺寸/圆角
   （20dp → 0dp）同一条弹簧驱动；顶部 40% 淡入、形变过半（150ms）时 push 目标页，尾部 35% 淡出交接；
3. 返回：先 `popBackStack()`（底层始终是干净的根页），覆盖层以全屏开始、经同一弹簧缩回卡片并淡出；
4. NavHost 的工具页 push/pop 转场设为 `None`，避免「页面滑走 + 卡片收回」双重动画；
5. 系统「移除动画」时直接跳转，无覆盖层。

**非工具页**（投票 / PPT / 数据隐私 / 权限 / 反馈 / 使用说明…）使用 `CtPageTransitions.harmonyPushEnter()` 等：
右移 7.8%（≈28vp）+ 淡入 + 0.985 缩放，时长 400ms / 标准缓动；返回反向 6.7%（≈24vp）+ 0.99。

### 4.4 列表错峰入场（`CtAnimatedEntrance` / `rememberStaggeredEntry`）
- fade + 上移 8dp；第 i 项延迟 `i × 40ms`（最多 8 项封顶）；进度用 `CtMotion.pageTween`；
- 应用：工作台区块（`HomeReveal`）、全部工具编辑网格、工具列表、`CtCard(entranceIndex=…)`；
- 预览渲染或系统「移除动画」时直接终态（无闪烁、无延迟）。

### 4.5 按压反馈（`ctPressFeedback`）
- scale → **0.97**（spring 0.75/600）+ 按压层 → **8%** state layer；
- 所有卡片/磁贴/列表行统一；「移除动画」时为 snap。

### 4.6 遵循系统「移除动画」
`rememberReduceMotion()` 读取 `Settings.Global.ANIMATOR_DURATION_SCALE`（辅助功能「移除动画」即为 0）与
`ValueAnimator.areAnimatorsEnabled()`，并用 `ContentObserver` 实时监听切换。命中时：
容器变换直接跳转、错峰入场瞬显、按压不再缩放、骨架不再呼吸、计数器直接显示目标值。
（`shouldReduceMotion()` 为纯函数，单测覆盖。）

---

## 5. 各页面使用规则

| 页面 | 结构 | 关键令牌/组件 |
|---|---|---|
| 启动页 `BootSplash` | 品牌标 72dp + 站名 + 小进度环 | `BrandMark` / `CtTypeScale.headline` / `CircularProgressIndicator` |
| 身份页 `IdentityScreen` | 品牌标 + 标题 + 卡片（两个输入 + 勾选 + 主按钮） | `CtTextField` / `CtButton(Filled)` / `CtCard` |
| 工作台 `HomeScreen` | hero（纸面卡，三色 3px 顶条 + 「今日」胶囊 + 问候 24 + 摘要）→ 今日课程 → 电费 → 作业 → 通知 → 常用工具 | `HomeHeroCard` / `HomeInfoCard`（40dp badge + 13 区块标题）/ `QuickToolTile`；单列，内容限宽 880/960 |
| 全部工具 `AllToolsScreen` | 28 Bold 页首 + 「编辑首页」13 主色 + 13 个工具按分类分组（学习/班级事务/系统）+ 2/3/4 列磁贴 | `CtPageTitle` / `CtSectionTitle`（分组用 headline 20）/ `AllToolCard`；编辑态长按拖动 + ✓ 角标 |
| 我的 `ProfileScreen` | 28 Bold 页首 + 身份卡 + 数据与同步（2×2 指标 + 开关 + 两行）+ 通知与权限 + 外观 + 关于与帮助 + 退出 | `CtPageTitle` / `CtGlassCard` + `CtListRow` / `CtRowDivider` / `Switch` / 单列限宽 720 |
| 工具页（全部 17 个） | `CtTopBar` 56dp 返回 + 20 Bold 标题 → 页面卡片（`CtCard`） | 顶栏/卡片/按钮/输入全部来自组件库；页内 13 号分组标题、20 号大标题 |
| 弹层（底部弹层/对话框） | 28dp 圆角 + `CtSheetContainer` | `CtBottomSheet` / `AlertDialog` |
| 空态 | 卡片内或整页 | `CtEmptyState`（48dp 线性图标） |
| 加载 | 首屏/区块 | `CtSkeletonPage` / `HarmonySkeletonRow`（与内容同高） |

### 5.1 平板断点（`CtAdaptive` / `rememberCtAdaptiveSpec`）
- **<600dp**（手机竖屏）：单列、2 列磁贴、页面边距 16dp；
- **600–839dp**（小平板/折叠展开/手机横屏）：单列限宽 840、3 列磁贴、边距 24dp；
- **≥840dp**（平板横屏/大屏）：双栏（主栏 + 辅助）、4 列磁贴、内容限宽 1200、边距 32dp；
- 所有页面用 `CtAdaptiveContent` / `spec`，**不要**直接读 `LocalConfiguration.screenWidthDp`。

---

## 6. 深色模式

- 默认深色的背景、低层卡片与最高层容器依次为 `#181D20`、`#232C2E`、`#354043`。九套预设与系统动态色均经过交互状态对比度校正。
- 预设强调色用于主按钮、选中胶囊与图标。
- 深色下阴影 = 0（深底投影不可见），层次靠 1dp 描边 + 容器明度差；
- 系统栏图标随主题切换（`ClassToolsTheme` 内设置 `isAppearanceLightStatusBars`）；
- 外观三档（跟随系统 / 浅色 / 深色）由「我的 → 外观」控制（`AppearanceMode`）。

---

## 7. 硬编码自查（提交前必跑）

```powershell
pwsh -File docs/design/grep_hardcode.ps1            # 汇总计数（应为 0）
pwsh -File docs/design/grep_hardcode.ps1 -Verbose   # 逐条列出
```

规则：扫描 `ui/**`（排除唯一令牌源 `ui/theme/` 与 `@Preview` 的 `ui/preview/`），统计
`Color(0x…)`、`Color.White/Black/…`、`RoundedCornerShape(数字)`、`fontSize = 数字.sp`、`数字.sp` 字面量；
允许的唯一例外需在行尾标注 `设计令牌-允许`（当前仅 1 处：`JwEntryScreen` 的 WebView 系统白底）。

重构前 **240 处** → 重构后 **0 处**。

---

## 8. 验收证据

| 项 | 位置 |
|---|---|
| 截图矩阵（浅/深 × 首页/全部/我的/工具页 STEP1·STEP2/结果/弹层） | `docs/design/shots/matrix_light_dark.png`（以及 `light_*.png` / `dark_*.png`） |
| 进/返动效抽帧（4× 慢速录屏抽帧，可直接看到卡片→页面形变） | `docs/design/shots/anim_enter_slowmo.png` / `anim_back_slowmo.png` |
| 原始录屏 | `docs/design/shots/anim_enter.mp4` / `anim_back.mp4` / `slow_enter.mp4` / `slow_back.mp4` |
| 令牌与 AA 锁定测试 | `app/src/test/kotlin/.../ui/theme/CtDesignTokensTest.kt`、`HarmonyTokensTest.kt`、`ImmersiveLightTest.kt`、`ui/components/dock/CtPageTransitionsHarmonyTest.kt` |
| 单测 | `gradle testDebugUnitTest`：**1678 / 0 失败**（基线 1654） |
| 构建 | `gradle assembleDebug` / `assembleRelease`：成功（beta11 release 5,836,925 B） |

---

## 9. 已知取舍（如实）

1. **圆角/标题字号与鸿蒙端不同**：卡片 18→20、hero 24→28、页面主标题 26→28、换行间距按 M3 Expressive 上移一档；
   跨端「同一套令牌语义」，但 Android 按自身平台节奏呈现（与 M3 指南一致）。
2. **底部指示器**：浮岛底栏药丸用 spring 位移吸附 + 跟手拉伸，未做 M3 NavigationBar 的「形状变形（胶囊⇄长条）」——
   当前 3 个 tab 的胶囊已足够表达选中，形状变形会与底栏固定宽度冲突。
3. **可变字体轴**：用系统字体的 weight 轴（400/500/600/700）表达层级；未内置可变字库（体积与零外部资源约束）。
4. **容器变换的覆盖层内容**：形变过程中显示「工具图标 + 名称」作为容器内容（真实页面在半程后淡入）；
   不是逐像素的 shared-element（不引入实验性 SharedTransitionLayout，避免与 NavHost 生命周期耦合）。
5. **透明状态栏/沉浸式**：沿用既有 `CtImmersiveStatusBar`（隐藏状态栏 + 顶部安全区），本次未改其逻辑。

---

## 10. 沉浸光感（Immersive Light，2026-11-19）

参考：[华为 HarmonyOS 沉浸光感设计语言](https://developer.huawei.com/consumer/cn/doc/doccenter-ux-design/immersivelight-0000002612101053)。
目标不是给页面加一层特效，而是把「交互层」从实色平面变成**有光学扩散与透光感的数字介质**：
光在层级间柔和漫溢，内容可透过浮层隐约可见，同时正文/按钮保持清晰可读。

### 10.1 令牌与映射表（唯一来源）

| 层 | 文件 | 说明 |
|---|---|---|
| 纯数据令牌 | `ui/theme/ImmersiveLight.kt` | 5 档材质 × 3 档强度 → `CtLightSpec`（可 JVM 单测） |
| 场景语义 | 同上 `CtLightScene` | 页面只声明「这是什么位置的浮层」，档位由映射表决定 |
| Compose 材质 | `ui/components/light/CtLight.kt` | `Modifier.ctLightMaterial(scene, shape, …)` |
| 场景容器 | `ui/components/light/CtLightScenarios.kt` | `CtLightPage` / `CtLightTopBar` / `CtLightBottomBar` / `CtLightBottomScrim` / `CtLightPanel` / `CtLightBackdrop` |
| 弹层/对话框 | `ui/components/light/CtLightOverlays.kt` | `CtLightAlertDialog`、`ctLightSheetContainerColor()`、`ctLightDialogContainerColor()` |

5 档材质（与鸿蒙同名）：`ULTRA_THIN / THIN / MEDIUM / THICK / ULTRA_THICK`。

官方场景 → 档位（`CtLightScene.level`）：

| 场景 | 档位 | 落地位置 |
|---|---|---|
| 顶部悬浮 | `ULTRA_THIN` | `CtTopBar` / `CtLightTopBar`（滚动 8dp 起渐显，48dp 内到位） |
| 底部悬浮 | `THIN` | 浮岛 dock、Draw/Vote 底部操作条、`CtLightBottomScrim` |
| 半模态/底部弹层 | `ULTRA_THICK` | `CtBottomSheet`（全部工具弹层共用） |
| 对话框 | `ULTRA_THICK` | `CtLightAlertDialog`（全部 AlertDialog 已迁移） |
| 菜单/临时浮层/Toast/Snackbar | `THICK` | Snackbar 包裹层、菜单、轻提示 |
| 工具条/页面内浮层 | `MEDIUM` | 筛选条、地图浮条、统计浮卡 |
| 媒体播放器浮层 | `THICK`（暗色媒体定制） | PPT 播放器控制条/章节/缩略图（保留媒体暗底） |

强度（我的 → 外观 → 沉浸光感）：**强 / 均衡（默认）/ 弱 / 关闭**，持久化在 DataStore
（`AppSettingsStore.immersiveLight`，冷启动镜像同步，避免首帧跳变）。强档更通透、高光更亮；
弱档更接近实色；关闭退回既有实色材质。

### 10.2 全应用覆盖点（深层页面）

- **页面背景**：`ExpressiveBackground` 统一改为 `CtLightBackdrop`（品牌柔光斑 + 纯色底，强度随设置）→
  所有使用该背景的页面自动获得全局光感，不需要逐页加装饰。
- **顶栏**：`CtTopBar` / `CtLightTopBar` 全部走 ULTRA_THIN 材质 + 底部渐变融合带；
  页面滚动量由 `scrollPx` 驱动（Homework / VoteCreate / VoteDetail / Roster / DataPrivacy / 权限 / 提醒 / 占位页等）。
- **底部**：浮岛 dock、Draw/Vote 底部操作条走 THIN + 底部渐变蒙层。
- **弹层**：`CtBottomSheet` 容器色 = 超厚档安全不透明度 + 材质 tint；`CtLightAlertDialog` 覆盖全部确认框。
- **Snackbar**：14 个页面统一包裹 THICK 材质。
- **PPT 播放器**：暗色媒体底保留 `CtPalette.MediaScrim*`，控制条/章节/缩略图使用媒体浮层（THICK 语义），
  不把浅色材质铺到视频画面上。

### 10.3 可读性与性能红线

1. 承载正文的材质（弹层/菜单/浮卡）自动抬到 `TEXT_SAFE_ALPHA = 0.78`；
   `ctLightContrastFloor()` 校验 WCAG 2.1 AA ≥ 4.5（浅/深色、正文三级文字，单测锁定）。
2. 单页常驻材质最多 2 个（顶栏 + 底栏），叠加弹层最多 3 个（`CtLightBudget`）。
3. **不采用整层 RenderEffect**：Compose 无公开 backdrop-filter，若给容器挂 `BlurEffect` 会把标题/图标一起糊掉
   （2026-11-19 实测底部 dock 文字被糊）。当前实现 = 半透明底 + 顶部高光 + 边缘折射 + 渐变融合带，
   视觉上是「被光柔化的薄雾」，且真机文字清晰；后续若引入可靠的 backdrop 采样库再升级为真模糊。
4. 光斑背板为静态绘制（不逐帧动画），滚动列表不重复采样。
5. 设备能力：API 31+ 记为 FULL、API 26–30 为 DEGRADED（本实现两者视觉一致，能力档预留给未来真模糊）；
   关闭光感 = OFF。

### 10.4 验收

- 单测：`ImmersiveLightTest`（9 项：档位单调性、强度方向、场景映射、AA 对比度、顶栏阈值、能力档、预算、持久化往返）。
- 截图：`docs/design/shots/light-01-identity.png`（身份页光晕）、`light-09-home-final.png`（首页 + 浮岛）、
  `light-10-all.png`（全部工具）、`light-12-tool-topbar.png`（工具页顶栏材质）、`light-08-dark-home.png`（深色）、
  `light-07-appearance.png`（外观四档设置）。
- 构建：`assembleDebug` / `assembleRelease` 通过。


---

## 11. 自定义背景（Custom Background，2026-11-19）

> 以下记录保留当时的预设与存储设计。当前界面使用随主题取色的预设缩略图和 0–40 dp 照片模糊滑块。[当前深色规范](../design/DARK_COMFORT_2026-11-26.md)

参考：鸿蒙「个性化 · 背景」与官方沉浸光感（背景可模糊、前景绝不模糊）。
入口：**我的 → 背景**（位于「外观」之上，紧邻身份/同步信息，一屏可达）。

### 11.1 数据与隐私边界（红线）

| 项 | 策略 |
|---|---|
| 图片存放 | `filesDir/backgrounds/<bg_时间戳>.jpg`（App 私有目录，随卸载删除） |
| 图片压缩 | 解码 → 长边缩到 **≤2048px**（`inSampleSize` 先粗缩再精确缩放）→ **JPEG 质量 85** 落盘 |
| 选图入口 | 系统照片选择器 `ActivityResultContracts.PickVisualMedia(ImageOnly)`（无存储权限；低版本自动回退 GetContent） |
| 偏好存储 | `SharedPreferences("ct_background_prefs").getString("ct_background_selection")` |
| 上传/同步 | **不上传、不进 userdata v6 云同步、不进任何导出**（纯本机偏好，与外观模式同层） |
| 删除 | 删偏好 + 删 `filesDir/backgrounds/bg_*.jpg`（两处都做，避免残留占空间）；恢复默认「暖白」 |
| 路径安全 | 偏好只存**文件名**（不存绝对路径）；读取前校验 `bg_[A-Za-z0-9_-]+.jpg` + `canonicalFile` 必须在私有目录内（防路径穿越，单测覆盖） |

### 11.2 持久化契约

`storageValue` 格式（**禁止改名**，老版本读不懂就回落默认）：

```
warm_white
deep_space|custom:bg_1700000000000.jpg|blur:2
```

- 第 1 段 = 预设 id（`warm_white / mist_teal / slate_blue / warm_sand / deep_space /
  morning_mist / sunset_angle / teal_indigo / nightfall`）；
- `custom:<文件名>` 存在 = 正在使用自定义图片；
- `blur:0..3` = 背景模糊档（缺省 = 2「标准」）；
- 任何非法/未知值 → 回退默认暖白（不抛异常、不丢其他设置）。

### 11.3 预设与明暗参数（唯一映射表 `CtBackgroundPalette`）

| 预设 | 浅色底 | 深色底 | 类型 |
|---|---|---|---|
| 暖白（默认） | `CtPalette.Bg` | `DarkBg` | 纯色 |
| 雾青 | Bg + 雾青 10% | DarkBg | 纯色 |
| 灰蓝 | Bg + 灰蓝 10% | DarkBg | 纯色 |
| 暖沙 | Bg + 暖沙 12% | DarkBg | 纯色 |
| 深空 | `DarkBg` | `DarkBg`（+深一档） | 纯色 |
| 晨雾渐变 | Bg → Bg+雾青 16% | DarkBg | 线性渐变 |
| 斜阳渐变 | Bg+暖沙 18% → Container+暖沙 10% | DarkBg | 线性渐变 |
| 青黛渐变 | Bg+雾青 14% → Bg+灰蓝 16% | DarkBg | 线性渐变 |
| 夜幕渐变 | DarkBg → DarkBg+雾青 16% | DarkBg → 青 20% | 线性渐变 |

每个预设都带 4 个可读性参数：`headlineOnBackground`（标题对背景）、`cardAlphaBoost`（卡片玻璃补偿）、
`scrimTop/scrimBottom`（顶部/底部蒙层）、`bloomAlpha`（光感柔光斑强度）。
**自定义图片**在此基础上再抬一档：卡片补偿 **+0.18**、顶部 scrim **0.84（深）/0.82（浅）**、
底部 0.88（深）/0.86（浅）；旧机（无 GPU 模糊）额外压一层 0.85/0.90 底色蒙层。
该不透明度按「任意图片（纯黑/纯白）最坏情况」计算仍满足标题 AA（单测 `CtBackgroundTest`）。

### 11.4 渲染层序（`CtBackgroundLayer`，前景绝不模糊）

```
ExpressiveBackground
 ├─ CtBackgroundLayer            ← 背景层（唯一被模糊的层）
 │   ├─ 1) 底色 / 预设渐变
 │   ├─ 2) 自定义图片 ContentScale.Crop + graphicsLayer{renderEffect=BlurEffect}（仅 API31+；clip=true）
 │   ├─ 3) 预设光感柔光斑（仅图片未启用时）
 │   └─ 4) 顶部/底部/中间四段 scrim（保证顶栏、dock 附近文字可读）
 └─ CompositionLocalProvider(LocalCtBackgroundCardBoost) { 页面内容 }   ← 前景，永远锐利
```

- 模糊只作用在**图片图层**（`Image` 的 `graphicsLayer.renderEffect`），父容器/卡片/文字在**另一个子节点**，
  因此不会被糊（这是需求硬红线；beta11 曾因给容器挂 RenderEffect 把 dock 文字糊掉而放弃整层模糊）。
- 旧机（API 26–30）：`ctBackgroundBlurSigma()` 返回 0，改画底色蒙层，观感一致、可读性不降。
- 背景层静态绘制（不逐帧动画/不采样列表），滚动只重绘前景。

### 11.5 设置 UI（`BackgroundSection`）

3×3 预设网格（点按 ≥48dp、选中 = 2dp 主色描边 + ✓；缩略图始终用**浅色参数**展示预设本色，避免深色模式下 8 个浅色预设全显示为黑色）→「从相册选择图片」行（副标题说明只存本机/长边/质量）
→ 已选图片时出现「背景模糊：不模糊/轻/标准/强」四档 + 「删除」。全部状态即时生效并持久化。

---

## 12. 磨砂玻璃卡片（Frosted Glass Card，2026-11-19）

> 本节记录早期绘制器的实现。当前深色卡片的材质、亮度保护和降级采用 2026-11-26 验收表。[当前深色规范](../design/DARK_COMFORT_2026-11-26.md)

**所有卡片一个材质来源**：`ui/components/background/CtGlassSurface.kt`（`CtGlassTokens` 为唯一映射表）。

### 12.1 材质分层

1. **半透明底**：浅色 = 白 `0.55–0.70`、深色 = 深容器 `0.45–0.60`（按沉浸光感强度取档）；
2. **顶部线性高光**：白 `0.10–0.22` → 0（42% 处透明），表现「玻璃被上方光照亮」；
3. **1px 高光边**：顶部亮边（浅 0.40–0.60 / 深 0.18–0.26）+ 底部极淡暗边（塑造厚度，深色必留）；
4. **柔和外阴影**：浅色 `CtElevation.card`（1dp、墨色 10%）；深色 0（深底投影不可见，靠高光边分层）。

### 12.2 映射表（明暗 × 强度）

| 强度 | 浅色 baseAlpha | 深色 baseAlpha | 浅色 顶高光 | 深色 顶高光 |
|---|---|---|---|---|
| 强 | 0.55 | 0.45 | 0.22 | 0.16 |
| 均衡（默认） | 0.62 | 0.52 | 0.18 | 0.13 |
| 弱 | 0.68 | 0.58 | 0.14 | 0.10 |
| 关闭 | 0.92 | 0.94 | 0.06 | 0.05 |

**背景补偿**：`resolvedAlpha = clamp(baseAlpha + boost, MIN_BODY_ALPHA 0.62, MAX_BODY_ALPHA 0.92)`，
`boost` 来自当前背景预设/图片（`CtBackgroundColors.cardAlphaBoost`，图片再 +0.18）——
背景越花，卡片越实，形成「真实磨砂」但绝不牺牲可读性。

### 12.3 接入点（页面零改动）

| 组件 | 说明 |
|---|---|
| `CtCard`（`ui/components/CtLayout.kt`） | 直接使用 `CtGlassSurface`；`frosted=false` 可退回实色 |
| `CtGlassCard`（旧名保留） | 同上（我的页 `GroupCard`、身份卡、hero 等全部经此） |
| `ToolCard` / `AllToolCard` | 工作台快捷工具 / 全部工具磁贴 |
| `CtGlassSurface` | 新增底层容器：`CtGlassSurface(onClick=…, boost=…, frosted=true)` |

**不玻璃化的部分（有意）**：列表行 `CtListRow`、输入框 `CtTextField`、弹层正文容器、以及自定义图片背景下的
所有承载正文的卡片——它们保持实色 `container` 底（`frosted=false`/原实现），避免「玻璃套玻璃」导致可读性下降。

### 12.4 AA 红线（单测锁定）

`CtGlassTokensTest` 对 **9 预设 × 明暗 × 3 强度** 全组合断言：
正文（`TextPrimary`/`DarkTextPrimary`）与次级文字（`TextSecondary`/`DarkTextSecondary`）压在
「玻璃底 + 最坏底层」合成色上的对比度 **≥ 4.5**（复用 `ctLightContrastFloor`）。当前最差组合 ≥ 5.4。

---

## 13. 排版与间隔（Harmony Typography & Spacing，2026-11-19）

参考：[鸿蒙文本排版](https://developer.huawei.com/consumer/cn/doc/doccenter-ux-design/typography-0000002622688363)、
[鸿蒙间隔参数](https://developer.huawei.com/consumer/cn/doc/doccenter-ux-design/interval-parameter-0000002562577161)。

### 13.1 字号阶梯（`CtType` / `CtTypeScale`）

| 语义 | 字号/行高/倍数/字重 | 用途 |
|---|---|---|
| display | **28 / 40 / 1.43 / Bold** | 页面主标题（工作台/全部/我的） |
| title | **24 / 36 / 1.50 / Bold** | hero 问候、身份卡姓名、指标数字 |
| headline | **20 / 28 / 1.40 / Bold** | 工具页顶栏、分组大标题、弹层大标题 |
| title16 | **16 / 24 / 1.50 / Medium** | 区块标题、列表主文字 |
| body | **14 / 22 / 1.57 / Regular** | 正文 |
| label | **14 / 20 / 1.43 / Medium** | 按钮、标签、区块标题 |
| caption | **12 / 18 / 1.50 / Regular** | 说明、徽标、页脚 |
| micro | 11 / 16 / Medium | 底栏文案（装饰性） |

字重只有 **Regular 400 / Medium 500 / Bold 700** 三档（旧 600 已并入 500）。
相对 beta11 的变化：body 15→14、label 13→14、caption 行高 17→18、display 行高 36→40、
headline 行高 27→28、title 行高 32→36。

### 13.2 间距（`CtSpace`，8vp 网格）

| 令牌 | 值 | 语义 |
|---|---|---|
| gridUnit | 8vp | 网格基本单位 |
| xxs / xs / sm | 4 / 8 / 12 | 半格、控件间距 |
| md / lg / xl | 16 / 20 / 24 | 卡片内边距 / 页面边距 / 区块间距 |
| xxl / xxxl / huge | 32 / 40 / 48 | 大分区 |
| **pagePhone** | **20vp**（原 16） | 屏幕边缘（手机） |
| pageFold / pageTablet | 24 / 32 | 折叠 / 平板 |
| cardPadding | 16vp | 卡片内边距 |
| rowPaddingV / rowPaddingH | 8 / 16 | 列表行内边距 |
| cardGap / sectionGap / groupGap | 16 / 24 / 32 | 卡片 / 区块 / 大分区 |

细则：所有 spacing 令牌是 **4 的倍数**（半格），语义间距（控件/卡片/区块）是 **8 的倍数**；
页面边距三档 20/24/32 与 `CtTokens.Spacing.pageHorizontal*` / `CtAdaptive.horizontalPaddingDp()` 同源，
因此首页/全部/我的/工具页/设置页**一次性全部**按新边距生效（有效内容宽 = min(屏幕, 上限) − 2×20）。

### 13.3 迁移影响与自查

- 令牌变更后回归了 `CtDesignTokensTest` / `HarmonyTokensTest` / `AdaptiveLayoutTest` 中的旧断言（已按新规范更新）。
- 新增 `CtSpacingTokensTest`（14 项）：8vp 网格、4/8 倍数、页面边距 20/24/32、
  字号层级 28/24/20/16/14/12、行高 1.4–1.6、字重三档、`CtTypeScale` 与令牌一致。
- 硬编码自查 `docs/design/grep_hardcode.ps1` 仍为 **0**（本轮把 14 处 Snackbar 圆角、光感高光/折射色收敛进令牌）。

### 13.4 工具页控件间隔（按钮 / 选项，2026-11-20）

用户反馈「工具页内部的按钮/选项间隔过近」。按 [鸿蒙间隔参数](https://developer.huawei.com/consumer/cn/doc/doccenter-ux-design/interval-parameter-0000002562577161)
对控件间距的三档（8 / 12 / 16vp）收敛成一条可核查的规则：

| 场景 | 令牌 | 值 |
|---|---|---|
| 相邻两个**可点控件**（文字按钮 / 图标按钮 / 筛选胶囊 / 分段选项 / 统计筛选卡） | `CtSpace.controlGap` | **8vp（下限）** |
| 有明显边界的控件成组（整块按钮、列表行） | `CtSpace.controlGapMedium` | 12vp |
| 独立成块的操作区 | `CtSpace.controlGapLarge` | 16vp |

不受这条约束的：图标与文字之间（4–6vp）、主次文本之间（`CtSpace.xxs` 4vp）、
以及分段控件内部的两个选项（同一个复合控件）。

本轮把工具页里低于 8vp 的控件间隔统一提到 `CtSpace.controlGap`：

| 位置 | 原值 | 现值 |
|---|---|---|
| 体测 · 历史记录「回填 / 改名 / 删除」 | 4vp | 8vp |
| 点名 · 操作行（沿用抽签名单 / 全选 / 清空 / 重置本轮） | 6 / 4vp | 8 / 8vp |
| 通知 · 统计筛选卡（待处理 / 紧急 / 本周 / 长期） | 6vp | 8vp |
| 通知 · 筛选胶囊行 | 6 / 6vp | 8 / 8vp |
| 名单 · 筛选行（已加入点名 / 全选点名） | 6vp | 8vp |
| 课表 · 顶栏两组控件（周切换 / 视图切换） | 6vp | 8vp |
| 演示 · 下载详情「在线预览 / 重新处理」 | 4vp | 8vp |
| 文件传输 · 下载 / 删除 | 4vp | 8vp |
| 身份核验 · 《用户协议》/《隐私政策》 | 4vp | 8vp |

诊断页（2026-11-21 第 2 轮，用户反馈「诊断页的间距还是有问题」）：

| 位置 | 原值 | 现值 |
|---|---|---|
| 摘要卡 ↔ 下方第一张卡片 | 0vp（两卡圆角相压） | 12vp（`CtSpace.sm`） |
| 诊断页卡片 ↔ 卡片 | 10vp（不在 4/8 栅格） | 12vp（`CtSpace.sm`） |
| 摘要卡「最后更新」↔ 上一行 | 0vp | 4vp（`CtSpace.xxs`） |
| `DiagCard` 标题行 ↔ 正文 | 0vp | 8vp（`CtSpace.controlGap`） |
| `DiagCard` 内容行之间 | 6vp | 8vp |
| `DiagCard` 标题 / 状态胶囊 / 右上角操作 | 0vp | 8vp |
| 设备检查行「上报」↔ 状态胶囊 | 0vp | 8vp |
| 诊断顶栏动作（胶囊 / 简洁·详细 / 暂停 / 刷新） | 4vp（顶栏默认） | 8vp（仅本页） |

前后对照实拍：`docs/design/shots/diag-before.png` / `diag-after.png`。

`CtSpacingTokensTest` 新增 `control gap floor is 8vp and tiers are ascending` 锁定 8/12/16 三档与
「控件间距不得大于卡片间距」。新增控件时请直接用 `CtSpace.controlGap`，不要写字面量 `4.dp`/`6.dp`。

---

## 14. 2026-11-19 深色工具栏优化（Dark Toolbars）

> 本节保留 2026-11-19 的版本记录。当前深色吸顶栏使用不透明栏色。[当前深色规范](../design/DARK_COMFORT_2026-11-26.md)

用户反馈「深色下工具栏灰糊」。根因：浮岛 dock 与顶栏在深色里都用 `surfaceContainerLow`/`DarkContainer`，
与页面底 `DarkBg` 只差 3–4 个色阶，材质又半透明 → 轮廓消失。

| 位置 | 改法 |
|---|---|
| 浮岛 dock | 底色改 `CtPalette.DarkBar`(#171C1F) × **0.92**（比页面底更暗）；顶部线性高光 白 0.10；**1px 高光边 白 0.18** + 底部暗边 黑 0.35；选中药丸 = `primary` 0.22 低饱和雾青 + **1px 高光边 白 0.14** |
| 顶栏 | 深色下改用 `THICK` 材质（更大模糊 + 更高底色），并补 1px 高光边 白 0.14（随浮层进度 α） |
| 卡片 | 玻璃底 + 顶部高光 + 1px 高光边（见 §12），深色 0 阴影靠高光边分层 |
| 文字 | 选中 dock 项 `primary`（对 DarkBar ≥ 7:1）、未选中 `onSurfaceVariant`（≥ 5.3:1）——AA |

对比截图：`docs/design/shots/dark-dock-before.png`（beta11）与 `dark-dock-after.png`（本轮）。

---

## 15. 验收证据（2026-11-19 第 2 轮）

| 项 | 结果 |
|---|---|
| 单测 | `:app:testDebugUnitTest` **1725 / 0 失败 / 2 skipped**（新增 47：背景 26 + 玻璃 11 + 间距排版 14，扣除重命名合并） |
| 构建 | `assembleRelease` 成功：**v1.0.0-beta12 / vc95**，APK **5,853,309 B / SHA-256 `CBFE71FC…B3A86C`**（最终包，含玻璃底修正与背景动画修复） |
| 硬编码自查 | `grep_hardcode.ps1` = **0** |
| 模拟器（emulator-5562，1440×3200，API 37） | 浅/深 × 首页/全部/我的/背景选择器/自定义图片/工具页实拍：`v2-beta12-light-custom.png`、`v2-beta12-dark-custom.png`、`v2-dark-all.png`、`dark-*-before/after.png` |
| 端到端 | 系统照片选择器 → 选图 → 落盘 `files/backgrounds/bg_1795097692069.jpg`（72,862 B）→ 偏好 `warm_white|custom:bg_…\|blur:2` → 全部页面背景即时生效（含深色与工具页） |

### 15.1 残留风险与未验证项（如实）

1. **「深色 + 自定义图片」已复拍**：beta12 最终包在重建的 AVD 上实拍（`v2-beta12-dark-custom.png`），
   深色卡片用 surfaceContainer 玻璃底，不再灰糊；另修复 `HomeReveal` 未 remember Animatable 导致
   自定义背景触发重组时整页停留在半透明入场态的问题（表现为文字/卡片发灰，已通过像素采样 + 截图确认恢复）。
2. **大屏/平板未实拍**：本轮只验证 1440×3200（411dp 宽）手机竖屏；页面边距 20dp 在 600/840dp 断点下走
   24/32（`CtAdaptive`），未逐屏实拍 360–430dp 与平板组合（横向溢出风险低：内容宽度只减不增）。
3. **背景图片内存**：2048 长边 ARGB_8888 ≈ 16MB 常驻（1 份，全局共享）；未做「按窗口尺寸二次降采样」，
   低端机 2GB RAM 场景需注意。
4. **照片选择器**：依赖系统 `PickVisualMedia`（Android 13+ 原生）；Android 12 及以下由 AndroidX 回退到
   `ACTION_GET_CONTENT`，未在 API 26–30 设备实测。
5. **沉浸光感「关闭」时的玻璃**：卡片退化为 0.92/0.94 近实色（仍带 1px 高光边），与 beta11 的纯实色略有差别，
   如需严格回退可把 `CtGlassTokens.*(enabled=false)` 的 `baseAlpha` 设为 1.0。
