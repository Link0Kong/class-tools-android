@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.furina.classtools.ui.screens.fit

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.sync.UserDataRepository
import com.furina.classtools.features.fit.FIT_INPUT_FIELDS
import com.furina.classtools.features.fit.FitCalc
import com.furina.classtools.features.fit.FitCodec
import com.furina.classtools.features.fit.FitExportFactory
import com.furina.classtools.features.fit.FitExportPayload
import com.furina.classtools.features.fit.FitFieldSpec
import com.furina.classtools.features.fit.FitGrade
import com.furina.classtools.features.fit.FitHistory
import com.furina.classtools.features.fit.FitInput
import com.furina.classtools.features.fit.FitRecord
import com.furina.classtools.features.fit.FitScoring
import com.furina.classtools.features.fit.FitSex
import com.furina.classtools.features.fit.FitTables
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.tool.CtToolCard
import com.furina.classtools.ui.components.tool.CtToolField
import com.furina.classtools.ui.components.tool.CtToolPrimaryButton
import com.furina.classtools.ui.components.tool.CtToolSegmented
import com.furina.classtools.ui.components.tool.CtToolStat
import com.furina.classtools.ui.components.CtTopBar
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.expressive.CtBottomSheet
import com.furina.classtools.ui.components.expressive.CtBottomSheetHeader
import com.furina.classtools.ui.components.expressive.CtSegmentOption
import com.furina.classtools.ui.components.expressive.CtSegmentedButtons
import com.furina.classtools.ui.theme.CtShapeTokens
import com.furina.classtools.ui.theme.ctEmphasized
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 体测页（大学国标七项实时算分 + 历史记录 + 导出）。
 *
 * 接线方式（已由 Lead 在 MainNavHost 完成，fitrecs E14/E15/E16）：
 * ```kotlin
 * "fit" -> FitScreen(
 *     onBack = { navController.popBackStack() },
 *     session = session,
 *     userDataRepository = container.userDataRepository,
 *     initialHistory = fitHistory,  // container.fitRecsRepository.load()
 *     onSave = { record, kept, dropped -> repository.save(record, kept, dropped) },
 *     onRename = { record -> repository.rename(record) },      // POST upsert data
 *     onDelete = { record -> repository.archive(record.id) },  // PATCH arch=true
 *     onExport = { payload -> launchExport(payload.toCardSpec()) },
 * )
 * ```
 *
 * - 输入随 `userdata.fit`（sex/grade + 9 项）防抖 600ms 同步；失败静默；
 * - 历史记录：`initialHistory`（fitrecs 云端，最多 2 条）+ 本地 state；
 *   保存回调带 `dropped`（被挤出 2 条的旧记录由仓库 PATCH arch=true 软删）；
 * - 实时算分：任意一项合法输入立即出单项分/总分/等级/加分/缺项；
 * - 四态：加载 / 错误 / 空（未填写） / 正常；≥600dp 左表单右评分&历史。
 *
 * @param initialInput / initialHistory / skipRestore 预览注入
 */
