package com.furina.classtools.data.notify

import com.furina.classtools.data.calendar.ReminderCalendar
import com.furina.classtools.data.notices.*
import com.furina.classtools.data.reminder.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class SignupReminderTest {
    private val now = Instant.parse("2026-11-26T00:00:00Z").toEpochMilli()
    private fun stamp(delta: Long) = Instant.ofEpochMilli(now + delta).toString()
    private val group get() = NoticeGroupDto(id = "id:with/slash", title = "活动报名", source = "社团",
        aiSegments = listOf(NoticeSegment("地点", "礼堂")), signupStart = stamp(9_000_000),
        signupWindows = listOf(NoticeSignupWindow(stamp(900_000), stamp(910_000)),
            NoticeSignupWindow(stamp(-100), stamp(-10)), NoticeSignupWindow(stamp(120_123), stamp(180_000))))

    @Test fun selectsNextValidRoundAndKeepsItsMillisecondTimestampAndEnd() {
        val watch = SignupCountdown.from("qa", group, emptyList(), now)!!
        assertEquals(now + 120_123, watch.start)
        assertEquals(now + 180_000, watch.end)
        assertEquals(now + 180_000, watch.expiresAt)
        assertTrue(watch.details().contains("礼堂"))
        assertTrue(watch.details().contains("社团"))
        assertEquals(now + 900_000, SignupCountdown.from("qa", group, emptyList(), now + 120_124)!!.start)
        assertNull(SignupCountdown.from("qa", group.copy(signupStart = "", signupWindows = emptyList(),
            eventAt = stamp(80_000), deadline = stamp(120_000)), emptyList(), now))
    }

    @Test fun distinguishesSignupLinksFromUnrelatedAttachments() {
        fun link(title: String, url: String) = NoticeItemMeta.Link("link", title, title, "", "", true, url)
        val items = listOf(link("课程资料", "https://example.org/course"), link("报名", "file:///example"),
            link("活动报名表", "https://example.org/signup"))
        assertEquals("https://example.org/signup", SignupCountdown.from("qa", group, items, now)!!.signupUrl)
        val custom = SignupCountdown.from("qa", group, emptyList(), now, customStart = now + 10_346)!!
        assertTrue(custom.customTime)
        assertNull(custom.end)
    }

    @Test fun progressivelyRefinesAndNeverDisplaysZeroEarly() {
        for ((left, label) in listOf(3_600_001L to "1小时 1分", 3_600_000L to "60:00",
            60_001L to "01:01", 60_000L to "60.0 秒", 10_001L to "10.1 秒", 10_000L to "10.000 秒",
            123L to "0.123 秒", 1L to "0.001 秒", 0L to "已到报名时间", -2L to "已到报名时间")) {
            assertEquals(label, SignupClock.label(left))
        }
        for (left in listOf(3_600_001L, 60_001L, 10_001L)) assertEquals(1L, SignupClock.delayMillis(left))
        assertEquals(0L, SignupClock.delayMillis(10_000))
    }

    @Test fun reminderPickerUsesSignupAndAllowsFinalMinute() {
        val options = ReminderOneShot.noticePlannedTimes(group, now, ZoneOffset.UTC)
        assertEquals(listOf(OneShotReminderOption.BEFORE_SIGNUP_1_MIN, OneShotReminderOption.AT_SIGNUP), options.map { it.option })
        assertEquals(now + 60_123, options.first().fireAtMillis)
        assertTrue(ReminderOneShot.noticePlannedTimes(group, now + 120_122, ZoneOffset.UTC).any { it.fireAtMillis == now + 120_123 })
        val legacy = OneShotPendingCodec.decode("""[{"id":"g","kind":"NOTICE","label":"旧提醒","fireAt":1400}]""").single()
        assertEquals(1400L, legacy.targetAtMillis)
        val saved = legacy.copy(targetAtMillis = 90_000)
        assertEquals(saved, OneShotPendingCodec.decode(OneShotPendingCodec.encode(listOf(saved))).single())
    }

    @Test fun genericSignupNeverFiresEarlyOrReplaysAfterRegistrationOpens() {
        val group = group.copy(signupWindows = emptyList(), signupStart = stamp(10 * 60_000))
        val entry = NoticeEntry(NoticeRow(id = "auto_test"), group, emptyList(), false, false)
        val data = NoticesData(listOf(entry), emptyList(), 0, now, false)
        val event = ReminderPlanner.planNoticeSignup(data, nowMillis = now, zone = ZoneOffset.UTC).single()
        val fireAt = now + 5 * 60_000
        assertEquals(fireAt, event.triggerAtMillis)
        assertTrue(ReminderPlanner.filterDue(listOf(event), fireAt - 1).isEmpty())
        assertEquals(listOf(event), ReminderPlanner.filterDue(listOf(event), fireAt))
        assertEquals(entry.id, ReminderIntentFactory.focusFor(event.key))
        assertTrue(ReminderPendingCatchUp.dueCatchUp(listOf(event), fireAt + 360_000, 3_600_000).isEmpty())
    }

    @Test fun calendarTracksUserSelectedTimeAndSnooze() {
        fun task(done: Boolean) = buildJsonObject {
            put("id", "t"); put("title", "复习"); put("done", done)
            put("remindAt", now + 60_000); put("snoozeUntil", now + 300_000); put("due", "2026-12-25")
        }
        val event = ReminderCalendar.task("qa", task(false), now)!!
        assertEquals(now + 300_000, event.beginMillis)
        assertEquals(0, event.reminderMinutes)
        assertEquals("reminder:qa:task:t", event.sourceKey)
        assertNull(ReminderCalendar.task("qa", task(true), now))
        assertNull(ReminderCalendar.task("qa", task(false), now + 400_000))
    }
}
