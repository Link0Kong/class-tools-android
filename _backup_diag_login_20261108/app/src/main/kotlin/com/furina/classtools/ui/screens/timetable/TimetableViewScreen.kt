@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.furina.classtools.ui.screens.timetable

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furina.classtools.data.timetable.Course
import com.furina.classtools.data.timetable.SessionInfo
import com.furina.classtools.data.timetable.SessionState
import com.furina.classtools.data.timetable.TimetableRepository
import com.furina.classtools.data.timetable.TodayState
import com.furina.classtools.data.timetable.TtCalendar
import com.furina.classtools.data.timetable.TtFailureKind
import com.furina.classtools.data.timetable.TtLoadResult
import com.furina.classtools.data.timetable.TtPeriods
import com.furina.classtools.data.timetable.TtPlan
import com.furina.classtools.data.timetable.TtSnapshot
import com.furina.classtools.features.export.ExportCardSpec
import com.furina.classtools.ui.adaptive.rememberCtAdaptiveSpec
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.CtTopBar
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.expressive.CtBottomSheet
import com.furina.classtools.ui.components.expressive.CtSegmentOption
import com.furina.classtools.ui.components.expressive.CtSegmentedButtons
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.LocalCtDarkTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

/** 课表视图模式（周/日只由按钮切换，不参与左右滑动）。 */
enum class TimetableViewMode { WEEK, DAY }

/**
 * 课表页（v3.0 重构；对齐网页/小程序课表能力）。
 *
 * 功能：
 * - **显示比例自适应**：节次行高/左时间列/表头高按窗口宽度（手机/平板/横屏双栏）压缩或放大；
 *   7 列恒不横向溢出（周末两天都无课时隐藏周末列，5 列）；今日高亮；
 * - **左右滑动切周**：跟手（横向锁定 12px、|dx|>|dy|×1.35、边缘橡皮筋 0.28、提交阈值 56px）；
 *   松手提交后 150ms 飞出 + 230ms 飞入；与顶部「上一周/下一周」「本周」按钮、周次标题联动；
 *   与竖向滚动不冲突（纵向手势不拦截）；滑动后 320ms 内点击被抑制；
 * - **16 色稳定课程配色**（按课程名散列，浅/深两套，对比度 ≥4.5）；
 * - 课程详情（教师/教室/节次/时间/周次/单双周 + 写日程入口）；
 * - 下一节 / 上课中提示（含剩余分钟）；
 * - 导出课表图片：`onExportTimetable(ExportCardSpec)`（Lead 接 ExportPresenter）；
 * - 平板限宽 + 左右两栏（左周表/日视图，右下一节 + 全部课程 + 导出）。
 *
 * 接线（Lead 在 MainNavHost）：
 * ```kotlin
 * TimetableViewScreen(
 *     onBack = { navController.popBackStack() },
 *     repository = container.timetableRepository,
 *     onAddToCalendar = { course, week, termStart -> calendarWriter.writeCourse(course, week, termStart) },
 *     onExportTimetable = { spec -> launchExport(spec) },
 * )
 * ```
 *
 * @param initialSnapshot / initialError / initialErrorKind / initialDayView / initialWeekOffset / initialSwipeOffsetPx
 *        预览/测试注入（不读仓库、不联网）
 * @param skipLoad 预览/测试：跳过仓库请求
 * @param nowMillis 预览/测试：固定「现在」
 * @param onAddToCalendar 写系统日历（课程 → 周次 + 开学日；null = 不显示入口）
 * @param onExportTimetable 导出课表图片（payload 为统一 ExportCardSpec；null = 不显示导出按钮）
 */
