package com.furina.classtools.data.network

import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 2026-11-18 连接复用量化测试（同机本地 HTTP 服务，真实 TCP 连接计数，不发外网请求）。
 *
 * 指标：
 * - 优化后（共享连接池）：REST/附件/下载 3 个模块 client 顺序请求同一 host → 只建 1 条 TCP 连接；
 * - 优化前基线（每个模块独立 OkHttpClient）：同样 3 次请求 → 3 条 TCP 连接。
 * TLS/HTTP2 多路复用同理（备案后 https 域名为 HTTP/2；本测试用明文 HTTP/1.1 keep-alive 验证连接复用本身）。
 */
class NetworkClientsConnectionTest {

    private lateinit var server: ServerSocket
    private val connections = AtomicInteger(0)
    private var acceptor: Thread? = null

    @Before
    fun setUp() {
        server = ServerSocket(0)
        acceptor = thread(isDaemon = true, name = "net-test-http") {
            while (!server.isClosed) {
                val socket: Socket = try {
                    server.accept()
                } catch (_: Exception) {
                    break
                }
                connections.incrementAndGet()
                thread(isDaemon = true) { serve(socket) }
            }
        }
    }

    @After
    fun tearDown() {
        try { server.close() } catch (_: Exception) { /* ignore */ }
        acceptor?.join(1000)
    }

    private fun serve(socket: Socket) {
        try {
            socket.use { s ->
                val reader = s.getInputStream().bufferedReader(Charsets.UTF_8)
                val out = s.getOutputStream()
                while (true) {
                    val first = reader.readLine() ?: break
                    if (first.isEmpty()) continue
                    var line: String? = reader.readLine()
                    while (!line.isNullOrEmpty()) line = reader.readLine()
                    val body = "[]"
                    out.write(
                        (
                            "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: application/json\r\n" +
                                "Content-Length: ${body.length}\r\n" +
                                "Connection: keep-alive\r\n\r\n$body"
                            ).toByteArray(Charsets.UTF_8),
                    )
                    out.flush()
                }
            }
        } catch (_: Exception) {
            // 客户端关闭：正常结束
        }
    }

    private fun get(client: OkHttpClient, url: String) {
        client.newCall(Request.Builder().url(url).build()).execute().use { it.body.string() }
    }

    @Test
    fun `共享连接池：3 个模块 client 顺序请求同一 host 只建 1 条 TCP 连接`() {
        val base = "http://127.0.0.1:${server.localPort}/"
        val clients = listOf(
            NetworkClients.apiClient(),
            NetworkClients.attachmentClient(),
            NetworkClients.downloadClient(),
        )
        clients.forEachIndexed { index, client -> get(client, base + "module$index") }

        assertEquals(1, connections.get())
    }

    @Test
    fun `优化前基线：每模块各自 OkHttpClient 会各自建连（3 个 client = 3 条连接）`() {
        val base = "http://127.0.0.1:${server.localPort}/"
        val clients = (1..3).map { OkHttpClient.Builder().build() }
        clients.forEachIndexed { index, client -> get(client, base + "legacy$index") }

        assertEquals(3, connections.get())
    }
}
