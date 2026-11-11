package com.furina.classtools.data.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设备诊断判定单测（阈值/通过-注意-异常/计数/设备 payload 字段）。
 *
 * 纯 JVM：只测 [DiagEvaluator] 与 [DiagProbeInput]（不触碰 Android framework）。
 */
class DeviceDiagnosticsTest {

    private val now = 1_793_930_400_000L

    private fun healthy(overrides: DiagProbeInput.() -> DiagProbeInput = { this }): DiagProbeInput =
        DiagProbeInput(
            manufacturer = "Xiaomi",
            model = "24129PN74C",
            androidVersion = "17",
            sdkInt = 37,
            appVersion = "3.0.0",
            appVersionCode = 6,
            screenWidthDp = 412,
            screenHeightDp = 915,
            densityDpi = 420,
            language = "zh-CN",
            timezoneId = "Asia/Shanghai",
            timezoneOffsetMinutes = 480,
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

    private fun checkOf(checks: List<DiagCheck>, id: String): DiagCheck =
        checks.first { it.id == id }

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
        assertTrue(checks.any { it.id == "device" })
        assertTrue(checks.any { it.id == "os_version" })
        assertTrue(checks.any { it.id == "app_version" })
        assertTrue(checks.any { it.id == "screen" })
        assertTrue(checks.any { it.id == "timezone" })
    }

    @Test
    fun offlineNetwork_andNullGateway_areFailures() {
        val checks = DiagEvaluator.evaluate(
            healthy {
                copy(networkType = "offline", networkValidated = false, gatewayLatencyMs = null)
            },
        )
        assertEquals(DiagStatus.FAIL, checkOf(checks, "network").status)
        assertEquals(DiagStatus.FAIL, checkOf(checks, "gateway").status)
        val summary = DiagEvaluator.summary(
            healthy { copy(networkType = "offline", networkValidated = false, gatewayLatencyMs = null) },
        )
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
    fun memory_thresholds() {
        // 可用 8% → 注意；4% → 异常；低内存标记 → 异常
        assertEquals(
            DiagStatus.WARN,
            checkOf(DiagEvaluator.evaluate(healthy { copy(memoryTotalMb = 10_000, memoryAvailMb = 800) }), "memory").status,
        )
        assertEquals(
            DiagStatus.FAIL,
            checkOf(DiagEvaluator.evaluate(healthy { copy(memoryTotalMb = 10_000, memoryAvailMb = 400) }), "memory").status,
        )
        assertEquals(
            DiagStatus.FAIL,
            checkOf(DiagEvaluator.evaluate(healthy { copy(lowMemory = true) }), "memory").status,
        )
    }

    @Test
    fun storage_thresholds() {
        // 绝对剩余 <200MB 且比例 ≥1% → 注意；比例 <1% → 异常
        assertEquals(
            DiagStatus.WARN,
            checkOf(DiagEvaluator.evaluate(healthy { copy(storageTotalMb = 10_000, storageFreeMb = 150) }), "storage").status,
        )
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
        val missing = checkOf(DiagEvaluator.evaluate(healthy { copy(serverEpochMs = null) }), "clock")
        assertEquals(DiagStatus.INFO, missing.status)
        assertNull(missing.id.takeIf { it.isBlank() })
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
        assertEquals(summary.checks.size, summary.passCount + summary.warnCount + summary.failCount + summary.checks.count { it.status == DiagStatus.INFO })
    }

    @Test
    fun deviceMap_containsOnlyWhitelistedSafeKeys() {
        val map = healthy().deviceMap()
        assertTrue(map.containsKey("platform"))
        assertTrue(map.containsKey("manufacturer"))
        assertTrue(map.containsKey("os_version"))
        assertTrue(map.containsKey("screen"))
        assertTrue(map.containsKey("network"))
        assertTrue(map.containsKey("gateway_latency_ms"))
        assertFalse(map.containsKey("sid"))
        assertTrue(map["timezone"].orEmpty().contains("UTC+08:00"))
    }

    @Test
    fun formattingHelpers() {
        assertEquals("UTC+08:00", DiagEvaluator.formatUtcOffset(480))
        assertEquals("UTC-05:30", DiagEvaluator.formatUtcOffset(-330))
        assertEquals("UTC+00:00", DiagEvaluator.formatUtcOffset(0))
        assertEquals("Wi-Fi", DiagEvaluator.networkLabel("wifi"))
        assertEquals("移动网络", DiagEvaluator.networkLabel("cellular"))
        assertEquals("离线", DiagEvaluator.networkLabel("offline"))
        assertEquals("45 秒", DiagEvaluator.formatDuration(45_000))
        assertEquals("1 分钟", DiagEvaluator.formatDuration(90_000))
        assertEquals("2 分钟", DiagEvaluator.formatDuration(120_000))
    }
}
