package com.furina.classtools.data.timetable

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A timetable has no server-side occurrence ID. Resolve the displayed slot against today's account data. */
@Serializable
data class CourseFocus(val date: String, val start: Int = 0, val end: Int = 0, val name: String = "", val room: String = "") {
    fun encode(): String = Json.encodeToString(this)

    fun match(snapshot: TtSnapshot, day: LocalDate): Course? {
        val week = TtCalendar.weekOf(TtCalendar.parseTermStart(snapshot.termStart), day)
        return snapshot.c.singleOrNull {
            it.day == TtCalendar.dayIndex(day) && TtCalendar.isActive(it, week) &&
                it.start == start && it.end == end && it.name.ifBlank { "课程" } == name && it.room.trim() == room
        }
    }

    companion object {
        fun of(session: SessionInfo, zone: ZoneId = ZoneId.systemDefault()) = CourseFocus(
            Instant.ofEpochMilli(session.startAt).atZone(zone).toLocalDate().toString(),
            session.start, session.end, session.name, session.fullRoom,
        )
        fun decode(raw: String?): CourseFocus? = raw?.let {
            runCatching { Json.decodeFromString<CourseFocus>(it).also { focus -> LocalDate.parse(focus.date) } }.getOrNull()
        }
    }
}
