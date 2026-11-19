package com.furina.classtools.ui.screens.draw

import com.furina.classtools.ui.components.light.ctLightMaterial
import com.furina.classtools.ui.theme.CtLightScene
import com.furina.classtools.ui.theme.CtShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.member.ClassMember
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.sync.UserDataRepository
import com.furina.classtools.features.draw.DrawConfig
import com.furina.classtools.features.draw.DrawEntry
import com.furina.classtools.features.draw.DrawError
import com.furina.classtools.features.draw.DrawExportFactory
import com.furina.classtools.features.draw.DrawExportPayload
import com.furina.classtools.features.draw.DrawRandom
import com.furina.classtools.features.draw.DrawResult
import com.furina.classtools.features.draw.DrawResultSheetPayload
import com.furina.classtools.features.draw.DrawSheetFactory
import com.furina.classtools.features.draw.DrawSortMode
import com.furina.classtools.features.draw.DrawStep
import com.furina.classtools.features.draw.DrawTransition
import com.furina.classtools.features.draw.DrawValidation
import com.furina.classtools.features.draw.DrawValidator
import com.furina.classtools.features.draw.DrawWorkflow
import com.furina.classtools.features.draw.DrawWorkflowState
import com.furina.classtools.features.draw.UserDataDrawConfigStore
import com.furina.classtools.features.draw.defaultDrawRandom
import com.furina.classtools.ui.components.CtLoading
import com.furina.classtools.ui.components.CtPill
import com.furina.classtools.ui.components.ctTopBarRaw
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.theme.CtSpace
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 抽签工具原生页面（三步 vertical slice）。
 *
 * STEP1 配置 → STEP2 候选预览 → STEP3 结果（支持剔除/重抽/排序/导出 payload）。
 *
 * 接线方式（由 Lead 在 MainNavHost 完成）：
 * ```kotlin
 * composable(Routes.tool("draw")) {
 *     DrawScreen(
 *         onBack = { navController.popBackStack() },
 *         session = session,
 *         members = classMembers,               // 可选：来自 ClassMembersRepository 缓存
 *         userDataRepository = container.userDataRepository,
 *         onExport = { payload -> exportCoordinator.exportDraw(payload) },
 *     )
 * }
 * ```
 *
 * 设计约束：
 * - 页面不自己构造 AppContainer/仓库，全部依赖由参数注入（便于预览与测试）；
 * - 参数持久化走 `UserDataRepository` 的 `draw` section（`USERDATA_V6.md`），失败静默（抽签本身不受影响）；
 * - 导出本轮只产出 [DrawExportPayload]，不绘制 Canvas/不引第三方库；
 * - 四态：正常 / 空（无结果）/ 加载（恢复参数、抽签中）/ 错误（未登录或恢复失败）。
 *
 * @param initialConfig 预览/测试注入的初始参数
 * @param initialStep 预览/测试注入的初始步骤
 * @param initialCandidates 预览注入的候选
 * @param initialResult 预览注入的结果
 * @param forcedErrorMessage 预览注入的错误态（非空时直接显示错误页）
 * @param skipRestore 预览/测试：跳过 userdata 恢复
 */
