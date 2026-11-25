@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.furina.classtools.ui.screens.group

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.sync.UserDataRepository
import com.furina.classtools.features.draw.DrawLastResult
import com.furina.classtools.features.draw.DrawLastResultStore
import com.furina.classtools.features.group.GroupConfig
import com.furina.classtools.features.group.GroupConfigCodec
import com.furina.classtools.features.group.GroupEngine
import com.furina.classtools.features.group.GroupExportFactory
import com.furina.classtools.features.group.GroupExportPayload
import com.furina.classtools.features.group.GroupMember
import com.furina.classtools.features.group.GroupMemberResolver
import com.furina.classtools.features.group.GroupMode
import com.furina.classtools.features.group.GroupRandom
import com.furina.classtools.features.group.GroupResult
import com.furina.classtools.features.group.GroupSource
import com.furina.classtools.features.group.GroupStep
import com.furina.classtools.features.group.GroupTransition
import com.furina.classtools.features.group.GroupValidation
import com.furina.classtools.features.group.GroupValidator
import com.furina.classtools.features.group.GroupWorkflow
import com.furina.classtools.features.group.GroupWorkflowState
import com.furina.classtools.features.group.SeededGroupRandom
import com.furina.classtools.ui.components.RosterPickerSheet
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.ctTopBarRaw
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.expressive.CtBottomSheet
import com.furina.classtools.ui.components.tool.CtToolCard
import com.furina.classtools.ui.components.tool.CtToolPrimaryButton
import com.furina.classtools.ui.components.tool.CtToolSegmented
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.CtSpace
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import com.furina.classtools.data.member.MemberEntry
import com.furina.classtools.data.member.RosterMemberSource

/**
 * 分组工具页（独立工具：配置 + 结果同页，不依赖抽签流程）。
 *
 * 接线方式（MainNavHost）：
 * ```kotlin
 * "group" -> GroupScreen(
 *     onBack = { popTool(toolId) },
 *     onExport = { payload -> exportCoordinator.exportGroup(payload) },
 *     session = session,
 *     userDataRepository = container.userDataRepository,
 *     rosterMembers = classMembers.map { GroupMember(it.seat, it.name) },
 *     lastResultStore = SharedPreferencesDrawLastResultStore(context), // 本机最近抽签
 * )
 * ```
 *
 * - 成员来源：全班（名单 + 全选）/ 最近抽签（本机持久化）/ 手动选择；
 * - 最近抽签不依赖抽签页跳转或内存传递，进程重启后仍可读；
 * - 参数持久化：`userdata.group`（GroupConfigCodec），防抖 600ms；失败静默；
 * - 旧数据 `source=prev` 但本机没有最近抽签时给出友好提示（不崩溃）。
 *
 * @param prevMembers 可选的内存兜底（预览/测试）；生产不依赖它
 * @param rosterMembers 班级名单（全班/手动选择/姓名派生）
 * @param lastResultStore 本机最近抽签存储；null = 预览/测试不读
 * @param initialConfig / initialResult / initialStep 预览注入
 * @param skipRestore 预览/测试：跳过 userdata 恢复与保存
 * @param randomSeed 预览/测试：固定随机 seed
 */
