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
        var revoked = 0
        var revokeCode = 0
        var nextId = "replacement-reg"
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

        override fun deleteRegId(context: Context, onState: (Int) -> Unit) {
            revoked++
            if (revokeCode == 0) regId = nextId
            onState(revokeCode)
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
        var owner: VivoBinding? = null
        var retired: String? = null
        override fun binding() = owner
        override fun rememberBinding(binding: VivoBinding?): Boolean { owner = binding; return true }
        override fun revokedId() = retired
        override fun markRevoked(regId: String): Boolean { retired = regId; owner = null; id = null; return true }
        override fun registrationId(): String? = id
        override fun save(status: VivoPushStatus) {
            saved = status
            if (status.registrationId.isNotBlank()) id = status.registrationId
        }
    }

    private class FakeUploader : PushTokenBinding {
        val releases = mutableListOf<Pair<String, String>>()
        var releaseResult: PushUploadResult = PushUploadResult.Uploaded
        override suspend fun releaseOwn(sid: String, registrationId: String): PushUploadResult {
            releases += sid to registrationId
            return releaseResult
        }
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
        active: () -> Session? = { session },
        bindingFor: (Session) -> PushTokenBinding = { uploader },
    ): VivoPushManager = VivoPushManager(
        context = context,
        config = config,
        sdk = sdk,
        store = store,
        uploaderForSession = bindingFor,
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        sessionProvider = active,
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

        assertEquals(VivoPushState.BOUND, mgr.status.value.state)
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
        assertEquals("设备已注册，待绑定 · ****1234", VivoPushText.label(status))
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

    private val first = Session("3260799998", "测试甲", "first")
    private val second = Session("3260799999", "测试乙", "second")

    @Test
    fun `cloud failure is visible and retry binds without a second subscription`() {
        val sdk = FakeSdk()
        val uploader = FakeUploader().apply { result = PushUploadResult.Failed("offline") }
        val mgr = manager(sdk = sdk, uploader = uploader)
        mgr.onSessionChanged()
        assertEquals(VivoPushState.BIND_FAILED, mgr.status.value.state)
        uploader.result = PushUploadResult.Uploaded
        mgr.retry()
        assertEquals(VivoPushState.BOUND, mgr.status.value.state)
        assertEquals(1, sdk.turnOn)
        assertEquals(2, uploader.payloads.size)
    }

    @Test
    fun `switch and logout release only the recorded device with its own credentials`() {
        var account: Session? = first
        val a = FakeUploader()
        val b = FakeUploader()
        val store = FakeStore()
        val sdk = FakeSdk(regId = "reg1")
        val mgr = manager(sdk = sdk, store = store, active = { account },
            bindingFor = { if (it.token == first.token) a else b })
        mgr.onSessionChanged()
        account = second
        mgr.onSessionChanged(first)
        assertEquals(listOf(first.sid to "reg1"), a.releases)
        assertEquals(listOf("reg1"), b.payloads.map { it.token })
        account = null
        mgr.onSessionChanged(second)
        assertEquals(listOf(second.sid to "reg1"), b.releases)
        assertEquals(null, store.binding())
        assertEquals(VivoPushState.SIGNED_OUT, mgr.status.value.state)
        assertEquals(0, sdk.revoked)
    }

    @Test
    fun `credential renewal and vendor rotation bind once to the active account`() {
        var account = first
        val a = FakeUploader()
        val renewed = FakeUploader()
        val mgr = manager(sdk = FakeSdk(regId = "reg1"), active = { account },
            bindingFor = { if (it.token == first.token) a else renewed })
        mgr.onSessionChanged()
        account = account.copy(token = "new-credentials")
        mgr.onSessionChanged(first)
        assertEquals(1, a.payloads.size)
        VivoPushBus.dispatchRegId("reg2")
        VivoPushBus.dispatchRegId("reg2")
        assertEquals(listOf("reg1", "reg2"), renewed.payloads.map { it.token })
        assertEquals(listOf(first.sid to "reg1"), renewed.releases)
    }

    @Test
    fun `restarted app invalidates former device id when old credentials are missing`() {
        val store = FakeStore("old").apply { owner = VivoBinding(first.sid, "old") }
        val sdk = FakeSdk(regId = "old")
        val uploader = FakeUploader()
        val mgr = manager(sdk = sdk, store = store, session = second, uploader = uploader)
        mgr.onSessionChanged()
        assertEquals(1, sdk.revoked)
        assertTrue(uploader.releases.isEmpty())
        assertEquals(listOf(sdk.nextId), uploader.payloads.map { it.token })
        assertEquals(VivoBinding(second.sid, sdk.nextId), store.binding())
        VivoPushBus.dispatchRegId("old")
        assertEquals(1, uploader.payloads.size)
        assertEquals(VivoPushState.BOUND, mgr.status.value.state)
    }

    @Test
    fun `failed revocation never transfers the old registration to the new user`() {
        val store = FakeStore("old").apply { owner = VivoBinding(first.sid, "old") }
        val sdk = FakeSdk(regId = "old").apply { revokeCode = 1003 }
        val uploader = FakeUploader()
        val mgr = manager(sdk = sdk, store = store, session = second, uploader = uploader)
        mgr.onSessionChanged()
        assertEquals(VivoPushState.BIND_FAILED, mgr.status.value.state)
        assertEquals(VivoBinding(first.sid, "old"), store.binding())
        assertTrue(uploader.payloads.isEmpty())
        sdk.revokeCode = 0
        mgr.retry()
        assertEquals(VivoPushState.BOUND, mgr.status.value.state)
    }

    @Test
    fun `offline upgrade remembers an ambiguous legacy owner until a confirmed bind`() {
        val store = FakeStore("legacy")
        val sdk = FakeSdk(regId = "legacy")
        val uploader = FakeUploader().apply { result = PushUploadResult.Failed("offline") }
        manager(sdk = sdk, store = store, uploader = uploader, session = second).onSessionChanged()
        assertEquals(true, store.binding()?.legacyOwnerUnknown)
        val restarted = manager(sdk = sdk, store = store, uploader = uploader, session = second)
        uploader.result = PushUploadResult.Failed("denied", 403)
        restarted.onSessionChanged()
        assertEquals(1, sdk.revoked)
        assertEquals(sdk.nextId, store.binding()?.regId)
        assertEquals(false, store.binding()?.legacyOwnerUnknown)
        uploader.result = PushUploadResult.Uploaded
        restarted.retry()
        assertEquals(VivoPushState.BOUND, restarted.status.value.state)
    }

    @Test
    fun `legacy registered status does not claim a cloud binding`() {
        val local = PushProviderStatus("vivo", "REGISTERED")
        assertFalse(local.isRegistered)
        assertTrue(local.label.contains("等待绑定"))
        assertTrue(PushProviderStatus("vivo", "BOUND").isRegistered)
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
