package com.furina.classtools.data.timetable

import com.furina.classtools.FakeTransport
import com.furina.classtools.createTempDir
import com.furina.classtools.testApiClient
import com.furina.classtools.data.network.ApiResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课表仓库/解析单测（E24：site_assets id=tt，body 兼容 jsonb 对象 / JSON 字符串，失败回退本机缓存）。
 */
class TimetableRepositoryTest {

    private val rowWithObjectBody = """
        [{"body":{"c":[
            {"name":"高等数学","teacher":"王老师","room":"自强楼(教室01104)","day":0,"start":1,"end":2,"weeks":"1-16","parity":"all"},
            {"name":"大学英语","room":"明理楼(教室02201)","day":4,"start":6,"end":7,"weeks":"2-15","parity":"odd"}
          ],"t":"2026-08-31"},
          "meta":{"source":"jwxt","fetchedAt":1760000000000,"count":2,"termStart":"2026-08-31"},
          "updated_at":"2026-11-07T03:00:00+00:00"}]
    """.trimIndent()

    private val rowWithStringBody = """
        [{"body":"{\"c\":[{\"name\":\"数据结构\",\"day\":1,\"start\":3,\"end\":4,\"weeks\":\"1-16\"}],\"t\":\"2026-08-31\"}",
          "meta":{"termStart":"2026-08-31"}}]
    """.trimIndent()

    @Test
    fun parseSnapshot_jsonbObjectBody() {
        val snapshot = DefaultTimetableRepository.parseSnapshot(com.furina.classtools.data.network.NetJson, rowWithObjectBody)
        assertNotNull(snapshot)
        assertEquals(2, snapshot!!.c.size)
        assertEquals("高等数学", snapshot.c[0].name)
        assertEquals("2026-08-31", snapshot.termStart)
        assertEquals("jwxt", snapshot.meta?.source)
        assertEquals(2, snapshot.meta?.count)
    }

    @Test
    fun parseSnapshot_jsonStringBody() {
        val snapshot = DefaultTimetableRepository.parseSnapshot(com.furina.classtools.data.network.NetJson, rowWithStringBody)
        assertNotNull(snapshot)
        assertEquals("数据结构", snapshot!!.c.single().name)
        assertEquals("2026-08-31", snapshot.termStart)
    }

    @Test
    fun parse_threeStates_emptyFormatSuccess() {
        val json = com.furina.classtools.data.network.NetJson
        // 空数据：空响应 / [] / 缺 body / 缺 c / c 空数组
        assertEquals(TtParseOutcome.Empty, DefaultTimetableRepository.parse(json, ""))
        assertEquals(TtParseOutcome.Empty, DefaultTimetableRepository.parse(json, "null"))
        assertEquals(TtParseOutcome.Empty, DefaultTimetableRepository.parse(json, "[]"))
        assertEquals(TtParseOutcome.Empty, DefaultTimetableRepository.parse(json, """[{"body":{"t":"2026-08-31"}}]"""))
        assertEquals(TtParseOutcome.Empty, DefaultTimetableRepository.parse(json, """[{"body":{"c":[]}}]"""))
        // 格式错误：坏 JSON / body 非对象 / c 非数组 / 课程全非法
        assertEquals(TtParseOutcome.Format, DefaultTimetableRepository.parse(json, "oops"))
        assertEquals(TtParseOutcome.Format, DefaultTimetableRepository.parse(json, """[{"body":"not-json"}]"""))
        assertEquals(TtParseOutcome.Format, DefaultTimetableRepository.parse(json, """[{"body":{"c":{}}}]"""))
        assertEquals(TtParseOutcome.Format, DefaultTimetableRepository.parse(json, """[{"body":{"c":[{"name":""}]}}]"""))
    }

    @Test
    fun parseSnapshot_skipsInvalidCoursesAndClamps() {
        val raw = """[{"body":{"c":[
            {"name":"","day":0,"start":1,"end":2},
            {"name":"越界课","day":9,"start":0,"end":99,"weeks":"","parity":"weird"}
        ],"t":"2026-08-31"}}]"""
        val snapshot = DefaultTimetableRepository.parseSnapshot(com.furina.classtools.data.network.NetJson, raw)
        assertNotNull(snapshot)
        assertEquals(1, snapshot!!.c.size)
        assertEquals(6, snapshot.c[0].day)
        assertEquals(1, snapshot.c[0].start)
        assertEquals(12, snapshot.c[0].end)
        assertEquals("all", snapshot.c[0].parity)
    }

