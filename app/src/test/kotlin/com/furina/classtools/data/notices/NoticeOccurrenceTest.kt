package com.furina.classtools.data.notices

import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 长期通知期次 / recurrence.until / 时间显示 §3 / 统计 §2.4（三端同构契约固定向量）。
 *
 * 契约：`web/通知三端_长期通知与时间显示_契约_20261114.md`
 * §1.1（until）、§1.2（资料/无时间）、§1.3（期次窗口/key）、§2.4（统计）、§3（时间格式）。
 * now 固定注入（2026-11-14 13:31 周六），避免真实时钟导致测试不稳定。
 */
class NoticeOccurrenceTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 契约固定向量基准时刻：2026-11-14 13:31（周六）。 */
    private val now: Long = at(2026, 11, 14, 13, 31)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun dateTimeOf(ts: Long): LocalDateTime =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ts), zone)

    private fun group(
        id: String = "g1",
        recurrence: NoticeRecurrenceDto? = null,
        deadline: String = "",
        deadlineAction: String = "",
        eventAt: String = "",
        expireAt: String = "",
        signupStart: String = "",
        signupEnd: String = "",
        extensions: List<NoticeExtension> = emptyList(),
        actionable: Boolean = true,
        items: List<kotlinx.serialization.json.JsonObject> = emptyList(),
    ) = NoticeGroupDto(
        id = id,
        title = "测试通知",
        deadline = deadline,
        deadlineAction = deadlineAction,
        eventAt = eventAt,
        expireAt = expireAt,
        signupStart = signupStart,
        signupEnd = signupEnd,
        extensions = extensions,
        recurrence = recurrence,
        actionable = actionable,
        items = items,
    )

    private fun dailyRecurrence(
        time: String = "07:30",
        startAt: String = "2026-11-10T07:30:00+08:00",
        until: String = "",
    ) = NoticeRecurrenceDto(freq = "daily", time = time, startAt = startAt, until = until)

    // ------------------------------------------------------------ §1.1 recurrence.until

    @Test
    fun `until 为日期时含当天有效 次日零点才结束`() {
        val g = group(recurrence = dailyRecurrence(until = "2026-11-15"))
        val until = NoticeStatusMachine.recurUntilOf(g, zone)
        assertNotNull(until)
        // 11-15 23:59:59.999（含当天）
        val untilTime = dateTimeOf(until!!)
        assertEquals(11, untilTime.monthValue)
        assertEquals(15, untilTime.dayOfMonth)
        assertEquals(23, untilTime.hour)
        assertEquals(59, untilTime.minute)

        // 最后一天的产生前（11-15 00:30）：下一次 07:30 仍在 until 内 → 仍未结束，期次可见
        assertFalse(NoticeStatusMachine.recurEndedOf(g, at(2026, 11, 15, 0, 30), zone))
        assertNotNull(NoticeStatusMachine.occurrenceOf(g, at(2026, 11, 15, 0, 30), zone))
        // 最后一次发生之后（11-15 08:00）：下一次 11-16 已超出 until → 结束（§1.3 不再产生期次）
        assertTrue(NoticeStatusMachine.recurEndedOf(g, at(2026, 11, 15, 8, 0), zone))
        // 次日必然结束
        assertTrue(NoticeStatusMachine.recurEndedOf(g, at(2026, 11, 16, 0, 0), zone))
    }

    @Test
    fun `until 为 ISO datetime 时按该瞬时判定`() {
        val g = group(recurrence = dailyRecurrence(until = "2026-11-15T07:30:00+08:00"))
        val until = NoticeStatusMachine.recurUntilOf(g, zone)
        assertEquals(at(2026, 11, 15, 7, 30), until)
        // 07:29 时下一次 07:30 仍在 until 内 → 未结束
        assertFalse(NoticeStatusMachine.recurEndedOf(g, at(2026, 11, 15, 7, 29), zone))
        // 07:31：下一次 11-16 07:30 已超出 until → 结束
        assertTrue(NoticeStatusMachine.recurEndedOf(g, at(2026, 11, 15, 7, 31), zone))
    }

    @Test
    fun `until 过期后状态为长期安排已结束 归已过期区`() {
        val g = group(recurrence = dailyRecurrence(until = "2026-11-10"))
        val status = NoticeStatusMachine.statusOf(g, now, zone)
        assertEquals("recur_ended", status.key)
        assertTrue(status.expired)
        assertEquals("长期安排已结束", status.label)
        assertEquals(NoticeBucket.EXPIRED, status.bucket)
        // 不再产生期次、不计长期/待处理
        assertNull(NoticeStatusMachine.occurrenceOf(g, now, zone))
        assertTrue(NoticeStatusMachine.longtermDigests(listOf(NoticeDigest(g, ts = 1L)), now, zone).isEmpty())
        assertFalse(NoticeStatusMachine.isPending(g, now, zone))
    }

    @Test
    fun `固定向量3 until 为今天时已结束`() {
        val g = group(id = "gid", recurrence = dailyRecurrence(until = "2026-11-14"))
        // now = 2026-11-14 13:31；下一次 = 11-15 07:30 已超出 until → 结束
        assertTrue(NoticeStatusMachine.recurEndedOf(g, now, zone))
        assertNull(NoticeStatusMachine.occurrenceOf(g, now, zone))
        assertTrue(NoticeStatusMachine.statusOf(g, now, zone).expired)
    }

    @Test
    fun `无时间或非法 freq 的 recurrence 视为非法 不按长期安排处理`() {
        val noTime = group(recurrence = NoticeRecurrenceDto(freq = "daily", time = ""))
        assertNull(NoticeStatusMachine.effectiveRecurrenceOf(noTime))
        assertFalse(NoticeStatusMachine.isLongterm(noTime))
        assertNull(NoticeStatusMachine.recurNextOf(noTime, now, zone))

        val badFreq = group(recurrence = NoticeRecurrenceDto(freq = "yearly", time = "07:30"))
        assertNull(NoticeStatusMachine.effectiveRecurrenceOf(badFreq))

        val badTime = group(recurrence = NoticeRecurrenceDto(freq = "daily", time = "7点半"))
        assertNull(NoticeStatusMachine.effectiveRecurrenceOf(badTime))

        // 非法 recurrence 按普通通知判断（无时间锚点 → 资料/无时间，不判长期）
        assertFalse(NoticeStatusMachine.statusOf(noTime, now, zone).expired)
        assertEquals("none", NoticeStatusMachine.statusOf(noTime, now, zone).key)
    }

    // ------------------------------------------------------------ §1.3 期次窗口 / key

    @Test
    fun `固定向量1 monthly_weeks 下次与期次 key`() {
        val g = group(
            id = "gid",
            recurrence = NoticeRecurrenceDto(
                freq = "monthly_weeks",
                weekday = 3, // 周三（JS 0=周日）
                weeks = listOf(1, 2, 3),
                time = "17:30",
                startAt = "2026-11-04T17:30:00+08:00",
            ),
        )
        val next = NoticeStatusMachine.recurNextOf(g, now, zone)
        assertNotNull(next)
        val nextTime = dateTimeOf(next!!)
        assertEquals(11, nextTime.monthValue)
        assertEquals(18, nextTime.dayOfMonth)
        assertEquals(17, nextTime.hour)
        assertEquals(30, nextTime.minute)

        // 期次窗口 = [next-24h, next)
        val windowStart = next - 24L * 60 * 60 * 1000
        assertEquals(at(2026, 11, 17, 17, 30), windowStart)
        assertEquals("gid@occ:20261118", NoticeStatusMachine.occurrenceKeyOf("gid", next, zone))
        // now 13:31 还没进窗口 → 不显示期次卡
        assertNull(NoticeStatusMachine.occurrenceOf(g, now, zone))
        // 进入窗口后（11-17 18:00）显示该期次
        val occ = NoticeStatusMachine.occurrenceOf(g, at(2026, 11, 17, 18, 0), zone)
        assertNotNull(occ)
        assertEquals("gid@occ:20261118", occ!!.key)
        assertEquals(next, occ.nextMillis)
        assertEquals(windowStart, occ.windowStartMillis)
        assertEquals("本次 11月18日（下周三） 17:30", NoticeStatusMachine.occurrenceLabelOf(occ, now, zone))
    }

    @Test
    fun `固定向量2 until 前一天窗口内显示期次`() {
        val g = group(id = "gid", recurrence = dailyRecurrence(until = "2026-11-15"))
        val occ = NoticeStatusMachine.occurrenceOf(g, now, zone)
        assertNotNull(occ)
        assertEquals(at(2026, 11, 15, 7, 30), occ!!.nextMillis)
        assertEquals(at(2026, 11, 14, 7, 30), occ.windowStartMillis)
        assertEquals("gid@occ:20261115", occ.key)
        assertEquals("本次 11月15日（明天） 07:30", NoticeStatusMachine.occurrenceLabelOf(occ, now, zone))
    }

    @Test
    fun `期次窗口边界 窗口起点可见 发生时刻切换到下一期`() {
        // 每天 07:30；now 11-14 13:31 → next 11-15 07:30，窗口起点 11-14 07:30（含）
        val g = group(id = "gid", recurrence = dailyRecurrence())
        val windowStart = at(2026, 11, 14, 7, 30)
        assertNotNull(NoticeStatusMachine.occurrenceOf(g, windowStart, zone))
        // 发生时刻：本期结束，next 变成 11-16 07:30（窗口 11-15 07:30 起）
        val atMoment = NoticeStatusMachine.occurrenceOf(g, at(2026, 11, 15, 7, 30), zone)
        assertNotNull(atMoment)
        assertEquals("gid@occ:20261116", atMoment!!.key)
        // 发生前最后一毫秒仍是本期
        val justBefore = NoticeStatusMachine.occurrenceOf(g, at(2026, 11, 15, 7, 29), zone)
        assertEquals("gid@occ:20261115", justBefore!!.key)
    }

    @Test
    fun `期次 done 按 key 隔离 不影响下一期`() {
        // 每周五 19:00（JS weekday=5）；now=11-14 13:31 → next=11-20 19:00，窗口 11-19 19:00 起
        val g = group(
            id = "gid",
            recurrence = NoticeRecurrenceDto(
                freq = "weekly",
                weekday = 5,
                time = "19:00",
                startAt = "2026-11-06T19:00:00+08:00",
            ),
        )
        val occ = NoticeStatusMachine.occurrenceOf(g, at(2026, 11, 19, 20, 0), zone)
        assertNotNull(occ)
        assertEquals("gid@occ:20261120", occ!!.key)

        // 本期已处理 → 不计待处理
        val done = setOf(occ.key)
        assertFalse(NoticeStatusMachine.isPending(g, at(2026, 11, 19, 20, 0), zone, doneRef = { it in done }))
        assertTrue(NoticeStatusMachine.isPending(g, at(2026, 11, 19, 20, 0), zone, doneRef = { false }))

        // 本期结束后（11-20 20:00）→ 无期次卡（等待下一期窗口）
        assertNull(NoticeStatusMachine.occurrenceOf(g, at(2026, 11, 20, 20, 0), zone))
        // 下一期窗口（11-26 19:00 起）→ 新 key，未处理
        val nextOcc = NoticeStatusMachine.occurrenceOf(g, at(2026, 11, 26, 20, 0), zone)
        assertNotNull(nextOcc)
        assertEquals("gid@occ:20261127", nextOcc!!.key)
        assertFalse(NoticeStatusMachine.isPending(g, at(2026, 11, 26, 20, 0), zone, doneRef = { it in done }))
    }

    @Test
    fun `长期清单只含进行中的安排 按下次时间升序`() {
        val soon = group(id = "a", recurrence = dailyRecurrence())
        val later = group(
            id = "b",
            recurrence = NoticeRecurrenceDto(freq = "weekly", weekday = 5, time = "19:00"),
        )
        val ended = group(id = "c", recurrence = dailyRecurrence(until = "2026-11-10"))
        val noRecur = group(id = "d", deadline = "2026-11-14T18:00:00+08:00")
        val list = NoticeStatusMachine.longtermDigests(
            listOf(
                NoticeDigest(later, ts = 3L),
                NoticeDigest(noRecur, ts = 2L),
                NoticeDigest(ended, ts = 1L),
                NoticeDigest(soon, ts = 0L),
            ),
            now,
            zone,
        )
        assertEquals(listOf("a", "b"), list.map { it.group.id })
    }

    // ------------------------------------------------------------ §2.4 统计口径

    @Test
    fun `统计口径 待处理排除资料无时间 长期定义只按未处理期次计入`() {
        val material = group(id = "material") // 无任何时间锚点 → 资料/无时间
        val normals = group(id = "normal", deadline = "2026-11-14T18:00:00+08:00")
        val expired = group(id = "expired", deadline = "2026-11-13T18:00:00+08:00")
        val endedLongterm = group(id = "ended", recurrence = dailyRecurrence(until = "2026-11-10"))
        val longtermInWindow = group(id = "lt1", recurrence = dailyRecurrence(until = "2026-11-15"))
        val longtermNoWindow = group(
            id = "lt2",
            recurrence = NoticeRecurrenceDto(freq = "weekly", weekday = 5, time = "19:00"),
        )
        val doneNormal = group(id = "done", deadline = "2026-11-15T18:00:00+08:00")
        val infoOnly = group(id = "info", deadline = "2026-11-15T18:00:00+08:00", actionable = false)

        val digests = listOf(
            NoticeDigest(material, ts = 1L),
            NoticeDigest(normals, ts = 2L),
            NoticeDigest(expired, ts = 3L),
            NoticeDigest(endedLongterm, ts = 4L),
            NoticeDigest(longtermInWindow, ts = 5L),
            NoticeDigest(longtermNoWindow, ts = 6L),
            NoticeDigest(doneNormal, done = true, ts = 7L),
            NoticeDigest(infoOnly, ts = 8L),
        )
        val stats = NoticeStatusMachine.statsOf(digests, now, zone)
        // pending: normals + 期次(lt1, 11-15 07:30 在 24h 内) = 2
        assertEquals(2, stats.pending)
        assertEquals(2, stats.urgent)
        assertEquals(2, stats.week)
        // expired: expired + endedLongterm = 2
        assertEquals(2, stats.expired)
        // longterm：进行中的 2 条（lt1 / lt2），已结束不计
        assertEquals(2, stats.longterm)
        assertEquals(8L, stats.latestPostedMillis)
    }

    @Test
    fun `统计口径 已处理期次不计待处理`() {
        val g = group(id = "gid", recurrence = dailyRecurrence(until = "2026-11-15"))
        val occKey = "gid@occ:20261115"
        val withoutDone = NoticeStatusMachine.statsOf(listOf(NoticeDigest(g, ts = 1L)), now, zone)
        assertEquals(1, withoutDone.pending)

        val withDone = NoticeStatusMachine.statsOf(
            digests = listOf(NoticeDigest(g, ts = 1L)),
            nowMillis = now,
            zone = zone,
            doneRef = { it == occKey },
        )
        assertEquals(0, withDone.pending)
        assertEquals(1, withDone.longterm)
    }

    @Test
    fun `最新发布相对文案 今天 昨天 更早 与空`() {
        assertEquals("今天 12:49", NoticeStatusMachine.latestPostedTextOf(at(2026, 11, 14, 12, 49), now, zone))
        assertEquals("昨天 09:05", NoticeStatusMachine.latestPostedTextOf(at(2026, 11, 13, 9, 5), now, zone))
        assertEquals("11月8日（上周日）", NoticeStatusMachine.latestPostedTextOf(at(2026, 11, 8, 9, 5), now, zone))
        assertEquals("—", NoticeStatusMachine.latestPostedTextOf(0L, now, zone))
    }

    @Test
    fun `统计含人工通知发布时间 取最新`() {
        val g = group(id = "g", deadline = "2026-11-15T18:00:00+08:00")
        val stats = NoticeStatusMachine.statsOf(
            digests = listOf(NoticeDigest(g, ts = at(2026, 11, 10, 8, 0))),
            nowMillis = now,
            zone = zone,
            extraPostedMillis = listOf(at(2026, 11, 14, 12, 49)),
        )
        assertEquals(at(2026, 11, 14, 12, 49), stats.latestPostedMillis)
        assertEquals("今天 12:49", stats.latestPostedText)
    }

    // ------------------------------------------------------------ §3 时间显示固定向量

    private fun fmtDate(text: String): String = NoticeStatusMachine.fmtDateText(text, now, zone)

    @Test
    fun `时间格式固定向量 今天明天本周上周下周与跨两周`() {
        assertEquals("11月13日（本周五）", fmtDate("11-13"))
        assertEquals("11月14日（今天）", fmtDate("11-14"))
        assertEquals("11月15日（明天）", fmtDate("11-15"))
        assertEquals("11月12日（本周四）", fmtDate("11-12"))
        assertEquals("11月8日（上周日）", fmtDate("11-08"))
        assertEquals("11月16日（下周一）", fmtDate("11-16"))
        assertEquals("11月20日（下周五）", fmtDate("11-20"))
        assertEquals("12月1日（周二）", fmtDate("12-01"))
    }

    @Test
    fun `时间格式 多日期与区间两端都替换`() {
        assertEquals(
            "11月13日（本周五） 11月14日（本周六）不上晚自习",
            fmtDate("11-13 11-14 不上晚自习"),
        )
        assertEquals(
            "11月13日（本周五）至11月14日（本周六）",
            fmtDate("11-13至11-14"),
        )
        assertEquals("11月14日（今天）", fmtDate("2026-11-14"))
        assertEquals("11月14日（今天）", fmtDate("11月14日"))
        assertEquals("2027年1月3日（周日）", fmtDate("2027年1月3日"))
    }

    @Test
    fun `时间格式 已是括号相对标签时不重复加括号`() {
        assertEquals("11月13日（本周五）", fmtDate("11月13日（本周五）"))
        assertEquals("11月13日（周五）", fmtDate("11-13（周五）"))
        assertEquals("今天（周五）开会", fmtDate("今天（周五）开会"))
    }

    @Test
    fun `时间格式 不误伤非日期数字`() {
        assertEquals("请 42号同学到 5号楼 门口", fmtDate("请 42号同学到 5号楼 门口"))
        assertEquals("9:30 集合", fmtDate("9:30 集合"))
    }

    @Test
    fun `fmtWhen 绝对日期分支使用 §3 格式 倒计时保持相对`() {
        assertEquals("30 分钟后", NoticeStatusMachine.fmtWhen(now + 30 * 60_000, now, zone))
        assertEquals("2 小时后", NoticeStatusMachine.fmtWhen(now + 2 * 3_600_000, now, zone))
        assertEquals("3 天后", NoticeStatusMachine.fmtWhen(now + 3 * 86_400_000L, now, zone))
        assertEquals("11月13日（昨天） 11:31", NoticeStatusMachine.fmtWhen(now - 26 * 3_600_000L, now, zone))
        assertEquals("11月20日（下周五） 13:31", NoticeStatusMachine.fmtWhen(now + 6 * 86_400_000L, now, zone))
    }

    @Test
    fun `长期清单下次文案为 §3 格式`() {
        val g = group(
            id = "gid",
            recurrence = NoticeRecurrenceDto(
                freq = "monthly_weeks",
                weekday = 3,
                weeks = listOf(1, 2, 3),
                time = "17:30",
                startAt = "2026-11-04T17:30:00+08:00",
            ),
        )
        assertEquals("下次 11月18日（下周三） 17:30", NoticeStatusMachine.recurNextTextOf(g, now, zone))
    }

    @Test
    fun `资料无时间判定 hasTimeAnchor`() {
        assertFalse(NoticeStatusMachine.hasTimeAnchor(group(), zone))
        assertTrue(NoticeStatusMachine.hasTimeAnchor(group(deadline = "2026-11-15T18:00:00+08:00"), zone))
        assertTrue(NoticeStatusMachine.hasTimeAnchor(group(eventAt = "2026-11-15T18:00:00+08:00"), zone))
        assertTrue(NoticeStatusMachine.hasTimeAnchor(group(signupStart = "2026-11-14T18:00:00+08:00"), zone))
        assertTrue(NoticeStatusMachine.hasTimeAnchor(group(signupEnd = "2026-11-15T18:00:00+08:00"), zone))
    }

    // ------------------------------------------------------------ 显示层文本管线（相对→绝对→§3）

    @Test
    fun `displayText 先做相对替换再做 §3 格式化`() {
        val anchor = at(2026, 11, 10, 9, 0) // 周一
        val items = listOf(
            buildJsonObject {
                put("kind", JsonPrimitive("text"))
                put("time", JsonPrimitive("11-10 09:00"))
            },
        )
        val g = group(items = items)
        val anchorFromGroup = NoticeStatusMachine.anchorOf(g, zone)
        assertNotNull(anchorFromGroup)
        // 「明天」按消息时间 = 11-11，按渲染时 now=11-14 → 本周三
        assertEquals(
            "11月11日（本周三） 交材料",
            NoticeStatusMachine.displayText("明天 交材料", anchorFromGroup, now, zone),
        )
        assertEquals("11月14日（今天）", NoticeStatusMachine.displayText("11-14", anchor, now, zone))
    }
}
