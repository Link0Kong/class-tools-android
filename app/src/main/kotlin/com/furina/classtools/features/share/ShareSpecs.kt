package com.furina.classtools.features.share

import com.furina.classtools.features.export.ChipsBlock
import com.furina.classtools.features.export.ExportCardSpec
import com.furina.classtools.features.export.GroupsBlock
import com.furina.classtools.features.export.TextBlock

/**
 * 分享内容构建（纯函数，可单测；绘制见 [ShareCardPainter]）。
 *
 * 对齐微信小程序端 `utils/share.js` + `utils/sharecard.js`：
 * - 统一分享文案（标题 + 简介 + 网页链接，链接带工具锚点 `#tab=<id>`）；
 * - 工具结果 → 5:4 信息卡（标题/徽标/统计块/信息行/页脚）；
 * - 通知卡：标题 / 分类 / 状态 / 摘要 / 至多 3 条要点 / 时间·地点等字段 / 来源 / 发布者；
 * - 投票卡：**固定不展示票数与百分比**（只给标题、状态、规则、选项文字与「结果请打开投票页查看」）。
 *
 * 隐私：卡片内容只来自页面已展示的数据；不出现学号、手机号、宿舍号等字段。
 */
object ShareSpecs {

    /** 默认分享标题（小程序端 `share.js → DEFAULT_TITLE` 同文）。 */
    const val DEFAULT_TITLE: String = "班级工具｜课表 · 作业 · 通知 · 抽签"

    /** 网页工具页路径（分享链接统一落到带 `#tab=` 的工具页）。 */
    const val TOOLS_PATH: String = "tools/index.html"

    // ------------------------------------------------------------ 链接 / 文案

    /** 工具深链：`<base>/tools/index.html#tab=<toolId>`（[toolId] 为空 = 首页）。 */
    fun toolUrl(baseUrl: String, toolId: String): String {
        val base = baseUrl.trim().trimEnd('/')
        val id = toolId.trim()
        return if (id.isEmpty()) "$base/$TOOLS_PATH" else "$base/$TOOLS_PATH#tab=$id"
    }

    /** 分享链接（标题 + 一句话简介 + 网页链接）。 */
    fun link(kind: ShareKind, baseUrl: String, title: String? = null, text: String? = null): ShareLink =
        ShareLink(
            title = title?.takeIf { it.isNotBlank() } ?: defaultTitle(kind),
            text = text.orEmpty(),
            url = toolUrl(baseUrl, kind.toolId),
        )

    /** 各类分享的默认标题（与小程序端各页 `share.message` 的标题口径一致）。 */
    fun defaultTitle(kind: ShareKind): String = when (kind) {
        ShareKind.DRAW -> "抽签结果"
        ShareKind.GROUP -> "分组结果"
        ShareKind.SUM -> "抽签分组结果单"
        ShareKind.HOMEWORK -> "班级作业板"
        ShareKind.FITNESS -> "体测成绩"
        ShareKind.TIMETABLE -> "课程表"
        ShareKind.ROSTER -> "班级名单"
        ShareKind.NOTICE -> "班级通知"
        ShareKind.VOTE -> "班级投票"
        ShareKind.APP -> DEFAULT_TITLE
    }

    /** 「分享给同学」的默认卡片（我的页入口；无业务数据）。 */
    fun appCard(baseUrl: String): ShareCardView = ShareCardView(
        kind = ShareKind.APP,
        title = "班级工具站",
        badge = "班级工具",
        subtitle = "课表 · 作业 · 通知 · 抽签 · 投票 · 演示",
        stats = listOf(
            ShareStat("工具", "18 项"),
            ShareStat("端", "网页 / 小程序 / 安卓 / 鸿蒙"),
            ShareStat("数据", "同账号同步"),
        ),
        lines = listOf(
            ShareLine("课表", "周/日视图，下一节课提醒"),
            ShareLine("作业", "截止倒计时与完成状态"),
            ShareLine("通知", "班委通知与附件"),
            ShareLine("抽签", "公平随机，可导出结果"),
            ShareLine("投票", "限时/长时，实时结果"),
        ),
        note = "微信搜索「班级工具」小程序，或直接用浏览器打开。",
        footerLeft = "班级工具 · 分享",
        footerRight = "",
        link = link(ShareKind.APP, baseUrl),
    )

