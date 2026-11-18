package com.furina.classtools

import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.visits.VisitsRepository
import com.furina.classtools.data.visits.VisitsResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 安卓访问统计读取单测（FakeTransport，不触真实网络/账号）：
 * URL 口径、ts 数字/字符串兼容、client 解析、失败降级。
 */
class VisitsRepositoryTest {

    @Test
    fun pull_usesStatsQueryAndParsesRows() = runTest {
        val transport = FakeTransport {
            ApiResponse(
                200,
                """[
                  {"slot":"2026-11-18-11","sid":"9000000001","name":"张三","ts":1763444700000,"client":"android"},
                  {"slot":"2026-11-18-10","sid":"9000000002","name":"李四","ts":"1763443800000","client":"mp"}
                ]""",
            )
        }
        val repo = VisitsRepository(testApiClient(transport))

        val result = repo.pull("2026-11-12-00")

        assertTrue(result is VisitsResult.Success)
        val rows = (result as VisitsResult.Success).rows
        assertEquals(2, rows.size)
        assertEquals("9000000001", rows[0].sid)
        assertEquals("android", rows[0].client)
        assertEquals(1763444700000L, rows[0].ts)
        assertEquals("mp", rows[1].client)
        assertEquals(1763443800000L, rows[1].ts)

        val request = transport.lastRequest()
        assertTrue(request.url.contains("visits?select=slot,sid,name,ts,client"))
        assertTrue(request.url.contains("slot=gte.2026-11-12-00"))
        assertTrue(request.url.contains("order=ts.desc"))
        assertTrue(request.url.contains("limit=5000"))
    }

    @Test
    fun pull_skipsAnonymousRows() = runTest {
        val transport = FakeTransport {
            ApiResponse(
                200,
                """[
                  {"slot":"2026-11-18-11","sid":"","name":"匿名","ts":1,"client":"web"},
                  {"slot":"2026-11-18-11","sid":"9000000001","name":"张三","ts":2,"client":"web"}
                ]""",
            )
        }
        val rows = (VisitsRepository(testApiClient(transport)).pull("2026-11-12-00") as VisitsResult.Success).rows
        assertEquals(1, rows.size)
        assertEquals("9000000001", rows[0].sid)
    }

    @Test
    fun pull_httpFailureReturnsFailure() = runTest {
        val transport = FakeTransport { ApiResponse(500, """{"message":"boom"}""") }
        val result = VisitsRepository(testApiClient(transport)).pull("2026-11-12-00")
        assertTrue(result is VisitsResult.Failure)
        assertTrue((result as VisitsResult.Failure).message.isNotBlank())
    }
}
