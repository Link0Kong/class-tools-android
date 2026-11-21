package com.furina.classtools.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// =============================================================================
// 唯一设计令牌源（Single Source of Truth）— CtDesign
//
// 权威参考（按优先级）：
//   1) Material 3 Expressive —— motion physics（spatial/effects 弹簧）、形状变形、
//      tonal 扩展色板（surface container 5 级）、可变字体轴 / 字重层级；
//   2) HarmonyOS「One Harmonious Universe」—— 卡片式、轻拟物光影（发丝高光边 + 极浅投影）、
//      引力动效（弹簧回弹）、多设备一致（手机/折叠/平板三档断点与栅格）；
//   3) 本地鸿蒙端 `harmony-class-tools/entry/src/main/ets/ui/**`（Tokens / Motion /
//      PageHeader / ToolTile / SectionHeader / AppShell / HomeScreen）的实际数值。
//
// 规则（docs/DESIGN_SYSTEM.md §7，可脚本检查）：
//   - **所有**颜色 / 圆角 / 间距 / 字号 / 动效时长只能来自本文件的 `Ct*` 对象（或 Theme.kt 的
//     ColorScheme，其数值同样取自本文件）；
//   - 业务 UI（ui/components、ui/screens、ui/navigation）禁止出现 `Color(0x…)`、
//     `Color.White/Black`、`RoundedCornerShape(数字)`、`fontSize = 数字.sp`；
//   - 新增语义先在这里加令牌，再在页面引用。
//
// 命名迁移说明：本文件由旧的 `Harmony.kt` 扩写而来。旧名 `HarmonyTokens` / `HarmonyMotion` /
// `HarmonyColors` / `CtText` 均保留为上文的 `typealias` 与同名属性，**调用方无需改动**；
// 新代码统一使用 `CtPalette` / `CtType` / `CtRadius` / `CtSpace` / `CtMotion` / `CtColor`。
// =============================================================================

// -----------------------------------------------------------------------------
// 1. 色板 CtPalette
// -----------------------------------------------------------------------------

/**
 * 色板令牌（明/暗各一套语义槽位）。
 *
 * 浅色对齐鸿蒙 Tokens.ets（BG #F7F8F8 / TEXT_PRIMARY #1C2629 / DIVIDER #E8EDEE…）；
 * 深色按同一语义做**中性深板岩**映射（surface #121517 / container #1A1E21 / high #232A2E），
 * 不再出现大面积品牌绿；品牌绿只作小面积强调（hero accent / 主色按钮）。
 *
 * 对比度（WCAG 2.1，实测见 docs/DESIGN_SYSTEM.md §2.4）：
 *   light textSecondary #4F5A5D / card = 6.52:1，textTertiary #61696C / card = 5.17:1，
 *   dark  textSecondary #A8B4B7 / #1A1E21 = 7.70:1，textTertiary #8A979A / #1A1E21 = 5.36:1 —— 全部 ≥ AA(4.5)。
 */
object CtPalette {
    // ---- 品牌三色（与网页 / 小程序 / 鸿蒙共用；图形/图标/大面积色块使用） ----
    val MistTeal = Color(0xFF4E9082)
    val SlateBlue = Color(0xFF6E8CA8)
    val WarmSand = Color(0xFFB98F63)

    // ---- 浅色中性（M3 tonal 扩展：surface container 5 级） ----
    val Bg = Color(0xFFF2F3F4)
    val Surface = Color(0xFFFFFFFF)

    /**
     * 卡片底（**半透明白**，2026-11-20 沉浸光感 v2）。
     *
     * 全 App 所有「直接使用 containerLow 作卡片底色」的 Surface（首页信息卡、列表卡、
     * 大量工具页卡片）因此自动获得透光感 —— 光场颜色从卡片下透出来，不再是一块死白板。
     * 不透明度 0.60：实测在最强光斑上正文对比度仍 > 9:1（AA 双倍余量）。
     */
    val ContainerLow = Color(0xE0FFFFFF)
    val Container = Color(0xFFF4F6F6)
    val ContainerHigh = Color(0xFFEDF0F1)
    val ContainerHighest = Color(0xFFE6EAEC)
    val BarLight = Color(0xFFFBFCFC)
    val TextPrimary = Color(0xFF1C2629)
    val TextSecondary = Color(0xFF4F5A5D)
    val TextTertiary = Color(0xFF61696C)
    val Divider = Color(0xFFE2E7E8)
    val Outline = Color(0xFF98A4A8)
    val TintTeal = Color(0xFFE7F0ED)
    val TintBlue = Color(0xFFE9EFF4)
    val TintSand = Color(0xFFF5EFE7)
    val Danger = Color(0xFFB4553F)

