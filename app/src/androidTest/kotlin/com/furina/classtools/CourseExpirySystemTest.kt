package com.furina.classtools

import androidx.test.core.app.ActivityScenario
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationManager
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.core.app.NotificationCompat
import com.furina.classtools.data.notify.buildLiveUpdate
import com.furina.classtools.data.reminder.AndroidReminderNotifier
import com.furina.classtools.data.reminder.CourseActivities
import com.furina.classtools.data.reminder.CourseReminderInfo
import com.furina.classtools.data.reminder.ReminderEvent
import com.furina.classtools.data.reminder.ReminderKind
import com.furina.classtools.data.reminder.reminderOwner
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.session.SessionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class CourseExpirySystemTest {
    @Test fun platformReportsTemplateCompatibilitySeparatelyFromPromotionPermission() {
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 36)
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        AndroidReminderNotifier(app).ensureChannels()
        val mgr = app.getSystemService(NotificationManager::class.java)
        fun builder() = NotificationCompat.Builder(app, ReminderKind.COURSE_SOON.channelId)
            .setSmallIcon(R.drawable.ic_live_course).setContentTitle("课程测试").setContentText("即将上课")
            .setStyle(NotificationCompat.BigTextStyle().bigText("测试教室"))
            .setRequestPromotedOngoing(true).setOngoing(true)
        val standard = builder().build().hasPromotableCharacteristics()
        val colorized = builder().setColorized(true).build().hasPromotableCharacteristics()
        val selected = builder().buildLiveUpdate().hasPromotableCharacteristics()
        android.util.Log.i("CourseExpiryQA", "template api=${android.os.Build.VERSION.SDK_INT} " +
            "systemPermission=${mgr.canPostPromotedNotifications()} standard=$standard colorized=$colorized selected=$selected")
        assertEquals(standard || colorized, selected)
    }

    @Test fun expiresWithoutForegroundServiceOrManualReconciliation(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as ClassToolsApp
        val store = app.container.sessionStore
        val session = Session("3260799999", "测试账号", token = "", debug = true)
        val saved = store.state.first { it != SessionState.Loading }
        assumeTrue(saved !is SessionState.SignedIn || saved.session.debug && saved.session.sid == session.sid)
        val manager = app.getSystemService(NotificationManager::class.java)
        val device = UiDevice.getInstance(instrumentation)
        val doze = InstrumentationRegistry.getArguments().getString("courseDoze") == "yes"
        val removeTask = InstrumentationRegistry.getArguments().getString("courseRemoveTask") == "yes"
        val originallyInteractive = device.isScreenOn
        var scenario: ActivityScenario<MainActivity>? = null
        val owner = session.reminderOwner
        try {
            store.save(session)
            app.container.prepareSession(session)
            val notifier = AndroidReminderNotifier(app, owner)
            notifier.ensureChannels()
            if (removeTask) scenario = ActivityScenario.launch(MainActivity::class.java)
            val now = System.currentTimeMillis()
            val start = now + 8_000L
            val event = ReminderEvent(ReminderKind.COURSE_SOON, "class:$start:test", now, "系统测试课程", "",
                course = CourseReminderInfo("系统测试课程", "测试教室", start, start + 30_000L, trackLive = true))
            assertTrue(notifier.notify(event))
            fun card() = manager.activeNotifications.firstOrNull { it.id == event.notificationId }
            val publicationLimit = SystemClock.elapsedRealtime() + 3_000
            while (card() == null && SystemClock.elapsedRealtime() < publicationLimit) SystemClock.sleep(50)
            val posted = card()
            assertNotNull("需要授予系统通知权限", posted)
            assertEquals(0, posted!!.notification.flags and Notification.FLAG_FOREGROUND_SERVICE)
            android.util.Log.i("CourseExpiryQA", "posted deadline=$start postedAt=${posted.postTime} " +
                "flags=${posted.notification.flags} exact=${com.furina.classtools.data.reminder.UserReminderAlarms.exactAllowed(app)} doze=$doze")
            scenario?.onActivity { it.finishAndRemoveTask() }
            if (removeTask) android.util.Log.i("CourseExpiryQA", "removed application task before bell")
            if (doze) {
                device.sleep()
                device.executeShellCommand("cmd deviceidle force-idle deep")
                assertTrue(device.executeShellCommand("cmd deviceidle get deep").contains("IDLE"))
            }
            SystemClock.sleep((start - System.currentTimeMillis() - 800L).coerceAtLeast(0))
            assertNotNull("课程提前退出", card())
            val limit = SystemClock.elapsedRealtime() + 15_000L
            while (card() != null && SystemClock.elapsedRealtime() < limit) SystemClock.sleep(100L)
            assertNull("系统未自行撤销过期通知", card())
            android.util.Log.i("CourseExpiryQA", "absent at=${System.currentTimeMillis()} deadline=$start")
            @Suppress("DEPRECATION")
            val services = app.getSystemService(ActivityManager::class.java).getRunningServices(100)
            assertFalse(services.any { it.foreground && it.service.className.endsWith(".CourseLiveService") })
            assertFalse("过期课程不能重新发出", notifier.notify(event))
            assertNull(card())
            android.util.Log.i("CourseExpiryQA", "no course FGS; expired post rejected")
        } finally {
            scenario?.close()
            if (doze) {
                device.executeShellCommand("cmd deviceidle unforce")
                if (originallyInteractive) device.wakeUp()
            }
            if (saved is SessionState.SignedIn) store.save(saved.session) else store.clear()
            CourseActivities.bind(app, (saved as? SessionState.SignedIn)?.session?.reminderOwner)
        }
    }
}
