package com.furina.classtools.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 鸿蒙「文本排版 / 间隔参数」令牌单测（2026-11-19 排版重构）。
 *
 * 锁定官方口径：
 * - **8vp 网格**：所有间距令牌是 4 的倍数（半格），语义间距（控件/卡片/区块）是 8 的倍数；
 * - **屏幕边缘 20vp**（手机）/ 24（折叠）/ 32（平板）；
 * - 字号层级 **28 / 24 / 20 / 16 / 14 / 12**（正文 14–16、说明 12、标题 20–28）；
 * - 行高 **1.4–1.6 倍**；
 * - 字重只用 Regular(400) / Medium(500) / Bold(700)。
 */
class CtSpacingTokensTest {

    private fun dpOf(v: androidx.compose.ui.unit.Dp): Float = v.value

    @Test
    fun `spacing tokens sit on the 4vp half grid`() {
        val tokens = listOf(
            "xxs" to CtSpace.xxs,
            "xs" to CtSpace.xs,
            "sm" to CtSpace.sm,
            "md" to CtSpace.md,
            "lg" to CtSpace.lg,
            "xl" to CtSpace.xl,
            "xxl" to CtSpace.xxl,
            "xxxl" to CtSpace.xxxl,
            "huge" to CtSpace.huge,
            "cardPadding" to CtSpace.cardPadding,
            "rowPaddingH" to CtSpace.rowPaddingH,
            "rowPaddingV" to CtSpace.rowPaddingV,
            "tilePadding" to CtSpace.tilePadding,
            "cardGap" to CtSpace.cardGap,
            "sectionGap" to CtSpace.sectionGap,
            "groupGap" to CtSpace.groupGap,
            "controlGap" to CtSpace.controlGap,
            "controlGapMedium" to CtSpace.controlGapMedium,
            "controlGapLarge" to CtSpace.controlGapLarge,
        )
        tokens.forEach { (name, value) ->
            val v = dpOf(value)
            assertEquals("$name=$v 必须是 4 的倍数", 0f, v % 4f, 0.0001f)
        }
    }

    @Test
    fun `semantic gaps are multiples of 8`() {
        listOf(
            "cardGap" to CtSpace.cardGap,
            "sectionGap" to CtSpace.sectionGap,
            "groupGap" to CtSpace.groupGap,
            "controlGap" to CtSpace.controlGap,
            "controlGapLarge" to CtSpace.controlGapLarge,
            "cardPadding" to CtSpace.cardPadding,
            "rowPaddingH" to CtSpace.rowPaddingH,
            "rowPaddingV" to CtSpace.rowPaddingV,
        ).forEach { (name, value) ->
            val v = dpOf(value)
            assertEquals("$name=$v 必须是 8 的倍数", 0f, v % 8f, 0.0001f)
        }
    }

    @Test
    fun `page margins follow huawei screen edge spec`() {
        assertEquals(20f, dpOf(CtSpace.pagePhone), 0.0001f)
        assertEquals(24f, dpOf(CtSpace.pageFold), 0.0001f)
        assertEquals(32f, dpOf(CtSpace.pageTablet), 0.0001f)
    }

    @Test
    fun `card padding is 16 and section gap is 24`() {
        assertEquals(16f, dpOf(CtSpace.cardPadding), 0.0001f)
        assertEquals(24f, dpOf(CtSpace.sectionGap), 0.0001f)
    }

    @Test
    fun `grid unit is 8`() {
        assertEquals(8f, dpOf(CtSpace.gridUnit), 0.0001f)
    }

    /**
     * 工具页控件间距（2026-11-20 用户反馈「按钮/选项间隔过近」）。
     *
     * 鸿蒙「间隔参数」对控件间距给到 8 / 12 / 16 三档；相邻可点控件的下限就是 8vp，
     * 工具页里的按钮/选项一律取 [CtSpace.controlGap]，不允许再出现 4/6vp 的控件间空白。
     */
    @Test
    fun `control gap floor is 8vp and tiers are ascending`() {
        assertEquals("控件间距下限必须是 8vp", 8f, dpOf(CtSpace.controlGap), 0.0001f)
        assertEquals(12f, dpOf(CtSpace.controlGapMedium), 0.0001f)
        assertEquals(16f, dpOf(CtSpace.controlGapLarge), 0.0001f)
        assertTrue(
            "控件间距三档递增：${CtSpace.controlGap} < ${CtSpace.controlGapMedium} < ${CtSpace.controlGapLarge}",
            dpOf(CtSpace.controlGap) < dpOf(CtSpace.controlGapMedium) &&
                dpOf(CtSpace.controlGapMedium) < dpOf(CtSpace.controlGapLarge),
        )
        // 控件间距不得比「卡片间距」还宽松，否则分组层级会塌陷。
        assertTrue(
            "controlGap=${CtSpace.controlGap} 不应大于 cardGap=${CtSpace.cardGap}",
            dpOf(CtSpace.controlGap) <= dpOf(CtSpace.cardGap),
        )
    }

