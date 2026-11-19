# 班级工具站 · Android 统一设计规范（DESIGN_SYSTEM）

> 版本：v1.0（2026-11-19「系统性视觉/动效重构」）　适用：`android-class-tools` 全部页面
> 唯一令牌源：`app/src/main/kotlin/com/furina/classtools/ui/theme/CtDesign.kt`
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
3. **禁止风格化残留**：玻璃（半透明 + 折射高光 + 内阴影）、渐变（hero 渐变、按钮渐变）、噪点、暗角全部移除；
   背景就是纯色 `CtPalette.Bg`（深色 `DarkBg`），层次只靠「卡片 + 描边 + 极浅投影 + 间距」。
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

| 槽位 | 浅色 | 深色 | 用途 |
|---|---|---|---|
| background | `#F2F3F4` | `#121517` | 页面底（唯一背景） |
| surface | `#FFFFFF` | `#1A1E21` | 卡片/弹层底 |
| containerLow | `#FFFFFF` | `#1A1E21` | 卡片（surfaceContainerLow） |
| container | `#F4F6F6` | `#1E2326` | 输入框、编号/胶囊底 |
| containerHigh | `#EDF0F1` | `#232A2E` | 选中项底、内嵌块 |
| containerHighest | `#E6EAEC` | `#2B3338` | 最高一层（对话框/菜单） |
| bar | `#FBFCFC` | `#171C1F` | 浮岛底栏 |
| textPrimary | `#1C2629` | `#E7ECEB` | 正文/标题 |
| textSecondary | `#4F5A5D` | `#A8B4B7` | 次级文字、区块标题 |
| textTertiary | `#61696C` | `#96A3A6` | 说明文字、图标 |
| divider | `#E2E7E8` | `#2C353A` | 1dp 描边、分割线 |
| outline | `#98A4A8` | `#6E7B7F` | 控件边框（focus 前的弱边） |
| primary / onPrimary | `#24695C` / `#FFFFFF` | `#90D3C4` / `#00382F` | 主操作、选中态（M3 品牌雾青） |
| error / onError | `#9A433E` / `#FFFFFF` | `#FFB4AB` / `#690005` | 危险操作 |
| heroPaper | `#F1F6F2` | `#1C2427` | 工作台 hero / 身份卡（鸿蒙纸面） |
| heroBorder / heroDivider | `#DCE7E2` / `#D8E4DE` | `#334044` / `#364448` | hero 发丝边/分割线 |
| heroTitle / heroSub | `#303A35` / `#5C6860` | `#E7ECEB` / `#A8B4B7` | hero 主/副文字 |
| heroAccent / heroLink | `#3F6F66` / `#4E6D8C` | `#8FD0C3` / `#A9C4DA` | 「今日」胶囊文字 / 文字链接 |
| stateLayer | `#1C2629`（8%） | `#E7ECEB`（8%） | M3 按压层 |

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

- 完整色板见 §2.1；深色不是「浅色反相」，而是中性深板岩（`#121517 / #1A1E21 / #232A2E`）+ 抬升的容器层级；
- 品牌绿只做小面积强调（主按钮、选中胶囊、图标 tint），不铺大面积；
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
| 令牌与 AA 锁定测试 | `app/src/test/kotlin/.../ui/theme/CtDesignTokensTest.kt`、`HarmonyTokensTest.kt`、`ui/components/dock/CtPageTransitionsHarmonyTest.kt` |
| 单测 | `gradle testDebugUnitTest`：**1669 / 0 失败**（基线 1654） |
| 构建 | `gradle assembleDebug`：成功 |

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
