package com.furina.classtools.data.jw

import com.furina.classtools.data.network.ApiRequest
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.HttpMethod
import com.furina.classtools.data.network.HttpTransport
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 教务官方接口客户端单测（规格 `_audit/jw_entry_20261113/JW_CARDS_V1_SPEC.md` §2）。
 *
 * 覆盖：URL/参数构造、请求头（Cookie/UA/Referer/X-Requested-With/Accept）、
 * HTTP 闸门（非 2xx/HTML=会话失效/空响应）、4 类 JSON 解析 + 宽松容错 + 失败态、
 * [JwApiClient] 与传输层的接线（用假 [HttpTransport]，**不触真实网络、不用真实账号**）。
 *
 * 全部 JSON 为**合成夹造数据**，不含任何真实个人数据。
 */
class JwApiClientTest {

    private val cookie = "JSESSIONID=TESTCOOKIE123; route=test"
    private val userAgent = "Mozilla/5.0 (Linux; Android 17) TestWebView/1.0"

    // ---- URL / 参数 ---------------------------------------------------------

    @Test
    fun `默认 URL 与实测参数逐字一致`() {
        assertEquals(
            "http://210.34.74.77/jsxsd/kscj/cjcx_list" +
                "?pageNum=1&pageSize=5&kksj=&kcxz=&kcsx=&kcmc=&xsfs=all&sfxsbcxq=1",
            JwApiSpec.urlFor(JwApiSpec.GRADES),
        )
        assertEquals(
            "http://210.34.74.77/jsxsd/xsks/xsksap_list?pageNum=1&pageSize=5&xnxqid=&xqlb=",
            JwApiSpec.urlFor(JwApiSpec.EXAMS),
        )
        assertEquals(
            "http://210.34.74.77/jsxsd/xsxk/xklc_list_data?pageNum=1&pageSize=10",
            JwApiSpec.urlFor(JwApiSpec.ENROLL),
        )
        assertEquals(
            "http://210.34.74.77/jsxsd/ggly/ysgg_query?type=list&pageNum=1&pageSize=5",
            JwApiSpec.urlFor(JwApiSpec.NOTICES),
        )
    }

    @Test
    fun `pageNum 可指定且只出现在分页参数里`() {
        val url = JwApiSpec.urlFor(JwApiSpec.NOTICES, pageNum = 2)
        assertTrue(url.contains("pageNum=2"))
        assertTrue(url.contains("pageSize=5"))
        // 公告 path 已有 ?type=list，必须用 & 追加
        assertTrue(url.contains("?type=list&pageNum=2"))
    }

    // ---- 请求头 -------------------------------------------------------------

    @Test
    fun `请求头包含 Cookie UA Referer X-Requested-With Accept`() {
        val headers = JwApiSpec.headersFor(cookie, userAgent, JwApiSpec.GRADES)
        assertEquals(cookie, headers["Cookie"])
        assertEquals(userAgent, headers["User-Agent"])
        assertEquals(
            "http://210.34.74.77/jsxsd/kscj/cjcx_frm",
            headers["Referer"],
        )
        assertEquals("XMLHttpRequest", headers["X-Requested-With"])
        assertTrue(headers["Accept"]!!.contains("application/json"))
        assertEquals(5, headers.size)
    }

    @Test
    fun `各接口 Referer 指向对应官方页面`() {
        assertEquals(
            "http://210.34.74.77/jsxsd/xsks/xsksap_query",
            JwApiSpec.headersFor(cookie, userAgent, JwApiSpec.EXAMS)["Referer"],
        )
        assertEquals(
            "http://210.34.74.77/jsxsd/xsxk/xklc_list",
            JwApiSpec.headersFor(cookie, userAgent, JwApiSpec.ENROLL)["Referer"],
        )
        assertEquals(
            "http://210.34.74.77/jsxsd/ggly/ysgg_query",
            JwApiSpec.headersFor(cookie, userAgent, JwApiSpec.NOTICES)["Referer"],
        )
    }

    @Test
    fun `UA 为空时不发送 User-Agent 头`() {
        val headers = JwApiSpec.headersFor(cookie, "  ", JwApiSpec.GRADES)
        assertNull(headers["User-Agent"])
        assertEquals(4, headers.size)
    }

    // ---- HTTP 闸门 ----------------------------------------------------------

    @Test
    fun `HTTP 闸门区分成功 失败 会话失效`() {
        assertTrue(JwApiSpec.gate(ApiResponse(200, "{\"code\":0}")) is JwHttpGate.Ok)
        assertTrue(JwApiSpec.gate(ApiResponse(200, "  {\"code\":0}  ")) is JwHttpGate.Ok)

        val http500 = JwApiSpec.gate(ApiResponse(500, "{\"code\":0}"))
        assertTrue(http500 is JwHttpGate.Failed)
        assertEquals("HTTP 500", (http500 as JwHttpGate.Failed).message)

        assertEquals(JwHttpGate.Expired, JwApiSpec.gate(ApiResponse(200, "<!DOCTYPE html><html>login</html>")))
        assertEquals(JwHttpGate.Expired, JwApiSpec.gate(ApiResponse(200, "  <html><body>统一身份认证</body></html>")))
        assertEquals(JwHttpGate.Expired, JwApiSpec.gate(ApiResponse(200, "<script>window.location='login'</script>")))

        val blank = JwApiSpec.gate(ApiResponse(200, "   "))
        assertTrue(blank is JwHttpGate.Failed)
        assertEquals(JwApiSpec.MSG_FETCH_FAILED, (blank as JwHttpGate.Failed).message)
    }

