package com.furina.classtools.data.push

import android.content.Context
import com.vivo.push.IPushActionListener
import com.vivo.push.PushClient
import com.vivo.push.PushConfig
import com.vivo.push.listener.IPushQueryActionListener

/**
 * vivo 推送 SDK 最小接口隔离（官方 vpush_clientSDK 4.1.7.0_517）。
 *
 * 真实实现 [VivoPushSdkImpl] 调 `PushClient`；单测使用假实现，不依赖 vivo 真机与真实密钥。
 */
interface VivoPushSdk {
    /** 初始化（agreePrivacyStatement=true 才允许联网注册）。返回是否成功（异常也归一为 false）。 */
    fun initialize(context: Context, agreePrivacyStatement: Boolean): Boolean

    /** 打开推送开关；[onState] 0=成功。 */
    fun turnOnPush(context: Context, onState: (Int) -> Unit)

    /** 使旧设备标识失效，供原账号凭据已不可用时解除绑定。 */
    fun deleteRegId(context: Context, onState: (Int) -> Unit)

    /** 获取 regId（需 turnOnPush 成功后调用）。 */
    fun requestRegId(
        context: Context,
        onSuccess: (String) -> Unit,
        onFailure: (Int) -> Unit,
    )

    /** 设备/系统是否支持 vivo 推送。 */
    fun isSupport(context: Context): Boolean
}

/** 官方 SDK 适配（所有调用 runCatching，异常不冒泡到 App 启动流程）。 */
class VivoPushSdkImpl : VivoPushSdk {

    override fun initialize(context: Context, agreePrivacyStatement: Boolean): Boolean = runCatching {
        val config = PushConfig.Builder()
            .agreePrivacyStatement(agreePrivacyStatement)
            .build()
        PushClient.getInstance(context.applicationContext).initialize(config)
        true
    }.getOrDefault(false)

    override fun turnOnPush(context: Context, onState: (Int) -> Unit) {
        runCatching {
            PushClient.getInstance(context.applicationContext).turnOnPush(object : IPushActionListener {
                override fun onStateChanged(state: Int) = onState(state)
            })
        }.onFailure { onState(-2) }
    }

    override fun deleteRegId(context: Context, onState: (Int) -> Unit) {
        runCatching {
            PushClient.getInstance(context.applicationContext).deleteRegid { state -> onState(state) }
        }.onFailure { onState(-2) }
    }

    override fun requestRegId(
        context: Context,
        onSuccess: (String) -> Unit,
        onFailure: (Int) -> Unit,
    ) {
        runCatching {
            PushClient.getInstance(context.applicationContext)
                .getRegId(object : IPushQueryActionListener {
                    override fun onSuccess(regId: String) = onSuccess(regId)
                    override fun onFail(errorCode: Int?) = onFailure(errorCode ?: -2)
                })
        }.onFailure { onFailure(-2) }
    }

    override fun isSupport(context: Context): Boolean =
        runCatching { PushClient.getInstance(context.applicationContext).isSupport() }
            .getOrDefault(false)
}
