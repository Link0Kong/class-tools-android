package com.furina.classtools.data.power

import com.furina.classtools.FakeTransport
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.testApiClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 电费仓库/解析单测（E22 power 当前值；E23 power_history 采样）。
 *
 * 口径：2 min 当前值 TTL；5 min 每档历史缓存；空结果/失败不写缓存；未选宿舍 0 请求。
 */
class PowerRepositoryTest {

    private val now: Long = 1_794_016_800_000L // 2026-11-07T02:00:00Z = 10:00 +08:00

    private val rowJson = """
        [{"id":"4-23A---305","label":"23号楼A座305","building":"23号楼A座","room":"305",
          "quantity":35.62,"unit":"度","low":false,"updated_at":"2026-11-07T02:00:00+00:00"}]
    """.trimIndent()

    private val historyJson = """
        [{"ts":"2026-11-06T02:00:00+00:00","quantity":40.12},
         {"ts":"2026-11-07T02:00:00+00:00","quantity":35.62},
         {"ts":"bad","quantity":1.0},
         {"ts":"2026-11-07T03:00:00+00:00","quantity":null}]
    """.trimIndent()

    @Test
    fun parseRow_numericAndStringQuantity() {
        val row = DefaultPowerRepository.parseRow(com.furina.classtools.data.network.NetJson, rowJson)
        assertNotNull(row)
        assertEquals("4-23A---305", row!!.id)
        assertEquals("23号楼A座", row.building)
        assertEquals(35.62, row.q!!, 0.0001)
        assertEquals("2026-11-07T02:00:00+00:00", row.updatedAt)

        val stringQty = """[{"id":"x","quantity":"12.5"}]"""
        assertEquals(12.5, DefaultPowerRepository.parseRow(com.furina.classtools.data.network.NetJson, stringQty)!!.q!!, 0.0001)

        assertNull(DefaultPowerRepository.parseRow(com.furina.classtools.data.network.NetJson, "[]"))
        assertNull(DefaultPowerRepository.parseRow(com.furina.classtools.data.network.NetJson, "[]").let { null } ?: DefaultPowerRepository.parseRow(com.furina.classtools.data.network.NetJson, "[{\"label\":\"no-id\"}]"))
        assertNull(DefaultPowerRepository.parseRow(com.furina.classtools.data.network.NetJson, "oops"))
    }

    @Test
    fun parseHistory_skipsInvalidAndSortsByTime() {
        val points = DefaultPowerRepository.parseHistory(com.furina.classtools.data.network.NetJson, historyJson)
        assertEquals(2, points.size)
        assertTrue(points[0].t < points[1].t)
        assertEquals(40.12, points[0].q, 0.0001)
        assertEquals(35.62, points[1].q, 0.0001)
    }

    @Test
    fun numberOrNull_handlesNumericForms() {
        val json = com.furina.classtools.data.network.NetJson
        assertNull(DefaultPowerRepository.numberOrNull(null))
        assertNotNull(DefaultPowerRepository.numberOrNull(json.parseToJsonElement("12.5")))
        assertNull(DefaultPowerRepository.numberOrNull(json.parseToJsonElement("null")))
        assertNull(DefaultPowerRepository.numberOrNull(json.parseToJsonElement("\"abc\"")))
    }

