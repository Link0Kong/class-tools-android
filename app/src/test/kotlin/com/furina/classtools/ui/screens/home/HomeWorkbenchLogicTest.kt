package com.furina.classtools.ui.screens.home

import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.marks.MarksSnapshot
import com.furina.classtools.data.notices.NoticeEntry
import com.furina.classtools.data.notices.NoticeGroupDto
import com.furina.classtools.data.notices.NoticeRow
import com.furina.classtools.data.power.PowerRow
import com.furina.classtools.data.timetable.SessionInfo
import com.furina.classtools.data.timetable.SessionState
import com.furina.classtools.data.timetable.TodayState
import com.furina.classtools.data.timetable.TtPlan
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工作台状态归约纯逻辑单测（问候/日期/待办过滤/轮换/电费卡）。
 */
class HomeWorkbenchLogicTest {

    private val now: Long = 1_793_930_400_000L // 2026-11-06 10:00 +08

    private fun hw(
        id: String,
        subject: String,
        due: String = "2026-11-09",
        time: String = "20:00",
        ts: Long = now - 1_000L,
    ): HomeworkRow = HomeworkRow(id = id, subject = subject, due = due, date = due, time = time, ts = ts)

    private fun autoEntry(
        id: String,
        title: String,
        cat: String = "教务",
        ts: Long = now,
        deadline: String = "",
        publishedAt: String? = null,
    ): NoticeEntry =
        NoticeEntry(
            row = NoticeRow(id = id, title = title, ts = ts),
            group = NoticeGroupDto(
                id = id.removePrefix("auto_"),
                title = title,
                cat = cat,
                deadline = deadline,
                publishedAt = publishedAt,
            ),
            items = emptyList(),
            hidden = false,
            edited = false,
        )

    // ---- 问候 / 日期 --------------------------------------------------------

    @Test
    fun greeting_timeBuckets() {
        assertEquals("夜深了", HomeWorkbenchLogic.greeting(0))
        assertEquals("夜深了", HomeWorkbenchLogic.greeting(4))
        assertEquals("早上好", HomeWorkbenchLogic.greeting(5))
        assertEquals("上午好", HomeWorkbenchLogic.greeting(9))
        assertEquals("中午好", HomeWorkbenchLogic.greeting(12))
        assertEquals("下午好", HomeWorkbenchLogic.greeting(14))
        assertEquals("晚上好", HomeWorkbenchLogic.greeting(18))
        assertEquals("夜深了", HomeWorkbenchLogic.greeting(23))
    }

    @Test
    fun dateAndWeekText() {
        val date = LocalDate.of(2026, 11, 6) // 周五
        assertEquals("11月6日 周五", HomeWorkbenchLogic.dateText(date))
        assertEquals("第 10 周", HomeWorkbenchLogic.weekText(10))
        assertEquals("", HomeWorkbenchLogic.weekText(null))
        assertEquals(
            "11月6日 周五 · 第 10 周 · 副团支部书记",
            HomeWorkbenchLogic.subtitle(date, 10, "副团支部书记"),
        )
        assertEquals("11月6日 周五", HomeWorkbenchLogic.subtitle(date, null, ""))
    }

    // ---- 作业 ---------------------------------------------------------------

    @Test
    fun openHomework_filtersDoneAndExpiredAndSortsByDue() {
        val rows = listOf(
            hw("a", "截止 11-10", due = "2026-11-10"),
            hw("b", "截止 11-09", due = "2026-11-09"),
            hw("c", "已完成", due = "2026-11-09"),
            hw("d", "已过期", due = "2026-11-04"),
        )
        val marks = MarksSnapshot(hwDone = mapOf("c" to true))
        val open = HomeWorkbenchLogic.openHomeworkRows(rows, marks, now)
        assertEquals(listOf("b", "a"), open.map { it.id })
    }

    @Test
    fun homeworkRotation_titleAndDueSub() {
        val rows = listOf(hw("a", "数据结构实验报告"), hw("b", ""))
        val items = HomeWorkbenchLogic.homeworkRotation(rows, MarksSnapshot(), now)
        assertEquals("数据结构实验报告", items[0].title)
        assertTrue(items[0].sub.isNotBlank())
        // 空科目回落内容/作业
        assertEquals("作业", items[1].title)
    }

    // ---- 通知 ---------------------------------------------------------------

