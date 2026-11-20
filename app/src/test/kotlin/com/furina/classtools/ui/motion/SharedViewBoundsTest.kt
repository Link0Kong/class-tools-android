package com.furina.classtools.ui.motion

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 金标 SharedView bounds 规则（对齐官方 README §Bounds 坐标系/可见性/返回落点）。
 *
 * 这些用例锁的是「按设备声明动态选坐标系」和「半遮卡片不启动 Kit」两条关键降级边界，
 * 避免以后有人为修某一台机型改成按品牌硬编码或强行拉伸 bounds。
 */
class SharedViewBoundsTest {

    @Test
    fun `null and legacy rect metadata default to screen`() {
        assertEquals(SharedViewBounds.SCREEN, SharedViewBounds.parseCoordinateToken(null))
        assertEquals(SharedViewBounds.SCREEN, SharedViewBounds.parseCoordinateToken(""))
        assertEquals(SharedViewBounds.SCREEN, SharedViewBounds.parseCoordinateToken("rect"))
        assertEquals(SharedViewBounds.SCREEN, SharedViewBounds.parseCoordinateToken("rect|SCREEN|View bounds"))
    }

    @Test
    fun `window token in second segment wins`() {
        assertEquals(
            SharedViewBounds.WINDOW,
            SharedViewBounds.parseCoordinateToken("rect|WINDOW|View bounds (window)"),
        )
        assertEquals(
            SharedViewBounds.WINDOW,
            SharedViewBounds.parseCoordinateToken("rect|window|lowercase"),
        )
    }

    @Test
    fun `second segment is authoritative over description text`() {
        // 描述里出现 WINDOW 不能覆盖第二段的 SCREEN。
        assertEquals(
            SharedViewBounds.SCREEN,
            SharedViewBounds.parseCoordinateToken("rect|SCREEN|legacy WINDOW description"),
        )
        assertEquals(
            SharedViewBounds.WINDOW,
            SharedViewBounds.parseCoordinateToken("rect|WINDOW|screen text in description"),
        )
    }

    @Test
    fun `legacy fallback detects window keyword`() {
        assertEquals(
            SharedViewBounds.WINDOW,
            SharedViewBounds.parseCoordinateToken("rect|unknown|window coordinates"),
        )
        assertEquals(
            SharedViewBounds.SCREEN,
            SharedViewBounds.parseCoordinateToken("rect|unknown|screen coordinates"),
        )
    }

    @Test
    fun `kit rect offsets root bounds and rounds outward`() {
        val rect = SharedViewBounds.toKitBounds(
            bounds = Rect(10.4f, 20.6f, 110.2f, 220.9f),
            originX = 3,
            originY = 7,
        )
        assertEquals(13, rect.left)
        assertEquals(27, rect.top)
        assertEquals(114, rect.right)
        assertEquals(228, rect.bottom)
    }

    @Test
    fun `fully visible accepts edge tolerance but rejects clipped cards`() {
        val card = Rect(0f, 100f, 300f, 260f)
        assertTrue(SharedViewBounds.isFullyVisible(card, 1080, 2400))
        assertTrue(SharedViewBounds.isFullyVisible(Rect(-0.5f, -0.5f, 300f, 260f), 1080, 2400))
        assertFalse(SharedViewBounds.isFullyVisible(Rect(-30f, 100f, 300f, 260f), 1080, 2400))
        assertFalse(SharedViewBounds.isFullyVisible(Rect(0f, 2350f, 300f, 2500f), 1080, 2400))
        assertFalse(SharedViewBounds.isFullyVisible(Rect(10f, 10f, 10f, 10f), 1080, 2400))
    }

    @Test
    fun `return bounds reusable only when source alive and screen unchanged`() {
        assertTrue(SharedViewBounds.canReuseReturnBounds(true, 1080, 2400, 1080, 2400))
        assertFalse(SharedViewBounds.canReuseReturnBounds(false, 1080, 2400, 1080, 2400))
        assertFalse(SharedViewBounds.canReuseReturnBounds(true, 2400, 1080, 1080, 2400))
    }
}
