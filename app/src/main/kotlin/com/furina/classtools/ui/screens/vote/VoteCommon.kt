package com.furina.classtools.ui.screens.vote

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.StopCircle
import com.furina.classtools.ui.components.light.CtLightAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.vote.PollCountdown
import com.furina.classtools.data.vote.PollSettings
import com.furina.classtools.data.vote.VoteError
import com.furina.classtools.data.vote.VoteField
import com.furina.classtools.data.vote.VoteLimits
import com.furina.classtools.data.vote.VoteLogic
import com.furina.classtools.data.vote.VoteValidationError
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.expressive.CtBottomSheet
import com.furina.classtools.ui.components.expressive.CtBottomSheetHeader
import com.furina.classtools.ui.components.expressive.CtSegmentOption
import com.furina.classtools.ui.components.expressive.CtSegmentedButtons
import com.furina.classtools.ui.components.tool.CtToolCard
import com.furina.classtools.ui.components.tool.CtToolChip
import com.furina.classtools.ui.components.tool.CtToolErrorState
import com.furina.classtools.ui.components.tool.CtToolPrimaryButton
import com.furina.classtools.ui.components.tool.CtToolSegmented
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtShape
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.CtSpace
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** 列表/详情轮询间隔（页面可见时；Lead 接线无需传参）。 */
internal const val VOTE_POLL_INTERVAL_MS: Long = 15_000L

/** 一秒一跳的本地时钟（用于倒计时；页面不可见时由调用方停用）。 */
@Composable
internal fun rememberNowTick(enabled: Boolean = true): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        while (isActive) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    return now
}

/**
 * 以服务端时间为基准 + 本地流逝时间推算「现在」：
 * `serverNow > 0` 时 = serverNow + (localNowClient - localAtFetch)，否则直接用本地时钟。
 */
internal fun voteEffectiveNow(serverNow: Long, localAtFetch: Long, localNowClient: Long): Long =
    if (serverNow > 0L) serverNow + (localNowClient - localAtFetch) else localNowClient

/**
 * v6 可点击工具卡：CtToolCard 外壳（24dp/软描边/统一 padding）+ 按压缩放（稳定优先，不做共享元素）。
 * 用于投票列表/详情/发起页的可点卡片。
 */
@Composable
internal fun VoteToolCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = CtMotion.spatialFast,
        label = "voteToolCardPress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(CtRadius.hero))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        CtToolCard(contentPadding = contentPadding, content = content)
    }
}
/** 倒计时胶囊：不限时不显示；已结束显示「已结束」；其余显示「剩 mm:ss / hh:mm:ss」。 */
@Composable
internal fun VoteCountdownPill(
    closed: Boolean,
    deadline: Long,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    when (val countdown = VoteLogic.countdown(closed, deadline, nowMillis)) {
        PollCountdown.Unlimited -> Unit
        PollCountdown.Closed -> CtPill(
            text = "已结束",
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is PollCountdown.Remaining -> CtPill(
            text = countdown.text,
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

/** 标签行（规则 / 范围 / 状态），自动换行。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VoteTagRow(
    tags: List<String>,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.forEach { tag -> CtPill(text = tag) }
    }
}

/** 构造列表/详情通用标签（规则 + 可见范围 + 我已投/我发起）。 */
internal fun voteTags(
    settings: PollSettings,
    closed: Boolean,
    hasVoted: Boolean,
    audienceTag: String?,
    mine: Boolean = false,
): List<String> = buildList {
    addAll(settings.tags(hasVoted = hasVoted, closed = closed))
    audienceTag?.let { add(it) }
    if (hasVoted) add("我已投")
    if (mine) add("我发起")
}

/** 结果百分比条（带 fill 动画）。 */
@Composable
internal fun VotePercentBar(
    percent: Double,
    modifier: Modifier = Modifier,
    fillColor: Color = MaterialTheme.colorScheme.primary,
) {
    val fraction by animateFloatAsState(
        targetValue = (percent / 100.0).coerceIn(0.0, 1.0).toFloat(),
        animationSpec = CtMotion.effects,
        label = "votePercentFill",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = fraction)
                .clip(CircleShape)
                .background(fillColor),
        )
    }
}

/** 错误态（可重试）。 */
@Composable
internal fun VoteErrorContent(
    error: VoteError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CtToolErrorState(
            title = "投票加载失败",
            message = error.message,
            onRetry = onRetry,
        )
    }
}

/** 离线/缓存提示条。 */
@Composable
internal fun VoteOfflineBanner(
    warning: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CtRadius.cardCompact),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = warning?.takeIf { it.isNotBlank() } ?: "网络不可用，显示本机缓存",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}

/** 字段错误行。 */
@Composable
internal fun VoteFieldError(text: String?, modifier: Modifier = Modifier) {
    if (text.isNullOrBlank()) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier,
    )
}

