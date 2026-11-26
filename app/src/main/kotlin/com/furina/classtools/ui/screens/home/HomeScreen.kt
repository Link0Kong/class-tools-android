package com.furina.classtools.ui.screens.home

import com.furina.classtools.ui.components.CtToastBus

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import com.furina.classtools.ui.components.CtButton
import com.furina.classtools.ui.components.CtButtonStyle

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.QrCodeScanner
import com.furina.classtools.ui.components.light.CtLightAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.furina.classtools.data.homework.HomeworkLoadResult
import com.furina.classtools.data.homework.HomeworkRepository
import com.furina.classtools.data.tasks.TaskRepository
import com.furina.classtools.data.tasks.pendingTasks
import com.furina.classtools.data.tasks.text
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
import com.furina.classtools.data.timetable.SessionInfo
import com.furina.classtools.data.timetable.SessionState
import com.furina.classtools.data.timetable.TimetableRepository
import com.furina.classtools.data.timetable.TodayState
import com.furina.classtools.data.timetable.TtCalendar
import com.furina.classtools.data.timetable.TtLoadResult
import com.furina.classtools.data.timetable.TtSnapshot
import com.furina.classtools.data.timetable.TtPlan
import com.furina.classtools.data.tool.AppTool
import com.furina.classtools.data.tool.ToolCatalog
import com.furina.classtools.features.home.HomeToolsEditor
import com.furina.classtools.ui.adaptive.rememberCtAdaptiveSpec
import com.furina.classtools.ui.components.CtBrandDots
import com.furina.classtools.ui.components.CtGlassIconButton
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.CtRootHeader
import com.furina.classtools.ui.components.CtStickyHeaderBar
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.HarmonySkeletonRow
import com.furina.classtools.ui.components.HarmonySkeletonTextBlock
import com.furina.classtools.ui.components.NotificationPermissionBanner
import com.furina.classtools.ui.components.ctStickyHeaderProgress
import com.furina.classtools.ui.components.glass.CtGlassCard
import com.furina.classtools.ui.theme.CtAccents
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtShape
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.CtSpace
import com.furina.classtools.ui.theme.CtText
import com.furina.classtools.ui.theme.CtTokens
import com.furina.classtools.ui.theme.rememberReduceMotion
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import com.furina.classtools.ui.components.ctRootPageEdgeFade
import com.furina.classtools.ui.components.ctHeaderCollapseProgress

