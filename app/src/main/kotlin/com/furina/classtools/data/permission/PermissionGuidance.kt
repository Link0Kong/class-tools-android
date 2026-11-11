package com.furina.classtools.data.permission

/**
 * 权限/后台限制的用户引导文案（纯函数，JVM 单测可覆盖）。
 *
 * 口径（v3.7 通知可靠性）：
 * - 通知权限四态（已授予 / 未请求 / 拒绝过 / 永久拒绝）分别给不同动作，不诱导、不夸大；
 * - 电池优化/自启动只给**说明与系统设置入口**，不主动弹系统豁免弹窗（不申请
 *   `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 权限，避免商店审核与隐私争议）；
 * - 如实说明「无厂商推送/FCM 时无法实时秒达，周期轮询系统下限约 15 分钟」。
 */
data class NotificationPermissionGuide(
    /** 状态胶囊文案：已开启 / 未开启 / 需系统设置。 */
    val statusLabel: String,
    /** 用户可点的动作文案；null = 无需动作。 */
    val actionLabel: String?,
    /** 解释文案（1~2 句，说明用途与后台提醒的关系）。 */
    val message: String,
    /** 是否可再次弹系统授权框。 */
    val canRequest: Boolean,
    /** 是否只能去系统设置页开启。 */
    val needsSettings: Boolean,
)

object PermissionGuidance {

    /** 后台周期轮询间隔（系统下限，分钟）—— Diagnostics/文案共用，避免多处硬编码。 */
    const val BACKGROUND_INTERVAL_MINUTES: Long = 15L

    fun notification(state: PermissionState): NotificationPermissionGuide = when (state) {
        PermissionState.GRANTED -> NotificationPermissionGuide(
            statusLabel = "已开启",
            actionLabel = null,
            message = "提醒已可送达。App 关闭或在后台时，约每 15 分钟联网检查一次新通知与作业截止；" +
                "被系统省电/厂商限制时可能延迟。",
            canRequest = false,
            needsSettings = false,
        )

        PermissionState.REQUESTABLE -> NotificationPermissionGuide(
            statusLabel = "未开启",
            actionLabel = "开启通知权限",
            message = "开启后才能在 App 关闭/后台时收到新通知、作业截止与上课提醒；" +
                "仅用于本机通知，不会上传其他数据。",
            canRequest = true,
            needsSettings = false,
        )

        PermissionState.DENIED -> NotificationPermissionGuide(
            statusLabel = "未开启",
            actionLabel = "重新授权",
            message = "通知权限被拒绝，当前收不到任何提醒；可点「重新授权」再次请求，" +
                "或在系统设置中打开通知。",
            canRequest = true,
            needsSettings = false,
        )

        PermissionState.PERMANENTLY_DENIED -> NotificationPermissionGuide(
            statusLabel = "需系统设置",
            actionLabel = "去系统设置开启",
            message = "通知已被永久关闭，App 无法再弹授权框；请到系统设置 → 应用 → 班级工具站 → 通知 中允许通知，" +
                "并确认对应渠道未被关闭。",
            canRequest = false,
            needsSettings = true,
        )
    }

    /** 电池优化说明（诊断页展示；不强制豁免）。 */
    fun batteryOptimization(ignoringBatteryOptimizations: Boolean): String =
        if (ignoringBatteryOptimizations) {
            "电池优化：已允许后台运行，周期检查通常能准时执行。"
        } else {
            "电池优化：本应用仍受系统省电限制（Doze），息屏/长时间未使用后提醒可能延迟到系统允许的窗口；" +
                "可在 系统设置 → 电池 → 电池优化 中把本应用设为「不受限制」（非必需）。"
        }

    /** 厂商自启动/后台运行说明（小米/华为/OPPO/vivo 等）。 */
    fun autoStartHint(): String =
        "厂商后台限制：小米（自启动/省电策略）、华为（应用启动管理）、OPPO/vivo（后台高耗电/自启动）等需在系统设置中" +
            "允许本应用自启动与后台运行，否则重启/清理后台后可能收不到提醒。"

    /** 精确闹钟说明（Android 12+ 特殊权限；未授权时单条提醒回退 WorkManager）。 */
    fun exactAlarm(canScheduleExactAlarms: Boolean): String =
        if (canScheduleExactAlarms) {
            "精确闹钟：已授权，单条提醒（截止前 1 天/1 小时等）会按设定时刻触发。"
        } else {
            "精确闹钟：未授权（Android 12+「闹钟和提醒」权限），单条提醒会回退到 WorkManager 近似调度，" +
                "可能延迟数分钟到数十分钟；可在 系统设置 → 应用 → 特殊应用权限 → 闹钟和提醒 中授权。"
        }

    /** 硬限制如实口径（设置页/文档共用）。 */
    fun backgroundLimits(intervalMinutes: Long = BACKGROUND_INTERVAL_MINUTES): String =
        "后台检查依赖系统调度：无厂商推送/FCM 时无法做到实时秒达；周期任务系统下限约 " +
            "${intervalMinutes.coerceAtLeast(BACKGROUND_INTERVAL_MINUTES)} 分钟，息屏/省电/厂商清理时会进一步延迟。" +
            "打开 App 会立即刷新。"
}
