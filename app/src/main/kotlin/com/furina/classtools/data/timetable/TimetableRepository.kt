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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 课表仓库（契约：docs/api/ENDPOINTS.md E24 + docs/BACKEND_CONTRACT.md §7.4）。
 *
 * 数据源优先级（2026-11-08 课表登录）：**个人 ttLocal > 公共 `site_assets?id=eq.tt` 快照 > 本机缓存**。
 * - 个人课表 = userdata v6 `tt.ttLocal`（`{c,t}`，与 web/mp 同字段；由 [TimetablePersonalStore] 读写）；
 * - 公共快照 body 兼容 jsonb 对象/JSON 字符串；6h TTL；失败回退本机缓存（旧域名自 2026-11-07 起不再请求）。
 */
interface TimetableRepository {
    /** 拉取课表（TTL 内直接返回缓存；有个人课表时永远优先返回个人课表）。 */
    suspend fun load(force: Boolean = false): TtLoadResult

    /** 只读本机缓存/个人课表（不发起请求；预览/离线首帧用）。 */
    suspend fun cached(): TtSnapshot?

    suspend fun clear()

    /** 只读个人课表（ttLocal）；无 → null。默认 null 保持既有 Fake 实现兼容。 */
    suspend fun personal(): TtSnapshot? = null

    /** 保存个人课表到 userdata v6 `tt.ttLocal`（默认空实现，供预览/测试 Fake 复用）。 */
    suspend fun savePersonal(snapshot: TtSnapshot) = Unit

    /** 清除个人课表（回退公共课表）。 */
    suspend fun clearPersonal() = Unit
}

class DefaultTimetableRepository(
    private val api: ApiClient,
    private val cache: FileTimetableCache,
    private val personalStore: TimetablePersonalStore? = null,
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
        // 0) 个人课表（登录教务产生）永远优先于公共快照与缓存。
        val personal = personalSnapshot()
        if (personal != null) {
            return TtLoadResult.Success(
                snapshot = personal,
                fromCache = false,
                fetchedAt = now,
                source = TtSource.PERSONAL,
            )
        }
        // 1) 内存 L1（TTL 内）
        val memory = memorySnapshot
        if (!force && memory != null && now - memorySavedAt < ttlMs) {
            return TtLoadResult.Success(memory, fromCache = true, fetchedAt = memorySavedAt, source = TtSource.CACHE)
        }
        // 2) 落盘缓存（TTL 内直接用；过期但请求失败时作为兜底）
        val cached = cache.read()?.also { file ->
            memorySnapshot = file.snapshot
            memorySavedAt = file.savedAt
        }
        if (!force && cached != null && now - cached.savedAt < ttlMs) {
            return TtLoadResult.Success(
                cached.snapshot,
                fromCache = true,
                fetchedAt = cached.savedAt,
                source = TtSource.CACHE,
            )
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
                    TtLoadResult.Success(snapshot, fromCache = false, fetchedAt = now, source = TtSource.PUBLIC)
                }

                TtParseOutcome.Empty -> cachedFallback(cached, MSG_EMPTY, TtFailureKind.EMPTY)
                TtParseOutcome.Format -> cachedFallback(cached, MSG_FORMAT, TtFailureKind.FORMAT)
            }
        }
    }

    private suspend fun personalSnapshot(): TtSnapshot? =
        runCatching { personalStore?.read() }.getOrNull()?.takeIf { it.c.isNotEmpty() }

    private fun cachedFallback(
        cached: FileTimetableCache.Cached?,
        message: String,
        kind: TtFailureKind,
    ): TtLoadResult =
        if (cached != null) {
            TtLoadResult.Success(
                snapshot = cached.snapshot,
                fromCache = true,
                fetchedAt = cached.savedAt,
                source = TtSource.CACHE,
            )
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
            personalSnapshot()?.let { return it }
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

    override suspend fun personal(): TtSnapshot? = personalSnapshot()

    override suspend fun savePersonal(snapshot: TtSnapshot) {
        personalStore?.save(snapshot)
    }

    override suspend fun clearPersonal() {
        personalStore?.clear()
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
            return parseBodyObject(json, body, row["meta"].asObjectOrNull())
        }

        /**
         * 解析「课表 body」对象（`{c:[course],t:"YYYY-MM-DD",meta:{...}}`）。
         *
         * 公共快照 body、userdata `tt.ttLocal`、登录 RPC `result` 共用同一结构/校验，
         * 区别只在 meta 来源（公共快照的 meta 可能在行上、也可能在 body 内）。
         */
        fun parseBodyObject(
            json: Json,
            body: JsonObject,
            rowMeta: JsonObject? = null,
        ): TtParseOutcome {
            val coursesArrayElement = body["c"] ?: return TtParseOutcome.Empty
            val coursesArray = coursesArrayElement as? JsonArray ?: return TtParseOutcome.Format
            if (coursesArray.isEmpty()) return TtParseOutcome.Empty
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

        /** 直接解析课表 body（ttLocal / 登录 result 用）：成功返回快照，空/坏数据返回 null。 */
        fun parseSnapshotBody(json: Json, body: JsonObject): TtSnapshot? =
            (parseBodyObject(json, body) as? TtParseOutcome.Success)?.snapshot

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
