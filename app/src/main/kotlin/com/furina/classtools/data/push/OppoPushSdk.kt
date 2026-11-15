package com.furina.classtools.data.push

import android.content.Context
import com.heytap.msp.push.HeytapPushManager
import com.heytap.msp.push.callback.ICallBackResultService

/**
 * OPPO PUSH SDK 的最小接口隔离。
 *
 * 真实实现 [HeytapOppoPushSdk] 直接调用官方 `HeytapPushManager`；单测使用假实现，
 * 因此**注册流程/状态机/上传逻辑的单元测试不依赖真机、SDK 运行环境或真实密钥**。
 */
interface OppoPushSdk {
    /** 官方 `HeytapPushManager.init(context, debug)`（Application.onCreate 调用一次）。 */
    fun init(debug: Boolean)

    /** 官方 `HeytapPushManager.isSupportPush(context)`：仅 ColorOS/Heytap 通道设备为 true。 */
    fun isSupportPush(): Boolean

    /** 官方 `HeytapPushManager.register(context, appKey, appSecret, callback)`。 */
    fun register(appKey: String, appSecret: String, callback: OppoPushCallback)

    /** 官方 `HeytapPushManager.getRegisterID()`（可能为空：未注册成功）。 */
    fun currentRegistrationId(): String?
}

/** 注册结果回调（已把 SDK 的 4 个 Java 回调参数收敛为 2 个业务回调）。 */
interface OppoPushCallback {
    fun onRegisterSucceeded(registrationId: String)

    fun onRegisterFailed(code: Int, message: String)
}

/** 官方 SDK 适配实现（Android 运行时；逻辑极薄，避免把 SDK 类型扩散到业务层）。 */
class HeytapOppoPushSdk(context: Context) : OppoPushSdk {

    private val appContext: Context = context.applicationContext

    override fun init(debug: Boolean) {
        HeytapPushManager.init(appContext, debug)
    }

    override fun isSupportPush(): Boolean =
        runCatching { HeytapPushManager.isSupportPush(appContext) }.getOrDefault(false)

    override fun register(appKey: String, appSecret: String, callback: OppoPushCallback) {
        HeytapPushManager.register(
            appContext,
            appKey,
            appSecret,
            object : ICallBackResultService {
                /** 官方签名：`onRegister(int responseCode, String registerID, String packageName, String miniPackageName)`。 */
                override fun onRegister(
                    responseCode: Int,
                    registerID: String?,
                    packageName: String?,
                    miniPackageName: String?,
                ) {
                    if (responseCode == OppoPushText.SUCCESS_CODE && !registerID.isNullOrBlank()) {
                        callback.onRegisterSucceeded(registerID)
                    } else {
                        val code = if (responseCode != OppoPushText.SUCCESS_CODE) {
                            responseCode
                        } else {
                            -2
                        }
                        callback.onRegisterFailed(code, packageName.orEmpty())
                    }
                }

                override fun onUnRegister(
                    responseCode: Int,
                    packageName: String?,
                    miniProgramPkg: String?,
                ) = Unit

                override fun onSetPushTime(responseCode: Int, pushTime: String?) = Unit

                override fun onGetPushStatus(responseCode: Int, status: Int) = Unit

                override fun onGetNotificationStatus(responseCode: Int, status: Int) = Unit

                override fun onError(
                    errorCode: Int,
                    message: String?,
                    packageName: String?,
                    miniProgramPkg: String?,
                ) {
                    val detail = listOfNotNull(message?.takeIf { it.isNotBlank() }, packageName)
                        .joinToString(" ")
                    callback.onRegisterFailed(errorCode, detail)
                }
            },
        )
    }

    override fun currentRegistrationId(): String? =
        runCatching { HeytapPushManager.getRegisterID() }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}
