package com.furina.classtools.data.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OPPO PUSH 纯逻辑单测（不依赖真机 / SDK 运行环境 / 真实 AppKey）：
 * 配置空跳过、状态文案与掩码、回调状态映射、通知渠道常量、点击参数解析与目标路由、
 * token 上传请求构造、重复上传幂等门。
 */
class OppoPushConfigTest {

    // ------------------------------------------------------------ 配置

    @Test
    fun `空配置视为未配置 不触发注册`() {
        val config = OppoPushConfig.of("", "")
        assertFalse(config.isConfigured)

        val trimmed = OppoPushConfig.of("  ", null)
        assertFalse(trimmed.isConfigured)
        assertEquals("", trimmed.appKey)
        assertEquals("", trimmed.appSecret)
    }

    @Test
    fun `AppKey 与 AppSecret 都非空才算已配置`() {
        assertFalse(OppoPushConfig.of("key", "").isConfigured)
        assertFalse(OppoPushConfig.of("", "secret").isConfigured)
        assertTrue(OppoPushConfig.of(" key ", " secret ").isConfigured)
        assertEquals("key", OppoPushConfig.of(" key ", " secret ").appKey)
        assertEquals("secret", OppoPushConfig.of(" key ", " secret ").appSecret)
    }

    // ------------------------------------------------------------ 状态文案 / 掩码 / 错误码

    @Test
    fun `状态文案映射`() {
        assertEquals("未配置", OppoPushStatus(OppoPushState.NOT_CONFIGURED).label)
        assertEquals("当前设备不支持", OppoPushStatus(OppoPushState.UNSUPPORTED).label)
        assertEquals("注册中…", OppoPushStatus(OppoPushState.REGISTERING).label)
        assertEquals(
            "已注册 · ****a1b2",
            OppoPushStatus(OppoPushState.REGISTERED, registrationId = "0123456789a1b2").label,
        )
        assertEquals(
            "注册失败（14）",
            OppoPushStatus(OppoPushState.FAILED, errorCode = 14).label,
        )
        assertEquals("注册失败", OppoPushStatus(OppoPushState.FAILED).label)
    }

    @Test
    fun `RegistrationID 掩码只保留末四位`() {
        assertEquals("****a1b2", OppoPushText.maskRegistrationId("0123456789a1b2"))
        assertEquals("****", OppoPushText.maskRegistrationId("abc"))
        assertEquals("", OppoPushText.maskRegistrationId(""))
        assertEquals("", OppoPushText.maskRegistrationId(null))
        // 掩码不得暴露前段
        assertFalse(OppoPushText.maskRegistrationId("0123456789a1b2").contains("0123"))
    }

    @Test
    fun `注册成功回调映射 带与不带 RegistrationID 兜底`() {
        val ok = OppoPushText.fromRegisterResult(0, "reg-abc")
        assertEquals(OppoPushState.REGISTERED, ok.state)
        assertEquals("reg-abc", ok.registrationId)

        val fallback = OppoPushText.fromRegisterResult(0, "  ", fallbackRegistrationId = "reg-cached")
        assertEquals(OppoPushState.REGISTERED, fallback.state)
        assertEquals("reg-cached", fallback.registrationId)

        val noRegId = OppoPushText.fromRegisterResult(0, "")
        assertEquals(OppoPushState.FAILED, noRegId.state)
        assertEquals(-2, noRegId.errorCode)
    }

    @Test
    fun `注册失败与 onError 回调映射错误码`() {
        val failed = OppoPushText.fromRegisterResult(14, null)
        assertEquals(OppoPushState.FAILED, failed.state)
        assertEquals(14, failed.errorCode)
        assertEquals("AppKey 无效", failed.errorMessage)

        val error = OppoPushText.fromError(-1, "服务不可用")
        assertEquals(OppoPushState.FAILED, error.state)
        assertEquals(-1, error.errorCode)
        assertEquals("服务不可用", error.errorMessage)

        assertTrue(OppoPushText.errorHint(16).contains("签名"))
        assertTrue(OppoPushText.errorHint(9999).isNotBlank())
    }

    // ------------------------------------------------------------ 通知渠道（与服务端 channel_id 契约）

    @Test
    fun `通知渠道常量固定`() {
        assertEquals("class_notice", PushChannels.CLASS_NOTICE_ID)
        assertEquals("班级通知", PushChannels.CLASS_NOTICE_NAME)
        assertTrue(PushChannels.CLASS_NOTICE_DESCRIPTION.isNotBlank())
    }

    // ------------------------------------------------------------ 点击参数解析 / 目标路由

    @Test
    fun `点击参数 直接 extra noticeId 优先`() {
        assertEquals("g_notice_1", OppPushClick.noticeIdFrom(null, "g_notice_1"))
        assertEquals(
            "g_direct",
            OppPushClick.noticeIdFrom("""{"noticeId":"g_json"}""", "g_direct"),
        )
    }

