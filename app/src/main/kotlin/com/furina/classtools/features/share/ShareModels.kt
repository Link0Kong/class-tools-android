package com.furina.classtools.features.share

/**
 * 分享卡片的纯数据模型（对齐微信小程序端 `utils/sharecard.js` 的视图对象）。
 *
 * 为什么单独一套模型：小程序端把「抽签/分组/作业/体测/课表/名单/通知/投票」统一成
 * **5:4 信息卡**（标题 + 徽标 + 副标题 + 至多 3 个统计块 + 至多 6 行信息 + 页脚），
 * 安卓导出卡是竖版长图（`ExportCardSpec`），两者用途不同；
 * 因此分享卡由 [ShareSpecs] 从业务数据/导出卡派生，不改变既有导出链路。
 *
 * 隐私口径（与小程序端一致）：
 * - 名单/体测/课表只出现姓名、座号、科目、成绩等班级内可见信息，**不含学号/手机号/宿舍号**；
 * - 投票分享卡**固定不展示票数与百分比**（[ShareCardView.maskedResults] = true），结果只能点开投票页查看；
 * - 分享全部由用户主动触发，写入的临时文件只在 `cache/share/`，不联网、不上传。
 */
enum class ShareKind {
    DRAW,
    GROUP,
    SUM,
    HOMEWORK,
    FITNESS,
    TIMETABLE,
    ROSTER,
    NOTICE,
    VOTE,
    APP,
    ;

    /** 分享卡右上角徽标（与小程序端 `brand(g, right)` 文案一致）。 */
    val badge: String
        get() = when (this) {
            DRAW -> "抽签"
            GROUP -> "分组"
            SUM -> "结果单"
            HOMEWORK -> "作业"
            FITNESS -> "体测"
            TIMETABLE -> "课表"
            ROSTER -> "名单"
            NOTICE -> "通知"
            VOTE -> "投票"
            APP -> "班级工具"
        }

    /** 点击分享卡/文案时打开的网页工具锚点（与网页 `#tab=<id>` 一致；空 = 首页）。 */
    val toolId: String
        get() = when (this) {
            DRAW -> "draw"
            GROUP -> "group"
            SUM -> "draw"
            HOMEWORK -> "hw"
            FITNESS -> "ft"
            TIMETABLE -> "tt"
            ROSTER -> "roster"
            NOTICE -> "nt"
            VOTE -> "vote"
            APP -> ""
        }
}

/** 一个统计块（`值` 大字 + `说明` 小字）。 */
data class ShareStat(val label: String, val value: String)

/** 一行「标签 + 文本」（标签可空）。 */
data class ShareLine(val label: String = "", val text: String = "")

/** 分享文案（标题 + 正文 + 链接）：复制/发送到聊天时使用。 */
data class ShareLink(
    val title: String,
    val text: String,
    val url: String,
) {
    /** 纯文本分享内容：标题 + 正文 + 链接（链接为空时只发标题/正文）。 */
    val message: String
        get() = listOf(title, text, url)
            .filter { it.isNotBlank() }
            .joinToString("\n")
}

/**
 * 一张 5:4 分享卡的内容（纯数据，可单测；绘制见 [ShareCardPainter]）。
 *
 * 排版约定（与小程序端 `sharecard.js` 一致）：
 * - [stats] 最多展示 3 个；[lines] 最多 6 行；[fields] 最多 2 条（通知卡的时间/地点优先）；
 * - [maskedResults] = true 时绘制层不得出现票数/百分比（投票卡）。
 */
data class ShareCardView(
    val kind: ShareKind,
    val title: String,
    val badge: String = kind.badge,
    val subtitle: String = "",
    /** 状态胶囊（通知分类/状态、投票状态与规则等）。 */
    val pills: List<String> = emptyList(),
    val stats: List<ShareStat> = emptyList(),
    val lines: List<ShareLine> = emptyList(),
    /** 关键字段（通知卡：时间/地点/截止等，最多 2 条）。 */
    val fields: List<ShareLine> = emptyList(),
    /** 备注（默认「打开班级工具查看完整内容」）。 */
    val note: String = "",
    val footerLeft: String = "",
    val footerRight: String = "",
    val maskedResults: Boolean = false,
    val link: ShareLink,
) {
    companion object {
        const val MAX_STATS: Int = 3
        const val MAX_LINES: Int = 6
        const val MAX_FIELDS: Int = 2
        const val MAX_POINTS: Int = 3
    }
}
