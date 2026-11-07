package com.furina.classtools.data.reminder

import com.furina.classtools.data.homework.HomeworkDueCalculator
import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.timetable.TtPlan
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 提醒规划器（纯函数，无 Android 依赖，可单测）。
 *
 * 规划两类事件：
 * 1. **作业**
 *    - 每日作业截止汇总：默认每天 20:00（可配），汇总「从汇总时间起 48 小时内截止」的未完成作业；
 *      已经过了当天汇总时间则安排次日汇总；
 *    - 截止前 N 天提醒（0/1/3 天，默认 1 天）：触发时间 = 截止时间 − N×24h；若已进入提前窗口，
 *      立即提醒（now + [IMMEDIATE_DELAY_MS]）；被当日汇总覆盖的作业不再单独提醒，避免重复。
 * 2. **课表**
 *    - 下一节课开始前 N 分钟（5/10/15，默认 15）：触发时间 = 开始时间 − N 分钟；已进入窗口则立即提醒。
 *
 * 还会过滤：已完成、无截止/非法截止、已截止、长期课（无下一节）、安静时段（可选）、horizon 外（由调用方过滤或 plan 内部按 48h 上限）。
 */
object ReminderPlanner {

    /** 作业汇总的覆盖窗口：汇总时间起 48 小时（即“明天/后天截止”）。 */
    const val HOMEWORK_DIGEST_WINDOW_MS: Long = 48L * 60 * 60 * 1000

    /** 已进入提前窗口时，安排到下次 Worker 轮询附近立即提醒。 */
    const val IMMEDIATE_DELAY_MS: Long = 30L * 1000

    /** 规划器内部最大规划窗口：7 天（未来更远的截止由后续轮次再规划）。 */
    const val PLANNING_HORIZON_MS: Long = 7L * 24 * 60 * 60 * 1000

    private const val DAY_MS: Long = 24L * 60 * 60 * 1000

