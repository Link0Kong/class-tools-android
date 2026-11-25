package com.furina.classtools.data.notices

import com.furina.classtools.data.network.NetJson
import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * 通知 body 解析（契约：`docs/api/NOTICES.md` §2/§6.2）。
 *
 * - 自动通知：`body.startsWith("[[CLASS_NOTICE_V1]]")` → 去前缀 JSON.parse 成 [NoticeGroupDto]；
 * - 人工通知：其余归手动列表，附件解析 `attachments`（`[{n,t,s,d}]`）；
 * - `items[]` → [NoticeItemMeta]：**只保留渲染元信息**，图片/文件的大 base64 不进入 UI 模型
 *   （详情需要时再按需取，见 `HomeworkAttachments.dataOf` 的同类能力）。
 */
object NoticeBodyParser {

    fun isAuto(body: String): Boolean = body.trimStart().startsWith(NOTICE_BODY_PREFIX)

    /** 解析 body 前缀后的 group JSON；坏 JSON 返回 null（该行跳过，不崩整页）。 */
    fun parseGroup(body: String, json: Json = NetJson): NoticeGroupDto? {
        if (!isAuto(body)) return null
        val raw = body.trimStart().removePrefix(NOTICE_BODY_PREFIX).trim()
        if (raw.isEmpty()) return null
        return try {
            json.decodeFromString(groupSerializer, raw)
        } catch (_: Throwable) {
            null
        }
    }

    // The collector now emits { source, group, kind, ... } entries. Older snapshots use strings.
    private val groupSerializer = object : JsonTransformingSerializer<NoticeGroupDto>(NoticeGroupDto.serializer()) {
        override fun transformDeserialize(element: JsonElement): JsonElement {
            val group = element as? JsonObject ?: return element
            val sources = group["mergedSources"] as? JsonArray ?: return element
            val labels = sources.mapNotNull { source ->
                when (source) {
                    is JsonObject -> source.str("source").ifBlank { source.str("group") }.takeIf { it.isNotBlank() }
                    is JsonPrimitive -> source.contentOrNull
                    else -> null
                }?.let(::JsonPrimitive)
            }
            return JsonObject(group + ("mergedSources" to JsonArray(labels)))
        }
    }

