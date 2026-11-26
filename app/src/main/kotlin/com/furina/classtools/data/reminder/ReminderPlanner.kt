package com.furina.classtools.data.reminder

import com.furina.classtools.data.homework.HomeworkDueCalculator
import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.notices.NoticeStatusMachine
import com.furina.classtools.data.notices.NoticesData
import com.furina.classtools.data.vote.PollResultVisibility
import com.furina.classtools.data.vote.PollSummary
import com.furina.classtools.data.timetable.TtPlan
import com.furina.classtools.features.water.WaterCalculator
import com.furina.classtools.features.water.WaterMonthStats
import com.furina.classtools.features.water.WaterRoomState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 通知规划器（纯函数，无 Android 依赖，可单测）。
 *
 * 口径对齐鸿蒙端 `features/notify/NotifyPlanner.ets`（2026-11-21）：
 * 1. **新增类**（新作业 / 新通知 / 新投票）：与上次快照比较，首次运行只建立基线不推送；
 * 2. **截止类**（作业截止 / 活动截止）：只在未来 7 天内排程，触发时间 = 目标时间 − 统一提前时间；
 * 3. **上课**：下一节课开始前 N 分钟；
 * 4. **开抢**：报名开始前 5 分钟；
 * 5. **投票结果**：投票结束（或截止后 1 分钟）提醒查看结果；
 * 6. **水费**：用本机水量记录计算，只有真正产生用量/水费才提醒。
 *
 * 安卓额外保留「每日汇总」（[ReminderSettings.digestEnabled]，默认关闭）：
 * 每天固定时间汇总未来 48 小时截止的作业/通知，与单条截止提醒互斥（汇总已覆盖的不再单独提醒）。
 *
 * 还会过滤：已完成（marks）、无截止/非法截止、已截止、长期课（无下一节）、安静时段（可选）、
 * horizon 外（未来 7 天）。
 */
object ReminderPlanner {

    /** 每日汇总的覆盖窗口：汇总时间起 48 小时（即「明天/后天截止」）。 */
    const val HOMEWORK_DIGEST_WINDOW_MS: Long = 48L * 60 * 60 * 1000

    /** 已进入提前窗口时，安排到下次 Worker 轮询附近立即提醒。 */
    const val IMMEDIATE_DELAY_MS: Long = 30L * 1000

    /** 规划器内部最大规划窗口：7 天（未来更远的截止由后续轮次再规划）。 */
    const val PLANNING_HORIZON_MS: Long = 7L * 24 * 60 * 60 * 1000

    /** 活动开抢提前量：报名开始前 5 分钟（与鸿蒙端一致）。 */
    const val SIGNUP_LEAD_MS: Long = 5L * 60 * 1000

    /** 投票结束后多久提醒查看结果（避免与服务端结算竞争）。 */
    const val VOTE_RESULT_DELAY_MS: Long = 60L * 1000

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

    // ================================================================ 作业

