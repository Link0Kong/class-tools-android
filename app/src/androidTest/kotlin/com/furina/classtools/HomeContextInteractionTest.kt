package com.furina.classtools

import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.session.SessionState
import com.furina.classtools.data.settings.LocalAppSettingsStore
import com.furina.classtools.data.timetable.Course
import com.furina.classtools.data.timetable.CourseFocus
import com.furina.classtools.data.timetable.TtCalendar
import com.furina.classtools.data.timetable.TtSnapshot
import com.furina.classtools.features.home.HomeContentPreferences
import com.furina.classtools.features.home.HomeContextRepository
import com.furina.classtools.features.home.HomeContextSource
import com.furina.classtools.features.home.HomePhrase
import com.furina.classtools.features.home.HomePhraseBatch
import com.furina.classtools.features.home.HomeTone
import com.furina.classtools.features.home.HomeTopic
import com.furina.classtools.features.home.HomeWeather
import com.furina.classtools.features.home.LocalHomeContextSource
import com.furina.classtools.ui.navigation.navigationAccount
import com.furina.classtools.ui.screens.home.HomeScreen
import com.furina.classtools.ui.screens.profile.ProfileScreen
import com.furina.classtools.ui.theme.ClassToolsTheme
import com.furina.classtools.ui.theme.CtLightIntensity
import com.furina.classtools.ui.theme.CtLightSetting
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Render and exercise the same Composables with no external network or real user data. */
class HomeContextInteractionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as ClassToolsApp
    private val device = UiDevice.getInstance(instrumentation)
    private val testSession = Session("3260799998", "工作台测试账号", token = "", debug = true)
    private val regionLabel = By.textContains("福州市闽侯县")
    private val phraseLabel = By.res("home_context_phrase")

    @Before fun requireReviewDevice() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("homeContextReview") == "yes")
        assertEquals("Dedicated offline emulator required", 1, Settings.Global.getInt(
            app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0,
        ))
    }

    @Test fun captureScenariosAndExerciseCardActions() {
        val config = app.resources.configuration
        val wideText = config.fontScale > 1.5f
        val landscape = config.screenWidthDp > config.screenHeightDp
        val folder = File(app.filesDir, "home-context-review/" + config.screenWidthDp + "dp-" + config.fontScale).apply { mkdirs() }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val morning = today.atTime(8, 4).atZone(zone).toInstant().toEpochMilli()
        val day = today.atTime(15, 0).atZone(zone).toInstant().toEpochMilli()
        val dateKey = today.minusDays(TtCalendar.dayIndex(today).toLong()).minusWeeks(2).toString()
        val hour = AtomicInteger()
        val sampleCourse = Course("数据可视化与校园开放数据实作", "测试教师", "测试教学楼 01105", TtCalendar.dayIndex(today), 1, 2)
        val coursePlan = TtCalendar.plan(TtSnapshot(listOf(sampleCourse), dateKey), morning, zone)
        val courseFocus = CourseFocus.of(coursePlan.next!!, zone).encode()
        var opened: Pair<String, String?>? = null
        val sampleWeather = object : HomeContextSource {
            override suspend fun weather(regionId: String, now: Long): HomeWeather {
                hour.incrementAndGet()
                return HomeWeather(regionId, Instant.ofEpochMilli(now - 120_000).toString(), 305, "小雨", 18.0)
            }
            override suspend fun content(now: Long) = null
        }
        val sampleSources = object : HomeContextSource {
            override suspend fun weather(regionId: String, now: Long) = null
            override suspend fun content(now: Long): HomePhraseBatch {
                val start = Instant.ofEpochMilli(now - 120_000)
                return HomePhraseBatch(start.toString(), start.plusSeconds(86_400).toString(),
                    HomeTone.entries.mapIndexed { i, tone ->
                        HomePhrase("ai-demo-" + i, "合上笔记，试着说出一个还记得的发现。", HomeTopic.STUDY, tone,
                            sourceTitle = "Effective Study Strategies", sourceUrl = "https://lsc.cornell.edu/how-to-study/")
                    })
            }
        }
        fun show(
            dark: Boolean,
            glass: Boolean = false,
            clock: Long = day,
            source: HomeContextSource? = null,
            withCourse: Boolean = false,
        ) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        ClassToolsTheme(darkTheme = dark, immersiveLight = CtLightSetting(CtLightIntensity.BALANCED, glass)) {
                            CompositionLocalProvider(LocalHomeContextSource provides source) {
                                HomeScreen(session = testSession, onOpenTool = {}, nowMillis = clock,
                                    initialTtPlan = coursePlan.takeIf { withCourse },
                                    onCardOpen = { tool, focus -> opened = tool to focus })
                            }
                        }
                    }
                }
                node(By.text("工作台"))
                node(phraseLabel)
                when {
                    withCourse -> {
                        if (!wideText) {
                            shot(folder, "course-dark-glass.png")
                            node(By.text("查看课程")).click()
                            assertEquals("tt" to courseFocus, opened)
                        } else {
                            visibleText("数据可视化")
                            shot(folder, "course-narrow-large-font.png")
                        }
                    }
                    source === sampleWeather -> {
                        node(regionLabel)
                        if (wideText) {
                            shot(folder, "weather-narrow-large-font.png")
                        } else {
                            shot(folder, "weather-light.png")
                            val expected = hour.get()
                            repeat(3) { node(By.text("换一句")).click() }
                            assertEquals(expected, hour.get())
                            repeat(8) {
                                if (!device.hasObject(By.text("看中文"))) node(By.text("换一句")).click()
                            }
                            // The explicit control and its idle state survive a short feedback.
                            if (device.hasObject(By.desc("点亮小灯"))) {
                                node(By.desc("点亮小灯")).click()
                                SystemClock.sleep(1100)
                                assertTrue(device.hasObject(By.desc("点亮小灯")))
                            }
                        }
                    }
                    source === sampleSources -> {
                        seekAction("出处").click()
                        node(By.text("文案出处"))
                        node(By.text("Effective Study Strategies"))
                        node(By.text("阅读原文"))
                        shot(folder, "online-source-fixture.png")
                        device.pressBack()
                        assertTrue(device.wait(Until.gone(By.text("文案出处")), 5000))
                    }
                    else -> {
                        if (wideText) {
                            visibleText("尚未配置课表")
                            shot(folder, "offline-narrow-large-font.png")
                        } else {
                            seekAction("看中文").click()
                            node(By.text("收起中文"))
                            shot(folder, if (landscape) "english-landscape.png" else "english-expanded.png")
                            node(By.text("换一句")).click()
                            assertFalse(device.hasObject(By.text("收起中文")))
                        }
                    }
                }
            }
        }
        if (landscape) {
            show(dark = false)
            return
        }
        show(dark = false, source = sampleWeather)
        show(dark = true, glass = true, clock = morning, withCourse = true)
        show(dark = false)
        if (!wideText) show(dark = false, source = sampleSources)
    }

    @Test fun actualHostReturnsFromTomorrowAndRestoresWorkbench() {
        assumeTrue(app.resources.configuration.fontScale < 1.5f)
        val container = app.container
        val oldSession = runBlocking { container.sessionStore.state.first { it != SessionState.Loading } }
        assumeTrue(oldSession !is SessionState.SignedIn || oldSession.session.debug)
        val account = testSession.navigationAccount
        val oldPreferences = runBlocking { container.appSettings.homeContent(account).first().preferences }
        var oldPlan: TtSnapshot? = null
        val tomorrow = LocalDate.now().plusDays(1)
        val tomorrowName = "明日数据整理实训"
        runBlocking {
            container.sessionStore.save(testSession)
            container.prepareSession(testSession)
            oldPlan = container.timetableRepository.personal()
            val termStart = LocalDate.now().minusDays(TtCalendar.dayIndex(LocalDate.now()).toLong()).minusWeeks(2)
            container.timetableRepository.savePersonal(TtSnapshot(
                listOf(Course(tomorrowName, "", "测试楼", TtCalendar.dayIndex(tomorrow), 1, 2)), termStart.toString(),
            ))
            container.appSettings.changeHomeContent(account) { HomeContentPreferences(english = false, onlineContent = false, motion = false) }
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                node(By.text("工作台"))
                val expected = node(phraseLabel).text
                val button = node(By.text("查看明日课表"))
                val y = button.visibleCenter.y
                button.click()
                node(By.text(tomorrowName))
                device.pressBack()
                node(By.text("工作台"))
                assertEquals(expected, node(phraseLabel).text)
                assertTrue(kotlin.math.abs(y - node(By.text("查看明日课表")).visibleCenter.y) < device.displayHeight / 20)
                node(By.text("查看明日课表")).click()
                node(By.text(tomorrowName))
                val up = device.findObject(By.desc("返回")) ?: device.findObject(By.text("返回"))
                assertNotNull("Timetable Up control", up)
                up.click()
                node(By.text("工作台"))
                scenario.recreate()
                node(By.text("查看明日课表"))
                assertEquals(expected, node(phraseLabel).text)
                assertFalse(runBlocking { container.appSettings.homeContent(account).first().preferences.motion })
                runBlocking { container.appSettings.changeHomeContent(account) { it.copy(english = true) } }
                device.waitForIdle(1_500)
                seekAction("看中文").click()
                node(By.text("收起中文"))
                val english = node(phraseLabel).text
                scenario.recreate()
                node(By.text("收起中文"))
                assertEquals(english, node(phraseLabel).text)
            }
        } finally {
            runBlocking {
                container.appSettings.changeHomeContent(account) { oldPreferences }
                oldPlan?.let { container.timetableRepository.savePersonal(it) }
                    ?: container.timetableRepository.clearPersonal()
                if (oldSession is SessionState.SignedIn) container.sessionStore.save(oldSession.session)
                else container.sessionStore.clear()
            }
        }
    }

    @Test fun profileSettingsChangeAndRestoreWeatherRegion() {
        assumeTrue(app.resources.configuration.fontScale < 1.5f)
        val store = app.container.appSettings
        val account = testSession.navigationAccount
        val original = runBlocking { store.homeContent(account).first().preferences }
        runBlocking { store.changeHomeContent(account) { it.copy(funContent = true, motion = true) } }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        ClassToolsTheme(darkTheme = false) {
                            CompositionLocalProvider(LocalAppSettingsStore provides store) {
                                ProfileScreen(session = testSession, onSignOut = {})
                            }
                        }
                    }
                }
                node(By.text("数据与同步"))
                clickCenteredProfileRow(By.text("首页内容"))
                val motion = node(By.text("装饰动效"))
                (motion.parent?.takeIf { it.isClickable } ?: motion).click()
                runBlocking { withTimeout(5000) { store.homeContent(account).first { !it.preferences.motion } } }
                val weather = By.textContains("天气地区")
                clickSettingsRow(weather)
                node(By.text("选择天气地区"))
                node(By.text("搜索城市或区县")).text = "福州市"
                node(By.text("福建省 · 福州市")).click()
                runBlocking { withTimeout(5000) { store.homeContent(account).first { it.preferences.regionId == "1810815" } } }
                node(By.text("首页内容"))
                clickSettingsRow(weather)
                node(By.text("选择天气地区"))
                node(By.text("恢复默认 · 福州市闽侯县")).click()
                runBlocking { withTimeout(5000) { store.homeContent(account).first { it.preferences.regionId == null } } }
                scrollTo(By.text("完成")).click()
                assertTrue(device.wait(Until.gone(By.textContains("天气地区")), 6000))
            }
        } finally {
            runBlocking { store.changeHomeContent(account) { original } }
        }
    }

    @Test fun switchingRegionsHidesTheOldWeatherWhileLoading() {
        assumeTrue(app.resources.configuration.fontScale < 1.5f)
        val store = app.container.appSettings
        val account = testSession.navigationAccount
        val original = runBlocking { store.homeContent(account).first().preferences }
        val loadCity = CompletableDeferred<Unit>()
        val source = object : HomeContextSource {
            override suspend fun weather(regionId: String, now: Long): HomeWeather {
                if (regionId != "minhou") loadCity.await()
                return HomeWeather(regionId, Instant.ofEpochMilli(now - 120_000).toString(), 305, "小雨", 18.0)
            }
            override suspend fun content(now: Long) = null
        }
        runBlocking { store.changeHomeContent(account) { it.copy(regionId = null) } }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        ClassToolsTheme(darkTheme = false) {
                            CompositionLocalProvider(LocalAppSettingsStore provides store, LocalHomeContextSource provides source) {
                                HomeScreen(session = testSession, onOpenTool = {})
                            }
                        }
                    }
                }
                node(regionLabel)
                runBlocking { store.changeHomeContent(account) { it.copy(regionId = "1810815") } }
                assertTrue(device.wait(Until.gone(regionLabel), 5000))
                loadCity.complete(Unit)
                node(By.textContains("福建省 · 福州市"))
                assertFalse(device.hasObject(regionLabel))
                runBlocking { store.changeHomeContent(account) { it.copy(regionId = null) } }
                node(regionLabel)
            }
        } finally {
            runBlocking { store.changeHomeContent(account) { original } }
        }
    }

    @Test fun verifiedBackendServesPublicPhrasesOverLoopback() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("homeBackendReview") == "yes")
        val now = System.currentTimeMillis()
        val emptyCache = File.createTempFile("home-content-review-", ".json", app.cacheDir)
        try {
            val repository = HomeContextRepository(BuildConfig.HOME_CONTEXT_BASE_URL, emptyCache, allowLoopback = true)
            val result = requireNotNull(runBlocking { repository.content(now) }) {
                "Expected a fresh reviewed batch from the loopback service"
            }
            assertTrue(result.validAt(now))
            assertTrue(result.entries.any { it.topic == HomeTopic.EVERYDAY })
            assertTrue(result.entries.any { it.topic == HomeTopic.STUDY })
            assertEquals(result.entries.size, result.entries.map { it.id }.toSet().size)
            assertTrue(emptyCache.length() > 0)
        } finally {
            emptyCache.delete()
        }
    }

    private fun seekAction(text: String): UiObject2 {
        val selector = By.text(text)
        repeat(16) {
            device.waitForIdle(1500)
            if (device.hasObject(selector)) {
                val currentPhrase = node(phraseLabel).text
                SystemClock.sleep(500)
                val ready = device.findObject(selector)
                if (ready != null && node(phraseLabel).text == currentPhrase) {
                    return ready.parent?.takeIf { it.isClickable } ?: ready
                }
            } else {
                val swap = node(By.text("换一句"))
                (swap.parent?.takeIf { it.isClickable } ?: swap).click()
                SystemClock.sleep(250)
            }
        }
        return node(selector)
    }

    private fun node(selector: BySelector): UiObject2 =
        requireNotNull(device.wait(Until.findObject(selector), 9_000)) {
            val folder = File(app.filesDir, "home-context-review").apply { mkdirs() }
            device.dumpWindowHierarchy(File(folder, "last-failure.xml"))
            device.takeScreenshot(File(folder, "last-failure.png"))
            "Missing $selector"
        }

    private fun visibleText(text: String) = scrollTo(By.textContains(text))

    private fun clickCenteredProfileRow(selector: BySelector) {
        val height = device.displayHeight
        val middle = height / 2
        repeat(8) {
            val row = scrollTo(selector)
            if (row.visibleCenter.y in height / 5..height * 4 / 5) {
                SystemClock.sleep(600)
                clickSettingsRow(selector)
                if (device.wait(Until.hasObject(By.text("趣味内容")), 1_500)) return
            } else {
                val displacement = (row.visibleCenter.y - middle).coerceIn(-height / 3, height / 3)
                val x = device.displayWidth / 2
                device.swipe(x, middle, x, middle - displacement, 35)
                device.waitForIdle(1500)
            }
        }
        node(By.text("趣味内容"))
    }

    private fun clickSettingsRow(selector: BySelector) {
        val row = scrollTo(selector)
        (row.parent?.takeIf { it.isClickable } ?: row).click()
    }

    private fun scrollTo(selector: BySelector): UiObject2 {
        repeat(14) {
            device.findObject(selector)?.let { return it }
            val x = device.displayWidth / 2
            device.swipe(x, device.displayHeight * 13 / 20, x, device.displayHeight * 8 / 20, 25)
            device.waitForIdle(1200)
        }
        return node(selector)
    }

    private fun shot(folder: File, name: String) {
        instrumentation.waitForIdleSync()
        device.waitForIdle(1500)
        SystemClock.sleep(350)
        assertTrue(device.takeScreenshot(File(folder, name)))
    }
}
