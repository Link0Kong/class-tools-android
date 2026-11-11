package com.furina.classtools.data.diag

import com.furina.classtools.FakeTransport
import com.furina.classtools.createTempDir
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.testApiClient
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 崩溃/异常上报单测（契约 §2：fingerprint 10 分钟去重、每批 ≤3 条、失败落队列补报、
 * 崩溃信息脱敏且不泄漏任何凭据）。
 */
class ClientDiagReporterTest {

    private val sid = "3260707024"
    private val appVersion = "3.0.0"

    private fun row(
        fingerprint: String = "fp-1",
        ts: Long = 1_000_000L,
        message: String = "boom",
        id: String = "an_" + fingerprint,
    ): JsonObject = DiagPayload.buildRow(
        id = id,
        sid = sid,
        appVersion = appVersion,
        kind = "error",
        level = "error",
        message = message,
        fingerprint = fingerprint,
        now = ts,
    )

    private fun repository(transport: FakeTransport): DefaultDiagRepository =
        DefaultDiagRepository(testApiClient(transport), currentSid = { sid })

    // ------------------------------------------------------------ 限频

    @Test
    fun rateLimiter_sameFingerprintOnlyOncePer10Minutes() {
        val limiter = ClientDiagRateLimiter()
        limiter.beginBatch()
        assertTrue(limiter.allow("fp-a", 0L))
        assertFalse(limiter.allow("fp-a", 60_000L))
        assertFalse(limiter.allow("fp-a", 9L * 60 * 1000))
        assertTrue(limiter.allow("fp-a", 10L * 60 * 1000))
        assertTrue(limiter.allow("fp-b", 10L * 60 * 1000))
    }

    @Test
    fun rateLimiter_maxThreePerBatch_beginBatchResetsCountNotWindow() {
        val limiter = ClientDiagRateLimiter()
        limiter.beginBatch()
        assertTrue(limiter.allow("a", 0L))
        assertTrue(limiter.allow("b", 0L))
        assertTrue(limiter.allow("c", 0L))
        assertFalse(limiter.allow("d", 0L))
        assertEquals(3, limiter.batchCount())

        limiter.beginBatch()
        assertEquals(0, limiter.batchCount())
        assertTrue(limiter.allow("d", 1L))
        assertFalse(limiter.allow("a", 2L)) // 指纹窗口不因新批次重置
    }

    // ------------------------------------------------------------ 上报/队列

    @Test
    fun reporter_sentThenSkippedByFingerprintWindow() = runTest {
        val transport = FakeTransport { ApiResponse(201, "") }
        val pending = InMemoryClientDiagPendingStore()
        val reporter = ClientDiagReporter(repository(transport), pending)
        reporter.beginBatch()
        assertEquals(DiagReportOutcome.Sent, reporter.report(row(fingerprint = "fp", ts = 1_000L)))
        assertEquals(DiagReportOutcome.Skipped, reporter.report(row(fingerprint = "fp", ts = 2_000L)))
        assertEquals(1, transport.requests.size)
        assertEquals(0, pending.size())
    }

    @Test
    fun reporter_networkFailureQueuesAndDrainSends() = runTest {
        var failing = true
        val transport = FakeTransport {
            if (failing) throw java.io.IOException("offline") else ApiResponse(201, "")
        }
        val pending = InMemoryClientDiagPendingStore()
        val reporter = ClientDiagReporter(repository(transport), pending)
        reporter.beginBatch()
        assertEquals(DiagReportOutcome.Queued, reporter.report(row(fingerprint = "fp", ts = 1_000L)))
        assertEquals(1, pending.size())

        failing = false
        val sent = reporter.drainPending()
        assertEquals(1, sent)
        assertEquals(0, pending.size())
    }

    @Test
    fun reporter_batchLimitCapsAutoReportsAtThree() = runTest {
        val transport = FakeTransport { ApiResponse(201, "") }
        val reporter = ClientDiagReporter(repository(transport), InMemoryClientDiagPendingStore())
        reporter.beginBatch()
        assertEquals(DiagReportOutcome.Sent, reporter.report(row("a", 1L)))
        assertEquals(DiagReportOutcome.Sent, reporter.report(row("b", 1L)))
        assertEquals(DiagReportOutcome.Sent, reporter.report(row("c", 1L)))
        assertEquals(DiagReportOutcome.Skipped, reporter.report(row("d", 1L)))
        assertEquals(3, transport.requests.size)
    }

