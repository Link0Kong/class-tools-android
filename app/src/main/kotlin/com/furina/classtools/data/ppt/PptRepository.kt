package com.furina.classtools.data.ppt

import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiError
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.isTokenInvalid
import com.furina.classtools.data.session.Session
import com.furina.classtools.features.ppt.PptIds
import com.furina.classtools.features.ppt.PptLimits
import com.furina.classtools.features.ppt.PptStatusTexts
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 一次上传的阶段（UI 进度卡片展示）。 */
enum class PptUploadPhase { IDLE, CREATING, UPLOADING, SUBMITTING, DONE, FAILED, CANCELLED }

/** 上传进度快照（仓库以 [PptRepository.uploadProgress] 暴露 StateFlow，UI collect）。 */
data class PptUploadProgress(
    val deckId: String? = null,
    val percent: Int = 0,
    val phase: PptUploadPhase = PptUploadPhase.IDLE,
    val message: String = "",
    val title: String = "",
)

/**
 * 演示（PPT）仓库（契约：web/ppt/CONTRACT.md §4 全部接口 + §3.4 心跳）。
 *
 * 设计：
 * - **不抛异常**（除协程取消）；失败统一返回 sealed 结果 / [ApiResult]；
 * - 列表/播放清单失败时可回退本机上次成功结果（不白屏、不丢本地状态，契约 §4 开头）；
 * - 上传：建行 → 分片（512KiB、base64、≤4 并行、失败重试 2 次）→ 提交转换；
 *   进度通过 [uploadProgress] StateFlow 暴露；取消时软删已建的行（契约 §8.1「取消即软删」）；
 * - 分片读取走 [PptByteSource]（Android 实现按 offset 读取，不整文件进内存）。
 *
 * 纯 Kotlin（无 android.*）→ JVM 单测可用 FakeTransport 全链路验证。
 */
interface PptRepository {
    /** 上传进度（StateFlow：compass UI 无需轮询）。 */
    val uploadProgress: StateFlow<PptUploadProgress>

    /** §4.4 列表 + §3.4 心跳（同一轮并行拉取）。 */
    suspend fun loadList(): PptListResult

    /** §4.5 播放前取清单（旧 deck 在线预览入口）。 */
    suspend fun loadPlayer(deckId: String): PptPlayerResult

    /** v2.0 §12.4.1 下载详情：单 deck manifest + 状态/进度/文件（不要求可播放）。 */
    suspend fun loadDownloadDetail(deckId: String): PptDetailResult

    /** §4.1→§4.2→§4.3 一次完整上传（含取消软删）。 */
    suspend fun upload(input: PptUploadInput): PptUploadResult

    /** §4.6 软删除（仅本人或管理员）。 */
    suspend fun softDelete(deckId: String): ApiResult<Unit>

    /** §4.6 失败重试（重新排队）。 */
    suspend fun retryConversion(deckId: String): ApiResult<Unit>

    /** §3.4 单独拉心跳（列表轮询内已包含，此入口供刷新按钮/测试）。 */
    suspend fun loadAgentStatus(): PptAgentStatus?
}

/** 上传内部失败（携带是否 token 失效）。 */
private class PptUploadFailure(
    override val message: String,
    val tokenInvalid: Boolean = false,
) : Exception(message)

