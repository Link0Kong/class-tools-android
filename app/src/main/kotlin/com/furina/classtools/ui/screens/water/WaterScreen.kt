@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.furina.classtools.ui.screens.water

import com.furina.classtools.ui.components.light.ctLightMaterial
import com.furina.classtools.ui.theme.CtLightScene
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Opacity
import com.furina.classtools.ui.components.light.CtLightAlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.power.PowerRooms
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.sync.UserDataRepository
import com.furina.classtools.data.water.UserDataWaterRepository
import com.furina.classtools.features.water.WaterCalculator
import com.furina.classtools.features.water.WaterCharge
import com.furina.classtools.features.water.WaterCodec
import com.furina.classtools.features.water.WaterMonth
import com.furina.classtools.features.water.WaterMonthStats
import com.furina.classtools.features.water.WaterMutator
import com.furina.classtools.features.water.WaterRecord
import com.furina.classtools.features.water.WaterRecordReview
import com.furina.classtools.features.water.WaterRoomState
import com.furina.classtools.features.water.WaterRules
import com.furina.classtools.features.water.WaterState
import com.furina.classtools.ui.components.CtLoading
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.ctTopBarRaw
import com.furina.classtools.ui.components.EmptyState
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.tool.CtToolCard
import com.furina.classtools.ui.components.tool.CtToolChip
import com.furina.classtools.ui.components.tool.CtToolField
import com.furina.classtools.ui.components.tool.CtToolInfoRow
import com.furina.classtools.ui.components.tool.CtToolPrimaryButton
import com.furina.classtools.ui.components.tool.CtToolStat
import com.furina.classtools.ui.theme.CtShape
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.CtSpace
import com.furina.classtools.ui.theme.rememberReduceMotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * 宿舍水量页（原生）。
 *
 * 契约：`docs/api/USERDATA_V6.md` water section + `web/tools/tests/_water_contract.md` §0/§1/§2/§7。
 * - 数据真源：`userdata.water = {rooms:{<roomverify>:{p:1..6, recs:[{m,r,ts}]}}}`，整段 LWW；
 * - 关注宿舍沿用 `userdata.power.sel`（未选时本页可直接选择并写回 power section）；
 * - 1–6 人间切换影响免费额度（2 吨/人/月）与水费：超出部分 × 3.03 元/吨，折合扣电 = 水费 ÷ 0.58；
 * - 月份选择器（‹ 上月 / YYYY年MM月 / 下月 › + 12 月网格弹层，已有抄表月份显示圆点）；
 * - 隐私：只显示楼栋+房间号+读数，不含姓名/学号；**不导出、不分享**。
 *
 * 接线（Lead 负责 MainNavHost）：
 * ```kotlin
 * "water" -> WaterScreen(
 *     onBack = { navController.popBackStack() },
 *     session = session,
 *     userDataRepository = container.userDataRepository,
 * )
 * ```
 *
 * @param initialRooms 预览注入：water section 内容
 * @param initialSelectedRv 预览注入：关注宿舍 roomverify
 * @param initialMonth 预览注入：当前月份
 * @param skipLoad 预览/测试：跳过首次 pull
 * @param forcedErrorMessage 预览注入：错误态
 */
