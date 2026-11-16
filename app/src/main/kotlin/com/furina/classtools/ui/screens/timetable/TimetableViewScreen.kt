@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.furina.classtools.ui.screens.timetable

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furina.classtools.data.timetable.Course
import com.furina.classtools.data.timetable.SessionInfo
import com.furina.classtools.data.timetable.SessionState
import com.furina.classtools.data.timetable.TimetableLoginRepository
import com.furina.classtools.data.timetable.TimetableRepository
import com.furina.classtools.data.timetable.TtCalendar
import com.furina.classtools.data.timetable.TtFailureKind
import com.furina.classtools.data.timetable.TtLoadResult
import com.furina.classtools.data.timetable.TtPeriods
import com.furina.classtools.data.timetable.TtPlan
import com.furina.classtools.data.timetable.TtSnapshot
import com.furina.classtools.data.timetable.TtSource
import com.furina.classtools.features.export.ExportCardSpec
import com.furina.classtools.ui.adaptive.CtAdaptiveSpec
import com.furina.classtools.ui.adaptive.rememberCtAdaptiveSpec
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.CtTopBar
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.expressive.CtBottomSheet
import com.furina.classtools.ui.components.expressive.CtBottomSheetFooter
import com.furina.classtools.ui.components.expressive.CtBottomSheetHeader
import com.furina.classtools.ui.components.expressive.CtSegmentOption
import com.furina.classtools.ui.components.expressive.CtSegmentedButtons
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.LocalCtDarkTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 课表视图模式（周/日只由按钮切换，不参与左右滑动）。 */
enum class TimetableViewMode { WEEK, DAY }

/**
 * 课表页 v5（参考 HFUT-Schedule / xiaoying 的马卡龙课表重做；详见 `docs/design/UI_REFERENCE_20261113.md`）。
 *
 * 视觉：
 * - 马卡龙 16 色课程块（浅/深两套、对比度 ≥4.5；见 [TimetablePalette]）；
 * - 日期头两行（星期 + 日号），今日整列柔和强调，周末弱化；
 * - 顶部「周次胶囊 + 切换视图 + 导出/设置」；块内「时间 → 课名 → 地点」；
 * - 「现在 HH:mm」虚线 + 进行中课程进度条；下一节摘要条；
 * - 课程详情 Bottom Sheet（spring 进入 + 拖拽把手）、首次/切周课程块错峰入场、按下缩放。
 *
 * 功能（不回归）：
 * - 左右滑动切周（跟手/边缘阻尼/提交阈值/点击抑制）、上下周按钮、「本周」；
 * - 周/日视图切换（按钮）与日视图日期选择；周末空列隐藏；今日高亮；全部课程折叠卡；
 * - 导出课表图片 `onExportTimetable`、登录教务/个人课表/清除个人课表、公共/缓存来源标识；
 * - 平板 ≥800dp 两栏（右栏固定导出卡）、大字体 1.3x、空/加载/错误态；
 * - 课程详情「写日程」入口。
 *
 * 接线（Lead 在 MainNavHost）：
 * ```kotlin
 * TimetableViewScreen(
 *     onBack = { navController.popBackStack() },
 *     repository = container.timetableRepository,
 *     loginRepository = container.timetableLoginRepository,
 *     sessionSid = session.sid,
 *     onAddToCalendar = { course, week, termStart -> calendarWriter.writeCourse(course, week, termStart) },
 *     onExportTimetable = { spec -> launchExport(spec) },
 * )
 * ```
 */
