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

    /** 演示播放器（沉浸式全屏独立路由，不显示底栏）。 */
    const val PPT_DECK_ARG = "deckId"
    const val PPT_PLAYER = "pptPlay/{$PPT_DECK_ARG}"

    fun pptPlayer(deckId: String): String = "pptPlay/$deckId"

    /** v2.0 下载详情页（点击列表卡片进入；旧播放器仅作「在线预览」入口）。 */
    const val PPT_DETAIL = "pptDetail/{$PPT_DECK_ARG}"

    fun pptDetail(deckId: String): String = "pptDetail/$deckId"

    val topLevel: Set<String> = setOf(HOME, ALL, PROFILE)
}
