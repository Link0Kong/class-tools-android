package com.furina.classtools.ui.navigation

import android.net.Uri
import android.os.Bundle
import com.furina.classtools.data.session.Session
import com.furina.classtools.widget.widgetOwner
import java.util.UUID

data class IncomingNavigation(
    val route: String,
    val courseFocus: String? = null,
    val account: String? = null,
    val id: String = UUID.randomUUID().toString(),
    val issuedAt: Long? = null,
    val activityParent: String? = null,
    /** 任务分享链接（`/l/task-share#s=...`）：内容在片段里，交给任务页预览后确认导入。 */
    val taskShare: String? = null,
) {
    internal fun isFor(session: Session): Boolean =
        (account == null || account == session.navigationAccount) && (issuedAt == null || issuedAt == session.issuedAt)

    internal fun sameDestination(other: IncomingNavigation): Boolean =
        route == other.route && courseFocus == other.courseFocus && account == other.account &&
            issuedAt == other.issuedAt && activityParent == other.activityParent && taskShare == other.taskShare

    internal fun save(): Bundle = Bundle().apply {
        putString("route", route)
        putString("activityParent", activityParent)
        putString("focus", courseFocus)
        putString("account", account)
        putString("id", id)
        putString("share", taskShare)
        issuedAt?.let { putLong("issuedAt", it) }
    }

    internal companion object {
        const val KEY = "incomingNavigation"

        /** 任务分享载荷投递键（savedStateHandle）：导航参数里不放 8KB 快照。 */
        const val TASK_SHARE_DELIVERY = "taskShareDelivery"

        fun restore(state: Bundle): IncomingNavigation? {
            val request = state.getBundle(KEY)
            if (request != null) {
                val route = request.getString("route") ?: return null
                val id = request.getString("id") ?: return null
                val issuedAt = request.getLong("issuedAt").takeIf { request.containsKey("issuedAt") }
                return IncomingNavigation(
                    route, request.getString("focus"), request.getString("account"), id, issuedAt,
                    activityParent = request.getString("activityParent"),
                    taskShare = request.getString("share"),
                )
            }
            val route = state.getString("pendingCardRoute") ?: return null
            val base = route.substringBefore('?')
            return if (base == Routes.tool("tt")) IncomingNavigation(base, Uri.parse(route).getQueryParameter(Routes.FOCUS_ARG))
            else IncomingNavigation(route)
        }
    }
}

internal val Session.navigationAccount: String get() = widgetOwner
