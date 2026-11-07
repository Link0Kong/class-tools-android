package com.furina.classtools.ui.navigation

object Routes {
    const val HOME = "home"
    const val ALL = "all"
    const val PROFILE = "profile"
    const val DATA_PRIVACY = "dataPrivacy"

    const val TOOL_ARG = "toolId"
    const val TOOL = "tool/{$TOOL_ARG}"

    fun tool(toolId: String): String = "tool/$toolId"

    val topLevel: Set<String> = setOf(HOME, ALL, PROFILE)
}
