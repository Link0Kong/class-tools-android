package com.furina.classtools.data.diag

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设备诊断 v2.1 判定单测：
 * - 10 个检查项（系统/App/WebView/屏幕/内存/存储/网络/网关/时钟/本地数据），不再有品牌/型号/语言/时区；
 * - 通过/注意/异常阈值；
 * - `client_diag.device` 只输出 §B android 白名单（screen 嵌套 w/h/dpr）。
 *
 * 纯 JVM：只测 [DiagEvaluator]/[DiagProbeInput]（不触碰 Android framework）。
 */
class DeviceDiagnosticsTest {

    private val now = 1_793_930_400_000L

    private fun healthy(overrides: DiagProbeInput.() -> DiagProbeInput = { this }): DiagProbeInput =
        DiagProbeInput(
            androidVersion = "17",
            appVersion = "3.0.0",
            webviewVersion = "WebView/137",
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            screenDensity = 2.75f,
            memoryTotalMb = 12_288,
            memoryAvailMb = 4_096,
            storageTotalMb = 200_000,
            storageFreeMb = 80_000,
            networkType = "wifi",
            networkValidated = true,
            gatewayLatencyMs = 120,
            serverEpochMs = now + 500,
            nowMillis = now,
            localDataOk = true,
        ).overrides()

    private fun checkOf(checks: List<DiagCheck>, id: String): DiagCheck = checks.first { it.id == id }

    @Test
    fun checks_areTenMinimalItems_withoutDeviceModelLanguageTimezone() {
        val checks = DiagEvaluator.evaluate(healthy())
        assertEquals(10, checks.size)
        assertEquals(
            listOf(
                "os_version", "app_version", "webview", "screen", "memory",
                "storage", "network", "gateway", "clock", "local_data",
            ),
            checks.map { it.id },
        )
        val ids = checks.map { it.id }.toSet()
        assertFalse(ids.contains("device"))
        assertFalse(ids.contains("language"))
        assertFalse(ids.contains("timezone"))
        // 展示里不得出现品牌/型号/语言
        val display = checks.joinToString(" ") { "${it.label} ${it.value} ${it.hint}" }
        assertFalse(display.contains("Xiaomi"))
        assertFalse(display.contains("24129PN74C"))
        assertFalse(display.contains("zh-CN"))
        assertFalse(display.contains("Asia/Shanghai"))
    }

    @Test
    fun healthyDevice_allPassOrInfo() {
        val checks = DiagEvaluator.evaluate(healthy())
        val summary = DiagEvaluator.summary(healthy())
        assertEquals(0, summary.failCount)
        assertEquals(0, summary.warnCount)
        assertEquals(DiagStatus.PASS, summary.overall)
        assertEquals(DiagStatus.PASS, checkOf(checks, "network").status)
        assertEquals(DiagStatus.PASS, checkOf(checks, "gateway").status)
        assertEquals(DiagStatus.PASS, checkOf(checks, "memory").status)
        assertEquals(DiagStatus.PASS, checkOf(checks, "storage").status)
        assertEquals(DiagStatus.PASS, checkOf(checks, "local_data").status)
        assertEquals("WebView/137", checkOf(checks, "webview").value)
        assertEquals("1080×2400 @2.75x", checkOf(checks, "screen").value)
    }

    @Test
    fun offlineNetwork_andNullGateway_areFailures() {
        val input = healthy {
            copy(networkType = "offline", networkValidated = false, gatewayLatencyMs = null)
        }
        val checks = DiagEvaluator.evaluate(input)
        assertEquals(DiagStatus.FAIL, checkOf(checks, "network").status)
        assertEquals(DiagStatus.FAIL, checkOf(checks, "gateway").status)
        val summary = DiagEvaluator.summary(input)
        assertEquals(2, summary.failCount)
        assertEquals(DiagStatus.FAIL, summary.overall)
        assertEquals(2, summary.anomalies.size)
    }

    @Test
    fun networkNotValidated_isWarn() {
        val checks = DiagEvaluator.evaluate(healthy { copy(networkValidated = false) })
        assertEquals(DiagStatus.WARN, checkOf(checks, "network").status)
    }

    @Test
    fun gatewayLatency_thresholds() {
        assertEquals(DiagStatus.PASS, checkOf(DiagEvaluator.evaluate(healthy { copy(gatewayLatencyMs = 2_999) }), "gateway").status)
        assertEquals(DiagStatus.WARN, checkOf(DiagEvaluator.evaluate(healthy { copy(gatewayLatencyMs = 3_001) }), "gateway").status)
        assertEquals(DiagStatus.FAIL, checkOf(DiagEvaluator.evaluate(healthy { copy(gatewayLatencyMs = 10_001) }), "gateway").status)
    }

    @Test
    fun memory_thresholds_onlyAvailableDisplayed() {
        val warn = checkOf(DiagEvaluator.evaluate(healthy { copy(memoryTotalMb = 10_000, memoryAvailMb = 800) }), "memory")
        assertEquals(DiagStatus.WARN, warn.status)
        assertTrue(warn.value.startsWith("可用 "))
        assertFalse(warn.value.contains("10_000"))
        val fail = checkOf(DiagEvaluator.evaluate(healthy { copy(memoryTotalMb = 10_000, memoryAvailMb = 400) }), "memory")
        assertEquals(DiagStatus.FAIL, fail.status)
        assertEquals(
            DiagStatus.FAIL,
            checkOf(DiagEvaluator.evaluate(healthy { copy(lowMemory = true) }), "memory").status,
        )
    }

