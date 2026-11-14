package com.furina.classtools.data.notices

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 状态机输入：组 + 已处理（marks ntdone）+ 排序时间（行 ts）。 */
data class NoticeDigest(
    val group: NoticeGroupDto,
    val done: Boolean = false,
    val ts: Long = 0L,
)

/** 报名窗口（可只填一端）。 */
data class SignupWindow(val start: Long?, val end: Long?)

/**
 * 通知状态机（纯函数，Android 移植版）。
 *
 * 唯一真源：`docs/api/NOTICES.md` §3（网页 `anStatus` 简化版 + 小程序 `notice.js` 完整版口径）
 * + `web/通知三端_长期通知与时间显示_契约_20261114.md`（长期通知期次 / 时间显示 §3 / 统计 §2.4）：
 * - **时间优先级**：报名窗口 > 真实截止（extensions 最后一次延期优先）> 活动/事件 > 无时间；
 * - `expireAt` 只用于「活动类自动归档」，**绝不当截止时间展示**；
 * - 有效的 `recurrence`（长期安排）存在时**永不按 deadline 判过期**；`recurrence.until` 到期（或下一次发生
 *   时间已超出 until）→ 状态归「长期安排已结束」（已过期区），不再产生期次（§1.1/§1.3）；
 * - 期次：`next`（严格 > now）在 `[next-24h, next)` 窗口内 → [occurrenceOf] 返回期次（key=`<gid>@occ:<yyyyMMdd>`）；
 * - 时间展示 `fmtWhen`：过期/远期 → `M月D日（相对周） HH:mm`；<1h→`N 分钟后`；<24h→`N 小时后`；
 *   <7d→`N 天后`（倒计时保持相对；绝对日期按 §3 统一格式）；
 * - 动作文案精简（≤6 字，`报名已截止→报名截止` 等映射）。
 */
object NoticeStatusMachine {

    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 60 * MINUTE_MS
    private const val DAY_MS = 24 * HOUR_MS
    private const val WEEK_MS = 7 * DAY_MS

    /** 期次提前展示窗口（契约 §1.3：发生前 24h 进入主列表）。 */
    private const val OCCURRENCE_LEAD_MS = DAY_MS

    /** recurrence 合法 freq（契约 §1.1）。 */
    private val RECUR_FREQS = setOf("daily", "weekly", "monthly_day", "monthly_weeks")

    /** recurrence.time 合法形式 `HH:MM`（24h）。 */
    private val RECUR_TIME = Regex("^([01]?\\d|2[0-3]):[0-5]\\d$")

