package com.furina.classtools.ui.screens.notices

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.notices.NoticeBucket
import com.furina.classtools.data.notices.NoticeStatus
import com.furina.classtools.ui.components.tool.CtToolSectionHeader
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.CtSpace

/**
 * 列表中的一张通知卡（错峰入场 + 按压动效包装；[mode] 决定状态胶囊 / 操作区，见契约 §2.2/§2.3）。
 */
@Composable
private fun NoticeCardEntry(
    item: NoticeUiItem,
    mode: NoticeCardMode,
    index: Int,
    itemKey: String,
    reduceMotion: Boolean,
    appearedKeys: SnapshotStateMap<String, Boolean>,
    previewEntranceProgress: Float?,
    previewPressedKey: String?,
    previewExpandedKey: String?,
    selectedKey: String?,
    onOpen: (NoticeUiItem) -> Unit,
    onToggleDone: (NoticeUiItem) -> Unit,
    onTogglePin: (NoticeUiItem) -> Unit,
    onAddTask: ((NoticeUiItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    NoticeEntrance(
        itemKey = itemKey,
        index = index,
        reduceMotion = reduceMotion,
        appearedKeys = appearedKeys,
        previewBaseProgress = previewEntranceProgress,
        modifier = modifier,
    ) {
        NoticeCard(
            item = item,
            mode = mode,
            reduceMotion = reduceMotion,
            expanded = selectedKey == item.key || previewExpandedKey == item.key,
            previewPressed = previewPressedKey == item.key,
            onOpen = { onOpen(item) },
            onToggleDone = { onToggleDone(item) },
            onTogglePin = { onTogglePin(item) },
            onAddTask = onAddTask?.let { add -> { add(item) } },
        )
    }
}

/**
 * 通知列表（分区渲染；契约 §2.2）：
 * 主列表（含期次卡）→「资料 / 无时间」栏（默认展开）→「已过期」区（默认折叠）→ 底部「共 N 组」；
 * 「长期通知」筛选 = 长期安排清单；「最新发布」= 按发布时间倒序（过期卡样式仍按过期显示）。
 */
@Composable
internal fun NoticeList(
    content: NoticesViewContent,
    columns: Int,
    state: LazyGridState,
    materialExpanded: Boolean,
    onToggleMaterial: () -> Unit,
    expiredExpanded: Boolean,
    onToggleExpired: () -> Unit,
    onOpen: (NoticeUiItem) -> Unit,
    onToggleDone: (NoticeUiItem) -> Unit,
    onTogglePin: (NoticeUiItem) -> Unit,
    onAddTask: ((NoticeUiItem) -> Unit)? = null,
    reduceMotion: Boolean,
    appearedKeys: SnapshotStateMap<String, Boolean>,
    selectedKey: String?,
    previewEntranceProgress: Float?,
    previewPressedKey: String?,
    previewExpandedKey: String?,
    hasMore: Boolean = false,
    remainingCount: Int = 0,
    loadingMore: Boolean = false,
    loadMoreError: String? = null,
    onLoadMore: () -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    banner: (@Composable () -> Unit)? = null,
    empty: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun LazyGridScope.cards(
        rows: List<NoticeUiItem>,
        prefix: String,
        indexOffset: Int = 0,
        modeOf: (NoticeUiItem) -> NoticeCardMode,
    ) {
        itemsIndexed(
            items = rows,
            key = { _, item -> "$prefix-${item.key}" },
            contentType = { _, _ -> "notice" },
        ) { index, item ->
            NoticeCardEntry(
                item = item,
                mode = modeOf(item),
                index = indexOffset + index,
                itemKey = "$prefix-${item.key}",
                reduceMotion = reduceMotion,
                appearedKeys = appearedKeys,
                previewEntranceProgress = previewEntranceProgress,
                previewPressedKey = previewPressedKey,
                previewExpandedKey = previewExpandedKey,
                selectedKey = selectedKey,
                onOpen = onOpen,
                onToggleDone = onToggleDone,
                onTogglePin = onTogglePin,
                onAddTask = onAddTask,
                modifier = this.noticeAnimateItem(reduceMotion),
            )
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        modifier = modifier,
        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (banner != null) {
            item(key = "offline-banner", span = { GridItemSpan(maxLineSpan) }) {
                Box(modifier = Modifier.fillMaxWidth()) { banner() }
            }
        }
        if (header != null) {
            item(key = "filter-header", span = { GridItemSpan(maxLineSpan) }) {
                Box(modifier = Modifier.fillMaxWidth()) { header() }
            }
        }
        if (content.isEmpty) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") { empty() }
        } else if (content.longtermOnly || content.latestOnly) {
            val list = if (content.longtermOnly) content.longterm else content.latest
            cards(list, "x") { item ->
                if (content.longtermOnly) NoticeCardMode.LONGTERM_DEF else noticeLatestModeOf(item)
            }
        } else {
            cards(content.primary, "c", modeOf = ::noticeCardModeOf)
            if (content.showMaterial && content.material.isNotEmpty()) {
                item(key = "material-header", span = { GridItemSpan(maxLineSpan) }) {
                    NoticeGroupHeader(
                        title = "资料 / 无时间",
                        subtitle = "${content.material.size} 条",
                        expanded = materialExpanded,
                        onToggle = onToggleMaterial,
                        reduceMotion = reduceMotion,
                        modifier = this.noticeAnimateItem(reduceMotion),
                    )
                }
                if (materialExpanded) {
                    cards(content.material, "m", indexOffset = content.primary.size + 1) {
                        NoticeCardMode.NORMAL
                    }
                }
            }
            if (content.showExpiredSection && content.expired.isNotEmpty()) {
                item(key = "expired-header", span = { GridItemSpan(maxLineSpan) }) {
                    NoticeGroupHeader(
                        title = "已过期",
                        subtitle = "${content.expired.size} 组",
                        expanded = expiredExpanded,
                        onToggle = onToggleExpired,
                        reduceMotion = reduceMotion,
                        modifier = this.noticeAnimateItem(reduceMotion),
                    )
                }
                if (expiredExpanded) {
                    cards(content.expired, "e", indexOffset = content.primary.size + 1) {
                        NoticeCardMode.EXPIRED
                    }
                }
            }
        }
        if (content.totalCount > 0) {
            item(key = "total-count", span = { GridItemSpan(maxLineSpan) }) {
                NoticeTotalCountRow(count = content.totalCount)
            }
        }
        if (hasMore || loadMoreError != null) {
            item(key = "load-more", span = { GridItemSpan(maxLineSpan) }) {
                NoticeLoadMoreRow(
                    remainingCount = remainingCount,
                    loading = loadingMore,
                    error = loadMoreError,
                    onLoadMore = onLoadMore,
                )
            }
        }
    }
}

/** 最新发布视图的卡片模式：过期卡仍按过期显示；长期安排定义卡不显示「本次已处理」。 */
internal fun noticeLatestModeOf(item: NoticeUiItem): NoticeCardMode = when {
    item.expired -> NoticeCardMode.EXPIRED
    item.recurrence != null -> NoticeCardMode.LONGTERM_DEF
    else -> NoticeCardMode.NORMAL
}

/** 列表底部统计「共 N 组」（§2.2：主列表条目数；期次计 1 条；长期清单视图计长期条数）。 */
@Composable
private fun NoticeTotalCountRow(count: Int) {
    Text(
        text = "共 $count 组",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    )
}

/**
 * 底部「加载更早的通知」行（**按需加载**，2026-11-19 取代原「后台自动加载全部」）。
 *
 * - 常态（还有更早数据）：`加载更早的通知（还有 N 条）`，点按加载一页；不点就不请求；
 * - 加载中：`正在加载…（还有 N 条）`，整行不可点；
 * - 失败：错误文案 +「点此重试」，点按重新加载这一页；
 * - light meta（一次拿全量）链路下 `hasMore=false`，这一行不会出现。
 *
 * 触控 ≥52dp。
 */
@Composable
private fun NoticeLoadMoreRow(
    remainingCount: Int,
    loading: Boolean,
    error: String?,
    onLoadMore: () -> Unit,
) {
    val remainingText = if (remainingCount > 0) "（还有 $remainingCount 条）" else ""
    val idleText = "加载更早的通知$remainingText"
    val loadingText = "正在加载…$remainingText"
    Surface(
        onClick = { if (!loading) onLoadMore() },
        enabled = !loading,
        shape = RoundedCornerShape(CtRadius.cardCompact),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            when {
                loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = loadingText,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                error != null -> Text(
                    text = "$error（点此重试）",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )

                else -> Text(
                    text = idleText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 分区头（「资料 / 无时间」「已过期」）：整行可点折叠/展开，箭头旋转动画（v6 CtToolSectionHeader）。 */
@Composable
private fun NoticeGroupHeader(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reduceMotion) snap() else NoticeMotionTokens.pressSpring,
        label = "noticeSectionArrow",
    )
    CtToolSectionHeader(
        title = title,
        subtitle = subtitle,
        modifier = modifier
            .clip(RoundedCornerShape(CtRadius.cardCompact))
            .clickable(onClick = onToggle)
            .heightIn(min = 52.dp),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (expanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = arrowRotation },
                )
            }
        },
    )
}

// -----------------------------------------------------------------------------
// 卡片
// -----------------------------------------------------------------------------

/**
 * 通知卡片（契约 §2.2/§2.3）：[mode] 决定状态胶囊与操作区。
 *
 * - 普通：标记已处理 / 置顶；已过期普通通知保留「标记已处理」（已过期区保持现状）；
 * - 期次：状态胶囊 `本次 M月D日（周X）HH:mm` + 操作「本次已处理」（写 marks ref = 期次 key）；
 * - 长期定义（长期清单）：状态「进行中」+ 周期 / 下次，**无已处理**；
 * - 长期安排已结束：状态「长期安排已结束」，无已处理。
 */
@Composable
private fun NoticeCard(
    item: NoticeUiItem,
    onOpen: () -> Unit,
    onToggleDone: () -> Unit,
    onTogglePin: () -> Unit,
    onAddTask: (() -> Unit)? = null,
    mode: NoticeCardMode = NoticeCardMode.NORMAL,
    reduceMotion: Boolean = false,
    expanded: Boolean = false,
    previewPressed: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    // 长期清单视图：状态胶囊固定「进行中」（即使该安排当前处于期次窗口，见 §2.2）
    val displayStatus = if (mode == NoticeCardMode.LONGTERM_DEF) {
        NoticeStatus("recurring", "进行中", "recurring", false, NoticeBucket.RECURRING)
    } else {
        item.status
    }
    NoticePressableCard(
        onClick = onOpen,
        reduceMotion = reduceMotion,
        contentDescription = item.title.ifBlank { "通知" },
        expanded = expanded,
        previewPressed = previewPressed,
        pinned = item.pinned,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CtSpace.xs),
                verticalArrangement = Arrangement.spacedBy(CtSpace.xs),
            ) {
                NoticeCatPill(cat = item.cat, tone = item.tone)
                displayStatus?.let { NoticeStatusLabel(it) }
                if (item.recurring) ItemFlag(text = "长期安排", tone = NoticeTone.BLUE)
                if (item.edited) ItemFlag(text = "已更新", tone = NoticeTone.BLUE)
                if (item.hidden) ItemFlag(text = "已隐藏", tone = NoticeTone.GREY)
            }

            Text(
                text = item.title.ifBlank { "通知" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            if (item.summary.isNotBlank()) {
                Text(
                    text = item.summary,
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            NoticeCardMeta(item = item, mode = mode)
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.65f))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CtSpace.xs),
                verticalArrangement = Arrangement.spacedBy(CtSpace.xs),
            ) {
                when {
                    // 期次卡：本期单独已处理（marks ref = 期次 key，§2.3）
                    item.occurrenceKey != null -> DonePill(
                        done = item.done,
                        reduceMotion = reduceMotion,
                        onClick = onToggleDone,
                        doneText = "本期已处理",
                        undoneText = "本次已处理",
                    )

                    // 普通通知（含已过期普通通知）：沿用既有「标记已处理」
                    item.isAuto && !item.expired && item.recurrence == null -> DonePill(
                        done = item.done,
                        reduceMotion = reduceMotion,
                        onClick = onToggleDone,
                    )

                    // 长期安排定义卡 / 长期安排已结束：无已处理按钮（§2.3）
                    item.isAuto -> Unit

                    else -> PinPill(pinned = item.pinned, reduceMotion = reduceMotion, onClick = onTogglePin)
                }
                NoticeRawMessagesButton(item)
                if (onAddTask != null) TextButton(onClick = onAddTask) { Text("加入任务") }

            }
        }
    }
}

