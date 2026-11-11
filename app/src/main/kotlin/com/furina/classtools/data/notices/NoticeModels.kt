package com.furina.classtools.data.notices

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** 自动通知 body 前缀（契约：`body = "[[CLASS_NOTICE_V1]]" + JSON`）。 */
const val NOTICE_BODY_PREFIX: String = "[[CLASS_NOTICE_V1]]"

/**
 * 通知行（`public.notices`，契约：`docs/BACKEND_CONTRACT.md` §3/§6 + `docs/api/NOTICES.md` §1）。
 *
 * 自动通知：`id = auto_<gid>` 且 `body` 以 [NOTICE_BODY_PREFIX] 开头；人工通知反之。
 * 管理员覆盖 4 列（override_json/hidden/edited_at/edited_by）由服务端下发，客户端只读。
 */
@Serializable
data class NoticeRow(
    val id: String,
    val title: String = "",
    val body: String = "",
    val pin: Boolean = false,
    val imp: Boolean = false,
    val author: String = "",
    val owner: String = "",
    val attachments: String = "",
    val archived: Boolean = false,
    val ts: Long = 0L,
    val audience: String = "all",
    @SerialName("override_json") val overrideJson: String = "",
    val hidden: Boolean = false,
    @SerialName("edited_at") val editedAt: Long = 0L,
    @SerialName("edited_by") val editedBy: String = "",
)

/** 轻量签名行：`GET notices?select=id,ts,hidden,edited_at`（约 2KB，先比签名再拉整表）。 */
@Serializable
data class NoticeSigRow(
    val id: String,
    val ts: Long = 0L,
    val hidden: Boolean = false,
    @SerialName("edited_at") val editedAt: Long = 0L,
)

/** AI 结构化段落（`{type,text}`；type 归一到 时间/地点/对象/内容/报名/截止/要求/联系方式/补充）。 */
@Serializable
data class NoticeSegment(
    val type: String = "",
    val text: String = "",
)

/** 延期记录：最后一次的 deadline 才是真实截止。 */
@Serializable
data class NoticeExtension(
    val deadline: String = "",
)

/** 长期安排（recurrence；存在时永不按 deadline 判过期）。 */
@Serializable
data class NoticeRecurrenceDto(
    val freq: String = "",
    val weekday: Int? = null,
    val weeks: List<Int> = emptyList(),
    val day: Int? = null,
    val time: String = "",
    val startAt: String = "",
    val label: String = "",
)

/**
 * 自动通知组（body 前缀后的 JSON，`[[CLASS_NOTICE_V1]]{...}`）。
 *
 * 容错：所有字段可选/带默认值（采集端会持续加字段）；`items` 保留为 [JsonObject] 原始列表，
 * 由 [NoticeBodyParser.parseItems] 转成只含元信息的渲染模型（不把大 base64 塞进 UI 模型）。
 */
@Serializable
data class NoticeGroupDto(
    val id: String = "",
    val title: String = "",
    val cat: String = "",
    val source: String = "",
    val senderName: String = "",
    val senderRole: String = "",
    val senderBadge: String = "",
    val summary: String = "",
    val aiSummary: String = "",
    val aiPoints: List<String> = emptyList(),
    val aiSegments: List<NoticeSegment> = emptyList(),
    val deadline: String = "",
    val deadlineAction: String = "",
    val eventAt: String = "",
    val eventAction: String = "",
    val expireAt: String = "",
    val timeLabel: String = "",
    val signupStart: String = "",
    val signupEnd: String = "",
    val extensions: List<NoticeExtension> = emptyList(),
    val recurrence: NoticeRecurrenceDto? = null,
    val actionable: Boolean = true,
    val alert: Boolean = false,
    val ocr: Boolean = false,
    val updatedAt: String = "",
    val mergedSources: List<String> = emptyList(),
    @SerialName("crossSource") val crossSource: Boolean = false,
    val items: List<JsonObject> = emptyList(),
)

/** 通知状态（由 [NoticeStatusMachine.statusOf] 计算）。 */
enum class NoticeBucket { URGENT, TODAY, SOON, ACTIVE, EXPIRED, ALERT, RECURRING, EXTENDED, NONE }

data class NoticeStatus(
    val key: String,
    val label: String,
    /** UI 样式分类：urgent/today/soon/active/expired/alert/recurring/extended/none。 */
    val cls: String,
    val expired: Boolean,
    val bucket: NoticeBucket,
)

/** 主显示时间 + 动作（`报名 > 截止 > 活动` 优先级）。 */
data class NoticeWhen(
    val atMillis: Long,
    val action: String,
)

/** 简报统计（全部/未处理 tab 顶部或桌面侧栏用）。 */
data class NoticeStats(
    val pending: Int,
    val urgent: Int,
    val week: Int,
    val expired: Int,
)

/** 通知附件/条目元信息（`items[]`；不保留 base64 原文，只给渲染必需字段）。 */
sealed interface NoticeItemMeta {
    val kind: String

