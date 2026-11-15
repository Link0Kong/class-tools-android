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

    /** 登录页提交后等待档案域 + 延迟重试提取的最大次数（2026-11-16：登录后状态延迟渲染）。 */
    const val EXTRACT_MAX_ATTEMPTS: Int = 3

    /** 重试提取间隔（1.2s × 2 次 = 约 2.4s 等待，覆盖登录跳转后异步渲染）。 */
    const val EXTRACT_RETRY_INTERVAL_MS: Long = 1200L

    /** 读取 my.chsi 会话 Cookie 的 URL（CookieManager 按 URL 取同域 Cookie，辅助登录态判定）。 */
    const val ARCHIVE_COOKIE_URL: String = "https://my.chsi.com.cn"

    /** 是否还应继续延迟重试提取（attempt 从 0 开始）。 */
    fun shouldRetryExtract(attempt: Int): Boolean = attempt < EXTRACT_MAX_ATTEMPTS - 1

    /** 页面加载完成后是否应**自动**打开官方登录页：永远 false（不强制跳登录页）。 */
    fun shouldAutoOpenLoginPage(mode: Mode, pageIsLoginUrl: Boolean): Boolean = false

    /** 档案域页面是否应触发只读提取（卡片页/登录页后台都提取；全屏官方页不提取）。 */
    fun shouldExtract(mode: Mode, pageUrl: String?): Boolean =
        mode != Mode.WEB && ChsiExtractLogic.isLoggedInUrl(pageUrl)

    /** 提取完成后应处于的模式：确认已登录 → 回卡片页；未登录 → 保持当前模式（登录页/卡片页）。 */
    fun modeAfterExtract(mode: Mode, loggedIn: Boolean): Mode = if (loggedIn) Mode.CARDS else mode

    /**
     * 一次提取结果是否应判定为「已登录」。
     *
     * - 后台自动恢复（CARDS）：沿用强信号 [ChsiExtractLogic.isLoggedInPayload]；
     * - 用户主动登录（LOGIN）：真实档案页可能没有「退出/个人中心」字样，使用宽松判定
     *   [ChsiExtractLogic.isLoginSuccessAfterLogin]（档案域 + 非空数据 + 无营销标记 + 数据信号/Cookie 辅助）。
     */
    fun loggedInAfterExtract(
        payload: ChsiPagePayload?,
        pageUrl: String?,
        mode: Mode,
        archiveCookiePresent: Boolean = false,
    ): Boolean {
        if (ChsiExtractLogic.isLoggedInPayload(payload)) return true
        if (mode != Mode.LOGIN) return false
        return ChsiExtractLogic.isLoginSuccessAfterLogin(payload, pageUrl, archiveCookiePresent)
    }

    /**
     * 是否展示「已登录，进入卡片页」兜底按钮（2026-11-16：确保卡片页与登录页都能看到）。
     *
     * - 登录页（LOGIN）空闲时始终展示（用户可能已完成登录但自动判定失败）；
     * - 卡片页（CARDS）未登录且空闲时也展示（提取失败但用户确认已登录）。
     */
    fun showEnterCardsFallback(
        mode: Mode,
        refreshing: Boolean,
        loggedIn: Boolean = false,
    ): Boolean = !refreshing && (mode == Mode.LOGIN || (mode == Mode.CARDS && !loggedIn))

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
