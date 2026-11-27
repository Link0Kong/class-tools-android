package com.furina.classtools.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.furina.classtools.data.homework.HomeworkRepository
import com.furina.classtools.data.marks.MarksRepository
import com.furina.classtools.data.marks.MarksSnapshot
import com.furina.classtools.data.notices.NoticesData
import com.furina.classtools.data.notices.NoticesRepository
import com.furina.classtools.data.power.PowerRepository
import com.furina.classtools.data.power.PowerRow
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.sync.UserDataRepository
import com.furina.classtools.data.tasks.TaskRepository
import com.furina.classtools.data.tasks.pendingTasks
import com.furina.classtools.data.tasks.taskDueLabel
import com.furina.classtools.data.tasks.taskPriorityOrder
import com.furina.classtools.data.tasks.text
import com.furina.classtools.data.timetable.CourseFocus
import com.furina.classtools.data.timetable.SessionInfo
import com.furina.classtools.data.timetable.SessionState
import com.furina.classtools.data.timetable.TimetableRepository
import com.furina.classtools.data.timetable.TodayState
import com.furina.classtools.data.timetable.TtCalendar
import com.furina.classtools.data.timetable.TtLocal
import com.furina.classtools.data.timetable.TtPlan
import com.furina.classtools.data.tool.AppTool
import com.furina.classtools.data.tool.ToolCatalog
import com.furina.classtools.features.home.HomeToolsEditor
import com.furina.classtools.ui.adaptive.CtAdaptive
import com.furina.classtools.ui.adaptive.CtAdaptiveSpec
import com.furina.classtools.ui.adaptive.CtTwoPaneRow
import com.furina.classtools.ui.adaptive.rememberCtAdaptiveSpec
import com.furina.classtools.ui.components.CtButton
import com.furina.classtools.ui.components.CtButtonStyle
import com.furina.classtools.ui.components.CtGlassIconButton
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.CtRootHeader
import com.furina.classtools.ui.components.CtStickyHeaderBar
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.HarmonySkeletonRow
import com.furina.classtools.ui.components.NotificationPermissionBanner
import com.furina.classtools.ui.components.ctHeaderCollapseProgress
import com.furina.classtools.ui.components.ctRootPageEdgeFade
import com.furina.classtools.ui.components.ctStickyHeaderProgress
import com.furina.classtools.ui.components.glass.CtGlassCard
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtSpace
import com.furina.classtools.ui.theme.CtText
import com.furina.classtools.ui.theme.CtTokens
import com.furina.classtools.ui.theme.rememberReduceMotion
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/** 工作台展示当前账号的课程、宿舍、个人待办、通知与常用工具。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    session: Session,
    onOpenTool: (AppTool) -> Unit,
    onHomeToolsChange: (List<String>) -> Unit = {},
    modifier: Modifier = Modifier,
    quickToolIds: List<String> = ToolCatalog.defaultQuickIds,
    taskRepository: TaskRepository? = null,
    timetableRepository: TimetableRepository? = null,
    homeworkRepository: HomeworkRepository? = null,
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
    onCardOpen: ((String, String?) -> Unit)? = null,
    nowMillis: Long? = null,
    initialTtPlan: TtPlan? = null,
    initialNoticesData: NoticesData? = null,
    initialPowerSel: String? = null,
    initialPowerRow: PowerRow? = null,
    /** 预览/测试：强制显示「尚未勾选常用工具」空态（真实用户空选择由 Lead 传空 quickToolIds）。 */
    forceEmptyQuickTools: Boolean = false,
    editSession: HomeToolsEditSession? = null,
) {
    val spec = rememberCtAdaptiveSpec()
    val zone = remember { ZoneId.systemDefault() }
    // 仅在工作台可见时按分钟更新课程及期限。
    var lifecycleResumed by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { lifecycleResumed = true }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { lifecycleResumed = false }
    val pageActive = isPageActive && lifecycleResumed
    var liveTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pageActive, nowMillis) {
        if (nowMillis == null && pageActive) {
            while (true) {
                liveTime = System.currentTimeMillis()
                delay(60_000L - liveTime % 60_000L)
            }
        }
    }
    val currentTime = nowMillis ?: liveTime
    val today = remember(currentTime) { Instant.ofEpochMilli(currentTime).atZone(zone).toLocalDate() }

    val marksFlow = remember(marksRepository) {
        marksRepository?.marks ?: MutableStateFlow(MarksSnapshot())
    }
    val marksSnapshot by marksFlow.collectAsState()
    val marks = remember(marksSnapshot, session.sid) {
        marksSnapshot.takeIf { it.ownerSid == null || it.ownerSid == session.sid } ?: MarksSnapshot()
    }

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

    val loads = rememberHomeDataLoads(
        session = session,
        active = pageActive,
        timetableRepository = timetableRepository,
        taskRepository = taskRepository,
        homeworkRepository = homeworkRepository,
        noticesRepository = noticesRepository,
        marksRepository = marksRepository,
        powerRepository = powerRepository,
        personalTimetable = (sections[TtLocal.SECTION_KEY] as? JsonObject)?.get(TtLocal.FIELD_KEY),
        room = effectivePowerSel,
        initialNotices = initialNoticesData,
    )
    val timetableState = loads.timetable?.state
    val ttSnapshot = timetableState?.data?.snapshot
    val ttLoading = timetableState?.loading == true
    val ttPlan = remember(ttSnapshot, currentTime, initialTtPlan) {
        ttSnapshot?.let { TtCalendar.plan(it, currentTime, zone) } ?: initialTtPlan
    }
    val noticesState = loads.notices?.state
    val noticesData = if (noticesState != null) noticesState.data else initialNoticesData
    val marksState = loads.marks?.state
    val noticesLoading = noticesState?.loading == true || marksState?.loading == true
    val noticesError = (noticesState?.error ?: marksState?.error).takeUnless { noticesLoading }
    val noticeStatusLabel = if (noticesState?.error == null && marksState?.error != null) "通知处理状态" else "通知"
    val powerState = loads.power?.state
    val powerRow = powerState?.data ?: initialPowerRow.takeIf { effectivePowerSel == initialPowerSel?.trim() }

    // ---- 卡片摘要与可定位条目 ----------------------------------------------------
    val personal = taskRepository?.snapshot?.collectAsState()?.value
    val taskState = loads.tasks?.state
    val homeworkState = loads.homework?.state
    val hwItems = remember(personal, today) {
        personal?.pendingTasks().orEmpty().sortedWith(taskPriorityOrder(today.toString())).map {
            HomeRotationItem(it.text("id"), it.text("title"), taskDueLabel(it, today.toString()).ifBlank { "未设置截止" })
        }
    }
    val hiddenNotices = (sections["hide"] as? JsonObject)?.get("ntHide") as? JsonObject
    val ntItems = remember(noticesData, marks, currentTime, hiddenNotices) {
        val hiddenIds = hiddenNotices.orEmpty().filterValues { (it as? JsonPrimitive)?.booleanOrNull == true }.keys
        HomeWorkbenchLogic.noticeRotation(noticesData?.auto.orEmpty(), marks, currentTime, hiddenIds)
    }
    val noticeBadge = !noticesLoading && noticesError == null && ntItems.isNotEmpty()
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

    val reduceMotion = rememberReduceMotion()

    val editor = editSession ?: rememberPreviewHomeToolsEditSession(0, quickToolIds, onSaved = onHomeToolsChange)
    val scrollState = rememberScrollState()
    var offsetBeforeOpening by rememberSaveable(session.sid) { mutableIntStateOf(-1) }
    var offsetToRestore by remember(scrollState) { mutableIntStateOf(offsetBeforeOpening) }
    val cardsLoading = ttLoading || noticesLoading || taskState?.loading == true ||
        homeworkState?.loading == true || powerState?.loading == true
    val layoutReady by rememberUpdatedState(pageActive && !cardsLoading)
    LaunchedEffect(scrollState) {
        if (offsetToRestore < 0) return@LaunchedEffect
        snapshotFlow { layoutReady || scrollState.isScrollInProgress }.first { it }
        if (!scrollState.isScrollInProgress) {
            withFrameNanos { }
            scrollState.scrollTo(offsetToRestore)
        }
        offsetToRestore = -1
        offsetBeforeOpening = -1
    }

    fun openToolId(id: String) {
        ToolCatalog.byId(id)?.let(onOpenTool)
    }

    fun openCard(tool: String, focus: String? = null) {
        offsetBeforeOpening = scrollState.value
        if (onCardOpen != null) onCardOpen(tool, focus) else openToolId(if (tool == "class-homework") "hw" else tool)
    }

    // 预览渲染（android.exe preview / Android Studio）不播放首屏错峰动画，避免首帧全透明。
    val animateEntrance = !reduceMotion && !LocalInspectionMode.current

    // 2026-11-20 页面骨架 v2：滚动内容 + 常驻悬浮玻璃条（渐变模糊）。
    // haze 源 = 滚动内容（只有它会从悬浮条下面经过），悬浮条自己做真模糊。
    val hazeState = rememberHazeState()
    val density = LocalDensity.current
    val stickyProgress = ctStickyHeaderProgress(scrollState.value.toFloat(), density.density)
    val contextCourse = remember(ttPlan, timetableState, currentTime, today, zone) {
        homeCourseContext(ttPlan, timetableState, currentTime, today, zone)
    }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
                PullToRefreshBox(
                    isRefreshing = loads.isRefreshing,
                    onRefresh = loads::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        val contentWidth = maxWidth.value.toInt()
                        val layout = remember(contentWidth) { CtAdaptive.specFor(contentWidth) }
                        // 刷新时保留已滚过的空间，避免卡片暂时变短后截掉返回位置。
                        val reservedOffset = if (cardsLoading) maxOf(offsetToRestore, scrollState.value) else offsetToRestore
                        val minContentHeight = if (reservedOffset > 0) {
                            maxHeight + with(density) { reservedOffset.toDp() }
                        } else {
                            0.dp
                        }
                        Column(
                            modifier = Modifier
                                .widthIn(max = layout.contentMaxWidth)
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = layout.horizontalPadding)
                                // 顶部/底部渐隐：内容滑到标题栏/浮岛附近时逐像素淡出（官方参考的顶部悬浮行为）。
                                .ctRootPageEdgeFade(scrollState.value.toFloat())
                                .verticalScroll(scrollState)
                                .heightIn(min = minContentHeight)
                                .padding(top = com.furina.classtools.ui.theme.HarmonyTokens.BreathTop, bottom = if (spec.widthDp >= CtAdaptive.MEDIUM_MIN_WIDTH_DP) 24.dp else 120.dp),
                            verticalArrangement = Arrangement.spacedBy(CtTokens.Spacing.cardGapCompact),
                        ) {
                            HomeReveal(enabled = animateEntrance, index = 0) {
                                CtRootHeader(
                                    title = "工作台",
                                    collapse = ctHeaderCollapseProgress(scrollState.value.toFloat()),
                                    actions = {
                                        CtGlassIconButton(
                                            icon = Icons.Rounded.QrCodeScanner,
                                            contentDescription = "扫一扫",
                                            onClick = onScan,
                                        )
                                        CtGlassIconButton(
                                            icon = Icons.Rounded.Notifications,
                                            contentDescription = "通知待处理",
                                            badge = noticeBadge,
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
                            HomeContextCard(
                                session = session,
                                course = contextCourse,
                                now = currentTime,
                                zone = zone,
                                active = pageActive,
                                reduceMotion = reduceMotion,
                            )
                            HomeReveal(enabled = animateEntrance, index = 1) {
                                HomeTodayCoursesCard(
                                    plan = ttPlan,
                                    nowMillis = currentTime,
                                    loading = ttLoading,
                                    error = timetableState?.error,
                                    context = contextCourse,
                                    onRetry = { loads.retry(loads.timetable) },
                                    onOpen = {
                                        val tomorrow = contextCourse.focus.takeIf { contextCourse.action == "查看明日课表" }
                                        openCard("tt", (tomorrow ?: CourseFocus(today.toString())).encode())
                                    },
                                    onCourse = { openCard("tt", CourseFocus.of(it, zone).encode()) },
                                )
                            }
                            HomeActivities(session, currentTime)
                            HomeWorkbenchColumns(layout, updates = {
                                HomeReveal(enabled = animateEntrance, index = 2) {
                                    HomeNoticesCard(
                                        items = ntItems,
                                        loading = noticesLoading,
                                        error = noticesError,
                                        hasData = noticesData != null,
                                        usingCache = noticesState?.error != null && noticesData != null,
                                        statusLabel = noticeStatusLabel,
                                        updatedText = noticesUpdatedText,
                                        pageActive = pageActive,
                                        animate = animateEntrance,
                                        onOpen = { openCard("nt") },
                                        onItem = { openCard("nt", it.id) },
                                        onRetry = loads::retryNotices,
                                    )
                                }
                                HomeReveal(enabled = animateEntrance, index = 3) {
                                    HomeHomeworkCard(
                                        items = hwItems,
                                        task = taskState,
                                        homework = homeworkState,
                                        onOpen = { openCard("hw") },
                                        onItem = { openCard("hw", it.id) },
                                        onRetryTasks = { loads.retry(loads.tasks) },
                                        onRetryHomework = { loads.retry(loads.homework, loads.marks) },
                                    )
                                }
                                if (showNotificationPermissionBanner && onOpenPermissions != null) {
                                    HomeReveal(enabled = animateEntrance, index = 4) {
                                        NotificationPermissionBanner(
                                            visible = true,
                                            onOpenPermissions = onOpenPermissions,
                                        )
                                    }
                                }
                            }, utilities = {
                                HomeReveal(enabled = animateEntrance, index = 5) {
                                    HomePowerCard(
                                        card = powerCard,
                                        loading = powerState?.loading == true,
                                        error = powerState?.error,
                                        hasCache = powerRow != null,
                                        onRetry = { loads.retry(loads.power) },
                                        onOpen = {
                                            if (powerCard == null && onOpenPowerPicker != null) {
                                                onOpenPowerPicker()
                                            } else {
                                                openToolId("power")
                                            }
                                        },
                                    )
                                }
                            }, tools = {
                                HomeReveal(enabled = animateEntrance, index = 6) {
                                    HomeQuickToolsSection(
                                        editor = editor,
                                        quickToolIds = if (forceEmptyQuickTools) emptyList() else quickToolIds,
                                        isPageActive = pageActive,
                                        reduceMotion = reduceMotion,
                                        onOpenTool = onOpenTool,
                                        onOpenAll = onOpenAll,
                                    )
                                }
                            })
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
                        badge = noticeBadge,
                        onClick = { openToolId("nt") },
                    )
                },
            )
        }
    }
}

