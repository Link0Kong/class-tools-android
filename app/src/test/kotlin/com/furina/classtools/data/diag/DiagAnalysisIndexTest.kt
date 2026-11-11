package com.furina.classtools.data.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 结论下挂映射单测（契约 v2.1 §A-1/3/6）：
 * - `related.health_ids` → 服务健康行；`related.checks` → 设备检查行；
 * - 正常行不挂 AI（分析进「其它诊断」）；
 * - 无关联/未知 id/旧数据 → others；orphan repair → others；
 * - `related.repair_id` 或 repair.analysis_id 匹配关联修复。
 */
class DiagAnalysisIndexTest {

    private fun analysis(
        id: String,
        status: String = "new",
        healthIds: List<String> = emptyList(),
        checks: List<String> = emptyList(),
        repairId: String = "",
        fixable: Boolean = false,
        action: String = "",
        title: String = id,
    ): DiagAnalysis = DiagAnalysis(
        id = id,
        ts = 1_000L,
        platform = "android",
        scope = "cloud",
        severity = "warn",
        title = title,
        cause = "",
        suggestions = emptyList(),
        fixable = fixable,
        action = action,
        status = status,
        confidence = 0.5,
        flow = emptyList(),
        relatedHealthIds = healthIds,
        relatedChecks = checks,
        relatedRepairId = repairId,
    )

    private fun repair(
        id: String,
        analysisId: String = "",
        status: String = "done",
    ): RepairTask = RepairTask(
        id = id,
        createdAt = 100L,
        action = "recheck_health",
        reason = "",
        status = status,
        claimedAt = 0L,
        finishedAt = 0L,
        result = "",
        analysisId = analysisId,
        node = "local",
    )

    @Test
    fun mapsByHealthId() {
        val index = DiagAnalysisIndex.build(
            analyses = listOf(analysis("a1", healthIds = listOf("cloud_xgj_sync"))),
            anomalyHealthIds = setOf("cloud_xgj_sync"),
        )
        assertEquals(listOf("a1"), index.analysesForHealth("cloud_xgj_sync").map { it.id })
        assertTrue(index.others.isEmpty())
    }

    @Test
    fun mapsByCheckId() {
        val index = DiagAnalysisIndex.build(
            analyses = listOf(analysis("a1", checks = listOf("gateway"))),
            anomalyCheckIds = setOf("gateway"),
        )
        assertEquals(listOf("a1"), index.analysesForCheck("gateway").map { it.id })
        assertTrue(index.others.isEmpty())
    }

    @Test
    fun multiLink_appearsUnderBothRows() {
        val index = DiagAnalysisIndex.build(
            analyses = listOf(analysis("a1", healthIds = listOf("cloudbase"), checks = listOf("network"))),
            anomalyHealthIds = setOf("cloudbase"),
            anomalyCheckIds = setOf("network"),
        )
        assertEquals(1, index.analysesForHealth("cloudbase").size)
        assertEquals(1, index.analysesForCheck("network").size)
        assertTrue(index.others.isEmpty())
    }

    @Test
    fun multipleAnalysesUnderOneRowKeepOrder() {
        val index = DiagAnalysisIndex.build(
            analyses = listOf(
                analysis("a1", healthIds = listOf("row")),
                analysis("a2", healthIds = listOf("row")),
            ),
            anomalyHealthIds = setOf("row"),
        )
        assertEquals(listOf("a1", "a2"), index.analysesForHealth("row").map { it.id })
    }

    @Test
    fun unknownLinkedIds_goOthers() {
        val index = DiagAnalysisIndex.build(
            analyses = listOf(analysis("a1", healthIds = listOf("missing"), checks = listOf("nope"))),
            anomalyHealthIds = setOf("known"),
            anomalyCheckIds = setOf("gateway"),
        )
        assertEquals(listOf("a1"), index.others.map { it.id })
        assertEquals(1, index.othersCount)
    }

    @Test
    fun linkedToNormalRow_goOthers() {
        // 行已恢复正常 → 不挂 AI 行（契约 §A-4：正常行不显示 AI 行）
        val index = DiagAnalysisIndex.build(
            analyses = listOf(analysis("a1", healthIds = listOf("cloudbase"))),
            anomalyHealthIds = emptySet(),
        )
        assertEquals(listOf("a1"), index.others.map { it.id })
    }

    @Test
    fun oldDataWithoutRelated_goOthers() {
        val index = DiagAnalysisIndex.build(analyses = listOf(analysis("legacy")))
        assertEquals(listOf("legacy"), index.others.map { it.id })
    }

    @Test
    fun relatedRepair_byRepairId() {
        val index = DiagAnalysisIndex.build(
            analyses = listOf(analysis("a1", checks = listOf("gateway"), repairId = "rt-1")),
            repairs = listOf(repair("rt-1", analysisId = "a1")),
            anomalyCheckIds = setOf("gateway"),
        )
        assertEquals("rt-1", index.repairFor(index.analysesForCheck("gateway").single())?.id)
    }

    @Test
    fun relatedRepair_fallsBackToAnalysisId() {
        val index = DiagAnalysisIndex.build(
            analyses = listOf(analysis("a1", healthIds = listOf("row"))),
            repairs = listOf(repair("rt-x", analysisId = "a1")),
            anomalyHealthIds = setOf("row"),
        )
        assertEquals("rt-x", index.repairFor(index.analysesForHealth("row").single())?.id)
    }

    @Test
    fun orphanRepairs_listedSeparately() {
        val index = DiagAnalysisIndex.build(
            analyses = listOf(analysis("a1", healthIds = listOf("row"))),
            repairs = listOf(repair("rt-1", analysisId = "a1"), repair("rt-orphan", analysisId = "")),
            anomalyHealthIds = setOf("row"),
        )
        assertEquals(listOf("rt-orphan"), index.orphanRepairs.map { it.id })
        assertEquals(1, index.othersCount)
    }

    @Test
    fun othersCount_includesAnalysesAndOrphanRepairs() {
        val index = DiagAnalysisIndex.build(
            analyses = listOf(analysis("a1"), analysis("a2")),
            repairs = listOf(repair("rt-orphan")),
        )
        assertEquals(3, index.othersCount)
    }

    @Test
    fun emptyInputs_areSafe() {
        val index = DiagAnalysisIndex.build(emptyList())
        assertTrue(index.byHealthId.isEmpty())
        assertTrue(index.byCheckId.isEmpty())
        assertTrue(index.others.isEmpty())
        assertTrue(index.orphanRepairs.isEmpty())
        assertEquals(0, index.othersCount)
        assertNull(index.repairFor(analysis("none")))
        assertTrue(index.analysesForHealth("x").isEmpty())
    }
}
