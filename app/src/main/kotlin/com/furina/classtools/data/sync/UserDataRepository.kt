package com.furina.classtools.data.sync

import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiError
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.asObjectOrNull
import com.furina.classtools.data.network.asStringOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import com.furina.classtools.data.session.Session
import java.net.URLEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 同步节奏常量（契约 §4.3：拉取 TTL 60s、推送防抖 1200ms、清除后抑制期 30 天）。 */
data class UserdataSyncConfig(
    val ttlMs: Long = 60_000L,
    val debounceMs: Long = 1_200L,
    val holdMs: Long = 30L * 24 * 60 * 60 * 1000,
)

sealed interface UserdataPullResult {
    data class Success(
        val changed: Boolean,
        val pushed: Boolean,
        val fresh: Boolean = false,
        val migratedV5: Boolean = false,
    ) : UserdataPullResult

    /** 云同步已关闭 / 未登录等前置条件不满足。 */
    data object Disabled : UserdataPullResult
    data class Failure(val error: ApiError) : UserdataPullResult
}

sealed interface UserdataPushResult {
    data class Success(val pushed: Boolean) : UserdataPushResult

    /** 无基线且非强制（不盲推，避免用默认值覆盖云端）。 */
    data object Skipped : UserdataPushResult
    data object Disabled : UserdataPushResult

    /** 「清除云端我的数据」后的抑制期：不自动回推。 */
    data object Held : UserdataPushResult
    data class Failure(val error: ApiError) : UserdataPushResult
}

sealed interface UserdataSectionUpdate {
    data class Saved(val syncPending: Boolean) : UserdataSectionUpdate
    data class Conflict(val value: JsonElement?) : UserdataSectionUpdate
    data object Inactive : UserdataSectionUpdate
}

/**
 * userdata v6 双向同步仓库（契约：docs/BACKEND_CONTRACT.md §4 + docs/api/USERDATA_V6.md）。
 *
 * 线程/协程约定：
 * - 所有 `suspend` 方法主线程安全（内部只做本机文件 IO 与网络 IO，均切 IO 线程）；
 * - [touch] 是 **非挂起** 的「本机数据已变」通知：内部在 [scope] 里做脏检测并防抖 1200ms 推送；
 * - [pull] 启动/回前台/TTL 到期调用；[push] 手动同步/关键偏好立即落云调用；[flush] 页面隐藏/退出前调用。
 *
 * 关键规则（照抄契约，勿简化）：
 * - 脏 section 抢时间戳 `max(now, base.at+1, remote.at+1)`（[UserdataMerge.localView]）；
 * - 无基线不盲推：先 pull 建基线；用户先改动则 [UserdataLocalStore.markPending] 保证本地赢；
 * - push 已带 force 语义：GET→merge→POST upsert（`Prefer: return=representation,resolution=merge-duplicates`）；
 * - 失败保留 `pend` 待补发；云端 v5 → 合并后立即写回 v6（迁移幂等）。
 */
interface UserDataRepository {
    /** 本机当前 11 个 section 的值（不含 v/at/homeToolsAt），供 UI/业务层观察。 */
    val currentSections: StateFlow<JsonObject>

    suspend fun pull(force: Boolean = false): UserdataPullResult

    suspend fun push(force: Boolean = false, forceKeys: Set<String> = emptySet()): UserdataPushResult

    /** 本机同步内容变更入口：持久化 sections → 更新 Flow → 防抖推送。 */
    suspend fun updateCurrent(sections: JsonObject)

    /** Read, compare and durably write one section. A null transform result leaves it unchanged. */
    suspend fun updateCurrentSection(key: String, change: (JsonElement?) -> JsonElement?): UserdataSectionUpdate =
        UserdataSectionUpdate.Inactive

    /** 本机数据变了（业务层在 `store.save()` 等价位置调用）：内部做脏检测。 */
    fun touch()

    /** 立即冲刷待推送（页面隐藏/退出前）。 */
    suspend fun flush(): UserdataPushResult

    suspend fun setSyncEnabled(enabled: Boolean)
    suspend fun syncEnabled(): Boolean

    /** 最近一次成功合并的基线 blob（调试/清除云端用）。 */
    suspend fun cachedBlob(): JsonObject?

