package com.furina.classtools

import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.HttpMethod
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.visits.VisitDeviceId
import com.furina.classtools.data.visits.VisitReporter
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 安卓访问上报单测（契约 `web/统计多端_最新登录_契约_20261118.md` §1/§3）：
 * 全部走 [FakeTransport]（不发真实网络、不用真实账号/token），固定 clock + 固定时区 +
 * 内存 deviceId，断言 POST 路径/请求头/请求体、小时去重、无会话不上报、失败静默。
 */
class VisitReporterTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val fixedNow: Long = LocalDateTime.of(2026, 11, 18, 21, 32, 45)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
    private val slot: String = "2026-11-18-21"
    private val did: String = "0123456789abcdef"
    private val sid: String = "3260799999" // 官方测试身份（公开），不涉及真实账号
    private val name: String = "测试账号"

    private val session = Session(
        sid = sid,
        name = name,
        token = "test-token",
        issuedAt = fixedNow,
    )

    private fun reporter(
        transport: FakeTransport,
        currentSession: () -> Session? = { session },
        clock: () -> Long = { fixedNow },
        deviceId: () -> String = { did },
    ): VisitReporter = VisitReporter(
        api = testApiClient(transport),
        currentSession = currentSession,
        deviceIdProvider = deviceId,
        clock = clock,
        zoneId = zone,
    )

    private fun rowOf(transport: FakeTransport): JsonObject {
        val request = transport.lastRequest()
        assertEquals(HttpMethod.POST, request.method)
        assertTrue(request.url.endsWith("/visits"))
        assertEquals(VisitReporter.PREFER, request.headers["Prefer"])
        val rows = NetJson.parseToJsonElement(requireNotNull(request.body)) as JsonArray
        assertEquals(1, rows.size)
        return rows[0] as JsonObject
    }

    // ------------------------------------------------------------ 请求体

    @Test
    fun reportOnce_postsAndroidVisitRow() = runTest {
        val transport = FakeTransport { ApiResponse(201, "") }

        assertTrue(reporter(transport).reportOnce())

        assertEquals(1, transport.requests.size)
        val request = transport.lastRequest()
        assertEquals("test-token", request.headers["X-Class-Token"])

        val row = rowOf(transport)
        assertEquals("$sid|$slot|android", row["id"]!!.jsonPrimitive.content)
        assertEquals(slot, row["slot"]!!.jsonPrimitive.content)
        assertEquals(sid, row["sid"]!!.jsonPrimitive.content)
        assertEquals(name, row["name"]!!.jsonPrimitive.content)
        assertEquals(did, row["did"]!!.jsonPrimitive.content)
        assertEquals("android", row["client"]!!.jsonPrimitive.content)
        assertEquals(fixedNow, row["ts"]!!.jsonPrimitive.long)
    }

    @Test
    fun reportOnce_sameSidAndSlotOnlyOncePerProcess() = runTest {
        var now = fixedNow
        val transport = FakeTransport { ApiResponse(201, "") }
        val reporter = reporter(transport, clock = { now })

        assertTrue(reporter.reportOnce())
        assertFalse(reporter.reportOnce()) // 同进程同 sid+slot 去重
        assertEquals(1, transport.requests.size)

        // 下一个小时槽：同一进程/同一会话允许再报一行。
        now = fixedNow + 60L * 60L * 1000L
        assertTrue(reporter.reportOnce())
        assertEquals(2, transport.requests.size)
        val row = rowOf(transport)
        assertEquals("$sid|2026-11-18-22|android", row["id"]!!.jsonPrimitive.content)
        assertEquals("2026-11-18-22", row["slot"]!!.jsonPrimitive.content)
    }

    @Test
    fun reportOnce_differentSidSameSlotIsNotDeduped() = runTest {
        val transport = FakeTransport { ApiResponse(201, "") }
        var current: Session? = session
        val reporter = reporter(transport, currentSession = { current })

        assertTrue(reporter.reportOnce())
        current = session.copy(sid = "9999000001", name = "临时QA")
        assertTrue(reporter.reportOnce())
        assertEquals(2, transport.requests.size)
        assertEquals("9999000001", rowOf(transport)["sid"]!!.jsonPrimitive.content)
    }

    // ------------------------------------------------------------ 不上报场景

    @Test
    fun reportOnce_withoutSessionDoesNotPost() = runTest {
        val transport = FakeTransport { ApiResponse(201, "") }
        assertFalse(reporter(transport, currentSession = { null }).reportOnce())
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun reportOnce_debugSessionDoesNotPost() = runTest {
        val transport = FakeTransport { ApiResponse(201, "") }
        assertFalse(reporter(transport, currentSession = { session.copy(debug = true) }).reportOnce())
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun reportOnce_blankSidOrNameDoesNotPost() = runTest {
        val transport = FakeTransport { ApiResponse(201, "") }

        assertFalse(reporter(transport, currentSession = { session.copy(sid = "  ") }).reportOnce())
        assertFalse(reporter(transport, currentSession = { session.copy(name = " ") }).reportOnce())
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun reportOnce_invalidDeviceIdDoesNotPost() = runTest {
        val transport = FakeTransport { ApiResponse(201, "") }
        assertFalse(reporter(transport, deviceId = { "not-hex" }).reportOnce())
        assertTrue(transport.requests.isEmpty())
    }

    // ------------------------------------------------------------ 失败静默/可重试

    @Test
    fun reportOnce_networkFailureIsSilentAndRetriable() = runTest {
        var failing = true
        val transport = FakeTransport {
            if (failing) throw IOException("offline") else ApiResponse(201, "")
        }
        val reporter = reporter(transport)

        assertFalse(reporter.reportOnce()) // 不抛异常
        assertEquals(1, transport.requests.size)

        failing = false
        assertTrue(reporter.reportOnce()) // 失败不占用 slot，下次可重试
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun reportOnce_httpErrorReturnsFalse() = runTest {
        val transport = FakeTransport {
            ApiResponse(400, """{"code":"PGRST204","message":"column client does not exist"}""")
        }
        assertFalse(reporter(transport).reportOnce())
    }

    // ------------------------------------------------------------ did 持久化

    @Test
    fun deviceId_generatesOnceAndPersists() {
        val store = InMemoryVisitDeviceIdStore()
        val generated = mutableListOf("0123456789abcdef", "fedcba9876543210")
        val deviceId = VisitDeviceId(store) { generated.removeAt(0) }

        assertEquals("0123456789abcdef", deviceId.get())
        assertEquals("0123456789abcdef", store.value)
        assertEquals("0123456789abcdef", deviceId.get()) // 内存缓存，不再生成
        assertTrue(generated.isNotEmpty()) // 只消耗了一个随机值

        // 模拟进程重启：新实例读持久化值，不再生成。
        val restarted = VisitDeviceId(store) { "fedcba9876543210" }
        assertEquals("0123456789abcdef", restarted.get())
    }

    @Test
    fun deviceId_repairsInvalidPersistedValue() {
        val store = InMemoryVisitDeviceIdStore("not-a-device-id")
        val deviceId = VisitDeviceId(store) { "0011223344556677" }

        assertEquals("0011223344556677", deviceId.get())
        assertEquals("0011223344556677", store.value)
    }

    @Test
    fun randomHex16_isLowercaseHex16() {
        repeat(20) {
            assertTrue(VisitReporter.isValidDeviceId(VisitDeviceId.randomHex16()))
        }
    }

    private class InMemoryVisitDeviceIdStore(initial: String? = null) : VisitDeviceId.Store {
        var value: String? = initial

        override fun read(): String? = value

        override fun write(value: String) {
            this.value = value
        }
    }
}
