@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.furina.classtools.ui.screens.roster

import com.furina.classtools.ui.components.light.ctLightMaterial
import com.furina.classtools.ui.theme.CtLightScene
import com.furina.classtools.ui.theme.CtShape
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Refresh
import com.furina.classtools.ui.components.light.CtLightAlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.member.ClassMembersRepository
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.sync.UserDataRepository
import com.furina.classtools.features.roster.RollStats
import com.furina.classtools.features.roster.RollStatus
import com.furina.classtools.features.roster.RosterLogic
import com.furina.classtools.features.roster.RosterMember
import com.furina.classtools.features.roster.RosterState
import com.furina.classtools.ui.components.CtLoading
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.ctTopBarRaw
import com.furina.classtools.ui.components.EmptyState
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.tool.CtToolCard
import com.furina.classtools.ui.components.tool.CtToolChip
import com.furina.classtools.ui.components.tool.CtToolField
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtSpace
import kotlinx.coroutines.launch

/**
 * 点名页（原生，2026-11-14 task-54 从原「名单 · 点名」双页拆成独立工具页）。
 *
 * 契约：`docs/api/USERDATA_V6.md` 的 `roster` section + `docs/BACKEND_CONTRACT.md` §7.7：
 * - 点名名单 `userdata.roster.rollSel`、到/假 `rollSt`、派生 `rollNames`，整段随账号同步；
 * - 名单变化时按**姓名**迁移状态（[RosterLogic.withRollSel]，对齐 mp `roster.js writeRoll`），
 *   与名册页 [RosterScreen] 共用 [rememberRosterSync]，同步语义与拆分前一致；
 * - 标题「点名」；到/假切换、已处理沉底、全选 / 沿用抽签名单 / 清空、重置本轮、搜索/筛选、
 *   空/加载/错误态 + 刷新错误条；
 * - 隐私：不显示学号、不导出名单；搜索仅本机。
 *
 * 接线（Lead 负责 MainNavHost）：
 * ```kotlin
 * "roll" -> RollCallScreen(
 *     onBack = { popTool(toolId) },
 *     session = session,
 *     classMembersRepository = container.classMembersRepository,
 *     userDataRepository = container.userDataRepository,
 * )
 * ```
 *
 * @param skipLoad 预览/测试：跳过班级名单与 userdata 拉取
 * @param initialMembers 预览注入：班级名单
 * @param initialRoster 预览注入：roster section 内存态
 * @param forcedErrorMessage 预览注入：错误态
 */
