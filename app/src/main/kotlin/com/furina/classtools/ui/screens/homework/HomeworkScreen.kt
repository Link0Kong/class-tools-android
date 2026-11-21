package com.furina.classtools.ui.screens.homework

import com.furina.classtools.ui.components.CtSnackbarHost

import com.furina.classtools.ui.components.light.ctLightMaterial
import com.furina.classtools.ui.theme.CtLightScene
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import com.furina.classtools.ui.components.light.CtLightAlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.homework.HomeworkAttachmentMeta
import com.furina.classtools.data.homework.HomeworkDueCalculator
import com.furina.classtools.data.homework.HomeworkDueTone
import com.furina.classtools.data.homework.HomeworkFilter
import com.furina.classtools.data.homework.HomeworkItem
import com.furina.classtools.data.homework.HomeworkLoadResult
import com.furina.classtools.data.homework.HomeworkRepository
import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.marks.MarkKind
import com.furina.classtools.data.marks.MarksRepository
import com.furina.classtools.data.marks.MarksSnapshot
import com.furina.classtools.data.session.Session
import com.furina.classtools.ui.components.CtLoading
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.CtLightTopBar
import com.furina.classtools.ui.components.EmptyState
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.tool.CtToolCard
import com.furina.classtools.ui.components.tool.CtToolChip
import com.furina.classtools.ui.components.tool.CtToolErrorState
import com.furina.classtools.ui.components.tool.CtToolPrimaryButton
import com.furina.classtools.ui.components.tool.CtToolSectionHeader
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtShape
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.CtSpace
import com.furina.classtools.ui.theme.rememberReduceMotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 作业页（原生、只读列表 + marks 勾选）· 简洁化 v1。
 *
 * 数据：`HomeworkRepository.load()`（`GET homework?select=*&order=ts.asc&archived=eq.false`，TTL 10min）
 * 完成状态：`MarksRepository`（`marks kind='hw'`，跨端唯一真源；乐观更新由 marks 仓库的 StateFlow 提供）。
 *
 * 简洁化 + 左右滑动：
 * - 顶栏统一 [CtTopBar]（标题「作业」+ 未完成/已完成计数 + 缓存标记），页内无重复标题；
 * - 筛选「全部 / 未完成 / 已完成」用 [HorizontalPager] 左右滑动切换：跟手翻页、点击 chip 同步 pager；
 *   指示条跟随 pager 偏移（跟手），`reduceMotion` 时点击/chip 直接切页无动画；
 * - 列表行统一 [CtToolCard]（紧凑 padding），分组标题 [CtToolSectionHeader]；空/错误/加载一行提示；
 * - 截止/完成状态/撤销（Snackbar）/提醒入口/详情弹层全部保留。
 *
 * 接线（Lead 负责 MainNavHost）：
 * ```kotlin
 * "hw" -> HomeworkScreen(
 *     onBack = { navController.popBackStack() },
 *     session = session,
 *     homeworkRepository = container.homeworkRepository,
 *     marksRepository = container.marksRepository,
 * )
 * ```
 *
 * 四态：正常列表 / 空（暂无作业） / 加载（首屏 + 下拉刷新） / 错误（加载失败且有缓存时仍显示列表 + 顶部提示）。
 *
 * @param initialFilter 预览注入：初始筛选页（默认全部；不改变既有调用方）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeworkScreen(
    onBack: () -> Unit,
    session: Session?,
    marksRepository: MarksRepository?,
    homeworkRepository: HomeworkRepository?,
    modifier: Modifier = Modifier,
    initialRows: List<HomeworkRow> = emptyList(),
    initialMarks: MarksSnapshot? = null,
    forcedErrorMessage: String? = null,
    skipLoad: Boolean = false,
    /** v3.2：单条作业提醒（Lead 弹提醒时间选项并排程）。 */
    onRemindHomework: ((com.furina.classtools.data.homework.HomeworkRow) -> Unit)? = null,
    /** 预览注入：初始筛选（全部/未完成/已完成）。 */
    initialFilter: HomeworkFilter = HomeworkFilter.ALL,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val reduceMotion = rememberReduceMotion()
    val fallbackMarks = remember { MutableStateFlow(initialMarks ?: MarksSnapshot()) }
    val marksFlow = remember(marksRepository, initialMarks) { marksRepository?.marks ?: fallbackMarks }
    val marks: MarksSnapshot by marksFlow.collectAsState()

    var rows by remember { mutableStateOf(initialRows) }
    var phase by remember {
        mutableStateOf(if (skipLoad || forcedErrorMessage != null) Phase.READY else Phase.LOADING)
    }
    var errorMessage by remember { mutableStateOf(forcedErrorMessage) }
    var fromCache by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<HomeworkItem?>(null) }

    fun notify(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    /** v4.1 撤销：完成/取消完成后 5s 内可一键撤销。 */
    fun notifyUndo(message: String, undo: () -> Unit) {
        scope.launch {
            val result = snackbar.showSnackbar(
                message = message,
                actionLabel = "撤销",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                undo()
                snackbar.showSnackbar("已撤销")
            }
        }
    }

    suspend fun applyResult(result: HomeworkLoadResult) {
        when (result) {
            is HomeworkLoadResult.Success -> {
                rows = result.rows
                fromCache = result.fromCache
                errorMessage = null
                phase = Phase.READY
            }
            is HomeworkLoadResult.Failure -> {
                errorMessage = result.message
                phase = if (rows.isEmpty()) Phase.FAILED else Phase.READY
            }
        }
    }

    LaunchedEffect(reloadToken) {
        if (skipLoad || forcedErrorMessage != null) {
            phase = Phase.READY
            return@LaunchedEffect
        }
        if (session == null) {
            errorMessage = "请先完成身份验证后再查看作业"
            phase = Phase.FAILED
            return@LaunchedEffect
        }
        phase = Phase.LOADING
        val repository = homeworkRepository
        if (repository == null) {
            phase = Phase.READY
            return@LaunchedEffect
        }
        applyResult(repository.load(force = reloadToken > 0))
        runCatching { marksRepository?.refresh() }
    }

    fun refresh() {
        scope.launch {
            isRefreshing = true
            try {
                val repository = homeworkRepository
                if (repository != null) applyResult(repository.load(force = true))
                runCatching { marksRepository?.refresh(force = true) }
            } finally {
                isRefreshing = false
            }
        }
    }

    fun toggleDone(item: HomeworkItem) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val repository = marksRepository
        if (repository == null) {
            notify("未登录，无法同步完成状态")
            return
        }
        val target = !item.done
        scope.launch {
            when (repository.setMark(MarkKind.HW, item.id, target)) {
                is com.furina.classtools.data.marks.MarksUpdateResult.Success ->
                    notifyUndo(if (target) "已完成，正在同步" else "已取消完成") {
                        scope.launch { repository.setMark(MarkKind.HW, item.id, item.done) }
                    }
                is com.furina.classtools.data.marks.MarksUpdateResult.Failure ->
                    notify("同步失败，稍后自动重试")
            }
        }
    }

    val nowMillis = remember(reloadToken) { System.currentTimeMillis() }
    val allItems = remember(rows, marks, nowMillis) {
        HomeworkDueCalculator.pruneExpired(
            rows.map { row -> HomeworkDueCalculator.toItem(row, marks.isDone(MarkKind.HW, row.id), nowMillis) },
        )
    }
    val unfinishedCount = allItems.count { !it.done }
    val doneCount = allItems.count { it.done }
    val filterPages = remember(allItems) {
        HomeworkFilter.entries.map { filter ->
            HomeworkDueCalculator.sort(HomeworkDueCalculator.filter(allItems, filter))
        }
    }
    val pagerState = rememberPagerState(
        initialPage = initialFilter.ordinal.coerceIn(0, HomeworkFilter.entries.size - 1),
        pageCount = { HomeworkFilter.entries.size },
    )

    val subtitle = buildString {
        if (allItems.isNotEmpty()) append("未完成 $unfinishedCount · 已完成 $doneCount")
        if (fromCache) {
            if (isNotEmpty()) append(" · ")
            append("本机缓存")
        }
    }.trim()

    // 沉浸光感顶栏「滚动 → 浮层」：滚动量由列表状态上报（列表滚动 → 顶栏材质渐显）。
    var ctTopBarScrollPx by remember { mutableFloatStateOf(0f) }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                CtLightTopBar(
                    scrollPx = ctTopBarScrollPx,
                    title = "作业",
                    subtitle = subtitle.takeIf { it.isNotBlank() },
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { refresh() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "刷新作业")
                        }
                    },
                )

                HomeworkFilterTabs(
                    pagerState = pagerState,
                    allCount = allItems.size,
                    unfinishedCount = unfinishedCount,
                    doneCount = doneCount,
                    reduceMotion = reduceMotion,
                )

                Box(modifier = Modifier.weight(1f)) {
                    val contentModifier = Modifier.fillMaxSize()
                    when {
                        phase == Phase.LOADING -> CtLoading(
                            modifier = Modifier.align(Alignment.Center),
                            text = "加载作业…",
                        )

                        phase == Phase.FAILED -> HomeworkErrorContent(
                            message = errorMessage ?: "加载失败",
                            onRetry = { reloadToken += 1 },
                            modifier = contentModifier,
                        )

                        else -> PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = { refresh() },
                            modifier = contentModifier,
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                key = { page -> HomeworkFilter.entries[page].name },
                            ) { page ->
                                HomeworkFilterPage(
                                    items = filterPages.getOrElse(page) { emptyList() },
                                    allEmpty = allItems.isEmpty(),
                                    nowMillis = nowMillis,
                                    onToggle = { item -> toggleDone(item) },
                                    onOpen = { item -> detail = item },
                                    onRemind = onRemindHomework?.let { cb -> { item -> cb(item.row) } },
                                    onRefresh = { refresh() },
                                    onScrollPx = { px -> ctTopBarScrollPx = px },
                                )
                            }
                        }
                    }

                    if (phase == Phase.READY && errorMessage != null) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(CtRadius.cardCompact),
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.ErrorOutline,
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

            androidx.compose.foundation.layout.Box(
            modifier = Modifier.ctLightMaterial(
                CtLightScene.SNACKBAR,
                shape = CtShape.input,
                bodySafe = true,
            ),
        ) { CtSnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            )
            }
        }
    }

    detail?.let { item ->
        HomeworkDetailDialog(
            item = item,
            onRemind = onRemindHomework?.let { cb -> { cb(item.row) } },
            onToggle = {
                toggleDone(item)
                detail = item.copy(done = !item.done)
            },
            onDismiss = { detail = null },
        )
    }
}

