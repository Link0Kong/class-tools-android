package com.furina.classtools.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// =============================================================================
// 班级工具站 · 主题入口
//
// 用法：ClassToolsTheme(darkTheme = isSystemInDarkTheme(), dynamicColor = false) { ... }
//
// 取色策略（design/ANDROID17_DESIGN.md §2.1）：
//   L1 品牌固定方案（默认）：品牌 HCT 色板生成的明/暗 48 角色，跨端一致、QA 可复现。
//   L2 品牌种子动态（可选，Android 12+）：surface/neutral 跟随系统壁纸，
//      primary/secondary/tertiary 仍保留品牌色（hybrid），避免失去班级工具识别。
//   L3 完全动态：暂不提供（会丢失功能语义色与品牌）。
// =============================================================================

/** Material Theme 之外的语义间距/可点区 token（组件与页面共用；2026-11-17 对齐鸿蒙 PAGE_PADDING 20 / GAP 12）。 */
data class CtSpacing(
    /** 页面左右边距。 */
    val screenHorizontal: Dp = 20.dp,
    /** 大区块之间的垂直间距。 */
    val sectionGap: Dp = 20.dp,
    /** 卡片之间的间距。 */
    val cardGap: Dp = 12.dp,
    /** 最小触控目标（无障碍硬线 48dp）。 */
    val minTouchTarget: Dp = 48.dp,
    /** 卡片内边距。 */
    val cardPadding: Dp = 14.dp,
    /** medium 宽度下的页面边距。 */
    val pageHorizontalMedium: Dp = 24.dp,
    /** expanded 宽度下的页面边距。 */
    val pageHorizontalExpanded: Dp = 32.dp,
    /** 大屏内容最大宽度（居中留白）。 */
    val contentMaxWidth: Dp = 960.dp,
)

val LocalCtSpacing = staticCompositionLocalOf { CtSpacing() }

/** 当前是否深色主题（供自绘组件取玻璃/阴影等非 ColorScheme 数值时判断）。 */
val LocalCtDarkTheme = staticCompositionLocalOf { false }

// ---------------------------------------------------------------------------
// 浅色 ColorScheme（48 角色；数值 = design/tokens.json → color.light）
// ---------------------------------------------------------------------------
private val CtLightColorScheme: ColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    inversePrimary = LightInversePrimary,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = LightSurfaceTint,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = LightScrim,
    surfaceBright = LightSurfaceBright,
    surfaceDim = LightSurfaceDim,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    primaryFixed = LightPrimaryFixed,
    primaryFixedDim = LightPrimaryFixedDim,
    onPrimaryFixed = LightOnPrimaryFixed,
    onPrimaryFixedVariant = LightOnPrimaryFixedVariant,
    secondaryFixed = LightSecondaryFixed,
    secondaryFixedDim = LightSecondaryFixedDim,
    onSecondaryFixed = LightOnSecondaryFixed,
    onSecondaryFixedVariant = LightOnSecondaryFixedVariant,
    tertiaryFixed = LightTertiaryFixed,
    tertiaryFixedDim = LightTertiaryFixedDim,
    onTertiaryFixed = LightOnTertiaryFixed,
    onTertiaryFixedVariant = LightOnTertiaryFixedVariant,
)

// ---------------------------------------------------------------------------
// 深色 ColorScheme（48 角色；数值 = design/tokens.json → color.dark）
// ---------------------------------------------------------------------------
private val CtDarkColorScheme: ColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = DarkInversePrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkSurfaceTint,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = DarkScrim,
    surfaceBright = DarkSurfaceBright,
    surfaceDim = DarkSurfaceDim,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    primaryFixed = DarkPrimaryFixed,
    primaryFixedDim = DarkPrimaryFixedDim,
    onPrimaryFixed = DarkOnPrimaryFixed,
    onPrimaryFixedVariant = DarkOnPrimaryFixedVariant,
    secondaryFixed = DarkSecondaryFixed,
    secondaryFixedDim = DarkSecondaryFixedDim,
    onSecondaryFixed = DarkOnSecondaryFixed,
    onSecondaryFixedVariant = DarkOnSecondaryFixedVariant,
    tertiaryFixed = DarkTertiaryFixed,
    tertiaryFixedDim = DarkTertiaryFixedDim,
    onTertiaryFixed = DarkOnTertiaryFixed,
    onTertiaryFixedVariant = DarkOnTertiaryFixedVariant,
)

