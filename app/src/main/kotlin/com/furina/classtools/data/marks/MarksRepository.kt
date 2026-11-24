package com.furina.classtools.data.marks

import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiError
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.asObjectOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import java.net.URLEncoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

/** marks 本机缓存/待补发（接口化便于单测与后续落盘）。 */
interface MarksLocalStore {
    suspend fun readCache(): List<MarkRow>
    suspend fun writeCache(rows: List<MarkRow>)
    suspend fun readPending(): List<MarkRow>
    suspend fun writePending(rows: List<MarkRow>)
}

class InMemoryMarksLocalStore : MarksLocalStore {
    private var cache: List<MarkRow> = emptyList()
    private var pending: List<MarkRow> = emptyList()

    override suspend fun readCache(): List<MarkRow> = cache
    override suspend fun writeCache(rows: List<MarkRow>) {
        cache = rows
    }

    override suspend fun readPending(): List<MarkRow> = pending
    override suspend fun writePending(rows: List<MarkRow>) {
        pending = rows
    }
}

sealed interface MarksUpdateResult {
    data object Success : MarksUpdateResult
    data class Failure(val error: ApiError) : MarksUpdateResult
}

/**
 * marks 仓库（契约 §5）：`marks` 是「作业完成 / 通知置顶 / 通知已处理」的跨端唯一真源。
 *
 * - 读：`GET marks?select=*&seat=eq.<sid>`，TTL 2 分钟；`Flow` 暴露当前快照；
 * - 写：`POST marks` + `Prefer: return=representation,resolution=merge-duplicates`（upsert）；
 *   取消 = `val:false`，**不做 DELETE**（anon 无删除权限）；
 * - 失败：写入进本机 pending 队列，[flushPending] 在网络恢复/刷新后补发；
 * - 拉回后 pending 覆盖云端旧值，避免刚点的状态被回滚（契约 §5 需求 16）。
 */
interface MarksRepository {
    val marks: StateFlow<MarksSnapshot>

    suspend fun refresh(force: Boolean = false): ApiResult<MarksSnapshot>

    suspend fun setMark(kind: MarkKind, ref: String, value: Boolean): MarksUpdateResult

    /** 补发本机失败写入；返回成功条数。 */
    suspend fun flushPending(): Int

    suspend fun pendingCount(): Int
}

