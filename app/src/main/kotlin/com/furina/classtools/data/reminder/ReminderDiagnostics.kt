package com.furina.classtools.data.reminder

import android.content.Context
import com.furina.classtools.data.permission.PermissionState
import com.furina.classtools.data.permission.PermissionType

/**
 * 通知系统 v3 可观测性组件：
 * - [ReminderRunStateStore]：记录上次运行时间/摘要（Doze 延迟补偿 + 诊断页）；
 * - [ReminderCatchUp]：根据上次运行时间计算「补发回看窗口」；
 * - [ReminderDiagnostics]：设置页「通知诊断」用（权限/设置/缓存/上次运行摘要）；
 * - [ReminderLog]：Worker logcat 摘要（单行、不含隐私数据）。
 *
 * 全部纯逻辑/接口，可单测；Android 实现只有 [SharedPrefsReminderRunStateStore]。
 */

/** 运行状态存储（SharedPreferences；进程重启保留）。 */
interface ReminderRunStateStore {
    suspend fun lastRunAt(): Long?

    suspend fun lastRunSummary(): String?

    suspend fun recordRun(atMillis: Long, summary: String)
}

class InMemoryReminderRunStateStore(
    initialLastRunAt: Long? = null,
    initialSummary: String? = null,
) : ReminderRunStateStore {
    private var lastAt: Long? = initialLastRunAt
    private var lastSummary: String? = initialSummary

    override suspend fun lastRunAt(): Long? = lastAt

    override suspend fun lastRunSummary(): String? = lastSummary

    override suspend fun recordRun(atMillis: Long, summary: String) {
        lastAt = atMillis
        lastSummary = summary
    }
}

class SharedPrefsReminderRunStateStore(context: Context, owner: String = "") : ReminderRunStateStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(if (owner.isEmpty()) PREFS_NAME else "${PREFS_NAME}_$owner", Context.MODE_PRIVATE)

    override suspend fun lastRunAt(): Long? =
        prefs.getLong(KEY_LAST_RUN, 0L).takeIf { it > 0L }

    override suspend fun lastRunSummary(): String? =
        prefs.getString(KEY_LAST_SUMMARY, null)?.takeIf { it.isNotBlank() }

    override suspend fun recordRun(atMillis: Long, summary: String) {
        prefs.edit()
            .putLong(KEY_LAST_RUN, atMillis)
            .putString(KEY_LAST_SUMMARY, summary.take(MAX_SUMMARY_LENGTH))
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "ct_reminder_run_state"
        const val KEY_LAST_RUN = "last_run_at"
        const val KEY_LAST_SUMMARY = "last_run_summary"
        const val MAX_SUMMARY_LENGTH = 240
    }
}

/** Doze/省电延迟补偿：按上次运行时间计算回看窗口（10 分钟 ~ 6 小时）。 */
object ReminderCatchUp {

    const val DEFAULT_GRACE_MS: Long = 10L * 60 * 1000

    /** 最大回看 6 小时：超过则不补发（避免重启后轰炸旧提醒）。 */
    const val MAX_LOOKBACK_MS: Long = 6L * 60 * 60 * 1000

    fun lookbackMillis(
        nowMillis: Long,
        lastRunAtMillis: Long?,
        graceMillis: Long = DEFAULT_GRACE_MS,
        maxLookbackMillis: Long = MAX_LOOKBACK_MS,
    ): Long {
        val grace = graceMillis.coerceAtLeast(0L)
        val max = maxLookbackMillis.coerceAtLeast(grace)
        val last = lastRunAtMillis
        if (last == null || last <= 0L || last > nowMillis) return grace
        return (nowMillis - last).coerceIn(grace, max)
    }

    /** 事件是否属于「错过补发」（早于正常 grace 窗口但落在补发回看窗口内）。 */
    fun isCatchUp(
        triggerAtMillis: Long,
        nowMillis: Long,
        graceMillis: Long = DEFAULT_GRACE_MS,
    ): Boolean = triggerAtMillis < nowMillis - graceMillis &&
        triggerAtMillis >= nowMillis - MAX_LOOKBACK_MS
}

