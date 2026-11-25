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
        val marks = MarksSnapshot(hwDone = mapOf("done" to true))
        val brief = homeworkBrief(rows, marks, now, emptySet())
        assertEquals(5, brief.pending.size)
        assertEquals(2, brief.overdue)
        assertEquals(1, brief.dueSoon)
        assertEquals(listOf("late", "next"), brief.highlights.map { it.id })

        val remaining = homeworkBrief(rows, marks, now, setOf("late", "next"))
        assertEquals(listOf("late2", "later", "undated"), remaining.pending.map { it.id })
        assertEquals(listOf("late2", "later"), remaining.highlights.map { it.id })
        assertEquals(1, remaining.overdue)
        assertEquals(0, remaining.dueSoon)
        assertEquals(HomeworkBrief(), homeworkBrief(rows, marks, now, brief.pending.map { it.id }.toSet()))
        assertEquals(HomeworkBrief(), homeworkBrief(emptyList(), marks, now, emptySet()))
    }
}
