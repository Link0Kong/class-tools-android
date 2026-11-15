package com.furina.classtools.features.chsi

/**
 * 学信网页面导航/登录恢复纯逻辑（2026-11-15 需求：默认卡片页 + 自动恢复登录，不强制跳登录页）。
 *
 * 用户要求：点「学信网」直接看到工具卡片并自动恢复登录 ——
 * - 默认进入 [Mode.CARDS]（数据卡 + 官方工具 10 卡始终可见）；
 * - 后台加载档案页判断登录态，已登录则自动提取并刷新数据卡（仍停在卡片页）；
 * - **绝不自动跳登录页**；只有用户主动点「登录学信网」才进入 [Mode.LOGIN] 官方登录页；
 * - 官方登录页完成登录（页面内容确认已登录）后自动回卡片页。
 *
 * 本文件不依赖 Compose / Android / WebView，可直接 JVM 单测。
 */
object ChsiNavigationLogic {

    /** 页面模式（与 `ChsiEntryScreen` 内部枚举一一对应）。 */
    enum class Mode {
        CARDS,
        LOGIN,
        WEB,
    }

    /** 进入工具页的默认模式：卡片页（官方工具 10 卡始终可见）。 */
    fun initialMode(): Mode = Mode.CARDS

    /** 页面加载完成后是否应**自动**打开官方登录页：永远 false（不强制跳登录页）。 */
    fun shouldAutoOpenLoginPage(mode: Mode, pageIsLoginUrl: Boolean): Boolean = false

    /** 档案域页面是否应触发只读提取（卡片页/登录页后台都提取；全屏官方页不提取）。 */
    fun shouldExtract(mode: Mode, pageUrl: String?): Boolean =
        mode != Mode.WEB && ChsiExtractLogic.isLoggedInUrl(pageUrl)

    /** 提取完成后应处于的模式：确认已登录 → 回卡片页；未登录 → 保持当前模式（登录页/卡片页）。 */
    fun modeAfterExtract(mode: Mode, loggedIn: Boolean): Mode = if (loggedIn) Mode.CARDS else mode

    /** 页面加载完成时若命中登录页 URL：仅用户主动在登录模式时停留；卡片页后台检查不跳转。 */
    fun staysOnLoginPage(mode: Mode, pageIsLoginUrl: Boolean): Boolean = pageIsLoginUrl && mode == Mode.LOGIN

    /** 未登录时数据卡提示文案（去登录）；已登录但提取不到 → 空态兜底文案。 */
    fun dataCardHint(loggedIn: Boolean): String =
        if (loggedIn) ChsiTexts.MSG_EMPTY_FALLBACK else ChsiTexts.MSG_NEED_LOGIN

    /** 卡片页状态条文案：后台检查中 / 提取中 / 已登录+更新时间 / 未登录。 */
    fun statusText(
        loggedIn: Boolean,
        refreshing: Boolean,
        lastUpdated: String?,
        globalError: String? = null,
    ): String = when {
        globalError != null -> globalError
        refreshing && !loggedIn -> ChsiTexts.STATUS_CHECKING
        refreshing -> ChsiTexts.STATUS_EXTRACTING
        loggedIn && lastUpdated != null -> ChsiTexts.STATUS_READY_PREFIX + lastUpdated
        else -> ChsiTexts.STATUS_NOT_LOGGED_IN
    }
}
