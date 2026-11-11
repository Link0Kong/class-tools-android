package com.furina.classtools.features.ppt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 播放器手势/转场/热点几何（契约 §5/§7/§8.1）。 */
class PptPlayerLogicTest {

    @Test
    fun transitionOf_knownMappings() {
        assertEquals(PptPlayerLogic.TransitionKind.NONE, PptPlayerLogic.transitionOf("none"))
        assertEquals(PptPlayerLogic.TransitionKind.FADE, PptPlayerLogic.transitionOf("fade"))
        assertEquals(PptPlayerLogic.TransitionKind.PUSH, PptPlayerLogic.transitionOf("push"))
        assertEquals(PptPlayerLogic.TransitionKind.WIPE, PptPlayerLogic.transitionOf("wipe"))
        assertEquals(PptPlayerLogic.TransitionKind.ZOOM, PptPlayerLogic.transitionOf("zoom"))
        assertEquals(PptPlayerLogic.TransitionKind.COVER, PptPlayerLogic.transitionOf("cover"))
        // 未知/空 → fade（契约 §5）
        assertEquals(PptPlayerLogic.TransitionKind.FADE, PptPlayerLogic.transitionOf("morph"))
        assertEquals(PptPlayerLogic.TransitionKind.FADE, PptPlayerLogic.transitionOf(null))
        assertEquals(PptPlayerLogic.TransitionKind.FADE, PptPlayerLogic.transitionOf(" FADE "))
    }

    @Test
    fun transitionDuration_usesTrdWithinBounds() {
        assertEquals(450, PptPlayerLogic.transitionDurationMs("fade", 450))
        assertEquals(320, PptPlayerLogic.transitionDurationMs("fade", null))
        assertEquals(320, PptPlayerLogic.transitionDurationMs("fade", 0))
        assertEquals(320, PptPlayerLogic.transitionDurationMs("fade", -10))
        assertEquals(100, PptPlayerLogic.transitionDurationMs("fade", 1)) // 下限 100
        assertEquals(2_000, PptPlayerLogic.transitionDurationMs("push", 9_999)) // 上限 2000
        assertEquals(0, PptPlayerLogic.transitionDurationMs("none", 450)) // none 直切
        assertEquals(150, PptPlayerLogic.STEP_FADE_MS)
    }

    @Test
    fun tapTarget_leftThirdPrev_restNext() {
        assertEquals(PptPlayerLogic.TapTarget.PREV, PptPlayerLogic.tapTarget(0f))
        assertEquals(PptPlayerLogic.TapTarget.PREV, PptPlayerLogic.tapTarget(0.32f))
        assertEquals(PptPlayerLogic.TapTarget.NEXT, PptPlayerLogic.tapTarget(1f / 3f))
        assertEquals(PptPlayerLogic.TapTarget.NEXT, PptPlayerLogic.tapTarget(0.5f))
        assertEquals(PptPlayerLogic.TapTarget.NEXT, PptPlayerLogic.tapTarget(0.99f))
        // 非法值不崩
        assertEquals(PptPlayerLogic.TapTarget.NEXT, PptPlayerLogic.tapTarget(Float.NaN))
    }

    @Test
    fun swipeTarget_threshold() {
        assertEquals(PptPlayerLogic.SwipeTarget.NEXT, PptPlayerLogic.swipeTarget(-120f, 60f))
        assertEquals(PptPlayerLogic.SwipeTarget.PREV, PptPlayerLogic.swipeTarget(120f, 60f))
        assertEquals(PptPlayerLogic.SwipeTarget.NONE, PptPlayerLogic.swipeTarget(-59f, 60f))
        assertEquals(PptPlayerLogic.SwipeTarget.NONE, PptPlayerLogic.swipeTarget(0f, 60f))
        assertEquals(PptPlayerLogic.SwipeTarget.NONE, PptPlayerLogic.swipeTarget(Float.NaN, 60f))
        // 阈值 0/负 → 至少 1
        assertEquals(PptPlayerLogic.SwipeTarget.NEXT, PptPlayerLogic.swipeTarget(-1f, 0f))
        assertEquals(PptPlayerLogic.SwipeTarget.NONE, PptPlayerLogic.swipeTarget(-0.5f, 0f))
    }

    @Test
    fun hotspotRect_clampsIntoPage() {
        val rect = PptPlayerLogic.hotspotRect(0.9f, 0.95f, 0.5f, 0.5f)
        assertEquals(0.5f, rect.x, 0.0001f)
        assertEquals(0.5f, rect.y, 0.0001f)
        assertEquals(0.5f, rect.w, 0.0001f)
        assertEquals(0.5f, rect.h, 0.0001f)

        val tiny = PptPlayerLogic.hotspotRect(-1f, -1f, 0f, 0f)
        assertTrue(tiny.x >= 0f && tiny.y >= 0f)
        assertTrue(tiny.w >= 0.01f && tiny.h >= 0.01f)
    }