    /** 纯文本/补充通知（可安全展示全文）。 */
    data class Text(
        override val kind: String,
        val label: String,
        val fromName: String,
        val fromRole: String,
        val timeText: String,
        val text: String,
        val isFollow: Boolean,
    ) : NoticeItemMeta

    /**
     * 图片：只描述元信息（caption/OCR/近似体积），不把 base64 放 UI 模型。
     *
     * @param hasData 内联 `data:image/...;base64,`（旧数据）
     * @param url 外置签名 URL（新数据：`https://<host>/att/<id>?exp=&sig=`；空 = 内联）
     */
    data class Image(
        override val kind: String,
        val caption: String,
        val ocr: Boolean,
        val hasData: Boolean,
        val approxBytes: Long,
        val url: String = "",
    ) : NoticeItemMeta

    /**
     * 文件附件：name/扩展名/大小/是否有 URL 或内联数据；「待获取」无内容。
     *
     * @param url 外置签名 URL（空 = 内容仍是内联 data URI 或无内容）
     * @param archiveFiles 压缩包内文件清单（`archive.files[]`，仅保留元信息 + 各自 url）
     */
    data class File(
        override val kind: String,
        val name: String,
        val ext: String,
        val sizeText: String,
        val note: String,
        val hasContent: Boolean,
        val pending: Boolean,
        val archiveOk: Boolean,
        val archiveCount: Int,
        val archiveSummary: String,
        val url: String = "",
        val archiveFiles: List<NoticeArchiveFile> = emptyList(),
    ) : NoticeItemMeta

    /** 收集表（腾讯文档类）。 */
    data class Form(
        override val kind: String,
        val name: String,
        val statusText: String,
        val closeText: String,
        val note: String,
        val hasUrl: Boolean,
    ) : NoticeItemMeta

    /** 链接/小程序/文章。 */
    data class Link(
        override val kind: String,
        val name: String,
        val title: String,
        val account: String,
        val host: String,
        val hasUrl: Boolean,
    ) : NoticeItemMeta

    /** 合并转发的聊天记录（最多保留 8 条文本）。 */
    data class Merged(
        override val kind: String,
        val name: String,
        val count: Int,
        val messages: List<MergedMessage>,
    ) : NoticeItemMeta

    /** 未知 kind：本轮只做占位（不渲染、不联网）。 */
    data class Unknown(
        override val kind: String,
        val keys: List<String>,
    ) : NoticeItemMeta
}

/** 压缩包内单个文件（`archive.files[]`；`url` 为外置签名 URL，空 = 无独立内容）。 */
data class NoticeArchiveFile(
    val name: String,
    val kind: String,
    val sizeText: String,
    /** 外置签名 URL；空串 = 不可单独打开（旧数据 / 未外置）。 */
    val url: String = "",
    /** 旧数据内联 `data:` 内容（不保留原文，只标记存在）。 */
    val hasData: Boolean = false,
)

data class MergedMessage(
    val fromName: String,
    val fromBadge: String,
    val timeText: String,
    val text: String,
)

/** 自动通知条目（渲染/排序用；done/pinned 由 marks 在 UI 层叠加）。 */
data class NoticeEntry(
    val row: NoticeRow,
    /** body 前缀解析 + 覆盖应用后的组（`items` 已清空，渲染走 [items] 元信息）。 */
    val group: NoticeGroupDto,
    val items: List<NoticeItemMeta>,
    val hidden: Boolean,
    val edited: Boolean,
) {
    val id: String get() = group.id.ifBlank { row.id.removePrefix("auto_") }
    val rowId: String get() = row.id
    val ts: Long get() = row.ts
    val title: String get() = group.title.ifBlank { row.title.ifBlank { "班级通知" } }
    val cat: String get() = group.cat.ifBlank { "通知" }
}

/** 人工通知（无 CLASS_NOTICE_V1 前缀；本轮只读展示，发布/编辑不做）。 */
data class ManualNotice(
    val row: NoticeRow,
    val attachments: List<NoticeManualAttachment>,
) {
    val id: String get() = row.id
    val ts: Long get() = row.ts
}

/** 人工通知附件元信息（`attachments` JSON 字符串里的 `[{n,t,s,d}]`；新数据可能带 `url`）。 */
data class NoticeManualAttachment(
    val index: Int,
    val name: String,
    val mime: String,
    val sizeText: String,
    val hasData: Boolean,
    /** 外置签名 URL（新数据）；空串 = 旧数据（`d` 内联 data URI）。 */
    val url: String = "",
)

/** 一次通知加载的完整快照。 */
data class NoticesData(
    val auto: List<NoticeEntry>,
    val manual: List<ManualNotice>,
    /** 管理员身份下被 hidden 的行数（普通身份恒 0，且这些行不下发到列表）。 */
    val hiddenCount: Int,
    val fetchedAt: Long,
    val fromCache: Boolean,
) {
    val isEmpty: Boolean get() = auto.isEmpty() && manual.isEmpty()
}

/** 通知加载结果。 */
sealed interface NoticesLoadResult {
    data class Success(val data: NoticesData) : NoticesLoadResult
    data class Failure(val message: String) : NoticesLoadResult
}
