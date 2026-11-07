@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.furina.classtools.ui.screens.timetable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.CtTopBar
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.expressive.CtBottomSheet
import com.furina.classtools.ui.components.expressive.CtSegmentOption
import com.furina.classtools.ui.components.expressive.CtSegmentedButtons
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.CtShapeTokens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

/**
 * 课表页（周/日视图 + 今日高亮 + 下一节；契约 docs/BACKEND_CONTRACT.md §7.4）。
 *
 * 接线方式（由 Lead 在 MainNavHost 完成）：
 * ```kotlin
 * "tt" -> TimetableViewScreen(
 *     onBack = { navController.popBackStack() },
 *     repository = container.timetableRepository,
 * )
 * ```
 *
 * - 数据：`TimetableRepository`（site_assets id=tt → 本机缓存；6h TTL）；
 * - 周/日视图只由分段按钮切换（不横向滑动切视图）；上一周/下一周按钮切换周次；
 * - compact 单列（课表网格可横向滚动）；≥600dp 左侧网格 + 右侧「下一节 / 全部课程」；
 * - 四态：加载 / 空（课表为空）/ 错误 / 正常。
 *
 * @param initialSnapshot 预览注入：非空时直接渲染该快照
 * @param initialError 预览注入：非空时直接显示错误态
 * @param skipLoad 预览/测试：跳过仓库请求
 * @param nowMillis 预览/测试：固定「现在」（默认系统时间）
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
    skipLoad: Boolean = false,
    nowMillis: Long = System.currentTimeMillis(),
    /** v2：写系统日历（课程 → 指定周/开学日；null = 不显示入口）。 */
    onAddToCalendar: ((Course, Int, LocalDate) -> Unit)? = null,
) {
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

    val zone = remember { ZoneId.systemDefault() }

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

    var weekOffset by remember { mutableIntStateOf(0) }
    var viewMode by remember { mutableStateOf(if (initialDayView) TtViewMode.DAY else TtViewMode.WEEK) }
    var selectedDay by remember { mutableIntStateOf(todayIndex) }
    var selectedCourse by remember { mutableStateOf<Course?>(null) }

    val currentWeek = plan?.week
    val displayWeek = currentWeek?.let { max(1, it + weekOffset) }

    fun reload() {
        refreshToken += 1
    }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            CtTopBar(
                title = "课表",
                subtitle = "周/日视图 · 下一节",
                onBack = onBack,
                actions = {
                    if (fromCache) CtPill(text = "本机缓存")
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
                    } else {
                        val weekTitle = TtCalendar.weekTitle(termStart, displayWeek)
                        val monday = displayWeek?.let { TtCalendar.mondayOf(termStart, it) }
                            ?: today.minusDays(todayIndex.toLong())

                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val expanded = maxWidth >= 600.dp
                            val contentPadding = if (expanded) 24.dp else 16.dp
                            if (expanded) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = contentPadding),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(0.66f)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        WeekHeader(
                                            weekTitle = weekTitle,
                                            showThisWeek = weekOffset != 0,
                                            canGoPrev = displayWeek != null,
                                            canGoNext = displayWeek != null,
                                            viewMode = viewMode,
                                            onPrev = { if (weekOffset > -520) weekOffset -= 1 },
                                            onNext = { if (weekOffset < 520) weekOffset += 1 },
                                            onThisWeek = { weekOffset = 0 },
                                            onViewMode = { viewMode = it },
                                        )
                                        TimetableBody(
                                            snapshot = snapshot,
                                            termStart = termStart,
                                            displayWeek = displayWeek,
                                            monday = monday,
                                            todayIndex = todayIndex,
                                            plan = plan,
                                            viewMode = viewMode,
                                            selectedDay = selectedDay,
                                            onSelectDay = { selectedDay = it },
                                            onCourseClick = { selectedCourse = it },
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                    Column(
                                        modifier = Modifier
                                            .weight(0.34f)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        NextSessionCard(next = plan?.next)
                                        AllCoursesCard(
                                            snapshot = snapshot,
                                            termStart = termStart,
                                            week = displayWeek,
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = contentPadding),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    WeekHeader(
                                        weekTitle = weekTitle,
                                        showThisWeek = weekOffset != 0,
                                        canGoPrev = displayWeek != null,
                                        canGoNext = displayWeek != null,
                                        viewMode = viewMode,
                                        onPrev = { if (weekOffset > -520) weekOffset -= 1 },
                                        onNext = { if (weekOffset < 520) weekOffset += 1 },
                                        onThisWeek = { weekOffset = 0 },
                                        onViewMode = { viewMode = it },
                                    )
                                    NextSessionCard(next = plan?.next)
                                    TimetableBody(
                                        snapshot = snapshot,
                                        termStart = termStart,
                                        displayWeek = displayWeek,
                                        monday = monday,
                                        todayIndex = todayIndex,
                                        plan = plan,
                                        viewMode = viewMode,
                                        selectedDay = selectedDay,
                                        onSelectDay = { selectedDay = it },
                                        onCourseClick = { selectedCourse = it },
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        selectedCourse?.let { course ->
            CtBottomSheet(onDismissRequest = { selectedCourse = null }) {
                CourseDetailSheet(
                    course = course,
                    termStart = termStart,
                    displayWeek = displayWeek,
                    onAddToCalendar = onAddToCalendar,
                )
            }
        }

        if (phase == TtPhase.READY && plan != null && plan.todayState != TodayState.NODATA) {
            // 只在底部留白：内容自身可滚动，避免与导航栏重叠。
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

private enum class TtPhase { LOADING, READY, ERROR }

private enum class TtViewMode { WEEK, DAY }

@Composable
private fun WeekHeader(
    weekTitle: String,
    showThisWeek: Boolean,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    viewMode: TtViewMode,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit,
    onViewMode: (TtViewMode) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev, enabled = canGoPrev) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "上一周",
                    )
                }
                Text(
                    text = weekTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
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
                    selectedKey = if (viewMode == TtViewMode.WEEK) "week" else "day",
                    onSelect = { onViewMode(if (it == "week") TtViewMode.WEEK else TtViewMode.DAY) },
                    showCheckIcon = false,
                )
            }
        }
    }
}