    // ---- 深色中性（中性深板岩；hero/pill 同族更深一档） ----
    val DarkBg = Color(0xFF121517)
    val DarkSurface = Color(0xFF1A1E21)
    /** 深色卡片底（半透明深玻璃，比页面底亮半档 —— 在深色光场上才能「浮」起来）。 */
    val DarkContainerLow = Color(0xFF1A1E21)
    val DarkContainer = Color(0xFF1E2326)
    val DarkContainerHigh = Color(0xFF232A2E)
    val DarkContainerHighest = Color(0xFF2B3338)
    val DarkBar = Color(0xFF171C1F)
    val DarkTextPrimary = Color(0xFFE7ECEB)
    val DarkTextSecondary = Color(0xFFA8B4B7)
    val DarkTextTertiary = Color(0xFF96A3A6)
    val DarkDivider = Color(0xFF2C353A)
    val DarkOutline = Color(0xFF6E7B7F)

    // ---- 浅色「莫奈纸面」hero（鸿蒙 HomeScreen.heroCard 实测值） ----
    val HeroPaper = Color(0xFFF1F6F2)
    val HeroPaperBorder = Color(0xFFDCE7E2)
    val HeroPaperPill = Color(0xFFFFFCF6)
    val HeroPaperTitle = Color(0xFF303A35)
    val HeroPaperSub = Color(0xFF5C6860)
    val HeroPaperDivider = Color(0xFFD8E4DE)
    val HeroPaperAccent = Color(0xFF3F6F66)
    val HeroPaperLink = Color(0xFF4E6D8C)

    // ---- 深色 hero（中性 tonal，不含大面积品牌绿） ----
    val HeroDarkPaper = Color(0xFF1C2427)
    val HeroDarkBorder = Color(0xFF334044)
    val HeroDarkPill = Color(0xFF2B3639)
    val HeroDarkDivider = Color(0xFF364448)
    val HeroDarkAccent = Color(0xFF8FD0C3)
    val HeroDarkLink = Color(0xFFA9C4DA)

    // ---- 加载骨架（鸿蒙 HomeScreen.skeletonPage 实测三档灰） ----
    val SkeletonStrong = Color(0xFFE9EFF1)
    val SkeletonBase = Color(0xFFEFF3F4)
    val SkeletonSoft = Color(0xFFF5F7F8)
    val DarkSkeletonStrong = Color(0xFF273235)
    val DarkSkeletonBase = Color(0xFF222C2F)
    val DarkSkeletonSoft = Color(0xFF1D2629)

    // ---- 遮罩 / 按压层（M3 state layer 语义：按压 12%、悬停 8%、拖拽 16%） ----
    val ScrimLight = Color(0xFF0B1418)
    val StateLayerLight = Color(0xFF1C2629)
    val StateLayerDark = Color(0xFFE7ECEB)

    // ---- 媒体/浮层（全屏图片、视频播放器、地图底部浮动块的固定遮罩） ----
    val MediaScrimWeak = Color(0xEE000000)
    val MediaScrimSoft = Color(0xCC000000)
    val MediaScrimBody = Color(0xEB121414)
    val MediaOnScrim = Color(0xFFFFFFFF)

    // ---- 阴影（鸿蒙卡片 radius18/y6/#141C2629；hero radius16/y6/#0F4E9082） ----
    val CardShadow = Color(0x1A1C2629)
    val HeroShadow = Color(0x0F4E9082)
    val DarkCardShadow = Color(0x66000000)
}

/**
 * 工具/分类强调色（同一色相两档）：[tintRef] 用于图标与淡底（大图形，3:1 即可），
 * [textRef] 用于**文字**（同色相加深，白底 ≥4.5:1 满足 AA）。
 *
 * 浅色：textRef 为原色向黑混合 30%（雾青 6.61 / 灰蓝 6.32 / 暖沙 5.44 / 叶绿 6.71 / 梅紫 7.84 /
 * 陶红 7.28 / 琥珀 4.97）；深色：textRef 为提亮版本（深底 ≥7:1）。
 */
data class CtAccentRef(
    /** 图形/淡底参考色（图标、tint 背景、色条）。 */
    val tintRef: Color,
    /** 文字参考色（次级文字/标签；AA）。 */
    val textRef: Color,
)

