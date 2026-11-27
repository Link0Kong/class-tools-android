package com.furina.classtools

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.session.SessionState
import com.furina.classtools.data.sync.FileUserDataLocalStore
import com.furina.classtools.features.home.HomeToolsEditor
import java.io.File
import java.io.ByteArrayOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in interaction test on an offline emulator with the dedicated debug identity. */
class HomeToolsInteractionTest {
    @Test
    fun bothEditorsShareTheSaveAndLeaveRules() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("interactionReview") == "yes")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as ClassToolsApp
        assertEquals("Use an offline review device", 1, Settings.Global.getInt(
            app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0,
        ))
        val container = app.container
        val device = UiDevice.getInstance(instrumentation)
        val session = Session("3260799999", "测试账号", token = "", debug = true)
        val originalSession = runBlocking { container.sessionStore.state.first() }
        assumeTrue(originalSession !is SessionState.SignedIn ||
            (originalSession.session.debug && originalSession.session.sid == session.sid))
        val directory = File(app.filesDir, "userdata")
        val file = File(directory, "userdata_${session.sid}.json")
        val originalFile = file.takeIf { it.isFile }?.readBytes()
        val local = FileUserDataLocalStore(directory, { session.sid })
        val baseline = listOf("tt", "nt")
        val saved = baseline + "draw"
        val leaveDialog = By.text("离开常用工具编辑？")
        var host: MainActivity? = null

        fun assertStored(expected: List<String>) = runBlocking {
            assertEquals(expected, HomeToolsEditor.read(local.readCurrent()))
        }

        fun waitFor(selector: BySelector, timeout: Long = 4_000): UiObject2 =
            requireNotNull(device.wait(Until.findObject(selector), timeout)) {
                "Missing $selector"
            }

        fun scrollTo(selector: BySelector): UiObject2 {
            val x = device.displayWidth * 2 / 3
            val top = device.displayHeight / 3
            val bottom = device.displayHeight * 3 / 4
            val aboveDock = device.displayHeight - (106 * app.resources.displayMetrics.density).toInt()
            for ((from, to) in listOf(bottom to top, top to bottom)) {
                repeat(6) {
                    if (device.wait(Until.hasObject(selector), 450)) {
                        device.waitForIdle(800)
                        // The floating dock can overlap the scroll viewport near its bottom.
                        try {
                            device.findObject(selector)?.let { if (it.visibleCenter.y in top / 2..aboveDock) return it }
                        } catch (_: StaleObjectException) {
                            // A settling pager or scroll can recycle the semantics node.
                        }
                    }
                    device.swipe(x, from, x, to, 70)
                    SystemClock.sleep(320)
                }
            }
            return waitFor(selector)
        }

        fun scrollTo(label: String): UiObject2 = scrollTo(By.text(label).pkg(app.packageName))

        fun activateLabel(label: String) {
            // Read the coordinates from a fresh hierarchy after the scroll has settled.
            val output = ByteArrayOutputStream()
            device.dumpWindowHierarchy(output)
            val hierarchy = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(output.toByteArray().inputStream())
            val nodes = hierarchy.getElementsByTagName("node")
            val target = (0 until nodes.length).asSequence().map { nodes.item(it) as org.w3c.dom.Element }
                .first { it.getAttribute("package") == app.packageName && it.getAttribute("text") == label }
            val bounds = Regex("\\d+").findAll(target.getAttribute("bounds")).map { it.value.toInt() }.toList()
            device.executeShellCommand("input tap ${(bounds[0] + bounds[2]) / 2} ${(bounds[1] + bounds[3]) / 2}")
        }

        fun swipeToTools() {
            val y = device.displayHeight / 2
            assertTrue(device.swipe(device.displayWidth * 4 / 5, y, device.displayWidth / 5, y, 24))
            waitFor(leaveDialog)
        }

        try {
            runBlocking {
                container.updateActiveSession(null)
                local.writeCurrent(JsonObject(local.readCurrent() + ("homeTools" to HomeToolsEditor.section(baseline))))
                container.sessionStore.save(session)
            }
            host = instrumentation.startActivitySync(Intent(app, MainActivity::class.java).apply {
                action = "com.furina.classtools.widget.OPEN.home"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }) as MainActivity

            scrollTo("编辑")
            activateLabel("编辑")
            scrollTo("完成")
            scrollTo("抽签")
            activateLabel("抽签")
            assertStored(baseline)
            swipeToTools()
            waitFor(By.text("继续编辑")).click()
            assertTrue(device.wait(Until.gone(leaveDialog), 4_000))
            assertStored(baseline)
            device.pressBack()
            waitFor(leaveDialog)
            waitFor(By.text("继续编辑")).click()
            assertTrue(device.wait(Until.gone(leaveDialog), 4_000))
            swipeToTools()
            waitFor(By.text("保存并离开")).click()
            waitFor(By.text("全部工具"))
            assertStored(saved)

            waitFor(By.desc("编辑首页快捷")).click()
            scrollTo("分组")
            activateLabel("分组")
            assertStored(saved)
            waitFor(By.text("取消")).click()
            assertStored(saved)
            waitFor(By.desc("编辑首页快捷")).click()
            device.pressBack()
            waitFor(By.desc("编辑首页快捷"))
            assertTrue(device.wait(Until.gone(leaveDialog), 4_000))

            waitFor(By.descContains("工作台，第 1 个 Tab")).click()
            val timetableTile = By.desc("课表")
            val noticesTile = By.desc("通知")
            val first = scrollTo(timetableTile).visibleCenter
            val second = waitFor(noticesTile).visibleCenter
            assertTrue(device.drag(first.x, first.y, second.x, second.y, 35))
            scrollTo("完成")
            assertTrue(waitFor(timetableTile).visibleCenter.x > waitFor(noticesTile).visibleCenter.x)
            assertStored(saved)
            waitFor(By.text("取消")).click()
            waitFor(By.text("编辑"))
            assertStored(saved)
            assertTrue(waitFor(timetableTile).visibleCenter.x < waitFor(noticesTile).visibleCenter.x)
        } catch (failure: Throwable) {
            device.dumpWindowHierarchy(File(app.cacheDir, "home-tools-interaction-failure.xml"))
            throw failure
        } finally {
            instrumentation.runOnMainSync { host?.finish() }
            runBlocking {
                container.updateActiveSession(null)
                if (originalFile == null) file.delete() else file.writeBytes(originalFile)
                if (originalSession is SessionState.SignedIn) container.sessionStore.save(originalSession.session)
                else container.sessionStore.clear()
            }
        }
    }
}