@Composable
fun RollCallScreen(
    onBack: () -> Unit,
    session: Session?,
    classMembersRepository: ClassMembersRepository?,
    userDataRepository: UserDataRepository?,
    modifier: Modifier = Modifier,
    skipLoad: Boolean = false,
    initialMembers: List<RosterMember> = emptyList(),
    initialRoster: RosterState? = null,
    forcedErrorMessage: String? = null,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val (rosterState, writeRoster) = rememberRosterSync(
        userDataRepository = userDataRepository,
        initialRoster = initialRoster,
    )
    val membersState = rememberRosterMembersState(
        session = session,
        repository = classMembersRepository,
        userDataRepository = userDataRepository,
        initialMembers = initialMembers,
        skipLoad = skipLoad,
        forcedErrorMessage = forcedErrorMessage,
        unauthenticatedMessage = "请先完成身份验证后再查看点名名单",
    )
    val members = membersState.members

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(RollFilter.ALL) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    fun notify(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    /** v4.1 撤销：5s 内可撤销的本地危险操作（重置本轮/清空点名名单）。 */
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

    val stats = remember(rosterState) { RosterLogic.stats(rosterState) }
    // 已处理沉底：未点在前，到/假沉底（RosterLogic.rollItems handledLast = true）。
    val allItems = remember(rosterState, members) {
        RosterLogic.rollItems(rosterState, members, handledLast = true)
    }
    val visibleItems = remember(allItems, query, filter) {
        val q = query.trim()
        allItems.filter { item ->
            val matchQuery = q.isEmpty() ||
                item.name.contains(q, ignoreCase = true) ||
                item.seat.toString() == q
            val matchFilter = when (filter) {
                RollFilter.ALL -> true
                RollFilter.PENDING -> item.status == null
                RollFilter.IN -> item.status == RollStatus.IN
                RollFilter.AB -> item.status == RollStatus.AB
            }
            matchQuery && matchFilter
        }
    }
    val filterCounts = listOf(allItems.size, stats.pending, stats.present, stats.absent)

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                ctTopBarRaw(
                    title = "点名",
                    subtitle = buildString {
                        val total = rosterState.rollSel.size
                        if (total > 0) {
                            append("点名 $total 人 · 到 ${stats.present} · 假 ${stats.absent}")
                        }
                        if (membersState.fromCache) {
                            if (isNotEmpty()) append(" · ")
                            append("本机缓存")
                        }
                    },
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { membersState.reload() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "刷新点名名单")
                        }
                    },
                )

                Box(modifier = Modifier.weight(1f)) {
                    when (membersState.phase) {
                        RosterPhase.LOADING -> CtLoading(
                            modifier = Modifier.align(Alignment.Center),
                            text = "加载班级名单…",
                        )

                        RosterPhase.ERROR -> RosterErrorContent(
                            message = membersState.errorMessage ?: "名单加载失败",
                            onRetry = { membersState.reload() },
                            modifier = Modifier.fillMaxSize(),
                        )

                        RosterPhase.READY -> Column(modifier = Modifier.fillMaxSize()) {
                            if (members.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    EmptyState(
                                        icon = Icons.Rounded.HowToReg,
                                        title = "名单尚未同步",
                                        message = "请稍后重试",
                                    )
                                }
                            } else {
                                RollStatsCard(stats = stats)
                                // 操作行用 FlowRow：大字体 1.3x / 窄屏时自动换行，避免「重置本轮」被挤成两行或被裁切。
                                androidx.compose.foundation.layout.FlowRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(CtSpace.controlGap),
                                    verticalArrangement = Arrangement.spacedBy(CtSpace.controlGap),
                                ) {
                                    CtToolChip(
                                        text = "沿用抽签名单",
                                        selected = false,
                                        onClick = {
                                            if (rosterState.rosterSel.isEmpty()) {
                                                notify("抽签名单为空，请先在抽签页选择名单")
                                            } else {
                                                writeRoster(
                                                    RosterLogic.withRollSel(
                                                        rosterState,
                                                        rosterState.rosterSel,
                                                        members,
                                                    ),
                                                )
                                                notify("已沿用抽签名单 ${rosterState.rosterSel.size} 人")
                                            }
                                        },
                                    )
                                    CtToolChip(
                                        text = "全选",
                                        selected = false,
                                        onClick = {
                                            writeRoster(RosterLogic.selectAll(rosterState, members))
                                            notify("已全选加入点名名单")
                                        },
                                    )
                                    CtToolChip(
                                        text = "清空",
                                        selected = false,
                                        onClick = { showClearConfirm = true },
                                    )
                                    TextButton(
                                        onClick = { showResetConfirm = true },
                                        enabled = rosterState.rollSel.isNotEmpty(),
                                    ) { Text("重置本轮") }
                                }

                                if (rosterState.rollSel.isNotEmpty()) {
                                    CtToolField(
                                        label = "搜索",
                                        value = query,
                                        onValueChange = { query = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                        placeholder = "搜索姓名 / 座号",
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        RollFilter.entries.forEachIndexed { index, option ->
                                            CtToolChip(
                                                text = "${option.label} ${filterCounts.getOrElse(index) { 0 }}",
                                                selected = filter == option,
                                                onClick = { filter = option },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }

                                Box(modifier = Modifier.weight(1f)) {
                                    when {
                                        rosterState.rollSel.isEmpty() -> Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            EmptyState(
                                                icon = Icons.Rounded.HowToReg,
                                                title = "还没有点名名单",
                                                message = "可在名册页勾选成员，或沿用抽签名单",
                                            )
                                        }

                                        visibleItems.isEmpty() -> Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            EmptyState(
                                                icon = Icons.Rounded.HowToReg,
                                                title = "未找到匹配成员",
                                                message = if (query.isNotBlank()) {
                                                    "换个姓名/座号试试"
                                                } else {
                                                    "切换筛选查看全部"
                                                },
                                            )
                                        }

                                        else -> LazyVerticalGrid(
                                            columns = GridCells.Adaptive(minSize = 150.dp),
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .widthIn(max = 900.dp),
                                            contentPadding = PaddingValues(
                                                start = 16.dp,
                                                end = 16.dp,
                                                top = 6.dp,
                                                bottom = 24.dp,
                                            ),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            items(visibleItems, key = { "${it.index}-${it.seat}" }) { item ->
                                                RollBubble(
                                                    name = item.name,
                                                    seat = item.seat,
                                                    status = item.status,
                                                    onToggle = {
                                                        writeRoster(RosterLogic.toggleStatus(rosterState, item.index))
                                                    },
                                                    onSetStatus = { status ->
                                                        haptics.performHapticFeedback(
                                                            HapticFeedbackType.TextHandleMove,
                                                        )
                                                        writeRoster(
                                                            RosterLogic.setStatus(rosterState, item.index, status),
                                                        )
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (membersState.phase == RosterPhase.READY && membersState.errorMessage != null) {
                        RosterRefreshErrorBanner(
                            message = membersState.errorMessage.orEmpty(),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 16.dp, vertical = 6.dp),
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
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            )
            }
        }
    }

    if (showResetConfirm) {
        CtLightAlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("重置本轮点名") },
            text = { Text("清空全部「到/假」状态，保留点名名单。") },
            confirmButton = {
                TextButton(onClick = {
                    val before = rosterState
                    writeRoster(RosterLogic.resetRoll(rosterState))
                    showResetConfirm = false
                    notifyUndo("已重置本轮") { writeRoster(before) }
                }) { Text("重置") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("取消") }
            },
        )
    }

    if (showClearConfirm) {
        CtLightAlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空点名名单") },
            text = { Text("清空点名名单与本轮到/假状态（不影响抽签名单）。") },
            confirmButton = {
                TextButton(onClick = {
                    val before = rosterState
                    writeRoster(RosterLogic.clearRoll(rosterState))
                    showClearConfirm = false
                    notifyUndo("已清空点名名单") { writeRoster(before) }
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            },
        )
    }
}

// -----------------------------------------------------------------------------
// 点名列表内容
// -----------------------------------------------------------------------------

@Composable
private fun RollStatsCard(stats: RollStats) {
    CtToolCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatItem("应到", stats.total.toString())
                StatItem("已处理", stats.handled.toString())
                StatItem("到", stats.present.toString(), MaterialTheme.colorScheme.primary)
                StatItem("假", stats.absent.toString(), MaterialTheme.colorScheme.tertiary)
                StatItem("未点", stats.pending.toString(), MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (stats.completed) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "本轮完成 · 到 ${stats.present} / 假 ${stats.absent}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RollBubble(
    name: String,
    seat: Int,
    status: RollStatus?,
    onToggle: () -> Unit,
    onSetStatus: (RollStatus?) -> Unit,
) {
    val statusText = when (status) {
        RollStatus.IN -> "到"
        RollStatus.AB -> "假"
        null -> "未点"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CtRadius.hero))
            .clickable(onClick = onToggle),
    ) {
        CtToolCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "座号 $seat",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CtPill(
                        text = statusText,
                        containerColor = when (status) {
                            RollStatus.IN -> MaterialTheme.colorScheme.primaryContainer
                            RollStatus.AB -> MaterialTheme.colorScheme.tertiaryContainer
                            null -> MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        contentColor = when (status) {
                            RollStatus.IN -> MaterialTheme.colorScheme.onPrimaryContainer
                            RollStatus.AB -> MaterialTheme.colorScheme.onTertiaryContainer
                            null -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = { onSetStatus(RollStatus.IN) }) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = "到",
                            tint = if (status == RollStatus.IN) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = { onSetStatus(RollStatus.AB) }) {
                        Icon(
                            Icons.Rounded.Cancel,
                            contentDescription = "假",
                            tint = if (status == RollStatus.AB) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 点名列表筛选（纯展示，本页状态）：全部 / 未点 / 到 / 假。 */
private enum class RollFilter(val label: String) {
    ALL("全部"),
    PENDING("未点"),
    IN("到"),
    AB("假"),
}
