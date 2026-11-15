package com.furina.classtools.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 顶部 hero / 渐变卡配色（工作台 hero、全部工具页头、我的身份卡等共用）。
 *
 * 设计口径（2026-11-15 用户反馈「深色模式顶部绿色 hero 观感差」）：
 * - **浅色**：保持原视觉不变——品牌雾青 primary → 灰蓝 secondary 深渐变 + 白字；
 * - **深色**：不再使用 primaryContainer/secondaryContainer 的大面积青绿渐变，改为中性
 *   tonal 深色（surfaceContainerHigh → surfaceContainer）+ onSurface 文字，
 *   **品牌绿只保留小面积 accent**（chip / 头像 / 进度条等 [CtHeroColors.accent]）。
 *
 * 纯数据 + 纯函数，便于预览与单测；不要在页面里再手写渐变颜色。
 */
@Immutable
data class CtHeroColors(
    /** 渐变起点（左上）。 */
    val start: Color,
    /** 渐变终点（右下）。 */
    val end: Color,
    /** 主文字/图标色（正文内容）。 */
    val content: Color,
    /** 小面积品牌强调色（chip / 头像 / 进度条）。 */
    val accent: Color,
    /** 1dp 描边（深浅色都保持极轻）。 */
    val border: Color,
    /** hero 内部浅色分块填充（课程/课表信息块）。 */
    val innerFill: Color,
)

/** 当前主题下的品牌 hero 配色（浅色品牌渐变 / 深色中性 tonal）。 */
@Composable
fun rememberCtHeroColors(): CtHeroColors =
    ctHeroColors(dark = LocalCtDarkTheme.current, scheme = MaterialTheme.colorScheme)

/** 纯函数版本（预览/单测可直接传 [ColorScheme]）。 */
fun ctHeroColors(dark: Boolean, scheme: ColorScheme): CtHeroColors = if (dark) {
    CtHeroColors(
        start = scheme.surfaceContainerHigh,
        end = scheme.surfaceContainer,
        content = scheme.onSurface,
        accent = scheme.primary,
        border = scheme.outlineVariant.copy(alpha = 0.55f),
        innerFill = scheme.onSurface.copy(alpha = 0.06f),
    )
} else {
    CtHeroColors(
        start = scheme.primary,
        end = scheme.secondary,
        content = scheme.onPrimary,
        accent = scheme.onPrimary,
        border = scheme.onPrimary.copy(alpha = 0.14f),
        innerFill = scheme.onPrimary.copy(alpha = 0.10f),
    )
}

/** 「我的」身份 hero 的浅色固定渐变（沿用 v5 深青→灰蓝；深色改中性 tonal，见 [profileHeroColors]）。 */
object ProfileHeroPalette {
    val LightStart = Color(0xFF2F5D54)
    val LightEnd = Color(0xFF44566E)
}

/**
 * 「我的」身份 hero 配色：浅色保持既有固定深青→灰蓝 + 白字；
 * 深色用中性 tonal（品牌绿只出现在胶囊/标识的小面积 accent）。
 */
@Composable
fun rememberProfileHeroColors(): CtHeroColors {
    val dark = LocalCtDarkTheme.current
    return if (dark) ctHeroColors(dark = true, scheme = MaterialTheme.colorScheme) else CtHeroColors(
        start = ProfileHeroPalette.LightStart,
        end = ProfileHeroPalette.LightEnd,
        content = Color.White,
        accent = Color.White,
        border = Color.White.copy(alpha = 0.16f),
        innerFill = Color.White.copy(alpha = 0.14f),
    )
}
