package com.furina.classtools.data.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * 提醒点击 → 打开 App 指定工具。
 *
 * 约定（Lead 在 `MainActivity` / `MainNavHost` 接线）：
 * - 通知 PendingIntent 以 `ACTION_MAIN` 启动 Launcher Activity（singleTop），并携带 extras：
 *   [EXTRA_TOOL_ID]（`"hw"` / `"tt"` / `"nt"`）、[EXTRA_REMINDER_KEY]、[EXTRA_REMINDER_KIND]；
 * - Activity 取到 [EXTRA_TOOL_ID] 后 `navController.navigate(Routes.tool(toolId))`（或首页 quick card 打开），
 *   然后清除 intent（避免旋转/返回重复跳转）；
 * - 若 App 尚未登录，先进入身份页，登录后再按 extras 跳转（Lead 处理会话恢复后的 pending route）。
 */
object ReminderIntentFactory {

    const val EXTRA_TOOL_ID: String = "ct.reminder.toolId"
    const val EXTRA_REMINDER_KEY: String = "ct.reminder.key"
    const val EXTRA_REMINDER_KIND: String = "ct.reminder.kind"

    /** kind → 工具 id（与 `ToolCatalog` 的 `hw`/`tt` 一致；供纯函数测试/UI 使用）。 */
    fun toolIdFor(kind: ReminderKind): String = kind.toolId

    /** 读取 Intent 中的工具 id（仅接受已知提醒工具，防脏数据）。 */
    fun toolIdFromExtras(toolId: String?): String? =
        toolId?.trim()?.takeIf { it == "hw" || it == "tt" || it == "nt" }

    /** 构造打开 App 的 Intent（无 Launcher Activity 时返回 null）。 */
    fun openIntent(context: Context, event: ReminderEvent): Intent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return launch.apply {
            addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK,
            )
            putExtra(EXTRA_TOOL_ID, event.toolId)
            putExtra(EXTRA_REMINDER_KEY, event.key)
            putExtra(EXTRA_REMINDER_KIND, event.kind.name)
        }
    }

    /** 通知点击 PendingIntent（同 key 覆盖，不堆叠）。 */
    fun pendingIntent(context: Context, event: ReminderEvent): PendingIntent? {
        val intent = openIntent(context, event) ?: return null
        return PendingIntent.getActivity(
            context,
            event.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
