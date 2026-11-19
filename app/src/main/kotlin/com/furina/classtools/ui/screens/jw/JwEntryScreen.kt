package com.furina.classtools.ui.screens.jw

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import com.furina.classtools.ui.components.light.CtLightAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.furina.classtools.BuildConfig
import com.furina.classtools.data.jw.JwCardsRepository
import com.furina.classtools.data.jw.JwCredentialStore
import com.furina.classtools.features.jw.JwLoginLogic
import com.furina.classtools.features.jw.JwNetworkDetector
import com.furina.classtools.features.jw.JwWebVpn
import com.furina.classtools.ui.components.ctTopBarRaw
import com.furina.classtools.ui.components.ExpressiveBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** 页面模式：登录（WebView 自动/手动）→ 卡片页；点卡片进入全屏 WebView 浏览官方页面。 */
private enum class JwMode {
    LOGIN,
    CARDS,
    WEB,
}

/** WebVPN 门户页自动找教务资源的最多尝试次数（失败回退用户手动点）。 */
private const val MAX_PORTAL_JUMP_ATTEMPTS: Int = 3

/** WebVPN 门户跳转基础延迟（等待资源列表渲染；按尝试次数递增）。 */
private const val PORTAL_JUMP_DELAY_MS: Long = 600L

/** WebVPN 页面状态探针延迟（登录提交后等待网关跳转/门户渲染）。 */
private const val WEBVPN_PROBE_DELAY_MS: Long = 900L

/**
 * DEBUG 专用：本地 data URL 长页面，用于设备上验证 WEB 模式 WebView 是否填满顶栏以下区域并可滚动
 * （不联网、不含任何真实数据；通过顶栏「布局自测」按钮触发，release 包不显示该按钮）。
 */
private val JW_LAYOUT_SELFTEST_URL: String by lazy {
    val rows = (1..80).joinToString("") { "<div class=\"row\">布局自测 行 $it / 80</div>" }
    val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">""" +
        "<style>body{margin:0;font-family:sans-serif}" +
        "#h{position:sticky;top:0;background:#4E9082;color:#fff;padding:10px;font-size:13px}" +
        ".row{height:44px;line-height:44px;border-bottom:1px solid #ddd;padding-left:12px;font-size:13px}" +
        "</style></head><body><div id=\"h\">布局自测：本页应填满顶栏以下区域，并可滚动到底部</div>$rows" +
        "<script>window.addEventListener('load',function(){" +
        "document.getElementById('h').textContent='布局自测：视口高 '+window.innerHeight+'px，应可滚动到底部';});" +
        "</script></body></html>"
    "data:text/html;charset=utf-8," + Uri.encode(html)
}

