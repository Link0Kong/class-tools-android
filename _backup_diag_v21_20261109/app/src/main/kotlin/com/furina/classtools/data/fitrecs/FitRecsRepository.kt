package com.furina.classtools.data.fitrecs

import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.asObjectOrNull
import com.furina.classtools.data.network.asStringOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import com.furina.classtools.features.fit.FitCodec
import com.furina.classtools.features.fit.FitRecord
import java.net.URLEncoder
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * 体测历史云端行（`public.fitrecs`，契约：`docs/api/ENDPOINTS.md` E14/E15/E16）。
 *
 * `data` 是 **JSON 字符串**（需二次 parse，见 [FitCodec.recordFromData]）；
 * `arch=true` 为软删除（客户端 PATCH，不发 DELETE）。
 */
data class FitRecsRow(
    val id: String,
    val acc: String = "",
    val data: String = "",
    val arch: Boolean = false,
    val ts: Long = 0L,
) {
    /** 解析后的记录；坏 JSON/缺 id → null（调用方忽略该行）。 */
    val record: FitRecord? get() = FitCodec.recordFromData(data)
}

/** 体测历史加载结果。 */
sealed interface FitRecsLoadResult {
    data class Success(
        val records: List<FitRecord>,
        /** true = TTL 内缓存或网络失败降级。 */
        val fromCache: Boolean,
    ) : FitRecsLoadResult

    data class Failure(val message: String) : FitRecsLoadResult
}

/** 体测历史写入结果（synced=false 表示已写本机 pending，等待补发）。 */
sealed interface FitRecsWriteResult {
    data class Success(val synced: Boolean = true) : FitRecsWriteResult
    data class Failure(val message: String) : FitRecsWriteResult
}

/**
 * 体测历史仓库（E14/E15/E16）：
 * - 读：`GET fitrecs?select=*&acc=eq.<sid>&arch=eq.false&order=ts.desc`，2min TTL；
 * - 写：`POST fitrecs` + `Prefer: return=representation,resolution=merge-duplicates`（按 id upsert）；
 * - 最多保留 2 条：保存后多余的旧记录 PATCH `arch=true` 软删；
 * - 离线：新记录进 pending 队列，下次 [load]/[flushPending] 补发；加载失败降级本机缓存；
 * - `data` JSON 字符串二次 parse 成 [FitRecord]；坏行跳过。
 */
interface FitRecsRepository {
    /** @param force true = 跳过 TTL（并优先补发 pending）。 */
    suspend fun load(force: Boolean = false): FitRecsLoadResult

    /**
     * 保存一条记录。
     * @param kept 保存后保留的 ≤2 条（含 [record]，用于更新缓存 / 补发）
     * @param dropped 被挤出 2 条之外的旧记录（PATCH arch=true 软删）
     */
    suspend fun save(
        record: FitRecord,
        kept: List<FitRecord>,
        dropped: List<FitRecord> = emptyList(),
    ): FitRecsWriteResult

    /** 历史改名：按 id upsert 替换 data（不改 arch/ts）。 */
    suspend fun rename(record: FitRecord): FitRecsWriteResult

    /** 软删：PATCH `fitrecs?id=eq.<id>` `{"arch":true}`。 */
    suspend fun archive(id: String): FitRecsWriteResult

    /** 待补发数量（保存 + 软删）。 */
    suspend fun pendingCount(): Int

    /** 补发 pending；返回成功条数。 */
    suspend fun flushPending(): Int
}