    @Test
    fun hotspotRect_missingCoordsCentered() {
        val centered = PptPlayerLogic.hotspotRect(null, null, null, null)
        assertEquals(PptPlayerLogic.CENTER_X, centered.x, 0.0001f)
        assertEquals(PptPlayerLogic.CENTER_Y, centered.y, 0.0001f)
        assertEquals(PptPlayerLogic.CENTER_W, centered.w, 0.0001f)
        assertEquals(PptPlayerLogic.CENTER_H, centered.h, 0.0001f)
        // 单坐标缺失按 0 处理
        val partial = PptPlayerLogic.hotspotRect(null, 0.2f, 0.3f, 0.4f)
        assertEquals(0f, partial.x, 0.0001f)
        assertEquals(0.2f, partial.y, 0.0001f)
    }

    @Test
    fun aspectRatio_fallback() {
        assertEquals(16f / 9f, PptPlayerLogic.aspectRatio(1600, 900), 0.0001f)
        assertEquals(4f / 3f, PptPlayerLogic.aspectRatio(1600, 1200), 0.0001f)
        assertEquals(16f / 9f, PptPlayerLogic.aspectRatio(0, 0), 0.0001f)
        assertEquals(16f / 9f, PptPlayerLogic.aspectRatio(-1, 5), 0.0001f)
    }

    @Test
    fun fitSize_letterbox() {
        // 宽受限
        val (w1, h1) = PptPlayerLogic.fitSize(1000f, 1000f, 16f / 9f)
        assertEquals(1000f, w1, 0.001f)
        assertEquals(562.5f, h1, 0.001f)
        // 高受限
        val (w2, h2) = PptPlayerLogic.fitSize(1000f, 400f, 16f / 9f)
        assertEquals(711.111f, w2, 0.01f)
        assertEquals(400f, h2, 0.001f)
        // 非法可用/比例
        assertEquals(0f to 0f, PptPlayerLogic.fitSize(0f, 100f, 1.5f))
        val (w3, h3) = PptPlayerLogic.fitSize(800f, 600f, Float.NaN)
        assertEquals(800f, w3, 0.001f)
        assertEquals(450f, h3, 0.001f)
    }

    @Test
    fun swipeThreshold_maxOf40pxAnd12Percent() {
        assertEquals(40f, PptPlayerLogic.swipeThresholdPx(0f), 0.001f)
        assertEquals(48f, PptPlayerLogic.swipeThresholdPx(400f), 0.001f) // 400*0.12=48
        assertEquals(120f, PptPlayerLogic.swipeThresholdPx(1000f), 0.001f)
        assertEquals(40f, PptPlayerLogic.swipeThresholdPx(Float.NaN), 0.001f)
        assertEquals(40f, PptPlayerLogic.swipeThresholdPx(-100f), 0.001f)
    }

    @Test
    fun formatMediaDuration() {
        assertEquals("0:12", PptPlayerLogic.formatMediaDuration(12.4))
        assertEquals("0:00", PptPlayerLogic.formatMediaDuration(0.4))
        assertEquals("1:05", PptPlayerLogic.formatMediaDuration(65.0))
        assertEquals("1:00:00", PptPlayerLogic.formatMediaDuration(3600.0))
        assertEquals("", PptPlayerLogic.formatMediaDuration(0.0))
        assertEquals("", PptPlayerLogic.formatMediaDuration(-3.0))
        assertEquals("", PptPlayerLogic.formatMediaDuration(Double.NaN))
    }

    @Test
    fun lowEndDevice_fadeDegrade() {
        assertTrue(PptPlayerLogic.isLowEndDevice(cores = 2, memoryClassMb = 4096))
        assertTrue(PptPlayerLogic.isLowEndDevice(cores = 8, memoryClassMb = 2048))
        assertFalse(PptPlayerLogic.isLowEndDevice(cores = 8, memoryClassMb = 4096))
        // 低端：除 none 外统一 fade 200ms；步内 0ms（DESIGN §4.11）
        assertEquals(200, PptPlayerLogic.effectivePageDurationMs("push", 360, lowEnd = true))
        assertEquals(200, PptPlayerLogic.effectivePageDurationMs("fade", 450, lowEnd = true))
        assertEquals(0, PptPlayerLogic.effectivePageDurationMs("none", 450, lowEnd = true))
        assertEquals(360, PptPlayerLogic.effectivePageDurationMs("push", 360, lowEnd = false))
        assertEquals(0, PptPlayerLogic.effectiveStepDurationMs(lowEnd = true))
        assertEquals(PptPlayerLogic.STEP_FADE_MS, PptPlayerLogic.effectiveStepDurationMs(lowEnd = false))
    }
}
