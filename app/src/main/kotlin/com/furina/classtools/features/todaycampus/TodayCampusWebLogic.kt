package com.furina.classtools.features.todaycampus

import java.net.URI

/**
 * 今日校园 / 融合门户 WebView 纯逻辑（2026-11-16 用户反馈：CAS 登录后页面白屏）。
 *
 * 背景：学校「今日校园」入口 `https://e.fjtcm.edu.cn/login2` → CAS 统一身份认证登录 →
 * 登录成功回报后 WebView 可能停留在空白壳页（/login2 空壳、或页面 body 为空），
 * 需要：
 * - 登录成功后**显式加载门户首页** `https://e.fjtcm.edu.cn/index.html`（不再停在 /login2 空壳）；
 * - 检测白屏（页面 innerText 长度 < 20）→ 清缓存重载一次（**不清登录 Cookie**）→
 *   仍白屏显示错误条并提供「浏览器打开 / 融合门户」降级；
 * - WebView 开启 DOM storage / 第三方 Cookie / JS / 允许 mixed content（外壳页面可能混合内容）。
 *
 * 本文件不依赖 Compose / Android / WebView，可直接 JVM 单测。
 */
object TodayCampusWebLogic {

    /** 融合门户首页（登录成功后显式加载）。 */
    const val PORTAL_HOME_URL: String = "https://e.fjtcm.edu.cn/index.html"

    /** 融合门户域名。 */
    const val PORTAL_HOST: String = "e.fjtcm.edu.cn"

    /** 页内文本少于该长度视为白屏/空壳。 */
    const val BLANK_TEXT_THRESHOLD: Int = 20

    /** onPageFinished 后延迟多久做空白检测（等 SPA/异步渲染）。 */
    const val BLANK_CHECK_DELAY_MS: Long = 500L

    /**
     * 首次检测到空白后的二次复核延迟（门户是 Vue SPA，外壳 HTML 只有 `<div id="app">`，
     * 首次 500ms 可能仍在挂载 → 再等 800ms 复核，避免把「加载稍慢」误判成白屏而触发重载）。
     */
    const val BLANK_RECHECK_DELAY_MS: Long = 800L

    /** 白屏允许的自动重载次数（清缓存重载 1 次，仍空白转错误条，不循环）。 */
    const val MAX_BLANK_RELOADS: Int = 1

    // ---- 文案 ---------------------------------------------------------------

    const val MSG_BLANK: String = "登录后页面空白，已尝试重新加载；仍空白请点「浏览器打开」"
    const val MSG_BLANK_RETRYING: String = "页面空白，正在重新加载…"
    const val MSG_PORTAL_HOME_LOADING: String = "登录成功，正在打开融合门户首页…"
    const val BTN_OPEN_BROWSER: String = "浏览器打开"
    const val BTN_OPEN_PORTAL: String = "融合门户"

    // ---- URL 判定 -----------------------------------------------------------

    private fun schemeOf(url: String?): String? = runCatching {
        url?.trim()?.takeIf { it.isNotEmpty() }?.let { URI(it).scheme?.lowercase() }
    }.getOrNull()

    private fun hostOf(url: String?): String? = runCatching {
        url?.trim()?.takeIf { it.isNotEmpty() }?.let { URI(it).host?.lowercase() }
    }.getOrNull()

    private fun pathOf(url: String?): String? = runCatching {
        url?.trim()?.takeIf { it.isNotEmpty() }?.let { URI(it).path ?: "" }
    }.getOrNull()

    /** 是否融合门户域（http/https）。 */
    fun isPortalHost(url: String?): Boolean {
        val scheme = schemeOf(url) ?: return false
        if (scheme != "http" && scheme != "https") return false
        return hostOf(url) == PORTAL_HOST
    }

    /** 是否门户首页（index.html 或根路径）。 */
    fun isPortalHome(url: String?): Boolean {
        if (!isPortalHost(url)) return false
        val path = pathOf(url).orEmpty().lowercase()
        return path.isEmpty() || path == "/" || path.endsWith("/index.html")
    }

    /** 是否门户的登录/空壳页（/login、/login2、/login2/、/cas、/sso 等）。 */
    fun isPortalLoginShell(url: String?): Boolean {
        if (!isPortalHost(url)) return false
        val path = pathOf(url).orEmpty().lowercase()
        return path.contains("login") || path.contains("/cas") || path.contains("/sso") || path.isBlank()
    }

    /**
     * CAS 登录成功后是否应显式跳到门户首页。
     *
     * 条件：当前在门户域、且不是首页（典型是停留在 `/login2` 空壳或其它登录相关路径），
     * 且尚未为本轮会话跳转过首页（由调用方维护 loadedHome 标记，避免循环）。
     */
    fun shouldLoadPortalHomeAfterLogin(url: String?, alreadyLoadedHome: Boolean): Boolean {
        if (alreadyLoadedHome) return false
        if (!isPortalHost(url)) return false
        if (isPortalHome(url)) return false
        return isPortalLoginShell(url)
    }

    // ---- 白屏检测 -----------------------------------------------------------

    /** 页面文本是否为白屏（trim 后长度 < [BLANK_TEXT_THRESHOLD]）。 */
    fun isBlankPageText(text: String?): Boolean = isBlankTextLength(text?.trim()?.length ?: 0)

    /** 检测到的页面文本长度是否为白屏（供 WebView 回调的纯数字使用）。 */
    fun isBlankTextLength(length: Int): Boolean = length < BLANK_TEXT_THRESHOLD

    /** 注入只读脚本：返回 body.innerText.trim().length（数字字符串；异常返回 0）。 */
    fun buildBlankCheckScript(): String = """
        (function () {
          try {
            var body = document.body;
            var text = body && body.innerText ? String(body.innerText) : '';
            return String(text.trim().length);
          } catch (e) {
            return '0';
          }
        })()
    """.trimIndent()

    /** 解析空白检测脚本回调（WebView 返回 JSON 字符串/数字；失败按 0 空白处理）。 */
    fun parseBlankTextLength(raw: String?): Int {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text == "null") return 0
        val unquoted = text.removeSurrounding("\"").toIntOrNull()
        return unquoted ?: 0
    }

    /** 白屏处理决策（不循环：最多清缓存重载 [MAX_BLANK_RELOADS] 次，之后转错误条）。 */
    enum class BlankAction {
        /** 清缓存 + 重载当前 URL（保留登录 Cookie）。 */
        RELOAD,

        /** 显示错误条：重试 / 浏览器打开 / 融合门户。 */
        SHOW_ERROR,
    }

    fun blankAction(blankReloads: Int): BlankAction =
        if (blankReloads < MAX_BLANK_RELOADS) BlankAction.RELOAD else BlankAction.SHOW_ERROR

    /**
     * 白屏重载时优先回门户首页：如果当前是登录空壳页则加载 index.html，否则重载当前 URL。
     */
    fun reloadUrlForBlank(url: String?): String =
        if (isPortalHost(url)) PORTAL_HOME_URL else url.orEmpty()
}