    /**
     * `items[]` → 渲染元信息。
     *
     * 注意：不返回原 [JsonObject]（其中可能含 MB 级 base64），文本条目只保留可展示文本。
     */
    fun parseItems(items: List<JsonObject>, rowId: String = ""): List<NoticeItemMeta> {
        val out = ArrayList<NoticeItemMeta>(items.size)
        var textPos = 0
        var imageOrdinal = 0
        items.forEachIndexed { index, item ->
            val kind = item.str("kind").ifBlank { "text" }
            when (kind) {
                "text", "follow" -> {
                    val isFollow = kind == "follow"
                    val label = item.str("label").ifBlank {
                        if (isFollow) "补充通知" else if (textPos == 0) "原始消息" else "补充通知"
                    }
                    textPos += 1
                    out.add(
                        NoticeItemMeta.Text(
                            kind = kind,
                            label = label,
                            fromName = item.str("fromName").ifBlank { item.str("from") },
                            fromRole = item.str("fromRole").ifBlank { item.str("fromBadge") },
                            timeText = item.str("time"),
                            text = item.str("text").trim(),
                            isFollow = isFollow,
                        ),
                    )
                }

                "img" -> {
                    val src = item.str("src").ifBlank { item.str("url") }
                    out.add(
                        NoticeItemMeta.Image(
                            kind = kind,
                            caption = item.str("caption"),
                            rowId = rowId,
                            imageOrdinal = imageOrdinal++,
                            ocr = item.bool("ocr") || item.bool("isOcr"),
                            hasData = NoticeAttachmentSource.inlineOf(src) != null,
                            approxBytes = estimateDataUriBytes(src),
                            url = NoticeAttachmentSource.urlOf(src),
                            // 服务端 light meta（class_notices_meta）把内联 data URI 置空并写 lazy=true
                            lazy = item.bool("lazy"),
                        ),
                    )
                }

                "file" -> {
                    val name = item.str("name").ifBlank { item.str("text").ifBlank { "附件" } }
                    val raw = item.str("url").ifBlank { item.str("src") }
                    val hasContent = raw.isNotBlank()
                    val archive = parseArchive(item.obj("archive"))
                    val explicitSize = item.str("size")
                    val sizeText = explicitSize.ifBlank {
                        val approx = estimateDataUriBytes(raw)
                        if (approx > 0) formatSize(approx) else ""
                    }
                    out.add(
                        NoticeItemMeta.File(
                            kind = kind,
                            name = name,
                            ext = extOf(name),
                            // URL 形态下无法从 base64 估算体积：item.size 或「附件」，绝不显示 0 B
                            sizeText = sizeText.ifBlank { "附件" },
                            note = item.str("note"),
                            hasContent = hasContent,
                            // 「待获取」= 无内容且压缩包无文件清单（archive 只有摘要不算有内容）
                            pending = !hasContent && !item.bool("lazy") && archive.files.isEmpty(),
                            rowId = rowId,
                            itemIndex = index,
                            archiveOk = archive.files.isNotEmpty(),
                            archiveCount = archive.count,
                            archiveSummary = archive.summary,
                            url = NoticeAttachmentSource.urlOf(raw),
                            archiveFiles = archive.files,
                            lazy = item.bool("lazy"),
                        ),
                    )
                }

                "form" -> {
                    val name = item.str("realTitle").ifBlank { item.str("name").ifBlank { "收集表" } }
                    val close = item.str("close")
                    val url = item.str("realUrl").ifBlank { item.str("url") }.ifBlank { item.str("src") }
                    out.add(
                        NoticeItemMeta.Form(
                            kind = kind,
                            name = name,
                            statusText = if (close.isBlank()) "进行中" else "$close 截止",
                            closeText = close,
                            note = item.str("note"),
                            hasUrl = url.isNotBlank(),
                            url = NoticeAttachmentSource.urlOf(url),
                        ),
                    )
                }

                "link", "mini", "article" -> {
                    val name = item.str("name").ifBlank { item.str("title").ifBlank { "链接" } }
                    val url = item.str("realUrl").ifBlank { item.str("url") }.ifBlank { item.str("src") }
                    out.add(
                        NoticeItemMeta.Link(
                            kind = kind,
                            name = name,
                            title = item.str("title"),
                            account = item.str("account").ifBlank { item.str("nickname") },
                            host = hostOf(url),
                            hasUrl = url.isNotBlank(),
                            url = url.take(NoticeAttachmentSource.MAX_URL_LENGTH),
                        ),
                    )
                }

                "merged" -> {
                    val messages = ArrayList<MergedMessage>()
                    val arr = item.arr("messages") ?: JsonArray(emptyList())
                    for (message in arr) {
                        val obj = message as? JsonObject ?: continue
                        messages.add(
                            MergedMessage(
                                fromName = obj.str("fromName").ifBlank { obj.str("from") },
                                fromBadge = obj.str("fromRole").ifBlank { obj.str("fromBadge") },
                                timeText = obj.str("time"),
                                text = obj.str("text").trim(),
                            ),
                        )
                        if (messages.size >= MAX_MERGED_MESSAGES) break
                    }
                    out.add(
                        NoticeItemMeta.Merged(
                            kind = kind,
                            name = item.str("name").ifBlank { item.str("title").ifBlank { "合并消息" } },
                            count = item.int("count").takeIf { it > 0 } ?: messages.size,
                            messages = messages,
                        ),
                    )
                }

                else -> out.add(
                    NoticeItemMeta.Unknown(
                        kind = kind,
                        keys = item.keys.take(8).toList(),
                    ),
                )
            }
            if (out.size >= MAX_ITEMS) return@forEachIndexed
        }
        return out
    }

    /**
     * 人工通知 `attachments` 解析（只保留元信息）。
     *
     * 旧数据内容在 `d`（data URI）；外置迁移后 `d`/`url`/`u` 可能是签名 URL。
     */
    fun parseManualAttachments(raw: String?, json: Json = NetJson): List<NoticeManualAttachment> {
        if (raw.isNullOrBlank()) return emptyList()
        val element = try {
            json.parseToJsonElement(raw)
        } catch (_: Throwable) {
            return emptyList()
        }
        val array = element as? JsonArray ?: return emptyList()
        val out = ArrayList<NoticeManualAttachment>(array.size)
        array.forEachIndexed { index, item ->
            val obj = item as? JsonObject ?: return@forEachIndexed
            val name = obj.str("n").ifBlank { "附件 ${index + 1}" }
            val mime = obj.str("t")
            val source = obj.str("d").ifBlank { obj.str("url") }.ifBlank { obj.str("u") }
            val explicit = obj.str("s").toLongOrNull() ?: 0L
            val size = if (explicit > 0) explicit else estimateDataUriBytes(source)
            out.add(
                NoticeManualAttachment(
                    index = index,
                    name = name,
                    mime = mime,
                    // URL 形态无 base64 可估体积：显示「附件」，绝不显示 0 B
                    sizeText = if (size > 0) formatSize(size) else "附件",
                    hasData = NoticeAttachmentSource.inlineOf(source) != null,
                    url = NoticeAttachmentSource.urlOf(source),
                ),
            )
        }
        return out
    }