@Composable
fun TimetableViewScreen(
    onBack: () -> Unit,
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
    var errorMessage by remember(initialError) { mutableStateOf(initialError.orEmpty()) }
    var errorKind by remember(initialErrorKind) { mutableStateOf(initialErrorKind) }
    var loadToken by remember { mutableIntStateOf(0) }
    var refreshToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(loadToken, refreshToken) {
        if (skipLoad) return@LaunchedEffect
        phase = TtPhase.LOADING
        when (val result = repository.load(force = refreshToken > 0)) {
            is TtLoadResult.Success -> {
                snapshot = result.snapshot
                fromCache = result.fromCache
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

    /** 周切换动画：飞出 150ms → 切周 → 飞入 230ms（与 web/mp 同参数）。 */
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
                    subtitle = buildString {
                        append(if (viewMode == TimetableViewMode.WEEK) "周视图" else "日视图")
                        displayWeek?.let { append(" · 第 $it 周") }
                        if (fromCache) append(" · 本机缓存")
                    },
                    onBack = onBack,
                    actions = {
                        if (fromCache) CtPill(text = "缓存")
                        if (onExportTimetable != null && snapshot.c.isNotEmpty()) {
                            IconButton(
                                onClick = { exportTimetable() },
                                enabled = !weekTransitioning,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.IosShare,
                                    contentDescription = "导出课表图片",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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

                when (phase) {
                    TtPhase.LOADING -> LoadingState()
                    TtPhase.ERROR -> ErrorState(
                        message = errorMessage,
                        kind = errorKind,
                        onRetry = { loadToken += 1 },
                    )

                    TtPhase.READY -> {
                        if (snapshot.c.isEmpty()) {
                            EmptyState(onRefresh = { if (!skipLoad) reload() })
                        } else if (spec.twoPane) {
                            TabletBody(
                                spec = spec,
                                snapshot = snapshot,
                                termStart = termStart,
                                plan = plan,
                                displayWeek = displayWeek,
                                monday = monday,
                                todayIndex = todayIndex,
                                maxWeek = maxWeek,
                                canGoPrev = displayWeek != null && displayWeek > TimetableWeekLogic.MIN_WEEK,
                                canGoNext = displayWeek != null && displayWeek < maxWeek,
                                showThisWeek = !TimetableWeekLogic.isThisWeek(weekOffset),
                                viewMode = viewMode,
                                selectedDay = selectedDay,
                                swipeOffset = swipeOffset,
                                dark = dark,
                                nowMillis = nowMillis,
                                onPrev = { goWeek(TimetableSwipe.Direction.PREV) },
                                onNext = { goWeek(TimetableSwipe.Direction.NEXT) },
                                onThisWeek = { goThisWeek() },
                                onViewMode = { viewMode = it },
                                onSelectDay = { selectedDay = it },
                                onCourseClick = { course ->
                                    if (System.currentTimeMillis() >= suppressClickUntil) selectedCourse = course
                                },
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
                                                animate(swipeOffset, 0f, animationSpec = tween(180)) { v, _ -> swipeOffset = v }
                                            }
                                        }
                                    },
                                    onWidth = { gridWidthPx = it },
                                ),
                            )
                        } else {
                            CompactBody(
                                spec = spec,
                                snapshot = snapshot,
                                termStart = termStart,
                                plan = plan,
                                displayWeek = displayWeek,
                                monday = monday,
                                todayIndex = todayIndex,
                                maxWeek = maxWeek,
                                canGoPrev = displayWeek != null && displayWeek > TimetableWeekLogic.MIN_WEEK,
                                canGoNext = displayWeek != null && displayWeek < maxWeek,
                                showThisWeek = !TimetableWeekLogic.isThisWeek(weekOffset),
                                viewMode = viewMode,
                                selectedDay = selectedDay,
                                swipeOffset = swipeOffset,
                                dark = dark,
                                nowMillis = nowMillis,
                                onPrev = { goWeek(TimetableSwipe.Direction.PREV) },
                                onNext = { goWeek(TimetableSwipe.Direction.NEXT) },
                                onThisWeek = { goThisWeek() },
                                onViewMode = { viewMode = it },
                                onSelectDay = { selectedDay = it },
                                onCourseClick = { course ->
                                    if (System.currentTimeMillis() >= suppressClickUntil) selectedCourse = course
                                },
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
                                                animate(swipeOffset, 0f, animationSpec = tween(180)) { v, _ -> swipeOffset = v }
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
    }
}

private enum class TtPhase { LOADING, READY, ERROR }

// =============================================================================
// 布局（compact 单列 / 平板双栏）
// =============================================================================

@Composable
private fun CompactBody(
    spec: com.furina.classtools.ui.adaptive.CtAdaptiveSpec,
    snapshot: TtSnapshot,
    termStart: LocalDate?,
    plan: TtPlan?,
    displayWeek: Int?,
    monday: LocalDate,
    todayIndex: Int,
    maxWeek: Int,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    showThisWeek: Boolean,
    viewMode: TimetableViewMode,
    selectedDay: Int,
    swipeOffset: Float,
    dark: Boolean,
    nowMillis: Long,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit,
    onViewMode: (TimetableViewMode) -> Unit,
    onSelectDay: (Int) -> Unit,
    onCourseClick: (Course) -> Unit,
    swipeModifier: Modifier,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spec.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WeekHeaderCard(
            weekTitle = TtCalendar.weekTitle(termStart, displayWeek),
            rangeText = TimetableWeekLogic.weekRangeLabel(termStart, displayWeek),
            canGoPrev = canGoPrev,
            canGoNext = canGoNext,
            showThisWeek = showThisWeek,
            viewMode = viewMode,
            onPrev = onPrev,
            onNext = onNext,
            onThisWeek = onThisWeek,
            onViewMode = onViewMode,
        )
        OngoingOrNextCard(plan = plan, nowMillis = nowMillis)
        Box(
            modifier = swipeModifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = swipeOffset
                    alpha = 1f - min(0.2f, abs(swipeOffset) / 900f * 0.2f)
                },
        ) {
            when (viewMode) {
                TimetableViewMode.WEEK -> WeekGrid(
                    snapshot = snapshot,
                    termStart = termStart,
                    week = displayWeek,
                    monday = monday,
                    todayIndex = todayIndex,
                    dark = dark,
                    twoPane = false,
                    onCourseClick = onCourseClick,
                )

                TimetableViewMode.DAY -> DayView(
                    snapshot = snapshot,
                    termStart = termStart,
                    week = displayWeek,
                    monday = monday,
                    selectedDay = selectedDay,
                    plan = plan,
                    dark = dark,
                    nowMillis = nowMillis,
                    onSelectDay = onSelectDay,
                    onCourseClick = onCourseClick,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun TabletBody(
    spec: com.furina.classtools.ui.adaptive.CtAdaptiveSpec,
    snapshot: TtSnapshot,
    termStart: LocalDate?,
    plan: TtPlan?,
    displayWeek: Int?,
    monday: LocalDate,
    todayIndex: Int,
    maxWeek: Int,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    showThisWeek: Boolean,
    viewMode: TimetableViewMode,
    selectedDay: Int,
    swipeOffset: Float,
    dark: Boolean,
    nowMillis: Long,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit,
    onViewMode: (TimetableViewMode) -> Unit,
    onSelectDay: (Int) -> Unit,
    onCourseClick: (Course) -> Unit,
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
                .weight(0.66f)
                .verticalScroll(rememberScrollState())
                .padding(top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WeekHeaderCard(
                weekTitle = TtCalendar.weekTitle(termStart, displayWeek),
                rangeText = TimetableWeekLogic.weekRangeLabel(termStart, displayWeek),
                canGoPrev = canGoPrev,
                canGoNext = canGoNext,
                showThisWeek = showThisWeek,
                viewMode = viewMode,
                onPrev = onPrev,
                onNext = onNext,
                onThisWeek = onThisWeek,
                onViewMode = onViewMode,
            )
            Box(
                modifier = swipeModifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationX = swipeOffset
                        alpha = 1f - min(0.2f, abs(swipeOffset) / 1200f * 0.2f)
                    },
            ) {
                when (viewMode) {
                    TimetableViewMode.WEEK -> WeekGrid(
                        snapshot = snapshot,
                        termStart = termStart,
                        week = displayWeek,
                        monday = monday,
                        todayIndex = todayIndex,
                        dark = dark,
                        twoPane = true,
                        onCourseClick = onCourseClick,
                    )

                    TimetableViewMode.DAY -> DayView(
                        snapshot = snapshot,
                        termStart = termStart,
                        week = displayWeek,
                        monday = monday,
                        selectedDay = selectedDay,
                        plan = plan,
                        dark = dark,
                        nowMillis = nowMillis,
                        onSelectDay = onSelectDay,
                        onCourseClick = onCourseClick,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(0.34f)
                .verticalScroll(rememberScrollState())
                .padding(top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OngoingOrNextCard(plan = plan, nowMillis = nowMillis)
            AllCoursesCard(snapshot = snapshot, termStart = termStart, week = displayWeek, dark = dark)
            if (onExport != null) {
                ExportCard(onExport = onExport)
            }
        }
    }
}

/** 顶部周次卡：上一周 / 周次标题+日期 / 下一周；本周 / 导出 / 周-日切换。 */
@Composable
private fun WeekHeaderCard(
    weekTitle: String,
    rangeText: String,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    showThisWeek: Boolean,
    viewMode: TimetableViewMode,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit,
    onViewMode: (TimetableViewMode) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev, enabled = canGoPrev) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "上一周",
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = weekTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    if (rangeText.isNotBlank()) {
                        Text(
                            text = rangeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                IconButton(onClick = onNext, enabled = canGoNext) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "下一周",
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showThisWeek) {
                    TextButton(onClick = onThisWeek) { Text("本周") }
                }
                Spacer(modifier = Modifier.weight(1f))
                CtSegmentedButtons(
                    options = listOf(
                        CtSegmentOption(key = "week", label = "周"),
                        CtSegmentOption(key = "day", label = "日"),
                    ),
                    selectedKey = if (viewMode == TimetableViewMode.WEEK) "week" else "day",
                    onSelect = { onViewMode(if (it == "week") TimetableViewMode.WEEK else TimetableViewMode.DAY) },
                    showCheckIcon = false,
                )
            }
        }
    }
}

/** 下一节 / 上课中提示（含剩余分钟）。 */
@Composable
private fun OngoingOrNextCard(plan: TtPlan?, nowMillis: Long) {
    val ongoing = plan?.today?.firstOrNull { it.state == SessionState.ONGOING }
    val next = plan?.next
    val primaryColor = MaterialTheme.colorScheme.primaryContainer
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = primaryColor.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(
                    text = if (ongoing != null) "上课中" else "下一节",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
                Text(
                    text = ongoing?.startText ?: next?.startText ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ongoing?.name ?: next?.name ?: "近期暂无课程",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = buildString {
                    val session = ongoing ?: next
                    if (session != null) {
                        if (ongoing == null) {
                            append(session.label)
                            append(" · ")
                        }
                        append(session.startText)
                        append("-")
                        append(session.endText)
                        if (session.room.isNotBlank()) append(" · ").append(TtCalendar.roomShort(session.room))
                        if (ongoing != null) {
                            val remainMinutes = ((session.endAt - nowMillis) / 60_000L).coerceAtLeast(0)
                            append(" · 剩余 ").append(remainMinutes).append(" 分钟")
                        }
                    }
                }
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (ongoing != null) {
                CtPill(
                    text = "上课中",
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

// =============================================================================
// 周视图（自适应比例 + 16 色 + 今日高亮 + 周末空列隐藏）
// =============================================================================

@Composable
private fun WeekGrid(
    snapshot: TtSnapshot,
    termStart: LocalDate?,
    week: Int?,
    monday: LocalDate,
    todayIndex: Int,
    dark: Boolean,
    twoPane: Boolean,
    onCourseClick: (Course) -> Unit,
) {
    val weekendEmpty = remember(snapshot, termStart, week) {
        TtCalendar.coursesOnDay(snapshot.c, termStart, week, 5).isEmpty() &&
            TtCalendar.coursesOnDay(snapshot.c, termStart, week, 6).isEmpty()
    }
    val days = remember(weekendEmpty) { (0 until TimetableLayout.visibleDayCount(weekendEmpty)).toList() }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val metrics = remember(maxWidth, twoPane) {
                TimetableLayout.metricsFor(maxWidth.value.toInt(), twoPane)
            }
            val labelWidth = metrics.labelWidthDp.dp
            val rowHeight = metrics.rowHeightDp.dp
            val headerHeight = metrics.headerHeightDp.dp
            val available = maxWidth - labelWidth - 4.dp
            val columnWidth = TimetableLayout.columnWidthDp(available.value, days.size).dp
            Row(modifier = Modifier.fillMaxWidth()) {
                // 左侧节次时间列（紧凑）
                Column(modifier = Modifier.width(labelWidth)) {
                    Spacer(modifier = Modifier.height(headerHeight))
                    for (period in 1..TtPeriods.COUNT) {
                        Column(
                            modifier = Modifier
                                .height(rowHeight)
                                .fillMaxWidth()
                                .padding(top = 3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "$period",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = TtPeriods.startText(period),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row {
                        days.forEach { dayIndex ->
                            val date = monday.plusDays(dayIndex.toLong())
                            val isToday = dayIndex == todayIndex
                            val weekend = dayIndex >= 5
                            Column(
                                modifier = Modifier
                                    .width(columnWidth)
                                    .height(headerHeight)
                                    .background(
                                        when {
                                            isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            weekend -> MaterialTheme.colorScheme.surfaceContainerLow
                                            else -> Color.Transparent
                                        },
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = TtPeriods.DAY_LABELS.getOrElse(dayIndex) { "" }
                                        .removePrefix("周"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isToday) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                )
                                Text(
                                    text = "${date.monthValue}/${date.dayOfMonth}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight * TtPeriods.COUNT),
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val gridLine = if (dark) Color(0x22FFFFFF) else Color(0x14000000)
                            for (period in 1..TtPeriods.COUNT) {
                                val y = period * rowHeight.toPx()
                                drawLine(
                                    color = gridLine,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1f,
                                )
                            }
                        }
                        Row {
                            days.forEach { dayIndex ->
                                val courses = TtCalendar.coursesOnDay(
                                    courses = snapshot.c,
                                    termStart = termStart,
                                    week = week,
                                    dayIndex = dayIndex,
                                )
                                DayColumn(
                                    width = columnWidth,
                                    rowHeight = rowHeight,
                                    courses = courses,
                                    dark = dark,
                                    isToday = dayIndex == todayIndex,
                                    metrics = metrics,
                                    onCourseClick = onCourseClick,
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
private fun DayColumn(
    width: androidx.compose.ui.unit.Dp,
    rowHeight: androidx.compose.ui.unit.Dp,
    courses: List<Course>,
    dark: Boolean,
    isToday: Boolean,
    metrics: TimetableMetrics,
    onCourseClick: (Course) -> Unit,
) {
    val sorted = remember(courses) { courses.sortedBy { it.start } }
    Box(
        modifier = Modifier
            .width(width)
            .height(rowHeight * TtPeriods.COUNT)
            .background(
                if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.10f)
                else Color.Transparent,
            ),
    ) {
        sorted.forEach { course ->
            val paletteColor = if (dark) {
                TimetablePalette.darkOf(course.name)
            } else {
                TimetablePalette.lightOf(course.name)
            }
            val top = rowHeight * (course.start - 1)
            val span = (course.end - course.start + 1).coerceAtLeast(1)
            Box(
                modifier = Modifier
                    .padding(start = 1.5.dp, end = 1.5.dp, top = 2.dp)
                    .height(rowHeight * span - 4.dp)
                    .width(width - 3.dp)
                    .clip(RoundedCornerShape(metrics.blockCornerDp.dp))
                    .background(Color(paletteColor.container))
                    .clickable { onCourseClick(course) }
                    .padding(metrics.blockPaddingDp.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = course.name,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = if (metrics.courseNameMaxLines >= 3) 11.sp else 10.sp,
                        ),
                        fontWeight = FontWeight.SemiBold,
                        color = Color(paletteColor.content),
                        maxLines = metrics.courseNameMaxLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (course.room.isNotBlank() && span >= 2) {
                        Text(
                            text = TtCalendar.roomShort(course.room),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = Color(paletteColor.content).copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// 日视图（优化比例）
// =============================================================================

@Composable
private fun DayView(
    snapshot: TtSnapshot,
    termStart: LocalDate?,
    week: Int?,
    monday: LocalDate,
    selectedDay: Int,
    plan: TtPlan?,
    dark: Boolean,
    nowMillis: Long,
    onSelectDay: (Int) -> Unit,
    onCourseClick: (Course) -> Unit,
) {
    val date = remember(monday, selectedDay) { monday.plusDays(selectedDay.toLong()) }
    val sessions = remember(snapshot, termStart, week, selectedDay, monday, nowMillis) {
        TtCalendar.sessionsOn(
            courses = snapshot.c,
            termStart = termStart,
            date = date,
            nowMillis = nowMillis,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            TtPeriods.DAY_LABELS.forEachIndexed { index, label ->
                val active = index == selectedDay
                Surface(
                    onClick = { onSelectDay(index) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (active) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = label.removePrefix("周"),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CtShapeTokens.toolCard,
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
                    Text(
                        text = "当天没有课",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    sessions.forEach { session ->
                        DaySessionRow(
                            session = session,
                            dark = dark,
                            onClick = {
                                snapshot.c.firstOrNull { course ->
                                    course.name == session.name && course.day == selectedDay
                                }?.let(onCourseClick)
                            },
                        )
                    }
                }
                if (plan != null && sessions.isEmpty()) {
                    val next = plan.next
                    if (next != null) {
                        Text(
                            text = "下次 · ${next.label} ${next.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DaySessionRow(
    session: SessionInfo,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val pair = if (dark) {
        TimetablePalette.darkOf(session.name)
    } else {
        TimetablePalette.lightOf(session.name)
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(pair.container)),
            )
            Column(modifier = Modifier.width(46.dp)) {
                Text(
                    text = session.startText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = session.endText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(session.span)
                        if (session.room.isNotBlank()) append(" · ${TtCalendar.roomShort(session.room)}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (session.state) {
                SessionState.ONGOING -> CtPill(
                    text = "上课中",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )

                SessionState.NEXT -> CtPill(text = "待上")
                SessionState.DONE -> CtPill(text = "已结束")
            }
        }
    }
}

// =============================================================================
// 全部课程 / 导出卡片
// =============================================================================

@Composable
private fun AllCoursesCard(
    snapshot: TtSnapshot,
    termStart: LocalDate?,
    week: Int?,
    dark: Boolean,
) {
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
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "全部课程（${groups.size}）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(pair.container)),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
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

@Composable
private fun ExportCard(onExport: () -> Unit) {
    Surface(
        onClick = onExport,
        modifier = Modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                    text = "按周生成课表清单卡片",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// =============================================================================
// 课程详情（公开：供预览/页面复用）
// =============================================================================

/**
 * 课程详情内容（底部弹层内使用）：
 * 课程名 + 配色点 + 教师/教室/节次/时间/星期/周次/单双周 + 「写日程」入口。
 */
@Composable
fun TimetableCourseDetailContent(
    course: Course,
    displayWeek: Int?,
    onAddToCalendar: ((Course, Int, LocalDate) -> Unit)? = null,
    termStart: LocalDate? = null,
) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val pair = if (LocalCtDarkTheme.current) {
                TimetablePalette.darkOf(course.name)
            } else {
                TimetablePalette.lightOf(course.name)
            }
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(pair.container)),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = course.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TimetableDetail.detailRows(course, displayWeek).forEach { (label, value) ->
            Row {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(48.dp),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            text = "课表来自教务快照，只读",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val week = displayWeek
        val term = termStart
        if (onAddToCalendar != null) {
            if (week != null && term != null) {
                androidx.compose.material3.OutlinedButton(
                    onClick = { onAddToCalendar(course, week, term) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("添加到系统日历（本周）") }
            } else {
                Text(
                    text = "开学日未知，暂时无法写日程",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// =============================================================================
// 状态页
// =============================================================================

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        Text(
            text = "课表加载中…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorState(message: String, kind: TtFailureKind?, onRetry: () -> Unit) {
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(onClick = onRetry, shape = CtShapeTokens.pill) { Text("重试") }
    }
}

@Composable
private fun EmptyState(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.CalendarMonth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = "课表还是空的",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "教务课表同步后会自动出现",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRefresh, shape = CtShapeTokens.pill) { Text("刷新") }
    }
}

// =============================================================================
// 滑动切周手势
// =============================================================================

/**
 * 给课表内容挂「左右滑动切周」手势（跟手 → 松手吸附/飞出飞入）。
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


