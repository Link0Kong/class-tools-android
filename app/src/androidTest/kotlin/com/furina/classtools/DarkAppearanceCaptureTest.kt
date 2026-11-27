package com.furina.classtools

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.setContent
import com.furina.classtools.data.cloudspace.CloudSpaceFile
import com.furina.classtools.data.cloudspace.CloudSpaceSession
import com.furina.classtools.data.cloudspace.CloudSpaceSnapshot
import com.furina.classtools.data.ppt.PptDeck
import com.furina.classtools.ui.ClassToolsThemeHost
import com.furina.classtools.ui.screens.cloudspace.CloudSpaceContent
import com.furina.classtools.ui.screens.ppt.PptListContent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.furina.classtools.data.background.CtBackgroundPreset
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.tasks.newTask
import com.furina.classtools.data.tasks.withFields
import com.furina.classtools.ui.theme.AppearanceMode
import com.furina.classtools.ui.theme.CtLightSetting
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.put
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in capture on a dedicated, offline review emulator. Uses real activities and local test data. */
class DarkAppearanceCaptureTest {
    @Test fun capture() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("darkReview") == "yes")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as ClassToolsApp
        val container = app.container
        val device = UiDevice.getInstance(instrumentation)
        val config = app.resources.configuration
        val folder = File(app.filesDir, "dark-review/${config.screenWidthDp}dp-${config.fontScale}")
        folder.mkdirs()
        val session = Session("3260799999", "测试账号", token = "", debug = true)
        val title = "整理课程笔记"
        runBlocking {
            container.appSettings.setAppearanceMode(AppearanceMode.DARK)
            container.appSettings.setHideStatusBar(false)
            container.appSettings.setDynamicColor(false)
            container.appSettings.setImmersiveLight(CtLightSetting.Off)
            container.sessionStore.save(session)
        }
        container.backgroundStore.selectPreset(CtBackgroundPreset.DEFAULT)
        val repository = container.tasksFor(session)
        repository.edit { items, now ->
            if (items.any { it["id"].toString() == "\"dark-review\"" }) items
            else items + newTask(title, now, 0.0).withFields {
                put("id", "dark-review")
                put("due", LocalDate.now().toString())
                put("dueTime", "19:30")
                put("notes", "核对本周讲义和课后练习。")
            }
        }
        fun open(route: String) {
            app.startActivity(Intent(app, MainActivity::class.java).apply {
                action = "com.furina.classtools.widget.OPEN.$route"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
        fun visible(label: String) {
            assertTrue("Missing $label", device.wait(Until.hasObject(By.textContains(label)), 20_000))
        }
        fun shot(name: String) {
            instrumentation.waitForIdleSync()
            device.waitForIdle(1_000)
            SystemClock.sleep(1000)
            instrumentation.waitForIdleSync()
            assertTrue(device.takeScreenshot(File(folder, "$name.png")))
        }

        open("home")
        visible("工作台")
        shot("home-preset")
        for (photo in listOf("white", "black")) {
            val image = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            image.eraseColor(if (photo == "white") android.graphics.Color.WHITE else android.graphics.Color.BLACK)
            val source = File(app.cacheDir, "review-$photo.png")
            source.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
            assertTrue(runBlocking { container.backgroundStore.importImage(Uri.fromFile(source)) })
            container.backgroundStore.setCustomBlur(if (photo == "white") 40 else 0)
            if (photo == "white" && config.screenWidthDp == 412 && config.fontScale == 1f) {
                for (grade in listOf("weak", "strong")) {
                    runBlocking { container.appSettings.setImmersiveLight(CtLightSetting.fromStorage(grade)) }
                    open("home")
                    visible("工作台")
                    shot("home-white-brand-$grade")
                }
            }
            for (dynamic in listOf(false, true)) {
                runBlocking { container.appSettings.setDynamicColor(dynamic) }
                for (glass in listOf("off", "balanced")) {
                    runBlocking { container.appSettings.setImmersiveLight(CtLightSetting.fromStorage(glass)) }
                    open("home")
                    visible("工作台")
                    shot("home-$photo-${if (dynamic) "dynamic" else "brand"}-$glass")
                }
            }
        }
        runBlocking { container.appSettings.setDynamicColor(false) }
        if (config.screenWidthDp == 412 && config.fontScale == 1f) {
            container.backgroundStore.selectPreset(CtBackgroundPreset.SLATE_BLUE)
            open("home")
            visible("工作台")
            shot("home-blue")
        }
        container.backgroundStore.selectPreset(CtBackgroundPreset.WARM_SAND)
        val tools = device.findObject(By.text("工具"))
        requireNotNull(tools) { "工具 navigation missing" }.click()
        visible("全部工具")
        shot("tools-sand")
        val x = device.displayWidth * 2 / 3
        val top = device.displayHeight / 3
        val bottom = device.displayHeight * 3 / 4
        fun scrollCycle() {
            device.swipe(x, bottom, x, top, 20)
            device.waitForIdle(500)
            device.swipe(x, top, x, bottom, 20)
            device.waitForIdle(500)
        }
        scrollCycle()
        device.executeShellCommand("dumpsys gfxinfo ${app.packageName} reset")
        repeat(4) { scrollCycle() }
        File(folder, "scroll-gfxinfo.txt").writeText(device.executeShellCommand("dumpsys gfxinfo ${app.packageName}"))

        open("hw")
        visible(title)
        shot("tasks")
        device.findObject(By.text(title)).click()
        visible("截止时间")
        shot("task-detail")
        open("new-task")
        visible("标题")
        shot("task-editor")

        // The two network lists use their production content composables with identical local rows.
        val host = instrumentation.startActivitySync(Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }) as MainActivity
        instrumentation.runOnMainSync {
            host.setContent {
                ClassToolsThemeHost(container) {
                    PptListContent(decks = listOf(
                        PptDeck("ready", "课堂演示", session.sid, session.name, "ready", slideCount = 12),
                        PptDeck("failed", "实验记录", session.sid, session.name, "failed"),
                    ), agentOnline = true, mySid = session.sid, imageLoader = container.pptImageLoader,
                        loading = false, errorMessage = null, offlineHint = false, uploadBar = null, uploadFailedMessage = null,
                        onBack = {}, onUpload = {}, onOpenDetail = {}, onDelete = {}, onRetry = {}, onCancelUpload = {},
                        onDismissUploadError = {}, onRefresh = {})
                }
            }
        }
        visible("课堂演示")
        shot("ppt-fixture")
        val now = 1795629600000L
        instrumentation.runOnMainSync {
            host.setContent {
                ClassToolsThemeHost(container) {
                    CloudSpaceContent(CloudSpaceSnapshot(CloudSpaceSession("review"),
                        listOf(CloudSpaceFile("f1", "本周讲义.pdf", 2097152, uploadedAt = now - 3600000))),
                        loading = false, errorMessage = null, uploads = emptyList(), downloadingIds = emptySet(),
                        downloadedIds = setOf("f1"), nowMillis = now, onBack = {}, onRefresh = {}, onUpload = {},
                        onClear = {}, onDownload = {}, onDelete = {}, onCancelUpload = {}, onRetry = {})
                }
            }
        }
        visible("文件传输")
        shot("files-fixture")
        instrumentation.runOnMainSync { host.finish() }
    }
}
