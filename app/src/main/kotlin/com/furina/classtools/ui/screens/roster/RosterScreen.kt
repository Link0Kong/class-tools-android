@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.furina.classtools.ui.screens.roster

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.member.ClassMembersRepository
import com.furina.classtools.data.member.ClassMembersResult
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.sync.UserDataRepository
import com.furina.classtools.features.roster.RollStats
import com.furina.classtools.features.roster.RollStatus
import com.furina.classtools.features.roster.RosterCodec
import com.furina.classtools.features.roster.RosterFilter
import com.furina.classtools.features.roster.RosterLogic
import com.furina.classtools.features.roster.RosterMember
import com.furina.classtools.features.roster.RosterState
import com.furina.classtools.features.roster.RosterTab
import com.furina.classtools.ui.components.CtLoading
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.CtTopBar
import com.furina.classtools.ui.components.EmptyState
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.expressive.CtSegmentOption
import com.furina.classtools.ui.components.expressive.CtSegmentedButtons
import com.furina.classtools.ui.theme.CtShapeTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 名册 / 点名页（原生）。
 *
 * 契约：`docs/api/USERDATA_V6.md` 的 `roster` section + `docs/BACKEND_CONTRACT.md` §7.7：
 * - 名单来源 `class_members`（`ClassMembersRepository`，6h 缓存），只显示姓名/座号/职务/徽章，**不显示学号**；
 * - 点名状态写 `userdata.roster`（`rollSel` + `rollSt` in/ab + 派生 `rollNames`），整段随账号同步；
 * - 名单变化时按**姓名**迁移状态（对齐 mp `roster.js writeRoll`），避免座号重排错位；
 * - 名册勾选加入点名名单；点名页「到/假」切换、已处理沉底、重置本轮、沿用抽签名单；
 * - 隐私：不导出名单、不显示学号；搜索仅本机。
 *
 * 接线（Lead 负责 MainNavHost）：
 * ```kotlin
 * "roster" -> RosterScreen(
 *     onBack = { navController.popBackStack() },
 *     session = session,
 *     classMembersRepository = container.classMembersRepository,
 *     userDataRepository = container.userDataRepository,
 * )
 * ```
 *
 * @param initialMembers 预览注入：班级名单
 * @param initialRoster 预览注入：roster section 内存态
 * @param initialTab 预览注入：默认页签
 * @param skipLoad 预览/测试：跳过仓库请求
 * @param forcedErrorMessage 预览注入：错误态
 */
