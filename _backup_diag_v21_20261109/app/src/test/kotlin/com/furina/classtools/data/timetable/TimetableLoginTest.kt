package com.furina.classtools.data.timetable

import com.furina.classtools.FakeTransport
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.HttpMethod
import com.furina.classtools.testApiClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课表登录单测：RPC 请求体契约、enqueue/status 响应解析（成功/agent_offline/rate/pwd/auth/failed）、
 * 错误映射、轮询状态机（1.5s/120s、done 停止、超时停止、网络失败可重试、auth 立即终止）。
 */
class TimetableLoginTest {

    private val snapshotJson = """{"c":[{"name":"体育（排球）","teacher":"赵阳","room":"体育馆","day":4,"start":6,"end":7,"weeks":"1-16","parity":"all"}],"t":"2026-08-31","meta":{"source":"jwxt","fetchedAt":1760000000000,"count":1}}"""

    private fun loginRepo(transport: FakeTransport): DefaultTimetableLoginRepository =
        DefaultTimetableLoginRepository(testApiClient(transport))

    // ------------------------------------------------------------ 请求体

    @Test
    fun enqueueBody_hasContractFields() {
        val body = DefaultTimetableLoginRepository.enqueueBody("p@ss-word")
        val obj = Json.parseToJsonElement(body) as JsonObject
        assertEquals("p@ss-word", (obj["p_pwd"] as JsonPrimitive).content)
        assertEquals("android", (obj["p_platform"] as JsonPrimitive).content)
        // 学号不随请求体发送（云端 sid 由 token 决定）
        assertFalse(obj.containsKey("p_sid"))
    }

    @Test
    fun statusBody_hasJobId() {
        val obj = Json.parseToJsonElement(DefaultTimetableLoginRepository.statusBody("job-1")) as JsonObject
        assertEquals("job-1", (obj["p_id"] as JsonPrimitive).content)
    }

