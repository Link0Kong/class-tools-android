package com.furina.classtools.data.cloudspace

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 临时云空间仓库：会话生命周期 + 列表事件合并 + 分片上传 + 删除/清空 + 下载前刷新签名链接。
 *
 * 设计要点（对齐网页 `B2FILE` / 小程序 `pages/cloudspace`）：
 * - 同账号跨设备自动共享：先 `GET /mine`，无会话再 `POST /open`（**不展示房间码/配对**）；
 * - 列表以 `/state` 为准：`since=0` 全量重建（force），否则按 cursor 增量合并（file/filedel）；
 * - 下载链接 30 分钟有效且每次 `/state` 重签：下载前调 [findFreshFile] 强拉一次最新链接；
 * - 上传：`init → chunk × N（8MiB 原始字节）→ finish`，逐片回调进度；
 * - 所有公开方法主线程安全、不抛异常（协程取消除外），失败返回 [CloudSpaceResult.Failure]。
 *
 * 装配（Lead 负责）：`CloudSpaceRepository(CloudSpaceApi(tokenProvider = { activeSession?.token }))`。
 * token 只由 [CloudSpaceApi] 放进 `X-Device-Id`，本类不接触 token、不落盘、不打日志。
 */
class CloudSpaceRepository(
    private val api: CloudSpaceApi,
    private val now: () -> Long = System::currentTimeMillis,
    private val chunkSize: Int = CloudSpaceDefaults.CHUNK_SIZE,
) {

    private val mutex = Mutex()

    @Volatile
    private var session: CloudSpaceSession? = null

    @Volatile
    private var files: List<CloudSpaceFile> = emptyList()

    @Volatile
    private var expiredIds: Set<String> = emptySet()

    @Volatile
    private var cursor: Long = 0L

    /** 当前列表快照（只读；可在组合/任意线程读取）。 */
    fun snapshot(): CloudSpaceSnapshot = CloudSpaceSnapshot(
        session = session,
        files = files,
        expiredCount = expiredIds.size,
        cursor = cursor,
    )

    // ---------------------------------------------------------------- 会话 + 列表

    /**
     * 确保已连接本账号云空间（首次进入页面调用）：`/mine` → 无会话则 `/open` → 全量拉列表。
     * 已连接时等价于 [refresh]`(force = true)`（网页/小程序进入页面即强制刷新）。
     */
    suspend fun ensureSpace(): CloudSpaceResult<CloudSpaceSnapshot> = mutex.withLock {
        if (session != null) return@withLock refreshLocked(force = true)
        when (val resolved = resolveSessionLocked()) {
            is CloudSpaceResult.Failure -> resolved
            is CloudSpaceResult.Success -> {
                session = resolved.value
                files = emptyList()
                expiredIds = emptySet()
                cursor = 0L
                refreshLocked(force = true)
            }
        }
    }

    /**
     * 刷新列表。
     *
     * @param force true = `since=0` 全量重建（进入页面/下拉刷新/下载前）；false = 增量（5s 轮询）。
     * 失败时保留上一次列表（离线仍可看缓存），由 UI 展示错误提示。
     */
    suspend fun refresh(force: Boolean = false): CloudSpaceResult<CloudSpaceSnapshot> {
        val hasSession = mutex.withLock { session != null }
        if (!hasSession) return ensureSpace()
        return mutex.withLock { refreshLocked(force = force) }
    }

    /** 下载前调用：强拉一次最新列表并返回该文件（带 30 分钟有效的签名下载链接）。 */
    suspend fun findFreshFile(fileId: String): CloudSpaceResult<CloudSpaceFile> {
        if (fileId.isBlank()) {
            return CloudSpaceResult.Failure(CloudSpaceErrors.local("文件不存在或已被删除"))
        }
        when (val refreshed = refresh(force = true)) {
            is CloudSpaceResult.Failure -> return refreshed
            is CloudSpaceResult.Success -> {
                val file = refreshed.value.files.firstOrNull { it.id == fileId }
                    ?: return CloudSpaceResult.Failure(
                        CloudSpaceErrors.local("文件已过期或已被删除（上传超过 72 小时）"),
                    )
                if (file.expired || file.downloadUrl.isBlank()) {
                    return CloudSpaceResult.Failure(
                        CloudSpaceErrors.local("文件已过期（上传超过 72 小时，已自动删除）"),
                    )
                }
                return CloudSpaceResult.Success(file)
            }
        }
    }

    // ---------------------------------------------------------------- 上传

    /**
     * 分片上传一个文件（任意大小；8MiB/片，逐片回调 [onProgress]）。
     *
     * 失败语义：任何一片失败都立即停止（服务端残留分片由 24h 清理线程回收，重传同 index 可覆盖）。
     * 成功后把新文件插入本地列表（随后 5s 轮询增量合并仍幂等）。
     */
    suspend fun upload(
        source: CloudSpaceByteSource,
        onProgress: (CloudSpaceUploadProgress) -> Unit = {},
    ): CloudSpaceResult<CloudSpaceFile> {
        val size = source.size
        if (size <= 0L) {
            return CloudSpaceResult.Failure(CloudSpaceErrors.local("空文件无法上传（0 字节）"))
        }
        val plan = CloudSpaceLogic.chunkPlan(size, chunkSize)
        if (plan.totalChunks > CloudSpaceDefaults.MAX_UPLOAD_CHUNKS) {
            return CloudSpaceResult.Failure(
                CloudSpaceErrors.local("文件超过服务上限（约 512 GB），请压缩后重试"),
            )
        }
        val sessionResult = mutex.withLock { resolveSessionLocked() }
        val currentSession = when (sessionResult) {
            is CloudSpaceResult.Failure -> return sessionResult
            is CloudSpaceResult.Success -> sessionResult.value
        }
        val name = CloudSpaceLogic.safeName(source.name)
        val mimeType = source.mimeType.ifBlank { "application/octet-stream" }

        onProgress(CloudSpaceUploadProgress(CloudSpaceUploadState.WAITING, sentBytes = 0L, totalBytes = size))

        val initResult = api.initUpload(
            sessionId = currentSession.id,
            name = name,
            size = size,
            mimeType = mimeType,
            totalChunks = plan.totalChunks,
        )
        val init = when (initResult) {
            is CloudSpaceResult.Failure -> return initResult
            is CloudSpaceResult.Success -> initResult.value
        }
        if (init.chunkSize != plan.chunkSize) {
            // 契约固定 8MiB；服务端返回不同值说明版本/配置异常，继续上传可能拼接失败。
            return CloudSpaceResult.Failure(
                CloudSpaceErrors.local("云空间分片大小异常（${init.chunkSize} B），请稍后重试"),
            )
        }

        var sent = 0L
        for (chunk in plan.chunks) {
            onProgress(
                CloudSpaceUploadProgress(
                    state = CloudSpaceUploadState.UPLOADING,
                    sentBytes = chunk.start,
                    totalBytes = size,
                ),
            )
            val bytes = try {
                source.read(chunk.start, chunk.size.toInt())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                return CloudSpaceResult.Failure(
                    CloudSpaceErrors.local("读取文件失败，请重新选择文件"),
                )
            }
            if (bytes.size.toLong() != chunk.size) {
                return CloudSpaceResult.Failure(
                    CloudSpaceErrors.local("文件读取不完整（可能已被移动或截断），请重新选择文件"),
                )
            }
            when (val chunkResult = api.uploadChunk(init.uploadId, chunk.index, bytes)) {
                is CloudSpaceResult.Failure -> {
                    onProgress(
                        CloudSpaceUploadProgress(
                            state = CloudSpaceUploadState.FAILED,
                            sentBytes = sent,
                            totalBytes = size,
                            message = chunkResult.error.displayMessage,
                        ),
                    )
                    return chunkResult
                }

                is CloudSpaceResult.Success -> {
                    sent = chunk.endExclusive
                    onProgress(
                        CloudSpaceUploadProgress(
                            state = CloudSpaceUploadState.UPLOADING,
                            sentBytes = sent,
                            totalBytes = size,
                        ),
                    )
                }
            }
        }

        onProgress(
            CloudSpaceUploadProgress(
                state = CloudSpaceUploadState.UPLOADING,
                sentBytes = size,
                totalBytes = size,
                message = "正在完成…",
            ),
        )
        val finishResult = api.finishUpload(currentSession.id, init.uploadId)
        val finished = when (finishResult) {
            is CloudSpaceResult.Failure -> {
                onProgress(
                    CloudSpaceUploadProgress(
                        state = CloudSpaceUploadState.FAILED,
                        sentBytes = size,
                        totalBytes = size,
                        message = finishResult.error.displayMessage,
                    ),
                )
                return finishResult
            }

            is CloudSpaceResult.Success -> finishResult.value
        }
        val stored = finished.copy(
            uploadedAt = if (finished.uploadedAt > 0L) finished.uploadedAt else now(),
            expired = false,
        )
        mutex.withLock {
            files = (listOf(stored) + files.filterNot { it.id == stored.id })
                .sortedWith(compareByDescending<CloudSpaceFile> { it.uploadedAt }.thenBy { it.id })
            expiredIds = expiredIds - stored.id
        }
        onProgress(
            CloudSpaceUploadProgress(
                state = CloudSpaceUploadState.DONE,
                sentBytes = size,
                totalBytes = size,
            ),
        )
        return CloudSpaceResult.Success(stored)
    }

    // ---------------------------------------------------------------- 删除 / 清空

    /** 删除单个文件（幂等；成功后从本地列表移除）。 */
    suspend fun delete(fileId: String): CloudSpaceResult<CloudSpaceSnapshot> {
        if (fileId.isBlank()) {
            return CloudSpaceResult.Failure(CloudSpaceErrors.local("文件不存在或已被删除"))
        }
        val currentSession = mutex.withLock { session }
            ?: return CloudSpaceResult.Failure(CloudSpaceErrors.local("云空间未连接，请先刷新"))
        when (val result = api.deleteFile(fileId, currentSession.id)) {
            is CloudSpaceResult.Failure -> return result
            is CloudSpaceResult.Success -> {
                mutex.withLock {
                    files = files.filterNot { it.id == fileId }
                    expiredIds = expiredIds - fileId
                }
                return CloudSpaceResult.Success(snapshot())
            }
        }
    }

    /**
     * 清空云空间：`POST /close` 删除会话与全部文件，随后自动重开一个新空间（网页/小程序同语义）。
     */
    suspend fun clearSpace(): CloudSpaceResult<CloudSpaceSnapshot> {
        val currentSession = mutex.withLock { session } ?: return ensureSpace()
        when (val closed = api.closeSpace(currentSession.id)) {
            is CloudSpaceResult.Failure -> return closed
            is CloudSpaceResult.Success -> Unit
        }
        mutex.withLock {
            session = null
            files = emptyList()
            expiredIds = emptySet()
            cursor = 0L
        }
        return ensureSpace()
    }

    /** 退出登录/切换账号时清空内存状态（不向服务端发请求）。 */
    fun reset() {
        session = null
        files = emptyList()
        expiredIds = emptySet()
        cursor = 0L
    }

    // ---------------------------------------------------------------- 内部实现（调用方需持有 mutex）

    private suspend fun resolveSessionLocked(): CloudSpaceResult<CloudSpaceSession> {
        session?.let { return CloudSpaceResult.Success(it) }
        val mine = api.mine()
        if (mine is CloudSpaceResult.Failure) return mine
        val existing = (mine as CloudSpaceResult.Success).value
        if (existing != null) return CloudSpaceResult.Success(existing)
        return api.openSpace()
    }

    private suspend fun refreshLocked(force: Boolean): CloudSpaceResult<CloudSpaceSnapshot> {
        val currentSession = session
            ?: return CloudSpaceResult.Failure(CloudSpaceErrors.local("云空间未连接"))
        val since = if (force) 0L else cursor
        return when (val stateResult = api.state(currentSession.id, since)) {
            is CloudSpaceResult.Failure -> stateResult
            is CloudSpaceResult.Success -> {
                val state = stateResult.value
                val merged = CloudSpaceLogic.applyEvents(
                    currentFiles = if (force) emptyList() else files,
                    currentExpiredIds = if (force) emptySet() else expiredIds,
                    events = state.events,
                )
                session = state.session ?: currentSession
                files = merged.files
                expiredIds = merged.expiredIds
                cursor = if (force) state.cursor else maxOf(cursor, state.cursor)
                CloudSpaceResult.Success(snapshot())
            }
        }
    }
}
