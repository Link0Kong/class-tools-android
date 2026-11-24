package com.furina.classtools.data.calendar

import com.furina.classtools.data.notices.NoticeGroupDto
import com.furina.classtools.data.notices.NoticeStatusMachine
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * 通知 → 系统日历事件映射（纯函数，可单测）。
 *
 * `eligible` 口径（任一不满足即不可写）：
 * - 有标题；
 * - 不是长期安排（`recurrence != null`）；
 * - 有具体时间：优先 `eventAt`（活动），否则真实截止 `deadline`（含延期 `extensions` 最后一条）；
 * - 目标时间 **晚于 now**（已过期不写）。
 *
 * 字段：标题=通知标题；描述=摘要 + 来源/发送人 + 截止/活动动作；地点从 AI 结构化段落 `地点` 提取。
 */
object NoticeCalendarMapper {

    fun targetMillis(
        group: NoticeGroupDto,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        val eventAt = NoticeStatusMachine.eventAtOf(group, zone)
        if (eventAt != null && eventAt > nowMillis) return eventAt
        val deadline = NoticeStatusMachine.deadlineOf(group, zone)
        if (deadline != null && deadline > nowMillis) return deadline
        return null
    }

    fun eligible(
        group: NoticeGroupDto,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (group.recurrence != null) return false
        if (group.title.isBlank()) return false
        return targetMillis(group, nowMillis, zone) != null
    }

    fun eventFor(
        group: NoticeGroupDto,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        reminderMinutes: Int = CalendarEvent.DEFAULT_REMINDER_MINUTES,
    ): CalendarEvent? {
        if (!eligible(group, nowMillis, zone)) return null
        val begin = targetMillis(group, nowMillis, zone) ?: return null
        val isEvent = NoticeStatusMachine.eventAtOf(group, zone)?.let { it > nowMillis } == true
        val action = if (isEvent) {
            NoticeStatusMachine.conciseAction(group.eventAction.ifBlank { "开始" })
        } else {
            NoticeStatusMachine.conciseAction(group.deadlineAction.ifBlank { "截止" })
        }
        return CalendarEvent(
            title = group.title.trim(),
            beginMillis = begin,
            endMillis = begin + CalendarEvent.DEFAULT_DURATION_MINUTES * 60_000L,
            description = descriptionOf(group, action),
            location = locationOf(group),
            reminderMinutes = reminderMinutes,
            timeZone = zone.id,
            sourceKey = "notice:${group.id}",
        )
    }

    /** 地点：`aiSegments` 中 type 含「地点」的文本；没有则空串。 */
    fun locationOf(group: NoticeGroupDto): String = group.aiSegments
        .firstOrNull { it.type.contains("地点") }
        ?.text
        ?.trim()
        .orEmpty()

    /** 描述：摘要（aiSummary/summary，截断）+ 来源/发送人 + 动作 + 来源标记。 */
    fun descriptionOf(group: NoticeGroupDto, action: String): String = buildString {
        val summary = group.aiSummary.ifBlank { group.summary }.trim()
        if (summary.isNotEmpty()) {
            append(summary.take(MAX_SUMMARY_CHARS))
            append('\n')
        }
        val source = group.source.trim()
        if (source.isNotEmpty()) {
            append("来源：")
            append(source)
            if (group.senderName.isNotBlank()) {
                append(" · ")
                append(group.senderName.trim())
            }
            append('\n')
        }
        if (action.isNotBlank()) {
            append(action)
            append("时间见日程\n")
        }
        append("来自枫丹工具箱通知")
    }

    /** 通知时间文案（供日历描述/通知内容复用）：`MM-dd HH:mm`。 */
    fun formatAt(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)
            .format(Instant.ofEpochMilli(millis).atZone(zone))

    const val MAX_SUMMARY_CHARS: Int = 160
}
