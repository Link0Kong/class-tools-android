@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.furina.classtools.ui.screens.vote

import com.furina.classtools.ui.components.light.ctLightMaterial
import com.furina.classtools.ui.theme.CtLightScene
import com.furina.classtools.ui.theme.CtShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HowToVote
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.furina.classtools.data.notify.LiveUpdateNotifier
import com.furina.classtools.data.vote.Poll
import com.furina.classtools.data.vote.PollDetail
import com.furina.classtools.data.vote.PollManageAction
import com.furina.classtools.data.vote.PollManageResult
import com.furina.classtools.data.vote.PollOption
import com.furina.classtools.data.vote.PollSettings
import com.furina.classtools.data.vote.PollVoter
import com.furina.classtools.data.vote.VoteError
import com.furina.classtools.data.vote.VoteLogic
import com.furina.classtools.data.vote.VoteRepository
import com.furina.classtools.data.vote.VoteResult
import com.furina.classtools.ui.adaptive.rememberCtAdaptiveSpec
import com.furina.classtools.ui.components.CtLoading
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.CtLightTopBar
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.tool.CtToolCard
import com.furina.classtools.ui.components.tool.CtToolSectionHeader
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtShapeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 投票详情页（v4）：投票/改票、实时结果、实名投票人、结果隐藏态、管理入口。
 *
 * 轮询：页面可见时 15s `detail(force = true)`；隐藏停止；返回前刷新。
 * 管理：编辑（仅 0 票）/提前结束/延长重开/删除（二次确认）。
 *
 * 接线（Lead）：
 * ```kotlin
 * composable("vote/{pollId}") { entry ->
 *     VoteDetailScreen(
 *         pollId = entry.arguments?.getString("pollId").orEmpty(),
 *         repository = container.voteRepository,
 *         onBack = { navController.popBackStack() },
 *         onEdit = { id -> navController.navigate("vote/create?pollId=$id") },
 *     )
 * }
 * ```
 *
 * @param initialDetail 预览/测试注入
 * @param initialError 预览/测试错误态
 * @param initialManageSheetOpen 预览：直接展开管理弹层
 * @param skipLoad 预览：跳过加载与轮询
 */
