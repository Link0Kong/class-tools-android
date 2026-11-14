package com.furina.classtools.ui.screens.jw

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.material3.AlertDialog
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
import com.furina.classtools.data.jw.JwCardsRepository
import com.furina.classtools.data.jw.JwCredentialStore
import com.furina.classtools.features.jw.JwLoginLogic
import com.furina.classtools.ui.components.CtTopBar
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

/**
 * 教务工具页 v3（规格 `_audit/jw_entry_20261113/JW_CARDS_V1_SPEC.md`，2026-11-14 用户新需求）。
 *
 * - 点「教务」卡 = 直接进入**卡片页**（与班级工具站卡片同风格）：
 *   4 张官方接口数据卡（成绩/考试/选课/公告）+ 8 张官方工具入口卡 + 「打开完整教务官网」；
 * - 登录沿用 v4.46.1：WebView 打开 `sso.jsp` → authserver 自动填充账号密码并提交（验证码/失败回退人工）；
 *   登录成功后从 `CookieManager` 取同域 cookie，由 [JwCardsRepository] 原生 GET 官方 JSON 接口；
 * - 点任意卡片 = App 内置 WebView 全屏打开官方页面，返回回卡片页（写操作只在官方页面内完成）；
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
                    JwLoginLogic.PageKind.AUTH_LOGIN -> state.authPageSeq.value += 1
                    // 回到 210.34.74.77 = 已登录进入教务系统。
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
                    JwLoginLogic.PageKind.JW_ENTRY, JwLoginLogic.PageKind.OTHER -> Unit
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
            setBackgroundColor(android.graphics.Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
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

    LaunchedEffect(Unit) {
        webView.loadUrl(JwLoginLogic.ENTRY_URL)
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
        delay(JwLoginLogic.INJECT_DELAY_MS)
        if (!JwLoginLogic.isAuthServerLoginUrl(state.pageUrl)) return@LaunchedEffect
        val fillResult = runCatching {
            webView.evaluateJavascriptAwait(JwLoginLogic.buildFillAndSubmitScript(sid, password))
        }.getOrNull()
        val token = JwLoginLogic.parseJsStringResult(fillResult)
        if (JwLoginLogic.loginOutcomeOf(token) != JwLoginLogic.LoginOutcome.SUBMITTED) {
            state.manualRequired.value = true
            state.statusText.value = JwLoginLogic.MSG_NEED_MANUAL
            return@LaunchedEffect
        }
        // 提交后复核：仍停在登录页且出现错误提示/验证码 → 回退人工。
        delay(JwLoginLogic.VERIFY_DELAY_MS)
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

    // 拉取 4 个官方接口数据（登录成功后 / 刷新 / 单卡重试）。
    val refreshSeq = state.refreshSeq.value
    LaunchedEffect(refreshSeq) {
        if (refreshSeq <= 0) return@LaunchedEffect
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
        state.promptVisible.value = false
        state.statusText.value = JwLoginLogic.MSG_AUTO_LOGIN
        state.reloginAttempts = 0
        state.mode.value = JwMode.LOGIN
        // 登录页若已经加载完成（不再触发 onPageFinished），手动补一次注入触发。
        if (JwLoginLogic.isAuthServerLoginUrl(state.pageUrl)) state.authPageSeq.value += 1
    }

    fun openOfficialPage(url: String, title: String) {
        state.webTitle.value = title
        state.mode.value = JwMode.WEB
        state.loading.value = true
        webView.loadUrl(url)
    }

    fun clearLogin() {
        runCatching { store.clear() }
        state.password.value = null
        state.injectAttempts = 0
        state.manualRequired.value = false
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
        runCatching { CookieManager.getInstance().removeAllCookies(null) }
        runCatching { CookieManager.getInstance().flush() }
        runCatching { WebStorage.getInstance().deleteAllData() }
        runCatching {
            webView.clearCache(true)
            webView.clearHistory()
            webView.clearFormData()
        }
        webView.loadUrl(JwLoginLogic.ENTRY_URL)
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
            CtTopBar(
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
                    if (mode != JwMode.WEB) {
                        TextButton(onClick = { state.clearConfirmVisible.value = true }) {
                            Text(JwLoginLogic.BTN_CLEAR)
                        }
                    }
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
                    modifier = Modifier.weight(1f),
                )
            } else {
                state.statusText.value?.let { text ->
                    JwStatusBanner(
                        text = text,
                        manual = state.manualRequired.value,
                    )
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

    if (state.promptVisible.value) {
        JwLoginPromptDialog(
            maskedAccount = JwLoginLogic.maskAccount(sid),
            onDismiss = { state.promptVisible.value = false },
            onSubmit = { password, saveToDevice -> submitPassword(password, saveToDevice) },
        )
    }

    if (state.clearConfirmVisible.value) {
        AlertDialog(
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

    AlertDialog(
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
                        text = "校外打不开可先连学校 VPN",
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
