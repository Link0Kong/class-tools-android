package com.furina.classtools.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// =============================================================================
// Material 3 Typography（15 baseline + 15 emphasized）
//
// 2026-11-21 按 MD3 官方知识库（lwhhz/Android-MD3-Knowledge）重做：
//   01-Foundations/typography.md · 03-Android-Compose/typography.md
//
// 三条硬规则（对应 ui-review-checklist 的「主题 / 设计一致性」阻断项）：
//   1. **不手写 15 档数值**：基线直接取 Compose material3 的 `Typography()` 默认值
//      （= 官方 M3 type scale），只对行高做语言适配；
//   2. **常规层级只用 400 / 500**：Display / Headline / Title Large / Body 为 400，
//      Title Medium / Title Small / 全部 Label 为 500。700 仅用于 Emphasized 的极端强调，
//      中文里 700 容易糊成一团，页面标题不再用 Bold；
//   3. **中文行高 ×1.07**：官方语言高度分类里中文属 Medium（约 +7%），
//      行高 = 官方行高 × 1.07 后取整；改字号时行高自动跟着走，不再各页面手调。
//
// 页面统一用 [CtTypeScale] 的语义名（display / title / headline / body / label / caption），
// 不写 `fontSize = 34.sp` 这类脱离字阶的覆盖。
// =============================================================================

/** 中文语言高度系数（MD3 语言高度 Medium 档 ≈ ×1.07）。 */
private const val ZH_LINE_HEIGHT_SCALE = 1.07f

/** 按中文语言高度加高行高；非 sp 行高（如 Unspecified）原样返回。 */
private fun TextStyle.withChineseLineHeight(): TextStyle {
    if (lineHeight == TextUnit.Unspecified) return this
    return copy(lineHeight = (lineHeight.value * ZH_LINE_HEIGHT_SCALE).roundToInt().sp)
}

/**
 * 应用字阶：M3 baseline 15 档 + 中文行高。
 *
 * 字体族保留系统默认（Roboto Flex 拉丁 + 设备中文字体），不打包字体：
 * 中文字体由各厂商提供，换 family 时行高与字距必须一起换（官方提醒）。
 */
val CtTypography: Typography = Typography().run {
    Typography(
        displayLarge = displayLarge.withChineseLineHeight(),
        displayMedium = displayMedium.withChineseLineHeight(),
        displaySmall = displaySmall.withChineseLineHeight(),
        headlineLarge = headlineLarge.withChineseLineHeight(),
        headlineMedium = headlineMedium.withChineseLineHeight(),
        headlineSmall = headlineSmall.withChineseLineHeight(),
        titleLarge = titleLarge.withChineseLineHeight(),
        titleMedium = titleMedium.withChineseLineHeight(),
        titleSmall = titleSmall.withChineseLineHeight(),
        bodyLarge = bodyLarge.withChineseLineHeight(),
        bodyMedium = bodyMedium.withChineseLineHeight(),
        bodySmall = bodySmall.withChineseLineHeight(),
        labelLarge = labelLarge.withChineseLineHeight(),
        labelMedium = labelMedium.withChineseLineHeight(),
        labelSmall = labelSmall.withChineseLineHeight(),
    )
}

/** 系统默认字体族（与 [CtTypography] 一致；供少数自绘组件显式引用）。 */
val CtFontFamily: FontFamily = CtTypography.bodyMedium.fontFamily ?: FontFamily.Default

/**
 * Emphasized 变体（M3 的同一套字阶、更重的一档；不是更大的字）。
 *
 * 官方适用位置：Badges / Buttons（仅主操作）/ Extended FAB / 选中列表项 / 选中菜单项。
 * 本 App 只把 hero 数字、主操作、选中态换用 emphasized；普通标题一律 baseline。
 *
 * 字重规则：baseline 400 → emphasized 500；baseline 500 → emphasized 600。
 */
data class CtEmphasizedTypography(
    val displayLargeEmphasized: TextStyle = CtTypography.displayLarge.copy(fontWeight = FontWeight.Medium),
    val displayMediumEmphasized: TextStyle = CtTypography.displayMedium.copy(fontWeight = FontWeight.Medium),
    val displaySmallEmphasized: TextStyle = CtTypography.displaySmall.copy(fontWeight = FontWeight.Medium),
    val headlineLargeEmphasized: TextStyle = CtTypography.headlineLarge.copy(fontWeight = FontWeight.Medium),
    val headlineMediumEmphasized: TextStyle = CtTypography.headlineMedium.copy(fontWeight = FontWeight.Medium),
    val headlineSmallEmphasized: TextStyle = CtTypography.headlineSmall.copy(fontWeight = FontWeight.Medium),
    val titleLargeEmphasized: TextStyle = CtTypography.titleLarge.copy(fontWeight = FontWeight.Medium),
    val titleMediumEmphasized: TextStyle = CtTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    val titleSmallEmphasized: TextStyle = CtTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    val bodyLargeEmphasized: TextStyle = CtTypography.bodyLarge.copy(fontWeight = FontWeight.Medium),
    val bodyMediumEmphasized: TextStyle = CtTypography.bodyMedium.copy(fontWeight = FontWeight.Medium),
    val bodySmallEmphasized: TextStyle = CtTypography.bodySmall.copy(fontWeight = FontWeight.Medium),
    val labelLargeEmphasized: TextStyle = CtTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    val labelMediumEmphasized: TextStyle = CtTypography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
    val labelSmallEmphasized: TextStyle = CtTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
)

val CtEmphasizedType = CtEmphasizedTypography()

/** 表达性字型 CompositionLocal；由 ClassToolsTheme 提供。 */
val LocalCtEmphasizedType = staticCompositionLocalOf { CtEmphasizedType }

/** 表达性字型访问器：`MaterialTheme.ctEmphasized.displayMediumEmphasized`。 */
val MaterialTheme.ctEmphasized: CtEmphasizedTypography
    @Composable @ReadOnlyComposable
    get() = LocalCtEmphasizedType.current
