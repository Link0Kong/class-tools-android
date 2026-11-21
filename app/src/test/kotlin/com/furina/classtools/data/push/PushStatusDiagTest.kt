package com.furina.classtools.data.push

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 推送状态诊断（纯逻辑）：状态文案、诊断行字段、空会话不上报。 */
class PushStatusDiagTest {

    private val device = JsonObject(mapOf("model" to JsonPrimitive("OnePlus PYB110")))

    private fun status(state: String, code: Int = 0, tail: String = "") =
        PushProviderStatus(provider = "oppo", state = state, errorCode = code, registrationTail = tail)

    @Test
    fun `状态文案覆盖未配置 不支持 注册中 已注册 失败`() {
        assertEquals("未配置密钥", status("NOT_CONFIGURED").label)
        assertTrue(status("UNSUPPORTED").label.contains("本机不支持"))
        assertEquals("注册中…", status("REGISTERING").label)
        assertEquals("已注册（…a1b2）", status("REGISTERED", tail = "a1b2").label)
        assertEquals("已注册", status("REGISTERED").label)
        assertEquals("注册失败（错误码 33）", status("FAILED", code = 33).label)
        assertEquals("未知", status("SOMETHING").label)
    }

    @Test
    fun `快照诊断摘要与 code`() {
        val snapshot = PushStatusSnapshot(
            oppo = PushProviderStatus("oppo", "REGISTERED", 0, "a1b2"),
            vivo = PushProviderStatus("vivo", "FAILED", 10001),
        )
        assertEquals("推送状态 oppo=REGISTERED:0 vivo=FAILED:10001", snapshot.diagMessage())
        assertEquals("REGISTERED/0 FAILED/10001", snapshot.diagCode())
        assertTrue(snapshot.anyRegistered)
    }

    @Test
    fun `诊断行字段只含白名单键且可上报`() {
        val snapshot = PushStatusSnapshot(
            oppo = PushProviderStatus("oppo", "FAILED", 33),
            vivo = PushProviderStatus("vivo", "NOT_CONFIGURED"),
        )
        val row = PushStatusDiag.buildDiagRow(
            snapshot = snapshot,
            sid = "3260799999",
            appVersion = "1.0.0-beta27",
            device = device,
            nowMillis = 1_760_000_000_000L,
        )
        assertNotNull(row)
        row!!
        assertEquals("manual", row["kind"]?.let { (it as JsonPrimitive).content })
        assertEquals("3260799999", row["sid"]?.let { (it as JsonPrimitive).content })
        val detail = row["detail"]!!.jsonObject
        assertEquals("push_status", detail["check"]?.let { (it as JsonPrimitive).content })
        assertEquals("FAILED/33 NOT_CONFIGURED/0", detail["code"]?.let { (it as JsonPrimitive).content })
        assertTrue(row["fingerprint"]?.let { (it as JsonPrimitive).content }!!.isNotBlank())
    }

    @Test
    fun `空会话不生成诊断行`() {
        val snapshot = PushStatusSnapshot(
            oppo = PushProviderStatus("oppo", "REGISTERED"),
            vivo = PushProviderStatus("vivo", "REGISTERED"),
        )
        assertNull(
            PushStatusDiag.buildDiagRow(snapshot, "", "1.0.0", device, 1L),
        )
        assertNull(
            PushStatusDiag.buildDiagRow(snapshot, "   ", "1.0.0", device, 1L),
        )
    }

    @Test
    fun `已知状态枚举与摘要行`() {
        assertTrue(PushStatusDiag.isKnownState("REGISTERED"))
        assertFalse(PushStatusDiag.isKnownState("SOMETHING"))
        val snapshot = PushStatusSnapshot(
            oppo = PushProviderStatus("oppo", "FAILED", 33),
            vivo = PushProviderStatus("vivo", "UNSUPPORTED"),
        )
        val lines = PushStatusDiag.summaryLines(snapshot)
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("注册失败"))
        assertTrue(lines[1].contains("本机不支持"))
    }
}
