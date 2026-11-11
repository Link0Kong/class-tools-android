package com.furina.classtools.features.ppt

import com.furina.classtools.features.ppt.PptNav.PptPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** step 导航（页/步换算、前进后退、进度、文案）契约 §5/§8.1。 */
class PptNavTest {

    private val counts = listOf(1, 3, 2) // 3 页：1 / 3 / 2 步 = 6 步

    private fun pos(slide: Int, step: Int) = PptPosition(slide, step)

    @Test
    fun totals() {
        assertEquals(3, PptNav.pageCount(counts))
        assertEquals(6, PptNav.totalSteps(counts))
        assertEquals(1, PptNav.stepsOfSlide(counts, 0))
        assertEquals(3, PptNav.stepsOfSlide(counts, 1))
        assertEquals(2, PptNav.stepsOfSlide(counts, 2))
    }

    @Test
    fun globalIndex_andPositionAt_roundTrip() {
        val all = (0 until 6).map { PptNav.positionAt(counts, it) }
        assertEquals(
            listOf(pos(0, 0), pos(1, 0), pos(1, 1), pos(1, 2), pos(2, 0), pos(2, 1)),
            all,
        )
        assertEquals(listOf(0, 1, 2, 3, 4, 5), all.map { PptNav.globalIndex(counts, it) })
    }

    @Test
    fun next_crossesSlideBoundary() {
        assertEquals(pos(1, 0), PptNav.next(counts, pos(0, 0)))
        assertEquals(pos(1, 1), PptNav.next(counts, pos(1, 0)))
        assertEquals(pos(1, 2), PptNav.next(counts, pos(1, 1)))
        assertEquals(pos(2, 0), PptNav.next(counts, pos(1, 2)))
        assertEquals(pos(2, 1), PptNav.next(counts, pos(2, 0)))
    }

    @Test
    fun lastStep_nextNull_prevSymmetry() {
        assertNull(PptNav.next(counts, pos(2, 1)))
        assertTrue(PptNav.isEnd(counts, pos(2, 1)))
        assertFalse(PptNav.isEnd(counts, pos(2, 0)))
        assertNull(PptNav.prev(counts, pos(0, 0)))
        assertFalse(PptNav.isEnd(counts, pos(0, 0)))
    }

    @Test
    fun prev_crossesSlideBoundary() {
        assertEquals(pos(1, 2), PptNav.prev(counts, pos(2, 0)))
        assertEquals(pos(1, 0), PptNav.prev(counts, pos(1, 1)))
        assertEquals(pos(0, 0), PptNav.prev(counts, pos(1, 0)))
    }

    @Test
    fun jumpToSlide_alwaysFirstStep_andClamped() {
        assertEquals(pos(0, 0), PptNav.jumpToSlide(counts, -3))
        assertEquals(pos(1, 0), PptNav.jumpToSlide(counts, 1))
        assertEquals(pos(2, 0), PptNav.jumpToSlide(counts, 2))
        assertEquals(pos(2, 0), PptNav.jumpToSlide(counts, 99))
    }

    @Test
    fun normalize_clampsBothAxes() {
        assertEquals(pos(0, 0), PptNav.normalize(counts, pos(-1, -1)))
        assertEquals(pos(1, 2), PptNav.normalize(counts, pos(1, 99)))
        assertEquals(pos(2, 1), PptNav.normalize(counts, pos(9, 9)))
    }

    @Test
    fun label_contractFormat() {
        assertEquals("3/3 · 2/2", PptNav.label(counts, pos(2, 1)))
        assertEquals("1/3 · 1/1", PptNav.label(counts, pos(0, 0)))
        assertEquals("2/3 · 2/3", PptNav.label(counts, pos(1, 1)))
        assertEquals("第 2 页 · 第 2 步", PptNav.spokenLabel(counts, pos(1, 1)))
        assertEquals("2/3", PptNav.pageLabel(counts, 1))
    }

    @Test
    fun progress_firstAndLast() {
        assertEquals(1f / 6f, PptNav.progress(counts, pos(0, 0)), 0.0001f)
        assertEquals(1f, PptNav.progress(counts, pos(2, 1)), 0.0001f)
        assertEquals(0.5f, PptNav.progress(counts, PptPosition(1, 1)), 0.0001f) // 第 3 步 / 6
    }

    @Test
    fun emptyCounts_noCrash() {
        val empty = emptyList<Int>()
        assertEquals(0, PptNav.totalSteps(empty))
        assertEquals(pos(0, 0), PptNav.first())
        assertEquals(pos(0, 0), PptNav.last(empty))
        assertEquals(pos(0, 0), PptNav.positionAt(empty, 5))
        assertNull(PptNav.next(empty, pos(0, 0)))
        assertNull(PptNav.prev(empty, pos(0, 0)))
        assertEquals(0f, PptNav.progress(empty, pos(0, 0)), 0.0001f)
        assertEquals("0/0 · 0/0", PptNav.label(empty, pos(0, 0)))
        assertTrue(PptNav.isEnd(empty, pos(0, 0)))
    }

    @Test
    fun zeroStepSlideTreatedAsOne() {
        val weird = listOf(0, 2)
        assertEquals(3, PptNav.totalSteps(weird))
        assertEquals(pos(0, 0), PptNav.positionAt(weird, 0))
        assertEquals(pos(1, 0), PptNav.positionAt(weird, 1))
    }
}
