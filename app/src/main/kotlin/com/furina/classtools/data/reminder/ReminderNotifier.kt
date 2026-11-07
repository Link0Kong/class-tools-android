package com.furina.classtools.data.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.furina.classtools.data.permission.PermissionTexts

/**
 * 本机提醒通知发送（接口化：Worker 用 Android 实现，单测用假实现，绝不在单测真发通知）。
 *
 * 渠道：作业 [PermissionTexts.CHANNEL_HOMEWORK_ID] / 课表 [PermissionTexts.CHANNEL_CLASS_ID]；
 * 无通知权限时静默返回 false（不崩、不请求权限——请求由 UI 负责）。
 */
interface ReminderNotifier {
    /** 创建通知渠道（幂等；API 26+）。 */
    fun ensureChannels()

    /** 发送一条提醒；返回是否真正发出（无权限/无 Launcher Activity 时 false）。 */
    fun notify(event: ReminderEvent): Boolean
}

class AndroidReminderNotifier(
    context: Context,
) : ReminderNotifier {

    private val appContext: Context = context.applicationContext

    override fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(PermissionTexts.CHANNEL_HOMEWORK_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    PermissionTexts.CHANNEL_HOMEWORK_ID,
                    PermissionTexts.CHANNEL_HOMEWORK_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = PermissionTexts.CHANNEL_HOMEWORK_DESC },
            )
        }
        if (manager.getNotificationChannel(PermissionTexts.CHANNEL_CLASS_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    PermissionTexts.CHANNEL_CLASS_ID,
                    PermissionTexts.CHANNEL_CLASS_NAME,
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = PermissionTexts.CHANNEL_CLASS_DESC },
            )
        }
    }

    override fun notify(event: ReminderEvent): Boolean {
        if (!canNotify()) return false
        val pendingIntent = ReminderIntentFactory.pendingIntent(appContext, event) ?: return false
        viewEvent(event)
        val builder = NotificationCompat.Builder(appContext, event.kind.channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(event.title)
            .setContentText(event.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(event.body))
            .setPriority(
                if (event.kind == ReminderKind.COURSE_SOON) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                },
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
        return runCatching {
            NotificationManagerCompat.from(appContext).notify(event.notificationId, builder.build())
            true
        }.getOrDefault(false)
    }

    private fun canNotify(): Boolean {
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun viewEvent(event: ReminderEvent) {
        // 预留埋点/诊断钩子（当前不采集，保持零外发）。
        @Suppress("UNUSED_VARIABLE")
        val ignored = event.key
    }
}
