package com.furina.classtools.data.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat

internal object LiveCapabilities {
    fun canNotify(context: Context, channelId: String): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (manager.getNotificationChannel(channelId)?.importance ?: NotificationManager.IMPORTANCE_NONE) >
                NotificationManager.IMPORTANCE_NONE
    }

    fun canPromote(context: Context, channelId: String): Boolean {
        if (Build.VERSION.SDK_INT < 36 || !canNotify(context, channelId)) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        return (manager.getNotificationChannel(channelId)?.importance ?: NotificationManager.IMPORTANCE_NONE) > NotificationManager.IMPORTANCE_MIN &&
            manager.canPostPromotedNotifications()
    }

    fun summary(context: Context, colorOs: Boolean): String {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return "系统通知已关闭，可在工作台查看活动"
        val manager = context.getSystemService(NotificationManager::class.java)
        val count = manager.activeNotifications.count { record ->
            record.isOngoing && record.notification.channelId in setOf(LiveUpdateNotifier.CHANNEL_ID,
                com.furina.classtools.data.reminder.ReminderKind.COURSE_SOON.channelId) &&
                isPromoted(record.notification)
        }
        if (count > 0) return "系统已提升 " + count + " 个实时活动"
        if (Build.VERSION.SDK_INT < 36 || !manager.canPostPromotedNotifications()) return "通过普通通知展示活动"
        return if (colorOs) "支持的活动可请求流体云展示" else "支持的活动可请求顶部展示"
    }

    private fun isPromoted(notification: Notification): Boolean =
        Build.VERSION.SDK_INT >= 36 && notification.flags and Notification.FLAG_PROMOTED_ONGOING != 0

    /** null until System UI has a matching active record. */
    fun promoted(context: Context, tag: String?, id: Int): Boolean? {
        if (Build.VERSION.SDK_INT < 36) return false
        return context.getSystemService(NotificationManager::class.java).activeNotifications
            .firstOrNull { it.tag == tag && it.id == id }?.let {
                isPromoted(it.notification)
            }
    }
}
