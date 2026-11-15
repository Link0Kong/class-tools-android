package com.furina.classtools.data.push

import com.furina.classtools.data.session.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * OPPO PUSH 客户端编排器（启用条件 / 注册 / 状态 / token 上传）。
 *
 * 流程（官方快速接入）：
 * 1. Application.onCreate → [onAppCreated]：
 *    - 先创建通知渠道 `class_notice`（Android 8+ 必需，与密钥是否配置无关，幂等）；
 *    - 未配置 AppKey/AppSecret → 状态「未配置」，**不 init、不 register、不上传**（安全跳过）；
 *    - 已配置 → `HeytapPushManager.init(context, debug)` → `isSupportPush` 检测 →
 *      `register(appKey, appSecret, callback)`；
 * 2. SDK 回调成功拿到 RegistrationID → 缓存（[OppoPushStore]）→ 状态「已注册」→ 后台上传 token；
 * 3. 登录/切换账号（[onSessionChanged]）→ 用缓存 RegistrationID 重新上传（未登录时跳过，等下次）；
 * 4. 设置页可 [retry] 重新注册；失败只记录状态/日志，不阻塞 UI。
 *
 * 依赖全部走接口（[OppoPushSdk]/[OppoPushStore]/[PushTokenUploader]），单测不依赖真机与真实密钥。
 */
class OppoPushManager(
    private val config: OppoPushConfig,
    private val sdk: OppoPushSdk,
    private val store: OppoPushStore,
    private val uploader: PushTokenUploader,
    private val scope: CoroutineScope,
    private val sessionProvider: () -> Session?,
    private val deviceLabel: () -> String,
    private val appVersion: String,
    private val debug: Boolean,
    private val gate: PushTokenUploadGate = PushTokenUploadGate(),
    private val ensureNotificationChannel: () -> Unit = {},
) {

    private val _status = MutableStateFlow(initialStatus())
    val status: StateFlow<OppoPushStatus> = _status.asStateFlow()

    /** 是否已配置密钥（未配置时设置页点击只提示，不触发注册）。 */
    val isConfigured: Boolean get() = config.isConfigured

    /** 设备/系统是否支持（onAppCreated 检测后更新；未检测前保持 false）。 */
    @Volatile
    var isDeviceSupported: Boolean = false
        private set

    private fun initialStatus(): OppoPushStatus {
        if (!config.isConfigured) return OppoPushStatus.NotConfigured
        val cached = store.registrationId().orEmpty()
        return if (cached.isNotEmpty()) {
            OppoPushStatus(OppoPushState.REGISTERED, registrationId = cached)
        } else {
            OppoPushStatus.Registering
        }
    }

    /**
     * Application.onCreate 调用（幂等）：创建通知渠道 → 按配置检测/注册。
     * 所有 SDK 调用都 runCatching 包裹：SDK 异常不得影响 App 启动。
     */
    fun onAppCreated() {
        runCatching { ensureNotificationChannel() }
        if (!config.isConfigured) {
            // 未配置密钥：不 init（避免 SDK 打印/尝试无效注册），状态明确显示未配置。
            _status.value = OppoPushStatus.NotConfigured
            return
        }
        runCatching { sdk.init(debug) }.onFailure { error ->
            android.util.Log.w(TAG, "OPPO PUSH init 失败：${error.javaClass.simpleName}")
        }
        val supported = runCatching { sdk.isSupportPush() }.getOrDefault(false)
        isDeviceSupported = supported
        if (!supported) {
            _status.value = OppoPushStatus.Unsupported
            return
        }
        registerNow()
    }

    /** 设置页「重新注册」/ 失败重试：重新执行 register（未配置/不支持时更新为对应状态）。 */
    fun retry() {
        if (!config.isConfigured) {
            _status.value = OppoPushStatus.NotConfigured
            return
        }
        if (!runCatching { sdk.isSupportPush() }.getOrDefault(false)) {
            _status.value = OppoPushStatus.Unsupported
            return
        }
        registerNow()
    }

    /** 登录 / 账号切换 / token 恢复后调用：把缓存的 RegistrationID 上传到当前账号。 */
    fun onSessionChanged() {
        scope.launch { runCatching { uploadTokenNow() } }
    }

    /**
     * 立即执行一次上传判定（suspend；供测试与显式重试）。
     *
     * 跳过条件：未登录 / debug 预览会话 / token 为空 / 无 RegistrationID / 同一会话重复上传。
     * 上传失败时释放幂等门，让下次登录或重试可以补传。
     */
    suspend fun uploadTokenNow(): PushUploadResult {
        val session = sessionProvider() ?: return PushUploadResult.Skipped
        if (session.debug || session.token.isBlank()) return PushUploadResult.Skipped
        val registrationId = store.registrationId().orEmpty()
            .ifBlank { _status.value.registrationId }
            .trim()
        val payload = PushTokenPayload.of(registrationId, deviceLabel(), appVersion)
            ?: return PushUploadResult.Skipped
        if (!gate.shouldUpload(session.sid, registrationId, appVersion)) {
            return PushUploadResult.Skipped
        }
        val result = uploader.upload(payload)
        if (result is PushUploadResult.Failed) {
            gate.reset()
            android.util.Log.w(TAG, "推送 token 上传失败：${result.message}")
        }
        return result
    }

    // ------------------------------------------------------------ 内部

    private fun registerNow() {
        _status.value = OppoPushStatus.Registering
        runCatching { sdk.register(config.appKey, config.appSecret, callback) }
            .onFailure { error ->
                _status.value = OppoPushStatus(
                    state = OppoPushState.FAILED,
                    errorCode = -2,
                    errorMessage = error.message.orEmpty(),
                )
            }
    }

    private val callback: OppoPushCallback = object : OppoPushCallback {

        override fun onRegisterSucceeded(registrationId: String) {
            isDeviceSupported = true
            val next = OppoPushStatus(OppoPushState.REGISTERED, registrationId = registrationId)
            _status.value = next
            runCatching { store.save(next) }
            scope.launch { runCatching { uploadTokenNow() } }
        }

        override fun onRegisterFailed(code: Int, message: String) {
            val next = OppoPushStatus(
                state = OppoPushState.FAILED,
                errorCode = code,
                errorMessage = message,
            )
            _status.value = next
            runCatching { store.save(next) }
            android.util.Log.w(TAG, "OPPO PUSH 注册失败 code=$code")
        }
    }

    private companion object {
        const val TAG = "OppoPush"
    }
}
