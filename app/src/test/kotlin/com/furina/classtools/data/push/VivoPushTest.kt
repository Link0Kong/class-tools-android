package com.furina.classtools.data.push

import android.content.Context
import android.content.ContextWrapper
import com.furina.classtools.data.session.Session
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** vivo 推送配置/状态/文案/编排（全部假 SDK/假存储/假上传器，不发真实请求）。 */
class VivoPushTest {

    private val context: Context = ContextWrapper(null)

    private class FakeSdk(
        var support: Boolean = true,
        var initializeOk: Boolean = true,
        var turnOnState: Int = 0,
        var regId: String? = "vivo-reg-id-123456",
        var regFail: Int? = null,
    ) : VivoPushSdk {
        var initialized = 0
        var turnOn = 0
        var requests = 0

        override fun initialize(context: Context, agreePrivacyStatement: Boolean): Boolean {
            initialized += 1
            return initializeOk
        }

        override fun turnOnPush(context: Context, onState: (Int) -> Unit) {
            turnOn += 1
            onState(turnOnState)
        }

        override fun requestRegId(
            context: Context,
            onSuccess: (String) -> Unit,
            onFailure: (Int) -> Unit,
        ) {
            requests += 1
            val id = regId
            val fail = regFail
            if (fail != null) onFailure(fail) else if (id != null) onSuccess(id) else onFailure(-2)
        }

        override fun isSupport(context: Context): Boolean = support
    }

    private class FakeStore(var id: String? = null) : VivoPushStore {
        var saved: VivoPushStatus? = null
        override fun registrationId(): String? = id
        override fun save(status: VivoPushStatus) {
            saved = status
            if (status.registrationId.isNotBlank()) id = status.registrationId
        }
    }

    private class FakeUploader : PushTokenUploader {
        val payloads = mutableListOf<PushTokenPayload>()
        var result: PushUploadResult = PushUploadResult.Uploaded
        override suspend fun upload(payload: PushTokenPayload): PushUploadResult {
            payloads += payload
            return result
        }
    }

    private fun manager(
        config: VivoPushConfig = VivoPushConfig.of("106177851", "api-key"),
        sdk: FakeSdk = FakeSdk(),
        store: FakeStore = FakeStore(),
        uploader: FakeUploader = FakeUploader(),
        session: Session? = Session(sid = "3260799999", name = "测试账号", token = "t"),
    ): VivoPushManager = VivoPushManager(
        context = context,
        config = config,
        sdk = sdk,
        store = store,
        uploader = uploader,
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        sessionProvider = { session },
        deviceLabel = { "vivo V2309A" },
        appVersion = "1.0.0-beta16",
    )

    @Test
    fun `unconfigured does not init sdk`() {
        val sdk = FakeSdk()
        val mgr = manager(config = VivoPushConfig.of("", ""), sdk = sdk)
        mgr.onSessionChanged()
        assertEquals(0, sdk.initialized)
        assertFalse(mgr.isConfigured)
        assertEquals(VivoPushState.NOT_CONFIGURED, mgr.status.value.state)
    }

    @Test
    fun `unsupported device does not request regId`() {
        val sdk = FakeSdk(support = false)
        val mgr = manager(sdk = sdk)
        mgr.onSessionChanged()
        assertEquals(1, sdk.initialized)
        assertEquals(0, sdk.requests)
        assertEquals(VivoPushState.UNSUPPORTED, mgr.status.value.state)
    }

    @Test
    fun `registration saves regId and uploads vivo provider`() = runTest {
        val sdk = FakeSdk(regId = "vivo-reg-abcdef")
        val store = FakeStore()
        val uploader = FakeUploader()
        val mgr = manager(sdk = sdk, store = store, uploader = uploader)

        mgr.onSessionChanged()
        // 回调式注册后主动触发一次上传判定（生产由回调 scope.launch 执行）。
        mgr.uploadTokenNow()

        assertEquals(VivoPushState.REGISTERED, mgr.status.value.state)
        assertEquals("vivo-reg-abcdef", store.saved?.registrationId)
        assertEquals(1, uploader.payloads.size)
        assertEquals("vivo", uploader.payloads.single().provider)
        assertEquals("vivo V2309A", uploader.payloads.single().device)
    }

    @Test
    fun `turn on failure saves error code`() {
        val store = FakeStore()
        val mgr = manager(sdk = FakeSdk(turnOnState = 10001), store = store)
        mgr.onSessionChanged()

        val status = mgr.status.value
        assertEquals(VivoPushState.FAILED, status.state)
        assertEquals(10001, status.errorCode)
        assertEquals(10001, store.saved?.errorCode)
    }

    @Test
    fun `upload skipped when logged out or debug session`() = runTest {
        val uploader = FakeUploader()
        val loggedOut = manager(uploader = uploader, session = null)
        loggedOut.onSessionChanged()
        assertEquals(0, uploader.payloads.size)

        val debug = manager(
            uploader = uploader,
            session = Session(sid = "3260799999", name = "x", token = "t", debug = true),
        )
        debug.onSessionChanged()
        assertEquals(0, uploader.payloads.size)
    }

    @Test
    fun `label masks regId`() {
        val status = VivoPushText.fromRegId("abcdefgh1234")
        assertEquals(VivoPushState.REGISTERED, status.state)
        assertEquals("已注册 · ****1234", VivoPushText.label(status))
        assertEquals("未配置", VivoPushText.label(VivoPushStatus.NotConfigured))
        assertEquals("当前设备不支持", VivoPushText.label(VivoPushStatus.Unsupported))
    }

    @Test
    fun `status text maps key error codes`() {
        assertTrue(VivoPushText.label(VivoPushText.fromError(10001)).contains("10001"))
        assertTrue(VivoPushText.errorHint(10001).contains("Manifest"))
        assertTrue(VivoPushText.errorHint(10004).contains("api_key"))
        assertEquals("未返回 regId", VivoPushText.fromRegId("  ").errorMessage)
    }

    @Test
    fun `payload json uses vivo provider`() {
        val payload = PushTokenPayload.of("reg", "vivo X", "1.0", PushTokenPayload.PROVIDER_VIVO)!!
        val json = payload.toJson()
        assertTrue(json.contains("\"provider\":\"vivo\""))
        assertTrue(json.contains("\"token\":\"reg\""))
        assertTrue(json.contains("\"app_version\":\"1.0\""))
    }
}