@Composable
private fun HomeWorkbenchColumns(
    layout: CtAdaptiveSpec,
    updates: @Composable () -> Unit,
    utilities: @Composable () -> Unit,
    tools: @Composable () -> Unit,
) {
    if (CtAdaptive.supportsWorkbenchTwoPane(layout)) {
        val gap = Arrangement.spacedBy(CtTokens.Spacing.cardGapCompact)
        CtTwoPaneRow(layout, main = { modifier ->
            Column(modifier, verticalArrangement = gap) {
                updates()
                tools()
            }
        }, side = { modifier ->
            Column(modifier, verticalArrangement = gap) { utilities() }
        })
    } else {
        updates()
        utilities()
        tools()
    }
}

// =============================================================================
// 今日课程卡（v5 玻璃卡 + 分类色图标 + 课程时间线）
// =============================================================================

@Composable
internal fun HomeTodayCoursesCard(
    plan: TtPlan?,
    nowMillis: Long,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onCourse: (SessionInfo) -> Unit,
    onOpen: () -> Unit,
    context: HomeCourseContext? = null,
) {
    val todayCourses = plan?.today.orEmpty()
    val todayHighlight = todayCourses.firstOrNull { it.state == SessionState.ONGOING }
        ?: todayCourses.firstOrNull { it.state == SessionState.NEXT }
    val highlightedCourse = todayHighlight ?: plan?.next
    val hasData = plan?.hasData == true
    HomeInfoCard(
        icon = Icons.Rounded.CalendarMonth,
        title = "今日课程",
        accent = MaterialTheme.colorScheme.secondary,
        onClick = onOpen,
        badge = if (hasData) "${todayCourses.size} 节" else null,
        trailing = {
            Text(
                text = if (context?.action == "查看明日课表") "查看明日课表 ›" else "课表 ›",
                style = CtText.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        when {
            loading && !hasData -> HomeLoadingRow("读取课表…")
            !hasData -> {
                val state = HomeWorkbenchLogic.heroState(plan, loading, nowMillis)
                HomeEmptyHint(context?.status ?: state.title, state.detail, actionText = "打开课表", onAction = onOpen)
            }
            else -> {
                if (context?.cached == true && error == null && context.status != null) {
                    Text(context.status, style = CtText.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (todayCourses.isEmpty() || plan.todayState == TodayState.DONE) {
                    val state = HomeWorkbenchLogic.heroState(plan, loading, nowMillis)
                    HomeEmptyHint(state.title, sub = if (highlightedCourse == null) state.detail else "")
                } else if (context?.status == "课间" || context?.status == "即将上课") {
                    Text(context.status, style = CtText.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (highlightedCourse != null) {
                    HomeCourseHighlight(highlightedCourse, nowMillis) { onCourse(highlightedCourse) }
                }
                val remaining = todayCourses.filterNot { it === todayHighlight }
                if (remaining.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        remaining.forEachIndexed { index, sessionInfo ->
                            if (highlightedCourse != null || index > 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            CourseRow(
                                sessionInfo,
                                nowMillis,
                                isNext = plan.next?.let { it.dayOffset == 0 && it.startAt == sessionInfo.startAt } == true &&
                                    sessionInfo.state == SessionState.NEXT,
                                onClick = { onCourse(sessionInfo) },
                            )
                        }
                    }
                }
            }
        }
        HomeModuleStatus("课表", loading && hasData, error, hasData, onRetry)
    }
}

/** 单节课程行：时间、课程名、节次、教室和实时状态。 */
@Composable
private fun CourseRow(sessionInfo: SessionInfo, nowMillis: Long, isNext: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick).heightIn(min = 48.dp).padding(vertical = 6.dp),
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
                text = listOf(sessionInfo.span, sessionInfo.fullRoom)
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
private fun HomePowerCard(
    card: HomePowerCard?,
    loading: Boolean,
    error: String?,
    hasCache: Boolean,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
) {
    HomeInfoCard(
        icon = Icons.Rounded.Bolt,
        title = "宿舍电费",
        accent = stateColor(card?.stateKey.orEmpty()),
        onClick = onOpen,
        badge = if (card != null && card.hasReading) card.stateText else null,
    ) {
        when {
            card == null -> HomeEmptyHint(
                title = "关注宿舍后显示剩余电量",
                sub = "去选择关注宿舍 ›",
            )

            else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${card.qText} ${card.unit}", style = MaterialTheme.typography.headlineMedium,
                    color = stateColor(card.stateKey))
                val update = when {
                    loading && !card.hasReading -> "读取电量…"
                    error != null && !card.hasReading -> "读数不可用"
                    !card.hasReading -> "暂缺读数，点开刷新"
                    card.updText.isNotBlank() -> "更新 ${card.updText}"
                    else -> ""
                }
                Text(listOf(card.label, update).filter(String::isNotBlank).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HomeModuleStatus("电费", loading && hasCache, error, hasCache, onRetry)
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
// 待办、班级作业与通知
// =============================================================================

@Composable
private fun HomeHomeworkCard(
    items: List<HomeRotationItem>,
    task: HomeLoadState<Unit>?,
    homework: HomeLoadState<*>?,
    onOpen: () -> Unit,
    onItem: (HomeRotationItem) -> Unit,
    onRetryTasks: () -> Unit,
    onRetryHomework: () -> Unit,
) {
    HomeInfoCard(Icons.AutoMirrored.Rounded.Assignment, "待办任务", MaterialTheme.colorScheme.primary, onOpen,
        badge = "${items.size} 项") {
        if (items.isEmpty()) {
            HomeMutedLine("暂无待完成事项")
        } else {
            items.take(2).forEach { item ->
                key(item.id) {
                    CardItemRow(item, Modifier.clickable { onItem(item) })
                }
            }
        }
        HomeModuleStatus("个人待办", loading = false, task?.error, task?.data != null, onRetryTasks)
        HomeModuleStatus(
            label = "班级作业",
            loading = homework?.loading == true && homework.data == null && homework.error == null,
            error = homework?.error,
            hasCache = homework?.data != null,
            onRetry = onRetryHomework,
            cacheMessage = "已保留上次作业数据",
        )
    }
}

@Composable
private fun HomeNoticesCard(
    items: List<HomeRotationItem>,
    loading: Boolean,
    error: String?,
    hasData: Boolean,
    usingCache: Boolean,
    statusLabel: String,
    updatedText: String,
    pageActive: Boolean,
    animate: Boolean,
    onOpen: () -> Unit,
    onItem: (HomeRotationItem) -> Unit,
    onRetry: () -> Unit,
) {
    HomeInfoCard(Icons.Rounded.Notifications, "班级通知", MaterialTheme.colorScheme.primary, onOpen,
        badge = if (!loading && error == null) "${items.size} 待处理" else null) {
        HomeRotatingItems(items, pageActive, animate, onItem)
        when {
            loading && !hasData -> HomeLoadingRow("同步通知…")
            !loading && error == null && items.isEmpty() -> HomeMutedLine("暂无待处理通知")
        }
        HomeModuleStatus(
            statusLabel,
            loading && hasData,
            error,
            usingCache,
            onRetry,
            cacheTime = updatedText,
        )
    }
}

@Composable
private fun HomeModuleStatus(
    label: String,
    loading: Boolean,
    error: String?,
    hasCache: Boolean,
    onRetry: () -> Unit,
    cacheTime: String = "",
    cacheMessage: String = "显示本机缓存",
) {
    when {
        loading -> HomeRefreshingRow("更新$label…")
        error != null -> {
            val cacheInfo = if (hasCache) listOf(cacheMessage, cacheTime).filter(String::isNotBlank) else emptyList()
            val text = (listOf("${label}未更新", error) + cacheInfo).joinToString(" · ")
            Text(text, style = CtText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            TextButton(onClick = onRetry) { Text("重试$label") }
        }
    }
}

@Composable
private fun HomeRotatingItems(
    items: List<HomeRotationItem>,
    pageActive: Boolean,
    animate: Boolean,
    onItem: (HomeRotationItem) -> Unit,
) {
    if (!animate || items.size < 2) {
        items.take(2).forEach { item ->
            CardItemRow(item, Modifier.clickable { onItem(item) })
        }
        return
    }

    val ids = remember(items) { items.map { it.id } }
    var selectedId by remember { mutableStateOf(ids.first()) }
    val index = ids.indexOf(selectedId).coerceAtLeast(0)
    val current = items[index]
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val canRotate = HomeWorkbenchLogic.rotationEnabled(items.size, pageActive) && !pressed && !focused
    LaunchedEffect(ids, index, canRotate) {
        if (canRotate) {
            delay(HomeWorkbenchLogic.ROTATION_INTERVAL_MS)
            selectedId = ids[HomeWorkbenchLogic.nextRotationIndex(index, ids.size)]
        }
    }

    AnimatedContent(
        targetState = current,
        contentKey = { it.id },
        modifier = Modifier.fillMaxWidth().clipToBounds(),
        transitionSpec = {
            slideInVertically(CtMotion.pageTweenInt) { it } togetherWith
                slideOutVertically(CtMotion.pageTweenInt) { -it }
        },
        label = "工作台条目轮播",
    ) { item ->
        val accessibility = if (item.id == current.id) Modifier else Modifier.semantics { hideFromAccessibility() }
        CardItemRow(item, accessibility.clickable(interaction, LocalIndication.current) { onItem(item) })
    }
}

@Composable
private fun CardItemRow(item: HomeRotationItem, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(item.title, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(item.sub, style = CtText.caption, color = scheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// =============================================================================
// 常用工具（v5：四图标快捷行 + 编辑网格）
// =============================================================================

@Composable
private fun HomeQuickToolsSection(
    editor: HomeToolsEditSession,
    quickToolIds: List<String>,
    isPageActive: Boolean,
    reduceMotion: Boolean,
    onOpenTool: (AppTool) -> Unit,
    onOpenAll: () -> Unit,
) {
    val editing = editor.isEditing(0)
    val ids = HomeToolsEditor.availableIds(quickToolIds)
    Column(verticalArrangement = Arrangement.spacedBy(CtTokens.Spacing.cardGapCompact)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "常用工具",
                style = CtText.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (editing) {
                TextButton(onClick = editor::discard, enabled = editor.canEdit) {
                    Text("取消", style = CtText.label)
                }
                TextButton(onClick = editor::save, enabled = editor.canEdit) {
                    Text(if (editor.saving) "保存中" else "完成", style = CtText.label)
                }
            } else {
                TextButton(onClick = { editor.begin(0, quickToolIds) }) {
                    Text("编辑", style = CtText.label)
                }
            }
            TextButton(onClick = onOpenAll, enabled = !editor.saving) {
                Text("全部 ›", style = CtText.label)
            }
        }

        HomeQuickToolsRow(editor, 0, quickToolIds, isPageActive, reduceMotion, onOpenTool)
        if (editing) {
            HomeToolsEditHint(editor, quickToolIds)
            HomeEditableToolGrid(
                tools = HomeToolsEditor.availableTools,
                columns = 4,
                draft = editor.draft?.ids.orEmpty(),
                enabled = editor.canEdit,
                reduceMotion = reduceMotion,
                onToggle = { editor.toggle(0, it) },
            )
        } else if (ids.isEmpty()) {
            CtGlassCard(onClick = onOpenAll, shape = HomeCardShape) {
                Row(
                    modifier = Modifier.padding(CtTokens.Spacing.cardPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "尚未勾选常用工具",
                            style = CtText.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "在全部工具页选择常用工具（最多 4 个）",
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
        }
    }
}
/** 编辑网格：2/3/4 列（随窗口），点击加入/移出逻辑由 [onToggle] 保持原样。 */
@Composable
private fun HomeEditableToolGrid(
    tools: List<AppTool>,
    columns: Int,
    draft: List<String>,
    enabled: Boolean,
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
                val isSelected = tool.id in draft
                EditableToolTile(
                    tool = tool,
                    selected = isSelected,
                    onClick = { onToggle(tool.id) },
                    modifier = Modifier.weight(1f).semantics {
                        selected = isSelected
                        if (!enabled) disabled()
                    },
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





