package com.furina.classtools.data.sync

import com.furina.classtools.data.network.NetJson
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * userdata 本机持久层（按 sid 隔离）：
 * - `cache`  = 最近一次合并结果（脏检测基线，契约 §4.3；对应网页 `zq_udcache_<sid>`、小程序 `store.udCache`）；
 * - `current`= 本机各 section 当前值（业务层写入，对应网页各 `D.d.*` / 小程序 `store.*`）；
 * - `pend` + `pendingKeys` = 待补发标记（对应 `zq_udpend_<sid>` / `store.udPend`）；
 * - `lastPullAt` = 上次成功拉取时间（60s TTL）；`holdUntil` = 清除云端后的抑制期；`syncEnabled` = 云同步开关。
 *
 * 接口化是为了单测注入内存实现（[InMemoryUserDataLocalStore]）与后续换 Room/DataStore。
 */
interface UserDataLocalStore {
    suspend fun readCache(): JsonObject?
    suspend fun writeCache(blob: JsonObject)

    suspend fun readCurrent(): JsonObject
    suspend fun writeCurrent(sections: JsonObject)

    suspend fun pend(): Boolean
    suspend fun setPend(value: Boolean)
    suspend fun pendingKeys(): Set<String>
    suspend fun markPending(keys: Collection<String>)
    suspend fun clearPending()

    suspend fun lastPullAt(): Long
    suspend fun setLastPullAt(at: Long)

    suspend fun holdUntil(): Long
    suspend fun setHoldUntil(at: Long)

    suspend fun syncEnabled(): Boolean
    suspend fun setSyncEnabled(enabled: Boolean)

    suspend fun clear()
}

/** 落盘结构（单文件一 sid；cache/current 直接存 JSON 文本，避免嵌套序列化边界问题）。 */
@Serializable
data class UserDataStoreFile(
    val cacheJson: String? = null,
    val currentJson: String? = null,
    val pendingKeys: List<String> = emptyList(),
    val pend: Boolean = false,
    val lastPullAt: Long = 0L,
    val holdUntil: Long = 0L,
    val syncEnabled: Boolean = true,
)

/**
 * 文件实现：`<directory>/userdata_<sid>.json`。
 * 目录建议 `File(context.filesDir, "userdata")`（**不要放 cacheDir**：系统可能清缓存导致 pending/基线丢失）。
 * 写入失败静默（本机缓存语义：失败不影响业务，下一次再写）。
 */
