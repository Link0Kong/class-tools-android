package com.furina.classtools.features.ppt

import com.furina.classtools.data.ppt.PptManifest
import com.furina.classtools.data.ppt.PptMediaFile
import com.furina.classtools.data.ppt.PptMediaKind

/**
 * v2.3 §12.8「视频/音频单独下载」纯逻辑：行模型（序号/页号/时长/分辨率/大小/MIME/文件名/URL）。
 *
 * 纯 Kotlin（无 android.*）→ 全部 JVM 单测；Android 层复用 `PptFileDownloader` 下载后
 * `ACTION_VIEW`（video/… 或 audio/… MIME）打开或分享。
 */
object PptMediaDownloadLogic {

    /** 单独下载分组标题（契约 §12.8 固定文案）。 */
    const val GROUP_TITLE: String = "视频单独下载"

    const val FALLBACK_BASE: String = "演示文件"

    /** MIME（按扩展名映射；未知按 kind 兜底，ACTION_VIEW 用）。 */
    const val MIME_VIDEO_MP4: String = "video/mp4"
    const val MIME_VIDEO_WEBM: String = "video/webm"
    const val MIME_VIDEO_QUICKTIME: String = "video/quicktime"
    const val MIME_AUDIO_MP4: String = "audio/mp4"
    const val MIME_AUDIO_MPEG: String = "audio/mpeg"
    const val MIME_AUDIO_AAC: String = "audio/aac"

    /** 详情页分组里的一行（视频1 · 第 7 页 + 时长/分辨率 + 大小）。 */
    data class MediaRow(
        /** 稳定的下载状态键（视频/音频各自从 1 编号：`video_1`、`audio_2`）。 */
        val key: String,
        val kind: PptMediaKind,
        /** 同类型内序号（1 基，与转换机文件名 `_视频1_第7页` 对齐）。 */
        val index: Int,
        /** 行标题：`视频1 · 第 7 页` / `视频1`（无页号）。 */
        val title: String,
        /** 副标题：`02:30 · 1024×576`（音频只显示时长；未知省略）。 */
        val detail: String,
        val sizeBytes: Long,
        val sizeText: String,
        val fileName: String,
        val mimeType: String,
        val url: String,
        val durationSec: Double,
        /** 关联页号（已去重升序；空 = 未知）。 */
        val pages: List<Int>,
    )

    /** 是否有可展示的媒体行（空/缺失 → 不显示分组，向后兼容旧 deck）。 */
    fun hasRows(manifest: PptManifest): Boolean = manifest.mediaFiles.isNotEmpty()

    /**
     * 构建分组行（按 manifest.media_files 顺序；视频/音频分别从 1 编号）。
     *
     * @param deckTitle deck 标题（文件名兜底用）
     */
    fun rows(manifest: PptManifest, deckTitle: String): List<MediaRow> {
        if (manifest.mediaFiles.isEmpty()) return emptyList()
        var videoIndex = 0
        var audioIndex = 0
        val out = ArrayList<MediaRow>(manifest.mediaFiles.size)
        for (info in manifest.mediaFiles) {
            val index = if (info.kind == PptMediaKind.AUDIO) {
                audioIndex += 1
                audioIndex
            } else {
                videoIndex += 1
                videoIndex
            }
            val ext = extensionOf(info.name).ifBlank { extensionOf(info.src) }
            val mime = mimeTypeOf(info.kind, ext)
            val name = suggestedFileName(deckTitle, info, index, ext)
            out.add(
                MediaRow(
                    key = "${info.kind.name.lowercase()}_$index",
                    kind = info.kind,
                    index = index,
                    title = titleOf(info.kind, index, info.slides),
                    detail = detailOf(info),
                    sizeBytes = info.size,
                    sizeText = if (info.size > 0L) PptLimits.formatBytes(info.size) else "—",
                    fileName = name,
                    mimeType = mime,
                    url = manifest.mediaUrl(info),
                    durationSec = info.durationSec,
                    pages = pageNumbers(info.slides),
                ),
            )
        }
        return out
    }

    /** 行标题：`视频1 · 第 7 页` / `视频1`；多页 `第7、12页`。 */
    fun titleOf(kind: PptMediaKind, index: Int, slides: List<Int>): String {
        val prefix = kindLabel(kind) + index
        val pageText = pageLabel(slides)
        return if (pageText.isBlank()) prefix else "$prefix · $pageText"
    }

