package com.furina.classtools.features.draw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawWorkflowTest {

    private val random: DrawRandom = SeededDrawRandom(42)

    private fun readyPreviewState(): DrawWorkflowState {
        val transition = DrawWorkflow.toPreview(
            DrawWorkflowState(config = DrawConfig(start = 1, end = 10, count = 3)),
            rosterSeats = listOf(1, 2, 3, 4, 5),
        )
        return (transition as DrawTransition.Next).state
    }

    private fun readyResultState(): DrawWorkflowState {
        val preview = readyPreviewState()
        return (DrawWorkflow.startDraw(preview, random) as DrawTransition.Next).state
    }

    @Test
    fun `初始状态在第一步`() {
        assertEquals(DrawStep.CONFIG, DrawWorkflowState().step)
    }

    @Test
    fun `第一步可更新参数`() {
        val state = DrawWorkflow.updateConfig(DrawWorkflowState(), DrawConfig(count = 7))
        assertEquals(7, state.config.count)
    }

    @Test
    fun `结果页不可直接改参数`() {
        val result = readyResultState()
        val after = DrawWorkflow.updateConfig(result, DrawConfig(count = 99))
        assertEquals(3, after.config.count)
    }

    @Test
    fun `参数非法时不能进入第二步`() {
        val transition = DrawWorkflow.toPreview(DrawWorkflowState(config = DrawConfig(start = 9, end = 2)))
        val error = (transition as DrawTransition.Rejected).error
        assertEquals(DrawErrorCode.RANGE_INVALID, error.code)
    }

    @Test
    fun `进入第二步生成候选且带姓名映射`() {
        val transition = DrawWorkflow.toPreview(
            DrawWorkflowState(config = DrawConfig(start = 1, end = 5, count = 2, poolSource = DrawPoolSource.ROSTER)),
            rosterSeats = listOf(2, 4, 5),
            nameOf = { n -> if (n == 2) "张三" else null },
        )
        val state = (transition as DrawTransition.Next).state
        assertEquals(DrawStep.PREVIEW, state.step)
        assertEquals(listOf(2, 4, 5), state.candidates.map { it.number })
        assertEquals("张三", state.candidates.first().name)
        assertNull(state.result)
    }

    @Test
    fun `第二步不可重复进入`() {
        val transition = DrawWorkflow.toPreview(readyPreviewState())
        assertEquals(DrawErrorCode.INVALID_STEP, (transition as DrawTransition.Rejected).error.code)
    }

    @Test
    fun `第一步不能直接抽签`() {
        val transition = DrawWorkflow.startDraw(DrawWorkflowState(), random)
        assertEquals(DrawErrorCode.INVALID_STEP, (transition as DrawTransition.Rejected).error.code)
    }

    @Test
    fun `第二步抽签进入结果页`() {
        val state = readyResultState()
        assertEquals(DrawStep.RESULT, state.step)
        val result = state.result
        assertTrue(result != null)
        assertEquals(3, result!!.entries.size)
        assertEquals(10, result.pool.size) // 默认号码范围来源：1..10 共 10 个候选
    }

    @Test
    fun `返回上一步的出栈顺序`() {
        val resultState = readyResultState()
        val previewState = DrawWorkflow.back(resultState)
        assertEquals(DrawStep.PREVIEW, previewState.step)
        assertNull(previewState.result)
        assertTrue(previewState.candidates.isNotEmpty())

        val configState = DrawWorkflow.back(previewState)
        assertEquals(DrawStep.CONFIG, configState.step)
        assertTrue(configState.candidates.isEmpty())

        assertEquals(DrawStep.CONFIG, DrawWorkflow.back(configState).step)
    }

    @Test
    fun `只有结果页可以剔除重抽`() {
        val preview = readyPreviewState()
        val rejected = DrawWorkflow.removeAndRedraw(preview, 0, true, random)
        assertEquals(DrawErrorCode.INVALID_STEP, (rejected as DrawTransition.Rejected).error.code)

        val result = readyResultState()
        val next = DrawWorkflow.removeAndRedraw(result, 0, true, random) as DrawTransition.Next
        assertEquals(3, next.state.result!!.entries.size)
        assertEquals(1, next.state.result!!.log.size)
    }

    @Test
    fun `切换排序同步写回 config 与结果顺序`() {
        val result = readyResultState()
        val next = DrawWorkflow.changeSort(result, DrawSortMode.ASCENDING, random) as DrawTransition.Next
        assertEquals(DrawSortMode.ASCENDING, next.state.config.sort)
        assertEquals(DrawSortMode.ASCENDING, next.state.result!!.config.sort)
        val numbers = next.state.result!!.entries.map { it.number }
        assertEquals(numbers.sorted(), numbers)
    }

    @Test
    fun `预览页不能切换排序`() {
        val transition = DrawWorkflow.changeSort(readyPreviewState(), DrawSortMode.ASCENDING, random)
        assertEquals(DrawErrorCode.INVALID_STEP, (transition as DrawTransition.Rejected).error.code)
    }

    @Test
    fun `重新抽签保持参数与池`() {
        val result = readyResultState()
        val originalConfig = result.config
        val next = DrawWorkflow.reroll(result, SeededDrawRandom(7)) as DrawTransition.Next
        assertEquals(originalConfig, next.state.config)
        assertEquals(result.result!!.pool.map { it.number }, next.state.result!!.pool.map { it.number })
    }

    @Test
    fun `重置回到第一步并清空结果`() {
        val reset = DrawWorkflow.reset(readyResultState())
        assertEquals(DrawStep.CONFIG, reset.step)
        assertTrue(reset.candidates.isEmpty())
        assertNull(reset.result)
    }

    @Test
    fun `从结果页回填参数编辑`() {
        val state = DrawWorkflow.editConfigFromResult(readyResultState())
        assertEquals(DrawStep.CONFIG, state.step)
        assertNull(state.result)
    }
}
