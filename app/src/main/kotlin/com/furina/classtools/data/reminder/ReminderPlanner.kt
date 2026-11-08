package com.furina.classtools.data.reminder

import com.furina.classtools.data.homework.HomeworkDueCalculator
import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.notices.NoticeStatusMachine
import com.furina.classtools.data.notices.NoticesData
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

    /** 通知临近提醒提前量：目标时间前 24 小时；已进入窗口则立即提醒。 */
    const val NOTICE_LEAD_MS: Long = 24L * 60 * 60 * 1000

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

    // ------------------------------------------------------------ 通知提醒（ntremind）

    /** 通知目标类型（活动开始 / 截止；决定去重键后缀与文案）。 */
    enum class NoticeTargetType(val keySuffix: String, val label: String) {
        EVENT("event", "开始"),
        DEADLINE("deadline", "截止"),
    }

    /** 一条可提醒的通知目标（活动时间优先于截止时间）。 */
    data class NoticeTarget(
        val id: String,
        val title: String,
        val targetAtMillis: Long,
        val type: NoticeTargetType,
        val action: String,
        val source: String,
    ) {
        val displayName: String get() = title.ifBlank { "班级通知" }
        val dedupeKey: String get() = "notice:$id:${type.keySuffix}"
    }

    /**
     * 从通知快照解析可提醒目标（纯函数）：
     * - 只取 `auto` 组（人工通知无结构化时间）；`hidden` 不提醒；
     * - 长期安排（`recurrence != null`）不提醒；
     * - 已处理（marks `ntdone` 命中组 id）不提醒；
     * - 目标时间 = 未来 7 天内且 `eventAt` 未来优先，否则真实截止 `deadline`（含延期 extensions）；
     * - 已过期/无时间 → 不提醒。
     */
    fun noticeTargets(
        notices: NoticesData?,
        handledIds: Set<String> = emptySet(),
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<NoticeTarget> {
        val entries = notices?.auto ?: return emptyList()
        return entries.mapNotNull { entry ->
            if (entry.hidden) return@mapNotNull null
            val id = entry.id.trim()
            if (id.isEmpty() || id in handledIds) return@mapNotNull null
            val group = entry.group
            if (group.recurrence != null) return@mapNotNull null
            val eventAt = NoticeStatusMachine.eventAtOf(group, zone)?.takeIf { it > nowMillis }
            val deadline = NoticeStatusMachine.deadlineOf(group, zone)?.takeIf { it > nowMillis }
            val (targetAt, type) = when {
                eventAt != null -> eventAt to NoticeTargetType.EVENT
                deadline != null -> deadline to NoticeTargetType.DEADLINE
                else -> return@mapNotNull null
            }
            if (targetAt > nowMillis + PLANNING_HORIZON_MS) return@mapNotNull null
            val action = NoticeStatusMachine.conciseAction(
                if (type == NoticeTargetType.EVENT) group.eventAction else group.deadlineAction,
            )
            NoticeTarget(
                id = id,
                title = group.title.ifBlank { entry.title },
                targetAtMillis = targetAt,
                type = type,
                action = action,
                source = group.source,
            )
        }
    }

    /** 通知提醒（每日汇总 + 24 小时内临近提醒）。 */
    fun planNotices(
        notices: NoticesData?,
        handledIds: Set<String> = emptySet(),
        nowMillis: Long = System.currentTimeMillis(),
        settings: ReminderSettings = ReminderSettings.DEFAULT,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ReminderEvent> {
        if (!settings.enabled || !settings.noticeEnabled) return emptyList()
        val targets = noticeTargets(notices, handledIds, nowMillis, zone)
        if (targets.isEmpty()) return emptyList()

        val events = mutableListOf<ReminderEvent>()
        val digestAt = nextDigestAt(nowMillis, settings, zone)
        val digestWindowEnd = digestWindowEnd(digestAt, zone)
        val digestItems = targets.filter { it.targetAtMillis in (digestAt + 1)..digestWindowEnd }
        if (digestItems.isNotEmpty() && !settings.isQuiet(digestAt, zone)) {
            events += ReminderEvent(
                kind = ReminderKind.NOTICE_DIGEST,
                key = "nt-digest:" + formatDate(digestAt, zone),
                triggerAtMillis = digestAt,
                title = "通知截止汇总",
                body = noticeDigestBody(digestItems, zone),
                toolId = ReminderKind.NOTICE_DIGEST.toolId,
            )
        }

        targets.forEach { target ->
            // 已进入当日汇总窗口的通知由汇总覆盖，避免当晚两条提醒
            if (target in digestItems) return@forEach
            val rawTrigger = target.targetAtMillis - NOTICE_LEAD_MS
            val trigger = if (rawTrigger <= nowMillis) nowMillis + IMMEDIATE_DELAY_MS else rawTrigger
            if (settings.isQuiet(trigger, zone)) return@forEach
            events += ReminderEvent(
                kind = ReminderKind.NOTICE_DUE,
                key = target.dedupeKey,
                triggerAtMillis = trigger,
                title = "通知即将${target.type.label}：${target.displayName}",
                body = buildString {
                    if (target.action.isNotBlank()) {
                        append(target.action)
                        append(" · ")
                    }
                    append(formatAt(target.targetAtMillis, zone))
                    append(" ")
                    append(target.type.label)
                    if (target.source.isNotBlank()) {
                        append(" · ")
                        append(target.source)
                    }
                },
                toolId = ReminderKind.NOTICE_DUE.toolId,
            )
        }
        return events
    }

    /** 通知汇总正文：`2 条通知即将截止/开始：主题 11-09 18:00；...`（最多 5 条 + 省略）。 */
    fun noticeDigestBody(items: List<NoticeTarget>, zone: ZoneId = ZoneId.systemDefault()): String {
        val sorted = items.sortedBy { it.targetAtMillis }
        val lines = sorted.take(MAX_DIGEST_ITEMS).map { target ->
            "${target.displayName} ${formatAt(target.targetAtMillis, zone)} ${target.type.label}"
        }
        val more = if (sorted.size > MAX_DIGEST_ITEMS) " 等 ${sorted.size} 条" else ""
        return "${sorted.size} 条通知即将截止/开始：" + lines.joinToString("；") + more
    }

    /** 合并规划（按触发时间升序、同 key 去重）。 */
    fun plan(
        rows: List<HomeworkRow>,
        doneIds: Set<String> = emptySet(),
        plan: TtPlan? = null,
        nowMillis: Long = System.currentTimeMillis(),
        settings: ReminderSettings = ReminderSettings.DEFAULT,
        zone: ZoneId = ZoneId.systemDefault(),
        notices: NoticesData? = null,
        handledNoticeIds: Set<String> = emptySet(),
    ): ReminderPlan {
        if (!settings.enabled) return ReminderPlan(emptyList())
        val events = buildList {
            addAll(planHomework(rows, doneIds, nowMillis, settings, zone))
            addAll(planCourse(plan, nowMillis, settings, zone))
            addAll(planNotices(notices, handledNoticeIds, nowMillis, settings, zone))
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
