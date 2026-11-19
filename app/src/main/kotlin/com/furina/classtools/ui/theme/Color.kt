package com.furina.classtools.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// M3 角色 → 具体颜色（明 / 暗两套，各 48 角色）
//
// 数值全部来自 [CtDesign.kt]（唯一令牌源）：品牌种子 [CtPalette]，中性/容器色同源。
// 本文件不做「设计决策」，只把 [CtPalette] 的槽位映射到 Material 3 的 ColorScheme 角色，
// 保证 M3 组件（Button/Card/TextField/Switch…）与自绘组件取到同一套颜色。
//
// AA 对比度（WCAG 2.1）已逐项核算，明细见 docs/DESIGN_SYSTEM.md §2.4：
//   light  onSurface/surface 15.4 · onSurfaceVariant/surface 6.5 · primary/onPrimary 6.1
//   dark   onSurface/surface 14.2 · onSurfaceVariant/surface 7.7 · primary/onPrimary 10.9
// =============================================================================

// ---------------------------------------------------------------------------
// 品牌种子（导出卡/图示/图表等固定使用；业务 UI 请走 ColorScheme 或 CtColor）
// ---------------------------------------------------------------------------
val BrandMistTeal = CtPalette.MistTeal
val BrandSlateBlue = CtPalette.SlateBlue
val BrandWarmSand = CtPalette.WarmSand
val BrandWarmWhite = CtPalette.Bg

/** 品牌墨色（导出卡文字）。 */
val BrandInk = CtPalette.TextPrimary
val BrandInk2 = CtPalette.TextSecondary
val BrandInk3 = CtPalette.TextTertiary

// ---------------------------------------------------------------------------
// 浅色 · 48 角色
// ---------------------------------------------------------------------------
val LightPrimary = Color(0xFF24695C)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFACF0DF)
val LightOnPrimaryContainer = Color(0xFF00201B)
val LightInversePrimary = Color(0xFF90D3C4)

val LightSecondary = Color(0xFF43617C)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFCCE5FF)
val LightOnSecondaryContainer = Color(0xFF001D31)

val LightTertiary = Color(0xFF7B5730)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFFFDCBC)
val LightOnTertiaryContainer = Color(0xFF2C1700)

val LightError = Color(0xFF9A433E)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD7)
val LightOnErrorContainer = Color(0xFF400104)

val LightBackground = CtPalette.Bg
val LightOnBackground = CtPalette.TextPrimary

val LightSurface = CtPalette.Surface
val LightOnSurface = CtPalette.TextPrimary
val LightSurfaceVariant = CtPalette.ContainerHigh
val LightOnSurfaceVariant = CtPalette.TextSecondary
val LightSurfaceTint = LightPrimary
val LightInverseSurface = CtPalette.TextPrimary
val LightInverseOnSurface = CtPalette.Container
val LightOutline = CtPalette.Outline
val LightOutlineVariant = CtPalette.Divider
val LightScrim = CtPalette.ScrimLight

val LightSurfaceDim = CtPalette.ContainerHighest
val LightSurfaceBright = CtPalette.Surface
val LightSurfaceContainerLowest = CtPalette.Surface
val LightSurfaceContainerLow = CtPalette.ContainerLow
val LightSurfaceContainer = CtPalette.Container
val LightSurfaceContainerHigh = CtPalette.ContainerHigh
val LightSurfaceContainerHighest = CtPalette.ContainerHighest

val LightPrimaryFixed = Color(0xFFACF0DF)
val LightPrimaryFixedDim = Color(0xFF90D3C4)
val LightOnPrimaryFixed = Color(0xFF00201B)
val LightOnPrimaryFixedVariant = Color(0xFF005045)
val LightSecondaryFixed = Color(0xFFCCE5FF)
val LightSecondaryFixedDim = Color(0xFFABCAE8)
val LightOnSecondaryFixed = Color(0xFF001D31)
val LightOnSecondaryFixedVariant = Color(0xFF2B4963)
val LightTertiaryFixed = Color(0xFFFFDCBC)
val LightTertiaryFixedDim = Color(0xFFEDBE8E)
val LightOnTertiaryFixed = Color(0xFF2C1700)
val LightOnTertiaryFixedVariant = Color(0xFF60401B)

// ---------------------------------------------------------------------------
// 深色 · 48 角色（surface #121517 / container #1A1E21 / high #232A2E）
// ---------------------------------------------------------------------------
val DarkPrimary = Color(0xFF90D3C4)
val DarkOnPrimary = Color(0xFF00382F)
val DarkPrimaryContainer = Color(0xFF005045)
val DarkOnPrimaryContainer = Color(0xFFACF0DF)
val DarkInversePrimary = Color(0xFF24695C)

