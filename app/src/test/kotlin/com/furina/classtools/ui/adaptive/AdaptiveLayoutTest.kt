package com.furina.classtools.ui.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2 自适应框架单测：断点/列数/限宽/双栏。
 */
class AdaptiveLayoutTest {

    @Test
    fun widthClass_boundaries() {
        assertEquals(CtWidthClass.COMPACT, CtAdaptive.widthClassOf(360))
        assertEquals(CtWidthClass.COMPACT, CtAdaptive.widthClassOf(599))
        assertEquals(CtWidthClass.MEDIUM, CtAdaptive.widthClassOf(600))
        assertEquals(CtWidthClass.MEDIUM, CtAdaptive.widthClassOf(839))
        assertEquals(CtWidthClass.EXPANDED, CtAdaptive.widthClassOf(840))
        assertEquals(CtWidthClass.EXPANDED, CtAdaptive.widthClassOf(1440))
    }

    @Test
    fun gridColumns_byWidthClass() {
        assertEquals(2, CtAdaptive.gridColumns(412))
        assertEquals(3, CtAdaptive.gridColumns(700))
        assertEquals(4, CtAdaptive.gridColumns(1000))
    }

    @Test
    fun horizontalPadding_byWidthClass() {
        assertEquals(16, CtAdaptive.horizontalPaddingDp(412))
        assertEquals(24, CtAdaptive.horizontalPaddingDp(700))
        assertEquals(32, CtAdaptive.horizontalPaddingDp(1000))
    }

    @Test
    fun contentMaxWidth_compactAndMediumUseCompactLimit() {
        assertEquals(840, CtAdaptive.contentMaxWidthDp(412))
        assertEquals(840, CtAdaptive.contentMaxWidthDp(700))
        assertEquals(1200, CtAdaptive.contentMaxWidthDp(1000))
    }

    @Test
    fun twoPane_onlyExpanded() {
        assertFalse(CtAdaptive.twoPane(599))
        assertFalse(CtAdaptive.twoPane(839))
        assertTrue(CtAdaptive.twoPane(840))
        assertTrue(CtAdaptive.twoPane(1200))
    }

    @Test
    fun effectiveContentWidth_neverExceedsLimit() {
        // 手机：窗口 - 2*16
        assertEquals(380, CtAdaptive.effectiveContentWidthDp(412))
        // 平板：min(1000-64, 1200) = 936
        assertEquals(936, CtAdaptive.effectiveContentWidthDp(1000))
        // 超宽屏：被 1200 限宽
        assertEquals(1200, CtAdaptive.effectiveContentWidthDp(2000))
    }

    @Test
    fun specFor_isConsistent() {
        val phone = CtAdaptive.specFor(412)
        assertEquals(CtWidthClass.COMPACT, phone.widthClass)
        assertFalse(phone.twoPane)
        assertEquals(2, phone.gridColumns)

        val tablet = CtAdaptive.specFor(1000)
        assertEquals(CtWidthClass.EXPANDED, tablet.widthClass)
        assertTrue(tablet.twoPane)
        assertEquals(4, tablet.gridColumns)
        assertEquals(32f, tablet.horizontalPadding.value, 0.01f)
    }

    @Test
    fun specFor_mainPaneWeightGrowsOnLargeScreens() {
        assertEquals(0.55f, CtAdaptive.specFor(900).mainPaneWeight, 0.001f)
        assertEquals(0.58f, CtAdaptive.specFor(1280).mainPaneWeight, 0.001f)
        assertTrue(CtAdaptive.supportsWorkbenchTwoPane(CtAdaptive.specFor(900)))
        assertFalse(CtAdaptive.supportsWorkbenchTwoPane(CtAdaptive.specFor(700)))
    }
}
