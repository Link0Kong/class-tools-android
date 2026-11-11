package com.furina.classtools.data.diag

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 折叠行/状态胶囊文案单测（契约 v2.1 §A-2/3/5）：
 * 标题 ≤22 字截断、new/queued/running/fixed/failed 胶囊、设备端「建议」、可自动修复徽标、
 * 异常无 AI 的排队/离线文案、flow `run` 进行中、顶部统计、最后更新时间。
 */
class DiagUiTextTest {

    private fun analysis(
        id: String = "a1",
        title: String = "标题",
        status: String = "new",
        fixable: Boolean = false,
        action: String = "",
        healthIds: List<String> = emptyList(),
        checks: List<String> = emptyList(),
        flow: List<FlowStep> = emptyList(),
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
        flow = flow,
        relatedHealthIds = healthIds,
        relatedChecks = checks,
    )

    @Test
    fun collapseTitle_shortUnchanged() {
        assertEquals("云同步延迟", DiagUiText.collapseTitle("云同步延迟"))
        assertEquals("未命名诊断", DiagUiText.collapseTitle(null))
        assertEquals("未命名诊断", DiagUiText.collapseTitle("   "))
    }

    @Test
    fun collapseTitle_truncatesTo22WithEllipsis() {
        val long = "云同步延迟而且这个问题描述特别特别长需要被截断"
        val folded = DiagUiText.collapseTitle(long)
        assertEquals(22, folded.length)
        assertTrue(folded.endsWith("…"))
        assertTrue(long.startsWith(folded.dropLast(1)))
    }

    @Test
    fun collapseTitle_sanitizesSensitiveText() {
        val folded = DiagUiText.collapseTitle("失败 https://x/a?token=abc 路径 C:\\briefdesk\\a.log")
        assertFalse(folded.contains("token=abc"))
        assertFalse(folded.contains("C:\\briefdesk"))
    }

    @Test
    fun pillLabel_statusMapping() {
        assertEquals("待处理", DiagUiText.pillLabel(analysis(status = "new")))
        assertEquals("待处理", DiagUiText.pillLabel(analysis(status = "queued")))
        assertEquals("修复中", DiagUiText.pillLabel(analysis(status = "running")))
        assertEquals("已修复", DiagUiText.pillLabel(analysis(status = "fixed")))
        assertEquals("修复失败", DiagUiText.pillLabel(analysis(status = "failed")))
    }

    @Test
    fun pillLabel_deviceScopedShowsAdvice() {
        assertEquals("建议", DiagUiText.pillLabel(analysis(checks = listOf("memory"))))
        // 同时挂健康行 + 设备检查 → 不是纯设备端，仍按状态显示
        assertEquals(
            "修复中",
            DiagUiText.pillLabel(analysis(status = "running", healthIds = listOf("row"), checks = listOf("memory"))),
        )
        assertTrue(DiagUiText.isDeviceScoped(analysis(checks = listOf("memory"))))
        assertFalse(DiagUiText.isDeviceScoped(analysis(healthIds = listOf("row"))))
    }

    @Test
    fun autoFixable_requiresFixableAndAction() {
        assertTrue(DiagUiText.autoFixable(analysis(fixable = true, action = "recheck_health")))
        assertFalse(DiagUiText.autoFixable(analysis(fixable = true, action = "")))
        assertFalse(DiagUiText.autoFixable(analysis(fixable = false, action = "recheck_health")))
    }

    @Test
    fun summaryCounts_autoFixableAndFixed() {
        val analyses = listOf(
            analysis("a1", status = "new", fixable = true, action = "recheck_health"),
            analysis("a2", status = "fixed", fixable = true, action = "sync_xgj"),
            analysis("a3", status = "fixed", fixable = true, action = "sync_power"),
            analysis("a4", status = "failed", fixable = true, action = "restart_gateway"),
            analysis("a5", status = "new", fixable = false),
            analysis("a6", status = "ignored", fixable = true, action = "web_pause_on"),
        )
        // 可自动修复 = fixable+action 且非 fixed/ignored：a1/a4
        assertEquals(2, DiagUiText.autoFixableCount(analyses))
        assertEquals(2, DiagUiText.fixedCount(analyses))
    }

    @Test
    fun pendingText_onlineVsOffline() {
        assertEquals("AI 分析排队中…", DiagUiText.pendingText(true))
        assertEquals("本机离线，已留存待开机修复", DiagUiText.pendingText(false))
    }

    @Test
    fun flowStateText_runShownAsInProgress() {
        assertEquals("进行中", DiagUiText.flowStateText("run"))
        assertEquals("完成", DiagUiText.flowStateText("done"))
        assertEquals("失败", DiagUiText.flowStateText("fail"))
        assertEquals("已修复", DiagUiText.flowStateText("fixed"))
        assertTrue(FlowStep(1L, "本机开始执行 sync_xgj", "run").running)
        assertFalse(FlowStep(1L, "完成", "done").running)
    }

    @Test
    fun repairStatusLabel_minimal() {
        assertEquals("修复中", DiagUiText.repairStatusLabel("running"))
        assertEquals("已完成", DiagUiText.repairStatusLabel("done"))
        assertEquals("失败", DiagUiText.repairStatusLabel("failed"))
        assertEquals("等待执行", DiagUiText.repairStatusLabel("pending"))
    }

    @Test
    fun lastUpdatedText_formatsHms() {
        // 2026-11-06T02:00:00Z = 10:00:00 +08
        val ts = 1_793_930_400_000L
        assertEquals("10:00:00", DiagUiText.lastUpdatedText(ts, ZoneId.of("Asia/Shanghai")))
        assertEquals("02:00:00", DiagUiText.lastUpdatedText(ts, ZoneId.of("UTC")))
        assertEquals("", DiagUiText.lastUpdatedText(0L))
    }
}