@Composable
fun RosterScreen(
    onBack: () -> Unit,
    session: Session?,
    classMembersRepository: ClassMembersRepository?,
    userDataRepository: UserDataRepository?,
    modifier: Modifier = Modifier,
    initialMembers: List<RosterMember> = emptyList(),
    initialRoster: RosterState? = null,
    initialTab: RosterTab = RosterTab.ROSTER,
    skipLoad: Boolean = false,
    forcedErrorMessage: String? = null,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val fallbackSections = remember { MutableStateFlow(JsonObject(emptyMap())) }
    val sectionsFlow = userDataRepository?.currentSections ?: fallbackSections
    val sections by sectionsFlow.collectAsState()

    var rosterState by remember(initialRoster) {
        mutableStateOf(initialRoster ?: RosterCodec.fromJson(sections["roster"]))
    }
    LaunchedEffect(sections) {
        val remote = RosterCodec.fromJson(sections["roster"])
        val emptyLocal = rosterState == RosterState() && rosterState.rollNamesText.isBlank()
        if (emptyLocal && remote != RosterState()) rosterState = remote
    }

    var members by remember(initialMembers) { mutableStateOf(initialMembers) }
    var fromCache by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(initialTab) }
    var filter by remember { mutableStateOf(RosterFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var phase by remember {
        mutableStateOf(
            when {
                forcedErrorMessage != null -> RosterPhase.ERROR
                skipLoad -> RosterPhase.READY
                else -> RosterPhase.LOADING
            },
        )
    }
    var errorMessage by remember { mutableStateOf(forcedErrorMessage) }

    fun notify(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    LaunchedEffect(reloadToken) {
        if (skipLoad || forcedErrorMessage != null) {
            phase = RosterPhase.READY
            return@LaunchedEffect
        }
        if (session == null) {
            errorMessage = "请先完成身份验证后再查看名册"
            phase = RosterPhase.ERROR
            return@LaunchedEffect
        }
        val repository = classMembersRepository
        if (repository == null) {
            phase = RosterPhase.READY
            return@LaunchedEffect
        }
        if (members.isEmpty()) phase = RosterPhase.LOADING
        when (val result = repository.load(force = reloadToken > 0)) {
            is ClassMembersResult.Success -> {
                members = RosterLogic.membersOf(result.members)
                fromCache = result.fromCache
                errorMessage = null
                phase = RosterPhase.READY
            }

            is ClassMembersResult.Failure -> {
                errorMessage = result.message
                phase = if (members.isEmpty()) RosterPhase.ERROR else RosterPhase.READY
            }
        }
        runCatching { userDataRepository?.pull(force = false) }
    }

    fun writeRoster(next: RosterState) {
        rosterState = next
        scope.launch {
            runCatching {
                val repository = userDataRepository ?: return@runCatching
                val current = repository.currentSections.first()
                val sectionsNext = LinkedHashMap<String, JsonElement>(current)
                sectionsNext["roster"] = RosterCodec.toJson(next)
                repository.updateCurrent(JsonObject(sectionsNext))
            }
        }
    }

    fun setRollSel(seats: List<Int>) {
        val next = RosterLogic.withRollSel(rosterState, seats, members)
        writeRoster(next)
    }

    fun toggleStatus(index: Int) {
        writeRoster(RosterLogic.toggleStatus(rosterState, index))
    }

    val stats = remember(rosterState) { RosterLogic.stats(rosterState) }
    val rows = remember(members, query, filter, rosterState) {
        RosterLogic.filterRows(members, query, filter, rosterState)
    }
    val rollItems = remember(rosterState, members) {
        RosterLogic.rollItems(rosterState, members, handledLast = true)
    }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                CtTopBar(
                    title = "名册 · 点名",
                    subtitle = buildString {
                        if (members.isNotEmpty()) append("${members.size} 人")
                        if (fromCache) {
                            if (isNotEmpty()) append(" · ")
                            append("本机缓存")
                        }
                        val rollCount = rosterState.rollSel.size
                        if (rollCount > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("点名 $rollCount 人")
                        }
                    },
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { reloadToken += 1 }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "刷新名册")
                        }
                    },
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CtSegmentedButtons(
                        options = listOf(
                            CtSegmentOption(RosterTab.ROSTER.name, "名册", Icons.Rounded.Groups),
                            CtSegmentOption(RosterTab.ROLL.name, "点名", Icons.Rounded.HowToReg),
                        ),
                        selectedKey = tab.name,
                        onSelect = { key ->
                            RosterTab.entries.firstOrNull { it.name == key }?.let { tab = it }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (phase) {
                        RosterPhase.LOADING -> CtLoading(
                            modifier = Modifier.align(Alignment.Center),
                            text = "加载班级名单…",
                        )

                        RosterPhase.ERROR -> ErrorContent(
                            message = errorMessage ?: "名册加载失败",
                            onRetry = { reloadToken += 1 },
                            modifier = Modifier.fillMaxSize(),
                        )

                        RosterPhase.READY -> {
                            if (members.isEmpty()) {
                                EmptyState(
                                    icon = Icons.Rounded.Groups,
                                    title = "名单尚未同步",
                                    message = "请检查身份验证或稍后重试",
                                )
                            } else {
                                when (tab) {
                                    RosterTab.ROSTER -> RosterListContent(
                                        rows = rows,
                                        query = query,
                                        filter = filter,
                                        onQuery = { query = it },
                                        onFilter = { filter = it },
                                        rosterState = rosterState,
                                        onToggleRoll = { seat ->
                                            writeRoster(RosterLogic.toggleMemberInRoll(rosterState, seat, members))
                                        },
                                        onSelectAll = {
                                            writeRoster(RosterLogic.selectAll(rosterState, members))
                                            notify("已全选加入点名名单")
                                        },
                                        onClear = { showClearConfirm = true },
                                    )

                                    RosterTab.ROLL -> RollContent(
                                        stats = stats,
                                        items = rollItems,
                                        hasRollList = rosterState.rollSel.isNotEmpty(),
                                        onToggle = { index -> toggleStatus(index) },
                                        onSetStatus = { index, status -> writeRoster(RosterLogic.setStatus(rosterState, index, status)) },
                                        onUseRosterSel = {
                                            if (rosterState.rosterSel.isEmpty()) {
                                                notify("抽签名单为空，请先在抽签页选择名单")
                                            } else {
                                                setRollSel(rosterState.rosterSel)
                                                notify("已沿用抽签名单 ${rosterState.rosterSel.size} 人")
                                            }
                                        },
                                        onSelectAll = {
                                            writeRoster(RosterLogic.selectAll(rosterState, members))
                                            notify("已全选加入点名名单")
                                        },
                                        onClear = { showClearConfirm = true },
                                        onReset = { showResetConfirm = true },
                                    )
                                }
                            }
                        }
                    }

                    if (phase == RosterPhase.READY && errorMessage != null) {
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

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            )
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("重置本轮点名") },
            text = { Text("清空全部「到/假」状态，保留点名名单。") },
            confirmButton = {
                TextButton(onClick = {
                    writeRoster(RosterLogic.resetRoll(rosterState))
                    showResetConfirm = false
                    notify("已重置本轮")
                }) { Text("重置") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("取消") }
            },
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空点名名单") },
            text = { Text("清空点名名单与本轮到/假状态（不影响抽签名单）。") },
            confirmButton = {
                TextButton(onClick = {
                    writeRoster(RosterLogic.clearRoll(rosterState))
                    showClearConfirm = false
                    notify("已清空点名名单")
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            },
        )
    }
}

// -----------------------------------------------------------------------------
// 名册页签
// -----------------------------------------------------------------------------

@Composable
private fun RosterListContent(
    rows: List<com.furina.classtools.features.roster.RosterRow>,
    query: String,
    filter: RosterFilter,
    onQuery: (String) -> Unit,
    onFilter: (RosterFilter) -> Unit,
    rosterState: RosterState,
    onToggleRoll: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text("搜索姓名 / 座号 / 职务") },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RosterFilter.entries.forEach { option ->
                val count = when (option) {
                    RosterFilter.ALL -> rows.size
                    RosterFilter.COMMITTEE -> rows.count { it.member.isCommittee }
                    RosterFilter.ROLL -> rows.count { it.inRoll }
                }
                FilterChipSmall(
                    label = "${option.label} $count",
                    selected = filter == option,
                    onClick = { onFilter(option) },
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSelectAll) { Text("全选点名") }
            TextButton(onClick = onClear) { Text("清空") }
        }
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(rows, key = { it.member.seat }) { row ->
                MemberRow(row = row, onToggleRoll = { onToggleRoll(row.member.seat) })
            }
        }
    }
}

