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
import com.furina.classtools.ClassToolsApp
import com.furina.classtools.R
import com.furina.classtools.data.notify.buildLiveUpdate
import com.furina.classtools.data.timetable.TimetableLink
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 本机提醒通知发送（接口化：Worker 用 Android 实现，单测用假实现，绝不在单测真发通知）。
 *
 * 渠道（按 [ReminderKind] 自动创建）：作业 homework_due / 课表 class_soon / 通知 notice_due；
 * 分组：同一类提醒折叠为一个通知组（`setGroup` + 组摘要），避免多条提醒刷屏；
 * 小图标：优先专用单色 drawable `ic_stat_class_tools`，缺失时回退系统占位（见 [ReminderSmallIcon]）；
 * 无通知权限时静默返回 false（不崩、不请求权限——请求由 UI 负责）。
 */
interface ReminderNotifier {
    /** 创建通知渠道（幂等；API 26+）。 */
    fun ensureChannels()

    /** 发送一条提醒；返回是否真正发出（无权限/无 Launcher Activity 时 false）。 */
    fun notify(event: ReminderEvent): Boolean

    /** Refresh a visible course card or withdraw it if the timetable or clock moved out of its window. */
    fun reconcileLiveCourse(due: ReminderEvent?, nowMillis: Long, alreadySent: Boolean = false): List<String> = emptyList()
}

/** 通知分组口径（纯常量/文案，可单测）。 */
object ReminderGrouping {

    const val GROUP_HOMEWORK: String = "ct_reminder_group_hw"
    const val GROUP_COURSE: String = "ct_reminder_group_tt"
    const val GROUP_NOTICE: String = "ct_reminder_group_nt"
    const val GROUP_VOTE: String = "ct_reminder_group_vote"
    const val GROUP_WATER: String = "ct_reminder_group_water"
    const val GROUP_TEST: String = "ct_reminder_group_test"

    fun groupKey(kind: ReminderKind): String = when (kind) {
        ReminderKind.PERSONAL_TASK -> "ct_reminder_group_tasks"
        ReminderKind.HOMEWORK_NEW, ReminderKind.HOMEWORK_DIGEST, ReminderKind.HOMEWORK_DUE -> GROUP_HOMEWORK
        ReminderKind.COURSE_SOON -> GROUP_COURSE
        ReminderKind.NOTICE_NEW, ReminderKind.NOTICE_DIGEST, ReminderKind.NOTICE_DUE,
        ReminderKind.NOTICE_SIGNUP,
        -> GROUP_NOTICE
        ReminderKind.VOTE_NEW, ReminderKind.VOTE_RESULT -> GROUP_VOTE
        ReminderKind.WATER_DEDUCT -> GROUP_WATER
        ReminderKind.SYSTEM_TEST -> GROUP_TEST
    }

    fun summaryTitle(kind: ReminderKind): String = when (kind) {
        ReminderKind.PERSONAL_TASK -> "任务提醒"
        ReminderKind.HOMEWORK_NEW, ReminderKind.HOMEWORK_DIGEST, ReminderKind.HOMEWORK_DUE -> "作业提醒"
        ReminderKind.COURSE_SOON -> "上课提醒"
        ReminderKind.NOTICE_NEW, ReminderKind.NOTICE_DIGEST, ReminderKind.NOTICE_DUE,
        ReminderKind.NOTICE_SIGNUP,
        -> "通知提醒"
        ReminderKind.VOTE_NEW, ReminderKind.VOTE_RESULT -> "投票提醒"
        ReminderKind.WATER_DEDUCT -> "水费提醒"
        ReminderKind.SYSTEM_TEST -> "通知测试"
    }

    /** 组摘要 id：固定值（每组一个摘要通知，不堆叠）。 */
    fun summaryId(kind: ReminderKind): Int = groupKey(kind).hashCode()

    /** 组摘要文案：`2 条提醒 · 点击查看`。 */
    fun summaryText(count: Int): String = "${count.coerceAtLeast(1)} 条提醒 · 点击查看"
}

