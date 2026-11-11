@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.furina.classtools.ui.screens.power

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.power.PowerRange
import com.furina.classtools.data.power.PowerHistoryResult
import com.furina.classtools.data.power.PowerRepository
import com.furina.classtools.data.power.PowerRow
import com.furina.classtools.data.power.PowerRowResult
import com.furina.classtools.data.power.PowerRooms
import com.furina.classtools.data.power.PowerStats
import com.furina.classtools.data.power.PowerState
import com.furina.classtools.data.power.PowerTile
import com.furina.classtools.features.power.PowerCompareLogic
import com.furina.classtools.features.power.PowerCompareResult
import com.furina.classtools.data.power.RoomInfo
import com.furina.classtools.data.power.PowerPoint
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.sync.UserDataRepository
import com.furina.classtools.ui.components.CtPill
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
import com.furina.classtools.ui.theme.CtTokens
import com.furina.classtools.ui.theme.ctEmphasized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 宿舍电费页（hero 电量 + 24h/7d/30d 趋势 + 指标 tile；口径与网页/小程序一致）。
 *
 * 接线方式（由 Lead 在 MainNavHost 完成）：
 * ```kotlin
 * "power" -> PowerScreen(
 *     onBack = { navController.popBackStack() },
 *     repository = container.powerRepository,
 *     userDataRepository = container.userDataRepository,
 *     session = session,
 * )
 * ```
 *
 * 关键行为（web/AGENTS.md §4b-8/11/16/17）：
 * - 未选择宿舍 → **0 请求**；选择写入 userdata v6 `power` section（`sel/at/low`）；
 * - 趋势：点击/左右拖动命中最近真实采样点 → 竖线 + 圆点 + **右上角固定气泡**（绘图区外）；
 *   **禁止横向滚动**；空态不画图；
 * - 慢频房（13/16 号楼）：固定「日更」标记；30d 真实变化点 slowStats；估算「参考」；
 * - 慢更新：24h 变化 ≤2 次 → 「数据更新较慢 · 上次变化 …」/「数据未更新」；
 * - 隐私：仅展示楼栋+房间号；不显示姓名/学号；不导出、不分享。
 *
 * @param initialSelectedRv 预览注入：初始关注宿舍
 * @param initialRow 预览注入：电量行
 * @param initialHistories 预览注入：各区间采样
 * @param skipLoad 预览/测试：跳过仓库请求
 * @param initialError 预览注入：错误态
 */
