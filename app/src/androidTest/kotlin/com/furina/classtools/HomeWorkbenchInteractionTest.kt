package com.furina.classtools

import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.notices.NoticeEntry
import com.furina.classtools.data.notices.NoticeGroupDto
import com.furina.classtools.data.notices.NoticeRow
import com.furina.classtools.data.notices.NoticesData
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.tasks.TaskRepository
import com.furina.classtools.data.tasks.TaskStorage
import com.furina.classtools.data.tasks.newTask
import com.furina.classtools.data.tasks.text
import com.furina.classtools.data.timetable.Course
import com.furina.classtools.data.timetable.CourseFocus
import com.furina.classtools.data.timetable.TtCalendar
import com.furina.classtools.data.timetable.TtSnapshot
import com.furina.classtools.ui.adaptive.CtAdaptive
import com.furina.classtools.ui.screens.home.HomeScreen
import com.furina.classtools.ui.screens.home.HomeWorkbenchLogic
import com.furina.classtools.ui.theme.ClassToolsTheme
import com.furina.classtools.ui.theme.isReduceMotion
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class HomeWorkbenchInteractionTest {
    @Test fun rotatesThroughNoticesAndTasksAndKeepsCourseDetailsInTheTimetableCard() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("interactionReview") == "yes")
        val context = instrumentation.targetContext
        assertEquals("Use an offline review device", 1, Settings.Global.getInt(
            context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0,
        ))
        assertFalse("Enable animations to test rotation", isReduceMotion(context))
        val device = UiDevice.getInstance(instrumentation)
        val clock = ZonedDateTime.now().withHour(7).withMinute(0).withSecond(0).withNano(0)
        val now = clock.toInstant().toEpochMilli()
        val today = clock.toLocalDate()
        val day = TtCalendar.dayIndex(today)
        val course = Course("回归测试课", "测试老师", "自强楼(教室01105)", day, 1, 2)
        val plan = TtCalendar.plan(TtSnapshot(listOf(course), today.minusDays(day.toLong()).toString()), now)
        val entries = (1..3).map { index ->
            val id = "rotation-notice-$index"
            val title = "轮播测试通知 $index"
            NoticeEntry(
                row = NoticeRow(id = "auto_$id", title = title, ts = now - index * 600_000L),
                group = NoticeGroupDto(id = id, title = title, deadline = clock.plusDays(1).toInstant().toString()),
                items = emptyList(), hidden = false, edited = false,
            )
        }
        val notices = NoticesData(entries, emptyList(), 0, now, false)
        val tasks = (1..3).map { newTask("轮播测试待办 $it", now, it.toDouble()) }
        val scope = CoroutineScope(Dispatchers.Main)
        val storage = object : TaskStorage {
            override fun read(key: String): String? = null
            override fun save(local: JsonObject, base: JsonObject?, metadata: Map<String, String?>) = Unit
            override fun checkpointAttempt(value: String?): Boolean = true
        }
        val session = Session("3260799999", "测试账号", token = "", debug = true)
        val repository = TaskRepository(session.sid, storage, ApiClient("https://example.invalid", ""), scope, canSync = { false })
        repository.edit { _, _ -> tasks }
        val active = mutableStateOf(false)
        var opened: Pair<String, String?>? = null
        val rotationTimeout = HomeWorkbenchLogic.ROTATION_INTERVAL_MS * 3

        fun node(text: String, timeout: Long = 8_000) = checkNotNull(device.wait(Until.findObject(By.text(text)), timeout)) {
            "Missing $text (reduce motion: ${isReduceMotion(context)})"
        }

        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertFalse("Enable window animations to test rotation", isReduceMotion(activity))
                    activity.setContent {
                        ClassToolsTheme(darkTheme = false) {
                            HomeScreen(
                                session = session,
                                onOpenTool = {},
                                taskRepository = repository,
                                nowMillis = now,
                                initialTtPlan = plan,
                                initialNoticesData = notices,
                                isPageActive = active.value,
                                onCardOpen = { tool, focus -> opened = tool to focus },
                            )
                        }
                    }
                }
                val noticesHeader = node("班级通知").visibleCenter
                val tasksHeader = node("待办任务").visibleCenter
                val timetableHeader = node("今日课程").visibleCenter
                assertTrue(noticesHeader.y < tasksHeader.y)
                if (context.resources.configuration.screenWidthDp >= CtAdaptive.EXPANDED_MIN_WIDTH_DP) {
                    assertTrue(noticesHeader.x < timetableHeader.x && tasksHeader.x < timetableHeader.x)
                } else {
                    assertTrue(tasksHeader.y < timetableHeader.y)
                }
                assertEquals(1, device.findObjects(By.text(course.name)).size)
                assertTrue(timetableHeader.y < node(course.name).visibleCenter.y)
                node(course.name).click()
                scenario.onActivity { assertEquals("tt" to CourseFocus.of(plan.next!!).encode(), opened) }

                node("轮播测试通知 1")
                node("轮播测试待办 1")
                device.waitForIdle(3_000)
                scenario.onActivity { active.value = true }
                node("轮播测试通知 2", rotationTimeout)
                node("轮播测试待办 2", rotationTimeout)
                node("轮播测试通知 3", rotationTimeout).click()
                scenario.onActivity { assertEquals("nt" to entries.last().id, opened) }
                node("轮播测试待办 3", rotationTimeout).click()
                scenario.onActivity { assertEquals("hw" to tasks.last().text("id"), opened) }

                node("轮播测试通知 1", rotationTimeout)
                node("轮播测试待办 1", rotationTimeout)
                scenario.onActivity { active.value = false }
                SystemClock.sleep(HomeWorkbenchLogic.ROTATION_INTERVAL_MS + 500)
                assertTrue(device.hasObject(By.text("轮播测试通知 1")))
                assertTrue(device.hasObject(By.text("轮播测试待办 1")))
            }
        } finally {
            scope.cancel()
        }
    }
}
