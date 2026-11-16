package com.furina.classtools.ui.screens.timetable

import com.furina.classtools.data.timetable.SessionInfo
import com.furina.classtools.data.timetable.SessionState
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「下一节」倒计时文案单测（纯函数）。
 *
 * 覆盖：今天内分钟/小时分档、明天、周X、远期天数、已开始/非法时间兜底、不再出现 1000+ 分钟。
 */
class NextSessionCountdownTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun sessionAt(
        start: LocalDateTime,
        dayOffset: Int,
    ): SessionInfo {
        val zoned = start.atZone(zone)
        return SessionInfo(
            name = "高等数学",
            room = "01105",
            start = 1,
            end = 2,
            startText = "%02d:%02d".format(start.hour, start.minute),
            endText = "%02d:%02d".format(zoned.plusMinutes(90).hour, zoned.plusMinutes(90).minute),
            span = "第1-2节",
            startAt = zoned.toInstant().toEpochMilli(),
            endAt = zoned.plusMinutes(90).toInstant().toEpochMilli(),
            state = SessionState.NEXT,
            dayOffset = dayOffset,
            dayText = if (dayOffset == 0) "今天" else if (dayOffset == 1) "明天" else "",
            dateText = "${start.monthValue}/${start.dayOfMonth}",
            label = "第1-2节",
        )
    }

    @Test
    fun `今天 25 分钟显示分钟`() {
        val now = LocalDateTime.of(2026, 11, 16, 13, 45)
        val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
        val session = sessionAt(LocalDateTime.of(2026, 11, 16, 14, 10), dayOffset = 0)
        assertEquals("25 分钟后", NextSessionCountdown.text(session, nowMillis, zone))
    }

    @Test
    fun `今天 59 分钟仍显示分钟`() {
        val now = LocalDateTime.of(2026, 11, 16, 13, 1)
        val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
        val session = sessionAt(LocalDateTime.of(2026, 11, 16, 14, 0), dayOffset = 0)
        assertEquals("59 分钟后", NextSessionCountdown.text(session, nowMillis, zone))
    }

    @Test
    fun `今天超过 1 小时显示小时分`() {
        val now = LocalDateTime.of(2026, 11, 16, 12, 25)
        val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
        val session = sessionAt(LocalDateTime.of(2026, 11, 16, 14, 30), dayOffset = 0)
        assertEquals("2 小时 5 分", NextSessionCountdown.text(session, nowMillis, zone))
    }

    @Test
    fun `今天整小时不显示 0 分`() {
        val now = LocalDateTime.of(2026, 11, 16, 13, 30)
        val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
        val session = sessionAt(LocalDateTime.of(2026, 11, 16, 14, 30), dayOffset = 0)
        assertEquals("1 小时", NextSessionCountdown.text(session, nowMillis, zone))
    }

    @Test
    fun `明天的课显示明天加时间`() {
        val now = LocalDateTime.of(2026, 11, 16, 20, 0)
        val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
        val session = sessionAt(LocalDateTime.of(2026, 11, 17, 8, 10), dayOffset = 1)
        assertEquals("明天 08:10", NextSessionCountdown.text(session, nowMillis, zone))
    }

    @Test
    fun `两天后的课显示周几加时间`() {
        val now = LocalDateTime.of(2026, 11, 16, 20, 0) // 周一
        val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
        val session = sessionAt(LocalDateTime.of(2026, 11, 18, 14, 30), dayOffset = 2) // 周三
        assertEquals("周三 14:30", NextSessionCountdown.text(session, nowMillis, zone))
    }

    @Test
    fun `六天后的课仍显示周几`() {
        val now = LocalDateTime.of(2026, 11, 16, 20, 0) // 周一
        val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
        val session = sessionAt(LocalDateTime.of(2026, 11, 22, 8, 10), dayOffset = 6) // 周日
        assertEquals("周日 08:10", NextSessionCountdown.text(session, nowMillis, zone))
    }

    @Test
    fun `假期跨周显示天加小时`() {
        val now = LocalDateTime.of(2026, 11, 16, 10, 0)
        val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
        val startAt = nowMillis + 11L * 24 * 60 * 60 * 1000 + 5L * 60 * 60 * 1000
        val session = sessionAt(LocalDateTime.of(2026, 11, 16, 10, 0), dayOffset = 11)
            .copy(startAt = startAt)
        assertEquals("11 天 5 小时", NextSessionCountdown.text(session, nowMillis, zone))
    }

    @Test
    fun `远期整天空数不显示 0 小时`() {
        val now = LocalDateTime.of(2026, 11, 16, 10, 0)
        val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
        val session = sessionAt(LocalDateTime.of(2026, 11, 16, 10, 0), dayOffset = 14)
            .copy(startAt = nowMillis + 14L * 24 * 60 * 60 * 1000)
        assertEquals("14 天", NextSessionCountdown.text(session, nowMillis, zone))
    }

    @Test
    fun `远期文案绝不出现四位数分钟`() {
        val now = LocalDateTime.of(2026, 11, 16, 10, 0)
        val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
        // 原实现会输出「3000+ 分钟后」的场景：14 天后的课。
        val session = sessionAt(LocalDateTime.of(2026, 11, 30, 10, 0), dayOffset = 14)
        val text = NextSessionCountdown.text(session, nowMillis, zone)
        assertFalse("远期文案不应含分钟：$text", text.contains("分钟"))
        assertTrue("应为天数文案：$text", text.contains("天"))
    }

    @Test
    fun `已开始或时间非法显示即将开始`() {
        val now = LocalDateTime.of(2026, 11, 16, 14, 10)
        val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
        val ongoing = sessionAt(LocalDateTime.of(2026, 11, 16, 14, 0), dayOffset = 0)
        assertEquals("即将开始", NextSessionCountdown.text(ongoing, nowMillis, zone))

        val invalid = sessionAt(LocalDateTime.of(2026, 11, 16, 14, 0), dayOffset = 0)
            .copy(startAt = 0L)
        assertEquals("即将开始", NextSessionCountdown.text(invalid, nowMillis, zone))
    }
}
