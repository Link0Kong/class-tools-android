package com.furina.classtools.features.ppt

/**
 * 演示（PPT）上传的硬边界与分片计划（契约：web/ppt/CONTRACT.md §3.2/§4.2/§8.1）。
 *
 * 纯 Kotlin、无 android.* 依赖，可直接 JVM 单测。
 * - 源文件上限 **64 MiB**（转换机与客户端同一上限）；
 * - 分片固定 **512 KiB 原始字节**（最后一片可小）；
 * - 并行度 ≤4、失败重试 2 次（400/1200ms）由上传器执行；
 * - 标题 ≤80 字。
 */
object PptLimits {

    /** 单片原始字节数（512 KiB）。 */
    const val CHUNK_BYTES: Int = 512 * 1024

    /** 源文件上限（64 MiB），超过直接拒绝上传。 */
    const val MAX_SOURCE_BYTES: Long = 64L * 1024 * 1024

    /** 上传并发度上限（契约 §3.2「并行度 ≤4」）。 */
    const val MAX_CONCURRENCY: Int = 4

    /** 单片失败重试次数（契约 §4.2「重试 2 次」）。 */
    const val RETRY_COUNT: Int = 2

    /** 重试退避（指数：400ms / 1200ms）。 */
    val RETRY_DELAYS_MS: List<Long> = listOf(400L, 1200L)

    /** 标题最大字数。 */
    const val MAX_TITLE_CHARS: Int = 80

    /** 允许的源文件扩展名（小写）。 */
    val ACCEPTED_EXTENSIONS: List<String> = listOf("ppt", "pptx")

    /** 一个待上传分片：[offset, offset+length) 原始字节。 */
    data class PptChunk(val idx: Int, val offset: Long, val length: Int)

    /**
     * 按固定分片大小切分文件（最后一片可小）。
     * 空文件/非法大小 → 空列表（调用方应先拒绝 0 字节文件）。
     */
    fun chunks(totalBytes: Long, chunkBytes: Int = CHUNK_BYTES): List<PptChunk> {
        require(chunkBytes > 0) { "chunkBytes must be > 0" }
        if (totalBytes <= 0L) return emptyList()
        val cb = chunkBytes.toLong()
        val count = ((totalBytes + cb - 1) / cb).toInt()
        val out = ArrayList<PptChunk>(count)
        var offset = 0L
        var idx = 0
        while (offset < totalBytes) {
            val len = minOf(cb, totalBytes - offset).toInt()
            out.add(PptChunk(idx = idx, offset = offset, length = len))
            offset += len
            idx += 1
        }
        return out
    }

    /** 分片数（上传建行时写 chunk_count）。 */
    fun chunkCount(totalBytes: Long, chunkBytes: Int = CHUNK_BYTES): Int =
        chunks(totalBytes, chunkBytes).size

    /** 是否超过 64 MiB 上限。 */
    fun exceedsLimit(totalBytes: Long): Boolean = totalBytes > MAX_SOURCE_BYTES

    /** 大小是否可用于上传（1B..64MiB）。 */
    fun isValidSize(totalBytes: Long): Boolean = totalBytes in 1..MAX_SOURCE_BYTES

    /** 文件名是否以 .ppt/.pptx 结尾（大小写不敏感）。 */
    fun isAcceptedFileName(name: String?): Boolean {
        val n = name?.trim().orEmpty()
        val dot = n.lastIndexOf('.')
        if (dot <= 0 || dot == n.lastIndex) return false
        return n.substring(dot + 1).lowercase() in ACCEPTED_EXTENSIONS
    }

    /** 上传格式错误提示（UI 直接展示）。 */
    fun rejectReason(totalBytes: Long, fileName: String?): String? = when {
        totalBytes <= 0L -> "文件为空，无法上传"
        exceedsLimit(totalBytes) -> "文件超过 64 MB，请压缩后再上传"
        !isAcceptedFileName(fileName) -> "仅支持 .ppt / .pptx 文件"
        else -> null
    }

    /** 标题清洗：压平换行/连续空白、去首尾、截断 80 字；空 → 未命名演示。 */
    fun sanitizeTitle(raw: String?): String {
        val flat = raw.orEmpty()
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace('\t', ' ')
            .trim()
            .replace(Regex(" {2,}"), " ")
        val clipped = if (flat.length <= MAX_TITLE_CHARS) flat else flat.take(MAX_TITLE_CHARS).trimEnd()
        return clipped.ifBlank { DEFAULT_TITLE }
    }

    /** 默认标题 = 文件名去扩展名（契约 §3.1），再走 [sanitizeTitle]。 */
    fun titleFromFileName(fileName: String?): String {
        val name = fileName?.trim().orEmpty()
        if (name.isEmpty()) return DEFAULT_TITLE
        val dot = name.lastIndexOf('.')
        val base = when {
            dot > 0 -> name.substring(0, dot)
            dot == 0 -> "" // 形如 ".pptx" 只有扩展名，无主名
            else -> name
        }
        return sanitizeTitle(base)
    }

    /** 字节数格式化（列表/进度条辅助文案）。 */
    fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "—"
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(java.util.Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(java.util.Locale.US, "%.1f MB", mb)
    }

    /** 无标题兜底。 */
    const val DEFAULT_TITLE: String = "未命名演示"
}
