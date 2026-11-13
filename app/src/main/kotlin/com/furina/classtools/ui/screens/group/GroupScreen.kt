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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.sync.UserDataRepository
import com.furina.classtools.features.group.GroupConfig
import com.furina.classtools.features.group.GroupConfigCodec
import com.furina.classtools.features.group.GroupEngine
import com.furina.classtools.features.group.GroupExportFactory
import com.furina.classtools.features.group.GroupExportPayload
import com.furina.classtools.features.group.GroupMember
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
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.tool.CtToolCard
import com.furina.classtools.ui.components.tool.CtToolPrimaryButton
import com.furina.classtools.ui.components.tool.CtToolSegmented
import com.furina.classtools.ui.components.tool.CtToolSectionHeader
import com.furina.classtools.ui.components.CtTopBar
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.expressive.CtBottomSheet
import com.furina.classtools.ui.components.expressive.CtBottomSheetFooter
import com.furina.classtools.ui.components.expressive.CtBottomSheetHeader
import com.furina.classtools.ui.components.expressive.CtChipGroup
import com.furina.classtools.ui.components.expressive.CtChipOption
import com.furina.classtools.ui.components.expressive.CtSegmentOption
import com.furina.classtools.ui.components.expressive.CtSegmentedButtons
import com.furina.classtools.ui.theme.CtShapeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 分组工具页（抽签三步的第 2 步；配置 → 分组结果）。
 *
 * 接线方式（由 Lead 在 MainNavHost 完成）：
 * ```kotlin
 * "group" -> GroupScreen(
 *     onBack = { navController.popBackStack() },
 *     onExport = { payload -> exportCoordinator.exportGroup(payload) },
 *     session = session,
 *     userDataRepository = container.userDataRepository,
 *     prevMembers = drawResultForGroup(),                 // 上一步抽签结果（座号+姓名）
 *     rosterMembers = classMembers.map { GroupMember(it.seat, it.name) },
 * )
 * ```
 *
 * - 参数持久化：`userdata.group`（GroupConfigCodec），防抖 600ms；失败静默；
 * - 导出：产出 [GroupExportPayload]（GroupsBlock + 参数区），绘制由 Lead 的导出协调器负责；
 * - 四态：加载（恢复参数）/ 空（来源无成员）/ 错误 / 正常；≥600dp 左右分栏。
 *
 * @param prevMembers 上一步抽签结果（来源「上一步抽签序号」）
 * @param rosterMembers 班级名单（来源「班级名单」）
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
    initialConfig: GroupConfig = GroupConfig(),
    initialResult: GroupResult? = null,
    initialStep: GroupStep? = null,
    forcedErrorMessage: String? = null,
    skipRestore: Boolean = false,
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

    val effectiveMembers: List<GroupMember> = when (state.config.source) {
        GroupSource.PREV -> prevMembers
        GroupSource.NAMES -> {
            if (state.config.selectedSeats.isEmpty()) rosterMembers
            else rosterMembers.filter { it.seat in state.config.selectedSeats }
        }
    }
    val rosterSelected = when (state.config.source) {
        GroupSource.NAMES -> effectiveMembers.isNotEmpty()
        GroupSource.PREV -> true
    }

    fun updateConfig(newConfig: GroupConfig) {
        inlineError = null
        state = GroupWorkflow.updateConfig(state, newConfig)
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
            CtTopBar(
                title = "分组",
                onBack = onBack,
                actions = {
                    if (state.step == GroupStep.RESULT) {
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
                        inlineError = inlineError,
                        onUpdateConfig = ::updateConfig,
                        onRunGrouping = ::runGrouping,
                        onRegroup = ::regroup,
                        onBackToConfig = { state = GroupWorkflow.back(state) },
                        onOpenRosterPicker = { showRosterPicker = true },
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
                all = rosterMembers,
                selected = state.config.selectedSeats.toSet(),
                onConfirm = { seats ->
                    showRosterPicker = false
                    updateConfig(state.config.copy(selectedSeats = seats.sorted()))
                },
                onDismiss = { showRosterPicker = false },
            )
        }
    }
}

private enum class GroupPhase { LOADING, READY, ERROR }

