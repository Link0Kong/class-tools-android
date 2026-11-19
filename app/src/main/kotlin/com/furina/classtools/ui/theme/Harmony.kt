package com.furina.classtools.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =============================================================================
// HarmonyOS 端设计令牌（2026-11-17 用户要求：Android 的卡片/UI/功能参照鸿蒙端重做）
//
// 权威来源：harmony-class-tools `ui/theme/Tokens.ets` + `HomeScreen.heroCard` 实际实现
// （浅色莫奈纸面 hero：#F1F6F2 底 / #DCE7E2 1px 边框 / 24 圆角 / 顶部三色 3px 条 /
//  「今日」#FFFCF6 奶白胶囊 / 问候 24 Bold #303A35 / 副行 12 #5C6860 / 摘要分割线 #D8E4DE）。
//
// 深色模式按同一语义做中性映射（深板岩底 + 提高文字对比），不另起一套颜色。
// 页面/组件请优先使用 [rememberHarmonyColors] / [CtText] / 本对象的尺寸常量，
// 不要在业务里散落新的魔法值。
// =============================================================================

object HarmonyTokens {

    // ---- 品牌色（与网页/小程序/鸿蒙共用） ----
    val MistTeal = Color(0xFF4E9082)
    val SlateBlue = Color(0xFF6E8CA8)
    val WarmSand = Color(0xFFB98F63)

    // ---- 浅色中性 ----
    val Bg = Color(0xFFF7F8F8)
    val Card = Color(0xFFFFFFFF)
    val BarLight = Color(0xFFFBFCFC)
    val TextPrimary = Color(0xFF1C2629)
    val TextSecondary = Color(0xFF66777D)
    val TextTertiary = Color(0xFF93A2A7)
    val Divider = Color(0xFFE8EDEE)
    val TintTeal = Color(0xFFE7F0ED)
    val TintBlue = Color(0xFFE9EFF4)
    val TintSand = Color(0xFFF5EFE7)
    val Danger = Color(0xFFB4553F)

    // ---- 深色中性 ----
    val DarkBg = Color(0xFF0F1416)
    val DarkCard = Color(0xFF181F22)
    val DarkBar = Color(0xFF121A1C)
    val DarkTextPrimary = Color(0xFFE9EEED)
    val DarkTextSecondary = Color(0xFFA6B4B8)
    val DarkTextTertiary = Color(0xFF7E8E92)
    val DarkDivider = Color(0xFF2A3538)

    // ---- 浅色莫奈纸面 hero（鸿蒙 HomeScreen.heroCard 实测值） ----
    val HeroPaper = Color(0xFFF1F6F2)
    val HeroPaperBorder = Color(0xFFDCE7E2)
    val HeroPaperPill = Color(0xFFFFFCF6)
    val HeroPaperTitle = Color(0xFF303A35)
    val HeroPaperSub = Color(0xFF5C6860)
    val HeroPaperDivider = Color(0xFFD8E4DE)
    val HeroPaperAccent = Color(0xFF3F6F66)
    val HeroPaperLink = Color(0xFF4E6D8C)

    // ---- 形状 / 间距（鸿蒙同值；2026-11-19 逐项复核 Tokens.ets / Breakpoints.ets） ----
    val BreathTop = 8.dp
    /** 宽屏顶部呼吸区（鸿蒙 Breakpoints.BREATH_TOP_WIDE=12）。 */
    val BreathTopWide = 12.dp
    val RadiusCard = 18.dp
    val RadiusInput = 14.dp
    val RadiusHero = 24.dp
    val RadiusSheet = 26.dp
    val RadiusTile = 12.dp
    val Gap = 12.dp