    // ---- JSON 解析：成绩 ----------------------------------------------------

    @Test
    fun `成绩 JSON 解析官方字段并映射`() {
        val raw = """
            {"msg":"操作成功","code":0,"count":1,"data":[
              {"xnxqid":"2026-2027-1","kch":"A001","kc_mc":"高等数学","ksdw":"数学学院",
               "zcjstr":"92","cjbs":"","xf":"4","zxs":"64","jd":"4.2","bcxq":"",
               "unknownField":"ignored"}
            ]}
        """.trimIndent()
        val result = JwApiSpec.parseGrades(raw)
        assertTrue(result is JwApiResult.Success<*>)
        val page = successPage(result)
        assertEquals(1, page.count)
        assertEquals(1, page.rows.size)
        val row = page.rows.first()
        assertEquals("2026-2027-1", row.xnxqid)
        assertEquals("A001", row.kch)
        assertEquals("高等数学", row.kcMc)
        assertEquals("数学学院", row.ksdw)
        assertEquals("92", row.zcjstr)
        assertEquals("4", row.xf)
        assertEquals("64", row.zxs)
        assertEquals("4.2", row.jd)
    }

    @Test
    fun `code 兼容字符串 0 与缺省`() {
        assertTrue(JwApiSpec.parseGrades("{\"msg\":\"ok\",\"code\":\"0\",\"data\":[],\"count\":0}") is JwApiResult.Success<*>)
        assertTrue(JwApiSpec.parseGrades("{\"data\":[],\"count\":0}") is JwApiResult.Success<*>)
    }

    @Test
    fun `code 非 0 返回官方 msg 的业务错误`() {
        val result = JwApiSpec.parseGrades("{\"msg\":\"会话已失效\",\"code\":401,\"data\":null,\"count\":0}")
        assertTrue(result is JwApiResult.ApiError)
        assertEquals("会话已失效", (result as JwApiResult.ApiError).message)
        // msg 为空时给出兜底文案（含 code）
        val emptyMsg = JwApiSpec.parseGrades("{\"code\":500}")
        assertTrue(emptyMsg is JwApiResult.ApiError)
        assertTrue((emptyMsg as JwApiResult.ApiError).message.contains("500"))
    }

    @Test
    fun `空数据是正常空态不是失败`() {
        val result = JwApiSpec.parseGrades("{\"msg\":\"\",\"code\":0,\"data\":[],\"count\":0}")
        assertTrue(result is JwApiResult.Success<*>)
        val page = successPage(result)
        assertEquals(0, page.count)
        assertTrue(page.rows.isEmpty())
        // 部分接口空态返回 data:""
        val stringEmpty = JwApiSpec.parseGrades("{\"code\":0,\"data\":\"\",\"count\":0}")
        assertTrue(stringEmpty is JwApiResult.Success<*>)
        assertTrue(successPage(stringEmpty).rows.isEmpty())
        // count 缺省时回落到 rows.size
        val noCount = JwApiSpec.parseNotices("{\"code\":0,\"data\":[{\"title\":\"a\"}]}")
        assertEquals(1, successPage(noCount).count)
    }

    @Test
    fun `行字段缺省不崩溃且为空串`() {
        val result = JwApiSpec.parseExams("{\"code\":0,\"data\":[{}],\"count\":1}")
        val row = successPage(result).rows.first()
        assertEquals("", row.kssj)
        assertEquals("", row.kskcmc)
        assertEquals("", row.zwh)
        assertEquals("", row.zkzh)
    }

    @Test
    fun `非法 JSON 与 HTML 分别判为解析失败与会话失效`() {
        selfCheck(JwApiResult.Malformed, JwApiSpec.parseGrades("not-a-json"))
        selfCheck(JwApiResult.Malformed, JwApiSpec.parseGrades(""))
        selfCheck(JwApiResult.Malformed, JwApiSpec.parseGrades("[]"))
        selfCheck(JwApiResult.Malformed, JwApiSpec.parseGrades("{\"code\":0,\"data\":{\"x\":1}}"))
        selfCheck(JwApiResult.SessionExpired, JwApiSpec.parseGrades("<html><body>登录</body></html>"))
    }

    // ---- JSON 解析：考试 / 选课 / 公告 --------------------------------------

