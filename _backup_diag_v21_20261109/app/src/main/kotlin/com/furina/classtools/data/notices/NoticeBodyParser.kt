package com.furina.classtools.data.notices

import com.furina.classtools.data.network.NetJson
import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
            json.decodeFromString(NoticeGroupDto.serializer(), raw)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * `items[]` → 渲染元信息。
     *
     * 注意：不返回原 [JsonObject]（其中可能含 MB 级 base64），文本条目只保留可展示文本。
     */
    fun parseItems(items: List<JsonObject>): List<NoticeItemMeta> {
        val out = ArrayList<NoticeItemMeta>(items.size)
        var textPos = 0
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
                            ocr = item.bool("ocr") || item.bool("isOcr"),
                            hasData = src.startsWith("data:", ignoreCase = true),
                            approxBytes = estimateDataUriBytes(src),
                        ),
                    )
                }

                "file" -> {
                    val name = item.str("name").ifBlank { item.str("text").ifBlank { "附件" } }
                    val url = item.str("url").ifBlank { item.str("src") }
                    val hasContent = url.isNotBlank()
                    val archive = item.obj("archive")
                    val archiveFiles = archive?.arr("files") ?: JsonArray(emptyList())
                    val explicitSize = item.str("size")
                    val sizeText = explicitSize.ifBlank {
                        val approx = estimateDataUriBytes(url)
                        if (approx > 0) formatSize(approx) else ""
                    }
                    out.add(
                        NoticeItemMeta.File(
                            kind = kind,
                            name = name,
                            ext = extOf(name),
                            sizeText = sizeText.ifBlank { "附件" },
                            note = item.str("note"),
                            hasContent = hasContent,
                            pending = !hasContent && archiveFiles.isEmpty(),
                            archiveOk = archive != null && archiveFiles.isNotEmpty(),
                            archiveCount = archiveFiles.size,
                            archiveSummary = archive?.str("summary").orEmpty(),
                        ),
                    )
                }

                "form" -> {
                    val name = item.str("realTitle").ifBlank { item.str("name").ifBlank { "收集表" } }
                    val close = item.str("close")
                    val hasUrl = item.str("realUrl").ifBlank { item.str("url") }.isNotBlank()
                    out.add(
                        NoticeItemMeta.Form(
                            kind = kind,
                            name = name,
                            statusText = if (close.isBlank()) "进行中" else "$close 截止",
                            closeText = close,
                            note = item.str("note"),
                            hasUrl = hasUrl,
                        ),
                    )
                }

                "link", "mini", "article" -> {
                    val name = item.str("name").ifBlank { item.str("title").ifBlank { "链接" } }
                    val url = item.str("url").ifBlank { item.str("realUrl") }
                    out.add(
                        NoticeItemMeta.Link(
                            kind = kind,
                            name = name,
                            title = item.str("title"),
                            account = item.str("account").ifBlank { item.str("nickname") },
                            host = hostOf(url),
                            hasUrl = url.isNotBlank(),
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

    /** 人工通知 `attachments` 解析（只保留元信息）。 */
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
            val data = obj.str("d")
            val explicit = obj.str("s").toLongOrNull() ?: 0L
            val size = if (explicit > 0) explicit else estimateDataUriBytes(data)
            out.add(
                NoticeManualAttachment(
                    index = index,
                    name = name,
                    mime = mime,
                    sizeText = if (size > 0) formatSize(size) else "附件",
                    hasData = data.isNotBlank(),
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