    private val MM_DD_HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)

    /** 一条已解析的作业截止。 */
    data class HomeworkDeadline(
        val id: String,
        val dueAtMillis: Long,
        val subject: String,
        val content: String,
    ) {
        val displayName: String
            get() = subject.ifBlank { content.lineSequence().firstOrNull().orEmpty() }.ifBlank { "作业" }
    }

    /** 作业提醒（不含课表）。 */
    fun planHomework(
        rows: List<HomeworkRow>,
        doneIds: Set<String> = emptySet(),
        nowMillis: Long = System.currentTimeMillis(),
        settings: ReminderSettings = ReminderSettings.DEFAULT,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ReminderEvent> {
        if (!settings.enabled || !settings.homeworkEnabled) return emptyList()
        val deadlines = rows.mapNotNull { row ->
            if (row.id in doneIds) return@mapNotNull null
            val dueAt = HomeworkDueCalculator.parseDue(row.date, row.time, zone) ?: return@mapNotNull null
            if (dueAt <= nowMillis) return@mapNotNull null
            HomeworkDeadline(
                id = row.id,
                dueAtMillis = dueAt,
                subject = row.subject.trim(),
                content = row.content.trim(),
            )
        }.filter { it.dueAtMillis <= nowMillis + PLANNING_HORIZON_MS }
        if (deadlines.isEmpty()) return emptyList()

        val events = mutableListOf<ReminderEvent>()
        val digestAt = nextDigestAt(nowMillis, settings, zone)
        // 汇总覆盖「明天 + 后天」整个自然日（含后天 23:59），与产品文案「明天/后天截止」一致。
        val digestWindowEnd = digestWindowEnd(digestAt, zone)
        val digestItems = deadlines
            .filter { it.dueAtMillis in (digestAt + 1)..digestWindowEnd }
        if (digestItems.isNotEmpty() && !settings.isQuiet(digestAt, zone)) {
            events += ReminderEvent(
                kind = ReminderKind.HOMEWORK_DIGEST,
                key = "hw-digest:" + formatDate(digestAt, zone),
                triggerAtMillis = digestAt,
                title = "作业截止汇总",
                body = digestBody(digestItems, zone),
                toolId = ReminderKind.HOMEWORK_DIGEST.toolId,
            )
        }

        deadlines.forEach { deadline ->
            // 当日汇总已覆盖（只对 1 天提前档生效；3 天提前需单独提醒）
            if (deadline in digestItems && settings.homeworkLeadDays <= 1) return@forEach
            val leadMillis = settings.homeworkLeadDays * DAY_MS
            val rawTrigger = deadline.dueAtMillis - leadMillis
            val trigger = if (rawTrigger <= nowMillis) nowMillis + IMMEDIATE_DELAY_MS else rawTrigger
            if (settings.isQuiet(trigger, zone)) return@forEach
            events += ReminderEvent(
                kind = ReminderKind.HOMEWORK_DUE,
                key = "hw-due:${deadline.id}",
                triggerAtMillis = trigger,
                title = "作业即将截止",
                body = buildString {
                    append(deadline.displayName)
                    append(" · ")
                    append(formatAt(deadline.dueAtMillis, zone))
                    append(" 截止")
                },
                toolId = ReminderKind.HOMEWORK_DUE.toolId,
            )
        }
        return events
    }

    /** 课表提醒（下一节）。 */
    fun planCourse(
        plan: TtPlan?,
        nowMillis: Long = System.currentTimeMillis(),
        settings: ReminderSettings = ReminderSettings.DEFAULT,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ReminderEvent> {
        if (!settings.enabled || !settings.courseEnabled) return emptyList()
        val next = plan?.next ?: return emptyList()
        if (!plan.hasData) return emptyList()
        if (next.startAt <= nowMillis) return emptyList()
        val leadMillis = settings.courseLeadMinutes * 60_000L
        val rawTrigger = next.startAt - leadMillis
        val trigger = if (rawTrigger <= nowMillis) nowMillis + IMMEDIATE_DELAY_MS else rawTrigger
        if (settings.isQuiet(trigger, zone)) return emptyList()
        val minutesLeft = ((next.startAt - nowMillis) / 60_000L).coerceAtLeast(1)
        return listOf(
            ReminderEvent(
                kind = ReminderKind.COURSE_SOON,
                key = "class:${next.startAt}",
                triggerAtMillis = trigger,
                title = "即将上课：${next.name}",
                body = buildString {
                    append(next.startText)
                    append(" · ")
                    append(next.name)
                    if (next.room.isNotBlank()) {
                        append(" · ")
                        append(next.room)
                    }
                    append("（约 ")
                    append(minutesLeft)
                    append(" 分钟后）")
                },
                toolId = ReminderKind.COURSE_SOON.toolId,
            ),
        )
    }

    /** 合并规划（按触发时间升序、同 key 去重）。 */
    fun plan(
        rows: List<HomeworkRow>,
        doneIds: Set<String> = emptySet(),
        plan: TtPlan? = null,
        nowMillis: Long = System.currentTimeMillis(),
        settings: ReminderSettings = ReminderSettings.DEFAULT,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ReminderPlan {
        if (!settings.enabled) return ReminderPlan(emptyList())
        val events = buildList {
            addAll(planHomework(rows, doneIds, nowMillis, settings, zone))
            addAll(planCourse(plan, nowMillis, settings, zone))
        }
            .filter { it.triggerAtMillis <= nowMillis + PLANNING_HORIZON_MS }
            .sortedWith(compareBy({ it.triggerAtMillis }, { it.key }))
            .distinctBy { it.key }
        return ReminderPlan(events)
    }

    /** Worker 用：仅保留 `[now-grace, now+horizon]` 内到点的事件。 */
    fun filterDue(
        events: List<ReminderEvent>,
        nowMillis: Long,
        horizonMillis: Long = ReminderPlan.DEFAULT_HORIZON_MS,
        graceMillis: Long = ReminderPlan.DEFAULT_GRACE_MS,
    ): List<ReminderEvent> = events.filter { event ->
        event.triggerAtMillis in (nowMillis - graceMillis)..(nowMillis + horizonMillis)
    }

    /** 汇总覆盖窗口结束：汇总日 + 2 天（后天）的 23:59:59.999。 */
    fun digestWindowEnd(digestAtMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val digestDate = Instant.ofEpochMilli(digestAtMillis).atZone(zone).toLocalDate()
        return digestDate.plusDays(2).atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
    }
    /** 下一次作业汇总时间：今天的 hh:mm，若已过则明天同一时间。 */
    fun nextDigestAt(
        nowMillis: Long,
        settings: ReminderSettings = ReminderSettings.DEFAULT,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        var digest = now.toLocalDate().atTime(settings.homeworkDigestHour, settings.homeworkDigestMinute)
            .atZone(zone)
        if (!digest.toInstant().toEpochMilli().let { it > nowMillis }) {
            digest = digest.plusDays(1)
        }
        return digest.toInstant().toEpochMilli()
    }

    /** 汇总正文：`2 项 · 数学 11-09 23:59；英语 11-10 18:00`（最多 5 项 + 省略）。 */
    fun digestBody(items: List<HomeworkDeadline>, zone: ZoneId = ZoneId.systemDefault()): String {
        val sorted = items.sortedBy { it.dueAtMillis }
        val head = buildString {
            append(sorted.size)
            append(" 项作业即将截止：")
        }
        val lines = sorted.take(MAX_DIGEST_ITEMS).map { item ->
            "${item.displayName} ${formatAt(item.dueAtMillis, zone)}"
        }
        val more = if (sorted.size > MAX_DIGEST_ITEMS) " 等 ${sorted.size} 项" else ""
        return head + lines.joinToString("；") + more
    }

    /** `MM-dd HH:mm`。 */
    fun formatAt(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        MM_DD_HH_MM.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** `yyyy-MM-dd`（去重键用）。 */
    fun formatDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toString()

    /** 供 UI/测试：明天的日期（本地时区）。 */
    fun tomorrow(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().plusDays(1)

    const val MAX_DIGEST_ITEMS: Int = 5

    @Suppress("unused")
    private val unusedDateTime = LocalDateTime.MIN
}
