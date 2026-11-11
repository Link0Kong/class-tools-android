package com.furina.classtools.data.homework

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull

/**
 * 作业行（`public.homework`，契约：`docs/BACKEND_CONTRACT.md` §3 + `docs/api/ENDPOINTS.md` E4/E5/E6）。
 *
 * 注意：`done` 列前端**忽略**（完成状态唯一真源是 `public.marks kind='hw'`）；
 * 查询恒带 `archived=eq.false`。`attachments` 是 **JSON 字符串**（`[{n,t,s,d}]`），需二次解析，
 * 且 [HomeworkAttachmentMeta] 只保留元信息，不在 UI 模型里存 base64 大字段。
 */
@Serializable
data class HomeworkRow(
    val id: String,
    val subject: String = "",
    val content: String = "",
    val due: String = "",
    val date: String = "",
    val time: String = "",
    val author: String = "",
    val owner: String = "",
    val attachments: String = "",
    val done: Boolean = false,
    val archived: Boolean = false,
    val ts: Long = 0L,
)

/** 附件数据形态（只用于展示/后续按需读取，不把内容常驻内存）。 */
enum class HomeworkAttachmentKind {
    /** `data:<mime>;base64,...`（网页/小程序压缩后的内联附件）。 */
    DATA_URI,

    /** 普通 http(s) URL（历史数据兼容）。 */
    URL,

    /** 缺内容 / 格式无法识别。 */
    NONE,
}

/** 作业附件元信息（不含 base64 内容）。 */
data class HomeworkAttachmentMeta(
    /** 在原始 attachments 数组中的下标（「按需预览」时定位用）。 */
    val index: Int,
    val name: String,
    val mime: String,
    val sizeBytes: Long,
    val kind: HomeworkAttachmentKind,
) {
    val isImage: Boolean get() = mime.startsWith("image/")
    val sizeText: String get() = formatSize(sizeBytes)

    companion object {
        fun formatSize(bytes: Long): String = when {
            bytes <= 0L -> "—"
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(java.util.Locale.CHINA, "%.1f KB", bytes / 1024.0)
            else -> String.format(java.util.Locale.CHINA, "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}

/** 作业截止状态（纯计算，见 [HomeworkDueCalculator]）。 */
enum class HomeworkDueState {
    /** 无截止日期。 */
    NONE,

    /** 剩余 < 24h（紧急）。 */
    HOURS_LEFT,

    /** 剩余 ≥ 24h（按天展示）。 */
    DAYS_LEFT,

    /** 已过期 < 24h。 */
    OVERDUE_HOURS,

    /** 已过期 ≥ 24h。 */
    OVERDUE_DAYS,
}

/** 卡片语气色（UI 映射到 warning/urgent 等）。 */
enum class HomeworkDueTone { NEUTRAL, SAFE, WARN, URGENT, OVERDUE }

/** 一条作业的截止信息。 */
data class HomeworkDue(
    val atMillis: Long,
    val label: String,
    val state: HomeworkDueState,
    val tone: HomeworkDueTone,
    val expired: Boolean,
    /** 过期超过 3 天：只在内存过滤（契约要求，不下发不写云）。 */
    val prunable: Boolean,
)

/** UI 用的作业条目：行 + 完成状态 + 截止 + 附件元信息。 */
data class HomeworkItem(
    val row: HomeworkRow,
    val done: Boolean,
    val due: HomeworkDue?,
    val attachments: List<HomeworkAttachmentMeta>,
) {
    val id: String get() = row.id
    val title: String get() = row.subject.ifBlank { row.content.lineSequence().firstOrNull().orEmpty() }
}

/** 作业列表筛选（UI chips）。 */
enum class HomeworkFilter(val label: String) {
    ALL("全部"),
    UNFINISHED("未完成"),
    DONE("已完成"),
    ;
}

/** 解析 JSON 字符串里的附件数组（失败返回空列表，不崩）。 */
object HomeworkAttachments {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String?): List<HomeworkAttachmentMeta> {
        if (raw.isNullOrBlank()) return emptyList()
        val element = try {
            json.parseToJsonElement(raw)
        } catch (_: Throwable) {
            return emptyList()
        }
        val array = element as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        val out = ArrayList<HomeworkAttachmentMeta>(array.size)
        array.forEachIndexed { index, item ->
            val obj = item as? kotlinx.serialization.json.JsonObject ?: return@forEachIndexed
            val name = obj["n"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
                ?.takeIf { it.isNotBlank() } ?: "附件 ${index + 1}"
            val mime = obj["t"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }.orEmpty()
            val data = obj["d"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }.orEmpty()
            val explicitSize = obj["s"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
                ?.toLongOrNull() ?: 0L
            val size = if (explicitSize > 0) explicitSize else estimateDataUriBytes(data)
            val kind = when {
                data.startsWith("data:", ignoreCase = true) -> HomeworkAttachmentKind.DATA_URI
                data.startsWith("http://", ignoreCase = true) || data.startsWith("https://", ignoreCase = true) ->
                    HomeworkAttachmentKind.URL
                else -> HomeworkAttachmentKind.NONE
            }
            out.add(
                HomeworkAttachmentMeta(
                    index = index,
                    name = name,
                    mime = mime,
                    sizeBytes = size,
                    kind = kind,
                ),
            )
        }
        return out
    }

    /** 失败/空返回 0（用于坏数据容错）。 */
    fun count(raw: String?): Int = parse(raw).size

    /**
     * 按需取某个附件的原始 data URI / URL（列表页**不要**调用，只用于详情预览；
     * 大 base64 不应常驻内存，调用方用完即弃）。
     */
    fun dataOf(raw: String?, index: Int): String? {
        if (raw.isNullOrBlank()) return null
        val element = try {
            json.parseToJsonElement(raw)
        } catch (_: Throwable) {
            return null
        }
        val array = element as? kotlinx.serialization.json.JsonArray ?: return null
        val obj = array.getOrNull(index) as? kotlinx.serialization.json.JsonObject ?: return null
        return obj["d"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
    }

    /** data URI 的近似解码体积（base64 4→3）；非 data URI 返回 0。 */
    private fun estimateDataUriBytes(data: String): Long {
        if (!data.startsWith("data:", ignoreCase = true)) return 0L
        val comma = data.indexOf(',')
        if (comma < 0) return 0L
        val payloadLength = (data.length - comma - 1).toLong()
        return (payloadLength / 4) * 3
    }
}