    private val mmdd: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd", Locale.CHINA)
    private val mmddhhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)
    private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    private val isoDate: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-M-d", Locale.CHINA)

    private val ACTION_MAP = mapOf(
        "报送姓名截止" to "报送截止",
        "集合点名" to "点名",
        "活动开始" to "开始",
        "签到开始" to "签到",
        "签到签退" to "签到",
        "比赛开始" to "比赛",
        "考试开始" to "考试",
        "报名已截止" to "报名截止",
    )

    // ------------------------------------------------------------ 时间解析/格式化

    /** 宽松时间解析：ISO（带/不带偏移）→ 本地日期时间 → `yyyy-MM-dd HH:mm[:ss]` → `MM-dd HH:mm` → `MM-dd`。 */
    fun parseTs(raw: String?, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        try {
            return Instant.parse(text).toEpochMilli()
        } catch (_: Throwable) {
            // next
        }
        try {
            return OffsetDateTime.parse(text).toInstant().toEpochMilli()
        } catch (_: Throwable) {
            // next
        }
        try {
            return LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
        } catch (_: Throwable) {
            // next
        }
        try {
            return LocalDate.parse(text).atStartOfDay(zone).toInstant().toEpochMilli()
        } catch (_: Throwable) {
            // next
        }
        val year = LocalDate.now(zone).year
        try {
            val ldt = LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA))
            return ldt.atZone(zone).toInstant().toEpochMilli()
        } catch (_: Throwable) {
            // next
        }
        try {
            val ldt = LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA))
            return ldt.atZone(zone).toInstant().toEpochMilli()
        } catch (_: Throwable) {
            // next
        }
        try {
            val ldt = LocalDateTime.parse("$year-$text", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA))
            return ldt.atZone(zone).toInstant().toEpochMilli()
        } catch (_: Throwable) {
            // next
        }
        try {
            val date = LocalDate.parse("$year-$text", DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA))
            return date.atStartOfDay(zone).toInstant().toEpochMilli()
        } catch (_: Throwable) {
            return null
        }
    }

    /** `MM-dd HH:mm`。 */
    fun fmt(ts: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        mmddhhmm.format(Instant.ofEpochMilli(ts).atZone(zone))

    /**
     * 状态机/卡片 chip 用的人类可读时间（契约 §3）：
     * - 剩余 <1h / <24h / <7d → 相对倒计时（`N 分钟后`/`N 小时后`/`N 天后`，保持相对）；
     * - 其余（含已过期）→ 绝对 `M月D日（相对周） HH:mm`。
     */
    fun fmtWhen(ts: Long, nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        val left = ts - nowMillis
        if (left >= 0) {
            if (left < HOUR_MS) {
                val minutes = maxOf(1L, (left + MINUTE_MS - 1) / MINUTE_MS)
                return "$minutes 分钟后"
            }
            if (left < DAY_MS) {
                val hours = maxOf(1L, (left + HOUR_MS - 1) / HOUR_MS)
                return "$hours 小时后"
            }
            if (left < WEEK_MS) {
                val days = maxOf(1L, (left + DAY_MS - 1) / DAY_MS)
                return "$days 天后"
            }
        }
        return absoluteWhenText(ts, nowMillis, zone)
    }

    /** 绝对时间文案：`M月D日（相对周） HH:mm`（§3 统一格式）。 */
    fun absoluteWhenText(ts: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val zoned = Instant.ofEpochMilli(ts).atZone(zone)
        return "${fmtDateWeek(ts, nowMillis, zone)} ${hhmm.format(zoned)}"
    }

    /** 动作精简（≤6 字；长文案映射/截断）。 */
    fun conciseAction(action: String?): String {
        val text = action?.trim().orEmpty()
        if (text.isEmpty()) return ""
        ACTION_MAP[text]?.let { return it }
        if (text.length <= 6) return text
        if (text.contains("截止")) {
            val head = text.substringBefore("截止").takeLast(2)
            return head + "截止"
        }
        return text.take(6)
    }

    // ------------------------------------------------------------ 字段提取

    fun signupOf(group: NoticeGroupDto, zone: ZoneId = ZoneId.systemDefault()): SignupWindow =
        SignupWindow(
            start = parseTs(group.signupStart, zone),
            end = parseTs(group.signupEnd, zone),
        )

    /** 真实截止：extensions 最后一次延期的 deadline 优先，其次 deadline。 */
    fun deadlineOf(group: NoticeGroupDto, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val lastExtension = group.extensions.lastOrNull()
        if (lastExtension != null) {
            parseTs(lastExtension.deadline, zone)?.let { return it }
        }
        return parseTs(group.deadline, zone)
    }

    fun hasExtension(group: NoticeGroupDto): Boolean =
        group.extensions.lastOrNull()?.deadline?.isNotBlank() == true

    fun eventAtOf(group: NoticeGroupDto, zone: ZoneId = ZoneId.systemDefault()): Long? =
        parseTs(group.eventAt, zone)

    fun archiveAtOf(group: NoticeGroupDto, zone: ZoneId = ZoneId.systemDefault()): Long? =
        parseTs(group.expireAt, zone)

    /**
     * 是否有任何时间锚点（`deadline`/`extensions` 最后一条/`eventAt`/`signupStart`/`signupEnd`）；
     * 无时间锚点且非过期、无 recurrence → 「资料 / 无时间」（契约 §1.2），不参与待处理统计。
     */
    fun hasTimeAnchor(group: NoticeGroupDto, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        deadlineOf(group, zone) != null ||
            parseTs(group.eventAt, zone) != null ||
            parseTs(group.signupStart, zone) != null ||
            parseTs(group.signupEnd, zone) != null

    // ------------------------------------------------------------ 主显示时间 / 状态

    fun whenInfo(
        group: NoticeGroupDto,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): NoticeWhen? {
        val signup = signupOf(group, zone)
        val start = signup.start
        val end = signup.end
        if (start != null || end != null) {
            if (start != null && nowMillis < start) return NoticeWhen(start, "报名开始")
            if (end != null) return NoticeWhen(end, "报名截止")
            // 只有 start 且已开始：退回截止/活动时间
        }
        deadlineOf(group, zone)?.let { deadline ->
            return NoticeWhen(deadline, conciseAction(group.deadlineAction.ifBlank { "截止" }))
        }
        eventAtOf(group, zone)?.let { event ->
            return NoticeWhen(event, conciseAction(group.eventAction.ifBlank { "开始" }))
        }
        return null
    }

    fun whenOf(
        group: NoticeGroupDto,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? = whenInfo(group, nowMillis, zone)?.atMillis

    fun statusOf(
        group: NoticeGroupDto,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): NoticeStatus {
        // 长期安排：有效 recurrence 且进行中 → 「长期安排」；until 到期 / 无下一次 → 已结束（§1.1/§1.3）。
        val recurrence = effectiveRecurrenceOf(group)
        if (recurrence != null) {
            if (!recurOngoingOf(group, nowMillis, zone)) {
                return NoticeStatus(
                    key = "recur_ended",
                    label = "长期安排已结束",
                    cls = "expired",
                    expired = true,
                    bucket = NoticeBucket.EXPIRED,
                )
            }
            val label = recurLabelOf(group).ifBlank { "长期安排" }
            return NoticeStatus("recurring", "长期安排 · $label", "recurring", false, NoticeBucket.RECURRING)
        }

        val real = deadlineOf(group, zone)
        val event = eventAtOf(group, zone)
        val archive = archiveAtOf(group, zone)

        if (real != null && nowMillis > real && group.alert) {
            return NoticeStatus("alert", "已截止 · 有新名单", "alert", false, NoticeBucket.ALERT)
        }

        val signup = signupOf(group, zone)
        val start = signup.start
        val end = signup.end
        if (start != null || end != null) {
            if (start != null && nowMillis < start) {
                return bucketStatus(start, "报名开始", nowMillis, extension = false)
            }
            if (end != null) {
                if (nowMillis > end) {
                    return NoticeStatus(
                        key = "expired",
                        label = "${fmtWhen(end, nowMillis, zone)} 报名截止",
                        cls = "expired",
                        expired = true,
                        bucket = NoticeBucket.EXPIRED,
                    )
                }
                return bucketStatus(end, "报名截止", nowMillis, extension = false)
            }
        }

        if (real != null) {
            val action = conciseAction(group.deadlineAction.ifBlank { "截止" })
            val extension = hasExtension(group)
            if (nowMillis > real) {
                return NoticeStatus(
                    key = if (extension) "extended_past" else "expired",
                    label = "${fmtWhen(real, nowMillis, zone)} $action",
                    cls = "expired",
                    expired = true,
                    bucket = NoticeBucket.EXPIRED,
                )
            }
            return bucketStatus(real, action, nowMillis, extension)
        }

        if (event != null) {
            val action = conciseAction(group.eventAction.ifBlank { "开始" })
            val endAt = archive ?: event
            if (nowMillis > endAt) {
                return NoticeStatus(
                    key = "expired",
                    label = "${fmtWhen(event, nowMillis, zone)} $action",
                    cls = "expired",
                    expired = true,
                    bucket = NoticeBucket.EXPIRED,
                )
            }
            return NoticeStatus(
                key = "active",
                label = "${fmtWhen(event, nowMillis, zone)} $action",
                cls = "active",
                expired = false,
                bucket = NoticeBucket.ACTIVE,
            )
        }

        return NoticeStatus("none", "无时间", "none", false, NoticeBucket.NONE)
    }

    fun isExpired(
        group: NoticeGroupDto,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean = statusOf(group, nowMillis, zone).expired

    /** 是否「可处理」的通知（长期安排定义卡不算；契约 §2.4）。 */
    fun isActionable(group: NoticeGroupDto): Boolean =
        group.actionable && effectiveRecurrenceOf(group) == null

    private fun bucketStatus(ts: Long, action: String, nowMillis: Long, extension: Boolean, zone: ZoneId = ZoneId.systemDefault()): NoticeStatus {
        val label = "${fmtWhen(ts, nowMillis, zone)} $action"
        if (extension) return NoticeStatus("extended", label, "extended", false, NoticeBucket.EXTENDED)
        val left = ts - nowMillis
        return when {
            left < HOUR_MS -> NoticeStatus("urgent", label, "urgent", false, NoticeBucket.URGENT)
            left < DAY_MS -> NoticeStatus("today", label, "urgent", false, NoticeBucket.TODAY)
            left < 3 * DAY_MS -> NoticeStatus("soon", label, "active", false, NoticeBucket.SOON)
            else -> NoticeStatus("active", label, "active", false, NoticeBucket.ACTIVE)
        }
    }

    // ------------------------------------------------------------ 长期安排（recurrence）

    /** 周期 label（数据无 `label` 时的兜底；对齐小程序 `recurLabelOf`：每周五 19:00 / 每天 07:30）。 */
    fun recurLabelOf(group: NoticeGroupDto): String {
        val recur = group.recurrence ?: return ""
        if (recur.label.isNotBlank()) return recur.label
        return buildString {
            when (recur.freq) {
                "daily" -> append("每天")
                "weekly" -> recur.weekday?.let { append("每周${weekdayZh(it).removePrefix("周")}") }
                "monthly_day" -> recur.day?.let { append("每月 $it 日") }
                "monthly_weeks" -> {
                    val weeks = recur.weeks.joinToString("、")
                    val weekday = recur.weekday?.let { weekdayZh(it).removePrefix("周") } ?: "周"
                    append("每月第 $weeks 个周$weekday")
                }
                else -> append("长期安排")
            }
            if (recur.time.isNotBlank()) append(" ${recur.time}")
        }
    }

    /**
     * 下一次发生时间（本地计算；支持 daily / weekly / monthly_day / monthly_weeks）。
     * JS `weekday` 口径 0=周日 … 6=周六；monthly_day 只支持 1..28。
     * 非法 recurrence（freq/time 不合法，§1.1）→ null。
     */
    fun recurNextOf(
        group: NoticeGroupDto,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        val recur = effectiveRecurrenceOf(group) ?: return null
        val startAt = parseTs(recur.startAt, zone)
        val reference = maxOf(nowMillis, startAt ?: nowMillis)
        val now = Instant.ofEpochMilli(reference).atZone(zone)
        val today = now.toLocalDate()
        val time = if (recur.time.isBlank()) {
            LocalTime.MIDNIGHT
        } else {
            runCatching { LocalTime.parse(recur.time) }.getOrDefault(LocalTime.MIDNIGHT)
        }

        fun at(date: LocalDate): Long =
            LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()

        return when (recur.freq) {
            "daily" -> {
                var date = today
                var candidate = at(date)
                if (candidate <= nowMillis) {
                    date = date.plusDays(1)
                    candidate = at(date)
                }
                candidate
            }

            "weekly" -> {
                val weekday = recur.weekday ?: return null
                val target = dayOfWeekFromJs(weekday)
                var date = today
                repeat(62) {
                    val candidate = at(date)
                    if (date.dayOfWeek == target && candidate > nowMillis) return candidate
                    date = date.plusDays(1)
                }
                null
            }

            "monthly_day" -> {
                val day = recur.day ?: return null
                if (day !in 1..28) return null
                var year = today.year
                var month = today.monthValue
                repeat(3) {
                    val candidateDate = LocalDate.of(year, month, day)
                    val candidate = at(candidateDate)
                    if (candidate > nowMillis) return candidate
                    month += 1
                    if (month > 12) {
                        month = 1
                        year += 1
                    }
                }
                null
            }

            "monthly_weeks" -> {
                val weekday = recur.weekday ?: return null
                val target = dayOfWeekFromJs(weekday)
                val weeks = recur.weeks.filter { it in 1..5 }.ifEmpty { (1..5).toList() }
                var monthStart = today.withDayOfMonth(1)
                repeat(4) {
                    val first = monthStart.with(TemporalAdjusters.firstInMonth(target))
                    for (week in weeks) {
                        val candidateDate = first.plusWeeks((week - 1).toLong())
                        if (candidateDate.month != monthStart.month) continue
                        val candidate = at(candidateDate)
                        if (candidate > nowMillis) return candidate
                    }
                    monthStart = monthStart.plusMonths(1)
                }
                null
            }

            else -> null
        }
    }

    /** `下次 M月D日（周X）HH:mm`（契约 §3 时间格式）；无下次/非法 recurrence 则空串。 */
    fun recurNextTextOf(
        group: NoticeGroupDto,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val next = recurNextOf(group, nowMillis, zone) ?: return ""
        return "下次 ${fmtDateWeek(next, nowMillis, zone)}${timeSuffixOf(next, zone)}"
    }

    /** 期次状态胶囊文案：`本次 M月D日（周X）HH:mm`（契约 §2.3）。 */
    fun occurrenceLabelOf(
        occurrence: NoticeOccurrence,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = "本次 ${fmtDateWeek(occurrence.nextMillis, nowMillis, zone)}${timeSuffixOf(occurrence.nextMillis, zone)}"

    private fun timeSuffixOf(ts: Long, zone: ZoneId): String =
        " " + hhmm.format(Instant.ofEpochMilli(ts).atZone(zone))

    // ------------------------------------------------------------ 长期安排：until / 期次（契约 §1.1/§1.3）

    /** 有效 recurrence（freq 合法 + `time` 为 `HH:MM`；无时间的通知不得有 recurrence，§1.1）；非法 → null。 */
    fun effectiveRecurrenceOf(group: NoticeGroupDto): NoticeRecurrenceDto? {
        val recur = group.recurrence ?: return null
        if (recur.freq.trim().lowercase(Locale.ROOT) !in RECUR_FREQS) return null
        if (!RECUR_TIME.matches(recur.time.trim())) return null
        return recur
    }

    /** 是否长期安排（有效 recurrence；已结束状态由 [recurEndedOf] 判定）。 */
    fun isLongterm(group: NoticeGroupDto): Boolean = effectiveRecurrenceOf(group) != null

    /**
     * `recurrence.until` 解析（契约 §1.1）：
     * - `YYYY-MM-DD` → 当天 23:59:59.999（**含当天有效**）；
     * - ISO datetime（`...+08:00` / `Z` / 本地日期时间）→ 该瞬时；
     * - 空/非法 → null（= 无期限）。
     */
    fun recurUntilOf(group: NoticeGroupDto, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val raw = effectiveRecurrenceOf(group)?.until?.trim().orEmpty()
        if (raw.isEmpty()) return null
        // 先按纯日期解析（含当天有效；不能走 parseTs：那会得到当天 00:00）
        runCatching {
            val date = LocalDate.parse(raw)
            return date.atTime(23, 59, 59, 999_000_000).atZone(zone).toInstant().toEpochMilli()
        }
        return parseTs(raw, zone)
    }

    /**
     * 长期安排是否已结束（契约 §1.1/§1.3）：
     * - `now > until`（日期口径 = 当天 23:59:59.999）→ 结束；
     * - 下一次发生时间已超出 `until` → 结束（不再产生期次，含 until 当天之后的下一次）。
     */
    fun recurEndedOf(
        group: NoticeGroupDto,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (effectiveRecurrenceOf(group) == null) return false
        val until = recurUntilOf(group, zone) ?: return false
        if (nowMillis > until) return true
        val next = recurNextOf(group, nowMillis, zone) ?: return nowMillis > until
        return next > until
    }

    /** 期次 key：`<gid>@occ:<yyyyMMdd>`（next 的本地日期，§1.3）。 */
    fun occurrenceKeyOf(groupId: String, nextMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = Instant.ofEpochMilli(nextMillis).atZone(zone).toLocalDate()
        val ymd = String.format(Locale.ROOT, "%04d%02d%02d", date.year, date.monthValue, date.dayOfMonth)
        return "$groupId@occ:$ymd"
    }

    /**
     * 长期安排是否「进行中」（契约 §2.4；对齐网页 `recurOngoing`）：
     * 有效 recurrence + 未到 until + 能算出下一次发生。
     */
    fun recurOngoingOf(
        group: NoticeGroupDto,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (effectiveRecurrenceOf(group) == null) return false
        if (recurEndedOf(group, nowMillis, zone)) return false
        return recurNextOf(group, nowMillis, zone) != null
    }

    /**
     * 期次（§1.3）：进行中的长期安排 + `next` 严格 > now + `now ∈ [next-24h, next)` → 返回期次；
     * 已到 until / 不在窗口内 → null（窗口开始前不可见；已处理期次由 marks 判定后由 UI 过滤）。
     */
    fun occurrenceOf(
        group: NoticeGroupDto,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): NoticeOccurrence? {
        if (!recurOngoingOf(group, nowMillis, zone)) return null
        val next = recurNextOf(group, nowMillis, zone) ?: return null
        val windowStart = next - OCCURRENCE_LEAD_MS
        if (nowMillis < windowStart || nowMillis >= next) return null
        return NoticeOccurrence(
            nextMillis = next,
            windowStartMillis = windowStart,
            key = occurrenceKeyOf(group.id, next, zone),
        )
    }

    /** 进行中的长期安排清单（未到 until 且能算出下一次；按下次发生时间升序；契约 §2.2）。 */
    fun longtermDigests(
        digests: List<NoticeDigest>,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<NoticeDigest> = digests
        .filter { recurOngoingOf(it.group, nowMillis, zone) }
        .sortedWith(
            compareBy<NoticeDigest> { recurNextOf(it.group, nowMillis, zone) ?: Long.MAX_VALUE }
                .thenByDescending { it.ts },
        )

    /**
     * 是否计入「待处理」（契约 §2.4；工作台/统计共用）：
     * 未过期、`actionable !== false`、未已处理（普通通知按组 id、期次按期次 key），且
     * - 普通通知必须**有时间锚点**（资料 / 无时间不计）；
     * - 长期安排只有**处于期次窗口内且该期未处理**时计入（长期定义本身不计）。
     */
    fun isPending(
        group: NoticeGroupDto,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        done: Boolean = false,
        doneRef: (String) -> Boolean = { false },
    ): Boolean {
        if (effectiveRecurrenceOf(group) != null) {
            if (!recurOngoingOf(group, nowMillis, zone)) return false
            val occurrence = occurrenceOf(group, nowMillis, zone) ?: return false
            return !doneRef(occurrence.key)
        }
        if (statusOf(group, nowMillis, zone).expired) return false
        if (!group.actionable || done) return false
        // §2.4：必须有时间锚点（资料 / 无时间不计待处理；边界：只有报名开始且已过也算有时间锚点）
        return hasTimeAnchor(group, zone)
    }

    // ------------------------------------------------------------ 简报统计（契约 §2.4）/ 最紧急

    /**
     * §2.4 统计口径（顶部 5 卡 / 工作台同口径）：
     * - 待处理：未过期、非长期定义、非资料/无时间、未已处理、`actionable !== false`、且有时间锚点；
     *   期次卡也计入（已处理的期次不计；[doneRef] 同时用于普通通知 `gid` 与期次 key）；
     * - 24h / 7 天：上述待处理中主显示时间（期次按 next）剩余 ≤24h / ≤7 天；
     * - 已过期：`status.expired`（含「长期安排已结束」）；
     * - 长期通知：进行中的长期安排条数（until 已过 → 不计）；
     * - 最新发布：[extraPostedMillis]（人工通知等）+ 组发布时间（[NoticeDigest.ts]）中最新的。
     */
    fun statsOf(
        digests: List<NoticeDigest>,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        doneRef: (String) -> Boolean = { false },
        extraPostedMillis: List<Long> = emptyList(),
    ): NoticeStats {
        var pending = 0
        var urgent = 0
        var week = 0
        var expired = 0
        var longterm = 0
        var latestPosted = 0L
        for (digest in digests) {
            val group = digest.group
            if (digest.ts > latestPosted) latestPosted = digest.ts
            if (effectiveRecurrenceOf(group) != null) {
                if (!recurOngoingOf(group, nowMillis, zone)) {
                    expired += 1
                    continue
                }
                longterm += 1
                val occurrence = occurrenceOf(group, nowMillis, zone) ?: continue
                if (doneRef(occurrence.key)) continue
                pending += 1
                val left = occurrence.nextMillis - nowMillis
                if (left in 0..DAY_MS) urgent += 1
                if (left in 0..WEEK_MS) week += 1
                continue
            }
            if (statusOf(group, nowMillis, zone).expired) {
                expired += 1
                continue
            }
            if (digest.done || !group.actionable) continue
            // §2.4：有时间锚点才算待处理（资料 / 无时间不计）
            if (!hasTimeAnchor(group, zone)) continue
            pending += 1
            val whenAt = whenOf(group, nowMillis, zone) ?: continue
            val left = whenAt - nowMillis
            if (left in 0..DAY_MS) urgent += 1
            if (left in 0..WEEK_MS) week += 1
        }
        for (ts in extraPostedMillis) if (ts > latestPosted) latestPosted = ts
        return NoticeStats(
            pending = pending,
            urgent = urgent,
            week = week,
            expired = expired,
            longterm = longterm,
            latestPostedMillis = latestPosted,
            latestPostedText = latestPostedTextOf(latestPosted, nowMillis, zone),
        )
    }

    /**
     * 最新发布相对文案（契约 §2.1/§2.4，对齐网页 `relPubText`）：
     * 今天/昨天 → `今天 HH:mm`；更早（含跨年）→ `M月D日（相对周） HH:mm`；无发布 → `—`。
     */
    fun latestPostedTextOf(
        ts: Long,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        if (ts <= 0L) return "—"
        val at = Instant.ofEpochMilli(ts).atZone(zone)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val clock = hhmm.format(at)
        return when (at.toLocalDate()) {
            today -> "今天 $clock"
            today.minusDays(1) -> "昨天 $clock"
            else -> "${fmtDateWeek(ts, nowMillis, zone)} $clock"
        }
    }

    /** 最紧急一条（待处理口径；期次按 next 参与）：alert 优先 → 主显示时间升序（无时间最后）→ ts 降序。 */
    fun mostUrgent(
        digests: List<NoticeDigest>,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        doneRef: (String) -> Boolean = { false },
    ): NoticeDigest? {
        fun effectiveWhen(digest: NoticeDigest): Long? =
            if (effectiveRecurrenceOf(digest.group) != null) {
                occurrenceOf(digest.group, nowMillis, zone)?.nextMillis
            } else {
                whenOf(digest.group, nowMillis, zone)
            }
        val candidates = digests.filter { digest ->
            isPending(digest.group, nowMillis, zone, digest.done, doneRef) &&
                statusOf(digest.group, nowMillis, zone).bucket != NoticeBucket.EXPIRED
        }
        if (candidates.isEmpty()) return null
        return candidates.sortedWith(
            compareBy<NoticeDigest> { digest ->
                if (statusOf(digest.group, nowMillis, zone).bucket == NoticeBucket.ALERT) 0 else 1
            }.thenBy { digest ->
                effectiveWhen(digest) ?: Long.MAX_VALUE
            }.thenByDescending { it.ts },
        ).first()
    }

    // ------------------------------------------------------------ 相对日期替换（显示层）

    /**
     * 相对日期替换（对齐 mp `absRelDates` 核心语义；锚点 = 同卡第一条 item 的发送时间）：
     * - 今天/明天/后天/今早/明晚 → `MM-dd`；
     * - 本周X/这周X/下周X → 具体 `MM-dd`；裸「周X」→ 最近未来该星期；
     * - 裸「N 号/N 日」只在上下文出现时间/活动词时替换（避免「5 号楼」「42 号同学」误伤）；
     * - 裸时段（上午/中午/下午/晚上/凌晨）→ 前缀锚点日期；
     * - 绝对日期后的（周X）括号补充去掉。
     *
     * 解析失败/无锚点 → 原文返回，不抛错。
     */
    fun absRelDates(
        text: String,
        anchorTs: Long?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        if (text.isBlank() || anchorTs == null) return text
        val anchor = Instant.ofEpochMilli(anchorTs).atZone(zone).toLocalDate()
        var out = text

        // 1) 绝对日期后的括号星期补充去掉：11月5日（周三）→ 11月5日
        out = out.replace(Regex("[（(]\\s*(?:本|这|下)?周[一二三四五六日天]\\s*[）)]"), "")

        // 2) 今天/明天/后天（先处理复合词）
        out = out.replace(Regex("今天早上|今早"), "@@TODAY@@ 早上")
        out = out.replace(Regex("明天晚上|明晚"), "@@TOMORROW@@ 晚上")
        out = out.replace(Regex("今天|今日"), "@@TODAY@@")
        out = out.replace(Regex("明天|明日"), "@@TOMORROW@@")
        out = out.replace(Regex("后天"), "@@DAY2@@")
        out = out.replace("@@TODAY@@", dayText(anchor, 0))
        out = out.replace("@@TOMORROW@@", dayText(anchor, 1))
        out = out.replace("@@DAY2@@", dayText(anchor, 2))

        // 3) 本周X / 这周X / 下周X
        out = replaceRelativeWeekday(out, anchor, prefixPattern = "(?:本周|这周)", plusWeeks = 0)
        out = replaceRelativeWeekday(out, anchor, prefixPattern = "下周", plusWeeks = 1)

        // 4) 裸「周X」（前缀已替换为日期，不会再误伤）
        out = out.replace(Regex("周([一二三四五六日天])")) { match ->
            val target = weekdayIndex(match.groupValues[1])
            if (target < 0) {
                match.value
            } else {
                val anchorIndex = anchor.dayOfWeek.value - 1
                val delta = (target - anchorIndex + 7) % 7
                dayText(anchor, delta.toLong())
            }
        }

        // 5) 裸「N 号 / N 日」——仅在有语境词附近时替换
        val contextWords = listOf(
            "上午", "下午", "中午", "晚上", "凌晨", "早上", "点名", "比赛", "集合",
            "报名", "考试", "开始", "截止", "活动", "交", "发",
        )
        val current = out
        out = Regex("(?<!\\d)(\\d{1,2})\\s*[号日]").replace(current) { match ->
            val start = maxOf(0, match.range.first - 10)
            val end = minOf(current.length, match.range.last + 11)
            val context = current.substring(start, end)
            val day = match.groupValues[1].toIntOrNull()
            val hit = day != null && contextWords.any { it in context }
            if (!hit || day == null || day !in 1..31) {
                match.value
            } else {
                mmdd.format(anchor.withDayOfMonth(minOf(day, anchor.lengthOfMonth())))
            }
        }

        // 6) 裸时段：没有前置日期时补锚点日期
        for (part in listOf("上午", "中午", "下午", "晚上", "凌晨")) {
            val snapshot = out
            out = Regex(Regex.escape(part)).replace(snapshot) { match ->
                val start = match.range.first
                val prefixWindow = snapshot.substring(maxOf(0, start - 14), start)
                val hasDateBefore = Regex("\\d{1,2}\\s*[-月日号]|\\d{2}-\\d{2}").containsMatchIn(prefixWindow)
                if (hasDateBefore) part else "${mmdd.format(anchor)} $part"
            }
        }
        return out
    }

    /**
     * 锚点：同一卡片第一条带 `time` 的 item 的发送时间。
     *
     * 数据层解析会把 `group.items` 清空（大 base64 不进 UI 模型），此时用已解析的 [items] 元信息
     * （`NoticeItemMeta.Text.timeText` / 合并消息首条 timeText）兜底，保证「相对日期→绝对日期」替换仍生效。
     */
    fun anchorOf(
        group: NoticeGroupDto,
        items: List<NoticeItemMeta> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        for (item in group.items) {
            val time = (item["time"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            parseMsgTime(time, zone)?.let { return it }
        }
        for (item in items) {
            val raw = when (item) {
                is NoticeItemMeta.Text -> item.timeText
                is NoticeItemMeta.Merged -> item.messages.firstOrNull()?.timeText.orEmpty()
                else -> ""
            }
            parseMsgTime(raw, zone)?.let { return it }
        }
        return null
    }

    /** 解析 item.time（`MM-dd HH:mm` / `HH:mm` / ISO）。 */
    fun parseMsgTime(raw: String?, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        parseTs(text, zone)?.let { return it }
        runCatching {
            val time = LocalTime.parse(text)
            return LocalDate.now(zone).atTime(time).atZone(zone).toInstant().toEpochMilli()
        }
        runCatching {
            val year = LocalDate.now(zone).year
            val parsed = LocalDateTime.parse(
                "$year-$text",
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA),
            )
            return parsed.atZone(zone).toInstant().toEpochMilli()
        }
        return null
    }

    // ------------------------------------------------------------ 时间显示（契约 §3）：M月D日（相对周）
    //
    // 三端同构（对齐网页 `fmtDateText` / `_weekWordOf`）：今天/明天优先；**同一 ISO 周统一「本周X」**
    // （例：now=11-14 周六时 11-13 显示「本周五」而不是「昨天」）；「昨天」只用于跨周的一天前
    // （今天周一 → 昨天=上周日）；下一周「下周X」、上一周「上周X」、其它仅「周X」；年份非当年前缀 `YYYY年`；
    // 已是 `M月D日（…）` 的文本整体保护、不重复加括号；只作用于显示层，不写回数据（§3.2）。

    /** 已带括号的中文日期（整体保护，避免二次加括号）：`(YYYY年)?M月D日（…）`。 */
    private val PROTECTED_DATE_PAREN = Regex(
        "(?:20\\d{2}\\s*年\\s*)?\\d{1,2}\\s*月\\s*\\d{1,2}\\s*[日号]\\s*[（(][^）)\\n]{1,14}[）)]",
    )
    private val DATE_YMD_CN = Regex("(20\\d{2})\\s*年\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]")
    private val DATE_YMD_ISO = Regex("(20\\d{2})-(\\d{1,2})-(\\d{1,2})(?![0-9T])")
    private val DATE_MD_CN = Regex("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号](?!\\s*[（(])")
    private val DATE_MD_DASH = Regex("(\\d{1,2})-(\\d{1,2})")
    private val DATE_PLACEHOLDER = Regex("\\u0001(\\d+)\\u0002")

    /**
     * 时间显示归一（契约 §3.1，三端同一函数）：把文本里的日期 token 统一格式化为 `M月D日（相对）`。
     *
     * 识别 `MM-DD`、`M月D日`、`YYYY-MM-DD`、`YYYY年M月D日`（区间两端各自格式化）；
     * 已是 `M月D日（…）` 的整段保护不动；无年份 token 按渲染时年份解释（§3.2 数据仍存 MM-DD）；
     * 倒计时类文案（`N 小时后`）不经过本函数。
     */
    fun fmtDateText(text: String, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (text.isEmpty()) return text
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val slots = ArrayList<String>()
        var out = text

        // 0) 已带括号的中文日期整体保护（不重复加括号）
        out = PROTECTED_DATE_PAREN.replace(out) { match ->
            slots.add(match.value)
            "\u0001${slots.size - 1}\u0002"
        }
        fun convertDate(match: MatchResult, year: Int?, month: Int, day: Int): String {
            val date = runCatching {
                if (year != null) LocalDate.of(year, month, day) else LocalDate.of(today.year, month, day)
            }.getOrNull() ?: return match.value
            return formatDateLabel(date, today)
        }
        // 1) YYYY年M月D日
        out = DATE_YMD_CN.replace(out) { m ->
            convertDate(m, m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        // 2) YYYY-MM-DD（后面紧跟数字/T 的不是日期 token，如 ISO datetime 的时间部分）
        out = DATE_YMD_ISO.replace(out) { m ->
            convertDate(m, m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        // 3) M月D日（后面紧跟括号的是已保护/已有说明的日期）
        out = DATE_MD_CN.replace(out) { m ->
            convertDate(m, null, m.groupValues[1].toInt(), m.groupValues[2].toInt())
        }
        // 4) MM-DD（前后是数字/时间/量词时视为非日期：如 11:30、第 5-6 节、5 号楼）
        val snapshot = out
        out = DATE_MD_DASH.replace(snapshot) { m ->
            val before = snapshot.getOrNull(m.range.first - 1)
            val after = snapshot.getOrNull(m.range.last + 1)
            val guardBefore = before != null && (before.isDigit() || before in "：:节点号时分秒周月")
            val guardAfter = after != null && after in "节点号时分秒周"
            if (guardBefore || guardAfter) {
                m.value
            } else {
                convertDate(m, null, m.groupValues[1].toInt(), m.groupValues[2].toInt())
            }
        }
        // 5) 还原保护段
        out = DATE_PLACEHOLDER.replace(out) { m -> slots.getOrElse(m.groupValues[1].toInt()) { m.value } }
        return out
    }

    /** 单个时间戳 → `M月D日（相对周）`（跨年前缀 `YYYY年`；契约 §3）。 */
    fun fmtDateWeek(
        ts: Long,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val date = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
        return formatDateLabel(date, today)
    }

    /** `M月D日（相对）`（当年无年前缀；跨年为 `YYYY年M月D日（周X）`）。 */
    fun formatDateLabel(date: LocalDate, today: LocalDate): String {
        val prefix = if (date.year != today.year) "${date.year}年" else ""
        return "$prefix${date.monthValue}月${date.dayOfMonth}日（${relativeDayLabel(date, today)}）"
    }

    /**
     * 相对日/周标签（契约 §3.1 固定向量）：今天/明天 → 同一 ISO 周（周一起算）`本周X`（含昨天）→
     * `下周X` →「昨天」（仅跨周的一天前，如周一昨天=上周日）→ `上周X` → 其他仅 `周X`。
     */
    fun relativeDayLabel(date: LocalDate, today: LocalDate): String {
        val diff = ChronoUnit.DAYS.between(today, date)
        val weekday = weekdayCharZh(date.dayOfWeek)
        if (diff == 0L) return "今天"
        if (diff == 1L) return "明天"
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val dateMonday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeks = ChronoUnit.WEEKS.between(thisMonday, dateMonday)
        return when {
            weeks == 0L -> "本周$weekday"
            weeks == 1L -> "下周$weekday"
            diff == -1L -> "昨天"
            weeks == -1L -> "上周$weekday"
            else -> "周$weekday"
        }
    }

    /** `周X` 的「X」（用于 `本周X` 拼接）。 */
    private fun weekdayCharZh(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
        DayOfWeek.MONDAY -> "一"
        DayOfWeek.TUESDAY -> "二"
        DayOfWeek.WEDNESDAY -> "三"
        DayOfWeek.THURSDAY -> "四"
        DayOfWeek.FRIDAY -> "五"
        DayOfWeek.SATURDAY -> "六"
        DayOfWeek.SUNDAY -> "日"
    }

    /** 显示层统一入口：先做相对→绝对日期替换（锚点 = 首条 item 发送时间），再按 §3 统一格式（幂等）。 */
    fun displayText(
        text: String,
        anchorTs: Long? = null,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val absolute = absRelDates(text, anchorTs, zone)
        return fmtDateText(absolute, nowMillis, zone)
    }

    // ------------------------------------------------------------ 内部工具

    private fun replaceRelativeWeekday(
        text: String,
        anchor: LocalDate,
        prefixPattern: String,
        plusWeeks: Int,
    ): String = text.replace(Regex("($prefixPattern)\\s*周?([一二三四五六日天])")) { match ->
        val target = weekdayIndex(match.groupValues[2])
        if (target < 0) {
            match.value
        } else {
            val anchorIndex = anchor.dayOfWeek.value - 1
            val date = anchor.plusWeeks(plusWeeks.toLong()).plusDays((target - anchorIndex).toLong())
            mmdd.format(date)
        }
    }

    private fun dayText(anchor: LocalDate, offsetDays: Long): String = mmdd.format(anchor.plusDays(offsetDays))

    private fun weekdayIndex(char: String): Int = when (char) {
        "一" -> 0
        "二" -> 1
        "三" -> 2
        "四" -> 3
        "五" -> 4
        "六" -> 5
        "日", "天" -> 6
        else -> -1
    }

    private fun weekdayZh(jsWeekday: Int): String = weekdayZh(dayOfWeekFromJs(jsWeekday))

    private fun weekdayZh(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
        DayOfWeek.MONDAY -> "周一"
        DayOfWeek.TUESDAY -> "周二"
        DayOfWeek.WEDNESDAY -> "周三"
        DayOfWeek.THURSDAY -> "周四"
        DayOfWeek.FRIDAY -> "周五"
        DayOfWeek.SATURDAY -> "周六"
        DayOfWeek.SUNDAY -> "周日"
    }

    /** JS `Date.getDay()` 口径：0=周日 … 6=周六。 */
    private fun dayOfWeekFromJs(jsDay: Int): DayOfWeek = when (((jsDay % 7) + 7) % 7) {
        0 -> DayOfWeek.SUNDAY
        1 -> DayOfWeek.MONDAY
        2 -> DayOfWeek.TUESDAY
        3 -> DayOfWeek.WEDNESDAY
        4 -> DayOfWeek.THURSDAY
        5 -> DayOfWeek.FRIDAY
        else -> DayOfWeek.SATURDAY
    }
}
