package com.furina.classtools.data.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.furina.classtools.MainActivity
import com.furina.classtools.data.ppt.PptUploadPhase
import com.furina.classtools.data.ppt.PptUploadProgress
import com.furina.classtools.data.reminder.ReminderIntentFactory
import com.furina.classtools.data.reminder.ReminderSmallIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 系统级「实时活动」通知（Android 16 Live Updates / 状态栏胶囊）。
 *
 * 背景：ColorOS 16 的流体云完整接入了原生 Android 16 Live Updates API（OPPO 官方确认），
 * 因此遵循 Google Live Updates 规范即可在支持设备上以流体云/灵动岛/状态栏胶囊呈现；
 * 不支持的系统则退化为普通常驻通知（进度条 + 倒计时），不影响功能。
 *
 * 规则（Google 规范）：
 * - `POST_PROMOTED_NOTIFICATIONS` 非运行时权限（manifest 声明）；
 * - 通知必须 ongoing、有 contentTitle、不得使用 customContentView / 群摘要 / colorized；
 * - 样式为 Standard / BigText / Progress / Call / Metric；
 * - 通过 `setRequestPromotedOngoing(true)` 请求系统升级。
 */
object LiveUpdateNotifier {

    const val CHANNEL_ID = "ct_live_updates"

    private const val PPT_NOTIFICATION_ID = 7401
    private const val VOTE_NOTIFICATION_ID = 7402
    private const val DEBUG_NOTIFICATION_ID = 7403
    private const val PROGRESS_MAX = 100

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "实时活动",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "上传进度、限时投票倒计时等实时活动（支持 Android 16 实时更新/流体云）"
                    setShowBadge(false)
                    enableVibration(false)
                },
            )
        }
    }

    private fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }
            .getOrDefault(false)
    }

    private fun smallIcon(context: Context): Int = ReminderSmallIcon.resolve { name ->
        context.resources.getIdentifier(name, "drawable", context.packageName).takeIf { it != 0 }
    }

    private fun openToolIntent(context: Context, toolId: String): PendingIntent? = runCatching {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(ReminderIntentFactory.EXTRA_TOOL_ID, toolId)
        }
        PendingIntent.getActivity(
            context,
            9100 + toolId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }.getOrNull()

    // ---------------------------------------------------------------- PPT 上传

    /** 上传/转换进度：常驻 + 进度条 + 系统胶囊百分比。 */
    fun postPptUpload(context: Context, progress: PptUploadProgress) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val uploading = progress.phase == PptUploadPhase.UPLOADING
        val phaseText = when (progress.phase) {
            PptUploadPhase.CREATING -> "正在创建上传任务"
            PptUploadPhase.UPLOADING -> "已上传 ${progress.percent}%"
            PptUploadPhase.SUBMITTING -> "正在提交转换"
            else -> progress.message.ifBlank { "处理中" }
        }
        val percent = progress.percent.coerceIn(0, PROGRESS_MAX)
        val style = NotificationCompat.ProgressStyle()
            .setProgressSegments(
                listOf(
                    NotificationCompat.ProgressStyle.Segment(PROGRESS_MAX)
                        .setColor(0xFF4E9082.toInt()),
                ),
            )
            .setProgressIndeterminate(!uploading)
            .setStyledByProgress(true)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon(context))
            .setContentTitle("演示上传中")
            .setContentText(phaseText)
            .setStyle(style)
            .setProgress(PROGRESS_MAX, percent, !uploading)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(if (uploading) "上传 $percent%" else "演示处理中")
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openToolIntent(context, "ppt"))
        runCatching {
            NotificationManagerCompat.from(context).notify(PPT_NOTIFICATION_ID, builder.build())
        }
    }

    fun cancelPptUpload(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(PPT_NOTIFICATION_ID) }
    }

    // ---------------------------------------------------------------- 限时投票

    /** 限时投票倒计时：常驻 + 系统倒计时（无需应用逐秒刷新）。 */
    fun postVoteDeadline(context: Context, pollTitle: String, deadlineMillis: Long) {
        if (deadlineMillis <= System.currentTimeMillis()) return
        if (!canNotify(context)) return
        ensureChannel(context)
        val deadlineText = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(deadlineMillis))
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon(context))
            .setContentTitle("限时投票进行中")
            .setContentText(pollTitle.ifBlank { "点击查看投票" })
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${pollTitle.ifBlank { "投票" }} · 截止 $deadlineText",
                ),
            )
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(deadlineMillis)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText("限时投票")
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(openToolIntent(context, "vote"))
        runCatching {
            NotificationManagerCompat.from(context).notify(VOTE_NOTIFICATION_ID, builder.build())
        }
    }

    fun cancelVote(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(VOTE_NOTIFICATION_ID) }
    }

    // ---------------------------------------------------------------- 调试演示

    /** Debug 构建「实时活动测试」：60 秒倒计时，验证系统是否升级为实时活动/流体云。 */
    fun postDebugCountdown(context: Context, seconds: Int = 60) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val deadline = System.currentTimeMillis() + seconds * 1000L
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon(context))
            .setContentTitle("实时活动测试")
            .setContentText("$seconds 秒倒计时（常驻，可在通知中移除）")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$seconds 秒倒计时 · 用于验证实时活动/流体云/胶囊展示"))
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(deadline)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText("测试 $seconds 秒")
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(openToolIntent(context, "vote"))
        runCatching {
            NotificationManagerCompat.from(context).notify(DEBUG_NOTIFICATION_ID, builder.build())
        }
    }

    fun cancelDebug(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(DEBUG_NOTIFICATION_ID) }
    }
}
