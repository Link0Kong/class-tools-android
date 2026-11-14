package com.furina.classtools.data.jw

import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.HttpTransport
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 卡片数据仓库单测（规格 §1/§2/§5）。
 *
 * 覆盖：4 个官方接口并行拉取、单卡失败隔离、会话失效识别、cookie 缺失短路、空数据正常态、解析失败隔离。
 * 使用假 [HttpTransport] + 合成 JSON 夹具，**不触真实网络、不使用真实账号/真实个人数据**。
 */
class JwCardsRepositoryTest {

    private val cookie = "JSESSIONID=TESTCOOKIE; route=test"
    private val userAgent = "TestWebView/1.0"

    private fun repository(
        transport: HttpTransport,
        requests: MutableList<String>? = null,
    ): JwCardsRepository = JwCardsRepository(
        JwApiClient(
            HttpTransport { request ->
                requests?.add(request.url)
                transport.execute(request)
            },
        ),
    )

    private fun successJsonFor(url: String): String = when {
        url.contains("/jsxsd/kscj/cjcx_list") ->
            """{"code":0,"count":1,"data":[{"kc_mc":"高等数学","zcjstr":"92","xnxqid":"2026-2027-1","jd":"4.2"}]}"""

        url.contains("/jsxsd/xsks/xsksap_list") ->
            """{"code":0,"count":1,"data":[{"kssj":"2026-01-10 09:00","kskcmc":"高等数学","js_mc":"1-101","zwh":"12"}]}"""

        url.contains("/jsxsd/xsxk/xklc_list_data") ->
            """{"code":0,"count":1,"data":[{"xqmc":"2026-2027-1","xklc_mc":"第一轮选课","xksj":"2026-09-01"}]}"""

        url.contains("/jsxsd/ggly/ysgg_query") ->
            """{"code":0,"count":1,"data":[{"title":"通知一","gglbname":"教务"}]}"""

        else -> error("未知接口 URL：$url")
    }

    private fun okTransport(): HttpTransport = HttpTransport { request ->
        ApiResponse(200, successJsonFor(request.url))
    }

    // ---- 正常链路 -----------------------------------------------------------

    @Test
    fun `并行拉取 4 个接口且全部成功`() = runTest {
        val requests = ArrayList<String>()
        val snapshot = repository(okTransport(), requests).loadAll(cookie, userAgent)

        assertEquals(4, requests.size)
        assertTrue(requests.any { it.contains("/jsxsd/kscj/cjcx_list") })
        assertTrue(requests.any { it.contains("/jsxsd/xsks/xsksap_list") })
        assertTrue(requests.any { it.contains("/jsxsd/xsxk/xklc_list_data") })
        assertTrue(requests.any { it.contains("/jsxsd/ggly/ysgg_query") })

        assertFalse(snapshot.sessionExpired)
        assertTrue(snapshot.grades is JwApiResult.Success<*>)
        assertTrue(snapshot.exams is JwApiResult.Success<*>)
        assertTrue(snapshot.enrollRounds is JwApiResult.Success<*>)
        assertTrue(snapshot.notices is JwApiResult.Success<*>)
        assertEquals("高等数学", pageOf(snapshot.grades).rows.first().kcMc)
        assertEquals("通知一", pageOf(snapshot.notices).rows.first().title)
    }

    @Test
    fun `4 个请求确实并行发出`() = runTest {
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val transport = HttpTransport { request ->
            val now = active.incrementAndGet()
            maxActive.updateAndGet { previous -> maxOf(previous, now) }
            delay(10) // 挂起让其它协程进入（虚拟时间）
            active.decrementAndGet()
            ApiResponse(200, successJsonFor(request.url))
        }
        val snapshot = repository(transport).loadAll(cookie, userAgent)
        assertTrue("期望并行（并发峰值 ${maxActive.get()}）", maxActive.get() >= 2)
        assertFalse(snapshot.sessionExpired)
    }

