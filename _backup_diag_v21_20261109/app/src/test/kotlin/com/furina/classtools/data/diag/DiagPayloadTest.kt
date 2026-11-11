package com.furina.classtools.data.diag

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * client_diag 上报 payload 契约单测（契约 §2 字段白名单 + 脱敏 + 稳定指纹 + 不携带 sid 进 detail/device）。
 */
class DiagPayloadTest {

    private val sid = "3260707024"

    private fun stringOf(row: JsonObject, key: String): String =
        (row[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    @Test
    fun buildRow_hasContractFields() {
        val row = DiagPayload.buildRow(
            id = "an_testsuffix",
            sid = sid,
            appVersion = "3.0.0",
            kind = "network",
            level = "warn",
            message = "CloudBase 网关延迟 4126ms",
            detail = mapOf("check_id" to "gateway", "latency_ms" to "4126"),
            device = mapOf("platform" to "android", "model" to "24129PN74C"),
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
        assertEquals("gateway", stringOf(detail, "check_id"))
        assertEquals("4126", stringOf(detail, "latency_ms"))
        val device = row["device"] as JsonObject
        assertEquals("android", stringOf(device, "platform"))
        assertEquals("24129PN74C", stringOf(device, "model"))
    }

    @Test
    fun buildRow_dropsUnknownKeysAndNeverLeaksSidOrTokenInDetailDevice() {
        val row = DiagPayload.buildRow(
            id = "an_1",
            sid = sid,
            appVersion = "3.0.0",
            kind = "error",
            level = "error",
            message = "boom",
            detail = mapOf(
                "error_message" to "stack",
                "sid" to sid,
                "token" to "secret-token",
                "password" to "p@ss",
                "arbitrary" to "x",
            ),
            device = mapOf(
                "platform" to "android",
                "sid" to sid,
                "token" to "secret-token",
                "arbitrary" to "x",
            ),
            fingerprint = "fp",
            now = 1L,
        )
        val detail = row["detail"] as JsonObject
        val device = row["device"] as JsonObject
        assertEquals(setOf("error_message"), detail.keys)
        assertEquals(setOf("platform"), device.keys)
        assertFalse(detail.toString().contains(sid))
        assertFalse(device.toString().contains("secret-token"))
        assertFalse(row.toString().contains("p@ss"))
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
            detail = mapOf("error_message" to "C:\\secret\\a.txt 192.168.1.9"),
            device = mapOf("manufacturer" to "Xiaomi"),
            fingerprint = "fp",
            now = 1L,
        )
        assertEquals("error", stringOf(row, "kind"))
        assertEquals("warn", stringOf(row, "level"))
        assertEquals(300, stringOf(row, "message").length)
        val detail = row["detail"] as JsonObject
        assertFalse(stringOf(detail, "error_message").contains("C:\\secret"))
        assertFalse(stringOf(detail, "error_message").contains("192.168.1.9"))
        // 100 个连续字母像 token → 整体脱敏
        assertEquals("[redacted]", stringOf(row, "app_ver"))
    }

    @Test
    fun fingerprint_isStableForSameAnomaly() {
        val check = DiagCheck(
            id = "gateway",
            label = "CloudBase 网关延迟",
            status = DiagStatus.FAIL,
            value = "12000 ms",
            reportKind = "network",
        )
        val first = DiagPayload.fromCheck(check, sid, "3.0.0", emptyMap(), 1L)
        val second = DiagPayload.fromCheck(check, sid, "3.0.0", emptyMap(), 2L)
        assertNotEquals(stringOf(first, "id"), stringOf(second, "id"))
        assertEquals(stringOf(first, "fingerprint"), stringOf(second, "fingerprint"))
        assertEquals(stringOf(first, "fingerprint"), DiagPayload.fingerprintOf(first))
    }

    @Test
    fun fromCheck_mapsStatusToKindAndLevel() {
        val fail = DiagCheck("network", "网络", DiagStatus.FAIL, reportKind = "network")
        val row = DiagPayload.fromCheck(fail, sid, "3.0.0", emptyMap(), 1L)
        assertEquals("network", stringOf(row, "kind"))
        assertEquals("error", stringOf(row, "level"))

        val warn = DiagCheck("storage", "存储", DiagStatus.WARN, reportKind = "storage")
        val warnRow = DiagPayload.fromCheck(warn, sid, "3.0.0", emptyMap(), 1L)
        assertEquals("storage", stringOf(warnRow, "kind"))
        assertEquals("warn", stringOf(warnRow, "level"))
    }

    @Test
    fun manualRow_usesManualKindAndInfoLevel() {
        val row = DiagPayload.manualRow(
            sid = sid,
            appVersion = "3.0.0",
            message = "诊断页手动上报",
            detail = mapOf("check_id" to "manual"),
            device = mapOf("platform" to "android"),
            now = 99L,
        )
        assertEquals("manual", stringOf(row, "kind"))
        assertEquals("info", stringOf(row, "level"))
        assertEquals("manual", stringOf(row["detail"] as JsonObject, "check_id"))
        assertEquals(99L, (row["ts"] as? JsonPrimitive)?.longOrNull)
        assertTrue(stringOf(row, "id").startsWith(DiagPayload.ID_PREFIX))
    }

    @Test
    fun deviceJson_onlyWhitelistKeys() {
        val obj = DiagPayload.deviceJson(
            mapOf(
                "platform" to "android",
                "manufacturer" to "Xiaomi",
                "secret" to "x",
                "token" to "abc",
            ),
        )
        assertEquals(setOf("platform", "manufacturer"), obj.keys)
    }

    @Test
    fun reportBody_isSingleRowArray() {
        val row = DiagPayload.manualRow(sid, "3.0.0", "x", emptyMap(), emptyMap(), 1L)
        val body = com.furina.classtools.data.diag.DefaultDiagRepository.reportBody(row)
        val parsed = com.furina.classtools.data.network.parseJsonOrNull(
            com.furina.classtools.data.network.NetJson,
            body,
        )
        assertNotNull(parsed)
        assertTrue(parsed is kotlinx.serialization.json.JsonArray)
        assertEquals(1, (parsed as kotlinx.serialization.json.JsonArray).size)
    }

    @Test
    fun fingerprintOf_toleratesBadRows() {
        assertEquals("", DiagPayload.fingerprintOf(JsonObject(emptyMap())))
        assertNull((JsonObject(emptyMap())["missing"] as? JsonPrimitive)?.booleanOrNull)
    }
}
