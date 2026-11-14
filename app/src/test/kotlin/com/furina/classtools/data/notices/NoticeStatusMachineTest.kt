package com.furina.classtools.data.notices

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeStatusMachineTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now: Long = LocalDateTime.of(2026, 11, 7, 12, 0).atZone(zone).toInstant().toEpochMilli()

    /** 相对于 now 的 ISO 时间串（+hours 可为负）。 */
    private fun at(hoursFromNow: Long): String =
        LocalDateTime.of(2026, 11, 7, 12, 0).plusHours(hoursFromNow).toString()

    private fun group(
        deadline: String = "",
        deadlineAction: String = "",
        eventAt: String = "",
        eventAction: String = "",
        expireAt: String = "",
        signupStart: String = "",
        signupEnd: String = "",
        extensions: List<NoticeExtension> = emptyList(),
        recurrence: NoticeRecurrenceDto? = null,
        alert: Boolean = false,
        actionable: Boolean = true,
        items: List<kotlinx.serialization.json.JsonObject> = emptyList(),
    ) = NoticeGroupDto(
        id = "g_1",
        title = "测试通知",
        deadline = deadline,
        deadlineAction = deadlineAction,
        eventAt = eventAt,
        eventAction = eventAction,
        expireAt = expireAt,
        signupStart = signupStart,
        signupEnd = signupEnd,
        extensions = extensions,
        recurrence = recurrence,
        alert = alert,
        actionable = actionable,
        items = items,
    )

    // ------------------------------------------------------------ 状态机主流程

    @Test
    fun `1h 内截止为 urgent`() {
        val soon = LocalDateTime.of(2026, 11, 7, 12, 30).toString()
        val s = NoticeStatusMachine.statusOf(group(deadline = soon, deadlineAction = "截止"), now)
        assertEquals("urgent", s.key)
        assertEquals(NoticeBucket.URGENT, s.bucket)
        assertFalse(s.expired)
    }

    @Test
    fun `今日内截止为 today 并显示 N 小时后`() {
        val s = NoticeStatusMachine.statusOf(group(deadline = at(6)), now)
        assertEquals("today", s.key)
        assertTrue(s.label.contains("6 小时后"))
    }

    @Test
    fun `2 天内截止为 soon`() {
        val s = NoticeStatusMachine.statusOf(group(deadline = at(48)), now)
        assertEquals("soon", s.key)
        assertFalse(s.expired)
    }

    @Test
    fun `远期截止为 active`() {
        val s = NoticeStatusMachine.statusOf(group(deadline = at(24 * 10)), now)
        assertEquals("active", s.key)
        assertTrue(s.label.contains("截止"))
    }

    @Test
    fun `过了真实截止为 expired 且带时间与动作`() {
        val s = NoticeStatusMachine.statusOf(group(deadline = at(-2), deadlineAction = "报名截止"), now)
        assertEquals("expired", s.key)
        assertTrue(s.expired)
        assertTrue(s.label.contains("报名截止"))
        // 契约 §3：绝对时间统一为 `M月D日（相对周） HH:mm`
        assertTrue(s.label.contains("11月7日"))
    }

    @Test
    fun `extensions 最后一次延期优先且当截止已过按 extended_past`() {
        val futureExtension = NoticeExtension(deadline = at(30))
        val s = NoticeStatusMachine.statusOf(
            group(deadline = at(-30), extensions = listOf(NoticeExtension(deadline = at(10)), futureExtension)),
            now,
        )
        assertFalse(s.expired)
        assertEquals("extended", s.key)

        val pastExtension = NoticeStatusMachine.statusOf(
            group(deadline = at(-30), extensions = listOf(NoticeExtension(deadline = at(-5)))),
            now,
        )
        assertTrue(pastExtension.expired)
        assertEquals("extended_past", pastExtension.key)
    }

    @Test
    fun `报名窗口优先于截止`() {
        val notStarted = NoticeStatusMachine.statusOf(
            group(deadline = at(72), signupStart = at(5), signupEnd = at(20)),
            now,
        )
        assertTrue(notStarted.label.contains("报名开始"))

        val inWindow = NoticeStatusMachine.statusOf(
            group(deadline = at(72), signupStart = at(-5), signupEnd = at(20)),
            now,
        )
        assertTrue(inWindow.label.contains("报名截止"))
        assertFalse(inWindow.expired)

        val ended = NoticeStatusMachine.statusOf(
            group(deadline = at(72), signupStart = at(-20), signupEnd = at(-5)),
            now,
        )
        assertTrue(ended.expired)
        assertEquals("expired", ended.key)
    }

    @Test
    fun `活动时间用 expireAt 作为自动归档 不误当截止`() {
        val active = NoticeStatusMachine.statusOf(group(eventAt = at(-1), expireAt = at(5)), now)
        assertEquals("active", active.key)
        assertFalse(active.expired)

        val archived = NoticeStatusMachine.statusOf(group(eventAt = at(-10), expireAt = at(-2)), now)
        assertTrue(archived.expired)
        assertEquals("expired", archived.key)
    }

    @Test
    fun `长期安排永不判过期 且能算下次时间`() {
        val recurrence = NoticeRecurrenceDto(
            freq = "weekly",
            weekday = 5, // 周五（JS 0=周日）
            time = "17:30",
            label = "每周五 17:30",
        )
        val s = NoticeStatusMachine.statusOf(
            group(deadline = at(-100), recurrence = recurrence),
            now,
        )
        assertEquals("recurring", s.key)
        assertFalse(s.expired)

        val next = NoticeStatusMachine.recurNextOf(group(recurrence = recurrence), now, zone)
        assertNotNull(next)
        val nextDate = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(next!!), zone)
        assertEquals(java.time.DayOfWeek.FRIDAY, nextDate.dayOfWeek)
        assertTrue(next > now)
        assertTrue(NoticeStatusMachine.recurNextTextOf(group(recurrence = recurrence), now, zone).startsWith("下次 "))
    }

    @Test
    fun `monthly_day 计算下次`() {
        val recurrence = NoticeRecurrenceDto(freq = "monthly_day", day = 15, time = "09:00")
        val next = NoticeStatusMachine.recurNextOf(group(recurrence = recurrence), now, zone)!!
        val date = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(next), zone)
        assertEquals(15, date.dayOfMonth)
        assertTrue(next > now)
    }

    @Test
    fun `无任何时间为 none 且不判过期`() {
        val s = NoticeStatusMachine.statusOf(group(), now)
        assertEquals("none", s.key)
        assertEquals("无时间", s.label)
        assertFalse(s.expired)
    }

    @Test
    fun `alert 在已截止时仍显示有新名单`() {
        val s = NoticeStatusMachine.statusOf(
            group(deadline = at(-2), alert = true),
            now,
        )
        assertEquals("alert", s.key)
        assertEquals(NoticeBucket.ALERT, s.bucket)
        assertFalse(s.expired)
    }

    // ------------------------------------------------------------ 展示辅助

    @Test
    fun `fmtWhen 分档`() {
        assertEquals("30 分钟后", NoticeStatusMachine.fmtWhen(now + 30 * 60_000, now))
        assertEquals("2 小时后", NoticeStatusMachine.fmtWhen(now + 2 * 3_600_000, now))
        assertEquals("3 天后", NoticeStatusMachine.fmtWhen(now + 3 * 86_400_000L, now))
        // 绝对日期按 §3 统一格式（过期分支）
        assertTrue(NoticeStatusMachine.fmtWhen(now - 3_600_000, now).contains("11月7日"))
    }

    @Test
    fun `conciseAction 精简映射`() {
        assertEquals("报名截止", NoticeStatusMachine.conciseAction("报名已截止"))
        assertEquals("点名", NoticeStatusMachine.conciseAction("集合点名"))
        assertEquals("截止", NoticeStatusMachine.conciseAction("截止"))
        assertTrue(NoticeStatusMachine.conciseAction("非常非常长的动作文案").length <= 6)
    }

    @Test
    fun `whenInfo 优先级 报名 大于 截止 大于 活动`() {
        assertEquals(
            "报名开始",
            NoticeStatusMachine.whenInfo(
                group(signupStart = at(5), signupEnd = at(20), deadline = at(72), eventAt = at(100)),
                now,
            )?.action,
        )
        assertEquals(
            "截止",
            NoticeStatusMachine.whenInfo(group(deadline = at(10), eventAt = at(100)), now)?.action,
        )
        assertEquals(
            "开始",
            NoticeStatusMachine.whenInfo(group(eventAt = at(10)), now)?.action,
        )
        assertNull(NoticeStatusMachine.whenInfo(group(), now))
    }

    @Test
    fun `statsOf 统计待处理 紧急 本周 过期`() {
        val digests = listOf(
            NoticeDigest(group(deadline = at(1)), done = false),
            NoticeDigest(group(deadline = at(6)), done = false),
            NoticeDigest(group(deadline = at(48)), done = false),
            NoticeDigest(group(deadline = at(-5)), done = false),
            NoticeDigest(group(deadline = at(10)), done = true),
            NoticeDigest(group(recurrence = NoticeRecurrenceDto(freq = "daily", time = "07:30")), done = false),
        )
        val stats = NoticeStatusMachine.statsOf(digests, now)
        assertEquals(3, stats.pending)
        assertEquals(2, stats.urgent) // 1h 内 + 6h 内都算 24h 内
        // 契约 §2.4：7 天内包含 24h 内的（1h + 6h + 48h = 3），与 web/mp statOf 同口径
        assertEquals(3, stats.week)
        assertEquals(1, stats.expired)
        assertEquals(1, stats.longterm) // daily 07:30 进行中（now 12:00，下次 明日 07:30）
    }

    @Test
    fun `mostUrgent 优先 alert 再按时间`() {
        val alert = NoticeDigest(group(deadline = at(-5), alert = true))
        val later = NoticeDigest(group(deadline = at(10)))
        val sooner = NoticeDigest(group(deadline = at(2)))
        val picked = NoticeStatusMachine.mostUrgent(listOf(later, sooner, alert), now)
        assertEquals(alert.group, picked?.group)

        val picked2 = NoticeStatusMachine.mostUrgent(listOf(later, sooner), now)
        assertEquals(sooner.group, picked2?.group)
        assertNull(NoticeStatusMachine.mostUrgent(emptyList(), now))
    }

    @Test
    fun `absRelDates 替换今天明天本周与裸周X`() {
        val anchor = LocalDateTime.of(2026, 11, 7, 12, 0) // 周六
            .atZone(zone).toInstant().toEpochMilli()
        val text = "明天上午 9:00 点名；本周五交材料；下周一开会；周日下午训练"
        val replaced = NoticeStatusMachine.absRelDates(text, anchor, zone)
        assertTrue(replaced.contains("11-08上午 9:00 点名"))
        assertTrue(replaced.contains("11-06")) // 本周五（11-02 所在周的周五）
        assertTrue(replaced.contains("11-09")) // 下周一（原文「下周一开会」→「11-09开会」）
        assertTrue(replaced.contains("11-08")) // 裸周X：最近未来周日
    }

    @Test
    fun `absRelDates 不误伤楼号和同学号`() {
        val anchor = LocalDateTime.of(2026, 11, 7, 12, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val text = "请 42号同学到 5号楼 门口"
        val replaced = NoticeStatusMachine.absRelDates(text, anchor, zone)
        assertTrue(replaced.contains("42号同学"))
        assertTrue(replaced.contains("5号楼"))
    }

    @Test
    fun `anchorOf 取第一条带 time 的 item`() {
        val items = listOf(
            kotlinx.serialization.json.buildJsonObject {
                put("kind", kotlinx.serialization.json.JsonPrimitive("text"))
                put("text", kotlinx.serialization.json.JsonPrimitive("hi"))
                put("time", kotlinx.serialization.json.JsonPrimitive("11-05 12:00"))
            },
        )
        val anchor = NoticeStatusMachine.anchorOf(group(items = items))
        assertNotNull(anchor)
        val date = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(anchor!!), zone)
        assertEquals(11, date.monthValue)
        assertEquals(5, date.dayOfMonth)
    }
}