    /** 类型中文标签：视频 / 音频。 */
    fun kindLabel(kind: PptMediaKind): String =
        if (kind == PptMediaKind.AUDIO) "音频" else "视频"

    /** 页号展示：`第 7 页` / `第7、12页` / 空。0 视为第 1 页（转换机写 1 基，容错旧 0 基）。 */
    fun pageLabel(slides: List<Int>): String {
        val pages = pageNumbers(slides)
        if (pages.isEmpty()) return ""
        return "第" + pages.joinToString("、") + "页"
    }

    /** 规范化页号：去重升序、负数丢弃；0 → 1。 */
    fun pageNumbers(slides: List<Int>): List<Int> =
        slides.map { if (it <= 0) 1 else it }.distinct().sorted()

    /** 副标题：`02:30 · 1024×576`（音频 `03:45`；时长/分辨率未知则省略对应部分）。 */
    fun detailOf(info: PptMediaFile): String {
        val parts = ArrayList<String>(2)
        if (info.durationSec > 0.0) parts.add(PptVideoLogic.formatTime(info.durationSec))
        if (info.kind == PptMediaKind.VIDEO && info.width > 0 && info.height > 0) {
            parts.add("${info.width}×${info.height}")
        }
        return parts.joinToString(" · ")
    }

    /** 扩展名（小写，无点；空 → ""）。 */
    fun extensionOf(fileName: String?): String {
        val name = fileName?.trim().orEmpty()
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.lastIndex) return ""
        return name.substring(dot + 1).lowercase()
    }

    /** 下载后打开用的 MIME（系统播放器/WPS）。 */
    fun mimeTypeOf(kind: PptMediaKind, ext: String): String = when (ext.trim().lowercase().removePrefix(".")) {
        "mp4", "m4v" -> MIME_VIDEO_MP4
        "webm" -> MIME_VIDEO_WEBM
        "mov" -> MIME_VIDEO_QUICKTIME
        "m4a" -> MIME_AUDIO_MP4
        "mp3" -> MIME_AUDIO_MPEG
        "aac" -> MIME_AUDIO_AAC
        "wav" -> "audio/wav"
        "ogg", "flac" -> "audio/${ext.trim().lowercase().removePrefix(".")}"
        else -> if (kind == PptMediaKind.AUDIO) MIME_AUDIO_MP4 else MIME_VIDEO_MP4
    }

    /**
     * 建议下载文件名：优先 manifest `name`；否则 `<标题>_视频N_第X页.<ext>`。
     * 统一清洗非法字符、缺扩展名时补（视频 mp4 / 音频 m4a）。
     */
    fun suggestedFileName(deckTitle: String, info: PptMediaFile, index: Int, ext: String): String {
        val fallbackExt = ext.ifBlank {
            if (info.kind == PptMediaKind.AUDIO) "m4a" else "mp4"
        }
        val fromManifest = info.name.trim()
        if (fromManifest.isNotEmpty()) {
            return PptDownloadLogic.ensureExtension(
                PptDownloadLogic.sanitizeFileName(fromManifest),
                extensionOf(fromManifest).ifBlank { fallbackExt },
            )
        }
        val base = PptDownloadLogic.sanitizeFileName(deckTitle).ifBlank { FALLBACK_BASE }
        val page = pageNumbers(info.slides).firstOrNull()
        val pagePart = if (page != null) "_第${page}页" else ""
        return PptDownloadLogic.ensureExtension(
            "${base}_${kindLabel(info.kind)}$index$pagePart",
            fallbackExt,
        )
    }

    /**
     * 分组是否需要展示（旧 deck 无 media_files → false；有但全部坏项也 false）。
     * 与 [hasRows] 的区别：rows 需要 deck 标题，本函数只看 manifest 数据。
     */
    fun shouldShowGroup(manifest: PptManifest): Boolean = manifest.mediaFiles.isNotEmpty()

    /** 分组计数文案（如「2 个视频 · 1 个音频」；空 → 空串）。 */
    fun summaryText(rows: List<MediaRow>): String {
        if (rows.isEmpty()) return ""
        val videos = rows.count { it.kind == PptMediaKind.VIDEO }
        val audios = rows.count { it.kind == PptMediaKind.AUDIO }
        val parts = ArrayList<String>(2)
        if (videos > 0) parts.add("$videos 个视频")
        if (audios > 0) parts.add("$audios 个音频")
        return parts.joinToString(" · ")
    }
}
