package com.furina.classtools.features.jw

import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 网络连通性探测抽象（单测可注入假实现，避免真实网络）。 */
fun interface JwConnectivityProbe {
    suspend fun canConnect(host: String, port: Int, timeoutMs: Long): Boolean
}

/** TCP 短超时连通性探测：只建连（不发 HTTP），用于判断是否在校园网内可直连教务。 */
class SocketJwConnectivityProbe : JwConnectivityProbe {

    override suspend fun canConnect(host: String, port: Int, timeoutMs: Long): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs.toInt())
                }
                true
            }.getOrDefault(false)
        }
}

/**
 * 教务入口网络探测：能否直连 `210.34.74.77:80`。
 *
 * 不可直连（校外）时由页面自动改用学校 WebVPN（[JwWebVpn.LOGIN_URL]）。
 * 探测失败（异常/超时）一律按「不可直连」处理，宁可用 WebVPN 也不要卡在空白页。
 */
object JwNetworkDetector {

    suspend fun directReachable(
        probe: JwConnectivityProbe = SocketJwConnectivityProbe(),
    ): Boolean = runCatching {
        probe.canConnect(
            host = JwLoginLogic.JW_HOST,
            port = JwWebVpn.JW_PROBE_PORT,
            timeoutMs = JwWebVpn.PROBE_TIMEOUT_MS,
        )
    }.getOrDefault(false)
}