class DefaultPptRepository(
    private val api: ApiClient,
    private val currentSession: () -> Session? = { null },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
    private val chunkBytes: Int = PptLimits.CHUNK_BYTES,
    private val concurrency: Int = PptLimits.MAX_CONCURRENCY,
    private val retryDelays: List<Long> = PptLimits.RETRY_DELAYS_MS,
    private val json: Json = NetJson,
) : PptRepository {

    @Volatile
    private var cachedDecks: List<PptDeck>? = null

    @Volatile
    private var cachedAgent: PptAgentStatus? = null

    private val _uploadProgress = MutableStateFlow(PptUploadProgress())
    override val uploadProgress: StateFlow<PptUploadProgress> = _uploadProgress.asStateFlow()

    // ------------------------------------------------------------ 列表 / 心跳

    override suspend fun loadList(): PptListResult {
        val pair = coroutineScope {
            val decksDeferred = async { api.get(LIST_PATH) }
            val agentDeferred = async { api.get(AGENT_PATH) }
            decksDeferred.await() to agentDeferred.await()
        }
        val decksRaw = pair.first
        val agentRaw = pair.second

        if (agentRaw is ApiResult.Success) {
            PptParsers.parseAgentStatus(json, agentRaw.value)?.let { cachedAgent = it }
        }
        return when (decksRaw) {
            is ApiResult.Failure -> {
                val message = decksRaw.error.displayMessage
                PptListResult.Failure(
                    message = message,
                    cachedDecks = cachedDecks,
                    cachedAgent = cachedAgent,
                    tokenInvalid = decksRaw.error is ApiError.TokenInvalid,
                )
            }

            is ApiResult.Success -> {
                val decks = PptParsers.parseDeckList(json, decksRaw.value)
                cachedDecks = decks
                PptListResult.Success(decks = decks, agent = cachedAgent)
            }
        }
    }

    override suspend fun loadAgentStatus(): PptAgentStatus? {
        val result = api.get(AGENT_PATH)
        if (result is ApiResult.Success) {
            PptParsers.parseAgentStatus(json, result.value)?.let { cachedAgent = it }
        }
        return cachedAgent
    }

    // ------------------------------------------------------------ 播放清单

    override suspend fun loadPlayer(deckId: String): PptPlayerResult {
        return when (val result = loadDownloadDetail(deckId)) {
            is PptDetailResult.Failure -> PptPlayerResult.Failure(result.message, result.tokenInvalid)
            is PptDetailResult.Success -> {
                val detail = result.detail
                when {
                    detail.isPlayable -> PptPlayerResult.Success(detail)
                    detail.manifest.hasVideo -> PptPlayerResult.Success(detail)
                    detail.isReady -> PptPlayerResult.NotReady()
                    else -> PptPlayerResult.NotReady(PptStatusTexts.text(detail.status, detail.progress, null))
                }
            }
        }
    }

    override suspend fun loadDownloadDetail(deckId: String): PptDetailResult {
        val id = deckId.trim()
        if (id.isEmpty()) return PptDetailResult.Failure("演示参数无效")
        val result = api.get("ppt_decks?select=${DETAIL_SELECT}&id=eq.${encode(id)}")
        return when (result) {
            is ApiResult.Failure -> PptDetailResult.Failure(
                message = result.error.displayMessage,
                tokenInvalid = result.error is ApiError.TokenInvalid,
            )

            is ApiResult.Success -> {
                val detail = PptParsers.parseDeckDetail(json, result.value)
                if (detail == null) {
                    PptDetailResult.Failure("演示数据格式异常，请稍后重试")
                } else {
                    PptDetailResult.Success(detail)
                }
            }
        }
    }

    // ------------------------------------------------------------ 上传（§4.1/§4.2/§4.3）

    override suspend fun upload(input: PptUploadInput): PptUploadResult {
        var deckId: String? = null
        var completed = false
        try {
            val source = input.source
            val fileName = source.name.trim().ifBlank { "演示.pptx" }
            val sourceSize = source.size
            PptLimits.rejectReason(sourceSize, fileName)?.let { reason ->
                _uploadProgress.value = PptUploadProgress(phase = PptUploadPhase.FAILED, message = reason)
                return PptUploadResult.Failure(reason)
            }
            val session = currentSession()
            if (session == null || session.sid.isBlank() || session.token.isBlank() || session.debug) {
                val message = "身份已失效，请重新验证"
                _uploadProgress.value = PptUploadProgress(phase = PptUploadPhase.FAILED, message = message)
                return PptUploadResult.Failure(message, tokenInvalid = true)
            }
            val title = PptLimits.sanitizeTitle(
                input.title.ifBlank { PptLimits.titleFromFileName(fileName) },
            )
            val chunks = PptLimits.chunks(sourceSize, chunkBytes)
            val now = clock()
            val newDeckId = PptIds.newDeckId(now, random)
            deckId = newDeckId

            // §4.1 建行
            _uploadProgress.value = PptUploadProgress(deckId = newDeckId, phase = PptUploadPhase.CREATING, title = title)
            when (val created = createDeck(newDeckId, title, session, fileName, sourceSize, chunks.size, now)) {
                is ApiResult.Failure -> {
                    throw PptUploadFailure(created.error.displayMessage, created.error is ApiError.TokenInvalid)
                }

                is ApiResult.Success -> Unit
            }

            // §4.2 分片
            _uploadProgress.value = PptUploadProgress(deckId = newDeckId, phase = PptUploadPhase.UPLOADING, title = title)
            val total = chunks.size
            coroutineScope {
                val semaphore = Semaphore(concurrency.coerceAtLeast(1))
                val done = AtomicInteger(0)
                chunks.map { chunk ->
                    async(ioDispatcher) {
                        semaphore.withPermit {
                            val bytes = readChunk(source, chunk)
                            uploadChunk(newDeckId, session.sid, chunk.idx, bytes)
                            val finished = done.incrementAndGet()
                            val percent = if (total <= 0) 100 else (finished * 100 / total).coerceIn(0, 100)
                            _uploadProgress.value = PptUploadProgress(
                                deckId = newDeckId,
                                percent = percent,
                                phase = PptUploadPhase.UPLOADING,
                                title = title,
                            )
                        }
                    }
                }.awaitAll()
            }

            // §4.3 提交转换
            _uploadProgress.value = PptUploadProgress(deckId = newDeckId, percent = 100, phase = PptUploadPhase.SUBMITTING, title = title)
            val submit = submitForConversion(newDeckId, chunks.size, sourceSize)
            if (submit is ApiResult.Failure) {
                throw PptUploadFailure(submit.error.displayMessage, submit.error is ApiError.TokenInvalid)
            }
            completed = true
            _uploadProgress.value = PptUploadProgress(deckId = newDeckId, percent = 100, phase = PptUploadPhase.DONE)
            return PptUploadResult.Success(newDeckId)
        } catch (c: CancellationException) {
            // 取消上传：软删已建的行（契约 §8.1「取消即软删」）；不可取消以确保清理。
            withContext(NonCancellable) {
                deckId?.let { id -> runCatching { softDeleteInternal(id) } }
                _uploadProgress.value = PptUploadProgress(
                    deckId = deckId,
                    percent = 0,
                    phase = PptUploadPhase.CANCELLED,
                    message = "已取消上传",
                )
            }
            throw c
        } catch (failure: PptUploadFailure) {
            withContext(NonCancellable) {
                deckId?.let { id -> runCatching { markFailedInternal(id, FAIL_UPLOAD_MESSAGE) } }
            }
            _uploadProgress.value = PptUploadProgress(
                deckId = deckId,
                percent = 0,
                phase = PptUploadPhase.FAILED,
                message = failure.message,
            )
            return PptUploadResult.Failure(
                message = failure.message,
                deckId = deckId,
                tokenInvalid = failure.tokenInvalid,
            )
        } finally {
            if (!completed && _uploadProgress.value.phase == PptUploadPhase.IDLE) {
                _uploadProgress.value = PptUploadProgress()
            }
        }
    }

    /** 读取一片；长度不符视为读取失败（避免把残缺数据 Base64 上传导致转换失败）。 */
    private suspend fun readChunk(source: PptByteSource, chunk: PptLimits.PptChunk): ByteArray {
        val bytes = try {
            withContext(ioDispatcher) { source.read(chunk.offset, chunk.length) }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            throw PptUploadFailure("读取文件失败，请重试")
        }
        if (bytes.size != chunk.length) {
            throw PptUploadFailure("读取文件失败，请重试")
        }
        return bytes
    }

    /** 单片上传：失败重试 2 次（400/1200ms）；token 失效不重试。 */
    private suspend fun uploadChunk(deckId: String, sid: String, idx: Int, bytes: ByteArray) {
        val base64 = Base64.getEncoder().encodeToString(bytes)
        val body = buildJsonArray {
            addJsonObject {
                put("deck_id", deckId)
                put("owner_sid", sid)
                put("idx", idx)
                put("data", base64)
                put("ts", clock())
            }
        }.toString()
        var lastError: ApiError? = null
        for (attempt in 0..retryDelays.size) {
            when (val result = api.post(CHUNKS_PATH, body, prefer = PREFER_UPSERT_CHUNK)) {
                is ApiResult.Success -> return
                is ApiResult.Failure -> {
                    lastError = result.error
                    if (result.error is ApiError.TokenInvalid) break
                    if (attempt < retryDelays.size) delay(retryDelays[attempt])
                }
            }
        }
        val tokenInvalid = lastError is ApiError.TokenInvalid
        throw PptUploadFailure(
            message = if (tokenInvalid) "身份已失效，请重新验证" else FAIL_UPLOAD_MESSAGE,
            tokenInvalid = tokenInvalid,
        )
    }

    private suspend fun createDeck(
        deckId: String,
        title: String,
        session: Session,
        fileName: String,
        sourceSize: Long,
        chunkCount: Int,
        now: Long,
    ): ApiResult<Unit> {
        val body = buildJsonArray {
            addJsonObject {
                put("id", deckId)
                put("title", title)
                put("owner_sid", session.sid)
                put("owner_name", session.name)
                put("client", "android")
                put("status", PptDeck.STATUS_UPLOADING)
                put("source_name", fileName)
                put("source_size", sourceSize)
                put("chunk_count", chunkCount)
                put("aspect", "16:9")
                put("created_at", now)
                put("updated_at", now)
                put("deleted", false)
            }
        }.toString()
        return api.post(DECKS_PATH, body, prefer = PREFER_REPRESENTATION).mapUnit()
    }

    private suspend fun submitForConversion(deckId: String, chunkCount: Int, sourceSize: Long): ApiResult<Unit> {
        val body = buildJsonObject {
            put("status", PptDeck.STATUS_QUEUED)
            put("chunk_count", chunkCount)
            put("source_size", sourceSize)
            put("updated_at", clock())
        }.toString()
        return api.patch("ppt_decks?id=eq.${encode(deckId)}", body, prefer = PREFER_REPRESENTATION).mapUnit()
    }

    // ------------------------------------------------------------ 软删 / 重试（§4.6）

    override suspend fun softDelete(deckId: String): ApiResult<Unit> = softDeleteInternal(deckId)

    override suspend fun retryConversion(deckId: String): ApiResult<Unit> {
        val id = deckId.trim()
        if (id.isEmpty()) return ApiResult.Failure(ApiError.Config("演示参数无效"))
        val body = buildJsonObject {
            put("status", PptDeck.STATUS_QUEUED)
            put("progress", 0)
            put("error", "")
            put("updated_at", clock())
        }.toString()
        return api.patch("ppt_decks?id=eq.${encode(id)}", body, prefer = PREFER_REPRESENTATION).mapUnit()
    }

    private suspend fun softDeleteInternal(deckId: String): ApiResult<Unit> {
        val body = buildJsonObject {
            put("deleted", true)
            put("updated_at", clock())
        }.toString()
        return api.patch("ppt_decks?id=eq.${encode(deckId)}", body, prefer = PREFER_REPRESENTATION).mapUnit()
    }

    private suspend fun markFailedInternal(deckId: String, error: String) {
        val body = buildJsonObject {
            put("status", PptDeck.STATUS_FAILED)
            put("error", error.take(PptParsers.MAX_ERROR_CHARS))
            put("updated_at", clock())
        }.toString()
        api.patch("ppt_decks?id=eq.${encode(deckId)}", body, prefer = PREFER_REPRESENTATION)
    }

    private fun ApiResult<String>.mapUnit(): ApiResult<Unit> = when (this) {
        is ApiResult.Success -> ApiResult.Success(Unit)
        is ApiResult.Failure -> this
    }

    /** 供 UI：判断失败是否 token 失效（回身份页由全局 onTokenInvalid 处理）。 */
    fun isTokenInvalid(result: ApiResult<*>): Boolean = result.isTokenInvalid()

    companion object {
        const val DECKS_PATH: String = "ppt_decks"
        const val CHUNKS_PATH: String = "ppt_upload_chunks"
        const val AGENT_PATH: String = "site_assets?id=eq.ppt_agent&select=body,updated_at"

        /**
         * §4.4 列表只查轻字段（不再带完整 manifest）。
         * v2.0 §12.4.1 列表小标签需要知道 `files.compat/pdf`：用 PostgREST jsonb 路径只取 `manifest->files`
         * （几百字节的轻量 JSON 子对象，不是完整 manifest）。
         */
        const val LIST_SELECT: String =
            "id,title,owner_sid,owner_name,status,progress,error,slide_count,step_count,aspect," +
                "cover_url,source_name,source_size,created_at,converted_at,client,manifest->files"

        val LIST_PATH: String =
            "ppt_decks?select=$LIST_SELECT&deleted=eq.false&order=created_at.desc&limit=100"

        /** v2.0 §12.4.1 下载详情字段（含 manifest.files + 状态/进度/封面）。 */
        const val DETAIL_SELECT: String =
            "manifest,title,status,progress,error,owner_name,slide_count,step_count,aspect," +
                "cover_url,source_name,source_key,source_size,converted_at"

        /** §4.5 播放清单字段（旧 deck 在线预览；保留常量兼容旧断言）。 */
        const val PLAYER_SELECT: String = DETAIL_SELECT

        const val PREFER_REPRESENTATION: String = "return=representation"
        const val PREFER_UPSERT_CHUNK: String = "resolution=merge-duplicates,return=minimal"

        /** 分片失败后写入云端的中文错误（契约 §4.2，≤300 字）。 */
        const val FAIL_UPLOAD_MESSAGE: String = "上传中断，请重试"

        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    }
}
