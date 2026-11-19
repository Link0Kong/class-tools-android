package com.furina.classtools.ui.components.dock

import com.furina.classtools.ui.theme.HarmonyMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 页面转场对齐（2026-11-19）：鸿蒙 `Motion.pageEnter/pageExit` 的数值契约。
 *
 * 鸿蒙：进入 = 右移 28vp + 0.985 缩放 + 淡入；退出 = 右移 24vp + 0.99 缩放 + 淡出；
 * 时长 `Motion.SLOW = 380ms`，标准缓动 cubic-bezier(.4,0,.2,1)。
 * 转场函数在 NavHost 的 transitionSpec 里调用（非 @Composable，读不到 LocalDensity），
 * 位移按 360vp 手机折算为屏宽比例（28/360≈0.078、24/360≈0.067）。
 */
class CtPageTransitionsHarmonyTest {

    @Test
    fun `鸿蒙转场常量与 Motion 口径一致`() {
        assertEquals(0.078f, CtPageTransitions.HARMONY_ENTER_FRACTION, 0.0005f)
        assertEquals(0.067f, CtPageTransitions.HARMONY_EXIT_FRACTION, 0.0005f)
        assertEquals(380, CtPageTransitions.HARMONY_PAGE_MS)
        assertEquals(HarmonyMotion.SLOW_MS, CtPageTransitions.HARMONY_PAGE_MS)
        assertEquals(260, CtPageTransitions.HARMONY_FADE_MS)
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
        // 旧口径仍比鸿蒙口径位移更大（10%/12% vs 7.8%/6.7%）。
        assertTrue(CtPageTransitions.PUSH_SLIDE_FRACTION > CtPageTransitions.HARMONY_ENTER_FRACTION)
        assertTrue(CtPageTransitions.POP_SLIDE_FRACTION > CtPageTransitions.HARMONY_EXIT_FRACTION)
    }
}
