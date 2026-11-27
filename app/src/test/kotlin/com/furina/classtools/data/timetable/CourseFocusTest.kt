package com.furina.classtools.data.timetable

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CourseFocusTest {
    @Test fun dateSlotAndRoomMustStillIdentifyExactlyOneActiveCourse() {
        val day = LocalDate.of(2026, 11, 27)
        val c = Course("实验 / A&B", room = "北楼 302", day = 4, start = 3, end = 4, weeks = "1-16")
        val focus = CourseFocus(day.toString(),3,4,c.name,c.room)
        fun match(vararg courses: Course) = focus.match(TtSnapshot(courses.toList(),"2026-09-07"), day)
        assertEquals(focus, CourseFocus.decode(focus.encode()))
        assertEquals(c, match(c.copy(start = 1), c.copy(room = "301"), c))
        assertNull(match(c, c.copy(teacher = "另一位老师")))
        val specific = c.copy(teacher = "指定教师")
        assertEquals(specific, focus.copy(teacher = specific.teacher).match(
            TtSnapshot(listOf(c, specific), "2026-09-07"), day))
        assertNull(match(c.copy(weeks = "1-2")))
        assertNull(match())
        assertNull(CourseFocus.decode("bad date"))
    }
}