val DarkSecondary = Color(0xFFABCAE8)
val DarkOnSecondary = Color(0xFF12334B)
val DarkSecondaryContainer = Color(0xFF2B4963)
val DarkOnSecondaryContainer = Color(0xFFCCE5FF)

val DarkTertiary = Color(0xFFEDBE8E)
val DarkOnTertiary = Color(0xFF462A07)
val DarkTertiaryContainer = Color(0xFF60401B)
val DarkOnTertiaryContainer = Color(0xFFFFDCBC)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

val DarkBackground = CtPalette.DarkBg
val DarkOnBackground = CtPalette.DarkTextPrimary

val DarkSurface = CtPalette.DarkSurface
val DarkOnSurface = CtPalette.DarkTextPrimary
val DarkSurfaceVariant = CtPalette.DarkContainerHigh
val DarkOnSurfaceVariant = CtPalette.DarkTextSecondary
val DarkSurfaceTint = DarkPrimary
val DarkInverseSurface = CtPalette.DarkTextPrimary
val DarkInverseOnSurface = CtPalette.DarkSurface
val DarkOutline = CtPalette.DarkOutline
val DarkOutlineVariant = CtPalette.DarkDivider
val DarkScrim = CtPalette.ScrimLight

val DarkSurfaceDim = CtPalette.DarkBg
val DarkSurfaceBright = CtPalette.DarkContainerHighest
val DarkSurfaceContainerLowest = CtPalette.DarkBg
val DarkSurfaceContainerLow = CtPalette.DarkContainerLow
val DarkSurfaceContainer = CtPalette.DarkContainer
val DarkSurfaceContainerHigh = CtPalette.DarkContainerHigh
val DarkSurfaceContainerHighest = CtPalette.DarkContainerHighest

val DarkPrimaryFixed = Color(0xFFACF0DF)
val DarkPrimaryFixedDim = Color(0xFF90D3C4)
val DarkOnPrimaryFixed = Color(0xFF00201B)
val DarkOnPrimaryFixedVariant = Color(0xFF005045)
val DarkSecondaryFixed = Color(0xFFCCE5FF)
val DarkSecondaryFixedDim = Color(0xFFABCAE8)
val DarkOnSecondaryFixed = Color(0xFF001D31)
val DarkOnSecondaryFixedVariant = Color(0xFF2B4963)
val DarkTertiaryFixed = Color(0xFFFFDCBC)
val DarkTertiaryFixedDim = Color(0xFFEDBE8E)
val DarkOnTertiaryFixed = Color(0xFF2C1700)
val DarkOnTertiaryFixedVariant = Color(0xFF60401B)

// ---------------------------------------------------------------------------
// 品牌语义色（Material ColorScheme 之外的扩展；通过 LocalCtBrandColors 提供）
// ---------------------------------------------------------------------------
data class CtBrandColors(
    /** 品牌雾青字面色（图形/大字号）。 */
    val brandMist: Color,
    val brandBlue: Color,
    val brandSand: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    /** 弱危险（图标/小标签级，正文危险仍用 colorScheme.error）。 */
    val dangerSoft: Color,
    val powerPositive: Color,
    val powerWarn: Color,
    val powerLow: Color,
    val waterAccent: Color,
)

val LightCtBrandColors = CtBrandColors(
    brandMist = CtAccents.teal.tintRef,
    brandBlue = CtAccents.blue.tintRef,
    brandSand = CtAccents.sand.tintRef,
    success = CtSemantic.successLight,
    warning = CtSemantic.warningLight,
    info = CtSemantic.infoLight,
    dangerSoft = CtSemantic.dangerSoftLight,
    powerPositive = CtSemantic.successLight,
    powerWarn = CtAccents.sand.textRef,
    powerLow = CtSemantic.dangerSoftLight,
    waterAccent = CtSemantic.infoLight,
)

val DarkCtBrandColors = CtBrandColors(
    brandMist = CtAccents.darkTeal.tintRef,
    brandBlue = CtAccents.darkBlue.tintRef,
    brandSand = CtAccents.darkSand.tintRef,
    success = CtSemantic.successDark,
    warning = CtSemantic.warningDark,
    info = CtSemantic.infoDark,
    dangerSoft = CtSemantic.dangerSoftDark,
    powerPositive = CtSemantic.successDark,
    powerWarn = CtAccents.darkSand.textRef,
    powerLow = CtSemantic.dangerSoftDark,
    waterAccent = CtSemantic.infoDark,
)

/** 品牌语义色 CompositionLocal；默认浅色（Preview/孤立组件也能取到）。 */
val LocalCtBrandColors =
    androidx.compose.runtime.staticCompositionLocalOf { LightCtBrandColors }
