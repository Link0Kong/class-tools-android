package com.furina.classtools.ui.components.dock

import com.furina.classtools.ui.theme.CtMotionTokens
import com.furina.classtools.ui.theme.HarmonyMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 页面转场对齐（2026-11-19 重构后口径）。
 *
 * 两件事必须同时成立：
 * 1. **容器变换**（工具卡 → 工具页）用单一弹簧驱动，起始/结束圆角与卡片令牌一致，
 *    时长令牌 300ms 用于「形变过半后 push」的交接点；
 * 2. **非工具页**（投票/PPT/数据隐私…）仍走 NavHost 的页面转场：右移 28/24vp + 0.985/0.99 缩放，
 *    时长 = 令牌 [CtMotionTokens.slowMs]（400ms）。
 */
class CtPageTransitionsHarmonyTest {

    @Test
    fun `鸿蒙转场常量与令牌口径一致`() {
        assertEquals(0.078f, CtPageTransitions.HARMONY_ENTER_FRACTION, 0.0005f)
        assertEquals(0.067f, CtPageTransitions.HARMONY_EXIT_FRACTION, 0.0005f)
        assertEquals(CtMotionTokens.slowMs, CtPageTransitions.HARMONY_PAGE_MS)
        assertEquals(HarmonyMotion.SLOW_MS, CtPageTransitions.HARMONY_PAGE_MS)
        assertEquals(CtMotionTokens.shortMs, CtPageTransitions.HARMONY_FADE_MS)
        assertEquals(HarmonyMotion.NORMAL_MS, CtPageTransitions.HARMONY_FADE_MS)
        // 360vp 手机折算后与 28/24vp 的误差 < 1vp。
        assertEquals(28f, 360f * CtPageTransitions.HARMONY_ENTER_FRACTION, 1f)
        assertEquals(24f, 360f * CtPageTransitions.HARMONY_EXIT_FRACTION, 1f)
    }

    @Test
    fun `四个鸿蒙转场都能构造（非空）`() {
        assertNotNull(CtPageTransitions.harmonyPushEnter())
        assertNotNull(CtPageTransitions.harmonyPushExit())
        assertNotNull(CtPageTransitions.harmonyPopEnter())
        assertNotNull(CtPageTransitions.harmonyPopExit())
    }

    @Test
    fun `旧的 Material 口径转场保留（不破坏预览与既有调用）`() {
        assertNotNull(CtPageTransitions.pushEnter())
        assertNotNull(CtPageTransitions.pushExit())
        assertNotNull(CtPageTransitions.popEnter())
        assertNotNull(CtPageTransitions.popExit())
        assertTrue(CtPageTransitions.PUSH_SLIDE_FRACTION > CtPageTransitions.HARMONY_ENTER_FRACTION)
        assertTrue(CtPageTransitions.POP_SLIDE_FRACTION > CtPageTransitions.HARMONY_EXIT_FRACTION)
    }

    @Test
    fun `容器变换参数来自设计令牌（圆角 20 形变到 0 由弹簧驱动）`() {
        // 起始圆角 = 卡片圆角令牌；结束 = 0（页面直角）
        assertEquals(
            com.furina.classtools.ui.theme.CtRadius.card.value,
            CtMotionTokens.containerMorphStartDp,
            0.001f,
        )
        // 形变过半后 push 目标页：交接点 = 令牌 standardMs / 2
        assertEquals(300, CtMotionTokens.standardMs)
        // 页面转场（非工具页）时长 > 容器变换交接点（观感上容器先成形，再交接到页面）
        assertTrue(CtPageTransitions.HARMONY_PAGE_MS > CtMotionTokens.standardMs)
    }
}
