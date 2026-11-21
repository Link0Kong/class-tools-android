package com.furina.classtools.data.power

import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.network.NetJson
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json

/**
 * 宿舍目录加载结果。
 *
 * [Success.fromCache] = true 表示本次返回的是内存缓存（网络失败回退或 TTL 内命中）。
 */
sealed interface PowerDirectoryResult {
    data class Success(val rooms: List<RoomInfo>, val fromCache: Boolean) : PowerDirectoryResult

    data class Failure(val message: String) : PowerDirectoryResult
}

/**
 * 宿舍目录仓库（契约：docs/api/ENDPOINTS.md E21）。
 *
 * **为什么不在客户端硬编码房号**（2026-11-21 修复）：
 * - 真实宿舍号属于班级个人信息，公开仓库/源码不应内置；
 * - 班级增删宿舍只改云端 `power` 行（同步脚本维护），客户端无需发版；
 * - 历史问题：一次「公开仓库脱敏」把硬编码目录换成了虚构房号，导致真实宿舍全部选不到/不显示。
 *
 * 规则（与电费当前值/历史仓库同口径）：
 * - `GET power?select=id,label,building,room`（RLS：有效班级 token 才可读）；
 * - 5 min 内存缓存；**失败回退旧缓存**（fromCache=true），空目录不写缓存；
 * - 目录只是「可选宿舍列表」：已关注宿舍的展示名由 roomverify 派生（[PowerRooms]），
 *   因此目录加载失败不影响已关注宿舍看电量，只影响「换宿舍」。
 */
interface PowerDirectoryRepository {
    suspend fun load(force: Boolean = false): PowerDirectoryResult

    suspend fun clearCache()
}

class DefaultPowerDirectoryRepository(
    private val api: ApiClient,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ttlMs: Long = TTL_MS,
    private val json: Json = NetJson,
) : PowerDirectoryRepository {

    private data class Cached(val rooms: List<RoomInfo>, val savedAt: Long)

    private val mutex = Mutex()
    private var cache: Cached? = null

    override suspend fun load(force: Boolean): PowerDirectoryResult {
        mutex.lock()
        try {
            val now = clock()
            val cached = cache
            if (!force && cached != null && now - cached.savedAt < ttlMs) {
                return PowerDirectoryResult.Success(cached.rooms, fromCache = true)
            }
            return when (val result = api.get(PATH)) {
                is ApiResult.Failure -> fallback(cached?.rooms, result.error.displayMessage)
                is ApiResult.Success -> {
                    val rooms = PowerRooms.parseDirectory(json, result.value)
                    if (rooms.isEmpty()) {
                        // 空目录可能是「云端未同步」；不写缓存，保留上次成功目录（如有）。
                        fallback(cached?.rooms, "宿舍目录为空")
                    } else {
                        cache = Cached(rooms, now)
                        PowerDirectoryResult.Success(rooms, fromCache = false)
                    }
                }
            }
        } finally {
            mutex.unlock()
        }
    }

    override suspend fun clearCache() {
        mutex.lock()
        try {
            cache = null
        } finally {
            mutex.unlock()
        }
    }

    private fun fallback(cached: List<RoomInfo>?, message: String): PowerDirectoryResult =
        if (cached != null) {
            PowerDirectoryResult.Success(cached, fromCache = true)
        } else {
            PowerDirectoryResult.Failure(message)
        }

    companion object {
        const val TTL_MS: Long = 5L * 60 * 1000

        /** E21：只取目录四列（不拉 quantity，避免目录请求被电量更新放大）。 */
        const val PATH: String = "power?select=id,label,building,room&order=id.asc&limit=200"
    }
}