/** 强调色家族（工具目录 uiAccentOf(id) 与业务语义色都从这里取）。 */
object CtAccents {
    val teal = CtAccentRef(CtPalette.MistTeal, Color(0xFF37655B))
    val blue = CtAccentRef(CtPalette.SlateBlue, Color(0xFF4D6276))
    val sand = CtAccentRef(CtPalette.WarmSand, Color(0xFF826445))
    val leaf = CtAccentRef(Color(0xFF5F8F5B), Color(0xFF426440))
    val plum = CtAccentRef(Color(0xFF8A6A92), Color(0xFF614A66))
    val clay = CtAccentRef(Color(0xFFB06A55), Color(0xFF7B4A3B))
    val amber = CtAccentRef(Color(0xFFC29A3B), Color(0xFF886C29))
    val water = CtAccentRef(Color(0xFF4A88A8), Color(0xFF356B85))

    val darkTeal = CtAccentRef(Color(0xFF7FB9AB), Color(0xFF93B8AE))
    val darkBlue = CtAccentRef(Color(0xFF8FAAC8), Color(0xFF9FB6D2))
    val darkSand = CtAccentRef(Color(0xFFC4A383), Color(0xFFD2B08A))
    val darkLeaf = CtAccentRef(Color(0xFF8DB489), Color(0xFFA6C4A0))
    val darkPlum = CtAccentRef(Color(0xFFB69CBC), Color(0xFFC7A9D0))
    val darkClay = CtAccentRef(Color(0xFFCF9584), Color(0xFFE0B4A4))
    val darkAmber = CtAccentRef(Color(0xFFD6B265), Color(0xFFDDC188))
    val darkWater = CtAccentRef(Color(0xFF89B6CE), Color(0xFF9CC4D8))
}

/** 语义色（成功 / 警告 / 信息 / 危险软色 / 电量三档 / 水量蓝）。 */
object CtSemantic {
    val successLight = Color(0xFF3E7B5E)
    val warningLight = Color(0xFF8A6430)
    val infoLight = Color(0xFF4C6B85)
    val dangerSoftLight = Color(0xFFC0605A)

    val successDark = Color(0xFF8FCFB4)
    val warningDark = Color(0xFFF0C88E)
    val infoDark = Color(0xFFA8C6E2)
    val dangerSoftDark = Color(0xFFF0B4AA)
}

// -----------------------------------------------------------------------------
// 2. 字体标尺 CtType（M3 Expressive 层级；行高成比例）
// -----------------------------------------------------------------------------

/**
 * 语义字号令牌 —— **2026-11-21 按 MD3 官方字阶重排**。
 *
 * 数值不再手写：直接取 [CtTypography]（Compose material3 官方 15 档 + 中文行高 ×1.07），
 * 因此与 `MaterialTheme.typography`、M3 组件默认值永远同源。
 *
 * 语义映射（官方 Typography「按用途选样式」）：
 * - [displayTitleSize] 页面主标题 → Headline Medium（28sp）
 * - [titleSize] hero 问候 / 身份 → Headline Small（24sp）
 * - [headlineSize] 分区大标题 / 弹层标题 → Title Large（22sp）
 * - [title16Size] 卡片 / 列表主文字 → Title Medium（16sp / 500）
 * - [bodySize] 正文 → Body Medium（14sp）
 * - [labelSize] 按钮 / Chip / Tab → Label Large（14sp / 500）
 * - [captionSize] 辅助说明 → Body Small（12sp）
 * - [microSize] 角标 / 极小标注 → Label Small（11sp / 500）
 *
 * 常规层级只用 400 / 500；页面标题不再用 Bold（700），强调改用同档 Emphasized。
 */
object CtType {
    /** Headline Medium（28sp / 39sp 中文行高 / 400）。 */
    val displayTitleSize = CtTypography.headlineMedium.fontSize
    val displayTitleLine = CtTypography.headlineMedium.lineHeight.value.roundToInt()
    /** Headline Small（24sp / 34sp / 400）。 */
    val titleSize = CtTypography.headlineSmall.fontSize
    val titleLine = CtTypography.headlineSmall.lineHeight.value.roundToInt()
    /** Title Large（22sp / 30sp / 400）。 */
    val headlineSize = CtTypography.titleLarge.fontSize
    val headlineLine = CtTypography.titleLarge.lineHeight.value.roundToInt()
    /** Title Medium（16sp / 26sp / 500）。 */
    val title16Size = CtTypography.titleMedium.fontSize
    val title16Line = CtTypography.titleMedium.lineHeight.value.roundToInt()
    /** Body Medium（14sp / 21sp / 400）。 */
    val bodySize = CtTypography.bodyMedium.fontSize
    val bodyLine = CtTypography.bodyMedium.lineHeight.value.roundToInt()
    /** Label Large（14sp / 21sp / 500）。 */
    val labelSize = CtTypography.labelLarge.fontSize
    val labelLine = CtTypography.labelLarge.lineHeight.value.roundToInt()
    /** Body Small（12sp / 17sp / 400）。 */
    val captionSize = CtTypography.bodySmall.fontSize
    val captionLine = CtTypography.bodySmall.lineHeight.value.roundToInt()
    /** Label Small（11sp / 17sp / 500）。 */
    val microSize = CtTypography.labelSmall.fontSize
    val microLine = CtTypography.labelSmall.lineHeight.value.roundToInt()

