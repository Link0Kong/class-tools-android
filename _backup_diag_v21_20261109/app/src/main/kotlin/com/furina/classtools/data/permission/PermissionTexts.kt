package com.furina.classtools.data.permission

/**
 * 权限说明文案（通知/日历）：系统授权框前的解释、永久拒绝后的设置引导。
 *
 * 口径：不夸大用途、不诱导；明确“可随时关闭/拒绝”；
 * 与隐私政策（`ui/screens/privacy/PrivacyTexts.kt`）一致——权限只用于本地功能，不上传。
 */
data class PermissionCopy(
    val title: String,
    val message: String,
    val rationale: String,
    val settingsTitle: String,
    val settingsMessage: String,
    val confirmLabel: String,
    val cancelLabel: String = "暂不",
)

object PermissionTexts {

    val notifications: PermissionCopy = PermissionCopy(
        title = "开启作业与上课提醒",
        message = "允许通知后，App 会在作业截止前、上课前发送本机提醒；不申请其他通知用途。",
        rationale = "提醒需要通知权限才能显示在通知栏；你可以只开启「作业提醒」或「课表提醒」，也可随时在系统设置中关闭。",
        settingsTitle = "通知权限已被关闭",
        settingsMessage = "请到系统设置 → 应用 → 班级工具站 → 通知 中允许通知，然后返回重试。",
        confirmLabel = "去开启",
    )

    val calendar: PermissionCopy = PermissionCopy(
        title = "写入系统日历",
        message = "允许后，可将课表/有时间的通知写入本机日历，并由系统日历提醒；不会上传日历数据。",
        rationale = "写日程需要读取并写入本机日历权限（仅用于创建你主动添加的课程/通知日程）；选择“不授权”时会改用系统日历的插入界面由你确认。",
        settingsTitle = "日历权限已被关闭",
        settingsMessage = "请到系统设置 → 应用 → 班级工具站 → 权限 → 日历 中允许读写日历，然后返回重试。",
        confirmLabel = "去开启",
    )

    fun of(type: PermissionType): PermissionCopy = when (type) {
        PermissionType.NOTIFICATIONS -> notifications
        PermissionType.CALENDAR -> calendar
    }

    /** 通知渠道说明（系统设置里展示的渠道名/描述）。 */
    const val CHANNEL_HOMEWORK_ID: String = "homework_due"
    const val CHANNEL_HOMEWORK_NAME: String = "作业提醒"
    const val CHANNEL_HOMEWORK_DESC: String = "作业截止前与每日截止汇总"

    const val CHANNEL_CLASS_ID: String = "class_soon"
    const val CHANNEL_CLASS_NAME: String = "上课提醒"
    const val CHANNEL_CLASS_DESC: String = "下一节课开始前的提醒"
}