@Composable
fun GroupScreen(
    onBack: () -> Unit,
    onExport: (GroupExportPayload) -> Unit,
    modifier: Modifier = Modifier,
    session: Session? = null,
    userDataRepository: UserDataRepository? = null,
    prevMembers: List<GroupMember> = emptyList(),
    rosterMembers: List<GroupMember> = emptyList(),
    lastResultStore: DrawLastResultStore? = null,
    initialConfig: GroupConfig = GroupConfig(),
    initialResult: GroupResult? = null,
    initialStep: GroupStep? = null,
    forcedErrorMessage: String? = null,
    skipRestore: Boolean = false,
    memberSource: RosterMemberSource? = null,
    randomSeed: Long? = null,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var phase by remember(skipRestore, forcedErrorMessage) {
        mutableStateOf(
            when {
                forcedErrorMessage != null -> GroupPhase.ERROR
                skipRestore -> GroupPhase.READY
                else -> GroupPhase.LOADING
            },
        )
    }
    val initialState = remember {
        GroupWorkflowState(
            step = initialStep ?: if (initialResult != null) GroupStep.RESULT else GroupStep.CONFIG,
            config = initialConfig,
            result = initialResult,
        )
    }
    // 跨导航/旋转/进程重建恢复分组工作流（config/result/step）；预览 initial* 语义不变
    var state by rememberSaveable(stateSaver = GroupWorkflowSaver) { mutableStateOf(initialState) }
    var errorMessage by remember(forcedErrorMessage) { mutableStateOf(forcedErrorMessage.orEmpty()) }
    var inlineError by remember { mutableStateOf<String?>(null) }
    var showRosterPicker by remember { mutableStateOf(false) }
    var restored by remember { mutableStateOf(skipRestore) }

    // 最近抽签：本机持久化是唯一来源；SharedPreferences 读取很小，直接在 remember 中读。
    val lastDraw: DrawLastResult? = remember(lastResultStore) {
        runCatching { lastResultStore?.load() }.getOrNull()
    }

    // 1) 恢复 userdata.group 参数
    LaunchedEffect(skipRestore) {
        if (skipRestore) return@LaunchedEffect
        if (session == null) {
            phase = GroupPhase.ERROR
            errorMessage = "请先完成身份验证后再使用分组"
            return@LaunchedEffect
        }
        runCatching {
            userDataRepository?.currentSections?.first()?.get("group")?.let { element ->
                state = state.copy(config = GroupConfigCodec.fromJson(element))
            }
        }
        restored = true
        phase = GroupPhase.READY
    }

    // 2) 配置变更 → 防抖持久化 group section
    LaunchedEffect(state.config, restored) {
        if (!restored || skipRestore) return@LaunchedEffect
        delay(600)
        runCatching {
            val repository = userDataRepository ?: return@LaunchedEffect
            val current = repository.currentSections.first()
            val next = LinkedHashMap<String, JsonElement>(current)
            next["group"] = GroupConfigCodec.toJson(state.config)
            repository.updateCurrent(JsonObject(next))
        }
    }

    val random: GroupRandom = remember(randomSeed) {
        randomSeed?.let { SeededGroupRandom(it) } ?: com.furina.classtools.features.group.defaultGroupRandom()
    }

    val allSeats = remember(rosterMembers) { GroupMemberResolver.allClassSeats(rosterMembers) }
    val prevMembersResolved = remember(lastDraw, rosterMembers, prevMembers) {
        GroupMemberResolver.fromLastDraw(lastDraw, rosterMembers, prevMembers)
    }
    val effectiveMembers: List<GroupMember> = when (state.config.source) {
        GroupSource.PREV -> prevMembersResolved
        GroupSource.NAMES -> {
            val selected = state.config.selectedSeats.toSet()
            rosterMembers.filter { it.seat in selected }
        }
    }
    val rosterSelected = when (state.config.source) {
        GroupSource.NAMES -> effectiveMembers.isNotEmpty()
        // PREV 的「没有最近抽签」由 GroupValidator 给 NO_MEMBERS 文案。
        GroupSource.PREV -> true
    }

    fun updateConfig(newConfig: GroupConfig) {
        inlineError = null
        state = GroupWorkflow.updateConfig(state, newConfig)
    }

    fun saveSelection(seats: List<Int>) {
        updateConfig(state.config.copy(source = GroupSource.NAMES, selectedSeats = seats.distinct().sorted()))
    }

    fun runGrouping() {
        inlineError = null
        when (val transition = GroupWorkflow.start(state, effectiveMembers, random, rosterSelected)) {
            is GroupTransition.Next -> state = transition.state
            is GroupTransition.Rejected -> inlineError = transition.error.message
        }
    }

    fun regroup() {
        inlineError = null
        when (val transition = GroupWorkflow.regroup(state, effectiveMembers, random, rosterSelected)) {
            is GroupTransition.Next -> state = transition.state
            is GroupTransition.Rejected -> inlineError = transition.error.message
        }
    }

    fun export() {
        val result = state.result ?: run { inlineError = "还没分组"; return }
        onExport(GroupExportFactory.create(result))
    }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            ctTopBarRaw(
                subtitle = null,
                title = "分组",
                onBack = onBack,
                actions = {
                    if (state.result != null) {
                        IconButton(onClick = { regroup() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "重新分组")
                        }
                        IconButton(onClick = { export() }) {
                            Icon(Icons.Rounded.IosShare, contentDescription = "导出图片")
                        }
                    }
                },
            )

            when (phase) {
                GroupPhase.LOADING -> CenteredLoading("分组参数加载中…")
                GroupPhase.ERROR -> CenteredError(errorMessage) { phase = GroupPhase.LOADING; restored = false }
                GroupPhase.READY -> {
                    GroupContent(
                        state = state,
                        effectiveMembers = effectiveMembers,
                        rosterMembers = rosterMembers,
                        allSeats = allSeats,
                        lastDraw = lastDraw,
                        inlineError = inlineError,
                        onUpdateConfig = ::updateConfig,
                        onRunGrouping = ::runGrouping,
                        onRegroup = ::regroup,
                        onOpenRosterPicker = { showRosterPicker = true },
                        onSelectAll = { saveSelection(allSeats) },
                        onClearSelection = { saveSelection(emptyList()) },
                        onExport = { export() },
                        onCopy = {
                            val result = state.result ?: return@GroupContent
                            clipboard.setText(AnnotatedString(GroupExportFactory.copyText(result)))
                        },
                    )
                }
            }
        }

        if (showRosterPicker) {
            RosterPickerSheet(
                source = memberSource,
                fallbackMembers = rosterMembers.map { MemberEntry(it.seat, it.name) },
                initialSeats = state.config.selectedSeats.toSet(),
                onConfirm = { seats ->
                    showRosterPicker = false
                    saveSelection(seats.toList())
                },
                onDismiss = { showRosterPicker = false },
            )
        }
    }
}

