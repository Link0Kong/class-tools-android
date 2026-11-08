package com.furina.classtools.ui.navigation

object Routes {
    /** v2 根页：三 Tab Pager 宿主（工作台/工具/我的）。 */
    const val ROOT = "root"

    const val HOME = "home"
    const val ALL = "all"
    const val PROFILE = "profile"
    const val DATA_PRIVACY = "dataPrivacy"
    const val PERMISSIONS = "permissions"
    const val REMINDER_SETTINGS = "reminderSettings"
    const val FEEDBACK = "feedback"
    const val GUIDE = "guide"

    const val TOOL_ARG = "toolId"
    const val TOOL = "tool/{$TOOL_ARG}"

    fun tool(toolId: String): String = "tool/$toolId"

    val topLevel: Set<String> = setOf(HOME, ALL, PROFILE)
}
