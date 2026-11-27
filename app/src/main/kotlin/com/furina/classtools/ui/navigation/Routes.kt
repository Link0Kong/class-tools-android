package com.furina.classtools.ui.navigation

import android.net.Uri

object Routes {
    /** v2 根页：三 Tab Pager 宿主（工作台/工具/我的）。 */
    const val ROOT = "root"

    const val HOME = "home"
    const val ALL = "all"
    const val PROFILE = "profile"
    const val DATA_PRIVACY = "dataPrivacy"
    const val PERMISSIONS = "permissions"
    const val REMINDER_SETTINGS = "reminderSettings"
    const val VOTE = "vote"
    const val VOTE_CREATE_ARG = "pollId"
    const val VOTE_CREATE = "voteCreate?pollId={$VOTE_CREATE_ARG}"

    /** 发起投票（pollId 为空）或编辑（pollId 非空）。 */
    fun voteCreate(pollId: String? = null): String =
        if (pollId.isNullOrBlank()) "voteCreate" else "voteCreate?pollId=$pollId"

    const val VOTE_DETAIL_ARG = "pollId"
    const val VOTE_DETAIL = "voteDetail/{$VOTE_DETAIL_ARG}"

    fun voteDetail(pollId: String): String = "voteDetail/$pollId"

    const val FEEDBACK = "feedback"
    const val GUIDE = "guide"

    /** 扫一扫：电脑扫码登录 / 抽签存证核验（同一入口按二维码内容分流）。 */
    const val SCAN = "scan"

    /** 抽签核验详情（App Link `/l/draw/<recordId>`）：复用扫码核验界面，进来直接读记录。 */
    const val DRAW_VERIFY_ARG = "recordId"
    const val DRAW_VERIFY = "drawVerify/{$DRAW_VERIFY_ARG}"

    fun drawVerify(recordId: String): String = "drawVerify/" + Uri.encode(recordId)

    const val TOOL_ARG = "toolId"
    const val TOOL_PICK_ARG = "pick"
    const val MAP_ROOM_ARG = "room"
    const val FOCUS_ARG = "focus"
    const val CREATE_TASK_ARG = "createTask"
    const val TOOL = "tool/{$TOOL_ARG}?$TOOL_PICK_ARG={$TOOL_PICK_ARG}&$MAP_ROOM_ARG={$MAP_ROOM_ARG}&$FOCUS_ARG={$FOCUS_ARG}&$CREATE_TASK_ARG={$CREATE_TASK_ARG}"

    fun tool(toolId: String, pick: Boolean = false, focus: String? = null, createTask: Boolean = false): String {
        val query = listOfNotNull(
            "createTask=true".takeIf { createTask },
            "pick=true".takeIf { pick },
            focus?.takeIf(String::isNotBlank)?.let { "focus=" + Uri.encode(it) },
        )
        return "tool/$toolId" + if (query.isEmpty()) "" else query.joinToString("&", prefix = "?")
    }

    fun campusMap(room: String?): String =
        if (room.isNullOrBlank()) tool("map") else "tool/map?$MAP_ROOM_ARG=${Uri.encode(room.trim())}"

    /** 演示播放器（沉浸式全屏独立路由，不显示底栏）。 */
    const val PPT_DECK_ARG = "deckId"
    const val PPT_PLAYER = "pptPlay/{$PPT_DECK_ARG}"

    fun pptPlayer(deckId: String): String = "pptPlay/$deckId"

    /** v2.0 下载详情页（点击列表卡片进入；旧播放器仅作「在线预览」入口）。 */
    const val PPT_DETAIL = "pptDetail/{$PPT_DECK_ARG}"

    fun pptDetail(deckId: String): String = "pptDetail/$deckId"

    val topLevel: Set<String> = setOf(HOME, ALL, PROFILE)
}
