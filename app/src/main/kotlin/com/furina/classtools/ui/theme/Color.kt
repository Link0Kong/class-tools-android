package com.furina.classtools.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// =============================================================================
// 班级工具站 · Android 17 / Material 3 Expressive 色彩体系
//
// 权威来源：`design/tokens.json`（HCT tonal palette + M3 标准 role->tone 映射）。
//   - 品牌种子：雾青 #4E9082 / 灰蓝 #6E8CA8 / 暖沙 #B98F63 / 暖白 #F7F8F8
//   - primary 使用雾青的同色相 tone-40（#24695C），保证白字对比度 6.46:1（AA）；
//     品牌字面色 #4E9082 只用于大字号/图标/图形（对比 3.73:1，见 CtBrandColors.brandMist）。
//   - 明暗两套各 48 个 M3 角色，全部通过 WCAG 审计（design/ANDROID17_DESIGN.md §2.6）。
//
// 本文件是 ColorScheme 的唯一数据源；不要在别处硬编码颜色。
// =============================================================================

// ---------------------------------------------------------------------------
// 品牌种子色（不可变；导出卡/图示/图表等固定使用）
// ---------------------------------------------------------------------------
val BrandMistTeal = Color(0xFF4E9082)
val BrandSlateBlue = Color(0xFF6E8CA8)
val BrandWarmSand = Color(0xFFB98F63)
val BrandWarmWhite = Color(0xFFF7F8F8)
val BrandInk = Color(0xFF23302E)
val BrandInk2 = Color(0xFF5A6A67)
val BrandInk3 = Color(0xFF8C9A97)

// =============================================================================
// 浅色 · M3 48 角色
// =============================================================================
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

val LightBackground = Color(0xFFF7F8F8)
val LightOnBackground = Color(0xFF1C2629)

val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF1C2629)
val LightSurfaceVariant = Color(0xFFEEF2F2)
val LightOnSurfaceVariant = Color(0xFF66777D)
val LightSurfaceTint = LightPrimary
val LightInverseSurface = Color(0xFF2E3131)
val LightInverseOnSurface = Color(0xFFF0F1F1)
val LightOutline = Color(0xFF93A2A7)
val LightOutlineVariant = Color(0xFFE8EDEE)
val LightScrim = Color(0xFF000000)

val LightSurfaceDim = Color(0xFFE8EDEE)
val LightSurfaceBright = Color(0xFFFFFFFF)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFFFFFFF)
val LightSurfaceContainer = Color(0xFFF7F8F8)
val LightSurfaceContainerHigh = Color(0xFFEDF2F1)
val LightSurfaceContainerHighest = Color(0xFFE4EAE9)

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

// =============================================================================
// 深色 · M3 48 角色（fixed 角色明暗同值，见测试/验收）
// =============================================================================
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

val DarkError = Color(0xFFFFB3AD)
val DarkOnError = Color(0xFF5E1615)
val DarkErrorContainer = Color(0xFF7C2C29)
val DarkOnErrorContainer = Color(0xFFFFDAD7)

val DarkBackground = Color(0xFF0F1416)
val DarkOnBackground = Color(0xFFE9EEED)

val DarkSurface = Color(0xFF161D20)
val DarkOnSurface = Color(0xFFE9EEED)
val DarkSurfaceVariant = Color(0xFF232E31)
val DarkOnSurfaceVariant = Color(0xFFA6B4B8)
val DarkSurfaceTint = DarkPrimary
val DarkInverseSurface = Color(0xFFE1E3E3)
val DarkInverseOnSurface = Color(0xFF2E3131)
val DarkOutline = Color(0xFF7E8E92)
val DarkOutlineVariant = Color(0xFF2A3538)
val DarkScrim = Color(0xFF000000)

val DarkSurfaceDim = Color(0xFF0D1214)
val DarkSurfaceBright = Color(0xFF303C3F)
val DarkSurfaceContainerLowest = Color(0xFF0B0F11)
val DarkSurfaceContainerLow = Color(0xFF181F22)
val DarkSurfaceContainer = Color(0xFF1D2629)
val DarkSurfaceContainerHigh = Color(0xFF232E31)
val DarkSurfaceContainerHighest = Color(0xFF2B3739)

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