@Composable
fun PowerScreen(
    onBack: () -> Unit,
    repository: PowerRepository,
    modifier: Modifier = Modifier,
    userDataRepository: UserDataRepository? = null,
    session: Session? = null,
    clock: () -> Long = System::currentTimeMillis,
    initialSelectedRv: String? = null,
    initialLow: Int? = null,
    initialRow: PowerRow? = null,
    initialHistories: Map<PowerRange, List<PowerPoint>> = emptyMap(),
    initialRange: PowerRange = PowerRange.H24,
    initialSlowRoom: Boolean = false,
    skipLoad: Boolean = false,
    initialError: String? = null,
) {
    val scope = rememberCoroutineScope()
    val sectionsFlow: kotlinx.coroutines.flow.StateFlow<JsonObject> =
        userDataRepository?.currentSections ?: remember { MutableStateFlow(JsonObject(emptyMap())) }
    val sections by sectionsFlow.collectAsState()
    val udPower = remember(sections) { sections["power"] as? JsonObject }
    val udSel = remember(udPower) { PowerRooms.normalizeSel(udPower?.get("sel").asPlainString()) }
    val udLow = remember(udPower) { (udPower?.get("low") as? JsonPrimitive)?.content?.toIntOrNull() }

    var sel by remember { mutableStateOf(PowerRooms.normalizeSel(initialSelectedRv)) }
    var low by remember { mutableStateOf(PowerStats.normLow(initialLow)) }
    LaunchedEffect(udSel) { if (udSel.isNotEmpty()) sel = PowerRooms.normalizeSel(udSel) }
    LaunchedEffect(udLow) { if (udLow != null) low = PowerStats.normLow(udLow) }

    var phase by remember(skipLoad, initialError) {
        mutableStateOf(
            when {
                initialError != null -> PowerPhase.ERROR
                PowerRooms.normalizeSel(initialSelectedRv).isBlank() -> PowerPhase.IDLE
                skipLoad -> PowerPhase.READY
                else -> PowerPhase.LOADING
            },
        )
    }
    var row by remember(initialRow) { mutableStateOf(initialRow) }
    var histories by remember { mutableStateOf(initialHistories.toMap()) }
    val initialBuilding = remember { PowerRooms.buildingOf(PowerRooms.normalizeSel(initialSelectedRv)) }
    var range by remember { mutableStateOf(PowerCompareLogic.normRange(initialRange, initialBuilding)) }
    var rangeBeforeHidden by remember { mutableStateOf<PowerRange?>(null) }
    var showCompareDetail by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var errorMessage by remember(initialError) { mutableStateOf(initialError.orEmpty()) }
    var historyError by remember { mutableStateOf<String?>(null) }
    var slowRoom by remember { mutableStateOf(initialSlowRoom) }
    var cursorIndex by remember { mutableStateOf<Int?>(null) }
    var showHousePicker by remember { mutableStateOf(false) }
    var showLowPicker by remember { mutableStateOf(false) }

    LaunchedEffect(sel, range, refreshToken, skipLoad) {
        if (skipLoad) return@LaunchedEffect
        if (session == null) {
            phase = PowerPhase.ERROR
            errorMessage = "请先完成身份验证后再查看电费"
            return@LaunchedEffect
        }
        if (sel.isBlank()) {
            phase = PowerPhase.IDLE
            return@LaunchedEffect
        }
        val force = refreshToken > 0
        historyError = null
        if (row == null || force) phase = PowerPhase.LOADING

        // 1) 当前值
        when (val result = repository.loadRow(sel, force)) {
            is PowerRowResult.Success -> row = result.row
            is PowerRowResult.Failure -> {
                if (row == null) {
                    errorMessage = result.message
                    phase = PowerPhase.ERROR
                    return@LaunchedEffect
                }
                historyError = result.message
            }
        }

        // 2) 24h（必需：慢频判定 + 区间/估算）
        var points24: List<PowerPoint> = histories[PowerRange.H24].orEmpty()
        when (val result = repository.loadHistory(sel, PowerRange.H24, force)) {
            is PowerHistoryResult.Success -> {
                points24 = result.points
                histories = histories + (PowerRange.H24 to result.points)
            }

            is PowerHistoryResult.Failure -> if (points24.isEmpty()) historyError = result.message
        }

        // 3) 慢频房判定（13/16 号楼 && 24h 变化 <6 次）
        slowRoom = PowerStats.isSlowRoom(sel, points24)

        // 4) 7d（估算必需）
        runCatchingHistory(
            repository = repository,
            sel = sel,
            rangeKey = PowerRange.D7,
            force = force,
            onSuccess = { histories = histories + (PowerRange.D7 to it) },
            onFailure = { if (histories[PowerRange.D7].isNullOrEmpty()) historyError = it },
        )

        // 5) 30d（慢频房必需；或用户当前选中 30d）
        if (slowRoom || range == PowerRange.D30) {
            runCatchingHistory(
                repository = repository,
                sel = sel,
                rangeKey = PowerRange.D30,
                force = force,
                onSuccess = { histories = histories + (PowerRange.D30 to it) },
                onFailure = { if (histories[PowerRange.D30].isNullOrEmpty()) historyError = it },
            )
        }

        phase = PowerPhase.READY
    }

    // 13/16 号楼主楼：隐藏 24h；切换宿舍时归位到 7d，切回实时房恢复进入前档位（不覆盖用户显式选择）。
    val building = remember(sel) { PowerRooms.buildingOf(sel) }
    LaunchedEffect(building) {
        if (!PowerCompareLogic.showRange(range, building)) {
            rangeBeforeHidden = range
            range = PowerCompareLogic.normRange(range, building)
        } else {
            rangeBeforeHidden?.let { previous ->
                if (PowerCompareLogic.showRange(previous, building)) range = previous
                rangeBeforeHidden = null
            }
        }
    }
    val visibleRanges = remember(building) {
        PowerCompareLogic.showRange(PowerRange.entries, building)
    }
    // 同期比较：合并已加载窗口（当前仓储无 14d/70d 隐藏窗口能力，数据不足时自动隐藏徽标）。
    val compareHistory = remember(histories) {
        histories.values.flatten().distinctBy { it.t }.sortedBy { it.t }
    }
    val compareResult = remember(range, compareHistory, refreshToken) {
        PowerCompareLogic.compare(range, compareHistory, clock())
    }

    val rowValue = row
    val state = PowerStats.stateOf(rowValue, low)
    val history24 = histories[PowerRange.H24].orEmpty()
    val history7 = histories[PowerRange.D7].orEmpty()
    val history30 = histories[PowerRange.D30].orEmpty()
    val selectedHistory = histories[range].orEmpty()

    val estimate = remember(rowValue, history24, history7, history30, slowRoom) {
        if (slowRoom) {
            PowerStats.estimateSlow(history30, rowValue)
        } else {
            PowerStats.estimate(history24, history7, rowValue)
        }
    }
    val slowUpdate = remember(history24) { PowerStats.slowUpdate(history24) }
    val rangeStats = remember(selectedHistory, rowValue) { PowerStats.deriveRange(selectedHistory, rowValue) }
    val trend = remember(selectedHistory, range) { PowerStats.trendLine(selectedHistory, PowerStats.trendLimit(range)) }
    val changeDots = remember(trend, slowRoom) {
        if (slowRoom) PowerStats.changeDots(trend.points) else emptyList()
    }
    val tiles = remember(rowValue, estimate, rangeStats, range, low, slowUpdate) {
        PowerStats.tilesOf(rowValue, estimate, rangeStats, range, low, slowUpdate)
    }
    val bill = remember(rangeStats) { PowerStats.billOf(rangeStats) }
    val ringArc = remember(rowValue, estimate) { PowerStats.ringArc(rowValue, estimate) }
    val noUpdate = remember(range, selectedHistory) { PowerStats.noUpdateText(range, selectedHistory) }

    LaunchedEffect(trend) { cursorIndex = null }

    val cursorText: String? = cursorIndex
        ?.let { trend.points.getOrNull(it) }
        ?.let { "剩余 ${PowerStats.fmtQ(it.q)} 度 · ${PowerStats.cursorLabel(it.t, range)}" }

    fun chooseRoom(rv: String) {
        val normalized = PowerRooms.normalizeSel(rv)
        if (normalized.isEmpty()) return
        showHousePicker = false
        if (normalized == sel) return
        sel = normalized
        row = null
        histories = emptyMap()
        cursorIndex = null
        slowRoom = false
        phase = PowerPhase.LOADING
        scope.launch { writePowerSection(userDataRepository, normalized, low, clock()) }
    }

    fun chooseLow(value: Int) {
        showLowPicker = false
        val normalized = PowerStats.normLow(value)
        if (normalized == low) return
        low = normalized
        if (sel.isNotEmpty()) {
            scope.launch { writePowerSection(userDataRepository, sel, normalized, clock()) }
        }
    }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            CtTopBar(
                title = "宿舍电费",
                subtitle = if (sel.isBlank()) "关注宿舍 · 电量与趋势" else PowerRooms.labelOf(sel),
                onBack = onBack,
                actions = {
                    if (sel.isNotBlank()) {
                        IconButton(onClick = { refreshToken += 1 }) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "刷新电量",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )

            when (phase) {
                PowerPhase.IDLE -> IdleGuide(
                    onPick = { showHousePicker = true },
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )

                PowerPhase.LOADING -> LoadingState()
                PowerPhase.ERROR -> ErrorState(
                    message = errorMessage,
                    onRetry = { refreshToken += 1 },
                )

                PowerPhase.READY -> ReadyContent(
                    row = rowValue,
                    sel = sel,
                    low = low,
                    state = state,
                    ringArc = ringArc,
                    estimateDaysText = PowerStats.daysMain(estimate),
                    slowRoom = slowRoom,
                    slowUpdateText = slowUpdate.text,
                    range = range,
                    cursorText = cursorText,
                    trend = trend,
                    changeDots = changeDots,
                    cursorIndex = cursorIndex,
                    rangeStats = rangeStats,
                    rangeStatsForBill = bill,
                    tiles = tiles,
                    noUpdateText = noUpdate,
                    historyError = historyError,
                    visibleRanges = visibleRanges,
                    compare = compareResult,
                    onCompareDetail = { showCompareDetail = true },
                    onRangeChange = { range = it },
                    onCursor = { cursorIndex = it },
                    onPickHouse = { showHousePicker = true },
                    onLowClick = { showLowPicker = true },
                    onRetryHistory = { refreshToken += 1 },
                )
            }
        }

        if (showHousePicker) {
            HousePickerSheet(
                selectedRv = sel,
                onPick = { chooseRoom(it) },
                onDismiss = { showHousePicker = false },
            )
        }
        if (showLowPicker) {
            LowThresholdSheet(
                current = low,
                onPick = { chooseLow(it) },
                onDismiss = { showLowPicker = false },
            )
        }
        if (showCompareDetail && compareResult.available) {
            AlertDialog(
                onDismissRequest = { showCompareDetail = false },
                title = { Text(compareResult.label) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = compareResult.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = buildString {
                                append("当前周期用电 ")
                                append(PowerStats.fmtQ(compareResult.currentUse ?: 0.0))
                                append(" 度 · 上一周期 ")
                                append(PowerStats.fmtQ(compareResult.previousUse ?: 0.0))
                                append(" 度")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "仅统计工具记录的下降用电，不插值；历史数据不足时不显示比较。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCompareDetail = false }) { Text("知道了") }
                },
            )
        }
    }
}

// -----------------------------------------------------------------------------
// 状态页
// -----------------------------------------------------------------------------

private enum class PowerPhase { IDLE, LOADING, READY, ERROR }

@Composable
private fun IdleGuide(onPick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Bolt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = "关注一个宿舍，查看剩余电量",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "选择保存在账号里，换设备登录会同步；未选择时不请求任何电量数据。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FilledTonalButton(onClick = onPick, shape = CtShapeTokens.pill) {
            Text("选择宿舍")
        }
        PrivacyFooter()
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        Text(
            text = "电量加载中…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "电费加载失败",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = message.ifBlank { "请稍后重试" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FilledTonalButton(onClick = onRetry, shape = CtShapeTokens.pill) { Text("重试") }
    }
}

