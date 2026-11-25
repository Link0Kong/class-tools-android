@file:OptIn(ExperimentalLayoutApi::class)

package com.furina.classtools.ui.screens.notices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.notices.NoticeAttachmentSource
import com.furina.classtools.data.notices.MergedMessage
import com.furina.classtools.data.notices.NoticeGroupDto
import com.furina.classtools.data.notices.NoticeItemMeta
import com.furina.classtools.data.notices.NoticeManualAttachment
import com.furina.classtools.data.notices.NoticeSegment
import com.furina.classtools.data.notices.NoticeStatus
import com.furina.classtools.data.notices.NoticeStatusMachine
import com.furina.classtools.data.ppt.PptImageLoader
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.expressive.CtBottomSheet
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtShape
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.CtSpace

/**
 * 通知详情底部弹层（原生渲染）。
 *
 * 内容分区对齐小程序 `pages/nt` 展开态：
 * 标题 + 分类/状态 + 来源/发送者/发布时间 →
 * AI 摘要与要点 → 字段分段（时间/地点/要求…）→ 图片网格（缩略图 + 全屏大图）→
 * 附件（下载/系统打开、待获取占位、压缩包内文件清单）→ 链接/收集表/合并消息 → 原文条目；
 * 底部动作：加入日程 / 提醒我 / 标记已处理（人工通知为置顶）。
 *
 * 图片按需加载：列表阶段不保留 base64，[rawAttachmentLoader] 为 null 时降级为元信息占位。
 */
@Composable
internal fun NoticeDetailSheet(
    item: NoticeUiItem,
    rawAttachmentLoader: NoticeRawAttachmentLoader?,
    imageLoader: PptImageLoader?,
    onAddToCalendar: ((NoticeGroupDto) -> Unit)?,
    onRemindNotice: ((NoticeGroupDto) -> Unit)?,
    onAddTask: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onToggleDone: () -> Unit,
    onTogglePin: () -> Unit,
    /** 分享这条通知（生成 5:4 分享卡；标题/分类/状态/摘要/要点/时间地点/来源）。 */
    onShare: ((NoticeGroupDto, Int) -> Unit)? = null,
    onAdminAction: ((NoticeAdminAction) -> Unit)? = null,
    loading: Boolean = false,
    loadError: String? = null,
    onRetry: () -> Unit = {},
    nowMillis: Long = System.currentTimeMillis(),
) {
    CtBottomSheet(onDismissRequest = onDismiss) {
        NoticeDetailSheetContent(
            item = item,
            rawAttachmentLoader = rawAttachmentLoader,
            imageLoader = imageLoader,
            onAddToCalendar = onAddToCalendar,
            onRemindNotice = onRemindNotice,
            onAddTask = onAddTask,
            onDismiss = onDismiss,
            onToggleDone = onToggleDone,
            onTogglePin = onTogglePin,
            onShare = onShare,
            onAdminAction = onAdminAction,
            loading = loading,
            loadError = loadError,
            onRetry = onRetry,
            nowMillis = nowMillis,
        )
    }
}

/**
 * 详情弹层内容（独立 Composable，便于 `@Preview` 静态渲染 / 单测截图）。
 *
 * 弹层外框（[CtBottomSheet]）负责圆角/拖拽把手/遮罩；本函数只负责内容与动作区。
 */