    @Test
    fun noticePending_filtersDoneAndSortsByTimeDesc() {
        val entries = listOf(
            // 契约 §2.4：待处理必须有时间锚点（无时间=资料/无时间，不计工作台待办）
            autoEntry("auto_1", "旧通知", ts = now - 10_000L, deadline = "2026-11-07T20:00:00+08:00"),
            autoEntry("auto_2", "新通知", ts = now, deadline = "2026-11-08T20:00:00+08:00"),
            autoEntry("auto_3", "已处理", ts = now - 5_000L, deadline = "2026-11-08T20:00:00+08:00"),
            autoEntry("auto_4", "无时间资料", ts = now - 3_000L),
        )
        // NoticeEntry.id = group.id（真实 marks 也按 group id 存），因此 auto_3 的 id 是 "3"
        val marks = MarksSnapshot(ntDone = mapOf("3" to true))
        val pending = HomeWorkbenchLogic.noticePendingEntries(entries, marks, now)
        assertEquals(listOf("2", "1"), pending.map { it.id })
    }

    @Test
    fun noticePending_includesPublishedCard() {
        // 2026-11-18 契约 §5：发布卡（无时间锚点但有发布时间）计入待处理，兜底资料卡不计
        val entries = listOf(
            autoEntry("auto_pub", "发布卡", publishedAt = "2026-11-05T20:00:00+08:00"),
            autoEntry("auto_none", "无时间资料"),
        )
        val pending = HomeWorkbenchLogic.noticePendingEntries(entries, MarksSnapshot(), now)
        assertEquals(listOf("pub"), pending.map { it.id })
        val rotation = HomeWorkbenchLogic.noticeRotation(entries, MarksSnapshot(), now)
        assertTrue(rotation.single().sub.contains("发布"))
    }

    @Test
    fun noticeRotation_subContainsCategory() {
        val items = HomeWorkbenchLogic.noticeRotation(
            listOf(autoEntry("auto_1", "期中教学检查", cat = "教务", deadline = "2026-11-08T20:00:00+08:00")),
            MarksSnapshot(),
            now,
        )
        assertEquals(1, items.size)
        assertEquals("期中教学检查", items[0].title)
        assertTrue(items[0].sub.startsWith("教务"))
    }

    // ---- 电费 ---------------------------------------------------------------

    @Test
    fun powerCard_noSelectionMeansGuide() {
        assertNull(HomeWorkbenchLogic.powerCard(null, null, 30))
        assertNull(HomeWorkbenchLogic.powerCard("   ", null, 30))
    }

    @Test
    fun powerCard_loadingAndReady() {
        val loading = HomeWorkbenchLogic.powerCard("4-23A---321", null, 30)
        assertEquals(false, loading!!.hasReading)
        assertEquals("读取中", loading.stateText)

        val row = PowerRow(
            id = "4-23A---321",
            building = "23号楼A座",
            room = "321",
            q = 86.42,
            unit = "度",
            updatedAt = "2026-11-06T09:40:00+08:00",
        )
        val ready = HomeWorkbenchLogic.powerCard("4-23A---321", row, 30)!!
        assertTrue(ready.hasReading)
        assertEquals("23号楼A座 321", ready.label)
        assertEquals("86.4", ready.qText.trim())
        assertEquals("ok", ready.stateKey)

        val low = HomeWorkbenchLogic.powerCard("4-23A---321", row.copy(q = 5.0), 30)!!
        assertEquals("low", low.stateKey)
    }

    // ---- 轮换 / 今日课程 ----------------------------------------------------

    @Test
    fun rotationWrapsAndPauses() {
        assertEquals(1, HomeWorkbenchLogic.nextRotationIndex(0, 3))
        assertEquals(0, HomeWorkbenchLogic.nextRotationIndex(2, 3))
        assertEquals(0, HomeWorkbenchLogic.nextRotationIndex(0, 0))
        assertFalse(HomeWorkbenchLogic.rotationEnabled(1, pageVisible = true))
        assertFalse(HomeWorkbenchLogic.rotationEnabled(3, pageVisible = false))
        assertTrue(HomeWorkbenchLogic.rotationEnabled(3, pageVisible = true))
    }

    @Test
    fun todaySessionsAndOngoing() {
        val ongoing = SessionInfo(
            name = "数据结构",
            room = "明理楼",
            start = 1,
            end = 2,
            startText = "08:10",
            endText = "09:40",
            span = "第 1-2 节",
            startAt = now - 600_000L,
            endAt = now + 600_000L,
            state = SessionState.ONGOING,
            dayOffset = 0,
            label = "今天",
        )
        val plan = TtPlan(
            hasData = true,
            week = 10,
            today = listOf(ongoing),
            todayState = TodayState.HAS,
            next = null,
        )
        assertEquals(1, HomeWorkbenchLogic.todaySessions(plan).size)
        assertTrue(HomeWorkbenchLogic.hasOngoing(plan))
        assertFalse(HomeWorkbenchLogic.hasOngoing(null))
    }
}


