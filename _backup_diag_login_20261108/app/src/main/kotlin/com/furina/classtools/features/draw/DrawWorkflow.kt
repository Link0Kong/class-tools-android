package com.furina.classtools.features.draw

/** 三步工作流：STEP1 配置 → STEP2 候选预览 → STEP3 结果。 */
enum class DrawStep(val index: Int, val title: String) {
    CONFIG(1, "配置"),
    PREVIEW(2, "候选"),
    RESULT(3, "结果"),
}

/** 工作流状态（不可变；UI 持有并由 [DrawWorkflow] 纯函数迁移）。 */
data class DrawWorkflowState(
    val step: DrawStep = DrawStep.CONFIG,
    val config: DrawConfig = DrawConfig(),
    /** STEP2 预览的候选池（含姓名映射快照）。 */
    val candidates: List<DrawEntry> = emptyList(),
    /** STEP3 结果；未抽签为 null。 */
    val result: DrawResult? = null,
)

/** 状态迁移结果：成功给新状态，失败给可展示的错误（不抛异常）。 */
sealed interface DrawTransition {
    data class Next(val state: DrawWorkflowState) : DrawTransition
    data class Rejected(val error: DrawError) : DrawTransition
}

/**
 * 抽签工作流状态机（纯函数，无副作用、无 Android 依赖）。
 *
 * 约束：
 * - 参数只在 STEP1 修改；进入 STEP2 后固化候选池，保证「预览的候选 = 实际抽签池」；
 * - 返回操作：STEP3→STEP2→STEP1；STEP2→STEP1 丢弃候选；STEP3→STEP2 丢弃结果；
 * - 剔除/重抽只允许在 STEP3；排序修改会同步写回 `config.sort`（随 userdata.draw 同步）。
 */
object DrawWorkflow {

    fun updateConfig(state: DrawWorkflowState, config: DrawConfig): DrawWorkflowState =
        if (state.step == DrawStep.CONFIG) state.copy(config = config) else state

    /** STEP1 → STEP2：校验参数并生成候选（失败行内报错，不进入下一步）。 */
    fun toPreview(
        state: DrawWorkflowState,
        rosterSeats: List<Int> = emptyList(),
        nameOf: (Int) -> String? = { null },
    ): DrawTransition {
        if (state.step != DrawStep.CONFIG) {
            return DrawTransition.Rejected(
                DrawError(DrawErrorCode.INVALID_STEP, "当前步骤不能重新生成候选"),
            )
        }
        return when (val validation = DrawValidator.validate(state.config, rosterSeats)) {
            is DrawValidation.Invalid -> DrawTransition.Rejected(validation.error)
            is DrawValidation.Valid -> DrawTransition.Next(
                state.copy(
                    step = DrawStep.PREVIEW,
                    candidates = DrawEngine.entriesFor(validation.numbers, nameOf),
                    result = null,
                ),
            )
        }
    }

    /** STEP2 → STEP3：从固化的候选池抽签。 */
    fun startDraw(state: DrawWorkflowState, random: DrawRandom): DrawTransition {
        if (state.step != DrawStep.PREVIEW) {
            return DrawTransition.Rejected(
                DrawError(DrawErrorCode.INVALID_STEP, "请先预览候选再开始抽签"),
            )
        }
        return when (val outcome = DrawEngine.drawFromCandidates(state.config, state.candidates, random)) {
            is DrawOutcome.Failure -> DrawTransition.Rejected(outcome.error)
            is DrawOutcome.Success -> DrawTransition.Next(
                state.copy(step = DrawStep.RESULT, result = outcome.result),
            )
        }
    }

    /** 返回上一步；STEP1 由调用方决定是否退出页面（返回 null 表示已在第一步）。 */
    fun back(state: DrawWorkflowState): DrawWorkflowState = when (state.step) {
        DrawStep.CONFIG -> state
        DrawStep.PREVIEW -> state.copy(step = DrawStep.CONFIG, candidates = emptyList())
        DrawStep.RESULT -> state.copy(step = DrawStep.PREVIEW, result = null)
    }

    /** STEP3：剔除第 [index] 项并按 [autoRedraw] 决定是否补抽。 */
    fun removeAndRedraw(
        state: DrawWorkflowState,
        index: Int,
        autoRedraw: Boolean,
        random: DrawRandom,
    ): DrawTransition {
        if (state.step != DrawStep.RESULT) {
            return DrawTransition.Rejected(
                DrawError(DrawErrorCode.INVALID_STEP, "只有结果页可以剔除重抽"),
            )
        }
        val result = state.result
            ?: return DrawTransition.Rejected(DrawError(DrawErrorCode.INVALID_INDEX, "还没有抽签结果"))
        return when (val outcome = DrawEngine.removeAndRedraw(result, index, autoRedraw, random)) {
            is DrawOutcome.Failure -> DrawTransition.Rejected(outcome.error)
            is DrawOutcome.Success -> DrawTransition.Next(state.copy(result = outcome.result))
        }
    }

    /** STEP3：切换排序模式（同步写回 config.sort，下次抽签/跨端一致）。 */
    fun changeSort(state: DrawWorkflowState, mode: DrawSortMode, random: DrawRandom): DrawTransition {
        if (state.step != DrawStep.RESULT) {
            return DrawTransition.Rejected(
                DrawError(DrawErrorCode.INVALID_STEP, "只有结果页可以调整排序"),
            )
        }
        val result = state.result
            ?: return DrawTransition.Rejected(DrawError(DrawErrorCode.INVALID_INDEX, "还没有抽签结果"))
        val newConfig = state.config.copy(sort = mode)
        val sorted = DrawEngine.sortEntries(result.entries, mode, random)
        return DrawTransition.Next(
            state.copy(
                config = newConfig,
                result = result.copy(entries = sorted, config = newConfig),
            ),
        )
    }

    /** STEP3：同参数重新抽一次（结果集合变化，log 清空）。 */
    fun reroll(state: DrawWorkflowState, random: DrawRandom): DrawTransition {
        if (state.step != DrawStep.RESULT) {
            return DrawTransition.Rejected(
                DrawError(DrawErrorCode.INVALID_STEP, "只有结果页可以重新抽签"),
            )
        }
        val result = state.result
            ?: return DrawTransition.Rejected(DrawError(DrawErrorCode.INVALID_INDEX, "还没有抽签结果"))
        return when (val outcome = DrawEngine.reroll(result, random)) {
            is DrawOutcome.Failure -> DrawTransition.Rejected(outcome.error)
            is DrawOutcome.Success -> DrawTransition.Next(state.copy(result = outcome.result))
        }
    }

    /** 重置回 STEP1（保留参数，清空候选与结果）。 */
    fun reset(state: DrawWorkflowState): DrawWorkflowState =
        state.copy(step = DrawStep.CONFIG, candidates = emptyList(), result = null)

    /** 从结果回填参数（用于「调整参数后重新抽」快捷路径，等价于 back+reset）。 */
    fun editConfigFromResult(state: DrawWorkflowState): DrawWorkflowState =
        if (state.result == null) state else state.copy(step = DrawStep.CONFIG, candidates = emptyList(), result = null)
}
