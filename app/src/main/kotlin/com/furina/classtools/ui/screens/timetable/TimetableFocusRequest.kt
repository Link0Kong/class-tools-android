package com.furina.classtools.ui.screens.timetable

import com.furina.classtools.data.timetable.Course
import com.furina.classtools.data.timetable.CourseFocus
import com.furina.classtools.data.timetable.TtCalendar
import com.furina.classtools.data.timetable.TtSnapshot
import java.time.LocalDate

data class TimetableFocusRequest(val id: String, val raw: String?) {
    internal fun shouldApply(lastAppliedId: String?, openedFrom: String?): Boolean =
        id != lastAppliedId && (openedFrom == null || raw != openedFrom)

    internal fun resolve(snapshot: TtSnapshot, today: LocalDate, currentWeek: Int?, maxWeek: Int): TimetableFocusTarget {
        val focus = CourseFocus.decode(raw)
        val todayIndex = TtCalendar.dayIndex(today)
        val todayTarget = TimetableFocusTarget(todayIndex)
        if (raw == null) return todayTarget
        if (focus == null) return todayTarget.copy(message = "课程定位无效，显示今天的课表")
        val termStart = TtCalendar.parseTermStart(snapshot.termStart)
        if (!focus.isDateOnly && (snapshot.c.isEmpty() || termStart == null)) {
            return todayTarget.copy(message = "课表尚未同步，刷新后可在课表查看课程")
        }
        val date = LocalDate.parse(focus.date)
        val week = TtCalendar.weekOf(termStart, date)
        val todayMonday = today.minusDays(todayIndex.toLong())
        val weekOffset = when {
            currentWeek != null && week != null && week in 1..maxWeek -> week - currentWeek
            currentWeek == null && date in todayMonday..todayMonday.plusDays(6) -> 0
            else -> return todayTarget.copy(message = "该日期超出可查看的课表范围")
        }
        val course = if (focus.isDateOnly) null else focus.match(snapshot, date)
        return TimetableFocusTarget(
            day = TtCalendar.dayIndex(date),
            weekOffset = weekOffset,
            course = course,
            message = "这节课已变动，显示当天安排".takeIf { !focus.isDateOnly && course == null },
        )
    }
}

internal data class TimetableFocusTarget(
    val day: Int,
    val weekOffset: Int = 0,
    val course: Course? = null,
    val message: String? = null,
)
