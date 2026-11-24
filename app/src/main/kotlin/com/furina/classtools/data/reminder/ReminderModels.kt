package com.furina.classtools.data.reminder

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

/**
 * 本机通知设置（DataStore；不进云端 userdata，属于设备偏好）。
 *
 * 口径与鸿蒙端 `features/notify/NotifySettings.ets` 对齐（2026-11-21 用户要求「参考鸿蒙端增加安卓端的系统通知工具与内容」）：
 * - **总开关** [enabled] + 9 个模块开关：新作业 / 作业即将截止 / 上课 / 新通知 / 活动截止 / 活动开抢 / 新投票 / 投票结果 / 水费扣除；
 * - 截止类提醒统一提前 [leadMinutes]（10/30/60/120/1440 分钟，默认 60）；
 * - 上课提醒提前 [classLeadMinutes]（5/10/15/30 分钟，默认 10）；
 * - 安卓额外保留「每日汇总」[digestEnabled]（默认关闭）：开启后每天 20:00 汇总未来 48 小时截止的作业/通知。
 *
 * 与微信小程序口径：小程序用订阅消息（一次性授权、云端发），Android 全部**本机通知**（WorkManager），
 * 不联网、不上传、不需要云端权限。
 */
data class ReminderSettings(
    /** 总开关（关闭后不调度、不发任何提醒）。 */
    val enabled: Boolean = true,
    /** 新作业通知：有新的作业发布时提醒。 */
    val homeworkNewEnabled: Boolean = true,
    /** 作业即将截止：按 [leadMinutes] 在截止前提醒。 */
    val homeworkDueEnabled: Boolean = true,
    /** 上课提醒：按课表在课前 [classLeadMinutes] 分钟提醒。 */
    val classRemindEnabled: Boolean = true,
    /** 新通知提醒：班委发布新通知时提醒。 */
    val noticeNewEnabled: Boolean = true,
    /** 活动截止提醒：活动/报名截止前提醒。 */
    val noticeDeadlineEnabled: Boolean = true,
    /** 活动开抢提醒：报名开始时提前 5 分钟提醒。 */
    val noticeSignupEnabled: Boolean = true,
    /** 新投票提醒：发起新投票时提醒。 */
    val voteNewEnabled: Boolean = true,
    /** 投票结果可查看：投票结束后提醒查看结果。 */
    val voteResultEnabled: Boolean = true,
    /** 水费扣除提醒：抄表产生用量/水费时提醒。 */
    val waterDeductEnabled: Boolean = true,
    /** 每日汇总（安卓保留项，默认开启）：每天 [homeworkDigestHour]:[homeworkDigestMinute] 汇总未来 48 小时。 */
    val digestEnabled: Boolean = true,
    /** 截止类提醒统一提前分钟数（10/30/60/120/1440）。 */
    val leadMinutes: Int = DEFAULT_LEAD_MINUTES,
    /** 上课提醒提前分钟数（5/10/15/30）。 */
    val classLeadMinutes: Int = DEFAULT_CLASS_LEAD_MINUTES,
    /** 每日汇总时间（本地时区，默认 20:00）。 */
    val homeworkDigestHour: Int = 20,
    val homeworkDigestMinute: Int = 0,
    /** 安静时段（默认关闭；开启后该时段内到点的提醒不弹，等下次调度窗口）。 */
    val quietHoursEnabled: Boolean = false,
    val quietStartHour: Int = 23,
    val quietEndHour: Int = 7,
) {
    /** 字段规范化（DataStore 读到脏值时用）。 */
    fun sanitized(): ReminderSettings = copy(
        leadMinutes = normalizeLead(leadMinutes),
        classLeadMinutes = normalizeClassLead(classLeadMinutes),
        homeworkDigestHour = homeworkDigestHour.coerceIn(0, 23),
        homeworkDigestMinute = homeworkDigestMinute.coerceIn(0, 59),
        quietStartHour = quietStartHour.coerceIn(0, 23),
        quietEndHour = quietEndHour.coerceIn(0, 23),
    )

    /** 截止类提醒提前毫秒。 */
    val leadMillis: Long get() = normalizeLead(leadMinutes) * 60_000L

    /** 上课提醒提前毫秒。 */
    val classLeadMillis: Long get() = normalizeClassLead(classLeadMinutes) * 60_000L

    /** 该时间点是否处于安静时段（跨午夜支持：23–7）。 */
    fun isQuiet(triggerAtMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (!quietHoursEnabled) return false
        val hour = Instant.ofEpochMilli(triggerAtMillis).atZone(zone).hour
        return if (quietStartHour == quietEndHour) {
            false
        } else if (quietStartHour < quietEndHour) {
            hour in quietStartHour until quietEndHour
        } else {
            hour >= quietStartHour || hour < quietEndHour
        }
    }

    /** 任一通知模块是否开启（不含总开关）。 */
    val anyModuleEnabled: Boolean
        get() = homeworkNewEnabled || homeworkDueEnabled || classRemindEnabled ||
            noticeNewEnabled || noticeDeadlineEnabled || noticeSignupEnabled ||
            voteNewEnabled || voteResultEnabled || waterDeductEnabled || digestEnabled

    /** 是否有任何一类提醒实际开启（总开关 + 至少一个模块）。 */
    val anySectionEnabled: Boolean
        get() = enabled && anyModuleEnabled

    /** 已开启模块数（设置页/我的页展示「已开启 N 项」）。 */
    fun enabledCount(): Int = listOf(
        homeworkNewEnabled,
        homeworkDueEnabled,
        classRemindEnabled,
        noticeNewEnabled,
        noticeDeadlineEnabled,
        noticeSignupEnabled,
        voteNewEnabled,
        voteResultEnabled,
        waterDeductEnabled,
        digestEnabled,
    ).count { it }

    companion object {
        val DEFAULT: ReminderSettings = ReminderSettings()

        /** 截止类提前档位（分钟；1440 = 1 天）。 */
        val LEAD_OPTIONS: List<Int> = listOf(10, 30, 60, 120, 1440)

        /** 上课提前档位（分钟）。 */
        val CLASS_LEAD_OPTIONS: List<Int> = listOf(5, 10, 15, 30)

        const val DEFAULT_LEAD_MINUTES: Int = 60
        const val DEFAULT_CLASS_LEAD_MINUTES: Int = 10

        /** 规范化截止提前（非法值回退默认 60）。 */
        fun normalizeLead(value: Int): Int =
            if (value in LEAD_OPTIONS) value else DEFAULT_LEAD_MINUTES

        /** 规范化上课提前（非法值回退默认 10）。 */
        fun normalizeClassLead(value: Int): Int =
            if (value in CLASS_LEAD_OPTIONS) value else DEFAULT_CLASS_LEAD_MINUTES

        /** 提前分钟 → 人类可读（10 分钟 / 1 小时 / 1 天）。 */
        fun leadLabel(minutes: Int): String = when {
            minutes >= 1440 -> "${minutes / 1440} 天"
            minutes >= 60 -> "${minutes / 60} 小时"
            else -> "$minutes 分钟"
        }
    }
}

