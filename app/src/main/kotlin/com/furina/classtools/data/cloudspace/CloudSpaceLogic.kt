package com.furina.classtools.data.cloudspace

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 临时云空间的纯逻辑（无 Android / 无网络依赖，JVM 单测直接覆盖）：
 * 分片规划、大小/时间/剩余 TTL 文案、上传百分比、文件名清洗与去重、文件类型图标、事件合并。
 *
 * 与 `mp-class-tools/utils/cloudspace.js` 保持同一口径（格式可读性优先，不追求逐字节一致）。
 */
object CloudSpaceLogic {

    /** 分片规划：`[{index,start,endExclusive}]`，最后一片可小于 [chunkSize]。 */
    data class Chunk(
        val index: Int,
        val start: Long,
        val endExclusive: Long,
    ) {
        val size: Long get() = (endExclusive - start).coerceAtLeast(0L)
    }

    data class ChunkPlan(
        val totalChunks: Int,
        val chunkSize: Int,
        val chunks: List<Chunk>,
    )

    /**
     * 计算分片计划。
     *
     * - 空文件（size = 0）按 1 个 0 字节分片处理（与服务端 `totalChunks >= 1` 的约束一致；
     *   业务层在 [CloudSpaceRepository] 会拒绝空文件上传，这里保持纯函数语义完整）；
     * - [chunkSize] <= 0 时回退到 [CloudSpaceDefaults.CHUNK_SIZE]。
     */
    fun chunkPlan(sizeBytes: Long, chunkSize: Int = CloudSpaceDefaults.CHUNK_SIZE): ChunkPlan {
        val size = sizeBytes.coerceAtLeast(0L)
        val cs = if (chunkSize > 0) chunkSize else CloudSpaceDefaults.CHUNK_SIZE
        val totalLong = if (size == 0L) 1L else (size + cs - 1L) / cs
        val totalChunks = totalLong.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val chunks = ArrayList<Chunk>(totalChunks)
        for (index in 0 until totalChunks) {
            val start = index.toLong() * cs
            val end = (start + cs).coerceAtMost(size)
            chunks.add(Chunk(index = index, start = start, endExclusive = end))
        }
        return ChunkPlan(totalChunks = totalChunks, chunkSize = cs, chunks = chunks)
    }

    // ---------------------------------------------------------------- 文案格式

    /** 文件大小：B / KB / MB（≥10MB 不带小数，其余 1 位小数；与小程序口径一致）。 */
    fun fmtSize(bytes: Long): String {
        val n = bytes.coerceAtLeast(0L)
        return when {
            n == 0L -> "0 B"
            n >= 1024L * 1024L -> {
                val digits = if (n >= 10L * 1024L * 1024L) 0 else 1
                String.format(Locale.US, "%.${digits}f MB", n / (1024.0 * 1024.0))
            }

            n >= 1024L -> String.format(Locale.US, "%.1f KB", n / 1024.0)
            else -> "$n B"
        }
    }

