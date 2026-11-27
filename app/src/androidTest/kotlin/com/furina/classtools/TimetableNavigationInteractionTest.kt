package com.furina.classtools

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.session.SessionState
import com.furina.classtools.data.timetable.Course
import com.furina.classtools.data.timetable.CourseFocus
import com.furina.classtools.data.timetable.TtCalendar
import com.furina.classtools.data.timetable.TtSnapshot
import com.furina.classtools.widget.WidgetIntents
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class TimetableNavigationInteractionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as ClassToolsApp
    private val device = UiDevice.getInstance(instrumentation)
    private val session = Session("3260799999", "测试账号", token = "", debug = true)
    private val today = LocalDate.now()
    private val tomorrow = today.plusDays(1)
    private val courseA = Course("回归课程甲", "回归教师甲", "自强楼(教室01105)", TtCalendar.dayIndex(today), 3, 4)
    private val layoutReview = InstrumentationRegistry.getArguments().getString("courseLayout") == "yes"
    private val courseB = Course(if (layoutReview) "回归课程乙 · 高等数学与应用建模专题实验" else "回归课程乙",
        "回归教师乙", if (layoutReview) "第一教学楼信息工程实验中心东区综合实验楼四层 B302 教室" else "自强楼(教室01106)", TtCalendar.dayIndex(tomorrow), 5, 6)
    private val focusA = CourseFocus(today.toString(), courseA.start, courseA.end, courseA.name, courseA.room).encode()
    private val focusB = CourseFocus(tomorrow.toString(), courseB.start, courseB.end, courseB.name, courseB.room).encode()
    private val mapFromDetail = By.desc("在地图查看教室")
    private val mapFromTimetable = By.desc("打开校园地图")
    private val closeDetail = By.desc("关闭课程详情")
    private var originalSession: SessionState? = null
    private var originalTimetable: TtSnapshot? = null

    @Before fun prepareOfflineFixture() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("interactionReview") == "yes")
        assertEquals("Use an offline review device", 1, Settings.Global.getInt(
            app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0,
        ))
        runBlocking {
            val container = app.container
            val saved = container.sessionStore.state.first { it != SessionState.Loading }
            assumeTrue(saved !is SessionState.SignedIn || saved.session.debug && saved.session.sid == session.sid)
            originalSession = saved
            container.sessionStore.save(session)
            container.prepareSession(session)
            originalTimetable = container.timetableRepository.personal()
            val term = today.minusDays(TtCalendar.dayIndex(today).toLong()).minusWeeks(4)
            container.timetableRepository.savePersonal(TtSnapshot(listOf(courseA, courseB), term.toString()))
        }
    }

    @After fun restoreFixture() {
        val saved = originalSession ?: return
        runBlocking {
            val container = app.container
            originalTimetable?.let { container.timetableRepository.savePersonal(it) }
                ?: container.timetableRepository.clearPersonal()
            if (saved is SessionState.SignedIn) container.sessionStore.save(saved.session)
            else container.sessionStore.clear()
        }
    }

    @Test fun notificationOpensExactCourseAndRepeatedTapKeepsOneTimetableParent() {
        val info = com.furina.classtools.data.reminder.CourseReminderInfo(courseB.name, courseB.room,
            System.currentTimeMillis() + 60_000L, System.currentTimeMillis() + 90_000L,
            CourseFocus.decode(focusB))
        val activity = com.furina.classtools.data.reminder.CourseActivity("debug_" + session.sid,
            "test-navigation", "test-course-generation", info)
        val card = com.furina.classtools.data.reminder.ReminderIntentFactory.courseIntent(app, activity)
        withDesktopHost(desktop(destination = "home", cold = true)) {
            expectWorkbench()
            if (layoutReview) {
                val owner = "debug_" + session.sid
                val notifier = com.furina.classtools.data.reminder.AndroidReminderNotifier(app, owner)
                notifier.ensureChannels()
                val event = com.furina.classtools.data.reminder.ReminderEvent(
                    com.furina.classtools.data.reminder.ReminderKind.COURSE_SOON, "layout:${info.startAt}",
                    System.currentTimeMillis() - 1000L, courseB.name, "",
                    course = info.copy(teacher = courseB.teacher, span = "第5节", week = 5, trackLive = true))
                assertTrue(notifier.notify(event))
                assertTrue(device.openNotification())
                val row = node(By.pkg("com.android.systemui").textContains("回归课程乙"))
                device.takeScreenshot(java.io.File(app.getExternalFilesDir(null), "course-layout-shade.png"))
                row.click()
            } else card.send()
            if (layoutReview) {
                node(closeDetail)
                node(By.text(courseB.name))
                device.takeScreenshot(java.io.File(app.getExternalFilesDir(null), "course-layout-title.png"))
                val roomSelector = By.text(courseB.room)
                device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5,
                    device.displayWidth / 2, device.displayHeight / 3, 45)
                device.waitForIdle(600)
                val roomBounds = node(roomSelector).visibleBounds
                assertTrue("Long classroom should be above the navigation bar after scrolling",
                    roomBounds.bottom < device.displayHeight - 20)
                device.takeScreenshot(java.io.File(app.getExternalFilesDir(null), "course-layout-details.png"))
            } else expectDetails(courseB)
            card.send()
            if (layoutReview) node(By.text(courseB.room)) else expectDetails(courseB)
            device.pressBack()
            expectTimetable()
            device.pressBack()
            expectWorkbench()
        }
    }

    @Test fun coldCourseNotificationRestoresItsTargetAfterSameAccountLogin() {
        runBlocking { app.container.sessionStore.clear() }
        val intent = Intent(app, MainActivity::class.java).apply {
            data = Uri.parse("ct-course://notification/login-fixture")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(com.furina.classtools.data.reminder.ReminderIntentFactory.EXTRA_TOOL_ID, "tt")
            putExtra(MainActivity.CARD_ACCOUNT, "debug:" + session.sid)
            putExtra(MainActivity.CARD_FOCUS, focusB)
        }
        ActivityScenario.launch<MainActivity>(intent).use {
            node(By.text("身份核验"))
            runBlocking { app.container.sessionStore.save(session.copy(issuedAt = System.currentTimeMillis())) }
            expectDetails(courseB)
            device.pressBack()
            expectTimetable()
            device.pressBack()
            expectWorkbench()
        }
    }

    @Test fun coldDesktopCourseBackKeepsTimetableAndRestoresWorkbenchParent() {
        ActivityScenario.launch<MainActivity>(desktop(focus = focusA, cold = true)).use {
            expectDetails(courseA)
            device.pressBack()
            expectTimetable()
            device.pressBack()
            expectWorkbench()
            device.pressBack()
            assertTrue(device.wait(Until.gone(By.pkg(app.packageName)), 5_000))
        }
    }

    @Test fun warmDesktopDeliveryReusesTimetableAndCanReopenTheSameOrAnotherCourse() {
        withDesktopHost(desktop(destination = "home", cold = true)) { host ->
            expectWorkbench()
            instrumentation.runOnMainSync { host.startActivity(desktop(focus = focusA)) }
            expectDetails(courseA)
            node(closeDetail).click()
            expectTimetable()
            instrumentation.runOnMainSync { repeat(3) { host.startActivity(desktop(focus = focusA)) } }
            expectDetails(courseA)
            device.pressBack()
            expectTimetable()
            instrumentation.runOnMainSync { host.startActivity(desktop(focus = focusB)) }
            expectDetails(courseB)
            device.pressBack()
            expectTimetable()
            device.pressBack()
            expectWorkbench()
            instrumentation.runOnMainSync { host.startActivity(desktop(destination = "nt")) }
            node(By.text("班级通知"))
            device.pressHome()
            instrumentation.runOnMainSync { host.startActivity(desktop(focus = focusA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            expectDetails(courseA)
            node(closeDetail).click()
            expectTimetable()
            device.pressBack()
            expectWorkbench()
        }
    }

    @Test fun desktopDateAndInvalidFocusKeepAnOperableTimetable() {
        withDesktopHost(desktop(focus = CourseFocus(tomorrow.toString()).encode(), cold = true)) { host ->
            expectTimetable()
            node(By.text(courseB.name))
            // Use the same fill-in PendingIntent template as a launcher collection row.
            instrumentation.runOnMainSync {
                val data = Uri.Builder().scheme("classtools-widget").authority("open").appendPath("tt")
                    .appendQueryParameter("focus", "malformed").build()
                WidgetIntents.template(host, 9001).send(host, 0, Intent().setData(data))
            }
            node(By.text(courseA.name))
            expectTimetable()
            device.pressBack()
            expectWorkbench()
        }
    }

    @Test fun scrimDismissalStaysClosedOnRecreationAndAnotherDesktopDeliveryFindsTheParent() {
        ActivityScenario.launch<MainActivity>(desktop(focus = focusA, cold = true)).use { scenario ->
            expectDetails(courseA)
            device.click(device.displayWidth / 2, device.displayHeight / 8)
            expectTimetable()
            scenario.recreate()
            expectTimetable()
            node(mapFromTimetable).click()
            node(By.text("校内地图"))
            scenario.onActivity { it.startActivity(desktop(focus = focusB)) }
            expectDetails(courseB)
            device.pressBack()
            expectTimetable()
            device.pressBack()
            expectWorkbench()
        }
    }

    @Test fun workbenchCourseRestoresItsScrollPositionAndDetailAcrossTheMap() {
        ActivityScenario.launch<MainActivity>(desktop(destination = "home", cold = true)).use {
            expectWorkbenchAfterLoading()
            val source = scrollTo(By.text(courseA.name))
            val sourceCenter = source.visibleCenter
            val sourceY = sourceCenter.y
            assertTrue(device.click(sourceCenter.x, sourceY))
            expectDetails(courseA)
            node(mapFromDetail).click()
            val mapTitle = By.text("校内地图")
            node(mapTitle)
            // Return from the map's classroom selection or the map itself.
            device.pressBack()
            if (!device.wait(Until.gone(mapTitle), 5_000)) device.pressBack()
            assertTrue("Back should return from the map to the timetable", device.wait(Until.gone(mapTitle), 8_000))
            expectDetails(courseA)
            device.pressBack()
            expectTimetable()
            device.pressBack()
            expectWorkbenchAfterLoading()
            val deadline = SystemClock.uptimeMillis() + 5_000L
            val tolerance = device.displayHeight / 20
            var restoredY = node(By.text(courseA.name)).visibleCenter.y
            while (kotlin.math.abs(sourceY - restoredY) >= tolerance && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(100)
                restoredY = node(By.text(courseA.name)).visibleCenter.y
            }
            assertTrue("Course position changed from $sourceY to $restoredY",
                kotlin.math.abs(sourceY - restoredY) < tolerance)
        }
    }

    @Test fun standaloneTimetableReturnsToTheExistingMainHost() {
        ActivityScenario.launch<MainActivity>(desktop(destination = "home", cold = true)).use { scenario ->
            expectWorkbench()
            scenario.onActivity { it.startActivity(ToolActivity.createIntent(it, "tt", false, session)) }
            expectTimetable()
            node(By.text(courseA.name)).click()
            expectDetails(courseA)
            device.pressBack()
            expectTimetable()
            device.pressBack()
            expectWorkbench()
            scenario.onActivity { assertTrue(!it.isFinishing) }
        }
    }

    @Test fun standaloneWithoutAMainHostOpensTheWorkbenchOnUp() {
        val standalone = ToolActivity.createIntent(app, "tt", false, session).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
        )
        ActivityScenario.launch<ToolActivity>(standalone).use { scenario ->
            scenario.onActivity { assertTrue(it.isTaskRoot) }
            expectTimetable()
            node(By.desc("返回")).click()
            expectWorkbench()
            device.pressBack()
            assertTrue(device.wait(Until.gone(By.pkg(app.packageName)), 5_000))
        }
    }

    @Test fun aCourseWidgetFromAnotherLoginLeavesTheCurrentWorkbenchInPlace() {
        val oldWidget = desktop(focus = focusA, cold = true)
            .putExtra("com.furina.classtools.card.ACCOUNT", "debug:old-test-login")
        ActivityScenario.launch<MainActivity>(oldWidget).use {
            expectWorkbench()
            assertTrue(device.wait(Until.gone(mapFromDetail), 3_000))
            assertTrue(!device.hasObject(mapFromTimetable))
        }
    }

    @Test fun cancellingEdgeBackKeepsDetailsAndCompletingItClosesExactlyOneLayer() {
        assumeTrue(Settings.Secure.getInt(app.contentResolver, "navigation_mode", 0) == 2)
        assumeTrue(Settings.Secure.getInt(app.contentResolver, "user_setup_complete", 0) == 1)
        ActivityScenario.launch<MainActivity>(desktop(focus = focusA, cold = true)).use {
            expectDetails(courseA)
            edgeBack(cancel = true)
            expectDetails(courseA)
            edgeBack(cancel = false)
            expectTimetable()
            device.pressBack()
            expectWorkbench()
        }
    }

    @Test fun swipeDownClosesDetailsAndKeepsTheDay() {
        ActivityScenario.launch<MainActivity>(desktop(focus = focusB, cold = true)).use {
            expectDetails(courseB)
            val top = node(closeDetail).visibleBounds.top - (24 * app.resources.displayMetrics.density).toInt()
            val x = device.displayWidth / 2
            assertTrue(device.swipe(x, top, x, device.displayHeight - 10, 32))
            expectTimetable()
            node(By.text(courseB.name))
        }
    }

    private fun edgeBack(cancel: Boolean) {
        val downAt = SystemClock.uptimeMillis()
        val y = device.displayHeight * 3 / 4f
        val width = device.displayWidth / 4f
        fun inject(action: Int, x: Float) {
            val event = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action, x, y, 0)
            try {
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }
        inject(MotionEvent.ACTION_DOWN, 1f)
        for (step in 1..10) {
            SystemClock.sleep(16)
            inject(MotionEvent.ACTION_MOVE, width * step / 10)
        }
        inject(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, width)
    }

    private fun withDesktopHost(intent: Intent, block: (MainActivity) -> Unit) {
        val host = instrumentation.startActivitySync(intent) as MainActivity
        try {
            block(host)
        } finally {
            instrumentation.runOnMainSync { if (!host.isFinishing) host.finish() }
        }
    }

    private fun desktop(destination: String = "tt", focus: String = "", cold: Boolean = false): Intent =
        Intent(app, MainActivity::class.java).apply {
            action = "com.furina.classtools.widget.OPEN.$destination"
            putExtra("com.furina.classtools.card.FOCUS", focus)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (cold) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

    private fun expectDetails(course: Course) {
        node(mapFromDetail)
        node(By.text(course.teacher))
    }

    private fun expectTimetable() {
        assertTrue("The course detail sheet should be closed", device.wait(Until.gone(mapFromDetail), 8_000))
        node(mapFromTimetable)
    }

    private fun expectWorkbench() { node(By.text("工作台")) }

    private fun expectWorkbenchAfterLoading() {
        expectWorkbench()
        // Compare placement after the cards above the timetable have switched to their loaded height.
        for (selector in listOf(By.desc("同步通知…"), By.text("更新通知…"), By.text("更新班级作业…"), By.text("正在读取课表"))) {
            assertTrue(device.wait(Until.gone(selector), 10_000))
        }
        device.waitForIdle(3_000)
    }

    private fun node(selector: BySelector) = requireNotNull(device.wait(Until.findObject(selector), 8_000)) {
        "Missing $selector. Foreground package: ${device.currentPackageName}"
    }

    private fun scrollTo(selector: BySelector): androidx.test.uiautomator.UiObject2 {
        // Place the tap target in the middle of the scroll viewport, clear of both overlays.
        val middle = device.displayHeight / 2
        val step = device.displayHeight / 6
        val x = device.displayWidth * 2 / 3
        repeat(5) {
            val target = device.wait(Until.findObject(selector), 1_000)
            val y = target?.visibleCenter?.y ?: device.displayHeight
            if (target != null && y in middle - step..middle + step) return target
            val distance = if (y > middle) step else -step
            device.swipe(x, middle + distance, x, middle, 60)
            device.waitForIdle(3_000)
        }
        return node(selector).also {
            assertTrue("Course must be visible between the overlays", it.visibleCenter.y in step..device.displayHeight - step)
        }
    }
}