/** 提醒种类（决定通知渠道、通知分组与点击跳转工具）。 */
enum class ReminderKind(
    val toolId: String,
    val channelId: String,
    val channelName: String,
    val channelDesc: String,
    /** true = 渠道高重要性（课程/临近类，尽量即时弹出）。 */
    val highPriority: Boolean = false,
) {
    /** 有新作业发布（首轮只建基线，不推送）。 */
    HOMEWORK_NEW("hw", "homework_due", "作业提醒", "新作业发布与作业截止提醒"),

    /** 每日作业截止汇总（「明天/后天截止」；安卓保留项）。 */
    HOMEWORK_DIGEST("hw", "homework_due", "作业提醒", "作业截止前与每日截止汇总"),

    /** 单条作业截止前提醒（按统一提前时间）。 */
    HOMEWORK_DUE("hw", "homework_due", "作业提醒", "作业截止前与每日截止汇总"),
    PERSONAL_TASK("hw", "personal_tasks", "任务提醒", "个人任务的指定提醒时间"),

    /** 下一节课开始前提醒。 */
    COURSE_SOON("tt", "class_soon", "上课提醒", "下一节课开始前的提醒", highPriority = true),

    /** 新通知（班委发布）。 */
    NOTICE_NEW("nt", "notice_due", "通知提醒", "新通知、活动截止与开抢提醒"),

    /** 每日通知汇总（「明天/后天截止/开始」的未处理通知；安卓保留项）。 */
    NOTICE_DIGEST("nt", "notice_due", "通知提醒", "通知截止/活动汇总与临近提醒"),

    /** 单条通知临近截止提醒。 */
    NOTICE_DUE("nt", "notice_due", "通知提醒", "通知截止/活动汇总与临近提醒", highPriority = true),

    /** 活动开抢提醒（报名开始前 5 分钟）。 */
    NOTICE_SIGNUP("nt", "notice_due", "通知提醒", "新通知、活动截止与开抢提醒"),

    /** 新投票提醒。 */
    VOTE_NEW("vote", "vote_alert", "投票提醒", "新投票与投票结果可查看"),

    /** 投票结果可查看（投票结束后）。 */
    VOTE_RESULT("vote", "vote_alert", "投票提醒", "新投票与投票结果可查看"),

    /** 水费扣除提醒（本机水量记录产生用量/水费）。 */
    WATER_DEDUCT("water", "water_alert", "水费提醒", "抄表产生用量/水费时提醒"),

    /** 「发送测试通知」工具（验证系统权限与通知样式；不属于任何业务模块）。 */
    SYSTEM_TEST("", "ct_test", "通知测试", "验证系统通知权限与通知样式", highPriority = true),
}

