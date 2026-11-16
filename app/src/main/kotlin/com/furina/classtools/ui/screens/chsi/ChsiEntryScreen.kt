package com.furina.classtools.ui.screens.chsi

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.furina.classtools.features.chsi.ChsiCatalog
import com.furina.classtools.features.chsi.ChsiExtractLogic
import com.furina.classtools.features.chsi.ChsiNavigationLogic
import com.furina.classtools.features.chsi.ChsiNavigationLogic.Mode as ChsiMode
import com.furina.classtools.features.chsi.ChsiOfficialTool
import com.furina.classtools.features.chsi.ChsiPagePayload
import com.furina.classtools.features.chsi.ChsiTexts
import com.furina.classtools.features.chsi.ChsiWebViewLogic
import com.furina.classtools.ui.components.CtTopBar
import com.furina.classtools.ui.components.ExpressiveBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** 页面模式说明：默认卡片页（后台自动恢复登录）→ 官方登录页（仅用户主动点登录）→ 全屏 WebView。 */
/**
 * 学信网工具页（规格 `_audit/chsi_entry_20261115/CHSI_ENTRY_SPEC.md` §3，2026-11-15 需求调整）。
 *
 * - **默认进入卡片页**（CARDS，不强制跳登录页）：『数据』4 卡 +『官方工具』10 卡始终可见；
 *   后台隐藏 WebView 加载 `https://my.chsi.com.cn/archive/wap/index.jsp` 自动恢复登录：
 *   已登录 → 注入只读 JS 提取文本摘要并刷新数据卡；未登录 → 数据卡显示「未登录 · 去登录查看」；
 * - 未登录时点「登录学信网」/数据卡 → 全屏打开官方账号登录页（`account.chsi.com.cn`），
 *   由用户本人手动登录（验证码/短信/实人核验），App **不预填、不保存、不上传**账号密码；
 * - 官方登录页完成登录 → URL 回到 my.chsi.com.cn 且页面内容确认已登录 → 自动回卡片页并刷新数据卡；
 * - 点官方工具卡 = App 内 WebView 全屏打开官方页面，返回回卡片页；
 * - WebView 顶栏「桌面版/手机版」切换（UA + reload）；
 * - **CookieManager 共享 + 标准 Chrome UA + 第三方 Cookie/DOM storage**（2026-11-16，参考
 *   HFUT-Schedule 的 WebView+Cookie 模式）：默认 UA 为标准 Chrome 移动 UA（不用系统 WebView 的
 *   `wv` 标记）；创建 WebView 前统一开启 Cookie 接收，登录跳转页面 `flush()` 落盘；
 *   登录态仍以**页面内容**判定（Cookie 只做辅助信号，不只看 URL）；
 * - 卡片页/登录页都提供「清除 Cookie 重登」：**只清学信网域名 Cookie + 当前页 DOM 存储**，
 *   再打开官方登录页；「退出学信网登录」同样只清学信网域，不再全局 `removeAllCookies` /
 *   `WebStorage.deleteAllData`（否则会误清活动 H5 的 lgInfo 等其它工具会话）；
 * - 提取数据仅内存展示，不落盘/不上传/不打日志；展示前掩码 18 位身份证/11 位手机号。
 *
 * 参考实现：HFUT-Schedule（`ui/util/WebViewFactory.kt` 的 Cookie 启停/flush、
 * `activity/util/WebViewActivity.kt` 的 WebView 承载、`xueXin/XueXin.kt` 的学信网入口）。
 * 不复制其源码；只对齐「标准 UA + CookieManager 共享/清理 + 页面回调」的行为。
 *
 * @param sid 班级身份学号（本页不使用：学信网登录与班级身份无关，保持导航签名一致）
 * @param onBack 顶栏返回（卡片页 → 退出工具页；登录/WebView 模式 → 回卡片页）
 */
