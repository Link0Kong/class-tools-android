package com.furina.classtools.ui.screens.draw

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.member.ClassMember
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.sync.UserDataRepository
import com.furina.classtools.features.draw.DrawConfig
import com.furina.classtools.features.draw.DrawConfigCodec
import com.furina.classtools.features.draw.DrawEngine
import com.furina.classtools.features.draw.DrawEntry
import com.furina.classtools.features.draw.DrawExportFactory
import com.furina.classtools.features.draw.DrawExportPayload
import com.furina.classtools.features.draw.DrawLastResult
import com.furina.classtools.features.draw.DrawLastResultStore
import com.furina.classtools.features.draw.DrawPoolSource
import com.furina.classtools.features.draw.DrawResult
import com.furina.classtools.features.draw.DrawStep
import com.furina.classtools.features.draw.DrawTransition
import com.furina.classtools.features.draw.DrawValidation
import com.furina.classtools.features.draw.DrawValidator
import com.furina.classtools.features.draw.DrawWorkflow
import com.furina.classtools.features.draw.DrawWorkflowState
import com.furina.classtools.features.draw.UserDataDrawConfigStore
import com.furina.classtools.features.draw.defaultDrawRandom
import com.furina.classtools.ui.components.CtGlassIconButton
import com.furina.classtools.ui.components.CtToastBus
import com.furina.classtools.ui.components.EmptyState
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.components.RosterPickerSheet
import com.furina.classtools.ui.components.ctTopBarRaw
import com.furina.classtools.ui.theme.CtSpace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DrawScreen(
    onBack: () -> Unit,
    session: Session?,
    onExport: (DrawExportPayload) -> Unit,
    lastResultStore: DrawLastResultStore? = null,
    modifier: Modifier = Modifier,
    members: List<ClassMember> = emptyList(),
    userDataRepository: UserDataRepository? = null,
    initialConfig: DrawConfig = DrawConfig(),
    initialStep: DrawStep = DrawStep.CONFIG,
    initialCandidates: List<DrawEntry> = emptyList(),
    initialResult: DrawResult? = null,
    forcedErrorMessage: String? = null,
    skipRestore: Boolean = false,
    loadMembers: (suspend () -> List<ClassMember>)? = null,
    /** 扫码核验：打开「扫一扫」（抽签存证 / 电脑扫码登录同一入口）。 */
    onScan: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = if (skipRestore && initialResult != null) 1 else 0)
    val focus = LocalFocusManager.current
    val random = remember { defaultDrawRandom() }
    val savedConfig = remember(session?.sid, userDataRepository) {
        if (skipRestore) initialConfig
        else DrawConfigCodec.fromJson(userDataRepository?.currentSections?.value?.get("draw"), initialConfig)
    }
    var workflow by rememberSaveable(session?.sid, stateSaver = DrawWorkflowSaver) {
        mutableStateOf(DrawWorkflowState(initialStep, savedConfig, initialCandidates, initialResult))
    }
    var autoRedraw by rememberSaveable { mutableStateOf(true) }
    var showOptions by rememberSaveable { mutableStateOf(workflow.config.poolSource == DrawPoolSource.ROSTER) }
    var showRoster by rememberSaveable { mutableStateOf(false) }
    var selectedSeats by rememberSaveable { mutableStateOf(arrayListOf<Int>()) }
    var rosterAttempt by remember { mutableIntStateOf(0) }
    var loadedRoster by remember(session?.sid) { mutableStateOf<List<ClassMember>?>(null) }
    var showLog by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    val roster = loadedRoster ?: members
    val names = remember(roster) {
        roster.filter { it.seat > 0 }.associate { it.seat to it.name }
    }
    val rosterSeats = remember(names, selectedSeats) {
        if (selectedSeats.isEmpty()) names.keys.toList() else selectedSeats.filter { it in names }
    }
    val config = workflow.config
    val result = workflow.result
    val validation = remember(config, rosterSeats) { DrawValidator.validate(config, rosterSeats) }
    val screenError = forcedErrorMessage ?: if (session == null && !skipRestore) "请先完成身份验证后再使用抽签" else null

    val needsRoster = showOptions || config.poolSource == DrawPoolSource.ROSTER
    LaunchedEffect(needsRoster, loadMembers, rosterAttempt) {
        if (!needsRoster || loadMembers == null || members.isNotEmpty()) return@LaunchedEffect
        if (loadedRoster?.isNotEmpty() == true) return@LaunchedEffect
        loadedRoster = null
        loadedRoster = runCatching { loadMembers() }.getOrElse {
            if (it is CancellationException) throw it
            emptyList()
        }
    }

    val configStore = remember(userDataRepository) { userDataRepository?.let(::UserDataDrawConfigStore) }
    var persistedConfig by remember { mutableStateOf(DrawConfigCodec.toJson(savedConfig)) }
    val configJson = remember(config) { DrawConfigCodec.toJson(config) }
    LaunchedEffect(configJson, validation is DrawValidation.Valid) {
        if (skipRestore || screenError != null || configJson == persistedConfig ||
            validation !is DrawValidation.Valid || configStore == null
        ) return@LaunchedEffect
        delay(400)
        runCatching { configStore.save(config) }.onSuccess { persistedConfig = configJson }
    }

    fun applyTransition(transition: DrawTransition) {
        when (transition) {
            is DrawTransition.Next -> {
                workflow = transition.state
                transition.state.result?.takeUnless { it.isEmpty }?.let { next ->
                    runCatching { lastResultStore?.save(DrawLastResult.from(next)) }
                }
            }
            is DrawTransition.Rejected -> CtToastBus.show(transition.error.message)
        }
    }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            ctTopBarRaw(
                title = "抽签",
                onBack = onBack,
                actions = {
                    if (onScan != null) {
                        CtGlassIconButton(
                            icon = Icons.Rounded.QrCodeScanner,
                            contentDescription = "扫码核验",
                            onClick = onScan,
                        )
                    }
                },
            )

            if (screenError != null) {
                EmptyState(icon = Icons.Rounded.Casino, title = "暂时无法抽签", message = screenError)
                return@Column
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(CtSpace.md),
                    verticalArrangement = Arrangement.spacedBy(CtSpace.sm),
                ) {
                    item(key = "parameters", contentType = "parameters") {
                        DrawConfiguration(
                            config = config,
                            validation = validation,
                            showOptions = showOptions,
                            onToggleOptions = { showOptions = !showOptions },
                            autoRedraw = autoRedraw,
                            onAutoRedrawChange = { autoRedraw = it },
                            rosterCount = rosterSeats.size,
                            rosterLoading = loadMembers != null && loadedRoster == null && names.isEmpty(),
                            onPickRoster = {
                                if (names.isNotEmpty()) showRoster = true else rosterAttempt++
                            },
                            onConfigChange = { next ->
                                if (next.sort != config.sort && result != null) {
                                    applyTransition(DrawWorkflow.changeSort(workflow, next.sort, random))
                                } else {
                                    workflow = workflow.copy(config = next)
                                }
                            },
                        )
                    }
                    item(key = "result", contentType = "result") {
                        DrawResultHeader(
                            result = result,
                            onExport = { result?.let { onExport(DrawExportFactory.create(it)) } },
                            onRefill = {
                                result?.let {
                                    val filled = DrawEngine.refill(it, random)
                                    if (filled === it) CtToastBus.show("候选池中已无可补抽的号码")
                                    else applyTransition(DrawTransition.Next(workflow.copy(result = filled)))
                                }
                            },
                            onEdit = { scope.launch { listState.scrollToItem(0) } },
                            onClear = { confirmClear = true },
                        )
                    }
                    itemsIndexed(
                        items = result?.entries.orEmpty(),
                        key = { index, _ -> "entry-$index" },
                        contentType = { _, _ -> "entry" },
                    ) { index, entry ->
                        DrawResultEntry(entry) {
                            val previousCount = workflow.result?.entries?.size ?: 0
                            applyTransition(DrawWorkflow.removeAndRedraw(workflow, index, autoRedraw, random))
                            if (autoRedraw && (workflow.result?.entries?.size ?: 0) < previousCount) {
                                CtToastBus.show("已剔除，候选池中已无可补抽的号码")
                            }
                        }
                    }
                    if (result != null && result.log.isNotEmpty()) {
                        item(key = "log", contentType = "log") {
                            DrawLogToggle(result.log.size, showLog, onClick = { showLog = !showLog })
                        }
                        if (showLog) {
                            itemsIndexed(
                                items = result.log,
                                key = { index, _ -> "log-$index" },
                                contentType = { _, _ -> "log-entry" },
                            ) { _, record -> DrawLogEntry(record) }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(CtSpace.md),
                    contentAlignment = Alignment.Center,
                ) {
                    Button(
                        enabled = validation is DrawValidation.Valid,
                        modifier = Modifier.widthIn(max = 608.dp).fillMaxWidth(),
                        onClick = {
                            focus.clearFocus()
                            val nameOf: (Int) -> String? = if (config.poolSource == DrawPoolSource.ROSTER) {
                                { number -> names[number] }
                            } else {
                                { null }
                            }
                            val transition = DrawWorkflow.quickDraw(workflow, rosterSeats, nameOf, random)
                            applyTransition(transition)
                            if (transition is DrawTransition.Next) {
                                showLog = false
                                scope.launch { listState.scrollToItem(1) }
                            }
                        },
                    ) {
                        Text(if (result == null) "开始抽签" else "重新抽签")
                    }
                }
            }
        }
    }

    if (confirmClear && result != null) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空本轮结果？") },
            text = { Text("本页的抽签结果和剔除记录将被清空。") },
            confirmButton = {
                TextButton(onClick = {
                    workflow = DrawWorkflow.reset(workflow)
                    showLog = false
                    confirmClear = false
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
    if (showRoster && names.isNotEmpty()) {
        RosterPickerSheet(
            members = names.mapValues { (seat, name) -> "$seat $name" },
            selected = rosterSeats.toSet(),
            onConfirm = { seats ->
                selectedSeats = ArrayList(seats)
                workflow = workflow.copy(
                    config = config.copy(poolSource = if (seats.isEmpty()) DrawPoolSource.RANGE else DrawPoolSource.ROSTER),
                )
                showRoster = false
            },
            onDismiss = { showRoster = false },
        )
    }
}