    /**
     * 从本机持久化的 current sections 恢复内存镜像。
     *
     * 登录/启动时调用（在 pull 之前）：离线、云同步关闭或 Debug 会话下 UI 也能看到上次的本机
     * 偏好（homeTools/toolOrder/water/roster 等），而不是回落默认值。
     */
    suspend fun hydrateFromLocal()
}

/** One disk/UI publication can complete before retirement; later work cannot commit into a new login. */
class UserdataSession(val sid: String) {
    @Volatile var active: Boolean = true
        private set

    @Synchronized fun commit(action: () -> Unit) {
        if (active) action()
    }

    @Synchronized fun retire() { active = false }
}

/** Keep a stable observable for UI and injected services, but bind each operation and timer to one repository. */
class SessionUserdataRepository : UserDataRepository {
    private val sections = MutableStateFlow(JsonObject(emptyMap()))
    override val currentSections = sections.asStateFlow()
    @Volatile private var repository: DefaultUserDataRepository? = null
    private var session: UserdataSession? = null
    private var boundSession: Session? = null

    @Synchronized
    fun switchTo(sid: String?, owner: Session? = null, create: (UserdataSession, (JsonObject) -> Unit) -> DefaultUserDataRepository) {
        session?.retire()
        repository?.stop()
        repository = null
        boundSession = owner
        sections.value = JsonObject(emptyMap())
        session = sid?.let { UserdataSession(it) }?.also { owner ->
            repository = create(owner) { value -> owner.commit { sections.value = value } }
        }
    }

    /** Start a user action against the repository belonging to that login, even if the UI outlives it. */
    @Synchronized
    fun boundTo(owner: Session): UserDataRepository? {
        val bound = boundSession ?: return null
        return repository.takeIf {
            bound.sid == owner.sid && bound.token == owner.token &&
                bound.issuedAt == owner.issuedAt && bound.debug == owner.debug
        }
    }

    override suspend fun pull(force: Boolean) = repository?.pull(force) ?: UserdataPullResult.Disabled
    override suspend fun push(force: Boolean, forceKeys: Set<String>) =
        repository?.push(force, forceKeys) ?: UserdataPushResult.Disabled
    override suspend fun flush() = repository?.flush() ?: UserdataPushResult.Disabled
    override suspend fun updateCurrent(sections: JsonObject) { repository?.updateCurrent(sections) }
    override suspend fun updateCurrentSection(key: String, change: (JsonElement?) -> JsonElement?) =
        repository?.updateCurrentSection(key, change) ?: UserdataSectionUpdate.Inactive
    override fun touch() { repository?.touch() }
    override suspend fun setSyncEnabled(enabled: Boolean) { repository?.setSyncEnabled(enabled) }
    override suspend fun syncEnabled() = repository?.syncEnabled() == true
    override suspend fun cachedBlob(): JsonObject? {
        val owner = repository ?: return null
        return owner.cachedBlob()?.takeIf { repository === owner }
    }
    override suspend fun hydrateFromLocal() { repository?.hydrateFromLocal() }
}

