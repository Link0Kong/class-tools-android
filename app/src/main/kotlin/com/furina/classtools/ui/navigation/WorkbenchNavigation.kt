package com.furina.classtools.ui.navigation

import android.os.Bundle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController

internal const val TIMETABLE_DELIVERY = "timetableDelivery"

/** Older main hosts could start at a tool. Keep the saved timetable stack when inserting its parent. */
internal fun NavHostController.repairWorkbenchParent() {
    if (entryFor(Routes.ROOT) != null) return
    val timetable = Routes.tool("tt")
    if (entryFor(timetable) != null) {
        popBackStack(timetable, inclusive = true, saveState = true)
        navigate(Routes.ROOT)
        navigate(timetable) { restoreState = true }
    } else {
        showWorkbench()
    }
}

internal fun NavHostController.showWorkbench() {
    if (entryFor(Routes.ROOT) != null) {
        popBackStack(Routes.ROOT, inclusive = false)
    } else {
        navigate(Routes.ROOT) {
            popUpTo(graph.id)
            launchSingleTop = true
        }
    }
}

/** A desktop timetable always belongs directly below the workbench. */
internal fun NavHostController.openIncomingTimetable(request: IncomingNavigation) {
    val timetable = Routes.tool("tt")
    val existing = entryFor(timetable)
    if (existing != null && existing != currentBackStackEntry) popBackStack(timetable, inclusive = false)
    val canReuse = existing != null && currentBackStackEntry == existing && previousBackStackEntry?.destination?.route == Routes.ROOT
    if (!canReuse) {
        showWorkbench()
        navigate(timetable)
    }
    currentBackStackEntry?.savedStateHandle?.set(TIMETABLE_DELIVERY, Bundle().apply {
        putString("id", request.id)
        putString("focus", request.courseFocus)
    })
}

private fun NavHostController.entryFor(route: String): NavBackStackEntry? = try {
    getBackStackEntry(route)
} catch (_: IllegalArgumentException) {
    null
}