class DefaultFitRecsRepository(
    private val api: ApiClient,
    private val currentSid: () -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ttlMs: Long = DEFAULT_TTL_MS,
) : FitRecsRepository {

    private val mutex = Mutex()
    private var cache: List<FitRecord> = emptyList()
    private var loadedOnce = false
    private var lastFetchAt: Long = 0L
    private val pendingSaves = LinkedHashMap<String, FitRecord>()
    private val pendingArchives = LinkedHashSet<String>()

    // Mutex.withLock 的 action 不是 suspend，锁内需要挂起请求 → 手动 lock/unlock（同项目其它仓库）。

    override suspend fun load(force: Boolean): FitRecsLoadResult {
        mutex.lock()
        try {
            return loadLocked(force)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun loadLocked(force: Boolean): FitRecsLoadResult {
        if (!force && loadedOnce && clock() - lastFetchAt < ttlMs) {
            return FitRecsLoadResult.Success(cache, fromCache = true)
        }
        val sid = currentSid()?.trim().orEmpty()
        if (sid.isEmpty()) {
            return if (cache.isNotEmpty()) {
                FitRecsLoadResult.Success(cache, fromCache = true)
            } else {
                FitRecsLoadResult.Failure("请先完成身份验证")
            }
        }
        flushPendingLocked()
        return when (val result = api.get(loadPath(sid))) {
            is ApiResult.Failure -> {
                if (cache.isNotEmpty()) {
                    FitRecsLoadResult.Success(cache, fromCache = true)
                } else {
                    FitRecsLoadResult.Failure(result.error.displayMessage)
                }
            }

            is ApiResult.Success -> {
                val rows = parseRows(NetJson, result.value)
                if (rows == null) {
                    if (cache.isNotEmpty()) {
                        FitRecsLoadResult.Success(cache, fromCache = true)
                    } else {
                        FitRecsLoadResult.Failure("体测历史数据格式异常")
                    }
                } else {
                    val remote = recordsFromRows(rows)
                    val merged = mergeRecords(remote, pendingSaves.values.toList())
                    cache = merged
                    loadedOnce = true
                    lastFetchAt = clock()
                    FitRecsLoadResult.Success(merged, fromCache = false)
                }
            }
        }
    }

    override suspend fun save(
        record: FitRecord,
        kept: List<FitRecord>,
        dropped: List<FitRecord>,
    ): FitRecsWriteResult {
        mutex.lock()
        try {
            if (record.id.isBlank()) return FitRecsWriteResult.Failure("记录缺少 id")
            val sid = currentSid()?.trim().orEmpty()
            if (sid.isEmpty()) return FitRecsWriteResult.Failure("请先完成身份验证")
            cache = sanitize(kept)
            val body = buildUpsertBody(sid, listOf(record))
            val synced = when (api.post(FITRECS_PATH, body, prefer = PREFER_UPSERT)) {
                is ApiResult.Success -> {
                    pendingSaves.remove(record.id)
                    true
                }

                is ApiResult.Failure -> {
                    pendingSaves[record.id] = record
                    false
                }
            }
            dropped.mapNotNull { it.id.takeIf { id -> id.isNotBlank() } }.forEach { id ->
                if (synced) archiveLocked(id) else pendingArchives.add(id)
            }
            return FitRecsWriteResult.Success(synced = synced)
        } finally {
            mutex.unlock()
        }
    }

    override suspend fun rename(record: FitRecord): FitRecsWriteResult {
        mutex.lock()
        try {
            if (record.id.isBlank()) return FitRecsWriteResult.Failure("记录缺少 id")
            val sid = currentSid()?.trim().orEmpty()
            if (sid.isEmpty()) return FitRecsWriteResult.Failure("请先完成身份验证")
            cache = sanitize(cache.map { if (it.id == record.id) record else it })
            val body = buildUpsertBody(sid, listOf(record))
            return when (api.post(FITRECS_PATH, body, prefer = PREFER_UPSERT)) {
                is ApiResult.Success -> {
                    pendingSaves.remove(record.id)
                    FitRecsWriteResult.Success(synced = true)
                }

                is ApiResult.Failure -> {
                    pendingSaves[record.id] = record
                    FitRecsWriteResult.Success(synced = false)
                }
            }
        } finally {
            mutex.unlock()
        }
    }

    override suspend fun archive(id: String): FitRecsWriteResult {
        mutex.lock()
        try {
            val recordId = id.trim()
            if (recordId.isEmpty()) return FitRecsWriteResult.Failure("记录缺少 id")
            val sid = currentSid()?.trim().orEmpty()
            if (sid.isEmpty()) return FitRecsWriteResult.Failure("请先完成身份验证")
            cache = cache.filterNot { it.id == recordId }
            pendingSaves.remove(recordId)
            return when (api.patch(archivePath(recordId), ARCHIVE_BODY)) {
                is ApiResult.Success -> {
                    pendingArchives.remove(recordId)
                    FitRecsWriteResult.Success(synced = true)
                }

                is ApiResult.Failure -> {
                    pendingArchives.add(recordId)
                    FitRecsWriteResult.Success(synced = false)
                }
            }
        } finally {
            mutex.unlock()
        }
    }

    override suspend fun pendingCount(): Int {
        mutex.lock()
        try {
            return pendingSaves.size + pendingArchives.size
        } finally {
            mutex.unlock()
        }
    }

    override suspend fun flushPending(): Int {
        mutex.lock()
        try {
            return flushPendingLocked()
        } finally {
            mutex.unlock()
        }
    }

    // ------------------------------------------------------------ 内部（调用方持锁）

    /** 补发 pending：保存 upsert 成功 / 软删成功各计 1 条；返回成功条数。 */
    private suspend fun flushPendingLocked(): Int {
        val sid = currentSid()?.trim().orEmpty()
        if (sid.isEmpty()) return 0
        var flushed = 0
        for (id in pendingSaves.keys.toList()) {
            val record = pendingSaves[id] ?: continue
            val body = buildUpsertBody(sid, listOf(record))
            if (api.post(FITRECS_PATH, body, prefer = PREFER_UPSERT) is ApiResult.Success) {
                pendingSaves.remove(id)
                flushed += 1
            }
        }
        for (id in pendingArchives.toList()) {
            if (api.patch(archivePath(id), ARCHIVE_BODY) is ApiResult.Success) {
                pendingArchives.remove(id)
                flushed += 1
            }
        }
        return flushed
    }

    private suspend fun archiveLocked(id: String) {
        if (api.patch(archivePath(id), ARCHIVE_BODY) is ApiResult.Failure) {
            pendingArchives.add(id)
        }
    }

    companion object {
        /** GET 路径前缀（sid 由 [loadPath] 拼接）。 */
        const val LOAD_BASE: String = "fitrecs?select=*&acc=eq."

        /** POST 目标（裸表路径，PostgREST 批量语义）。 */
        const val FITRECS_PATH: String = "fitrecs"

        /** upsert 偏好：返回 representation + 冲突按 id 合并。 */
        const val PREFER_UPSERT: String = "return=representation,resolution=merge-duplicates"

        /** 软删 body（不 DELETE）。 */
        const val ARCHIVE_BODY: String = """{"arch":true}"""

        /** 2 分钟 TTL（契约 fitrecs 刷新口径）。 */
        const val DEFAULT_TTL_MS: Long = 2L * 60 * 1000

        /** 最多保留条数（与 `FitRecord.MAX_RECORDS` 一致）。 */
        const val MAX_RECORDS: Int = 2

        fun loadPath(sid: String): String =
            LOAD_BASE + URLEncoder.encode(sid, "UTF-8") + "&arch=eq.false&order=ts.desc"

        fun archivePath(id: String): String = "fitrecs?id=eq." + URLEncoder.encode(id, "UTF-8")

        /** 解析行数组；非数组 → null（调用方按格式异常/缓存降级）。坏行跳过。 */
        fun parseRows(json: Json, raw: String): List<FitRecsRow>? {
            val element = parseJsonOrNull(json, raw) ?: return null
            val array = element as? JsonArray ?: return null
            val rows = ArrayList<FitRecsRow>(array.size)
            for (item in array) {
                val obj = item.asObjectOrNull() ?: continue
                val id = obj["id"].asStringOrNull()?.trim().orEmpty()
                if (id.isEmpty()) continue
                rows.add(
                    FitRecsRow(
                        id = id,
                        acc = obj["acc"].asStringOrNull().orEmpty(),
                        data = obj["data"].asStringOrNull().orEmpty(),
                        arch = (obj["arch"] as? JsonPrimitive)?.booleanOrNull ?: false,
                        ts = (obj["ts"] as? JsonPrimitive)?.longOrNull ?: 0L,
                    ),
                )
            }
            return rows
        }

        /** 行 → 记录：跳过 arch=true / 坏 data / 缺 id；按记录 ts 降序、最多 2 条。 */
        fun recordsFromRows(rows: List<FitRecsRow>): List<FitRecord> = rows
            .filterNot { it.arch }
            .mapNotNull { it.record }
            .sortedWith(compareByDescending<FitRecord> { it.ts }.thenByDescending { it.id })
            .take(MAX_RECORDS)

        fun mergeRecords(remote: List<FitRecord>, pending: List<FitRecord>): List<FitRecord> {
            if (pending.isEmpty()) return sanitize(remote)
            val byId = LinkedHashMap<String, FitRecord>()
            pending.forEach { byId[it.id] = it }
            remote.forEach { if (!byId.containsKey(it.id)) byId[it.id] = it }
            return sanitize(byId.values.toList())
        }

        /** 排重 + 按 ts 降序 + 最多 2 条。 */
        fun sanitize(records: List<FitRecord>): List<FitRecord> = records
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .sortedWith(compareByDescending<FitRecord> { it.ts }.thenByDescending { it.id })
            .take(MAX_RECORDS)

        /** POST body：数组（PostgREST 批量语义），data 为 JSON 字符串。 */
        fun buildUpsertBody(sid: String, records: List<FitRecord>): String = JsonArray(
            records.map { record ->
                buildJsonObject {
                    put("id", JsonPrimitive(record.id))
                    put("acc", JsonPrimitive(sid))
                    put("data", JsonPrimitive(FitCodec.recordToData(record)))
                    put("arch", JsonPrimitive(false))
                    put("ts", JsonPrimitive(record.ts))
                }
            },
        ).toString()
    }
}
