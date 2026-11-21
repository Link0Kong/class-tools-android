# Android 端 MD3 对齐报告（2026-11-21）

> 依据：[`lwhhz/Android-MD3-Knowledge`](https://github.com/lwhhz/Android-MD3-Knowledge)（官网核验版，核验日期 2026-11-16）。
> 对应文件：`AI-INSTRUCTIONS.md`、`07-Development-Rules/android-ui-rules.md`、`compose-coding-rules.md`、
> `ui-review-checklist.md`、`01-Foundations/{typography,shape-system,spacing,layout,elevation}.md`、
> `02-Components/{cards,app-bars,navigation-bar,toolbars}.md`。
> 本文只记录**已落地**的改动与**明确保留**的产品决策；未做的进「待办」，不写成「已符合」。

## 0. 一句话结论

安卓端此前是「M3 Expressive × HarmonyOS」的混合口径（页面标题 34sp Bold、卡片 24dp、
页面边距 20/24/32、底栏 60dp）。本轮把**可判定的令牌与组件几何**收回 MD3 官方口径，
保留玻璃材质与极光背景作为品牌表达。改动集中在主题层，全端页面自动继承，不逐页重写。

## 1. 审计结果（按 KB 检查项）

| 检查项 | KB 口径 | 改造前 | 本轮 |
|---|---|---|---|
| 字阶数量 | 15 baseline + 15 emphasized | 结构对，但 15 档全部手写且值偏离官方 | 改为 `Typography()` 官方默认值 + 中文行高 |
| 常规字重 | 400 / 500；700 中文易糊 | 页面标题 700、分区标题 700 | 标题/分区 400，卡片标题 500 |
| 页面标题 | Large app bar = Display Small；小 app bar = Title Large | 34sp Bold 就地覆盖 | Display Small（36/400）；悬浮条 Title Large（22/400） |
| 中文行高 | 中文属 Medium，约 ×1.07 | 1.4–1.6 倍自定 | 官方行高 ×1.07（28→39、24→34、22→30…） |
| 圆角 | 十档：0/4/8/12/16/20/28/32/48/Full | 卡片 24、输入 14、Chip 10 | 卡片 12、输入 12、Chip 8、hero/弹层 28 |
| 页面边距 | compact 16，medium+ 24 | 20 / 24 / 32 | 16 / 24 / 24 |
| 卡片间距 | 官方卡片页上限 8dp | 16dp | 12dp（产品决策，见 §3） |
| 卡片阴影 | 静止卡片无阴影（Filled/Outlined 0dp） | 1dp | 0dp |
| 底栏 | NavigationBar 80dp / 指示器 64×32 / 图标 24；Floating toolbar 64dp | 60dp / 44dp 药丸 / 图标 20 / 11sp | 64dp / 64×32 / 24 / Label Medium 12 |
| 底栏配色 | 指示器 `secondaryContainer`、选中图标 `onSecondaryContainer` | 品牌色 0.22 / TintTeal | M3 语义角色 |
| 按钮文字 | Label Large 14/500 | 15sp 覆盖 | Label Large |
| 统计小字 | 最小 11sp | 9sp（统计紧凑态） | 11sp（Label Small） |
| 可点击目标 | ≥48dp | 底栏单项 60dp、图标按钮 44dp+padding | 底栏 64dp；保持 ≥48dp |

## 2. 已落地的改动

### 2.1 字体（`ui/theme/Type.kt`、`CtDesign.kt`、`DesignSystem.kt`）
- `CtTypography` 不再手写 15 档：取 Compose material3 `Typography()` 官方默认值，统一按中文语言高度 ×1.07 加高行高。
- 语义字阶改为 M3 role 映射：
  - `display` → Headline Medium 28/39/400
  - `title` → Headline Small 24/34/400
  - `headline` → Title Large 22/30/400
  - `title16` → Title Medium 16/26/500
  - `body` → Body Medium 14/21/400
  - `label` → Label Large 14/21/500
  - `caption` → Body Small 12/17/400
  - `micro` → Label Small 11/17/500
- Emphasized 15 档改为「同档 + 一档字重」，不再改字号。
- 页面标题：`CtRootHeader` 的 `34.sp / Bold / -0.4 letterSpacing` 覆盖 → `displaySmall`；
  悬浮条 52dp + 16sp Bold → 56dp + `titleLarge`；区块标题不再 700。
- 其余裸字号：按钮 15sp → `label`；统计 9sp → `micro`（11sp）。

### 2.2 形状（`ui/theme/Shape.kt`、`CtDesign.kt`）
- `MaterialTheme.shapes` 五档 = 官方 4 / 8 / 12 / 16 / 28。
- 语义圆角只取十档：卡片 12（M3 Card 默认）、紧凑卡 8、输入 12、图标块 12、Chip 8、
  hero 28、弹层 28、按下态 8（由 12 收紧，仍是十档）。
- 新增 `largeIncreased = 20`、`extraLargeIncreased = 32`，需要更大圆角时就地取档。

### 2.3 间距与层级（`CtDesign.kt`）
- 页面边距：16 / 24 / 24（MD3 layout 官方唯一两档）。
- 卡片间距 16 → 12；卡片内边距保持 16；区块间距 24。
- 阴影：卡片 1 → 0，hero 3 → 1，浮层/弹层 6 → 3，拖拽 12 → 6；层级主要靠 surface 色阶与描边。
- 卡片间距 12 属**产品决策**：官方卡片页写「≤8dp」，但本端是 2 列大卡（图标 + 双行文案），
  8dp 会让两组卡片粘成一块；12 是 MD3 `space150` 合法 token，故取 12 并在单测中锁定。

### 2.4 底栏（`ui/components/dock/`）
- 高度 60 → 64dp（M3 Expressive floating toolbar 官方 64dp）；单项触控 64dp。
- 选中指示器由「item 宽 -10dp / 44dp 高」改为 **64 × 32dp** 固定尺寸，颜色改 `secondaryContainer`。
- 图标 20 → 24dp；文案 11sp 裸字号 → `labelMedium`（12/17）。
- 选中图标 `onSecondaryContainer`、选中文案 `onSurface`、未选中 `onSurfaceVariant`（M3 NavigationBar 配色）。
- 仍在导航栏 inset 之上悬浮，横向 16dp 边距（floating toolbar 官方最小 16dp）。

### 2.5 文档
- `design/README.md` 增加本报告入口。
- 令牌单测（`CtSpacingTokensTest`、`CtDesignTokensTest`、`HarmonyTokensTest`）按新口径重写，
  锁定「只允许 MD3 token / 十档圆角 / 官方 role 字阶 / 中文行高」。

## 3. 明确保留的品牌决策

| 项 | 现状 | 理由 |
|---|---|---|
| 玻璃 / 极光材质 | 卡片、顶栏、底栏继续用 `CtGlassSurface` / `ctGlass` | 跨端识别度；KB 允许「表达性」但不能牺牲可用性，玻璃已有对比度单测（AA） |
| 8 个工具强调色 | `CtAccents` 八族 | M3 只有 primary/secondary/tertiary 三角色；工具目录需要色相编码，属产品信息架构 |
| 浮动底栏而非标准 NavigationBar | 64dp 悬浮胶囊 | 对齐 M3 Expressive floating toolbar；标准 NavigationBar 需 80dp 且不悬浮 |
| 卡片间距 12dp | 见 §2.3 | 2 列大卡的可读性 |

## 4. 待办（未在本轮完成，不能声称已符合）

1. **200% 字体缩放逐屏验证**：底栏已改 `heightIn(min=64dp)` 并实测 200% 不裁标签；其余页面仍有 100+ 处 `height(...)` 固定高度，需逐屏改 `heightIn(min=…)` 并实机跑一遍。
2. **卡片材质收敛**：内容卡片目前仍是半透明玻璃；若要严格 M3，应改 `surfaceContainerHighest`（filled）
   或 `surface + outlineVariant`（outlined），并去掉模糊。
3. **一屏一个 Filled 主操作**：工具页仍有多个高强调按钮，需要按屏幕复核。
4. **空 / 错 / 载四态**：部分工具页缺失可执行空态与重试，按 `ui-review-checklist` 逐页过。
5. **列表 `key` + `contentType`**：LazyColumn 仍有未提供 `contentType` 的混合列表。
6. **`@OptIn` 注释**：实验 API 需逐处注明原因（material3 1.4/1.5 双轨）。
7. **横屏 / 平板 / 折叠屏**：页面边距已按窗口宽度走，但双栏与弹出层边界未逐页验证。

## 5. 验证方式

```cmd
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:assembleDebug --project-prop "ct.skipUpdateChannel=true"
```

- 令牌单测：圆角十档、spacing 18 档、字阶 role 映射、中文行高、常规层级无 700。
- 编译：`compileDebugKotlin` 通过。
- 实机验收（发布前）：浅/深色、200% 字号、底栏 TalkBack 播报、指示器对比度、页面边距。

## 6. 来源

| 主题 | 链接 |
|---|---|
| 字阶 | https://m3.material.io/styles/typography/type-scale-tokens |
| 形状 | https://m3.material.io/styles/shape/corner-radius-scale |
| 间距 | https://m3.material.io/styles/spacing/tokens |
| 布局边距 | https://m3.material.io/foundations/layout/applying-layout/window-size-classes |
| 卡片 | https://m3.material.io/components/cards/specs |
| App bar | https://m3.material.io/components/app-bars/specs |
| Navigation bar | https://m3.material.io/components/navigation-bar/specs |
| Toolbar | https://m3.material.io/components/toolbars/specs |

知识库核验日期 2026-11-16；本端改动日期 2026-11-21。官方页面若更新，以官网为准并回写本表。