    @Test
    fun load_requestsSiteAssetsAndCachesByTtl() = runTest {
        val transport = FakeTransport { ApiResponse(200, rowWithObjectBody) }
        val dir = createTempDir("tt-cache-")
        var now = 1_000_000L
        val repo = DefaultTimetableRepository(
            api = testApiClient(transport),
            cache = FileTimetableCache(dir),
            clock = { now },
        )
        val first = repo.load()
        assertTrue(first is TtLoadResult.Success)
        assertFalse((first as TtLoadResult.Success).fromCache)
        assertTrue(transport.paths().single().contains("site_assets"))
        assertTrue(transport.paths().single().contains("id=eq.tt"))

        // TTL 内：不发请求，返回内存缓存
        now += 60_000L
        val second = repo.load()
        assertTrue((second as TtLoadResult.Success).fromCache)
        assertEquals(1, transport.requests.size)

        // TTL 过期 force：重新请求
        now += 7 * 3600_000L
        repo.load(force = true)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun load_fallsBackToCacheOnFailure() = runTest {
        val transport = FakeTransport { ApiResponse(200, rowWithObjectBody) }
        val dir = createTempDir("tt-cache-")
        var now = 1_000_000L
        val repo = DefaultTimetableRepository(
            api = testApiClient(transport),
            cache = FileTimetableCache(dir),
            clock = { now },
        )
        assertTrue(repo.load() is TtLoadResult.Success)

        // 网络失败 + force：仍返回旧缓存（fromCache=true）
        transport.responder = { throw java.io.IOException("offline") }
        now += 7 * 3600_000L
        val fallback = repo.load(force = true)
        assertTrue(fallback is TtLoadResult.Success)
        assertTrue((fallback as TtLoadResult.Success).fromCache)
        assertEquals(2, fallback.snapshot.c.size)
    }

    @Test
    fun load_noCacheAndFailure_returnsFailure() = runTest {
        val transport = FakeTransport { ApiResponse(500, """{"message":"boom"}""") }
        val repo = DefaultTimetableRepository(
            api = testApiClient(transport),
            cache = FileTimetableCache(createTempDir("tt-cache-")),
            clock = { 1_000_000L },
        )
        val result = repo.load()
        assertTrue(result is TtLoadResult.Failure)
    }

    @Test
    fun load_emptyCourses_returnsEmptyKind() = runTest {
        val transport = FakeTransport { ApiResponse(200, """[{"body":{"c":[],"t":"2026-08-31"}}]""") }
        val repo = DefaultTimetableRepository(
            api = testApiClient(transport),
            cache = FileTimetableCache(createTempDir("tt-cache-")),
            clock = { 1_000_000L },
        )
        val result = repo.load()
        assertTrue(result is TtLoadResult.Failure)
        assertEquals(TtFailureKind.EMPTY, (result as TtLoadResult.Failure).kind)
        assertEquals("暂无课表数据", result.message)
    }

    @Test
    fun load_noToken_showsNoDataNotFormatError() = runTest {
        val transport = FakeTransport { ApiResponse(401, """{"message":"invalid token"}""") }
        val repo = DefaultTimetableRepository(
            api = testApiClient(transport),
            cache = FileTimetableCache(createTempDir("tt-cache-")),
            clock = { 1_000_000L },
        )
        val result = repo.load()
        assertTrue(result is TtLoadResult.Failure)
        assertEquals(TtFailureKind.NO_TOKEN, (result as TtLoadResult.Failure).kind)
    }

    @Test
    fun load_networkFailure_kindNetwork() = runTest {
        val transport = FakeTransport { throw java.io.IOException("offline") }
        val repo = DefaultTimetableRepository(
            api = testApiClient(transport),
            cache = FileTimetableCache(createTempDir("tt-cache-")),
            clock = { 1_000_000L },
        )
        val result = repo.load()
        assertTrue(result is TtLoadResult.Failure)
        assertEquals(TtFailureKind.NETWORK, (result as TtLoadResult.Failure).kind)
    }

    @Test
    fun load_badJson_kindFormat() = runTest {
        val transport = FakeTransport { ApiResponse(200, "oops") }
        val repo = DefaultTimetableRepository(
            api = testApiClient(transport),
            cache = FileTimetableCache(createTempDir("tt-cache-")),
            clock = { 1_000_000L },
        )
        val result = repo.load()
        assertTrue(result is TtLoadResult.Failure)
        assertEquals(TtFailureKind.FORMAT, (result as TtLoadResult.Failure).kind)
        assertEquals("课表数据格式异常", result.message)
    }
}