    @Test
    fun `点击参数 从 actionParameters JSON 解析`() {
        assertEquals("g_notice_1", OppPushClick.noticeIdFrom("""{"noticeId":"g_notice_1"}""", null))
        // 服务端可能追加其它参数
        assertEquals(
            "g_occ",
            OppPushClick.noticeIdFrom(
                """{"noticeId":"g_occ","channel":"class_notice","extra":"x"}""",
                null,
            ),
        )
        // 期次 key 含 @ / :
        assertEquals(
            "g_1@occ:20261119",
            OppPushClick.noticeIdFrom("""{"noticeId":"g_1@occ:20261119"}""", null),
        )
    }

    @Test
    fun `点击参数 非法输入安全返回 null 不崩`() {
        assertNull(OppPushClick.noticeIdFrom(null, null))
        assertNull(OppPushClick.noticeIdFrom("", "   "))
        assertNull(OppPushClick.noticeIdFrom("not-a-json", null))
        assertNull(OppPushClick.noticeIdFrom("{}", null))
        assertNull(OppPushClick.noticeIdFrom("""{"noticeId":""}""", null))
        assertNull(OppPushClick.noticeIdFrom("""{"noticeId":123}""", null))
        // 超长 / 可疑字符（防任意 intent 内容被当 id）
        assertNull(OppPushClick.noticeIdFrom("""{"noticeId":"${"a".repeat(129)}"}""", null))
        assertNull(OppPushClick.noticeIdFrom(null, "g id<script>"))
        assertNull(OppPushClick.noticeIdFrom(null, "a/b"))
    }

    @Test
    fun `点击目标路由进入通知工具页`() {
        assertEquals("nt", OppPushClick.targetToolId("g_notice_1"))
        assertEquals("nt", OppPushClick.targetToolId("g_1@occ:20261119"))
        assertNull(OppPushClick.targetToolId(null))
        assertNull(OppPushClick.targetToolId("   "))
        // extra 名与常量固定（服务端契约）
        assertEquals("noticeId", OppPushClick.EXTRA_NOTICE_ID)
        assertEquals("actionParameters", OppPushClick.EXTRA_ACTION_PARAMETERS)
    }

    // ------------------------------------------------------------ token 上传 payload

    @Test
    fun `上传 payload JSON 与路径 Prefer 固定`() {
        val payload = PushTokenPayload.of("reg-abc", "OPPO PGZ110", "4.51.0")!!
        assertEquals("oppo", payload.provider)
        assertEquals(
            """{"provider":"oppo","token":"reg-abc","device":"OPPO PGZ110","app_version":"4.51.0"}""",
            payload.toJson(),
        )
        assertEquals("push_tokens?on_conflict=provider,token", PushTokenPayload.PATH)
        assertEquals("resolution=merge-duplicates,return=minimal", PushTokenPayload.PREFER)
    }

    @Test
    fun `上传 payload 空 RegistrationID 返回 null`() {
        assertNull(PushTokenPayload.of(null, "device", "1.0"))
        assertNull(PushTokenPayload.of("   ", "device", "1.0"))
        assertFalse(PushTokenPayload("", "device", "1.0").isValid)
    }

    @Test
    fun `上传 payload 对引号做 JSON 转义`() {
        val payload = PushTokenPayload(token = "a\"b", device = "OPPO \"X\"", appVersion = "1.0")
        val json = payload.toJson()
        assertTrue(json.contains("""a\"b"""))
        assertTrue(json.contains("""OPPO \"X\""""))
    }

    // ------------------------------------------------------------ 重复上传幂等门

    @Test
    fun `幂等门 同一会话同一regId同版本只允许一次`() {
        val gate = PushTokenUploadGate()
        assertTrue(gate.shouldUpload("sid1", "reg1", "4.51.0"))
        assertFalse(gate.shouldUpload("sid1", "reg1", "4.51.0"))
        // 换账号 / 换 regId / 换版本 → 允许重新上传（服务端 upsert 幂等）
        assertTrue(gate.shouldUpload("sid2", "reg1", "4.51.0"))
        assertTrue(gate.shouldUpload("sid2", "reg2", "4.51.0"))
        assertTrue(gate.shouldUpload("sid2", "reg2", "4.52.0"))
    }

    @Test
    fun `幂等门 未登录或无 regId 不允许上传`() {
        val gate = PushTokenUploadGate()
        assertFalse(gate.shouldUpload(null, "reg1", "4.51.0"))
        assertFalse(gate.shouldUpload("", "reg1", "4.51.0"))
        assertFalse(gate.shouldUpload("sid1", null, "4.51.0"))
        assertFalse(gate.shouldUpload("sid1", "  ", "4.51.0"))
    }

    @Test
    fun `幂等门 reset 后可重新上传`() {
        val gate = PushTokenUploadGate()
        assertTrue(gate.shouldUpload("sid1", "reg1", "4.51.0"))
        gate.reset()
        assertTrue(gate.shouldUpload("sid1", "reg1", "4.51.0"))
    }
}
