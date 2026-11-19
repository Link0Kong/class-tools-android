package com.furina.classtools.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// =============================================================================
// hero / 身份卡配色（工作台 hero、全部工具页头、我的身份卡共用）
//
// 2026-11-19 统一视觉语言重构：hero 由「品牌渐变大色块」改为**鸿蒙纸面卡**（浅色 #F1F6F2）
// 与**深色中性卡**（#1C2427）——M3 Expressive 的「形体 + 层级」比渐变更能表达层次，
// 同时与 HarmonyOS「轻拟物光影」一致：1 发丝边 + 极浅投影，不使用渐变/噪点/暗角。
//
// 本文件只提供语义别名；颜色本身全部来自 [CtColor]（[CtPalette] 的槽位）。
// =============================================================================

/** hero 语义色（工作台 / 身份卡 / 页头共用）。 */
data class CtHeroColors(
    /** 卡片底（浅色纸面 / 深色中性）。 */
    val container: Color,
    /** 发丝描边。 */
    val border: Color,
    /** 主文字。 */
    val content: Color,
    /** 次级文字。 */
    val sub: Color,
    /** 内部分割线。 */
    val divider: Color,
    /** 小面积品牌强调（链接/徽标/「今日」胶囊文字）。 */
    val accent: Color,
    /** 链接/文字操作色。 */
    val link: Color,
    /** 「今日」胶囊底。 */
    val pill: Color,
    /** 内部浅色分块填充（摘要/课程块）。 */
    val innerFill: Color,
)

/** 当前主题下的 hero 配色。 */
@Composable
fun rememberCtHeroColors(): CtHeroColors = rememberCtHeroColorsFor(LocalCtDarkTheme.current)

@Composable
private fun rememberCtHeroColorsFor(dark: Boolean): CtHeroColors =
    androidx.compose.runtime.remember(dark) { ctHeroColors(ctColorOf(dark)) }

/** 纯函数版本（预览/单测可直接传 [CtColor]；保留旧签名重载见下）。 */
fun ctHeroColors(dark: Boolean, scheme: ColorScheme): CtHeroColors =
    ctHeroColors(ctColorOf(dark))

/** 纯函数版本：由语义色直接推导 hero 配色。 */
fun ctHeroColors(color: CtColor): CtHeroColors = CtHeroColors(
    container = color.heroPaper,
    border = color.heroBorder,
    content = color.heroTitle,
    sub = color.heroSub,
    divider = color.heroDivider,
    accent = color.heroAccent,
    link = color.heroLink,
    pill = color.heroPill,
    innerFill = color.heroSub.copy(alpha = if (color.dark) 0.10f else 0.06f),
)

/** 「我的」身份卡：与工作台 hero 同一套纸面语言（保留旧入口名）。 */
@Composable
fun rememberProfileHeroColors(): CtHeroColors = rememberCtHeroColors()

/** 旧入口：身份卡浅色渐变起点（已废弃，保留常量避免预览引用失败；实际渲染不用渐变）。 */
object ProfileHeroPalette {
    val LightStart: Color = CtPalette.HeroPaperAccent
    val LightEnd: Color = CtPalette.HeroPaperLink
}

/** 便捷：hero 的 Compose 颜色引用（MaterialTheme.colorScheme 取不到时用本对象）。 */
@Composable
fun heroColorOf(): Color = MaterialTheme.colorScheme.primary