    @Test
    fun `空数据是正常空态`() = runTest {
        val emptyTransport = HttpTransport { ApiResponse(200, """{"msg":"","code":0,"data":[],"count":0}""") }
        val snapshot = repository(emptyTransport).loadAll(cookie, userAgent)
        assertFalse(snapshot.sessionExpired)
        val grades = pageOf(snapshot.grades)
        assertEquals(0, grades.count)
        assertTrue(grades.rows.isEmpty())
        assertTrue(pageOf(snapshot.notices).rows.isEmpty())
    }

    // ---- 单卡失败隔离 -------------------------------------------------------

    @Test
    fun `单卡 HTTP 失败只影响该卡`() = runTest {
        val transport = HttpTransport { request ->
            if (request.url.contains("/jsxsd/kscj/cjcx_list")) {
                ApiResponse(500, "boom")
            } else {
                ApiResponse(200, successJsonFor(request.url))
            }
        }
        val snapshot = repository(transport).loadAll(cookie, userAgent)
        assertTrue(snapshot.grades is JwApiResult.ApiError)
        assertTrue(snapshot.exams is JwApiResult.Success<*>)
        assertTrue(snapshot.enrollRounds is JwApiResult.Success<*>)
        assertTrue(snapshot.notices is JwApiResult.Success<*>)
        assertFalse(snapshot.sessionExpired)
    }

    @Test
    fun `单卡解析失败只影响该卡`() = runTest {
        val transport = HttpTransport { request ->
            if (request.url.contains("/jsxsd/xsxk/xklc_list_data")) {
                ApiResponse(200, "not-a-json")
            } else {
                ApiResponse(200, successJsonFor(request.url))
            }
        }
        val snapshot = repository(transport).loadAll(cookie, userAgent)
        selfCheck(JwApiResult.Malformed, snapshot.enrollRounds)
        assertTrue(snapshot.grades is JwApiResult.Success<*>)
        assertTrue(snapshot.notices is JwApiResult.Success<*>)
    }

    @Test
    fun `单卡网络异常只影响该卡`() = runTest {
        val transport = HttpTransport { request ->
            if (request.url.contains("/jsxsd/ggly/ysgg_query")) {
                throw java.io.IOException("断开")
            } else {
                ApiResponse(200, successJsonFor(request.url))
            }
        }
        val snapshot = repository(transport).loadAll(cookie, userAgent)
        assertTrue(snapshot.notices is JwApiResult.ApiError)
        assertTrue(snapshot.grades is JwApiResult.Success<*>)
        assertFalse(snapshot.sessionExpired)
    }

    // ---- 会话失效 -----------------------------------------------------------

    @Test
    fun `任一接口返回 HTML 即标记会话失效`() = runTest {
        val transport = HttpTransport { request ->
            if (request.url.contains("/jsxsd/xsks/xsksap_list")) {
                ApiResponse(200, "<html><body>统一身份认证登录</body></html>")
            } else {
                ApiResponse(200, successJsonFor(request.url))
            }
        }
        val snapshot = repository(transport).loadAll(cookie, userAgent)
        selfCheck(JwApiResult.SessionExpired, snapshot.exams)
        assertTrue(snapshot.sessionExpired)
        // 其它卡仍返回数据（页面层可以选择是否展示；仓库不吞掉）
        assertTrue(snapshot.grades is JwApiResult.Success<*>)
    }

    @Test
    fun `cookie 缺失直接判会话失效且不发请求`() = runTest {
        val requests = ArrayList<String>()
        val repo = repository(okTransport(), requests)
        selfCheck(JwApiResult.SessionExpired, repo.loadAll(null, userAgent).grades)
        selfCheck(JwApiResult.SessionExpired, repo.loadAll("", userAgent).grades)
        selfCheck(JwApiResult.SessionExpired, repo.loadAll("   ", userAgent).grades)
        assertEquals(0, requests.size)
        assertTrue(repo.loadAll(null, userAgent).sessionExpired)
    }

    // ---- 工具 ---------------------------------------------------------------
    private fun <T> pageOf(result: JwApiResult<T>): JwApiPage<T> =
        (result as JwApiResult.Success<T>).page

    private fun selfCheck(expected: JwApiResult<*>, actual: JwApiResult<*>) {
        assertEquals(
            "期望 ${expected::class.simpleName}，实际 ${actual::class.simpleName}",
            expected,
            actual,
        )
    }
}


