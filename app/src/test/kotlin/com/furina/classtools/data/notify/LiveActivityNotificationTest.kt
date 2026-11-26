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
import com.furina.classtools.data.ppt.PptUploadPhase
import com.furina.classtools.data.ppt.PptUploadProgress
import com.furina.classtools.data.reminder.AndroidReminderNotifier
import com.furina.classtools.data.reminder.CourseLiveService
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
        assertEquals("08:00–09:35 · 教学楼 202", notice.extras.getString(Notification.EXTRA_TEXT))
        assertEquals("时间  08:00–09:35\n教室  教学楼 202", notice.extras.getString(Notification.EXTRA_BIG_TEXT))
        val noRoom = card(event.copy(course = course.copy(room = "", endAt = start)))
        assertEquals("时间  08:00", noRoom.extras.getString(Notification.EXTRA_BIG_TEXT))
        val legacy = card(event.copy(course = null, body = "08:00–09:35 · 教学楼 202"))
        assertEquals("高等数学", legacy.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("08:00–09:35 · 教学楼 202", legacy.extras.getString(Notification.EXTRA_BIG_TEXT))
    }

    @Test fun courseSurvivesTaskRemovalAndRestoresTheSameDeadlineIfTheCardIsCleared() {
        val now = System.currentTimeMillis()
        val start = now + 180_000L
        val event = ReminderEvent(ReminderKind.COURSE_SOON, "class:$start", now, "数学", "101",
            course = CourseReminderInfo("数学", "101", start, start + 2_400_000L))
        val notifier = AndroidReminderNotifier(app, supportsLiveCourse = true)
        notifier.ensureChannels()
        assertTrue(notifier.notify(event))
        val serviceIntent = shadowOf(app).nextStartedService
        assertEquals(CourseLiveService::class.java.name, serviceIntent.component?.className)
        val controller = Robolectric.buildService(CourseLiveService::class.java).create()
        val service = controller.get()
        service.onStartCommand(serviceIntent, 0, 1)
        service.onTaskRemoved(Intent())
        val lifecycle = shadowOf(service)
        assertFalse(lifecycle.isStoppedBySelf)
        assertEquals(event.notificationId, lifecycle.lastForegroundNotificationId)
        assertEquals(start, lifecycle.lastForegroundNotification.`when`)
        assertEquals(0, posted().flags and Notification.FLAG_AUTO_CANCEL)
        app.getSystemService(NotificationManager::class.java).cancelAll()
        val reopened = AndroidReminderNotifier(app, supportsLiveCourse = true)
        reopened.reconcileLiveCourse(event, System.currentTimeMillis(), alreadySent = true)
        assertEquals(start, posted().`when`)
        assertTrue(NotificationCompat.isRequestPromotedOngoing(posted()))

        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMinutes(3))
        assertEquals(1, lifecycle.stopSelfResultId)
        controller.destroy()
        reopened.reconcileLiveCourse(null, start)
        assertTrue(app.getSystemService(NotificationManager::class.java).activeNotifications.isEmpty())
    }

    @Test fun uploadProgressUsesTheSameValueInChipAndExpandedTemplate() {
        LiveUpdateNotifier.postPptUpload(app, PptUploadProgress(percent = 64, phase = PptUploadPhase.UPLOADING, title = "课程汇报"))
        val notice = posted()
        assertEquals(R.drawable.ic_live_upload, notice.smallIcon.resId)
        assertEquals("课程汇报", notice.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(64, notice.extras.getInt(Notification.EXTRA_PROGRESS))
        assertEquals(100, notice.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertFalse(notice.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertTrue(NotificationCompat.isRequestPromotedOngoing(notice))
        assertEquals("64%", NotificationCompat.getShortCriticalText(notice))
    }

    @Test fun voteExpandsLongSubjectAndLeavesChipToTheSystemTimer() {
        val title = "下周课程展示的分组和时间安排"
        val now = System.currentTimeMillis()
        val localDeadline = now + 300_000L
        LiveUpdateNotifier.postVoteDeadline(app, title, now + 7_200_000L, localDeadline)
        val notice = posted()
        assertEquals(R.drawable.ic_live_vote, notice.smallIcon.resId)
        assertEquals(title, notice.extras.getString(Notification.EXTRA_BIG_TEXT))
        assertTrue(notice.extras.getString(Notification.EXTRA_TEXT).orEmpty().startsWith("截止 "))
        assertTrue(notice.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertTrue(notice.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertEquals(localDeadline, notice.`when`)
        assertTrue(notice.timeoutAfter in 1L..300_000L)
        assertNull(NotificationCompat.getShortCriticalText(notice))
        assertTrue(NotificationCompat.isRequestPromotedOngoing(notice))
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