@Composable
fun VoteDetailScreen(
    pollId: String,
    repository: VoteRepository,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onEdit: (String) -> Unit = {},
    /** 分享投票（生成 5:4 分享卡；**不展示票数/百分比**）。 */
    onShare: ((Poll, PollDetail) -> Unit)? = null,
    nowMillis: Long = System.currentTimeMillis(),
    initialDetail: PollDetail? = null,
    initialError: VoteError? = null,
    initialManageSheetOpen: Boolean = false,
    skipLoad: Boolean = false,
) {
    val spec = rememberCtAdaptiveSpec()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var detail by remember(initialDetail) { mutableStateOf(initialDetail) }
    var error by remember(initialError) { mutableStateOf(initialError) }
    var loading by remember { mutableStateOf(initialDetail == null && initialError == null && !skipLoad) }
    var submitting by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var localAtFetch by remember { mutableLongStateOf(nowMillis) }
    var showManageSheet by remember { mutableStateOf(initialManageSheetOpen) }
    var confirmClose by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showExtend by remember { mutableStateOf(false) }

    // Live Updates：限时投票在系统实时活动/流体云中显示倒计时，离开详情页即撤销。
    val liveUpdateContext = LocalContext.current
    LaunchedEffect(detail?.poll?.id, detail?.poll?.deadline, detail?.closed, skipLoad) {
        if (skipLoad) return@LaunchedEffect
        val current = detail
        if (current != null && current.poll.deadline > 0L && !current.closed) {
            LiveUpdateNotifier.postVoteDeadline(
                context = liveUpdateContext,
                pollTitle = current.poll.title,
                deadlineMillis = current.poll.deadline,
            )
        } else {
            LiveUpdateNotifier.cancelVote(liveUpdateContext)
        }
    }
    DisposableEffect(liveUpdateContext) {
        onDispose { LiveUpdateNotifier.cancelVote(liveUpdateContext) }
    }

    val nowTick = rememberNowTick(enabled = !skipLoad)
    val effectiveNow = remember(detail, localAtFetch, nowTick) {
        voteEffectiveNow(detail?.serverNow ?: 0L, localAtFetch, nowTick)
    }

    fun applyDetail(next: PollDetail) {
        detail = next
        localAtFetch = System.currentTimeMillis()
        error = null
        loading = false
        if (next.hasVoted) {
            selected = next.myChoices.toSet()
        }
    }

    LaunchedEffect(pollId, reloadToken, skipLoad) {
        if (skipLoad) return@LaunchedEffect
        if (detail == null) loading = true
        when (val result = repository.detail(pollId, force = reloadToken > 0)) {
            is VoteResult.Success -> applyDetail(result.value)
            is VoteResult.Failure -> {
                loading = false
                if (detail == null) error = result.error
            }
        }
    }

    LaunchedEffect(pollId, lifecycleOwner, skipLoad) {
        if (skipLoad) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                delay(VOTE_POLL_INTERVAL_MS)
                when (val result = repository.detail(pollId, force = true)) {
                    is VoteResult.Success -> if (!submitting && !managing) applyDetail(result.value)
                    is VoteResult.Failure -> Unit
                }
            }
        }
    }

    fun submit() {
        val current = detail ?: return
        if (submitting) return
        val choiceError = VoteLogic.validateChoices(
            settings = current.poll.settings,
            choices = selected.toList(),
            optionIds = current.poll.options.map { it.id },
        )
        if (choiceError != null) {
            scope.launch { snackbar.showSnackbar(choiceError.message) }
            return
        }
        scope.launch {
            submitting = true
            when (val result = repository.submit(pollId, selected.toList())) {
                is VoteResult.Success -> {
                    applyDetail(result.value)
                    snackbar.showSnackbar(if (current.hasVoted) "已改票" else "已提交投票")
                }

                is VoteResult.Failure -> snackbar.showSnackbar(result.error.message)
            }
            submitting = false
        }
    }

    fun runManage(action: PollManageAction, successMessage: String? = null) {
        if (managing) return
        scope.launch {
            managing = true
            when (val result = repository.manage(pollId, action)) {
                is VoteResult.Success -> when (val value = result.value) {
                    is PollManageResult.Deleted -> onBack()
                    is PollManageResult.Updated -> {
                        applyDetail(value.detail)
                        successMessage?.let { snackbar.showSnackbar(it) }
                    }
                }

                is VoteResult.Failure -> snackbar.showSnackbar(result.error.message)
            }
            managing = false
        }
    }

    // 沉浸光感顶栏：详情滚动量驱动顶栏浮层渐显。
    val ctTopBarLazyState = androidx.compose.foundation.lazy.rememberLazyListState()
    val ctTopBarScrollPx = if (ctTopBarLazyState.firstVisibleItemIndex > 0) {
        com.furina.classtools.ui.theme.CT_TOP_BAR_FULL_SCROLL_PX
    } else {
        ctTopBarLazyState.firstVisibleItemScrollOffset.toFloat()
    }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = spec.contentMaxWidth)
                    .fillMaxSize()
                    .padding(horizontal = spec.horizontalPadding),
            ) {
                val current = detail
                // 沉浸光感顶栏：滚动量由详情 LazyColumn 上报（列表滚动 → 顶栏材质渐显）。
                CtLightTopBar(
                    scrollPx = ctTopBarScrollPx,
                    title = "投票详情",
                    subtitle = current?.poll?.let { if (it.isClosedStatus) "已结束" else current.statusText() },
                    onBack = onBack,
                    actions = {
                        if (current != null && onShare != null) {
                            IconButton(onClick = { onShare(current.poll, current) }) {
                                Icon(
                                    imageVector = Icons.Rounded.IosShare,
                                    contentDescription = "分享投票",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { reloadToken += 1 }) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "刷新投票详情",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (current?.canManage == true) {
                            IconButton(onClick = { showManageSheet = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "管理投票",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                )

                when {
                    loading -> CtLoading(
                        text = "加载投票…",
                        modifier = Modifier.padding(top = 40.dp),
                    )

                    error != null && current == null -> VoteErrorContent(
                        error = error!!,
                        onRetry = { reloadToken += 1 },
                        modifier = Modifier.weight(1f),
                    )

                    current != null -> {
                        val poll = current.poll
                        val closed = current.closed || poll.isClosedStatus ||
                            VoteLogic.effectiveClosed(poll.status, poll.deadline, effectiveNow)
                        val blockReason = VoteLogic.voteBlockReason(
                            closed = closed,
                            settings = poll.settings,
                            hasVoted = current.hasVoted,
                        )
                        val resultsVisible = !current.resultsHidden
                        val canSelect = blockReason == null || VoteLogic.canChangeVote(
                            settings = poll.settings,
                            closed = closed,
                            hasVoted = current.hasVoted,
                        )

                        LazyColumn(
                            state = ctTopBarLazyState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            item(key = "hero") {
                                VoteDetailHero(
                                    detail = current,
                                    closed = closed,
                                    nowMillis = effectiveNow,
                                    onRefresh = { reloadToken += 1 },
                                )
                            }
                            if (poll.description.isNotBlank()) {
                                item(key = "desc") {
                                    CtToolCard {
                                        Text(
                                            text = poll.description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                            item(key = "options-title") {
                                CtToolSectionHeader(
                                    title = if (resultsVisible) "选项与结果" else "选项",
                                )
                            }
                            items(items = poll.options, key = { it.id }) { option ->
                                val checked = option.id in selected
                                VoteOptionRow(
                                    option = option,
                                    checked = checked,
                                    singleChoice = !poll.settings.multi,
                                    enabled = canSelect && !submitting,
                                    resultsVisible = resultsVisible,
                                    count = current.counts[option.id] ?: 0,
                                    total = current.total,
                                    onClick = {
                                        if (!canSelect) return@VoteOptionRow
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selected = when {
                                            checked -> selected - option.id
                                            poll.settings.multi -> {
                                                if (selected.size >= poll.settings.maxChoices) {
                                                    scope.launch {
                                                        snackbar.showSnackbar("最多选 ${poll.settings.maxChoices} 项")
                                                    }
                                                    selected
                                                } else {
                                                    selected + option.id
                                                }
                                            }

                                            else -> setOf(option.id)
                                        }
                                    },
                                )
                            }
                            if (current.resultsHidden) {
                                item(key = "hidden") {
                                    VoteHiddenResults(
                                        settings = poll.settings,
                                        closed = closed,
                                        hasVoted = current.hasVoted,
                                        total = current.total,
                                    )
                                }
                            }
                            if (!current.resultsHidden && current.voters.isNotEmpty()) {
                                item(key = "voters") {
                                    VoteVotersCard(
                                        voters = current.voters,
                                        anonymous = poll.settings.anonymous,
                                        total = current.total,
                                    )
                                }
                            }
                            item(key = "meta") {
                                Text(
                                    text = buildString {
                                        append("发起人：")
                                        append(poll.ownerName.ifBlank { "班级成员" })
                                        if (poll.createdAt > 0L) {
                                            append(" · ")
                                            append(VoteLogic.formatAt(poll.createdAt))
                                        }
                                        poll.audienceLet { tag -> append(" · ").append(tag) }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                                )
                            }
                        }

                        VoteDetailActionBar(
                            settings = poll.settings,
                            selectedCount = selected.size,
                            hasVoted = current.hasVoted,
                            closed = closed,
                            blockReason = blockReason,
                            submitting = submitting,
                            resultsHidden = current.resultsHidden,
                            onSubmit = { submit() },
                        )
                    }
                }
            }

            androidx.compose.foundation.layout.Box(
            modifier = Modifier.ctLightMaterial(
                CtLightScene.SNACKBAR,
                shape = CtShape.input,
                bodySafe = true,
            ),
        ) { SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
            )
            }
        }
    }

    if (showManageSheet && detail != null) {
        val current = detail!!
        val total = current.total
        VoteManageSheet(
            canEdit = total == 0,
            closed = current.closed || current.poll.isClosedStatus,
            onDismiss = { showManageSheet = false },
            onEdit = {
                showManageSheet = false
                if (total == 0) onEdit(pollId) else scope.launch { snackbar.showSnackbar("已有投票，不能修改内容") }
            },
            onClose = {
                showManageSheet = false
                confirmClose = true
            },
            onExtend = {
                showManageSheet = false
                showExtend = true
            },
            onDelete = {
                showManageSheet = false
                confirmDelete = true
            },
        )
    }

    if (confirmClose) {
        VoteConfirmDialog(
            title = "提前结束投票？",
            message = "结束后不可再投票，结果仍可查看。",
            confirmText = "结束投票",
            onConfirm = {
                confirmClose = false
                runManage(PollManageAction.Close, successMessage = "已结束投票")
            },
            onDismiss = { confirmClose = false },
        )
    }

    if (confirmDelete) {
        VoteConfirmDialog(
            title = "删除投票？",
            message = "删除后不可恢复，投票记录也会一并移除。",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                confirmDelete = false
                runManage(PollManageAction.Delete)
            },
            onDismiss = { confirmDelete = false },
        )
    }

    if (showExtend) {
        VoteExtendDialog(
            nowMillis = effectiveNow,
            onDismiss = { showExtend = false },
            onExtend = { deadline ->
                showExtend = false
                runManage(PollManageAction.Extend(deadline), successMessage = "已延长截止时间")
            },
        )
    }
}

private fun PollDetail.statusText(): String = when {
    resultsHidden -> "投票后可见结果"
    total > 0 -> "${total} 人已投"
    else -> "等待投票"
}

private inline fun com.furina.classtools.data.vote.Poll.audienceLet(block: (String) -> Unit) {
    VoteLogic.audienceTag(audience, audienceCount)?.let(block)
}

/** 详情顶部 hero：标题/说明/标签/倒计时/参与数。 */
@Composable
private fun VoteDetailHero(
    detail: PollDetail,
    closed: Boolean,
    nowMillis: Long,
    onRefresh: () -> Unit,
) {
    val poll = detail.poll
    CtToolCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = poll.title.ifBlank { "未命名投票" },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                VoteCountdownPill(closed = closed, deadline = poll.deadline, nowMillis = nowMillis)
                if (!closed && poll.deadline <= 0L) CtPill(text = "不限时")
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = voteParticipantText(detail.total),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!closed && poll.deadline > 0L) {
                Text(
                    text = voteDeadlineText(poll.deadline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            VoteTagRow(
                tags = voteTags(
                    settings = poll.settings,
                    closed = closed,
                    hasVoted = detail.hasVoted,
                    audienceTag = VoteLogic.audienceTag(poll.audience, poll.audienceCount),
                    mine = detail.mine,
                ),
            )
            if (detail.hasVoted) {
                Text(
                    text = if (poll.settings.anonymous) "你已投票 · 匿名" else "你已投票 · 实名",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 一个选项（可投票 + 结果条）。 */
@Composable
private fun VoteOptionRow(
    option: PollOption,
    checked: Boolean,
    singleChoice: Boolean,
    enabled: Boolean,
    resultsVisible: Boolean,
    count: Int,
    total: Int,
    onClick: () -> Unit,
) {
    val percent = VoteLogic.percent(count, total)
    val ringColor = if (checked) MaterialTheme.colorScheme.primary else Color.Transparent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CtRadius.hero))
            .border(1.5.dp, ringColor, RoundedCornerShape(CtRadius.hero))
            .selectable(
                selected = checked,
                enabled = enabled,
                role = if (singleChoice) Role.RadioButton else Role.Checkbox,
                onClick = onClick,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CtToolCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (singleChoice) {
                RadioButton(selected = checked, onClick = null, enabled = enabled)
            } else {
                Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.text.ifBlank { "未命名选项" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (option.desc.isNotBlank()) {
                    Text(
                        text = option.desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (resultsVisible) {
                Text(
                    text = VoteLogic.formatPercent(percent),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (option.img.isNotBlank()) {
            VoteDataUrlImage(
                dataUrl = option.img,
                contentDescription = option.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(CtShapeTokens.listItem),
            )
        }
        if (resultsVisible) {
            VotePercentBar(percent = percent)
            Text(
                text = "$count 票",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
            }
        }
    }
}

/** 结果隐藏态。 */
@Composable
private fun VoteHiddenResults(
    settings: PollSettings,
    closed: Boolean,
    hasVoted: Boolean,
    total: Int,
) {
    CtToolCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "结果暂不可见",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = VoteLogic.resultHiddenTag(settings) ?: "投票后可见",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (closed) {
                    "投票已结束 · $total 人已投"
                } else if (hasVoted) {
                    "你已投票 · $total 人已投"
                } else {
                    "投票后可见结果 · $total 人已投"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 实名投票人（仅结果可见时由服务端返回；无学号）。 */
@Composable
private fun VoteVotersCard(
    voters: List<PollVoter>,
    anonymous: Boolean,
    total: Int,
) {
    var expanded by remember { mutableStateOf(false) }
    val shown = if (expanded) voters else voters.take(20)
    CtToolCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "投票人（${voters.size} / $total）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                shown.forEach { voter ->
                    CtPill(text = voter.name.ifBlank { "匿名成员" })
                }
            }
            if (!expanded && voters.size > shown.size) {
                TextButton(onClick = { expanded = true }) {
                    Text("展开全部 ${voters.size} 人")
                }
            }
            if (anonymous) {
                Text(
                    text = "匿名投票不显示投票人",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 底部固定动作条：提交/改票/不可投原因。 */
@Composable
private fun VoteDetailActionBar(
    settings: PollSettings,
    selectedCount: Int,
    hasVoted: Boolean,
    closed: Boolean,
    blockReason: String?,
    submitting: Boolean,
    resultsHidden: Boolean,
    onSubmit: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .ctLightMaterial(
                CtLightScene.BOTTOM_DOCK,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                bodySafe = true,
            ),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val hint = when {
                blockReason != null -> blockReason
                settings.multi -> "已选 $selectedCount / 最多 ${settings.maxChoices} 项"
                hasVoted -> "可改票"
                else -> "请选择 1 项"
            }
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = if (blockReason != null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Button(
                onClick = onSubmit,
                enabled = blockReason == null && selectedCount > 0 && !submitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = CtShapeTokens.pill,
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = when {
                        closed -> "投票已结束"
                        submitting -> "提交中…"
                        hasVoted -> "确认改票"
                        else -> "提交投票"
                    },
                )
            }
            if (resultsHidden && !closed && hasVoted) {
                Text(
                    text = "结果将在${VoteLogic.resultHiddenTag(settings)?.removeSuffix("可见") ?: "投票后"}可见",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}
