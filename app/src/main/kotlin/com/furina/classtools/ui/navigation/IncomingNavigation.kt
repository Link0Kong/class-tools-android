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
) {
    internal fun isFor(session: Session): Boolean =
        (account == null || account == session.navigationAccount) && (issuedAt == null || issuedAt == session.issuedAt)

    internal fun sameDestination(other: IncomingNavigation): Boolean =
        route == other.route && courseFocus == other.courseFocus && account == other.account && issuedAt == other.issuedAt

    internal fun save(): Bundle = Bundle().apply {
        putString("route", route)
        putString("focus", courseFocus)
        putString("account", account)
        putString("id", id)
        issuedAt?.let { putLong("issuedAt", it) }
    }

    internal companion object {
        const val KEY = "incomingNavigation"

        fun restore(state: Bundle): IncomingNavigation? {
            val request = state.getBundle(KEY)
            if (request != null) {
                val route = request.getString("route") ?: return null
                val id = request.getString("id") ?: return null
                val issuedAt = request.getLong("issuedAt").takeIf { request.containsKey("issuedAt") }
                return IncomingNavigation(route, request.getString("focus"), request.getString("account"), id, issuedAt)
            }
            val route = state.getString("pendingCardRoute") ?: return null
            val base = route.substringBefore('?')
            return if (base == Routes.tool("tt")) IncomingNavigation(base, Uri.parse(route).getQueryParameter(Routes.FOCUS_ARG))
            else IncomingNavigation(route)
        }
    }
}

internal val Session.navigationAccount: String get() = widgetOwner