    @Test
    fun loadRow_requestsAndCachesByTtl() = runTest {
        val transport = FakeTransport { ApiResponse(200, rowJson) }
        var clock = now
        val repo = DefaultPowerRepository(api = testApiClient(transport), clock = { clock })

        val first = repo.loadRow("4-23A---305")
        assertTrue(first is PowerRowResult.Success)
        assertFalse((first as PowerRowResult.Success).fromCache)
        val path = transport.paths().single()
        assertTrue(path.contains("power?select="))
        assertTrue(path.contains("id=eq.4-23A---305"))

        // TTL 内：不重复请求
        clock += 60_000L
        val second = repo.loadRow("4-23A---305")
        assertTrue((second as PowerRowResult.Success).fromCache)
        assertEquals(1, transport.requests.size)

        // TTL 过期 + force：重新请求
        clock += 3 * 60_000L
        repo.loadRow("4-23A---305", force = true)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun loadRow_blankRoomReturnsFailureWithoutRequest() = runTest {
        val transport = FakeTransport { ApiResponse(200, rowJson) }
        val repo = DefaultPowerRepository(api = testApiClient(transport), clock = { now })
        assertTrue(repo.loadRow("") is PowerRowResult.Failure)
        assertTrue(repo.loadRow("bad-room") is PowerRowResult.Failure)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun loadRow_failureFallsBackToCache() = runTest {
        val transport = FakeTransport { ApiResponse(200, rowJson) }
        var clock = now
        val repo = DefaultPowerRepository(api = testApiClient(transport), clock = { clock })
        assertTrue(repo.loadRow("4-23A---305") is PowerRowResult.Success)

        transport.responder = { throw java.io.IOException("offline") }
        clock += 10 * 60_000L
        val fallback = repo.loadRow("4-23A---305", force = true)
        assertTrue(fallback is PowerRowResult.Success)
        assertTrue((fallback as PowerRowResult.Success).fromCache)
        assertEquals(35.62, fallback.row.q!!, 0.0001)
    }

    @Test
    fun loadHistory_buildsWindowQueryAndEncodesCut() = runTest {
        val transport = FakeTransport { ApiResponse(200, historyJson) }
        val repo = DefaultPowerRepository(api = testApiClient(transport), clock = { now })
        val result = repo.loadHistory("4-23A---305", PowerRange.H24)
        assertTrue(result is PowerHistoryResult.Success)
        assertEquals(2, (result as PowerHistoryResult.Success).points.size)
        val path = transport.paths().single()
        assertTrue(path.contains("power_history?select=ts,quantity"))
        assertTrue(path.contains("roomverify=eq.4-23A---305"))
        assertTrue(path.contains("ts=gte."))
        assertTrue(path.contains("2026-11-06T02%3A00%3A00Z"))
        assertTrue(path.contains("order=ts.asc"))
        assertTrue(path.contains("limit=2000"))
    }

    @Test
    fun loadHistory_cachesPerRangeAndDoesNotCacheEmpty() = runTest {
        var body = historyJson
        val transport = FakeTransport { ApiResponse(200, body) }
        var clock = now
        val repo = DefaultPowerRepository(api = testApiClient(transport), clock = { clock })

        // 非空：5 min 缓存
        repo.loadHistory("4-23A---305", PowerRange.H24)
        repo.loadHistory("4-23A---305", PowerRange.H24)
        assertEquals(1, transport.requests.size)

        // 不同区间：单独请求
        repo.loadHistory("4-23A---305", PowerRange.D7)
        assertEquals(2, transport.requests.size)

        // 空结果不缓存：每次都要请求
        body = "[]"
        clock += 60_000L
        repo.loadHistory("4-23A---305", PowerRange.D7, force = true)
        repo.loadHistory("4-23A---305", PowerRange.D7, force = true)
        assertEquals(4, transport.requests.size)
        // 空结果不覆盖旧缓存：仍返回上次非空缓存
        val cachedAfter = repo.loadHistory("4-23A---305", PowerRange.D7)
        assertTrue(cachedAfter is PowerHistoryResult.Success)
        assertEquals(2, (cachedAfter as PowerHistoryResult.Success).points.size)
    }

    @Test
    fun loadHistory_failureFallsBackToCachedRange() = runTest {
        val transport = FakeTransport { ApiResponse(200, historyJson) }
        var clock = now
        val repo = DefaultPowerRepository(api = testApiClient(transport), clock = { clock })
        repo.loadHistory("4-23A---305", PowerRange.H24)

        transport.responder = { ApiResponse(500, """{"message":"boom"}""") }
        clock += 10 * 60_000L
        val fallback = repo.loadHistory("4-23A---305", PowerRange.H24, force = true)
        assertTrue(fallback is PowerHistoryResult.Success)
        assertEquals(2, (fallback as PowerHistoryResult.Success).points.size)
        assertTrue(fallback.fromCache)
    }

    @Test
    fun cacheKey_isPerRoomAndRange() {
        assertEquals("4-23A---305|24h", DefaultPowerRepository.cacheKey("4-23A---305", PowerRange.H24))
        assertEquals("1-13---301|30d", DefaultPowerRepository.cacheKey("1-13---301", PowerRange.D30))
        // 隐藏比较窗口单独缓存键（不与区间历史串用）
        assertEquals("4-23A---305|14d", DefaultPowerRepository.cacheKey("4-23A---305", PowerCompareRange.D14))
        assertEquals("4-23A---305|70d", DefaultPowerRepository.cacheKey("4-23A---305", PowerCompareRange.D70))
    }

    // ------------------------------------------------------------ 隐藏比较窗口（14d/70d）

    @Test
    fun loadCompareHistory_buildsHiddenWindowQueryAndCaches() = runTest {
        val transport = FakeTransport { ApiResponse(200, historyJson) }
        val repo = DefaultPowerRepository(api = testApiClient(transport), clock = { now })

        val d14 = repo.loadCompareHistory("4-23A---305", PowerCompareRange.D14)
        assertTrue(d14 is PowerHistoryWindowResult.Success)
        assertEquals(2, (d14 as PowerHistoryWindowResult.Success).points.size)
        assertFalse(d14.fromCache)
        val path14 = transport.paths().single()
        assertTrue(path14.contains("power_history?select=ts,quantity"))
        assertTrue(path14.contains("roomverify=eq.4-23A---305"))
        assertTrue(path14.contains("order=ts.asc"))
        assertTrue(path14.contains("limit=2000"))
        // now - 14d = 2026-10-24T02:00:00Z
        assertTrue(path14.contains("2026-10-24T02%3A00%3A00Z"))

        // 5 min 内同窗口命中缓存：不再请求
        val cached = repo.loadCompareHistory("4-23A---305", PowerCompareRange.D14)
        assertTrue((cached as PowerHistoryWindowResult.Success).fromCache)
        assertEquals(1, transport.requests.size)

        // 70d 单独窗口/缓存键
        repo.loadCompareHistory("4-23A---305", PowerCompareRange.D70)
        assertEquals(2, transport.requests.size)
        // now - 70d = 2026-08-29T02:00:00Z
        assertTrue(transport.paths().last().contains("2026-08-29T02%3A00%3A00Z"))
    }

    @Test
    fun loadCompareHistory_blankRoomReturnsFailureWithoutRequest() = runTest {
        val transport = FakeTransport { ApiResponse(200, historyJson) }
        val repo = DefaultPowerRepository(api = testApiClient(transport), clock = { now })
        assertTrue(repo.loadCompareHistory("", PowerCompareRange.D14) is PowerHistoryWindowResult.Failure)
        assertTrue(repo.loadCompareHistory("bad-room", PowerCompareRange.D70) is PowerHistoryWindowResult.Failure)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun loadCompareHistory_failureFallsBackToCachedWindow() = runTest {
        val transport = FakeTransport { ApiResponse(200, historyJson) }
        var clock = now
        val repo = DefaultPowerRepository(api = testApiClient(transport), clock = { clock })
        repo.loadCompareHistory("4-23A---305", PowerCompareRange.D14)

        transport.responder = { ApiResponse(500, """{"message":"boom"}""") }
        clock += 10 * 60_000L
        val fallback = repo.loadCompareHistory("4-23A---305", PowerCompareRange.D14, force = true)
        assertTrue(fallback is PowerHistoryWindowResult.Success)
        assertEquals(2, (fallback as PowerHistoryWindowResult.Success).points.size)
        assertTrue(fallback.fromCache)
    }
}


