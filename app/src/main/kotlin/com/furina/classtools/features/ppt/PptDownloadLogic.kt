package com.furina.classtools.features.ppt

import com.furina.classtools.data.ppt.PptDeck
import com.furina.classtools.data.ppt.PptDeckDetail
import com.furina.classtools.data.ppt.PptFileInfo
import com.furina.classtools.data.ppt.PptManifest

/**
 * v2.0 下载中心纯逻辑（契约 §12.4）：下载目标/文件名/大小/打开 MIME/兼容提示/旧 deck 判定。
 *
 * 纯 Kotlin（无 android.*）→ 全部 JVM 单测；Android 层只负责 DownloadManager/Intent 执行。
 */
object PptDownloadLogic {

    /** 三个下载目标（顺序 = 详情页展示顺序）。 */
    enum class Kind(val key: String, val label: String) {
        COMPAT("compat", "兼容版 PPTX"),
        PDF("pdf", "PDF"),
        SOURCE("source", "原始文件"),
    }

    /** 详情页一行下载项。 */
    data class DownloadRow(
        val kind: Kind,
        val available: Boolean,
        val url: String,
        val fileName: String,
        val sizeBytes: Long,
        val sizeText: String,
        /** 副标题：PDF 页数 / 扩展名（如 "PPTX"）/ 空。 */
        val subtitle: String,
        val mimeType: String,
    )

    const val MIME_PDF: String = "application/pdf"
    const val MIME_PPTX: String = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    const val MIME_PPT: String = "application/vnd.ms-powerpoint"
    const val MIME_BINARY: String = "application/octet-stream"
    const val FALLBACK_FILE_NAME: String = "演示文件"

    /** 兜底下载文件名（无扩展名时用）。 */
    fun fallbackFileName(deckTitle: String, kind: Kind): String {
        val base = sanitizeFileName(deckTitle).ifBlank { FALLBACK_FILE_NAME }
        return when (kind) {
            Kind.COMPAT -> "$base.pptx"
            Kind.PDF -> "$base.pdf"
            Kind.SOURCE -> base
        }
    }

    /**
     * 构建详情页三行下载项（顺序固定 compat/pdf/source）。
     *
     * - v2 新 deck：来自 `manifest.files`（缺失的文件行 available=false）；
     * - 旧 deck（无 files）：compat/pdf 未生成，source 回退到旧 `manifest.src`（v1 原件），仍可下载原始文件。
     */
    fun rows(detail: PptDeckDetail): List<DownloadRow> {
        val manifest = detail.manifest
        val files = manifest.files
        return listOf(
            buildRow(Kind.COMPAT, files?.compat, manifest, detail, legacyFallback = null),
            buildRow(Kind.PDF, files?.pdf, manifest, detail, legacyFallback = null),
            buildRow(
                Kind.SOURCE,
                files?.source,
                manifest,
                detail,
                legacyFallback = if (files?.source == null && manifest.src.isNotBlank()) {
                    PptFileInfo(
                        src = manifest.src,
                        size = detail.sourceSize,
                        ext = extensionOf(detail.sourceName),
                        name = detail.sourceName,
                    )
                } else {
                    null
                },
            ),
        )
    }

    private fun buildRow(
        kind: Kind,
        info: PptFileInfo?,
        manifest: PptManifest,
        detail: PptDeckDetail,
        legacyFallback: PptFileInfo?,
    ): DownloadRow {
        val effective = info ?: legacyFallback
        if (effective == null) {
            return DownloadRow(
                kind = kind,
                available = false,
                url = "",
                fileName = fallbackFileName(detail.title, kind),
                sizeBytes = 0L,
                sizeText = "—",
                subtitle = "",
                mimeType = mimeTypeFor(kind, ""),
            )
        }
        val ext = resolvedExtension(kind, effective)
        val url = manifest.fileUrl(effective)
        val fileName = suggestedFileName(kind, effective, detail, ext)
        val size = effective.size
        return DownloadRow(
            kind = kind,
            available = url.isNotBlank() && size >= 0L,
            url = url,
            fileName = fileName,
            sizeBytes = size,
            sizeText = if (size > 0L) PptLimits.formatBytes(size) else "—",
            subtitle = when {
                kind == Kind.PDF && effective.pages > 0 -> "${effective.pages} 页"
                ext.isNotBlank() -> ext.uppercase()
                else -> ""
            },
            mimeType = if (kind == Kind.PDF) MIME_PDF else mimeTypeFor(kind, ext),
        )
    }

    private fun resolvedExtension(kind: Kind, info: PptFileInfo): String {
        val fromInfo = info.ext.trim().lowercase().removePrefix(".")
        if (fromInfo.isNotEmpty()) return fromInfo
        val fromName = extensionOf(info.name)
        if (fromName.isNotEmpty()) return fromName
        return when (kind) {
            Kind.COMPAT -> "pptx"
            Kind.PDF -> "pdf"
            Kind.SOURCE -> ""
        }
    }