    @Test
    fun reportable_requiresFingerprintAndTs() {
        assertTrue(ClientDiagReporter.isReportable(row()))
        assertFalse(ClientDiagReporter.isReportable(row(fingerprint = "", ts = 1L)))
        assertFalse(ClientDiagReporter.isReportable(row(fingerprint = "fp", ts = 0L)))
    }

    // ------------------------------------------------------------ 崩溃记录

    @Test
    fun crashRecorder_sanitizesAndQueuesMinimalDetail() {
        val pending = InMemoryClientDiagPendingStore()
        val recorder = CrashDiagRecorder(
            pending = pending,
            appVersion = appVersion,
            sidProvider = { sid },
            deviceProvider = {
                buildJsonObject {
                    put("platform", "android")
                    put("model", "x") // 旧字段：必须被 v2.1 白名单过滤
                    put("language", "zh-CN")
                }
            },
            now = { 1_700_000_000_000L },
            suffixProvider = { "crashtestsuffix" },
        )
        recorder.record(
            Thread.currentThread(),
            IllegalStateException(
                "登录失败 token=supersecret C:\\Users\\Ko_Li\\pwd.txt https://x.example/a?pwd=hunter2",
            ),
        )
        assertEquals(1, pending.size())
        val saved = pending.readAll().single()
        assertEquals("crash", (saved["kind"] as JsonPrimitive).content)
        assertEquals("error", (saved["level"] as JsonPrimitive).content)
        assertEquals("android", (saved["platform"] as JsonPrimitive).content)
        assertEquals(sid, (saved["sid"] as JsonPrimitive).content)
        assertEquals("an_crashtestsuffix", (saved["id"] as JsonPrimitive).content)
        val text = saved.toString()
        assertFalse(text.contains("supersecret"))
        assertFalse(text.contains("hunter2"))
        assertFalse(text.contains("C:\\Users\\Ko_Li"))
        assertTrue(text.contains("[redacted]") || text.contains("[url]"))
        val detail = saved["detail"] as JsonObject
        assertEquals(setOf("check", "code"), detail.keys)
        assertEquals("crash", (detail["check"] as JsonPrimitive).content)
        assertEquals("java.lang.IllegalStateException", (detail["code"] as JsonPrimitive).content)
        assertFalse(detail.toString().contains(sid))
        // device 白名单过滤：品牌/型号/语言不得出现
        val device = saved["device"] as JsonObject
        assertEquals(setOf("platform"), device.keys)
        assertFalse(device.toString().contains("model"))
    }

    @Test
    fun installCrashHandler_recordsThenDelegatesToPrevious() {
        val pending = InMemoryClientDiagPendingStore()
        val recorder = CrashDiagRecorder(
            pending = pending,
            appVersion = appVersion,
            sidProvider = { sid },
            now = { 1L },
        )
        val previousCalled = AtomicBoolean(false)
        val previous = Thread.UncaughtExceptionHandler { _, _ -> previousCalled.set(true) }
        val original = Thread.getDefaultUncaughtExceptionHandler()
        try {
            val handler = installCrashHandler(recorder, previous)
            handler.uncaughtException(Thread.currentThread(), RuntimeException("boom"))
            assertTrue(previousCalled.get())
            assertEquals(1, pending.size())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }

    @Test
    fun filePendingStore_roundTripAndRemove() {
        val dir = createTempDir("diag-pending-")
        val store = FileClientDiagPendingStore(dir)
        assertEquals(0, store.size())
        store.append(row("fp-1", 1L))
        store.append(row("fp-2", 2L))
        assertEquals(2, store.size())
        val rows = store.readAll()
        assertEquals("fp-1", (rows[0]["fingerprint"] as JsonPrimitive).content)
        store.replaceAll(listOf(rows[1]))
        assertEquals(1, store.size())
        store.replaceAll(emptyList())
        assertEquals(0, store.size())
        // 坏行/空行不崩
        java.io.File(dir, FileClientDiagPendingStore.FILE_NAME)
            .writeText("not-json\n\n" + NetJson.encodeToString(JsonObject.serializer(), row("fp-3", 3L)), Charsets.UTF_8)
        assertEquals(1, store.size())
    }

    @Test
    fun crashRecorder_sidAbsentWhenLoggedOut() {
        val pending = InMemoryClientDiagPendingStore()
        val recorder = CrashDiagRecorder(
            pending = pending,
            appVersion = appVersion,
            sidProvider = { null },
            now = { 1L },
        )
        recorder.record(Thread.currentThread(), RuntimeException("x"))
        assertEquals("", (pending.readAll().single()["sid"] as JsonPrimitive).contentOrNull)
    }
}