    /**
     * 页面左右边距：**16dp**（鸿蒙 `Tokens.PAGE_PADDING` / `Breakpoints.PAGE_PADDING_PHONE`）。
     *
     * 2026-11-19 修正：此前误取 20dp，比鸿蒙宽 4dp，与 `CtAdaptiveSpec`（16/24/32）也不一致；
     * 现按鸿蒙规范 phone=16 / 折叠屏=24 / 平板=32 三档给出，页面默认用 [PagePadding]。
     */
    val PagePadding = 16.dp
    val PagePaddingFold = 24.dp
    val PagePaddingTablet = 32.dp
    val CardPadding = 16.dp
    val TilePadding = 12.dp
    val TileMinHeight = 96.dp
    val GlyphBox = 40.dp
    /** 工具卡字形图标尺寸（鸿蒙 ToolTile 非紧凑态 24vp）。 */
    val TileGlyph = 24.dp
    /** 工具卡图标块与文字间距（鸿蒙 ToolTile `margin({ left: Tokens.GAP })` = 12）。 */
    val TileTextGap = 12.dp
    /** 主次文本上下间距（鸿蒙 `Tokens.TEXT_GAP` = 2）。 */
    val TextGap = 2.dp
    /** 主次文本左右间隔（鸿蒙 TEXT_GAP_H = 8vp）。 */
    val TextGapHorizontal = 8.dp
    /** 元素间大间隔（鸿蒙 GAP_L = 16vp）。 */
    val ElementGapLarge = 16.dp
    /** 元素间普通间隔（鸿蒙 GAP = 8vp）。 */
    val ElementGap = 8.dp
    /** 卡片小标题图标块（鸿蒙 HomeScreen.iconBadge(box=30, glyph=17)）。 */
    val BadgeBox = 30.dp
    val BadgeGlyph = 17.dp
    /** 顶栏高度（鸿蒙 `PageHeader.ets` headerBar 高 56vp；2026-11-19 由 60 修正）。 */
    val PageHeaderHeight = 56.dp
    val ButtonHeight = 48.dp
    val SectionGap = 16.dp
    /** 发丝线宽度（鸿蒙 `border({width: 0.5})`）。 */
    val HairlineWidth = 0.5.dp
    val ContentMaxWidthPhone = 880.dp
    /** 大屏内容最大宽度（鸿蒙 `Breakpoints.CONTENT_MAX_WIDE` = 1080；2026-11-19 由 960 修正）。 */
    val ContentMaxWidthWide = 1080.dp
    /** 大屏工具页内容最大宽度（鸿蒙 `Breakpoints.TOOL_MAX_WIDE` = 960）。 */
    val ToolMaxWide = 960.dp
    /** 悬浮底部页签占位（鸿蒙 `Breakpoints.FLOAT_BAR_SPACE` = 96）。 */
    val FloatBarSpace = 96.dp
    /** 侧边导航底部留白（鸿蒙 `Breakpoints.SIDE_BAR_SPACE` = 32）。 */
    val SideBarSpace = 32.dp
    /** 侧边导航宽度（鸿蒙 `Breakpoints.SIDE_NAV_WIDTH` = 104）。 */
    val SideNavWidth = 104.dp

    // ---- 加载骨架（鸿蒙 HomeScreen.skeletonPage 实测色：块 #E6ECEE / #E9EEEF / #EDF1F2） ----
    val SkeletonStrong = Color(0xFFE6ECEE)
    val SkeletonBase = Color(0xFFE9EEEF)
    val SkeletonSoft = Color(0xFFEDF1F2)
    val DarkSkeletonStrong = Color(0xFF273235)
    val DarkSkeletonBase = Color(0xFF222C2F)
    val DarkSkeletonSoft = Color(0xFF1D2629)

    // ---- 字号（鸿蒙 F_TITLE/F_HEADING/F_BODY/F_LABEL/F_CAPTION） ----
    val TitleSize = 26.sp
    val HeadingSize = 19.sp
    val BodySize = 15.sp
    val LabelSize = 13.sp
    val CaptionSize = 12.sp

    // ---- 卡片浅阴影（鸿蒙 radius 18 / y6 / 0x0D1C2629） ----
    // Compose 的 elevation 阴影无法逐像素复刻大半径柔光：用 2dp elevation + 低 alpha 墨色近似。
    val CardShadowElevation = 2.dp
    val CardShadowColor = Color(0x1A1C2629)

