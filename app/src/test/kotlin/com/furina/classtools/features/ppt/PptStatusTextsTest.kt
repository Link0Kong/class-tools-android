package com.furina.classtools.features.ppt

import com.furina.classtools.data.ppt.PptDeck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 列表状态文案（契约 §4.4 展示规则 + §8.1 短文案，三端一致）。 */
class PptStatusTextsTest {

    private fun deck(
        id: String = "d1",
        ownerSid: String = "2024000001",
        status: String = PptDeck.STATUS_READY,
        createdAt: Long = 1_000L,
    ) = PptDeck(id = id, ownerSid = ownerSid, status = status, createdAt = createdAt)

    @Test
    fun kindOf_knownAndUnknown() {
        assertEquals(PptStatusTexts.Kind.UPLOADING, PptStatusTexts.kindOf("uploading"))
        assertEquals(PptStatusTexts.Kind.QUEUED, PptStatusTexts.kindOf(" QUEUED "))
        assertEquals(PptStatusTexts.Kind.PROCESSING, PptStatusTexts.kindOf("processing"))
        assertEquals(PptStatusTexts.Kind.READY, PptStatusTexts.kindOf("ready"))
        assertEquals(PptStatusTexts.Kind.FAILED, PptStatusTexts.kindOf("failed"))
        assertEquals(PptStatusTexts.Kind.UNKNOWN, PptStatusTexts.kindOf(null))
        assertEquals(PptStatusTexts.Kind.UNKNOWN, PptStatusTexts.kindOf("weird"))
        assertTrue(PptStatusTexts.isPlayable("ready"))
        assertFalse(PptStatusTexts.isPlayable("processing"))
    }

    @Test
    fun text_matchesContractWording() {
        assertEquals("上传中 34%", PptStatusTexts.text("uploading", progress = 34))
        assertEquals("上传中", PptStatusTexts.text("uploading", progress = 0))
        assertEquals("排队转换", PptStatusTexts.text("queued", agentOnline = true))
        assertEquals("转换机离线 · 排队中", PptStatusTexts.text("queued", agentOnline = false))
        assertEquals("转换中 61%", PptStatusTexts.text("processing", progress = 61))
        assertEquals("转换失败", PptStatusTexts.text("failed"))
        assertEquals("可播放", PptStatusTexts.text("ready"))
        assertEquals("处理中", PptStatusTexts.text("mystery"))
        // 进度夹取
        assertEquals("转换中 100%", PptStatusTexts.text("processing", progress = 500))
        assertEquals("转换中 0%", PptStatusTexts.text("processing", progress = -5))
    }

    @Test
    fun unknownAgentStatus_doesNotClaimOffline() {
        assertEquals("排队转换", PptStatusTexts.text("queued", agentOnline = null))
    }

    @Test
    fun errorLine_flattensAndCaps() {
        assertEquals("转换失败原因 第二行", PptStatusTexts.errorLine("转换失败原因\n第二行"))
        assertEquals("", PptStatusTexts.errorLine(null))
        val long = "错".repeat(200)
        val line = PptStatusTexts.errorLine(long)
        assertEquals(121, line.length) // 120 + 省略号
        assertTrue(line.endsWith("…"))
    }

    @Test
    fun agentOfflineBanner_onlyWithPendingWork() {
        val queued = listOf(deck(status = PptDeck.STATUS_QUEUED))
        val ready = listOf(deck(status = PptDeck.STATUS_READY))
        assertTrue(PptStatusTexts.shouldShowAgentOfflineBanner(queued, agentOnline = false))
        assertTrue(
            PptStatusTexts.shouldShowAgentOfflineBanner(
                listOf(deck(status = PptDeck.STATUS_PROCESSING)),
                false,
            ),
        )
        assertFalse(PptStatusTexts.shouldShowAgentOfflineBanner(ready, agentOnline = false))
        assertFalse(PptStatusTexts.shouldShowAgentOfflineBanner(queued, agentOnline = true))
        assertFalse(PptStatusTexts.shouldShowAgentOfflineBanner(queued, agentOnline = null))
        assertFalse(PptStatusTexts.shouldShowAgentOfflineBanner(emptyList(), false))
    }

    @Test
    fun ownInProgress() {
        assertTrue(PptStatusTexts.isOwnInProgress(deck(status = "queued"), "2024000001"))
        assertFalse(PptStatusTexts.isOwnInProgress(deck(status = "queued"), "other"))
        assertFalse(PptStatusTexts.isOwnInProgress(deck(status = "ready"), "2024000001"))
        assertFalse(PptStatusTexts.isOwnInProgress(deck(status = "queued"), null))
    }
}
