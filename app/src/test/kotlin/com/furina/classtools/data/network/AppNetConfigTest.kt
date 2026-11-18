package com.furina.classtools.data.network

import com.furina.classtools.BuildConfig
import okhttp3.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-11-18 自建后端切流配置 + 全局网络栈（P3）单测。
 *
 * 单测编译的是 **debug** 变体，所以这里验证 debug 默认值：
 * - REST base 默认 = ECS staging 影子库（http IP，仅 debug 放行明文）；发布前不发布/不上传；
 * - ECS 账号 / 云空间 / 更新 / 地图默认 = 备案前 IP 调试入口；
 * - 所有模块 client 共享同一连接池/Dispatcher，协议列表 HTTP/2 优先（备案后 HTTPS 自动多路复用）。
 * release 变体的「必须 https 且不含 121.196.47.2」由 Gradle 任务 `:app:verifyReleaseEndpoints` 校验
 * （JVM 单测只能看到 debug 变体，见 deploy/migration/CLIENTS.md）。
 */
class AppNetConfigTest {

    @Test
    fun `debug 构建默认 REST base 指向 ECS staging 影子路由`() {
        assertEquals("http://121.196.47.2/v1/rdb/rest-next/", BuildConfig.REST_BASE_URL)
        assertEquals("staging", BuildConfig.REST_PROFILE)
        assertTrue("debug 允许 http（不校验合法域名/明文仅 debug 放行）", BuildConfig.REST_BASE_URL.startsWith("http://"))
    }

    @Test
    fun `debug 构建 ECS 服务默认走备案前 IP 调试入口`() {
        assertTrue(BuildConfig.ACCOUNT_BASE_URL.startsWith("http://121.196.47.2/"))
        assertTrue(BuildConfig.CLOUDSPACE_BASE_URL.startsWith("http://121.196.47.2/"))
        assertTrue(BuildConfig.UPDATE_BASE_URL.startsWith("http://121.196.47.2/"))
        assertTrue(BuildConfig.MAP_BASE_URL.startsWith("http://121.196.47.2/"))
    }

    @Test
    fun `全局网络栈：api 下载 附件 client 共享同一连接池与 Dispatcher`() {
        val api = NetworkClients.apiClient()
        val cloudSpace = NetworkClients.cloudSpaceClient()
        val download = NetworkClients.downloadClient()
        val attachment = NetworkClients.attachmentClient()

        assertSame(NetworkClients.connectionPool, api.connectionPool)
        assertSame(NetworkClients.connectionPool, cloudSpace.connectionPool)
        assertSame(NetworkClients.connectionPool, download.connectionPool)
        assertSame(NetworkClients.connectionPool, attachment.connectionPool)
        assertSame(NetworkClients.dispatcher, api.dispatcher)
        assertSame(NetworkClients.dispatcher, download.dispatcher)

        // HTTP/2 优先（同一 host 多路复用）；HTTP/1.1 keep-alive 兜底（staging 的 http IP 无 h2c）
        assertEquals(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1), api.protocols)
        // 重试由 ApiClient 显式控制（写请求绝不自动重试）
        assertFalse(api.retryOnConnectionFailure)
        // 下载 client 不设整体超时（大文件），附件/API 有整体超时
        assertTrue(download.readTimeoutMillis >= 60_000)
    }
}
