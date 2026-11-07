package com.furina.classtools.data.notices

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
 * 唯一真源：`docs/api/NOTICES.md` §3（网页 `anStatus` 简化版 + 小程序 `notice.js` 完整版口径）：
 * - **时间优先级**：报名窗口 > 真实截止（extensions 最后一次延期优先）> 活动/事件 > 无时间；
 * - `expireAt` 只用于「活动类自动归档」，**绝不当截止时间展示**；
 * - `recurrence`（长期安排）存在时**永不判过期**；
 * - 时间展示 `fmtWhen`：过期→`MM-dd HH:mm`；<1h→`N 分钟后`；<24h→`N 小时后`；<7d→`N 天后`；否则 `MM-dd HH:mm`；
 * - 动作文案精简（≤6 字，`报名已截止→报名截止` 等映射）。
 */
object NoticeStatusMachine {

    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 60 * MINUTE_MS
    private const val DAY_MS = 24 * HOUR_MS
    private const val WEEK_MS = 7 * DAY_MS

    private val mmdd: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd", Locale.CHINA)
    private val mmddhhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)

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

    /** 状态机/卡片 chip 用的人类可读时间。 */
    fun fmtWhen(ts: Long, nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        val left = ts - nowMillis
        if (left < 0) return fmt(ts, zone)
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
        return fmt(ts, zone)
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
        if (group.recurrence != null) {
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

    fun isActionable(group: NoticeGroupDto): Boolean =
        group.actionable && group.recurrence == null

    private fun bucketStatus(ts: Long, action: String, nowMillis: Long, extension: Boolean): NoticeStatus {
        val label = "${fmtWhen(ts, nowMillis)} $action"
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

    fun recurLabelOf(group: NoticeGroupDto): String {
        val recur = group.recurrence ?: return ""
        if (recur.label.isNotBlank()) return recur.label
        return buildString {
            when (recur.freq) {
                "daily" -> append("每天")
                "weekly" -> recur.weekday?.let { append("每周${weekdayZh(it)}") }
                "monthly_day" -> recur.day?.let { append("每月 $it 日") }
                "monthly_weeks" -> {
                    val weeks = recur.weeks.joinToString("、")
                    val weekday = recur.weekday?.let { weekdayZh(it) } ?: "周"
                    append("每月第 $weeks 个$weekday")
                }
                else -> append("长期安排")
            }
            if (recur.time.isNotBlank()) append(" ${recur.time}")
        }
    }

    /**
     * 下一次发生时间（本地计算；支持 daily / weekly / monthly_day / monthly_weeks）。
     * JS `weekday` 口径 0=周日 … 6=周六；monthly_day 只支持 1..28。
     */
    fun recurNextOf(
        group: NoticeGroupDto,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        val recur = group.recurrence ?: return null
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

    /** `下次 MM-dd 周X HH:mm`；无则空串。 */
    fun recurNextTextOf(
        group: NoticeGroupDto,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val recur = group.recurrence ?: return ""
        val next = recurNextOf(group, nowMillis, zone) ?: return ""
        val zoned = Instant.ofEpochMilli(next).atZone(zone)
        return buildString {
            append("下次 ")
            append(mmdd.format(zoned))
            append(' ')
            append(weekdayZh(zoned.dayOfWeek))
            if (recur.time.isNotBlank()) {
                append(' ')
                append(recur.time)
            }
        }
    }

    // ------------------------------------------------------------ 简报统计 / 最紧急

    fun statsOf(
        digests: List<NoticeDigest>,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): NoticeStats {
        var pending = 0
        var urgent = 0
        var week = 0
        var expired = 0
        for (digest in digests) {
            val group = digest.group
            if (group.recurrence != null) continue
            if (!group.actionable) continue
            val status = statusOf(group, nowMillis, zone)
            if (status.expired) {
                expired += 1
                continue
            }
            if (digest.done) continue
            pending += 1
            val whenAt = whenOf(group, nowMillis, zone) ?: continue
            val left = whenAt - nowMillis
            if (left in 0..DAY_MS) urgent += 1 else if (left in 0..WEEK_MS) week += 1
        }
        return NoticeStats(pending = pending, urgent = urgent, week = week, expired = expired)
    }

    /** 最紧急一条：alert 优先 → 主显示时间升序（无时间最后）→ ts 降序。 */
    fun mostUrgent(
        digests: List<NoticeDigest>,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): NoticeDigest? {
        val candidates = digests.filter { digest ->
            val group = digest.group
            if (group.recurrence != null) return@filter false
            if (!group.actionable) return@filter false
            if (digest.done) return@filter false
            !statusOf(group, nowMillis, zone).expired
        }
        if (candidates.isEmpty()) return null
        return candidates.sortedWith(
            compareBy<NoticeDigest> { digest ->
                if (statusOf(digest.group, nowMillis, zone).bucket == NoticeBucket.ALERT) 0 else 1
            }.thenBy { digest ->
                whenOf(digest.group, nowMillis, zone) ?: Long.MAX_VALUE
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

    /** 锚点：同一卡片第一条带 `time` 的 item 的发送时间。 */
    fun anchorOf(group: NoticeGroupDto, zone: ZoneId = ZoneId.systemDefault()): Long? {
        for (item in group.items) {
            val time = (item["time"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            parseMsgTime(time, zone)?.let { return it }
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