    @Test
    fun enqueue_blankPassword_failsWithoutRequest() = runTest {
        val transport = FakeTransport { ApiResponse(200, """{"ok":true,"job_id":"x"}""") }
        val result = loginRepo(transport).enqueue("   ")
        assertTrue(result is TtLoginEnqueueResult.Failure)
        assertEquals(TtLoginFailureReason.PWD, (result as TtLoginEnqueueResult.Failure).reason)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun enqueue_postsPasswordOnlyInRequestAndParsesJobId() = runTest {
        val transport = FakeTransport {
            ApiResponse(200, """{"ok":true,"job_id":"job-abc","reused":true}""")
        }
        val result = loginRepo(transport).enqueue("secret-pwd")
        assertTrue(result is TtLoginEnqueueResult.Success)
        assertEquals("job-abc", (result as TtLoginEnqueueResult.Success).jobId)
        assertTrue(result.reused)
        val request = transport.lastRequest()
        assertEquals(HttpMethod.POST, request.method)
        assertTrue(request.url.endsWith("rpc/tt_login_enqueue"))
        assertTrue(request.body.orEmpty().contains("secret-pwd"))
        // 返回值不含密码
        assertFalse(result.toString().contains("secret-pwd"))
    }

    @Test
    fun parseEnqueue_errorReasons() {
        fun reason(raw: String): TtLoginFailureReason =
            (TtLoginParser.parseEnqueue(raw) as TtLoginEnqueueResult.Failure).reason
        assertEquals(TtLoginFailureReason.AGENT_OFFLINE, reason("""{"ok":false,"reason":"agent_offline"}"""))
        assertEquals(TtLoginFailureReason.RATE, reason("""{"ok":false,"reason":"rate"}"""))
        assertEquals(TtLoginFailureReason.PWD, reason("""{"ok":false,"reason":"pwd"}"""))
        assertEquals(TtLoginFailureReason.AUTH, reason("""{"ok":false,"reason":"auth"}"""))
        assertEquals(TtLoginFailureReason.ENCRYPT, reason("""{"ok":false,"reason":"encrypt"}"""))
        assertEquals(TtLoginFailureReason.UNKNOWN, reason("""{"ok":false,"reason":"whatever"}"""))
        assertEquals(TtLoginFailureReason.PARSE, reason("oops"))
        // 兼容单元素数组
        assertEquals(
            "job-in-array",
            (TtLoginParser.parseEnqueue("""[{"ok":true,"job_id":"job-in-array"}]""") as TtLoginEnqueueResult.Success).jobId,
        )
    }

    @Test
    fun parseStatus_runningDoneFailedExpired() {
        assertTrue(TtLoginParser.parseStatus("""{"ok":true,"status":"pending"}""") is TtLoginStatusResult.Running)
        assertTrue(TtLoginParser.parseStatus("""{"ok":true,"status":"claimed"}""") is TtLoginStatusResult.Running)

        val done = TtLoginParser.parseStatus(
            """{"ok":true,"status":"done","finished":1760000000000,"error":"","result":$snapshotJson}""",
        )
        assertTrue(done is TtLoginStatusResult.Done)
        val snapshot = (done as TtLoginStatusResult.Done).snapshot
        assertEquals(1, snapshot.c.size)
        assertEquals("体育（排球）", snapshot.c.single().name)
        assertEquals("2026-08-31", snapshot.t)

        // result 也可能是 JSON 字符串（后端 jsonb → text 形态）
        val doneStringResult = TtLoginParser.parseStatus(
            """{"ok":true,"status":"done","result":"{\"c\":[{\"name\":\"英语\",\"day\":1}]}"}""",
        )
        assertTrue(doneStringResult is TtLoginStatusResult.Done)
        assertEquals("英语", (doneStringResult as TtLoginStatusResult.Done).snapshot.c.single().name)

        val failed = TtLoginParser.parseStatus("""{"ok":true,"status":"failed","error":"pwd"}""")
        assertTrue(failed is TtLoginStatusResult.Failed)
        assertEquals(TtLoginFailureReason.PWD, (failed as TtLoginStatusResult.Failed).reason)

        val failedFreeText = TtLoginParser.parseStatus(
            """{"ok":true,"status":"failed","error":"SSO 失败 http://10.0.0.1/login?token=abc"}""",
        )
        assertEquals(TtLoginFailureReason.FAILED, (failedFreeText as TtLoginStatusResult.Failed).reason)
        assertFalse(failedFreeText.message.contains("10.0.0.1"))
        assertFalse(failedFreeText.message.contains("token=abc"))

        val expired = TtLoginParser.parseStatus("""{"ok":true,"status":"expired"}""")
        assertEquals(TtLoginFailureReason.EXPIRED, (expired as TtLoginStatusResult.Failed).reason)
    }

    @Test
    fun parseStatus_failureReasons() {
        val forbidden = TtLoginParser.parseStatus("""{"ok":false,"reason":"forbidden"}""")
        assertEquals(TtLoginFailureReason.FORBIDDEN, (forbidden as TtLoginStatusResult.Failure).reason)
        val auth = TtLoginParser.parseStatus("""{"ok":false,"reason":"auth"}""")
        assertEquals(TtLoginFailureReason.AUTH, (auth as TtLoginStatusResult.Failure).reason)
        // done 但 result 非法 → 解析失败（不会把空课表当成成功）
        val badDone = TtLoginParser.parseStatus("""{"ok":true,"status":"done","result":{"c":[]}}""")
        assertTrue(badDone is TtLoginStatusResult.Failure)
    }

    @Test
    fun repository_mapsNetworkAndAuthErrors() = runTest {
        val offline = FakeTransport { throw java.io.IOException("offline") }
        val networkResult = loginRepo(offline).enqueue("pwd")
        assertEquals(TtLoginFailureReason.NETWORK, (networkResult as TtLoginEnqueueResult.Failure).reason)
        assertTrue(networkResult.reason.retriable)

        val unauthorized = FakeTransport { ApiResponse(401, """{"message":"denied"}""") }
        val statusResult = loginRepo(unauthorized).status("job-1")
        assertEquals(TtLoginFailureReason.AUTH, (statusResult as TtLoginStatusResult.Failure).reason)
        assertFalse(statusResult.reason.retriable)
    }

    // ------------------------------------------------------------ 轮询状态机

    private fun snapshot(): TtSnapshot = TtSnapshot(
        c = listOf(Course(name = "体育", day = 4, start = 6, end = 7, weeks = "1-16")),
        t = "2026-08-31",
    )

    @Test
    fun polling_doneStopsImmediately() = runTest {
        var calls = 0
        var sleeps = 0
        var now = 0L
        val outcome = TtLoginPolling.poll(
            jobId = "job",
            fetch = {
                calls += 1
                if (calls < 2) TtLoginStatusResult.Running("claimed") else TtLoginStatusResult.Done(snapshot())
            },
            config = TtLoginPollConfig(),
            now = { now },
            sleep = { now += it; sleeps += 1 },
        )
        assertTrue(outcome is TtLoginPollOutcome.Done)
        assertEquals(2, calls)
        assertEquals(1, sleeps)
        assertEquals(1_500L, now) // 默认 1.5s 间隔
    }

    @Test
    fun polling_timeoutStopsPolling() = runTest {
        var calls = 0
        var sleeps = 0
        var now = 0L
        val outcome = TtLoginPolling.poll(
            jobId = "job",
            fetch = { calls += 1; TtLoginStatusResult.Running("pending") },
            config = TtLoginPollConfig(intervalMs = 1_500L, timeoutMs = 5_000L),
            now = { now },
            sleep = { now += it; sleeps += 1 },
        )
        assertTrue(outcome is TtLoginPollOutcome.Timeout)
        // 0ms/1500/3000/4500 各拉一次；5000ms 到期停止，不再请求
        assertEquals(4, calls)
        assertEquals(4, sleeps)
        assertEquals(5_000L, now)
    }

    @Test
    fun polling_terminalFailureStopsImmediately() = runTest {
        var calls = 0
        var sleeps = 0
        val outcome = TtLoginPolling.poll(
            jobId = "job",
            fetch = {
                calls += 1
                TtLoginStatusResult.Failed(TtLoginFailureReason.PWD)
            },
            now = { 0L },
            sleep = { sleeps += 1 },
        )
        assertTrue(outcome is TtLoginPollOutcome.Failed)
        assertEquals(TtLoginFailureReason.PWD, (outcome as TtLoginPollOutcome.Failed).reason)
        assertEquals(1, calls)
        assertEquals(0, sleeps)
    }

    @Test
    fun polling_networkFailureRetriesThenSucceeds() = runTest {
        var calls = 0
        var now = 0L
        val outcome = TtLoginPolling.poll(
            jobId = "job",
            fetch = {
                calls += 1
                when (calls) {
                    1, 2 -> TtLoginStatusResult.Failure(TtLoginFailureReason.NETWORK)
                    else -> TtLoginStatusResult.Done(snapshot())
                }
            },
            now = { now },
            sleep = { now += it },
        )
        assertTrue(outcome is TtLoginPollOutcome.Done)
        assertEquals(3, calls)
    }

    @Test
    fun polling_nonRetriableFailureStops() = runTest {
        var calls = 0
        val outcome = TtLoginPolling.poll(
            jobId = "job",
            fetch = {
                calls += 1
                TtLoginStatusResult.Failure(TtLoginFailureReason.AUTH)
            },
            now = { 0L },
            sleep = { },
        )
        assertTrue(outcome is TtLoginPollOutcome.Failed)
        assertEquals(1, calls)
    }

    @Test
    fun studentIdRegex_matchesTenDigits() {
        assertTrue(DefaultTimetableLoginRepository.STUDENT_ID_REGEX.matches("3260707024"))
        assertFalse(DefaultTimetableLoginRepository.STUDENT_ID_REGEX.matches("123"))
        assertFalse(DefaultTimetableLoginRepository.STUDENT_ID_REGEX.matches("abcdefghij"))
    }
}
