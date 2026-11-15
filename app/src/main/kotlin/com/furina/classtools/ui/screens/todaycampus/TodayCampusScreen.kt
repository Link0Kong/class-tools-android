package com.furina.classtools.ui.screens.todaycampus

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.furina.classtools.data.todaycampus.TodayCampusAppLink
import com.furina.classtools.data.todaycampus.TodayCampusCatalog
import com.furina.classtools.data.todaycampus.TodayCampusEntry
import com.furina.classtools.data.todaycampus.TodayCampusLoginLogic
import com.furina.classtools.data.todaycampus.TodayCampusLoginLogic.FillOutcome
import com.furina.classtools.data.todaycampus.TodayCampusLoginLogic.PageKind
import com.furina.classtools.data.todaycampus.TodayCampusLoginLogic.VerifyOutcome
import com.furina.classtools.ui.components.CtTopBar
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.theme.CtTokens
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

/** 页面模式：首页（服务入口网格 + 搜索）⇄ App 内 WebView（融合门户）。 */
private enum class TodayCampusMode {
    HOME,
    WEB,
}

/**
 * 「今日校园」工具页（复刻 HFUT-Schedule 的 TodayCampus：服务入口网格 + 搜索 + WebView）。
 *
 * ## 功能
 * - 首页：`CtTopBar`「今日校园」+ 返回；搜索框（**本地**过滤入口名/说明/关键词）；
 *   10–14 个静态服务入口（2 列：学工服务、奖助学金、勤工助学、请假申请、困难认定、宿舍服务、
 *   心理健康、一卡通、课表成绩、水电查询、报修/失物、融合门户）；点按 ≥48dp、浅/深色 token；
 * - 点入口 = App 内 WebView 打开学校融合门户 `https://e.fjtcm.edu.cn`（未知子页打开门户首页，
 *   顶部一句提示让用户在官方门户里手动点，**不伪造业务 URL**）；WebView 填满顶栏以下、可滚动，
 *   顶栏返回（回首页）/ 刷新；
 * - **CAS 自动登录**：登录入口 302 到 `authserver.fjtcm.edu.cn/authserver/login`（与教务同一套
 *   Apereo CAS 页面），页面加载后自动填入学号 + [passwordProvider] 提供的统一身份认证密码并提交；
 *   验证码 / 失败 / 无密码 → 一律回退人工，自动只尝试 1 次（不循环、不绕过验证码）；
 *   **密码只在内存暂存、只输入官方页面，不保存、不上传、不打日志**；
 * - 顶栏动作：「打开今日校园 App」（Intent `com.wisedu.cpdaily`，未安装/拉起失败回退门户）、
 *   「融合门户」；
 * - 空态 / 加载态（进度条）/ 错误态（提示 + 重试）/ 已进入提示。
 *
 * @param sid 当前班级身份学号（CAS 账号）
 * @param passwordProvider 本机统一身份认证密码提供者（通常复用 `JwCredentialStore` 的教务密码；
 *   返回 null 表示本机无密码 → 直接人工登录）。只在需要自动填充时调用一次，不落盘、不上传。
 * @param onBack 首页返回（退出工具页）；WebView 模式下系统返回先回首页。
 * @param modifier 页面修饰符（默认填满）。
 */
