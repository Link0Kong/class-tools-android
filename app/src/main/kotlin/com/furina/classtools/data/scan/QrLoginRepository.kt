package com.furina.classtools.data.scan

import com.furina.classtools.data.network.ApiRequest
import com.furina.classtools.data.network.HttpMethod
import com.furina.classtools.data.network.HttpTransport
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.asBooleanOrNull
import com.furina.classtools.data.network.asStringOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 扫码登录结果（UI 只展示 message / 成功后的姓名）。 */
data class QrLoginResult(
    val ok: Boolean,
    val name: String = "",
    val code: String = "",
    val message: String = "",
)

/**
 * 安卓 App「扫一扫 → 电脑网页扫码登录」：把扫码得到的票据交给 ECS `/account/qr/authorize`。
 *
 * 契约：`web/docs/QR_LOGIN_CONTRACT.md` §4.3；
 * - header `X-Class-Token` = 当前班级 token；
 * - body `{t, p:"any|harmony", via:"app"}`；
 * - 服务端校验身份后给**发起端（电脑）**另签 30 天 token，App 不接收任何 token。
 *
 * 不抛异常：网络/超时/非 2xx/坏 JSON 都映射为 [QrLoginResult]。
 */
class QrLoginRepository(
    private val transport: HttpTransport,
    baseUrl: String,
    private val requestTimeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val json: Json = NetJson,
) {
    val baseUrl: String = baseUrl.trim().trimEnd('/').ifBlank { DEFAULT_BASE_URL }

    suspend fun authorize(ticket: String, harmony: Boolean, classToken: String): QrLoginResult {
        val t = ticket.trim().lowercase()
        if (!TICKET_RE.matches(t)) return QrLoginResult(false, code = "bad_ticket", message = "二维码票据无效，请重新扫描")
        val token = classToken.trim()
        if (token.isEmpty()) return QrLoginResult(false, code = "unauthorized", message = "登录已失效，请重新验证")

        val body = buildJsonObject {
            put("t", t)
            put("p", if (harmony) "harmony" else "any")
            put("via", "app")
        }.toString()
        val request = ApiRequest(
            method = HttpMethod.POST,
            url = "$baseUrl/qr/authorize",
            headers = mapOf(
                "Accept" to "application/json",
                "Content-Type" to "application/json; charset=utf-8",
                "X-Class-Token" to token,
            ),
            body = body,
        )
        val response = try {
            withTimeout(requestTimeoutMs) { transport.execute(request) }
        } catch (timeout: TimeoutCancellationException) {
            return QrLoginResult(false, code = "timeout", message = "网络超时，请稍后重试")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return QrLoginResult(false, code = "network", message = "网络不可用，请稍后重试")
        }
        return parseResponse(response.status, response.body)
    }

    internal fun parseResponse(status: Int, raw: String): QrLoginResult {
        if (status == 401 || status == 403) {
            return QrLoginResult(false, code = "unauthorized", message = "登录已失效，请重新验证")
        }
        if (status == 429) {
            return QrLoginResult(false, code = "rate_limited", message = "请求过于频繁，请稍后再试")
        }
        val obj = parseJsonOrNull(json, raw) as? JsonObject
        if (status !in 200..299) {
            val serverMessage = obj?.get("message").asStringOrNull().orEmpty()
            return QrLoginResult(
                ok = false,
                code = obj?.get("code").asStringOrNull().orEmpty().ifBlank { "http_$status" },
                message = serverMessage.ifBlank {
                    if (status in 500..599) "扫码服务暂时不可用，请稍后重试" else "请求失败（HTTP $status）"
                },
            )
        }
        if (obj == null) return QrLoginResult(false, code = "parse", message = "扫码服务返回了无法解析的内容")
        if (obj["ok"].asBooleanOrNull() != true) {
            return QrLoginResult(
                ok = false,
                code = obj["code"].asStringOrNull().orEmpty().ifBlank { "qr_authorize_failed" },
                message = obj["message"].asStringOrNull().orEmpty().ifBlank { "确认失败，请重试" },
            )
        }
        return QrLoginResult(ok = true, name = obj["name"].asStringOrNull().orEmpty())
    }

    companion object {
        const val DEFAULT_BASE_URL: String = "https://tool.furina.fun/account"
        const val DEFAULT_TIMEOUT_MS: Long = 15_000L
        private val TICKET_RE = Regex("^[0-9a-f]{32}$")
    }
}
