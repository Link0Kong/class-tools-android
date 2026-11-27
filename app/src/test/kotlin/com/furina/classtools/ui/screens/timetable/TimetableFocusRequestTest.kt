package com.furina.classtools.ui.screens.timetable

import com.furina.classtools.data.timetable.Course
import com.furina.classtools.data.timetable.CourseFocus
import com.furina.classtools.data.timetable.TtSnapshot
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableFocusRequestTest {
    private val today = LocalDate.of(2026, 11, 27)
    private val nextMonday = today.plusDays(3)
    private val course = Course("实验课", room = "教学楼 310", start = 3, end = 4, weeks = "13")
    private val snapshot = TtSnapshot(listOf(course), "2026-09-07")
    private val raw = CourseFocus(nextMonday.toString(), 3, 4, course.name, course.room).encode()

    @Test fun consumedDeliveryNeverReopensButLaterTapCanOpenTheSameCourse() {
        val first = TimetableFocusRequest("first", raw)
        val second = TimetableFocusRequest("second", raw)
        assertTrue(first.shouldApply(null, null))
        assertFalse(first.shouldApply("first", null))
        assertFalse(second.shouldApply("first", raw))
        assertTrue(second.shouldApply("first", null))
        assertTrue(TimetableFocusRequest("third", "other").shouldApply("first", raw))
    }

    @Test fun dateHeadingAndExactCourseHaveTheSameDayButOnlyOneOpensDetails() {
        val date = TimetableFocusRequest("date", CourseFocus(nextMonday.toString()).encode())
            .resolve(snapshot, today, 12, 18)
        val row = TimetableFocusRequest("row", raw).resolve(snapshot, today, 12, 18)
        assertEquals(0, date.day)
        assertEquals(1, date.weekOffset)
        assertNull(date.course)
        assertNull(date.message)
        assertEquals(date.copy(course = course), row)
    }

    @Test fun movedMissingAndAmbiguousCoursesLeaveTheDayUsable() {
        val request = TimetableFocusRequest("row", raw)
        for (courses in listOf(listOf(course.copy(room = "教学楼 309")), listOf(course, course.copy(teacher = "另一位老师")))) {
            val target = request.resolve(snapshot.copy(c = courses), today, 12, 18)
            assertEquals(1, target.weekOffset)
            assertEquals(0, target.day)
            assertNull(target.course)
            assertNotNull(target.message)
        }
    }

    @Test fun malformedOutOfTermAndUnsyncedRequestsFallBackWithoutMatchingAnotherOccurrence() {
        val malformed = CourseFocus(today.toString(), start = 3, end = 0, name = course.name).encode()
        val old = CourseFocus("2026-08-31", 3, 4, course.name, course.room).encode()
        val invalidDate = CourseFocus("2026-11-31").encode()
        for (focus in listOf(malformed, old, invalidDate)) {
            val target = TimetableFocusRequest("bad", focus).resolve(snapshot, today, 12, 18)
            assertEquals(4, target.day)
            assertNull(target.course)
            assertNotNull(target.message)
        }
        val unsynced = TimetableFocusRequest("unsynced", raw).resolve(TtSnapshot(), today, null, 18)
        assertEquals(4, unsynced.day)
        assertNull(unsynced.course)
        assertNotNull(unsynced.message)
    }
}
