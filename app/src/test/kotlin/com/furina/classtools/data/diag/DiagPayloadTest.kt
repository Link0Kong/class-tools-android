package com.furina.classtools.data.diag

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * client_diag 上报 payload 契约单测（v2.1 §B 设备信息最小化）：
 * - device 唯一白名单：platform/osVersion/appVer/webview/screen{w,h,dpr}/memoryMB/storagePct/network/clockSkewMs；
 * - detail 唯一白名单：check/latency_ms/storage_pct/clock_skew_ms/err_count/code；
 * - `check`＝设备检查 id（agent 据此生成 diag_analysis.related.checks）；
 * - 脱敏、稳定指纹、sid 只在行顶层。
 */
class DiagPayloadTest {

    private val sid = "2024000001"

    private fun stringOf(row: JsonObject, key: String): String =
        (row[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun device(): JsonObject = DiagDeviceInfo(
        osVersion = "17",
        appVer = "3.0.0",
        webview = "WebView/137",
        screenWidth = 1080,
        screenHeight = 2400,
        screenDpr = 2.75,
        memoryMB = 2_293,
        storagePct = 88,
        network = "wifi",
        clockSkewMs = 450,
    ).toJson()

    @Test
    fun buildRow_hasContractFields() {
        val row = DiagPayload.buildRow(
            id = "an_testsuffix",
            sid = sid,
            appVersion = "3.0.0",
            kind = "network",
            level = "warn",
            message = "CloudBase 网关延迟 4126ms",
            detail = mapOf("check" to "gateway", "latency_ms" to "4126"),
            device = device(),
            fingerprint = "fp-123",
            now = 1_700_000_000_000L,
        )
        assertEquals("an_testsuffix", stringOf(row, "id"))
        assertEquals(sid, stringOf(row, "sid"))
        assertEquals("android", stringOf(row, "platform"))
        assertEquals("3.0.0", stringOf(row, "app_ver"))
        assertEquals("network", stringOf(row, "kind"))
        assertEquals("warn", stringOf(row, "level"))
        assertEquals("fp-123", stringOf(row, "fingerprint"))
        assertEquals(1_700_000_000_000L, (row["ts"] as? JsonPrimitive)?.longOrNull)
        val detail = row["detail"] as JsonObject
        assertEquals("gateway", stringOf(detail, "check"))
        assertEquals("4126", stringOf(detail, "latency_ms"))
        val device = row["device"] as JsonObject
        assertEquals("android", stringOf(device, "platform"))
        assertEquals("WebView/137", stringOf(device, "webview"))
    }

    @Test
    fun deviceJson_onlyV21Whitelist_nestedScreen() {
        val json = device()
        assertEquals(
            setOf("platform", "osVersion", "appVer", "webview", "screen", "memoryMB", "storagePct", "network", "clockSkewMs"),
            json.keys,
        )
        val screen = json["screen"] as JsonObject
        assertEquals(setOf("w", "h", "dpr"), screen.keys)
        assertEquals("1080", (screen["w"] as JsonPrimitive).content)
        assertEquals("2400", (screen["h"] as JsonPrimitive).content)
        assertEquals("2.75", (screen["dpr"] as JsonPrimitive).content)
        // 最小化：品牌/型号/语言/API level/时区/存储总量一律不出现
        val text = json.toString()
        assertFalse(text.contains("manufacturer"))
        assertFalse(text.contains("model"))
        assertFalse(text.contains("language"))
        assertFalse(text.contains("timezone"))
        assertFalse(text.contains("sdk"))
        assertFalse(text.contains("Xiaomi"))
    }

    @Test
    fun buildRow_deviceLegacyExtraFieldsFiltered() {
        val legacy = buildJsonObject {
            put("platform", "android")
            put("manufacturer", "Xiaomi")
            put("model", "24129PN74C")
            put("language", "zh-CN")
            put("timezone", "Asia/Shanghai")
            put("sdk", 37)
            put("os_version", "17")
            put("memory_total_mb", 12288)
            put("local_data_ok", true)
            put("storagePct", 88)
            put(
                "screen",
                buildJsonObject {
                    put("w", 1080)
                    put("h", 2400)
                    put("dpr", 2.75)
                    put("dpi", 420)
                },
            )
        }
        val row = DiagPayload.buildRow(
            id = "an_1",
            sid = sid,
            appVersion = "3.0.0",
            kind = "manual",
            level = "info",
            message = "x",
            device = legacy,
            fingerprint = "fp",
            now = 1L,
        )
        val device = row["device"] as JsonObject
        // 旧键（os_version/manufacturer/model/...）一律被 v2.1 白名单过滤；只留下合法键
        assertEquals(setOf("platform", "storagePct", "screen"), device.keys)
        assertEquals(setOf("w", "h", "dpr"), (device["screen"] as JsonObject).keys)
        assertFalse(device.toString().contains("Xiaomi"))
    }

    @Test
    fun buildRow_detailWhitelistOnly() {
        val row = DiagPayload.buildRow(
            id = "an_1",
            sid = sid,
            appVersion = "3.0.0",
            kind = "error",
            level = "error",
            message = "boom",
            detail = mapOf(
                "check" to "memory",
                "latency_ms" to "120",
                "storage_pct" to "3",
                "clock_skew_ms" to "-500",
                "err_count" to "1",
                "code" to "memory_low",
                "sid" to sid,
                "token" to "secret-token",
                "exception" to "java.lang.IllegalStateException",
                "hint" to "x",
                "status" to "fail",
                "arbitrary" to "y",
            ),
            device = JsonObject(emptyMap()),
            fingerprint = "fp",
            now = 1L,
        )
        val detail = row["detail"] as JsonObject
        assertEquals(
            setOf("check", "latency_ms", "storage_pct", "clock_skew_ms", "err_count", "code"),
            detail.keys,
        )
        assertFalse(detail.toString().contains(sid))
        assertFalse(detail.toString().contains("secret-token"))
    }

    @Test
    fun buildRow_sanitizesValuesAndTruncatesMessage300() {
        val row = DiagPayload.buildRow(
            id = "an_2",
            sid = sid,
            appVersion = "v".repeat(100),
            kind = "weird",
            level = "verbose",
            message = "失败 " + "https://x.example/a?token=abc " + "错".repeat(400),
            detail = mapOf("code" to """C:\secret\a.txt 192.168.1.9"""),
            device = JsonObject(emptyMap()),
            fingerprint = "fp",
            now = 1L,
        )
        assertEquals("error", stringOf(row, "kind"))
        assertEquals("warn", stringOf(row, "level"))
        assertEquals(300, stringOf(row, "message").length)
        val detail = row["detail"] as JsonObject
        assertFalse(stringOf(detail, "code").contains("C:\\secret"))
        assertFalse(stringOf(detail, "code").contains("192.168.1.9"))
        assertEquals("[redacted]", stringOf(row, "app_ver"))
    }

    @Test
    fun fingerprint_isStableForSameAnomaly() {
        val check = DiagCheck("gateway", "CloudBase 网关延迟", DiagStatus.FAIL, "12000 ms", reportKind = "network")
        val first = DiagPayload.fromCheck(check, sid, "3.0.0", device(), 1L)
        val second = DiagPayload.fromCheck(check, sid, "3.0.0", device(), 2L)
        assertNotEquals(stringOf(first, "id"), stringOf(second, "id"))
        assertEquals(stringOf(first, "fingerprint"), stringOf(second, "fingerprint"))
        assertEquals(stringOf(first, "fingerprint"), DiagPayload.fingerprintOf(first))
    }

    @Test
    fun fromCheck_includesCheckIdAndTypeSpecificDetail() {
        val gateway = DiagCheck("gateway", "CloudBase 网关延迟", DiagStatus.WARN, "4126 ms", reportKind = "network")
        val gatewayRow = DiagPayload.fromCheck(
            gateway, sid, "3.0.0", device(), 1L,
            latencyMs = 4126, code = "gateway_slow",
        )
        val gatewayDetail = gatewayRow["detail"] as JsonObject
        assertEquals("gateway", stringOf(gatewayDetail, "check"))
        assertEquals("4126", stringOf(gatewayDetail, "latency_ms"))
        assertEquals("gateway_slow", stringOf(gatewayDetail, "code"))
        assertEquals(setOf("check", "latency_ms", "code"), gatewayDetail.keys)

        val storage = DiagCheck("storage", "可用存储", DiagStatus.FAIL, "剩余 3%", reportKind = "storage")
        val storageRow = DiagPayload.fromCheck(storage, sid, "3.0.0", device(), 1L, storagePct = 3)
        val storageDetail = storageRow["detail"] as JsonObject
        assertEquals("storage", stringOf(storageDetail, "check"))
        assertEquals("3", stringOf(storageDetail, "storage_pct"))

        val clock = DiagCheck("clock", "时钟偏差", DiagStatus.WARN, "本机慢 5 分钟", reportKind = "perf")
        val clockRow = DiagPayload.fromCheck(clock, sid, "3.0.0", device(), 1L, clockSkewMs = 300_000)
        val clockDetail = clockRow["detail"] as JsonObject
        assertEquals("clock", stringOf(clockDetail, "check"))
        assertEquals("300000", stringOf(clockDetail, "clock_skew_ms"))
    }

    @Test
    fun fromCheck_mapsStatusToKindAndLevel() {
        val fail = DiagCheck("network", "网络", DiagStatus.FAIL, reportKind = "network")
        val row = DiagPayload.fromCheck(fail, sid, "3.0.0", device(), 1L, code = "network_offline")
        assertEquals("network", stringOf(row, "kind"))
        assertEquals("error", stringOf(row, "level"))
        assertEquals("network", stringOf(row["detail"] as JsonObject, "check"))
        assertEquals("network_offline", stringOf(row["detail"] as JsonObject, "code"))

        val warn = DiagCheck("memory", "可用内存", DiagStatus.WARN, reportKind = "perf")
        val warnRow = DiagPayload.fromCheck(warn, sid, "3.0.0", device(), 1L)
        assertEquals("perf", stringOf(warnRow, "kind"))
        assertEquals("warn", stringOf(warnRow, "level"))
    }

    @Test
    fun manualRow_usesManualKindAndInfoLevel() {
        val row = DiagPayload.manualRow(
            sid = sid,
            appVersion = "3.0.0",
            message = "诊断页手动上报",
            detail = mapOf("check" to "manual"),
            device = device(),
            now = 99L,
        )
        assertEquals("manual", stringOf(row, "kind"))
        assertEquals("info", stringOf(row, "level"))
        assertEquals("manual", stringOf(row["detail"] as JsonObject, "check"))
        assertEquals(99L, (row["ts"] as? JsonPrimitive)?.longOrNull)
        assertTrue(stringOf(row, "id").startsWith(DiagPayload.ID_PREFIX))
    }

    @Test
    fun crashDetail_isMinimal() {
        val detail = DiagPayload.crashDetail("java.lang.IllegalStateException")
        assertEquals(mapOf("check" to "crash", "code" to "java.lang.IllegalStateException"), detail)
        // 不允许的旧字段（exception/thread/source）不在 detail 白名单内
        val row = DiagPayload.buildRow(
            id = "an_crash",
            sid = sid,
            appVersion = "3.0.0",
            kind = "crash",
            level = "error",
            message = "boom",
            detail = detail + mapOf("exception" to "x", "thread" to "main"),
            device = device(),
            fingerprint = "fp",
            now = 1L,
        )
        assertEquals(setOf("check", "code"), (row["detail"] as JsonObject).keys)
    }

    @Test
    fun checkIdOf_readsDetailCheck() {
        val check = DiagCheck("memory", "可用内存", DiagStatus.WARN, reportKind = "perf")
        val row = DiagPayload.fromCheck(check, sid, "3.0.0", device(), 1L)
        assertEquals("memory", DiagPayload.checkIdOf(row))
        assertEquals("", DiagPayload.checkIdOf(JsonObject(emptyMap())))
    }

    @Test
    fun reportBody_isSingleRowArray() {
        val row = DiagPayload.manualRow(sid, "3.0.0", "x", emptyMap(), device(), 1L)
        val body = com.furina.classtools.data.diag.DefaultDiagRepository.reportBody(row)
        val parsed = com.furina.classtools.data.network.parseJsonOrNull(
            com.furina.classtools.data.network.NetJson,
            body,
        )
        assertNotNull(parsed)
        assertTrue(parsed is kotlinx.serialization.json.JsonArray)
        assertEquals(1, (parsed as kotlinx.serialization.json.JsonArray).size)
    }
}
