package com.furina.classtools.ui.screens.chsi

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
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
import com.furina.classtools.features.chsi.ChsiOfficialTool
import com.furina.classtools.features.chsi.ChsiTexts
import com.furina.classtools.ui.components.CtTopBar
import com.furina.classtools.ui.components.ExpressiveBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** 页面模式：登录（官方页面，用户手动登录）→ 卡片页；点卡片进入全屏 WebView。 */
private enum class ChsiMode {
    LOGIN,
    CARDS,
    WEB,
}

/**
 * 学信网工具页（规格 `_audit/chsi_entry_20261115/CHSI_ENTRY_SPEC.md` §3）。
 *
 * - 进入即加载 `https://my.chsi.com.cn/archive/wap/index.jsp`（隐藏 WebView）；
 *   未登录会被重定向到 `account.chsi.com.cn` 登录页 → **全屏显示官方登录页，由用户本人手动登录**
 *   （验证码/短信/实人核验），App **不预填、不保存、不上传**学信网账号密码；
 * - 登录成功（URL 回到 `my.chsi.com.cn` 档案域）→ 注入只读 JS 提取页面文本摘要 → 卡片页；
 * - 卡片页：『数据』学籍/学历/学位/在线验证报告（标签就近取 1–3 行，取不到 → 空态+官方页面入口）
 *   +『官方工具』10 个官方入口（内置 WebView 打开）+「打开完整学信档案」+「退出学信网登录」；
 * - WebView 顶栏「桌面版/手机版」切换（UA + reload）；
 * - 提取数据仅内存展示，不落盘/不上传/不打日志；展示前掩码 18 位身份证/11 位手机号。
 *
 * @param sid 班级身份学号（本页不使用：学信网登录与班级身份无关，保持导航签名一致）
 * @param onBack 顶栏返回（卡片页/登录页 → 退出工具页；WebView 模式 → 回卡片页）
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
                if (state.mode.value == ChsiMode.WEB) return
                when {
                    // 被重定向到学信网账号登录页 = 未登录：显示全屏官方登录页（用户手动登录）。
                    ChsiExtractLogic.isLoginUrl(current) -> {
                        state.loggedIn.value = false
                        state.refreshing.value = false
                        state.extractInFlight = false
                        if (state.mode.value != ChsiMode.LOGIN) state.mode.value = ChsiMode.LOGIN
                    }
                    // 回到学信档案域 = 已登录：提取数据（登录完成 / 刷新 / 会话恢复共用）。
                    ChsiExtractLogic.isLoggedInUrl(current) -> {
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
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // 安全（规格 §3）：禁 file/content access，仅 http(s)（由 shouldOverrideUrlLoading 保证）。
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.loadsImagesAutomatically = true
            webViewClient = pageClient
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }
    }

    // 进入即加载学信档案 wap 页：未登录会自动跳官方登录页（由用户手动登录）。
    LaunchedEffect(Unit) {
        webView.loadUrl(ChsiCatalog.ARCHIVE_ENTRY)
    }

    // 已登录页面加载完成 → 延迟注入只读 JS 提取文本摘要（仅内存）。
    val extractSeq = state.extractSeq.value
    LaunchedEffect(extractSeq) {
        if (extractSeq <= 0) return@LaunchedEffect
        state.refreshing.value = true
        state.globalError.value = null
        delay(ChsiExtractLogic.EXTRACT_DELAY_MS)
        if (!ChsiExtractLogic.isLoggedInUrl(state.pageUrl)) {
            // 提取时已不在档案域（会话过期/被重定向到登录）：回到官方登录页。
            state.extractInFlight = false
            state.refreshing.value = false
            state.loggedIn.value = false
            state.mode.value = ChsiMode.LOGIN
            return@LaunchedEffect
        }
        val raw = runCatching {
            webView.evaluateJavascriptAwait(ChsiExtractLogic.buildExtractScript())
        }.getOrNull()
        val payload = ChsiExtractLogic.parseWebViewResult(raw)
        // 学信档案域未登录时也会显示营销首页（不是登录页 URL），必须用页面内容确认登录态，
        // 否则会把「免费查看学籍…」的引导页误判成已登录并展示空数据卡。
        if (!ChsiExtractLogic.isLoggedInPayload(payload)) {
            state.extractInFlight = false
            state.refreshing.value = false
            state.loggedIn.value = false
            state.lastUpdated.value = null
            state.cards.value = ChsiExtractLogic.extractCards(null)
            state.mode.value = ChsiMode.LOGIN
            // 档案域未登录时只是营销首页；直接跳到官方账号登录页，减少用户再点一次。
            if (ChsiExtractLogic.isArchiveUrl(state.pageUrl)) {
                state.loading.value = true
                webView.loadUrl(ChsiCatalog.LOGIN_URL)
            }
            return@LaunchedEffect
        }
        state.cards.value = ChsiExtractLogic.extractCards(payload)
        state.loggedIn.value = true
        state.lastUpdated.value = ChsiExtractLogic.updatedTimeText()
        if (payload == null) state.globalError.value = ChsiTexts.MSG_EXTRACT_FAILED
        state.refreshing.value = false
        state.extractInFlight = false
        state.mode.value = ChsiMode.CARDS
    }

    fun reloadArchive() {
        state.extractInFlight = false
        state.loading.value = true
        webView.loadUrl(ChsiCatalog.ARCHIVE_ENTRY)
    }

    fun goLogin() {
        state.loggedIn.value = false
        state.globalError.value = null
        state.mode.value = ChsiMode.LOGIN
        reloadArchive()
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

    /** UA 切换（桌面版/手机版 + reload，规格 §3）。 */
    fun toggleUserAgent() {
        val desktop = !state.desktopUa.value
        state.desktopUa.value = desktop
        webView.settings.userAgentString = if (desktop) ChsiCatalog.DESKTOP_UA else ChsiCatalog.MOBILE_UA
        state.loading.value = true
        webView.reload()
    }

    /** 退出学信网登录：清 Cookie + WebStorage + 缓存，回登录态（不影响学信网账号本身）。 */
    fun logout() {
        state.logoutConfirmVisible.value = false
        runCatching { CookieManager.getInstance().removeAllCookies(null) }
        runCatching { CookieManager.getInstance().flush() }
        runCatching { WebStorage.getInstance().deleteAllData() }
        runCatching {
            webView.clearCache(true)
            webView.clearHistory()
            webView.clearFormData()
        }
        state.cards.value = ChsiExtractLogic.extractCards(null)
        state.lastUpdated.value = null
        state.loggedIn.value = false
        state.globalError.value = null
        state.refreshing.value = false
        state.extractInFlight = false
        state.desktopUa.value = false
        webView.settings.userAgentString = ChsiCatalog.MOBILE_UA
        state.mode.value = ChsiMode.LOGIN
        state.loading.value = true
        webView.loadUrl(ChsiCatalog.ARCHIVE_ENTRY)
        Toast.makeText(context, ChsiTexts.MSG_LOGGED_OUT, Toast.LENGTH_SHORT).show()
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
            state.logoutConfirmVisible.value -> state.logoutConfirmVisible.value = false
            state.mode.value == ChsiMode.WEB -> state.mode.value = ChsiMode.CARDS
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
                title = if (mode == ChsiMode.WEB) {
                    state.webTitle.value ?: ChsiTexts.PAGE_TITLE
                } else {
                    ChsiTexts.PAGE_TITLE
                },
                onBack = if (mode == ChsiMode.WEB) {
                    { state.mode.value = ChsiMode.CARDS }
                } else {
                    onBack
                },
                actions = {
                    if (mode == ChsiMode.WEB) {
                        TextButton(onClick = { toggleUserAgent() }) {
                            Text(
                                if (state.desktopUa.value) ChsiTexts.BTN_MOBILE else ChsiTexts.BTN_DESKTOP,
                            )
                        }
                    }
                },
            )

            when (mode) {
                ChsiMode.CARDS -> ChsiCardsContent(
                    cards = state.cards.value,
                    loggedIn = state.loggedIn.value,
                    refreshing = state.refreshing.value,
                    lastUpdated = state.lastUpdated.value,
                    globalError = state.globalError.value,
                    onLogin = { goLogin() },
                    onRefresh = { refresh() },
                    onOpenCard = { card -> openOfficialPage(card.targetUrl, card.kind.title) },
                    onOpenTool = { tool -> openOfficialPage(tool.url, tool.title) },
                    onOpenArchive = { openOfficialPage(ChsiCatalog.ARCHIVE_HOME, ChsiTexts.PAGE_TITLE) },
                    onLogout = { state.logoutConfirmVisible.value = true },
                    modifier = Modifier.weight(1f),
                )

                ChsiMode.LOGIN, ChsiMode.WEB -> {
                    if (mode == ChsiMode.LOGIN) {
                        ChsiHintBanner(ChsiTexts.HINT_MANUAL_LOGIN)
                    }
                    state.globalError.value?.takeIf { it.isNotBlank() }?.let { message ->
                        ChsiHintBanner(message, isError = true)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        AndroidView(
                            factory = { webView },
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (state.loading.value) {
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
    }

    if (state.logoutConfirmVisible.value) {
        AlertDialog(
            onDismissRequest = { state.logoutConfirmVisible.value = false },
            title = { Text(ChsiTexts.LOGOUT_CONFIRM_TITLE) },
            text = { Text(ChsiTexts.LOGOUT_CONFIRM_TEXT) },
            confirmButton = {
                TextButton(onClick = { logout() }) { Text(ChsiTexts.BTN_LOGOUT) }
            },
            dismissButton = {
                TextButton(onClick = { state.logoutConfirmVisible.value = false }) {
                    Text("取消")
                }
            },
        )
    }
}

/** 页面运行态（集中放 Compose State，供 WebViewClient 回调与提取协程读写）。 */
private class ChsiScreenState {
    val mode = mutableStateOf(ChsiMode.LOGIN)
    val webTitle = mutableStateOf<String?>(null)
    val loading = mutableStateOf(true)
    val cards = mutableStateOf(ChsiExtractLogic.extractCards(null))
    val lastUpdated = mutableStateOf<String?>(null)
    val loggedIn = mutableStateOf(false)
    val refreshing = mutableStateOf(false)
    val globalError = mutableStateOf<String?>(null)
    val logoutConfirmVisible = mutableStateOf(false)
    val desktopUa = mutableStateOf(false)

    /** 每次 +1 触发一次页面文本提取。 */
    val extractSeq = mutableStateOf(0)

    /** 最近一次页面 URL（WebView 回调更新）。 */
    var pageUrl: String = ""

    /** 是否有提取正在进行（避免同一次页面加载重复触发）。 */
    var extractInFlight: Boolean = false
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
