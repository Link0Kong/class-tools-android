package com.furina.classtools

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.furina.classtools.data.notices.ManualNotice
import com.furina.classtools.data.notices.NoticeRow
import com.furina.classtools.data.notices.NoticesData
import com.furina.classtools.ui.screens.notices.NoticesScreen
import com.furina.classtools.ui.theme.ClassToolsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeFilterSwipeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test fun swipingKeepsControlsInPlaceAndSwitchesFilters() {
        val now = System.currentTimeMillis()
        val data = NoticesData(
            auto = emptyList(),
            manual = (1..20).map { index ->
                ManualNotice(
                    row = NoticeRow(id = "test-$index", title = "通知样例 $index", ts = now - index * 60_000L),
                    attachments = emptyList(),
                )
            },
            hiddenCount = 0,
            fetchedAt = now,
            fromCache = false,
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    ClassToolsTheme(darkTheme = false) {
                        NoticesScreen(
                            onBack = {},
                            session = null,
                            marksRepository = null,
                            noticesRepository = null,
                            initialData = data,
                            previewEntranceProgress = 1f,
                            skipLoad = true,
                        )
                    }
                }
            }
            val stat = By.textContains("7 天内")
            val allChip = By.text("全部 20")
            val firstNotice = By.text("通知样例 1")
            val statBounds = node(stat).visibleBounds
            val chipBounds = node(allChip).visibleBounds
            val y = node(firstNotice).visibleCenter.y.toFloat()
            val start = device.displayWidth * 0.75f
            val end = device.displayWidth * 0.4f
            val downAt = SystemClock.uptimeMillis()
            injectMotion(downAt, MotionEvent.ACTION_DOWN, start, y)
            try {
                for (step in 1..8) {
                    SystemClock.sleep(16)
                    injectMotion(downAt, MotionEvent.ACTION_MOVE, start + (end - start) * step / 8, y)
                }
                assertEquals("Overview bounds while dragging", statBounds, node(stat).visibleBounds)
                assertEquals("Filter chip bounds while dragging", chipBounds, node(allChip).visibleBounds)
            } finally {
                injectMotion(downAt, MotionEvent.ACTION_UP, end, y)
            }
            val pendingEmpty = By.text("没有未处理通知")
            node(pendingEmpty)
            node(allChip).click()
            val bounds = node(firstNotice).visibleBounds
            assertTrue(device.swipe(
                bounds.centerX(), device.displayHeight * 4 / 5,
                bounds.centerX(), bounds.centerY(), 32,
            ))
            assertTrue("Overview still scrolls vertically", device.wait(Until.gone(stat), 3_000))

            val middle = device.displayHeight / 2
            assertTrue(device.swipe(start.toInt(), middle, end.toInt(), middle, 24))
            node(pendingEmpty)
            assertTrue(device.swipe(end.toInt(), middle, start.toInt(), middle, 24))
            assertTrue(device.wait(Until.gone(pendingEmpty), 3_000))
            assertTrue("Returning to all keeps its vertical position", device.wait(Until.gone(stat), 3_000))
        }
    }

    private fun injectMotion(downAt: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action, x, y, 0)
        try {
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
        } finally {
            event.recycle()
        }
    }

    private fun node(selector: BySelector) = requireNotNull(device.wait(Until.findObject(selector), 5_000)) {
        "Missing $selector"
    }
}