    @Test
    fun `考试 JSON 解析`() {
        val raw = """
            {"code":0,"count":2,"data":[
              {"xqmc":"旗山校区","ksxq":"旗山","kssj":"2026-01-10 09:00-11:00","js_mc":"1-101",
               "kch":"A001","kskcmc":"高等数学","jsxm":"张老师","zwh":"12","zkzh":"ZK001",
               "ksccmc":"上午","bzywmc":""},
              {"kssj":"2026-01-12 14:00-16:00","kskcmc":"大学英语"}
            ]}
        """.trimIndent()
        val page = successPage(JwApiSpec.parseExams(raw))
        assertEquals(2, page.count)
        assertEquals(2, page.rows.size)
        assertEquals("旗山校区", page.rows[0].xqmc)
        assertEquals("2026-01-10 09:00-11:00", page.rows[0].kssj)
        assertEquals("1-101", page.rows[0].jsMc)
        assertEquals("12", page.rows[0].zwh)
        assertEquals("上午", page.rows[0].ksccmc)
    }

    @Test
    fun `选课轮次 JSON 解析`() {
        val raw = """
            {"code":"0","count":1,"data":[
              {"xqmc":"2026-2027-1","xklc_mc":"第一轮选课","xksj":"2026-09-01 08:00 ~ 2026-09-03 22:00"}
            ]}
        """.trimIndent()
        val page = successPage(JwApiSpec.parseEnrollRounds(raw))
        val row = page.rows.first()
        assertEquals("2026-2027-1", row.xqmc)
        assertEquals("第一轮选课", row.xklcMc)
        assertEquals("2026-09-01 08:00 ~ 2026-09-03 22:00", row.xksj)
    }

    @Test
    fun `公告 JSON 解析`() {
        val raw = """
            {"code":0,"count":1,"data":[
              {"title":"关于开展选课的通知","gglbname":"教务通知","cjr":"教务处"}
            ]}
        """.trimIndent()
        val page = successPage(JwApiSpec.parseNotices(raw))
        val row = page.rows.first()
        assertEquals("关于开展选课的通知", row.title)
        assertEquals("教务通知", row.gglbname)
        assertEquals("教务处", row.cjr)
    }

    @Test
    fun `非法行对象跳过但整体不失败`() {
        val raw = """{"code":0,"count":2,"data":[{"title":"有效"},null,123]}"""
        val page = successPage(JwApiSpec.parseNotices(raw))
        assertEquals(1, page.rows.size)
        assertEquals("有效", page.rows.first().title)
    }

    // ---- JwApiClient 与传输层接线 -----------------------------------------

    @Test
    fun `客户端发送 GET 且带齐请求头`() = runTest {
        val requests = ArrayList<ApiRequest>()
        val transport = HttpTransport { request ->
            requests += request
            ApiResponse(200, "{\"code\":0,\"data\":[],\"count\":0}")
        }
        val client = JwApiClient(transport)
        val result = client.fetchGrades(cookie, userAgent)
        assertTrue(result is JwApiResult.Success<*>)

        assertEquals(1, requests.size)
        val request = requests.first()
        assertEquals(HttpMethod.GET, request.method)
        assertEquals(JwApiSpec.urlFor(JwApiSpec.GRADES), request.url)
        assertEquals(cookie, request.headers["Cookie"])
        assertEquals(userAgent, request.headers["User-Agent"])
        assertEquals("http://210.34.74.77/jsxsd/kscj/cjcx_frm", request.headers["Referer"])
        assertEquals("XMLHttpRequest", request.headers["X-Requested-With"])
    }

    @Test
    fun `传输异常转成网络失败文案`() = runTest {
        val client = JwApiClient(HttpTransport { throw IOException("boom") })
        val result = client.fetchNotices(cookie, userAgent)
        assertTrue(result is JwApiResult.ApiError)
        assertEquals(JwApiSpec.MSG_FETCH_FAILED, (result as JwApiResult.ApiError).message)
    }

    @Test
    fun `返回 HTML 判会话失效`() = runTest {
        val client = JwApiClient(HttpTransport { ApiResponse(200, "<html>去登录</html>") })
        selfCheck(JwApiResult.SessionExpired, client.fetchExams(cookie, userAgent))
    }

    @Test
    fun `cookie 为空直接判会话失效且不发请求`() = runTest {
        var called = 0
        val client = JwApiClient(HttpTransport { called += 1; ApiResponse(200, "{\"code\":0}") })
        selfCheck(JwApiResult.SessionExpired, client.fetchGrades("", userAgent))
        selfCheck(JwApiResult.SessionExpired, client.fetchGrades("   ", userAgent))
        assertEquals(0, called)
    }

    // ---- 工具 ---------------------------------------------------------------
    private fun <T> successPage(result: JwApiResult<T>): JwApiPage<T> =
        (result as JwApiResult.Success<T>).page

    private fun selfCheck(expected: JwApiResult<*>, actual: JwApiResult<*>) {
        assertEquals(
            "期望 ${expected::class.simpleName}，实际 ${actual::class.simpleName}",
            expected,
            actual,
        )
    }
}


