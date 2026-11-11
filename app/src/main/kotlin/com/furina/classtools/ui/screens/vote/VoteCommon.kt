package com.furina.classtools.ui.screens.vote

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.member.MemberCategory
import com.furina.classtools.data.member.MemberEntry
import com.furina.classtools.data.vote.PollSettings
import com.furina.classtools.data.vote.PollCountdown
import com.furina.classtools.data.vote.VoteError
import com.furina.classtools.data.vote.VoteField
import com.furina.classtools.data.vote.VoteLimits
import com.furina.classtools.data.vote.VoteLogic
import com.furina.classtools.data.vote.VoteValidationError
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.expressive.CtBottomSheet
import com.furina.classtools.ui.components.expressive.CtBottomSheetFooter
import com.furina.classtools.ui.components.expressive.CtBottomSheetHeader
import com.furina.classtools.ui.components.expressive.CtSegmentOption
import com.furina.classtools.ui.components.expressive.CtSegmentedButtons
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtShapeTokens
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(36.dp),
        )
        Text(
            text = error.message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "下拉可重试，或稍后再试",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRetry, shape = CtShapeTokens.pill) { Text("重试") }
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
        shape = CtShapeTokens.listItem,
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
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
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
    Spacer(modifier = Modifier.height(12.dp))
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
    AlertDialog(
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (selectedMinutes > 0) "延长截止时间" else "重新开放") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "从现在起延长：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CtSegmentedButtons(
                    options = VoteLimits.QUICK_MINUTES.map { minutes ->
                        CtSegmentOption(key = minutes.toString(), label = "$minutes 分钟")
                    },
                    selectedKey = selectedMinutes.toString(),
                    onSelect = { key -> key.toIntOrNull()?.let { selectedMinutes = it } },
                    showCheckIcon = false,
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

/**
 * 指定成员选择器（可见范围=custom）：
 * 班委 / 团员 / 全班 三分类 + 姓名/座号搜索（**不显示学号**），座号多选。
 */
@Composable
internal fun VoteMemberPickerSheet(
    source: VoteMemberSource,
    initialSeats: Set<Int>,
    onDismiss: () -> Unit,
    onConfirm: (Set<Int>) -> Unit,
) {
    var category by remember { mutableStateOf(MemberCategory.ALL) }
    var selected by remember { mutableStateOf(initialSeats) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var retryToken by remember { mutableStateOf(0) }
    val cache = remember { mutableStateMapOf<MemberCategory, List<MemberEntry>>() }
    var members by remember { mutableStateOf<List<MemberEntry>>(emptyList()) }

    LaunchedEffect(source, category, retryToken) {
        val cached = cache[category]
        if (cached != null) {
            members = cached
            loading = false
            failed = false
            return@LaunchedEffect
        }
        loading = true
        failed = false
        runCatching { source.load(category) }
            .onSuccess { list ->
                cache[category] = list
                members = list
                loading = false
            }
            .onFailure {
                failed = true
                loading = false
            }
    }

    val filtered = remember(members, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            members
        } else {
            members.filter { member ->
                member.name.contains(q, ignoreCase = true) || member.seat.toString().contains(q)
            }
        }
    }

    CtBottomSheet(onDismissRequest = onDismiss) {
        CtBottomSheetHeader(
            title = "选择可见成员",
            subtitle = "已选 ${selected.size} 人 · 仅显示姓名与座号",
        )
        CtSegmentedButtons(
            options = MemberCategory.entries.map { CtSegmentOption(key = it.name, label = it.label) },
            selectedKey = category.name,
            onSelect = { key -> MemberCategory.entries.firstOrNull { it.name == key }?.let { category = it } },
            modifier = Modifier.padding(horizontal = 24.dp),
            showCheckIcon = false,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(20) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            singleLine = true,
            placeholder = { Text("搜索姓名 / 座号") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val currentSelected = filtered.count { it.seat in selected }
            Text(
                text = "当前 ${filtered.size} 人 · 已选 $currentSelected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { selected = selected + filtered.map { it.seat } }) { Text("全选") }
            TextButton(onClick = { selected = selected - filtered.map { it.seat }.toSet() }) { Text("清空") }
        }
        Box(modifier = Modifier.heightIn(min = 160.dp, max = 420.dp)) {
            when {
                loading -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(
                        text = "加载名单…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                failed -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("名单加载失败", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { retryToken += 1 }) { Text("重试") }
                }

                filtered.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (query.isBlank()) "名单为空" else "未找到 · 换个关键词",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(items = filtered, key = { it.seat }) { member ->
                        val checked = member.seat in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable {
                                    selected = if (checked) selected - member.seat else selected + member.seat
                                }
                                .padding(horizontal = 24.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(
                                text = "${member.seat}  ${member.name}",
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (member.badge.isNotBlank()) {
                                CtPill(text = member.badge)
                            }
                        }
                    }
                }
            }
        }
        CtBottomSheetFooter(
            primaryText = "确定（${selected.size}）",
            onPrimary = { onConfirm(selected) },
            primaryEnabled = selected.isNotEmpty(),
            secondaryText = "取消",
            onSecondary = onDismiss,
        )
    }
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
