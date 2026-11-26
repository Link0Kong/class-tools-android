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
import androidx.core.graphics.drawable.IconCompat
import com.furina.classtools.MainActivity
import com.furina.classtools.R
import com.furina.classtools.data.ppt.PptUploadPhase
import com.furina.classtools.data.ppt.PptUploadProgress
import com.furina.classtools.data.reminder.ReminderIntentFactory
import com.furina.classtools.data.vote.VoteLogic

/**
 * 系统级「实时活动」通知（Android 16 Live Updates / 状态栏胶囊）。
 *
 * 背景：ColorOS 16 的流体云完整接入了原生 Android 16 Live Updates API（OPPO 官方确认），
 * 因此遵循 Google Live Updates 规范即可在支持设备上以流体云/灵动岛/状态栏胶囊呈现；
 * 不支持的系统则退化为普通常驻通知（进度条 + 倒计时），不影响功能。
 *
 * 规则（Google 规范）：
 * - `POST_PROMOTED_NOTIFICATIONS` 非运行时权限（manifest 声明）；
 * - 使用 ongoing 和 contentTitle，交给系统绘制展开模板；Android 16 各修订版的配色兼容见 [buildLiveUpdate]；
 * - 样式为 Standard / BigText / Progress / Call / Metric；
 * - 通过 `setRequestPromotedOngoing(true)` 请求系统升级。
 */
object LiveUpdateNotifier {

    const val CHANNEL_ID = "ct_live_updates"

    private const val PPT_NOTIFICATION_ID = 7401
    private const val VOTE_NOTIFICATION_ID = 7402
    private const val DEBUG_NOTIFICATION_ID = 7403
    private const val PPT_RESULT_NOTIFICATION_ID = 7411
    private const val PROGRESS_MAX = 100
    private const val CHANNEL_DESCRIPTION = "上传进度、限时投票倒计时等实时通知"
    private const val OLD_CHANNEL_DESCRIPTION = "上传进度、限时投票倒计时等实时活动（支持 Android 16 实时更新/流体云）"

    /** 预先创建「实时活动」通知渠道（设置入口跳转前调用）。 */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "实时活动",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = CHANNEL_DESCRIPTION
                    setShowBadge(false)
                    enableVibration(false)
                },
            )
        } else if (existing.description == OLD_CHANNEL_DESCRIPTION) {
            existing.description = CHANNEL_DESCRIPTION
            manager.createNotificationChannel(existing)
        }
    }

    /** 供 UI 判断「实时活动是否会显示」；未授权通知时可在页面上给出解释。 */
    fun isAvailable(context: Context): Boolean = canNotify(context)

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
        val percent = progress.percent.coerceIn(0, PROGRESS_MAX)
        val phaseText = when (progress.phase) {
            PptUploadPhase.CREATING -> "正在准备文件"
            PptUploadPhase.UPLOADING -> "已上传 $percent%"
            PptUploadPhase.SUBMITTING -> "上传完成，正在提交转换"
            else -> progress.message.ifBlank { "处理中" }
        }
        val accent = context.getColor(R.color.live_upload_accent)
        val style = NotificationCompat.ProgressStyle()
            .setProgressSegments(
                listOf(
                    NotificationCompat.ProgressStyle.Segment(PROGRESS_MAX).setColor(accent),
                ),
            )
            .setProgress(percent)
            .setProgressIndeterminate(!uploading)
            .setStyledByProgress(true)
        if (uploading) {
            style.setProgressTrackerIcon(IconCompat.createWithResource(context, R.drawable.ic_live_upload).setTint(accent))
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_live_upload)
            .setColor(accent)
            .setContentTitle(progress.title.ifBlank { "上传演示文稿" })
            .setSubText("演示上传")
            .setContentText(phaseText)
            .setShowWhen(false)
            .setStyle(style)
            .setProgress(PROGRESS_MAX, percent, !uploading)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(when (progress.phase) {
                PptUploadPhase.UPLOADING -> "$percent%"
                PptUploadPhase.CREATING -> "准备中"
                else -> "提交中"
            })
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openToolIntent(context, "ppt"))
        runCatching {
            NotificationManagerCompat.from(context).notify(PPT_NOTIFICATION_ID, builder.buildLiveUpdate())
        }
    }

    /** 上传成功/失败后的短暂结果通知（非实时活动，自动消失）。 */
    fun postPptFinished(context: Context, success: Boolean, message: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_live_upload)
            .setColor(context.getColor(R.color.live_upload_accent))
            .setContentTitle(if (success) "演示上传完成" else "演示上传失败")
            .setContentText(message.ifBlank { if (success) "已提交转换，可在演示列表查看进度" else "请稍后重试" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.ifBlank { if (success) "已提交转换，可在演示列表查看进度" else "请稍后重试" }))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openToolIntent(context, "ppt"))
        runCatching {
            NotificationManagerCompat.from(context).notify(PPT_RESULT_NOTIFICATION_ID, builder.build())
        }
    }

    fun cancelPptUpload(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(PPT_NOTIFICATION_ID) }
    }

    // ---------------------------------------------------------------- 限时投票

    /** 限时投票倒计时：常驻 + 系统倒计时（无需应用逐秒刷新）。 */
    fun postVoteDeadline(context: Context, pollTitle: String, deadlineMillis: Long, countdownDeadlineMillis: Long = deadlineMillis) {
        val remaining = countdownDeadlineMillis - System.currentTimeMillis()
        if (remaining <= 0L) {
            cancelVote(context)
            return
        }
        if (!canNotify(context)) return
        ensureChannel(context)
        val deadlineText = "截止 ${VoteLogic.formatAt(deadlineMillis)}"
        val title = pollTitle.ifBlank { "班级投票" }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_live_vote)
            .setColor(context.getColor(R.color.live_vote_accent))
            .setSubText("限时投票")
            .setContentTitle(title)
            .setContentText(deadlineText)
            .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(deadlineText).bigText(title))
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setShowWhen(true)
            .setWhen(countdownDeadlineMillis)
            .setTimeoutAfter(remaining)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setRequestPromotedOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(openToolIntent(context, "vote"))
        runCatching {
            NotificationManagerCompat.from(context).notify(VOTE_NOTIFICATION_ID, builder.buildLiveUpdate())
        }
    }

    fun cancelVote(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(VOTE_NOTIFICATION_ID) }
    }

    // ---------------------------------------------------------------- 调试演示

    /** Debug 构建「实时活动测试」：60 秒倒计时，验证系统是否升级为实时活动/流体云。 */
    fun postDebugCountdown(context: Context, seconds: Int = 60) {
        if (seconds <= 0) {
            cancelDebug(context)
            return
        }
        if (!canNotify(context)) return
        ensureChannel(context)
        val deadline = System.currentTimeMillis() + seconds * 1000L
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_live_timer)
            .setContentTitle("实时活动测试")
            .setContentText("正在验证系统倒计时")
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(deadline)
            .setShowWhen(true)
            .setTimeoutAfter(seconds * 1_000L)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setRequestPromotedOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(openToolIntent(context, "vote"))
        runCatching {
            NotificationManagerCompat.from(context).notify(DEBUG_NOTIFICATION_ID, builder.buildLiveUpdate())
        }
    }

    fun cancelDebug(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(DEBUG_NOTIFICATION_ID) }
    }
}