@SuppressLint("SetJavaScriptEnabled")
@Suppress("UNUSED_PARAMETER")
@Composable
fun ChsiEntryScreen(
    sid: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state = remember { ChsiScreenState() }

    val pageClient = remember {
        object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // 规格 §3：仅允许 http(s) 导航；其它 scheme 一律阻止。
                return !ChsiExtractLogic.isAllowedNavigation(request.url.toString())
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                state.loading.value = true
                state.pageUrl = url.orEmpty()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                state.loading.value = false
                val current = url.orEmpty().ifBlank { view.url.orEmpty() }
                state.pageUrl = current
                // CookieManager 共享/落盘：学信网页面加载完成即 flush，避免登录跳转后会话偶发丢失。
                if (ChsiExtractLogic.isChsiHost(current)) {
                    runCatching { CookieManager.getInstance().flush() }
                }
                if (state.mode.value == ChsiMode.WEB) return
                when {
                    // 被重定向到学信网账号登录页 = 未登录：只在用户主动点「登录学信网」时停留登录页；
                    // 默认卡片页/后台检查时不强制跳转（shouldAutoOpenLoginPage 恒为 false），
                    // 保持官方工具卡始终可见。
                    ChsiExtractLogic.isLoginUrl(current) -> {
                        state.loggedIn.value = false
                        state.refreshing.value = false
                        state.extractInFlight = false
                    }
                    // 回到学信档案域（含后台恢复登录）：提取数据，登录态由页面内容二次确认。
                    ChsiNavigationLogic.shouldExtract(state.mode.value, current) -> {
                        if (!state.extractInFlight) {
                            state.extractInFlight = true
                            state.extractSeq.value += 1
                        }
                    }
                    else -> Unit
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) {
                    state.loading.value = false
                    state.globalError.value = ChsiTexts.MSG_LOAD_FAILED
                }
            }
        }
    }

    val webView = remember {
        // CookieManager 是进程级共享的：先确保接受 Cookie，再创建 WebView（HFUT WebView+Cookie 模式）。
        runCatching { CookieManager.getInstance().setAcceptCookie(true) }
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // 2026-11-16：默认使用标准 Chrome UA（不用系统 WebView UA），避免官方站点识别为内置浏览器。
            settings.userAgentString = ChsiWebViewLogic.defaultUserAgent()
            // 安全（规格 §3）：禁 file/content access，仅 http(s)（由 shouldOverrideUrlLoading 保证）。
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.loadsImagesAutomatically = true
            webViewClient = pageClient
            // Cookie 共享 + 第三方 Cookie + DOM storage（登录链路跨 account→my 子域，缺一不可）。
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }
    }

    // 默认进卡片页（不强制跳登录页）：后台加载学信档案 wap 页，已登录则自动提取并刷新数据卡。
    LaunchedEffect(Unit) {
        state.refreshing.value = true
        webView.loadUrl(ChsiCatalog.ARCHIVE_ENTRY)
    }

    // 已登录页面加载完成 → 延迟注入只读 JS 提取文本摘要（仅内存）。
    // 2026-11-16：登录跳转后档案页可能延迟渲染，改为「延迟重试 2–3 次（1.2s）+ Cookie 辅助判定」。
    val extractSeq = state.extractSeq.value
    LaunchedEffect(extractSeq) {
        if (extractSeq <= 0) return@LaunchedEffect
        state.refreshing.value = true
        state.globalError.value = null
        delay(ChsiExtractLogic.EXTRACT_DELAY_MS)
        var payload: ChsiPagePayload? = null
        var loggedIn = false
        var lastHasCookie = false
        for (attempt in 0 until ChsiNavigationLogic.EXTRACT_MAX_ATTEMPTS) {
            if (!ChsiExtractLogic.isLoggedInUrl(state.pageUrl)) {
                // 提取时已不在档案域（会话过期/被重定向到登录）：
                // 用户已人工确认「我已登录」时不打回未登录态，直接按已登录展示卡片页兜底。
                state.extractInFlight = false
                state.refreshing.value = false
                if (state.loginConfirmedByUser) {
                    state.loggedIn.value = true
                    state.cards.value = ChsiExtractLogic.extractCards(null)
                    state.lastUpdated.value = ChsiExtractLogic.updatedTimeText()
                    state.globalError.value = ChsiTexts.MSG_EXTRACT_FAILED
                    state.mode.value = ChsiMode.CARDS
                } else {
                    state.loggedIn.value = false
                    state.cards.value = ChsiExtractLogic.extractCards(null)
                }
                return@LaunchedEffect
            }
            val raw = runCatching {
                webView.evaluateJavascriptAwait(ChsiExtractLogic.buildExtractScript())
            }.getOrNull()
            payload = ChsiExtractLogic.parseWebViewResult(raw)
            // Cookie 辅助信号（只读，不改 Cookie / 不读密码）：登录态延迟或强信号缺失时辅助判定。
            val archiveCookie = runCatching {
                CookieManager.getInstance().getCookie(ChsiNavigationLogic.ARCHIVE_COOKIE_URL)
            }.getOrNull()
            val hasArchiveCookie = ChsiExtractLogic.hasArchiveSessionCookie(archiveCookie)
            lastHasCookie = hasArchiveCookie
            // 用户在登录页/卡片页点过「已登录，进入卡片页」→ 提取按登录流程的宽松判定处理，
            // 避免刚进卡片页又被营销页误判打回未登录态。
            loggedIn = ChsiNavigationLogic.loggedInAfterExtract(
                payload = payload,
                pageUrl = state.pageUrl,
                mode = if (state.loginConfirmedByUser) ChsiMode.LOGIN else state.mode.value,
                archiveCookiePresent = hasArchiveCookie,
            )
            if (loggedIn) break
            if (ChsiNavigationLogic.shouldRetryExtract(attempt)) {
                delay(ChsiNavigationLogic.EXTRACT_RETRY_INTERVAL_MS)
            }
        }
        // 人工确认兜底：用户明确点过「我已登录」且存在 my.chsi 会话 Cookie → 视为已登录（Cookie 只辅助，不阻塞）。
        if (!loggedIn && state.loginConfirmedByUser &&
            ChsiNavigationLogic.confirmedLoginByCookie(state.pageUrl, lastHasCookie)
        ) {
            loggedIn = true
        }
        if (!loggedIn) {
            state.extractInFlight = false
            state.refreshing.value = false
            if (state.loginConfirmedByUser) {
                // 用户已人工确认登录：不再显示未登录/不卡登录页，展示卡片页 + 10 个官方入口/完整档案兜底。
                state.loggedIn.value = true
                state.cards.value = ChsiExtractLogic.extractCards(payload)
                state.lastUpdated.value = ChsiExtractLogic.updatedTimeText()
                state.globalError.value = ChsiTexts.MSG_EXTRACT_FAILED
                state.mode.value = ChsiMode.CARDS
            } else {
                state.loggedIn.value = false
                state.lastUpdated.value = null
                state.cards.value = ChsiExtractLogic.extractCards(null)
            }
            // 不强制跳回登录页（2026-11-16 要求）：停留在当前页面，卡片页/登录页都显示
            // 「我已登录，进入卡片页」兜底按钮，由用户确认后手动进入。
            return@LaunchedEffect
        }
        state.cards.value = ChsiExtractLogic.extractCards(payload)
        state.loggedIn.value = true
        state.lastUpdated.value = ChsiExtractLogic.updatedTimeText()
        if (payload == null) state.globalError.value = ChsiTexts.MSG_EXTRACT_FAILED
        state.refreshing.value = false
        state.extractInFlight = false
        // 登录成功（含在官方登录页完成登录）：自动回卡片页并展示已提取的数据卡。
        state.mode.value = ChsiNavigationLogic.modeAfterExtract(state.mode.value, loggedIn = true)
    }

    fun reloadArchive() {
        state.extractInFlight = false
        state.loading.value = true
        webView.loadUrl(ChsiCatalog.ARCHIVE_ENTRY)
    }

    /** 用户主动点「登录学信网」：打开官方账号登录页，由用户本人手动登录。 */
    fun goLogin() {
        state.loggedIn.value = false
        state.globalError.value = null
        state.mode.value = ChsiMode.LOGIN
        state.loading.value = true
        webView.loadUrl(ChsiCatalog.LOGIN_URL)
    }

    /** 从登录页返回卡片页（不改变登录态；后台可再检查一次）。 */
    fun backToCards() {
        state.mode.value = ChsiMode.CARDS
        if (!state.loggedIn.value && !state.refreshing.value) {
            state.extractInFlight = false
            state.loading.value = true
            webView.loadUrl(ChsiCatalog.ARCHIVE_ENTRY)
        }
    }

    /**
     * LOGIN 模式兜底：用户反馈已完成登录但页面仍停留在账号/档案页时，手动进入卡片页。
     *
     * 不伪造登录态：只是切回卡片页并再尝试提取一次数据；提取不到的卡走空态兜底。
     */
    fun enterCardsFromLogin() {
        state.loginConfirmedByUser = true
        state.mode.value = ChsiMode.CARDS
        state.loggedIn.value = true
        state.refreshing.value = true
        state.globalError.value = null
        state.extractInFlight = false
        state.loading.value = true
        state.extractSeq.value += 1
    }

    fun refresh() {
        if (state.refreshing.value) return
        state.refreshing.value = true
        state.globalError.value = null
        reloadArchive()
    }

    fun openOfficialPage(url: String, title: String) {
        state.webTitle.value = title
        state.mode.value = ChsiMode.WEB
        state.loading.value = true
        webView.loadUrl(url)
    }

    /** UA 切换（桌面版/手机版 + reload，规格 §3；默认标准 Chrome 移动 UA）。 */
    fun toggleUserAgent() {
        val desktop = !state.desktopUa.value
        state.desktopUa.value = desktop
        webView.settings.userAgentString = ChsiWebViewLogic.userAgentFor(desktop)
        state.loading.value = true
        webView.reload()
    }

    /**
     * 只清理学信网域名的登录状态（Cookie + 当前页 DOM 存储 + 表单/缓存）。
     *
     * 2026-11-16 根因修复：旧实现 `removeAllCookies()` + `WebStorage.deleteAllData()` 是**全局**清理，
     * 会把活动 H5 的 `lgInfo`、教务/今日校园等其它 WebView 会话一并清掉。这里：
     * - 按 [ChsiWebViewLogic.COOKIE_URLS] 逐个域名读取 Cookie 头，再用 `setCookie` 过期同名 Cookie
     *   （只影响 chsi.com.cn 域）；
     * - 仅当当前页面属于学信网时注入 [ChsiWebViewLogic.CLEAR_PAGE_STORAGE_JS] 清 localStorage/
     *   sessionStorage（不触碰其它站点）；
     * - `clearCache(true)` 只清 HTTP 缓存（非凭据），避免登录跳转的缓存页残留。
     */
    fun clearChsiLoginState() {
        val manager = CookieManager.getInstance()
        ChsiWebViewLogic.COOKIE_URLS.forEach { cookieUrl ->
            val header = runCatching { manager.getCookie(cookieUrl) }.getOrNull()
            ChsiWebViewLogic.expiredCookieDirectives(header).forEach { directive ->
                runCatching { manager.setCookie(cookieUrl, directive) }
            }
        }
        runCatching { manager.flush() }
        if (ChsiExtractLogic.isChsiHost(state.pageUrl)) {
            runCatching { webView.evaluateJavascript(ChsiWebViewLogic.CLEAR_PAGE_STORAGE_JS, null) }
        }
        runCatching { webView.clearFormData() }
        runCatching { webView.clearHistory() }
        runCatching { webView.clearCache(true) }
        state.loginConfirmedByUser = false
        state.desktopUa.value = false
        runCatching { webView.settings.userAgentString = ChsiWebViewLogic.defaultUserAgent() }
        state.cards.value = ChsiExtractLogic.extractCards(null)
        state.lastUpdated.value = null
        state.loggedIn.value = false
        state.globalError.value = null
        state.refreshing.value = false
        state.extractInFlight = false
    }

    /** 退出学信网登录：只清学信网域登录状态（不影响其它工具），回卡片页后台重新检查。 */
    fun logout() {
        state.clearConfirmAction.value = null
        clearChsiLoginState()
        state.mode.value = ChsiMode.CARDS
        state.loading.value = true
        state.refreshing.value = true
        webView.loadUrl(ChsiCatalog.ARCHIVE_ENTRY)
        Toast.makeText(context, ChsiTexts.MSG_LOGGED_OUT, Toast.LENGTH_SHORT).show()
    }

    /** 清除 Cookie 重登（登录页/卡片页都提供）：清学信网域 Cookie 后重新打开官方登录页。 */
    fun clearCookiesAndRelogin() {
        state.clearConfirmAction.value = null
        clearChsiLoginState()
        state.loading.value = true
        state.globalError.value = null
        state.mode.value = ChsiMode.LOGIN
        webView.loadUrl(ChsiCatalog.LOGIN_URL)
        Toast.makeText(context, ChsiTexts.MSG_COOKIE_CLEARED_RELOGIN, Toast.LENGTH_SHORT).show()
    }

    // WebView 随页面销毁（返回工具页 / 旋转重建）释放，避免内存泄漏。
    DisposableEffect(webView) {
        onDispose {
            runCatching {
                webView.stopLoading()
                webView.clearHistory()
                webView.removeAllViews()
                webView.destroy()
            }
        }
    }

    BackHandler {
        when {
            state.clearConfirmAction.value != null -> state.clearConfirmAction.value = null
            state.mode.value == ChsiMode.WEB -> state.mode.value = ChsiMode.CARDS
            state.mode.value == ChsiMode.LOGIN -> backToCards()
            else -> onBack()
        }
    }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            val mode = state.mode.value
            CtTopBar(
                title = when (mode) {
                    ChsiMode.WEB -> state.webTitle.value ?: ChsiTexts.PAGE_TITLE
                    ChsiMode.LOGIN -> ChsiTexts.BTN_LOGIN_ACTION
                    ChsiMode.CARDS -> ChsiTexts.PAGE_TITLE
                },
                onBack = when (mode) {
                    ChsiMode.WEB -> ({ state.mode.value = ChsiMode.CARDS })
                    ChsiMode.LOGIN -> ({ backToCards() })
                    ChsiMode.CARDS -> onBack
                },
                actions = {
                    when (mode) {
                        ChsiMode.WEB -> TextButton(onClick = { toggleUserAgent() }) {
                            Text(
                                if (state.desktopUa.value) ChsiTexts.BTN_MOBILE else ChsiTexts.BTN_DESKTOP,
                            )
                        }
                        ChsiMode.LOGIN -> TextButton(onClick = { backToCards() }) {
                            Text(ChsiTexts.BTN_BACK_TO_CARDS)
                        }
                        ChsiMode.CARDS -> Unit
                    }
                },
            )

            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (mode == ChsiMode.LOGIN) {
                    ChsiHintBanner(ChsiTexts.HINT_MANUAL_LOGIN)
                    // 2026-11-15 兜底：真实登录页可能没有「退出登录」等强信号导致自动判定失败，
                    // 用户确认已登录时可直接进入卡片页（不伪造登录态，进卡片后仍会再提取一次）。
                    if (ChsiNavigationLogic.showEnterCardsFallback(mode, state.refreshing.value)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = ChsiTexts.HINT_ENTER_CARDS_FALLBACK,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                modifier = Modifier.weight(1f),
                            )
                            // 2026-11-16：按钮**始终可见**；提取进行中禁用防误触，完成后可点。
                            TextButton(
                                onClick = { enterCardsFromLogin() },
                                enabled = !state.refreshing.value,
                            ) {
                                Text(ChsiTexts.BTN_ENTER_CARDS)
                            }
                        }
                    }
                    // 2026-11-16：登录页也提供「清除 Cookie 重登」（登录异常/卡登录页时的恢复入口）。
                    if (ChsiWebViewLogic.shouldShowClearCookies(mode)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = ChsiTexts.HINT_CLEAR_COOKIE_RELOGIN,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = { state.clearConfirmAction.value = ClearCookieAction.RELOGIN },
                            ) {
                                Text(ChsiTexts.BTN_CLEAR_COOKIE_RELOGIN)
                            }
                        }
                    }
                }
                state.globalError.value?.takeIf { it.isNotBlank() }?.let { message ->
                    ChsiHintBanner(message, isError = true)
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // WebView 始终保留在组合树里（卡片页隐藏为 1dp）：后台恢复登录/提取依赖它。
                    AndroidView(
                        factory = { webView },
                        modifier = if (mode == ChsiMode.CARDS) {
                            Modifier.size(1.dp)
                        } else {
                            Modifier.fillMaxSize()
                        },
                    )
                    if (mode == ChsiMode.CARDS) {
                        ChsiCardsContent(
                            cards = state.cards.value,
                            loggedIn = state.loggedIn.value,
                            refreshing = state.refreshing.value,
                            lastUpdated = state.lastUpdated.value,
                            globalError = state.globalError.value,
                            onLogin = { goLogin() },
                            onRefresh = { refresh() },
                            onOpenCard = { card -> openOfficialPage(card.targetUrl, card.kind.title) },
                            onOpenTool = { tool -> openOfficialPage(tool.url, tool.title) },
                            onOpenArchive = {
                                openOfficialPage(ChsiCatalog.ARCHIVE_HOME, ChsiTexts.PAGE_TITLE)
                            },
                            onLogout = { state.clearConfirmAction.value = ClearCookieAction.LOGOUT },
                            onEnterCards = { enterCardsFromLogin() },
                            onClearCookies = {
                                state.clearConfirmAction.value = ClearCookieAction.RELOGIN
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (mode != ChsiMode.CARDS && state.loading.value) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .height(2.dp),
                        )
                    }
                }
            }
        }
    }

    state.clearConfirmAction.value?.let { action ->
        val isLogout = action == ClearCookieAction.LOGOUT
        AlertDialog(
            onDismissRequest = { state.clearConfirmAction.value = null },
            title = {
                Text(
                    if (isLogout) ChsiTexts.LOGOUT_CONFIRM_TITLE else ChsiTexts.CLEAR_COOKIE_CONFIRM_TITLE,
                )
            },
            text = {
                Text(
                    if (isLogout) ChsiTexts.LOGOUT_CONFIRM_TEXT else ChsiTexts.CLEAR_COOKIE_CONFIRM_TEXT,
                )
            },
            confirmButton = {
                TextButton(onClick = { if (isLogout) logout() else clearCookiesAndRelogin() }) {
                    Text(
                        if (isLogout) ChsiTexts.BTN_LOGOUT else ChsiTexts.BTN_CLEAR_COOKIE_RELOGIN,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { state.clearConfirmAction.value = null }) {
                    Text("取消")
                }
            },
        )
    }
}

