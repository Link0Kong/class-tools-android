package com.furina.classtools.ui.theme

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

    // ---- 形状 / 间距（鸿蒙同值） ----
    val BreathTop = 8.dp
    val RadiusCard = 18.dp
    val RadiusInput = 14.dp
    val RadiusHero = 24.dp
    val RadiusSheet = 26.dp
    val RadiusTile = 12.dp
    val Gap = 12.dp
    val PagePadding = 20.dp
    val CardPadding = 14.dp
    val TilePadding = 12.dp
    val TileMinHeight = 96.dp
    val GlyphBox = 40.dp
    val PageHeaderHeight = 60.dp
    val ButtonHeight = 48.dp
    val SectionGap = 20.dp
    val ContentMaxWidthPhone = 880.dp
    val ContentMaxWidthWide = 960.dp

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
