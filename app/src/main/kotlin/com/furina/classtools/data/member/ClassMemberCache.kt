package com.furina.classtools.data.member

import com.furina.classtools.data.network.NetJson
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 落盘缓存结构：`{ "savedAt": <ms>, "members": [...] }`。 */
@Serializable
data class ClassMemberCacheFile(
    val savedAt: Long = 0L,
    val members: List<ClassMember> = emptyList(),
)

/**
 * 班级名单落盘缓存（按 sid 隔离）。
 *
 * 目录建议：`File(context.cacheDir, "class_members")`；文件：`members_<sid>.json`。
 * 只缓存名单（班级内部公开信息），不含 token；退出身份时由调用方 [clear] 删除。
 */
class FileClassMemberCache(
    private val directory: File,
    private val json: Json = NetJson,
) {
    private fun fileFor(sid: String): File {
        val safeSid = sid.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        return File(directory, "members_$safeSid.json")
    }

    suspend fun read(sid: String): ClassMemberCacheFile? = withContext(Dispatchers.IO) {
        if (sid.isBlank()) return@withContext null
        val file = fileFor(sid)
        if (!file.isFile) return@withContext null
        try {
            json.decodeFromString(ClassMemberCacheFile.serializer(), file.readText())
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun write(sid: String, members: List<ClassMember>, savedAt: Long) {
        if (sid.isBlank()) return
        withContext(Dispatchers.IO) {
            try {
                directory.mkdirs()
                val file = fileFor(sid)
                file.writeText(
                    json.encodeToString(
                        ClassMemberCacheFile.serializer(),
                        ClassMemberCacheFile(savedAt = savedAt, members = members),
                    ),
                )
            } catch (_: Throwable) {
                // 缓存写失败不影响主流程（契约：localStorage 只作缓存）。
            }
        }
    }

    suspend fun clear(sid: String) {
        if (sid.isBlank()) return
        withContext(Dispatchers.IO) {
            try {
                fileFor(sid).delete()
            } catch (_: Throwable) {
                // ignore
            }
        }
    }
}
