package com.furina.classtools.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 鸿蒙设计令牌对齐锁定（2026-11-19）。
 *
 * 期望值全部来自 `harmony-class-tools`：
 * - `entry/src/main/ets/ui/theme/Tokens.ets`（颜色 / 间距 / 字号 / 圆角 / 骨架色）
 * - `entry/src/main/ets/ui/theme/Motion.ets`（160/260/380 与页面转场 28/24vp + 0.985/0.99）
 * - `entry/src/main/ets/ui/components/PageHeader.ets`（顶栏 56vp、标题 F_HEADING Bold）
 * - `entry/src/main/ets/ui/theme/Breakpoints.ets`（16/24/32 页面边距、内容最大宽 880/1080）
 *
 * 这些断言的价值：令牌被人无意改动时测试直接失败，避免再次出现「说是对齐了其实差 4dp」。
 */
class HarmonyTokensTest {

    @Test
    fun `间距与圆角与鸿蒙 Tokens 同值`() {
        assertEquals(16.dp, HarmonyTokens.PagePadding)   // Tokens.PAGE_PADDING
        assertEquals(24.dp, HarmonyTokens.PagePaddingFold)
        assertEquals(32.dp, HarmonyTokens.PagePaddingTablet)
        assertEquals(12.dp, HarmonyTokens.Gap)           // Tokens.GAP
        assertEquals(16.dp, HarmonyTokens.CardPadding)   // 鸿蒙间隔规范：有明显边界元素 16vp
        assertEquals(12.dp, HarmonyTokens.TilePadding)   // ToolTile padding SPACE_12
        assertEquals(96.dp, HarmonyTokens.TileMinHeight) // ToolTile minHeight
        assertEquals(40.dp, HarmonyTokens.GlyphBox)      // ToolTile 40dp 图标块
        assertEquals(12.dp, HarmonyTokens.RadiusTile)    // ToolTile radius 12
        assertEquals(18.dp, HarmonyTokens.RadiusCard)    // Tokens.RADIUS_CARD
        assertEquals(24.dp, HarmonyTokens.RadiusHero)    // hero borderRadius 24
        assertEquals(14.dp, HarmonyTokens.RadiusInput)   // Tokens.RADIUS_INPUT
        assertEquals(26.dp, HarmonyTokens.RadiusSheet)   // 通知面板实测 26
        assertEquals(8.dp, HarmonyTokens.BreathTop)      // Tokens.BREATH_TOP
        assertEquals(12.dp, HarmonyTokens.BreathTopWide) // Breakpoints.BREATH_TOP_WIDE
        assertEquals(0.5.dp, HarmonyTokens.HairlineWidth) // 鸿蒙 border 0.5
    }

    @Test
    fun `内容宽度与底部占位与鸿蒙 Breakpoints 同值`() {
        assertEquals(880.dp, HarmonyTokens.ContentMaxWidthPhone)  // CONTENT_MAX_PHONE
        assertEquals(1080.dp, HarmonyTokens.ContentMaxWidthWide)  // CONTENT_MAX_WIDE（此前误取 960）
        assertEquals(960.dp, HarmonyTokens.ToolMaxWide)           // TOOL_MAX_WIDE
        assertEquals(96.dp, HarmonyTokens.FloatBarSpace)          // FLOAT_BAR_SPACE
        assertEquals(32.dp, HarmonyTokens.SideBarSpace)           // SIDE_BAR_SPACE
        assertEquals(104.dp, HarmonyTokens.SideNavWidth)          // SIDE_NAV_WIDTH
    }

    @Test
    fun `顶栏与卡片小标题口径与鸿蒙 PageHeader、iconBadge 同值`() {
        assertEquals(56.dp, HarmonyTokens.PageHeaderHeight) // PageHeader headerBar height 56
        assertEquals(30.dp, HarmonyTokens.BadgeBox)         // iconBadge(box=30)
        assertEquals(17.dp, HarmonyTokens.BadgeGlyph)       // iconBadge(glyph=17)
        assertEquals(12.dp, HarmonyTokens.TileTextGap)      // ToolTile margin left GAP
        assertEquals(2.dp, HarmonyTokens.TextGap)           // Tokens.TEXT_GAP
        assertEquals(8.dp, HarmonyTokens.TextGapHorizontal) // TEXT_GAP_H
        assertEquals(16.dp, HarmonyTokens.ElementGapLarge)  // GAP_L
        assertEquals(8.dp, HarmonyTokens.ElementGap)        // GAP
        assertEquals(24.dp, HarmonyTokens.TileGlyph)        // ToolTile fontSize 24
    }