/**
 * L2 混合动态取色：surface/neutral 跟随系统壁纸，品牌 primary/secondary/tertiary
 * （含容器与 fixed 角色）保留品牌方案，避免「跟随壁纸后认不出班级工具」。
 */
private fun brandedDynamicScheme(context: android.content.Context, dark: Boolean): ColorScheme {
    val dynamic = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    val brand = if (dark) CtDarkColorScheme else CtLightColorScheme
    return dynamic.copy(
        primary = brand.primary,
        onPrimary = brand.onPrimary,
        primaryContainer = brand.primaryContainer,
        onPrimaryContainer = brand.onPrimaryContainer,
        inversePrimary = brand.inversePrimary,
        secondary = brand.secondary,
        onSecondary = brand.onSecondary,
        secondaryContainer = brand.secondaryContainer,
        onSecondaryContainer = brand.onSecondaryContainer,
        tertiary = brand.tertiary,
        onTertiary = brand.onTertiary,
        tertiaryContainer = brand.tertiaryContainer,
        onTertiaryContainer = brand.onTertiaryContainer,
        primaryFixed = brand.primaryFixed,
        primaryFixedDim = brand.primaryFixedDim,
        onPrimaryFixed = brand.onPrimaryFixed,
        onPrimaryFixedVariant = brand.onPrimaryFixedVariant,
        secondaryFixed = brand.secondaryFixed,
        secondaryFixedDim = brand.secondaryFixedDim,
        onSecondaryFixed = brand.onSecondaryFixed,
        onSecondaryFixedVariant = brand.onSecondaryFixedVariant,
        tertiaryFixed = brand.tertiaryFixed,
        tertiaryFixedDim = brand.tertiaryFixedDim,
        onTertiaryFixed = brand.onTertiaryFixed,
        onTertiaryFixedVariant = brand.onTertiaryFixedVariant,
    )
}

/**
 * 全局主题。
 *
 * @param darkTheme 是否深色（默认跟随系统）。调用方按「外观模式」用
 *   [AppearanceMode.resolveDark] 解析后传入即可强制浅色/深色（我的 → 外观），
 *   传 `isSystemInDarkTheme()` 则跟随系统。
 * @param dynamicColor 是否跟随系统壁纸取色（Material You）。默认关闭以保持品牌识别；
 *   Android 12+ 开启后为「混合动态色」（品牌主色保留，surface 跟随壁纸）。
 * @param spacing 语义间距（默认 CtSpacing；大屏可由调用方覆盖）。
 */
@Composable
fun ClassToolsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    spacing: CtSpacing = CtSpacing(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            brandedDynamicScheme(context, darkTheme)

        darkTheme -> CtDarkColorScheme
        else -> CtLightColorScheme
    }

    // 品牌语义色 / 玻璃材质 / 表达性字型：随明暗切换。
    // 系统栏图标随主题切换：深色模式状态栏/导航栏图标用浅色，避免「黑字压暗底」不可读。
    LaunchedEffect(darkTheme) {
        val activity = context.findActivity() ?: return@LaunchedEffect
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val brandColors = if (darkTheme) DarkCtBrandColors else LightCtBrandColors
    val glassColors = if (darkTheme) DarkCtGlassColors else LightCtGlassColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CtTypography,
        shapes = CtShapes,
        content = {
            CompositionLocalProvider(
                LocalCtSpacing provides spacing,
                LocalCtEmphasizedType provides CtEmphasizedType,
                LocalCtBrandColors provides brandColors,
                LocalCtGlassColors provides glassColors,
                LocalCtDarkTheme provides darkTheme,
                content = content,
            )
        },
    )
}

/**
 * 按「外观模式」强制浅/深（跟随系统时取 [isSystemInDarkTheme]）：我的 → 外观三选一直接传入。
 *
 * 与 [ClassToolsTheme] 的 `darkTheme` 重载等价，便于页面/预览少写解析代码。
 */
@Composable
fun ClassToolsTheme(
    appearance: AppearanceMode,
    dynamicColor: Boolean = false,
    spacing: CtSpacing = CtSpacing(),
    content: @Composable () -> Unit,
) {
    ClassToolsTheme(
        darkTheme = appearance.resolveDark(isSystemInDarkTheme()),
        dynamicColor = dynamicColor,
        spacing = spacing,
        content = content,
    )
}

/** 从 Compose Context 向上找 Activity（系统栏外观设置用）。 */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}