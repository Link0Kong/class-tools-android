package com.furina.classtools.ui.screens.timetable

import com.furina.classtools.data.timetable.SessionInfo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 「下一节」倒计时文案（纯函数，无 Compose/Android 依赖，便于单测）。
 *
 * 背景（2026-11-16 用户反馈）：课表页下一节摘要条原先直接用 `(startAt - now) / 60_000` 拼
 * 「N 分钟后」，假期/跨周时下一节可能在 14 天内很远的日期 → 出现「几千分钟」怪值。
 *
 * 口径（按「距离 + 相对天」分档，全部输出人类可读文案，不再出现 4 位分钟数）：
 * - 今天内：< 60 分钟 → `N 分钟后`；≥ 60 分钟 → `x 小时` / `x 小时 y 分`；
 * - 明天：`明天 HH:mm`；
 * - 本周 / 6 天内：`周X HH:mm`（如「周日 08:10」）；
 * - 更远（假期、跨周）：`x 天` / `x 天 y 小时`；
 * - 已开始 / 时间非法：`即将开始`。
 *
 * 说明：判断「明天/周X」用 [SessionInfo.dayOffset]（数据层 `nextSession()` 已按本地日期填充：
 * 0 = 今天，1 = 明天，2.. = 之后第 N 天），比纯时间差更稳（避免 23:50 看次日 08:00 变成「8 小时」）。
 */
internal object NextSessionCountdown {

    private const val MINUTE_MS: Long = 60_000L
    private const val HOUR_MS: Long = 60L * MINUTE_MS
    private const val DAY_MS: Long = 24L * HOUR_MS

    private val hourMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

    private val weekLabels: List<String> = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    /**
     * 下一节倒计时文案（不会输出超过 59 的分钟数）。
     *
     * @param session 下一节课（[SessionInfo.startAt] / [SessionInfo.dayOffset] 有效）
     * @param nowMillis 当前时间戳（可注入，便于单测）
     * @param zone 时区（默认系统时区；单测注入固定时区）
     */
    fun text(
        session: SessionInfo,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val diff = session.startAt - nowMillis
        if (diff <= 0L) return "即将开始"
        return when (val dayOffset = session.dayOffset) {
            in Int.MIN_VALUE..0 -> withinDayText(diff)
            1 -> "明天 ${timeOf(session.startAt, zone)}"
            in 2..6 -> "${weekLabelOf(session.startAt, zone)} ${timeOf(session.startAt, zone)}"
            else -> farText(diff)
        }
    }

    /** 今天内的距离文案：< 1 小时用分钟，否则用「x 小时 [y 分]」。 */
    private fun withinDayText(diff: Long): String {
        if (diff < HOUR_MS) {
            val minutes = (diff / MINUTE_MS).coerceAtLeast(1L)
            return "$minutes 分钟后"
        }
        val totalMinutes = diff / MINUTE_MS
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            minutes == 0L -> "$hours 小时"
            else -> "$hours 小时 $minutes 分"
        }
    }

    /** 6 天以上的远期距离：`x 天 [y 小时]`（跨周/假期、直到 14 天搜索窗口）。 */
    private fun farText(diff: Long): String {
        val days = diff / DAY_MS
        if (days <= 0L) return withinDayText(diff)
        val hours = (diff % DAY_MS) / HOUR_MS
        return if (hours == 0L) "$days 天" else "$days 天 $hours 小时"
    }

    private fun timeOf(ts: Long, zone: ZoneId): String =
        hourMinute.format(Instant.ofEpochMilli(ts).atZone(zone))

    private fun weekLabelOf(ts: Long, zone: ZoneId): String {
        val index = Instant.ofEpochMilli(ts).atZone(zone).dayOfWeek.value - 1
        return weekLabels.getOrElse(index) { "" }
    }
}