@Composable
private fun MemberRow(
    row: com.furina.classtools.features.roster.RosterRow,
    onToggleRoll: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CtShapeTokens.listItem,
        color = if (row.inRoll) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.member.seat.toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(30.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.member.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (row.member.role.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        CtPill(
                            text = row.member.role,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    if (row.member.badge.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        CtPill(text = row.member.badge)
                    }
                }
            }
            Checkbox(
                checked = row.inRoll,
                onCheckedChange = { onToggleRoll() },
            )
        }
    }
}

@Composable
private fun FilterChipSmall(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
    )
}

// -----------------------------------------------------------------------------
// 点名页签
// -----------------------------------------------------------------------------

@Composable
private fun RollContent(
    stats: RollStats,
    items: List<com.furina.classtools.features.roster.RollItem>,
    hasRollList: Boolean,
    onToggle: (Int) -> Unit,
    onSetStatus: (Int, RollStatus?) -> Unit,
    onUseRosterSel: () -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onReset: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        RollStatsCard(stats = stats)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onUseRosterSel) { Text("沿用抽签名单") }
            OutlinedButton(onClick = onSelectAll) { Text("全选") }
            OutlinedButton(onClick = onClear) { Text("清空") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onReset, enabled = hasRollList) { Text("重置本轮") }
        }
        if (items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                EmptyState(
                    icon = Icons.Rounded.HowToReg,
                    title = "还没有点名名单",
                    message = "可在名册页勾选成员，或沿用抽签名单",
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 900.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { "${it.index}-${it.seat}" }) { item ->
                    RollBubble(
                        name = item.name,
                        seat = item.seat,
                        status = item.status,
                        onToggle = { onToggle(item.index) },
                        onSetStatus = { status -> onSetStatus(item.index, status) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RollStatsCard(stats: RollStats) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
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
private fun StatItem(label: String, value: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
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
    val container = when (status) {
        RollStatus.IN -> MaterialTheme.colorScheme.primaryContainer
        RollStatus.AB -> MaterialTheme.colorScheme.tertiaryContainer
        null -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onToggle),
        shape = CtShapeTokens.listItem,
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "座号 $seat" + when (status) {
                        RollStatus.IN -> " · 到"
                        RollStatus.AB -> " · 假"
                        null -> ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { onSetStatus(RollStatus.IN) },
                modifier = Modifier.size(36.dp),
            ) {
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
            IconButton(
                onClick = { onSetStatus(RollStatus.AB) },
                modifier = Modifier.size(36.dp),
            ) {
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

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyState(icon = Icons.Rounded.ErrorOutline, title = "名册加载失败", message = message)
        FilledTonalButton(onClick = onRetry) { Text("重试") }
    }
}

/** 页面内部阶段（四态中的加载/错误两态由它承载）。 */
private enum class RosterPhase { LOADING, READY, ERROR }