class DefaultMarksRepository(
    private val api: ApiClient,
    private val currentSid: () -> String?,
    private val local: MarksLocalStore = InMemoryMarksLocalStore(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val json: Json = NetJson,
) : MarksRepository {

    private val _marks = MutableStateFlow(MarksSnapshot())
    override val marks: StateFlow<MarksSnapshot> = _marks.asStateFlow()

    private val mutex = Mutex()
    private var cachedSid: String? = null
    private var lastFetchAt = 0L

    override suspend fun refresh(force: Boolean): ApiResult<MarksSnapshot> {
        val sid = sidOrNull() ?: return ApiResult.Failure(ApiError.Config("请先完成身份验证"))
        mutex.lock()
        try {
            ensureCacheLoaded(sid)
            if (!force && lastFetchAt > 0 && clock() - lastFetchAt < ttlMs) {
                return ApiResult.Success(_marks.value)
            }
            val path = "marks?select=*&seat=eq." + URLEncoder.encode(sid, "UTF-8")
            return when (val result = api.get(path)) {
                is ApiResult.Failure -> ApiResult.Failure(result.error)
                is ApiResult.Success -> {
                    val rows = parseRows(json, result.value)
                        ?: return ApiResult.Failure(ApiError.Parse("marks 响应不是合法数组"))
                    local.writeCache(local.readCache().filterNot { it.seat == sid } + rows)
                    lastFetchAt = clock()
                    val snapshot = MarksSnapshot().replaceRows(rows).applyPending(local.readPending().filter { it.seat == sid })
                    _marks.value = snapshot
                    ApiResult.Success(snapshot)
                }
            }
        } finally {
            mutex.unlock()
        }
    }

    override suspend fun setMark(
        kind: MarkKind,
        ref: String,
        value: Boolean,
    ): MarksUpdateResult {
        val sid = sidOrNull() ?: return MarksUpdateResult.Failure(ApiError.Config("请先完成身份验证"))
        if (ref.isBlank()) return MarksUpdateResult.Failure(ApiError.Config("ref 为空"))
        mutex.lock()
        try {
            ensureCacheLoaded(sid)
            val row = MarkRow(
                id = MarkRow.idOf(sid, kind, ref),
                seat = sid,
                kind = kind.wire,
                ref = ref,
                value = value,
                ts = clock(),
            )
            // 先本地乐观更新（UI 立即生效；失败也有本机状态）。
            _marks.value = _marks.value.with(kind, ref, value)
            local.writeCache(local.readCache().filterNot { it.id == row.id } + row)

            val body = json.encodeToString(ListSerializer(MarkRow.serializer()), listOf(row))
            return when (val result = api.post(path = MARKS_PATH, body = body, prefer = UPSERT_PREFER)) {
                is ApiResult.Failure -> {
                    local.writePending(dropPending(local.readPending(), row.id) + row)
                    MarksUpdateResult.Failure(result.error)
                }
                is ApiResult.Success -> {
                    local.writePending(dropPending(local.readPending(), row.id))
                    MarksUpdateResult.Success
                }
            }
        } finally {
            mutex.unlock()
        }
    }

    override suspend fun flushPending(): Int {
        mutex.lock()
        try {
            val pending = local.readPending()
            if (pending.isEmpty()) return 0
            var success = 0
            val remaining = ArrayList<MarkRow>()
            for (row in pending) {
                val body = json.encodeToString(ListSerializer(MarkRow.serializer()), listOf(row))
                when (api.post(path = MARKS_PATH, body = body, prefer = UPSERT_PREFER)) {
                    is ApiResult.Success -> success += 1
                    is ApiResult.Failure -> remaining.add(row)
                }
            }
            local.writePending(remaining)
            if (success > 0) lastFetchAt = 0L
            return success
        } finally {
            mutex.unlock()
        }
    }

    override suspend fun pendingCount(): Int = local.readPending().size

    // ------------------------------------------------------------ 内部

    private suspend fun ensureCacheLoaded(sid: String) {
        if (cachedSid == sid) return
        val cached = local.readCache().filter { it.seat == sid }
        val pending = local.readPending().filter { it.seat == sid }
        _marks.value = MarksSnapshot().replaceRows(cached).applyPending(pending)
        cachedSid = sid
        lastFetchAt = 0L
    }

    private fun MarksSnapshot.applyPending(pending: List<MarkRow>): MarksSnapshot {
        var snapshot = this
        for (row in pending) {
            val kind = MarkKind.fromWire(row.kind) ?: continue
            snapshot = snapshot.with(kind, row.ref, row.value)
        }
        return snapshot
    }

    private fun dropPending(pending: List<MarkRow>, id: String): List<MarkRow> =
        pending.filterNot { it.id == id }

    private fun sidOrNull(): String? = currentSid()?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        const val MARKS_PATH: String = "marks"
        const val UPSERT_PREFER: String = "return=representation,resolution=merge-duplicates"
        const val DEFAULT_TTL_MS: Long = 2L * 60 * 1000

        /** 解析 `GET marks` 数组；不是数组 → null（调用方按 Parse 错误处理）。 */
        fun parseRows(json: Json, raw: String): List<MarkRow>? {
            val element = parseJsonOrNull(json, raw) ?: return null
            val array = element as? JsonArray ?: return null
            val rows = ArrayList<MarkRow>(array.size)
            for (item in array) {
                val obj = item.asObjectOrNull() ?: continue
                val decoded = try {
                    json.decodeFromJsonElement(MarkRow.serializer(), obj)
                } catch (_: Throwable) {
                    continue
                }
                if (decoded.ref.isNotBlank()) rows.add(decoded)
            }
            return rows
        }
    }
}
