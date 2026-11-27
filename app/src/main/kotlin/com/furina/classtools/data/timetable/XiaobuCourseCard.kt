package com.furina.classtools.data.timetable

import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Fields agreed with OPPO for the course reminder scene and the desktop suggestion card. */
internal data class XiaobuCourseScene(
    val intentName: String,
    val launcherId: String,
    val entityName: String,
    val milestoneCode: Int,
    val milestoneText: String,
)

internal object XiaobuCourseCard {
    const val TIMETABLE_LINK = "classtools://com.furina.classtools/timetable"
    private val clock = DateTimeFormatter.ofPattern("HH:mm").withZone(TimetableLink.CHINA_ZONE)

    data class Window(val course: TimetableLink.PlannedSession?, val nextChange: Long?)

    fun window(snapshot: TtSnapshot?, now: Long, lead: Long): Window {
        if (snapshot == null) return Window(null, null)
        val firstWeek = TimetableLink.currentWeek(snapshot, now, TimetableLink.CHINA_ZONE) ?: 1
        val next = TimetableLink.planSessions(snapshot, fromWeek = firstWeek, limit = Int.MAX_VALUE)
            .firstOrNull { it.startMillis > now } ?: return Window(null, null)
        val due = next.startMillis - lead
        return if (now < due) Window(null, due) else Window(next, next.startMillis)
    }

    fun focus(course: TimetableLink.PlannedSession) = CourseFocus(
        Instant.ofEpochMilli(course.startMillis).atZone(TimetableLink.CHINA_ZONE).toLocalDate().toString(),
        course.startPeriod, course.endPeriod, course.courseName, course.room, course.teacher.trim(),
    )

    fun entityId(course: TimetableLink.PlannedSession, owner: String) = "class-" +
        TimetableLink.sha1Hex("$owner|${course.sourceKey}|${course.endPeriod}|${course.startMillis}")

    fun responseCode(result: String?): Int? {
        val obj = result?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
        return (obj?.get("code") as? JsonPrimitive)?.intOrNull
    }

    // OPPO 文档 13558 / 13565 / 13576。仅请求桌面小布建议入口。
    fun payload(scene: XiaobuCourseScene, course: TimetableLink.PlannedSession, update: Boolean,
                requestId: String, now: Long, owner: String = ""): JsonObject {
        val start = clock.format(Instant.ofEpochMilli(course.startMillis))
        val end = clock.format(Instant.ofEpochMilli(course.endMillis))
        return buildJsonObject {
            put("intentName", scene.intentName)
            put("identifier", requestId)
            put("timestamp", now)
            putJsonObject("serviceId") { put("launcher", scene.launcherId) }
            putJsonObject("intentAction") { put("actionStatus", if (update) 1 else 0) }
            putJsonObject("intentEntity") {
                put("entityName", scene.entityName)
                put("entityId", entityId(course, owner))
                putJsonObject("milestone") {
                    put("code", scene.milestoneCode)
                    put("text", scene.milestoneText)
                }
                putJsonObject("capsule") { put("rightText", "$start 上课") }
                putJsonObject("primary") {
                    put("title", course.courseName)
                    put("content", listOf("$start–$end", course.room).filter(String::isNotBlank).joinToString(" · "))
                    put("clickAction", "$TIMETABLE_LINK?activity=${entityId(course, owner)}")
                }
            }
        }
    }
}