    @Test
    fun storage_thresholds_onlyPercentDisplayed() {
        val warn = checkOf(DiagEvaluator.evaluate(healthy { copy(storageTotalMb = 10_000, storageFreeMb = 150) }), "storage")
        assertEquals(DiagStatus.WARN, warn.status)
        assertEquals("剩余 1%", warn.value)
        assertEquals(
            DiagStatus.FAIL,
            checkOf(DiagEvaluator.evaluate(healthy { copy(storageTotalMb = 10_000, storageFreeMb = 40) }), "storage").status,
        )
        assertEquals(
            DiagStatus.PASS,
            checkOf(DiagEvaluator.evaluate(healthy { copy(storageTotalMb = 10_000, storageFreeMb = 5_000) }), "storage").status,
        )
    }

    @Test
    fun clockSkew_thresholdsAndMissingDate() {
        assertEquals(DiagStatus.PASS, checkOf(DiagEvaluator.evaluate(healthy()), "clock").status)
        assertEquals(
            DiagStatus.WARN,
            checkOf(DiagEvaluator.evaluate(healthy { copy(serverEpochMs = now + 3L * 60 * 1000) }), "clock").status,
        )
        assertEquals(
            DiagStatus.FAIL,
            checkOf(DiagEvaluator.evaluate(healthy { copy(serverEpochMs = now - 15L * 60 * 1000) }), "clock").status,
        )
        assertEquals(
            DiagStatus.INFO,
            checkOf(DiagEvaluator.evaluate(healthy { copy(serverEpochMs = null) }), "clock").status,
        )
    }

    @Test
    fun localData_unavailable_isFail() {
        val checks = DiagEvaluator.evaluate(healthy { copy(localDataOk = false, localDataDetail = "不可写") })
        assertEquals(DiagStatus.FAIL, checkOf(checks, "local_data").status)
    }

    @Test
    fun summary_countsAndOverall() {
        val input = healthy { copy(networkValidated = false, storageTotalMb = 10_000, storageFreeMb = 150) }
        val summary = DiagEvaluator.summary(input)
        assertEquals(2, summary.warnCount)
        assertEquals(DiagStatus.WARN, summary.overall)
        assertEquals(
            summary.checks.size,
            summary.passCount + summary.warnCount + summary.failCount +
                summary.checks.count { it.status == DiagStatus.INFO },
        )
    }

    @Test
    fun deviceJson_v21WhitelistOnly() {
        val device: JsonObject = healthy().deviceJson()
        assertEquals(
            setOf("platform", "osVersion", "appVer", "webview", "screen", "memoryMB", "storagePct", "network", "clockSkewMs"),
            device.keys,
        )
        assertEquals("android", (device["platform"] as JsonPrimitive).content)
        assertEquals("17", (device["osVersion"] as JsonPrimitive).content)
        assertEquals("3.0.0", (device["appVer"] as JsonPrimitive).content)
        assertEquals("WebView/137", (device["webview"] as JsonPrimitive).content)
        // memoryMB = 可用内存（不是总量）
        assertEquals("4096", (device["memoryMB"] as JsonPrimitive).content)
        assertEquals("40", (device["storagePct"] as JsonPrimitive).content)
        assertEquals("wifi", (device["network"] as JsonPrimitive).content)
        assertEquals("500", (device["clockSkewMs"] as JsonPrimitive).content)
        val screen = device["screen"] as JsonObject
        assertEquals(setOf("w", "h", "dpr"), screen.keys)
        assertEquals("1080", (screen["w"] as JsonPrimitive).content)
        assertEquals("2400", (screen["h"] as JsonPrimitive).content)
        assertEquals("2.75", (screen["dpr"] as JsonPrimitive).content)
    }

    @Test
    fun deviceInfo_fieldsMinimal() {
        val info = healthy().deviceInfo()
        assertEquals("android", info.platform)
        assertEquals(4_096L, info.memoryMB)
        assertEquals(40, info.storagePct)
        assertEquals(500L, info.clockSkewMs)
        // 无品牌/型号/语言字段
        assertEquals(
            setOf("platform", "osVersion", "appVer", "webview", "screen", "memoryMB", "storagePct", "network", "clockSkewMs"),
            info.toJson().keys,
        )
    }

    @Test
    fun formattingHelpers() {
        assertEquals("Wi-Fi", DiagEvaluator.networkLabel("wifi"))
        assertEquals("移动网络", DiagEvaluator.networkLabel("cellular"))
        assertEquals("离线", DiagEvaluator.networkLabel("offline"))
        assertEquals("2.75", DiagEvaluator.formatDpr(2.75f))
        assertEquals("2", DiagEvaluator.formatDpr(2.0f))
        assertEquals("2.2 GB", DiagEvaluator.formatMb(2_293))
        assertEquals("812 MB", DiagEvaluator.formatMb(812))
        assertEquals("45 秒", DiagEvaluator.formatDuration(45_000))
        assertEquals("1 分钟", DiagEvaluator.formatDuration(90_000))
        assertEquals("2 分钟", DiagEvaluator.formatDuration(120_000))
    }
}
