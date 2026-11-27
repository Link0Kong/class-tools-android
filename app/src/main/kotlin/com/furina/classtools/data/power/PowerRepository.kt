package com.furina.classtools.data.power

import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.asBooleanOrNull
import com.furina.classtools.data.network.asObjectOrNull
import com.furina.classtools.data.network.asStringOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import java.net.URLEncoder
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 宿舍电费仓库（契约：docs/api/ENDPOINTS.md E22/E23 + docs/BACKEND_CONTRACT.md §7.5）。
 *
 * 规则：
 * - **未选择宿舍时 0 请求**（由 UI 保证，仓库对空 rv 直接返回 Config 失败）；
 * - 当前值 2 min TTL；每档历史 5 min 缓存；**空结果/失败不写缓存**（防「数据积累中」毒化）；
 * - 网络失败但有旧缓存 → 返回旧缓存（fromCache=true），UI 显示「已显示本机缓存」。
 */
interface PowerRepository {
    suspend fun loadRow(rv: String, force: Boolean = false): PowerRowResult

    suspend fun cachedRow(rv: String): PowerRow? = null

    suspend fun loadHistory(rv: String, range: PowerRange, force: Boolean = false): PowerHistoryResult

    /**
     * 加载「比较取数」的隐藏窗口（14d/70d；对齐网页 `POWER.cmpSource`，不出现在区间按钮里）。
     *
     * 默认实现返回失败：预览/测试 fake 不需要该能力；生产实现按 5 min 缓存 + 失败回退旧缓存。
     */
    suspend fun loadCompareHistory(
        rv: String,
        range: PowerCompareRange,
        force: Boolean = false,
    ): PowerHistoryWindowResult = PowerHistoryWindowResult.Failure("暂不支持比较取数")

    suspend fun clearCache()
}

