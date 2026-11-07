package com.furina.classtools.ui.screens.notices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.marks.MarkKind
import com.furina.classtools.data.marks.MarksRepository
import com.furina.classtools.data.marks.MarksSnapshot
import com.furina.classtools.data.notices.ManualNotice
import com.furina.classtools.data.notices.MergedMessage
import com.furina.classtools.data.notices.NoticeEntry
import com.furina.classtools.data.notices.NoticeItemMeta
import com.furina.classtools.data.notices.NoticeManualAttachment
import com.furina.classtools.data.notices.NoticeStatus
import com.furina.classtools.data.notices.NoticeStatusMachine
import com.furina.classtools.data.notices.NoticesData
import com.furina.classtools.data.notices.NoticesLoadResult
import com.furina.classtools.data.notices.NoticesRepository
import com.furina.classtools.data.session.Session
import com.furina.classtools.ui.components.CtLoading
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.CtTopBar
import com.furina.classtools.ui.components.EmptyState
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.expressive.CtChipOption
import com.furina.classtools.ui.components.expressive.CtFilterChipRow
import com.furina.classtools.ui.components.expressive.CtPressableCard
import com.furina.classtools.ui.theme.CtShapeTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** 通知列表筛选（task 要求：全部/未处理/置顶）。 */
enum class NoticeFilter(val label: String) {
    ALL("全部"),
    PENDING("未处理"),
    PINNED("置顶"),
}

/** UI 列表项（自动通知 + 人工通知统一渲染）。 */
private sealed interface NoticeUiItem {
    val key: String
    val ts: Long
    val pinned: Boolean
    val done: Boolean
    val title: String
    val summary: String
    val timeText: String
    val attachmentCount: Int

    data class Auto(
        val entry: NoticeEntry,
        val status: NoticeStatus,
        override val done: Boolean,
        override val summary: String,
        override val attachmentCount: Int,
    ) : NoticeUiItem {
        override val key: String get() = entry.rowId
        override val ts: Long get() = entry.ts
        override val pinned: Boolean get() = false
        override val title: String get() = entry.title
        override val timeText: String get() = entry.group.timeLabel.ifBlank { status.label }
    }

    data class Manual(
        val notice: ManualNotice,
        override val pinned: Boolean,
    ) : NoticeUiItem {
        override val key: String get() = notice.id
        override val ts: Long get() = notice.ts
        override val done: Boolean get() = false
        override val title: String get() = notice.row.title.ifBlank { "班级通知" }
        override val summary: String get() = notice.row.body.take(160)
        override val timeText: String get() = formatTs(notice.ts)
        override val attachmentCount: Int get() = notice.attachments.size
    }
}