    /**
     * 字重轴（M3：常规 400 / 强调 500；600 只给 Emphasized 的 500 档，700 不再用于常规层级）。
     */
    const val weightBold = 700
    const val weightSemiBold = 500
    const val weightMedium = 500
    const val weightNormal = 400

    /** 行高倍数（由官方行高 × 中文 1.07 推导，只读展示/单测引用）。 */
    val lineRatioDisplay = displayTitleLine / displayTitleSize.value
    val lineRatioTitle = titleLine / titleSize.value
    val lineRatioHeadline = headlineLine / headlineSize.value
    val lineRatioBody = bodyLine / bodySize.value
    val lineRatioCaption = captionLine / captionSize.value
}

// -----------------------------------------------------------------------------
// 3. 形状 CtRadius（M3 Expressive 形状变形 + 鸿蒙圆角）
// -----------------------------------------------------------------------------

/**
 * 圆角（dp）—— **MD3 十档形状刻度**（01-Foundations/shape-system.md）：
 * None 0 / XS 4 / S 8 / M 12 / L 16 / L+ 20 / XL 28 / XL+ 32 / XXL 48 / Full。
 *
 * 只允许出现十档里的值，不写 10 / 14 / 24 这类中间值（官方「常见错误」点名 10dp/11dp/13dp）。
 * 2026-11-21 由 24 / 14 / 10 收回官方档位：卡片 12（medium，官方 Card 默认）、
 * 紧凑卡 8（small）、输入 12、图标块 12、Chip 8、hero / 弹层 28（extra large）。
 * 按下态由 12 收紧到 8（M3 Expressive 形状变形：交互态更方）。
 */
object CtRadius {
    /** 卡片 12dp（M3 Card 默认 medium 档）。 */
    val card = 12.dp
    /** 紧凑卡片 / 小磁贴 8dp（small）。 */
    val cardCompact = 8.dp
    /** hero / 大容器 28dp（extra large）。 */
    val hero = 28.dp
    /** 输入 / 列表块 12dp。 */
    val input = 12.dp
    /** 底部弹层顶部圆角 28dp（extra large）。 */
    val sheetTop = 28.dp
    /** 对话框 28dp（extra large）。 */
    val dialog = 28.dp
    /** 图标块 12dp（medium）。 */
    val tile = 12.dp
    /** Chip / 小徽标 8dp（small）。 */
    val chip = 8.dp
    /** 胶囊（Full）。 */
    val pill = 999.dp
    /** 卡片按下时形状收紧：12 → 8（仍是十档之一）。 */
    val cardPressed = 8.dp
    /** 按钮按下态 8dp（Expressive；静止 12/16/28 由组件自选）。 */
    val buttonPressed = 8.dp
    /** 扩展档：Large increased 20（大卡片 / 浮层容器）。 */
    val largeIncreased = 20.dp
    /** 扩展档：Extra large increased 32（FAB / 大圆角容器）。 */
    val extraLargeIncreased = 32.dp
}

// -----------------------------------------------------------------------------
// 4. 间距 CtSpace（4/8/12/16/20/24 栅格）
// -----------------------------------------------------------------------------

/**
 * 间距（dp）—— **MD3 8dp 基线 + 官方页面边距**（01-Foundations/spacing.md、layout.md）。
 *
 * 官方口径：
 * - spacing system 以 **8dp 为基线**（space100 = 8dp），4dp 是 space50 半档；
 * - 页面左右 margin **只有两档：compact 16dp，medium 及以上 24dp**；
 * - 卡片内边距 16dp、卡片间距 ≤ 8–12dp、区块间距 24dp。
 *
 * 2026-11-21 由鸿蒙口径（20 / 24 / 32）改回 MD3：手机 16、折叠 24、平板 24；
 * 2 列大卡之间取 12dp（space150；官方卡片页上限 8dp，大卡片需要一点呼吸，属产品决策）。
 */