    private val TIME_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)

    /** 本地时间 `MM-dd HH:mm`（与网页/小程序一致）；非法时间返回空串。 */
    fun fmtTime(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        if (epochMs <= 0L) return ""
        return runCatching {
            Instant.ofEpochMilli(epochMs).atZone(zoneId).format(TIME_FORMATTER)
        }.getOrDefault("")
    }

    /**
     * 剩余保留时间（自上传完成起 72h）：`剩 N 天 M 小时` / `剩 N 小时` / `已过期`。
     * 小时向上取整（与网页/小程序一致）。
     */
    fun ttlText(uploadedAt: Long, now: Long): String {
        if (uploadedAt <= 0L) return "已过期"
        val left = uploadedAt + CloudSpaceDefaults.FILE_TTL_MS - now
        if (left <= 0L) return "已过期"
        val hours = (left + 3_600_000L - 1L) / 3_600_000L
        return if (hours >= 24L) {
            val days = hours / 24L
            val rest = hours % 24L
            if (rest > 0L) "剩 $days 天 $rest 小时" else "剩 $days 天"
        } else {
            "剩 $hours 小时"
        }
    }

    /** 上传中百分比：0..99（100 由「完成」状态给出，避免提前显示 100%）。 */
    fun uploadPercent(sentBytes: Long, totalBytes: Long): Int {
        if (totalBytes <= 0L) return 0
        val sent = sentBytes.coerceIn(0L, totalBytes)
        return ((sent * 100L) / totalBytes).toInt().coerceIn(0, 99)
    }

    /** 已过期文件提示（与网页/小程序同文案）。 */
    fun expiredHint(count: Int): String =
        "$count 个文件已过期（上传超过 72 小时，已自动删除）"

    // ---------------------------------------------------------------- 文件名

    private val ILLEGAL_FILE_NAME_CHARS = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")
    private const val MAX_FILE_NAME_LENGTH = 120

    /** 文件名清洗：去路径分隔符/控制字符、限长（保留扩展名）；空名回退 `file`。 */
    fun safeName(rawName: String?): String {
        val cleaned = ILLEGAL_FILE_NAME_CHARS
            .replace(rawName.orEmpty(), "_")
            .trim()
            .trim('.')
        val name = cleaned.ifBlank { "file" }
        if (name.length <= MAX_FILE_NAME_LENGTH) return name
        val dot = name.lastIndexOf('.')
        return if (dot > 0 && name.length - dot <= 12) {
            val ext = name.substring(dot)
            name.take(MAX_FILE_NAME_LENGTH - ext.length) + ext
        } else {
            name.take(MAX_FILE_NAME_LENGTH)
        }
    }

    /** 同名文件去重：`名称 (1).ext`、`名称 (2).ext`…（目录内已存在时递增）。 */
    fun uniqueName(existingNames: Collection<String>, desiredName: String): String {
        val existing = existingNames.toSet()
        if (desiredName !in existing) return desiredName
        val dot = desiredName.lastIndexOf('.')
        val base = if (dot > 0) desiredName.substring(0, dot) else desiredName
        val ext = if (dot > 0) desiredName.substring(dot) else ""
        var index = 1
        while (index <= 9_999) {
            val candidate = "$base ($index)$ext"
            if (candidate !in existing) return candidate
            index += 1
        }
        return desiredName
    }

    // ---------------------------------------------------------------- 文件类型

    enum class FileKind { IMAGE, VIDEO, AUDIO, ARCHIVE, PDF, DOCUMENT, OTHER }

    private val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif", "svg")
    private val VIDEO_EXTS = setOf("mp4", "mov", "m4v", "mkv", "webm", "avi", "3gp", "ts")
    private val AUDIO_EXTS = setOf("mp3", "m4a", "wav", "aac", "ogg", "flac")
    private val ARCHIVE_EXTS = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
    private val DOCUMENT_EXTS = setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv")

    /** 按 MIME 优先、扩展名兜底判断文件类型（只用于列表图标）。 */
    fun fileKind(mimeType: String, name: String): FileKind {
        val mime = mimeType.trim().lowercase(Locale.US)
        val ext = name.substringAfterLast('.', "").trim().lowercase(Locale.US)
        return when {
            mime.startsWith("image/") || ext in IMAGE_EXTS -> FileKind.IMAGE
            mime.startsWith("video/") || ext in VIDEO_EXTS -> FileKind.VIDEO
            mime.startsWith("audio/") || ext in AUDIO_EXTS -> FileKind.AUDIO
            ext in ARCHIVE_EXTS -> FileKind.ARCHIVE
            mime == "application/pdf" || ext == "pdf" -> FileKind.PDF
            ext in DOCUMENT_EXTS -> FileKind.DOCUMENT
            else -> FileKind.OTHER
        }
    }

    // ---------------------------------------------------------------- 事件合并

    data class MergedFiles(
        val files: List<CloudSpaceFile>,
        val expiredIds: Set<String>,
    ) {
        val expiredCount: Int get() = expiredIds.size
    }

    /**
     * 合并 `/state` 事件到当前列表（`since > 0` 的增量合并；`since = 0` 时调用方传空列表重建）。
     *
     * - `file` 事件：未过期 → 覆盖/加入列表（同 id 去重，刷新 downloadUrl）；已过期 → 从列表移除并计入过期数；
     * - `filedel` 事件：从列表移除，同时从过期集合移除（已手动删除的文件不算「已过期」）；
     * - 输出按上传时间倒序（新文件在前），同时间按 id 稳定排序。
     */
    fun applyEvents(
        currentFiles: List<CloudSpaceFile>,
        currentExpiredIds: Set<String>,
        events: List<CloudSpaceEvent>,
    ): MergedFiles {
        val byId = LinkedHashMap<String, CloudSpaceFile>()
        currentFiles.forEach { byId[it.id] = it }
        val expiredIds = currentExpiredIds.toMutableSet()
        events.sortedBy { it.seq }.forEach { event ->
            when (event) {
                is CloudSpaceEvent.FileAdded -> {
                    val file = event.file
                    if (file.expired) {
                        byId.remove(file.id)
                        expiredIds.add(file.id)
                    } else {
                        byId[file.id] = file
                        expiredIds.remove(file.id)
                    }
                }

                is CloudSpaceEvent.FileDeleted -> {
                    byId.remove(event.fileId)
                    expiredIds.remove(event.fileId)
                }
            }
        }
        val files = byId.values.sortedWith(
            compareByDescending<CloudSpaceFile> { it.uploadedAt }.thenBy { it.id },
        )
        return MergedFiles(files = files, expiredIds = expiredIds)
    }
}
