package com.furina.classtools.data.jw

import com.furina.classtools.data.network.ApiRequest
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.HttpMethod
import com.furina.classtools.data.network.HttpTransport
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.OkHttpTransport
import com.furina.classtools.data.network.asIntOrNull
import com.furina.classtools.data.network.asObjectOrNull
import com.furina.classtools.data.network.asStringOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 教务官方 JSON 接口客户端（规格 `_audit/jw_entry_20261113/JW_CARDS_V1_SPEC.md` §2）。
 *
 * - **只读**：仅 GET 4 个官方查询接口（成绩/考试/选课/公告），**不调用任何写接口**；
 *   选课/报名/缓考等写操作只在官方页面内由用户完成（规格 §0/§3 红线）。
 * - 请求头按实测口径：`Cookie`（WebView 同域 cookie）+ `User-Agent`（WebView 的 UA）+
 *   `Referer`（对应官方页面）+ `X-Requested-With: XMLHttpRequest` + `Accept`。
 * - cookie / UA 只在调用瞬间作为参数传递，本文件不保存、不打印、不落盘。
 * - 纯逻辑（URL/参数/请求头/JSON 解析/失败态判定）与网络传输分离，便于 JVM 单测。
 */
object JwApiSpec {

    /** 教务系统根地址（只有 HTTP，规格 §2）。 */
    const val BASE_URL: String = "http://210.34.74.77"

    /** 网络层失败的通用文案（不暴露内部异常细节）。 */
    const val MSG_FETCH_FAILED: String = "网络请求失败，请稍后重试"

    /** JSON 结构不可识别的文案（官方改版/异常响应）。 */
    const val MSG_MALFORMED: String = "数据解析失败"

    /** 一次官方接口请求的静态描述（纯数据）。 */
    data class JwApiEndpoint(
        val id: String,
        val path: String,
        val refererPath: String,
        val pageSize: Int,
        val extraParams: List<Pair<String, String>> = emptyList(),
    )

    /** 成绩（规格 §2：`/jsxsd/kscj/cjcx_list`，默认 pageSize=5 + 官方筛选项）。 */
    val GRADES: JwApiEndpoint = JwApiEndpoint(
        id = "grades",
        path = "/jsxsd/kscj/cjcx_list",
        refererPath = "/jsxsd/kscj/cjcx_frm",
        pageSize = 5,
        extraParams = listOf(
            "kksj" to "",
            "kcxz" to "",
            "kcsx" to "",
            "kcmc" to "",
            "xsfs" to "all",
            "sfxsbcxq" to "1",
        ),
    )

    /** 考试（`/jsxsd/xsks/xsksap_list`，pageSize=5）。 */
    val EXAMS: JwApiEndpoint = JwApiEndpoint(
        id = "exams",
        path = "/jsxsd/xsks/xsksap_list",
        refererPath = "/jsxsd/xsks/xsksap_query",
        pageSize = 5,
        extraParams = listOf("xnxqid" to "", "xqlb" to ""),
    )

    /** 选课轮次（`/jsxsd/xsxk/xklc_list_data`，pageSize=10）。 */
    val ENROLL: JwApiEndpoint = JwApiEndpoint(
        id = "enroll",
        path = "/jsxsd/xsxk/xklc_list_data",
        refererPath = "/jsxsd/xsxk/xklc_list",
        pageSize = 10,
    )

    /** 公告（`/jsxsd/ggly/ysgg_query?type=list`，pageSize=5）。 */
    val NOTICES: JwApiEndpoint = JwApiEndpoint(
        id = "notices",
        path = "/jsxsd/ggly/ysgg_query?type=list",
        refererPath = "/jsxsd/ggly/ysgg_query",
        pageSize = 5,
    )

    /**
     * 构造官方接口 URL：`BASE + path + ?pageNum=&pageSize=` + 各自额外参数（顺序固定，便于单测/联调）。
     * `path` 已自带 query（公告 `?type=list`）时用 `&` 追加。
     */
    fun urlFor(endpoint: JwApiEndpoint, pageNum: Int = 1): String = buildString {
        append(BASE_URL).append(endpoint.path)
        append(if (endpoint.path.contains('?')) '&' else '?')
        append("pageNum=").append(pageNum)
        append("&pageSize=").append(endpoint.pageSize)
        endpoint.extraParams.forEach { (key, value) ->
            append('&')
            append(encode(key))
            append('=')
            append(encode(value))
        }
    }