@Composable
private fun TimetableBody(
    snapshot: TtSnapshot,
    termStart: LocalDate?,
    displayWeek: Int?,
    monday: LocalDate,
    todayIndex: Int,
    plan: TtPlan?,
    viewMode: TtViewMode,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit,
    onCourseClick: (Course) -> Unit,
) {
    when (viewMode) {
        TtViewMode.WEEK -> WeekGrid(
            snapshot = snapshot,
            termStart = termStart,
            week = displayWeek,
            monday = monday,
            todayIndex = todayIndex,
            onCourseClick = onCourseClick,
        )

        TtViewMode.DAY -> DayView(
            snapshot = snapshot,
            termStart = termStart,
            week = displayWeek,
            monday = monday,
            selectedDay = selectedDay,
            onSelectDay = onSelectDay,
            plan = plan,
            onCourseClick = onCourseClick,
        )
    }
}

@Composable
private fun WeekGrid(
    snapshot: TtSnapshot,
    termStart: LocalDate?,
    week: Int?,
    monday: LocalDate,
    todayIndex: Int,
    onCourseClick: (Course) -> Unit,
) {
    val rowHeight = 56.dp
    val labelWidth = 44.dp
    val headerHeight = 42.dp
    val palette = rememberCoursePalette()
    // 周末两天都无课 → 隐藏周末列（与 web/mp 一致，减少手机横向滚动）；有课则保留 7 列。
    val weekendEmpty = TtCalendar.coursesOnDay(snapshot.c, termStart, week, 5).isEmpty() &&
        TtCalendar.coursesOnDay(snapshot.c, termStart, week, 6).isEmpty()
    val visibleDays: List<Int> = if (weekendEmpty) (0..4).toList() else (0..6).toList()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val minColumn = 64.dp
            val available = maxWidth - labelWidth - 8.dp
            val columnWidth = maxOf(minColumn, available / visibleDays.size)
            val needHorizontalScroll = columnWidth * visibleDays.size > available
            val hScroll = rememberScrollState()

            Row(modifier = Modifier.fillMaxWidth()) {
                // 左侧节次标签列
                Column(modifier = Modifier.width(labelWidth)) {
                    Spacer(modifier = Modifier.height(headerHeight))
                    for (period in 1..TtPeriods.COUNT) {
                        Column(
                            modifier = Modifier
                                .height(rowHeight)
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "$period",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = TtPeriods.startText(period),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(if (needHorizontalScroll) Modifier.horizontalScroll(hScroll) else Modifier),
                ) {
                    // 表头：周一~周日（周末无课时不显示周末列）+ 日期
                    Row {
                        visibleDays.forEach { index ->
                            val label = TtPeriods.DAY_LABELS[index]
                            val date = monday.plusDays(index.toLong())
                            val isToday = index == todayIndex
                            Column(
                                modifier = Modifier
                                    .width(columnWidth)
                                    .height(headerHeight)
                                    .background(
                                        if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                        else Color.Transparent,
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isToday) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                )
                                Text(
                                    text = "${date.monthValue}/${date.dayOfMonth}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                )
                            }
                        }
                    }

                    // 网格主体：横线 + 今日列底 + 课程块
                    Box(modifier = Modifier.fillMaxWidth().height(rowHeight * TtPeriods.COUNT)) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val gridLine = Color(0x14000000)
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
                            visibleDays.forEach { dayIndex ->
                                val isToday = dayIndex == todayIndex
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
                                    palette = palette,
                                    isToday = isToday,
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
    width: Dp,
    rowHeight: Dp,
    courses: List<Course>,
    palette: List<CourseColor>,
    isToday: Boolean,
    onCourseClick: (Course) -> Unit,
) {
    val grouped = remember(courses) {
        courses.sortedBy { it.start }.mapIndexed { index, course -> index to course }
    }
    Box(
        modifier = Modifier
            .width(width)
            .height(rowHeight * TtPeriods.COUNT)
            .background(
                if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
                else Color.Transparent,
            ),
    ) {
        grouped.forEach { (index, course) ->
            val top = rowHeight * (course.start - 1)
            val span = (course.end - course.start + 1).coerceAtLeast(1)
            val color = palette[index % palette.size]
            Box(
                modifier = Modifier
                    .padding(start = 2.dp, end = 2.dp, top = 2.dp)
                    .height(rowHeight * span - 4.dp)
                    .width(width - 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.container)
                    .clickable { onCourseClick(course) }
                    .padding(4.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = course.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = color.content,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (course.room.isNotBlank()) {
                        Text(
                            text = TtCalendar.roomShort(course.room),
                            style = MaterialTheme.typography.labelSmall,
                            color = color.content.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayView(
    snapshot: TtSnapshot,
    termStart: LocalDate?,
    week: Int?,
    monday: LocalDate,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit,
    plan: TtPlan?,
    onCourseClick: (Course) -> Unit,
) {
    val date = remember(monday, selectedDay) { monday.plusDays(selectedDay.toLong()) }
    val sessions = remember(snapshot, termStart, week, selectedDay, monday) {
        TtCalendar.sessionsOn(
            courses = snapshot.c,
            termStart = termStart,
            date = date,
            nowMillis = System.currentTimeMillis(),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${date.monthValue}月${date.dayOfMonth}日 · ${TtPeriods.DAY_LABELS[selectedDay]}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (sessions.isEmpty()) {
                    Text(
                        text = "当天没有课",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    sessions.forEach { session ->
                        DaySessionRow(session = session, onClick = {
                            // 日视图点击：按名字找回对应课程对象（同一名字取第一节）
                            snapshot.c.firstOrNull { it.name == session.name }?.let(onCourseClick)
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun DaySessionRow(session: SessionInfo, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.width(52.dp)) {
                Text(
                    text = session.startText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = session.endText,
                    style = MaterialTheme.typography.labelSmall,
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
                        if (session.room.isNotBlank()) append(" · ${session.room}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun NextSessionCard(next: SessionInfo?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(
                    text = if (next == null) "—" else "下一节",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Text(
                    text = next?.startText ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = next?.name ?: "近期暂无课程",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (next != null) {
                    Text(
                        text = buildString {
                            append(next.label)
                            append(" · ")
                            append(next.startText)
                            append("-")
                            append(next.endText)
                            if (next.room.isNotBlank()) append(" · ${next.room}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AllCoursesCard(
    snapshot: TtSnapshot,
    termStart: LocalDate?,
    week: Int?,
) {
    val merged = remember(snapshot, termStart, week) {
        snapshot.c
            .filter { TtCalendar.isActive(it, week) }
            .groupBy { it.name }
            .map { (name, list) ->
                val first = list.first()
                MergedCourse(
                    name = name,
                    teacher = first.teacher,
                    room = TtCalendar.roomShort(first.room),
                    count = list.size,
                    days = list.map { it.day }.distinct().sorted(),
                )
            }
            .sortedBy { it.name }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "全部课程（${merged.size}）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (merged.isEmpty()) {
                Text(
                    text = "本周无课",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                merged.forEach { course ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = course.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = buildString {
                                if (course.teacher.isNotBlank()) append(course.teacher).append(" · ")
                                append(course.count).append(" 节")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private data class MergedCourse(
    val name: String,
    val teacher: String,
    val room: String,
    val count: Int,
    val days: List<Int>,
)

@Composable
private fun CourseDetailSheet(
    course: Course,
    termStart: LocalDate?,
    displayWeek: Int?,
    onAddToCalendar: ((Course, Int, LocalDate) -> Unit)? = null,
) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = course.name,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = buildString {
                append(TtPeriods.DAY_LABELS.getOrElse(course.day) { "" })
                append(" ")
                append(if (course.end > course.start) "第${course.start}-${course.end}节" else "第${course.start}节")
                append(" · ")
                append(TtPeriods.startText(course.start))
                append("-")
                append(TtPeriods.endText(course.end))
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (course.teacher.isNotBlank() || course.room.isNotBlank()) {
            Text(
                text = listOf(course.teacher, course.room).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = buildString {
                append("周次：")
                append(course.weeks.ifBlank { "全学期" })
                when (course.parity) {
                    "odd" -> append(" · 单周")
                    "even" -> append(" · 双周")
                }
                displayWeek?.let { append(" · 当前第 $it 周") }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "课表来自教务快照，只读",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onAddToCalendar != null) {
            val week = displayWeek
            val term = termStart
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
    // 三态文案：网络失败 / 未登录或空数据 / 解析失败（Debug 无 token 也不应显示「格式异常」）。
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

/** 课程色板：容器色 + 内容色（与主题协调的 6 组；按课程名散列分配）。 */
private data class CourseColor(val container: Color, val content: Color)

@Composable
private fun rememberCoursePalette(): List<CourseColor> {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        listOf(
            CourseColor(scheme.primaryContainer, scheme.onPrimaryContainer),
            CourseColor(scheme.secondaryContainer, scheme.onSecondaryContainer),
            CourseColor(scheme.tertiaryContainer, scheme.onTertiaryContainer),
            CourseColor(Color(0xFFE3F0E7), Color(0xFF1E4638)),
            CourseColor(Color(0xFFE8EDF6), Color(0xFF243B52)),
            CourseColor(Color(0xFFF6EDDE), Color(0xFF4A3A22)),
        )
    }
}






