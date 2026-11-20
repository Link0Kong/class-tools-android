package com.furina.classtools.data.push

import android.content.Context
import com.furina.classtools.data.session.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * vivo 推送客户端编排器（配置 / 初始化 / 注册 / 状态 / token 上传）。
 *
 * 与 OPPO PUSH 的差异：
 * - vivo 的初始化/开关/取 regId 都是回调式，且 `onReceiveRegId` 由独立 Receiver 回调（[VivoPushBus]）；
 * - **不在 Application.onCreate 初始化**：等用户完成身份核验（已同意隐私政策）后由
 *   [onSessionChanged] 首次启动，避免未同意隐私就初始化 SDK；
 * - AppSecret 只在服务端，客户端只有 AppID/AppKey（vivo 设计要求）。
 */
class VivoPushManager(
    private val context: Context,
    private val config: VivoPushConfig,
    private val sdk: VivoPushSdk,
    private val store: VivoPushStore,
    private val uploader: PushTokenUploader,
    private val scope: CoroutineScope,
    private val sessionProvider: () -> Session?,
    private val deviceLabel: () -> String,
    private val appVersion: String,
    private val gate: PushTokenUploadGate = PushTokenUploadGate(),
    private val ensureNotificationChannel: () -> Unit = {},
) {

    private val _status = MutableStateFlow(initialStatus())
    val status: StateFlow<VivoPushStatus> = _status.asStateFlow()

    val isConfigured: Boolean get() = config.isConfigured

    @Volatile
    private var started = false

    @Volatile
    var isDeviceSupported: Boolean = store.registrationId().isNullOrEmpty().not()
        private set

    private fun initialStatus(): VivoPushStatus {
        if (!config.isConfigured) return VivoPushStatus.NotConfigured
        val cached = store.registrationId().orEmpty()
        return if (cached.isNotEmpty()) {
            VivoPushStatus(VivoPushState.REGISTERED, registrationId = cached)
        } else {
            VivoPushStatus.Registering
        }
    }

    /** 登录 / 切换账号 / token 恢复后调用：首次初始化+注册，并把 regId 上传到当前账号。 */
    fun onSessionChanged() {
        ensureStarted()
        scope.launch { runCatching { uploadTokenNow() } }
    }

    /** 失败重试 / 设置页手动重试。 */
    fun retry() {
        started = false
        VivoPushBus.clear()
        ensureStarted()
    }

    /** 首次启动（幂等；只在已登录后调用，满足隐私合规）。 */
    @Synchronized
    private fun ensureStarted() {
        if (!config.isConfigured) {
            _status.value = VivoPushStatus.NotConfigured
            return
        }
        if (!sdk.initialize(context, agreePrivacyStatement = true)) {
            _status.value = VivoPushText.fromError(10000, "vivo SDK 初始化失败")
            return
        }
        if (!sdk.isSupport(context)) {
            _status.value = VivoPushStatus.Unsupported
            return
        }
        if (started) return
        started = true
        isDeviceSupported = true
        _status.value = VivoPushStatus.Registering
        VivoPushBus.register { regId -> onRegId(regId) }
        runCatching { ensureNotificationChannel() }
        sdk.turnOnPush(context) { state ->
            if (state == 0 || state == 1) {
                requestRegId()
            } else {
                val next = VivoPushText.fromError(state)
                _status.value = next
                runCatching { store.save(next) }
            }
        }
    }

    private fun requestRegId() {
        sdk.requestRegId(
            context = context,
            onSuccess = { regId -> onRegId(regId) },
            onFailure = { code ->
                val next = VivoPushText.fromError(code)
                _status.value = next
                runCatching { store.save(next) }
            },
        )
    }

    private fun onRegId(regId: String) {
        val next = VivoPushText.fromRegId(regId)
        _status.value = next
        isDeviceSupported = true
        runCatching { store.save(next) }
        scope.launch { runCatching { uploadTokenNow() } }
    }

    /** 立即上传判定（未登录/debug/无 regId/重复上传 → Skipped；失败释放幂等门）。 */
    suspend fun uploadTokenNow(): PushUploadResult {
        val session = sessionProvider() ?: return PushUploadResult.Skipped
        if (session.debug || session.token.isBlank()) return PushUploadResult.Skipped
        val registrationId = store.registrationId().orEmpty()
            .ifBlank { _status.value.registrationId }
            .trim()
        val payload = PushTokenPayload.of(
            registrationId = registrationId,
            device = deviceLabel(),
            appVersion = appVersion,
            provider = PushTokenPayload.PROVIDER_VIVO,
        ) ?: return PushUploadResult.Skipped
        if (!gate.shouldUpload(session.sid, registrationId, appVersion)) {
            return PushUploadResult.Skipped
        }
        val result = uploader.upload(payload)
        if (result is PushUploadResult.Failed) {
            gate.reset()
            android.util.Log.w(TAG, "vivo token 上传失败：${result.message}")
        }
        return result
    }

    private companion object {
        const val TAG = "VivoPush"
    }
}

