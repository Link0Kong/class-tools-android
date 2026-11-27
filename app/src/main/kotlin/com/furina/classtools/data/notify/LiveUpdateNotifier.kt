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
import com.furina.classtools.data.transfer.TransferActivities
import com.furina.classtools.data.transfer.TransferActivity
import com.furina.classtools.data.transfer.TransferStage
import com.furina.classtools.data.reminder.ReminderIntentFactory
import com.furina.classtools.data.vote.VoteLogic

/**
 * 系统级「实时活动」通知（Android 16 Live Updates / 状态栏胶囊）。
 *
 * 应用请求系统提升展示；具体入口和布局由系统决定。
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

    internal fun clearTransfer(context: Context, task: TransferActivity) {
        val tag = ActivityLinks.tag(task.owner, task.kind, task.id)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(tag, PPT_NOTIFICATION_ID)
        manager.cancel(tag, PPT_RESULT_NOTIFICATION_ID)
    }

    internal fun renderTransfer(context: Context, task: TransferActivity) {
        val tag = ActivityLinks.tag(task.owner, task.kind, task.id)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (task.hidden || task.stage in setOf(TransferStage.CANCELED, TransferStage.REMOVED)) {
            clearTransfer(context, task)
            return
        }
        ensureChannel(context)
        val other = if (task.active) PPT_RESULT_NOTIFICATION_ID else PPT_NOTIFICATION_ID
        manager.cancel(tag, other)
        if (!LiveCapabilities.canNotify(context, CHANNEL_ID)) return
        val accent = context.getColor(R.color.live_upload_accent)
        val open = ActivityLinks.open(context, task.owner, task.kind, task.id)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_live_upload).setColor(accent)
            .setContentTitle(task.title).setContentText(task.label)
            .setSubText(if (task.kind == "deck") "班级课件" else "文件传输")
            .setContentIntent(open).setShowWhen(false).setOnlyAlertOnce(true)
        if (task.active) {
            val percent = task.percent.takeIf { task.stage == TransferStage.UPLOADING || task.stage == TransferStage.PROCESSING }
            val hide = ActivityLinks.hide(context, task.owner, task.kind, task.id, task.generation)
            val promote = task.stage == TransferStage.UPLOADING && LiveCapabilities.canPromote(context, CHANNEL_ID)
            val style = NotificationCompat.ProgressStyle().setProgressSegments(listOf(
                NotificationCompat.ProgressStyle.Segment(PROGRESS_MAX).setColor(accent)))
                .setProgress(percent ?: 0).setProgressIndeterminate(percent == null)
            if (percent != null) style.setProgressTrackerIcon(IconCompat.createWithResource(context, R.drawable.ic_live_upload).setTint(accent))
            builder.setStyle(style).setProgress(PROGRESS_MAX, percent ?: 0, percent == null)
                .setOngoing(true).setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setShortCriticalText(percent?.let { "$it%" } ?: "处理中")
                .setRequestPromotedOngoing(promote).setDeleteIntent(hide).addAction(0, "隐藏", hide)
                .setTimeoutAfter((task.createdAt + (if (task.stage in TransferActivities.uploadStages) 24L else 8L) * 3_600_000 - System.currentTimeMillis()).coerceAtLeast(1))
            manager.notify(tag, PPT_NOTIFICATION_ID, if (promote) builder.buildLiveUpdate() else builder.build())
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(task.label)).setAutoCancel(true)
                .setTimeoutAfter(TransferActivities.RETAIN_MS).setCategory(NotificationCompat.CATEGORY_STATUS)
                .addAction(0, if (task.stage == TransferStage.READY) "查看或下载" else "查看详情", open)
            manager.notify(tag, PPT_RESULT_NOTIFICATION_ID, builder.build())
        }
    }

    // ---------------------------------------------------------------- 限时投票

    /** 限时投票倒计时：常驻 + 系统倒计时（无需应用逐秒刷新）。 */
    internal fun postVote(context: Context, watch: VoteWatch) {
        val countdownDeadlineMillis = watch.localDeadline
        val deadlineMillis = watch.deadline
        val pollTitle = watch.title
        val remaining = countdownDeadlineMillis - System.currentTimeMillis()
        if (remaining <= 0L) {
            cancelVote(context, watch.tag)
            return
        }
        if (!canNotify(context)) return
        ensureChannel(context)
        if (watch.hidden || !LiveCapabilities.canNotify(context, CHANNEL_ID)) return
        val hide = ActivityLinks.hide(context, watch.owner, "vote", watch.id, watch.generation)
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
            .setRequestPromotedOngoing(LiveCapabilities.canPromote(context, CHANNEL_ID))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setDeleteIntent(hide)
            .addAction(0, "隐藏本次展示", hide)
            .setContentIntent(ActivityLinks.open(context, watch.owner, "vote", watch.id))
        runCatching {
            NotificationManagerCompat.from(context).notify(watch.tag, VOTE_NOTIFICATION_ID, builder.buildLiveUpdate())
        }
    }

    internal fun cancelVote(context: Context, tag: String?) {
        runCatching { NotificationManagerCompat.from(context).cancel(tag, VOTE_NOTIFICATION_ID) }
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