class AndroidReminderNotifier(
    context: Context,
    private val supportsLiveCourse: Boolean = Build.VERSION.SDK_INT >= 36 &&
        (context.applicationContext as? ClassToolsApp)?.container?.hasColorOsRom() == true,
) : ReminderNotifier {

    private val appContext: Context = context.applicationContext

    private val smallIcon: Int by lazy {
        ReminderSmallIcon.resolve { name ->
            appContext.resources.getIdentifier(name, "drawable", appContext.packageName).takeIf { it != 0 }
        }
    }

    /** 本轮进程内各分组已发条数（组摘要文案用；进程重启后重新计数）。 */
    private val groupCounts: MutableMap<String, Int> = mutableMapOf()

    override fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        ReminderKind.entries
            .distinctBy { it.channelId }
            .forEach { kind ->
                if (manager.getNotificationChannel(kind.channelId) == null) {
                    manager.createNotificationChannel(
                        NotificationChannel(
                            kind.channelId,
                            kind.channelName,
                            if (kind.highPriority) {
                                NotificationManager.IMPORTANCE_HIGH
                            } else {
                                NotificationManager.IMPORTANCE_DEFAULT
                            },
                        ).apply { description = kind.channelDesc },
                    )
                }
            }
    }

    override fun reconcileLiveCourse(due: ReminderEvent?, nowMillis: Long, alreadySent: Boolean): List<String> {
        if (!supportsLiveCourse) return emptyList()
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return emptyList()
        if (due == null) CourseLiveService.stop(appContext)
        val active = runCatching { manager.activeNotifications }.getOrElse { return emptyList() }
        val withdrawn = mutableListOf<String>()
        for (card in active) {
            val shown = card.notification
            if (!card.isOngoing || shown.channelId != ReminderKind.COURSE_SOON.channelId) continue
            val key = "class:${shown.`when`}"
            if (card.id != key.hashCode()) continue
            if (shown.`when` == due?.courseStartAtMillis) continue
            if (due != null) CourseLiveService.stop(appContext)
            manager.cancel(card.id)
            if (shown.`when` > nowMillis) withdrawn.add(key)
        }
        if (alreadySent && due != null) {
            post(due, silent = true)
        }
        return withdrawn
    }

    override fun notify(event: ReminderEvent): Boolean = post(event, silent = false)

    private fun post(event: ReminderEvent, silent: Boolean): Boolean {
        if (!canNotify() || appContext.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(event.kind.channelId)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val pendingIntent = ReminderIntentFactory.pendingIntent(appContext, event)
        viewEvent(event)
        val startAt = event.courseStartAtMillis
        val now = System.currentTimeMillis()
        if (event.kind == ReminderKind.COURSE_SOON &&
            (startAt == null || startAt <= now || event.triggerAtMillis > now)) return false
        val liveCourse = startAt != null && supportsLiveCourse
        val body = if (event.kind == ReminderKind.COURSE_SOON) event.body.substringBeforeLast(" · 还有 ") else event.body
        val groupKey = ReminderGrouping.groupKey(event.kind)
        val builder = NotificationCompat.Builder(appContext, event.kind.channelId)
            .setSmallIcon(smallIcon)
            .setContentTitle(event.title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(
                if (event.kind.highPriority) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                },
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
        if (startAt != null) {
            builder.setWhen(startAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setTimeoutAfter(startAt - now)
        }
        val userReminder = event.kind == ReminderKind.PERSONAL_TASK || event.key.startsWith("oneshot:")
        if (userReminder) builder.setWhen(event.triggerAtMillis)
        if (liveCourse) {
            builder.setLiveCourseContent(appContext, event, body)
            builder.setAutoCancel(false)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setSilent(silent)
                .setOngoing(true)
                .setRequestPromotedOngoing(true)
        } else if (!userReminder) {
            builder.setGroup(groupKey).setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
        }
        val childOk = runCatching {
            val notification = if (liveCourse) builder.buildLiveUpdate() else builder.build()
            NotificationManagerCompat.from(appContext).notify(event.notificationId, notification)
            if (liveCourse) CourseLiveService.attach(appContext, event.notificationId, notification)
            true
        }.getOrDefault(false)
        if (childOk && !liveCourse && !userReminder) {
            postGroupSummary(event, groupKey, pendingIntent)
        }
        return childOk
    }

    private fun postGroupSummary(
        event: ReminderEvent,
        groupKey: String,
        pendingIntent: android.app.PendingIntent,
    ) {
        val count = synchronized(groupCounts) {
            val next = (groupCounts[groupKey] ?: 0) + 1
            groupCounts[groupKey] = next
            next
        }
        val summary = NotificationCompat.Builder(appContext, event.kind.channelId)
            .setSmallIcon(smallIcon)
            .setContentTitle(ReminderGrouping.summaryTitle(event.kind))
            .setContentText(ReminderGrouping.summaryText(count))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(ReminderGrouping.summaryText(count)),
            )
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()
        runCatching {
            NotificationManagerCompat.from(appContext)
                .notify(ReminderGrouping.summaryId(event.kind), summary)
        }
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

private val courseClock = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA).withZone(TimetableLink.CHINA_ZONE)

/** Use the platform's expandable template so System UI can also render the promoted chip. */
internal fun NotificationCompat.Builder.setLiveCourseContent(context: Context, event: ReminderEvent, fallback: String) {
    setSmallIcon(R.drawable.ic_live_course)
    setColor(context.getColor(R.color.live_course_accent))
    setSubText("即将上课")
    val course = event.course
    if (course == null) {
        setContentTitle(event.title.removePrefix("上课提醒 · ").ifBlank { "上课提醒" })
        setContentText(fallback)
        setStyle(NotificationCompat.BigTextStyle().bigText(fallback))
        return
    }
    val start = courseClock.format(Instant.ofEpochMilli(course.startAt))
    val time = if (course.endAt > course.startAt) {
        "$start–${courseClock.format(Instant.ofEpochMilli(course.endAt))}"
    } else start
    val room = course.room.trim()
    val details = buildString {
        append("时间  ").append(time)
        if (room.isNotEmpty()) append("\n教室  ").append(room)
    }
    setContentTitle(course.name.ifBlank { "上课提醒" })
    setContentText(if (room.isEmpty()) time else "$time · $room")
    setStyle(NotificationCompat.BigTextStyle().bigText(details))
}
