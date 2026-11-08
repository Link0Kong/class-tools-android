package com.furina.classtools.ui.screens.home

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.furina.classtools.data.homework.HomeworkLoadResult
import com.furina.classtools.data.homework.HomeworkRepository
import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.marks.MarksRepository
import com.furina.classtools.data.marks.MarksSnapshot
import com.furina.classtools.data.notices.NoticeEntry
import com.furina.classtools.data.notices.NoticesData
import com.furina.classtools.data.notices.NoticesLoadResult
import com.furina.classtools.data.notices.NoticesRepository
import com.furina.classtools.data.power.PowerRepository
import com.furina.classtools.data.power.PowerRow
import com.furina.classtools.data.power.PowerRowResult
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.sync.UserDataRepository
import com.furina.classtools.data.timetable.SessionState
import com.furina.classtools.data.timetable.TimetableRepository
import com.furina.classtools.data.timetable.TodayState
import com.furina.classtools.data.timetable.TtCalendar
import com.furina.classtools.data.timetable.TtLoadResult
import com.furina.classtools.data.timetable.TtPlan
import com.furina.classtools.data.tool.AppTool
import com.furina.classtools.data.tool.ToolCatalog
import com.furina.classtools.features.home.HomeToolsEditor
import com.furina.classtools.ui.adaptive.rememberCtAdaptiveSpec
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.ToolCard
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.CtTokens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * 工作台（v2.0.0，对齐微信小程序 `pages/index/index`）。
 *
 * 结构（自上而下）：
 * 1. 问候：`{问候}，{姓名}` + 日期 · 周次 · 角色；
 * 2. 今日课程卡：时间线（多节/上课中 pill/已结束）+「课表 ›」；无课含下次课；未同步引导打开课表；
 * 3. 宿舍电费卡：关注宿舍/剩余电量/状态 pill/更新时间；未关注只引导（**0 请求**）；
 * 4. 作业待交卡：待交数量 + 最新一条（每 5s 上浮淡出/下浮淡入轮换，页面不可见暂停）；
 * 5. 通知待处理卡：待处理数量 + 最新一条（轮换；同步中/失败/离线文案）；
 * 6. 常用工具：勾选 ≤4；无勾选空态引导去工具页/本页编辑；右上「全部 ›」。
 *
 * 自适应：≥600dp 双栏（左：今日课程 + 电费；右：待办 + 常用工具）；内容限宽居中（见 `ui/adaptive`）。
 *
 * 接线（Lead 在 MainNavHost）：新增可选参数传仓库 + `onOpenAll`（跳「工具」Tab）：
 * ```kotlin
 * HomeScreen(
 *     session = session,
 *     quickToolIds = quickToolIds,
 *     onOpenTool = ::openTool,
 *     onHomeToolsChange = { ... },
 *     homeworkRepository = container.homeworkRepository,
 *     timetableRepository = container.timetableRepository,
 *     marksRepository = container.marksRepository,
 *     powerRepository = container.powerRepository,
 *     noticesRepository = container.noticesRepository,
 *     userDataRepository = container.userDataRepository,
 *     onOpenAll = { navController.navigate(Routes.ALL) },
 *     isPageActive = pagerState.currentPage == 0 && pagerState.isScrollInProgress.not(),
 * )
 * ```
 */
