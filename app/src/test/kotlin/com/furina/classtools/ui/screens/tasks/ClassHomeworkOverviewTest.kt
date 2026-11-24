package com.furina.classtools.ui.screens.tasks

import com.furina.classtools.data.homework.HomeworkDueCalculator
import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.marks.MarksSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassHomeworkOverviewTest {
    @Test fun overdueBacklogKeepsNearestDeadlineVisibleAndExcludesFinishedWork() {
        val now = HomeworkDueCalculator.parseDue("2026-11-24", "12:00")!!
        val rows = listOf(
            HomeworkRow("archived", archived = true), HomeworkRow("done"), HomeworkRow("undated"),
            HomeworkRow("expired", date = "2026-11-20"), HomeworkRow("late", date = "2026-11-22"),
            HomeworkRow("late2", date = "2026-11-23"), HomeworkRow("next", date = "2026-11-24", time = "18:00"),
            HomeworkRow("later", date = "2026-11-29"),
        )
        val brief = homeworkBrief(rows, MarksSnapshot(hwDone = mapOf("done" to true)), now)
        assertEquals(5, brief.unfinished)
        assertEquals(2, brief.overdue)
        assertEquals(1, brief.dueSoon)
        assertEquals(listOf("late", "next"), brief.highlights.map { it.id })
    }
}
