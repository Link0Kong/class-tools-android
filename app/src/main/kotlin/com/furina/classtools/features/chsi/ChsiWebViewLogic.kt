package com.furina.classtools.features.chsi

/**
 * 学信网 WebView + Cookie 会话纯逻辑（参考 HFUT-Schedule 的 WebView+Cookie 模式）。
 *
 * ## 2026-11-16 根因（用户反馈「学信网还是不行」）
 *
 * 旧实现只做了 `domStorageEnabled` + `setAcceptCookie`，没有把 WebView 会话按 HFUT-Schedule
 * 的做法补齐：
 * 1. **默认 UA 是系统 WebView UA**（含 `; wv`、`Version/4.0`、应用名），学信网等站点会把它识别成
 *    「App 内置浏览器」而走不同分支（部分页面壳化/登录页功能受限）；HFUT 的 XueXin 入口与
 *    `WebViewActivity` 都固定用标准浏览器 Chrome UA 打开官方页面。
 * 2. **Cookie 只开不共享/不落盘**：`CookieManager` 是进程级共享的，但旧实现没有在创建 WebView
 *    前统一 `setAcceptCookie(true)`，也没有在关键页面 `flush()`，登录跳转后偶发丢会话。
 * 3. **第三方 Cookie / DOM storage 未显式保证**：学信网登录链路跨 `account.chsi.com.cn` →
 *    `my.chsi.com.cn`，第三方 Cookie 关闭时会丢登录态。
 * 4. **「清理 Cookie 重登」误伤其它工具**：旧退出直接 `removeAllCookies + WebStorage.deleteAllData`
 *    （全局），会把「活动 H5 的 lgInfo / 教务 / 今日校园」等会话一起清掉；这里改为**按学信网域名**
 *    过期 Cookie + 仅在学信网页面内清 localStorage/sessionStorage。
 *
 * 本文件不依赖 Android/Compose，纯 Kotlin，可直接 JVM 单测；真正的 CookieManager/WebView 操作在
 * `ChsiEntryScreen` 内完成。登录态仍以**页面内容**为准（[ChsiExtractLogic.isLoggedInPayload] 等），
 * Cookie 只做辅助信号，**绝不只按 URL 判定**。
 */
object ChsiWebViewLogic {

    /** 学信网 Cookie 根域（Cookie 过期指令用；不要带前导点）。 */
    const val COOKIE_DOMAIN: String = "chsi.com.cn"

    /**
     * 学信网各子系统域名（CookieManager 按 URL 取/写 Cookie；共享 Cookie 需覆盖这些域）。
     *
     * 覆盖：档案 `my` / 账号 `account` / 主站 `www` / 研招网 `yz` / 学职平台 `xz`。
     */
    val COOKIE_URLS: List<String> = listOf(
        "https://my.chsi.com.cn",
        "https://account.chsi.com.cn",
        "https://www.chsi.com.cn",
        "https://yz.chsi.com.cn",
        "https://xz.chsi.com.cn",
    )

    /** 合法的 Cookie 名（RFC 6265 token 的保守子集）。 */
    private val COOKIE_NAME_REGEX = Regex("""^[A-Za-z0-9_\-\.]+$""")

    /**
     * 默认 UA：**标准 Chrome UA**（不是系统 WebView UA）。
     *
     * 不用 `WebView.getDefaultUserAgent()`（含 `; wv` / `Version/4.0` / 应用名），避免学信网把请求
     * 当成「App 内置浏览器」；与 HFUT-Schedule 的 XueXin / WebViewActivity 固定 UA 的做法一致。
     */
    fun defaultUserAgent(): String = ChsiCatalog.MOBILE_UA

    /** 顶栏「桌面版/手机版」切换：桌面 → [ChsiCatalog.DESKTOP_UA]，否则标准 Chrome 移动 UA。 */
    fun userAgentFor(desktop: Boolean): String =
        if (desktop) ChsiCatalog.DESKTOP_UA else ChsiCatalog.MOBILE_UA

    /** UA 是否像「标准 Chrome」而非系统 WebView（单测/诊断用）。 */
    fun isStandardChromeUserAgent(ua: String?): Boolean {
        val text = ua?.trim().orEmpty()
        if (text.isEmpty()) return false
        if (!text.contains("Chrome/")) return false
        if (text.contains("; wv")) return false
        if (text.contains("Version/4.0")) return false
        return true
    }

    /** 该 URL 是否属于需要清理/读取 Cookie 的学信网域名。 */
    fun isChsiCookieUrl(url: String?): Boolean = ChsiExtractLogic.isChsiHost(url) &&
        ChsiExtractLogic.schemeOf(url) == "https"

    /**
     * 「清除 Cookie 重登」是否可见：卡片页 / 登录页都提供；全屏官方页（WEB）不提供，
     * 避免用户在浏览官方页面时误清会话导致页面跳登录。
     */
    fun shouldShowClearCookies(mode: ChsiNavigationLogic.Mode): Boolean =
        mode != ChsiNavigationLogic.Mode.WEB

    /**
     * Cookie 请求头（`a=1; b=2`）→ 逐项过期指令，交给 `CookieManager.setCookie(url, directive)`。
     *
     * 同时生成三种指令，覆盖学信网可能使用的 cookie 归属方式：
     * - `Domain=chsi.com.cn`（普通域 Cookie）；
     * - `Domain=.chsi.com.cn`（历史前导点写法）；
     * - 不带 Domain（host-only Cookie，只在当前 URL 主机名下有效）。
     *
     * 只处理学信网域名下的名称，**不会**影响其它站点/工具；非法名/空值/重复名会被忽略。
     */
    fun expiredCookieDirectives(
        cookieHeader: String?,
        cookieDomain: String = COOKIE_DOMAIN,
    ): List<String> {
        val header = cookieHeader?.trim().orEmpty()
        if (header.isEmpty()) return emptyList()
        val domain = cookieDomain.trim().trimStart('.').ifBlank { COOKIE_DOMAIN }
        val names = LinkedHashSet<String>()
        header.split(';').forEach { part ->
            val trimmed = part.trim()
            if (trimmed.isEmpty()) return@forEach
            val eq = trimmed.indexOf('=')
            if (eq <= 0) return@forEach
            val name = trimmed.substring(0, eq).trim()
            if (name.isEmpty() || !COOKIE_NAME_REGEX.matches(name)) return@forEach
            names.add(name)
        }
        if (names.isEmpty()) return emptyList()
        val directives = ArrayList<String>(names.size * 3)
        names.forEach { name ->
            directives.add("$name=; Max-Age=0; Path=/; Domain=$domain")
            directives.add("$name=; Max-Age=0; Path=/; Domain=.$domain")
            directives.add("$name=; Max-Age=0; Path=/")
        }
        return directives
    }

    /**
     * 只清「当前学信网页面」的 localStorage / sessionStorage（源内 DOM 存储）。
     *
     * 故意不用 `WebStorage.deleteAllData()`：那是全局清理，会破坏活动 H5 的 `lgInfo` 等其它工具会话。
     */
    const val CLEAR_PAGE_STORAGE_JS: String =
        "(function(){try{localStorage.clear();sessionStorage.clear();return 'cleared';}catch(e){return 'error';}})()"
}