    /** 组内首条可展示文本（列表摘要兜底）。 */
    fun firstTextOf(items: List<NoticeItemMeta>): String =
        items.filterIsInstance<NoticeItemMeta.Text>().firstOrNull { it.text.isNotBlank() }?.text.orEmpty()

    /** 附件数（file/form/link/img 计入）。 */
    fun attachmentCount(items: List<NoticeItemMeta>): Int =
        items.count {
            it is NoticeItemMeta.File || it is NoticeItemMeta.Form ||
                it is NoticeItemMeta.Link || it is NoticeItemMeta.Image
        }

    // ------------------------------------------------------------ 内部

    private const val MAX_ITEMS = 40
    private const val MAX_MERGED_MESSAGES = 8

    /** 压缩包内文件清单上限（只展示元信息；防异常大清单撑爆 UI 模型）。 */
    private const val MAX_ARCHIVE_FILES = 60

    /** `archive` 元信息（`{status,count,summary,files:[{name,kind,size,url?}]}`）。 */
    private data class ParsedArchive(
        val files: List<NoticeArchiveFile>,
        val count: Int,
        val summary: String,
    )

    /**
     * 解析压缩包内容清单（`NOTICES.md` §6 `archive`，三端共用）。
     *
     * `files[].url` 外置迁移后是签名 URL；旧数据可能是内联 data URI（只标记 [NoticeArchiveFile.hasData]，
     * 不把 base64 放进 UI 模型）。`count` 以服务端字段为准（可能大于本端截断后的清单长度）。
     */
    private fun parseArchive(archive: JsonObject?): ParsedArchive {
        if (archive == null) return ParsedArchive(emptyList(), 0, "")
        val summary = archive.str("summary")
        val array = archive.arr("files") ?: JsonArray(emptyList())
        val files = ArrayList<NoticeArchiveFile>(minOf(array.size, MAX_ARCHIVE_FILES))
        for ((index, element) in array.withIndex()) {
            if (files.size >= MAX_ARCHIVE_FILES) break
            val obj = element as? JsonObject ?: continue
            val rawName = obj.str("name").ifBlank { obj.str("path").substringAfterLast('/') }
            val source = obj.str("url").ifBlank { obj.str("src") }
            files.add(
                NoticeArchiveFile(
                    name = rawName.ifBlank { "文件 ${files.size + 1}" },
                    kind = obj.str("kind"),
                    sourceIndex = index,
                    sizeText = obj.str("size"),
                    url = NoticeAttachmentSource.urlOf(source),
                    hasData = NoticeAttachmentSource.inlineOf(source) != null,
                    lazy = obj.bool("lazy"),
                ),
            )
        }
        val count = archive.int("count").takeIf { it > 0 } ?: files.size
        return ParsedArchive(files, count, summary)
    }

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.int(key: String): Int =
        (this[key] as? JsonPrimitive)?.intOrNull ?: 0

    private fun JsonObject.bool(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.booleanOrNull ?: false

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

    private fun estimateDataUriBytes(data: String): Long {
        if (!data.startsWith("data:", ignoreCase = true)) return 0L
        val comma = data.indexOf(',')
        if (comma < 0) return 0L
        return ((data.length - comma - 1).toLong() / 4) * 3
    }

    private fun extOf(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot in 0 until name.length - 1) name.substring(dot + 1).lowercase() else ""
    }

    private fun hostOf(url: String): String {
        if (url.isBlank()) return ""
        return try {
            URI(url).host.orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes <= 0L -> ""
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(java.util.Locale.CHINA, "%.1f KB", bytes / 1024.0)
        else -> String.format(java.util.Locale.CHINA, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