object CtSpace {
    /** 半档（4dp，space50）。 */
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp
    val huge = 48.dp

    /** 页面左右 margin：compact 16 / medium 24 / expanded 24（官方 layout.md）。 */
    val pagePhone = 16.dp
    val pageFold = 24.dp
    val pageTablet = 24.dp

    /** 卡片内边距 / 列表项内边距 / 工具磁贴内边距（卡片内边距固定 16dp）。 */
    val cardPadding = 16.dp
    val rowPaddingH = 16.dp
    val rowPaddingV = 8.dp
    val tilePadding = 12.dp

    /** 卡片间距 12 / 区块间距 24 / 大分区间距 32。 */
    val cardGap = 12.dp
    val sectionGap = 24.dp
    val groupGap = 32.dp

    /**
     * 控件间距（官方 8 / 12 / 16 三档）。
     *
     * 规则（2026-11-20 按鸿蒙「间隔参数」统一）：**相邻两个可点控件之间的空白 ≥ [controlGap]（8vp）**——
     * 文字按钮、图标按钮、筛选胶囊、分段选项、统计筛选卡都算；有明显边界（卡片 / 整块按钮）的分组用
     * [controlGapMedium] 或 [controlGapLarge]。图标与文字之间的 4–6vp 属于控件内部排版，不受这条约束。
     */
    val controlGap = 8.dp
    val controlGapMedium = 12.dp
    val controlGapLarge = 16.dp

    /** 顶部呼吸区（页面内容与状态栏之间）。 */
    val breathTop = 8.dp

    /** 8vp 网格基本单位（文档/单测引用）。 */
    val gridUnit = 8.dp
}

// -----------------------------------------------------------------------------
// 5. 尺寸 CtSize（组件几何）
// -----------------------------------------------------------------------------

/** 组件几何（dp）。触摸目标一律 ≥48（无障碍硬线）。 */
object CtSize {
    val minTouch = 48.dp
    val buttonHeight = 56.dp
    val buttonHeightCompact = 48.dp
    val topBarHeight = 56.dp
    val backHit = 44.dp
    val tileMinHeight = 96.dp
    val glyphBox = 40.dp
    val glyph = 24.dp
    val badgeBox = 30.dp
    val badgeGlyph = 17.dp
    val rowMinHeight = 56.dp
    val iconSmall = 18.dp
    val icon = 22.dp
    val iconLarge = 24.dp
    val emptyIcon = 48.dp
    val dividerThickness = 0.5.dp
    /** 悬浮工具栏高度 64dp（M3 Expressive floating toolbar 官方值）。 */
    val dockHeight = 64.dp
    /** 选中指示器 64 × 32dp（M3 navigation bar / toolbar 指示器）。 */
    val dockIndicatorWidth = 64.dp
    val dockPillHeight = 32.dp

    /** 内容最大宽度：手机/折叠 880，宽屏 1080，工具页 960。 */
    val contentMaxWidthPhone = 880.dp
    val contentMaxWidthWide = 1080.dp
    val toolMaxWidth = 960.dp
    val floatBarSpace = 96.dp

    /** 平板断点（M3 window size class）：>=600 双栏候选，>=840 双栏/多列。 */
    val breakpointMedium = 600.dp
    val breakpointExpanded = 840.dp
}

// -----------------------------------------------------------------------------
// 6. 动效 CtMotion（M3 Expressive motion physics + 统一时长）
// -----------------------------------------------------------------------------

/**
 * 动效令牌。
 *
 * 时长（任务规范）：[fast] 120ms（按压回弹/勾选）、[short] 200ms（淡入淡出/指示器）、
 * [standard] 300ms（弹层/容器变换）、[slow] 400ms（页面转场兜底）。
 *
 * 物理（M3 Expressive motion physics，dampingRatio / stiffness，mass=1）：
 * - 空间（位置/尺寸/形状）：[springSpatial] 0.8/380（默认）、[springSpatialFast] 0.75/600（按压）、
 *   [springSpatialSlow] 0.9/200（大面积/页面）；
 * - 效果（颜色/透明度）：临界阻尼 1.0/1600（不过冲）。
 *
 * 缓动：[easeStandard] = fastOutSlowIn cubic-bezier(.4,0,.2,1)（M3 Emphasized）。
 *
 * 「移除动画」由 [rememberReduceMotion]（AccessibilityManager.animationsEnabled 或
 * ANIMATOR_DURATION_SCALE=0）驱动，组件在此情况下改用 [reduceMotionSpec]（snap）。
 */
