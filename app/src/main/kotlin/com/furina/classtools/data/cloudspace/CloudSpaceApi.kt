package com.furina.classtools.data.cloudspace

import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.asBooleanOrNull
import com.furina.classtools.data.network.asIntOrNull
import com.furina.classtools.data.network.asLongOrNull
import com.furina.classtools.data.network.asStringOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * ECS drop 服务 API 客户端（契约 `web/drop/CONTRACT.md` v2.2）。
 *
 * 接口：
 * - `GET  /mine`                       是否有活动会话（不创建）
 * - `POST /open`                       惰性创建/复用本账号会话（同 sid 跨设备自动互见）
 * - `GET  /state?sessionId=&since=`    事件列表（`file` 事件带签名 downloadUrl；`filedel` 表示删除）
 * - `POST /upload/init`                分片上传初始化（返回 uploadId/chunkSize）
 * - `POST /upload/chunk?uploadId=&index=` 原始字节分片（8MiB，`Content-Type: application/octet-stream`）
 * - `POST /upload/finish`              拼接完成（返回 file 并追加 file 事件）
 * - `DELETE /file/<fileId>?sessionId=` 单文件删除（幂等）
 * - `POST /close`                      清空：删除会话与全部文件
 *
 * 鉴权：所有请求带 `X-Device-Id: <班级 token>`（来自装配层注入的 [tokenProvider]）。
 * **token 只出现在请求头**：不进 URL / 请求体 / 日志 / 错误文案 / 落盘。
 *
 * 线程约定：所有方法都是 suspend 且主线程安全（IO 在 [OkHttpCloudSpaceTransport] 内切线程）；
 * 网络/HTTP/解析错误都以 [CloudSpaceResult.Failure] 返回，不抛异常（协程取消除外）。
 */