class FileUserDataLocalStore(
    private val directory: File,
    private val sidProvider: () -> String?,
    private val json: Json = NetJson,
    private val commit: (() -> Unit) -> Unit = { it() },
) : UserDataLocalStore {

    private val mutex = Mutex()

    private fun currentSid(): String = sidProvider()?.trim().orEmpty()

    private fun fileFor(sid: String): File {
        val safeSid = sid.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        return File(directory, "userdata_$safeSid.json")
    }

    // ---- 阻塞文件操作：只在 Dispatchers.IO + mutex 内调用 ----

    private fun readFileBlocking(): UserDataStoreFile {
        val sid = currentSid()
        if (sid.isEmpty()) return UserDataStoreFile()
        val file = fileFor(sid)
        if (!file.isFile) return UserDataStoreFile()
        return try {
            json.decodeFromString(UserDataStoreFile.serializer(), file.readText())
        } catch (_: Throwable) {
            UserDataStoreFile()
        }
    }

    private fun updateBlocking(transform: (UserDataStoreFile) -> UserDataStoreFile) = commit {
        val sid = currentSid()
        if (sid.isEmpty()) return@commit
        try {
            directory.mkdirs()
            val next = transform(readFileBlocking())
            fileFor(sid).writeText(json.encodeToString(UserDataStoreFile.serializer(), next))
        } catch (_: Throwable) {
            // 静默：本机持久化失败不影响业务流程
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { block() }
    }

    override suspend fun readCache(): JsonObject? = io {
        UserdataV6.decode(readFileBlocking().cacheJson, json)
    }

    override suspend fun writeCache(blob: JsonObject) {
        val text = UserdataV6.encode(blob, json)
        io { updateBlocking { it.copy(cacheJson = text) } }
    }

    override suspend fun readCurrent(): JsonObject = io {
        UserdataV6.decode(readFileBlocking().currentJson, json) ?: JsonObject(emptyMap())
    }

    override suspend fun writeCurrent(sections: JsonObject) {
        val text = UserdataV6.encode(sections, json)
        io { updateBlocking { it.copy(currentJson = text) } }
    }

    override suspend fun pend(): Boolean = io { readFileBlocking().pend }

    override suspend fun setPend(value: Boolean) {
        io { updateBlocking { it.copy(pend = value) } }
    }

    override suspend fun pendingKeys(): Set<String> = io { readFileBlocking().pendingKeys.toSet() }

    override suspend fun markPending(keys: Collection<String>) {
        io {
            updateBlocking { file ->
                file.copy(
                    pend = true,
                    pendingKeys = (file.pendingKeys + keys.filter { it.isNotBlank() }).distinct(),
                )
            }
        }
    }

    override suspend fun clearPending() {
        io { updateBlocking { it.copy(pend = false, pendingKeys = emptyList()) } }
    }

    override suspend fun lastPullAt(): Long = io { readFileBlocking().lastPullAt }

    override suspend fun setLastPullAt(at: Long) {
        io { updateBlocking { it.copy(lastPullAt = at) } }
    }

    override suspend fun holdUntil(): Long = io { readFileBlocking().holdUntil }

    override suspend fun setHoldUntil(at: Long) {
        io { updateBlocking { it.copy(holdUntil = at) } }
    }

    override suspend fun syncEnabled(): Boolean = io { readFileBlocking().syncEnabled }

    override suspend fun setSyncEnabled(enabled: Boolean) {
        io { updateBlocking { it.copy(syncEnabled = enabled) } }
    }

    override suspend fun clear() {
        io {
            val sid = currentSid()
            if (sid.isNotEmpty()) {
                try {
                    commit { fileFor(sid).delete() }
                } catch (_: Throwable) {
                    // ignore
                }
            }
        }
    }
}

/** 内存实现：单测 / Debug 预览用（进程内有效，不落盘）。 */
class InMemoryUserDataLocalStore(
    cache: JsonObject? = null,
    current: JsonObject = JsonObject(emptyMap()),
    syncEnabled: Boolean = true,
) : UserDataLocalStore {
    private val mutex = Mutex()
    private var cache: JsonObject? = cache
    private var current: JsonObject = current
    private var pend: Boolean = false
    private var pendingKeys: Set<String> = emptySet()
    private var lastPullAt: Long = 0L
    private var holdUntil: Long = 0L
    private var syncEnabled: Boolean = syncEnabled

    override suspend fun readCache(): JsonObject? = mutex.withLock { cache }
    override suspend fun writeCache(blob: JsonObject) = mutex.withLock { cache = blob }

    override suspend fun readCurrent(): JsonObject = mutex.withLock { current }
    override suspend fun writeCurrent(sections: JsonObject) = mutex.withLock { current = sections }

    override suspend fun pend(): Boolean = mutex.withLock { pend }
    override suspend fun setPend(value: Boolean) = mutex.withLock { pend = value }
    override suspend fun pendingKeys(): Set<String> = mutex.withLock { pendingKeys }
    override suspend fun markPending(keys: Collection<String>) = mutex.withLock {
        pend = true
        pendingKeys = pendingKeys + keys
    }

    override suspend fun clearPending() = mutex.withLock {
        pend = false
        pendingKeys = emptySet()
    }

    override suspend fun lastPullAt(): Long = mutex.withLock { lastPullAt }
    override suspend fun setLastPullAt(at: Long) = mutex.withLock { lastPullAt = at }

    override suspend fun holdUntil(): Long = mutex.withLock { holdUntil }
    override suspend fun setHoldUntil(at: Long) = mutex.withLock { holdUntil = at }

    override suspend fun syncEnabled(): Boolean = mutex.withLock { syncEnabled }
    override suspend fun setSyncEnabled(enabled: Boolean) = mutex.withLock { syncEnabled = enabled }

    override suspend fun clear() = mutex.withLock {
        cache = null
        current = JsonObject(emptyMap())
        pend = false
        pendingKeys = emptySet()
        lastPullAt = 0L
        holdUntil = 0L
    }
}