class DefaultUserDataRepository(
    private val api: ApiClient,
    private val local: UserDataLocalStore,
    private val currentSid: () -> String?,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val config: UserdataSyncConfig = UserdataSyncConfig(),
    private val json: Json = NetJson,
    /** Android 全量客户端拥有全部 section；如某端只想写部分，可收窄（未拥有 section 原样保留云端值）。 */
    private val ownedSections: Set<String> = UserdataV6.SECTION_KEYS.toSet(),
    private val syncAllowed: () -> Boolean = { true },
    private val onSections: (JsonObject) -> Unit = {},
) : UserDataRepository {

    private val _currentSections = MutableStateFlow(JsonObject(emptyMap()))
    override val currentSections: StateFlow<JsonObject> = _currentSections.asStateFlow()
    @Volatile
    private var sectionsSid: String? = null

    private val syncMutex = Mutex()
    private val currentMutex = Mutex()
    private var debounceJob: Job? = null
    private var recovering = false

    fun stop() { debounceJob?.cancel() }

    private fun publish(value: JsonObject) {
        _currentSections.value = value
        onSections(value)
    }

    // ------------------------------------------------------------ 本机状态

    override suspend fun updateCurrent(sections: JsonObject) {
        val sid = sidOrNull() ?: return
        val normalized = UserdataV6.sectionsOf(sections)
        currentMutex.withLock {
            if (sid != sidOrNull()) return
            local.writeCurrent(normalized)
            if (sid != sidOrNull()) return
            sectionsSid = sid
            publish(normalized)
        }
        onLocalChanged()
    }

    override suspend fun updateCurrentSection(key: String, change: (JsonElement?) -> JsonElement?): UserdataSectionUpdate {
        require(key in ownedSections)
        val sid = sidOrNull() ?: return UserdataSectionUpdate.Inactive
        return currentMutex.withLock {
            if (sid != sidOrNull()) return@withLock UserdataSectionUpdate.Inactive
            val current = UserdataV6.sectionsOf(local.readCurrent())
            val next = change(current[key]) ?: return@withLock UserdataSectionUpdate.Conflict(current[key])
            val enabled = syncEnabled()
            val changed = next != current[key]
            val durablePendingKey = key.takeIf { it == "homeTools" }
            val schedule = changed && enabled && clock() >= local.holdUntil() &&
                (durablePendingKey != null || local.readCache() != null)
            if (changed) {
                val updated = JsonObject(current + (key to next))
                withContext(NonCancellable) {
                    // Preserve an intentional empty homeTools selection even when there is no cloud baseline yet.
                    local.commitCurrent(updated, durablePendingKey)
                    if (sid == sidOrNull()) {
                        sectionsSid = sid
                        publish(updated)
                        if (schedule) schedulePush()
                    }
                }
            }
            if (sid != sidOrNull()) UserdataSectionUpdate.Inactive
            else UserdataSectionUpdate.Saved(
                syncPending = schedule || (enabled && durablePendingKey != null && key in local.pendingKeys()),
            )
        }
    }

    override fun touch() {
        scope.launch { onLocalChanged() }
    }

    private suspend fun onLocalChanged() {
        if (sidOrNull() == null) return
        if (!syncEnabled()) return

        currentMutex.withLock {
            if (sidOrNull() == null) return
            val current = normalizedCurrent()
            publish(current)
            val base = local.readCache()
            if (base != null) {
                if (UserdataMerge.dirtySections(base, current, ownedSections).isNotEmpty()) schedulePush()
                return
            }
            // 无基线时只标记实际改动的 section，以免用默认值覆盖云端。
            val changed = UserdataMerge.changedKeysVsDefaults(current, ownedSections)
            if (changed.isEmpty()) return
            local.markPending(changed)
            if (recovering) return
            recovering = true
        }
        try {
            pull(force = true)
        } finally {
            withContext(NonCancellable) { currentMutex.withLock { recovering = false } }
        }
    }

    private suspend fun normalizedCurrent(): JsonObject {
        val raw = local.readCurrent()
        val out = LinkedHashMap<String, JsonElement>()
        for (key in UserdataV6.SECTION_KEYS) {
            if (key !in ownedSections) {
                // 未拥有：不写本机值，交由 localView 从基线原样带上。
                continue
            }
            val value = raw[key]?.takeUnless { it is kotlinx.serialization.json.JsonNull }
            out[key] = if (key == "water") {
                WaterNormalizer.normalize(value)
            } else {
                value ?: UserdataV6.defaultSection(key)
            }
        }
        return JsonObject(out)
    }

    private fun schedulePush() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(config.debounceMs)
            // Only the waiting timer is debounced. An already submitted POST must finish and reconcile.
            scope.launch {
                if (local.readCache() == null) pull(force = true) else push()
            }
        }
    }

    // ------------------------------------------------------------ 拉取

    override suspend fun pull(force: Boolean): UserdataPullResult = syncMutex.withLock { performPull(force) }

    private suspend fun performPull(force: Boolean): UserdataPullResult {
        val sid = sidOrNull() ?: return UserdataPullResult.Disabled
        if (!syncEnabled()) return UserdataPullResult.Disabled

        val now = clock()
        if (!force && now - local.lastPullAt() < config.ttlMs) {
            return UserdataPullResult.Success(changed = false, pushed = false, fresh = true)
        }

        val response = fetchRemote(sid)
        if (sid != sidOrNull()) return UserdataPullResult.Disabled
        val remote = when (val result = response) {
            is ApiResult.Failure -> return UserdataPullResult.Failure(result.error)
            is ApiResult.Success -> result.value
        }
        if (sid != sidOrNull()) return UserdataPullResult.Disabled
        val wasV5 = remote != null && UserdataV6.numMs(remote["v"]) == 5L

        val forceKeys = LinkedHashSet<String>()
        val (changed, shouldPush) = currentMutex.withLock {
            if (sid != sidOrNull()) return UserdataPullResult.Disabled
            val base = local.readCache()
            val current = normalizedCurrent()
            val wasDirty = UserdataMerge.dirtySections(base, current, ownedSections)
            val pending = local.pendingKeys()
            if (local.pend() || pending.isNotEmpty()) {
                forceKeys.addAll(pending)
                // 无基线但用户已产生改动时至少保住 power（契约 §4.3 web 同款兜底）。
                if (base == null) forceKeys.add("power")
                forceKeys.remove("prefs")
            }

            val localView = UserdataMerge.localView(base, current, remote, forceKeys, now, ownedSections)
            val merged = UserdataMerge.merge(remote, localView)
            persistApplied(merged)
            local.setLastPullAt(now)

            val dirtyAfter = UserdataMerge.dirtySections(merged, normalizedCurrent(), ownedSections)
            sectionsDiffer(current, merged) to (wasV5 || wasDirty.isNotEmpty() || dirtyAfter.isNotEmpty() ||
                local.pend() || pending.isNotEmpty())
        }
        if (shouldPush) {
            val pushResult = performPush(force = true, forceKeys = forceKeys)
            return UserdataPullResult.Success(
                changed = changed || wasV5,
                pushed = pushResult is UserdataPushResult.Success && pushResult.pushed,
                fresh = false,
                migratedV5 = wasV5,
            )
        }
        return UserdataPullResult.Success(changed = changed, pushed = false)
    }

    // ------------------------------------------------------------ 推送

    override suspend fun push(force: Boolean, forceKeys: Set<String>): UserdataPushResult =
        syncMutex.withLock { performPush(force, forceKeys) }

    private suspend fun performPush(force: Boolean, forceKeys: Set<String>): UserdataPushResult {
        val sid = sidOrNull() ?: return UserdataPushResult.Disabled
        if (!syncEnabled()) return UserdataPushResult.Disabled
        if (clock() < local.holdUntil()) return UserdataPushResult.Held

        val base = local.readCache()
        if (base == null && !force && forceKeys.isEmpty()) return UserdataPushResult.Skipped

        val response = fetchRemote(sid)
        if (sid != sidOrNull()) return UserdataPushResult.Disabled
        val remote = when (val result = response) {
            is ApiResult.Failure -> {
                local.setPend(true)
                return UserdataPushResult.Failure(result.error)
            }
            is ApiResult.Success -> result.value
        }
        val now = clock()
        val (current, merged) = currentMutex.withLock {
            if (sid != sidOrNull()) return UserdataPushResult.Disabled
            val current = normalizedCurrent()
            val pending = local.pendingKeys()
            val localView = UserdataMerge.localView(
                base = base,
                current = current,
                remote = remote,
                forceKeys = forceKeys + pending.filterNot { it == "prefs" },
                now = now,
                ownedSections = ownedSections,
            )
            val merged = UserdataMerge.merge(remote, localView)
            val remoteNormalized = UserdataMerge.merge(remote, null)
            val mustWrite = force || remote == null || merged != remoteNormalized

            if (!mustWrite) {
                persistApplied(merged)
                local.clearPending()
                return UserdataPushResult.Success(pushed = false)
            }
            current to merged
        }

        val body = buildJsonArray {
            add(
                buildJsonObject {
                    put("id", JsonPrimitive(sid))
                    put("blob", JsonPrimitive(UserdataV6.encode(merged, json)))
                    put("ts", JsonPrimitive(now))
                },
            )
        }.toString()

        if (sid != sidOrNull()) return UserdataPushResult.Disabled
        val write = api.post(path = USERDATA_PATH, body = body, prefer = UPSERT_PREFER)
        return currentMutex.withLock {
            if (sid != sidOrNull()) return@withLock UserdataPushResult.Disabled
            when (write) {
                is ApiResult.Failure -> {
                    val dirty = UserdataMerge.dirtySections(base, normalizedCurrent(), ownedSections)
                    local.markPending(forceKeys + dirty)
                    UserdataPushResult.Failure(write.error)
                }
                is ApiResult.Success -> {
                    val latest = normalizedCurrent()
                    val changedDuringPost = latest.filter { (key, value) -> current[key] != value }
                    persistApplied(merged, changedDuringPost)
                    if (changedDuringPost.isEmpty()) local.clearPending()
                    UserdataPushResult.Success(pushed = true)
                }
            }
        }
    }

    override suspend fun flush(): UserdataPushResult {
        debounceJob?.cancel()
        debounceJob = null
        if (sidOrNull() == null) return UserdataPushResult.Disabled

        val base = local.readCache()
        val current = normalizedCurrent()
        val dirty = if (base != null) {
            UserdataMerge.dirtySections(base, current, ownedSections)
        } else {
            emptySet()
        }
        if (!local.pend() && dirty.isEmpty()) return UserdataPushResult.Skipped

        // web 同款特例：power 变更时强制带上 power（选完宿舍立刻退出也不丢）。
        val forceKeys = if (dirty.contains("power")) setOf("power") else emptySet()
        return push(force = true, forceKeys = forceKeys)
    }

    // ------------------------------------------------------------ 云同步开关

    override suspend fun setSyncEnabled(enabled: Boolean) {
        local.setSyncEnabled(enabled)
        if (!enabled) {
            debounceJob?.cancel()
            debounceJob = null
            return
        }
        // 恢复：先补推待发，再拉一次（契约 §4.3）。
        if (local.pend()) {
            push(force = true)
        } else {
            pull(force = true)
        }
    }

    override suspend fun syncEnabled(): Boolean = syncAllowed() && local.syncEnabled()

    override suspend fun cachedBlob(): JsonObject? = local.readCache()

    override suspend fun hydrateFromLocal() {
        currentMutex.withLock {
            val sid = sidOrNull() ?: return
            if (sectionsSid == sid && _currentSections.value.isNotEmpty()) return
            val stored = local.readCurrent()
            if (sid != sidOrNull()) return
            sectionsSid = sid
            publish(if (stored.isEmpty()) stored else UserdataV6.sectionsOf(stored))
        }
    }

    // ------------------------------------------------------------ 内部工具

    private suspend fun persistApplied(merged: JsonObject, retain: Map<String, JsonElement> = emptyMap()) {
        val sid = sidOrNull()
        val sections = JsonObject(UserdataV6.sectionsOf(merged) + retain)
        local.writeCache(merged)
        local.writeCurrent(sections)
        if (sid != sidOrNull()) return
        sectionsSid = sid
        publish(sections)
    }

    private suspend fun fetchRemote(sid: String): ApiResult<JsonObject?> {
        val path = "userdata?select=*&id=eq." + URLEncoder.encode(sid, "UTF-8")
        return when (val result = api.get(path)) {
            is ApiResult.Failure -> ApiResult.Failure(result.error)
            is ApiResult.Success -> {
                val element = parseJsonOrNull(json, result.value)
                if (element == null) {
                    ApiResult.Failure(ApiError.Parse("userdata 响应不是合法 JSON"))
                } else {
                    val array = element as? JsonArray ?: return ApiResult.Success(null)
                    val row = array.firstOrNull()?.asObjectOrNull()
                        ?: return ApiResult.Success(null)
                    val blob = row["blob"].asStringOrNull()
                    val parsed = UserdataV6.decode(blob, json)
                    ApiResult.Success(parsed)
                }
            }
        }
    }

    private fun sectionsDiffer(current: JsonObject, merged: JsonObject): Boolean {
        for (key in UserdataV6.SECTION_KEYS) {
            if (key !in ownedSections) continue
            val currentValue = current[key]?.takeUnless { it is kotlinx.serialization.json.JsonNull }
                ?: UserdataV6.defaultSection(key)
            if (currentValue != merged[key]) return true
        }
        return false
    }

    private fun sidOrNull(): String? = currentSid()?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        const val USERDATA_PATH: String = "userdata"
        const val UPSERT_PREFER: String = "return=representation,resolution=merge-duplicates"
    }
}