    // ---- hero 阴影（鸿蒙 HomeScreen.heroCard：radius 16 / y6 / #0F4E9082 雾青柔光） ----
    val HeroShadowElevation = 6.dp
    val HeroShadowColor = Color(0x0F4E9082)
}

/**
 * 鸿蒙动效令牌（2026-11-19 对照 `harmony ui/theme/Motion.ets` 逐项落地）。
 *
 * - 时长：快 160ms（按压/勾选）、常规 260ms（弹层/切换）、慢 380ms（页面转场）；
 * - 缓动：标准 cubic-bezier(.4,0,.2,1)（[HarmonyTokens] 的 ease），弹性 spring(.72,1,220,26)；
 * - 页面转场：进入 = 透明度 + 右移 28vp + 0.985 缩放；退出 = 透明度 + 右移 24vp + 0.99 缩放；
 * - 列表项进入 = 透明度 + 上移 10vp；卡片出现（popIn）= 透明度 + 0.9 缩放。
 *
 * Compose 无法直接复用 ArkUI 的 TransitionEffect，故这里只给**数值契约**，
 * 由 [com.furina.classtools.ui.components.dock.CtPageTransitions] / 组件各自按同一数值实现。
 */
object HarmonyMotion {

    /** 快（按压/勾选/切换高亮）。 */
    const val FAST_MS = 160

    /** 常规（弹层、tab 切换、卡片重排）。 */
    const val NORMAL_MS = 260

    /** 慢（页面转场）。 */
    const val SLOW_MS = 380

    /** 标准缓动 cubic-bezier(0.4, 0, 0.2, 1)。 */
    val ease = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** 页面进入：右移距离（vp）。 */
    val PageEnterShift = 28.dp

    /** 页面退出：右移距离（vp）。 */
    val PageExitShift = 24.dp

    /** 页面进入起始缩放（鸿蒙 0.985）。 */
    const val PageEnterScale = 0.985f

    /** 页面退出结束缩放（鸿蒙 0.99）。 */
    const val PageExitScale = 0.99f

    /** 列表项进入：上移距离（vp）。 */
    val ListItemRise = 10.dp

    /** 卡片出现 popIn 起始缩放（鸿蒙 0.9）。 */
    const val PopInScale = 0.9f

    /** 卡片按压缩放（鸿蒙 ToolTile pressed 0.97）。 */
    const val PressScale = 0.97f

    /** 卡片按压透明度（鸿蒙 ToolTile pressed 0.92）。 */
    const val PressAlpha = 0.92f

    /** 骨架 shimmer 呼吸的最低不透明度（鸿蒙 skeletonPage `this.shimmer ? 0.55 : 1`）。 */
    const val SkeletonDimAlpha = 0.55f

    /** 骨架呼吸周期（毫秒，单程）。 */
    const val SkeletonPulseMs = 900
}

/** 当前明/暗下的鸿蒙中性色 + 纸面 hero 色（全部来自 [HarmonyTokens]）。 */
data class HarmonyColors(
    val background: Color,
    val card: Color,
    val bar: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val dark: Boolean,
    val heroPaper: Color,
    val heroBorder: Color,
    val heroPill: Color,
    val heroTitle: Color,
    val heroSub: Color,
    val heroDivider: Color,
    val heroAccent: Color,
    val heroLink: Color,
    /** 骨架屏三档色（鸿蒙 HomeScreen.skeletonPage）。 */
    val skeletonStrong: Color = HarmonyTokens.SkeletonStrong,
    val skeletonBase: Color = HarmonyTokens.SkeletonBase,
    val skeletonSoft: Color = HarmonyTokens.SkeletonSoft,
)