class CloudSpaceApi(
    baseUrl: String = CloudSpaceDefaults.BASE_URL,
    private val tokenProvider: () -> String? = { null },
    private val transport: CloudSpaceTransport = OkHttpCloudSpaceTransport(),
    private val json: Json = NetJson,
    /** 401 回调（可选）：装配层可据此清会话；默认不做任何事（避免临时鉴权故障强制退出）。 */
    private val onUnauthorized: () -> Unit = {},
) {

    /** 规范化基址（**结尾不带 `/`**）；`url("/state")` → `<base>/state`。 */
    val baseUrl: String = baseUrl.trim().trimEnd('/')

    // ------------------------------------------------------------ URL / 请求头（可单测的纯函数）

    fun url(path: String): String {
        if (path.isEmpty()) return baseUrl
        return if (path.startsWith('/')) baseUrl + path else "$baseUrl/$path"
    }

    /** 统一请求头：token 只在 `X-Device-Id`，其余为 JSON/缓存约定。 */
    fun headers(contentType: String? = null): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        val token = tokenProvider()?.trim().orEmpty()
        if (token.isNotEmpty()) headers["X-Device-Id"] = token
        headers["Accept"] = "application/json"
        headers["Cache-Control"] = "no-store"
        if (!contentType.isNullOrBlank()) headers["Content-Type"] = contentType
        return headers
    }

    // ------------------------------------------------------------ API

    /** 查询本账号当前会话；无会话返回 Success(null)（不创建）。 */
    suspend fun mine(): CloudSpaceResult<CloudSpaceSession?> = when (val result = callGet("/mine")) {
        is CloudSpaceResult.Failure -> result
        is CloudSpaceResult.Success -> CloudSpaceResult.Success(parseSession(result.value["session"]))
    }

    /** 打开（惰性创建/复用）本账号会话；同一 sid 多设备复用同一会话。 */
    suspend fun openSpace(): CloudSpaceResult<CloudSpaceSession> {
        val body = buildJsonObject { }.toString()
        return when (val result = callJson(CloudSpaceHttpMethod.POST, "/open", body)) {
            is CloudSpaceResult.Failure -> result
            is CloudSpaceResult.Success -> {
                val session = parseSession(result.value["session"])
                if (session == null) {
                    CloudSpaceResult.Failure(CloudSpaceErrors.parse())
                } else {
                    CloudSpaceResult.Success(session)
                }
            }
        }
    }

    /** 拉取事件（`since` 为已收到的最大 seq；0 = 全量）。 */
    suspend fun state(sessionId: String, since: Long = 0L): CloudSpaceResult<CloudSpaceState> {
        val path = "/state?sessionId=${encode(sessionId)}&since=${since.coerceAtLeast(0L)}"
        return when (val result = callGet(path)) {
            is CloudSpaceResult.Failure -> result
            is CloudSpaceResult.Success -> {
                val obj = result.value
                val events = (obj["events"] as? JsonArray)
                    ?.mapNotNull { parseEvent(it) }
                    .orEmpty()
                val cursor = obj["cursor"].asLongOrNull()
                    ?: events.maxOfOrNull { it.seq }
                    ?: 0L
                CloudSpaceResult.Success(
                    CloudSpaceState(
                        session = parseSession(obj["session"]),
                        events = events,
                        cursor = cursor,
                    ),
                )
            }
        }
    }

    /** 分片上传初始化；`totalChunks` 必须等于 `ceil(size / 8MiB)`（至少 1）。 */
    suspend fun initUpload(
        sessionId: String,
        name: String,
        size: Long,
        mimeType: String,
        totalChunks: Int,
    ): CloudSpaceResult<CloudSpaceUploadInit> {
        val body = buildJsonObject {
            put("sessionId", sessionId)
            put("name", name)
            put("size", size.coerceAtLeast(0L))
            put("mime", mimeType.ifBlank { "application/octet-stream" })
            put("totalChunks", totalChunks)
        }.toString()
        return when (val result = callJson(CloudSpaceHttpMethod.POST, "/upload/init", body)) {
            is CloudSpaceResult.Failure -> result
            is CloudSpaceResult.Success -> {
                val uploadId = result.value["uploadId"].asStringOrNull().orEmpty()
                if (uploadId.isBlank()) {
                    CloudSpaceResult.Failure(CloudSpaceErrors.parse())
                } else {
                    CloudSpaceResult.Success(
                        CloudSpaceUploadInit(
                            uploadId = uploadId,
                            chunkSize = result.value["chunkSize"].asIntOrNull()
                                ?.takeIf { it > 0 }
                                ?: CloudSpaceDefaults.CHUNK_SIZE,
                        ),
                    )
                }
            }
        }
    }

    /** 上传一个原始字节分片；返回服务端累计已收字节（仅用于诊断，进度由客户端计算）。 */
    suspend fun uploadChunk(
        uploadId: String,
        index: Int,
        bytes: ByteArray,
    ): CloudSpaceResult<Long> {
        val path = "/upload/chunk?uploadId=${encode(uploadId)}&index=${index.coerceAtLeast(0)}"
        val request = CloudSpaceRequest(
            method = CloudSpaceHttpMethod.POST,
            url = url(path),
            headers = headers(contentType = OCTET_STREAM),
            body = bytes,
            contentType = OCTET_STREAM,
        )
        return when (val result = execute(request)) {
            is CloudSpaceResult.Failure -> result
            is CloudSpaceResult.Success -> CloudSpaceResult.Success(
                result.value["received"].asLongOrNull() ?: 0L,
            )
        }
    }

    /** 拼接分片完成；返回新文件（含下载链接），服务端同时追加 `file` 事件。 */
    suspend fun finishUpload(sessionId: String, uploadId: String): CloudSpaceResult<CloudSpaceFile> {
        val body = buildJsonObject {
            put("sessionId", sessionId)
            put("uploadId", uploadId)
        }.toString()
        return when (val result = callJson(CloudSpaceHttpMethod.POST, "/upload/finish", body)) {
            is CloudSpaceResult.Failure -> result
            is CloudSpaceResult.Success -> {
                val file = parseFinishedFile(result.value["file"])
                if (file == null) {
                    CloudSpaceResult.Failure(CloudSpaceErrors.parse())
                } else {
                    CloudSpaceResult.Success(file)
                }
            }
        }
    }

    /** 删除单个文件（幂等：文件不存在/已过期返回 deleted=false 且仍算成功）。 */
    suspend fun deleteFile(fileId: String, sessionId: String): CloudSpaceResult<Boolean> {
        val path = "/file/${encode(fileId)}?sessionId=${encode(sessionId)}"
        return when (val result = callRaw(CloudSpaceHttpMethod.DELETE, path)) {
            is CloudSpaceResult.Failure -> result
            is CloudSpaceResult.Success -> CloudSpaceResult.Success(
                result.value["deleted"].asBooleanOrNull() == true,
            )
        }
    }

    /** 清空：删除本账号会话与全部文件（随后页面自动重开一个新空间）。 */
    suspend fun closeSpace(sessionId: String): CloudSpaceResult<Unit> {
        val body = buildJsonObject { put("sessionId", sessionId) }.toString()
        return when (val result = callJson(CloudSpaceHttpMethod.POST, "/close", body)) {
            is CloudSpaceResult.Failure -> result
            is CloudSpaceResult.Success -> CloudSpaceResult.Success(Unit)
        }
    }

    // ------------------------------------------------------------ 执行 / 错误映射

    private suspend fun callGet(path: String): CloudSpaceResult<JsonObject> =
        callRaw(CloudSpaceHttpMethod.GET, path)

    private suspend fun callJson(
        method: CloudSpaceHttpMethod,
        path: String,
        jsonBody: String,
    ): CloudSpaceResult<JsonObject> = callRaw(
        method = method,
        path = path,
        body = jsonBody.toByteArray(Charsets.UTF_8),
        contentType = APPLICATION_JSON,
    )

    private suspend fun callRaw(
        method: CloudSpaceHttpMethod,
        path: String,
        body: ByteArray? = null,
        contentType: String? = null,
    ): CloudSpaceResult<JsonObject> {
        if (baseUrl.isBlank()) {
            return CloudSpaceResult.Failure(CloudSpaceErrors.local("云空间地址未配置"))
        }
        val request = CloudSpaceRequest(
            method = method,
            url = url(path),
            headers = headers(contentType),
            body = body,
            contentType = contentType,
        )
        return execute(request)
    }

    private suspend fun execute(request: CloudSpaceRequest): CloudSpaceResult<JsonObject> {
        val response = try {
            transport.execute(request)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // 异常信息可能包含 URL / 主机名；统一用固定中文文案，不拼接 cause（避免泄漏到 UI/日志）。
            return CloudSpaceResult.Failure(CloudSpaceErrors.network())
        }
        return interpret(response)
    }

    private fun interpret(response: CloudSpaceResponse): CloudSpaceResult<JsonObject> {
        val status = response.status
        if (status == 401) {
            onUnauthorized()
            return CloudSpaceResult.Failure(
                CloudSpaceErrors.http(401, extractReason(response.body)),
            )
        }
        val parsed = parseJsonOrNull(json, response.body) as? JsonObject
        if (parsed == null) {
            return if (status in 200..299) {
                CloudSpaceResult.Failure(CloudSpaceErrors.parse())
            } else {
                CloudSpaceResult.Failure(CloudSpaceErrors.http(status))
            }
        }
        if (status !in 200..299) {
            return CloudSpaceResult.Failure(
                CloudSpaceErrors.http(status, parsed["reason"].asStringOrNull()),
            )
        }
        if (parsed["ok"].asBooleanOrNull() == false) {
            return CloudSpaceResult.Failure(
                CloudSpaceErrors.rejected(parsed["reason"].asStringOrNull()),
            )
        }
        return CloudSpaceResult.Success(parsed)
    }

    private fun extractReason(body: String): String? =
        (parseJsonOrNull(json, body) as? JsonObject)?.get("reason").asStringOrNull()

    // ------------------------------------------------------------ 解析

    private fun parseSession(element: JsonElement?): CloudSpaceSession? {
        val obj = element as? JsonObject ?: return null
        val id = obj["id"].asStringOrNull()?.trim().orEmpty()
        if (id.isEmpty()) return null
        return CloudSpaceSession(
            id = id,
            code = obj["code"].asStringOrNull().orEmpty(),
            createdAt = obj["createdAt"].asLongOrNull() ?: 0L,
            expiresAt = obj["expiresAt"].asLongOrNull() ?: 0L,
        )
    }

    /** `/state` 的 file 事件：payload 用 `fileId`（`/upload/finish` 响应用 `id`，两者都兼容）。 */
    private fun parseEvent(element: JsonElement): CloudSpaceEvent? {
        val obj = element as? JsonObject ?: return null
        val kind = obj["kind"].asStringOrNull().orEmpty()
        val seq = obj["seq"].asLongOrNull() ?: 0L
        val at = obj["at"].asLongOrNull() ?: 0L
        val payload = obj["payload"] as? JsonObject ?: JsonObject(emptyMap())
        return when (kind) {
            // 契约 §2.1：file 事件的 event.at = 文件上传完成时刻（TTL 起算点）。
            "file" -> parseFileObject(payload, uploadedAt = at)
                ?.let { CloudSpaceEvent.FileAdded(file = it, seq = seq) }

            "filedel" -> {
                val fileId = payload["fileId"].asStringOrNull()
                    ?: payload["id"].asStringOrNull()
                fileId?.takeIf { it.isNotBlank() }?.let {
                    CloudSpaceEvent.FileDeleted(fileId = it, seq = seq)
                }
            }

            // text / cmd 事件与本页（临时云空间）无关，忽略。
            else -> null
        }
    }

    private fun parseFinishedFile(element: JsonElement?): CloudSpaceFile? {
        val obj = element as? JsonObject ?: return null
        return parseFileObject(obj, uploadedAt = 0L)
    }

    private fun parseFileObject(obj: JsonObject, uploadedAt: Long): CloudSpaceFile? {
        val fileId = obj["fileId"].asStringOrNull() ?: obj["id"].asStringOrNull()
        if (fileId.isNullOrBlank()) return null
        val downloadUrl = obj["downloadUrl"].asStringOrNull().orEmpty()
        val expired = obj["expired"].asBooleanOrNull() == true || downloadUrl.isBlank()
        return CloudSpaceFile(
            id = fileId,
            name = obj["name"].asStringOrNull().orEmpty().ifBlank { "文件" },
            sizeBytes = obj["size"].asLongOrNull() ?: 0L,
            mimeType = obj["mime"].asStringOrNull().orEmpty(),
            downloadUrl = if (expired) "" else downloadUrl,
            expired = expired,
            uploadedAt = uploadedAt,
        )
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        const val APPLICATION_JSON = "application/json"
        const val OCTET_STREAM = "application/octet-stream"
    }
}