// -----------------------------------------------------------------------------
// 正常内容
// -----------------------------------------------------------------------------

@Composable
private fun ReadyContent(
    row: PowerRow?,
    sel: String,
    low: Int,
    state: PowerState,
    ringArc: Float,
    estimateDaysText: String,
    slowRoom: Boolean,
    slowUpdateText: String,
    range: PowerRange,
    cursorText: String?,
    trend: com.furina.classtools.data.power.TrendLine,
    changeDots: List<com.furina.classtools.data.power.TrendPoint>,
    cursorIndex: Int?,
    rangeStats: com.furina.classtools.data.power.RangeStats,
    rangeStatsForBill: com.furina.classtools.data.power.BillInfo,
    tiles: List<PowerTile>,
    noUpdateText: String,
    historyError: String?,
    visibleRanges: List<PowerRange>,
    compare: PowerCompareResult?,
    onCompareDetail: () -> Unit,
    onRangeChange: (PowerRange) -> Unit,
    onCursor: (Int?) -> Unit,
    onPickHouse: () -> Unit,
    onLowClick: () -> Unit,
    onRetryHistory: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PickRoomCard(sel = sel, onPick = onPickHouse)
        HeroCard(
            row = row,
            state = state,
            ringArc = ringArc,
            estimateDaysText = estimateDaysText,
            slowRoom = slowRoom,
        )
        TrendSection(
            range = range,
            slowRoom = slowRoom,
            rangeStats = rangeStats,
            trend = trend,
            changeDots = changeDots,
            cursorIndex = cursorIndex,
            cursorText = cursorText,
            slowUpdateText = slowUpdateText,
            noUpdateText = noUpdateText,
            historyError = historyError,
            ranges = visibleRanges,
            onRangeChange = onRangeChange,
            onCursor = onCursor,
            onRetry = onRetryHistory,
        )
        TilesSection(
            tiles = tiles,
            bill = rangeStatsForBill,
            compare = compare,
            onCompareClick = onCompareDetail,
            onTileClick = { tile ->
                if (tile.clickable) onLowClick()
            },
        )
        PrivacyFooter()
        Spacer(modifier = Modifier.navigationBarsPadding().height(8.dp))
    }
}