    // ------------------------------------------------------------ 工具导出结果 → 分享卡

    /**
     * 把既有导出卡（`ExportCardSpec`；抽签/分组/作业/体测/课表/名单）转成 5:4 分享卡。
     *
     * 取用规则（与小程序端一致）：标题 + 副标题 + 统计块（结果数/组数/行数）+ 至多 6 行内容 + 页脚；
     * 参数配置区压缩为副标题列表的一行，不把长参数全塞进卡片。
     */
    fun fromExportCard(
        spec: ExportCardSpec,
        kind: ShareKind,
        baseUrl: String,
        nowMillis: Long = System.currentTimeMillis(),
        timeLabel: String = "",
    ): ShareCardView {
        val chips = spec.blocks.filterIsInstance<ChipsBlock>().sumOf { it.chips.size }
        val groups = spec.blocks.filterIsInstance<GroupsBlock>().sumOf { it.groups.size }
        val groupMembers = spec.blocks.filterIsInstance<GroupsBlock>().sumOf { block ->
            block.groups.sumOf { it.size }
        }
        val textLines = spec.blocks.filterIsInstance<TextBlock>().sumOf { it.lines.size }

        val stats = when (kind) {
            ShareKind.DRAW, ShareKind.SUM -> listOfNotNull(
                ShareStat("结果", chips.takeIf { it > 0 }?.toString().orEmpty()),
                ShareStat("行数", textLines.takeIf { it > 0 }?.toString().orEmpty()),
            )
            ShareKind.GROUP -> listOfNotNull(
                ShareStat("组数", groups.takeIf { it > 0 }?.toString().orEmpty()),
                ShareStat("人数", groupMembers.takeIf { it > 0 }?.toString().orEmpty()),
            )
            else -> listOfNotNull(
                ShareStat("条目", textLines.takeIf { it > 0 }?.toString().orEmpty()),
            )
        }.filter { it.value.isNotBlank() }

        val lines = mutableListOf<ShareLine>()
        spec.blocks.forEach { block ->
            when (block) {
                is ChipsBlock -> {
                    val text = block.chips.take(10).joinToString("、") { chip ->
                        (chip.n.toString() + if (chip.nm.isNotBlank()) " ${chip.nm}" else "")
                    }
                    if (text.isNotBlank()) lines += ShareLine(block.label, text)
                }
                is GroupsBlock -> block.groups.take(3).forEachIndexed { index, members ->
                    val text = members.take(12).joinToString("、") { chip ->
                        (chip.n.toString() + if (chip.nm.isNotBlank()) " ${chip.nm}" else "")
                    }
                    lines += ShareLine("第 ${index + 1} 组（${members.size}）", text)
                }
                is TextBlock -> block.lines.take(6).forEach { line ->
                    val text = line.plain ?: line.runs.joinToString("") { it.t }
                    if (text.isNotBlank()) lines += ShareLine(block.label, text)
                }
            }
        }

        val configSummary = spec.config.joinToString(" · ") { (label, value) ->
            if (value.isBlank()) label else "$label $value"
        }

        return ShareCardView(
            kind = kind,
            title = spec.title,
            subtitle = listOf(spec.sub, configSummary).filter { it.isNotBlank() }.joinToString(" · "),
            stats = stats.take(ShareCardView.MAX_STATS),
            lines = lines.take(ShareCardView.MAX_LINES),
            footerLeft = spec.foot.ifBlank { "班级工具 · ${kind.badge}" },
            footerRight = timeLabel,
            link = link(kind, baseUrl, title = spec.title, text = spec.sub),
        )
    }

    // ------------------------------------------------------------ 通知卡

    /** 通知分享输入（来自通知详情/卡片已展示字段；不含学号等隐私）。 */
    data class NoticeShareInput(
        val title: String,
        val category: String = "",
        val statusLabel: String = "",
        val summary: String = "",
        val points: List<String> = emptyList(),
        /** 结构化字段（类型 + 文本）：时间/地点/截止/对象/报名 优先取前 2 条。 */
        val segments: List<ShareLine> = emptyList(),
        val senderName: String = "",
        val senderRole: String = "",
        val source: String = "",
        val timeLabel: String = "",
        /** 附件数量（只显示数量，不放大段原文/图片）。 */
        val attachmentCount: Int = 0,
    )

