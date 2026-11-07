package com.furina.classtools.data.timetable

import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiError
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.asIntOrNull
import com.furina.classtools.data.network.asLongOrNull
import com.furina.classtools.data.network.asObjectOrNull
import com.furina.classtools.data.network.asStringOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 课表仓库（契约：docs/api/ENDPOINTS.md E24 + docs/BACKEND_CONTRACT.md §7.4）。
 *
 * 数据源优先级：CloudBase `site_assets?id=eq.tt`（body 兼容 jsonb 对象/JSON 字符串）
 * → 失败回退本机缓存（旧域名 `tool.furina.xx/tt.json` 自 2026-11-07 起 DNS 已暂停，不再请求）。
 *
 * 缓存：内存 L1 + [FileTimetableCache] 落盘，TTL 6h；失败时即使缓存过期也作为兜底返回（fromCache=true）。
 */
interface TimetableRepository {
    /** 拉取课表（TTL 内直接返回缓存）。 */
    suspend fun load(force: Boolean = false): TtLoadResult

    /** 只读本机缓存（不发起请求；预览/离线首帧用）。 */
    suspend fun cached(): TtSnapshot?

    suspend fun clear()
}

class DefaultTimetableRepository(
    private val api: ApiClient,
    private val cache: FileTimetableCache,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val json: Json = NetJson,
) : TimetableRepository {

    private val mutex = Mutex()
    private var memorySnapshot: TtSnapshot? = null
    private var memorySavedAt: Long = 0L

    override suspend fun load(force: Boolean): TtLoadResult {
        // Mutex.withLock 的 action 不是 suspend，这里手动 lock/unlock 以在锁内调挂起请求。
        mutex.lock()
        try {
            return loadLocked(force)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun loadLocked(force: Boolean): TtLoadResult {
        val now = clock()
        // 1) 内存 L1（TTL 内）
        val memory = memorySnapshot
        if (!force && memory != null && now - memorySavedAt < ttlMs) {
            return TtLoadResult.Success(memory, fromCache = true, fetchedAt = memorySavedAt)
        }
        // 2) 落盘缓存（TTL 内直接用；过期但请求失败时作为兜底）
        val cached = cache.read()?.also { file ->
            memorySnapshot = file.snapshot
            memorySavedAt = file.savedAt
        }
        if (!force && cached != null && now - cached.savedAt < ttlMs) {
            return TtLoadResult.Success(cached.snapshot, fromCache = true, fetchedAt = cached.savedAt)
        }

        // 3) 云端请求
        return when (val result = api.get(PATH)) {
            is ApiResult.Failure -> {
                val kind = failureKindOf(result.error)
                cachedFallback(cached, result.error.displayMessage, kind)
            }

            is ApiResult.Success -> when (val outcome = parse(json, result.value)) {
                is TtParseOutcome.Success -> {
                    val snapshot = outcome.snapshot
                    memorySnapshot = snapshot
                    memorySavedAt = now
                    cache.write(snapshot, now)
                    TtLoadResult.Success(snapshot, fromCache = false, fetchedAt = now)
                }

                TtParseOutcome.Empty -> cachedFallback(cached, MSG_EMPTY, TtFailureKind.EMPTY)
                TtParseOutcome.Format -> cachedFallback(cached, MSG_FORMAT, TtFailureKind.FORMAT)
            }
        }
    }

    private fun cachedFallback(
        cached: FileTimetableCache.Cached?,
        message: String,
        kind: TtFailureKind,
    ): TtLoadResult =
        if (cached != null) {
            TtLoadResult.Success(cached.snapshot, fromCache = true, fetchedAt = cached.savedAt)
        } else {
            TtLoadResult.Failure(message, kind)
        }

    /** ApiError → 课表失败语义（Debug 无 token 时也要显示「暂无课表数据」而不是格式异常）。 */
    private fun failureKindOf(error: ApiError): TtFailureKind = when (error) {
        is ApiError.Network -> TtFailureKind.NETWORK
        is ApiError.TokenInvalid -> TtFailureKind.NO_TOKEN
        is ApiError.Config -> TtFailureKind.NO_TOKEN
        is ApiError.Parse -> TtFailureKind.FORMAT
        is ApiError.Http -> TtFailureKind.NETWORK
    }

    override suspend fun cached(): TtSnapshot? {
        mutex.lock()
        try {
            memorySnapshot?.let { return it }
            return cache.read()?.also { file ->
                memorySnapshot = file.snapshot
                memorySavedAt = file.savedAt
            }?.snapshot
        } finally {
            mutex.unlock()
        }
    }

    override suspend fun clear() {
        mutex.lock()
        try {
            memorySnapshot = null
            memorySavedAt = 0L
            cache.clear()
        } finally {
            mutex.unlock()
        }
    }

    companion object {
        const val PATH: String = "site_assets?select=body,meta,updated_at&id=eq.tt"
        const val DEFAULT_TTL_MS: Long = 6L * 60 * 60 * 1000

        /** 空数据文案（UI 三态共用，避免中英文/标点不一致）。 */
        const val MSG_EMPTY: String = "暂无课表数据"
        const val MSG_FORMAT: String = "课表数据格式异常"

        /**
         * 解析 E24 响应（三态语义）：
         * - [TtParseOutcome.Empty]：请求成功但没有数据 —— 空字符串 / `[]` / body 缺失 / `c` 缺失或空数组；
         * - [TtParseOutcome.Format]：响应结构不符合契约 —— 坏 JSON / body 不是对象 / `c` 不是数组 / 课程全部非法；
         * - [TtParseOutcome.Success]：解析出至少一门课。
         */
        fun parse(json: Json, raw: String): TtParseOutcome {
            val text = raw.trim()
            if (text.isEmpty() || text == "null") return TtParseOutcome.Empty
            val root = parseJsonOrNull(json, text) ?: return TtParseOutcome.Format
            val row: JsonObject = when (root) {
                is JsonArray -> {
                    if (root.isEmpty()) return TtParseOutcome.Empty
                    root.firstOrNull().asObjectOrNull() ?: return TtParseOutcome.Format
                }
                is JsonObject -> root
                else -> return TtParseOutcome.Format
            }
            val bodyElement: JsonElement = row["body"] ?: return TtParseOutcome.Empty
            val body: JsonObject = when (bodyElement) {
                is JsonObject -> bodyElement
                is JsonPrimitive -> {
                    val bodyText = bodyElement.contentOrNullSafe()
                    if (bodyText.isNullOrBlank()) return TtParseOutcome.Empty
                    parseJsonOrNull(json, bodyText).asObjectOrNull() ?: return TtParseOutcome.Format
                }
                else -> return TtParseOutcome.Format
            }
            val coursesArrayElement = body["c"] ?: return TtParseOutcome.Empty
            val coursesArray = coursesArrayElement as? JsonArray ?: return TtParseOutcome.Format
            if (coursesArray.isEmpty()) return TtParseOutcome.Empty
            val rowMeta = row["meta"].asObjectOrNull()
            val bodyMeta = body["meta"].asObjectOrNull()

            fun metaString(key: String): String =
                bodyMeta?.get(key).asStringOrNull()
                    ?: rowMeta?.get(key).asStringOrNull()
                    ?: ""

            val termStart = metaString("termStart").ifBlank {
                body["t"].asStringOrNull().orEmpty()
            }
            val meta = TtMeta(
                source = metaString("source"),
                fetchedAt = bodyMeta?.get("fetchedAt").asLongOrNull()
                    ?: rowMeta?.get("fetchedAt").asLongOrNull() ?: 0L,
                count = bodyMeta?.get("count").asIntOrNull()
                    ?: rowMeta?.get("count").asIntOrNull() ?: coursesArray.size,
                termStart = termStart,
                syncedAt = metaString("syncedAt"),
            )
            val courses = ArrayList<Course>(coursesArray.size)
            for (item in coursesArray) {
                val obj = item.asObjectOrNull() ?: continue
                val name = obj["name"].asStringOrNull()?.trim().orEmpty()
                if (name.isEmpty()) continue
                val day = (obj["day"].asIntOrNull() ?: 0).coerceIn(0, 6)
                val start = (obj["start"].asIntOrNull() ?: 1).coerceIn(1, TtPeriods.COUNT)
                val end = (obj["end"].asIntOrNull() ?: start).coerceIn(start, TtPeriods.COUNT)
                courses.add(
                    Course(
                        name = name,
                        teacher = obj["teacher"].asStringOrNull()?.trim().orEmpty(),
                        room = obj["room"].asStringOrNull()?.trim().orEmpty(),
                        day = day,
                        start = start,
                        end = end,
                        weeks = obj["weeks"].asStringOrNull()?.trim().orEmpty(),
                        parity = obj["parity"].asStringOrNull()?.trim()
                            ?.takeIf { it == "odd" || it == "even" } ?: "all",
                    ),
                )
            }
            if (courses.isEmpty()) return TtParseOutcome.Format
            return TtParseOutcome.Success(
                TtSnapshot(
                    c = courses.sortedWith(compareBy({ it.day }, { it.start }, { it.name })),
                    t = body["t"].asStringOrNull()?.trim().orEmpty(),
                    meta = meta,
                ),
            )
        }

        /** 兼容旧调用方：只要成功快照（空数据/格式错误都返回 null）。 */
        fun parseSnapshot(json: Json, raw: String): TtSnapshot? =
            (parse(json, raw) as? TtParseOutcome.Success)?.snapshot

        private fun JsonPrimitive.contentOrNullSafe(): String? =
            if (isString) content else null

    }
}

/** 课表响应解析三态（空数据 / 格式错误 / 成功）。 */
sealed interface TtParseOutcome {
    data class Success(val snapshot: TtSnapshot) : TtParseOutcome

    /** 请求成功但没有数据：空响应、`c: []`、body 缺失。 */
    data object Empty : TtParseOutcome

    /** 响应结构不符合契约：坏 JSON、body 非对象、c 非数组、课程全部非法。 */
    data object Format : TtParseOutcome
}


