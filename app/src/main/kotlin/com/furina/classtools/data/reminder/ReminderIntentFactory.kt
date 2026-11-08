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
 * - Activity 用 [consumeToolId] 读取一次后**移除 extras**（避免旋转/重进用旧通知重复跳转）；
 * - 若 App 尚未登录，先进入身份页，登录后再按 pending route 跳转（Lead 处理）。
 *
 * OEM 兼容（v3 复核）：
 * - `getLaunchIntentForPackage` 在部分 OEM（MIUI/EMUI）可能返回 null → 回退 `ACTION_MAIN + CATEGORY_LAUNCHER + setPackage`；
 * - PendingIntent 固定 `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`（Android 12+ 必须显式可变性）；requestCode = key hash（同事件覆盖，不同事件隔离）；
 * - 不依赖隐式组件（显式包名 + launcher category），避免被省电策略拦截。
 */
object ReminderIntentFactory {

    const val EXTRA_TOOL_ID: String = "ct.reminder.toolId"
    const val EXTRA_REMINDER_KEY: String = "ct.reminder.key"
    const val EXTRA_REMINDER_KIND: String = "ct.reminder.kind"

    /** PendingIntent 标志（UPDATE_CURRENT 保证 extra 最新；IMMUTABLE 满足 Android 12+）。 */
    val PENDING_INTENT_FLAGS: Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    /** kind → 工具 id（与 `ToolCatalog` 的 `hw`/`tt`/`nt` 一致；供纯函数测试/UI 使用）。 */
    fun toolIdFor(kind: ReminderKind): String = kind.toolId

    /** 读取 Intent 中的工具 id（仅接受已知提醒工具，防脏数据）。 */
    fun toolIdFromExtras(toolId: String?): String? =
        toolId?.trim()?.takeIf { it == "hw" || it == "tt" || it == "nt" }

    /** 稳定 requestCode：同一提醒覆盖、不同提醒隔离（OEM 上避免 PendingIntent 复用串数据）。 */
    fun requestCodeFor(key: String): Int = key.hashCode()

    /** 读取并清除 extras（Activity 消费一次；防旋转/重进重复跳转）。 */
    fun consumeToolId(intent: Intent?): String? {
        if (intent == null) return null
        val toolId = toolIdFromExtras(intent.getStringExtra(EXTRA_TOOL_ID))
        intent.removeExtra(EXTRA_TOOL_ID)
        intent.removeExtra(EXTRA_REMINDER_KEY)
        intent.removeExtra(EXTRA_REMINDER_KIND)
        return toolId
    }

    /** 构造打开 App 的 Intent（总是非空：先 launcher intent，OEM 返回 null 时回退显式 launcher）。 */
    fun openIntent(context: Context, event: ReminderEvent): Intent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(context.packageName)
            }
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
    fun pendingIntent(context: Context, event: ReminderEvent): PendingIntent {
        val intent = openIntent(context, event)
        return PendingIntent.getActivity(
            context,
            requestCodeFor(event.key),
            intent,
            PENDING_INTENT_FLAGS,
        )
    }
}