/** 清理学信网登录状态的两种确认动作（都只清 chsi.com.cn 域，不影响其它工具）。 */
private enum class ClearCookieAction {
    /** 退出学信网登录：清 Cookie 后回卡片页后台重新检查。 */
    LOGOUT,

    /** 清除 Cookie 重登：清 Cookie 后重新打开官方登录页。 */
    RELOGIN,
}

/** 页面运行态（集中放 Compose State，供 WebViewClient 回调与提取协程读写）。 */
private class ChsiScreenState {
    /** 默认卡片页（不强制跳登录页）：官方工具 10 卡始终可见，后台自动恢复登录态。 */
    val mode = mutableStateOf(ChsiNavigationLogic.initialMode())
    val webTitle = mutableStateOf<String?>(null)
    val loading = mutableStateOf(true)
    val cards = mutableStateOf(ChsiExtractLogic.extractCards(null))
    val lastUpdated = mutableStateOf<String?>(null)
    val loggedIn = mutableStateOf(false)

    /** 后台自动恢复登录/提取进行中（首帧即 true，避免数据卡先闪「未登录」）。 */
    val refreshing = mutableStateOf(true)
    val globalError = mutableStateOf<String?>(null)

    /** 清 Cookie / 退出登录确认框动作（null = 不显示）。 */
    val clearConfirmAction = mutableStateOf<ClearCookieAction?>(null)
    val desktopUa = mutableStateOf(false)

    /** 每次 +1 触发一次页面文本提取。 */
    val extractSeq = mutableStateOf(0)

    /** 最近一次页面 URL（WebView 回调更新）。 */
    var pageUrl: String = ""

    /** 是否有提取正在进行（避免同一次页面加载重复触发）。 */
    var extractInFlight: Boolean = false

    /** 用户是否在登录页点过「已登录，进入卡片页」兜底（之后提取按登录流程宽松判定）。 */
    var loginConfirmedByUser: Boolean = false
}

/** 学信网页内提示条（手动登录提示 / 加载失败）。 */
@Composable
private fun ChsiHintBanner(text: String, isError: Boolean = false) {
    val container = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = container, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                maxLines = 2,
            )
        }
    }
}

/** `evaluateJavascript` 的挂起封装（回调在主线程）。 */
private suspend fun WebView.evaluateJavascriptAwait(script: String): String? =
    suspendCancellableCoroutine { continuation ->
        runCatching {
            evaluateJavascript(script) { value ->
                if (continuation.isActive) continuation.resume(value)
            }
        }.onFailure {
            if (continuation.isActive) continuation.resume(null)
        }
    }