private enum class GroupPhase { LOADING, READY, ERROR }

/** 成员来源的三个展示分段（「全班」与「手动选择」在 userdata 里仍是 source=names）。 */
private enum class MemberSourceTab { ALL, LAST_DRAW, MANUAL }

private fun sourceTabOf(config: GroupConfig, allSeats: List<Int>): MemberSourceTab = when {
    config.source == GroupSource.PREV -> MemberSourceTab.LAST_DRAW
    allSeats.isNotEmpty() && config.selectedSeats.toSet() == allSeats.toSet() -> MemberSourceTab.ALL
    else -> MemberSourceTab.MANUAL
}

@Composable
private fun GroupContent(
    state: GroupWorkflowState,
    effectiveMembers: List<GroupMember>,
    rosterMembers: List<GroupMember>,
    allSeats: List<Int>,
    lastDraw: DrawLastResult?,
    inlineError: String?,
    onUpdateConfig: (GroupConfig) -> Unit,
    onRunGrouping: () -> Unit,
    onRegroup: () -> Unit,
    onOpenRosterPicker: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onExport: () -> Unit,
    onCopy: () -> Unit,
) {
    val lastDrawCount = lastDraw?.result?.size ?: 0
    val configCard: @Composable () -> Unit = {
        ConfigCard(
            config = state.config,
            memberCount = effectiveMembers.size,
            rosterCount = rosterMembers.size,
            allSeats = allSeats,
            lastDrawCount = lastDrawCount,
            lastDrawTime = lastDraw?.atMillis?.takeIf { it > 0 }?.let(::formatDrawTime).orEmpty(),
            inlineError = inlineError,
            onUpdate = onUpdateConfig,
            onOpenRosterPicker = onOpenRosterPicker,
            onSelectAll = onSelectAll,
            onClearSelection = onClearSelection,
        )
    }
    val primaryActions: @Composable () -> Unit = {
        PrimaryActions(
            result = state.result,
            canRun = effectiveMembers.isNotEmpty(),
            onRun = onRunGrouping,
            onRegroup = onRegroup,
            onExport = onExport,
            onCopy = onCopy,
        )
    }
    val resultArea: @Composable () -> Unit = {
        ResultArea(state = state)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val expanded = maxWidth >= 600.dp
            val padding = if (expanded) 20.dp else 12.dp
            if (expanded) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = padding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(0.42f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        configCard()
                        primaryActions()
                        Spacer(modifier = Modifier.height(CtSpace.sm))
                    }
                    Column(
                        modifier = Modifier.weight(0.58f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        resultArea()
                        Spacer(modifier = Modifier.height(CtSpace.sm))
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = padding),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        configCard()
                        resultArea()
                        Spacer(modifier = Modifier.height(CtSpace.sm))
                    }
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = padding)) {
                        primaryActions()
                    }
                    Spacer(modifier = Modifier.navigationBarsPadding().height(6.dp))
                }
            }
        }
    }
}

