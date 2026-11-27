package com.furina.classtools.data.timetable

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A timetable has no server-side occurrence ID. Resolve the displayed slot against today's account data. */
@Serializable
data class CourseFocus(val date: String, val start: Int = 0, val end: Int = 0, val name: String = "", val room: String = "", val teacher: String = "") {
    fun encode(): String = Json.encodeToString(this)

    val isDateOnly: Boolean get() = start == 0 && end == 0 && name.isEmpty() && room.isEmpty()

    fun match(snapshot: TtSnapshot, day: LocalDate): Course? {
        if (isDateOnly || day.toString() != date) return null
        val termStart = TtCalendar.parseTermStart(snapshot.termStart) ?: return null
        val week = TtCalendar.weekOf(termStart, day) ?: return null
        return snapshot.c.singleOrNull {
            it.day == TtCalendar.dayIndex(day) && TtCalendar.isActive(it, week) &&
                it.start == start && it.end == end && it.name.ifBlank { "课程" } == name && it.room.trim() == room &&
                (teacher.isEmpty() || it.teacher.trim() == teacher)
        }
    }

    companion object {
        fun of(session: SessionInfo, zone: ZoneId = ZoneId.systemDefault()) = CourseFocus(
            Instant.ofEpochMilli(session.startAt).atZone(zone).toLocalDate().toString(),
            session.start, session.end, session.name, session.fullRoom, session.teacher.trim(),
        )
        fun decode(raw: String?): CourseFocus? = raw?.let {
            runCatching {
                Json.decodeFromString<CourseFocus>(it).takeIf { focus ->
                    val year = LocalDate.parse(focus.date).year
                    year in 1..9999 && (focus.isDateOnly ||
                        (focus.start in 1..TtPeriods.COUNT && focus.end in focus.start..TtPeriods.COUNT && focus.name.isNotBlank()))
                }
            }.getOrNull()
        }
    }
}