/**
 * 工作台 v5（参考 HFUT-Schedule 卡片层次 + xiaoying 渐变 hero / 快捷图标行；只借鉴设计）。
 *
 * 结构（自上而下）：
 * 0. 「今日」hero：日期/星期 + 周次/角色 + 问候 + 上课中/下一节/已结束/无课 + 进度条；
 * 1. 今日课程卡：时间线（多节/上课中 pill/已结束）+「现在」时间线（H-05）+「课表 ›」；
 * 2. 宿舍电费卡：关注宿舍/剩余电量/状态 pill/更新时间；未关注只引导（**0 请求**）；
 * 3. 作业待交卡：待交数量 + 最新一条（每 5s 上浮淡出/下浮淡入轮换，页面不可见暂停）；
 * 4. 通知待处理卡：待处理数量 + 最新一条（轮换；同步中/失败/离线文案 + 行内重试）；
 * 5. 常用工具：xiaoying 式四图标行（无勾选空态引导去工具页/本页编辑；右上「全部 ›」）。
 *
 * 交互/无障碍：
 * - 首次进入逐块错峰淡入（系统「移除动画」/ 预览渲染时自动退化为无动画）；
 * - 快捷工具编辑保持原逻辑（点击加入/移出、最多 4 个、H-03 返回二次确认）；
 * - 四态（加载/空/失败/离线）全部保留（不再提供下拉刷新：与「卡片式 + 无多余手势」的统一动效语言一致）。
 *
 * 自适应：≥840dp 双栏（左：今日课程 + 电费；右：作业 + 通知 + 常用工具），hero 全宽；
 * 内容限宽居中（见 `ui/adaptive`）。
 *
 * 接线（Lead 在 MainNavHost；参数保持 v4 完全一致）：
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    session: Session,
    onOpenTool: (AppTool) -> Unit,
    onHomeToolsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    quickToolIds: List<String> = ToolCatalog.defaultQuickIds,
    homeworkRepository: HomeworkRepository? = null,
    taskRepository: TaskRepository? = null,
    timetableRepository: TimetableRepository? = null,
    marksRepository: MarksRepository? = null,
    powerRepository: PowerRepository? = null,
    noticesRepository: NoticesRepository? = null,
    userDataRepository: UserDataRepository? = null,
    onOpenAll: () -> Unit = {},
    /** 扫一扫：电脑扫码登录 / 抽签存证核验。 */
    onScan: () -> Unit = {},
    showNotificationPermissionBanner: Boolean = false,
    onOpenPermissions: (() -> Unit)? = null,
    /** H-04：电量卡未关注宿舍时直接打开宿主的选择器（宿主通过路由参数让电费页首帧弹选择器）。 */
    onOpenPowerPicker: (() -> Unit)? = null,
    isPageActive: Boolean = true,
    rotationEnabled: Boolean = true,
    nowMillis: Long? = null,
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
    // 页面可见（Lifecycle + 翻页 active）才轮换；不可见暂停
    var lifecycleResumed by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { lifecycleResumed = true }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { lifecycleResumed = false }
    var liveTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isPageActive, lifecycleResumed, nowMillis) {
        if (nowMillis == null && isPageActive && lifecycleResumed) {
            while (true) {
                liveTime = System.currentTimeMillis()
                delay(60_000L - liveTime % 60_000L)
            }
        }
    }
    val currentTime = nowMillis ?: liveTime
    val today = remember(currentTime) { Instant.ofEpochMilli(currentTime).atZone(zone).toLocalDate() }

    var refreshToken by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshToken) {
        if (refreshToken > 0) {
            delay(1000)
            refreshing = false
        }
    }

    // ---- 数据状态 -----------------------------------------------------------
    var ttSnapshot by remember(timetableRepository) { mutableStateOf<TtSnapshot?>(null) }
    val ttPlan = remember(ttSnapshot, currentTime, initialTtPlan) {
        ttSnapshot?.let { TtCalendar.plan(it, currentTime, zone) } ?: initialTtPlan
    }
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
    LaunchedEffect(timetableRepository, refreshToken, isPageActive) {
        if (!isPageActive) return@LaunchedEffect
        val repo = timetableRepository ?: return@LaunchedEffect
        ttLoading = true
        runCatching { repo.cached() }.getOrNull()?.let { snapshot ->
            ttSnapshot = snapshot
        }
        val result = runCatching { repo.load(force = refreshToken > 0) }.getOrNull()
        if (result is TtLoadResult.Success) {
            ttSnapshot = result.snapshot
        }
        ttLoading = false
    }

    // 懒加载：作业
    LaunchedEffect(homeworkRepository, refreshToken) {
        if (taskRepository != null) return@LaunchedEffect
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
                noticesData = repo.cached()
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
    val hwOpenRows = remember(homeworkRows, marks, currentTime) {
        HomeWorkbenchLogic.openHomeworkRows(homeworkRows, marks, currentTime)
    }
    val personal = taskRepository?.snapshot?.collectAsState()?.value
    val taskStatus = taskRepository?.syncStatus?.collectAsState()?.value
    LaunchedEffect(taskRepository, refreshToken) { taskRepository?.sync() }
    val hwItems = remember(personal, hwOpenRows, marks, currentTime) {
        personal?.pendingTasks()
            ?.map { HomeRotationItem(it.text("id"), it.text("title"), listOf(it.text("due"), it.text("dueTime")).filter(String::isNotEmpty).joinToString(" ")) }
            ?: HomeWorkbenchLogic.homeworkRotation(homeworkRows, marks, currentTime)
    }
    val ntEntries: List<NoticeEntry> = noticesData?.auto.orEmpty()
    val ntItems = remember(ntEntries, marks, currentTime) {
        HomeWorkbenchLogic.noticeRotation(ntEntries, marks, currentTime)
    }
    // G13：通知卡展示最后更新时间，便于判断数据是否过期。
    val noticesUpdatedText = remember(noticesData?.fetchedAt) {
        noticesData?.fetchedAt?.takeIf { it > 0 }?.let { ts ->
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(ts))
        }.orEmpty()
    }
    val powerCard = remember(effectivePowerSel, powerRow, powerLow) {
        HomeWorkbenchLogic.powerCard(effectivePowerSel, powerRow, powerLow)
    }

    var hwIndex by remember { mutableIntStateOf(0) }
    var ntIndex by remember { mutableIntStateOf(0) }

    // G22：系统移除动画时停止卡内容自动轮换 + 首屏错峰淡入。
    val reduceMotion = rememberReduceMotion()
    val rotationActive = rotationEnabled &&
        !reduceMotion &&
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
    // H-03：编辑态按返回键先确认是否放弃修改，避免误触丢草稿。
    var showDiscardEdit by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = editing) { showDiscardEdit = true }
    if (showDiscardEdit) {
        CtLightAlertDialog(
            onDismissRequest = { showDiscardEdit = false },
            title = { Text("放弃本次修改？") },
            text = { Text("首页常用工具的改动尚未保存。") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardEdit = false
                    draft = quickToolIds
                    editing = false
                }) { Text("放弃") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardEdit = false }) { Text("继续编辑") }
            },
        )
    }
    // 本地乐观顺序：保存回调是异步的，先更新 UI，避免「点完成没反应」。
    var quickOrder by rememberSaveable { mutableStateOf(quickToolIds) }
    LaunchedEffect(quickToolIds) {
        if (!editing) quickOrder = quickToolIds
    }
    val quickTools = remember(quickOrder, forceEmptyQuickTools) {
        if (forceEmptyQuickTools) emptyList() else ToolCatalog.quickTools(quickOrder)
    }
    val editableTools = remember { ToolCatalog.tools.filter { !it.adminOnly && it.id != "settings" } }

    fun toggleTool(id: String) {
        draft = if (id in draft) {
            draft - id
        } else if (draft.size >= HomeToolsEditor.MAX_QUICK_TOOLS) {
            CtToastBus.show("最多选 ${HomeToolsEditor.MAX_QUICK_TOOLS} 个常用工具")
            draft
        } else {
            draft + id
        }
    }

    fun finishEditing() {
        val saved = draft.take(HomeToolsEditor.MAX_QUICK_TOOLS)
        quickOrder = saved
        onHomeToolsChange(saved)
        editing = false
    }

    fun openToolId(id: String) {
        ToolCatalog.byId(id)?.let(onOpenTool)
    }

    fun retry() {
        refreshToken += 1
        scope.launch { runCatching { marksRepository?.refresh(force = true) } }
    }

    // 预览渲染（android.exe preview / Android Studio）不播放首屏错峰动画，避免首帧全透明。
    val animateEntrance = !reduceMotion && !LocalInspectionMode.current
    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val contentMaxWidth = if (screenWidthDp >= 900) 960.dp else 880.dp

    // 2026-11-20 页面骨架 v2：滚动内容 + 常驻悬浮玻璃条（渐变模糊）。
    // haze 源 = 滚动内容（只有它会从悬浮条下面经过），悬浮条自己做真模糊。
    val scrollState = rememberScrollState()
    val hazeState = rememberHazeState()
    val density = LocalDensity.current
    val stickyProgress = ctStickyHeaderProgress(scrollState.value.toFloat(), density.density)
    val greeting = remember(today, currentTime) {
        HomeWorkbenchLogic.greeting(Instant.ofEpochMilli(currentTime).atZone(zone).hour)
    }
    val todayLabel = remember(today) { HomeWorkbenchLogic.dateText(today) }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = {
                        refreshing = true
                        refreshToken += 1
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = contentMaxWidth)
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = com.furina.classtools.ui.theme.HarmonyTokens.PagePadding)
                            // 顶部/底部渐隐：内容滑到标题栏/浮岛附近时逐像素淡出（官方参考的顶部悬浮行为）。
                            .ctRootPageEdgeFade(scrollState.value.toFloat())
                            .verticalScroll(scrollState)
                            .padding(top = com.furina.classtools.ui.theme.HarmonyTokens.BreathTop, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(CtTokens.Spacing.cardGapCompact),
                    ) {
                        HomeReveal(enabled = animateEntrance, index = 0) {
                            CtRootHeader(
                                title = "工作台",
                                collapse = ctHeaderCollapseProgress(scrollState.value.toFloat()),
                                eyebrow = "枫丹工具箱",
                                subtitle = "$todayLabel · $greeting",
                                actions = {
                                    CtGlassIconButton(
                                        icon = Icons.Rounded.QrCodeScanner,
                                        contentDescription = "扫一扫",
                                        onClick = onScan,
                                    )
                                    CtGlassIconButton(
                                        icon = Icons.Rounded.Notifications,
                                        contentDescription = "通知待处理",
                                        badge = ntItems.isNotEmpty(),
                                        onClick = { openToolId("nt") },
                                    )
                                    CtGlassIconButton(
                                        icon = Icons.Rounded.Apps,
                                        contentDescription = "全部工具",
                                        onClick = onOpenAll,
                                    )
                                },
                            )
                        }
                        HomeReveal(enabled = animateEntrance, index = 1) {
                            HomeHeroCard(
                                session = session,
                                date = today,
                                plan = ttPlan,
                                loading = ttLoading,
                                nowMillis = currentTime,
                                reduceMotion = reduceMotion,
                                onOpenTimetable = { openToolId("tt") },
                            )
                        }
                        if (showNotificationPermissionBanner && onOpenPermissions != null) {
                            HomeReveal(enabled = animateEntrance, index = 2) {
                                NotificationPermissionBanner(
                                    visible = true,
                                    onOpenPermissions = onOpenPermissions,
                                )
                            }
                        }
                        // 鸿蒙 HomeScreen 顺序：hero → 今日课程 → 宿舍电费 → 作业待交 → 通知待处理 → 常用工具
                        // （单列、内容最大宽 880/960 居中；平板不再分双栏）。
                        HomeReveal(enabled = animateEntrance, index = 3) {
                            HomeTodayCoursesCard(
                                plan = ttPlan,
                                nowMillis = currentTime,
                                loading = ttLoading,
                                onOpen = { openToolId("tt") },
                            )
                        }
                        HomeReveal(enabled = animateEntrance, index = 4) {
                            HomePowerCard(
                                card = powerCard,
                                loading = powerLoading,
                                onOpen = {
                                    if (powerCard == null && onOpenPowerPicker != null) {
                                        onOpenPowerPicker()
                                    } else {
                                        openToolId("power")
                                    }
                                },
                            )
                        }
                        HomeReveal(enabled = animateEntrance, index = 5) {
                            HomeHomeworkCard(
                                count = hwItems.size,
                                item = hwItems.getOrNull(hwIndex % maxOf(1, hwItems.size)),
                                loading = if (personal == null) homeworkLoading else taskStatus == "同步中…",
                                failed = if (personal == null) homeworkFailed else taskStatus == "仅本机",
                                onOpen = { openToolId("hw") },
                            )
                        }
                        HomeReveal(enabled = animateEntrance, index = 6) {
                            HomeNoticesCard(
                                pendingCount = ntItems.size,
                                item = ntItems.getOrNull(ntIndex % maxOf(1, ntItems.size)),
                                state = noticesState,
                                errorText = noticesFailedMessage,
                                updatedText = noticesUpdatedText,
                                onOpen = { openToolId("nt") },
                                onRetry = ::retry,
                            )
                        }
                        HomeReveal(enabled = animateEntrance, index = 7) {
                            HomeQuickToolsSection(
                                quickTools = quickTools,
                                editing = editing,
                                draft = draft,
                                editableTools = editableTools,
                                columns = 4,
                                reduceMotion = reduceMotion,
                                onToggle = ::toggleTool,
                                onStartEdit = {
                                    draft = quickOrder
                                    editing = true
                                },
                                onFinishEdit = ::finishEditing,
                                onReorder = { ids ->
                                    quickOrder = ids
                                    onHomeToolsChange(ids)
                                },
                                onOpenTool = onOpenTool,
                                onOpenAll = onOpenAll,
                            )
                        }
                    }
                    }
                }
            }

            // 常驻悬浮玻璃条：滚过 12dp 后由透明渐变到「材质 + 模糊 + 融合带」。
            CtStickyHeaderBar(
                title = "工作台",
                progress = stickyProgress,
                hazeState = hazeState,
                actions = {
                    CtGlassIconButton(
                        icon = Icons.Rounded.QrCodeScanner,
                        contentDescription = "扫一扫",
                        onClick = onScan,
                    )
                    CtGlassIconButton(
                        icon = Icons.Rounded.Notifications,
                        contentDescription = "通知待处理",
                        badge = ntItems.isNotEmpty(),
                        onClick = { openToolId("nt") },
                    )
                },
            )
        }
    }
}

