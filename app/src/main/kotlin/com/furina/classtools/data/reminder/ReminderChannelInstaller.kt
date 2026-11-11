package com.furina.classtools.data.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat

/**
 * 通知渠道创建/读取（Android 实现）。
 *
 * v3.7 起：
 * - 在 `ClassToolsApp.onCreate()` / 每次提醒发送前调用 [ensureCreated]（**提前创建**，不再等首次 notify）；
 * - 渠道创建后用户可能自行调整重要性，应用不能强行覆盖 → 诊断页读取系统真实值并给用户明确指引；
 * - `notice_inbox_high` 是 v3.7 新增的高优先级「新通知」渠道（旧安装也能获得 HIGH 渠道行为）。
 */
object ReminderChannelInstaller {

    /** 幂等创建全部渠道（API 26+；已有渠道不覆盖用户设置，只补齐缺失 id）。 */
    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.applicationContext.getSystemService(NotificationManager::class.java) ?: return
        ReminderChannels.ALL.forEach { spec ->
            if (manager.getNotificationChannel(spec.id) != null) return@forEach
            runCatching { manager.createNotificationChannel(buildChannel(spec)) }
        }
    }

    /** 读取系统真实渠道重要性（未创建/低版本 → null）。 */
    fun importanceOf(context: Context, channelId: String): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val manager = context.applicationContext.getSystemService(NotificationManager::class.java) ?: return null
        return runCatching { manager.getNotificationChannel(channelId)?.importance }.getOrNull()
    }

    /** 系统通知总开关（Android 13+ 还会叠加 POST_NOTIFICATIONS 运行时权限）。 */
    fun areNotificationsEnabled(context: Context): Boolean =
        runCatching { NotificationManagerCompat.from(context.applicationContext).areNotificationsEnabled() }
            .getOrDefault(false)

    private fun buildChannel(spec: ReminderChannelSpec): NotificationChannel {
        val importance = if (spec.highImportance) {
            NotificationManager.IMPORTANCE_HIGH
        } else {
            NotificationManager.IMPORTANCE_DEFAULT
        }
        return NotificationChannel(spec.id, spec.name, importance).apply {
            description = spec.description
            enableVibration(spec.vibration)
            if (spec.vibration) vibrationPattern = longArrayOf(0L, 180L, 120L, 180L)
            enableLights(true)
            setShowBadge(true)
            lockscreenVisibility = if (spec.lockscreenPublic) {
                Notification.VISIBILITY_PUBLIC
            } else {
                Notification.VISIBILITY_PRIVATE
            }
        }
    }
}
