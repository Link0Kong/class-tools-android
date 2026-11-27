@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.furina.classtools.data.notify

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.work.Configuration
import androidx.work.WorkManager
import com.furina.classtools.ClassToolsApp
import com.furina.classtools.MainActivity
import com.furina.classtools.data.reminder.reminderOwner
import com.furina.classtools.data.session.Session
import com.furina.classtools.ui.navigation.IncomingNavigation
import com.furina.classtools.ui.navigation.Routes
import com.furina.classtools.ui.screens.notices.SignupCountdownActivity
import java.time.Duration
import org.robolectric.shadows.ShadowDialog
import kotlinx.serialization.json.Json
import com.furina.classtools.data.transfer.TransferActivity
import com.furina.classtools.data.transfer.TransferStage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// A complete activity hosts a window recomposer and application-lifetime data stores. Keep its sandbox
// separate from lightweight Compose component tests that use a plain Application.
@Config(sdk = [34], application = ClassToolsApp::class,
    instrumentedPackages = ["com.vivo.push.sdk", "com.furina.classtools.ui.navigation"])
class ActivityEntryInteractionTest {
    private val app: ClassToolsApp get() = RuntimeEnvironment.getApplication() as ClassToolsApp
    private val session = Session("3260799999", "报名测试", token = "", debug = true)
    private val owner = session.reminderOwner
    private val main get() = shadowOf(Looper.getMainLooper())