    /** 建议下载文件名：优先服务端 `name`，否则按标题+扩展名兜底；统一清洗。 */
    fun suggestedFileName(
        kind: Kind,
        info: PptFileInfo,
        detail: PptDeckDetail,
        ext: String,
    ): String {
        val raw = info.name.trim().ifBlank {
            when (kind) {
                Kind.COMPAT -> "${detail.title.ifBlank { FALLBACK_FILE_NAME }}.pptx"
                Kind.PDF -> "${detail.title.ifBlank { FALLBACK_FILE_NAME }}.pdf"
                Kind.SOURCE -> detail.sourceName.ifBlank {
                    "${detail.title.ifBlank { FALLBACK_FILE_NAME }}${if (ext.isNotBlank()) ".$ext" else ""}"
                }
            }
        }
        return ensureExtension(sanitizeFileName(raw), ext.ifBlank { extensionOf(raw) })
    }

    /** 文件名清洗：去掉路径分隔符/Windows 非法字符/控制字符，压缩空白，限制 120 字。 */
    fun sanitizeFileName(raw: String?): String {
        val text = raw.orEmpty()
            .filter { it.code >= 32 && it != '\u007f' }
            .replace(Regex("[<>:\"|?*\\\\/]+"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
        return if (text.length <= 120) text else text.take(120).trim()
    }

    /** 缺扩展名时补上（已有则不重复）。 */
    fun ensureExtension(name: String, ext: String): String {
        if (ext.isBlank()) return name
        val normalized = ext.trim().lowercase().removePrefix(".")
        if (normalized.isEmpty()) return name
        return if (name.lowercase().endsWith(".$normalized")) name else "$name.$normalized"
    }

    /** 文件扩展名（小写、不含点；无 → 空串）。 */
    fun extensionOf(fileName: String?): String {
        val name = fileName?.trim().orEmpty()
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.lastIndex) return ""
        return name.substring(dot + 1).lowercase()
    }

    /** 查看器 MIME：PDF / PPTX / PPT / 其它二进制。 */
    fun mimeTypeFor(kind: Kind, ext: String): String {
        if (kind == Kind.PDF) return MIME_PDF
        return when (ext.trim().lowercase().removePrefix(".")) {
            "pdf" -> MIME_PDF
            "pptx" -> MIME_PPTX
            "ppt" -> MIME_PPT
            else -> MIME_BINARY
        }
    }

    /** 详情页是否有可下载文件（compat/pdf/source 任一）。 */
    fun hasAnyDownload(detail: PptDeckDetail): Boolean = rows(detail).any { it.available }

    /** 新 deck（有 files）还是旧 deck（无 files → 「兼容版未生成」）。 */
    fun isLegacyDeck(detail: PptDeckDetail): Boolean = detail.manifest.files == null

    /** 是否有旧版在线预览（分步 slides 或原样视频）→「在线预览」入口。 */
    fun canPreview(detail: PptDeckDetail): Boolean = detail.manifest.hasPreview

    /** 兼容提示（橙色逐条）；空列表 = 不展示该区块。 */
    fun warnings(detail: PptDeckDetail): List<String> =
        detail.manifest.compat?.warnings.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /** 列表小标签（§12.4.1：「兼容版」/「PDF」，不写长句）。 */
    fun listTags(deck: PptDeck): List<String> = buildList {
        if (deck.hasCompatFiles) add("兼容版")
        if (deck.hasPdfFile) add("PDF")
    }

    /**
     * 下载完成后的打开 Intent 选择（纯判定）：返回 MIME 和是否需要用「查看器」打开。
     * 实际 Intent 构造在 Android 层，这里只给出可测的 MIME/类型判定。
     */
    fun viewerMime(row: DownloadRow): String = row.mimeType.ifBlank { MIME_BINARY }

    /** 是否可用「分享」而不是「打开」（音视频/二进制也允许分享；这里全部允许）。 */
    fun canShare(row: DownloadRow): Boolean = row.available

    /** 下载文件名去重：目录已有同名文件时追加 ` (1)`、` (2)`…（不覆盖已下载文件）。 */
    fun nextAvailableFileName(existingNames: Set<String>, desired: String): String {
        if (desired !in existingNames) return desired
        val dot = desired.lastIndexOf('.')
        val base = if (dot > 0) desired.substring(0, dot) else desired
        val ext = if (dot > 0) desired.substring(dot) else ""
        var index = 1
        while (index < 1000) {
            val candidate = "$base ($index)$ext"
            if (candidate !in existingNames) return candidate
            index += 1
        }
        return "${base}_${System.currentTimeMillis()}$ext"
    }

    /** 状态文案：未就绪时详情页只显示状态与进度（§12.4.1）。 */
    fun statusLine(detail: PptDeckDetail): String {
        val kind = PptStatusTexts.kindOf(detail.status)
        return when (kind) {
            PptStatusTexts.Kind.READY -> "已完成"
            else -> PptStatusTexts.text(detail.status, detail.progress, agentOnline = null)
        }
    }

    /** 旧 deck 提示文案（不写长句）。 */
    const val LEGACY_TITLE: String = "兼容版未生成"
}
