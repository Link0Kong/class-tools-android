package com.furina.classtools.data.update

import com.furina.classtools.data.network.ApiRequest
import com.furina.classtools.data.network.HttpMethod
import com.furina.classtools.data.network.HttpTransport
import com.furina.classtools.data.network.NetworkClients
import com.furina.classtools.data.network.OkHttpTransport
import java.time.Duration
import okhttp3.OkHttpClient

/**
 * 远端 latest.json 检查器。
 *
 * 契约：
 * - 端点按 [UpdateEndpoints] 顺序 fallback：主 IP 入口失败（网络异常/非 2xx/坏 JSON）→ OSS 兜底；
 * - 单次请求 10s 超时（[UpdateHttpTransport.defaultClient]）；错误不抛出，转 [UpdateCheckResult.Failed]
 *   （调用方静默处理，离线不打扰）；
 * - 判定「有新版」只看 `versionCode > 当前 versionCode`（见 [UpdateVersion.isNewer]）。
 *
 * 纯网络逻辑依赖 [HttpTransport] 接口 → JVM 单测用 FakeTransport 覆盖 fallback 链路。
 */
class UpdateChecker(
    private val currentVersionCode: Int,
    private val currentVersionName: String,
    private val transport: HttpTransport,
    private val endpoints: List<String> = UpdateEndpoints.ALL,
) {

    /** 依次尝试端点；首个「2xx + 合法 JSON」即返回结果（无新版也返回 UpToDate，不再试后续端点）。 */
    suspend fun check(): UpdateCheckResult {
        if (endpoints.isEmpty()) return UpdateCheckResult.Failed("未配置更新地址")
        val errors = ArrayList<String>(endpoints.size)
        for (url in endpoints) {
            val response = runCatching {
                transport.execute(
                    ApiRequest(
                        method = HttpMethod.GET,
                        url = url,
                        headers = REQUEST_HEADERS,
                    ),
                )
            }.getOrElse { error ->
                errors.add(describe(error))
                continue
            }
            if (response.status !in 200..299) {
                errors.add("HTTP ${response.status}")
                continue
            }
            val info = UpdateJsonParser.parse(response.body)
            if (info == null) {
                errors.add("数据格式错误")
                continue
            }
            return if (UpdateVersion.isNewer(info, currentVersionCode)) {
                UpdateCheckResult.Available(info)
            } else {
                UpdateCheckResult.UpToDate(
                    currentVersionName = currentVersionName,
                    currentVersionCode = currentVersionCode,
                )
            }
        }
        return UpdateCheckResult.Failed(errors.lastOrNull() ?: "检查更新失败")
    }

    companion object {
        /** 公开静态 JSON：禁缓存，避免 CDN/代理返回旧版本。 */
        val REQUEST_HEADERS: Map<String, String> = mapOf(
            "Accept" to "application/json",
            "Cache-Control" to "no-cache",
        )

        private fun describe(error: Throwable): String = when (error) {
            is java.net.SocketTimeoutException -> "连接超时"
            is java.net.UnknownHostException -> "无法解析域名"
            is java.io.IOException -> "网络不可用"
            else -> "网络异常"
        }
    }
}

/** 更新检查用 OkHttp 传输（10s 超时、不自动重试；失败由端点链兜底）。 */
object UpdateHttpTransport {

    fun create(client: OkHttpClient = defaultClient()): HttpTransport = OkHttpTransport(client)

    /** 契约：连接/读/call 均 10s（updates 检查是轻量 JSON，超时宁可静默失败）。
     *  2026-11-18：共享全局连接池/Dispatcher（同一主机连接跨模块复用）。 */
    fun defaultClient(): OkHttpClient = NetworkClients.builder()
        .connectTimeout(Duration.ofSeconds(10))
        .readTimeout(Duration.ofSeconds(10))
        .writeTimeout(Duration.ofSeconds(10))
        .callTimeout(Duration.ofSeconds(10))
        .retryOnConnectionFailure(false)
        .build()
}
