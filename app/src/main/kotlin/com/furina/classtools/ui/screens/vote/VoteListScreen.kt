package com.furina.classtools.ui.screens.vote

import com.furina.classtools.ui.components.CtToastBus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.HowToVote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.furina.classtools.data.vote.PollList
import com.furina.classtools.data.vote.PollSummary
import com.furina.classtools.data.vote.VoteError
import com.furina.classtools.data.vote.VoteLogic
import com.furina.classtools.data.vote.VoteRepository
import com.furina.classtools.data.vote.VoteResult
import com.furina.classtools.ui.adaptive.rememberCtAdaptiveSpec
import com.furina.classtools.ui.components.CtLoading
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.ctTopBarRaw
import com.furina.classtools.ui.components.EmptyState
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.tool.CtToolChip
import com.furina.classtools.ui.components.tool.CtToolPrimaryButton
import com.furina.classtools.ui.components.tool.CtToolSegmented
import com.furina.classtools.ui.theme.CtSpace
import com.furina.classtools.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 投票列表页（v4）。
 *
 * 功能：进行中/已结束筛选、倒计时、规则/可见范围标签、「我已投」、参与人数；
 * 下拉刷新 + 页面可见时 15s 静默轮询；加载/空/错误/离线四态；手机单列、平板两列。
 *
 * 接线（Lead）：
 * ```kotlin
 * composable(Routes.VOTE) {
 *     VoteListScreen(
 *         repository = container.voteRepository,
 *         onBack = { navController.popBackStack() },
 *         onOpenDetail = { id -> navController.navigate("vote/$id") },
 *         onCreate = { navController.navigate(Routes.VOTE_CREATE) },
 *     )
 * }
 * ```
 *
 * @param initialPolls 预览/测试注入（跳过仓库请求）
 * @param initialError 预览/测试：直接进入错误态
 * @param skipLoad 预览/测试：跳过加载与轮询
 * @param nowMillis 预览/测试的固定时钟基线（仅用于初始服务端时间偏移计算）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoteListScreen(
    repository: VoteRepository,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onOpenDetail: (String) -> Unit = {},
    onCreate: () -> Unit = {},
    nowMillis: Long = System.currentTimeMillis(),
    initialPolls: PollList? = null,
    initialError: VoteError? = null,
    skipLoad: Boolean = false,
    /** 预览：左右滑动中间帧（0.5 = 停在两页之间；null = 正常渲染）。 */
    previewPagerOffset: Float? = null,
) {
    val spec = rememberCtAdaptiveSpec()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var pollList by remember(initialPolls) { mutableStateOf(initialPolls) }
    var error by remember(initialError) { mutableStateOf(initialError) }
    var loading by remember { mutableStateOf(initialPolls == null && initialError == null && !skipLoad) }
    var isRefreshing by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableStateOf(0) }
    var showClosed by remember { mutableStateOf(false) }
    // 左右滑动切「进行中 ↔ 已结束」（跟手 + spring；reduceMotion 时禁用拖拽、点击直接切）
    val reduceMotion = rememberReduceMotion()
    val votePagerState = rememberPagerState(
        initialPage = if (showClosed) 1 else 0,
        initialPageOffsetFraction = (previewPagerOffset ?: 0f).coerceIn(-0.5f, 0.5f),
    ) { 2 }
    LaunchedEffect(votePagerState) {
        snapshotFlow { votePagerState.currentPage }.collect { page -> showClosed = page == 1 }
    }
    fun selectClosed(closed: Boolean) {
        showClosed = closed
        val index = if (closed) 1 else 0
        scope.launch {
            if (reduceMotion) votePagerState.scrollToPage(index) else votePagerState.animateScrollToPage(index)
        }
    }
    var mineOnly by remember { mutableStateOf(false) }
    var listFetchedLocalAt by remember { mutableLongStateOf(nowMillis) }
    var backgroundError by remember { mutableStateOf<String?>(null) }

    val nowTick = rememberNowTick(enabled = !skipLoad)
    val effectiveNow = remember(pollList, listFetchedLocalAt, nowTick) {
        voteEffectiveNow(pollList?.serverNow ?: 0L, listFetchedLocalAt, nowTick)
    }

    fun applyList(next: PollList, fromRefresh: Boolean) {
        pollList = next
        listFetchedLocalAt = System.currentTimeMillis()
        error = null
        loading = false
        backgroundError = if (next.fromCache) next.warning else null
        if (fromRefresh && next.fromCache) {
            CtToastBus.show(next.warning ?: "网络不可用，显示缓存")
        }
    }

    LaunchedEffect(reloadToken, skipLoad) {
        if (skipLoad) return@LaunchedEffect
        if (pollList == null) loading = true
        when (val result = repository.list(force = reloadToken > 0)) {
            is VoteResult.Success -> applyList(result.value, fromRefresh = false)
            is VoteResult.Failure -> {
                loading = false
                if (pollList == null) error = result.error else backgroundError = result.error.message
            }
        }
    }

    // 页面可见时 15s 静默轮询（隐藏即停）。
    LaunchedEffect(lifecycleOwner, skipLoad) {
        if (skipLoad) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                delay(VOTE_POLL_INTERVAL_MS)
                when (val result = repository.list(force = true)) {
                    is VoteResult.Success -> applyList(result.value, fromRefresh = false)
                    is VoteResult.Failure -> backgroundError = result.error.message
                }
            }
        }
    }

    fun refresh() {
        if (skipLoad) return
        scope.launch {
            isRefreshing = true
            when (val result = repository.list(force = true)) {
                is VoteResult.Success -> applyList(result.value, fromRefresh = true)
                is VoteResult.Failure -> CtToastBus.show(result.error.message)
            }
            isRefreshing = false
        }
    }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = spec.contentMaxWidth)
                    .fillMaxSize()
                    .padding(horizontal = spec.horizontalPadding),
            ) {
                val current = pollList
                ctTopBarRaw(
                    subtitle = current?.let { "进行中 ${it.openCount} · 已结束 ${it.closedCount}" },
                    title = "投票",
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { refresh() }) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "刷新投票列表",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onCreate) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "发起投票",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )

                when {
                    loading -> CtLoading(text = "加载投票…", modifier = Modifier.padding(top = 40.dp))

                    error != null && pollList == null -> VoteErrorContent(
                        error = error!!,
                        onRetry = { reloadToken += 1 },
                        modifier = Modifier.weight(1f),
                    )

                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CtToolSegmented(
                                options = listOf(
                                    "进行中 ${current?.openCount ?: 0}",
                                    "已结束 ${current?.closedCount ?: 0}",
                                ),
                                selectedIndex = if (showClosed) 1 else 0,
                                onSelect = { index -> selectClosed(index == 1) },
                            )
                            if (current?.polls?.any { it.mine } == true) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CtToolChip(
                                        text = "全部",
                                        selected = !mineOnly,
                                        onClick = { mineOnly = false },
                                    )
                                    CtToolChip(
                                        text = "我发起的",
                                        selected = mineOnly,
                                        onClick = { mineOnly = true },
                                    )
                                }
                            }
                            if (backgroundError != null || current?.fromCache == true) {
                                VoteOfflineBanner(
                                    warning = current?.warning ?: backgroundError,
                                    onRetry = { refresh() },
                                )
                            }
                        }

                        HorizontalPager(
                            state = votePagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(top = 6.dp),
                            userScrollEnabled = !reduceMotion,
                            key = { page -> if (page == 1) "closed" else "open" },
                        ) { pageIndex ->
                            val pageShowClosed = pageIndex == 1
                            val visible = remember(current, pageShowClosed, mineOnly) {
                                val base = current
                                if (base == null) {
                                    emptyList()
                                } else {
                                    VoteLogic.filterForTab(
                                        polls = if (mineOnly) base.polls.filter { it.mine } else base.polls,
                                        showClosed = pageShowClosed,
                                    )
                                }
                            }
                            PullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = { refresh() },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                if (visible.isEmpty()) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Spacer(modifier = Modifier.height(CtSpace.huge))
                                        EmptyState(
                                            icon = Icons.Rounded.HowToVote,
                                            title = if (current == null || current.polls.isEmpty()) {
                                                "还没有投票"
                                            } else {
                                                "该筛选下没有投票"
                                            },
                                            message = if (current == null || current.polls.isEmpty()) {
                                                "点右上角 + 发起"
                                            } else {
                                                "点「全部」查看所有投票"
                                            },
                                        )
                                        CtToolPrimaryButton(
                                            text = if (current == null || current.polls.isEmpty()) {
                                                "发起投票"
                                            } else {
                                                "查看全部"
                                            },
                                            onClick = {
                                                if (current == null || current.polls.isEmpty()) {
                                                    onCreate()
                                                } else {
                                                    mineOnly = false
                                                    selectClosed(false)
                                                }
                                            },
                                            modifier = Modifier.widthIn(max = 260.dp),
                                        )
                                    }
                                } else if (spec.isExpanded) {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp),
                                    ) {
                                        items(items = visible, key = { it.id }) { summary ->
                                            VoteCard(
                                                summary = summary,
                                                nowMillis = effectiveNow,
                                                onClick = { onOpenDetail(summary.id) },
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp),
                                    ) {
                                        items(items = visible, key = { it.id }) { summary ->
                                            VoteCard(
                                                summary = summary,
                                                nowMillis = effectiveNow,
                                                onClick = { onOpenDetail(summary.id) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 列表卡片。 */
@Composable
private fun VoteCard(
    summary: PollSummary,
    nowMillis: Long,
    onClick: () -> Unit,
) {
    val audienceTag = VoteLogic.audienceTag(summary.audience, summary.audienceCount)
    VoteToolCard(onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = summary.title.ifBlank { "未命名投票" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (summary.hasVoted) {
                    CtPill(
                        text = "我已投",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                } else if (summary.closed) {
                    CtPill(text = "已结束")
                }
            }
            VoteTagRow(
                // 右上角已有「我已投」胶囊，避免标签行重复同一关键字
                tags = voteTags(
                    settings = summary.settings,
                    closed = summary.closed,
                    hasVoted = summary.hasVoted,
                    audienceTag = audienceTag,
                    mine = summary.mine,
                ).filterNot { it == "我已投" },
            )
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                VoteCountdownPill(closed = summary.closed, deadline = summary.deadline, nowMillis = nowMillis)
                if (!summary.closed && summary.deadline <= 0L) {
                    CtPill(text = "不限时")
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = voteParticipantText(summary.total),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (summary.ownerName.isNotBlank()) {
                Text(
                    text = "发起人：${summary.ownerName}" +
                        if (summary.createdAt > 0L) " · ${VoteLogic.formatAt(summary.createdAt)}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

