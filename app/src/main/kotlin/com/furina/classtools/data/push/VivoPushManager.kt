package com.furina.classtools.data.push

import android.content.Context
import com.furina.classtools.data.session.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** 串行管理本机的厂商注册、按账号的云端绑定以及旧标识的注销。 */
class VivoPushManager(
    private val context: Context,
    private val config: VivoPushConfig,
    private val sdk: VivoPushSdk,
    private val store: VivoPushStore,
    private val uploaderForSession: (Session) -> PushTokenBinding,
    private val scope: CoroutineScope,
    private val sessionProvider: () -> Session?,
    private val deviceLabel: () -> String,
    private val appVersion: String,
    private val ensureNotificationChannel: () -> Unit = {},
) {
    private val lock = Mutex()
    private val _status = MutableStateFlow(if (config.isConfigured) VivoPushStatus.Registering else VivoPushStatus.NotConfigured)
    val status: StateFlow<VivoPushStatus> = _status.asStateFlow()
    val isConfigured: Boolean get() = config.isConfigured
    private var initialized = false
    private var deviceRegId: String? = null
    private var bindingOwner: Session? = null
    private var confirmed: VivoBinding? = null
    @Volatile var isDeviceSupported = false
        private set

    private fun owner(): Session? = sessionProvider()?.takeIf {
        !it.debug && it.sid.isNotBlank() && it.token.isNotBlank() && it.isLikelyValid()
    }

    fun onSessionChanged(previous: Session? = null): Job {
        VivoPushBus.clear()
        if (owner() == null) _status.value = VivoPushStatus(VivoPushState.SIGNED_OUT)
        return scope.launch { synchronize(previous = previous) }
    }

    fun retry() {
        scope.launch { synchronize(force = true) }
    }

    fun retryIfPending() {
        if (_status.value.state in setOf(VivoPushState.BIND_FAILED, VivoPushState.FAILED)) retry()
    }

    suspend fun uploadTokenNow(): PushUploadResult = synchronize()

    private suspend fun synchronize(
        previous: Session? = null,
        force: Boolean = false,
        changedId: String? = null,
        callbackOwner: Session? = null,
    ): PushUploadResult = lock.withLock {
        try {
            if (callbackOwner != null && callbackOwner != owner()) return@withLock PushUploadResult.Skipped
            sync(previous, force, changedId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            bindingFailed()
        }
    }

    private suspend fun sync(previous: Session?, force: Boolean, changedId: String?): PushUploadResult {
        if (!isConfigured) {
            publish(VivoPushStatus.NotConfigured)
            return PushUploadResult.Skipped
        }
        val active = owner()
        var old = store.binding()
        // An installation upgraded while already signed in can still release its old-format registration.
        if (old == null && previous != null && previous.sid != active?.sid && !previous.debug) {
            old = store.registrationId()?.let { VivoBinding(previous.sid, it) }
            if (old != null && !store.rememberBinding(old)) return bindingFailed()
        }
        if (old != null && old.sid != active?.sid) {
            val prior = old
            val credential = listOfNotNull(previous, bindingOwner).firstOrNull {
                it.sid == prior.sid && it.isLikelyValid() && !it.debug && it.token.isNotBlank()
            }
            val released = credential?.let { uploaderForSession(it).releaseOwn(prior.sid, prior.regId) }
            if (released == PushUploadResult.Uploaded && !prior.legacyOwnerUnknown) {
                if (!store.rememberBinding(null)) return bindingFailed()
            } else if (!revokeDeviceId(old.regId)) {
                return bindingFailed()
            }
            confirmed = null
            bindingOwner = null
            old = null
        }
        if (owner() != active) return PushUploadResult.Skipped
        if (active == null) {
            VivoPushBus.clear()
            deviceRegId = null
            publish(VivoPushStatus(VivoPushState.SIGNED_OUT))
            return PushUploadResult.Skipped
        }
        if (!initialize()) return PushUploadResult.Skipped
        VivoPushBus.register { id -> scope.launch { synchronize(changedId = id, callbackOwner = active) } }
        val cachedId = store.registrationId()
        val id = changedId ?: deviceRegId?.takeUnless { force } ?: register() ?: return PushUploadResult.Skipped
        if (owner() != active) return PushUploadResult.Skipped
        if (id == store.revokedId()) {
            // A delayed Receiver delivery may still carry an identifier that we already retired.
            return if (changedId != null) PushUploadResult.Skipped else bindingFailed()
        }
        deviceRegId = id
        val next = VivoBinding(active.sid, id)
        if (old != null && old.regId != id) {
            if (uploaderForSession(active).releaseOwn(old.sid, old.regId) != PushUploadResult.Uploaded) return bindingFailed()
            if (!store.rememberBinding(null)) return bindingFailed()
        }
        if (owner() != active) return PushUploadResult.Skipped
        if (!force && confirmed == next && bindingOwner?.token == active.token) {
            publish(VivoPushStatus(VivoPushState.BOUND, id))
            return PushUploadResult.Skipped
        }
        val legacy = (old?.legacyOwnerUnknown == true && old.regId == id) ||
            (old == null && cachedId == id && store.revokedId() == null)
        bindingOwner = active
        confirmed = null
        publish(VivoPushText.fromRegId(id))
        if (!store.rememberBinding(next.copy(legacyOwnerUnknown = legacy))) return bindingFailed()
        val payload = PushTokenPayload.of(id, deviceLabel(), appVersion, PushTokenPayload.PROVIDER_VIVO)
            ?: return bindingFailed()
        val result = uploaderForSession(active).upload(payload)
        if (result == PushUploadResult.Uploaded) {
            if (legacy && !store.rememberBinding(next)) return bindingFailed()
            confirmed = next
            if (owner() == active) publish(VivoPushStatus(VivoPushState.BOUND, id))
        } else if (owner() == active) {
            // Old versions stored a device id without its owner. Recover an RLS ownership conflict using the vendor.
            if (legacy && result is PushUploadResult.Failed && result.httpStatus == 403 && revokeDeviceId(id)) {
                return sync(null, true, null)
            }
            bindingFailed()
        }
        return result
    }

    private fun publish(value: VivoPushStatus) {
        _status.value = value
        store.save(value)
    }

    private fun bindingFailed(): PushUploadResult.Failed {
        publish(VivoPushStatus(VivoPushState.BIND_FAILED))
        return PushUploadResult.Failed("设备与账号的云端绑定未完成")
    }

    private fun initialize(): Boolean {
        if (!initialized) {
            if (!sdk.initialize(context, agreePrivacyStatement = true)) {
                publish(VivoPushText.fromError(10000))
                return false
            }
            initialized = true
        }
        isDeviceSupported = sdk.isSupport(context)
        if (!isDeviceSupported) {
            publish(VivoPushStatus.Unsupported)
            return false
        }
        ensureNotificationChannel()
        return true
    }

    private suspend fun register(): String? {
        publish(VivoPushStatus.Registering)
        val state = if (deviceRegId == null) sdkResult<Int> { done -> sdk.turnOnPush(context, done) } else 0
        if (state != 0 && state != 1) {
            publish(VivoPushText.fromError(state ?: 1003))
            return null
        }
        val result = sdkResult<VivoPushStatus> { done ->
            sdk.requestRegId(context, { done(VivoPushText.fromRegId(it)) }, { done(VivoPushText.fromError(it)) })
        } ?: VivoPushText.fromError(1003)
        if (result.state == VivoPushState.FAILED) publish(result)
        return result.registrationId.takeIf { it.isNotBlank() }
    }

    /** Without a usable old class session, invalidate this installation's vendor id before changing owner. */
    private suspend fun revokeDeviceId(oldId: String): Boolean {
        if (!initialize()) return false
        val result = sdkResult<Int> { done -> sdk.deleteRegId(context, done) }
        if (result != 0 || !store.markRevoked(oldId)) return false
        deviceRegId = null
        confirmed = null
        bindingOwner = null
        return true
    }

    private suspend fun <T> sdkResult(start: ((T) -> Unit) -> Unit): T? = withTimeoutOrNull(15_000) {
        val result = CompletableDeferred<T>()
        start { result.complete(it) }
        result.await()
    }
}