/**
 * 通知页（原生只读渲染）。
 *
 * 数据：`NoticesRepository.load()` 两级拉取（轻量签名 → 变化才拉整表），
 * `[[CLASS_NOTICE_V1]]` 解析、hidden 过滤、override 白名单、附件元信息。
 * 已处理 = `marks kind='ntdone'`（ref = 通知组 id）；置顶 = `marks kind='nt'`（仅人工通知有效）。
 *
 * 接线（Lead 负责 MainNavHost）：
 * ```kotlin
 * "nt" -> NoticesScreen(
 *     onBack = { navController.popBackStack() },
 *     session = session,
 *     noticesRepository = container.noticesRepository,
 *     marksRepository = container.marksRepository,
 * )
 * ```
 *
 * 详情为**原生渲染**：文本条目展示全文；**img 附件支持缩略图预览 + 点击看大图**
 * （按需拉取原始 data URI、Bitmap 采样解码，列表阶段仍只存元信息）；文件/链接/收集表/合并消息
 * 只展示元信息，未知 kind 显示「暂不支持预览」；不加载 WebView。
 *
 * @param rawAttachmentLoader 图片附件按需加载器（Lead 接线传
 *   `ApiNoticeRawAttachmentLoader(container.apiClient)`；为 null 时降级为元信息占位）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticesScreen(
    onBack: () -> Unit,
    session: Session?,
    marksRepository: MarksRepository?,
    noticesRepository: NoticesRepository?,
    rawAttachmentLoader: NoticeRawAttachmentLoader? = null,
    /** v2：把有明确时间的结构化通知写入系统日历（null = 不显示入口）。 */
    onAddToCalendar: ((com.furina.classtools.data.notices.NoticeGroupDto) -> Unit)? = null,
    modifier: Modifier = Modifier,
    initialData: NoticesData? = null,
    initialMarks: MarksSnapshot? = null,
    forcedErrorMessage: String? = null,
    skipLoad: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val fallbackMarks = remember { MutableStateFlow(initialMarks ?: MarksSnapshot()) }
    val marksFlow = remember(marksRepository, initialMarks) { marksRepository?.marks ?: fallbackMarks }
    val marks: MarksSnapshot by marksFlow.collectAsState()

    var data by remember { mutableStateOf(initialData) }
    var phase by remember {
        mutableStateOf(
            when {
                skipLoad || forcedErrorMessage != null -> Phase.READY
                initialData != null -> Phase.READY
                else -> Phase.LOADING
            },
        )
    }
    var errorMessage by remember { mutableStateOf(forcedErrorMessage) }
    var isRefreshing by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf(NoticeFilter.ALL) }
    var selected by remember { mutableStateOf<NoticeUiItem?>(null) }

    fun notify(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    suspend fun applyResult(result: NoticesLoadResult) {
        when (result) {
            is NoticesLoadResult.Success -> {
                data = result.data
                errorMessage = null
                phase = Phase.READY
            }
            is NoticesLoadResult.Failure -> {
                errorMessage = result.message
                phase = if (data == null) Phase.FAILED else Phase.READY
            }
        }
    }

    LaunchedEffect(reloadToken) {
        if (skipLoad || forcedErrorMessage != null) {
            phase = Phase.READY
            return@LaunchedEffect
        }
        if (session == null) {
            errorMessage = "请先完成身份验证后再查看通知"
            phase = Phase.FAILED
            return@LaunchedEffect
        }
        val repository = noticesRepository
        if (repository == null) {
            phase = Phase.READY
            return@LaunchedEffect
        }
        if (data == null) phase = Phase.LOADING
        applyResult(repository.load(force = reloadToken > 0))
        runCatching { marksRepository?.refresh() }
    }

    fun refresh() {
        scope.launch {
            isRefreshing = true
            try {
                noticesRepository?.let { applyResult(it.load(force = true)) }
                runCatching { marksRepository?.refresh(force = true) }
            } finally {
                isRefreshing = false
            }
        }
    }

    fun toggleDone(item: NoticeUiItem.Auto) {
        val repository = marksRepository ?: run {
            notify("未登录，无法同步处理状态")
            return
        }
        scope.launch {
            when (repository.setMark(MarkKind.NT_DONE, item.entry.id, !item.done)) {
                is com.furina.classtools.data.marks.MarksUpdateResult.Success ->
                    notify(if (!item.done) "已标记处理" else "已恢复未处理")
                is com.furina.classtools.data.marks.MarksUpdateResult.Failure ->
                    notify("同步失败，稍后自动重试")
            }
        }
    }

    fun togglePin(item: NoticeUiItem.Manual) {
        val repository = marksRepository ?: run {
            notify("未登录，无法同步置顶状态")
            return
        }
        scope.launch {
            when (repository.setMark(MarkKind.NT, item.notice.id, !item.pinned)) {
                is com.furina.classtools.data.marks.MarksUpdateResult.Success ->
                    notify(if (!item.pinned) "已置顶" else "已取消置顶")
                is com.furina.classtools.data.marks.MarksUpdateResult.Failure ->
                    notify("同步失败，稍后自动重试")
            }
        }
    }

    val nowMillis = remember(reloadToken) { System.currentTimeMillis() }
    val allItems = remember(data, marks, nowMillis) {
        buildUiItems(data, marks, nowMillis)
    }
    val visibleItems = remember(allItems, filter) {
        when (filter) {
            NoticeFilter.ALL -> allItems
            NoticeFilter.PENDING -> allItems.filterIsInstance<NoticeUiItem.Auto>().filter { !it.done }
            NoticeFilter.PINNED -> allItems.filter { it.pinned }
        }
    }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                CtTopBar(
                    title = "班级通知",
                    subtitle = buildString {
                        val pending = allItems.filterIsInstance<NoticeUiItem.Auto>().count { !it.done }
                        if (allItems.isNotEmpty()) append("未处理 $pending / ${allItems.size}")
                        if (data?.fromCache == true) {
                            if (isNotEmpty()) append(" · ")
                            append("本机缓存")
                        }
                        val hidden = data?.hiddenCount ?: 0
                        if (hidden > 0) append(" · 已隐藏 $hidden")
                    },
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { refresh() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "刷新通知")
                        }
                    },
                )

                val pendingCount = allItems.filterIsInstance<NoticeUiItem.Auto>().count { !it.done }
                val pinnedCount = allItems.count { it.pinned }
                CtFilterChipRow(
                    options = listOf(
                        CtChipOption(NoticeFilter.ALL.name, "全部 ${allItems.size}"),
                        CtChipOption(NoticeFilter.PENDING.name, "未处理 $pendingCount"),
                        CtChipOption(NoticeFilter.PINNED.name, "置顶 $pinnedCount"),
                    ),
                    selectedKey = filter.name,
                    onSelect = { key ->
                        NoticeFilter.entries.firstOrNull { it.name == key }?.let { filter = it }
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.padding(top = 2.dp),
                )

                Box(modifier = Modifier.weight(1f)) {
                    when {
                        phase == Phase.LOADING -> CtLoading(
                            modifier = Modifier.align(Alignment.Center),
                            text = "加载通知…",
                        )

                        phase == Phase.FAILED -> ErrorContent(
                            message = errorMessage ?: "加载失败",
                            onRetry = { reloadToken += 1 },
                            modifier = Modifier.fillMaxSize(),
                        )

                        else -> PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = { refresh() },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            if (visibleItems.isEmpty()) {
                                EmptyState(
                                    icon = Icons.Rounded.CheckCircle,
                                    title = when (filter) {
                                        NoticeFilter.PENDING -> "没有未处理通知"
                                        NoticeFilter.PINNED -> "没有置顶通知"
                                        NoticeFilter.ALL -> "暂无通知"
                                    },
                                    message = if (allItems.isEmpty()) "班级通知会显示在这里" else "切换筛选看看",
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 6.dp,
                                        bottom = 24.dp,
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(visibleItems, key = { it.key }) { item ->
                                        NoticeCard(
                                            item = item,
                                            onOpen = { selected = item },
                                            onToggleDone = { (item as? NoticeUiItem.Auto)?.let { toggleDone(it) } },
                                            onTogglePin = { (item as? NoticeUiItem.Manual)?.let { togglePin(it) } },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (phase == Phase.READY && errorMessage != null) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = CtShapeTokens.listItem,
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "刷新失败：$errorMessage（显示本机缓存）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            )
        }
    }

    selected?.let { item ->
        NoticeDetailDialog(
            item = item,
            rawAttachmentLoader = rawAttachmentLoader,
            onAddToCalendar = onAddToCalendar,
            onDismiss = { selected = null },
            onToggleDone = {
                (item as? NoticeUiItem.Auto)?.let {
                    toggleDone(it)
                    selected = null
                }
            },
            onTogglePin = {
                (item as? NoticeUiItem.Manual)?.let {
                    togglePin(it)
                    selected = null
                }
            },
        )
    }
}

// -----------------------------------------------------------------------------
// 数据 → UI 列表
// -----------------------------------------------------------------------------

private fun buildUiItems(
    data: NoticesData?,
    marks: MarksSnapshot,
    nowMillis: Long,
): List<NoticeUiItem> {
    if (data == null) return emptyList()
    val autoItems = data.auto.map { entry ->
        val status = NoticeStatusMachine.statusOf(entry.group, nowMillis)
        val done = marks.isDone(MarkKind.NT_DONE, entry.id)
        val summary = entry.group.aiSummary.ifBlank {
            entry.group.summary.ifBlank {
                com.furina.classtools.data.notices.NoticeBodyParser.firstTextOf(entry.items)
            }
        }
        NoticeUiItem.Auto(
            entry = entry,
            status = status,
            done = done,
            summary = summary,
            attachmentCount = com.furina.classtools.data.notices.NoticeBodyParser.attachmentCount(entry.items),
        )
    }.sortedWith(
        compareBy<NoticeUiItem.Auto> { it.done }
            .thenByDescending { it.ts },
    )
    val manualItems = data.manual.map { notice ->
        NoticeUiItem.Manual(
            notice = notice,
            pinned = marks.isDone(MarkKind.NT, notice.id),
        )
    }.sortedWith(
        compareBy<NoticeUiItem.Manual> { if (it.pinned) 0 else 1 }
            .thenByDescending { it.ts },
    )
    return manualItems + autoItems
}

private fun formatTs(ts: Long): String {
    if (ts <= 0) return ""
    return java.time.format.DateTimeFormatter
        .ofPattern("MM-dd HH:mm", java.util.Locale.CHINA)
        .format(java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()))
}

// -----------------------------------------------------------------------------
// 卡片 / 详情
// -----------------------------------------------------------------------------

@Composable
private fun NoticeCard(
    item: NoticeUiItem,
    onOpen: () -> Unit,
    onToggleDone: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val container = when {
        item.done -> MaterialTheme.colorScheme.surfaceContainerLowest
        item.pinned -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    CtPressableCard(
        onClick = onOpen,
        containerColor = container,
        contentDescription = item.title,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            if (item.pinned) {
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp, top = 14.dp)
                        .width(3.dp)
                        .height(48.dp)
                        .clip(CtShapeTokens.pill)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val status = (item as? NoticeUiItem.Auto)?.status
                    CtPill(
                        text = if (item is NoticeUiItem.Auto) item.entry.cat else "手动通知",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (status != null) {
                        Spacer(Modifier.width(6.dp))
                        StatusPill(status = status)
                    }
                    if (item.pinned) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "置顶",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (item is NoticeUiItem.Auto) {
                        Checkbox(
                            checked = item.done,
                            onCheckedChange = { onToggleDone() },
                        )
                    } else {
                        IconButton(onClick = onTogglePin) {
                            Icon(
                                Icons.Rounded.PushPin,
                                contentDescription = if (item.pinned) "取消置顶" else "置顶",
                                tint = if (item.pinned) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.summary.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = item.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.attachmentCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Rounded.AttachFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "${item.attachmentCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (item is NoticeUiItem.Auto && item.entry.group.mergedSources.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Rounded.Hub,
                            contentDescription = "多源合并",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    if (item is NoticeUiItem.Auto && item.entry.edited) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "已更新",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: NoticeStatus) {
    val (container, content) = when (status.cls) {
        "urgent", "expired" ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer

        "alert" ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer

        "recurring" ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer

        "none" ->
            MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant

        else ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(shape = CtShapeTokens.pill, color = container, contentColor = content) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyState(icon = Icons.Rounded.ErrorOutline, title = "通知加载失败", message = message)
        TextButton(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun NoticeDetailDialog(
    item: NoticeUiItem,
    rawAttachmentLoader: NoticeRawAttachmentLoader?,
    onDismiss: () -> Unit,
    onToggleDone: () -> Unit,
    onTogglePin: () -> Unit,
    onAddToCalendar: ((com.furina.classtools.data.notices.NoticeGroupDto) -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = item.title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (item) {
                    is NoticeUiItem.Auto -> AutoDetailContent(item, rawAttachmentLoader)
                    is NoticeUiItem.Manual -> ManualDetailContent(item, rawAttachmentLoader)
                }
            }
        },
        confirmButton = {
            when (item) {
                is NoticeUiItem.Auto -> Row {
                    if (onAddToCalendar != null) {
                        TextButton(onClick = { onAddToCalendar(item.entry.group) }) {
                            Text("加入日程")
                        }
                    }
                    TextButton(onClick = onToggleDone) {
                        Text(if (item.done) "标记未处理" else "标记已处理")
                    }
                }

                is NoticeUiItem.Manual -> TextButton(onClick = onTogglePin) {
                    Text(if (item.pinned) "取消置顶" else "置顶")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun AutoDetailContent(item: NoticeUiItem.Auto, rawAttachmentLoader: NoticeRawAttachmentLoader?) {
    val group = item.entry.group
    Row(verticalAlignment = Alignment.CenterVertically) {
        CtPill(text = item.entry.cat)
        Spacer(Modifier.width(8.dp))
        StatusPill(status = item.status)
        if (item.done) {
            Spacer(Modifier.width(8.dp))
            CtPill(
                text = "已处理",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
    if (group.source.isNotBlank()) {
        Text(
            text = group.source,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (group.aiSummary.isNotBlank()) {
        Text(text = group.aiSummary, style = MaterialTheme.typography.bodyMedium)
    }
    if (group.aiPoints.isNotEmpty()) {
        group.aiPoints.forEach { point ->
            Row {
                Text("• ", style = MaterialTheme.typography.bodyMedium)
                Text(text = point, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    val segments = group.aiSegments.filter { it.text.isNotBlank() }.take(6)
    if (segments.isNotEmpty()) {
        HorizontalDivider()
        segments.forEach { segment ->
            Row {
                Text(
                    text = "${segment.type} ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = segment.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    if (item.entry.items.isNotEmpty()) {
        HorizontalDivider()
        var imageOrdinal = 0
        item.entry.items.forEachIndexed { index, meta ->
            val currentImageOrdinal = if (meta is NoticeItemMeta.Image) imageOrdinal++ else -1
            NoticeItemMetaRow(
                index = index,
                meta = meta,
                rowId = item.entry.rowId,
                imageOrdinal = currentImageOrdinal,
                rawAttachmentLoader = rawAttachmentLoader,
            )
        }
    }
    Text(
        text = "图片按需加载（仅内联 data URI 可预览）· 其它附件暂不支持预览",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ManualDetailContent(item: NoticeUiItem.Manual, rawAttachmentLoader: NoticeRawAttachmentLoader?) {
    val notice = item.notice
    Text(
        text = notice.row.body.ifBlank { "（无正文）" },
        style = MaterialTheme.typography.bodyMedium,
    )
    if (notice.attachments.isNotEmpty()) {
        HorizontalDivider()
        notice.attachments.forEach { attachment ->
            ManualAttachmentRow(
                attachment = attachment,
                rowId = notice.row.id,
                rawAttachmentLoader = rawAttachmentLoader,
            )
        }
    }
    Text(
        text = formatTs(notice.ts),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NoticeItemMetaRow(
    index: Int,
    meta: NoticeItemMeta,
    rowId: String,
    imageOrdinal: Int,
    rawAttachmentLoader: NoticeRawAttachmentLoader?,
) {
    when (meta) {
        is NoticeItemMeta.Text -> {
            Row {
                Text(
                    text = meta.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = meta.text.ifBlank { "（空）" },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            if (meta.fromName.isNotBlank() || meta.timeText.isNotBlank()) {
                Text(
                    text = listOf(meta.fromName, meta.fromRole, meta.timeText)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is NoticeItemMeta.Image -> {
            if (meta.hasData && rawAttachmentLoader != null && imageOrdinal >= 0) {
                NoticeAttachmentThumb(
                    loader = rawAttachmentLoader,
                    rowId = rowId,
                    ordinal = imageOrdinal,
                    manual = false,
                    title = "图片",
                    caption = meta.caption,
                )
            } else {
                MetaRow(
                    title = "图片",
                    detail = buildString {
                        if (meta.caption.isNotBlank()) append(meta.caption)
                        if (meta.ocr) append(" · OCR")
                        append(" · 无法预览 · 暂不支持预览")
                    },
                )
            }
        }

        is NoticeItemMeta.File -> MetaRow(
            title = meta.name,
            detail = listOf(
                if (meta.ext.isNotBlank()) meta.ext.uppercase() else "",
                meta.sizeText,
                if (meta.pending) "待获取" else if (meta.archiveOk) "压缩包 ${meta.archiveCount} 项" else "",
                if (meta.archiveSummary.isNotBlank()) meta.archiveSummary.take(80) else "",
                "暂不支持预览",
            ).filter { it.isNotBlank() }.joinToString(" · "),
        )

        is NoticeItemMeta.Form -> MetaRow(
            title = meta.name,
            detail = listOf(meta.statusText, meta.note, "请在网页/微信中打开")
                .filter { it.isNotBlank() }.joinToString(" · "),
        )

        is NoticeItemMeta.Link -> MetaRow(
            title = meta.name,
            detail = listOf(meta.host, meta.account, "无法在 App 内打开")
                .filter { it.isNotBlank() }.joinToString(" · "),
        )

        is NoticeItemMeta.Merged -> {
            Column {
                Text(
                    text = "${meta.name} · ${meta.count} 条合并消息",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                meta.messages.forEach { message: MergedMessage ->
                    Text(
                        text = listOf(message.fromName, message.timeText)
                            .filter { it.isNotBlank() }.joinToString(" · ") +
                            "：" + message.text.take(120),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        is NoticeItemMeta.Unknown -> MetaRow(
            title = "未知类型：${meta.kind}",
            detail = "暂不支持预览（字段：${meta.keys.joinToString("/")}）",
        )
    }
}

@Composable
private fun MetaRow(title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.AttachFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
) {
    val isImage = attachment.mime.startsWith("image/", ignoreCase = true)
    if (isImage && attachment.hasData && rawAttachmentLoader != null) {
        NoticeAttachmentThumb(
            loader = rawAttachmentLoader,
            rowId = rowId,
            ordinal = attachment.index,
            manual = true,
            title = attachment.name,
        )
    } else {
        MetaRow(
            title = attachment.name,
            detail = listOf(
                attachment.mime,
                attachment.sizeText,
                if (isImage) "无法预览 · 暂不支持预览" else "暂不支持预览",
            ).filter { it.isNotBlank() }.joinToString(" · "),
        )
    }
}

/** 页面内部阶段（四态中的加载/错误两态由它承载）。 */
private enum class Phase { LOADING, READY, FAILED }