    /** 构造请求头（规格 §2 实测必需项）。cookie/UA 只在此参数传递，不保存。 */
    fun headersFor(
        cookie: String,
        userAgent: String,
        endpoint: JwApiEndpoint,
    ): Map<String, String> = LinkedHashMap<String, String>().apply {
        put("Cookie", cookie)
        if (userAgent.isNotBlank()) put("User-Agent", userAgent)
        put("Referer", BASE_URL + endpoint.refererPath)
        put("X-Requested-With", "XMLHttpRequest")
        put("Accept", "application/json, text/javascript, */*")
    }

    /** 对传输层的响应做「是否可解析 / 是否会话失效」判定（纯函数，可单测）。 */
    fun gate(response: ApiResponse): JwHttpGate {
        if (response.status !in 200..299) {
            return JwHttpGate.Failed("HTTP ${response.status}")
        }
        val body = response.body.trim()
        if (body.isEmpty()) return JwHttpGate.Failed(MSG_FETCH_FAILED)
        // 会话失效时官方返回登录页/HTML（规格 §1）：交给调用方触发重登。
        if (looksLikeHtml(body)) return JwHttpGate.Expired
        return JwHttpGate.Ok(body)
    }

    /** 是否为 HTML/登录页（会话失效的特征）。 */
    fun looksLikeHtml(text: String): Boolean {
        val trimmed = text.trimStart()
        if (trimmed.startsWith("<")) return true
        val head = trimmed.take(512).lowercase()
        return head.contains("<html") || head.contains("<!doctype")
    }

    // ---- JSON 解析（宽松；官方字段缺省不崩溃）-------------------------------

    /** 解析通用分页信封，`decodeRow` 负责各卡片行字段映射。 */
    fun <T> parsePage(
        raw: String,
        json: Json = NetJson,
        decodeRow: (JsonObject) -> T,
    ): JwApiResult<T> {
        val text = raw.trim()
        if (text.isEmpty()) return JwApiResult.Malformed
        if (looksLikeHtml(text)) return JwApiResult.SessionExpired
        val root = parseJsonOrNull(json, text).asObjectOrNull() ?: return JwApiResult.Malformed

        // code 可能是 0 / "0"；缺失按 0 处理（官方成功响应可能不返回 code）。
        val code = root["code"].asIntOrNull() ?: 0
        val msg = root["msg"].asStringOrNull()
        if (code != 0) {
            return JwApiResult.ApiError(msg?.takeIf { it.isNotBlank() } ?: "接口返回错误（code=$code）")
        }

        val dataElement = root["data"]
        val rows: List<T> = when {
            dataElement == null || dataElement is JsonNull -> emptyList()
            dataElement is JsonArray -> dataElement.mapNotNull { (it as? JsonObject)?.let(decodeRow) }
            // 部分接口空态返回 data:""（字符串占位）→ 视为空列表。
            dataElement is JsonPrimitive && !dataElement.isString -> return JwApiResult.Malformed
            dataElement is JsonPrimitive && dataElement.content.isBlank() -> emptyList()
            else -> return JwApiResult.Malformed
        }
        val count = root["count"].asIntOrNull() ?: rows.size
        return JwApiResult.Success(JwApiPage(rows, count, msg, code))
    }

    /** 成绩接口解析（行字段见 [JwGradeRow]）。 */
    fun parseGrades(raw: String, json: Json = NetJson): JwApiResult<JwGradeRow> =
        parsePage(raw, json) { obj ->
            JwGradeRow(
                xnxqid = obj.str("xnxqid"),
                kch = obj.str("kch"),
                kcMc = obj.str("kc_mc"),
                ksdw = obj.str("ksdw"),
                zcjstr = obj.str("zcjstr"),
                cjbs = obj.str("cjbs"),
                xf = obj.str("xf"),
                zxs = obj.str("zxs"),
                jd = obj.str("jd"),
                bcxq = obj.str("bcxq"),
            )
        }