    @Before fun setUp() {
        if (runCatching { WorkManager.getInstance(app) }.isFailure)
            WorkManager.initialize(app, Configuration.Builder().setTaskExecutor { }.build())
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After fun releaseFixture() {
        SignupCountdowns.all(app).filter { it.owner == owner }.forEach { SignupCountdowns.cancel(app, owner, it.id) }
        app.container.transferActivities.sessionChanged(null)
        runBlocking { app.container.sessionStore.clear() }
    }

    @Test fun duplicateActivityIntentsKeepOnePendingTargetAndItsParentUntilLogin() {
        for ((kind, parent) in listOf("file" to Routes.tool("file"), "vote" to Routes.VOTE)) {
            val id = "qa/对象-1"
            val first = ActivityLinks.intent(app, owner, kind, id)
            val controller = Robolectric.buildActivity(MainActivity::class.java, first).create()
            fun saved(): IncomingNavigation = IncomingNavigation.restore(Bundle().also { controller.saveInstanceState(it) })!!
            val pending = saved()
            val firstInstance = pending.id
            controller.newIntent(ActivityLinks.intent(app, owner, kind, id))
            val duplicate = saved()
            assertEquals(firstInstance, duplicate.id)
            assertEquals(parent, duplicate.activityParent)
            assertEquals(if (kind == "file") Routes.tool("file", focus = id) else Routes.voteDetail(android.net.Uri.encode(id)), duplicate.route)
            assertTrue(duplicate.isFor(session))
            assertFalse(duplicate.isFor(session.copy(sid = "3260799998")))
            controller.newIntent(ActivityLinks.intent(app, owner, kind, "different"))
            assertNotEquals(firstInstance, saved().id)
            controller.destroy()
        }
    }

    @Config(instrumentedPackages = ["com.vivo.push.sdk", "com.furina.classtools.ui.screens.notices"])
    @Test fun permissionDeniedStillOpensTheCountdownAndNoticeAndSupportsCompactAndClose() {
        runBlocking { app.container.sessionStore.save(session) }
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
        assertFalse(SignupCountdowns.available(app))
        val watch = SignupCountdown(owner, "qa/signup", "测试报名", System.currentTimeMillis() + 300_000L)
        assertTrue(SignupCountdowns.start(app, watch))
        val launch = SignupCountdowns.timerIntent(app, owner, watch.id)
        val controller = Robolectric.buildActivity(SignupCountdownActivity::class.java, launch).setup()
        val timer = controller.get()
        val view = timer.window.decorView
        assertNotNull(SignupCountdowns.get(app, owner, watch.id))
        await({ "Timer finishing=" + timer.isFinishing + ", lifecycle=" + timer.lifecycle.currentState +
            ", watch=" + SignupCountdowns.get(app, owner, watch.id)?.instance +
            ", intent=" + launch.data?.lastPathSegment + ", nodes=" + view.nodes().mapNotNull { it.text() }.toList() }) {
            view.nodes().any { it.text() == "查看活动原文" }
        }
        val openNotice = find(view, "查看活动原文")
        assertTrue(openNotice.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
        val notice = shadowOf(timer).nextStartedActivity
        val target = ActivityLinks.navigation(notice)!!
        assertEquals(Routes.tool("nt", focus = watch.id), target.route)
        assertTrue(target.isFor(session))
        val pin = find(view, "悬浮小窗")
        assertTrue(pin.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
        assertTrue(timer.isInPictureInPictureMode)
        timer.onPictureInPictureModeChanged(true, timer.resources.configuration)
        assertNotNull(find(view, watch.title))
        await { view.nodes().none { it.text() == "结束倒计时" } }
        timer.onPictureInPictureModeChanged(false, timer.resources.configuration)
        val close = find(view, "结束倒计时")
        assertTrue(close.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
        assertNull(SignupCountdowns.get(app, owner, watch.id))
        SignupCountdowns.restore(app, owner)
        assertNull(SignupCountdowns.get(app, owner, watch.id))
        assertTrue(app.getSystemService(NotificationManager::class.java).activeNotifications.isEmpty())
        controller.pause().stop().destroy()
    }

    @Test fun blockedNotificationsDoNotPreventExpiryOrResurrectTheWatch() = runBlocking {
        app.container.sessionStore.save(session)
        val now = System.currentTimeMillis()
        val watch = SignupCountdown(owner, "qa/expire", "过期样例", now + 1_000L, end = now + 1_300L)
        assertTrue(SignupCountdowns.start(app, watch))
        await { System.currentTimeMillis() > watch.expiresAt }
        SignupCountdowns.onBoundary(app, owner, watch.id)
        assertNull(SignupCountdowns.get(app, owner, watch.id))
        SignupCountdowns.restore(app, owner)
        assertTrue(app.getSystemService(NotificationManager::class.java).activeNotifications.isEmpty())
    }

    @Test fun doubleClickOpensJustOneVoteOrFileAndBackReturnsToItsList() = runBlocking {
        val proxy = java.net.ProxySelector.getDefault()
        // The UI performs routine page refreshes. Confine each request to this isolated JVM.
        java.net.ProxySelector.setDefault(object : java.net.ProxySelector() {
            override fun select(uri: java.net.URI?) = listOf(java.net.Proxy(java.net.Proxy.Type.HTTP,
                java.net.InetSocketAddress("127.0.0.1", 9)))
            override fun connectFailed(uri: java.net.URI?, address: java.net.SocketAddress?, ioe: java.io.IOException?) = Unit
        })
        try {
            val task = TransferActivity(owner, "file", "qa-file", "离线文件样例", System.currentTimeMillis(),
                "qa-version", stage = TransferStage.FAILED, error = "离线样例")
            app.getSharedPreferences("transfer_activities_v1", android.content.Context.MODE_PRIVATE).edit()
                .putString("$owner/file/qa-file", Json.encodeToString(task)).commit()
            app.container.sessionStore.save(session)
            app.container.prepareSession(session)
            val vote = ActivityLinks.intent(app, owner, "vote", "qa-vote")
            val controller = Robolectric.buildActivity(MainActivity::class.java, vote).setup()
            val activity = controller.get()
            val view = activity.window.decorView
            find(view, "投票详情")
            controller.newIntent(vote)
            main.idleFor(Duration.ofMillis(200))
            activity.onBackPressedDispatcher.onBackPressed()
            find(view, "投票")
            await { view.nodes().none { it.text() == "投票详情" } }

            val file = ActivityLinks.intent(app, owner, "file", task.id)
            controller.newIntent(file)
            await { ShadowDialog.getLatestDialog()?.isShowing == true }
            controller.newIntent(file)
            main.idleFor(Duration.ofMillis(300))
            val dialog = requireNotNull(ShadowDialog.getLatestDialog())
            val sheet = requireNotNull(dialog.window).decorView
            find(sheet, task.title)
            val close = find(sheet, "返回文件传输")
            assertTrue(close.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
            await { ShadowDialog.getLatestDialog()?.isShowing != true }
            find(view, "文件传输")
            controller.pause().stop().destroy()
        } finally {
            java.net.ProxySelector.setDefault(proxy)
        }
        Unit
    }

    private fun find(view: View, text: String): SemanticsNode {
        var result: SemanticsNode? = null
        await({ "Waiting for $text. Visible: " + view.nodes().mapNotNull { it.text() }.toList() }) {
            result = view.nodes().firstOrNull { node -> node.text() == text }
            result != null
        }
        return requireNotNull(result)
    }

    private fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }

    private fun View.nodes(): Sequence<SemanticsNode> = sequence {
        if (this@nodes is RootForTest) {
            suspend fun SequenceScope<SemanticsNode>.walk(node: SemanticsNode) {
                yield(node)
                for (child in node.children) walk(child)
            }
            walk(semanticsOwner.rootSemanticsNode)
        } else if (this@nodes is ViewGroup) {
            for (index in 0 until childCount) yieldAll(getChildAt(index).nodes())
        }
    }

    private fun await(message: () -> String = { "Activity did not reach the expected state" }, condition: () -> Boolean) {
        val until = System.nanoTime() + 12_000_000_000L
        while (System.nanoTime() < until) {
            main.idleFor(Duration.ofMillis(32))
            if (condition()) return
            Thread.sleep(10)
        }
        fail(message())
    }
}