object CtMotionTokens {
    const val fastMs = 120
    const val shortMs = 200
    const val standardMs = 300
    const val slowMs = 400

    /** 弹簧：空间默认（0.8 / 380）。 */
    const val springSpatialDamping = 0.8f
    const val springSpatialStiffness = 380f
    /** 弹簧：空间快（按压 0.75 / 600）。 */
    const val springFastDamping = 0.75f
    const val springFastStiffness = 600f
    /** 弹簧：空间慢（页面/大面积 0.9 / 200）。 */
    const val springSlowDamping = 0.72f
    const val springSlowStiffness = 600f
    /** 弹簧：效果（临界阻尼 1.0 / 1600）。 */
    const val springEffectsStiffness = 1600f

    /** 按压缩放（M3 Expressive 0.97）。 */
    const val pressScale = 0.97f
    /** 按压态层不透明度（surface tonal + 8% state layer）。 */
    const val pressOverlayAlpha = 0.08f
    /** 列表项错峰入场：每项递增延迟（毫秒）。 */
    const val staggerStepMs = 40L
    /** 列表项入场位移（dp）。 */
    const val itemEnterRise = 8f
    /** 容器变换起始圆角（dp）：卡片圆角 → 页面 0（随 [CtRadius.card] 自动同步）。 */
    val containerMorphStartDp = CtRadius.card.value
    /** 骨架脉冲周期（毫秒，往返）。 */
    const val skeletonPulseMs = 900
    /** 骨架最低不透明度。 */
    const val skeletonDimAlpha = 0.55f
}

// -----------------------------------------------------------------------------
// 7. 阴影 CtElevation
// -----------------------------------------------------------------------------

/**
 * 阴影（dp）—— MD3：**静止卡片无阴影**（靠 surface 色阶 / 描边分层）。
 *
 * 官方 Cards：Filled / Outlined 0dp，Elevated 1dp；浮层用 surface container 色阶，
 * 不用大阴影（01-Foundations/elevation.md）。因此卡片 0、hero 1、浮层/弹层 3、拖拽 6。
 */
object CtElevation {
    val none = 0.dp
    val card = 0.dp
    val hero = 1.dp
    val floating = 3.dp
    val sheet = 3.dp
    val dragged = 6.dp
}

// -----------------------------------------------------------------------------
// 8. 兼容别名（旧名保留，零改动迁移）
// -----------------------------------------------------------------------------

/** 旧名：鸿蒙令牌（数值与 [CtPalette]/[CtRadius]/[CtSpace]/[CtType]/[CtSize] 一致或为其代理）。 */
object HarmonyTokens {
    // 品牌三色
    val MistTeal = CtPalette.MistTeal
    val SlateBlue = CtPalette.SlateBlue
    val WarmSand = CtPalette.WarmSand

    // 浅色中性
    val Bg = CtPalette.Bg
    val Card = CtPalette.Surface
    val BarLight = CtPalette.BarLight
    val TextPrimary = CtPalette.TextPrimary
    val TextSecondary = CtPalette.TextSecondary
    val TextTertiary = CtPalette.TextTertiary
    val Divider = CtPalette.Divider
    val TintTeal = CtPalette.TintTeal
    val TintBlue = CtPalette.TintBlue
    val TintSand = CtPalette.TintSand
    val Danger = CtPalette.Danger

    // 深色中性
    val DarkBg = CtPalette.DarkBg
    val DarkCard = CtPalette.DarkSurface
    val DarkBar = CtPalette.DarkBar
    val DarkTextPrimary = CtPalette.DarkTextPrimary
    val DarkTextSecondary = CtPalette.DarkTextSecondary
    val DarkTextTertiary = CtPalette.DarkTextTertiary
    val DarkDivider = CtPalette.DarkDivider

    // hero
    val HeroPaper = CtPalette.HeroPaper
    val HeroPaperBorder = CtPalette.HeroPaperBorder
    val HeroPaperPill = CtPalette.HeroPaperPill
    val HeroPaperTitle = CtPalette.HeroPaperTitle
    val HeroPaperSub = CtPalette.HeroPaperSub
    val HeroPaperDivider = CtPalette.HeroPaperDivider
    val HeroPaperAccent = CtPalette.HeroPaperAccent
    val HeroPaperLink = CtPalette.HeroPaperLink

    /** 宽屏顶部呼吸区。 */
    val BreathTopWide: Dp = 12.dp

