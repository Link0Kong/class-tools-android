package com.furina.classtools.data.member

import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiError
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.asBooleanOrNull
import com.furina.classtools.data.network.asIntOrNull
import com.furina.classtools.data.network.asObjectOrNull
import com.furina.classtools.data.network.asStringOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * 团员名单模型（`POST rpc/class_league` → `{ok:true,count,seats:[{seat,name}]}`）。
 *
 * 隐私红线：RPC **不返回 sid/学号**，模型也只保留 seat/name；页面不得显示学号。
 */
data class LeagueMember(
    val seat: Int = 0,
    val name: String = "",
)

/** 团员名单加载结果（失败时若在本机缓存内则降级）。 */
sealed interface ClassLeagueResult {
    data class Success(
        val members: List<LeagueMember>,
        val fromCache: Boolean = false,
        /** 降级提示（如网络不可用，UI 可 toast）。 */
        val warning: String? = null,
    ) : ClassLeagueResult

    data class Failure(val message: String) : ClassLeagueResult
}

/**
 * 团员名单仓库（名单三分类之「团员」）。
 *
 * - 读取 `class_league` RPC（只读、无 sid），会话内缓存 TTL 6 小时；
 * - 网络失败 → 有缓存返回缓存（fromCache=true + warning），否则 Failure；
 * - 空名单视为有效空（团员可能为 0）。
 */
interface ClassLeagueRepository {
    suspend fun load(force: Boolean = false): ClassLeagueResult

    /** 只读内存缓存，不发请求。 */
    suspend fun cached(): List<LeagueMember>

    /** 退出身份时清空（与 class_members 缓存策略一致）。 */
    suspend fun clear()
}

class DefaultClassLeagueRepository(
    private val api: ApiClient,
    private val currentSid: () -> String? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
    private val json: Json = NetJson,
    private val ttlMs: Long = DEFAULT_TTL_MS,
) : ClassLeagueRepository {

    private var cacheSid: String? = null
    private var cache: List<LeagueMember> = emptyList()
    private var loadedOnce = false
    private var lastFetchAt: Long = 0L

    override suspend fun load(force: Boolean): ClassLeagueResult {
        val sid = currentSid()?.trim().orEmpty()
        if (sid.isNotEmpty() && sid != cacheSid) {
            cacheSid = sid
            cache = emptyList()
            loadedOnce = false
            lastFetchAt = 0L
        }
        val now = clock()
        if (!force && loadedOnce && now - lastFetchAt < ttlMs) {
            return ClassLeagueResult.Success(cache, fromCache = true)
        }
        return when (val result = api.post(LEAGUE_PATH, "{}")) {
            is ApiResult.Failure -> fallback(result.error.displayMessage)
            is ApiResult.Success -> {
                val parsed = parseLeague(result.value, json)
                if (parsed == null) {
                    fallback("团员名单格式异常")
                } else {
                    cache = parsed
                    loadedOnce = true
                    lastFetchAt = now
                    ClassLeagueResult.Success(parsed, fromCache = false)
                }
            }
        }
    }

    override suspend fun cached(): List<LeagueMember> = cache

    override suspend fun clear() {
        cache = emptyList()
        loadedOnce = false
        lastFetchAt = 0L
        cacheSid = null
    }

    private fun fallback(message: String): ClassLeagueResult =
        if (loadedOnce) {
            ClassLeagueResult.Success(cache, fromCache = true, warning = message)
        } else {
            ClassLeagueResult.Failure(message)
        }

    companion object {
        const val LEAGUE_PATH: String = "rpc/class_league"
        const val DEFAULT_TTL_MS: Long = 6L * 60 * 60 * 1000

        /**
         * 解析 `class_league` 响应（纯函数，可单测）：
         * - `{ok:true, seats:[{seat,name}]}`；
         * - `{ok:false,reason}` → null（调用方映射错误/降级）；
         * - 坏行（seat 非 1..70 / name 空）跳过；按 seat 升序去重。
         */
        fun parseLeague(raw: String, json: Json = NetJson): List<LeagueMember>? {
            val element = parseJsonOrNull(json, raw) ?: return null
            val body = element.asObjectOrNull() ?: return null
            if (body["ok"].asBooleanOrNull() != true) return null
            val seats = body["seats"] as? JsonArray ?: return null
            val out = LinkedHashMap<Int, LeagueMember>()
            seats.forEach { item ->
                val obj = item.asObjectOrNull() ?: return@forEach
                val seat = obj["seat"].asIntOrNull() ?: return@forEach
                val name = obj["name"].asStringOrNull()?.trim().orEmpty()
                if (seat !in SEAT_RANGE || name.isEmpty()) return@forEach
                out.putIfAbsent(seat, LeagueMember(seat = seat, name = name))
            }
            return out.values.sortedBy { it.seat }
        }

        private val SEAT_RANGE = 1..70
    }
}
