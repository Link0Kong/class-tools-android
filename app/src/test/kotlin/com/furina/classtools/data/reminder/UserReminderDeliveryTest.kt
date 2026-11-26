package com.furina.classtools.data.reminder

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.furina.classtools.data.notify.SignupCountdown
import com.furina.classtools.data.notify.SignupCountdowns
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.session.SessionStore
import com.furina.classtools.data.tasks.deliverTaskReminder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, instrumentedPackages = ["com.vivo.push.sdk"])
class UserReminderDeliveryTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val manager get() = app.getSystemService(NotificationManager::class.java)
    private val owner = "3260799999"

    @Before fun login() = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        SessionStore(app).save(Session(owner, "测试", "local-fixture"))
    }

    @Test fun alarmAndBackupShareOneDeliveryAndCancellationWins() = runBlocking {
        val at = System.currentTimeMillis() - 200
        val key = OneShotKey(OneShotTargetKind.NOTICE, "test/notice", at)
        val store = SharedPrefsOneShotPendingStore(app, owner)
        store.add(OneShotPending(key, "测试通知", at, at + 60_000))
        deliverOneShot(app, key, "different-owner")
        assertEquals(0, manager.activeNotifications.size)
        (1..2).map { async { deliverOneShot(app, key, owner) } }.awaitAll()
        assertTrue(store.load().isEmpty())
        val posted = manager.activeNotifications.single().notification
        assertEquals(at, posted.`when`)
        assertNull(posted.group)
        manager.cancelAll()
        deliverOneShot(app, key, owner)
        assertEquals(0, manager.activeNotifications.size)
    }

    @Test fun completedSnoozedAndStaleTasksCannotRing() = runBlocking {
        val at = System.currentTimeMillis() - 1000
        val prefs = app.getSharedPreferences("tasks_$owner", Context.MODE_PRIVATE)
        fun store(reminder: Long, snooze: Long = 0, done: Boolean = false) {
            prefs.edit().putString("local", """{"items":[{"id":"t","title":"测试任务","remindAt":$reminder,"snoozeUntil":$snooze,"done":$done}]}""").commit()
        }
        store(at, done = true)
        deliverTaskReminder(app, owner, "t", at)
        store(at, snooze = at + 60_000)
        deliverTaskReminder(app, owner, "t", at)
        store(at - ReminderCatchUp.MAX_LOOKBACK_MS)
        deliverTaskReminder(app, owner, "t", at - ReminderCatchUp.MAX_LOOKBACK_MS)
        assertTrue(manager.activeNotifications.isEmpty())
        store(at)
        deliverTaskReminder(app, owner, "t", at)
        assertEquals("测试任务", manager.activeNotifications.single().notification.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(at, prefs.getLong("notified:t", 0))
        manager.cancelAll()
        deliverTaskReminder(app, owner, "t", at)
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Config(sdk = [34, 36])
    @Test fun systemCardChangesAtOpeningAndKeepsSeparateActivityIdentities() = runBlocking {
        val now = System.currentTimeMillis()
        val watch = SignupCountdown(owner, "a/b", "社团报名", now + 45_000, now + 120_000, "礼堂", "社团")
        fun persist(item: SignupCountdown) {
            SignupCountdowns.prefs(app).edit().putString("$owner/${Uri.encode(item.id)}", Json.encodeToString(item)).commit()
        }
        persist(watch)
        SignupCountdowns.onBoundary(app, owner, watch.id)
        val second = watch.copy(id = "b", title = "另一场活动")
        persist(second)
        SignupCountdowns.onBoundary(app, owner, second.id)
        val entries = manager.activeNotifications
        assertEquals(2, entries.size)
        val card = entries.single { it.notification.extras.getString(Notification.EXTRA_TITLE) == watch.title }.notification
        assertEquals(watch.start, card.`when`)
        assertTrue(NotificationCompat.isRequestPromotedOngoing(card))
        if (android.os.Build.VERSION.SDK_INT >= 36) assertTrue(card.hasPromotableCharacteristics())
        assertTrue(card.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals(watch.details(), card.extras.getString(Notification.EXTRA_BIG_TEXT))
        assertEquals(2, entries.map { it.notification.contentIntent }.distinct().size)
        val open = watch.copy(start = now - 1)
        persist(open)
        SignupCountdowns.onBoundary(app, owner, open.id)
        assertTrue(SignupCountdowns.get(app, owner, open.id)!!.opened)
        assertTrue(manager.activeNotifications.any { it.notification.extras.getString(Notification.EXTRA_TEXT).orEmpty().contains("已到报名时间") })
        assertEquals(2, manager.activeNotifications.size)
    }
}
