package com.furina.classtools.features.share

import com.furina.classtools.data.notices.NoticeBucket
import com.furina.classtools.data.notices.NoticeGroupDto
import com.furina.classtools.data.notices.NoticeStatus
import com.furina.classtools.data.notices.NoticeStatusMachine
import com.furina.classtools.data.vote.Poll
import com.furina.classtools.data.vote.PollDetail
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 业务模型 → 分享输入的映射（纯函数，可单测）。
 *
 * 只读取「页面已经展示给用户」的字段：
 * - **通知**：标题/分类/状态/摘要/要点/时间地点截止等结构化字段/来源/发布者/附件数；
 * - **投票**：标题/状态/规则/发起人/截止/说明/选项**文字**——票数与百分比不进入分享模型
 *   （[com.furina.classtools.data.vote.PollDetail.counts] 刻意不读取）。
 */
object ShareMappers {

    private val MM_DD_HH_MM: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)

    /** 通知卡输入（[attachmentCount] 只用于展示数量）。 */
    fun notice(
        group: NoticeGroupDto,
        attachmentCount: Int = 0,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ShareSpecs.NoticeShareInput {
        val status: NoticeStatus = runCatching { NoticeStatusMachine.statusOf(group, nowMillis, zone) }
            .getOrElse {
                NoticeStatus(key = "none", label = "", cls = "none", expired = false, bucket = NoticeBucket.NONE)
            }
        val segments = group.aiSegments.mapNotNull { segment ->
            val text = segment.text.trim()
            if (text.isEmpty()) return@mapNotNull null
            ShareLine(label = segment.type.trim().ifBlank { "信息" }, text = text)
        }
        // 无 AI 结构化字段时退化用「截止/活动时间」文本，保证卡片仍有时间信息。
        val fallback = listOfNotNull(
            group.deadline.trim().takeIf { it.isNotBlank() }?.let { ShareLine("截止", it) },
            group.eventAt.trim().takeIf { it.isNotBlank() }?.let { ShareLine("活动", it) },
            group.signupStart.trim().takeIf { it.isNotBlank() }?.let { ShareLine("报名", it) },
        )
        return ShareSpecs.NoticeShareInput(
            title = group.title,
            category = group.cat,
            statusLabel = status.label,
            summary = group.aiSummary.trim().ifBlank { group.summary.trim() },
            points = group.aiPoints,
            segments = (segments + fallback).distinctBy { it.label + it.text },
            senderName = group.senderName,
            senderRole = group.senderRole,
            source = group.source,
            timeLabel = group.timeLabel.trim(),
            attachmentCount = attachmentCount,
        )
    }

    /** 投票卡输入：**只带选项文字**，不读取 counts/百分比（隐私口径）。 */
    fun vote(
        poll: Poll,
        detail: PollDetail? = null,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ShareSpecs.VoteShareInput {
        val closed = detail?.closed ?: poll.isClosedStatus
        val statusText = if (closed) "已结束" else "进行中"
        val deadlineText = if (poll.deadline <= 0L) {
            "不限时"
        } else {
            MM_DD_HH_MM.format(Instant.ofEpochMilli(poll.deadline).atZone(zone))
        }
        return ShareSpecs.VoteShareInput(
            title = poll.title,
            status = statusText,
            modeText = poll.mode.label,
            ownerText = poll.ownerName.trim().takeIf { it.isNotBlank() }?.let { "发起人 $it" }.orEmpty(),
            deadlineText = deadlineText,
            description = poll.description,
            tags = poll.settings.tags(hasVoted = detail?.hasVoted == true, closed = closed),
            optionTexts = poll.options.map { it.text },
            timeLabel = MM_DD_HH_MM.format(Instant.ofEpochMilli(nowMillis).atZone(zone)),
        )
    }
}