@Composable
fun FitScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    session: Session? = null,
    userDataRepository: UserDataRepository? = null,
    initialInput: FitInput = FitInput(),
    initialHistory: List<FitRecord> = emptyList(),
    onSave: (FitRecord, List<FitRecord>, List<FitRecord>) -> Unit = { _, _, _ -> },
    onRename: (FitRecord) -> Unit = {},
    onDelete: (FitRecord) -> Unit = {},
    onExport: (FitExportPayload) -> Unit = {},
    forcedErrorMessage: String? = null,
    skipRestore: Boolean = false,
    nowMillis: Long = System.currentTimeMillis(),
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var phase by remember(skipRestore, forcedErrorMessage) {
        mutableStateOf(
            when {
                forcedErrorMessage != null -> FitPhase.ERROR
                skipRestore -> FitPhase.READY
                else -> FitPhase.LOADING
            },
        )
    }
    var input by remember { mutableStateOf(initialInput) }
    var history by remember { mutableStateOf(initialHistory.take(FitRecord.MAX_RECORDS)) }
    // 云端历史加载完成 / 仓库更新后同步（本地保存后的 kept 与 initialHistory 一致，不会重复覆盖）
    LaunchedEffect(initialHistory) {
        if (initialHistory.isNotEmpty() && initialHistory != history) {
            history = initialHistory.take(FitRecord.MAX_RECORDS)
        }
    }
    var errorMessage by remember(forcedErrorMessage) { mutableStateOf(forcedErrorMessage.orEmpty()) }
    var restored by remember { mutableStateOf(skipRestore) }
    var showTable by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var renameIndex by remember { mutableStateOf<Int?>(null) }
    var deleteIndex by remember { mutableStateOf<Int?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    // D-07：删除后 5 秒内可撤销（撤销=恢复本地列表并按原 id 重新保存）。
    var undoRecord by remember { mutableStateOf<FitRecord?>(null) }

    val calc: FitCalc = remember(input) { FitScoring.calc(input) }

    LaunchedEffect(skipRestore) {
        if (skipRestore) return@LaunchedEffect
        if (session == null) {
            phase = FitPhase.ERROR
            errorMessage = "请先完成身份验证后再使用体测"
            return@LaunchedEffect
        }
        runCatching {
            userDataRepository?.currentSections?.first()?.get("fit")?.let { element ->
                input = FitCodec.inputFromJson(element)
            }
        }
        restored = true
        phase = FitPhase.READY
    }

    LaunchedEffect(input, restored) {
        if (!restored || skipRestore) return@LaunchedEffect
        delay(600)
        runCatching {
            val repository = userDataRepository ?: return@LaunchedEffect
            val current = repository.currentSections.first()
            val next = LinkedHashMap<String, JsonElement>(current)
            next["fit"] = FitCodec.toJson(input)
            repository.updateCurrent(JsonObject(next))
        }
    }

    fun saveRecord() {
        if (calc.wtDone <= 0) {
            toast = "先填写成绩"
            return
        }
        val saved = FitHistory.save(
            records = history,
            input = input,
            calc = calc,
            id = "fit-${nowMillis}",
            now = nowMillis,
        )
        history = saved.records
        onSave(saved.record, saved.records, saved.dropped)
        toast = if (saved.merged) "已合并到历史记录" else "已保存到历史记录"
    }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            CtTopBar(
                title = "体测",
                subtitle = "国标 2014 · 未填项按 0 计",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showTable = true }) {
                        Icon(Icons.Rounded.TableChart, contentDescription = "查看国标表")
                    }
                    IconButton(onClick = { onExport(FitExportFactory.create(calc, nowMillis)) }) {
                        Icon(Icons.Rounded.IosShare, contentDescription = "导出成绩")
                    }
                },
            )

            when (phase) {
                FitPhase.LOADING -> FitCenteredLoading("体测数据加载中…")
                FitPhase.ERROR -> FitCenteredError(errorMessage) {
                    phase = FitPhase.LOADING
                    restored = false
                }

                FitPhase.READY -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.weight(1f)) {
                        val expanded = maxWidth >= 600.dp
                        val padding = if (expanded) 20.dp else 12.dp
                        val form: @Composable (Modifier) -> Unit = { m ->
                            Column(
                                modifier = m.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                InputCard(
                                    input = input,
                                    onInput = { input = it },
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        val side: @Composable (Modifier) -> Unit = { m ->
                            Column(
                                modifier = m.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ScoreCard(calc = calc)
                                HistoryCard(
                                    history = history,
                                    onRestore = { index ->
                                        FitHistory.restoreAt(history, index)?.let { input = it }
                                    },
                                    onRename = { renameIndex = it },
                                    onDelete = { deleteIndex = it },
                                )
                                Spacer(modifier = Modifier.navigationBarsPadding().height(16.dp))
                            }
                        }
                        if (expanded) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = padding),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(modifier = Modifier.weight(0.55f)) { form(Modifier.fillMaxSize()) }
                                Box(modifier = Modifier.weight(0.45f)) { side(Modifier.fillMaxSize()) }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = padding),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                InputCard(input = input, onInput = { input = it })
                                ScoreCard(calc = calc)
                                HistoryCard(
                                    history = history,
                                    onRestore = { index ->
                                        FitHistory.restoreAt(history, index)?.let { input = it }
                                    },
                                    onRename = { renameIndex = it },
                                    onDelete = { deleteIndex = it },
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                    ActionRow(
                        canSave = calc.wtDone > 0,
                        onSave = { saveRecord() },
                        onClear = { showClearDialog = true },
                        onCopy = {
                            clipboard.setText(AnnotatedString(FitExportFactory.copyText(calc)))
                            toast = "已复制成绩"
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    Spacer(modifier = Modifier.navigationBarsPadding().height(6.dp))
                    }
                }
            }
        }

        // 国标表弹层（当前性别/年级；横向滚动看 20 档阈值）
        if (showTable) {
            StandardsSheet(
                sex = input.sex,
                grade = input.grade,
                onDismiss = { showTable = false },
            )
        }

        // 重命名 / 删除 / 清空确认
        renameIndex?.let { index ->
            val record = history.getOrNull(index)
            if (record != null) {
                var name by remember(record.id, index) { mutableStateOf(record.displayName()) }
                AlertDialog(
                    onDismissRequest = { renameIndex = null },
                    title = { Text("历史记录名称") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it.take(24) },
                                singleLine = true,
                                label = { Text("名称") },
                            )
                            // D-07：改名长度计数，避免误输入被静默截断。
                            Text(
                                text = "${name.length}/24",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.End),
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val updated = FitHistory.renameAt(history, index, name)
                            history = updated
                            updated.getOrNull(index)?.let { onRename(it) }
                            renameIndex = null
                        }) { Text("保存") }
                    },
                    dismissButton = { TextButton(onClick = { renameIndex = null }) { Text("取消") } },
                )
            } else {
                renameIndex = null
            }
        }
        deleteIndex?.let { index ->
            AlertDialog(
                onDismissRequest = { deleteIndex = null },
                title = { Text("删除记录") },
                text = { Text("删除后可在提示条中撤销（云端会同步删除）") },
                confirmButton = {
                    TextButton(onClick = {
                        val removed = history.getOrNull(index)
                        history = FitHistory.deleteAt(history, index)
                        removed?.let { onDelete(it) }
                        undoRecord = removed
                        deleteIndex = null
                        toast = "已删除"
                    }) { Text("删除") }
                },
                dismissButton = { TextButton(onClick = { deleteIndex = null }) { Text("取消") } },
            )
        }
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("清空输入") },
                text = { Text("清空体测输入与结果？历史记录不受影响") },
                confirmButton = {
                    TextButton(onClick = {
                        input = FitInput.blank(input.sex, input.grade)
                        showClearDialog = false
                        toast = "已清空"
                    }) { Text("清空") }
                },
                dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("取消") } },
            )
        }

        toast?.let { message ->
            LaunchedEffect(message) {
                // 有可撤销记录时给足 5 秒操作窗口，否则保持 2.2 秒。
                delay(if (undoRecord != null) 5000 else 2200)
                if (toast == message) {
                    toast = null
                    undoRecord = null
                }
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Surface(
                    modifier = Modifier.padding(bottom = 72.dp),
                    shape = CtShapeTokens.pill,
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        val pending = undoRecord
                        if (pending != null) {
                            TextButton(onClick = {
                                val restored = (listOf(pending) + history).take(FitRecord.MAX_RECORDS)
                                history = restored
                                onSave(pending, restored, emptyList())
                                undoRecord = null
                                toast = "已恢复"
                            }) {
                                Text("撤销")
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class FitPhase { LOADING, READY, ERROR }


/** v6 紧凑卡：标题 titleSmall + 14/12 内边距，密度对齐小程序。 */
@Composable
private fun FitCompactCard(
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

/** 紧凑统计：数值 + 小标签（替代大 padding 的 CtToolStat）。 */
@Composable
private fun FitStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun InputCard(input: FitInput, onInput: (FitInput) -> Unit) {
    FitCompactCard(title = "填写成绩") {
        CtToolSegmented(
            options = listOf(FitSex.M.label, FitSex.F.label),
            selectedIndex = if (input.sex == FitSex.M) 0 else 1,
            onSelect = { index -> onInput(input.copy(sex = if (index == 0) FitSex.M else FitSex.F)) },
            modifier = Modifier.fillMaxWidth(),
        )
        CtToolSegmented(
            options = listOf(FitGrade.G12.label, FitGrade.G34.label),
            selectedIndex = if (input.grade == FitGrade.G12) 0 else 1,
            onSelect = { index -> onInput(input.copy(grade = if (index == 0) FitGrade.G12 else FitGrade.G34)) },
            modifier = Modifier.fillMaxWidth(),
        )

        FIT_INPUT_FIELDS.chunked(2).forEach { rowFields ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowFields.forEach { spec ->
                    val label = if (spec.key == "pull") {
                        if (input.sex == FitSex.F) "1 分钟仰卧起坐" else "引体向上"
                    } else {
                        spec.label
                    }
                    NumberField(
                        value = input.valueOf(spec.key),
                        label = label,
                        unit = spec.unit,
                        spec = spec,
                        onValueChange = { value -> onInput(input.withValue(spec.key, value)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowFields.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
        // 长跑：分 + 秒
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NumberField(
                value = input.durM,
                label = if (input.sex == FitSex.M) "1000 米跑 · 分" else "800 米跑 · 分",
                unit = "分",
                spec = FitFieldSpec("durM", "长跑分", "分", 0),
                onValueChange = { onInput(input.copy(durM = it)) },
                modifier = Modifier.weight(1f),
            )
            NumberField(
                value = input.durS,
                label = "长跑 · 秒",
                unit = "秒",
                spec = FitFieldSpec("durS", "长跑秒", "秒", 0),
                onValueChange = { onInput(input.copy(durS = it)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    label: String,
    unit: String,
    spec: FitFieldSpec,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    CtToolField(
        value = value,
        onValueChange = { raw -> onValueChange(FitScoring.sanitizeInput(raw, spec)) },
        label = label,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (spec.decimals > 0) KeyboardType.Decimal else KeyboardType.Number,
        ),
        trailing = {
            Text(
                text = unit,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun ScoreCard(calc: FitCalc) {
    val gradePalette = com.furina.classtools.ui.theme.GradeColors.tokens()
    val gradeTone = if (calc.wtDone > 0) gradePalette.toneOf(calc.grade) else null
    FitCompactCard(
        title = "实时评分",
        trailing = {
            CtPill(
                text = if (calc.wtDone > 0) calc.grade else "未填写",
                containerColor = gradeTone?.container ?: androidx.compose.ui.graphics.Color.Transparent,
                contentColor = gradeTone?.content ?: MaterialTheme.colorScheme.onSurface,
            )
        },
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = calc.totalText,
                style = MaterialTheme.ctEmphasized.displayMediumEmphasized,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = " 分",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FitStat("标准分", calc.std.toString(), Modifier.weight(1f))
            FitStat("加分", calc.plus.toString(), Modifier.weight(1f))
            FitStat("缺项", calc.miss.toString(), Modifier.weight(1f))
            FitStat("已填权重", "${calc.wtDone}%", Modifier.weight(1f))
        }
        if (calc.miss > 0) {
            Text(
                text = "未填项按 0 计（共 ${calc.miss} 项缺项）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        calc.rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${row.sub} · 权重 ${row.weight}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = row.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(88.dp),
                )
                Text(
                    text = row.score?.toString() ?: "—",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (row.score == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(40.dp),
                )
                Text(
                    text = if (row.plus > 0) "+${row.plus}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(32.dp),
                )
            }
        }
    }
}



@Composable
private fun StatChip(label: String, value: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = " $value",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ActionRow(
    canSave: Boolean,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CtToolPrimaryButton(
            text = "保存记录",
            onClick = onSave,
            enabled = canSave,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onClear, shape = CtShapeTokens.pill) {
            Text("清空")
        }
        IconButton(onClick = onCopy) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = "复制成绩")
        }
        IconButton(onClick = onSave, enabled = canSave) {
            Icon(Icons.Rounded.Refresh, contentDescription = "重新计算（实时已算）")
        }
    }
}

@Composable
private fun HistoryCard(
    history: List<FitRecord>,
    onRestore: (Int) -> Unit,
    onRename: (Int) -> Unit,
    onDelete: (Int) -> Unit,
) {
    FitCompactCard(
        title = "历史记录",
        trailing = { CtPill(text = "最多 2 条") },
    ) {
        if (history.isEmpty()) {
            Text(
                text = "暂无记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            history.forEachIndexed { index, record ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
                            RoundedCornerShape(16.dp),
                        )
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = record.displayName(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${record.totalText()} 分 · ${record.grade}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = "${record.timeText()} · ${record.sexEnum.label} · ${record.gradeEnum.short}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = record.detailText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onRestore(index) }) { Text("回填") }
                        TextButton(onClick = { onRename(index) }) { Text("改名") }
                        TextButton(onClick = { onDelete(index) }) { Text("删除") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StandardsSheet(
    sex: FitSex,
    grade: FitGrade,
    onDismiss: () -> Unit,
) {
    val items = listOf(
        "肺活量" to FitTables.vc,
        "50 米跑" to FitTables.r50,
        "坐位体前屈" to FitTables.sit,
        "立定跳远" to FitTables.jump,
        "引体/仰卧" to if (sex == FitSex.M) FitTables.pull else FitTables.sup,
        "长跑" to FitTables.dur,
    )
    CtBottomSheet(onDismissRequest = onDismiss) {
        CtBottomSheetHeader(
            title = "国标表",
            subtitle = "${sex.label} · ${grade.label} · 等级分 100→10",
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items.forEach { (name, table) ->
                val column = table.column(sex, grade)
                if (column.isEmpty()) return@forEach
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "$name（${if (table.hi) "≥ 达标" else "≤ 达标"}）",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        column.forEachIndexed { index, threshold ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.padding(end = 4.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = FitTables.FTS.getOrNull(index)?.toString() ?: "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = if (threshold % 1.0 == 0.0) threshold.toInt().toString()
                                        else threshold.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Text(
                text = "数据来源：《国家学生体质健康标准（2014 年修订）》大学组；BMI 分档见评分卡。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FitCenteredLoading(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CtToolCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FitCenteredError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CtToolCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("加载失败", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = message.ifBlank { "请稍后重试" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                CtToolPrimaryButton(
                    text = "重试",
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
