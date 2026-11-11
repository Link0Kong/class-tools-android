package com.furina.classtools.ui.screens.timetable

import com.furina.classtools.data.timetable.Course
import com.furina.classtools.data.timetable.TtSnapshot
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v3 课表纯逻辑单测：配色稳定/对比度、滑动切周、周次边界、显示比例、详情文案、导出 payload。
 */
class TimetableDesignTest {

    private val snapshot = TtSnapshot(
        c = listOf(
            Course(
                name = "高等数学",
                teacher = "刘老师",
                room = "自强楼(教室01104)",
                day = 0,
                start = 1,
                end = 2,
                weeks = "1-16",
                parity = "all",
            ),
            Course(
                name = "大学英语",
                teacher = "王老师",
                room = "明理楼(教室02301)",
                day = 4,
                start = 6,
                end = 7,
                weeks = "2-20",
                parity = "odd",
            ),
        ),
        t = "2026-08-31",
    )
    private val termStart = LocalDate.of(2026, 8, 31)

    // ---- 配色 ---------------------------------------------------------------

    @Test
    fun palette_has16PairsAndAAContrast() {
        assertEquals(16, TimetablePalette.LIGHT.size)
        assertEquals(16, TimetablePalette.DARK.size)
        TimetablePalette.LIGHT.forEach { pair ->
            assertTrue(
                "浅色配色对比度不足: ${pair.container.toString(16)}",
                TimetablePalette.contrastRatio(pair.container, pair.content) >= 4.5,
            )
        }
        TimetablePalette.DARK.forEach { pair ->
            assertTrue(
                "深色配色对比度不足: ${pair.container.toString(16)}",
                TimetablePalette.contrastRatio(pair.container, pair.content) >= 4.5,
            )
        }
    }

    @Test
    fun palette_indexStableAndInRange() {
        val first = TimetablePalette.indexOf("高等数学")
        repeat(5) { assertEquals(first, TimetablePalette.indexOf("高等数学 ")) }
        assertTrue(first in 0 until TimetablePalette.COLOR_COUNT)
        assertEquals(0, TimetablePalette.indexOf(""))
        // 同名浅/深色索引一致
        assertEquals(first, TimetablePalette.LIGHT.indexOf(TimetablePalette.lightOf("高等数学")))
        assertEquals(first, TimetablePalette.DARK.indexOf(TimetablePalette.darkOf("高等数学")))
        // 多门课能分布到不同颜色（不是全部一个色）
        val distinct = (1..30).map { TimetablePalette.indexOf("课程$it") }.distinct().size
        assertTrue("30 门课只分布了 $distinct 种颜色", distinct >= 6)
    }

    // ---- 滑动切周 -----------------------------------------------------------

    @Test
    fun swipe_lockThreshold() {
        assertFalse(TimetableSwipe.shouldLock(11f, 0f))
        assertTrue(TimetableSwipe.shouldLock(12f, 0f))
        assertTrue(TimetableSwipe.shouldLock(30f, 20f)) // 30 > 20*1.35
        assertFalse(TimetableSwipe.shouldLock(30f, 23f)) // 30 <= 31.05，偏纵向
    }

    @Test
    fun swipe_directionAndCommit() {
        assertEquals(TimetableSwipe.Direction.NEXT, TimetableSwipe.directionOf(-60f))
        assertEquals(TimetableSwipe.Direction.PREV, TimetableSwipe.directionOf(60f))
        assertEquals(TimetableSwipe.Direction.NONE, TimetableSwipe.directionOf(10f))
        assertTrue(TimetableSwipe.shouldCommit(-60f, 10f))
        assertTrue(TimetableSwipe.shouldCommit(56f, 0f))
        assertFalse(TimetableSwipe.shouldCommit(40f, 0f))
        assertFalse(TimetableSwipe.shouldCommit(60f, 60f))
    }

    @Test
    fun swipe_dampedAtEdges() {
        assertEquals(100f, TimetableSwipe.damped(100f, hasTarget = true), 0.001f)
        assertEquals(28f, TimetableSwipe.damped(100f, hasTarget = false), 0.001f)
    }

    @Test
    fun swipe_targetWeekBounds() {
        assertNull(TimetableSwipe.targetWeek(1, TimetableSwipe.Direction.PREV, 18))
        assertNull(TimetableSwipe.targetWeek(18, TimetableSwipe.Direction.NEXT, 18))
        assertEquals(2, TimetableSwipe.targetWeek(1, TimetableSwipe.Direction.NEXT, 18))
        assertEquals(17, TimetableSwipe.targetWeek(18, TimetableSwipe.Direction.PREV, 18))
    }

    // ---- 周次 ---------------------------------------------------------------

    @Test
    fun maxWeek_minimum18AndFromData() {
        assertEquals(18, TimetableWeekLogic.maxWeekOf(emptyList()))
        assertEquals(20, TimetableWeekLogic.maxWeekOf(snapshot.c))
    }

    @Test
    fun displayWeek_clamps() {
        assertEquals(10, TimetableWeekLogic.displayWeek(10, 0, 18))
        assertEquals(18, TimetableWeekLogic.displayWeek(10, 9, 18))
        assertEquals(1, TimetableWeekLogic.displayWeek(10, -20, 18))
        assertNull(TimetableWeekLogic.displayWeek(null, 0, 18))
        assertTrue(TimetableWeekLogic.isThisWeek(0))
        assertFalse(TimetableWeekLogic.isThisWeek(-1))
    }

