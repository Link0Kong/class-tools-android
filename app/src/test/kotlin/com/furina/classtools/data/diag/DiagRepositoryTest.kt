package com.furina.classtools.data.diag

import com.furina.classtools.FakeTransport
import com.furina.classtools.data.network.ApiError
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.HttpMethod
import com.furina.classtools.testApiClient
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 诊断仓库单测：端点路径/请求头、RPC body、client_diag 上报 body、只看自己的上报、错误映射。
 */
class DiagRepositoryTest {

    private val sid = "2024000001"

    private fun repository(
        transport: FakeTransport,
        currentSid: String? = sid,
    ): DefaultDiagRepository = DefaultDiagRepository(
        api = testApiClient(transport),
        currentSid = { currentSid },
    )

    @Test
    fun loadServiceHealth_usesContractPathAndParsesRows() = runTest {
        val transport = FakeTransport {
            ApiResponse(200, """[{"id":"cloudbase","name":"云端数据库","kind":"cloud","status":"ok","detail":"200","meta":{},"checked_at":"2026-11-08T13:31:00+08:00"}]""")
        }
        val result = repository(transport).loadServiceHealth()
        assertTrue(result is DiagResult.Success)
        assertEquals("cloudbase", (result as DiagResult.Success).value.single().id)
        val path = transport.lastRequest().url
        assertTrue(path.contains("service_health?select=id,name,kind,status,detail,meta,checked_at"))
        assertTrue(path.contains("order=id.asc"))
        // 班级 token 头必须带（诊断对全班可见，但仍需有效身份）
        assertEquals("test-token", transport.lastRequest().headers["X-Class-Token"])
        assertTrue(transport.lastRequest().headers["Authorization"].orEmpty().startsWith("Bearer "))
    }

    @Test
    fun loadAnalyses_andRepairs_useContractPaths() = runTest {
        val transport = FakeTransport { request ->
            if (request.url.contains("diag_analysis")) {
                ApiResponse(200, """[{"id":"an-1","ts":1,"severity":"warn","title":"t"}]""")
            } else {
                ApiResponse(200, """[{"id":"rt-1","created_at":2,"action":"recheck_health","status":"done"}]""")
            }
        }
        val repo = repository(transport)
        assertEquals("an-1", (repo.loadAnalyses() as DiagResult.Success).value.single().id)
        assertTrue(transport.lastRequest().url.contains("order=ts.desc&limit=20"))
        assertEquals("rt-1", (repo.loadRepairs() as DiagResult.Success).value.single().id)
        assertTrue(transport.lastRequest().url.contains("order=created_at.desc&limit=20"))
    }

    @Test
    fun agentInfo_postsEmptyBodyAndParsesStatus() = runTest {
        val transport = FakeTransport {
            ApiResponse(200, """{"ok":true,"online":true,"seen_at":1760000000000,"host":"local-windows","version":"1.0"}""")
        }
        val result = repository(transport).agentInfo()
        assertTrue(result is DiagResult.Success)
        assertTrue((result as DiagResult.Success).value.online)
        val request = transport.lastRequest()
        assertEquals(HttpMethod.POST, request.method)
        assertTrue(request.url.endsWith("rpc/diag_agent_info"))
        assertEquals("{}", request.body)
    }

    @Test
    fun measureGateway_returnsLatencyAndServerDate() = runTest {
        val serverDate = ZonedDateTime.of(2026, 11, 8, 13, 31, 0, 0, ZoneOffset.ofHours(8))
            .format(DateTimeFormatter.RFC_1123_DATE_TIME)
        val transport = FakeTransport {
            ApiResponse(200, """[{"id":"cloudbase"}]""", headers = mapOf("Date" to serverDate))
        }
        val result = repository(transport).measureGateway()
        assertTrue(result is DiagResult.Success)
        val probe = (result as DiagResult.Success).value
        assertTrue(probe.latencyMs >= 0)
        assertEquals(
            java.time.Instant.parse("2026-11-08T05:31:00Z").toEpochMilli(),
            probe.serverEpochMs,
        )
        assertTrue(transport.lastRequest().url.contains("service_health?select=id&limit=1"))
    }

    @Test
    fun measureGateway_networkFailure_mapsToFailure() = runTest {
        val transport = FakeTransport { throw java.io.IOException("offline") }
        val result = repository(transport).measureGateway()
        assertTrue(result is DiagResult.Failure)
        assertTrue((result as DiagResult.Failure).error is ApiError.Network)
        // 计时请求不重试：单次调用只发 1 个请求
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun reportClientDiag_postsSingleRowArray() = runTest {
        val transport = FakeTransport { ApiResponse(201, "") }
        val row = DiagPayload.manualRow(sid, "3.0.0", "手动上报", emptyMap(), JsonObject(emptyMap()), 1L)
        val result = repository(transport).reportClientDiag(row)
        assertTrue(result is com.furina.classtools.data.network.ApiResult.Success)
        val request = transport.lastRequest()
        assertEquals(HttpMethod.POST, request.method)
        assertTrue(request.url.endsWith("client_diag"))
        val body = kotlinx.serialization.json.Json.parseToJsonElement(request.body!!) as JsonArray
        assertEquals(1, body.size)
        val posted = body[0] as JsonObject
        assertEquals(sid, (posted["sid"] as JsonPrimitive).content)
        assertEquals("android", (posted["platform"] as JsonPrimitive).content)
        assertEquals("manual", (posted["kind"] as JsonPrimitive).content)
    }

    @Test
    fun loadMyReports_filtersOwnSidAndNeedsLogin() = runTest {
        val transport = FakeTransport { ApiResponse(200, """[{"id":"an-1","kind":"crash","level":"error","ts":1}]""") }
        val repo = repository(transport)
        val result = repo.loadMyReports()
        assertTrue(result is DiagResult.Success)
        assertEquals("an-1", (result as DiagResult.Success).value.single().id)
        val path = transport.lastRequest().url
        assertTrue(path.contains("client_diag?select=*"))
        assertTrue(path.contains("order=ts.desc&limit=20"))
        assertTrue(path.contains("sid=eq.$sid"))
    }

    @Test
    fun loadMyReports_withoutSid_returnsConfigFailureWithoutRequest() = runTest {
        val transport = FakeTransport { ApiResponse(200, "[]") }
        val result = repository(transport, currentSid = null).loadMyReports()
        assertTrue(result is DiagResult.Failure)
        assertTrue((result as DiagResult.Failure).error is ApiError.Config)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun permissionDenied_mapsToHttpFailure() = runTest {
        val transport = FakeTransport { ApiResponse(401, """{"code":"DATABASE_42501","message":"denied"}""") }
        val result = repository(transport).loadServiceHealth()
        assertTrue(result is DiagResult.Failure)
        assertEquals("DATABASE_42501", ((result as DiagResult.Failure).error as ApiError.Http).postgrestCode)
    }

    @Test
    fun timedToProbe_keepsLatencyWhenServerDateMissing() {
        val probe = DefaultDiagRepository.timedToProbe(
            com.furina.classtools.data.network.TimedResponse(body = "[]", latencyMs = 321L, serverEpochMs = null),
        )
        assertEquals(321L, probe.latencyMs)
        assertNull(probe.serverEpochMs)
        assertNotNull(DiagParser.parseAgentInfo("""{"ok":true,"online":false}"""))
    }
}