    // 形状（数值升级为 Expressive 档：card 20 / hero 28 / sheet 28；紧凑档保留旧值）
    val RadiusCard: Dp = CtRadius.card
    val RadiusInput: Dp = CtRadius.input
    val RadiusHero: Dp = CtRadius.hero
    val RadiusSheet: Dp = CtRadius.sheetTop
    val RadiusTile: Dp = CtRadius.tile
    val Gap: Dp = CtSpace.cardGap

    // 页面边距三档
    val PagePadding: Dp = CtSpace.pagePhone
    val PagePaddingFold: Dp = CtSpace.pageFold
    val PagePaddingTablet: Dp = CtSpace.pageTablet
    val CardPadding: Dp = CtSpace.cardPadding
    val TilePadding: Dp = CtSpace.tilePadding
    val TileMinHeight: Dp = CtSize.tileMinHeight
    val GlyphBox: Dp = CtSize.glyphBox
    val TileGlyph: Dp = CtSize.glyph
    val TileTextGap: Dp = CtSpace.md
    val TextGap: Dp = CtSpace.xxs
    val TextGapHorizontal: Dp = CtSpace.sm
    val ElementGapLarge: Dp = CtSpace.lg
    val ElementGap: Dp = CtSpace.sm
    val BadgeBox: Dp = CtSize.badgeBox
    val BadgeGlyph: Dp = CtSize.badgeGlyph
    val PageHeaderHeight: Dp = CtSize.topBarHeight
    val ButtonHeight: Dp = CtSize.buttonHeightCompact
    val SectionGap: Dp = CtSpace.sectionGap
    val HairlineWidth: Dp = CtSize.dividerThickness
    val ContentMaxWidthPhone: Dp = CtSize.contentMaxWidthPhone
    val ContentMaxWidthWide: Dp = CtSize.contentMaxWidthWide
    val ToolMaxWide: Dp = CtSize.toolMaxWidth
    val FloatBarSpace: Dp = CtSize.floatBarSpace
    val SideBarSpace: Dp = 32.dp
    val SideNavWidth: Dp = 104.dp

    // 骨架
    val SkeletonStrong = CtPalette.SkeletonStrong
    val SkeletonBase = CtPalette.SkeletonBase
    val SkeletonSoft = CtPalette.SkeletonSoft
    val DarkSkeletonStrong = CtPalette.DarkSkeletonStrong
    val DarkSkeletonBase = CtPalette.DarkSkeletonBase
    val DarkSkeletonSoft = CtPalette.DarkSkeletonSoft

    // 字号（旧五档 → 新阶梯）
    val TitleSize = CtType.displayTitleSize
    val HeadingSize = CtType.headlineSize
    val BodySize = CtType.bodySize
    val LabelSize = CtType.labelSize
    val CaptionSize = CtType.captionSize

    // 阴影
    val CardShadowElevation: Dp = CtElevation.card
    val CardShadowColor = CtPalette.CardShadow
    val HeroShadowElevation: Dp = CtElevation.hero
    val HeroShadowColor = CtPalette.HeroShadow

    /** 顶部呼吸区（页面内容与状态栏之间；沉浸式状态下额外留白的来源）。 */
    val BreathTop: Dp = CtSpace.breathTop
}

/** 旧名：鸿蒙动效令牌（数值映射到 [CtMotion]，页面转场沿用鸿蒙 28/24vp + 0.985/0.99）。 */
object HarmonyMotion {
    /** 快（按压/勾选/切换高亮）= 120ms。 */
    const val FAST_MS = CtMotionTokens.fastMs
    /** 常规（弹层、tab 切换、卡片重排）= 200ms。 */
    const val NORMAL_MS = CtMotionTokens.shortMs
    /** 慢（页面转场）= 400ms。 */
    const val SLOW_MS = CtMotionTokens.slowMs

    /** 标准缓动 cubic-bezier(0.4, 0, 0.2, 1)。 */
    val ease = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    val PageEnterShift: Dp = 28.dp
    val PageExitShift: Dp = 24.dp
    const val PageEnterScale = 0.985f
    const val PageExitScale = 0.99f
    val ListItemRise: Dp = 10.dp
    const val PopInScale = 0.9f
    const val PressScale = 0.97f
    const val PressAlpha = 0.92f
    const val SkeletonDimAlpha = CtMotionTokens.skeletonDimAlpha
    const val SkeletonPulseMs = CtMotionTokens.skeletonPulseMs
}

// -----------------------------------------------------------------------------
// 9. 明暗语义色 CtColor（CompositionLocal 提供）
// -----------------------------------------------------------------------------