/** 来源、发送者、发布时间与附件状态随可用宽度换行。 */
@Composable
private fun NoticeCardMeta(item: NoticeUiItem, mode: NoticeCardMode = NoticeCardMode.NORMAL) {
    val scheme = MaterialTheme.colorScheme
    val sourceColors = toneColors(
        when (item.sourceKind) {
            NoticeSourceKind.WECHAT -> NoticeTone.TEAL
            NoticeSourceKind.QQ -> NoticeTone.BLUE
            NoticeSourceKind.DOC, NoticeSourceKind.UNKNOWN -> NoticeTone.GREY
        },
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (item.source.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(sourceColors.dot, CircleShape),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = item.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        if (item.senderLine.isNotBlank()) {
            Text(
                text = item.senderLine,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
        if (item.postedText.isNotBlank()) {
            Text(
                text = "发布于 ${item.postedText}",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        // 长期安排：周期 label（期次卡 / 长期定义卡都显示）
        if (item.recurLabel.isNotBlank()) {
            ItemFlag(text = item.recurLabel, tone = NoticeTone.BLUE)
        }
        // 「下次 …」只在长期定义卡显示（期次卡状态胶囊已显示「本次 …」，避免重复）
        if (mode == NoticeCardMode.LONGTERM_DEF && item.recurNextText.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = scheme.secondary,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = item.recurNextText,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurface,
                )
            }
        }
        if (item.attachmentCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.AttachFile,
                    contentDescription = "附件 ${item.attachmentCount} 个",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = "附件 ${item.attachmentCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        if (item.mergeCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Hub,
                    contentDescription = "多源合并 ${item.mergeCount} 个来源",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = "多源",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ItemFlag(text: String, tone: NoticeTone) {
    val colors = toneColors(tone)
    Surface(shape = CtShapeTokens.chip, color = colors.container, contentColor = colors.content) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = CtSpace.sm, vertical = CtSpace.xxs),
        )
    }
}

/** 分类色块（类型色点/标签；小程序 nt-cat 同口径）。 */
@Composable
internal fun NoticeCatPill(
    cat: String,
    tone: NoticeTone,
    modifier: Modifier = Modifier,
) {
    val colors = toneColors(tone)
    Surface(
        modifier = modifier,
        shape = CtShapeTokens.chip,
        color = colors.container,
        contentColor = colors.content,
    ) {
        Text(
            text = cat.ifBlank { "通知" },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@Composable
internal fun NoticeStatusLabel(status: NoticeStatus) {
    ItemFlag(text = status.label, tone = statusTone(status))
}

/** 「标记已处理」胶囊（≥48dp；已处理=次要容器底）。期次卡文案为「本次已处理 / 本期已处理」。 */
@Composable
private fun DonePill(
    done: Boolean,
    reduceMotion: Boolean,
    onClick: () -> Unit,
    doneText: String = "已处理",
    undoneText: String = "标记已处理",
) {
    val scheme = MaterialTheme.colorScheme
    val pillContainer by animateColorAsState(
        targetValue = if (done) scheme.primaryContainer else scheme.surfaceContainerHigh,
        animationSpec = if (reduceMotion) snap() else NoticeMotionTokens.colorSpec,
        label = "donePillContainer",
    )
    val pillContent by animateColorAsState(
        targetValue = if (done) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        animationSpec = if (reduceMotion) snap() else NoticeMotionTokens.colorSpec,
        label = "donePillContent",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (done) 1f else 0.9f,
        animationSpec = if (reduceMotion) snap() else NoticeMotionTokens.pressSpring,
        label = "donePillIcon",
    )
    Surface(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics {
                role = Role.Checkbox
                stateDescription = if (done) "已处理" else "未处理"
            },
        shape = CtShapeTokens.pill,
        color = pillContainer,
        contentColor = pillContent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier
                    .size(17.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (done) doneText else undoneText,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** 「置顶」胶囊（≥48dp；仅人工通知可置顶）。 */
@Composable
private fun PinPill(
    pinned: Boolean,
    reduceMotion: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val pillContainer by animateColorAsState(
        targetValue = if (pinned) scheme.primaryContainer else scheme.surfaceContainerHigh,
        animationSpec = if (reduceMotion) snap() else NoticeMotionTokens.colorSpec,
        label = "pinPillContainer",
    )
    val pillContent by animateColorAsState(
        targetValue = if (pinned) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        animationSpec = if (reduceMotion) snap() else NoticeMotionTokens.colorSpec,
        label = "pinPillContent",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (pinned) 1f else 0.9f,
        animationSpec = if (reduceMotion) snap() else NoticeMotionTokens.pressSpring,
        label = "pinPillIcon",
    )
    Surface(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics {
                role = Role.Button
                stateDescription = if (pinned) "已置顶" else "未置顶"
            },
        shape = CtShapeTokens.pill,
        color = pillContainer,
        contentColor = pillContent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.PushPin,
                contentDescription = null,
                modifier = Modifier
                    .size(17.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (pinned) "已置顶" else "置顶",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

// -----------------------------------------------------------------------------
// 空 / 错误 / 加载态
// -----------------------------------------------------------------------------