@Composable
internal fun NoticeDetailSheetContent(
    item: NoticeUiItem,
    rawAttachmentLoader: NoticeRawAttachmentLoader? = null,
    imageLoader: PptImageLoader? = null,
    onAddToCalendar: ((NoticeGroupDto) -> Unit)? = null,
    onRemindNotice: ((NoticeGroupDto) -> Unit)? = null,
    onAddTask: (() -> Unit)? = null,
    onDismiss: () -> Unit = {},
    onToggleDone: () -> Unit = {},
    onTogglePin: () -> Unit = {},
    /** 分享这条通知（详情底栏「分享」）。 */
    onShare: ((NoticeGroupDto, Int) -> Unit)? = null,
    onAdminAction: ((NoticeAdminAction) -> Unit)? = null,
    loading: Boolean = false,
    loadError: String? = null,
    onRetry: () -> Unit = {},
    /** 渲染时 now（§3 时间显示：所有日期 token → `M月D日（相对周）`）。 */
    nowMillis: Long = System.currentTimeMillis(),
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(end = CtSpace.xs)) {
            Icon(Icons.Rounded.Close, contentDescription = "关闭通知详情")
        }

        Column(
            modifier = Modifier.weight(1f, fill = false).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CtSpace.lg)
                .padding(bottom = CtSpace.md),
            verticalArrangement = Arrangement.spacedBy(CtSpace.sm),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CtSpace.xs),
                verticalArrangement = Arrangement.spacedBy(CtSpace.xs),
            ) {
                NoticeCatPill(cat = item.cat, tone = item.tone)
                item.status?.let { NoticeStatusLabel(it) }
                if (item.done) {
                    CtPill(
                        text = "已处理",
                        containerColor = scheme.primaryContainer,
                        contentColor = scheme.onPrimaryContainer,
                    )
                }
            }
            Text(
                text = item.title.ifBlank { "通知" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            val metadata = detailMetaText(item, nowMillis)
            if (metadata.isNotBlank()) {
                Text(metadata, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            DetailTagLine(item = item, nowMillis = nowMillis)
            HorizontalDivider()

            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("正在加载完整通知…", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            if (loadError != null) {
                Text(loadError, style = MaterialTheme.typography.bodyMedium, color = scheme.error)
                TextButton(onClick = onRetry) { Text("重试加载详情") }
            }

            when {
                item.auto != null -> AutoNoticeDetail(
                    item = item,
                    rawAttachmentLoader = rawAttachmentLoader,
                    imageLoader = imageLoader,
                    nowMillis = nowMillis,
                )

                item.manual != null -> ManualNoticeDetail(
                    item = item,
                    rawAttachmentLoader = rawAttachmentLoader,
                    imageLoader = imageLoader,
                    nowMillis = nowMillis,
                )
            }

            HorizontalDivider()
            DetailActions(
                item = item,
                onAddToCalendar = onAddToCalendar,
                onRemindNotice = onRemindNotice,
                onToggleDone = onToggleDone,
                onTogglePin = onTogglePin,
                onShare = onShare,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(CtSpace.sm)) {
                if (onAddTask != null) TextButton(onClick = onAddTask) { Text("加入我的任务") }
                if (onAdminAction != null) {
                    TextButton(onClick = { onAdminAction(NoticeAdminAction.EDIT) }) { Text("编辑通知") }
                    TextButton(onClick = { onAdminAction(NoticeAdminAction.VISIBILITY) }) {
                        Text(if (item.hidden) "恢复通知" else "隐藏通知")
                    }
                }
            }
        }
    }
}

/** 来源 / 发送者 / 发布时间（详情头部第二行；§3：绝对时间为 `M月D日（相对周） HH:mm`）。 */
private fun detailMetaText(item: NoticeUiItem, nowMillis: Long): String = buildList {
    if (item.timeLabel.isNotBlank() && item.timeLabel != item.status?.label) add(item.timeLabel)
    if (item.source.isNotBlank()) add(item.source)
    if (item.senderLine.isNotBlank()) add(item.senderLine)
    if (item.ts > 0) add("发布于 ${NoticeStatusMachine.absoluteWhenText(item.ts, nowMillis)}")
}.joinToString(" · ")

/** 长期安排下次 / 已隐藏 / 已更新 / 多源合并（详情头部第三行，有才显示）。 */
@Composable
private fun DetailTagLine(item: NoticeUiItem, nowMillis: Long) {
    val auto = item.auto
    // 长期安排定义卡：下次 `M月D日（周X）HH:mm`（§2.2/§3；期次卡状态胶囊已显示本次，不重复）
    val nextText = if (item.occurrenceKey == null) item.recurNextText.ifBlank { null } else null
    val merged = auto?.group?.mergedSources.orEmpty()
    if (!item.hidden && !item.edited && merged.isEmpty() && nextText == null) return
    FlowRow(
        modifier = Modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (nextText != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = nextText.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        if (merged.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Hub,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "多源合并 ${merged.joinToString("、")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (item.edited) {
            CtPill(
                text = "已更新",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        if (item.hidden) {
            CtPill(text = "已隐藏")
        }
    }
}

// -----------------------------------------------------------------------------
// 自动通知详情
// -----------------------------------------------------------------------------

/** 详情正文统一格式化（契约 §3）：相对→绝对日期替换 + 日期 token → `M月D日（相对周）`。 */
private fun detailText(text: String, anchorMillis: Long?, nowMillis: Long): String =
    NoticeStatusMachine.displayText(text, anchorMillis, nowMillis)

@Composable
private fun AutoNoticeDetail(
    item: NoticeUiItem,
    rawAttachmentLoader: NoticeRawAttachmentLoader?,
    imageLoader: PptImageLoader?,
    nowMillis: Long,
) {
    val entry = item.auto ?: return
    val group = entry.group
    val anchor = NoticeStatusMachine.anchorOf(group, entry.items)

    val aiSummary = detailText(group.aiSummary.ifBlank { group.summary }, anchor, nowMillis)
    val hasAi = aiSummary.isNotBlank() || group.aiPoints.isNotEmpty()
    if (hasAi) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CtRadius.cardCompact),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CtShapeTokens.chip,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Text(
                            text = "AI",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "摘要",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (aiSummary.isNotBlank()) {
                    Text(
                        text = aiSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (group.aiPoints.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        group.aiPoints.forEach { point ->
                            Surface(
                                shape = CtShapeTokens.chip,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ) {
                                Text(
                                    text = detailText(point, anchor, nowMillis),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val segments = group.aiSegments.filter { it.text.isNotBlank() }
    segments.forEach { segment -> DetailSegmentRow(segment = segment, anchorMillis = anchor, nowMillis = nowMillis) }

    val images = entry.items.filterIsInstance<NoticeItemMeta.Image>()
    val others = entry.items.filterNot { it is NoticeItemMeta.Image || it is NoticeItemMeta.Text || it is NoticeItemMeta.Merged }
    if (images.isNotEmpty()) {
        DetailImageGrid(
            images = images,
            rowId = entry.rowId,
            rawAttachmentLoader = rawAttachmentLoader,
            imageLoader = imageLoader,
        )
    }
    others.forEach { meta ->
        DetailItemRow(
            meta = meta,
            rowId = entry.rowId,
            rawAttachmentLoader = rawAttachmentLoader,
            imageLoader = imageLoader,
            anchorMillis = anchor,
            nowMillis = nowMillis,
        )
    }

    NoticeRawMessagesButton(item, nowMillis)
    if (!hasAi && segments.isEmpty() && entry.items.isEmpty()) {
        Text(
            text = "暂无更多内容",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 字段分段（时间 / 地点 / 要求 …）：左侧类型 chip + 右侧内容，大字体自动换行。 */
@Composable
private fun DetailSegmentRow(segment: NoticeSegment, anchorMillis: Long?, nowMillis: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            shape = CtShapeTokens.chip,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Text(
                text = segment.type.ifBlank { "信息" },
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Text(
            text = detailText(segment.text, anchorMillis, nowMillis),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 图片网格：1 张整行，多张两列（缩略图点击看大图）。 */
@Composable
private fun DetailImageGrid(
    images: List<NoticeItemMeta.Image>,
    rowId: String,
    rawAttachmentLoader: NoticeRawAttachmentLoader?,
    imageLoader: PptImageLoader?,
) {
    if (images.size == 1) {
        NoticeAttachmentThumb(
            loader = rawAttachmentLoader,
            rowId = images[0].rowId.ifBlank { rowId },
            ordinal = images[0].imageOrdinal.takeIf { it >= 0 } ?: 0,
            manual = false,
            title = images[0].caption.ifBlank { "图片" },
            caption = images[0].caption,
            url = images[0].url,
            imageLoader = imageLoader,
        )
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        images.chunked(2).forEachIndexed { rowIndex, pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pair.forEachIndexed { columnIndex, image ->
                    val ordinal = rowIndex * 2 + columnIndex
                    Box(modifier = Modifier.weight(1f)) {
                        NoticeAttachmentThumb(
                            loader = rawAttachmentLoader,
                            rowId = image.rowId.ifBlank { rowId },
                            ordinal = image.imageOrdinal.takeIf { it >= 0 } ?: ordinal,
                            manual = false,
                            title = image.caption.ifBlank { "图片 ${ordinal + 1}" },
                            caption = image.caption,
                            url = image.url,
                            imageLoader = imageLoader,
                        )
                    }
                }
                if (pair.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** 非图片条目：文本 / 文件 / 收集表 / 链接 / 合并消息 / 未知占位。 */
@Composable
private fun DetailItemRow(
    meta: NoticeItemMeta,
    rowId: String,
    rawAttachmentLoader: NoticeRawAttachmentLoader?,
    imageLoader: PptImageLoader?,
    anchorMillis: Long?,
    nowMillis: Long,
) {
    when (meta) {
        is NoticeItemMeta.Text -> DetailTextItem(meta = meta, anchorMillis = anchorMillis, nowMillis = nowMillis)

        is NoticeItemMeta.File -> NoticeFileAttachmentRow(meta = meta, loader = rawAttachmentLoader)

        is NoticeItemMeta.Form -> DetailInfoRow(
            iconText = "表",
            title = detailText(meta.name, anchorMillis, nowMillis),
            detail = detailText(
                listOf(meta.statusText, meta.note)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                anchorMillis,
                nowMillis,
            ),
            url = meta.url,
        )

        is NoticeItemMeta.Link -> DetailInfoRow(
            iconText = when (meta.kind) {
                "mini" -> "程"
                "article" -> "公"
                else -> "链"
            },
            title = detailText(meta.name.ifBlank { meta.title }, anchorMillis, nowMillis),
            detail = detailText(
                listOf(meta.host, meta.account)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                anchorMillis,
                nowMillis,
            ),
            url = meta.url,
        )

        is NoticeItemMeta.Merged -> DetailMergedBlock(
            meta = meta,
            anchorMillis = anchorMillis,
            nowMillis = nowMillis,
        )

        is NoticeItemMeta.Image -> NoticeAttachmentThumb(
            loader = rawAttachmentLoader,
            rowId = rowId,
            ordinal = 0,
            manual = false,
            title = meta.caption.ifBlank { "图片" },
            caption = meta.caption,
            url = meta.url,
            imageLoader = imageLoader,
        )

        is NoticeItemMeta.Unknown -> DetailInfoRow(
            iconText = "附",
            title = "附件",
            detail = "暂不支持预览",
        )
    }
}

@Composable
internal fun NoticeRawMessagesButton(item: NoticeUiItem, nowMillis: Long = System.currentTimeMillis()) {
    val entry = item.auto
    val raw = entry?.items.orEmpty().filter { it is NoticeItemMeta.Text || it is NoticeItemMeta.Merged }
    val body = item.manual?.row?.body.orEmpty()
    if (raw.isEmpty() && body.isBlank()) return
    var expanded by remember(item.key) { mutableStateOf(false) }
    TextButton(onClick = { expanded = true }) { Text("原始消息") }
    if (expanded) {
        val anchor = entry?.let { NoticeStatusMachine.anchorOf(it.group, it.items) } ?: item.ts
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text("原始消息") },
            text = {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (body.isNotBlank()) Text(body)
                        raw.forEach { meta ->
                            when (meta) {
                                is NoticeItemMeta.Text -> DetailTextItem(meta, anchor, nowMillis)
                                is NoticeItemMeta.Merged -> DetailMergedBlock(meta, anchor, nowMillis)
                                else -> Unit
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { expanded = false }) { Text("关闭") } },
        )
    }
}

@Composable
private fun DetailTextItem(meta: NoticeItemMeta.Text, anchorMillis: Long?, nowMillis: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = CtShapeTokens.chip,
                color = if (meta.isFollow) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (meta.isFollow) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Text(
                    text = meta.label.ifBlank { "消息" },
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            val metaText = detailText(
                listOf(meta.fromRole, meta.fromName, meta.timeText)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                anchorMillis,
                nowMillis,
            )
            if (metaText.isNotBlank()) {
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            text = detailText(meta.text.ifBlank { "（空）" }, anchorMillis, nowMillis),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 收集表 / 链接 / 小程序 / 公众号 / 未知附件：图标块 + 标题 + 说明（小程序 nt-linkrow 同口径）。 */
@Composable
private fun DetailInfoRow(
    iconText: String,
    title: String,
    detail: String,
    url: String = "",
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = CtShapeTokens.listItem,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = iconText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (url.isNotBlank()) {
            val context = LocalContext.current
            val clipboard = LocalClipboardManager.current
            Column {
                if (NoticeAttachmentSource.isHttpUrl(url)) {
                    TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        if (runCatching { context.startActivity(intent) }.isFailure) {
                            clipboard.setText(AnnotatedString(url))
                            android.widget.Toast.makeText(context, "链接已复制", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("打开") }
                }
                TextButton(onClick = { clipboard.setText(AnnotatedString(url)) }) { Text("复制链接") }
            }
        }
    }
}

/** 合并转发的聊天记录块。 */
@Composable
private fun DetailMergedBlock(meta: NoticeItemMeta.Merged, anchorMillis: Long?, nowMillis: Long) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CtRadius.cardCompact),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = meta.name.ifBlank { "合并消息" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${meta.count} 条",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            meta.messages.forEach { message: MergedMessage ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val head = detailText(
                        listOf(message.fromBadge, message.fromName, message.timeText)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        anchorMillis,
                        nowMillis,
                    )
                    if (head.isNotBlank()) {
                        Text(
                            text = head,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = detailText(message.text, anchorMillis, nowMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 人工通知详情
// -----------------------------------------------------------------------------

@Composable
private fun ManualNoticeDetail(
    item: NoticeUiItem,
    rawAttachmentLoader: NoticeRawAttachmentLoader?,
    imageLoader: PptImageLoader?,
    nowMillis: Long,
) {
    val notice = item.manual ?: return
    NoticeRawMessagesButton(item, nowMillis)
    if (notice.attachments.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            notice.attachments.forEach { attachment ->
                ManualAttachmentRow(
                    attachment = attachment,
                    rowId = notice.row.id,
                    rawAttachmentLoader = rawAttachmentLoader,
                    imageLoader = imageLoader,
                )
            }
        }
    }
}

@Composable
private fun ManualAttachmentRow(
    attachment: NoticeManualAttachment,
    rowId: String,
    rawAttachmentLoader: NoticeRawAttachmentLoader?,
    imageLoader: PptImageLoader?,
) {
    val isImage = attachment.mime.startsWith("image/", ignoreCase = true)
    val url = attachment.url
    if (isImage && (url.isNotBlank() || (attachment.hasData && rawAttachmentLoader != null))) {
        NoticeAttachmentThumb(
            loader = rawAttachmentLoader,
            rowId = rowId,
            ordinal = attachment.index,
            manual = true,
            title = attachment.name,
            url = url,
            imageLoader = imageLoader,
        )
    } else if (url.isNotBlank() || (attachment.hasData && rawAttachmentLoader != null)) {
        // 新数据：人工通知附件也可能是外置签名 URL（图片以外走「下载 → 系统打开」）
        NoticeUrlAttachmentRow(
            title = attachment.name,
            detail = listOf(attachment.mime, attachment.sizeText)
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            source = url,
            fileName = attachment.name,
            resolve = { rawAttachmentLoader?.load(rowId, attachment.index, manual = true) },
        )
    } else {
        DetailInfoRow(
            iconText = "附",
            title = attachment.name,
            detail = listOf(
                attachment.mime,
                attachment.sizeText,
                if (isImage) "图片加载失败" else "暂不支持预览",
            ).filter { it.isNotBlank() }.joinToString(" · "),
        )
    }
}

// -----------------------------------------------------------------------------
// 底部动作
// -----------------------------------------------------------------------------

@Composable
private fun DetailActions(
    item: NoticeUiItem,
    onAddToCalendar: ((NoticeGroupDto) -> Unit)?,
    onRemindNotice: ((NoticeGroupDto) -> Unit)?,
    onToggleDone: () -> Unit,
    onTogglePin: () -> Unit,
    onShare: ((NoticeGroupDto, Int) -> Unit)? = null,
) {
    val group = item.auto?.group
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CtSpace.xs),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onShare != null && item.isAuto && group != null) {
            OutlinedButton(
                onClick = { onShare(group, item.attachmentCount) },
                modifier = Modifier.heightIn(min = 48.dp),
                shape = CtShapeTokens.pill,
            ) {
                Icon(
                    imageVector = Icons.Rounded.IosShare,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("分享")
            }
        }
        if (item.isAuto && group != null) {
            if (onAddToCalendar != null) {
                OutlinedButton(
                    onClick = { onAddToCalendar(group) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = CtShapeTokens.pill,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("加入日程")
                }
            }
            if (onRemindNotice != null) {
                OutlinedButton(
                    onClick = { onRemindNotice(group) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = CtShapeTokens.pill,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("提醒我")
                }
            }
            // 期次卡：本期单独已处理（marks ref = 期次 key，§2.3）；长期安排定义卡/已结束无 done
            val showDone = item.occurrenceKey != null || (!item.expired && item.recurrence == null)
            if (showDone) {
                Button(
                    onClick = onToggleDone,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = CtShapeTokens.pill,
                ) {
                    Icon(
                        imageVector = if (item.done) Icons.Rounded.Replay else Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            item.occurrenceKey != null && item.done -> "恢复本期未处理"
                            item.occurrenceKey != null -> "本次已处理"
                            item.done -> "恢复未处理"
                            else -> "标记已处理"
                        },
                    )
                }
            }
        } else {
            Button(
                onClick = onTogglePin,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = CtShapeTokens.pill,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(if (item.pinned) "取消置顶" else "置顶")
            }
        }
    }
}
