package com.furina.classtools.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// =============================================================================
// 统一视觉语言 · 组合入口（2026-11-19 M3 Expressive × HarmonyOS 重构）
//
// 本文件只做「组合与派生」，数值一律来自 [CtDesign.kt]（唯一令牌源）：
//   - 字体 [CtType]  → [CtTypeScale]（页面直接使用的 TextStyle）
//   - 形状 [CtRadius] → [CtShape]（卡片/输入/hero/弹层/胶囊 的 Shape）
//   - 明暗   [CtColor] → [CtColorLight]/[CtColorDark] → [CtColorScheme]
//   - 强调色 [CtAccents] → [rememberCtAccents] / [ctAccentsOf]
//
// 业务代码统一写法：
//   val c = rememberCtColors()                 // 语义色
//   Card(colors = CardDefaults.cardColors(containerColor = c.surface)) { … }
//   Text("标题", style = CtTypeScale.title16, color = c.textPrimary)
//
// 旧入口 `rememberHarmonyColors()` / `harmonyColors()` / `harmonyTint()` / `CtText`
// 全部保留为这里的薄封装（行为一致），已有页面零改动即可继续工作。
// =============================================================================

// -----------------------------------------------------------------------------
// 字体：CtTypeScale（由 CtType 生成）
// -----------------------------------------------------------------------------

/**
 * 字体层级（TextStyle）—— **2026-11-21 起直接引用 [CtTypography] 的官方 M3 档位**。
 *
 * 不再手写字号/行高/字重：每个语义名都指向官方字阶里的具体 role，
 * 页面改字号时改 role，不写 `copy(fontSize = …)`。
 *
 * | 语义名 | M3 role | 用途 |
 * |---|---|---|
 * | display | Headline Medium | 页面主标题（大标题 App Bar） |
 * | title | Headline Small | hero 问候 / 身份 |
 * | headline | Title Large | 分区标题 / 弹层标题 |
 * | title16 | Title Medium | 卡片 / 列表主文字 |
 * | body | Body Medium | 正文 |
 * | label | Label Large | 按钮 / Chip / Tab |
 * | caption | Body Small | 辅助说明 |
 * | micro | Label Small | 角标 / 极小标注 |
 */
object CtTypeScale {
    /** 页面主标题：Headline Medium（28 / 39 / 400）。 */
    val display: TextStyle = CtTypography.headlineMedium

    /** hero 问候 / 身份姓名：Headline Small（24 / 34 / 400）。 */
    val title: TextStyle = CtTypography.headlineSmall

    /** 分区标题 / 弹层大标题：Title Large（22 / 30 / 400）。 */
    val headline: TextStyle = CtTypography.titleLarge

    /** 卡片 / 列表主文字：Title Medium（16 / 26 / 500）。 */
    val title16: TextStyle = CtTypography.titleMedium

    /** 正文：Body Medium（14 / 21 / 400）。 */
    val body: TextStyle = CtTypography.bodyMedium

    /** 强调正文：Body Medium 500。 */
    val bodyMedium: TextStyle = CtTypography.bodyMedium.copy(fontWeight = FontWeight.Medium)

    /** 按钮 / Chip / Tab：Label Large（14 / 21 / 500）。 */
    val label: TextStyle = CtTypography.labelLarge

    /** 次级标签（不加粗）：Label Large 400。 */
    val labelPlain: TextStyle = CtTypography.labelLarge.copy(fontWeight = FontWeight.Normal)

    /** 辅助说明：Body Small（12 / 17 / 400）。 */
    val caption: TextStyle = CtTypography.bodySmall

    /** 辅助说明（Medium，用于区块标题小字）：Body Small 500。 */
    val captionMedium: TextStyle = CtTypography.bodySmall.copy(fontWeight = FontWeight.Medium)

    /** 角标：Label Small（11 / 17 / 500）。 */
    val micro: TextStyle = CtTypography.labelSmall

    /** 区块标题：Title Small（14 / 21 / 500）。 */
    val sectionTitle: TextStyle = CtTypography.titleSmall

    /** 数字展示（电费/统计等）：Headline Small + 等宽数字（tnum）。 */
    val metric: TextStyle = CtTypography.headlineSmall.copy(fontFeatureSettings = "tnum")
}

/**
 * 旧入口：鸿蒙字号层级 `CtText`。保留同名属性（title/heading/body/bodyMedium/label/
 * labelMedium/caption），**数值改为新阶梯**：
 *   title 28（页面主标题）、heading 20（顶栏/分组标题）、body 15、label 13、caption 12。
 * 旧页面继续可用；新代码请用 [CtTypeScale] 的语义名。
 */
object CtText {
    val title: TextStyle = CtTypeScale.title
    val heading: TextStyle = CtTypeScale.headline
    val title16: TextStyle = CtTypeScale.title16
    val body: TextStyle = CtTypeScale.body
    val bodyMedium: TextStyle = CtTypeScale.bodyMedium
    val label: TextStyle = CtTypeScale.label
    val labelMedium: TextStyle = CtTypeScale.label
    val caption: TextStyle = CtTypeScale.caption
    val micro: TextStyle = CtTypeScale.micro
}