fun harmonyColors(dark: Boolean): HarmonyColors = if (dark) {
    HarmonyColors(
        background = HarmonyTokens.DarkBg,
        card = HarmonyTokens.DarkCard,
        bar = HarmonyTokens.DarkBar,
        textPrimary = HarmonyTokens.DarkTextPrimary,
        textSecondary = HarmonyTokens.DarkTextSecondary,
        textTertiary = HarmonyTokens.DarkTextTertiary,
        divider = HarmonyTokens.DarkDivider,
        dark = true,
        heroPaper = Color(0xFF202A2D),
        heroBorder = Color(0xFF334044),
        heroPill = Color(0xFF2B3639),
        heroTitle = HarmonyTokens.DarkTextPrimary,
        heroSub = HarmonyTokens.DarkTextSecondary,
        heroDivider = Color(0xFF364448),
        heroAccent = Color(0xFF8FD0C3),
        heroLink = Color(0xFFA9C4DA),
        skeletonStrong = HarmonyTokens.DarkSkeletonStrong,
        skeletonBase = HarmonyTokens.DarkSkeletonBase,
        skeletonSoft = HarmonyTokens.DarkSkeletonSoft,
    )
} else {
    HarmonyColors(
        background = HarmonyTokens.Bg,
        card = HarmonyTokens.Card,
        bar = HarmonyTokens.BarLight,
        textPrimary = HarmonyTokens.TextPrimary,
        textSecondary = HarmonyTokens.TextSecondary,
        textTertiary = HarmonyTokens.TextTertiary,
        divider = HarmonyTokens.Divider,
        dark = false,
        heroPaper = HarmonyTokens.HeroPaper,
        heroBorder = HarmonyTokens.HeroPaperBorder,
        heroPill = HarmonyTokens.HeroPaperPill,
        heroTitle = HarmonyTokens.HeroPaperTitle,
        heroSub = HarmonyTokens.HeroPaperSub,
        heroDivider = HarmonyTokens.HeroPaperDivider,
        heroAccent = HarmonyTokens.HeroPaperAccent,
        heroLink = HarmonyTokens.HeroPaperLink,
    )
}

@Composable
fun rememberHarmonyColors(): HarmonyColors = harmonyColors(LocalCtDarkTheme.current)

/** 工具 tint 底（鸿蒙 ToolTile：`Tokens.tint(accent, 0.12)`）。 */
fun harmonyTint(accent: Color): Color = accent.copy(alpha = 0.12f)

/** 鸿蒙字号层级（TextStyle；行高按 1.3–1.5 倍中文可读性取整）。 */
object CtText {
    val title: TextStyle = TextStyle(
        fontSize = HarmonyTokens.TitleSize,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
    )
    val heading: TextStyle = TextStyle(
        fontSize = HarmonyTokens.HeadingSize,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Medium,
    )
    val body: TextStyle = TextStyle(
        fontSize = HarmonyTokens.BodySize,
        lineHeight = 22.sp,
    )
    val bodyMedium: TextStyle = TextStyle(
        fontSize = HarmonyTokens.BodySize,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
    )
    val label: TextStyle = TextStyle(
        fontSize = HarmonyTokens.LabelSize,
        lineHeight = 18.sp,
    )
    val labelMedium: TextStyle = TextStyle(
        fontSize = HarmonyTokens.LabelSize,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    )
    val caption: TextStyle = TextStyle(
        fontSize = HarmonyTokens.CaptionSize,
        lineHeight = 17.sp,
    )
}

/** 鸿蒙卡片形状（18dp）与 hero 形状（24dp）。 */
val HarmonyCardShape = RoundedCornerShape(HarmonyTokens.RadiusCard)
val HarmonyHeroShape = RoundedCornerShape(HarmonyTokens.RadiusHero)
val HarmonyTileShape = RoundedCornerShape(HarmonyTokens.RadiusTile)
val HarmonyInputShape = RoundedCornerShape(HarmonyTokens.RadiusInput)
val HarmonySheetShape = RoundedCornerShape(
    topStart = HarmonyTokens.RadiusSheet,
    topEnd = HarmonyTokens.RadiusSheet,
)
