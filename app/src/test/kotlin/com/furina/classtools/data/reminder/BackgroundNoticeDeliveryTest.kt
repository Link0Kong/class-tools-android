package com.furina.classtools.data.reminder

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.HttpTransport
import com.furina.classtools.data.session.Session
import com.furina.classtools.testApiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, instrumentedPackages = ["com.vivo.push.sdk"])
class BackgroundNoticeDeliveryTest {
    @Test fun aFreshScanPostsANewNoticeToTheSystemWithoutOpeningAnActivity() = runBlocking {
        val app: Application = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val owner = Session("3260799999", "测试账号", "fake-token")
        val notices = mutableListOf("""{"id":"manual-old","title":"旧通知","body":"测试","ts":1}""")
        suspend fun scan(): ReminderRunResult {
            val http = HttpTransport { request ->
                val response = if ("marks?" in request.url) "[]" else notices.joinToString(",", "[", "]")
                ApiResponse(200, response)
            }
            val feeds = loadReminderFeeds(testApiClient(http, owner.token), owner.sid, setOf("nt"))
            return ReminderEngine(app, BackgroundReminderData(app, owner, feeds), owner.sid).runNow()
        }

        assertEquals(0, scan().sent)
        notices += """{"id":"manual-new","title":"后台新通知","body":"测试","ts":2}"""
        assertEquals(1, scan().sent)
        assertEquals(0, scan().sent)
        val system = app.getSystemService(NotificationManager::class.java)
        val card = system.activeNotifications.single { it.id == "nt-new:manual-new".hashCode() }
        assertEquals("后台新通知", card.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }
}
