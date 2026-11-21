package com.furina.classtools.ui.screens.me

import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.marks.MarksSnapshot
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeStatsSectionTest {

    private fun hw(id: String, subject: String = "高等数学"): HomeworkRow = HomeworkRow(
        id = id,
        subject = subject,
        content = "习题",
        date = LocalDate.of(2026, 11, 10).toString(),
        time = "23:59",
        ts = 1L,
    )

    // ------------------------------------------------------------ 聚合

    @Test
    fun `按 marks 统计待交与已完成`() {
        val rows = listOf(hw("a"), hw("b"), hw("c"))
        val marks = MarksSnapshot(hwDone = mapOf("b" to true, "c" to true))
        val stats = MeStatsBuilder.fromCached(rows, marks, fitRecords = 2, drawRecords = 1)
        assertEquals(1, stats.pendingHomework)
        assertEquals(2, stats.doneHomework)
        assertEquals(2, stats.fitRecords)
        assertEquals(1, stats.drawRecords)
    }

    @Test
    fun `隐藏作业不计入待交`() {
        val rows = listOf(hw("a"), hw("b"))
        val stats = MeStatsBuilder.fromCached(
            homeworkRows = rows,
            marks = MarksSnapshot(),
            fitRecords = 0,
            drawRecords = 0,
            hiddenHomeworkIds = setOf("a"),
        )
        assertEquals(1, stats.pendingHomework)
        assertEquals(0, stats.doneHomework)
    }

    @Test
    fun `通知 marks 不影响作业统计`() {
        val marks = MarksSnapshot(ntDone = mapOf("a" to true), ntPin = mapOf("b" to true))
        val stats = MeStatsBuilder.fromCached(listOf(hw("a")), marks, fitRecords = 0, drawRecords = 0)
        assertEquals(1, stats.pendingHomework)
        assertEquals(0, stats.doneHomework)
    }

    @Test
    fun `负数记录被钳制为 0`() {
        val stats = MeStatsBuilder.fromCached(emptyList(), MarksSnapshot(), fitRecords = -3, drawRecords = -1)
        assertEquals(0, stats.fitRecords)
        assertEquals(0, stats.drawRecords)
    }

    // ------------------------------------------------------------ 格式化

    @Test
    fun `数字格式化 0 到 999 原样 1000 以上折叠`() {
        assertEquals("0", MeStatsBuilder.formatCount(0))
        assertEquals("7", MeStatsBuilder.formatCount(7))
        assertEquals("999", MeStatsBuilder.formatCount(999))
        assertEquals("999+", MeStatsBuilder.formatCount(1000))
        assertEquals("999+", MeStatsBuilder.formatCount(99999))
    }

    @Test
    fun `电量格式化与无数据隐藏`() {
        assertNull(MeStatsBuilder.formatPower(null))
        assertNull(MeStatsBuilder.formatPower(Double.NaN))
        assertEquals("36.5 度", MeStatsBuilder.formatPower(36.5))
        assertEquals("23号楼A座305 · 36.5 度", MeStatsBuilder.formatPower(36.5, "23号楼A座305"))
    }

    @Test
    fun `可选扩展指标只展示有数据的项`() {
        assertEquals(emptyList<Pair<String, String>>(), MeStatsBuilder.extraMetrics(MeStats()))
        val trimmed = MeStatsBuilder.extraMetrics(
            MeStats(pendingNotices = 3, classMembers = 68, powerRemaining = 12.0),
        )
        assertEquals(listOf("未处理通知" to "3", "班级人数" to "68", "关注宿舍电量" to "12.0 度"), trimmed)
    }

    @Test
    fun `抽签记录数安全处理`() {
        assertEquals(0, MeStatsBuilder.drawCountFrom(null))
        assertEquals(0, MeStatsBuilder.drawCountFrom(-2))
        assertEquals(3, MeStatsBuilder.drawCountFrom(3))
    }

    // ------------------------------------------------------------ 提醒入口状态

    @Test
    fun `提醒入口未开启文案`() {
        val status = ReminderStatus(enabled = false)
        assertEquals("未开启 · 点击设置", status.label)
        assertFalse(status.isWarning)
    }

    @Test
    fun `提醒已开启但通知权限缺失时警告`() {
        val status = ReminderStatus(
            enabled = true,
            notificationPermission = false,
            enabledModuleCount = 10,
        )
        assertEquals("通知权限未开启 · 点击设置", status.label)
        assertTrue(status.isWarning)
    }

    @Test
    fun `提醒已开启正常文案展示范围`() {
        val status = ReminderStatus(
            enabled = true,
            notificationPermission = true,
            enabledModuleCount = 10,
        )
        assertEquals("已开启 10 项 · 截止提前 1 小时", status.label)
        assertFalse(status.isWarning)
    }

    @Test
    fun `只开作业或都没开时的文案`() {
        val homeworkOnly = ReminderStatus(
            enabled = true,
            notificationPermission = true,
            enabledModuleCount = 3,
        )
        assertEquals("已开启 3 项 · 截止提前 1 小时", homeworkOnly.label)

        val none = ReminderStatus(
            enabled = true,
            notificationPermission = true,
            enabledModuleCount = 0,
        )
        assertEquals("未选择通知类型 · 点击设置", none.label)
        assertEquals(0, none.enabledModuleCount)
    }

    @Test
    fun `默认值符合产品口径`() {
        val stats = MeStats()
        assertEquals(0, stats.pendingHomework)
        assertEquals(0, stats.doneHomework)
        assertEquals(0, stats.drawRecords)
        assertEquals(0, stats.fitRecords)
        assertNull(stats.pendingNotices)

        val reminder = ReminderStatus()
        assertFalse(reminder.enabled)
        assertFalse(reminder.notificationPermission)
        assertEquals(60, reminder.leadMinutes)
        assertEquals(10, reminder.classLeadMinutes)
    }
}