/** 一条待发的本机提醒。 */
data class ReminderEvent(
    val kind: ReminderKind,
    /** 去重键（同 id 当日不重复）：`hw-due:<id>` / `class:<startAt>` / `vote-new:<id>` … */
    val key: String,
    val triggerAtMillis: Long,
    val title: String,
    val body: String,
    /** 点击跳转工具 id（与 `Routes.tool(id)` 一致：hw/tt/nt/vote/water）。 */
    val toolId: String = kind.toolId,
) {
    /** 稳定的通知 id（同一 key 覆盖而非堆叠）。 */
    val notificationId: Int get() = key.hashCode()
}

/** 一次规划结果（已按触发时间升序、key 去重）。 */
data class ReminderPlan(
    val events: List<ReminderEvent>,
) {
    val isEmpty: Boolean get() = events.isEmpty()

    /** 仅保留 [now, now+horizon] 内到点的（Worker 每 15 分钟跑一次时使用）。 */
    fun dueBy(
        nowMillis: Long,
        horizonMillis: Long = DEFAULT_HORIZON_MS,
        graceMillis: Long = DEFAULT_GRACE_MS,
    ): List<ReminderEvent> = events.filter { event ->
        event.triggerAtMillis in (nowMillis - graceMillis)..(nowMillis + horizonMillis)
    }

    companion object {
        const val DEFAULT_HORIZON_MS: Long = 20L * 60 * 1000
        const val DEFAULT_GRACE_MS: Long = 10L * 60 * 1000
    }
}

/**
 * 「新增类」通知基线快照（对齐鸿蒙 `NotifySnapshot`）：
 *
 * 首次运行只建立基线**不推送**；之后对比出新增作业/通知/投票才提醒。
 * 各 id 列表保留最近 [MAX_IDS] 条，避免无限增长；只存 id，不含正文/姓名等隐私。
 */
@Serializable
data class ReminderSnapshot(
    /** false = 尚未建立基线（首轮只记录、不推送）。 */
    val initialized: Boolean = false,
    val hwIds: List<String> = emptyList(),
    val ntIds: List<String> = emptyList(),
    val pollIds: List<String> = emptyList(),
    /** 已结束的投票 id（用于「投票结果可查看」）。 */
    val closedPollIds: List<String> = emptyList(),
    /** 上一次水费提醒指纹：`<roomverify>|<month>|<reading>`。 */
    val waterKey: String = "",
) {
    companion object {
        const val MAX_IDS: Int = 300
    }
}

/** 一次 Worker 运行的结果（不参与 UI；用于日志/测试/诊断）。 */
data class ReminderRunResult(
    val sent: Int = 0,
    val skipped: Int = 0,
    val candidates: Int = 0,
    val disabledReason: String? = null,
    // ---- v3 诊断字段（默认值兼容旧调用/旧测试） ----
    /** 人类可读原因，如「已发送 2 条」「未到提醒时间」「通知权限未开启」。 */
    val reason: String = "",
    /** 本次运行时的通知权限状态。 */
    val permission: String = "",
    /** 设置摘要（不含敏感信息）：如「总开关=开；作业=开；课表=开；通知=开」。 */
    val settingsSummary: String = "",
    /** 本机缓存摘要：作业条数/课表是否可用/通知条数/投票数。 */
    val cacheSummary: String = "",
    /** 本次运行时间（epoch ms）。 */
    val checkedAtMillis: Long = 0L,
    /** 本次补发的错过事件数（Doze/省电延迟补偿）。 */
    val catchUpEvents: Int = 0,
) {
    val enabled: Boolean get() = disabledReason == null

    /** 便于 Worker logcat / 诊断页：单行摘要（不含通知正文/隐私数据）。 */
    fun logSummary(): String = buildString {
        append("sent=").append(sent)
        append(" skipped=").append(skipped)
        append(" candidates=").append(candidates)
        append(" catchUp=").append(catchUpEvents)
        if (permission.isNotBlank()) append(" permission=").append(permission)
        if (reason.isNotBlank()) append(" reason=").append(reason)
        if (disabledReason != null) append(" disabled=").append(disabledReason)
    }
}

/** 「发送测试通知」结果（设置页提示文案按此分支）。 */
sealed interface ReminderTestNotificationResult {
    /** 已发出。 */
    data object Sent : ReminderTestNotificationResult

    /** 系统通知权限未开启（引导用户去权限页）。 */
    data object PermissionMissing : ReminderTestNotificationResult

    /** 发送失败（系统限制/渠道被关闭）。 */
    data class Failed(val message: String) : ReminderTestNotificationResult
}

/**
 * 「发送测试通知」的固定内容（对齐鸿蒙 `NotifyCenter.sendTest`）：
 * 本地立即发一条，用于检查系统权限与通知样式。
 */
object ReminderTestNotification {
    const val TITLE: String = "枫丹工具箱 · 测试通知"
    const val BODY: String = "通知已开启，作业、通知、投票与水费提醒会在这里出现。"

    fun event(nowMillis: Long): ReminderEvent = ReminderEvent(
        kind = ReminderKind.SYSTEM_TEST,
        key = "ct-test-notification",
        triggerAtMillis = nowMillis,
        title = TITLE,
        body = BODY,
    )
}
