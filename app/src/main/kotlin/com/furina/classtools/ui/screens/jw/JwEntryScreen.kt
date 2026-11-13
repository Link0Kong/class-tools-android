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
import com.furina.classtools.data.jw.JwCredentialStore
import com.furina.classtools.features.jw.JwLoginLogic
import com.furina.classtools.ui.components.CtTopBar
import com.furina.classtools.ui.components.ExpressiveBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 教务入口 v2（契约增量 55 §5.0/§5.1/§5.3，2026-11-13 用户新要求）。
 *
 * 点「教务」卡 = **直接进入内置 WebView** 打开 `http://210.34.74.77/sso.jsp`；
 * 跳转到学校统一身份认证登录页后，自动填充账号（当前身份学号）+ 密码并点击提交。
 *
 * - 密码只在**本机**保存：Android Keystore AES/GCM + SharedPreferences（见 [JwCredentialStore]），
 *   不写日志/不上报/不参与同步；顶栏「清除登录」清密码 + Cookie + 缓存；
 * - 无匹配凭据（首次 / 身份切换 / 已清除）先弹一次性密码输入，可「仅打开不保存」；
 * - 注入：onPageFinished 且 URL 为 authserver `/authserver/login` → 300ms 后填表提交 →
 *   800ms 复核 `#showErrorTip` / `#captchaDiv`，失败回退人工，不循环重试；
 * - WebView：JS / DOM storage / Cookie 开，禁 file access，仅允许 http(s)，校外 http(s) 交给系统浏览器；
 * - 仅支持 http(s) 且校内域（教务 IP / 统一认证 / *.fjtcm.edu.cn）在内置页加载。
 *
 * @param sid 当前班级身份学号（默认登录账号；与已存凭据绑定，变化时要求重输）
 * @param onBack 顶栏返回（回到工具页）
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

    val pageClient = remember {
        object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (JwLoginLogic.isAllowedNavigation(url)) return false
                // 校外 http(s)（如教务处外链）：交给系统浏览器，不把内置登录页带出校内域。
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
                    // 统一身份认证登录页：触发自动填充/提交。
                    JwLoginLogic.PageKind.AUTH_LOGIN -> state.authPageSeq.value += 1
                    // 回到 210.34.74.77 = 已登录进入教务系统：清提示、结束自动流程。
                    JwLoginLogic.PageKind.JW_ENTERED -> {
                        state.statusText.value = null
                        state.manualRequired.value = false
                        state.injectAttempts = 0
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
            // 旧身份残留凭据：sid 不匹配时清掉，要求重输（契约 §5.3-2）。
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
        // 登录页若已经加载完成（不再触发 onPageFinished），手动补一次注入触发。
        if (JwLoginLogic.isAuthServerLoginUrl(state.pageUrl)) state.authPageSeq.value += 1
    }

    fun clearLogin() {
        runCatching { store.clear() }
        state.password.value = null
        state.injectAttempts = 0
        state.manualRequired.value = false
        state.statusText.value = null
        state.clearConfirmVisible.value = false
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
            webView.canGoBack() -> webView.goBack()
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
            CtTopBar(
                title = JwLoginLogic.PAGE_TITLE,
                onBack = onBack,
                actions = {
                    TextButton(onClick = { state.clearConfirmVisible.value = true }) {
                        Text(JwLoginLogic.BTN_CLEAR)
                    }
                },
            )

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

/** 页面运行态（集中放 Compose State，供 WebViewClient 回调与注入协程读写）。 */
private class JwScreenState {
    val promptVisible = mutableStateOf(false)
    val clearConfirmVisible = mutableStateOf(false)
    val password = mutableStateOf<String?>(null)
    val statusText = mutableStateOf<String?>(null)
    val manualRequired = mutableStateOf(false)
    val loading = mutableStateOf(true)

    /** 每次 authserver 登录页加载完成 +1，用于触发一次注入。 */
    val authPageSeq = mutableStateOf(0)

    /** 最近一次页面 URL（WebView 回调更新，用于注入时机判断）。 */
    var pageUrl: String = ""

    /** 本次进入页面已尝试注入次数（防失败页面反复刷新导致死循环）。 */
    var injectAttempts: Int = 0
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
