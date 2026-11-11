package com.furina.classtools.data.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 诊断进展轮询状态机单测（契约 v2.1 §A-7：15s → 10s；仅页面可见；进入/恢复前台立即拉）。
 */
class DiagPollingTest {

    @Test
    fun defaultIntervalIsTenSeconds() {
        assertEquals(10_000L, DiagPollConfig().intervalMs)
        assertEquals(10_000L, DiagPollConfig.INTERVAL_MS)
    }

    @Test
    fun onVisible_fetchesImmediately() {
        assertEquals(DiagPollTick.FetchNow, DiagPolling.onVisible())
    }

    @Test
    fun afterTick_visibleWaitsTenSeconds() {
        val tick = DiagPolling.afterTick(visible = true)
        assertTrue(tick is DiagPollTick.Wait)
        assertEquals(10_000L, (tick as DiagPollTick.Wait).delayMs)
    }

    @Test
    fun afterTick_hiddenStopsPolling() {
        assertEquals(DiagPollTick.Stopped, DiagPolling.afterTick(visible = false))
    }

    @Test
    fun customInterval_usedByTick() {
        val tick = DiagPolling.afterTick(visible = true, config = DiagPollConfig(intervalMs = 2_500L))
        assertEquals(2_500L, (tick as DiagPollTick.Wait).delayMs)
    }

    @Test
    fun invalidInterval_rejected() {
        val thrown = try {
            DiagPollConfig(intervalMs = 0L)
            false
        } catch (_: IllegalArgumentException) {
            true
        }
        assertTrue(thrown)
    }
}
