package com.furina.classtools.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.furina.classtools.ui.theme.LocalCtLight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
//   Android 12+ 默认使用系统生成的壁纸配色。
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
internal val CtLightColorScheme: ColorScheme = lightColorScheme(
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
internal val CtDarkColorScheme: ColorScheme = darkColorScheme(
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

/** The same surface and content roles are used by Compose and the desktop widgets. */
internal fun appColorScheme(context: Context, dark: Boolean, monet: Boolean): ColorScheme =
    if (monet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) CtDarkColorScheme else CtLightColorScheme

private fun ColorScheme.semanticColors(dark: Boolean) = ctColorOf(dark).copy(
    background = surface, surface = surface, containerLow = surfaceContainerLow,
    container = surfaceContainer, containerHigh = surfaceContainerHigh, containerHighest = surfaceContainerHighest,
    bar = surfaceContainer, textPrimary = onSurface, textSecondary = onSurfaceVariant,
    textTertiary = onSurfaceVariant, divider = outlineVariant, outline = outline,
    primary = primary, onPrimary = onPrimary, primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer, error = error, onError = onError, errorContainer = errorContainer,
    stateLayer = onSurface, skeletonStrong = surfaceContainerHighest, skeletonBase = surfaceContainer,
    skeletonSoft = surfaceContainerLow, heroPaper = surfaceContainerLow, heroTitle = onSurface,
    heroBorder = outlineVariant, heroPill = primaryContainer, heroSub = onSurfaceVariant,
    heroAccent = primary, heroLink = primary, heroDivider = outlineVariant,
)

private fun ColorScheme.toolAccents(dark: Boolean): CtAccentColors {
    val first = CtAccentRef(primary, primary)
    val second = CtAccentRef(secondary, secondary)
    val third = CtAccentRef(tertiary, tertiary)
    return ctAccentsOf(dark).copy(teal = first, blue = second, sand = third, leaf = first,
        plum = third, clay = third, amber = third, water = second)
}

/**
 * 全局主题。
 *
 * @param darkTheme 是否深色（默认跟随系统；调用方按「外观模式」用 [AppearanceMode.resolveDark] 解析）。
 * @param dynamicColor 是否跟随系统壁纸取色（Material You）。设备支持时默认使用系统完整配色。
 * @param spacing 语义间距（默认 [CtSpacing]；大屏可由调用方覆盖）。
 * @param immersiveLight 沉浸光感设置（强 / 均衡 / 弱 / 关闭；默认 [CtLightSetting.Default]）。
 * @param lightCapabilityOverride 光感能力档覆盖（预览/测试注入；null = 按 Build.VERSION.SDK_INT + 硬件加速推导）。
 */
@Composable
fun ClassToolsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    spacing: CtSpacing = CtSpacing(),
    immersiveLight: CtLightSetting = CtLightSetting.Default,
    lightCapabilityOverride: CtLightCapability? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val monet = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = remember(context, configuration, darkTheme, monet) { appColorScheme(context, darkTheme, monet) }
    val semantic = remember(colorScheme, darkTheme, monet) { if (monet) colorScheme.semanticColors(darkTheme) else ctColorOf(darkTheme) }
    val accents = remember(colorScheme, darkTheme, monet) { if (monet) colorScheme.toolAccents(darkTheme) else ctAccentsOf(darkTheme) }

    // 系统栏图标随主题切换：深色模式状态栏/导航栏图标用浅色，避免「黑字压暗底」不可读。
    LaunchedEffect(darkTheme) {
        val activity = context.findActivity() ?: return@LaunchedEffect
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    // 沉浸光感运行期上下文：设备能力档（API31+ 才有 RenderEffect 模糊）+ 安全区高度（顶部材质向上延展）。
    val view = LocalView.current
    val density = LocalDensity.current
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val navigationBarHeight = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val hardwareAccelerated = remember(view) { runCatching { view.isHardwareAccelerated }.getOrDefault(true) }
    val sdkInt = remember { Build.VERSION.SDK_INT }
    val capability = lightCapabilityOverride ?: ctLightCapabilityOf(sdkInt, hardwareAccelerated, immersiveLight)
    val lightContext = remember(immersiveLight, capability, statusBarHeight, navigationBarHeight) {
        CtLightContext(
            setting = immersiveLight,
            capability = capability,
            statusBarHeight = statusBarHeight,
            navigationBarHeight = navigationBarHeight,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CtTypography,
        shapes = CtShapes,
        content = {
            CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides colorScheme.onSurface,
                LocalCtSpacing provides spacing,
                LocalCtEmphasizedType provides CtEmphasizedType,
                LocalCtColors provides semantic,
                LocalCtAccents provides accents,
                LocalCtBrandColors provides if (darkTheme) DarkCtBrandColors else LightCtBrandColors,
                LocalCtDarkTheme provides darkTheme,
                LocalCtLightSetting provides immersiveLight,
                LocalCtLightCapability provides capability,
                LocalCtLight provides lightContext,
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
    dynamicColor: Boolean = true,
    spacing: CtSpacing = CtSpacing(),
    immersiveLight: CtLightSetting = CtLightSetting.Default,
    content: @Composable () -> Unit,
) {
    ClassToolsTheme(
        darkTheme = appearance.resolveDark(isSystemInDarkTheme()),
        dynamicColor = dynamicColor,
        spacing = spacing,
        immersiveLight = immersiveLight,
        content = content,
    )
}

/** 从 Compose Context 向上找 Activity（系统栏外观设置用）。 */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
