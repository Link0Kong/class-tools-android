package com.furina.classtools.features.ppt

import com.furina.classtools.data.ppt.PptDeck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 列表过滤/排序/入口可见性（契约 §4.4/§4.6）。 */
class PptDeckListLogicTest {

    private val me = "3260707024"

    private fun deck(
        id: String,
        status: String,
        ownerSid: String = me,
        createdAt: Long = 1_000L,
        slideCount: Int = 12,
        stepCount: Int = 0,
        ownerName: String = "陈子麒",
        title: String = "班会",
    ) = PptDeck(
        id = id,
        title = title,
        ownerSid = ownerSid,
        ownerName = ownerName,
        status = status,
        createdAt = createdAt,
        slideCount = slideCount,
        stepCount = stepCount,
    )

    @Test
    fun visible_hidesOthersUploading() {
        val decks = listOf(
            deck("a", PptDeck.STATUS_UPLOADING, ownerSid = "other", createdAt = 3),
            deck("b", PptDeck.STATUS_UPLOADING, createdAt = 2),
            deck("c", PptDeck.STATUS_READY, ownerSid = "other", createdAt = 1),
        )
        val visible = PptDeckListLogic.visible(decks, me)
        assertEquals(listOf("b", "c"), visible.map { it.id })
        // 未登录/未知 sid：任何 uploading 都不显示
        assertEquals(listOf("c"), PptDeckListLogic.visible(decks, null).map { it.id })
    }

    @Test
    fun sort_ownInProgressFirst_thenCreatedDesc() {
        val decks = listOf(
            deck("ready-old", PptDeck.STATUS_READY, createdAt = 100),
            deck("mine-queued", PptDeck.STATUS_QUEUED, createdAt = 50),
            deck("ready-new", PptDeck.STATUS_READY, ownerSid = "other", createdAt = 300),
            deck("mine-failed", PptDeck.STATUS_FAILED, createdAt = 400),
            deck("other-processing", PptDeck.STATUS_PROCESSING, ownerSid = "other", createdAt = 500),
        )
        val sorted = PptDeckListLogic.visible(decks, me).map { it.id }
        assertEquals(listOf("mine-queued", "other-processing", "mine-failed", "ready-new", "ready-old"), sorted)
    }

    @Test
    fun sort_stableForEqualTimestamps() {
        val decks = listOf(
            deck("b", PptDeck.STATUS_READY, createdAt = 7),
            deck("a", PptDeck.STATUS_READY, createdAt = 7),
        )
        assertEquals(listOf("a", "b"), PptDeckListLogic.visible(decks, me).map { it.id })
    }

    @Test
    fun isEmpty() {
        assertTrue(PptDeckListLogic.isEmpty(emptyList(), me))
        assertTrue(
            PptDeckListLogic.isEmpty(listOf(deck("x", PptDeck.STATUS_UPLOADING, ownerSid = "other")), me),
        )
        assertFalse(PptDeckListLogic.isEmpty(listOf(deck("x", PptDeck.STATUS_READY)), me))
    }

    @Test
    fun entryVisibility() {
        val ready = deck("x", PptDeck.STATUS_READY)
        val failed = deck("y", PptDeck.STATUS_FAILED)
        assertTrue(PptDeckListLogic.canDelete(ready, me))
        assertFalse(PptDeckListLogic.canDelete(ready, "other"))
        assertFalse(PptDeckListLogic.canDelete(ready, null))
        assertTrue(PptDeckListLogic.canRetry(failed, me))
        assertFalse(PptDeckListLogic.canRetry(ready, me))
        assertFalse(PptDeckListLogic.canRetry(failed, "other"))
    }

    @Test
    fun ownerLabelFallback() {
        assertEquals("陈子麒", PptDeckListLogic.ownerLabel(deck("x", "ready")))
        assertEquals("同学 24", PptDeckListLogic.ownerLabel(deck("x", "ready", ownerSid = "3260707024", ownerName = "")))
        assertEquals("未知同学", PptDeckListLogic.ownerLabel(deck("x", "ready", ownerSid = "", ownerName = "")))
    }

    @Test
    fun pageText() {
        assertEquals("12 页 · 37 步", PptDeckListLogic.pageText(deck("x", "ready", slideCount = 12, stepCount = 37)))
        assertEquals("12 页", PptDeckListLogic.pageText(deck("x", "queued", slideCount = 12, stepCount = 0)))
        assertEquals("—", PptDeckListLogic.pageText(deck("x", "uploading", slideCount = 0)))
    }
}