    /** 考试接口解析（行字段见 [JwExamRow]）。 */
    fun parseExams(raw: String, json: Json = NetJson): JwApiResult<JwExamRow> =
        parsePage(raw, json) { obj ->
            JwExamRow(
                xqmc = obj.str("xqmc"),
                ksxq = obj.str("ksxq"),
                kssj = obj.str("kssj"),
                jsMc = obj.str("js_mc"),
                kch = obj.str("kch"),
                kskcmc = obj.str("kskcmc"),
                jsxm = obj.str("jsxm"),
                zwh = obj.str("zwh"),
                zkzh = obj.str("zkzh"),
                ksccmc = obj.str("ksccmc"),
                bzywmc = obj.str("bzywmc"),
            )
        }

    /** 选课接口解析（行字段见 [JwEnrollRow]）。 */
    fun parseEnrollRounds(raw: String, json: Json = NetJson): JwApiResult<JwEnrollRow> =
        parsePage(raw, json) { obj ->
            JwEnrollRow(
                xqmc = obj.str("xqmc"),
                xklcMc = obj.str("xklc_mc"),
                xksj = obj.str("xksj"),
            )
        }

    /** 公告接口解析（行字段见 [JwNoticeRow]）。 */
    fun parseNotices(raw: String, json: Json = NetJson): JwApiResult<JwNoticeRow> =
        parsePage(raw, json) { obj ->
            JwNoticeRow(
                title = obj.str("title"),
                gglbname = obj.str("gglbname"),
                cjr = obj.str("cjr"),
            )
        }

    // ---- 内部工具 -----------------------------------------------------------

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun JsonObject.str(key: String): String =
        (get(key) as? JsonPrimitive)?.content ?: ""
}

/** 传输层响应闸门：可解析 / 失败 / 会话失效（纯数据，可单测）。 */
sealed class JwHttpGate {
    data class Ok(val body: String) : JwHttpGate()
    data class Failed(val message: String) : JwHttpGate()
    data object Expired : JwHttpGate()
}

/**
 * 官方接口客户端：构造请求 → [HttpTransport] → 响应闸门 → JSON 解析。
 *
 * 生产使用默认 [OkHttpTransport]（10s 超时、禁用自动重试）；单测注入 `HttpTransport { ... }` 假实现，
 * 不触真实网络、不需要真实账号。
 */
class JwApiClient(
    private val transport: HttpTransport = OkHttpTransport(),
    private val json: Json = NetJson,
) {

    /** 拉成绩（pageSize=5）。 */
    suspend fun fetchGrades(cookie: String, userAgent: String): JwApiResult<JwGradeRow> =
        fetch(JwApiSpec.GRADES, cookie, userAgent) { body -> JwApiSpec.parseGrades(body, json) }

    /** 拉考试安排（pageSize=5）。 */
    suspend fun fetchExams(cookie: String, userAgent: String): JwApiResult<JwExamRow> =
        fetch(JwApiSpec.EXAMS, cookie, userAgent) { body -> JwApiSpec.parseExams(body, json) }

    /** 拉选课轮次（pageSize=10）。 */
    suspend fun fetchEnrollRounds(cookie: String, userAgent: String): JwApiResult<JwEnrollRow> =
        fetch(JwApiSpec.ENROLL, cookie, userAgent) { body -> JwApiSpec.parseEnrollRounds(body, json) }

    /** 拉已收公告（pageSize=5）。 */
    suspend fun fetchNotices(cookie: String, userAgent: String): JwApiResult<JwNoticeRow> =
        fetch(JwApiSpec.NOTICES, cookie, userAgent) { body -> JwApiSpec.parseNotices(body, json) }

    private suspend fun <T> fetch(
        endpoint: JwApiSpec.JwApiEndpoint,
        cookie: String,
        userAgent: String,
        parser: (String) -> JwApiResult<T>,
    ): JwApiResult<T> {
        if (cookie.isBlank()) return JwApiResult.SessionExpired
        val request = ApiRequest(
            method = HttpMethod.GET,
            url = JwApiSpec.urlFor(endpoint),
            headers = JwApiSpec.headersFor(cookie, userAgent, endpoint),
        )
        val response: ApiResponse = runCatching { transport.execute(request) }
            .getOrElse { return JwApiResult.ApiError(JwApiSpec.MSG_FETCH_FAILED) }
        return when (val gate = JwApiSpec.gate(response)) {
            is JwHttpGate.Ok -> parser(gate.body)
            is JwHttpGate.Failed -> JwApiResult.ApiError(gate.message)
            JwHttpGate.Expired -> JwApiResult.SessionExpired
        }
    }
}