/** 通知系统诊断快照（设置页展示；不含通知正文/学号等隐私数据）。 */
data class ReminderDiagnostics(
    val checkedAtMillis: Long = 0L,
    val permission: PermissionState = PermissionState.REQUESTABLE,
    val settings: ReminderSettings = ReminderSettings.DEFAULT,
    val lastRunAtMillis: Long? = null,
    val lastRunSummary: String? = null,
    val lastRunResult: ReminderRunResult? = null,
    /** 本机缓存摘要（作业/通知/投票条数、课表/水量是否可用）。 */
    val homeworkCacheCount: Int = 0,
    val noticeCacheCount: Int = 0,
    val voteCacheCount: Int = 0,
    val waterReady: Boolean = false,
    val timetableReady: Boolean = false,
    /** 通知渠道 id 与专用小图标回溯名（由 Notifier 侧常量提供）。 */
    val channelIds: List<String> = ReminderKind.entries.map { it.channelId }.distinct(),
    val smallIconName: String = ReminderSmallIcon.RES_NAME,
    /** 小图标是否为系统占位（true 表示 Lead 尚未加专用 drawable）。 */
    val smallIconFallback: Boolean = false,
    /** 补发回看窗口（毫秒），展示用。 */
    val catchUpLookbackMillis: Long = ReminderCatchUp.DEFAULT_GRACE_MS,
) {
    val settingsSummary: String
        get() = buildString {
            append("总开关=").append(if (settings.enabled) "开" else "关")
            append("；模块=").append(settings.enabledCount()).append("/10 开")
            append("；截止提前=").append(ReminderSettings.leadLabel(settings.leadMinutes))
            append("；上课提前=").append(ReminderSettings.leadLabel(settings.classLeadMinutes))
        }

    val cacheSummary: String
        get() = "作业 $homeworkCacheCount 条 · 课表${if (timetableReady) "可用" else "无"} · " +
            "通知 $noticeCacheCount 条 · 投票 $voteCacheCount 条 · 水量${if (waterReady) "可用" else "无"}"
}

/** Worker/诊断用日志摘要（不包含通知标题/正文/学号）。 */
object ReminderLog {

    const val TAG: String = "Reminder"

    fun summary(result: ReminderRunResult): String = "[CT_REMINDER] " + result.logSummary()

    /** 设置变更/请求立即运行时的人工触发日志（不含具体设置值以外的隐私）。 */
    fun manualTrigger(reason: String): String = "[CT_REMINDER] manual run requested: $reason"
}

/** 通知小图标解析：优先专用单色 drawable `ic_stat_class_tools`，缺失回退系统占位。 */
object ReminderSmallIcon {

    const val RES_NAME: String = "ic_stat_class_tools"

    /** 系统占位图标（Lead 加专用 drawable 前使用）。 */
    val FALLBACK_RES: Int = android.R.drawable.ic_dialog_info

    /** 可注入的资源查找（测试用）；返回 0/null 视为缺失。 */
    fun resolve(lookup: (String) -> Int?): Int {
        val found = lookup(RES_NAME)
        return if (found != null && found != 0) found else FALLBACK_RES
    }

    fun isFallback(resolved: Int): Boolean = resolved == FALLBACK_RES
}

/** UI 便捷入口（设置页诊断/测试按钮；内部构造 [ReminderEngine] 并调用 suspend API）。 */
object ReminderActions {

    /** 设置页「通知诊断」：权限/设置/缓存/上次运行摘要。 */
    suspend fun diagnostics(context: Context): ReminderDiagnostics = ReminderEngine(context).diagnostics()

    /** 设置页「发送测试通知」：立即发一条固定内容的本地通知。 */
    suspend fun testNotification(context: Context): ReminderTestNotificationResult =
        ReminderEngine(context).testNotification()
}
