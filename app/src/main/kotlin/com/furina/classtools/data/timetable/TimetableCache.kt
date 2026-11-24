package com.furina.classtools.data.timetable

import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.parseJsonOrNull
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 课表本机缓存（落盘）。
 *
 * 契约：`site_assets` 拉取失败 → 回退本机缓存（docs/api/ENDPOINTS.md E24）；
 * 缓存带 `savedAt`，仓库据此做 6h TTL。文件损坏/缺失一律返回 null（不崩溃）。
 */
class FileTimetableCache(
    private val directory: File,
    private val json: Json = NetJson,
    private val onChanged: () -> Unit = {},
) {
    private val file: File = File(directory, CACHE_FILE_NAME)

    @Serializable
    data class Envelope(
        val savedAt: Long = 0L,
        val snapshot: TtSnapshot = TtSnapshot(),
    )

    data class Cached(val snapshot: TtSnapshot, val savedAt: Long)

    fun read(): Cached? {
        if (!file.exists()) return null
        return runCatching {
            val text = file.readText(Charsets.UTF_8)
            val element = parseJsonOrNull(json, text) ?: return null
            val envelope = json.decodeFromString(Envelope.serializer(), element.toString())
            Cached(snapshot = envelope.snapshot, savedAt = envelope.savedAt)
        }.getOrNull()
    }

    fun write(snapshot: TtSnapshot, savedAt: Long) {
        runCatching {
            if (!directory.exists()) directory.mkdirs()
            val envelope = Envelope(savedAt = savedAt, snapshot = snapshot)
            file.writeText(json.encodeToString(Envelope.serializer(), envelope), Charsets.UTF_8)
            onChanged()
        }
    }

    fun clear() {
        runCatching { if (file.delete()) onChanged() }
    }

    companion object {
        const val CACHE_FILE_NAME: String = "tt_snapshot.json"
    }
}