@Composable
fun WaterScreen(
    onBack: () -> Unit,
    session: Session?,
    userDataRepository: UserDataRepository?,
    modifier: Modifier = Modifier,
    clock: () -> Long = System::currentTimeMillis,
    initialRooms: Map<String, WaterRoomState> = emptyMap(),
    initialSelectedRv: String = "",
    initialMonth: String = "",
    skipLoad: Boolean = false,
    forcedErrorMessage: String? = null,
) {
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    val snackbar = remember { SnackbarHostState() }
    val waterRepository = remember(userDataRepository) {
        userDataRepository?.let { UserDataWaterRepository(it) }
    }
    val fallbackSections = remember { MutableStateFlow(JsonObject(emptyMap())) }
    val sectionsFlow = userDataRepository?.currentSections ?: fallbackSections
    val sections by sectionsFlow.collectAsState()

    val udPower = remember(sections) { sections["power"] as? JsonObject }
    val udSel = remember(udPower) {
        PowerRooms.normalizeSel((udPower?.get("sel") as? JsonPrimitive)?.contentOrNull)
    }

    var selectedRv by remember {
        mutableStateOf(
            PowerRooms.normalizeSel(initialSelectedRv).ifBlank { PowerRooms.normalizeSel((initialRooms.keys.firstOrNull())) },
        )
    }
    LaunchedEffect(udSel) {
        if (selectedRv.isBlank() && udSel.isNotBlank()) selectedRv = udSel
        if (selectedRv.isNotBlank() && selectedRv != udSel) selectedRv = selectedRv // 本页选择优先
    }

    var waterState by remember(initialRooms) { mutableStateOf(WaterState(initialRooms)) }
    LaunchedEffect(sections) {
        val remote = WaterCodec.fromJson(sections["water"])
        if (waterState.rooms.isEmpty() && remote.rooms.isNotEmpty()) waterState = remote
    }

    var loadingDone by remember {
        mutableStateOf(
            skipLoad || forcedErrorMessage != null || userDataRepository == null ||
                sections.keys.isNotEmpty() || initialRooms.isNotEmpty(),
        )
    }
    LaunchedEffect(Unit) {
        if (!loadingDone && userDataRepository != null) {
            runCatching { userDataRepository.pull(force = false) }
            loadingDone = true
        }
    }

    var month by remember { mutableStateOf(WaterCodec.normalizeMonth(initialMonth) ?: WaterMonth.current(clock())) }
    var readingInput by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    var overwriteReview by remember { mutableStateOf<WaterRecordReview.Valid?>(null) }
    var largeDiffReview by remember { mutableStateOf<WaterRecordReview.Valid?>(null) }
    var deletingMonth by remember { mutableStateOf<String?>(null) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var showRoomPicker by remember { mutableStateOf(false) }
    var showClearRoomConfirm by remember { mutableStateOf(false) }
    var pickerYear by remember(month) { mutableStateOf(WaterMonth.yearOf(month)) }

    fun notify(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    /** v4.1 撤销：删除抄表记录后 5s 内可恢复（重新 persist 旧 state）。 */
    fun notifyUndo(message: String, undo: () -> Unit) {
        scope.launch {
            val result = snackbar.showSnackbar(
                message = message,
                actionLabel = "撤销",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                undo()
                snackbar.showSnackbar("已撤销")
            }
        }
    }

    fun persist(next: WaterState) {
        waterState = next
        scope.launch { runCatching { waterRepository?.save(next) } }
    }

    fun writePowerSel(roomVerify: String) {
        selectedRv = PowerRooms.normalizeSel(roomVerify)
        scope.launch {
            runCatching {
                val repository = userDataRepository ?: return@runCatching
                val current = repository.currentSections.first()
                val next = LinkedHashMap<String, JsonElement>(current)
                next["power"] = buildJsonObject {
                    (current["power"] as? JsonObject)?.forEach { (key, value) ->
                        if (key != "sel" && key != "at") put(key, value)
                    }
                    put("sel", selectedRv)
                    put("at", clock())
                }
                repository.updateCurrent(JsonObject(next))
            }
        }
    }

    val room = waterState.roomOrDefault(selectedRv)
    val stats: WaterMonthStats = remember(room, month, waterState, selectedRv) {
        WaterCalculator.stats(room, month, clock())
    }
    val monthsWithRecords = remember(room) { room.records.map { it.month }.toSet() }
    val isWide = false

    fun saveValid(valid: WaterRecordReview.Valid) {
        persist(WaterMutator.put(waterState, selectedRv, valid.record))
        readingInput = ""
        inputError = null
        overwriteReview = null
        largeDiffReview = null
        notify(if (valid.replaceExisting) "已更新 ${WaterMonth.label(valid.record.month)} 记录" else "已保存 ${WaterMonth.label(valid.record.month)} 记录")
    }

    fun submitReading() {
        if (selectedRv.isBlank()) {
            notify("请先选择关注宿舍")
            return
        }
        when (val review = WaterMutator.review(room, month, readingInput, clock())) {
            is WaterRecordReview.Invalid -> inputError = review.message
            is WaterRecordReview.Valid -> {
                inputError = null
                val prev = waterState.roomOrDefault(selectedRv).records.lastOrNull { it.month < review.record.month }
                val jumpTooLarge = prev != null && prev.reading > 0.01 &&
                    kotlin.math.abs(review.record.reading - prev.reading) / prev.reading > 0.30
                if (review.replaceExisting) {
                    overwriteReview = review
                } else if (jumpTooLarge) {
                    // W-03：与上一记录差异 >30% 时二次确认，避免录错小数位。
                    largeDiffReview = review
                } else {
                    saveValid(review)
                }
            }
        }
    }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                ctTopBarRaw(
                    title = "宿舍水量",
                    subtitle = buildString {
                        val label = PowerRooms.labelOf(selectedRv)
                        if (label.isNotBlank()) append(label)
                        val readings = room.records.size
                        if (readings > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("$readings 条记录")
                        }
                    },
                    onBack = onBack,
                )

                Box(modifier = Modifier.weight(1f)) {
                    val phase = when {
                        forcedErrorMessage != null -> WaterPhase.ERROR
                        session == null && !skipLoad -> WaterPhase.ERROR
                        !loadingDone -> WaterPhase.LOADING
                        else -> WaterPhase.READY
                    }
                    when (phase) {
                        WaterPhase.LOADING -> CtLoading(
                            modifier = Modifier.align(Alignment.Center),
                            text = "加载水量数据…",
                        )

                        WaterPhase.ERROR -> ErrorContent(
                            message = forcedErrorMessage ?: "请先完成身份验证后再查看水量",
                            onRetry = { showRoomPicker = true },
                            modifier = Modifier.fillMaxSize(),
                        )

                        WaterPhase.READY -> {
                            if (selectedRv.isBlank()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    CtToolCard {
                                        EmptyState(
                                            icon = Icons.Rounded.Apartment,
                                            title = "还没有关注宿舍",
                                            message = "选择后每月录入一次读数",
                                        )
                                    }
                                    CtToolPrimaryButton(
                                        text = "选择宿舍",
                                        onClick = { showRoomPicker = true },
                                    )
                                }
                            } else {
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .horizontalSwipe(
                                            enabled = true,
                                            onSwipeLeft = { month = WaterMonth.shift(month, 1) },
                                            onSwipeRight = { month = WaterMonth.shift(month, -1) },
                                        ),
                                ) {
                                    if (maxWidth >= 600.dp) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .weight(1.05f)
                                                    .verticalScroll(rememberScrollState())
                                                    .padding(bottom = 24.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                RoomCard(
                                                    selectedRv = selectedRv,
                                                    persons = room.persons,
                                                    onPickRoom = { showRoomPicker = true },
                                                    onPersons = { persons ->
                                                        persist(WaterMutator.withPersons(waterState, selectedRv, persons))
                                                    },
                                                    onClearRoom = { showClearRoomConfirm = true },
                                                )
                                                MonthSelector(
                                                    month = month,
                                                    reduceMotion = reduceMotion,
                                                    onPrev = { month = WaterMonth.shift(month, -1) },
                                                    onNext = { month = WaterMonth.shift(month, 1) },
                                                    onPick = { showMonthPicker = true },
                                                    onCurrent = { month = WaterMonth.current(clock()) },
                                                )
                                                EntryCard(
                                                    month = month,
                                                    readingInput = readingInput,
                                                    error = inputError,
                                                    preview = if (readingInput.isBlank()) null else {
                                                        WaterMutator.review(room, month, readingInput, clock())
                                                    },
                                                    room = room,
                                                    persons = room.persons,
                                                    onInput = {
                                                        readingInput = it
                                                        inputError = null
                                                    },
                                                    onSave = { submitReading() },
                                                )
                                                RuleCard(persons = room.persons)
                                                Spacer(Modifier.height(CtSpace.sm))
                                            }
                                            Column(
                                                modifier = Modifier
                                                    .weight(0.95f)
                                                    .verticalScroll(rememberScrollState())
                                                    .padding(bottom = 24.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                AnimatedStatsCard(stats = stats, reduceMotion = reduceMotion)
                                                HistoryCard(
                                                    records = room.records,
                                                    persons = room.persons,
                                                    room = room,
                                                    onDelete = { deletingMonth = it },
                                                )
                                            }
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(rememberScrollState())
                                                .padding(horizontal = 16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            RoomCard(
                                                selectedRv = selectedRv,
                                                persons = room.persons,
                                                onPickRoom = { showRoomPicker = true },
                                                onPersons = { persons ->
                                                    persist(WaterMutator.withPersons(waterState, selectedRv, persons))
                                                },
                                                onClearRoom = { showClearRoomConfirm = true },
                                            )
                                            MonthSelector(
                                                month = month,
                                                reduceMotion = reduceMotion,
                                                onPrev = { month = WaterMonth.shift(month, -1) },
                                                onNext = { month = WaterMonth.shift(month, 1) },
                                                onPick = { showMonthPicker = true },
                                                onCurrent = { month = WaterMonth.current(clock()) },
                                            )
                                            EntryCard(
                                                month = month,
                                                readingInput = readingInput,
                                                error = inputError,
                                                preview = if (readingInput.isBlank()) null else {
                                                    WaterMutator.review(room, month, readingInput, clock())
                                                },
                                                room = room,
                                                persons = room.persons,
                                                onInput = {
                                                    readingInput = it
                                                    inputError = null
                                                },
                                                onSave = { submitReading() },
                                            )
                                            AnimatedStatsCard(stats = stats, reduceMotion = reduceMotion)
                                            HistoryCard(
                                                records = room.records,
                                                persons = room.persons,
                                                room = room,
                                                onDelete = { deletingMonth = it },
                                            )
                                            RuleCard(persons = room.persons)
                                            Spacer(Modifier.height(CtSpace.xxl))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            androidx.compose.foundation.layout.Box(
            modifier = Modifier.ctLightMaterial(
                CtLightScene.SNACKBAR,
                shape = CtShape.input,
                bodySafe = true,
            ),
        ) { SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            )
            }
        }
    }

    if (showMonthPicker) {
        MonthPickerDialog(
            year = pickerYear,
            month = month,
            monthsWithRecords = monthsWithRecords,
            onYearChange = { pickerYear = it },
            onPick = { picked ->
                month = picked
                showMonthPicker = false
            },
            onDismiss = { showMonthPicker = false },
        )
    }

    if (showRoomPicker) {
        RoomPickerDialog(
            selectedRv = selectedRv,
            onPick = { rv ->
                writePowerSel(rv)
                showRoomPicker = false
            },
            onDismiss = { showRoomPicker = false },
        )
    }

    if (overwriteReview != null) {
        CtLightAlertDialog(
            onDismissRequest = { overwriteReview = null },
            title = { Text("该月份已有记录") },
            text = {
                Text("${WaterMonth.label(month)} 已有抄表记录，保存将覆盖原读数。")
            },
            confirmButton = {
                TextButton(onClick = { overwriteReview?.let { saveValid(it) } }) { Text("覆盖") }
            },
            dismissButton = {
                TextButton(onClick = { overwriteReview = null }) { Text("取消") }
            },
        )
    }

    val diffReview = largeDiffReview
    if (diffReview != null) {
        val prev = waterState.roomOrDefault(selectedRv).records.lastOrNull { it.month < diffReview.record.month }
        val pct = if (prev != null && prev.reading > 0.01) {
            kotlin.math.abs(diffReview.record.reading - prev.reading) / prev.reading * 100
        } else {
            0.0
        }
        CtLightAlertDialog(
            onDismissRequest = { largeDiffReview = null },
            title = { Text("读数差异较大") },
            text = {
                Text(
                    "上次读数 ${prev?.let { WaterMutator.formatReading(it.reading) } ?: "—"} 吨，" +
                        "本次 ${WaterMutator.formatReading(diffReview.record.reading)} 吨" +
                        "（相差 ${String.format(java.util.Locale.CHINA, "%.1f", pct)}%）。确认无误再保存。",
                )
            },
            confirmButton = {
                TextButton(onClick = { saveValid(diffReview) }) { Text("仍要保存") }
            },
            dismissButton = {
                TextButton(onClick = { largeDiffReview = null }) { Text("取消") }
            },
        )
    }

    if (deletingMonth != null) {
        val target = deletingMonth
        CtLightAlertDialog(
            onDismissRequest = { deletingMonth = null },
            title = { Text("删除抄表记录") },
            text = { Text("删除 ${WaterMonth.label(target.orEmpty())} 的读数？该操作会同步到云端。") },
            confirmButton = {
                TextButton(onClick = {
                    if (target != null) {
                        val before = waterState
                        persist(WaterMutator.delete(waterState, selectedRv, target))
                        notifyUndo("已删除 ${WaterMonth.label(target)} 记录") { persist(before) }
                    }
                    deletingMonth = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deletingMonth = null }) { Text("取消") }
            },
        )
    }

    if (showClearRoomConfirm) {
        CtLightAlertDialog(
            onDismissRequest = { showClearRoomConfirm = false },
            title = { Text("清除本间水量") },
            text = { Text("清除 ${PowerRooms.labelOf(selectedRv)} 的全部抄表记录？会同步清空云端该间数据。") },
            confirmButton = {
                TextButton(onClick = {
                    persist(WaterMutator.clearRoom(waterState, selectedRv))
                    showClearRoomConfirm = false
                    notify("已清除该间水量数据")
                }) { Text("清除") }
            },
            dismissButton = {
                TextButton(onClick = { showClearRoomConfirm = false }) { Text("取消") }
            },
        )
    }
}

// -----------------------------------------------------------------------------
// 组件
// -----------------------------------------------------------------------------

@Composable
private fun RoomCard(
    selectedRv: String,
    persons: Int,
    onPickRoom: () -> Unit,
    onPersons: (Int) -> Unit,
    onClearRoom: () -> Unit,
) {
    CtToolCard(contentPadding = PaddingValues(16.dp)) {
        WaterCardHeader(title = "关注宿舍") {
            TextButton(onClick = onPickRoom) { Text(if (selectedRv.isBlank()) "选择" else "更换") }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = PowerRooms.labelOf(selectedRv).ifBlank { "未选择宿舍" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "定额 2 吨/人/月",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 1–6 人间分两行，避免窄屏 6 个 chip 横向溢出。
            WaterRules.PERSONS_OPTIONS.chunked(3).forEach { rowOptions ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowOptions.forEach { option ->
                        CtToolChip(
                            text = "$option 人间",
                            selected = persons == option,
                            onClick = { onPersons(option) },
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClearRoom) { Text("清除本间") }
            }
        }
    }
}

@Composable
private fun MonthSelector(
    month: String,
    reduceMotion: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPick: () -> Unit,
    onCurrent: () -> Unit,
) {
    CtToolCard(contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Icon(Icons.Rounded.ChevronLeft, contentDescription = "上个月") }
            val label: @Composable (String) -> Unit = { shown ->
                TextButton(onClick = onPick) {
                    Text(
                        text = WaterMonth.label(shown),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            if (reduceMotion) {
                label(month)
            } else {
                AnimatedContent(
                    targetState = month,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally(tween(240)) { it / 4 } + fadeIn(tween(240)))
                                .togetherWith(slideOutHorizontally(tween(200)) { -it / 4 } + fadeOut(tween(160)))
                        } else {
                            (slideInHorizontally(tween(240)) { -it / 4 } + fadeIn(tween(240)))
                                .togetherWith(slideOutHorizontally(tween(200)) { it / 4 } + fadeOut(tween(160)))
                        }
                    },
                    label = "waterMonthLabel",
                ) { shown -> label(shown) }
            }
            IconButton(onClick = onNext) { Icon(Icons.Rounded.ChevronRight, contentDescription = "下个月") }
            Spacer(Modifier.weight(1f))
            if (!WaterMonth.isCurrent(month)) {
                TextButton(onClick = onCurrent) { Text("本月") }
            }
        }
    }
}

@Composable
private fun EntryCard(
    month: String,
    readingInput: String,
    error: String?,
    preview: WaterRecordReview?,
    room: WaterRoomState,
    persons: Int,
    onInput: (String) -> Unit,
    onSave: () -> Unit,
) {
    CtToolCard(contentPadding = PaddingValues(16.dp)) {
        WaterCardHeader(title = "新增抄表")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "月份 ${WaterMonth.label(month)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CtToolField(
                label = "读数（吨）",
                value = readingInput,
                onValueChange = onInput,
                placeholder = "例如 130.10",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (preview is WaterRecordReview.Valid) {
                val temp = WaterRecord(month = preview.record.month, reading = preview.record.reading, ts = preview.record.ts)
                val tempRoom = room.copy(records = (room.records + temp).distinctBy { it.month }.sortedBy { it.month })
                val charge = WaterCalculator.charge(tempRoom, temp, persons)
                PreviewLine(charge = charge)
            } else if (preview is WaterRecordReview.Invalid) {
                Text(
                    text = preview.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (WaterMonth.isCurrent(month)) {
                CtPill(text = "本月")
            }
            CtToolPrimaryButton(
                text = "保存记录",
                onClick = onSave,
                enabled = readingInput.isNotBlank(),
            )
        }
    }
}

@Composable
private fun PreviewLine(charge: WaterCharge) {
    val text = if (charge.usage == null) {
        "首次记录：无上一条读数，不计算本期用量"
    } else {
        "预览：用量 ${WaterCalculator.fmtReading(charge.usage)} 吨 · " +
            "水费 ${WaterCalculator.fmtMoney(charge.fee)} 元 · " +
            "折合扣电 ${WaterCalculator.fmtDegree(charge.degree)} 度"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun WaterCardHeader(title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
    }
}

@Composable
private fun AnimatedStatsCard(stats: WaterMonthStats, reduceMotion: Boolean) {
    if (reduceMotion) {
        StatsCard(stats = stats)
        return
    }
    AnimatedContent(
        targetState = stats,
        transitionSpec = {
            if (targetState.month > initialState.month) {
                (slideInHorizontally(tween(240)) { it / 5 } + fadeIn(tween(240)))
                    .togetherWith(slideOutHorizontally(tween(200)) { -it / 5 } + fadeOut(tween(160)))
            } else {
                (slideInHorizontally(tween(240)) { -it / 5 } + fadeIn(tween(240)))
                    .togetherWith(slideOutHorizontally(tween(200)) { it / 5 } + fadeOut(tween(160)))
            }
        },
        label = "waterStats",
    ) { shown ->
        StatsCard(stats = shown)
    }
}

@Composable
private fun StatsCard(stats: WaterMonthStats) {
    val charge = stats.charge
    CtToolCard(contentPadding = PaddingValues(16.dp)) {
        WaterCardHeader(title = "${WaterMonth.label(stats.month)} 统计")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CtToolStat(
                    value = if (charge.usage == null) "—" else WaterCalculator.fmtReading(charge.usage),
                    label = "本月用量（吨）",
                    modifier = Modifier.weight(1f),
                    valueColor = MaterialTheme.colorScheme.primary,
                )
                CtToolStat(
                    value = charge.fee?.let { WaterCalculator.fmtMoney(it) } ?: "—",
                    label = "本期水费（元）",
                    modifier = Modifier.weight(1f),
                )
            }
            CtToolInfoRow(
                label = "日均用量",
                value = stats.dailyAverage?.let { "${WaterCalculator.fmtReading(it)} 吨/天" } ?: "—",
            )
            CtToolInfoRow(
                label = "对比上月",
                value = when {
                    stats.deltaVsPreviousMonth == null -> "—"
                    stats.deltaVsPreviousMonth > 0.0 -> "+${WaterCalculator.fmtReading(stats.deltaVsPreviousMonth)} 吨"
                    stats.deltaVsPreviousMonth < 0.0 -> "${WaterCalculator.fmtReading(stats.deltaVsPreviousMonth)} 吨"
                    else -> "持平"
                },
            )
            CtToolInfoRow(
                label = "折合扣电",
                value = charge.degree?.let { "${WaterCalculator.fmtDegree(it)} 度" } ?: "—",
            )
            CtToolInfoRow(
                label = "免费额度",
                value = "${WaterCalculator.fmtReading(charge.free)} 吨（超出 ${WaterCalculator.fmtReading(charge.excess)} 吨）",
            )
            HorizontalDivider()
            CtToolInfoRow(
                label = "上次抄表",
                value = stats.previous?.let {
                    "${WaterMonth.label(it.month)} · ${WaterCalculator.fmtReading(it.reading)} 吨"
                } ?: "—",
            )
        }
    }
}

@Composable
private fun HistoryCard(
    records: List<WaterRecord>,
    persons: Int,
    room: WaterRoomState,
    onDelete: (String) -> Unit,
) {
    CtToolCard(contentPadding = PaddingValues(16.dp)) {
        WaterCardHeader(title = "抄表历史（${records.size}）")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (records.isEmpty()) {                Text(
                    text = "还没有抄表记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                records.sortedByDescending { it.month }.forEach { record ->
                    val charge = WaterCalculator.charge(room, record, persons)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp),
                        ) {
                            Text(
                                text = "${WaterMonth.label(record.month)} · ${WaterCalculator.fmtReading(record.reading)} 吨",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            val detail = if (charge.usage == null) {
                                "首次记录"
                            } else {
                                "用量 ${WaterCalculator.fmtReading(charge.usage)} 吨 · 水费 ${WaterCalculator.fmtMoney(charge.fee)} 元"
                            }
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { onDelete(record.month) },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "删除 ${WaterMonth.label(record.month)} 记录",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleCard(persons: Int) {
    CtToolCard(contentPadding = PaddingValues(16.dp)) {
        WaterCardHeader(title = "水费规则")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            WaterCalculator.ruleLines(persons).forEach { (label, value) ->
                if (value.isBlank()) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row {
                        Text(
                            text = "$label：",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 弹层（月份选择器 / 宿舍选择器公开给预览）
// -----------------------------------------------------------------------------

/**
 * 12 月网格（月份选择器内容）：已有抄表记录的月份显示圆点。
 * 公开给 `WaterRosterPreviews` 单独预览。
 */
@Composable
fun WaterMonthGrid(
    year: Int,
    month: String,
    monthsWithRecords: Set<String>,
    onPick: (String) -> Unit,
    onYearChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onYearChange(year - 1) }) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "上一年")
            }
            Text(
                text = "$year 年",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onYearChange(year + 1) }) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "下一年")
            }
        }
        WaterMonth.monthsOfYear(year).chunked(3).forEach { rowMonths ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowMonths.forEach { monthKey ->
                    val selected = monthKey == month
                    val hasRecords = monthKey in monthsWithRecords
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(CtShapeTokens.listItem)
                            .clickable { onPick(monthKey) },
                        shape = CtShapeTokens.listItem,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = WaterMonth.shortLabel(monthKey),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            Box(
                                modifier = Modifier
                                    .padding(top = 3.dp)
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (hasRecords) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    ),
                            )
                        }
                    }
                }
                repeat(3 - rowMonths.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MonthPickerDialog(
    year: Int,
    month: String,
    monthsWithRecords: Set<String>,
    onYearChange: (Int) -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    CtLightAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择抄表月份") },
        text = {
            WaterMonthGrid(
                year = year,
                month = month,
                monthsWithRecords = monthsWithRecords,
                onPick = onPick,
                onYearChange = onYearChange,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun RoomPickerDialog(
    selectedRv: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    CtLightAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择关注宿舍") },
        text = {
            var query by remember { mutableStateOf("") }
            val keyword = query.trim()
            var matched = false
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CtToolField(
                    label = "搜索房间号 / 楼栋",
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "输入房间号或楼栋",
                )
                PowerRooms.BUILDINGS.forEach { building ->
                    Text(
                        text = building,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val rooms = PowerRooms.roomsOfBuilding(building).filter { roomInfo ->
                        keyword.isBlank() ||
                            roomInfo.room.contains(keyword, ignoreCase = true) ||
                            building.contains(keyword, ignoreCase = true)
                    }
                    if (rooms.isEmpty()) return@forEach
                    matched = true
                    rooms.chunked(3).forEach { rowRooms ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowRooms.forEach { roomInfo ->
                                val selected = roomInfo.rv == selectedRv
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .clip(CtShapeTokens.pill)
                                        .clickable { onPick(roomInfo.rv) },
                                    shape = CtShapeTokens.pill,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                ) {
                                    Text(
                                        text = roomInfo.room,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            repeat(3 - rowRooms.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                if (!matched) {
                    Text(
                        text = "未找到房间",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CtToolCard {
            EmptyState(icon = Icons.Rounded.ErrorOutline, title = "水量数据不可用", message = message)
        }
        CtToolPrimaryButton(text = "选择宿舍后重试", onClick = onRetry)
    }
}

/** 页面内部阶段（四态中的加载/错误两态由它承载）。 */
private enum class WaterPhase { LOADING, READY, ERROR }

/**
 * 工具页统一左右滑动：横向拖动超过 48dp 松手触发；只消费横向手势，不与页面纵向滚动冲突。
 */
private fun Modifier.horizontalSwipe(
    enabled: Boolean,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(enabled, onSwipeLeft, onSwipeRight) {
        val threshold = 48.dp.toPx()
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                total += dragAmount
            },
            onDragEnd = {
                when {
                    total <= -threshold -> onSwipeLeft()
                    total >= threshold -> onSwipeRight()
                }
                total = 0f
            },
            onDragCancel = { total = 0f },
        )
    }
}