@Composable
fun HomeScreen(
    session: Session,
    onOpenTool: (AppTool) -> Unit,
    onHomeToolsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    quickToolIds: List<String> = ToolCatalog.defaultQuickIds,
    homeworkRepository: HomeworkRepository? = null,
    timetableRepository: TimetableRepository? = null,
    marksRepository: MarksRepository? = null,
    powerRepository: PowerRepository? = null,
    noticesRepository: NoticesRepository? = null,
    userDataRepository: UserDataRepository? = null,
    onOpenAll: () -> Unit = {},
    isPageActive: Boolean = true,
    rotationEnabled: Boolean = true,
    nowMillis: Long = System.currentTimeMillis(),
    initialTtPlan: TtPlan? = null,
    initialHomeworkRows: List<HomeworkRow> = emptyList(),
    initialNoticesData: NoticesData? = null,
    initialPowerSel: String? = null,
    initialPowerRow: PowerRow? = null,
    /** 预览/测试：强制显示「尚未勾选常用工具」空态（真实用户空选择由 Lead 传空 quickToolIds）。 */
    forceEmptyQuickTools: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spec = rememberCtAdaptiveSpec()
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(nowMillis) { Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate() }

    var refreshToken by remember { mutableIntStateOf(0) }

    // ---- 数据状态 -----------------------------------------------------------
    var ttPlan by remember { mutableStateOf(initialTtPlan) }
    var ttLoading by remember { mutableStateOf(timetableRepository != null && initialTtPlan == null) }
    var homeworkRows by remember { mutableStateOf(initialHomeworkRows) }
    var homeworkLoading by remember { mutableStateOf(homeworkRepository != null && initialHomeworkRows.isEmpty()) }
    var homeworkFailed by remember { mutableStateOf(false) }
    var noticesData by remember { mutableStateOf(initialNoticesData) }
    var noticesState by remember {
        mutableStateOf(if (initialNoticesData != null) NoticeHomeState.READY else NoticeHomeState.LOADING)
    }
    var noticesFailedMessage by remember { mutableStateOf("") }
    var powerRow by remember { mutableStateOf(initialPowerRow) }
    var powerLoading by remember { mutableStateOf(false) }

    val marksFlow = remember(marksRepository) {
        marksRepository?.marks ?: MutableStateFlow(MarksSnapshot())
    }
    val marks by marksFlow.collectAsState()

    val sections: JsonObject = userDataRepository?.currentSections?.collectAsState()?.value ?: JsonObject(emptyMap())
    val powerSel = remember(sections) {
        (sections["power"] as? JsonObject)
            ?.get("sel")
            ?.let { (it as? JsonPrimitive)?.content }
            ?.trim()
            .orEmpty()
    }
    val powerLow = remember(sections) {
        ((sections["power"] as? JsonObject)?.get("low") as? JsonPrimitive)?.intOrNull
    }
    // 预览/测试注入：没有 userdata 时也能量化展示电费卡（真实运行时以 userdata.power.sel 为准）。
    val effectivePowerSel = powerSel.ifBlank { initialPowerSel?.trim().orEmpty() }

    // 懒加载：课表（缓存优先 → 网络）
    LaunchedEffect(timetableRepository, refreshToken) {
        val repo = timetableRepository ?: return@LaunchedEffect
        ttLoading = true
        runCatching { repo.cached() }.getOrNull()?.let { snapshot ->
            ttPlan = runCatching { TtCalendar.plan(snapshot, nowMillis, zone) }.getOrNull()
        }
        val result = runCatching { repo.load(force = refreshToken > 0) }.getOrNull()
        if (result is TtLoadResult.Success) {
            ttPlan = runCatching { TtCalendar.plan(result.snapshot, nowMillis, zone) }.getOrNull()
        }
        ttLoading = false
    }

    // 懒加载：作业
    LaunchedEffect(homeworkRepository, refreshToken) {
        val repo = homeworkRepository ?: return@LaunchedEffect
        homeworkLoading = true
        homeworkFailed = false
        homeworkRows = runCatching { repo.cached() }.getOrDefault(emptyList())
        when (val result = runCatching { repo.load(force = refreshToken > 0) }.getOrNull()) {
            is HomeworkLoadResult.Success -> homeworkRows = result.rows
            is HomeworkLoadResult.Failure -> homeworkFailed = true
            null -> homeworkFailed = true
        }
        homeworkLoading = false
    }

    // 懒加载：通知（缓存优先 → 网络；失败有缓存则离线态）
    LaunchedEffect(noticesRepository, refreshToken) {
        val repo = noticesRepository ?: return@LaunchedEffect
        noticesState = NoticeHomeState.LOADING
        noticesData = runCatching { repo.cached() }.getOrNull()
        if (noticesData != null) noticesState = NoticeHomeState.READY
        when (val result = runCatching { repo.load(force = refreshToken > 0) }.getOrNull()) {
            is NoticesLoadResult.Success -> {
                noticesData = result.data
                noticesState = NoticeHomeState.READY
            }

            is NoticesLoadResult.Failure -> {
                noticesFailedMessage = result.message
                noticesState = if (noticesData != null) NoticeHomeState.OFFLINE else NoticeHomeState.ERROR
            }

            null -> noticesState = if (noticesData != null) NoticeHomeState.OFFLINE else NoticeHomeState.ERROR
        }
    }

    // 懒加载：电费（只有关注宿舍才请求）
    LaunchedEffect(powerRepository, effectivePowerSel, refreshToken) {
        val repo = powerRepository ?: return@LaunchedEffect
        if (effectivePowerSel.isBlank()) {
            if (initialPowerRow == null) powerRow = null
            powerLoading = false
            return@LaunchedEffect
        }
        powerLoading = powerRow == null
        val result = runCatching { repo.loadRow(effectivePowerSel, force = refreshToken > 0) }.getOrNull()
        (result as? PowerRowResult.Success)?.row?.let { powerRow = it }
        powerLoading = false
    }

    // ---- 统计 / 轮换数据 ----------------------------------------------------
    val hwOpenRows = remember(homeworkRows, marks, nowMillis) {
        HomeWorkbenchLogic.openHomeworkRows(homeworkRows, marks, nowMillis)
    }
    val hwItems = remember(hwOpenRows, marks, nowMillis) {
        HomeWorkbenchLogic.homeworkRotation(homeworkRows, marks, nowMillis)
    }
    val ntEntries: List<NoticeEntry> = noticesData?.auto.orEmpty()
    val ntItems = remember(ntEntries, marks, nowMillis) {
        HomeWorkbenchLogic.noticeRotation(ntEntries, marks, nowMillis)
    }
    val powerCard = remember(effectivePowerSel, powerRow, powerLow) {
        HomeWorkbenchLogic.powerCard(effectivePowerSel, powerRow, powerLow)
    }

    var hwIndex by remember { mutableIntStateOf(0) }
    var ntIndex by remember { mutableIntStateOf(0) }

    // 页面可见（Lifecycle + 翻页 active）才轮换；不可见暂停
    var lifecycleResumed by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { lifecycleResumed = true }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { lifecycleResumed = false }
    val rotationActive = rotationEnabled &&
        isPageActive &&
        lifecycleResumed &&
        HomeWorkbenchLogic.rotationEnabled(maxOf(hwItems.size, ntItems.size), pageVisible = true)

    LaunchedEffect(hwItems.size, hwItems.firstOrNull()?.id, rotationActive) {
        if (!rotationActive || !HomeWorkbenchLogic.rotationEnabled(hwItems.size, true)) return@LaunchedEffect
        while (true) {
            delay(HomeWorkbenchLogic.ROTATION_INTERVAL_MS)
            hwIndex = HomeWorkbenchLogic.nextRotationIndex(hwIndex, hwItems.size)
        }
    }
    LaunchedEffect(ntItems.size, ntItems.firstOrNull()?.id, rotationActive) {
        if (!rotationActive || !HomeWorkbenchLogic.rotationEnabled(ntItems.size, true)) return@LaunchedEffect
        while (true) {
            delay(HomeWorkbenchLogic.ROTATION_INTERVAL_MS)
            ntIndex = HomeWorkbenchLogic.nextRotationIndex(ntIndex, ntItems.size)
        }
    }

    // ---- 常用工具编辑 -------------------------------------------------------
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf(quickToolIds) }
    LaunchedEffect(quickToolIds, editing) {
        if (!editing) draft = quickToolIds
    }
    val quickTools = remember(quickToolIds, forceEmptyQuickTools) {
        if (forceEmptyQuickTools) emptyList() else ToolCatalog.quickTools(quickToolIds)
    }
    val editableTools = remember { ToolCatalog.tools.filter { it.id != "settings" } }

    fun toggleTool(id: String) {
        draft = if (id in draft) {
            draft - id
        } else if (draft.size >= HomeToolsEditor.MAX_QUICK_TOOLS) {
            Toast.makeText(context, "最多选 ${HomeToolsEditor.MAX_QUICK_TOOLS} 个常用工具", Toast.LENGTH_SHORT).show()
            draft
        } else {
            draft + id
        }
    }

    fun finishEditing() {
        onHomeToolsChange(draft.take(HomeToolsEditor.MAX_QUICK_TOOLS))
        editing = false
    }

    fun openToolId(id: String) {
        ToolCatalog.byId(id)?.let(onOpenTool)
    }

    fun retry() {
        refreshToken += 1
        scope.launch { runCatching { marksRepository?.refresh(force = true) } }
    }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val contentModifier = Modifier
                .widthIn(max = spec.contentMaxWidth)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = spec.horizontalPadding)
            if (spec.twoPane) {
                Row(
                    modifier = contentModifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(spec.mainPaneWeight)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 6.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(CtTokens.Spacing.cardGapCompact),
                    ) {
                        GreetingHeader(session, ttPlan, today, nowMillis)
                        TodayCoursesCard(
                            plan = ttPlan,
                            loading = ttLoading,
                            onOpen = { openToolId("tt") },
                        )
                        PowerCard(
                            card = powerCard,
                            loading = powerLoading,
                            onOpen = { openToolId("power") },
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f - spec.mainPaneWeight)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 6.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(CtTokens.Spacing.cardGapCompact),
                    ) {
                        HomeworkCard(
                            count = hwItems.size,
                            item = hwItems.getOrNull(hwIndex % maxOf(1, hwItems.size)),
                            loading = homeworkLoading,
                            failed = homeworkFailed,
                            onOpen = { openToolId("hw") },
                        )
                        NoticesCard(
                            pendingCount = ntItems.size,
                            item = ntItems.getOrNull(ntIndex % maxOf(1, ntItems.size)),
                            state = noticesState,
                            errorText = noticesFailedMessage,
                            onOpen = { openToolId("nt") },
                        )
                        QuickToolsSection(
                            quickTools = quickTools,
                            editing = editing,
                            draft = draft,
                            editableTools = editableTools,
                            columns = spec.gridColumns,
                            onToggle = ::toggleTool,
                            onStartEdit = {
                                draft = quickToolIds
                                editing = true
                            },
                            onFinishEdit = ::finishEditing,
                            onOpenTool = onOpenTool,
                            onOpenAll = onOpenAll,
                        )
                    }
                }
            } else {
                Column(
                    modifier = contentModifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 10.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(CtTokens.Spacing.cardGapCompact),
                ) {
                    GreetingHeader(session, ttPlan, today, nowMillis)
                    TodayCoursesCard(
                        plan = ttPlan,
                        loading = ttLoading,
                        onOpen = { openToolId("tt") },
                    )
                    PowerCard(card = powerCard, loading = powerLoading, onOpen = { openToolId("power") })
                    HomeworkCard(
                        count = hwItems.size,
                        item = hwItems.getOrNull(hwIndex % maxOf(1, hwItems.size)),
                        loading = homeworkLoading,
                        failed = homeworkFailed,
                        onOpen = { openToolId("hw") },
                    )
                    NoticesCard(
                        pendingCount = ntItems.size,
                        item = ntItems.getOrNull(ntIndex % maxOf(1, ntItems.size)),
                        state = noticesState,
                        errorText = noticesFailedMessage,
                        onOpen = { openToolId("nt") },
                    )
                    QuickToolsSection(
                        quickTools = quickTools,
                        editing = editing,
                        draft = draft,
                        editableTools = editableTools,
                        columns = spec.gridColumns,
                        onToggle = ::toggleTool,
                        onStartEdit = {
                            draft = quickToolIds
                            editing = true
                        },
                        onFinishEdit = ::finishEditing,
                        onOpenTool = onOpenTool,
                        onOpenAll = onOpenAll,
                    )
                    Text(
                        text = "数据与网页 / 小程序互通 · 身份已核验",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** 通知卡的数据状态（同步中/就绪/失败/离线缓存）。 */
private enum class NoticeHomeState { LOADING, READY, OFFLINE, ERROR }

// =============================================================================
// 问候
// =============================================================================

@Composable
private fun GreetingHeader(session: Session, plan: TtPlan?, date: LocalDate, nowMillis: Long) {
    val zone = remember { ZoneId.systemDefault() }
    val hour = remember(nowMillis) { Instant.ofEpochMilli(nowMillis).atZone(zone).hour }
    val greeting = HomeWorkbenchLogic.greeting(hour)
    val subtitle = HomeWorkbenchLogic.subtitle(
        date = date,
        week = plan?.week,
        role = session.role,
    )
    Column(modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)) {
        Text(
            text = "$greeting，${session.name.ifBlank { "同学" }}",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// =============================================================================
// 今日课程
// =============================================================================

@Composable
private fun TodayCoursesCard(
    plan: TtPlan?,
    loading: Boolean,
    onOpen: () -> Unit,
) {
    HomeCard(
        onClick = onOpen,
        header = {
            Text("今天", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "课表 ›",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        when {
            loading && plan == null -> HomeLoadingRow("读取课表…")
            plan == null || !plan.hasData -> HomeEmptyHint(
                title = "课表未同步",
                sub = "打开课表即可拉取本周课程",
                actionText = "打开课表",
                onAction = onOpen,
            )

            plan.today.isEmpty() -> HomeEmptyHint(
                title = "今天没课",
                sub = plan.next?.let { "下次 · ${it.label} ${it.name}" } ?: "近期暂无课程",
            )

            else -> {
                plan.today.forEach { sessionInfo ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = sessionInfo.startText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(46.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sessionInfo.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = listOf(sessionInfo.span, sessionInfo.room)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (sessionInfo.state == SessionState.ONGOING) {
                            CtPill(
                                text = "上课中",
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                if (plan.todayState == TodayState.DONE) {
                    Text(
                        text = buildString {
                            append("今日课程已结束")
                            plan.next?.let { append(" · 下次 ${it.label} ${it.name}") }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        if (loading && plan != null) {
            HomeLoadingRow("更新课表…")
        }
    }
}

// =============================================================================
// 电费
// =============================================================================

@Composable
private fun PowerCard(card: HomePowerCard?, loading: Boolean, onOpen: () -> Unit) {
    HomeCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("宿舍电费", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.weight(1f))
            if (card != null) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        when {
            card == null -> HomeEmptyHint(
                title = "关注宿舍后显示剩余电量",
                sub = "去选择关注宿舍 ›",
            )

            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when {
                            loading || !card.hasReading -> "读取电量…"
                            card.updText.isNotBlank() -> "更新 ${card.updText}"
                            else -> "点开查看趋势与充值"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = card.qText,
                        style = MaterialTheme.typography.headlineSmall,
                        color = stateColor(card.stateKey),
                    )
                    Text(
                        text = " ${card.unit}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                CtPill(
                    text = if (card.hasReading) card.stateText else "读取中",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = stateColor(card.stateKey),
                )
            }
        }
    }
}

@Composable
private fun stateColor(stateKey: String): Color = when (stateKey) {
    "low" -> MaterialTheme.colorScheme.error
    "warn" -> MaterialTheme.colorScheme.tertiary
    "ok" -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

// =============================================================================
// 作业 / 通知（轮换卡）
// =============================================================================

@Composable
private fun HomeworkCard(
    count: Int,
    item: HomeRotationItem?,
    loading: Boolean,
    failed: Boolean,
    onOpen: () -> Unit,
) {
    TaskCard(
        emoji = "📝",
        title = "作业待交",
        count = count,
        onOpen = onOpen,
        accent = MaterialTheme.colorScheme.tertiary,
    ) {
        when {
            loading && count == 0 -> HomeLoadingRow("同步作业…")
            failed && count == 0 -> HomeMutedLine("作业同步失败，点开重试")
            count == 0 -> HomeMutedLine("作业都完成了")
            item != null -> RotatingText(item)
            else -> HomeMutedLine("暂无待交作业")
        }
    }
}

@Composable
private fun NoticesCard(
    pendingCount: Int,
    item: HomeRotationItem?,
    state: NoticeHomeState,
    errorText: String,
    onOpen: () -> Unit,
) {
    TaskCard(
        emoji = "📢",
        title = "通知待处理",
        count = pendingCount,
        onOpen = onOpen,
        accent = MaterialTheme.colorScheme.primary,
    ) {
        when (state) {
            NoticeHomeState.LOADING -> HomeLoadingRow("通知同步中…")
            NoticeHomeState.OFFLINE -> HomeMutedLine("通知需联网同步（显示本机缓存）")
            NoticeHomeState.ERROR -> HomeMutedLine(
                errorText.ifBlank { "通知同步失败，点开重试" },
            )

            NoticeHomeState.READY -> when {
                pendingCount == 0 -> HomeMutedLine("暂无待处理通知")
                item != null -> RotatingText(item)
                else -> HomeMutedLine("暂无待处理通知")
            }
        }
    }
}

@Composable
private fun TaskCard(
    emoji: String,
    title: String,
    count: Int,
    onOpen: () -> Unit,
    accent: Color,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accent.copy(alpha = 0.14f),
            ) {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Text(text = emoji, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (count > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        CtPill(
                            text = count.toString(),
                            containerColor = accent.copy(alpha = 0.16f),
                            contentColor = accent,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                content()
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 卡内最新一条：每 5s 上浮淡出 → 下浮淡入（内容很短，无内置滚动）。 */
@Composable
private fun RotatingText(item: HomeRotationItem) {
    AnimatedContent(
        targetState = item,
        transitionSpec = {
            (slideInVertically { height -> height / 2 } + fadeIn(CtMotion.fadeIn))
                .togetherWith(
                    slideOutVertically { height -> -height / 2 } +
                        fadeOut(CtMotion.fadeOut),
                )
        },
        label = "homeRotation",
    ) { current ->
        Text(
            text = buildString {
                append(current.title)
                if (current.sub.isNotBlank()) append(" · ").append(current.sub)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// =============================================================================
// 常用工具
// =============================================================================

@Composable
private fun QuickToolsSection(
    quickTools: List<AppTool>,
    editing: Boolean,
    draft: List<String>,
    editableTools: List<AppTool>,
    columns: Int,
    onToggle: (String) -> Unit,
    onStartEdit: () -> Unit,
    onFinishEdit: () -> Unit,
    onOpenTool: (AppTool) -> Unit,
    onOpenAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CtTokens.Spacing.cardGapCompact)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "常用工具",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = if (editing) onFinishEdit else onStartEdit) {
                Text(if (editing) "完成" else "编辑")
            }
            TextButton(onClick = onOpenAll) { Text("全部 ›") }
        }

        if (editing) {
            Text(
                text = "点工具加入或移出首页快捷（最多 ${HomeToolsEditor.MAX_QUICK_TOOLS} 个，当前 ${draft.size} 个）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HomeToolGrid(
                tools = editableTools,
                columns = columns,
                showSelection = true,
                isSelected = { tool -> tool.id in draft },
                onClick = { tool -> onToggle(tool.id) },
            )
        } else if (quickTools.isEmpty()) {
            Surface(
                onClick = onOpenAll,
                modifier = Modifier.fillMaxWidth(),
                shape = CtShapeTokens.toolCard,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "尚未勾选常用工具",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "去工具页勾选想放在首页的工具（最多 4 个）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "去勾选 ›",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else {
            HomeToolGrid(
                tools = quickTools,
                columns = columns,
                showSelection = false,
                isSelected = { false },
                onClick = onOpenTool,
            )
        }
    }
}

/** 2/3/4 列工具网格（不依赖 LazyGrid，便于嵌在可滚动的双栏里）。 */
@Composable
private fun HomeToolGrid(
    tools: List<AppTool>,
    columns: Int,
    showSelection: Boolean,
    isSelected: (AppTool) -> Boolean,
    onClick: (AppTool) -> Unit,
) {
    tools.chunked(columns.coerceAtLeast(1)).forEach { rowTools ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CtTokens.Spacing.cardGapCompact),
        ) {
            rowTools.forEach { tool ->
                val selected = isSelected(tool)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (showSelection && selected) {
                                Modifier.border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CtShapeTokens.toolCard,
                                )
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    ToolCard(
                        tool = tool,
                        onClick = { onClick(tool) },
                        modifier = Modifier.height(126.dp),
                    )
                    if (showSelection && selected) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "已选",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(20.dp),
                        )
                    }
                }
            }
            repeat(columns - rowTools.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

// =============================================================================
// 通用小部件
// =============================================================================

@Composable
private fun HomeCard(
    onClick: () -> Unit,
    header: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) { header() }
            Spacer(modifier = Modifier.height(2.dp))
            content()
        }
    }
}

@Composable
private fun HomeEmptyHint(
    title: String,
    sub: String = "",
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (sub.isNotBlank()) {
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(2.dp))
            FilledTonalButton(
                onClick = onAction,
                shape = CtShapeTokens.pill,
                modifier = Modifier.height(CtTokens.Size.buttonHeightSmall),
            ) {
                Text(actionText, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun HomeLoadingRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HomeMutedLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}