    /** 通知卡：标题 / 分类 / 状态 / 摘要 / 至多 3 条要点 / 时间·地点字段 / 来源 / 发布者。 */
    fun noticeCard(input: NoticeShareInput, baseUrl: String): ShareCardView {
        val title = input.title.trim().ifBlank { "班级通知" }
        val priority = listOf("时间", "地点", "截止", "对象", "报名")
        val fields = input.segments
            .filter { it.text.isNotBlank() }
            .sortedBy { segment ->
                priority.indexOf(segment.label).let { if (it < 0) priority.size else it }
            }
            .distinctBy { it.label }
            .take(ShareCardView.MAX_FIELDS)
        val sender = listOf(input.senderName.trim(), input.senderRole.trim())
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        val lines = input.points
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(ShareCardView.MAX_POINTS)
            .map { ShareLine("", "· $it") }
        val attachText = if (input.attachmentCount > 0) "${input.attachmentCount} 个附件" else ""
        return ShareCardView(
            kind = ShareKind.NOTICE,
            title = title,
            subtitle = input.summary.trim().take(80),
            pills = listOfNotNull(
                input.category.trim().takeIf { it.isNotBlank() },
                input.statusLabel.trim().takeIf { it.isNotBlank() },
            ),
            fields = fields,
            lines = lines,
            note = listOf(
                if (sender.isNotBlank()) "发布 $sender" else "",
                attachText,
                if (input.timeLabel.isNotBlank()) input.timeLabel else "",
            ).filter { it.isNotBlank() }.joinToString(" · "),
            footerLeft = if (input.source.isNotBlank()) "来源 ${input.source}" else "班级工具 · 通知",
            footerRight = input.timeLabel,
            link = link(
                kind = ShareKind.NOTICE,
                baseUrl = baseUrl,
                title = "班级通知｜$title",
                text = input.summary.trim().take(60),
            ),
        )
    }

    // ------------------------------------------------------------ 投票卡

    /** 投票分享输入：**只接受选项文字**（结构上不提供票数/百分比，避免结果外泄）。 */
    data class VoteShareInput(
        val title: String,
        val status: String = "",
        val modeText: String = "",
        val ownerText: String = "",
        val deadlineText: String = "",
        val description: String = "",
        val tags: List<String> = emptyList(),
        /** 选项文字（最多 4 个；不包含票数/百分比）。 */
        val optionTexts: List<String> = emptyList(),
        val timeLabel: String = "",
    )

    /**
     * 投票卡：标题 / 状态与规则胶囊 / 发起人 / 截止 / 说明 / 至多 4 个选项文字；
     * 固定 `maskedResults = true`，并提示「结果请打开投票页查看」。
     */
    fun voteCard(input: VoteShareInput, baseUrl: String): ShareCardView {
        val title = input.title.trim().ifBlank { "班级投票" }
        val pills = listOf(
            listOf(input.status.trim(), input.modeText.trim())
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { "进行中" },
        )
        val lines = buildList {
            if (input.description.isNotBlank()) {
                add(ShareLine("说明", input.description.trim().take(60)))
            }
            input.optionTexts
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .take(4)
                .forEachIndexed { index, text ->
                    add(ShareLine("选项 ${index + 1}", text))
                }
            input.ownerText.trim().takeIf { it.isNotBlank() }?.let { add(ShareLine("发起", it)) }
            input.deadlineText.trim().takeIf { it.isNotBlank() }?.let { add(ShareLine("截止", it)) }
        }
        return ShareCardView(
            kind = ShareKind.VOTE,
            title = title,
            subtitle = input.tags.filter { it.isNotBlank() }.take(3).joinToString(" · "),
            pills = pills,
            lines = lines,
            note = "结果请打开投票页查看",
            footerLeft = "班级工具 · 投票",
            footerRight = input.timeLabel,
            maskedResults = true,
            link = link(
                kind = ShareKind.VOTE,
                baseUrl = baseUrl,
                title = "班级投票｜$title",
                text = "打开查看选项并参与投票",
            ),
        )
    }
}