/**
 * 教务工具页 v3（规格 `_audit/jw_entry_20261113/JW_CARDS_V1_SPEC.md`，2026-11-14 用户新需求）。
 *
 * - 点「教务」卡 = 直接进入**卡片页**（与班级工具站卡片同风格）：
 *   4 张官方接口数据卡（成绩/考试/选课/公告）+ 8 张官方工具入口卡 + 「打开完整教务官网」；
 * - 登录沿用 v4.46.1：WebView 打开 `sso.jsp` → authserver 自动填充账号密码并提交（验证码/失败回退人工）；
 *   登录成功后从 `CookieManager` 取同域 cookie，由 [JwCardsRepository] 原生 GET 官方 JSON 接口；
 * - 点任意卡片 = App 内置 WebView 全屏打开官方页面，返回回卡片页（写操作只在官方页面内完成）；
 * - **校外 / 非校园网（2026-11-15 新增）**：进入时短超时探测 `210.34.74.77:80`；不可直连则自动打开
 *   学校 WebVPN 登录页 `https://webvpn.fjtcm.edu.cn:4433/login`，用本机 Keystore 保存的教务账号密码
 *   自动填充官方登录页（失败/二次验证/滑块验证码 → 回退人工）；登录成功后在门户页自动找「本科教务」
 *   资源进入 WebVPN 包装后的教务地址（找不到则提示用户手动点）；顶栏/横幅提供「直接打开教务 / 走 WebVPN」
 *   手动入口。WebVPN 模式下官方 JSON 卡片接口不可直连，卡片区提示打开官方页面查看；
 * - cookie/密码只在调用瞬间使用，不落盘、不打日志、不上传；卡片数据只内存展示；
 * - 会话失效（接口返回 HTML/登录页）→ 自动重登 1 次，仍失败显示「登录失效，请重试」。
 *
 * @param sid 当前班级身份学号（默认登录账号；与已存凭据绑定，变化时要求重输）
 * @param onBack 顶栏返回（卡片页 → 退出工具页；WebView 模式 → 回卡片页）
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun JwEntryScreen(
    sid: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state = remember { JwScreenState() }
    val store = remember { JwCredentialStore(context) }
    val repository = remember { JwCardsRepository() }

    val pageClient = remember {
        object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (JwLoginLogic.isAllowedNavigation(url)) return false
                // 校外 http(s)（如教务处外链）：交给系统浏览器，不把内置页带出校内域。
                if (JwLoginLogic.isHttpUrl(url)) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }
                // 非 http(s)（intent:// / market:// / file:// 等）一律阻止。
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                state.loading.value = true
                state.pageUrl = url.orEmpty()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                state.loading.value = false
                val current = url.orEmpty().ifBlank { view.url.orEmpty() }
                state.pageUrl = current
                when (JwLoginLogic.pageKind(current)) {
                    // 统一身份认证登录页：触发自动填充/提交（卡片页/官方页内会话过期也适用）。
                    // 校外经 WebVPN 包装后的 authserver 登录页 host 变为 webvpn 域，但 DOM 相同，
                    // 同样走这条分支（JwLoginLogic.isAuthServerLoginUrl 已兼容）。
                    JwLoginLogic.PageKind.AUTH_LOGIN -> state.authPageSeq.value += 1
                    // 学校 WebVPN 登录页：校外入口，自动填充统一身份认证账号密码。
                    // 同时触发一次探针：个别网关登录成功后 URL 仍停在 /login（SPA 原地切换内容），
                    // 探针能识别「已是门户」并继续自动进教务，避免卡在登录页。
                    JwLoginLogic.PageKind.WEBVPN_LOGIN -> {
                        state.webVpnLoginSeq.value += 1
                        state.webVpnProbeSeq.value += 1
                    }
                    // WebVPN 登录后的门户页（URL 可能是 /、/portal、/home 等）：先用只读探针确认
                    // 页面确实是门户，再自动找「本科教务」资源（修复登录后不自动进教务）。
                    JwLoginLogic.PageKind.WEBVPN_PORTAL -> {
                        state.statusText.value = JwLoginLogic.MSG_WEBVPN_LOGGED_IN
                        state.webVpnProbeSeq.value += 1
                    }
                    // 已通过 WebVPN 进入包装后的教务业务页：全屏展示官方页面。
                    JwLoginLogic.PageKind.WEBVPN_ENTERED -> {
                        state.cookie = null
                        state.statusText.value = null
                        state.manualRequired.value = false
                        state.injectAttempts = 0
                        state.webVpnEntered = true
                        state.webTitle.value = "教务（WebVPN）"
                        if (state.mode.value == JwMode.LOGIN) state.mode.value = JwMode.WEB
                    }
                    // 回到 210.34.74.77 = 已登录进入教务系统（校园网直连）。
                    JwLoginLogic.PageKind.JW_ENTERED -> {
                        state.cookie = CookieManager.getInstance()
                            .getCookie(JwCardCatalog.BASE_URL)
                        state.userAgent = view.settings.userAgentString.orEmpty()
                        state.statusText.value = null
                        state.manualRequired.value = false
                        state.injectAttempts = 0
                        if (state.mode.value == JwMode.LOGIN) {
                            // 登录完成：切到卡片页并拉取官方接口数据。
                            state.mode.value = JwMode.CARDS
                            state.lastUpdated.value = null
                            state.refreshSeq.value += 1
                        }
                    }
                    JwLoginLogic.PageKind.JW_ENTRY -> Unit
                    // 其它页面：若在 WebVPN 域且不是登录页/包装页，可能是登录成功后的 SPA 门户变体，
                    // 同样触发一次探针，避免「URL 不匹配门户判定」导致卡住。
                    JwLoginLogic.PageKind.OTHER -> {
                        if (JwWebVpn.isWebVpnHost(current) &&
                            !JwWebVpn.isWebVpnLoginUrl(current) &&
                            !JwWebVpn.isWrappedUrl(current)
                        ) {
                            state.webVpnProbeSeq.value += 1
                        }
                    }
                }
                // 2026-11-16：自动登录进行中若落到非登录页（CAS 繁忙/错误页），触发一次只读繁忙检测；
                // 识别到「服务器繁忙」立即停止自动重试，改提示手动/浏览器打开。
                val status = state.statusText.value
                if (status == JwLoginLogic.MSG_AUTO_LOGIN ||
                    status == JwLoginLogic.MSG_WEBVPN_AUTO_LOGIN
                ) {
                    state.busyCheckSeq.value += 1
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) {
                    state.loading.value = false
                    state.statusText.value = JwLoginLogic.MSG_LOAD_FAILED
                }
            }
        }
    }

    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.WHITE)  // 设计令牌-允许：WebView 系统白底
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // 2026-11-16：与微信内一致的环境（微信 UA）/第三方 Cookie/DOM storage/mixed content，
            // 避免 CAS 因请求特征差异返回「服务器繁忙」。
            settings.userAgentString = JwLoginLogic.WECHAT_UA
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
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

    // 进入页面先判断本机凭据：sid 匹配 → 直接自动登录；否则弹一次性密码输入（账号默认当前 sid）。
    LaunchedEffect(sid) {
        state.injectAttempts = 0
        state.manualRequired.value = false
        if (sid.isBlank()) {
            state.promptVisible.value = true
            return@LaunchedEffect
        }
        val saved = runCatching { store.load(sid) }.getOrNull()
        if (saved != null && saved.password.isNotEmpty()) {
            state.password.value = saved.password
            state.statusText.value = JwLoginLogic.MSG_AUTO_LOGIN
        } else {
            // 旧身份残留凭据：sid 不匹配时清掉，要求重输（凭据契约 §5.3-2）。
            if (!store.savedSid().isNullOrBlank()) runCatching { store.clear() }
            state.password.value = null
            state.promptVisible.value = true
        }
    }

    /**
     * 进入时探测网络：先短超时直连 `210.34.74.77:80`；
     * 不可直连（校外）→ 自动打开学校 WebVPN 登录页（`webvpn.fjtcm.edu.cn:4433/login`），
     * 可直连 → 走原直进流程（sso.jsp → authserver 自动填充）。
     */
    LaunchedEffect(Unit) {
        state.probing.value = true
        state.statusText.value = JwLoginLogic.MSG_NETWORK_PROBING
        val direct = JwNetworkDetector.directReachable()
        state.directReachable.value = direct
        state.probing.value = false
        val route = JwWebVpn.routeFor(direct)
        state.route.value = route
        if (route == JwWebVpn.Route.WEBVPN) {
            state.statusText.value = JwLoginLogic.MSG_DIRECT_UNAVAILABLE
            webView.loadUrl(JwWebVpn.LOGIN_URL)
        } else {
            state.statusText.value = if (state.password.value.isNullOrEmpty()) {
                null
            } else {
                JwLoginLogic.MSG_AUTO_LOGIN
            }
            webView.loadUrl(JwLoginLogic.ENTRY_URL)
        }
    }

    /**
     * CAS/WebVPN 返回「服务器繁忙」：立即停止自动重试，提示改用手动/浏览器（不循环）。
     */
    fun onServerBusy() {
        state.manualRequired.value = true
        state.busyDetected.value = true
        state.statusText.value = JwLoginLogic.MSG_SERVER_BUSY
    }

    // 2026-11-15：模式切换（CARDS→LOGIN/WEB）后强制 WebView 重新测量。
    // WebView 现在始终保留在组合树（卡片页 1dp 隐藏），切换时 Compose 可能复用旧测量结果，
    // 这里 requestLayout 一次确保填满顶栏以下区域（修复「只显示顶部一小条」）。
    val modeForLayout = state.mode.value
    LaunchedEffect(modeForLayout) {
        if (modeForLayout == JwMode.WEB || modeForLayout == JwMode.LOGIN) {
            delay(40)
            runCatching {
                webView.requestLayout()
                webView.post { webView.requestLayout() }
            }
        }
    }

    // 自动填充 + 提交 + 复核（每次 authserver 登录页加载完成触发一次；失败回退人工、最多 MAX_INJECT_ATTEMPTS 次）。
    val authPageSeq = state.authPageSeq.value
    val injectionPassword = state.password.value
    LaunchedEffect(authPageSeq, injectionPassword) {
        val password = injectionPassword
        if (authPageSeq <= 0 || password.isNullOrEmpty()) return@LaunchedEffect
        if (state.manualRequired.value) return@LaunchedEffect
        if (state.injectAttempts >= JwLoginLogic.MAX_INJECT_ATTEMPTS) {
            state.manualRequired.value = true
            state.statusText.value = JwLoginLogic.MSG_NEED_MANUAL
            return@LaunchedEffect
        }
        state.injectAttempts += 1
        state.statusText.value = JwLoginLogic.MSG_AUTO_LOGIN
        // 2026-11-16：放慢自动填充节奏（页面加载后 700ms 再填、提交前再停 300ms），
        // 模拟人工操作，避免 CAS 风控返回「服务器繁忙」；自动只尝试 1 次。
        delay(JwLoginLogic.INJECT_DELAY_MS)
        if (!JwLoginLogic.isAuthServerLoginUrl(state.pageUrl)) return@LaunchedEffect
        val fillResult = runCatching {
            webView.evaluateJavascriptAwait(JwLoginLogic.buildFillScript(sid, password))
        }.getOrNull()
        if (JwLoginLogic.fillOutcomeOf(JwLoginLogic.parseJsStringResult(fillResult)) !=
            JwLoginLogic.FillOutcome.FILLED
        ) {
            state.manualRequired.value = true
            state.statusText.value = JwLoginLogic.MSG_NEED_MANUAL
            return@LaunchedEffect
        }
        delay(JwLoginLogic.PRE_SUBMIT_DELAY_MS)
        if (!JwLoginLogic.isAuthServerLoginUrl(state.pageUrl)) return@LaunchedEffect
        val submitResult = runCatching {
            webView.evaluateJavascriptAwait(JwLoginLogic.buildSubmitScript())
        }.getOrNull()
        if (JwLoginLogic.loginOutcomeOf(JwLoginLogic.parseJsStringResult(submitResult)) !=
            JwLoginLogic.LoginOutcome.SUBMITTED
        ) {
            state.manualRequired.value = true
            state.statusText.value = JwLoginLogic.MSG_NEED_MANUAL
            return@LaunchedEffect
        }
        // 提交后复核：先识别「服务器繁忙」（风控页可能已不是登录页 URL），命中即停止自动重试。
        delay(JwLoginLogic.VERIFY_DELAY_MS)
        val busyResult = runCatching {
            webView.evaluateJavascriptAwait(JwLoginLogic.buildBusyCheckScript())
        }.getOrNull()
        if (JwLoginLogic.busyOutcomeOf(JwLoginLogic.parseJsStringResult(busyResult)) ==
            JwLoginLogic.BusyOutcome.BUSY
        ) {
            onServerBusy()
            return@LaunchedEffect
        }
        if (!JwLoginLogic.isAuthServerLoginUrl(state.pageUrl)) return@LaunchedEffect
        val checkResult = runCatching {
            webView.evaluateJavascriptAwait(JwLoginLogic.buildResultCheckScript())
        }.getOrNull()
        when (JwLoginLogic.verifyOutcomeOf(JwLoginLogic.parseJsStringResult(checkResult))) {
            JwLoginLogic.VerifyOutcome.ERROR, JwLoginLogic.VerifyOutcome.CAPTCHA -> {
                state.manualRequired.value = true
                state.statusText.value = JwLoginLogic.MSG_NEED_MANUAL
            }
            JwLoginLogic.VerifyOutcome.WAIT -> Unit
        }
    }

    // WebVPN 登录页自动填充 + 提交 + 复核（复用本机 Keystore 的教务账号密码，只输入官方页面）。
    val webVpnLoginSeq = state.webVpnLoginSeq.value
    LaunchedEffect(webVpnLoginSeq, injectionPassword) {
        val password = injectionPassword
        if (webVpnLoginSeq <= 0) return@LaunchedEffect
        if (state.route.value != JwWebVpn.Route.WEBVPN || state.webVpnEntered) return@LaunchedEffect
        if (password.isNullOrEmpty()) {
            // 没有本机密码：不猜、不预填，直接回退手动登录。
            state.manualRequired.value = true
            state.statusText.value = JwLoginLogic.MSG_WEBVPN_NEED_MANUAL
            return@LaunchedEffect
        }
        if (state.webVpnInjectAttempts >= JwLoginLogic.MAX_INJECT_ATTEMPTS) {
            state.manualRequired.value = true
            state.statusText.value = JwLoginLogic.MSG_WEBVPN_NEED_MANUAL
            return@LaunchedEffect
        }
        if (state.manualRequired.value) return@LaunchedEffect
        state.webVpnInjectAttempts += 1
        state.statusText.value = JwLoginLogic.MSG_WEBVPN_AUTO_LOGIN
        // 2026-11-16：同样放慢节奏（700ms 后填、提交前 300ms），自动只尝试 1 次。
        delay(JwLoginLogic.INJECT_DELAY_MS)
        if (!JwWebVpn.isWebVpnLoginUrl(state.pageUrl)) return@LaunchedEffect
        val fillResult = runCatching {
            webView.evaluateJavascriptAwait(JwWebVpn.buildFillScript(sid, password))
        }.getOrNull()
        if (JwWebVpn.fillOutcomeOf(JwLoginLogic.parseJsStringResult(fillResult)) !=
            JwWebVpn.FillOutcome.FILLED
        ) {
            state.manualRequired.value = true
            state.statusText.value = JwLoginLogic.MSG_WEBVPN_NEED_MANUAL
            return@LaunchedEffect
        }
        delay(JwLoginLogic.PRE_SUBMIT_DELAY_MS)
        if (!JwWebVpn.isWebVpnLoginUrl(state.pageUrl)) return@LaunchedEffect
        val submitResult = runCatching {
            webView.evaluateJavascriptAwait(JwWebVpn.buildSubmitScript())
        }.getOrNull()
        if (JwWebVpn.loginOutcomeOf(JwLoginLogic.parseJsStringResult(submitResult)) !=
            JwWebVpn.LoginOutcome.SUBMITTED
        ) {
            state.manualRequired.value = true
            state.statusText.value = JwLoginLogic.MSG_WEBVPN_NEED_MANUAL
            return@LaunchedEffect
        }
        delay(JwLoginLogic.VERIFY_DELAY_MS)
        // 先识别「服务器繁忙」类错误（避免把风控页当普通失败后再次自动尝试）。
        val busyResult = runCatching {
            webView.evaluateJavascriptAwait(JwLoginLogic.buildBusyCheckScript())
        }.getOrNull()
        if (JwLoginLogic.busyOutcomeOf(JwLoginLogic.parseJsStringResult(busyResult)) ==
            JwLoginLogic.BusyOutcome.BUSY
        ) {
            onServerBusy()
            return@LaunchedEffect
        }
        if (!JwWebVpn.isWebVpnLoginUrl(state.pageUrl)) return@LaunchedEffect
        val checkResult = runCatching {
            webView.evaluateJavascriptAwait(JwWebVpn.buildResultCheckScript())
        }.getOrNull()
        when (JwWebVpn.verifyOutcomeOf(JwLoginLogic.parseJsStringResult(checkResult))) {
            JwWebVpn.VerifyOutcome.TWO_STEP -> {
                state.manualRequired.value = true
                state.statusText.value = JwLoginLogic.MSG_WEBVPN_TWO_STEP
            }
            JwWebVpn.VerifyOutcome.ERROR -> {
                state.manualRequired.value = true
                state.statusText.value = JwLoginLogic.MSG_WEBVPN_NEED_MANUAL
            }
            JwWebVpn.VerifyOutcome.LOGGED_IN, JwWebVpn.VerifyOutcome.WAIT -> Unit
        }
    }

    // WebVPN 页面状态探针（2026-11-15）：登录成功后自动确认门户并触发「本科教务」自动点击。
    val webVpnProbeSeq = state.webVpnProbeSeq.value
    LaunchedEffect(webVpnProbeSeq) {
        if (webVpnProbeSeq <= 0) return@LaunchedEffect
        if (state.route.value != JwWebVpn.Route.WEBVPN || state.webVpnEntered) return@LaunchedEffect
        delay(WEBVPN_PROBE_DELAY_MS)
        if (!JwWebVpn.isWebVpnHost(state.pageUrl) || JwWebVpn.isWrappedUrl(state.pageUrl)) return@LaunchedEffect
        val result = runCatching {
            webView.evaluateJavascriptAwait(JwWebVpn.buildPortalProbeScript())
        }.getOrNull()
        when (JwWebVpn.portalProbeOutcomeOf(JwLoginLogic.parseJsStringResult(result))) {
            JwWebVpn.PortalProbeOutcome.PORTAL -> {
                state.webVpnLoggedIn = true
                state.webVpnJumpFailed = false
                state.manualRequired.value = false
                state.statusText.value = JwLoginLogic.MSG_WEBVPN_LOGGED_IN
                state.portalJumpSeq.value += 1
            }
            JwWebVpn.PortalProbeOutcome.LOGIN_FORM -> {
                // 页面仍是登录表单（可能登录未提交/被拒）：有本机密码则再尝试一次自动填充。
                if (!state.password.value.isNullOrEmpty() &&
                    state.webVpnInjectAttempts < JwLoginLogic.MAX_INJECT_ATTEMPTS
                ) {
                    state.webVpnLoginSeq.value += 1
                } else {
                    state.manualRequired.value = true
                    state.statusText.value = JwLoginLogic.MSG_WEBVPN_NEED_MANUAL
                }
            }
            JwWebVpn.PortalProbeOutcome.TWO_STEP -> {
                state.manualRequired.value = true
                state.statusText.value = JwLoginLogic.MSG_WEBVPN_TWO_STEP
            }
            JwWebVpn.PortalProbeOutcome.ERROR -> {
                state.manualRequired.value = true
                state.statusText.value = JwLoginLogic.MSG_WEBVPN_NEED_MANUAL
            }
            JwWebVpn.PortalProbeOutcome.UNKNOWN -> {
                // 无法识别门户特征：给手动入口，用户可在当前页面自行点「本科教务」。
                state.manualRequired.value = true
                state.webVpnJumpFailed = true
                state.statusText.value = JwWebVpn.MSG_PORTAL_MANUAL
            }
        }
    }

    // WebVPN 登录成功后：在门户页自动找「本科教务」资源并进入（找不到提示手动点/给手动按钮）。
    val portalJumpSeq = state.portalJumpSeq.value
    LaunchedEffect(portalJumpSeq) {
        if (portalJumpSeq <= 0) return@LaunchedEffect
        if (state.route.value != JwWebVpn.Route.WEBVPN) return@LaunchedEffect
        if (!JwWebVpn.isWebVpnPortalUrl(state.pageUrl) && !JwWebVpn.isWebVpnHost(state.pageUrl)) {
            return@LaunchedEffect
        }
        // 门户资源列表可能异步渲染：按尝试次数递增延迟（600/1200/1800ms）。
        delay(PORTAL_JUMP_DELAY_MS * state.portalJumpAttempts.coerceAtLeast(1))
        val result = runCatching {
            webView.evaluateJavascriptAwait(JwWebVpn.buildPortalJumpScript())
        }.getOrNull()
        when (JwWebVpn.portalJumpOutcomeOf(JwLoginLogic.parseJsStringResult(result))) {
            JwWebVpn.PortalJumpOutcome.CLICKED -> {
                state.webVpnJumpFailed = false
                state.statusText.value = JwLoginLogic.MSG_WEBVPN_LOGGED_IN
            }
            JwWebVpn.PortalJumpOutcome.NEED_MANUAL -> {
                if (state.portalJumpAttempts < MAX_PORTAL_JUMP_ATTEMPTS) {
                    state.portalJumpAttempts += 1
                    state.portalJumpSeq.value += 1
                } else {
                    state.manualRequired.value = true
                    state.webVpnJumpFailed = true
                    state.statusText.value = JwWebVpn.MSG_PORTAL_MANUAL
                }
            }
        }
    }

    // 2026-11-16：页面加载完成后若仍在自动登录流程，做一次只读「服务器繁忙」检测；
    // 命中即停止自动重试（区分风控繁忙与验证码/密码错误，避免反复提交触发更严格风控）。
    val busyCheckSeq = state.busyCheckSeq.value
    LaunchedEffect(busyCheckSeq) {
        if (busyCheckSeq <= 0) return@LaunchedEffect
        if (state.manualRequired.value) return@LaunchedEffect
        delay(200)
        val raw = runCatching {
            webView.evaluateJavascriptAwait(JwLoginLogic.buildBusyCheckScript())
        }.getOrNull()
        if (JwLoginLogic.busyOutcomeOf(JwLoginLogic.parseJsStringResult(raw)) ==
            JwLoginLogic.BusyOutcome.BUSY
        ) {
            onServerBusy()
        }
    }

    // 拉取 4 个官方接口数据（登录成功后 / 刷新 / 单卡重试）。
    val refreshSeq = state.refreshSeq.value
    LaunchedEffect(refreshSeq) {
        if (refreshSeq <= 0) return@LaunchedEffect
        // 校外 WebVPN 模式：官方 JSON 接口只认直连校园网，卡片数据不可用，提示打开官方页面。
        if (state.route.value == JwWebVpn.Route.WEBVPN) {
            state.cardsLoading.value = false
            state.cardsError.value = JwLoginLogic.MSG_WEBVPN_CARDS_UNAVAILABLE
            state.mode.value = JwMode.CARDS
            return@LaunchedEffect
        }
        state.cardsLoading.value = true
        state.cardsError.value = null
        val cookie = CookieManager.getInstance().getCookie(JwCardCatalog.BASE_URL)
            ?: state.cookie
        if (cookie.isNullOrBlank()) {
            // 登录动作已完成但拿不到同域 cookie：按会话失效处理（重登一次后仍失败则提示）。
            state.cardsLoading.value = false
            state.cardsError.value = JwCardTexts.MSG_LOGIN_EXPIRED
            state.mode.value = JwMode.CARDS
            return@LaunchedEffect
        }
        state.cookie = cookie
        val userAgent = webView.settings.userAgentString.orEmpty()
        state.userAgent = userAgent
        val snapshot = repository.loadAll(cookie, userAgent)
        state.cardsLoading.value = false
        if (snapshot.sessionExpired) {
            if (state.reloginAttempts < JwCardConfig.MAX_RELOGIN_ATTEMPTS) {
                // 规格 §1：会话失效 → 自动重登 1 次（由 WebView 自动登录流程完成）。
                state.reloginAttempts += 1
                state.mode.value = JwMode.LOGIN
                state.statusText.value = JwCardTexts.MSG_RELOGIN
                state.manualRequired.value = false
                state.injectAttempts = 0
                state.loading.value = true
                webView.loadUrl(JwLoginLogic.ENTRY_URL)
            } else {
                state.cardsError.value = JwCardTexts.MSG_LOGIN_EXPIRED
                state.mode.value = JwMode.CARDS
            }
            return@LaunchedEffect
        }
        state.reloginAttempts = 0
        state.cards.value = JwCardMapper.cards(snapshot)
        state.lastUpdated.value = jwUpdatedTimeText()
        state.mode.value = JwMode.CARDS
    }

    fun submitPassword(rawPassword: String, saveToDevice: Boolean) {
        if (JwLoginLogic.validatePassword(rawPassword) != null) return
        if (saveToDevice && sid.isNotBlank()) {
            val saved = runCatching { store.save(sid, rawPassword) }.getOrDefault(false)
            if (!saved) Toast.makeText(context, JwLoginLogic.MSG_SAVE_FAILED, Toast.LENGTH_SHORT).show()
        }
        state.password.value = rawPassword
        state.injectAttempts = 0
        state.manualRequired.value = false
        state.busyDetected.value = false
        state.promptVisible.value = false
        state.statusText.value = JwLoginLogic.MSG_AUTO_LOGIN
        state.reloginAttempts = 0
        state.mode.value = JwMode.LOGIN
        // 登录页若已经加载完成（不再触发 onPageFinished），手动补一次注入触发。
        if (JwLoginLogic.isAuthServerLoginUrl(state.pageUrl)) state.authPageSeq.value += 1
        // 校外 WebVPN 登录页已加载时同样补一次自动填充。
        if (JwWebVpn.isWebVpnLoginUrl(state.pageUrl)) {
            state.webVpnInjectAttempts = 0
            state.webVpnLoginSeq.value += 1
        }
    }

    /** 手动入口：忽略探测结果，直接尝试校园网直连教务。 */
    fun openDirect() {
        state.route.value = JwWebVpn.Route.DIRECT
        state.userOverride = JwWebVpn.Route.DIRECT
        state.webVpnLoggedIn = false
        state.webVpnEntered = false
        state.manualRequired.value = false
        state.busyDetected.value = false
        state.injectAttempts = 0
        state.statusText.value = if (state.password.value.isNullOrEmpty()) {
            null
        } else {
            JwLoginLogic.MSG_AUTO_LOGIN
        }
        state.mode.value = JwMode.LOGIN
        state.loading.value = true
        webView.loadUrl(JwLoginLogic.ENTRY_URL)
    }

    /** 手动入口：忽略探测结果，先登录学校 WebVPN（校外可用）。 */
    fun openWebVpn() {
        state.route.value = JwWebVpn.Route.WEBVPN
        state.userOverride = JwWebVpn.Route.WEBVPN
        state.webVpnLoggedIn = false
        state.webVpnEntered = false
        state.webVpnJumpFailed = false
        state.webVpnInjectAttempts = 0
        state.portalJumpAttempts = 0
        state.webVpnLoginSeq.value = 0
        state.portalJumpSeq.value = 0
        state.webVpnProbeSeq.value = 0
        state.manualRequired.value = false
        state.busyDetected.value = false
        state.statusText.value = JwLoginLogic.MSG_WEBVPN_OPENING
        state.mode.value = JwMode.LOGIN
        state.loading.value = true
        webView.loadUrl(JwWebVpn.LOGIN_URL)
    }

    /** 手动入口：打开 WebVPN 门户（自动跳转失败时，让用户自己在门户里点「本科教务」）。 */
    fun openWebVpnPortal() {
        state.manualRequired.value = false
        state.busyDetected.value = false
        state.webVpnJumpFailed = false
        state.statusText.value = JwWebVpn.MSG_PORTAL_MANUAL
        state.loading.value = true
        webView.loadUrl(JwWebVpn.HOME_URL)
    }

    fun openOfficialPage(url: String, title: String) {
        state.webTitle.value = title
        state.mode.value = JwMode.WEB
        state.loading.value = true
        webView.loadUrl(url)
    }

    /** 用系统浏览器打开当前登录/官方页面（CAS 繁忙时的降级入口，可用微信内置浏览器）。 */
    fun openInSystemBrowser() {
        val url = state.pageUrl.takeIf { JwLoginLogic.isHttpUrl(it) }
            ?: if (state.route.value == JwWebVpn.Route.WEBVPN) JwWebVpn.LOGIN_URL else JwLoginLogic.ENTRY_URL
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    fun clearLogin() {
        runCatching { store.clear() }
        state.password.value = null
        state.injectAttempts = 0
        state.manualRequired.value = false
        state.busyDetected.value = false
        state.statusText.value = null
        state.clearConfirmVisible.value = false
        state.reloginAttempts = 0
        state.cookie = null
        state.userAgent = ""
        state.cards.value = JwCardMapper.loadingCards()
        state.cardsError.value = null
        state.cardsLoading.value = false
        state.lastUpdated.value = null
        state.mode.value = JwMode.LOGIN
        // 清登录同时重置 WebVPN 会话状态（Cookie 统一清除），入口按当前网络探测结果决定。
        state.webVpnLoggedIn = false
        state.webVpnEntered = false
        state.webVpnJumpFailed = false
        state.webVpnInjectAttempts = 0
        state.portalJumpAttempts = 0
        state.webVpnLoginSeq.value = 0
        state.portalJumpSeq.value = 0
        state.webVpnProbeSeq.value = 0
        runCatching { CookieManager.getInstance().removeAllCookies(null) }
        runCatching { CookieManager.getInstance().flush() }
        runCatching { WebStorage.getInstance().deleteAllData() }
        runCatching {
            webView.clearCache(true)
            webView.clearHistory()
            webView.clearFormData()
        }
        webView.loadUrl(
            if (state.route.value == JwWebVpn.Route.WEBVPN) JwWebVpn.LOGIN_URL else JwLoginLogic.ENTRY_URL,
        )
        state.promptVisible.value = true
        Toast.makeText(context, JwLoginLogic.MSG_CLEARED, Toast.LENGTH_SHORT).show()
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
            state.clearConfirmVisible.value -> state.clearConfirmVisible.value = false
            state.promptVisible.value -> state.promptVisible.value = false
            state.mode.value == JwMode.WEB -> state.mode.value = JwMode.CARDS
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
            ctTopBarRaw(
                subtitle = null,
                title = if (mode == JwMode.WEB) {
                    state.webTitle.value ?: JwLoginLogic.PAGE_TITLE
                } else {
                    JwLoginLogic.PAGE_TITLE
                },
                onBack = if (mode == JwMode.WEB) {
                    { state.mode.value = JwMode.CARDS }
                } else {
                    onBack
                },
                actions = {
                    if (mode == JwMode.CARDS) {
                        TextButton(
                            onClick = { state.refreshSeq.value += 1 },
                            enabled = !state.cardsLoading.value,
                        ) { Text(JwCardTexts.BTN_REFRESH) }
                    }
                    // 2026-11-16：CAS 繁忙/自动登录失败时的浏览器降级入口（LOGIN/WEB 模式可见）。
                    if (mode != JwMode.CARDS) {
                        TextButton(onClick = { openInSystemBrowser() }) {
                            Text(JwLoginLogic.BTN_OPEN_BROWSER)
                        }
                    }
                    // DEBUG：设备上验证 WEB 模式 WebView 是否填满顶栏以下并滚动（release 不显示）。
                    if (BuildConfig.DEBUG && mode == JwMode.WEB) {
                        TextButton(onClick = { webView.loadUrl(JW_LAYOUT_SELFTEST_URL) }) {
                            Text("布局自测")
                        }
                    }
                    if (mode != JwMode.WEB) {
                        TextButton(onClick = { state.clearConfirmVisible.value = true }) {
                            Text(JwLoginLogic.BTN_CLEAR)
                        }
                    }
                },
            )

            val showWeb = mode != JwMode.CARDS
            if (showWeb) {
                state.statusText.value?.let { text ->
                    JwStatusBanner(
                        text = text,
                        manual = state.manualRequired.value,
                    )
                }
                if (mode == JwMode.LOGIN) {
                    // 手动入口（契约：允许用户手动「直接打开 / 走 WebVPN」）。
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // WebVPN 自动进入教务失败时的手动入口：打开门户，由用户自己点「本科教务」。
                        if (state.route.value == JwWebVpn.Route.WEBVPN &&
                            (state.webVpnJumpFailed || state.webVpnLoggedIn)
                        ) {
                            TextButton(onClick = { openWebVpnPortal() }) {
                                Text(JwWebVpn.BTN_OPEN_PORTAL)
                            }
                        }
                        TextButton(
                            onClick = { openDirect() },
                            enabled = !state.probing.value,
                        ) { Text(JwLoginLogic.BTN_DIRECT) }
                        TextButton(
                            onClick = { openWebVpn() },
                            enabled = !state.probing.value,
                        ) { Text(JwLoginLogic.BTN_WEBVPN) }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                // 2026-11-15 修复「点官方卡片进入 WebView 只显示顶部一小条」：
                // WebView 始终保留在组合树里（卡片页时隐藏为 1dp），避免从 CARDS→WEB 重新
                // Attach 时 AndroidView 测量到旧/0 高度；update 里显式 MATCH_PARENT 并 requestLayout。
                AndroidView(
                    factory = { webView },
                    modifier = if (showWeb) Modifier.fillMaxSize() else Modifier.size(1.dp),
                    update = { wv ->
                        wv.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        wv.requestLayout()
                    },
                )
                if (mode == JwMode.CARDS) {
                    JwCardsSection(
                        cards = state.cards.value,
                        refreshing = state.cardsLoading.value,
                        lastUpdated = state.lastUpdated.value,
                        globalError = state.cardsError.value,
                        onRefresh = { state.refreshSeq.value += 1 },
                        onOpenDataCard = { card -> openOfficialPage(card.targetUrl, card.kind.title) },
                        onOpenTool = { tool -> openOfficialPage(JwCardCatalog.url(tool.path), tool.title) },
                        onOpenSite = {
                            openOfficialPage(JwCardCatalog.SITE_URL, JwCardTexts.BTN_OPEN_SITE)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (showWeb && state.loading.value) {
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

    if (state.promptVisible.value) {
        JwLoginPromptDialog(
            maskedAccount = JwLoginLogic.maskAccount(sid),
            onDismiss = { state.promptVisible.value = false },
            onSubmit = { password, saveToDevice -> submitPassword(password, saveToDevice) },
        )
    }

    if (state.clearConfirmVisible.value) {
        CtLightAlertDialog(
            onDismissRequest = { state.clearConfirmVisible.value = false },
            title = { Text(JwLoginLogic.CLEAR_CONFIRM_TITLE) },
            text = { Text(JwLoginLogic.CLEAR_CONFIRM_TEXT) },
            confirmButton = {
                TextButton(onClick = { clearLogin() }) { Text(JwLoginLogic.BTN_CLEAR) }
            },
            dismissButton = {
                TextButton(onClick = { state.clearConfirmVisible.value = false }) {
                    Text(JwLoginLogic.BTN_CANCEL)
                }
            },
        )
    }
}

/** 页面运行态（集中放 Compose State，供 WebViewClient 回调与数据拉取协程读写）。 */
private class JwScreenState {
    // ---- 网络探测 / WebVPN（2026-11-15 校外先登录 WebVPN）----
    /** 探测进行中（按钮禁用，状态条显示探测文案）。 */
    val probing = mutableStateOf(false)

    /** 最近一次探测结果：能否直连 210.34.74.77。 */
    val directReachable = mutableStateOf(false)

    /** 当前实际使用的入口（直连 / WebVPN）。 */
    val route = mutableStateOf(JwWebVpn.Route.DIRECT)

    /** 用户手动选择的入口（null = 使用自动探测结果）。 */
    var userOverride: JwWebVpn.Route? = null

    /** 每次 WebVPN 登录页加载完成 +1，用于触发一次自动填充。 */
    val webVpnLoginSeq = mutableStateOf(0)

    /** 每次 WebVPN 门户页加载完成 +1，用于触发一次教务资源跳转。 */
    val portalJumpSeq = mutableStateOf(0)

    /** 每次 WebVPN 页面（门户/登录后变体）+1，用于只读探针确认登录成功/门户态。 */
    val webVpnProbeSeq = mutableStateOf(0)

    /** WebVPN 登录是否成功（已回到门户页）。 */
    var webVpnLoggedIn: Boolean = false

    /** 自动找「本科教务」是否失败（用于展示「打开 WebVPN 门户」手动按钮）。 */
    var webVpnJumpFailed: Boolean = false

    /** 是否已通过 WebVPN 进入包装后的教务业务页。 */
    var webVpnEntered: Boolean = false

    /** 本次 WebVPN 登录页已尝试自动填充次数。 */
    var webVpnInjectAttempts: Int = 0

    /** 本次门户页已尝试自动跳转次数。 */
    var portalJumpAttempts: Int = 0

    /** 2026-11-16：页面加载完成后 +1，触发一次只读「服务器繁忙」检测。 */
    val busyCheckSeq = mutableStateOf(0)

    /** 是否已检测到 CAS/网关「服务器繁忙」（用于停止自动重试、提示手动/浏览器打开）。 */
    val busyDetected = mutableStateOf(false)

    // ---- 登录（沿用 v4.46.1）----
    val promptVisible = mutableStateOf(false)
    val clearConfirmVisible = mutableStateOf(false)
    val password = mutableStateOf<String?>(null)
    val statusText = mutableStateOf<String?>(null)
    val manualRequired = mutableStateOf(false)
    val loading = mutableStateOf(false)

    /** 每次 authserver 登录页加载完成 +1，用于触发一次注入。 */
    val authPageSeq = mutableStateOf(0)

    /** 最近一次页面 URL（WebView 回调更新，用于注入时机判断）。 */
    var pageUrl: String = ""

    /** 本次进入页面已尝试注入次数（防失败页面反复刷新导致死循环）。 */
    var injectAttempts: Int = 0

    // ---- 模式 / 卡片数据 ----
    val mode = mutableStateOf(JwMode.LOGIN)
    val webTitle = mutableStateOf<String?>(null)
    val cards = mutableStateOf(JwCardMapper.loadingCards())
    val cardsLoading = mutableStateOf(false)

    /** 页级错误（登录失效/拿不到 cookie）；单卡错误在卡片内体现。 */
    val cardsError = mutableStateOf<String?>(null)
    val lastUpdated = mutableStateOf<String?>(null)

    /** 每次 +1 触发一次官方接口拉取（登录成功/刷新/重试共用）。 */
    val refreshSeq = mutableStateOf(0)

    /** 本次进入页面已自动重登次数（规格 §1：最多 1 次）。 */
    var reloginAttempts: Int = 0

    /** 登录成功时缓存的同域 cookie / UA（下次拉取优先从 CookieManager 现取）。 */
    var cookie: String? = null
    var userAgent: String = ""
}

/** 一次性密码输入弹层：账号默认当前身份学号（掩码展示），可保存到本机或仅本次打开。 */
@Composable
private fun JwLoginPromptDialog(
    maskedAccount: String,
    onDismiss: () -> Unit,
    onSubmit: (password: String, saveToDevice: Boolean) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit(saveToDevice: Boolean) {
        val message = JwLoginLogic.validatePassword(password)
        if (message != null) {
            error = message
            return
        }
        onSubmit(password, saveToDevice)
    }

    CtLightAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(JwLoginLogic.PROMPT_TITLE) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "学号",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                    Text(
                        text = maskedAccount.ifBlank { "—" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it.take(256)
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("密码") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = {
                        Text(
                            text = error ?: JwLoginLogic.HINT_PASSWORD_LOCAL,
                            color = if (error != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                    ),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Rounded.VisibilityOff
                                } else {
                                    Icons.Rounded.Visibility
                                },
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                            )
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { submit(saveToDevice = true) },
                enabled = password.isNotEmpty(),
            ) { Text(JwLoginLogic.BTN_SAVE_LOGIN) }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { submit(saveToDevice = false) },
                    enabled = password.isNotEmpty(),
                ) { Text(JwLoginLogic.BTN_OPEN_ONLY) }
                TextButton(onClick = onDismiss) { Text(JwLoginLogic.BTN_CANCEL) }
            }
        },
    )
}

/** 自动登录状态提示条（进行中带 loading；失败态显示验证码/VPN 回退说明）。 */
@Composable
private fun JwStatusBanner(text: String, manual: Boolean) {
    val container = if (manual) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (manual) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = container, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!manual) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = content,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = content,
                )
                if (manual) {
                    Text(
                        text = "可点「走 WebVPN」/「浏览器」手动完成（校外可用）",
                        style = MaterialTheme.typography.labelSmall,
                        color = content.copy(alpha = 0.82f),
                    )
                }
            }
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
