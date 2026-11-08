package com.furina.classtools.data.reminder

import java.time.Instant
import java.time.ZoneId

/**
 * 本机提醒设置（DataStore；不进云端 userdata，属于设备偏好）。
 *
 * 与网页/小程序口径：
 * - 微信小程序用订阅消息（一次性授权、云端发），Android 用**本机通知**，无需云端权限；
 * - 默认只开作业/课表两类提醒；用户可在「我的 → 提醒」关闭/调整提前时间；
 * - 作业提前档位 0/1/3 天，课表提前 5/10/15 分钟（与设计稿/任务口径一致）。
 */
data class ReminderSettings(
    /** 总开关（关闭后不调度、不发任何提醒）。 */
    val enabled: Boolean = true,
    /** 作业提醒（每日 20:00 截止汇总 + 截止前 N 天提醒）。 */
    val homeworkEnabled: Boolean = true,
    /** 课表提醒（下一节课开始前 N 分钟）。 */
    val courseEnabled: Boolean = true,
    /** 通知提醒（有活动/截止时间的班级通知：每日汇总 + 临近提醒；对齐 mp ntremind）。 */
    val noticeEnabled: Boolean = true,
    /** 每日作业汇总时间（本地时区，默认 20:00）。 */
    val homeworkDigestHour: Int = 20,
    val homeworkDigestMinute: Int = 0,
    /** 作业截止提前天数（0/1/3；默认 1 天）。 */
    val homeworkLeadDays: Int = 1,
    /** 课表提前分钟（5/10/15；默认 15 分钟）。 */
    val courseLeadMinutes: Int = 15,
    /** 安静时段（默认关闭；开启后该时段内到点的提醒不弹，等下次调度窗口）。 */
    val quietHoursEnabled: Boolean = false,
    val quietStartHour: Int = 23,
    val quietEndHour: Int = 7,
) {
    /** 字段规范化（DataStore 读到脏值时用）。 */
    fun sanitized(): ReminderSettings = copy(
        homeworkDigestHour = homeworkDigestHour.coerceIn(0, 23),
        homeworkDigestMinute = homeworkDigestMinute.coerceIn(0, 59),
        homeworkLeadDays = homeworkLeadDays.coerceIn(MIN_HOMEWORK_LEAD_DAYS, MAX_HOMEWORK_LEAD_DAYS),
        courseLeadMinutes = courseLeadMinutes.coerceIn(MIN_COURSE_LEAD_MINUTES, MAX_COURSE_LEAD_MINUTES),
        quietStartHour = quietStartHour.coerceIn(0, 23),
        quietEndHour = quietEndHour.coerceIn(0, 23),
    )

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

    /** 任何一类提醒是否实际开启。 */
    val anySectionEnabled: Boolean
        get() = enabled && (homeworkEnabled || courseEnabled || noticeEnabled)

    companion object {
        val DEFAULT: ReminderSettings = ReminderSettings()

        val HOMEWORK_LEAD_DAY_OPTIONS: List<Int> = listOf(0, 1, 3)
        val COURSE_LEAD_MINUTE_OPTIONS: List<Int> = listOf(5, 10, 15)

        const val MIN_HOMEWORK_LEAD_DAYS: Int = 0
        const val MAX_HOMEWORK_LEAD_DAYS: Int = 3
        const val MIN_COURSE_LEAD_MINUTES: Int = 5
        const val MAX_COURSE_LEAD_MINUTES: Int = 15
    }
}

/** 提醒种类（决定通知渠道与点击跳转工具）。 */
enum class ReminderKind(
    val toolId: String,
    val channelId: String,
    val channelName: String,
    val channelDesc: String,
    /** true = 渠道高重要性（课程/临近类，尽量即时弹出）。 */
    val highPriority: Boolean = false,
) {
    /** 每日作业截止汇总（“明天/后天截止”）。 */
    HOMEWORK_DIGEST("hw", "homework_due", "作业提醒", "作业截止前与每日截止汇总"),

    /** 单条作业截止前 N 天提醒。 */
    HOMEWORK_DUE("hw", "homework_due", "作业提醒", "作业截止前与每日截止汇总"),

    /** 下一节课开始前提醒。 */
    COURSE_SOON("tt", "class_soon", "上课提醒", "下一节课开始前的提醒", highPriority = true),

    /** 每日通知汇总（“明天/后天截止/开始”的未处理通知）。 */
    NOTICE_DIGEST("nt", "notice_due", "通知提醒", "通知截止/活动汇总与临近提醒"),

    /** 单条通知临近（24 小时内到截止/活动时间）提醒。 */
    NOTICE_DUE("nt", "notice_due", "通知提醒", "通知截止/活动汇总与临近提醒", highPriority = true),
}

/** 一条待发的本机提醒。 */
data class ReminderEvent(
    val kind: ReminderKind,
    /** 去重键（同 id 当日不重复）：`hw-digest:<date>` / `hw-due:<id>` / `class:<startAt>`。 */
    val key: String,
    val triggerAtMillis: Long,
    val title: String,
    val body: String,
    /** 点击跳转工具 id（与 `Routes.tool(id)` 一致：hw/tt）。 */
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

/** 一次 Worker 运行的结果（不参与 UI；用于日志/测试/诊断）。 */
data class ReminderRunResult(
    val sent: Int = 0,
    val skipped: Int = 0,
    val candidates: Int = 0,
    val disabledReason: String? = null,
) {
    val enabled: Boolean get() = disabledReason == null
}