@Composable
private fun GroupContent(
    state: GroupWorkflowState,
    effectiveMembers: List<GroupMember>,
    rosterMembers: List<GroupMember>,
    inlineError: String?,
    onUpdateConfig: (GroupConfig) -> Unit,
    onRunGrouping: () -> Unit,
    onRegroup: () -> Unit,
    onBackToConfig: () -> Unit,
    onOpenRosterPicker: () -> Unit,
    onExport: () -> Unit,
    onCopy: () -> Unit,
) {
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
                        StepIndicator(state.step, onBackToConfig)
                        ConfigCard(
                            config = state.config,
                            memberCount = effectiveMembers.size,
                            rosterCount = rosterMembers.size,
                            inlineError = inlineError,
                            onUpdate = onUpdateConfig,
                            onOpenRosterPicker = onOpenRosterPicker,
                        )
                        PrimaryActions(
                            step = state.step,
                            result = state.result,
                            onRun = onRunGrouping,
                            onRegroup = onRegroup,
                            onBackToConfig = onBackToConfig,
                            onExport = onExport,
                            onCopy = onCopy,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Column(
                        modifier = Modifier.weight(0.58f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ResultArea(state = state, onExport = onExport, onCopy = onCopy)
                        Spacer(modifier = Modifier.height(8.dp))
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
                        StepIndicator(state.step, onBackToConfig)
                        when (state.step) {
                            GroupStep.CONFIG -> ConfigCard(
                                config = state.config,
                                memberCount = effectiveMembers.size,
                                rosterCount = rosterMembers.size,
                                inlineError = inlineError,
                                onUpdate = onUpdateConfig,
                                onOpenRosterPicker = onOpenRosterPicker,
                            )

                            GroupStep.RESULT -> ResultArea(
                                state = state,
                                onExport = onExport,
                                onCopy = onCopy,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = padding)) {
                        PrimaryActions(
                            step = state.step,
                            result = state.result,
                            onRun = onRunGrouping,
                            onRegroup = onRegroup,
                            onBackToConfig = onBackToConfig,
                            onExport = onExport,
                            onCopy = onCopy,
                        )
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
            maxLines = 1,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StepIndicator(step: GroupStep, onBackToConfig: () -> Unit, modifier: Modifier = Modifier) {
    CtToolSegmented(
        options = listOf("1 配置", "2 分组结果"),
        selectedIndex = if (step == GroupStep.CONFIG) 0 else 1,
        onSelect = { index ->
            if (index == 0 && step == GroupStep.RESULT) onBackToConfig()
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun ConfigCard(
    config: GroupConfig,
    memberCount: Int,
    rosterCount: Int,
    inlineError: String?,
    onUpdate: (GroupConfig) -> Unit,
    onOpenRosterPicker: () -> Unit,
) {
    GroupCompactCard(title = "分组设置") {
        Text("成员来源", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CtToolSegmented(
            options = listOf(GroupSource.PREV.label, GroupSource.NAMES.label),
            selectedIndex = if (config.source == GroupSource.PREV) 0 else 1,
            onSelect = { index ->
                onUpdate(
                    config.copy(
                        source = if (index == 0) GroupSource.PREV else GroupSource.NAMES,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "成员：${memberCount} 人",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (config.source == GroupSource.NAMES) {
                TextButton(onClick = onOpenRosterPicker) {
                    Text("选择名单（${config.selectedSeats.size}/$rosterCount）")
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
    step: GroupStep,
    result: GroupResult?,
    onRun: () -> Unit,
    onRegroup: () -> Unit,
    onBackToConfig: () -> Unit,
    onExport: () -> Unit,
    onCopy: () -> Unit,
) {
    when (step) {
        GroupStep.CONFIG -> CtToolPrimaryButton(
            text = "开始分组",
            onClick = onRun,
            modifier = Modifier.fillMaxWidth(),
        )

        GroupStep.RESULT -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onBackToConfig) { Text("修改参数") }
                CtToolPrimaryButton(
                    text = "重新分组",
                    onClick = onRegroup,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onExport) {
                    Icon(Icons.Rounded.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("导出图片")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onCopy, enabled = result != null) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "复制分组结果")
                }
            }
        }
    }
}

@Composable
private fun ResultArea(
    state: GroupWorkflowState,
    onExport: () -> Unit,
    onCopy: () -> Unit,
) {
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
                    text = "设置参数后开始分组",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GroupCompactCard(
            title = "${result.snapshot.groupCount} 组 · ${result.totalMembers} 人",
            trailing = { CtPill(text = result.snapshot.modeLabel) },
        ) {
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
                        RoundedCornerShape(10.dp),
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RosterPickerSheet(
    all: List<GroupMember>,
    selected: Set<Int>,
    onConfirm: (Set<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf(selected) }
    CtBottomSheet(onDismissRequest = onDismiss) {
        CtBottomSheetHeader(
            title = "选择名单",
            subtitle = "已选 ${current.size} / ${all.size} 人",
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            CtChipGroup(
                options = all.map { member ->
                    CtChipOption(key = member.seat.toString(), label = member.label)
                },
                selectedKeys = current.map { it.toString() }.toSet(),
                onToggle = { key ->
                    val seat = key.toIntOrNull() ?: return@CtChipGroup
                    current = if (seat in current) current - seat else current + seat
                },
                multiSelect = true,
            )
            CtBottomSheetFooter(
                primaryText = "确定",
                onPrimary = { onConfirm(current) },
                secondaryText = "全选",
                onSecondary = { current = all.map { it.seat }.toSet() },
            )
        }
    }
}

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
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.errorContainer) {
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

