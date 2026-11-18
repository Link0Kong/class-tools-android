package com.furina.classtools.data.network

import java.time.Duration
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol

/**
 * 全局 OkHttp 网络栈（2026-11-18 网络体验优化）。
 *
 * 背景：此前每个数据模块各自 `OkHttpClient.Builder()`（REST 网关、教务、云空间、附件下载、PPT 图片、
 * 更新检查/下载…），每个 client 都有独立连接池与 Dispatcher —— 同一个主机（ECS IP / 备案后域名）
 * 的 TCP/TLS 连接无法复用，握手次数翻倍、弱网首包更慢。
 *
 * 本对象提供**唯一共享的连接池 + Dispatcher**，各模块的 client 只按自己的超时口径配置：
 * - 连接池：maxIdle=8 / keepAlive=5min（同一 host 的 HTTP/1.1 keep-alive 与 HTTP/2 多路复用都复用）；
 * - Dispatcher：maxRequests=64 / maxRequestsPerHost=16（并发上限，避免打爆移动网络/服务端）；
 * - 协议：显式 HTTP/2 优先、回退 HTTP/1.1（OkHttp 对 HTTPS 默认协商 h2；staging 的 http IP 无 h2c，
 *   自动走 HTTP/1.1 keep-alive 复用——与迁移计划 P4 备案后 HTTPS/HTTP2 一致）；
 * - `retryOnConnectionFailure(false)`：重试策略统一由 App 层控制（写请求绝不自动重试）。
 *
 * 线程约定：这些 client 全部同步 `execute()`，调用方在 Dispatchers.IO 上执行（见 [OkHttpTransport]）。
 */
object NetworkClients {

    /** 共享连接池（同一主机跨模块复用 TCP/TLS/HTTP2 连接）。 */
    val connectionPool: ConnectionPool = ConnectionPool(maxIdleConnections = 8, keepAliveDuration = 5, timeUnit = TimeUnit.MINUTES)

    /** 共享调度器（并发上限；与连接池一起传给所有 client）。 */
    val dispatcher: Dispatcher = Dispatcher().apply {
        maxRequests = 64
        maxRequestsPerHost = 16
    }

    private fun baseBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectionPool(connectionPool)
        .dispatcher(dispatcher)
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .retryOnConnectionFailure(false)

    /** 需要自定义超时的模块用它构建（仍共享连接池/Dispatcher；不要直接 new OkHttpClient.Builder()）。 */
    fun builder(): OkHttpClient.Builder = baseBuilder()

    /** 云端 REST / 小请求（与既有契约一致：连接 15s / 读 30s / 写 30s / 整体 60s）。 */
    fun apiClient(): OkHttpClient = baseBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .readTimeout(Duration.ofSeconds(30))
        .writeTimeout(Duration.ofSeconds(30))
        .callTimeout(Duration.ofSeconds(60))
        .build()

    /** 云空间 JSON/分片请求：读/写放宽到 120s，整体 10 分钟（8MiB 分片弱网余量）。 */
    fun cloudSpaceClient(): OkHttpClient = baseBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .readTimeout(Duration.ofSeconds(120))
        .writeTimeout(Duration.ofSeconds(120))
        .callTimeout(Duration.ofMinutes(10))
        .build()

    /** 大文件下载：读 10 分钟、不设整体超时（由系统网络自然超时兜底）。 */
    fun downloadClient(): OkHttpClient = baseBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .readTimeout(Duration.ofMinutes(10))
        .writeTimeout(Duration.ofMinutes(10))
        .callTimeout(Duration.ZERO)
        .build()

    /** 附件/图片下载：连接 15s / 读 30s / 整体 120s（20MB 上限 + 移动网络余量）。 */
    fun attachmentClient(): OkHttpClient = baseBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .readTimeout(Duration.ofSeconds(30))
        .writeTimeout(Duration.ofSeconds(30))
        .callTimeout(Duration.ofSeconds(120))
        // 附件下载为只读且可安全重放：沿用旧实现的连接层重试（弱网抖动少一次失败）
        .retryOnConnectionFailure(true)
        .build()
}