// -----------------------------------------------------------------------------
// 形状：CtShape（由 CtRadius 生成）
// -----------------------------------------------------------------------------

/** 形状令牌（Shape 实例；需要数值动画时用 [CtRadius].* .dp）。 */
object CtShape {
    /** 标准卡片 12dp（M3 Card medium 档；静止无阴影，靠 surface 色阶分层）。 */
    val card = androidx.compose.foundation.shape.RoundedCornerShape(CtRadius.card)
    /** 紧凑卡片 8dp（小磁贴/编辑格）。 */
    val cardCompact = androidx.compose.foundation.shape.RoundedCornerShape(CtRadius.cardCompact)
    /** hero 28dp（extra large）。 */
    val hero = androidx.compose.foundation.shape.RoundedCornerShape(CtRadius.hero)
    /** 输入 12dp（tonal 底无边框）。 */
    val input = androidx.compose.foundation.shape.RoundedCornerShape(CtRadius.input)
    /** 顶部弹层 28dp。 */
    val sheet = androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = CtRadius.sheetTop,
        topEnd = CtRadius.sheetTop,
    )
    /** 对话框 28dp。 */
    val dialog = androidx.compose.foundation.shape.RoundedCornerShape(CtRadius.dialog)
    /** 图标块 12dp。 */
    val tile = androidx.compose.foundation.shape.RoundedCornerShape(CtRadius.tile)
    /** 小徽标 10dp。 */
    val chip = androidx.compose.foundation.shape.RoundedCornerShape(CtRadius.chip)
    /** 胶囊（按钮/标签/搜索栏）。 */
    val pill: Shape = androidx.compose.foundation.shape.CircleShape
}

/**
 * 旧入口别名：[CtShapeTokens]（原名保留）。数值按新规范上移一档：
 * hero 28 / toolCard 20 / 输入 14 / 弹层 28 / 对话框 28 / 列表块 14 / 图标块 12。
 */
object CtShapeTokens {
    val hero = CtShape.hero
    val toolCard = CtShape.card
    val cardLarge = CtShape.card
    val toolCardPressed = androidx.compose.foundation.shape.RoundedCornerShape(CtRadius.cardPressed)
    val pill = CtShape.pill
    val chip = CtShape.chip
    val input = CtShape.input
    val sheet = CtShape.sheet
    val dialog = CtShape.dialog
    val listItem = androidx.compose.foundation.shape.RoundedCornerShape(CtRadius.input)
    val iconTile = CtShape.tile
    val toolbar = CtShape.pill
}

/** 旧入口：[HarmonyCardShape]/[HarmonyHeroShape]/… 旧扩展属性名在 Theme.kt 中保留。 */

// -----------------------------------------------------------------------------
// 强调色：按明暗解析
// -----------------------------------------------------------------------------

/** 强调色包（工具/分类/语义）。 */
data class CtAccentColors(
    val teal: CtAccentRef,
    val blue: CtAccentRef,
    val sand: CtAccentRef,
    val leaf: CtAccentRef,
    val plum: CtAccentRef,
    val clay: CtAccentRef,
    val amber: CtAccentRef,
    val water: CtAccentRef,
    val success: Color,
    val warning: Color,
    val info: Color,
    val error: Color,
)

/** 纯函数：按明暗取强调色（预览/单测）。 */
fun ctAccentsOf(dark: Boolean): CtAccentColors = if (dark) {
    CtAccentColors(
        teal = CtAccents.darkTeal,
        blue = CtAccents.darkBlue,
        sand = CtAccents.darkSand,
        leaf = CtAccents.darkLeaf,
        plum = CtAccents.darkPlum,
        clay = CtAccents.darkClay,
        amber = CtAccents.darkAmber,
        water = CtAccents.darkWater,
        success = CtSemantic.successDark,
        warning = CtSemantic.warningDark,
        info = CtSemantic.infoDark,
        error = DarkError,
    )
} else {
    CtAccentColors(
        teal = CtAccents.teal,
        blue = CtAccents.blue,
        sand = CtAccents.sand,
        leaf = CtAccents.leaf,
        plum = CtAccents.plum,
        clay = CtAccents.clay,
        amber = CtAccents.amber,
        water = CtAccents.water,
        success = CtSemantic.successLight,
        warning = CtSemantic.warningLight,
        info = CtSemantic.infoLight,
        error = LightError,
    )
}

/** 强调色 CompositionLocal。 */
val LocalCtAccents = androidx.compose.runtime.staticCompositionLocalOf { ctAccentsOf(false) }

/** 取当前主题强调色。 */
@Composable
fun rememberCtAccents(): CtAccentColors = LocalCtAccents.current

/**
 * 工具/分类强调色解析：由工具目录的「族名」取强调色族（**数据层只存族名，不存颜色**）。
 *
 * 族名契约见 `data/tool/ToolCatalog.kt` 的 `accentFamily`（teal/blue/sand/leaf/plum/clay/amber/water）。
 */