    @Test
    fun `type scale uses the 28 24 20 16 14 12 ladder`() {
        assertEquals(28f, CtType.displayTitleSize.value, 0.0001f)
        assertEquals(24f, CtType.titleSize.value, 0.0001f)
        assertEquals(20f, CtType.headlineSize.value, 0.0001f)
        assertEquals(16f, CtType.title16Size.value, 0.0001f)
        assertEquals(14f, CtType.bodySize.value, 0.0001f)
        assertEquals(14f, CtType.labelSize.value, 0.0001f)
        assertEquals(12f, CtType.captionSize.value, 0.0001f)
    }

    @Test
    fun `body range is 14 to 16 and caption is 12`() {
        assertTrue(CtType.bodySize.value in 14f..16f)
        assertTrue(CtType.labelSize.value in 14f..16f)
        assertEquals(12f, CtType.captionSize.value, 0.0001f)
    }

    @Test
    fun `headline range is 20 to 28`() {
        listOf(CtType.headlineSize, CtType.titleSize, CtType.displayTitleSize).forEach {
            assertTrue("${it.value} in 20..28", it.value in 20f..28f)
        }
    }

    @Test
    fun `line height ratios are between 1_4 and 1_6`() {
        val pairs = listOf(
            "display" to (CtType.displayTitleSize.value to CtType.displayTitleLine),
            "title" to (CtType.titleSize.value to CtType.titleLine),
            "headline" to (CtType.headlineSize.value to CtType.headlineLine),
            "title16" to (CtType.title16Size.value to CtType.title16Line),
            "body" to (CtType.bodySize.value to CtType.bodyLine),
            "label" to (CtType.labelSize.value to CtType.labelLine),
            "caption" to (CtType.captionSize.value to CtType.captionLine),
        )
        pairs.forEach { (name, pair) ->
            val ratio = pair.second / pair.first
            assertTrue("$name ratio=$ratio 必须在 1.4–1.6", ratio in 1.4f..1.6f)
        }
    }

    @Test
    fun `declared line ratios match the token table`() {
        assertEquals(1.43f, CtType.lineRatioDisplay, 0.001f)
        assertEquals(1.50f, CtType.lineRatioTitle, 0.001f)
        assertEquals(1.40f, CtType.lineRatioHeadline, 0.001f)
        assertEquals(1.57f, CtType.lineRatioBody, 0.001f)
        assertEquals(1.50f, CtType.lineRatioCaption, 0.001f)
    }

    @Test
    fun `font weights are regular medium bold only`() {
        assertEquals(400, CtType.weightNormal)
        assertEquals(500, CtType.weightMedium)
        assertEquals(700, CtType.weightBold)
        // 旧 600 档统一并入 500（官方只用三档字重）
        assertEquals(500, CtType.weightSemiBold)
    }

    @Test
    fun `type scale line heights match tokens`() {
        assertEquals(CtType.displayTitleLine.toFloat(), CtTypeScale.display.lineHeight.value, 0.0001f)
        assertEquals(CtType.titleLine.toFloat(), CtTypeScale.title.lineHeight.value, 0.0001f)
        assertEquals(CtType.headlineLine.toFloat(), CtTypeScale.headline.lineHeight.value, 0.0001f)
        assertEquals(CtType.bodyLine.toFloat(), CtTypeScale.body.lineHeight.value, 0.0001f)
        assertEquals(CtType.labelLine.toFloat(), CtTypeScale.label.lineHeight.value, 0.0001f)
        assertEquals(CtType.captionLine.toFloat(), CtTypeScale.caption.lineHeight.value, 0.0001f)
    }

    @Test
    fun `type scale sizes match tokens`() {
        assertEquals(CtType.displayTitleSize, CtTypeScale.display.fontSize)
        assertEquals(CtType.titleSize, CtTypeScale.title.fontSize)
        assertEquals(CtType.headlineSize, CtTypeScale.headline.fontSize)
        assertEquals(CtType.title16Size, CtTypeScale.title16.fontSize)
        assertEquals(CtType.bodySize, CtTypeScale.body.fontSize)
        assertEquals(CtType.labelSize, CtTypeScale.label.fontSize)
        assertEquals(CtType.captionSize, CtTypeScale.caption.fontSize)
    }
}
