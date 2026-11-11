package com.furina.classtools.data.reminder

/**
 * 通知渠道规格（纯数据 + 纯函数，JVM 单测可覆盖；Android 创建见 `ReminderChannelInstaller`）。
 *
 * 背景（v3.7 通知可靠性，用户反馈第 12 条）：
 * - 旧实现只在 `notify()` 时才懒创建渠道，且作业/通知汇总渠道是 `IMPORTANCE_DEFAULT`（无横幅）；
 * - 渠道一旦创建，用户升级后系统可能沿用旧重要性（应用无法强制提高）；
 * - 因此 v3.7 新增**全新 id** 的高优先级「新通知」渠道 `notice_inbox_high`（IMPORTANCE_HIGH +
 *   声音/震动/锁屏可见），并统一在 App 启动时提前创建所有渠道。
 *
 * 本文件不 import 任何 Android 类，便于纯 JVM 单测。
 */
data class ReminderChannelSpec(
    val id: String,
    val name: String,
    val description: String,
    /** true = IMPORTANCE_HIGH（横幅 + 声音 + 震动）；false = IMPORTANCE_DEFAULT。 */
    val highImportance: Boolean,
    /** 是否震动（渠道级；系统设置里用户可再关）。 */
    val vibration: Boolean = true,
    /** 锁屏是否显示通知内容（VISIBILITY_PUBLIC）。 */
    val lockscreenPublic: Boolean = true,
)

object ReminderChannels {

    // Android NotificationManager 重要性常量（避免在纯逻辑里 import android 类；数值与系统一致）。
    const val IMPORTANCE_HIGH: Int = 4
    const val IMPORTANCE_DEFAULT: Int = 3
    const val IMPORTANCE_LOW: Int = 2
    const val IMPORTANCE_MIN: Int = 1
    const val IMPORTANCE_NONE: Int = 0

    /** 后台轮询发现「新通知」专用渠道：全新 id，保证升级用户也能拿到 HIGH 重要性。 */
    const val NEW_NOTICE_ID: String = "notice_inbox_high"
    const val NEW_NOTICE_NAME: String = "新通知提醒"
    const val NEW_NOTICE_DESC: String = "后台发现班级新通知时提醒（约 15 分钟检查一次；无网络/被系统限制时可能延迟）"

    /** 每日作业汇总 / 截止提醒（沿用历史渠道 id）。 */
    const val HOMEWORK_ID: String = "homework_due"
    const val HOMEWORK_NAME: String = "作业提醒"
    const val HOMEWORK_DESC: String = "作业截止前与每日截止汇总"

    /** 上课提醒（沿用历史渠道 id，历史实现即 HIGH）。 */
    const val CLASS_ID: String = "class_soon"
    const val CLASS_NAME: String = "上课提醒"
    const val CLASS_DESC: String = "下一节课开始前的提醒"

    /** 通知汇总 / 临近提醒（沿用历史渠道 id，历史实现即 HIGH）。 */
    const val NOTICE_DUE_ID: String = "notice_due"
    const val NOTICE_DUE_NAME: String = "通知提醒"
    const val NOTICE_DUE_DESC: String = "通知截止/活动汇总与临近提醒"

    val NEW_NOTICE: ReminderChannelSpec = ReminderChannelSpec(
        id = NEW_NOTICE_ID,
        name = NEW_NOTICE_NAME,
        description = NEW_NOTICE_DESC,
        highImportance = true,
        vibration = true,
        lockscreenPublic = true,
    )

    val HOMEWORK: ReminderChannelSpec = ReminderChannelSpec(
        id = HOMEWORK_ID,
        name = HOMEWORK_NAME,
        description = HOMEWORK_DESC,
        highImportance = false,
        vibration = true,
        lockscreenPublic = true,
    )

    val CLASS_SOON: ReminderChannelSpec = ReminderChannelSpec(
        id = CLASS_ID,
        name = CLASS_NAME,
        description = CLASS_DESC,
        highImportance = true,
        vibration = true,
        lockscreenPublic = true,
    )

    val NOTICE_DUE: ReminderChannelSpec = ReminderChannelSpec(
        id = NOTICE_DUE_ID,
        name = NOTICE_DUE_NAME,
        description = NOTICE_DUE_DESC,
        highImportance = true,
        vibration = true,
        lockscreenPublic = true,
    )

    /** 全部渠道（App 启动时提前创建；`distinctBy` 防御重复 id）。 */
    val ALL: List<ReminderChannelSpec> = listOf(HOMEWORK, CLASS_SOON, NOTICE_DUE, NEW_NOTICE)
        .distinctBy { it.id }

    /** 提醒类型 → 渠道规格（历史语义保持不变；新通知专用渠道由 [ReminderEvent.channelIdOverride] 指定）。 */
    fun specFor(kind: ReminderKind): ReminderChannelSpec = when (kind) {
        ReminderKind.HOMEWORK_DIGEST, ReminderKind.HOMEWORK_DUE -> HOMEWORK
        ReminderKind.COURSE_SOON -> CLASS_SOON
        ReminderKind.NOTICE_DIGEST, ReminderKind.NOTICE_DUE -> NOTICE_DUE
    }

    /** 实际使用的渠道 id：事件显式覆盖优先（后台新通知走 [NEW_NOTICE_ID]）。 */
    fun channelIdFor(event: ReminderEvent): String =
        event.channelIdOverride?.takeIf { it.isNotBlank() } ?: specFor(event.kind).id

    fun specOf(channelId: String): ReminderChannelSpec? = ALL.firstOrNull { it.id == channelId }

    /** 诊断展示：渠道重要性 → 中文文案（纯函数）。 */
    fun importanceLabel(importance: Int?): String = when (importance) {
        null -> "未创建"
        IMPORTANCE_HIGH -> "高（横幅/声音/震动）"
        IMPORTANCE_DEFAULT -> "普通（无横幅）"
        IMPORTANCE_LOW -> "低（无声音）"
        IMPORTANCE_MIN -> "最低"
        IMPORTANCE_NONE -> "已关闭"
        else -> "未知（$importance）"
    }

    /** 诊断单行：`新通知提醒：高（横幅/声音/震动）`。 */
    fun channelStatusLine(spec: ReminderChannelSpec, importance: Int?): String =
        "${spec.name}：${importanceLabel(importance)}"
}