// =============================================================================
// 品牌语义色（Material ColorScheme 之外的扩展；通过 LocalCtBrandColors 提供）
// =============================================================================
@Immutable
data class CtBrandColors(
    /** 品牌雾青字面色：仅用于大字号/图标/图形（对白 3.73:1，不可承载小正文）。 */
    val brandMist: Color,
    /** 品牌灰蓝字面色：同上（对白 3.51:1）。 */
    val brandBlue: Color,
    /** 品牌暖沙字面色。 */
    val brandSand: Color,
    /** 状态：成功/已完成/已同步。 */
    val success: Color,
    /** 状态：需要注意（低电、慢更新、待补发）。 */
    val warning: Color,
    /** 状态：同步中/信息/离线。 */
    val info: Color,
    /** 弱危险（图标/小标签级，正文危险仍用 colorScheme.error）。 */
    val dangerSoft: Color,
    /** 电费：充足 / 偏低 / 低电。 */
    val powerPositive: Color,
    val powerWarn: Color,
    val powerLow: Color,
    /** 水量/信息图表的蓝色强调。 */
    val waterAccent: Color,
)

val LightCtBrandColors = CtBrandColors(
    brandMist = BrandMistTeal,
    brandBlue = BrandSlateBlue,
    brandSand = BrandWarmSand,
    success = Color(0xFF3E7B5E),
    warning = Color(0xFF8A6430),
    info = Color(0xFF4C6B85),
    dangerSoft = Color(0xFFC0605A),
    powerPositive = Color(0xFF3E7B5E),
    powerWarn = BrandWarmSand,
    powerLow = Color(0xFFC0605A),
    waterAccent = Color(0xFF4C6B85),
)

val DarkCtBrandColors = CtBrandColors(
    brandMist = BrandMistTeal,
    brandBlue = BrandSlateBlue,
    brandSand = BrandWarmSand,
    success = Color(0xFF8FCFB4),
    warning = Color(0xFFF0C88E),
    info = Color(0xFFA8C6E2),
    dangerSoft = Color(0xFFF0B4AA),
    powerPositive = Color(0xFF8FCFB4),
    powerWarn = Color(0xFFEDBE8E),
    powerLow = Color(0xFFF0B4AA),
    waterAccent = Color(0xFFA8C6E2),
)

/** 品牌语义色 CompositionLocal；默认浅色（Preview/孤立组件也能取到）。 */
val LocalCtBrandColors = staticCompositionLocalOf { LightCtBrandColors }

// =============================================================================
// 玻璃 / 模糊材质 token（设计规格 §2.5；Compose 中作为半透明表面使用）
// =============================================================================
@Immutable
data class CtGlassColors(
    /** 毛玻璃底（叠加在模糊层上；Android 12+ 才真正模糊，低版本退化为半透明纯色）。 */
    val container: Color,
    /** 1dp 高光描边。 */
    val border: Color,
    /** 品牌色调薄涂层。 */
    val tint: Color,
    /** Dialog/Sheet 背板遮罩。 */
    val scrim: Color,
)

val LightCtGlassColors = CtGlassColors(
    container = BrandWarmWhite.copy(alpha = 0.72f),
    border = Color.White.copy(alpha = 0.55f),
    tint = BrandMistTeal.copy(alpha = 0.08f),
    scrim = Color.Black.copy(alpha = 0.38f),
)

val DarkCtGlassColors = CtGlassColors(
    container = Color(0xFF191C1C).copy(alpha = 0.66f),
    border = Color.White.copy(alpha = 0.12f),
    tint = DarkPrimary.copy(alpha = 0.10f),
    scrim = Color.Black.copy(alpha = 0.50f),
)

val LocalCtGlassColors = staticCompositionLocalOf { LightCtGlassColors }
