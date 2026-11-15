package com.furina.classtools.data.push

import com.furina.classtools.data.session.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OPPO PUSH 编排器单测（假 SDK / 假存储 / 假上传器）：状态机、安全跳过、token 上传与幂等。
 * 不依赖真机、ColorOS、真实 AppKey/AppSecret 或网络。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OppoPushManagerTest {

    private class FakeSdk : OppoPushSdk {
        var initCalls = 0
        var initDebug: Boolean? = null
        var support = true
        var registerCalls = 0
        var lastAppKey: String? = null
        var lastAppSecret: String? = null
        var callback: OppoPushCallback? = null
        var currentRegistrationId: String? = null

        override fun init(debug: Boolean) {
            initCalls += 1
            initDebug = debug
        }

        override fun isSupportPush(): Boolean = support

        override fun register(appKey: String, appSecret: String, callback: OppoPushCallback) {
            registerCalls += 1
            lastAppKey = appKey
            lastAppSecret = appSecret
            this.callback = callback
        }

        override fun currentRegistrationId(): String? = currentRegistrationId
    }

    private class FakeStore(initialRegistrationId: String? = null) : OppoPushStore {
        var registrationId: String? = initialRegistrationId
        var savedStatus: OppoPushStatus? = null

        override fun registrationId(): String? = registrationId

        override fun save(status: OppoPushStatus) {
            savedStatus = status
            if (status.registrationId.isNotBlank()) registrationId = status.registrationId
        }
    }

    private class FakeUploader : PushTokenUploader {
        val payloads: MutableList<PushTokenPayload> = mutableListOf()
        var result: PushUploadResult = PushUploadResult.Uploaded

        override suspend fun upload(payload: PushTokenPayload): PushUploadResult {
            payloads += payload
            return result
        }
    }

    private val session = Session(sid = "3260799999", name = "测试账号", token = "test-token")

    private fun manager(
        config: OppoPushConfig,
        sdk: OppoPushSdk,
        store: OppoPushStore,
        uploader: PushTokenUploader,
        scope: CoroutineScope,
        sessionProvider: () -> Session? = { session },
        debug: Boolean = false,
        ensureChannel: () -> Unit = {},
    ): OppoPushManager = OppoPushManager(
        config = config,
        sdk = sdk,
        store = store,
        uploader = uploader,
        scope = scope,
        sessionProvider = sessionProvider,
        deviceLabel = { "OPPO PGZ110" },
        appVersion = "4.51.0",
        debug = debug,
        ensureNotificationChannel = ensureChannel,
    )

    private val configured = OppoPushConfig(appKey = "test-app-key", appSecret = "test-secret")

    // ------------------------------------------------------------ 未配置 / 不支持

    @Test
    fun `未配置时 不 init 不 register 状态未配置 但创建渠道`() = runTest {
        val sdk = FakeSdk()
        val uploader = FakeUploader()
        var channelEnsured = false
        val push = manager(
            config = OppoPushConfig(),
            sdk = sdk,
            store = FakeStore(),
            uploader = uploader,
            scope = this,
            ensureChannel = { channelEnsured = true },
        )

        push.onAppCreated()
        advanceUntilIdle()

        assertTrue(channelEnsured)
        assertEquals(0, sdk.initCalls)
        assertEquals(0, sdk.registerCalls)
        assertEquals(OppoPushState.NOT_CONFIGURED, push.status.value.state)
        assertEquals("未配置", push.status.value.label)
        assertFalse(push.isConfigured)
        // 未配置时即使已登录也不上传 token
        assertEquals(PushUploadResult.Skipped, push.uploadTokenNow())
        assertTrue(uploader.payloads.isEmpty())
    }

    @Test
    fun `已配置但不支持推送 状态不支持 不 register`() = runTest {
        val sdk = FakeSdk().apply { support = false }
        val push = manager(configured, sdk, FakeStore(), FakeUploader(), scope = this)

        push.onAppCreated()
        advanceUntilIdle()

        assertEquals(1, sdk.initCalls)
        assertFalse(push.isDeviceSupported)
        assertEquals(0, sdk.registerCalls)
        assertEquals(OppoPushState.UNSUPPORTED, push.status.value.state)
        assertEquals("当前设备不支持", push.status.value.label)
    }

    @Test
    fun `init 传入 debug 标志`() = runTest {
        val sdk = FakeSdk()
        val push = manager(configured, sdk, FakeStore(), FakeUploader(), scope = this, debug = true)
        push.onAppCreated()
        assertEquals(true, sdk.initDebug)
        assertEquals(1, sdk.initCalls)
    }

    // ------------------------------------------------------------ 注册成功 / 失败 / 重试

    @Test
    fun `注册成功 缓存 regId 并上传 token`() = runTest {
        val sdk = FakeSdk()
        val store = FakeStore()
        val uploader = FakeUploader()
        val push = manager(configured, sdk, store, uploader, scope = this)

        push.onAppCreated()
        assertEquals(1, sdk.registerCalls)
        assertEquals("test-app-key", sdk.lastAppKey)
        assertEquals("test-secret", sdk.lastAppSecret)
        assertEquals(OppoPushState.REGISTERING, push.status.value.state)

        val callback = sdk.callback
        assertNotNull(callback)
        callback!!.onRegisterSucceeded("0123456789a1b2")
        advanceUntilIdle()

        assertEquals(OppoPushState.REGISTERED, push.status.value.state)
        assertEquals("已注册 · ****a1b2", push.status.value.label)
        assertEquals("0123456789a1b2", store.registrationId)
        assertEquals(OppoPushState.REGISTERED, store.savedStatus?.state)
        // 自动上传一次（登录态）：token/device/app_version 正确
        assertEquals(1, uploader.payloads.size)
        val payload = uploader.payloads.single()
        assertEquals("0123456789a1b2", payload.token)
        assertEquals("OPPO PGZ110", payload.device)
        assertEquals("4.51.0", payload.appVersion)
        assertEquals("oppo", payload.provider)
    }

    @Test
    fun `注册失败 状态失败并保留错误码`() = runTest {
        val sdk = FakeSdk()
        val store = FakeStore()
        val push = manager(configured, sdk, store, FakeUploader(), scope = this)

        push.onAppCreated()
        sdk.callback!!.onRegisterFailed(14, "AppKey 无效")

        assertEquals(OppoPushState.FAILED, push.status.value.state)
        assertEquals(14, push.status.value.errorCode)
        assertEquals("注册失败（14）", push.status.value.label)
        assertEquals(OppoPushState.FAILED, store.savedStatus?.state)
        advanceUntilIdle()
        // 失败不产生上传（无 regId）
        assertTrue(store.registrationId().isNullOrEmpty())
    }

    @Test
    fun `设置页重试 重新发起注册`() = runTest {
        val sdk = FakeSdk()
        val push = manager(configured, sdk, FakeStore(), FakeUploader(), scope = this)

        push.onAppCreated()
        sdk.callback!!.onRegisterFailed(13, "频率受限")
        assertEquals(OppoPushState.FAILED, push.status.value.state)

        push.retry()
        assertEquals(2, sdk.registerCalls)
        assertEquals(OppoPushState.REGISTERING, push.status.value.state)
    }

    @Test
    fun `未配置时调用 retry 不发起注册`() = runTest {
        val sdk = FakeSdk()
        val push = manager(OppoPushConfig(), sdk, FakeStore(), FakeUploader(), scope = this)
        push.retry()
        assertEquals(0, sdk.registerCalls)
        assertEquals(OppoPushState.NOT_CONFIGURED, push.status.value.state)
    }

    @Test
    fun `从本机缓存恢复已注册状态`() = runTest {
        val push = manager(
            config = configured,
            sdk = FakeSdk(),
            store = FakeStore(initialRegistrationId = "cached-reg-id-9z8y"),
            uploader = FakeUploader(),
            scope = this,
        )
        assertEquals(OppoPushState.REGISTERED, push.status.value.state)
        assertEquals("****9z8y", OppoPushText.maskRegistrationId(push.status.value.registrationId))
    }

    // ------------------------------------------------------------ 登录 / 切换账号上传

    @Test
    fun `登录后上传 未登录跳过`() = runTest {
        val store = FakeStore(initialRegistrationId = "reg-1")
        val uploader = FakeUploader()
        var currentSession: Session? = null
        val push = manager(
            config = configured,
            sdk = FakeSdk(),
            store = store,
            uploader = uploader,
            scope = this,
            sessionProvider = { currentSession },
        )

        push.onSessionChanged()
        advanceUntilIdle()
        assertTrue(uploader.payloads.isEmpty())

        currentSession = session
        push.onSessionChanged()
        advanceUntilIdle()
        assertEquals(1, uploader.payloads.size)

        // 同一账号重复触发 → 幂等门跳过，不重复上传
        push.onSessionChanged()
        advanceUntilIdle()
        assertEquals(1, uploader.payloads.size)
    }

    @Test
    fun `切换账号后重新上传`() = runTest {
        val store = FakeStore(initialRegistrationId = "reg-1")
        val uploader = FakeUploader()
        var currentSession: Session? = session
        val push = manager(
            config = configured,
            sdk = FakeSdk(),
            store = store,
            uploader = uploader,
            scope = this,
            sessionProvider = { currentSession },
        )

        push.onSessionChanged()
        advanceUntilIdle()
        assertEquals(1, uploader.payloads.size)

        currentSession = session.copy(sid = "3260707024")
        push.onSessionChanged()
        advanceUntilIdle()
        assertEquals(2, uploader.payloads.size)
    }

    @Test
    fun `debug 预览会话不上传`() = runTest {
        val store = FakeStore(initialRegistrationId = "reg-1")
        val uploader = FakeUploader()
        val push = manager(
            config = configured,
            sdk = FakeSdk(),
            store = store,
            uploader = uploader,
            scope = this,
            sessionProvider = { session.copy(debug = true) },
        )
        // 直接调用 suspend 判定 + 走 session 变化都应为跳过
        assertEquals(PushUploadResult.Skipped, push.uploadTokenNow())
        push.onSessionChanged()
        advanceUntilIdle()
        assertTrue(uploader.payloads.isEmpty())
    }

    @Test
    fun `上传失败会释放幂等门 便于下次补传`() = runTest {
        val store = FakeStore(initialRegistrationId = "reg-1")
        val uploader = FakeUploader().apply { result = PushUploadResult.Failed("网络错误") }
        val push = manager(configured, FakeSdk(), store, uploader, scope = this)

        push.onSessionChanged()
        advanceUntilIdle()
        assertEquals(1, uploader.payloads.size)

        // 失败后 gate 已 reset → 再次触发会重试
        uploader.result = PushUploadResult.Uploaded
        push.onSessionChanged()
        advanceUntilIdle()
        assertEquals(2, uploader.payloads.size)
        // 成功后同一会话再次判定 → 幂等门跳过（不重复打云端）
        assertEquals(PushUploadResult.Skipped, push.uploadTokenNow())
    }

    @Test
    fun `无 RegistrationID 不上传`() = runTest {
        val uploader = FakeUploader()
        val push = manager(configured, FakeSdk(), FakeStore(), uploader, scope = this)
        assertEquals(PushUploadResult.Skipped, push.uploadTokenNow())
        push.onSessionChanged()
        advanceUntilIdle()
        assertTrue(uploader.payloads.isEmpty())
        assertNull(push.status.value.registrationId.ifEmpty { null })
    }
}