/** v6 紧凑卡：标题 titleSmall + 14/12 内边距，密度对齐小程序。 */
@Composable
private fun GroupCompactCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    content: @Composable () -> Unit,
) {
    CtToolCard(modifier = modifier, contentPadding = contentPadding) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null || trailing != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    trailing?.invoke()
                }
            }
            content()
        }
    }
}

/** 卡片内紧凑说明行（只保留数据，不写解释段落）。 */
@Composable
private fun GroupCompactInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ConfigCard(
    config: GroupConfig,
    memberCount: Int,
    rosterCount: Int,
    allSeats: List<Int>,
    lastDrawCount: Int,
    lastDrawTime: String,
    inlineError: String?,
    onUpdate: (GroupConfig) -> Unit,
    onOpenRosterPicker: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
) {
    val tab = sourceTabOf(config, allSeats)
    GroupCompactCard(title = "分组设置") {
        Text("成员来源", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CtToolSegmented(
            options = listOf(
                if (rosterCount > 0) "全班 $rosterCount" else "全班",
                if (lastDrawCount > 0) "最近抽签 $lastDrawCount" else "最近抽签",
                if (tab == MemberSourceTab.MANUAL && memberCount > 0) "手动 $memberCount" else "手动选择",
            ),
            selectedIndex = when (tab) {
                MemberSourceTab.ALL -> 0
                MemberSourceTab.LAST_DRAW -> 1
                MemberSourceTab.MANUAL -> 2
            },
            onSelect = { index ->
                when (index) {
                    0 -> onUpdate(
                        config.copy(
                            source = GroupSource.NAMES,
                            selectedSeats = allSeats,
                        ),
                    )

                    1 -> if (lastDrawCount > 0) onUpdate(config.copy(source = GroupSource.PREV))

                    else -> {
                        val seats = if (config.source == GroupSource.NAMES && tab == MemberSourceTab.MANUAL) {
                            config.selectedSeats
                        } else {
                            emptyList()
                        }
                        onUpdate(config.copy(source = GroupSource.NAMES, selectedSeats = seats))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabledOptions = listOf(rosterCount > 0, lastDrawCount > 0, rosterCount > 0),
        )

        when (tab) {
            MemberSourceTab.ALL -> Text(
                text = "全班 $rosterCount 人 · 已全选",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            MemberSourceTab.LAST_DRAW -> if (lastDrawCount > 0) {
                Text(
                    text = "最近抽签 $lastDrawCount 人" + if (lastDrawTime.isNotBlank()) " · $lastDrawTime" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "还没有抽签结果",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            MemberSourceTab.MANUAL -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "已选 $memberCount/$rosterCount 人",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenRosterPicker, enabled = rosterCount > 0) {
                        Text("选择名单")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onSelectAll, enabled = rosterCount > 0) { Text("全选") }
                    TextButton(onClick = onClearSelection, enabled = memberCount > 0) { Text("清空") }
                }
            }
        }

        Text("分组方式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CtToolSegmented(
            options = listOf(GroupMode.GROUPS.label, GroupMode.PER_GROUP.label),
            selectedIndex = if (config.mode == GroupMode.GROUPS) 0 else 1,
            onSelect = { index ->
                onUpdate(
                    config.copy(
                        mode = if (index == 0) GroupMode.GROUPS else GroupMode.PER_GROUP,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        NumberStepper(
            label = if (config.mode == GroupMode.GROUPS) "组数" else "每组人数",
            value = config.n,
            valueLabel = if (config.mode == GroupMode.GROUPS) "${config.n} 组" else "${config.n} 人/组",
            onChange = { onUpdate(config.copy(n = it)) },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("打乱后再分组", style = MaterialTheme.typography.bodyMedium)
            }
            Switch(
                checked = config.shuffle,
                onCheckedChange = { onUpdate(config.copy(shuffle = it)) },
                modifier = Modifier.semantics { contentDescription = "打乱后再分组" },
            )
        }

        Text(
            text = "预计：${GroupEngine.groupCount(memberCount, config)} 组",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (inlineError != null) {
            Text(
                text = inlineError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun NumberStepper(
    label: String,
    value: Int,
    valueLabel: String,
    onChange: (Int) -> Unit,
    min: Int = 1,
    max: Int = 99,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(
            onClick = { onChange((value - 1).coerceIn(min, max)) },
            modifier = Modifier.semantics { contentDescription = "$label 减少 1" },
        ) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = valueLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(84.dp),
            textAlign = TextAlign.Center,
        )
        IconButton(
            onClick = { onChange((value + 1).coerceIn(min, max)) },
            modifier = Modifier.semantics { contentDescription = "$label 增加 1" },
        ) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun PrimaryActions(
    result: GroupResult?,
    canRun: Boolean,
    onRun: () -> Unit,
    onRegroup: () -> Unit,
    onExport: () -> Unit,
    onCopy: () -> Unit,
) {
    if (result == null) {
        CtToolPrimaryButton(
            text = "开始分组",
            onClick = onRun,
            enabled = canRun,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("导出图片")
            }
            OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("复制")
            }
        }
        CtToolPrimaryButton(
            text = "重新分组",
            onClick = onRegroup,
            enabled = canRun,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ResultArea(state: GroupWorkflowState) {
    val result = state.result
    if (result == null) {
        GroupCompactCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "还没有分组结果",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "选择成员来源后开始分组",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    val sizes = result.groups.map { it.size }
    val eachLabel = if (sizes.isEmpty()) "0" else "${sizes.min()}–${sizes.max()}"
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GroupCompactCard(
            title = "分组结果",
            trailing = { CtPill(text = result.snapshot.modeLabel) },
        ) {
            Text(
                text = "共 ${result.totalMembers} 人 · ${result.snapshot.groupCount} 组 · 每组 $eachLabel 人",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            GroupCompactInfoRow(
                label = "来源",
                value = "${result.snapshot.sourceLabel} · ${result.snapshot.innerLabel}",
            )
        }
        result.groups.chunked(2).forEach { rowGroups ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowGroups.forEach { group ->
                    GroupCard(group = group, modifier = Modifier.weight(1f))
                }
                if (rowGroups.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: com.furina.classtools.features.group.MemberGroup,
    modifier: Modifier = Modifier,
) {
    GroupCompactCard(
        modifier = modifier,
        title = "第 ${group.index} 组",
        trailing = { CtPill(text = "${group.size} 人") },
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        group.members.forEach { member ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                        RoundedCornerShape(CtRadius.tile),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = member.seat.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(30.dp),
                )
                Text(
                    text = member.name.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** 最近抽签时间：`MM-dd HH:mm`（本机时区）。 */
private fun formatDrawTime(atMillis: Long): String =
    runCatching {
        Instant.ofEpochMilli(atMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
    }.getOrDefault("")

@Composable
private fun CenteredLoading(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CenteredError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("加载失败", style = MaterialTheme.typography.titleMedium)
        Text(
            text = message.ifBlank { "请稍后重试" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FilledTonalButton(onClick = onRetry, shape = CtShapeTokens.pill) { Text("重试") }
    }
}

@Suppress("unused")
@Composable
private fun ValidationPreviewChip(error: String) {
    Surface(shape = RoundedCornerShape(CtRadius.tile), color = MaterialTheme.colorScheme.errorContainer) {
        Text(
            text = error,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .semantics { contentDescription = "校验提示：$error" },
        )
    }
}

/** 保留：分组校验独立入口（UI 之外也可复用）。 */
@Suppress("unused")
private fun validateConfig(config: GroupConfig, members: List<GroupMember>): String? =
    when (val validation = GroupValidator.validate(config, members)) {
        is GroupValidation.Valid -> null
        is GroupValidation.Invalid -> validation.error.message
    }