    @Test
    fun navigate_clampsAtBounds() {
        assertEquals(2, TimetableWeekLogic.navigate(1, TimetableSwipe.Direction.NEXT, 20))
        assertNull(TimetableWeekLogic.navigate(1, TimetableSwipe.Direction.PREV, 20))
        assertNull(TimetableWeekLogic.navigate(20, TimetableSwipe.Direction.NEXT, 20))
        assertEquals(19, TimetableWeekLogic.navigate(20, TimetableSwipe.Direction.PREV, 20))
    }

    @Test
    fun weekRangeLabel_format() {
        assertEquals("11/2 – 11/8", TimetableWeekLogic.weekRangeLabel(termStart, 10))
        assertEquals("", TimetableWeekLogic.weekRangeLabel(null, 10))
    }

    // ---- 显示比例 -----------------------------------------------------------

    @Test
    fun metrics_compactVsTablet() {
        val phone = TimetableLayout.metricsFor(412, twoPane = false)
        assertEquals(52, phone.rowHeightDp)
        assertEquals(38, phone.labelWidthDp)
        assertFalse(phone.showEndTime)

        val medium = TimetableLayout.metricsFor(700, twoPane = false)
        assertEquals(56, medium.rowHeightDp)
        assertTrue(medium.showEndTime)

        val tablet = TimetableLayout.metricsFor(1000, twoPane = true)
        assertEquals(60, tablet.rowHeightDp)
        assertEquals(52, tablet.labelWidthDp)
        assertTrue(tablet.courseNameMaxLines >= 2)
    }

    @Test
    fun columnWidth_fitsAvailableWidth() {
        // 手机 7 列不横向溢出
        val width = TimetableLayout.columnWidthDp(availableWidthDp = 350f, dayCount = 7)
        assertTrue(width * 7 <= 350f + 0.01f)
        // 5 列时更宽
        assertTrue(TimetableLayout.columnWidthDp(350f, 5) > width)
        assertEquals(0f, TimetableLayout.columnWidthDp(350f, 0), 0.001f)
        assertEquals(5, TimetableLayout.visibleDayCount(weekendEmpty = true))
        assertEquals(7, TimetableLayout.visibleDayCount(weekendEmpty = false))
    }

    // ---- 详情文案 -----------------------------------------------------------

    @Test
    fun periodAndTimeText() {
        assertEquals("第1-2节", TimetableDetail.periodText(1, 2))
        assertEquals("第5节", TimetableDetail.periodText(5, 5))
        assertEquals("08:10–09:40", TimetableDetail.timeRangeText(1, 2))
        assertEquals("14:30–16:00", TimetableDetail.timeRangeText(6, 7))
    }

    @Test
    fun detailRows_containsAllFields() {
        val course = snapshot.c.first()
        val rows = TimetableDetail.detailRows(course, 10)
        val map = rows.toMap()
        assertEquals("第1-2节", map["节次"])
        assertEquals("刘老师", map["教师"])
        assertTrue(map["教室"]!!.contains("自强楼"))
        assertTrue(map["周次"]!!.contains("1-16"))
        assertTrue(rows.any { it.first == "星期" })

        val oddCourse = snapshot.c[1]
        assertTrue(TimetableDetail.weekText(oddCourse, 10).contains("单周"))
    }

    // ---- 导出 ---------------------------------------------------------------

    @Test
    fun exportSpec_buildsConfigBlocksAndNaming() {
        val spec = TimetableExport.buildSpec(
            snapshot = snapshot,
            termStart = termStart,
            week = 10,
            nowMillis = 1_794_016_800_000L,
            recordSuffix = "ABCD",
        )
        assertNotNull(spec)
        assertEquals("第 10 周课表", spec!!.title)
        assertEquals(4, spec.config.size)
        assertTrue(spec.blocks.isNotEmpty())
        val firstBlock = spec.blocks.first()
        assertTrue(firstBlock is com.furina.classtools.features.export.TextBlock)
        val lines = (firstBlock as com.furina.classtools.features.export.TextBlock).lines
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.first().plain.orEmpty().contains("高等数学"))
        assertTrue(lines.first().plain.orEmpty().contains("@自强楼"))
        assertTrue(spec.file.startsWith("课表_"))
        assertTrue(spec.rec.startsWith("TT-"))
        assertTrue(spec.rec.endsWith("ABCD"))
        assertTrue(spec.foot.startsWith("生成时间"))
    }

    @Test
    fun exportSpec_emptyWeekReturnsNull() {
        val spec = TimetableExport.buildSpec(
            snapshot = snapshot,
            termStart = termStart,
            week = 19, // 高数 1-16、大英 2-20 单周：第 19 周只有大英（odd 匹配）→ 有内容
            nowMillis = 1_000L,
        )
        assertNotNull(spec)
        // 第 21 周：大英 2-20 不含、高数 1-16 不含 → 空
        val empty = TimetableExport.buildSpec(
            snapshot = snapshot,
            termStart = termStart,
            week = 21,
            nowMillis = 1_000L,
        )
        assertNull(empty)
    }
}