fun CtAccentColors.family(name: String): CtAccentRef = when (name) {
    "blue" -> blue
    "sand" -> sand
    "leaf" -> leaf
    "plum" -> plum
    "clay" -> clay
    "amber" -> amber
    "water" -> water
    else -> teal
}

/**
 * 解析工具强调色（Composable）：`val accent = uiAccentOf(tool)`，
 * `accent.tintRef` 用于图标/淡底，`accent.textRef` 用于文字。
 */
@Composable
fun uiAccentOf(tool: com.furina.classtools.data.tool.AppTool): CtAccentRef {
    return rememberCtAccents().family(tool.accentFamily)
}

/** 纯函数版本：按明暗 + 族名解析（预览/单测）。 */
fun uiAccentOf(family: String, dark: Boolean): CtAccentRef =
    if (family == com.furina.classtools.data.tool.CtAccentFamilies.WATER) {
        if (dark) CtAccents.darkWater else CtAccents.water
    } else {
        ctAccentsOf(dark).family(family)
    }

/** 当前明暗下水量蓝文字色。 */
fun waterTextOf(dark: Boolean): Color =
    if (dark) CtAccents.darkWater.textRef else CtAccents.water.textRef

/** 淡色底：强调色 12% 覆盖（图标块/tint 背景）。 */
fun harmonyTint(accent: Color): Color = accent.copy(alpha = 0.12f)

/** 按压态层覆盖（surface tonal + 8% state layer）。 */
fun pressOverlay(base: Color, layer: Color): Color = lerpColor(base, layer, CtMotionTokens.pressOverlayAlpha)

/** 颜色线性插值（Compose Color.lerp 的同义封装，避免各页面重复 import）。 */
fun lerpColor(from: Color, to: Color, fraction: Float): Color =
    androidx.compose.ui.graphics.lerp(from, to, fraction.coerceIn(0f, 1f))

// -----------------------------------------------------------------------------
// 旧入口：HarmonyColors / harmonyColors / rememberHarmonyColors
// -----------------------------------------------------------------------------

/**
 * 旧入口：鸿蒙中性色 + hero 纸面色（字段集合保持不变，数值来自 [CtColor]）。
 *
 * 保留该类是因为既有页面/组件按字段名取色（`colors.card` / `colors.textSecondary`…）；
 * 新代码请直接用 [CtColor]（`rememberCtColors()`），字段名更贴近 M3 语义。
 */
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
    val skeletonStrong: Color = CtPalette.SkeletonStrong,
    val skeletonBase: Color = CtPalette.SkeletonBase,
    val skeletonSoft: Color = CtPalette.SkeletonSoft,
)

/** 旧入口：按明暗构造 [HarmonyColors]（纯函数）。 */
fun harmonyColors(dark: Boolean): HarmonyColors = harmonyColors(ctColorOf(dark))

private fun harmonyColors(c: CtColor): HarmonyColors {
    return HarmonyColors(
        background = c.background,
        card = c.surface,
        bar = c.bar,
        textPrimary = c.textPrimary,
        textSecondary = c.textSecondary,
        textTertiary = c.textTertiary,
        divider = c.divider,
        dark = c.dark,
        heroPaper = c.heroPaper,
        heroBorder = c.heroBorder,
        heroPill = c.heroPill,
        heroTitle = c.heroTitle,
        heroSub = c.heroSub,
        heroDivider = c.heroDivider,
        heroAccent = c.heroAccent,
        heroLink = c.heroLink,
        skeletonStrong = c.skeletonStrong,
        skeletonBase = c.skeletonBase,
        skeletonSoft = c.skeletonSoft,
    )
}

/** 旧入口：当前明暗下的 [HarmonyColors]。 */
@Composable
fun rememberHarmonyColors(): HarmonyColors {
    val colors = LocalCtColors.current
    return androidx.compose.runtime.remember(colors) { harmonyColors(colors) }
}

// -----------------------------------------------------------------------------
// 旧入口：形状单例
// -----------------------------------------------------------------------------

/** 旧入口：卡片形状（20dp）。 */
val HarmonyCardShape = CtShape.card

/** 旧入口：hero 形状（28dp）。 */
val HarmonyHeroShape = CtShape.hero

/** 旧入口：磁贴形状（12dp）。 */
val HarmonyTileShape = CtShape.tile

/** 旧入口：输入形状（14dp）。 */
val HarmonyInputShape = CtShape.input

/** 旧入口：弹层形状（顶部 28dp）。 */
val HarmonySheetShape = CtShape.sheet

// -----------------------------------------------------------------------------
// 文字样式扩展：MaterialTheme.ctType（与 MaterialTheme.ctEmphasized 同风格）
// -----------------------------------------------------------------------------

/** `MaterialTheme.ctType.title16` 等便捷访问（只读）。 */
val MaterialTheme.ctType: CtTypeScale
    @Composable @ReadOnlyComposable
    get() = CtTypeScale

/** 标准缓动（写动画时统一引用，避免各文件 new 一个 bezier）。 */
val CtEase = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
