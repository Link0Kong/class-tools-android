package com.furina.classtools.data.push

import android.content.Context
import android.content.Intent
import com.furina.classtools.MainActivity
import com.vivo.push.model.UPSNotificationMessage
import com.vivo.push.sdk.OpenClientPushMessageReceiver

/**
 * vivo 推送回调入口（Manifest 注册，系统按类名实例化）。
 *
 * - [onReceiveRegId]：首次订阅成功 / regId 变化 → 交给 [VivoPushBus] → manager 缓存并上传云端；
 * - [onNotificationMessageClicked]：SDK 3.0+ 默认由系统通知中心直接拉起 Activity（服务端 skipType=4），
 *   本回调仅作兼容兜底：若通知未带自定义 intent，则手动带 noticeId 打开 MainActivity。
 */
class VivoPushReceiver : OpenClientPushMessageReceiver() {

    override fun onReceiveRegId(context: Context, regId: String?) {
        VivoPushBus.dispatchRegId(regId)
    }

    override fun onNotificationMessageClicked(context: Context, message: UPSNotificationMessage?) {
        val noticeId = message?.params?.get(EXTRA_NOTICE_ID)?.trim().orEmpty()
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (noticeId.isNotEmpty()) putExtra(EXTRA_NOTICE_ID, noticeId)
        }
        runCatching { context.startActivity(intent) }
    }

    private companion object {
        const val EXTRA_NOTICE_ID = "noticeId"
    }
}