/** 按字段取第一条错误文案。 */
internal fun voteErrorFor(errors: List<VoteValidationError>, field: VoteField): String? =
    errors.firstOrNull { it.field == field }?.message

/** 按选项下标取错误（code 形如 OPTION_TEXT_0 / OPTION_IMAGE_1）。 */
internal fun voteOptionError(errors: List<VoteValidationError>, index: Int, prefix: String): String? =
    errors.firstOrNull { it.code == "${prefix}_$index" }?.message

/** 管理动作行（底部弹层内）。 */
@Composable
internal fun VoteManageActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    subtitle: String? = null,
) {
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        danger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(CtRadius.cardCompact))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.titleSmall, color = contentColor)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 管理投票底部弹层：编辑（total==0）/提前结束/延长重开/删除。
 * 危险动作由调用方再弹确认框。
 */
@Composable
internal fun VoteManageSheet(
    canEdit: Boolean,
    closed: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onClose: () -> Unit,
    onExtend: () -> Unit,
    onDelete: () -> Unit,
) {
    CtBottomSheet(onDismissRequest = onDismiss) {
        VoteManageContent(
            canEdit = canEdit,
            closed = closed,
            onEdit = onEdit,
            onClose = onClose,
            onExtend = onExtend,
            onDelete = onDelete,
        )
    }
}

/** 管理动作列表内容（独立出来便于预览/测试；由 [VoteManageSheet] 包在底部弹层里）。 */
@Composable
internal fun VoteManageContent(
    canEdit: Boolean,
    closed: Boolean,
    onEdit: () -> Unit,
    onClose: () -> Unit,
    onExtend: () -> Unit,
    onDelete: () -> Unit,
) {
    CtBottomSheetHeader(
        title = "管理投票",
        subtitle = "发起人与管理员可操作",
    )
        VoteManageActionRow(
            icon = Icons.Rounded.Edit,
            label = "编辑投票",
            subtitle = if (canEdit) "仅 0 票时可编辑" else "已有投票，不能编辑",
            enabled = canEdit,
            onClick = onEdit,
        )
        VoteManageActionRow(
            icon = Icons.Rounded.StopCircle,
            label = "提前结束",
            subtitle = if (closed) "投票已结束" else "结束后不可再投票",
            enabled = !closed,
            onClick = onClose,
        )
        VoteManageActionRow(
            icon = Icons.Rounded.Schedule,
            label = if (closed) "重新开放" else "延长截止",
            subtitle = "快捷延长 30 / 60 / 120 分钟",
            onClick = onExtend,
        )
        VoteManageActionRow(
            icon = Icons.Rounded.DeleteOutline,
            label = "删除投票",
            subtitle = "删除后不可恢复",
            danger = true,
            onClick = onDelete,
        )
    Spacer(modifier = Modifier.height(CtSpace.md))
}

/** 通用确认框（危险操作二次确认）。 */
@Composable
internal fun VoteConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
) {
    CtLightAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText,
                    color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        shape = CtShapeTokens.dialog,
    )
}

/** 延长截止时间对话框（30/60/120 分钟快捷）。 */
@Composable
internal fun VoteExtendDialog(
    nowMillis: Long,
    onDismiss: () -> Unit,
    onExtend: (deadline: Long) -> Unit,
) {
    var selectedMinutes by remember { mutableStateOf(30) }
    CtLightAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (selectedMinutes > 0) "延长截止时间" else "重新开放") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "从现在起延长：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CtToolSegmented(
                    options = VoteLimits.QUICK_MINUTES.map { minutes -> "$minutes 分钟" },
                    selectedIndex = VoteLimits.QUICK_MINUTES.indexOf(selectedMinutes).coerceAtLeast(0),
                    onSelect = { index ->
                        VoteLimits.QUICK_MINUTES.getOrNull(index)?.let { selectedMinutes = it }
                    },
                )
                Text(
                    text = "新截止：${VoteLogic.formatAt(VoteLogic.quickDeadline(nowMillis, selectedMinutes))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onExtend(VoteLogic.quickDeadline(nowMillis, selectedMinutes)) }) {
                Text("延长")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        shape = CtShapeTokens.dialog,
    )
}

/** 兼容旧字段：投票规则标签（详情/列表共用）。 */
internal fun pollRuleTags(settings: PollSettings, closed: Boolean, hasVoted: Boolean): List<String> =
    settings.tags(hasVoted = hasVoted, closed = closed)

/** 选项总数/参与人数格式化。 */
internal fun voteParticipantText(total: Int): String = "$total 人已投"

/** 本机时区格式化截止时间（MM-dd HH:mm）。 */
internal fun voteDeadlineText(deadline: Long): String =
    if (deadline <= 0L) "不限时" else "截止 ${VoteLogic.formatAt(deadline)}"

/** 供 UI 预览/测试：惰性占位，避免未使用告警。 */
@Suppress("unused")
private val voteCommonIconHints: List<ImageVector> = emptyList()
