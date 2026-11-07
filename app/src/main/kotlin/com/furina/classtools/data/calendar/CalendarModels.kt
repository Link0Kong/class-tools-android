package com.furina.classtools.data.calendar

import com.furina.classtools.data.permission.PermissionState
import java.time.ZoneId

/**
 * 一条待写入系统日历的事件（纯数据，可单测）。
 *
 * 与 `CalendarContract.Events` 的映射：
 * - [title] → TITLE，[description] → DESCRIPTION，[location] → EVENT_LOCATION；
 * - [beginMillis]/[endMillis] → DTSTART/DTEND（本地时区毫秒）；
 * - [reminderMinutes] → Reminders 表 ALERT（默认提前 10 分钟）；
 * - [timeZone] → EVENT_TIMEZONE（默认本机时区 id）。
 */
data class CalendarEvent(
    val title: String,
    val beginMillis: Long,
    val endMillis: Long,
    val description: String = "",
    val location: String = "",
    val allDay: Boolean = false,
    val reminderMinutes: Int = DEFAULT_REMINDER_MINUTES,
    val timeZone: String = ZoneId.systemDefault().id,
    /** 来源标识（用于调试/去重；如 `course:<name>:<date>` / `notice:<gid>`）。 */
    val sourceKey: String = "",
) {
    val durationMillis: Long get() = endMillis - beginMillis

    companion object {
        const val DEFAULT_REMINDER_MINUTES: Int = 10

        /** 单节课/单个日程默认时长（课程按节次时间计算，不使用该值）。 */
        const val DEFAULT_DURATION_MINUTES: Int = 60
    }
}

/** 日历写入判定（纯函数）。 */
enum class CalendarWriteDecision {
    /** 有权限，可直接写入 CalendarContract。 */
    PROCEED,

    /** 未授权但可请求 → 由 UI 请求权限；不可请求时由 Lead 走 ACTION_INSERT 兜底。 */
    NEEDS_PERMISSION,

    /** 永久拒绝：不再弹系统框，只能引导设置或走 ACTION_INSERT 兜底。 */
    DENIED,
}

/**
 * 日历写入结果：
 * - [Success]：已直接写入，`eventId` 为 Events 行 id；
 * - [NeedsPermission]：无权限（含可请求/永久拒绝两种情况，由调用方决定请求或 ACTION_INSERT 兜底）；
 * - [NoCalendarAccount]：有权限但本机没有可写日历（如未登录任何日历账号）→ 用 ACTION_INSERT 兜底；
 * - [Invalid]/[Failed]：数据非法/写入异常。
 */
sealed interface CalendarWriteResult {
    data class Success(val eventId: Long, val sourceKey: String = "") : CalendarWriteResult
    data object NeedsPermission : CalendarWriteResult
    data object NoCalendarAccount : CalendarWriteResult
    data class Invalid(val reason: String) : CalendarWriteResult
    data class Failed(val message: String) : CalendarWriteResult
}

/** 日历写入前的纯校验/权限判定。 */
object CalendarWritePolicy {

    /** 事件字段校验；非法返回原因，合法返回 null。 */
    fun validate(event: CalendarEvent): String? = when {
        event.title.isBlank() -> "日程标题不能为空"
        event.beginMillis <= 0L -> "日程开始时间无效"
        event.endMillis <= event.beginMillis -> "日程结束时间需晚于开始时间"
        event.durationMillis > 7L * 24 * 60 * 60 * 1000 -> "单个日程时长过长（>7 天）"
        event.reminderMinutes < 0 -> "提醒分钟数不能为负"
        else -> null
    }

    /** 权限 → 写入决策。 */
    fun decide(state: PermissionState): CalendarWriteDecision = when (state) {
        PermissionState.GRANTED -> CalendarWriteDecision.PROCEED
        PermissionState.REQUESTABLE, PermissionState.DENIED -> CalendarWriteDecision.NEEDS_PERMISSION
        PermissionState.PERMANENTLY_DENIED -> CalendarWriteDecision.DENIED
    }

    /** 前置校验（字段 + 权限），返回 null 表示可写。 */
    fun precheck(event: CalendarEvent, permission: PermissionState): CalendarWriteResult? {
        validate(event)?.let { return CalendarWriteResult.Invalid(it) }
        return when (decide(permission)) {
            CalendarWriteDecision.PROCEED -> null
            CalendarWriteDecision.NEEDS_PERMISSION, CalendarWriteDecision.DENIED ->
                CalendarWriteResult.NeedsPermission
        }
    }

    /** 同一事件是否重复（简单按 sourceKey；空 sourceKey 不判重）。 */
    fun isDuplicate(candidate: CalendarEvent, existing: List<CalendarEvent>): Boolean =
        candidate.sourceKey.isNotBlank() && existing.any { it.sourceKey == candidate.sourceKey }
}