/**
 * 当前明暗下的语义色包（会话期读 `LocalCtColors.current` 或 [rememberCtColors]）。
 *
 * 页面只认这些槽位，不直接引用 [CtPalette] 的原始常量（便于以后主题/深色微调）。
 */
data class CtColor(
    val background: Color,
    val surface: Color,
    val containerLow: Color,
    val container: Color,
    val containerHigh: Color,
    val containerHighest: Color,
    val bar: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val outline: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val scrim: Color,
    val stateLayer: Color,
    val cardShadow: Color,
    val skeletonStrong: Color,
    val skeletonBase: Color,
    val skeletonSoft: Color,
    val heroPaper: Color,
    val heroBorder: Color,
    val heroPill: Color,
    val heroTitle: Color,
    val heroSub: Color,
    val heroDivider: Color,
    val heroAccent: Color,
    val heroLink: Color,
    val dark: Boolean,
)

/** 浅色语义色。 */
val CtColorLight: CtColor by lazy { ctColorLight() }
private fun ctColorLight(): CtColor = CtColor(
    background = CtPalette.Bg,
    surface = CtPalette.Surface,
    containerLow = CtPalette.ContainerLow,
    container = CtPalette.Container,
    containerHigh = CtPalette.ContainerHigh,
    containerHighest = CtPalette.ContainerHighest,
    bar = CtPalette.BarLight,
    textPrimary = CtPalette.TextPrimary,
    textSecondary = CtPalette.TextSecondary,
    textTertiary = CtPalette.TextTertiary,
    divider = CtPalette.Divider,
    outline = CtPalette.Outline,
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    scrim = CtPalette.ScrimLight,
    stateLayer = CtPalette.StateLayerLight,
    cardShadow = CtPalette.CardShadow,
    skeletonStrong = CtPalette.SkeletonStrong,
    skeletonBase = CtPalette.SkeletonBase,
    skeletonSoft = CtPalette.SkeletonSoft,
    heroPaper = CtPalette.HeroPaper,
    heroBorder = CtPalette.HeroPaperBorder,
    heroPill = CtPalette.HeroPaperPill,
    heroTitle = CtPalette.HeroPaperTitle,
    heroSub = CtPalette.HeroPaperSub,
    heroDivider = CtPalette.HeroPaperDivider,
    heroAccent = CtPalette.HeroPaperAccent,
    heroLink = CtPalette.HeroPaperLink,
    dark = false,
)

/** 深色语义色。 */
val CtColorDark: CtColor = CtColor(
    background = CtPalette.DarkBg,
    surface = CtPalette.DarkSurface,
    containerLow = CtPalette.DarkContainerLow,
    container = CtPalette.DarkContainer,
    containerHigh = CtPalette.DarkContainerHigh,
    containerHighest = CtPalette.DarkContainerHighest,
    bar = CtPalette.DarkBar,
    textPrimary = CtPalette.DarkTextPrimary,
    textSecondary = CtPalette.DarkTextSecondary,
    textTertiary = CtPalette.DarkTextTertiary,
    divider = CtPalette.DarkDivider,
    outline = CtPalette.DarkOutline,
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    scrim = CtPalette.ScrimLight,
    stateLayer = CtPalette.StateLayerDark,
    cardShadow = CtPalette.DarkCardShadow,
    skeletonStrong = CtPalette.DarkSkeletonStrong,
    skeletonBase = CtPalette.DarkSkeletonBase,
    skeletonSoft = CtPalette.DarkSkeletonSoft,
    heroPaper = CtPalette.HeroDarkPaper,
    heroBorder = CtPalette.HeroDarkBorder,
    heroPill = CtPalette.HeroDarkPill,
    heroTitle = CtPalette.DarkTextPrimary,
    heroSub = CtPalette.DarkTextSecondary,
    heroDivider = CtPalette.HeroDarkDivider,
    heroAccent = CtPalette.HeroDarkAccent,
    heroLink = CtPalette.HeroDarkLink,
    dark = true,
)

/** 纯函数：按明暗取语义色（预览/单测直接调用）。 */
fun ctColorOf(dark: Boolean): CtColor = if (dark) CtColorDark else CtColorLight

/** 语义色 CompositionLocal（由 ClassToolsTheme 提供）。 */
val LocalCtColors = androidx.compose.runtime.staticCompositionLocalOf<CtColor> { CtColorLight }

/** 取当前主题语义色（Composable）。 */
@androidx.compose.runtime.Composable
fun rememberCtColors(): CtColor = LocalCtColors.current