    @Test
    fun `字号层级与鸿蒙 F_TITLE F_HEADING F_BODY F_LABEL F_CAPTION 同值`() {
        assertEquals(26.sp, HarmonyTokens.TitleSize)
        assertEquals(19.sp, HarmonyTokens.HeadingSize)
        assertEquals(15.sp, HarmonyTokens.BodySize)
        assertEquals(13.sp, HarmonyTokens.LabelSize)
        assertEquals(12.sp, HarmonyTokens.CaptionSize)
        assertEquals(26.sp, CtText.title.fontSize)
        assertEquals(19.sp, CtText.heading.fontSize)
        assertEquals(15.sp, CtText.body.fontSize)
        assertEquals(13.sp, CtText.label.fontSize)
        assertEquals(12.sp, CtText.caption.fontSize)
    }

    @Test
    fun `动效时长与转场位移与鸿蒙 Motion 同值`() {
        assertEquals(160, HarmonyMotion.FAST_MS)   // Motion.FAST
        assertEquals(260, HarmonyMotion.NORMAL_MS) // Motion.NORMAL
        assertEquals(380, HarmonyMotion.SLOW_MS)   // Motion.SLOW
        assertEquals(28.dp, HarmonyMotion.PageEnterShift) // Motion.pageEnter translate x 28
        assertEquals(24.dp, HarmonyMotion.PageExitShift)  // Motion.pageExit translate x 24
        assertEquals(0.985f, HarmonyMotion.PageEnterScale)
        assertEquals(0.99f, HarmonyMotion.PageExitScale)
        assertEquals(0.97f, HarmonyMotion.PressScale)  // ToolTile stateStyles.pressed
        assertEquals(0.92f, HarmonyMotion.PressAlpha)
        assertEquals(0.9f, HarmonyMotion.PopInScale)
        assertEquals(10.dp, HarmonyMotion.ListItemRise) // Motion.listItem translate y 10
        assertEquals(0.55f, HarmonyMotion.SkeletonDimAlpha)
        assertEquals(900, HarmonyMotion.SkeletonPulseMs)
    }

    @Test
    fun `骨架色与 hero 阴影色取鸿蒙源码值`() {
        assertEquals(Color(0xFFE6ECEE), HarmonyTokens.SkeletonStrong)
        assertEquals(Color(0xFFE9EEEF), HarmonyTokens.SkeletonBase)
        assertEquals(Color(0xFFEDF1F2), HarmonyTokens.SkeletonSoft)
        assertEquals(Color(0x0F4E9082), HarmonyTokens.HeroShadowColor)
        assertEquals(6.dp, HarmonyTokens.HeroShadowElevation)
        // 深色骨架走中性深底（不与浅色共用同一组灰）。
        assertTrue(HarmonyTokens.DarkSkeletonStrong != HarmonyTokens.SkeletonStrong)
    }

    @Test
    fun `页面语义间距默认值与鸿蒙 PAGE_PADDING 一致`() {
        assertEquals(16.dp, CtSpacing().screenHorizontal)
        assertEquals(12.dp, CtSpacing().cardGap)
        assertEquals(16.dp, CtSpacing().cardPadding)
        assertEquals(16.dp, CtSpacing().sectionGap)
        assertEquals(16.dp, CtSpacing().elementGapLarge)
        assertEquals(8.dp, CtSpacing().elementGap)
        assertEquals(2.dp, CtSpacing().textGapVertical)
        assertEquals(8.dp, CtSpacing().textGapHorizontal)
        assertTrue(CtSpacing().minTouchTarget >= 48.dp)
    }

    @Test
    fun `明暗两套鸿蒙中性色都能取到卡片与骨架色`() {
        val light = harmonyColors(dark = false)
        val dark = harmonyColors(dark = true)
        assertEquals(HarmonyTokens.Card, light.card)
        assertEquals(HarmonyTokens.DarkCard, dark.card)
        assertEquals(HarmonyTokens.Bg, light.background)
        assertEquals(HarmonyTokens.DarkBg, dark.background)
        assertEquals(HarmonyTokens.SkeletonBase, light.skeletonBase)
        assertEquals(HarmonyTokens.DarkSkeletonBase, dark.skeletonBase)
        // hero 纸面色：浅色为鸿蒙实测 #F1F6F2，深色中性映射（不是大面积品牌绿）。
        assertEquals(HarmonyTokens.HeroPaper, light.heroPaper)
        assertTrue(dark.heroPaper != light.heroPaper)
    }
}
