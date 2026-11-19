package com.furina.classtools.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// =============================================================================
// Material 3 Typography（15 baseline + 15 emphasized）
//
// 数值来自 [CtDesign.kt] 的 [CtType]（唯一令牌源），保证「M3 组件」与「自绘组件」
// 使用同一套字号：
//   displayLarge 36 / displayMedium 32 / displaySmall 28（= displayTitle）
//   headlineLarge 28 / headlineMedium 24 / headlineSmall 20（= 工具页顶栏）
//   titleLarge 20 / titleMedium 16 / titleSmall 14
//   bodyLarge 15 / bodyMedium 14 / bodySmall 12
//   labelLarge 13 / labelMedium 12 / labelSmall 11
//
// 行高按中文可读性（1.3–1.5）取整；字体族用系统默认（Roboto Flex 拉丁 + Noto Sans CJK 中文），
// 不打包字体（体积 / 多设备品牌一致性由系统字体承担；weight 轴用于 Expressive 的情绪层级）。
// =============================================================================

private val CtFontFamily = FontFamily.Default

private fun style(
    weight: FontWeight,
    size: Int,
    line: Int,
    tracking: Double = 0.0,
): TextStyle = TextStyle(
    fontFamily = CtFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

val CtTypography = Typography(
    displayLarge = style(FontWeight.Medium, 36, 46, -0.2),
    displayMedium = style(FontWeight.Medium, 32, 42, -0.1),
    displaySmall = style(FontWeight.Bold, 28, 36),
    headlineLarge = style(FontWeight.Bold, 28, 36),
    headlineMedium = style(FontWeight.Bold, 24, 32),
    headlineSmall = style(FontWeight.Bold, 20, 27),
    titleLarge = style(FontWeight.Medium, 20, 28),
    titleMedium = style(FontWeight.Medium, CtType.title16Size.value.toInt(), CtType.title16Line, 0.1),
    titleSmall = style(FontWeight.Medium, 14, 21, 0.1),
    bodyLarge = style(FontWeight.Normal, CtType.bodySize.value.toInt(), CtType.bodyLine, 0.2),
    bodyMedium = style(FontWeight.Normal, 14, 21, 0.2),
    bodySmall = style(FontWeight.Normal, CtType.captionSize.value.toInt(), CtType.captionLine, 0.3),
    labelLarge = style(FontWeight.Medium, CtType.labelSize.value.toInt(), CtType.labelLine, 0.1),
    labelMedium = style(FontWeight.Medium, CtType.captionSize.value.toInt(), CtType.captionLine, 0.4),
    labelSmall = style(FontWeight.Medium, CtType.microSize.value.toInt(), CtType.microLine, 0.4),
)

/** Emphasized 变体（表达性时刻：hero/统计数字；每屏最多 2 处）。 */
data class CtEmphasizedTypography(
    val displayLargeEmphasized: TextStyle = style(FontWeight.SemiBold, 36, 46),
    val displayMediumEmphasized: TextStyle = style(FontWeight.SemiBold, 32, 42),
    val displaySmallEmphasized: TextStyle = style(FontWeight.Bold, 28, 36),
    val headlineLargeEmphasized: TextStyle = style(FontWeight.Bold, 28, 36),
    val headlineMediumEmphasized: TextStyle = style(FontWeight.Bold, 24, 32),
    val headlineSmallEmphasized: TextStyle = style(FontWeight.Bold, 20, 27),
    val titleLargeEmphasized: TextStyle = style(FontWeight.SemiBold, 20, 28),
    val titleMediumEmphasized: TextStyle = style(FontWeight.SemiBold, 16, 24),
    val titleSmallEmphasized: TextStyle = style(FontWeight.SemiBold, 14, 21),
    val bodyLargeEmphasized: TextStyle = style(FontWeight.Medium, 15, 22),
    val bodyMediumEmphasized: TextStyle = style(FontWeight.Medium, 14, 21),
    val bodySmallEmphasized: TextStyle = style(FontWeight.Medium, 12, 17),
    val labelLargeEmphasized: TextStyle = style(FontWeight.SemiBold, 13, 18),
    val labelMediumEmphasized: TextStyle = style(FontWeight.SemiBold, 12, 17),
    val labelSmallEmphasized: TextStyle = style(FontWeight.SemiBold, 11, 15),
)

val CtEmphasizedType = CtEmphasizedTypography()

/** 表达性字型 CompositionLocal；由 ClassToolsTheme 提供。 */
val LocalCtEmphasizedType = staticCompositionLocalOf { CtEmphasizedType }

/** 表达性字型访问器：`MaterialTheme.ctEmphasized.displayMediumEmphasized`。 */
val MaterialTheme.ctEmphasized: CtEmphasizedTypography
    @Composable @ReadOnlyComposable
    get() = LocalCtEmphasizedType.current
