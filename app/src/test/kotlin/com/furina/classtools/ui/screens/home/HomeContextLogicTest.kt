package com.furina.classtools.ui.screens.home

import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.HttpTransport
import com.furina.classtools.data.timetable.Course
import com.furina.classtools.data.timetable.TtCalendar
import com.furina.classtools.data.timetable.TtSnapshot
import com.furina.classtools.data.timetable.TtSource
import com.furina.classtools.features.home.HomeAtmosphere
import com.furina.classtools.features.home.HomeContentPreferences
import com.furina.classtools.features.home.HomeContextRepository
import com.furina.classtools.features.home.HomePhraseContext
import com.furina.classtools.features.home.HomeTopic
import com.furina.classtools.features.home.HomeWeather
import com.furina.classtools.features.home.homeAtmosphere
import com.furina.classtools.features.home.localHomePhrases
import com.furina.classtools.features.home.selectHomePhrase
import com.furina.classtools.features.home.validHomeSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HomeContextLogicTest {
    @get:Rule val files = TemporaryFolder()
    private val zone = ZoneId.of("Asia/Shanghai")
    private val date = LocalDate.of(2026, 11, 23)
    private fun at(hour: Int, minute: Int = 0): Long = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    private fun iso(time: Long) = Instant.ofEpochMilli(time).toString()
    private val preferences = HomeContentPreferences()

    @Test fun catalogueHasStableUniqueIdsAndCompleteTranslations() {
        assertEquals(57, localHomePhrases.size)
        assertEquals(57, localHomePhrases.map { it.id }.toSet().size)
        assertEquals(9, localHomePhrases.count { it.topic == HomeTopic.ENGLISH && !it.translation.isNullOrBlank() })
        assertEquals(18, localHomePhrases.count { it.topic.isWeather })
        assertEquals(15, localHomePhrases.count { it.topic.isCourse })
    }

    @Test fun nightWeatherAndFreshRainRespectTheRegionAndTimestamp() {
        val record = HomeWeather("minhou", iso(at(21, 3)), 100, "晴", 17.0, iso(at(6, 30)), iso(at(17, 20)))
        assertTrue(record.validFor("minhou", at(21, 4)))
        assertFalse(record.validFor("different", at(21, 4)))
        assertFalse(record.validFor("minhou", at(21)))
        assertFalse(record.validFor("minhou", at(21, 48)))
        assertEquals(HomeTopic.CLEAR_NIGHT, record.topicAt(at(21, 4)))
        assertEquals(HomeTopic.RAIN, record.copy(conditionCode = 305).topicAt(at(21, 4)))
        assertNull(record.copy(sunrise = null).topicAt(at(21, 4)))
        assertTrue(record.copy(conditionCode = 310).important)
        assertNull(record.copy(conditionCode = 310).topicAt(at(21, 4)))
    }

    @Test fun selectionPrioritizesFactsAndEnglishOptOutSurvivesManualRotation() {
        val now = at(8, 4)
        val rain = HomePhraseContext.at(now, zone, HomeTopic.RAIN, null, false)
        val near = rain.copy(course = HomeTopic.UPCOMING)
        fun pick(context: HomePhraseContext, selected: String? = null, seed: Int = 0) =
            selectHomePhrase(context, preferences.copy(english = false), emptyList(), emptyMap(), now, seed, selected)
        assertEquals(HomeTopic.RAIN, pick(rain)?.topic)
        assertEquals(HomeTopic.UPCOMING, pick(near)?.topic)
        var previous: String? = null
        repeat(40) { index ->
            val phrase = checkNotNull(pick(rain, previous, index))
            assertNotEquals(HomeTopic.ENGLISH, phrase.topic)
            assertEquals(preferences.tone, phrase.tone)
            assertNotEquals(previous, phrase.id)
            previous = phrase.id
        }
        assertNull(selectHomePhrase(rain, preferences.copy(funContent = false), emptyList(), emptyMap(), now, 0))
        assertNull(selectHomePhrase(rain, preferences, emptyList(), emptyMap(), now, 0, local = emptyList()))
    }

    @Test fun decorationUsesObservedRainAndSunAndFallsBackToTimeAtNight() {
        val sunrise = iso(at(6, 30))
        val sunset = iso(at(17, 20))
        val sunny = HomeWeather("minhou", iso(at(17)), 100, "晴", 16.0, sunrise, sunset)
        assertEquals(HomeAtmosphere.SUN, homeAtmosphere(sunny, at(17), HomeTopic.DAY))
        assertEquals(HomeAtmosphere.NIGHT, homeAtmosphere(sunny, at(17, 21), HomeTopic.DAY))
        assertEquals(HomeAtmosphere.NIGHT, homeAtmosphere(sunny.copy(conditionCode = 150), at(17, 21), HomeTopic.DAY))
        assertEquals(HomeAtmosphere.RAIN, homeAtmosphere(sunny.copy(conditionCode = 350), at(21), HomeTopic.DUSK))
        assertNull(homeAtmosphere(sunny.copy(conditionCode = 104), at(17), HomeTopic.DAY))
        assertNull(homeAtmosphere(null, at(17), HomeTopic.DAY))
        assertEquals(HomeAtmosphere.NIGHT, homeAtmosphere(null, at(21), HomeTopic.DUSK))
    }

    @Test fun avoidsRecentLinesAndFallsBackToTheOldestEligibleLine() {
        val now = at(23)
        val context = HomePhraseContext.at(now, zone, null, null, false)
        val plain = preferences.copy(english = false)
        val usable = localHomePhrases.filter { context.allows(it, plain) }
        val seen = usable.associate { it.id to now - 20_000 }
        assertNotNull(selectHomePhrase(context, plain, emptyList(), seen, now, 2))
        val recent = seen + (usable.first().id to now - 60_000)
        assertEquals(usable.first().id, selectHomePhrase(context, plain, emptyList(), recent, now, 1)?.id)
        assertNotEquals(usable.first().id, selectHomePhrase(context, plain, emptyList(), recent, now, 1,
            excluding = usable.first().id)?.id)
    }

    @Test fun courseFactsUseBellsAndSeparateUnknownFromNoClass() {
        val snapshot = TtSnapshot(listOf(Course("测试课程", "", "测试楼", 0, 1, 2)), date.toString())
        fun resolve(time: Long, value: TtSnapshot = snapshot): HomeCourseContext {
            val day = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
            return homeCourseContext(TtCalendar.plan(value, time, zone), null, time, day, zone)
        }

        assertEquals(HomeTopic.UPCOMING, resolve(at(8, 4)).topic)
        assertEquals(HomeTopic.ONGOING, resolve(at(8, 10)).topic)
        assertEquals(HomeTopic.BREAK, resolve(at(8, 51)).topic)
        assertEquals(HomeTopic.ENDED, resolve(at(9, 40)).topic)
        assertEquals(HomeTopic.NO_CLASS, resolve(at(21) + 8 * 60 * 60_000L).topic)
        val weekTwo = TtSnapshot(listOf(Course("仅第二周", "", "", 0, 1, 1, weeks = "2")), date.toString())
        assertEquals(HomeTopic.NO_CLASS, resolve(at(8, 4), weekTwo).topic)

        val now = at(8, 4)
        assertEquals("正在读取课表", homeCourseContext(null, HomeLoadState(loading = true), now, date, zone).status)
        assertEquals("尚未配置课表", homeCourseContext(null, HomeLoadState(), now, date, zone).status)
        assertEquals("课表读取失败", homeCourseContext(null, HomeLoadState(error = "超时"), now, date, zone).status)
        val stale = HomeLoadState(HomeTimetableSnapshot(snapshot, TtSource.CACHE, now - 7 * 60 * 60_000))
        assertNull(homeCourseContext(TtCalendar.plan(snapshot, now, zone), stale, now, date, zone).topic)
        val tomorrowOnly = TtSnapshot(listOf(Course("明日课程", "", "", 1, 1, 2)), date.toString())
        val nextDay = resolve(now, tomorrowOnly)
        assertEquals("查看明日课表", nextDay.action)
        assertEquals(date.plusDays(1).toString(), nextDay.focus?.date)
        assertEquals(true, nextDay.focus?.isDateOnly)
    }

    @Test fun englishTypographyRefreshesDuringTheSameContentBatch() = runTest {
        val now = at(9)
        val raw = """{
          "generatedAt":"${iso(at(6))}", "expiresAt":"${iso(at(22))}",
          "entries":[{"id":"ai-0123456789abcdef01234567","text":"读一页书，记下一个问题。","topic":"STUDY",
            "tone":"GENTLE","sourceTitle":"Cornell","sourceUrl":"https://lsc.cornell.edu/how-to-study/"}]
        }""".trimEnd()
        var response = ApiResponse(200, raw)
        val cache = files.newFolder().resolve("phrases.json")
        val transport = HttpTransport { response }
        val repository = HomeContextRepository("https://fixture.invalid", cache, transport)
        assertEquals("large", repository.content(now)?.englishTypography)
        response = ApiResponse(200, raw.dropLast(1) + ""","englishTypography":"extraLarge"}""")
        assertEquals("large", repository.content(now + 60_000)?.englishTypography)
        assertEquals("extraLarge", repository.content(now + 15 * 60_000)?.englishTypography)
        response = ApiResponse(503)
        val restarted = HomeContextRepository("https://fixture.invalid", cache, transport)
        assertEquals("extraLarge", restarted.content(now + 16 * 60_000)?.englishTypography)
        response = ApiResponse(200, raw)
        assertEquals("large", repository.content(now + 30 * 60_000)?.englishTypography)
    }

    @Test fun transportIsThrottledAndRejectsPreviousRegionOrMalformedSources() = runTest {
        val now = at(9)
        val requests = mutableListOf<String>()
        var response = """{"regionId":"minhou","validTime":"""" + iso(now) + """","conditionCode":305,"conditionText":"小雨"}"""
        val transport = HttpTransport { request ->
            assertTrue(request.headers.isEmpty())
            requests += request.url
            if (request.url.endsWith("/content/v1")) ApiResponse(503)
            else ApiResponse(200, response)
        }
        val repo = HomeContextRepository("https://fixture.invalid", files.newFolder().resolve("phrases.json"), transport)
        assertEquals("minhou", repo.weather("minhou", now)?.regionId)
        assertEquals("minhou", repo.weather("minhou", now + 90_000)?.regionId)
        assertEquals(1, requests.size)
        assertNull(repo.weather("other", now + 90_000))
        assertEquals(2, requests.size)
        response = """{"regionId":"minhou","validTime":"not-a-time","conditionCode":100,"conditionText":"晴"}"""
        assertNull(repo.weather("minhou", now + 15 * 60_000))
        assertNull(repo.cachedWeather("minhou", now + 15 * 60_000))
        assertNull(repo.content(now))
        assertNull(repo.content(now + 60_000))
        assertEquals(1, requests.count { it.endsWith("/content/v1") })
        assertTrue(validHomeSource("https://lsc.cornell.edu/how-to-study/"))
        assertFalse(validHomeSource("https://lsc.cornell.edu.other.invalid/"))
        assertFalse(validHomeSource("https://example.invalid@lsc.cornell.edu/"))
    }
}