// -----------------------------------------------------------------------------
// 筛选（pager + 跟手指示条）
// -----------------------------------------------------------------------------

@Composable
private fun HomeworkFilterTabs(
    pagerState: PagerState,
    allCount: Int,
    unfinishedCount: Int,
    doneCount: Int,
    reduceMotion: Boolean,
) {
    val scope = rememberCoroutineScope()
    val filters = HomeworkFilter.entries
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.forEachIndexed { index, filter ->
                CtToolChip(
                    text = when (filter) {
                        HomeworkFilter.ALL -> "全部 $allCount"
                        HomeworkFilter.UNFINISHED -> "未完成 $unfinishedCount"
                        HomeworkFilter.DONE -> "已完成 $doneCount"
                    },
                    selected = pagerState.currentPage == index,
                    onClick = {
                        scope.launch {
                            if (reduceMotion) pagerState.scrollToPage(index)
                            else pagerState.animateScrollToPage(index)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 列表 / 卡片 / 详情
// -----------------------------------------------------------------------------

@Composable
private fun HomeworkFilterPage(
    items: List<HomeworkItem>,
    allEmpty: Boolean,
    nowMillis: Long,
    onToggle: (HomeworkItem) -> Unit,
    onOpen: (HomeworkItem) -> Unit,
    onRemind: ((HomeworkItem) -> Unit)? = null,
    onRefresh: () -> Unit = {},
    onScrollPx: (Float) -> Unit = {},
) {
    val listState = rememberLazyListState()
    // 沉浸光感顶栏：把「Lazy 列表滚动」折算成顶栏需要的滚动量（px）。
    // 首项未滚满时用偏移量；已滚过首项给哨兵值 —— 顶栏浮层进度夹到 1（全显），不会闪烁。
    LaunchedEffect(listState, onScrollPx) {
        snapshotFlow {
            if (listState.firstVisibleItemIndex > 0) {
                com.furina.classtools.ui.theme.CT_TOP_BAR_FULL_SCROLL_PX
            } else {
                listState.firstVisibleItemScrollOffset.toFloat()
            }
        }.collect { onScrollPx(it) }
    }
    val grouped = LinkedHashMap<String, MutableList<HomeworkItem>>()
    for (item in items) {
        val label = HomeworkDueCalculator.groupLabel(item, nowMillis)
        grouped.getOrPut(label) { mutableListOf() }.add(item)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (items.isEmpty()) {
            item(key = "empty") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EmptyState(
                        icon = Icons.AutoMirrored.Rounded.Assignment,
                        title = if (allEmpty) "暂无作业" else "没有符合条件的作业",
                        message = if (allEmpty) "老师发布后会出现在这里" else "试试其他筛选",
                    )
                    if (allEmpty) {
                        Spacer(Modifier.height(CtSpace.sm))
                        CtToolPrimaryButton(
                            text = "刷新",
                            onClick = onRefresh,
                            modifier = Modifier.fillMaxWidth(0.6f),
                        )
                    }
                }
            }
        } else {
            grouped.forEach { (label, groupItems) ->
                item(key = "header-$label") {
                    CtToolSectionHeader(title = label)
                }
                items(groupItems, key = { it.id }) { item ->
                    HomeworkCard(
                        item = item,
                        onToggle = { onToggle(item) },
                        onOpen = { onOpen(item) },
                        onRemind = onRemind?.let { cb -> { cb(item) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeworkCard(
    item: HomeworkItem,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onRemind: (() -> Unit)? = null,
) {
    CtToolCard(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(
                checked = item.done,
                onCheckedChange = { onToggle() },
                modifier = Modifier.padding(top = 2.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpen)
                    .padding(start = 2.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title.ifBlank { "作业" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.done) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textDecoration = if (item.done) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.due != null) {
                        Spacer(Modifier.width(6.dp))
                        DuePill(text = item.due.label, tone = item.due.tone)
                    }
                }
                val body = item.row.content.trim()
                if (body.isNotEmpty()) {
                    Spacer(Modifier.height(CtSpace.xxs))
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val shortDue = HomeworkDueCalculator.shortDueText(item.row)
                    if (shortDue.isNotBlank()) {
                        Text(
                            text = shortDue,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (item.row.author.isNotBlank()) {
                        if (shortDue.isNotBlank()) Text(" · ", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = item.row.author,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (item.attachments.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.AttachFile,
                            contentDescription = "${item.attachments.size} 个附件",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "${item.attachments.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (onRemind != null) {
                IconButton(
                    onClick = onRemind,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = "提醒我",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DuePill(text: String, tone: HomeworkDueTone) {
    val (container, content) = when (tone) {
        HomeworkDueTone.OVERDUE, HomeworkDueTone.URGENT ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer

        HomeworkDueTone.WARN ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer

        HomeworkDueTone.SAFE ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer

        HomeworkDueTone.NEUTRAL ->
            MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = CtShapeTokens.pill, color = container, contentColor = content) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun HomeworkErrorContent(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CtToolErrorState(
            title = "作业加载失败",
            message = message,
            onRetry = onRetry,
        )
    }
}

@Composable
private fun HomeworkDetailDialog(
    item: HomeworkItem,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    onRemind: (() -> Unit)? = null,
) {
    CtLightAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = item.title.ifBlank { "作业" },
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CtPill(text = if (item.done) "已完成" else "未完成")
                    if (item.due != null) {
                        Spacer(Modifier.width(8.dp))
                        DuePill(text = item.due.label, tone = item.due.tone)
                    }
                }
                Text(
                    text = item.row.content.ifBlank { "（无内容）" },
                    style = MaterialTheme.typography.bodyMedium,
                )
                HorizontalDivider()
                DetailRow("截止", HomeworkDueCalculator.dueText(item.row))
                if (item.row.author.isNotBlank()) DetailRow("发布者", item.row.author)
                if (item.row.ts > 0) DetailRow("发布时间", HomeworkDueCalculator.tsText(item.row.ts))
                if (item.attachments.isNotEmpty()) {
                    DetailRow("附件", "${item.attachments.size} 个")
                    item.attachments.forEach { attachment ->
                        AttachmentRow(attachment)
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (onRemind != null) {
                    TextButton(onClick = onRemind) { Text("提醒我") }
                }
                TextButton(onClick = onToggle) {
                    Text(if (item.done) "取消完成" else "标记完成")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row {
        Text(
            text = "$label ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AttachmentRow(attachment: HomeworkAttachmentMeta) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.AttachFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = attachment.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = attachment.sizeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 页面内部阶段（四态中的加载/错误两态由它承载）。 */
private enum class Phase { LOADING, READY, FAILED }


