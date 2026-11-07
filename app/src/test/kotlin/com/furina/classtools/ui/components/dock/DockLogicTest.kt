package com.furina.classtools.ui.components.dock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 浮岛底栏 / Tab pager 联动纯逻辑单测。
 */
class DockLogicTest {

    @Test
    fun continuousPosition_followsPagerOffset() {
        assertEquals(0f, CtDockLogic.continuousPosition(0, 0f, 3), 0.0001f)
        assertEquals(0.42f, CtDockLogic.continuousPosition(0, 0.42f, 3), 0.0001f)
        assertEquals(1.6f, CtDockLogic.continuousPosition(1, 0.6f, 3), 0.0001f)
        // 越界夹住
        assertEquals(2f, CtDockLogic.continuousPosition(2, 0.5f, 3), 0.0001f)
        assertEquals(0f, CtDockLogic.continuousPosition(0, -0.5f, 3), 0.0001f)
        assertEquals(0f, CtDockLogic.continuousPosition(0, 0f, 0), 0.0001f)
    }

    @Test
    fun settlePage_halfThresholdAndClamp() {
        assertEquals(0, CtDockLogic.settlePage(0, 0.3f, 3))
        assertEquals(1, CtDockLogic.settlePage(0, 0.5f, 3))
        assertEquals(0, CtDockLogic.settlePage(1, -0.5f, 3))
        assertEquals(2, CtDockLogic.settlePage(2, 0.9f, 3))
        assertEquals(0, CtDockLogic.settlePage(0, -0.9f, 3))
        assertEquals(0, CtDockLogic.settlePage(0, 0f, 0))
    }

    @Test
    fun followOffset_isRelativeToSelected() {
        assertEquals(0.25f, CtDockLogic.followOffset(0, 0, 0.25f), 0.0001f)
        assertEquals(-0.4f, CtDockLogic.followOffset(1, 1, -0.4f), 0.0001f)
    }

    @Test
    fun pillScale_stretchesMaxEightPercent() {
        val (sx0, sy0) = CtDockLogic.pillScale(0f)
        assertEquals(1f, sx0, 0.0001f)
        assertEquals(1f, sy0, 0.0001f)
        val (sx, sy) = CtDockLogic.pillScale(0.5f)
        assertEquals(1.04f, sx, 0.0001f)
        assertEquals(0.98f, sy, 0.0001f)
        val (sxMax, _) = CtDockLogic.pillScale(2f)
        assertEquals(1.08f, sxMax, 0.0001f)
    }

    @Test
    fun pressScale_states() {
        assertEquals(1f, CtDockLogic.pressScale(selected = true, pressed = false), 0.0001f)
        assertEquals(0.96f, CtDockLogic.pressScale(selected = true, pressed = true), 0.0001f)
        assertEquals(0.90f, CtDockLogic.pressScale(selected = false, pressed = true), 0.0001f)
        assertEquals(0.94f, CtDockLogic.pressScale(selected = false, pressed = false, dragging = true), 0.0001f)
    }

    @Test
    fun indicatorCenter_matchesItemCenter() {
        assertEquals(50f, CtDockLogic.indicatorCenterX(0, 100f), 0.0001f)
        assertEquals(150f, CtDockLogic.indicatorCenterX(1, 100f), 0.0001f)
        assertEquals(250f, CtDockLogic.indicatorCenterX(2, 100f), 0.0001f)
    }

    @Test
    fun pageTransform_centerAndNeighbors() {
        val center = CtDockLogic.pageTransform(index = 1, currentPage = 1, offsetFraction = 0f)
        assertEquals(0f, center.translationXFraction, 0.0001f)
        assertEquals(1f, center.alpha, 0.0001f)
        assertEquals(1f, center.scale, 0.0001f)

        val prev = CtDockLogic.pageTransform(index = 0, currentPage = 1, offsetFraction = 0f)
        assertEquals(-CtDockLogic.PARALLAX_FRACTION, prev.translationXFraction, 0.0001f)
        assertEquals(CtDockLogic.MIN_ALPHA, prev.alpha, 0.0001f)
        assertEquals(CtDockLogic.MIN_SCALE, prev.scale, 0.0001f)
    }

    @Test
    fun pageTransform_partialSwipeInterpolates() {
        val half = CtDockLogic.pageTransform(index = 0, currentPage = 0, offsetFraction = 0.5f)
        assertTrue(half.translationXFraction < 0f) // 向下一页滑动时当前页左移
        assertTrue(half.alpha < 1f && half.alpha > CtDockLogic.MIN_ALPHA)
        assertTrue(half.scale < 1f && half.scale > CtDockLogic.MIN_SCALE)
    }

    @Test
    fun tabAnnouncement_mentionsPositionAndState() {
        assertEquals(
            "工作台，第 1 个 Tab，共 3 个，已选中",
            CtDockLogic.tabAnnouncement("工作台", 0, 3, selected = true),
        )
        assertEquals(
            "工具，第 2 个 Tab，共 3 个，未选中",
            CtDockLogic.tabAnnouncement("工具", 1, 3, selected = false),
        )
    }
}