@Composable
private fun PickRoomCard(sel: String, onPick: () -> Unit) {
    Surface(
        onClick = onPick,
        modifier = Modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Apartment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "关注宿舍",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (sel.isBlank()) "未选择" else PowerRooms.labelOf(sel),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "更换 ›",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun HeroCard(
    row: PowerRow?,
    state: PowerState,
    ringArc: Float,
    estimateDaysText: String,
    slowRoom: Boolean,
) {
    val brand = com.furina.classtools.ui.theme.LocalCtBrandColors.current
    val stateColor = when (state) {
        PowerState.LOW -> brand.powerLow
        PowerState.WARN -> brand.powerWarn
        PowerState.OK -> brand.powerPositive
        PowerState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CtShapeTokens.hero,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RingGauge(progress = ringArc, accent = stateColor) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = PowerStats.fmtMain(row?.q),
                            style = MaterialTheme.ctEmphasized.displaySmallEmphasized,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = row?.unit ?: "度",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CtPill(
                            text = PowerStats.stateText(state),
                            containerColor = stateColor.copy(alpha = 0.16f),
                            contentColor = stateColor,
                        )
                        if (slowRoom) {
                            CtPill(text = "日更", containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        }
                    }
                    Text(
                        text = "剩余电量",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "估算可用 $estimateDaysText 天",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "更新 ${PowerStats.updText(row?.updatedAt).ifBlank { "—" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RingGauge(
    progress: Float,
    accent: Color,
    content: @Composable () -> Unit,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier = Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2
            drawArc(
                color = track,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        accent.copy(alpha = 0.55f),
                        accent,
                        accent.copy(alpha = 0.85f),
                    ),
                ),
                startAngle = 135f,
                sweepAngle = 270f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

@Composable
private fun TrendSection(
    range: PowerRange,
    slowRoom: Boolean,
    rangeStats: com.furina.classtools.data.power.RangeStats,
    trend: com.furina.classtools.data.power.TrendLine,
    changeDots: List<com.furina.classtools.data.power.TrendPoint>,
    cursorIndex: Int?,
    cursorText: String?,
    slowUpdateText: String,
    noUpdateText: String,
    historyError: String?,
    ranges: List<PowerRange>,
    onRangeChange: (PowerRange) -> Unit,
    onCursor: (Int?) -> Unit,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = range.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (slowRoom) {
                    CtPill(text = "日更", containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                }
                if (rangeStats.recharge) {
                    CtPill(text = "期间有充值", containerColor = MaterialTheme.colorScheme.secondaryContainer)
                }
            }
            if (slowUpdateText.isNotBlank() && !slowRoom) {
                Text(
                    text = slowUpdateText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            // 区间分段 + 固定右上角气泡（气泡固定在绘图区外，不随手指移动）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CtSegmentedButtons(
                    options = ranges.map { CtSegmentOption(key = it.key, label = it.label) },
                    selectedKey = range.key,
                    onSelect = { onRangeChange(PowerRange.fromKey(it)) },
                    showCheckIcon = false,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = cursorText ?: "点按图表查看采样点",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (cursorText != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(150.dp),
                )
            }
            if (ranges.size < PowerRange.entries.size) {
                Text(
                    text = "13/16 号楼为日更数据，不提供 24h 趋势",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (historyError != null && trend.empty) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = historyError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetry) { Text("重试") }
                }
            } else {
                PowerTrendChart(
                    trend = trend,
                    range = range,
                    slow = slowRoom,
                    changeDots = changeDots,
                    cursorIndex = cursorIndex,
                    onCursor = onCursor,
                    yLabelHi = PowerStats.fmtQ(trend.hi),
                    yLabelMid = PowerStats.fmtQ(trend.mid),
                    yLabelLo = PowerStats.fmtQ(trend.lo),
                    legendStart = PowerStats.fmtQ(trend.points.firstOrNull()?.q),
                    legendCurrent = PowerStats.fmtQ(trend.points.lastOrNull()?.q),
                    legendDrop = PowerStats.fmtDrop(rangeStats.drop),
                )
            }

            if (noUpdateText.isNotBlank()) {
                Text(
                    text = noUpdateText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (trend.flat && !trend.empty) {
                Text(
                    text = "该区间电量无明显变化",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TilesSection(
    tiles: List<PowerTile>,
    bill: com.furina.classtools.data.power.BillInfo,
    compare: PowerCompareResult?,
    onCompareClick: () -> Unit,
    onTileClick: (PowerTile) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEachIndexed { rowIndex, rowTiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowTiles.forEachIndexed { colIndex, tile ->
                    Tile(
                        tile = tile,
                        modifier = Modifier.weight(1f),
                        compare = if (rowIndex == 0 && colIndex == 0) compare else null,
                        onCompareClick = onCompareClick,
                        onClick = { onTileClick(tile) },
                    )
                }
                if (rowTiles.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
        BillCard(bill = bill)
    }
}

@Composable
private fun Tile(
    tile: PowerTile,
    modifier: Modifier,
    onClick: () -> Unit,
    compare: PowerCompareResult? = null,
    onCompareClick: () -> Unit = {},
) {
    val container = if (tile.highlight) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    else MaterialTheme.colorScheme.surfaceContainerLow
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = container,
        enabled = tile.clickable,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = tile.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (tile.ref) {
                    Text(
                        text = "参考",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                if (tile.clickable) {
                    Text(
                        text = "›",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = tile.value,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (tile.unit.isNotBlank()) {
                    Text(
                        text = " ${tile.unit}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
            if (tile.sub.isNotBlank()) {
                Text(
                    text = tile.sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val availableCompare = compare?.takeIf { it.available }
            if (availableCompare != null) {
                val delta = availableCompare.percent ?: 0.0
                val increase = delta > 0.05
                val tone = if (increase) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                Row(
                    modifier = Modifier
                        .heightIn(min = 24.dp)
                        .clickable(onClick = onCompareClick)
                        .semantics {
                            // 颜色只区分增减，读屏需明确方向（无障碍：不依赖颜色传达信息）。
                            val direction = when {
                                delta > 0.05 -> "上升"
                                delta < -0.05 -> "下降"
                                else -> "持平"
                            }
                            contentDescription = "${availableCompare.label} $direction ${
                                PowerCompareLogic.formatPercent(kotlin.math.abs(delta))
                            }，点击查看说明"
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = buildString {
                            append(if (delta > 0.05) "↑" else if (delta < -0.05) "↓" else "→")
                            append(' ')
                            append(availableCompare.label)
                            append(' ')
                            append(PowerCompareLogic.formatPercent(delta))
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = tone,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun BillCard(bill: com.furina.classtools.data.power.BillInfo) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BillLine("电价", bill.elecText)
            BillLine("区间电费", bill.feeText)
            BillLine("免费额度", bill.freeText)
            BillLine("水费", bill.waterText)
            if (bill.hasDeduct) {
                Text(
                    text = bill.deductText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun BillLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PrivacyFooter() {
    Text(
        text = "仅显示关注宿舍的楼栋与房间号 · 不显示姓名/学号 · 不导出、不分享",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}

// -----------------------------------------------------------------------------
// 弹层：关注宿舍 / 低电阈值
// -----------------------------------------------------------------------------

@Composable
private fun HousePickerSheet(
    selectedRv: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var building by remember { mutableStateOf(PowerRooms.buildingOf(selectedRv).ifBlank { PowerRooms.BUILDINGS.first() }) }
    CtBottomSheet(onDismissRequest = onDismiss) {
        CtBottomSheetHeader(
            title = "关注宿舍",
            subtitle = "选择保存在账号里 · 只显示楼栋与房间号",
        )
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CtChipGroup(
                options = PowerRooms.BUILDINGS.map { CtChipOption(key = it, label = it) },
                selectedKeys = setOf(building),
                onToggle = { building = it },
                multiSelect = false,
            )
            val rooms = PowerRooms.roomsOfBuilding(building)
            rooms.chunked(4).forEach { chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    chunk.forEach { room ->
                        RoomButton(
                            room = room,
                            selected = room.rv == selectedRv,
                            onClick = { onPick(room.rv) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(4 - chunk.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
        CtBottomSheetFooter(
            primaryText = "完成",
            onPrimary = onDismiss,
            secondaryText = "取消",
            onSecondary = onDismiss,
        )
    }
}

@Composable
private fun RoomButton(room: RoomInfo, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(text = room.room, style = MaterialTheme.typography.titleSmall)
            if (selected) {
                Text(text = " ✓", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun LowThresholdSheet(
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    CtBottomSheet(onDismissRequest = onDismiss) {
        CtBottomSheetHeader(title = "低电阈值", subtitle = "低于该电量标红；低于 2 倍为偏低")
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PowerStats.LOW_OPTIONS.chunked(4).forEach { chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    chunk.forEach { value ->
                        val selected = value == current
                        Surface(
                            onClick = { onPick(value) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                        ) {
                            Text(
                                text = "$value",
                                style = MaterialTheme.typography.titleSmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                    repeat(4 - chunk.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
        CtBottomSheetFooter(
            primaryText = "完成",
            onPrimary = onDismiss,
            secondaryText = "取消",
            onSecondary = onDismiss,
        )
    }
}

// -----------------------------------------------------------------------------
// 工具
// -----------------------------------------------------------------------------

/** 写入 userdata v6 `power` section（sel/at/low；`at` 由 UserdataMerge.localView 镜像到顶层 at.power）。 */
private suspend fun writePowerSection(
    repository: UserDataRepository?,
    sel: String,
    low: Int,
    now: Long,
) {
    if (repository == null) return
    runCatching {
        val current = repository.currentSections.first()
        val next = LinkedHashMap<String, JsonElement>(current)
        next["power"] = buildJsonObject {
            put("sel", sel)
            put("at", now)
            put("low", PowerStats.normLow(low))
        }
        repository.updateCurrent(JsonObject(next))
    }
}

/** 读取历史：失败返回 null 并回传错误文案（供局部错误提示/重试）。 */
private suspend fun runCatchingHistory(
    repository: PowerRepository,
    sel: String,
    rangeKey: PowerRange,
    force: Boolean,
    onSuccess: (List<PowerPoint>) -> Unit,
    onFailure: (String) -> Unit,
) {
    when (val result = repository.loadHistory(sel, rangeKey, force)) {
        is PowerHistoryResult.Success -> onSuccess(result.points)
        is PowerHistoryResult.Failure -> onFailure(result.message)
    }
}

/** 安全读取 JsonPrimitive 字符串（不依赖外部扩展）。 */
private fun JsonElement?.asPlainString(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    return if (primitive.isString) primitive.content else null
}





