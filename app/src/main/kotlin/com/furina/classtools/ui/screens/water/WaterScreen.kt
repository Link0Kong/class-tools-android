@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.furina.classtools.ui.screens.water

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.furina.classtools.ui.components.CtTopBar
import com.furina.classtools.ui.components.EmptyState
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.theme.CtShapeTokens
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
 * - 数据真源：`userdata.water = {rooms:{<roomverify>:{p:4|6, recs:[{m,r,ts}]}}}`，整段 LWW；
 * - 关注宿舍沿用 `userdata.power.sel`（未选时本页可直接选择并写回 power section）；
 * - 4/6 人间切换影响免费额度（2 吨/人/月）与水费：超出部分 × 3.03 元/吨，折合扣电 = 水费 ÷ 0.58；
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
                if (review.replaceExisting) overwriteReview = review else saveValid(review)
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
                CtTopBar(
                    title = "宿舍水量",
                    subtitle = buildString {
                        val label = PowerRooms.labelOf(selectedRv)
                        if (label.isNotBlank()) append(label)
                        val readings = room.records.size
                        if (readings > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("$readings 条记录")
                        }
                        if (waterState.rooms.isNotEmpty()) {
                            if (isNotEmpty()) append(" · ")
                            append("随账号同步")
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
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    EmptyState(
                                        icon = Icons.Rounded.Apartment,
                                        title = "还没有关注宿舍",
                                        message = "选择本人宿舍后可录入抄表；数据随账号同步，不导出",
                                    )
                                    FilledTonalButton(
                                        onClick = { showRoomPicker = true },
                                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                                    ) {
                                        Text("选择宿舍")
                                    }
                                }
                            } else {
                                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
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
                                                Spacer(Modifier.height(8.dp))
                                            }
                                            Column(
                                                modifier = Modifier
                                                    .weight(0.95f)
                                                    .verticalScroll(rememberScrollState())
                                                    .padding(bottom = 24.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                StatsCard(stats = stats)
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
                                            StatsCard(stats = stats)
                                            HistoryCard(
                                                records = room.records,
                                                persons = room.persons,
                                                room = room,
                                                onDelete = { deletingMonth = it },
                                            )
                                            RuleCard(persons = room.persons)
                                            Spacer(Modifier.height(24.dp))
                                        }
                                    }
                                }
                            }
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
        AlertDialog(
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

    if (deletingMonth != null) {
        val target = deletingMonth
        AlertDialog(
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
        AlertDialog(
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
    Surface(modifier = Modifier.fillMaxWidth(), shape = CtShapeTokens.toolCard, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "关注宿舍",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onPickRoom) { Text(if (selectedRv.isBlank()) "选择" else "更换") }
                TextButton(onClick = onClearRoom) { Text("清除本间") }
            }
            Text(
                text = PowerRooms.labelOf(selectedRv).ifBlank { "未选择宿舍" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "人数影响免费水量与水费（2 吨/人/月）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WaterRules.PERSONS_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = persons == option,
                        onClick = { onPersons(option) },
                        label = { Text("$option 人间") },
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthSelector(
    month: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPick: () -> Unit,
    onCurrent: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = CtShapeTokens.toolCard, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev) { Icon(Icons.Rounded.ChevronLeft, contentDescription = "上个月") }
            TextButton(onClick = onPick) {
                Text(
                    text = WaterMonth.label(month),
                    style = MaterialTheme.typography.titleMedium,
                )
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
    Surface(modifier = Modifier.fillMaxWidth(), shape = CtShapeTokens.toolCard, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "新增抄表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "月份 ${WaterMonth.label(month)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = readingInput,
                onValueChange = onInput,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = error != null,
                label = { Text("读数（吨）") },
                placeholder = { Text("例如 130.10") },
                supportingText = {
                    if (error != null) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("两位小数；不得小于上月读数")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (WaterMonth.isCurrent(month)) {
                    CtPill(text = "本月")
                }
                Spacer(Modifier.weight(1f))
                FilledTonalButton(onClick = onSave, enabled = readingInput.isNotBlank()) {
                    Text("保存记录")
                }
            }
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
private fun StatsCard(stats: WaterMonthStats) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = CtShapeTokens.toolCard, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Opacity,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${WaterMonth.label(stats.month)} 统计",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            val charge = stats.charge
            StatRow("本月用量", if (charge.usage == null) "首次记录" else "${WaterCalculator.fmtReading(charge.usage)} 吨")
            StatRow("日均用量", if (stats.dailyAverage == null) "—" else "${WaterCalculator.fmtReading(stats.dailyAverage)} 吨/天")
            val delta = stats.deltaVsPreviousMonth
            StatRow(
                "对比上月",
                when {
                    delta == null -> "—"
                    delta > 0.0 -> "+${WaterCalculator.fmtReading(delta)} 吨"
                    delta < 0.0 -> "${WaterCalculator.fmtReading(delta)} 吨"
                    else -> "持平"
                },
            )
            StatRow("本期水费", if (charge.fee == null) "—" else "${WaterCalculator.fmtMoney(charge.fee)} 元")
            StatRow("折合扣电", if (charge.degree == null) "—" else "${WaterCalculator.fmtDegree(charge.degree)} 度")
            StatRow("免费额度", "${WaterCalculator.fmtReading(charge.free)} 吨（超出 ${WaterCalculator.fmtReading(charge.excess)} 吨）")
            HorizontalDivider()
            StatRow("上次抄表", stats.previous?.let { "${WaterMonth.label(it.month)} · ${WaterCalculator.fmtReading(it.reading)} 吨" } ?: "—")
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun HistoryCard(
    records: List<WaterRecord>,
    persons: Int,
    room: WaterRoomState,
    onDelete: (String) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = CtShapeTokens.toolCard, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "抄表历史（${records.size}）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (records.isEmpty()) {
                Text(
                    text = "还没有抄表记录 · 保存第一条后可对比用量",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                records.sortedByDescending { it.month }.forEach { record ->
                    val charge = WaterCalculator.charge(room, record, persons)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
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
                        IconButton(onClick = { onDelete(record.month) }) {
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
    Surface(modifier = Modifier.fillMaxWidth(), shape = CtShapeTokens.toolCard, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "计费规则（只读）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
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
    AlertDialog(
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择关注宿舍") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "只显示楼栋+房间号，不含姓名/学号；不导出",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PowerRooms.BUILDINGS.forEach { building ->
                    Text(
                        text = building,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PowerRooms.roomsOfBuilding(building).chunked(3).forEach { rowRooms ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowRooms.forEach { roomInfo ->
                                val selected = roomInfo.rv == selectedRv
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
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
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyState(icon = Icons.Rounded.ErrorOutline, title = "水量数据不可用", message = message)
        TextButton(onClick = onRetry) { Text("选择宿舍后重试") }
    }
}

/** 页面内部阶段（四态中的加载/错误两态由它承载）。 */
private enum class WaterPhase { LOADING, READY, ERROR }