@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
@Composable
fun TodayCampusScreen(
    sid: String,
    passwordProvider: suspend () -> String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state = remember { TodayCampusScreenState() }
    val latestPasswordProvider = rememberUpdatedState(passwordProvider)

    val pageClient = remember {
        object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (TodayCampusLoginLogic.isAllowedNavigation(url)) return false
                // 校外 http(s) 交系统浏览器；非 http(s)（intent:// / market:// / file:// 等）一律阻止。
                if (TodayCampusLoginLogic.isHttpUrl(url)) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }
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
                when (TodayCampusLoginLogic.pageKind(current)) {
                    // CAS 登录页：触发一次自动填充（失败/验证码回退人工，最多 1 次）。
                    PageKind.AUTH_LOGIN -> {
                        if (!state.manualRequired.value) state.authPageSeq.value += 1
                    }
                    // 回到门户 = 已进入（登录成功或已有会话）。
                    PageKind.PORTAL -> {
                        state.manualRequired.value = false
                        state.injectAttempts = 0
                        state.error.value = null
                        state.status.value = TodayCampusLoginLogic.MSG_ENTERED
                    }
                    PageKind.OTHER -> Unit
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) {
                    state.loading.value = false
                    state.error.value = TodayCampusLoginLogic.MSG_LOAD_FAILED
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                    state.loading.value = false
                    state.error.value = TodayCampusLoginLogic.MSG_LOAD_FAILED
                }
            }
        }
    }

    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // 安全：禁 file/content 访问，只走 http(s) allowlist；不保存表单数据（避免 WebView 记住密码）。
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.loadsImagesAutomatically = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.saveFormData = false
            webViewClient = pageClient
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }
    }

    /** 打开指定官方 URL（入口 / 门户首页 / App 回退共用）。 */
    fun openWeb(url: String, subtitle: String, manualHint: String?) {
        state.targetUrl.value = url
        state.webSubtitle.value = subtitle
        state.manualHint.value = manualHint
        state.mode.value = TodayCampusMode.WEB
        state.error.value = null
        state.manualRequired.value = false
        state.injectAttempts = 0
        state.status.value = TodayCampusLoginLogic.MSG_OPENING
        state.loading.value = true
        webView.loadUrl(url)
    }

    /** 打开一个服务入口：未知子页 → 门户首页 + 一句手动提示。 */
    fun openEntry(entry: TodayCampusEntry) {
        openWeb(
            url = TodayCampusCatalog.openUrlOf(entry),
            subtitle = entry.name,
            manualHint = TodayCampusCatalog.manualHintOf(entry),
        )
    }

    /** 打开融合门户（顶栏动作 / App 未安装回退）：统一走门户登录入口（未登录 → CAS 自动登录）。 */
    fun openPortal() {
        openWeb(
            url = TodayCampusCatalog.PORTAL_ENTRY_URL,
            subtitle = TodayCampusCatalog.BTN_PORTAL,
            manualHint = null,
        )
    }

    /** 返回首页（保留 Cookie/WebView 会话，下次打开更快；内存密码清除）。 */
    fun closeWeb() {
        runCatching { webView.stopLoading() }
        state.mode.value = TodayCampusMode.HOME
        state.webSubtitle.value = null
        state.manualHint.value = null
        state.error.value = null
        state.status.value = null
        state.loading.value = false
        state.manualRequired.value = false
        state.injectAttempts = 0
        state.passwordRequested = false
        state.password = null
    }

    /** WebView 顶栏刷新：重置自动登录状态后重载当前官方页。 */
    fun refreshWeb() {
        state.error.value = null
        state.manualRequired.value = false
        state.injectAttempts = 0
        state.loading.value = true
        runCatching { webView.reload() }
            .onFailure {
                state.loading.value = false
                state.error.value = TodayCampusLoginLogic.MSG_LOAD_FAILED
            }
    }

    /** 错误态重试：重新打开入口 URL（不依赖失败页的 URL）。 */
    fun retryWeb() {
        state.error.value = null
        state.manualRequired.value = false
        state.injectAttempts = 0
        state.loading.value = true
        webView.loadUrl(state.targetUrl.value)
    }

    /** 「打开今日校园 App」：未安装 / 拉起失败 → 提示并回退 App 内融合门户。 */
    fun openTodayCampusApp() {
        fun fallbackToPortal() {
            Toast.makeText(context, TodayCampusAppLink.MSG_NOT_INSTALLED, Toast.LENGTH_SHORT).show()
            openPortal()
        }
        val launchIntent = runCatching {
            context.packageManager.getLaunchIntentForPackage(TodayCampusAppLink.APP_PACKAGE)
        }.getOrNull()
        when (TodayCampusAppLink.targetFor(launchIntent != null)) {
            TodayCampusAppLink.LaunchTarget.APP -> {
                val started = launchIntent?.let { intent ->
                    runCatching { context.startActivity(intent) }.isSuccess
                } ?: false
                if (!started) fallbackToPortal()
            }
            TodayCampusAppLink.LaunchTarget.PORTAL_FALLBACK -> {
                // Android 11+ 包可见性下可能拿不到启动 Intent：仍尝试一次隐式启动，失败才回退门户。
                val implicitIntent = Intent(TodayCampusAppLink.ACTION_MAIN)
                    .addCategory(TodayCampusAppLink.CATEGORY_LAUNCHER)
                    .setPackage(TodayCampusAppLink.APP_PACKAGE)
                val started = runCatching { context.startActivity(implicitIntent) }.isSuccess
                if (!started) fallbackToPortal()
            }
        }
    }

    // CAS 登录页加载完成 → 自动填充一次；验证码 / 失败 / 无密码一律回退人工。
    val authPageSeq = state.authPageSeq.value
    LaunchedEffect(authPageSeq) {
        if (authPageSeq <= 0) return@LaunchedEffect
        if (state.manualRequired.value) return@LaunchedEffect
        if (state.injectAttempts >= TodayCampusLoginLogic.MAX_INJECT_ATTEMPTS) {
            state.manualRequired.value = true
            state.status.value = TodayCampusLoginLogic.MSG_NEED_MANUAL
            return@LaunchedEffect
        }
        val password = cachedPassword(state) { latestPasswordProvider.value.invoke() }
        val invalidReason = TodayCampusLoginLogic.validateAutomation(sid, password)
        if (invalidReason != null) {
            state.manualRequired.value = true
            state.status.value = invalidReason
            return@LaunchedEffect
        }
        state.injectAttempts += 1
        state.status.value = TodayCampusLoginLogic.MSG_AUTO_LOGIN
        delay(TodayCampusLoginLogic.INJECT_DELAY_MS)
        if (!TodayCampusLoginLogic.isCasLoginUrl(state.pageUrl)) return@LaunchedEffect
        val fillRaw = runCatching {
            webView.evaluateJavascriptAwait(
                TodayCampusLoginLogic.buildFillAndSubmitScript(sid, password.orEmpty()),
            )
        }.getOrNull()
        when (TodayCampusLoginLogic.fillOutcomeOf(TodayCampusLoginLogic.parseJsStringResult(fillRaw))) {
            FillOutcome.SUBMITTED -> {
                delay(TodayCampusLoginLogic.VERIFY_DELAY_MS)
                if (!TodayCampusLoginLogic.isCasLoginUrl(state.pageUrl)) return@LaunchedEffect
                val checkRaw = runCatching {
                    webView.evaluateJavascriptAwait(TodayCampusLoginLogic.buildResultCheckScript())
                }.getOrNull()
                when (
                    TodayCampusLoginLogic.verifyOutcomeOf(
                        TodayCampusLoginLogic.parseJsStringResult(checkRaw),
                    )
                ) {
                    VerifyOutcome.ERROR -> {
                        state.manualRequired.value = true
                        state.status.value = TodayCampusLoginLogic.MSG_NEED_MANUAL
                    }
                    VerifyOutcome.CAPTCHA -> {
                        state.manualRequired.value = true
                        state.status.value = TodayCampusLoginLogic.MSG_CAPTCHA
                    }
                    VerifyOutcome.WAIT -> Unit
                }
            }
            FillOutcome.CAPTCHA -> {
                state.manualRequired.value = true
                state.status.value = TodayCampusLoginLogic.MSG_CAPTCHA
            }
            FillOutcome.NEED_MANUAL -> {
                state.manualRequired.value = true
                state.status.value = TodayCampusLoginLogic.MSG_NEED_MANUAL
            }
        }
    }

    // 「已进入融合门户」提示自动收起。
    val statusText = state.status.value
    LaunchedEffect(statusText) {
        if (statusText == TodayCampusLoginLogic.MSG_ENTERED) {
            delay(ENTERED_TIP_DURATION_MS)
            if (state.status.value == TodayCampusLoginLogic.MSG_ENTERED) {
                state.status.value = null
            }
        }
    }

    // WebView 随页面销毁（退出工具页 / 旋转重建）释放，避免内存泄漏；同时清除内存密码。
    DisposableEffect(webView) {
        onDispose {
            state.password = null
            runCatching {
                webView.stopLoading()
                webView.clearHistory()
                webView.removeAllViews()
                webView.destroy()
            }
        }
    }

    BackHandler {
        if (state.mode.value == TodayCampusMode.WEB) {
            closeWeb()
        } else {
            onBack()
        }
    }

    val mode = state.mode.value
    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            CtTopBar(
                title = TodayCampusCatalog.PAGE_TITLE,
                subtitle = if (mode == TodayCampusMode.HOME) {
                    TodayCampusCatalog.SUBTITLE
                } else {
                    state.webSubtitle.value
                },
                onBack = if (mode == TodayCampusMode.WEB) {
                    { closeWeb() }
                } else {
                    onBack
                },
                actions = {
                    if (mode == TodayCampusMode.WEB) {
                        IconButton(
                            onClick = { refreshWeb() },
                            modifier = Modifier.size(CtTokens.Size.minTouchTarget),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = TodayCampusCatalog.BTN_REFRESH,
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { openTodayCampusApp() },
                            modifier = Modifier.size(CtTokens.Size.minTouchTarget),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhoneAndroid,
                                contentDescription = TodayCampusCatalog.BTN_OPEN_APP,
                            )
                        }
                        TextButton(
                            onClick = { openPortal() },
                            modifier = Modifier.heightIn(min = CtTokens.Size.minTouchTarget),
                        ) {
                            Text(TodayCampusCatalog.BTN_PORTAL)
                        }
                    }
                },
            )

            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (mode == TodayCampusMode.WEB) {
                    state.manualHint.value?.let { hint ->
                        TodayCampusNoticeBar(
                            message = hint,
                            actionLabel = TodayCampusCatalog.BTN_KNOW,
                            onAction = { state.manualHint.value = null },
                        )
                    }
                    state.error.value?.let { message ->
                        TodayCampusNoticeBar(
                            message = message,
                            isError = true,
                            actionLabel = TodayCampusCatalog.BTN_RETRY,
                            onAction = { retryWeb() },
                        )
                    }
                    if (state.error.value == null) {
                        state.status.value?.let { message ->
                            TodayCampusNoticeBar(
                                message = message,
                                isError = state.manualRequired.value,
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // WebView 常驻组合树（首页隐藏为 1dp）：复用 Cookie 会话，返回首页不销毁。
                    AndroidView(
                        factory = { webView },
                        modifier = if (mode == TodayCampusMode.WEB) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier.size(1.dp)
                        },
                    )
                    if (mode == TodayCampusMode.HOME) {
                        TodayCampusHome(
                            query = state.query.value,
                            onQueryChange = { state.query.value = it },
                            onOpenEntry = { entry -> openEntry(entry) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (mode == TodayCampusMode.WEB && state.loading.value) {
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

/** 「已进入融合门户」提示保留时长。 */
private const val ENTERED_TIP_DURATION_MS: Long = 2500L

/** 页面运行态（集中放 Compose State，供 WebViewClient 回调与自动登录协程读写）。 */
private class TodayCampusScreenState {
    /** 当前模式：首页 / WebView。 */
    val mode = mutableStateOf(TodayCampusMode.HOME)

    /** 搜索词（首页本地过滤）。 */
    val query = mutableStateOf("")

    /** WebView 顶部副标题（入口名 / 融合门户）。 */
    val webSubtitle = mutableStateOf<String?>(null)

    /** 未知子页入口的手动提示（一句话；已知子页为 null）。 */
    val manualHint = mutableStateOf<String?>(null)

    /** WebView 主文档加载中（顶部线性进度条）。 */
    val loading = mutableStateOf(false)

    /** 自动登录/状态一句话（正常态）。 */
    val status = mutableStateOf<String?>(null)

    /** 页面加载错误（带重试）。 */
    val error = mutableStateOf<String?>(null)

    /** 自动登录已回退人工（状态条用错误色，且不再自动尝试）。 */
    val manualRequired = mutableStateOf(false)

    /** 当前 WebView 打开的目标 URL（重试用；不依赖失败页 URL）。 */
    val targetUrl = mutableStateOf(TodayCampusCatalog.PORTAL_ENTRY_URL)

    /** 每次 CAS 登录页加载完成 +1，用于触发一次自动填充。 */
    val authPageSeq = mutableStateOf(0)

    /** 最近一次页面 URL（WebView 回调更新，注入时机判断用）。 */
    var pageUrl: String = ""

    /** 本次进入页面已自动尝试次数（防循环；上限 1 次）。 */
    var injectAttempts: Int = 0

    /** [passwordProvider] 是否已调用过（只调用一次）。 */
    var passwordRequested: Boolean = false

    /** 内存中的密码（仅自动填充时临时使用；退出 WebView / 页面销毁即清空）。 */
    var password: String? = null
}

/**
 * 取一次密码提供者结果并缓存（只调用一次；异常按「无密码」处理，避免阻塞人工登录）。
 * 取消协程时继续抛出，保证 `LaunchedEffect` 生命周期正确。
 */
private suspend fun cachedPassword(
    state: TodayCampusScreenState,
    provider: suspend () -> String?,
): String? {
    if (!state.passwordRequested) {
        state.passwordRequested = true
        state.password = try {
            provider()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (ignored: Throwable) {
            null
        }?.takeIf { it.isNotEmpty() }
    }
    return state.password
}

/** WebView 页内提示条（手动提示 / 加载失败 / 自动登录状态；48dp 触控）。 */
@Composable
private fun TodayCampusNoticeBar(
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val container = if (isError) scheme.errorContainer else scheme.surfaceContainerHigh
    val content = if (isError) scheme.onErrorContainer else scheme.onSurfaceVariant
    Surface(color = container, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .heightIn(min = CtTokens.Size.minTouchTarget)
                .padding(start = CtTokens.Spacing.lg, end = CtTokens.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CtTokens.Spacing.sm),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.heightIn(min = CtTokens.Size.minTouchTarget),
                ) {
                    Text(actionLabel)
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