@Composable
fun TimetableViewScreen(
    onBack: () -> Unit,
    onOpenMap: (() -> Unit)? = null,
    repository: TimetableRepository,
    modifier: Modifier = Modifier,
    initialSnapshot: TtSnapshot? = null,
    initialError: String? = null,
    initialErrorKind: TtFailureKind? = null,
    initialDayView: Boolean = false,
    initialWeekOffset: Int = 0,
    initialSwipeOffsetPx: Float = 0f,
    skipLoad: Boolean = false,
    nowMillis: Long = System.currentTimeMillis(),
    loginRepository: TimetableLoginRepository? = null,
    sessionSid: String = "",
    onAddToCalendar: ((Course, Int, LocalDate) -> Unit)? = null,
    onExportTimetable: ((ExportCardSpec) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val spec = rememberCtAdaptiveSpec()
    val dark = LocalCtDarkTheme.current
    val zone = remember { ZoneId.systemDefault() }

    var phase by remember(skipLoad, initialError) {
        mutableStateOf(
            when {
                initialError != null -> TtPhase.ERROR
                initialSnapshot != null -> TtPhase.READY
                skipLoad -> TtPhase.READY
                else -> TtPhase.LOADING
            },
        )
    }
    var snapshot by remember(initialSnapshot) { mutableStateOf(initialSnapshot ?: TtSnapshot()) }
    var fromCache by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf(TtSource.PUBLIC) }
    var errorMessage by remember(initialError) { mutableStateOf(initialError.orEmpty()) }
    var errorKind by remember(initialErrorKind) { mutableStateOf(initialErrorKind) }
    var loadToken by remember { mutableIntStateOf(0) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showWeekPicker by remember { mutableStateOf(false) }
    // 课表接入（WakeUp / ICS / 系统日历原生提醒）：独立弹层，见 TimetableLinkSheet.kt
    var showLinkSheet by remember { mutableStateOf(false) }

    LaunchedEffect(loadToken, refreshToken) {
        if (skipLoad) return@LaunchedEffect
        phase = TtPhase.LOADING
        when (val result = repository.load(force = refreshToken > 0)) {
            is TtLoadResult.Success -> {
                snapshot = result.snapshot
                fromCache = result.fromCache
                source = result.source
                errorMessage = ""
                phase = TtPhase.READY
            }

            is TtLoadResult.Failure -> {
                errorMessage = result.message
                errorKind = result.kind
                phase = TtPhase.ERROR
            }
        }
    }

    val plan = remember(snapshot, nowMillis) {
        if (snapshot.c.isEmpty()) null else TtCalendar.plan(snapshot, nowMillis, zone)
    }
    val termStart = remember(snapshot) { TtCalendar.parseTermStart(snapshot.termStart) }
    val today = remember(nowMillis) { Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate() }
    val todayIndex = remember(today) { TtCalendar.dayIndex(today) }
    val maxWeek = remember(snapshot) { TimetableWeekLogic.maxWeekOf(snapshot.c) }
    val currentWeek = plan?.week

    var weekOffset by remember { mutableIntStateOf(initialWeekOffset) }
    var viewMode by remember { mutableStateOf(if (initialDayView) TimetableViewMode.DAY else TimetableViewMode.WEEK) }
    var selectedDay by remember { mutableIntStateOf(todayIndex) }
    var selectedCourse by remember { mutableStateOf<Course?>(null) }

    val displayWeek = remember(currentWeek, weekOffset, maxWeek) {
        TimetableWeekLogic.displayWeek(currentWeek, weekOffset, maxWeek)
    }
    val monday = remember(termStart, displayWeek, today, todayIndex) {
        displayWeek?.let { TtCalendar.mondayOf(termStart, it) } ?: today.minusDays(todayIndex.toLong())
    }

    // 滑动切周状态
    var swipeOffset by remember { mutableFloatStateOf(initialSwipeOffsetPx) }
    var weekTransitioning by remember { mutableStateOf(false) }
    var suppressClickUntil by remember { mutableLongStateOf(0L) }
    var gridWidthPx by remember { mutableFloatStateOf(0f) }

    fun reload() {
        refreshToken += 1
    }

    /** 周切换动画：跟随拖拽 → 飞出 150ms → 切周 → 飞入 230ms（与既有 web/mp 参数一致）。 */
    fun animateWeekFlip(direction: TimetableSwipe.Direction, applyChange: () -> Unit) {
        if (weekTransitioning || direction == TimetableSwipe.Direction.NONE) return
        weekTransitioning = true
        scope.launch {
            val width = gridWidthPx.coerceAtLeast(1f)
            animate(
                initialValue = swipeOffset,
                targetValue = -direction.delta * width,
                animationSpec = tween(durationMillis = TimetableSwipe.FLY_OUT_MS),
            ) { value, _ -> swipeOffset = value }
            applyChange()
            swipeOffset = direction.delta * width
            animate(
                initialValue = swipeOffset,
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = TimetableSwipe.FLY_IN_MS,
                    easing = CubicBezierEasing(0.22f, 0.8f, 0.3f, 1f),
                ),
            ) { value, _ -> swipeOffset = value }
            weekTransitioning = false
            suppressClickUntil = System.currentTimeMillis() + TimetableSwipe.TAP_GUARD_MS
        }
    }

    fun goWeek(direction: TimetableSwipe.Direction) {
        val week = displayWeek ?: return
        val target = TimetableWeekLogic.navigate(week, direction, maxWeek) ?: return
        animateWeekFlip(direction) {
            weekOffset = target - (currentWeek ?: return@animateWeekFlip)
        }
    }

    fun goThisWeek(animate: Boolean = true) {
        if (!TimetableWeekLogic.isThisWeek(weekOffset)) {
            val direction = if (weekOffset > 0) TimetableSwipe.Direction.PREV else TimetableSwipe.Direction.NEXT
            if (animate) animateWeekFlip(direction) { weekOffset = 0 } else weekOffset = 0
        }
    }

    /** 周选择器：直接跳到目标教学周（无动画，避免跨多周飞行）。 */
    fun goToWeek(target: Int) {
        if (weekTransitioning) return
        val current = currentWeek ?: return
        weekOffset = (target - current).coerceIn(1 - current, maxWeek - current)
    }

    fun exportTimetable() {
        val export = onExportTimetable ?: return
        val exportSpec = TimetableExport.buildSpec(
            snapshot = snapshot,
            termStart = termStart,
            week = displayWeek,
            nowMillis = nowMillis,
        )
        if (exportSpec == null) {
            scope.launch { snackbar.showSnackbar("当前周次无课，暂无可导出的内容") }
        } else {
            export(exportSpec)
        }
    }

    fun clearPersonal() {
        scope.launch {
            runCatching { repository.clearPersonal() }
            loadToken += 1
            snackbar.showSnackbar("已清除个人课表，回退公共课表")
        }
    }

    val ongoing = plan?.today?.firstOrNull { it.state == SessionState.ONGOING }
    val nowIndicator = remember(nowMillis, displayWeek, currentWeek) {
        if (displayWeek == currentWeek) TimetableNowLogic.indicator(nowMillis, zone) as? TimetableNowState.Visible else null
    }
    val ongoingProgress = ongoing?.let { session ->
        TimetableNowLogic.progress(session.startAt, session.endAt, nowMillis)
    }

    val twoPane = TimetableLayout.twoPaneFor(spec.widthDp, spec.twoPane)
    val gridContext = TimetableGridContext(
        snapshot = snapshot,
        termStart = termStart,
        displayWeek = displayWeek,
        monday = monday,
        todayIndex = todayIndex,
        dark = dark,
        nowMillis = nowMillis,
        wide = twoPane,
        now = nowIndicator,
        ongoingName = ongoing?.name,
        ongoingProgress = ongoingProgress,
        onCourseClick = { course ->
            if (System.currentTimeMillis() >= suppressClickUntil) selectedCourse = course
        },
        onSelectDay = { day ->
            selectedDay = day
            viewMode = TimetableViewMode.DAY
        },
    )

    val rangeText = TimetableWeekLogic.weekRangeLabel(termStart, displayWeek)
    val sourceLabel = buildString {
        displayWeek?.let { append("第 $it 周") }
        when {
            source == TtSource.PERSONAL -> append(" · 个人课表")
            fromCache -> append(" · 本机缓存")
            else -> append(" · 公共课表")
        }
        if (rangeText.isNotBlank()) {
            append(" · ")
            append(rangeText)
        }
    }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = spec.contentMaxWidth)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .statusBarsPadding(),
            ) {
                CtTopBar(
                    title = "课表",
                    subtitle = sourceLabel,
                    onBack = onBack,
                    actions = {
                        if (onOpenMap != null) {
                            TextButton(onClick = onOpenMap) { Text("地图") }
                        }
                        if (source == TtSource.PERSONAL) {
                            CtPill(
                                text = "个人",
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        } else if (fromCache) {
                            CtPill(text = "缓存")
                        }
                        IconButton(onClick = { if (!skipLoad) reload() }) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "刷新课表",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )

                if (source == TtSource.PERSONAL) {
                    PersonalTimetableBanner(onClear = { clearPersonal() })
                }

                when (phase) {
                    TtPhase.LOADING -> TimetableLoadingState()

                    TtPhase.ERROR -> TimetableErrorState(
                        message = errorMessage,
                        kind = errorKind,
                        onRetry = { loadToken += 1 },
                    )

                    TtPhase.READY -> {
                        if (snapshot.c.isEmpty()) {
                            TimetableEmptyState(
                                onRefresh = { if (!skipLoad) reload() },
                                onLogin = if (loginRepository != null) ({ showLoginDialog = true }) else null,
                            )
                        } else if (twoPane) {
                            TabletBodyV5(
                                spec = spec,
                                grid = gridContext,
                                plan = plan,
                                nowMillis = nowMillis,
                                maxWeek = maxWeek,
                                canGoPrev = displayWeek != null && displayWeek > TimetableWeekLogic.MIN_WEEK,
                                canGoNext = displayWeek != null && displayWeek < maxWeek,
                                showThisWeek = !TimetableWeekLogic.isThisWeek(weekOffset),
                                viewMode = viewMode,
                                selectedDay = selectedDay,
                                swipeOffset = swipeOffset,
                                onPrev = { goWeek(TimetableSwipe.Direction.PREV) },
                                onNext = { goWeek(TimetableSwipe.Direction.NEXT) },
                                onThisWeek = { goThisWeek() },
                                onViewMode = { viewMode = it },
                                onSelectDay = { selectedDay = it },
                                onOpenWeekPicker = { showWeekPicker = true },
                                onOpenSettings = { showSettingsSheet = true },
                                onExport = if (onExportTimetable != null) ({ exportTimetable() }) else null,
                                swipeModifier = Modifier.timetableSwipe(
                                    displayWeek = displayWeek,
                                    maxWeek = maxWeek,
                                    enabled = !weekTransitioning,
                                    onOffset = { swipeOffset = it },
                                    onCommit = { dx, dy ->
                                        val direction = if (dx < 0) TimetableSwipe.Direction.NEXT else TimetableSwipe.Direction.PREV
                                        if (TimetableSwipe.shouldCommit(dx, dy)) {
                                            suppressClickUntil = System.currentTimeMillis() + TimetableSwipe.TAP_GUARD_MS
                                            goWeek(direction)
                                        } else {
                                            scope.launch {
                                                animate(
                                                    initialValue = swipeOffset,
                                                    targetValue = 0f,
                                                    animationSpec = CtMotion.spatial,
                                                ) { value, _ -> swipeOffset = value }
                                            }
                                        }
                                    },
                                    onWidth = { gridWidthPx = it },
                                ),
                            )
                        } else {
                            CompactBodyV5(
                                spec = spec,
                                grid = gridContext,
                                plan = plan,
                                nowMillis = nowMillis,
                                maxWeek = maxWeek,
                                canGoPrev = displayWeek != null && displayWeek > TimetableWeekLogic.MIN_WEEK,
                                canGoNext = displayWeek != null && displayWeek < maxWeek,
                                showThisWeek = !TimetableWeekLogic.isThisWeek(weekOffset),
                                viewMode = viewMode,
                                selectedDay = selectedDay,
                                swipeOffset = swipeOffset,
                                onPrev = { goWeek(TimetableSwipe.Direction.PREV) },
                                onNext = { goWeek(TimetableSwipe.Direction.NEXT) },
                                onThisWeek = { goThisWeek() },
                                onViewMode = { viewMode = it },
                                onSelectDay = { selectedDay = it },
                                onOpenWeekPicker = { showWeekPicker = true },
                                onOpenSettings = { showSettingsSheet = true },
                                onExport = if (onExportTimetable != null) ({ exportTimetable() }) else null,
                                swipeModifier = Modifier.timetableSwipe(
                                    displayWeek = displayWeek,
                                    maxWeek = maxWeek,
                                    enabled = !weekTransitioning,
                                    onOffset = { swipeOffset = it },
                                    onCommit = { dx, dy ->
                                        val direction = if (dx < 0) TimetableSwipe.Direction.NEXT else TimetableSwipe.Direction.PREV
                                        if (TimetableSwipe.shouldCommit(dx, dy)) {
                                            suppressClickUntil = System.currentTimeMillis() + TimetableSwipe.TAP_GUARD_MS
                                            goWeek(direction)
                                        } else {
                                            scope.launch {
                                                animate(
                                                    initialValue = swipeOffset,
                                                    targetValue = 0f,
                                                    animationSpec = CtMotion.spatial,
                                                ) { value, _ -> swipeOffset = value }
                                            }
                                        }
                                    },
                                    onWidth = { gridWidthPx = it },
                                ),
                            )
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

            // 课表接入入口（悬浮按钮）：导出 .ics / WakeUp 备份、写入系统日历并提醒
            if (phase == TtPhase.READY && snapshot.c.isNotEmpty()) {
                TimetableLinkFab(
                    onClick = { showLinkSheet = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 16.dp, bottom = 16.dp),
                )
            }
        }

        selectedCourse?.let { course ->
            CtBottomSheet(onDismissRequest = { selectedCourse = null }) {
                TimetableCourseDetailContent(
                    course = course,
                    displayWeek = displayWeek,
                    termStart = termStart,
                    onAddToCalendar = onAddToCalendar,
                )
            }
        }

        if (showSettingsSheet) {
            TimetableSettingsSheet(
                sourceLabel = sourceLabel,
                canExport = onExportTimetable != null && snapshot.c.isNotEmpty(),
                canLogin = loginRepository != null,
                isPersonal = source == TtSource.PERSONAL,
                onDismiss = { showSettingsSheet = false },
                onExport = {
                    showSettingsSheet = false
                    exportTimetable()
                },
                onLogin = {
                    showSettingsSheet = false
                    showLoginDialog = true
                },
                onClearPersonal = {
                    showSettingsSheet = false
                    clearPersonal()
                },
                onRefresh = {
                    showSettingsSheet = false
                    if (!skipLoad) reload()
                },
            )
        }

        if (showWeekPicker) {
            WeekPickerSheet(
                displayWeek = displayWeek,
                maxWeek = maxWeek,
                onPick = { week ->
                    showWeekPicker = false
                    goToWeek(week)
                },
                onThisWeek = {
                    showWeekPicker = false
                    goThisWeek(animate = false)
                },
                onDismiss = { showWeekPicker = false },
            )
        }

        // 课表接入（WakeUp 备份 / .ics / 系统日历原生写入+提醒）
        if (showLinkSheet) {
            TimetableLinkSheet(
                snapshot = snapshot,
                termStart = termStart,
                onDismiss = { showLinkSheet = false },
                onMessage = { text -> scope.launch { snackbar.showSnackbar(text) } },
            )
        }

        if (showLoginDialog && loginRepository != null) {
            TimetableLoginDialog(
                sessionSid = sessionSid,
                loginRepository = loginRepository,
                hasPersonal = source == TtSource.PERSONAL,
                onDismiss = { showLoginDialog = false },
                onSuccess = { personal ->
                    scope.launch {
                        runCatching { repository.savePersonal(personal) }
                        loadToken += 1
                        snackbar.showSnackbar("已保存个人课表（${personal.c.size} 门）")
                    }
                },
                onClearPersonal = {
                    runCatching { repository.clearPersonal() }
                    loadToken += 1
                    snackbar.showSnackbar("已清除个人课表，回退公共课表")
                },
            )
        }
    }
}

private enum class TtPhase { LOADING, READY, ERROR }

/** 课表网格上下文（快照/日期/今日/现在指示/进行中课程；减少 Composable 参数）。 */
private data class TimetableGridContext(
    val snapshot: TtSnapshot,
    val termStart: LocalDate?,
    val displayWeek: Int?,
    val monday: LocalDate,
    val todayIndex: Int,
    val dark: Boolean,
    val nowMillis: Long,
    val wide: Boolean,
    val now: TimetableNowState.Visible?,
    val ongoingName: String?,
    val ongoingProgress: Float?,
    val onCourseClick: (Course) -> Unit,
    val onSelectDay: (Int) -> Unit,
)

// =============================================================================
// 布局（手机单列 / 平板两栏）
// =============================================================================

@Composable
private fun CompactBodyV5(
    spec: CtAdaptiveSpec,
    grid: TimetableGridContext,
    plan: TtPlan?,
    nowMillis: Long,
    maxWeek: Int,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    showThisWeek: Boolean,
    viewMode: TimetableViewMode,
    selectedDay: Int,
    swipeOffset: Float,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit,
    onViewMode: (TimetableViewMode) -> Unit,
    onSelectDay: (Int) -> Unit,
    onOpenWeekPicker: () -> Unit,
    onOpenSettings: () -> Unit,
    onExport: (() -> Unit)?,
    swipeModifier: Modifier,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spec.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WeekControlBar(
            displayWeek = grid.displayWeek,
            maxWeek = maxWeek,
            canGoPrev = canGoPrev,
            canGoNext = canGoNext,
            showThisWeek = showThisWeek,
            viewMode = viewMode,
            onPrev = onPrev,
            onNext = onNext,
            onThisWeek = onThisWeek,
            onViewMode = onViewMode,
            onOpenWeekPicker = onOpenWeekPicker,
            onOpenSettings = onOpenSettings,
            onExport = onExport,
        )
        NextSessionBar(plan = plan, dark = grid.dark, nowMillis = nowMillis)
        Box(
            modifier = swipeModifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = swipeOffset
                    alpha = 1f - min(0.18f, abs(swipeOffset) / 900f * 0.18f)
                },
        ) {
            when (viewMode) {
                TimetableViewMode.WEEK -> WeekGridV5(grid = grid)
                TimetableViewMode.DAY -> DayViewV5(
                    grid = grid,
                    plan = plan,
                    selectedDay = selectedDay,
                    onSelectDay = onSelectDay,
                )
            }
        }
        AllCoursesCardV5(
            snapshot = grid.snapshot,
            termStart = grid.termStart,
            week = grid.displayWeek,
            dark = grid.dark,
            collapsible = true,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun TabletBodyV5(
    spec: CtAdaptiveSpec,
    grid: TimetableGridContext,
    plan: TtPlan?,
    nowMillis: Long,
    maxWeek: Int,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    showThisWeek: Boolean,
    viewMode: TimetableViewMode,
    selectedDay: Int,
    swipeOffset: Float,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit,
    onViewMode: (TimetableViewMode) -> Unit,
    onSelectDay: (Int) -> Unit,
    onOpenWeekPicker: () -> Unit,
    onOpenSettings: () -> Unit,
    onExport: (() -> Unit)?,
    swipeModifier: Modifier,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spec.horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(0.68f)
                .verticalScroll(rememberScrollState())
                .padding(top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WeekControlBar(
                displayWeek = grid.displayWeek,
                maxWeek = maxWeek,
                canGoPrev = canGoPrev,
                canGoNext = canGoNext,
                showThisWeek = showThisWeek,
                viewMode = viewMode,
                onPrev = onPrev,
                onNext = onNext,
                onThisWeek = onThisWeek,
                onViewMode = onViewMode,
                onOpenWeekPicker = onOpenWeekPicker,
                onOpenSettings = onOpenSettings,
                onExport = onExport,
            )
            Box(
                modifier = swipeModifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationX = swipeOffset
                        alpha = 1f - min(0.18f, abs(swipeOffset) / 1200f * 0.18f)
                    },
            ) {
                when (viewMode) {
                    TimetableViewMode.WEEK -> WeekGridV5(grid = grid)
                    TimetableViewMode.DAY -> DayViewV5(
                        grid = grid,
                        plan = plan,
                        selectedDay = selectedDay,
                        onSelectDay = onSelectDay,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(0.32f)
                .padding(top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NextSessionBar(plan = plan, dark = grid.dark, nowMillis = nowMillis)
                AllCoursesCardV5(
                    snapshot = grid.snapshot,
                    termStart = grid.termStart,
                    week = grid.displayWeek,
                    dark = grid.dark,
                )
            }
            if (onExport != null) {
                ExportCardV5(onExport = onExport)
            }
        }
    }
}

// =============================================================================
// 顶部控制条（周次胶囊 / 切换视图 / 导出+设置）
// =============================================================================

@Composable
private fun WeekControlBar(
    displayWeek: Int?,
    maxWeek: Int,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    showThisWeek: Boolean,
    viewMode: TimetableViewMode,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit,
    onViewMode: (TimetableViewMode) -> Unit,
    onOpenWeekPicker: () -> Unit,
    onOpenSettings: () -> Unit,
    onExport: (() -> Unit)?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev, enabled = canGoPrev, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "上一周",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    onClick = onOpenWeekPicker,
                    shape = CtShapeTokens.pill,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = displayWeek?.let { "第 $it 周" } ?: "我的课表",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Icon(
                            imageVector = Icons.Rounded.ExpandMore,
                            contentDescription = "选择周次",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                IconButton(onClick = onNext, enabled = canGoNext, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "下一周",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (onExport != null) {
                    IconButton(onClick = onExport) {
                        Icon(
                            imageVector = Icons.Rounded.IosShare,
                            contentDescription = "导出课表图片",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "课表设置",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "切换视图",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
                CtSegmentedButtons(
                    options = listOf(
                        CtSegmentOption(key = "week", label = "周"),
                        CtSegmentOption(key = "day", label = "日"),
                    ),
                    selectedKey = if (viewMode == TimetableViewMode.WEEK) "week" else "day",
                    onSelect = { onViewMode(if (it == "week") TimetableViewMode.WEEK else TimetableViewMode.DAY) },
                    showCheckIcon = false,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (showThisWeek) {
                    TextButton(onClick = onThisWeek) { Text("本周") }
                }
                Text(
                    text = "共 $maxWeek 周",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 下一节 / 上课中摘要条（马卡龙淡底，与下一节课同色）；无课显示「近期暂无课程」。 */
@Composable
private fun NextSessionBar(plan: TtPlan?, dark: Boolean, nowMillis: Long) {
    val ongoing = plan?.today?.firstOrNull { it.state == SessionState.ONGOING }
    val next = plan?.next
    val session = ongoing ?: next
    if (session == null) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CtShapeTokens.toolCard,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Text(
                text = "近期暂无课程",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
        return
    }
    val pair = if (dark) TimetablePalette.darkOf(session.name) else TimetablePalette.lightOf(session.name)
    val content = Color(pair.content)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = Color(pair.container),
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(
                    text = if (ongoing != null) "现在" else "下一节",
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.75f),
                )
                Text(
                    text = session.startText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(session.label.ifBlank { TtCalendar.dayLabel(session.dayOffset) })
                        append(" · ")
                        append(session.span)
                        if (session.room.isNotBlank()) append(" · ").append(TtCalendar.roomShort(session.room))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (ongoing != null) {
                val remain = ((session.endAt - nowMillis) / 60_000L).coerceAtLeast(0)
                CtPill(
                    text = "剩余 $remain 分",
                    containerColor = content.copy(alpha = 0.14f),
                    contentColor = content,
                )
            } else {
                // 下一节：假期/跨周时 `plan.next` 可能在 14 天内很远的日期，统一走分档文案
                // （分钟 / 小时 / 明天 HH:mm / 周X HH:mm / x 天 y 小时），不再输出「几千分钟」。
                CtPill(
                    text = NextSessionCountdown.text(session, nowMillis),
                    containerColor = content.copy(alpha = 0.14f),
                    contentColor = content,
                )
            }
        }
    }
}

// =============================================================================
// 周视图（马卡龙课程块 + 日期头 + 现在指示）
// =============================================================================

@Composable
private fun WeekGridV5(grid: TimetableGridContext) {
    val weekendEmpty = remember(grid.snapshot, grid.termStart, grid.displayWeek) {
        TtCalendar.coursesOnDay(grid.snapshot.c, grid.termStart, grid.displayWeek, 5).isEmpty() &&
            TtCalendar.coursesOnDay(grid.snapshot.c, grid.termStart, grid.displayWeek, 6).isEmpty()
    }
    val days = remember(weekendEmpty) { (0 until TimetableLayout.visibleDayCount(weekendEmpty)).toList() }
    val showNow = grid.now != null && days.contains(grid.todayIndex)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column {
            if (weekendEmpty) {
                Text(
                    text = "周末无课，已隐藏周末列",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
                )
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val fontScale = LocalDensity.current.fontScale
                val metrics = remember(maxWidth, fontScale) {
                    TimetableLayout.metricsFor(maxWidth.value.toInt(), twoPane = grid.wide, fontScale = fontScale)
                }
                val labelWidth = metrics.labelWidthDp.dp
                val rowHeight = metrics.rowHeightDp.dp
                val headerHeight = metrics.headerHeightDp.dp
                val available = maxWidth - labelWidth - 4.dp
                val columnWidth = TimetableLayout.columnWidthDp(available.value, days.size).dp
                val gridHeight = rowHeight * TtPeriods.COUNT
                val blockShape = remember(metrics) { RoundedCornerShape(metrics.blockCornerDp.dp) }

                Row(modifier = Modifier.fillMaxWidth()) {
                    // 左列：月份 + 节次
                    Column(modifier = Modifier.width(labelWidth)) {
                        Box(
                            modifier = Modifier
                                .height(headerHeight)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${grid.monday.monthValue}月",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                            )
                        }
                        for (period in 1..TtPeriods.COUNT) {
                            Column(
                                modifier = Modifier
                                    .height(rowHeight)
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = "$period",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = TtPeriods.startText(period),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.height(headerHeight)) {
                            days.forEach { dayIndex ->
                                DayHeaderCell(
                                    date = grid.monday.plusDays(dayIndex.toLong()),
                                    dayIndex = dayIndex,
                                    isToday = dayIndex == grid.todayIndex,
                                    isWeekend = dayIndex >= 5,
                                    width = columnWidth,
                                    height = headerHeight,
                                    onClick = { grid.onSelectDay(dayIndex) },
                                )
                            }
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(gridHeight)) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val gridLine = if (grid.dark) Color(0x26FFFFFF) else Color(0x18000000)
                                val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 7f))
                                for (period in 1..TtPeriods.COUNT) {
                                    val y = period * rowHeight.toPx()
                                    drawLine(
                                        color = gridLine,
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = 1f,
                                        pathEffect = dash,
                                    )
                                }
                            }
                            Row {
                                days.forEach { dayIndex ->
                                    val courses = TtCalendar.coursesOnDay(
                                        courses = grid.snapshot.c,
                                        termStart = grid.termStart,
                                        week = grid.displayWeek,
                                        dayIndex = dayIndex,
                                    )
                                    DayColumnV5(
                                        width = columnWidth,
                                        rowHeight = rowHeight,
                                        courses = courses,
                                        dark = grid.dark,
                                        isToday = dayIndex == grid.todayIndex,
                                        metrics = metrics,
                                        blockShape = blockShape,
                                        weekKey = grid.displayWeek ?: -1,
                                        ongoingName = if (dayIndex == grid.todayIndex) grid.ongoingName else null,
                                        ongoingProgress = grid.ongoingProgress,
                                        onCourseClick = grid.onCourseClick,
                                    )
                                }
                            }
                            if (showNow) {
                                NowIndicatorOverlay(
                                    now = grid.now!!,
                                    gridHeight = gridHeight,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeaderCell(
    date: LocalDate,
    dayIndex: Int,
    isToday: Boolean,
    isWeekend: Boolean,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val container = when {
        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
        isWeekend -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f)
        else -> Color.Transparent
    }
    val labelColor = when {
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        isWeekend -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(width)
            .height(height)
            .padding(horizontal = 1.5.dp, vertical = 3.dp),
        shape = shape,
        color = container,
        contentColor = labelColor,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = TtPeriods.DAY_LABELS.getOrElse(dayIndex) { "" }.removePrefix("周"),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                maxLines = 1,
            )
            Text(
                text = "${date.dayOfMonth}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else {
                    if (isWeekend) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                    else MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DayColumnV5(
    width: Dp,
    rowHeight: Dp,
    courses: List<Course>,
    dark: Boolean,
    isToday: Boolean,
    metrics: TimetableMetrics,
    blockShape: RoundedCornerShape,
    weekKey: Int,
    ongoingName: String?,
    ongoingProgress: Float?,
    onCourseClick: (Course) -> Unit,
) {
    val sorted = remember(courses) { courses.sortedBy { it.start } }
    Box(
        modifier = Modifier
            .width(width)
            .height(rowHeight * TtPeriods.COUNT)
            .background(if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.10f) else Color.Transparent),
    ) {
        sorted.forEachIndexed { index, course ->
            val pair = if (dark) TimetablePalette.darkOf(course.name) else TimetablePalette.lightOf(course.name)
            val isOngoing = ongoingName != null && course.name == ongoingName
            CourseBlockV5(
                course = course,
                width = width,
                rowHeight = rowHeight,
                metrics = metrics,
                container = Color(pair.container),
                content = Color(pair.content),
                shape = blockShape,
                blockIndex = index,
                weekKey = weekKey,
                isOngoing = isOngoing,
                ongoingProgress = if (isOngoing) ongoingProgress else null,
                onClick = { onCourseClick(course) },
            )
        }
    }
}

@Composable
private fun CourseBlockV5(
    course: Course,
    width: Dp,
    rowHeight: Dp,
    metrics: TimetableMetrics,
    container: Color,
    content: Color,
    shape: RoundedCornerShape,
    blockIndex: Int,
    weekKey: Int,
    isOngoing: Boolean,
    ongoingProgress: Float?,
    onClick: () -> Unit,
) {
    val span = (course.end - course.start + 1).coerceAtLeast(1)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = CtMotion.spatialFast,
        label = "courseBlockScale",
    )
    var entered by remember(weekKey, course.name, course.start) { mutableStateOf(false) }
    LaunchedEffect(weekKey, course.name, course.start) {
        delay(TimetableLayout.entranceDelayMs(blockIndex).toLong())
        entered = true
    }
    val enterAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = CtMotion.effects,
        label = "courseBlockAlpha",
    )
    val enterOffset by animateFloatAsState(
        targetValue = if (entered) 0f else 8f,
        animationSpec = CtMotion.spatial,
        label = "courseBlockOffset",
    )

    Box(
        modifier = Modifier
            // 课程块按开始节次偏移到对应行（v4.56 修复同一列全部叠在顶部）
            .offset(y = rowHeight * (course.start - 1).coerceAtLeast(0))
            .padding(start = 1.5.dp, end = 1.5.dp, top = 2.dp)
            .height(rowHeight * span - 4.dp)
            .width(width - 3.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = enterAlpha
                translationY = enterOffset.dp.toPx()
            }
            .clip(shape)
            .background(container)
            .then(
                if (isOngoing) {
                    Modifier.border(1.5.dp, content.copy(alpha = 0.55f), shape)
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(metrics.blockPaddingDp.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = TtPeriods.startText(course.start),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                fontWeight = FontWeight.Bold,
                color = content.copy(alpha = 0.78f),
                maxLines = 1,
            )
            Text(
                text = course.name,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (metrics.courseNameMaxLines >= 3) 11.sp else 10.sp,
                ),
                fontWeight = FontWeight.SemiBold,
                color = content,
                maxLines = if (span >= 2) metrics.courseNameMaxLines else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (course.room.isNotBlank() && span >= 2) {
                Text(
                    text = TtCalendar.roomShort(course.room),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                    color = content.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isOngoing && ongoingProgress != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(content.copy(alpha = 0.18f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ongoingProgress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(content),
                )
            }
        }
    }
}

@Composable
private fun NowIndicatorOverlay(now: TimetableNowState.Visible, gridHeight: Dp) {
    val y = gridHeight * now.fraction
    val badgeTop = maxOf(0.dp, y - 20.dp)
    Box(modifier = Modifier.fillMaxSize()) {
        val lineColor = MaterialTheme.colorScheme.error
        Canvas(modifier = Modifier.fillMaxSize()) {
            val lineY = y.toPx()
            drawLine(
                color = lineColor,
                start = Offset(0f, lineY),
                end = Offset(size.width, lineY),
                strokeWidth = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)),
            )
        }
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = badgeTop)
                .padding(end = 2.dp),
            shape = CtShapeTokens.pill,
            color = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ) {
            Text(
                text = now.label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
    }
}

// =============================================================================
// 日视图
// =============================================================================

@Composable
private fun DayViewV5(
    grid: TimetableGridContext,
    plan: TtPlan?,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit,
) {
    val date = remember(grid.monday, selectedDay) { grid.monday.plusDays(selectedDay.toLong()) }
    val sessions = remember(grid.snapshot, grid.termStart, grid.displayWeek, selectedDay, grid.monday, grid.nowMillis) {
        TtCalendar.sessionsOn(
            courses = grid.snapshot.c,
            termStart = grid.termStart,
            date = date,
            nowMillis = grid.nowMillis,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            (0..6).forEach { index ->
                val dayDate = grid.monday.plusDays(index.toLong())
                val active = index == selectedDay
                val today = index == grid.todayIndex
                Surface(
                    onClick = { onSelectDay(index) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = when {
                        active -> MaterialTheme.colorScheme.primaryContainer
                        today -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else -> MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    contentColor = if (active) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = TtPeriods.DAY_LABELS[index].removePrefix("周"),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                        Text(
                            text = "${dayDate.dayOfMonth}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${date.monthValue}月${date.dayOfMonth}日 · ${TtPeriods.DAY_LABELS[selectedDay]}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (sessions.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(52.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Text(
                            text = "当天没有课",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val next = plan?.next
                        if (next != null) {
                            Text(
                                text = "下次 · ${next.label} ${next.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    sessions.forEach { session ->
                        DaySessionRowV5(
                            session = session,
                            dark = grid.dark,
                            onClick = {
                                grid.snapshot.c.firstOrNull { course ->
                                    course.name == session.name && course.day == selectedDay
                                }?.let(grid.onCourseClick)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DaySessionRowV5(
    session: SessionInfo,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val pair = if (dark) TimetablePalette.darkOf(session.name) else TimetablePalette.lightOf(session.name)
    val content = Color(pair.content)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(pair.container),
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.width(48.dp)) {
                Text(
                    text = session.startText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = content,
                    maxLines = 1,
                )
                Text(
                    text = session.endText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = content.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(session.span)
                        if (session.room.isNotBlank()) append(" · ").append(TtCalendar.roomShort(session.room))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (session.state) {
                SessionState.ONGOING -> CtPill(
                    text = "上课中",
                    containerColor = content.copy(alpha = 0.14f),
                    contentColor = content,
                )

                SessionState.NEXT -> CtPill(
                    text = "待上",
                    containerColor = content.copy(alpha = 0.12f),
                    contentColor = content,
                )

                SessionState.DONE -> CtPill(
                    text = "已结束",
                    containerColor = content.copy(alpha = 0.10f),
                    contentColor = content.copy(alpha = 0.8f),
                )
            }
        }
    }
}

// =============================================================================
// 全部课程 / 导出卡
// =============================================================================

@Composable
private fun AllCoursesCardV5(
    snapshot: TtSnapshot,
    termStart: LocalDate?,
    week: Int?,
    dark: Boolean,
    collapsible: Boolean = false,
) {
    var expanded by remember(collapsible) { mutableStateOf(!collapsible) }
    val groups = remember(snapshot, termStart, week) {
        snapshot.c
            .filter { TtCalendar.isActive(it, week) }
            .groupBy { it.name }
            .map { (name, list) ->
                val first = list.first()
                Triple(name, first, list.size)
            }
            .sortedBy { it.first }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (collapsible) Modifier.clickable { expanded = !expanded } else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "全部课程（${groups.size}）",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (collapsible) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "收起全部课程" else "展开全部课程",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (expanded) {
                if (groups.isEmpty()) {
                    Text(
                        text = "本周无课",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    groups.forEach { (name, course, count) ->
                        val pair = if (dark) TimetablePalette.darkOf(name) else TimetablePalette.lightOf(name)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(pair.container)),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = buildString {
                                    if (course.teacher.isNotBlank()) append(course.teacher).append(" · ")
                                    append(count).append(" 节")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportCardV5(onExport: () -> Unit) {
    Surface(
        onClick = onExport,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.IosShare,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "导出课表图片",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "按当前周生成清单卡片",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// =============================================================================
// 周选择 / 设置弹层
// =============================================================================

@Composable
private fun WeekPickerSheet(
    displayWeek: Int?,
    maxWeek: Int,
    onPick: (Int) -> Unit,
    onThisWeek: () -> Unit,
    onDismiss: () -> Unit,
) {
    CtBottomSheet(onDismissRequest = onDismiss) {
        CtBottomSheetHeader(
            title = "选择周次",
            subtitle = "当前第 ${displayWeek ?: "-"} 周 · 共 $maxWeek 周",
        )
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (1..maxWeek).chunked(6).forEach { rowWeeks ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowWeeks.forEach { week ->
                        val selected = week == displayWeek
                        Surface(
                            onClick = { onPick(week) },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            contentColor = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$week",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                    }
                    repeat(6 - rowWeeks.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
        CtBottomSheetFooter(
            primaryText = "回到本周",
            onPrimary = onThisWeek,
            secondaryText = "取消",
            onSecondary = onDismiss,
        )
    }
}

@Composable
private fun TimetableSettingsSheet(
    sourceLabel: String,
    canExport: Boolean,
    canLogin: Boolean,
    isPersonal: Boolean,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onLogin: () -> Unit,
    onClearPersonal: () -> Unit,
    onRefresh: () -> Unit,
) {
    CtBottomSheet(onDismissRequest = onDismiss) {
        CtBottomSheetHeader(title = "课表设置", subtitle = sourceLabel)
        if (canExport) {
            SettingsActionRow(
                icon = Icons.Rounded.IosShare,
                title = "导出课表图片",
                subtitle = "按当前周生成清单卡片",
                onClick = onExport,
            )
        }
        if (canLogin) {
            SettingsActionRow(
                icon = Icons.AutoMirrored.Rounded.Login,
                title = if (isPersonal) "重新登录教务" else "登录教务",
                subtitle = "抓取个人课表（仅密文上云）",
                onClick = onLogin,
            )
        }
        if (isPersonal) {
            SettingsActionRow(
                icon = Icons.Rounded.DeleteOutline,
                title = "清除我的课表",
                subtitle = "回退公共课表",
                danger = true,
                onClick = onClearPersonal,
            )
        }
        SettingsActionRow(
            icon = Icons.Rounded.Refresh,
            title = "刷新课表",
            subtitle = "重新拉取教务快照",
            onClick = onRefresh,
        )
        Text(
            text = "课表为只读快照；个人课表仅本机保存并随账号同步。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun SettingsActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val contentColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// =============================================================================
// 课程详情（公开：供预览/页面复用；签名保持 v3 兼容）
// =============================================================================

/**
 * 课程详情内容（Bottom Sheet 内使用）：
 * 色块头（课程名 + 节次/时间）+ 教师/教室/星期/周次信息 + 「写日程」入口。
 */
@Composable
fun TimetableCourseDetailContent(
    course: Course,
    displayWeek: Int?,
    onAddToCalendar: ((Course, Int, LocalDate) -> Unit)? = null,
    termStart: LocalDate? = null,
) {
    val dark = LocalCtDarkTheme.current
    val pair = if (dark) TimetablePalette.darkOf(course.name) else TimetablePalette.lightOf(course.name)
    val content = Color(pair.content)
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color(pair.container),
            contentColor = content,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = course.name.ifBlank { "未命名课程" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(TimetableDetail.periodText(course.start, course.end))
                        append(" · ")
                        append(TimetableDetail.timeRangeText(course.start, course.end))
                        append(" · ")
                        append(TtCalendar.dayLabel(course.day))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.78f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TimetableDetail.detailRows(course, displayWeek).forEach { (label, value) ->
                    Row {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(44.dp),
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        val week = displayWeek
        val term = termStart
        if (onAddToCalendar != null) {
            if (week != null && term != null) {
                FilledTonalButton(
                    onClick = { onAddToCalendar(course, week, term) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = CtShapeTokens.pill,
                ) {
                    Text("添加到系统日历（本周）")
                }
            } else {
                Text(
                    text = "开学日未知，暂时无法写日程",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CtPill(text = "只读")
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "课表来自教务快照",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// =============================================================================
// 状态页（加载骨架 / 空 / 错误 / 个人课表提示）
// =============================================================================

@Composable
private fun TimetableLoadingState() {
    val transition = rememberInfiniteTransition(label = "ttSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "ttSkeletonAlpha",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(3) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha * 0.7f)),
                )
                repeat(5) { column ->
                    val color = if ((row + column) % 3 == 0) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha * 0.55f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha * 0.6f)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(color),
                    )
                }
            }
        }
        Text(
            text = "课表加载中…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TimetableErrorState(message: String, kind: TtFailureKind?, onRetry: () -> Unit) {
    val title = when (kind) {
        TtFailureKind.NETWORK -> "网络异常，点击重试"
        TtFailureKind.NO_TOKEN, TtFailureKind.EMPTY -> "暂无课表数据"
        TtFailureKind.FORMAT -> "课表数据格式异常"
        null -> "课表加载失败"
    }
    val hint = when (kind) {
        TtFailureKind.NETWORK -> message.ifBlank { "请检查网络后重试" }
        TtFailureKind.NO_TOKEN -> "登录后可查看教务同步的班级课表"
        TtFailureKind.EMPTY -> "教务快照为空，稍后刷新试试"
        TtFailureKind.FORMAT -> "快照内容不符合契约，请联系管理员"
        null -> message.ifBlank { "请稍后重试" }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(38.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            FilledTonalButton(onClick = onRetry, shape = CtShapeTokens.pill) { Text("重试") }
        }
    }
}

@Composable
private fun TimetableEmptyState(onRefresh: () -> Unit, onLogin: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Text(
                text = "课表还是空的",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "教务课表同步后会自动出现",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRefresh, shape = CtShapeTokens.pill) { Text("刷新") }
                if (onLogin != null) {
                    FilledTonalButton(onClick = onLogin, shape = CtShapeTokens.pill) { Text("登录教务") }
                }
            }
        }
    }
}

/** 个人课表提示条：标明当前展示的是本人教务课表，并提供「清除」回退公共课表。 */
@Composable
private fun PersonalTimetableBanner(onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CtPill(
            text = "个人课表",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = "登录教务抓取",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onClear) { Text("清除我的课表") }
    }
}

// =============================================================================
// 滑动切周手势（参数与 web/mp 一致）
// =============================================================================

/**
 * 给课表内容挂「左右滑动切周」手势（跟手 → 松手弹性吸附/飞出飞入）。
 *
 * - 横向锁定：|dx|≥12px 且 |dx| > |dy|×1.35；
 * - 纵向/斜向手势不拦截（交给页面滚动）；
 * - 有目标周 1:1 跟手，到第一/最后周时阻尼 0.28；
 * - 松手提交与否由调用方判断（[TimetableSwipe.shouldCommit]）。
 */
private fun Modifier.timetableSwipe(
    displayWeek: Int?,
    maxWeek: Int,
    enabled: Boolean,
    onOffset: (Float) -> Unit,
    onCommit: (dx: Float, dy: Float) -> Unit,
    onWidth: (Float) -> Unit,
): Modifier = this
    .onSizeChanged { onWidth(it.width.toFloat()) }
    .pointerInput(displayWeek, maxWeek, enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val start = down.position
            var locked = false
            var canceled = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val dx = change.position.x - start.x
                val dy = change.position.y - start.y
                if (!locked && !canceled) {
                    if (TimetableSwipe.shouldLock(dx, dy)) {
                        locked = true
                    } else if (abs(dy) >= TimetableSwipe.LOCK_PX && abs(dy) > abs(dx) * 1.2f) {
                        canceled = true
                    }
                }
                if (locked) {
                    change.consume()
                    val hasTarget = when {
                        dx < 0 -> displayWeek != null && displayWeek < maxWeek
                        dx > 0 -> displayWeek != null && displayWeek > TimetableWeekLogic.MIN_WEEK
                        else -> false
                    }
                    onOffset(TimetableSwipe.damped(dx, hasTarget))
                }
                if (change.changedToUpIgnoreConsumed()) {
                    if (locked) {
                        onCommit(dx, dy)
                    }
                    break
                }
                if (canceled) {
                    break
                }
            }
        }
    }
