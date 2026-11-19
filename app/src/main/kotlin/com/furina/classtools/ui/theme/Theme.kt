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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// =============================================================================
// 主题入口（唯一）
//
// 用法：ClassToolsTheme(darkTheme = …, dynamicColor = false) { … }
//
// 它一次性注入：
//   MaterialTheme.colorScheme  ← [Color.kt]（数值来自 [CtPalette]）
//   MaterialTheme.typography   ← [CtTypeScale]/[CtType]
//   MaterialTheme.shapes       ← [CtShapes]
//   LocalCtColors（语义色）/ LocalCtBrandColors（品牌语义）/ LocalCtAccents（强调色）
//   LocalCtDarkTheme / LocalCtSpacing / LocalCtEmphasizedType
//
// 取色策略：
//   L1 品牌固定方案（默认）：跨端一致、QA 可复现；
//   L2 品牌混合动态（可选）：surface/中性跟随壁纸，品牌主色保留。
// =============================================================================

/** 语义间距（CompositionLocal；默认值 = [CtSpace]，大屏可由调用方覆盖）。 */
data class CtSpacing(
    val screenHorizontal: Dp = CtSpace.pagePhone,
    val sectionGap: Dp = CtSpace.sectionGap,
    val cardGap: Dp = CtSpace.cardGap,
    val minTouchTarget: Dp = CtSize.minTouch,
    val cardPadding: Dp = CtSpace.cardPadding,
    val elementGapLarge: Dp = CtSpace.lg,
    val elementGap: Dp = CtSpace.sm,
    val textGapVertical: Dp = CtSpace.xxs,
    val textGapHorizontal: Dp = CtSpace.sm,
    val pageHorizontalMedium: Dp = CtSpace.pageFold,
    val pageHorizontalExpanded: Dp = CtSpace.pageTablet,
    val contentMaxWidth: Dp = CtSize.toolMaxWidth,
)

val LocalCtSpacing = staticCompositionLocalOf { CtSpacing() }

/** 当前是否深色主题。 */
val LocalCtDarkTheme = staticCompositionLocalOf { false }

// ---------------------------------------------------------------------------
// 浅色 ColorScheme（48 角色；数值 = Color.kt ← CtPalette）
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
// 深色 ColorScheme（48 角色）
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

/** M3 标准（非 Expressive）取色入口，供测试/预览对照。 */
val CtThemeColorSchemes: Pair<ColorScheme, ColorScheme> = CtLightColorScheme to CtDarkColorScheme

/**
 * L2 混合动态取色：surface/neutral 跟随系统壁纸，品牌 primary/secondary/tertiary
 * （含容器与 fixed 角色）保留品牌方案，避免「跟随壁纸后认不出班级工具」。
 */
private fun brandedDynamicScheme(context: Context, dark: Boolean): ColorScheme {
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
 * @param darkTheme 是否深色（默认跟随系统；调用方按「外观模式」用 [AppearanceMode.resolveDark] 解析）。
 * @param dynamicColor 是否跟随系统壁纸取色（Material You）。默认关闭以保持品牌识别。
 * @param spacing 语义间距（默认 [CtSpacing]；大屏可由调用方覆盖）。
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

    // 系统栏图标随主题切换：深色模式状态栏/导航栏图标用浅色，避免「黑字压暗底」不可读。
    LaunchedEffect(darkTheme) {
        val activity = context.findActivity() ?: return@LaunchedEffect
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CtTypography,
        shapes = CtShapes,
        content = {
            CompositionLocalProvider(
                LocalCtSpacing provides spacing,
                LocalCtEmphasizedType provides CtEmphasizedType,
                LocalCtColors provides ctColorOf(darkTheme),
                LocalCtAccents provides ctAccentsOf(darkTheme),
                LocalCtBrandColors provides if (darkTheme) DarkCtBrandColors else LightCtBrandColors,
                LocalCtDarkTheme provides darkTheme,
                content = content,
            )
        },
    )
}

/**
 * 按「外观模式」强制浅/深（跟随系统时取 [isSystemInDarkTheme]）：我的 → 外观三选一直接传入。
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
