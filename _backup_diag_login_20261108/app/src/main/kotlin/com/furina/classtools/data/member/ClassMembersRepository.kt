package com.furina.classtools.data.member

import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.asIntOrNull
import com.furina.classtools.data.network.asObjectOrNull
import com.furina.classtools.data.network.asStringOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * 班级名单仓库实现：`POST rpc/class_members`（body `{}`，带身份 token）。
 *
 * 缓存：内存 L1（按 sid 隔离）+ [FileClassMemberCache] 落盘；TTL 6h。
 * 失败语义（契约 §3.1）：网络失败/空数组 → **不覆盖旧缓存**，返回旧缓存并标记 fromCache。
 */
class DefaultClassMembersRepository(
    private val api: ApiClient,
    private val cache: FileClassMemberCache,
    private val currentSid: () -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val json: Json = NetJson,
) : ClassMembersRepository {

    private val mutex = Mutex()
    private var memorySid: String = ""
    private var memoryMembers: List<ClassMember> = emptyList()
    private var memorySavedAt: Long = 0L

    override suspend fun load(force: Boolean): ClassMembersResult {
        // Mutex.withLock 的 action 不是 suspend，手动 lock/unlock 以在锁内调用挂起函数。
        mutex.lock()
        try {
            return loadLocked(force)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun loadLocked(force: Boolean): ClassMembersResult {
        val sid = currentSid()?.trim().orEmpty()
        if (sid.isEmpty()) {
            return ClassMembersResult.Failure("请先完成身份验证")
        }

        // 1) 内存缓存（同 sid 且 TTL 内）
        if (!force && memorySid == sid && memoryMembers.isNotEmpty() &&
            clock() - memorySavedAt < ttlMs
        ) {
            return ClassMembersResult.Success(memoryMembers, fromCache = true)
        }

        // 2) 落盘缓存（TTL 内直接返回；TTL 过期但请求失败时仍作为兜底）
        val cached: List<ClassMember> = if (memorySid == sid && memoryMembers.isNotEmpty()) {
            memoryMembers
        } else {
            val file = cache.read(sid)
            if (file != null) {
                file.members.also {
                    memorySid = sid
                    memoryMembers = it
                    memorySavedAt = file.savedAt
                }
            } else {
                emptyList()
            }
        }
        val cachedAt = memorySavedAt
        if (!force && cached.isNotEmpty() && clock() - cachedAt < ttlMs) {
            return ClassMembersResult.Success(cached, fromCache = true)
        }

        // 3) 请求云端
        val body = buildJsonObject { }.toString()
        return when (val result = api.post(path = MEMBERS_PATH, body = body)) {
            is ApiResult.Failure -> {
                if (cached.isNotEmpty()) {
                    ClassMembersResult.Success(cached, fromCache = true)
                } else {
                    ClassMembersResult.Failure(result.error.displayMessage, cause = null)
                }
            }
            is ApiResult.Success -> {
                val members = parseMembers(json, result.value)
                if (members.isEmpty()) {
                    // 契约：空数组视为失败，保留旧缓存。
                    if (cached.isNotEmpty()) {
                        ClassMembersResult.Success(cached, fromCache = true)
                    } else {
                        ClassMembersResult.Failure("班级名单为空，请稍后重试")
                    }
                } else {
                    val now = clock()
                    memorySid = sid
                    memoryMembers = members
                    memorySavedAt = now
                    cache.write(sid, members, now)
                    ClassMembersResult.Success(members, fromCache = false)
                }
            }
        }
    }

    override suspend fun cached(): List<ClassMember> {
        mutex.lock()
        try {
            val sid = currentSid()?.trim().orEmpty()
            if (sid.isEmpty()) return emptyList()
            if (memorySid == sid && memoryMembers.isNotEmpty()) return memoryMembers
            val file = cache.read(sid) ?: return emptyList()
            memorySid = sid
            memoryMembers = file.members
            memorySavedAt = file.savedAt
            return file.members
        } finally {
            mutex.unlock()
        }
    }

    override suspend fun clear() {
        mutex.lock()
        try {
            val sid = currentSid()?.trim().orEmpty()
            memorySid = ""
            memoryMembers = emptyList()
            memorySavedAt = 0L
            if (sid.isNotEmpty()) cache.clear(sid)
        } finally {
            mutex.unlock()
        }
    }

    companion object {
        const val MEMBERS_PATH: String = "rpc/class_members"
        const val DEFAULT_TTL_MS: Long = 6L * 60 * 60 * 1000

        /** 解析名单：兼容直接数组与 `{data:[...]}`；清洗非法行/空姓名。 */
        fun parseMembers(json: Json, raw: String): List<ClassMember> {
            val element = parseJsonOrNull(json, raw) ?: return emptyList()
            val array: JsonArray = when (element) {
                is JsonArray -> element
                is JsonObject -> element["data"] as? JsonArray ?: JsonArray(emptyList())
                else -> JsonArray(emptyList())
            }
            val out = ArrayList<ClassMember>(array.size)
            val seenSid = HashSet<String>()
            for (item in array) {
                val obj = item.asObjectOrNull() ?: continue
                val member = normalizeMember(obj) ?: continue
                if (member.sid.isNotEmpty() && !seenSid.add(member.sid)) continue
                out.add(member)
            }
            return out.sortedWith(compareBy({ it.seat }, { it.name }))
        }

        private fun normalizeMember(obj: JsonObject): ClassMember? {
            val name = obj["name"].asStringOrNull()?.trim().orEmpty()
            if (name.isEmpty()) return null
            val seat = obj["seat"].asIntOrNull() ?: 0
            if (seat < 0 || seat > 999) return null
            return ClassMember(
                seat = seat,
                sid = obj["sid"].asStringOrNull()?.trim().orEmpty(),
                name = name,
                role = obj["role"].asStringOrNull()?.trim().orEmpty(),
                badge = obj["badge"].asStringOrNull()?.trim().orEmpty(),
                isAdmin = (obj["is_admin"] as? JsonPrimitive)?.booleanOrNull ?: false,
            )
        }
    }
}