@Composable
fun DrawScreen(
    onBack: () -> Unit,
    session: Session?,
    onExport: (DrawExportPayload) -> Unit,
    onGroup: (DrawResult) -> Unit = {},
    onExportSheet: (DrawResultSheetPayload) -> Unit = {},
    modifier: Modifier = Modifier,
    members: List<ClassMember> = emptyList(),
    userDataRepository: UserDataRepository? = null,
    initialConfig: DrawConfig = DrawConfig(),
    initialStep: DrawStep = DrawStep.CONFIG,
    initialCandidates: List<DrawEntry> = emptyList(),
    initialResult: DrawResult? = null,
    forcedErrorMessage: String? = null,
    skipRestore: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val random: DrawRandom = remember { defaultDrawRandom() }
    val snackbar = remember { SnackbarHostState() }
    // D-08：参数已自动持久化的首次提示（不打扰；仅本屏首次保存成功时出现）。
    var configSavedHintShown by remember { mutableStateOf(false) }
    var configFirstPersistDone by remember { mutableStateOf(false) }

    val initialWorkflow = remember {
        val base = DrawWorkflowState(step = initialStep, config = initialConfig)
        when (initialStep) {
            DrawStep.CONFIG -> base
            DrawStep.PREVIEW -> base.copy(candidates = initialCandidates)
            DrawStep.RESULT -> base.copy(candidates = initialCandidates, result = initialResult)
        }
    }
    // 跨导航（分组页返回）/旋转/进程重建恢复工作流（step/candidates/result/log）；预览 initial* 语义不变
    var workflow by rememberSaveable(stateSaver = DrawWorkflowSaver) { mutableStateOf(initialWorkflow) }
    var phase by remember {
        mutableStateOf(if (skipRestore || forcedErrorMessage != null) Phase.READY else Phase.LOADING)
    }
    var restoreError by remember { mutableStateOf(forcedErrorMessage) }
    var restoreAttempt by remember { mutableIntStateOf(0) }
    var isDrawing by remember { mutableStateOf(false) }
    var autoRedraw by rememberSaveable { mutableStateOf(true) }

    val configStore = remember(userDataRepository) {
        userDataRepository?.let { UserDataDrawConfigStore(it) }
    }
    val rosterSeats = remember(members) {
        members.mapNotNull { member -> member.seat.takeIf { it > 0 } }.distinct()
    }
    val nameOf: (Int) -> String? = remember(members) {
        { number -> members.firstOrNull { it.seat == number }?.name?.takeIf { it.isNotBlank() } }
    }
    val validation = remember(workflow.config, rosterSeats) {
        DrawValidator.validate(workflow.config, rosterSeats)
    }
    val configError: DrawError? = (validation as? DrawValidation.Invalid)?.error
    val poolNumbers: List<Int>? = (validation as? DrawValidation.Valid)?.numbers

    fun notify(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun applyTransition(transition: DrawTransition) {
        when (transition) {
            is DrawTransition.Next -> workflow = transition.state
            is DrawTransition.Rejected -> notify(transition.error.message)
        }
    }

    fun handleBack() {
        if (workflow.step == DrawStep.CONFIG) onBack() else workflow = DrawWorkflow.back(workflow)
    }

    fun startDraw() {
        if (isDrawing) return
        scope.launch {
            isDrawing = true
            try {
                delay(MIN_DRAWING_FEEDBACK_MS)
                applyTransition(DrawWorkflow.startDraw(workflow, random))
            } finally {
                isDrawing = false
            }
        }
    }

    // 恢复 draw section（一次性；失败静默回落默认参数）。
    LaunchedEffect(restoreAttempt) {
        if (skipRestore || forcedErrorMessage != null) {
            phase = Phase.READY
            return@LaunchedEffect
        }
        phase = Phase.LOADING
        if (session == null) {
            restoreError = "请先完成身份验证后再使用抽签"
            phase = Phase.FAILED
            return@LaunchedEffect
        }
        restoreError = null
        val loaded = runCatching { configStore?.load() }.getOrNull()
        if (loaded != null) workflow = workflow.copy(config = loaded)
        phase = Phase.READY
    }

    // 参数防抖持久化（600ms；失败静默；未登录/预览注入时不写）。
    LaunchedEffect(workflow.config) {
        if (skipRestore || forcedErrorMessage != null) return@LaunchedEffect
        if (phase != Phase.READY || restoreError != null) return@LaunchedEffect
        val store = configStore ?: return@LaunchedEffect
        if (session == null) return@LaunchedEffect
        delay(PERSIST_DEBOUNCE_MS)
        val saved = runCatching { store.save(workflow.config) }.isSuccess
        if (saved) {
            if (!configFirstPersistDone) {
                // 首次持久化通常是恢复的旧参数，不提示；用户改动后的首次保存再提示。
                configFirstPersistDone = true
            } else if (!configSavedHintShown) {
                configSavedHintShown = true
                snackbar.showSnackbar("参数已保存")
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
                    subtitle = null,
                    title = "抽签",
                    onBack = ::handleBack,
                    actions = {
                        val entryCount = workflow.result?.entries?.size ?: 0
                        if (phase == Phase.READY && workflow.step == DrawStep.RESULT && entryCount > 0) {
                            CtPill(text = "$entryCount 个")
                        }
                    },
                )

                Box(modifier = Modifier.weight(1f)) {
                    when {
                        phase == Phase.LOADING -> CtLoading(
                            modifier = Modifier.align(Alignment.Center),
                            text = "恢复抽签参数…",
                        )

                        !restoreError.isNullOrBlank() -> DrawErrorState(
                            message = restoreError.orEmpty(),
                            onRetry = { restoreAttempt += 1 },
                            onBack = onBack,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )

                        else -> DrawReadyContent(
                            workflow = workflow,
                            configError = configError,
                            poolNumbers = poolNumbers,
                            rosterCount = rosterSeats.size,
                            autoRedraw = autoRedraw,
                            isDrawing = isDrawing,
                            onConfigChange = { config -> workflow = DrawWorkflow.updateConfig(workflow, config) },
                            onToggleAutoRedraw = { autoRedraw = it },
                            onRemove = { index ->
                                applyTransition(
                                    DrawWorkflow.removeAndRedraw(workflow, index, autoRedraw, random),
                                )
                            },
                            onSortChange = { mode ->
                                applyTransition(DrawWorkflow.changeSort(workflow, mode, random))
                            },
                            onReroll = {
                                applyTransition(DrawWorkflow.reroll(workflow, random))
                                notify("已按相同参数重新抽签")
                            },
                            onEditConfig = { workflow = DrawWorkflow.editConfigFromResult(workflow) },
                            onExport = {
                                val result = workflow.result
                                if (result == null) {
                                    notify("还没有抽签结果")
                                } else {
                                    onExport(DrawExportFactory.create(result))
                                    notify("已生成导出参数（导出卡片接入中）")
                                }
                            },
                            onGroup = { result -> onGroup(result) },
                            onExportSheet = { result ->
                                onExportSheet(DrawSheetFactory.create(result))
                                notify("已生成结果单参数")
                            },
                            onSelectStep = { target ->
                                while (workflow.step.index > target.index) {
                                    workflow = DrawWorkflow.back(workflow)
                                }
                            },
                        )
                    }
                }

                if (phase == Phase.READY && restoreError.isNullOrBlank()) {
                    DrawBottomBar(
                        step = workflow.step,
                        canContinue = configError == null,
                        isDrawing = isDrawing,
                        onNext = {
                            when (workflow.step) {
                                DrawStep.CONFIG -> applyTransition(
                                    DrawWorkflow.toPreview(workflow, rosterSeats, nameOf),
                                )

                                DrawStep.RESULT -> onBack()
                                DrawStep.PREVIEW -> Unit
                            }
                        },
                        onStartDraw = ::startDraw,
                        onBackStep = { workflow = DrawWorkflow.back(workflow) },
                        modifier = Modifier.navigationBarsPadding(),
                    )
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
                    .padding(bottom = 92.dp),
            )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 内容布局（自适应：compact 单列 / ≥600dp 左右分栏）
// -----------------------------------------------------------------------------

@Composable
private fun DrawReadyContent(
    workflow: DrawWorkflowState,
    configError: DrawError?,
    poolNumbers: List<Int>?,
    rosterCount: Int,
    autoRedraw: Boolean,
    isDrawing: Boolean,
    onConfigChange: (DrawConfig) -> Unit,
    onToggleAutoRedraw: (Boolean) -> Unit,
    onRemove: (Int) -> Unit,
    onSortChange: (DrawSortMode) -> Unit,
    onReroll: () -> Unit,
    onEditConfig: () -> Unit,
    onExport: () -> Unit,
    onGroup: (DrawResult) -> Unit,
    onExportSheet: (DrawResult) -> Unit,
    onSelectStep: (DrawStep) -> Unit,
) {
    // D-02：记录哪一次结果已用于分组（同一结果对象再次进入结果页保持徽标；重新抽签后自然重置）。
    var groupedResult by remember { mutableStateOf<DrawResult?>(null) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE_BREAKPOINT
        val horizontal = if (wide) 20.dp else 12.dp

        if (wide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontal, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.56f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DrawStepIndicator(current = workflow.step, onSelect = onSelectStep)
                    if (workflow.step == DrawStep.CONFIG) {
                        DrawConfigCard(
                            config = workflow.config,
                            error = configError,
                            rosterCount = rosterCount,
                            enabled = !isDrawing,
                            onConfigChange = onConfigChange,
                        )
                    } else {
                        DrawConfigSummaryCard(config = workflow.config)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(0.44f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (workflow.step) {
                        DrawStep.CONFIG -> DrawPoolPreviewCard(
                            config = workflow.config,
                            numbers = poolNumbers,
                            error = configError,
                        )

                        DrawStep.PREVIEW -> DrawPreviewCard(candidates = workflow.candidates)

                        DrawStep.RESULT -> DrawResultCard(
                            result = workflow.result,
                            autoRedraw = autoRedraw,
                            grouped = groupedResult === workflow.result,
                            onToggleAutoRedraw = onToggleAutoRedraw,
                            onRemove = onRemove,
                            onSortChange = onSortChange,
                            onReroll = onReroll,
                            onEditConfig = onEditConfig,
                            onExport = onExport,
                            onGroup = {
                                workflow.result?.let {
                                    groupedResult = it
                                    onGroup(it)
                                }
                            },
                            onExportSheet = { workflow.result?.let(onExportSheet) },
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = horizontal),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DrawStepIndicator(current = workflow.step, onSelect = onSelectStep)

                when (workflow.step) {
                    DrawStep.CONFIG -> {
                        DrawConfigCard(
                            config = workflow.config,
                            error = configError,
                            rosterCount = rosterCount,
                            enabled = !isDrawing,
                            onConfigChange = onConfigChange,
                        )
                        DrawPoolPreviewCard(
                            config = workflow.config,
                            numbers = poolNumbers,
                            error = configError,
                        )
                    }

                    DrawStep.PREVIEW -> {
                        DrawConfigSummaryCard(config = workflow.config)
                        DrawPreviewCard(candidates = workflow.candidates)
                    }

                    DrawStep.RESULT -> {
                        DrawConfigSummaryCard(config = workflow.config)
                        DrawResultCard(
                            result = workflow.result,
                            autoRedraw = autoRedraw,
                            grouped = groupedResult === workflow.result,
                            onToggleAutoRedraw = onToggleAutoRedraw,
                            onRemove = onRemove,
                            onSortChange = onSortChange,
                            onReroll = onReroll,
                            onEditConfig = onEditConfig,
                            onExport = onExport,
                            onGroup = {
                                workflow.result?.let {
                                    groupedResult = it
                                    onGroup(it)
                                }
                            },
                            onExportSheet = { workflow.result?.let(onExportSheet) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(CtSpace.xs))
            }
        }
    }
}

/** 页面内部阶段（四态中的加载/错误两态由它承载）。 */
private enum class Phase { LOADING, READY, FAILED }

private val WIDE_BREAKPOINT = 600.dp
private const val PERSIST_DEBOUNCE_MS = 600L
private const val MIN_DRAWING_FEEDBACK_MS = 150L
