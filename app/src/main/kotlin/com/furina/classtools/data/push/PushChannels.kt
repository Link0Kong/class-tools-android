package com.furina.classtools.data.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * OPPO PUSH 通知渠道（服务端单点推送通知栏消息时带 `channel_id="class_notice"`）。
 *
 * 系统要求：Android 8+ 若通知携带的渠道不存在，通知会被**静默丢弃**；因此必须在
 * 推送到达前（App 启动 / 注册前）创建该渠道。渠道由客户端创建后，服务端只引用 id。
 *
 * 注册流程见 [OppoPushManager.onAppCreated]（未配置密钥时也创建渠道：不影响用户，仅占位）。
 */
object PushChannels {

    /** 与服务端 `channel_id` 完全一致的固定值（三端契约；不要改动）。 */
    const val CLASS_NOTICE_ID: String = "class_notice"

    /** 渠道展示名。 */
    const val CLASS_NOTICE_NAME: String = "班级通知"

    /** 渠道描述（系统设置里的说明）。 */
    const val CLASS_NOTICE_DESCRIPTION: String = "服务端推送的班级通知（未打开 App 时也能收到）"

    /** 幂等创建「班级通知」渠道。 */
    fun ensureClassNoticeChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.applicationContext
            .getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CLASS_NOTICE_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CLASS_NOTICE_ID,
                CLASS_NOTICE_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = CLASS_NOTICE_DESCRIPTION
                enableVibration(true)
                setShowBadge(true)
            },
        )
    }
}