class DefaultPowerRepository(
    private val api: ApiClient,
    private val clock: () -> Long = System::currentTimeMillis,
    private val rowTtlMs: Long = ROW_TTL_MS,
    private val historyTtlMs: Long = HISTORY_TTL_MS,
    private val json: Json = NetJson,
) : PowerRepository {

    private data class Timed<T>(val value: T, val savedAt: Long)

    private val mutex = Mutex()
    private val rowCache = HashMap<String, Timed<PowerRow>>()
    private val historyCache = HashMap<String, Timed<List<PowerPoint>>>()

    override suspend fun cachedRow(rv: String): PowerRow? = mutex.withLock {
        rowCache[PowerRooms.normalizeSel(rv)]?.value
    }

    override suspend fun loadRow(rv: String, force: Boolean): PowerRowResult {
        val room = PowerRooms.normalizeSel(rv)
        if (room.isEmpty()) return PowerRowResult.Failure("请先选择关注的宿舍")
        mutex.lock()
        try {
            val now = clock()
            val cached = rowCache[room]
            if (!force && cached != null && now - cached.savedAt < rowTtlMs) {
                return PowerRowResult.Success(cached.value, fromCache = true)
            }
            val path = "power?select=id,label,building,room,quantity,unit,low,updated_at" +
                "&id=eq.${encode(room)}"
            return when (val result = api.get(path)) {
                is ApiResult.Failure -> staleRowFallback(cached, result.error.displayMessage)
                is ApiResult.Success -> {
                    val row = parseRow(json, result.value)
                    if (row == null) {
                        staleRowFallback(cached, "电费数据格式异常")
                    } else {
                        rowCache[room] = Timed(row, now)
                        PowerRowResult.Success(row, fromCache = false)
                    }
                }
            }
        } finally {
            mutex.unlock()
        }
    }

    override suspend fun loadHistory(rv: String, range: PowerRange, force: Boolean): PowerHistoryResult {
        val room = PowerRooms.normalizeSel(rv)
        if (room.isEmpty()) return PowerHistoryResult.Failure("请先选择关注的宿舍")
        mutex.lock()
        try {
            val now = clock()
            val key = cacheKey(room, range)
            val cached = historyCache[key]
            if (!force && cached != null && now - cached.savedAt < historyTtlMs) {
                return PowerHistoryResult.Success(cached.value, range, fromCache = true)
            }
            val path = historyPath(room, Instant.ofEpochMilli(now - range.windowMs).toString())
            return when (val result = api.get(path)) {
                is ApiResult.Failure -> staleHistoryFallback(cached, range, result.error.displayMessage)
                is ApiResult.Success -> {
                    val points = parseHistory(json, result.value)
                    // 空结果不写缓存（契约：防「数据积累中」毒化）。
                    if (points.isNotEmpty()) {
                        historyCache[key] = Timed(points, now)
                    }
                    PowerHistoryResult.Success(points, range, fromCache = false)
                }
            }
        } finally {
            mutex.unlock()
        }
    }

    override suspend fun loadCompareHistory(
        rv: String,
        range: PowerCompareRange,
        force: Boolean,
    ): PowerHistoryWindowResult {
        val room = PowerRooms.normalizeSel(rv)
        if (room.isEmpty()) return PowerHistoryWindowResult.Failure("请先选择关注的宿舍")
        mutex.lock()
        try {
            val now = clock()
            val key = cacheKey(room, range)
            val cached = historyCache[key]
            if (!force && cached != null && now - cached.savedAt < historyTtlMs) {
                return PowerHistoryWindowResult.Success(cached.value, range, fromCache = true)
            }
            val path = historyPath(room, Instant.ofEpochMilli(now - range.windowMs).toString())
            return when (val result = api.get(path)) {
                is ApiResult.Failure -> staleWindowFallback(cached, range, result.error.displayMessage)
                is ApiResult.Success -> {
                    val points = parseHistory(json, result.value)
                    // 空结果不写缓存（防「数据积累中」毒化，与区间历史一致）。
                    if (points.isNotEmpty()) {
                        historyCache[key] = Timed(points, now)
                    }
                    PowerHistoryWindowResult.Success(points, range, fromCache = false)
                }
            }
        } finally {
            mutex.unlock()
        }
    }

    override suspend fun clearCache() {
        mutex.lock()
        try {
            rowCache.clear()
            historyCache.clear()
        } finally {
            mutex.unlock()
        }
    }

    private fun staleRowFallback(
        cached: Timed<PowerRow>?,
        message: String,
    ): PowerRowResult = if (cached != null) {
        PowerRowResult.Success(cached.value, fromCache = true, refreshError = message)
    } else {
        PowerRowResult.Failure(message)
    }

    private fun staleHistoryFallback(
        cached: Timed<List<PowerPoint>>?,
        range: PowerRange,
        message: String,
    ): PowerHistoryResult = if (cached != null) {
        PowerHistoryResult.Success(cached.value, range, fromCache = true)
    } else {
        PowerHistoryResult.Failure(message)
    }

    private fun staleWindowFallback(
        cached: Timed<List<PowerPoint>>?,
        range: PowerCompareRange,
        message: String,
    ): PowerHistoryWindowResult = if (cached != null) {
        PowerHistoryWindowResult.Success(cached.value, range, fromCache = true)
    } else {
        PowerHistoryWindowResult.Failure(message)
    }

    /** E23 历史请求路径（区间与隐藏比较窗口共用同一查询口径）。 */
    private fun historyPath(room: String, cutIso: String): String =
        "power_history?select=ts,quantity" +
            "&roomverify=eq.${encode(room)}" +
            "&ts=gte.${encode(cutIso)}" +
            "&order=ts.asc&limit=2000"

    companion object {
        const val ROW_TTL_MS: Long = 2L * 60 * 1000
        const val HISTORY_TTL_MS: Long = 5L * 60 * 1000

        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

        fun cacheKey(rv: String, range: PowerRange): String = "$rv|${range.key}"

        /** 隐藏比较窗口（14d/70d）单独缓存键，避免与区间历史串用。 */
        fun cacheKey(rv: String, range: PowerCompareRange): String = "$rv|${range.key}"

        /** 解析 E22 响应：数组→首行；quantity 兼容 number/数字字符串；非法 quantity → q=null。 */
        fun parseRow(json: Json, raw: String): PowerRow? {
            val root = parseJsonOrNull(json, raw) ?: return null
            val row: JsonObject = when (root) {
                is JsonArray -> root.firstOrNull().asObjectOrNull() ?: return null
                is JsonObject -> root
                else -> return null
            }
            val id = row["id"].asStringOrNull()?.trim().orEmpty()
            if (id.isEmpty()) return null
            return PowerRow(
                id = id,
                label = row["label"].asStringOrNull()?.trim().orEmpty(),
                building = row["building"].asStringOrNull()?.trim().orEmpty(),
                room = row["room"].asStringOrNull()?.trim().orEmpty(),
                q = numberOrNull(row["quantity"]),
                unit = row["unit"].asStringOrNull()?.trim().orEmpty().ifBlank { "度" },
                low = row["low"].asBooleanOrNull() ?: false,
                updatedAt = row["updated_at"].asStringOrNull()?.trim().orEmpty(),
            )
        }

        /** 解析 E23 响应：只保留 ts/quantity 合法点，按时间升序。 */
        fun parseHistory(json: Json, raw: String): List<PowerPoint> {
            val root = parseJsonOrNull(json, raw) ?: return emptyList()
            val array = root as? JsonArray ?: return emptyList()
            val out = ArrayList<PowerPoint>(array.size)
            for (item in array) {
                val obj = item.asObjectOrNull() ?: continue
                val q = numberOrNull(obj["quantity"]) ?: continue
                val t = PowerStats.parseIsoMillis(obj["ts"].asStringOrNull()) ?: continue
                out.add(PowerPoint(q = q, t = t))
            }
            return out.sortedBy { it.t }
        }

        /** 数值字段兼容 number 与数字字符串（云端 numeric 可能被序列化为字符串）。 */
        fun numberOrNull(element: JsonElement?): Double? {
            val primitive = element as? JsonPrimitive ?: return null
            if (primitive is JsonPrimitive && primitive.contentOrNull == null) return null
            return primitive.contentOrNull?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }
        }
    }
}
