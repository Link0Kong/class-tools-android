package com.furina.classtools.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// =============================================================================
// 班级工具站 · Android 17 类型标尺
//
// 数值来源：material3 1.4.0 `TypeScaleTokens`（30 个样式：15 baseline + 15 emphasized），
// 对应 `design/tokens.json → typography`。要点：
//   - 字体族使用系统默认（Android 默认 = Roboto Flex 拉丁 + Noto Sans CJK 中文），
//     不打包字体（体积 + 多设备品牌一致性由系统字体承担）；如需品牌字体后续可加
//     FontFamily(...) 与 FontVariation 设置，不影响本文件 API。
//   - material3 1.4.0 的 `Typography` 公开构造只有 15 个 baseline 参数；
//     15 个 emphasized 样式由本文件 `CtEmphasizedTypography` + `LocalCtEmphasizedType`
//     提供（不能通过公开 API 写进 MaterialTheme.typography）。
//   - 中文覆写：CJK 需要更松的行高，body/title/label 统一 +2sp（见每项注释）；
//     tracking 保留 M3 原值（对 CJK 视觉影响 <0.5sp，可忽略）。
// =============================================================================

private val CtFontFamily = FontFamily.Default

// ---------------------------------------------------------------------------
// Baseline · 15 样式（MaterialTheme.typography）
// ---------------------------------------------------------------------------
val CtTypography = Typography(
    /** 57/64 · w400 · -0.2（中文行高保持 64：Display 已足够松） */
    displayLarge = TextStyle(
        fontFamily = CtFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.2).sp,
    ),
    /** 45/52 · w400 · 0 */
    displayMedium = TextStyle(
        fontFamily = CtFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    ),
    /** 36/44 · w400 · 0 */
    displaySmall = TextStyle(
        fontFamily = CtFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),
    /** 32/40 · w400 · 0 */
    headlineLarge = TextStyle(
        fontFamily = CtFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    /** 28/36 · w400 · 0 */
    headlineMedium = TextStyle(
        fontFamily = CtFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    /** 24/32 · w400 · 0 → 中文 34 */
    headlineSmall = TextStyle(
        fontFamily = CtFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
    ),
    /** 22/28 · w400 · 0 → 中文 30 */
    titleLarge = TextStyle(
        fontFamily = CtFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp,
    ),
    /** 16/24 · w500 · 0.2 → 中文 26 */
    titleMedium = TextStyle(
        fontFamily = CtFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.2.sp,
    ),
    /** 14/20 · w500 · 0.1 → 中文 22 */
    titleSmall = TextStyle(
        fontFamily = CtFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    /** 16/24 · w400 · 0.5 → 中文 26 */
    bodyLarge = TextStyle(
        fontFamily = CtFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.5.sp,
    ),
    /** 14/20 · w400 · 0.2 → 中文 22 */
    bodyMedium = TextStyle(
        fontFamily = CtFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.2.sp,
    ),
    /** 12/16 · w400 · 0.4 → 中文 18 */
    bodySmall = TextStyle(
        fontFamily = CtFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp,
    ),
    /** 14/20 · w500 · 0.1 → 中文 21 */
    labelLarge = TextStyle(
        fontFamily = CtFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp,
    ),
    /** 12/16 · w500 · 0.5 → 中文 18 */
    labelMedium = TextStyle(
        fontFamily = CtFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp,
    ),
    /** 11/16 · w500 · 0.5 → 中文 17 */
    labelSmall = TextStyle(
        fontFamily = CtFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.5.sp,
    ),
)

// ---------------------------------------------------------------------------
// Emphasized · 15 样式（表达性时刻；每屏最多 2 处，见 design/ANDROID17_DESIGN.md §3.3）
//
// material3 1.4.0 的公开 Typography 构造不能覆盖 emphasized，故用独立类型 + CompositionLocal。
// 用法：`MaterialTheme.ctEmphasized.displayMediumEmphasized`（见文件末尾扩展属性）。
// ---------------------------------------------------------------------------
@Immutable
data class CtEmphasizedTypography(
    val displayLargeEmphasized: TextStyle = TextStyle(
        fontFamily = CtFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = 0.sp,
    ),
    val displayMediumEmphasized: TextStyle = TextStyle(
        fontFamily = CtFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp,
    ),
    val displaySmallEmphasized: TextStyle = TextStyle(
        fontFamily = CtFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp,
    ),
    val headlineLargeEmphasized: TextStyle = TextStyle(
        fontFamily = CtFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp,
    ),
    val headlineMediumEmphasized: TextStyle = TextStyle(
        fontFamily = CtFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp,
    ),
    val headlineSmallEmphasized: TextStyle = TextStyle(
        fontFamily = CtFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 24.sp, lineHeight = 34.sp, letterSpacing = 0.sp,
    ),
    val titleLargeEmphasized: TextStyle = TextStyle(
        fontFamily = CtFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 22.sp, lineHeight = 30.sp, letterSpacing = 0.sp,
    ),
    val titleMediumEmphasized: TextStyle = TextStyle(
        fontFamily = CtFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.15.sp,
    ),
    val titleSmallEmphasized: TextStyle = TextStyle(
        fontFamily = CtFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp,
    ),
    val bodyLargeEmphasized: TextStyle = TextStyle(
        fontFamily = CtFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.15.sp,
    ),
    val bodyMediumEmphasized: TextStyle = TextStyle(
        fontFamily = CtFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.25.sp,
    ),
    val bodySmallEmphasized: TextStyle = TextStyle(
        fontFamily = CtFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.4.sp,
    ),
    val labelLargeEmphasized: TextStyle = TextStyle(
        fontFamily = CtFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.1.sp,
    ),
    val labelMediumEmphasized: TextStyle = TextStyle(
        fontFamily = CtFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.5.sp,
    ),
    val labelSmallEmphasized: TextStyle = TextStyle(
        fontFamily = CtFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 11.sp, lineHeight = 17.sp, letterSpacing = 0.5.sp,
    ),
)

val CtEmphasizedType = CtEmphasizedTypography()

/** 表达性字型 CompositionLocal；由 ClassToolsTheme 提供。 */
val LocalCtEmphasizedType = staticCompositionLocalOf { CtEmphasizedType }

/**
 * 表达性字型访问器：
 * ```
 * Text("86.42", style = MaterialTheme.ctEmphasized.displayMediumEmphasized)
 * ```
 */
val MaterialTheme.ctEmphasized: CtEmphasizedTypography
    @Composable @ReadOnlyComposable
    get() = LocalCtEmphasizedType.current
