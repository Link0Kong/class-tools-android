package com.furina.classtools.data.homework

import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.asObjectOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull

/** 作业加载结果（失败也尽量带回本机缓存，保证离线可用）。 */
sealed interface HomeworkLoadResult {
    data class Success(
        val rows: List<HomeworkRow>,
        /** true = 本机缓存（TTL 内或网络失败降级）。 */
        val fromCache: Boolean,
        val refreshError: String? = null,
    ) : HomeworkLoadResult

    data class Failure(val message: String) : HomeworkLoadResult
}

/**
 * 作业仓库（只读）：`GET homework?select=*&order=ts.asc&archived=eq.false`。
 *
 * 契约：`docs/api/ENDPOINTS.md` E4；TTL 10 分钟（`WEB:3173`）。
 * 空数组是**合法结果**（作业列表本来可以为空），不当失败处理。
 * 发布/软删接口本轮**不实现写**（`notices`/`homework` 写权限也依赖发布者/管理员语义，UI 本轮只读）。
 */
interface HomeworkRepository {
    /** @param force true = 跳过 TTL 强制刷新（下拉刷新/重试）。 */
    suspend fun load(force: Boolean = false): HomeworkLoadResult

    /** 只读本机内存缓存，不发请求。 */
    suspend fun cached(): List<HomeworkRow>

    /** null 表示尚无缓存。已成功同步过的空列表也是可用缓存。 */
    suspend fun cachedSnapshot(): List<HomeworkRow>? = cached().takeIf { it.isNotEmpty() }

    /** 本轮只读：返回 false（保留接口给后续发布功能）。 */
    suspend fun publish(row: HomeworkRow): Boolean

    /** 本轮只读：返回 false（软删 = PATCH archived=true，后续增量实现）。 */
    suspend fun archive(id: String): Boolean
}

class DefaultHomeworkRepository(
    private val api: ApiClient,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val json: Json = NetJson,
) : HomeworkRepository {

    private val mutex = Mutex()
    private var cache: List<HomeworkRow> = emptyList()
    private var lastFetchAt: Long = 0L
    private var loadedOnce = false

    override suspend fun load(force: Boolean): HomeworkLoadResult = mutex.withLock {
        if (!force && loadedOnce && clock() - lastFetchAt < ttlMs) {
            return@withLock HomeworkLoadResult.Success(cache, fromCache = true)
        }
        return@withLock when (val result = api.get(HOMEWORK_PATH)) {
            is ApiResult.Failure -> {
                fallback(result.error.displayMessage)
            }
            is ApiResult.Success -> {
                val rows = parseRows(json, result.value)
                if (rows == null) {
                    fallback("作业数据格式异常")
                } else {
                    cache = rows
                    loadedOnce = true
                    lastFetchAt = clock()
                    HomeworkLoadResult.Success(rows, fromCache = false)
                }
            }
        }
    }

    override suspend fun cached(): List<HomeworkRow> = mutex.withLock { cache }

    override suspend fun cachedSnapshot(): List<HomeworkRow>? = mutex.withLock { cache.takeIf { loadedOnce } }

    private fun fallback(message: String): HomeworkLoadResult = if (loadedOnce) {
        HomeworkLoadResult.Success(cache, fromCache = true, refreshError = message)
    } else {
        HomeworkLoadResult.Failure(message)
    }

    override suspend fun publish(row: HomeworkRow): Boolean = false

    override suspend fun archive(id: String): Boolean = false

    companion object {
        const val HOMEWORK_PATH: String = "homework?select=*&order=ts.asc&archived=eq.false"
        const val DEFAULT_TTL_MS: Long = 10L * 60 * 1000

        /** 解析作业数组；不是数组 → null（调用方按格式异常/缓存降级）。坏行跳过。 */
        fun parseRows(json: Json, raw: String): List<HomeworkRow>? {
            val element = parseJsonOrNull(json, raw) ?: return null
            val array = element as? JsonArray ?: return null
            val rows = ArrayList<HomeworkRow>(array.size)
            for (item in array) {
                val obj = item.asObjectOrNull() ?: continue
                val id = (obj["id"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                    ?.takeIf { it.isNotBlank() } ?: continue
                val decoded = try {
                    json.decodeFromJsonElement(HomeworkRow.serializer(), obj)
                } catch (_: Throwable) {
                    continue
                }
                rows.add(decoded.copy(id = id))
            }
            return rows
        }
    }
}
