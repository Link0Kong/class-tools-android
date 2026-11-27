package com.furina.classtools.data.notify

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.furina.classtools.R
import com.furina.classtools.data.transfer.TransferActivity
import com.furina.classtools.data.transfer.TransferStage
import com.furina.classtools.data.reminder.AndroidReminderNotifier
import com.furina.classtools.data.reminder.CourseActivities
import com.furina.classtools.data.reminder.CourseReminderInfo
import com.furina.classtools.data.reminder.ReminderEvent
import com.furina.classtools.data.reminder.ReminderKind
import com.furina.classtools.data.reminder.setLiveCourseContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, instrumentedPackages = ["com.vivo.push.sdk"])
class LiveActivityNotificationTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private fun posted(): Notification =
        app.getSystemService(NotificationManager::class.java).activeNotifications.single().notification

    @Before fun allowNotifications() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test fun updatesOldChannelDescriptionAndPreservesChannelSettings() {
        val manager = app.getSystemService(NotificationManager::class.java)
        val channelId = LiveUpdateNotifier.CHANNEL_ID
        manager.createNotificationChannel(
            NotificationChannel(channelId, "实时活动", NotificationManager.IMPORTANCE_NONE).apply {
                description = "上传进度、限时投票倒计时等实时活动（支持 Android 16 实时更新/流体云）"
            },
        )

        LiveUpdateNotifier.ensureChannel(app)

        val updated = manager.getNotificationChannel(channelId)
        assertEquals("上传进度、限时投票倒计时等实时通知", updated.description)
        assertEquals(NotificationManager.IMPORTANCE_NONE, updated.importance)
    }

    @Test fun expandedCourseUsesCampusClockAndOnlyShowsAvailableDetails() {
        val start = Instant.parse("2026-11-25T00:00:00Z").toEpochMilli()
        val course = CourseReminderInfo("高等数学", "教学楼 202", start, start + 95 * 60_000L)
        val event = ReminderEvent(ReminderKind.COURSE_SOON, "class:$start", start - 600_000L,
            "上课提醒 · 高等数学", "", course = course)
        fun card(event: ReminderEvent): Notification = NotificationCompat.Builder(app, "course")
            .apply { setLiveCourseContent(app, event, event.body) }.build()
        val notice = card(event)
        assertEquals(R.drawable.ic_live_course, notice.smallIcon.resId)
        assertEquals("高等数学", notice.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("教学楼 202", notice.extras.getString(Notification.EXTRA_TEXT))
        assertEquals("教室  教学楼 202\n时间  08:00–09:35", notice.extras.getString(Notification.EXTRA_BIG_TEXT))
        val noRoom = card(event.copy(course = course.copy(room = "", endAt = start)))
        assertEquals("时间  08:00", noRoom.extras.getString(Notification.EXTRA_BIG_TEXT))
        val legacy = card(event.copy(course = null, body = "08:00–09:35 · 教学楼 202"))
        assertEquals("高等数学", legacy.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("08:00–09:35 · 教学楼 202", legacy.extras.getString(Notification.EXTRA_BIG_TEXT))
    }

    @Test fun expandedCardPreservesLongUnicodeDetailsAndAcademicDate() {
        val start = Instant.parse("2026-11-15T16:10:00Z").toEpochMilli()
        val name = "计算机组成原理与综合实验".repeat(5)
        val room = "第一教学楼信息工程实验中心".repeat(4) + " B302"
        val focus = com.furina.classtools.data.timetable.CourseFocus("2026-11-16", 3, 5, name, room)
        val course = CourseReminderInfo(name, room, start, start + 45 * 60_000, focus, "第4节", "张老师", 12)
        val event = ReminderEvent(ReminderKind.COURSE_SOON, "class:$start", start - 60_000, name, "", course = course)
        val card = NotificationCompat.Builder(app, "course").apply { setLiveCourseContent(app, event, "") }.build()
        assertEquals(name, card.extras.getString(Notification.EXTRA_TITLE))
        val expanded = card.extras.getString(Notification.EXTRA_BIG_TEXT).orEmpty()
        for (field in listOf(room, "00:10–00:55", "第4节", "张老师", "11月16日", "第 12 周")) {
            assertTrue(field, expanded.contains(field))
        }
    }

    @Test fun courseReplacementLateAlarmAndUserDismissalAreInstanceScoped() {
        val owner = "3260799999"
        val start = System.currentTimeMillis() + 180_000L
        CourseActivities.bind(app, owner)
        val notifier = AndroidReminderNotifier(app, owner)
        notifier.ensureChannels()
        fun event(name: String) = ReminderEvent(ReminderKind.COURSE_SOON, "class:$start:$name",
            start - 600_000L, name, "101", course = CourseReminderInfo(name, "101", start, start + 2_400_000L))
        val previous = event("数学")
        val replacement = event("语文")
        assertTrue(notifier.notify(previous))
        val old = CourseActivities.current(app, owner)!!
        assertTrue(notifier.notify(replacement))
        val current = CourseActivities.current(app, owner)!!
        CourseActivities.onBoundary(app, owner, old.generation)
        CourseActivities.hide(app, owner, old.generation)
        assertEquals("语文", posted().extras.getString(Notification.EXTRA_TITLE))
        assertEquals("隐藏本次", posted().actions.single().title)
        assertEquals(posted().actions.single().actionIntent, posted().deleteIntent)
        assertEquals(0, posted().flags and Notification.FLAG_FOREGROUND_SERVICE)
        assertNull(shadowOf(app).nextStartedService)
        CourseActivities.hide(app, owner, current.generation)
        notifier.reconcileLiveCourse(replacement, System.currentTimeMillis(), alreadySent = true)
        assertTrue(app.getSystemService(NotificationManager::class.java).activeNotifications.isEmpty())
        assertNull(CourseActivities.current(app, owner))
    }

    @Test fun deniedChannelRetainsInAppCourseWithoutReportingDelivery() {
        val owner = "3260799999"
        val now = System.currentTimeMillis()
        val start = now + 60_000
        val channel = ReminderKind.COURSE_SOON.channelId
        app.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(channel, "课程", NotificationManager.IMPORTANCE_NONE))
        CourseActivities.bind(app, owner)
        val notifier = AndroidReminderNotifier(app, owner)
        val event = ReminderEvent(ReminderKind.COURSE_SOON, "class:$start:blocked", now, "数学", "",
            course = CourseReminderInfo("数学", "", start, start + 120_000))
        assertFalse(notifier.notify(event))
        assertEquals("数学", CourseActivities.current(app, owner)?.info?.name)
        assertTrue(app.getSystemService(NotificationManager::class.java).activeNotifications.isEmpty())
    }

    @Test fun absoluteExpirationNeedsNoTimetableAndCannotReplayAfterClockRollback() {
        val owner = "3260799999"
        val now = System.currentTimeMillis()
        val start = now + 120_000L
        CourseActivities.bind(app, owner)
        val notifier = AndroidReminderNotifier(app, owner)
        notifier.ensureChannels()
        val event = ReminderEvent(ReminderKind.COURSE_SOON, "class:$start", now, "数学", "",
            course = CourseReminderInfo("数学", "", start, start + 60_000L))
        assertTrue(notifier.notify(event))
        CourseActivities.expire(app, start)
        notifier.reconcileLiveCourse(event, now, alreadySent = true)
        assertTrue(app.getSystemService(NotificationManager::class.java).activeNotifications.isEmpty())
        val oldRevision = CourseActivities.accountRevision
        CourseActivities.bind(app, "9999")
        assertFalse(CourseActivities.restore(app, owner, oldRevision))
        assertFalse(notifier.notify(event.copy(key = "other")))
    }

    @Test fun uploadProgressUsesTheSameValueInChipAndExpandedTemplate() {
        LiveUpdateNotifier.renderTransfer(app, TransferActivity("qa", "deck", "d1234567", "课程汇报",
            System.currentTimeMillis(), "version1", stage = TransferStage.UPLOADING, percent = 64))
        val notice = posted()
        assertEquals(R.drawable.ic_live_upload, notice.smallIcon.resId)
        assertEquals("课程汇报", notice.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(64, notice.extras.getInt(Notification.EXTRA_PROGRESS))
        assertEquals(100, notice.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertFalse(notice.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertEquals(LiveCapabilities.canPromote(app, LiveUpdateNotifier.CHANNEL_ID), NotificationCompat.isRequestPromotedOngoing(notice))
        assertEquals("64%", NotificationCompat.getShortCriticalText(notice))
    }

    @Test fun voteExpandsLongSubjectAndLeavesChipToTheSystemTimer() {
        val title = "下周课程展示的分组和时间安排"
        val now = System.currentTimeMillis()
        val localDeadline = now + 300_000L
        LiveUpdateNotifier.postVote(app, VoteWatch("test", "poll", title, now + 7_200_000L, localDeadline, "a"))
        val notice = posted()
        assertEquals(R.drawable.ic_live_vote, notice.smallIcon.resId)
        assertEquals(title, notice.extras.getString(Notification.EXTRA_BIG_TEXT))
        assertTrue(notice.extras.getString(Notification.EXTRA_TEXT).orEmpty().startsWith("截止 "))
        assertTrue(notice.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertTrue(notice.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertEquals(localDeadline, notice.`when`)
        assertTrue(notice.timeoutAfter in 1L..300_000L)
        assertNull(NotificationCompat.getShortCriticalText(notice))
        assertEquals(LiveCapabilities.canPromote(app, LiveUpdateNotifier.CHANNEL_ID), NotificationCompat.isRequestPromotedOngoing(notice))
    }

    @Test fun testCountdownEndsWithTheRequestedInterval() {
        LiveUpdateNotifier.postDebugCountdown(app, 30)
        val notice = posted()
        assertEquals(R.drawable.ic_live_timer, notice.smallIcon.resId)
        assertEquals(30_000L, notice.timeoutAfter)
        assertTrue(notice.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertNull(NotificationCompat.getShortCriticalText(notice))
        LiveUpdateNotifier.postDebugCountdown(app, 0)
        assertTrue(app.getSystemService(NotificationManager::class.java).activeNotifications.isEmpty())
    }
}