    /** 作业截止提醒（可选每日汇总 + 截止前统一提前）。 */
    fun planHomework(
        rows: List<HomeworkRow>,
        doneIds: Set<String> = emptySet(),
        nowMillis: Long = System.currentTimeMillis(),
        settings: ReminderSettings = ReminderSettings.DEFAULT,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ReminderEvent> {
        if (!settings.enabled) return emptyList()
        if (!settings.homeworkDueEnabled && !settings.digestEnabled) return emptyList()
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
        // 汇总覆盖「明天 + 后天」整个自然日（含后天 23:59），与文案「明天/后天截止」一致。
        val digestWindowEnd = digestWindowEnd(digestAt, zone)
        val digestItems = if (settings.digestEnabled) {
            deadlines.filter { it.dueAtMillis in (digestAt + 1)..digestWindowEnd }
        } else {
            emptyList()
        }
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

        val leadMillis = settings.leadMillis
        if (!settings.homeworkDueEnabled) return events
        deadlines.forEach { deadline ->
            // 当日汇总已覆盖的作业不再单独提醒（避免一晚两条）
            if (deadline in digestItems) return@forEach
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

    /** 新作业提醒（首轮只建基线；已完成的作业不提醒）。 */
    fun planHomeworkNew(
        rows: List<HomeworkRow>,
        doneIds: Set<String> = emptySet(),
        nowMillis: Long = System.currentTimeMillis(),
        settings: ReminderSettings = ReminderSettings.DEFAULT,
        snapshot: ReminderSnapshot = ReminderSnapshot(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ReminderEvent> {
        if (!settings.enabled || !settings.homeworkNewEnabled) return emptyList()
        if (!snapshot.initialized) return emptyList()
        val seen = snapshot.hwIds.toHashSet()
        val trigger = nowMillis + IMMEDIATE_DELAY_MS
        if (settings.isQuiet(trigger, zone)) return emptyList()
        return rows.mapNotNull { row ->
            val id = row.id.trim()
            if (id.isEmpty() || id in doneIds || id in seen) return@mapNotNull null
            val due = HomeworkDueCalculator.parseDue(row.date, row.time, zone)
            val name = row.subject.trim()
                .ifBlank { row.content.lineSequence().firstOrNull().orEmpty().trim() }
                .ifBlank { "作业" }
            ReminderEvent(
                kind = ReminderKind.HOMEWORK_NEW,
                key = "hw-new:$id",
                triggerAtMillis = trigger,
                title = "新作业",
                body = name + (due?.let { " · 截止 " + formatAt(it, zone) } ?: ""),
                toolId = ReminderKind.HOMEWORK_NEW.toolId,
            )
        }
    }

    // ================================================================ 上课

    /** 课表提醒（下一节）。 */
    fun planCourse(
        plan: TtPlan?,
        nowMillis: Long = System.currentTimeMillis(),
        settings: ReminderSettings = ReminderSettings.DEFAULT,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ReminderEvent> {
        if (!settings.enabled || !settings.classRemindEnabled) return emptyList()
        val next = plan?.next ?: return emptyList()
        if (!plan.hasData) return emptyList()
        if (next.startAt <= nowMillis) return emptyList()
        val leadMillis = if (next.afterShortBreak) settings.breakLeadMinutes * 60_000L else settings.classLeadMillis
        val rawTrigger = next.startAt - leadMillis
        val trigger = maxOf(rawTrigger, nowMillis)
        if (settings.isQuiet(trigger, zone)) return emptyList()
        return listOf(
            ReminderEvent(
                kind = ReminderKind.COURSE_SOON,
                key = "class:${next.startAt}",
                triggerAtMillis = trigger,
                title = "上课提醒 · ${next.name}",
                body = buildString {
                    append(next.startText)
                    if (next.endText.isNotBlank()) {
                        append("–")
                        append(next.endText)
                    }
                    append(" · ")
                    append(next.name)
                    if (next.room.isNotBlank()) {
                        append(" · ")
                        append(next.room)
                    }
                },
                toolId = ReminderKind.COURSE_SOON.toolId,
                course = CourseReminderInfo(next.name, next.fullRoom.ifBlank { next.room }, next.startAt, next.endAt),
            ),
        )
    }

    // ================================================================ 通知

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
        /** 报名开始时间（0 = 无）。 */
        val signupStartMillis: Long = 0L,
        val senderName: String = "",
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
                signupStartMillis = NoticeStatusMachine.signupOf(group, zone).start ?: 0L,
                senderName = group.senderName.trim(),
            )
        }
    }

    /** 新通知提醒（首轮只建基线；已处理/隐藏不提醒）。 */
    fun planNoticesNew(
        notices: NoticesData?,
        handledIds: Set<String> = emptySet(),
        nowMillis: Long = System.currentTimeMillis(),
        settings: ReminderSettings = ReminderSettings.DEFAULT,
        snapshot: ReminderSnapshot = ReminderSnapshot(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ReminderEvent> {
        if (!settings.enabled || !settings.noticeNewEnabled) return emptyList()
        if (!snapshot.initialized) return emptyList()
        if (notices == null) return emptyList()
        val trigger = nowMillis + IMMEDIATE_DELAY_MS
        if (settings.isQuiet(trigger, zone)) return emptyList()
        val seen = (snapshot.ntIds + snapshot.manualNoticeIds.orEmpty() + handledIds).toMutableSet()
        val events = mutableListOf<ReminderEvent>()
        fun addNotice(rawId: String, sender: String, title: String) {
            val id = rawId.trim()
            if (id.isEmpty() || !seen.add(id)) return
            val name = title.trim().ifBlank { "班级通知" }
            events += ReminderEvent(
                kind = ReminderKind.NOTICE_NEW,
                key = "nt-new:$id",
                triggerAtMillis = trigger,
                title = "新通知",
                body = listOf(sender.trim(), name).filter(String::isNotEmpty).joinToString(" · "),
                toolId = ReminderKind.NOTICE_NEW.toolId,
            )
        }
        notices.auto.forEach { entry ->
            if (!entry.hidden) addNotice(entry.id, entry.group.senderName, entry.title)
        }
        if (snapshot.manualNoticeIds != null) {
            notices.manual.forEach { notice ->
                if (!notice.row.hidden && !notice.row.archived) {
                    addNotice(notice.id, notice.row.author, notice.row.title)
                }
            }
        }
        return events
    }

    /** 活动开抢提醒（报名开始前 5 分钟）。 */
    fun planNoticeSignup(
        notices: NoticesData?,
        handledIds: Set<String> = emptySet(),
        nowMillis: Long = System.currentTimeMillis(),
        settings: ReminderSettings = ReminderSettings.DEFAULT,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ReminderEvent> {
        if (!settings.enabled || !settings.noticeSignupEnabled) return emptyList()
        val targets = noticeTargets(notices, handledIds, nowMillis, zone)
        return targets.mapNotNull { target ->
            val start = target.signupStartMillis
            if (start <= nowMillis + 60_000L) return@mapNotNull null
            if (start - nowMillis > PLANNING_HORIZON_MS) return@mapNotNull null
            val trigger = maxOf(nowMillis + IMMEDIATE_DELAY_MS, start - SIGNUP_LEAD_MS)
            if (settings.isQuiet(trigger, zone)) return@mapNotNull null
            ReminderEvent(
                kind = ReminderKind.NOTICE_SIGNUP,
                key = "nt-signup:${target.id}",
                triggerAtMillis = trigger,
                title = "活动开抢提醒",
                body = target.displayName + " · " + formatAt(start, zone) + " 开始报名",
                toolId = ReminderKind.NOTICE_SIGNUP.toolId,
            )
        }
    }

    /** 通知截止/临近提醒（可选每日汇总 + 统一提前）。 */
    fun planNotices(
        notices: NoticesData?,
        handledIds: Set<String> = emptySet(),
        nowMillis: Long = System.currentTimeMillis(),
        settings: ReminderSettings = ReminderSettings.DEFAULT,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ReminderEvent> {
        if (!settings.enabled) return emptyList()
        if (!settings.noticeDeadlineEnabled && !settings.digestEnabled) return emptyList()
        val targets = noticeTargets(notices, handledIds, nowMillis, zone)
        if (targets.isEmpty()) return emptyList()

        val events = mutableListOf<ReminderEvent>()
        val digestAt = nextDigestAt(nowMillis, settings, zone)
        val digestWindowEnd = digestWindowEnd(digestAt, zone)
        val digestItems = if (settings.digestEnabled) {
            targets.filter { it.targetAtMillis in (digestAt + 1)..digestWindowEnd }
        } else {
            emptyList()
        }
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

        val leadMillis = settings.leadMillis
        if (!settings.noticeDeadlineEnabled) return events
        targets.forEach { target ->
            // 已进入当日汇总窗口的通知由汇总覆盖，避免当晚两条提醒
            if (target in digestItems) return@forEach
            val rawTrigger = target.targetAtMillis - leadMillis
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

    // ================================================================ 投票

    /** 投票提醒（新投票 + 投票结果可查看）。 */
    fun planVotes(
        polls: List<PollSummary>,
        nowMillis: Long = System.currentTimeMillis(),
        settings: ReminderSettings = ReminderSettings.DEFAULT,
        snapshot: ReminderSnapshot = ReminderSnapshot(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ReminderEvent> {
        if (!settings.enabled) return emptyList()
        if (!settings.voteNewEnabled && !settings.voteResultEnabled) return emptyList()
        val firstRun = !snapshot.initialized
        val seenPolls = snapshot.pollIds.toHashSet()
        val seenClosed = snapshot.closedPollIds.toHashSet()
        val events = mutableListOf<ReminderEvent>()
        polls.forEach { poll ->
            val id = poll.id.trim()
            if (id.isEmpty()) return@forEach
            // 新投票：只有非首轮且不在基线里的才提醒
            if (settings.voteNewEnabled && !firstRun && id !in seenPolls) {
                val trigger = nowMillis + IMMEDIATE_DELAY_MS
                if (!settings.isQuiet(trigger, zone)) {
                    events += ReminderEvent(
                        kind = ReminderKind.VOTE_NEW,
                        key = "vote-new:$id",
                        triggerAtMillis = trigger,
                        title = "新投票",
                        body = poll.title.ifBlank { "班级投票" },
                        toolId = ReminderKind.VOTE_NEW.toolId,
                    )
                }
            }
            if (!settings.voteResultEnabled) return@forEach
            if (poll.closed && !firstRun && id !in seenClosed) {
                val trigger = nowMillis + IMMEDIATE_DELAY_MS
                if (!settings.isQuiet(trigger, zone)) {
                    events += ReminderEvent(
                        kind = ReminderKind.VOTE_RESULT,
                        key = "vote-result:$id",
                        triggerAtMillis = trigger,
                        title = "投票结果可查看",
                        body = poll.title.ifBlank { "班级投票" },
                        toolId = ReminderKind.VOTE_RESULT.toolId,
                    )
                }
                return@forEach
            }
            // 未结束且「截止后可见结果」：截止后 1 分钟提醒一次
            if (!poll.closed && poll.deadline > nowMillis + 60_000L &&
                poll.deadline - nowMillis <= PLANNING_HORIZON_MS &&
                poll.settings.resultVisibility == PollResultVisibility.AFTER_CLOSE
            ) {
                val trigger = maxOf(nowMillis + IMMEDIATE_DELAY_MS, poll.deadline + VOTE_RESULT_DELAY_MS)
                if (!settings.isQuiet(trigger, zone)) {
                    events += ReminderEvent(
                        kind = ReminderKind.VOTE_RESULT,
                        key = "vote-result:$id",
                        triggerAtMillis = trigger,
                        title = "投票结果可查看",
                        body = poll.title.ifBlank { "班级投票" },
                        toolId = ReminderKind.VOTE_RESULT.toolId,
                    )
                }
            }
        }
        return events
    }

    // ================================================================ 水费

    /** 水费扣除提醒（本机水量记录产生用量/水费时；首轮只建基线）。 */
    fun planWater(
        roomKey: String,
        room: WaterRoomState?,
        nowMillis: Long = System.currentTimeMillis(),
        settings: ReminderSettings = ReminderSettings.DEFAULT,
        snapshot: ReminderSnapshot = ReminderSnapshot(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ReminderEvent> {
        if (!settings.enabled || !settings.waterDeductEnabled) return emptyList()
        if (room == null || roomKey.isBlank()) return emptyList()
        val latest = room.records.lastOrNull() ?: return emptyList()
        val fingerprint = waterFingerprint(roomKey, latest.month, latest.reading)
        if (!snapshot.initialized || snapshot.waterKey == fingerprint) return emptyList()
        val stats: WaterMonthStats = WaterCalculator.stats(room, latest.month, nowMillis, zone)
        val charge = stats.charge
        val usage = charge.usage ?: return emptyList()
        val fee = charge.fee ?: return emptyList()
        if (usage <= 0.0 || fee <= 0.0) return emptyList()
        val trigger = nowMillis + IMMEDIATE_DELAY_MS
        if (settings.isQuiet(trigger, zone)) return emptyList()
        return listOf(
            ReminderEvent(
                kind = ReminderKind.WATER_DEDUCT,
                key = "water:$fingerprint",
                triggerAtMillis = trigger,
                title = "水费扣除提醒",
                body = buildString {
                    append(latest.month)
                    append(" · 用量 ")
                    append(WaterCalculator.fmtReading(usage))
                    append(" 吨 · 水费 ¥")
                    append(WaterCalculator.fmtMoney(fee))
                    append("（扣电 ")
                    append(WaterCalculator.fmtDegree(charge.degree))
                    append(" 度）")
                },
                toolId = ReminderKind.WATER_DEDUCT.toolId,
            ),
        )
    }

    /** 水费提醒指纹：`<roomverify>|<month>|<reading>`（同一条抄表只提醒一次）。 */
    fun waterFingerprint(roomKey: String, month: String, reading: Double): String =
        "$roomKey|$month|${String.format(Locale.US, "%.2f", reading)}"

    // ================================================================ 合并规划

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
        snapshot: ReminderSnapshot = ReminderSnapshot(),
        polls: List<PollSummary> = emptyList(),
        waterRoomKey: String = "",
        waterRoom: WaterRoomState? = null,
    ): ReminderPlan {
        if (!settings.enabled) return ReminderPlan(emptyList())
        val events = buildList {
            addAll(planHomeworkNew(rows, doneIds, nowMillis, settings, snapshot, zone))
            addAll(planHomework(rows, doneIds, nowMillis, settings, zone))
            addAll(planCourse(plan, nowMillis, settings, zone))
            addAll(planNoticesNew(notices, handledNoticeIds, nowMillis, settings, snapshot, zone))
            addAll(planNoticeSignup(notices, handledNoticeIds, nowMillis, settings, zone))
            addAll(planNotices(notices, handledNoticeIds, nowMillis, settings, zone))
            addAll(planVotes(polls, nowMillis, settings, snapshot, zone))
            addAll(planWater(waterRoomKey, waterRoom, nowMillis, settings, snapshot, zone))
        }
            .filter { it.triggerAtMillis <= nowMillis + PLANNING_HORIZON_MS }
            .sortedWith(compareBy({ it.triggerAtMillis }, { it.key }))
            .distinctBy { it.key }
        return ReminderPlan(events)
    }

    /** 更新快照（保留最近 [ReminderSnapshot.MAX_IDS] 条 id，避免无限增长）。 */
    fun nextSnapshot(
        rows: List<HomeworkRow> = emptyList(),
        notices: NoticesData? = null,
        polls: List<PollSummary> = emptyList(),
        waterRoomKey: String = "",
        waterRoom: WaterRoomState? = null,
        previous: ReminderSnapshot = ReminderSnapshot(),
    ): ReminderSnapshot {
        val latest = waterRoom?.records?.lastOrNull()
        return ReminderSnapshot(
            initialized = true,
            hwIds = mergeIds(previous.hwIds, rows.mapNotNull { it.id.trim().takeIf { id -> id.isNotEmpty() } }),
            ntIds = mergeIds(
                previous.ntIds,
                notices?.auto.orEmpty().mapNotNull { it.id.trim().takeIf { id -> id.isNotEmpty() } },
            ),
            manualNoticeIds = notices?.let { data ->
                val ids = data.manual.asReversed().map { it.id.trim() }.filter(String::isNotEmpty)
                mergeIds(previous.manualNoticeIds.orEmpty(), ids)
            } ?: previous.manualNoticeIds,
            pollIds = mergeIds(previous.pollIds, polls.mapNotNull { it.id.trim().takeIf { id -> id.isNotEmpty() } }),
            closedPollIds = mergeIds(
                previous.closedPollIds,
                polls.filter { it.closed }.mapNotNull { it.id.trim().takeIf { id -> id.isNotEmpty() } },
            ),
            waterKey = if (waterRoomKey.isNotBlank() && latest != null) {
                waterFingerprint(waterRoomKey, latest.month, latest.reading)
            } else {
                previous.waterKey
            },
        )
    }

    private fun mergeIds(oldIds: List<String>, newIds: List<String>): List<String> {
        val out = LinkedHashSet<String>()
        out.addAll(oldIds)
        out.addAll(newIds)
        return if (out.size > ReminderSnapshot.MAX_IDS) {
            out.toList().takeLast(ReminderSnapshot.MAX_IDS)
        } else {
            out.toList()
        }
    }

    /** Worker 用：仅保留 `[now-grace, now+horizon]` 内到点的事件。 */
    fun filterDue(
        events: List<ReminderEvent>,
        nowMillis: Long,
        horizonMillis: Long = ReminderPlan.DEFAULT_HORIZON_MS,
        graceMillis: Long = ReminderPlan.DEFAULT_GRACE_MS,
    ): List<ReminderEvent> = events.filter { event ->
        if (event.kind == ReminderKind.COURSE_SOON) {
            event.triggerAtMillis <= nowMillis && (event.courseStartAtMillis ?: 0L) > nowMillis
        } else {
            event.triggerAtMillis in (nowMillis - graceMillis)..(nowMillis + horizonMillis)
        }
    }

    /** 汇总覆盖窗口结束：汇总日 + 2 天（后天）的 23:59:59.999。 */
    fun digestWindowEnd(digestAtMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val digestDate = Instant.ofEpochMilli(digestAtMillis).atZone(zone).toLocalDate()
        return digestDate.plusDays(2).atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
    }

    /** 下一次汇总时间：今天的 hh:mm，若已过则明天同一时间。 */
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

    /** 汇总正文：`2 项作业即将截止：数学 11-09 23:59；英语 11-10 18:00`（最多 5 项 + 省略）。 */
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
}