/** 通知卡的数据状态（同步中/就绪/失败/离线缓存）。 */
private enum class NoticeHomeState { LOADING, READY, OFFLINE, ERROR }

// =============================================================================
// 今日课程卡（v5 玻璃卡 + 分类色图标 + 课程时间线）
// =============================================================================

@Composable
private fun HomeTodayCoursesCard(
    plan: TtPlan?,
    nowMillis: Long,
    loading: Boolean,
    onOpen: () -> Unit,
) {
    val todayCount = plan?.today?.size ?: 0
    val countBadge = if (plan?.hasData == true && todayCount > 0) "$todayCount 节" else null
    HomeInfoCard(
        icon = Icons.Rounded.CalendarMonth,
        title = "今日课程",
        accent = MaterialTheme.colorScheme.secondary,
        onClick = onOpen,
        badge = countBadge,
        trailing = {
            Text(
                text = "课表 ›",
                style = CtText.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        when {
            loading && plan == null -> HomeLoadingRow("读取课表…")
            plan == null || !plan.hasData -> HomeEmptyHint(
                title = "课表未同步",
                sub = "点此同步",
                actionText = "打开课表",
                onAction = onOpen,
            )

            plan.today.isEmpty() -> HomeEmptyHint(
                title = "今天没课",
                sub = plan.next?.let { "下次 · ${it.label} ${it.name}" } ?: "近期暂无课程",
            )

            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    plan.today.forEachIndexed { index, sessionInfo ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        CourseRow(sessionInfo, nowMillis, isNext = sessionInfo.state == SessionState.NEXT &&
                            plan.next?.startAt == sessionInfo.startAt)
                    }
                }
                if (plan.todayState == TodayState.DONE) {
                    Text(
                        text = buildString {
                            append("今日课程已结束")
                            plan.next?.let { append(" · 下次 ${it.label} ${it.name}") }
                        },
                        style = CtText.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        if (loading && plan != null) {
            HomeRefreshingRow("更新课表…")
        }
    }
}

/** 单节课程行：时间、课程名、节次、教室和实时状态。 */
@Composable
private fun CourseRow(sessionInfo: SessionInfo, nowMillis: Long, isNext: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = sessionInfo.startText,
            style = CtText.label,
            color = MaterialTheme.colorScheme.primary,
            // 大字体 1.3x 下时间不换行（旧实现固定 46dp 会截断）：至少保留最小宽度，宽度随字号自然增长。
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.widthIn(min = 46.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sessionInfo.name,
                style = CtText.body,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = listOf(sessionInfo.span, sessionInfo.room)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = CtText.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (sessionInfo.state == SessionState.ONGOING || isNext) {
            Spacer(modifier = Modifier.width(6.dp))
            CtPill(
                text = HomeWorkbenchLogic.courseStatus(sessionInfo, nowMillis),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

// =============================================================================
// 电费卡
// =============================================================================

@Composable
private fun HomePowerCard(card: HomePowerCard?, loading: Boolean, onOpen: () -> Unit) {
    HomeInfoCard(
        icon = Icons.Rounded.Bolt,
        title = "宿舍电费",
        accent = MaterialTheme.colorScheme.tertiary,
        onClick = onOpen,
        badge = if (card != null && card.hasReading) card.stateText else null,
    ) {
        when {
            card == null -> HomeEmptyHint(
                title = "关注宿舍后显示剩余电量",
                sub = "去选择关注宿舍 ›",
            )

            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.label,
                        style = CtText.body,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = when {
                            loading || !card.hasReading -> "读取电量…"
                            card.updText.isNotBlank() -> "更新 ${card.updText}"
                            else -> "点开查看趋势与充值"
                        },
                        style = CtText.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = card.qText,
                        style = CtText.title,
                        color = stateColor(card.stateKey),
                    )
                    Text(
                        text = " ${card.unit}",
                        style = CtText.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
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
private fun HomeHomeworkCard(
    count: Int,
    item: HomeRotationItem?,
    loading: Boolean,
    failed: Boolean,
    onOpen: () -> Unit,
) {
    HomeInfoCard(
        icon = Icons.AutoMirrored.Rounded.Assignment,
        title = "待办任务",
        accent = MaterialTheme.colorScheme.tertiary,
        onClick = onOpen,
        badge = count.takeIf { it > 0 }?.toString(),
    ) {
        when {
            loading && count == 0 -> HomeLoadingRow("同步任务…")
            failed && count == 0 -> HomeMutedLine("仅显示本机任务")
            count == 0 -> HomeMutedLine("暂无待办任务")
            item != null -> RotatingText(item)
            else -> HomeMutedLine("暂无待办任务")
        }
    }
}

@Composable
private fun HomeNoticesCard(
    pendingCount: Int,
    item: HomeRotationItem?,
    state: NoticeHomeState,
    errorText: String,
    onOpen: () -> Unit,
    onRetry: () -> Unit = {},
    updatedText: String = "",
) {
    HomeInfoCard(
        icon = Icons.Rounded.Notifications,
        title = "通知待处理",
        accent = MaterialTheme.colorScheme.primary,
        onClick = onOpen,
        badge = pendingCount.takeIf { it > 0 }?.toString(),
    ) {
        when (state) {
            NoticeHomeState.LOADING -> HomeLoadingRow("通知同步中…")
            NoticeHomeState.OFFLINE -> Row(verticalAlignment = Alignment.CenterVertically) {
                HomeMutedLine("通知需联网同步（显示本机缓存）")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRetry) { Text("重试") }
            }

            NoticeHomeState.ERROR -> Row(verticalAlignment = Alignment.CenterVertically) {
                HomeMutedLine(errorText.ifBlank { "通知同步失败" })
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRetry) { Text("重试") }
            }

            NoticeHomeState.READY -> when {
                pendingCount == 0 -> HomeMutedLine("暂无待处理通知")
                item != null -> RotatingText(item)
                else -> HomeMutedLine("暂无待处理通知")
            }
        }
        if (state == NoticeHomeState.READY && updatedText.isNotBlank()) {
            Text(
                text = "更新 $updatedText",
                style = CtText.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            style = CtText.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// =============================================================================
// 常用工具（v5：四图标快捷行 + 编辑网格）
// =============================================================================

@Composable
private fun HomeQuickToolsSection(
    quickTools: List<AppTool>,
    editing: Boolean,
    draft: List<String>,
    editableTools: List<AppTool>,
    columns: Int,
    reduceMotion: Boolean,
    onToggle: (String) -> Unit,
    onStartEdit: () -> Unit,
    onFinishEdit: () -> Unit,
    onReorder: (List<String>) -> Unit = {},
    onOpenTool: (AppTool) -> Unit,
    onOpenAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CtTokens.Spacing.cardGapCompact)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "常用工具",
                style = CtText.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (!editing && quickTools.isNotEmpty()) {
                Text(
                    text = "${quickTools.size}/${HomeToolsEditor.MAX_QUICK_TOOLS}",
                    style = CtText.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(CtTokens.Spacing.elementGap))
            }
            Text(
                text = if (editing) "完成" else "编辑",
                style = CtText.label,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { if (editing) onFinishEdit() else onStartEdit() }
                    .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            )
            Text(
                text = "全部 ›",
                style = CtText.label,
                color = com.furina.classtools.ui.theme.HarmonyTokens.SlateBlue,
                modifier = Modifier
                    .clickable { onOpenAll() }
                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
            )
        }

        if (editing) {
            Text(
                text = "首页快捷 ${draft.size}/${HomeToolsEditor.MAX_QUICK_TOOLS}",
                style = CtText.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HomeEditableToolGrid(
                tools = editableTools,
                columns = columns,
                draft = draft,
                reduceMotion = reduceMotion,
                onToggle = onToggle,
            )
        } else if (quickTools.isEmpty()) {
            CtGlassCard(
                onClick = onOpenAll,
                shape = HomeCardShape,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = CtTokens.Spacing.cardPadding, vertical = CtTokens.Spacing.cardPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "尚未勾选常用工具",
                            style = CtText.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "去全部工具页勾选想放在首页的工具（最多 4 个）",
                            style = CtText.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "去勾选 ›",
                        style = CtText.label,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else {
            CtGlassCard(shape = HomeCardShape) {
                // 首页快捷栏：不进入编辑态也可长按左右拖动排序（松手写回 userdata）。
                var dragOrder by remember(quickTools) { mutableStateOf(quickTools.map { it.id }) }
                var draggingId by remember { mutableStateOf<String?>(null) }
                var dragCenterX by remember { mutableFloatStateOf(0f) }
                val density = androidx.compose.ui.platform.LocalDensity.current
                val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
                val orderedTools = dragOrder.mapNotNull { id -> quickTools.firstOrNull { it.id == id } }
                    .ifEmpty { quickTools }
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val itemWidthPx = with(density) { (maxWidth / HomeToolsEditor.MAX_QUICK_TOOLS).toPx() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        orderedTools.forEachIndexed { index, tool ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .graphicsLayer { alpha = if (draggingId == tool.id) 0.35f else 1f }
                                    .pointerInput(tool.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                if (orderedTools.size < 2) return@detectDragGesturesAfterLongPress
                                                draggingId = tool.id
                                                dragCenterX = (index + 0.5f) * itemWidthPx
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDrag = { change, amount ->
                                                if (draggingId != tool.id) return@detectDragGesturesAfterLongPress
                                                change.consume()
                                                dragCenterX += amount.x
                                                val target = (dragCenterX / itemWidthPx).toInt()
                                                    .coerceIn(0, orderedTools.size - 1)
                                                val from = dragOrder.indexOf(tool.id)
                                                if (from >= 0 && target != from) {
                                                    val next = dragOrder.toMutableList().apply {
                                                        removeAt(from)
                                                        add(target, tool.id)
                                                    }
                                                    dragOrder = next
                                                    dragCenterX = (target + 0.5f) * itemWidthPx
                                                }
                                            },
                                            onDragEnd = {
                                                if (draggingId != null) onReorder(dragOrder)
                                                draggingId = null
                                            },
                                            onDragCancel = { draggingId = null },
                                        )
                                    },
                            ) {
                                QuickToolTile(
                                    tool = tool,
                                    onClick = { if (draggingId == null) onOpenTool(tool) },
                                    modifier = Modifier.fillMaxWidth(),
                                    reduceMotion = reduceMotion,
                                )
                            }
                        }
                        repeat((HomeToolsEditor.MAX_QUICK_TOOLS - orderedTools.size).coerceAtLeast(0)) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** 编辑网格：2/3/4 列（随窗口），点击加入/移出逻辑由 [onToggle] 保持原样。 */
@Composable
private fun HomeEditableToolGrid(
    tools: List<AppTool>,
    columns: Int,
    draft: List<String>,
    reduceMotion: Boolean,
    onToggle: (String) -> Unit,
) {
    val cols = columns.coerceAtLeast(1)
    tools.chunked(cols).forEach { rowTools ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CtTokens.Spacing.cardGapCompact),
        ) {
            rowTools.forEach { tool ->
                EditableToolTile(
                    tool = tool,
                    selected = tool.id in draft,
                    onClick = { onToggle(tool.id) },
                    modifier = Modifier.weight(1f),
                    reduceMotion = reduceMotion,
                )
            }
            repeat(cols - rowTools.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

// =============================================================================
// 通用小部件
// =============================================================================

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
            style = CtText.body,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (sub.isNotBlank()) {
            Text(
                text = sub,
                style = CtText.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(CtSpace.sm))
            // 2026-11-20 按钮重做：改用全局 CtButton（Tonal = 玻璃材质 + 主色文字 + 按压形变），
            // 宽度只包内容（Modifier.fillMaxWidth(false) 由 wrapContentWidth 保证）。
            CtButton(
                text = actionText,
                onClick = onAction,
                modifier = Modifier,
                style = CtButtonStyle.Tonal,
                fillWidth = false,
                compact = true,
                trailingIcon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            )
        }
    }
}

/**
 * 卡内加载态（2026-11-19 对齐鸿蒙 skeletonPage）：两行骨架块 + 900ms 呼吸，
 * 不再用「转圈 + 文案」；文案转为读屏说明（TalkBack 仍播报「读取课表…」）。
 */
@Composable
private fun HomeLoadingRow(text: String) {
    // 骨架行与真实内容同高（40dp 图标块 + 两行文本 ≈ 56dp），避免加载完成时卡片高度跳变
    HarmonySkeletonRow(description = text)
}

/** 卡内「已有内容、正在刷新」提示（内容还在，不用骨架替换，保持可读）。 */
@Composable
private fun HomeRefreshingRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = CtText.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HomeMutedLine(text: String) {
    Text(
        text = text,
        style = CtText.body,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}





