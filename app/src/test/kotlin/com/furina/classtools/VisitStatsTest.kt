package com.furina.classtools

import com.furina.classtools.data.visits.VisitRange
import com.furina.classtools.data.visits.VisitRow
import com.furina.classtools.data.visits.VisitStats
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 访问统计纯逻辑单测（契约 `web/统计多端_最新登录_契约_20261118.md` §2）：
 * 排序 / 分钟时间 / 登录端 / 忽略测试账号 / 旧行回退。
 *
 * 夹具只用官方测试身份 `3260799999`、9999 段 QA 身份与 9000 段虚构成员；不含真实学号。
 */
class VisitStatsTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now: Long = LocalDateTime.of(2026, 11, 18, 21, 32)
        .atZone(zone).toInstant().toEpochMilli()
    private val today: String = "2026-11-18"
    private val yesterday: String = "2026-11-17"

    private fun ts(day: String, hour: Int, minute: Int): Long =
        LocalDateTime.of(
            day.substring(0, 4).toInt(),
            day.substring(5, 7).toInt(),
            day.substring(8, 10).toInt(),
            hour,
            minute,
        ).atZone(zone).toInstant().toEpochMilli()

    private fun rows(): List<VisitRow> = listOf(
        VisitRow("$today-10", "9000000001", "张三", ts(today, 10, 42), "web"),
        VisitRow("$today-11", "9000000001", "张三", ts(today, 11, 5), "android"),
        VisitRow("$today-09", "9000000002", "李四", ts(today, 9, 7), "mp"),
        VisitRow("$yesterday-20", "9000000003", "王五", ts(yesterday, 20, 15), "win"),
        VisitRow("$today-12", "3260799999", "测试账号", ts(today, 12, 30), "web"),
        VisitRow("$today-12", "9999000001", "测试同学", ts(today, 12, 40), "mp"),
        VisitRow("$today-08", "", "匿名", ts(today, 8, 0), "web"),
    )

    @Test
    fun isTestAccount_onlyOfficialAndQaRange() {
        assertTrue(VisitStats.isTestAccount("3260799999"))
        assertTrue(VisitStats.isTestAccount("9999000001"))
        assertFalse(VisitStats.isTestAccount("9000000001"))
        assertFalse(VisitStats.isTestAccount(""))
        assertFalse(VisitStats.isTestAccount(null))
    }

    @Test
    fun build_week_sortsByLatestLoginAndShowsClientAndMinutes() {
        val snapshot = VisitStats.build(rows(), VisitRange.WEEK, nowMillis = now, zoneId = zone)

        assertEquals(3, snapshot.accounts.size)
        assertEquals("测试账号/匿名行不参与", 4, snapshot.total)
        assertEquals(2, snapshot.activeToday)

        assertEquals("张三", snapshot.accounts[0].name)
        assertEquals(2, snapshot.accounts[0].count)
        assertEquals("11/18 11:05", snapshot.accounts[0].lastText)
        assertEquals("安卓", snapshot.accounts[0].clientText)
        assertEquals("李四", snapshot.accounts[1].name)
        assertEquals("王五", snapshot.accounts[2].name)
        assertTrue(snapshot.accounts[2].detail.contains("Windows"))
    }

    @Test
    fun build_today_excludesYesterdayAccount() {
        val snapshot = VisitStats.build(rows(), VisitRange.TODAY, nowMillis = now, zoneId = zone)

        assertEquals(2, snapshot.accounts.size)
        assertTrue(snapshot.accounts.none { it.name == "王五" })
        assertEquals(3, snapshot.total)
        assertTrue(snapshot.accounts.all { it.activeToday })
    }

    @Test
    fun effectiveTs_fallsBackToSlotHourForLegacyRow() {
        val legacy = VisitRow("$today-08", "9000000001", "张三", 0, "")
        val effective = VisitStats.effectiveTs(legacy, zone)
        assertEquals("11/18 08:00", VisitStats.formatTime(effective, zone))
    }

    @Test
    fun clientLabel_knownAndUnknown() {
        assertEquals("网页", VisitStats.clientLabel("web"))
        assertEquals("小程序", VisitStats.clientLabel("mp"))
        assertEquals("安卓", VisitStats.clientLabel("android"))
        assertEquals("Windows", VisitStats.clientLabel("win"))
        assertEquals("鸿蒙", VisitStats.clientLabel("harmony"))
        assertEquals("未记录", VisitStats.clientLabel(""))
        assertEquals("未记录", VisitStats.clientLabel(null))
    }
}
