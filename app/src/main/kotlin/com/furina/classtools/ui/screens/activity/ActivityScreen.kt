package com.furina.classtools.ui.screens.activity

import android.annotation.SuppressLint
import android.text.format.DateFormat
import android.util.Log
import android.webkit.CookieManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.furina.classtools.BuildConfig
import com.furina.classtools.data.activity.ActivityRepository
import com.furina.classtools.data.activity.ActivityResult
import com.furina.classtools.data.activity.ActivityToken
import com.furina.classtools.data.activity.ActivityWebSession
import com.furina.classtools.data.activity.ScActivity
import com.furina.classtools.data.activity.ScDetail
import com.furina.classtools.ui.components.CtTopBar
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.theme.CtShapeTokens
import java.util.Date
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

private const val SPA_URL = ActivityWebSession.SPA_LOGIN_URL
private const val LG_INFO_JS = ActivityWebSession.LG_INFO_JS

/**
 * 「活动」工具：学校第二课堂活动列表 / 名额 / 报名。
 *
 * 登录方式：内置 WebView 打开学校网页，用户在页面上用统一身份认证登录；
 * 登录成功后从 localStorage.lgInfo 读回 token（不接触密码），只存本机。
 */
@Composable
fun ActivityScreen(
    repository: ActivityRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf<ActivityToken?>(null) }
    var checked by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<ScActivity>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showLogin by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<ScDetail?>(null) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    suspend fun load() {
        loading = true
        error = null
        when (val r = repository.list()) {
            is ActivityResult.Ok -> items = r.value
            is ActivityResult.Err -> error = r.message
        }
        loading = false
    }

    LaunchedEffect(Unit) {
        token = repository.currentToken()
        checked = true
    }
    LaunchedEffect(token, refreshKey) {
        if (token != null) load()
    }

    fun doSign(id: String, roleId: String) {
        scope.launch {
            busy = true
            when (val r = repository.sign(id, roleId)) {
                is ActivityResult.Ok -> {
                    detail = null
                    detailId = null
                    load()
                }
                is ActivityResult.Err -> error = r.message
            }
            busy = false
        }
    }

    fun doCancel(id: String) {
        scope.launch {
            busy = true
            when (val r = repository.cancel(id)) {
                is ActivityResult.Ok -> {
                    detail = null
                    detailId = null
                    load()
                }
                is ActivityResult.Err -> error = r.message
            }
            busy = false
        }
    }

    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            CtTopBar(title = "活动", onBack = onBack)

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("第二课堂", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (token == null) "未登录 · 数据只存本机" else "共 ${items.size} 条 · 点开看名额",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            when {
                !checked -> LoadingBox()
                token == null -> LoginPrompt(
                    onLogin = { showLogin = true },
                    onBack = onBack,
                )
                loading && items.isEmpty() -> LoadingBox()
                else -> ActivityList(
                    items = items,
                    error = error,
                    detail = detail,
                    detailId = detailId,
                    busy = busy,
                    onOpenDetail = { id ->
                        if (detailId == id) {
                            detailId = null
                            detail = null
                        } else {
                            scope.launch {
                                when (val r = repository.detail(id)) {
                                    is ActivityResult.Ok -> {
                                        detail = r.value
                                        detailId = id
                                    }
                                    is ActivityResult.Err -> error = r.message
                                }
                            }
                        }
                    },
                    onSign = { id, roleId -> doSign(id, roleId) },
                    onCancel = { id -> doCancel(id) },
                )
            }
        }
    }

    if (showLogin) {
        LoginDialog(
            onDismiss = { showLogin = false },
            onToken = { t ->
                scope.launch {
                    repository.saveToken(t)
                    token = t
                    showLogin = false
                    refreshKey++
                }
            },
        )
    }
}

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun LoginPrompt(onLogin: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Text("还没登录学校账号", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "点下面按钮，在打开的学校页面里用统一身份认证登录；登录后回到这里即可看到活动。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onLogin) { Text("登录学校账号") }
        Spacer(Modifier.height(10.dp))
        Text(
            "登录信息只保存在本机，不上传任何服务器。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActivityList(
    items: List<ScActivity>,
    error: String?,
    detail: ScDetail?,
    detailId: String?,
    busy: Boolean,
    onOpenDetail: (String) -> Unit,
    onSign: (String, String) -> Unit,
    onCancel: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (error != null) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂时没有可报名的活动", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id }) { a ->
                Surface(
                    shape = CtShapeTokens.hero,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(a.name, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "报名 ${fmt(a.supBeginTime)} ~ ${fmt(a.supEndTime)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "活动 ${fmt(a.actBeginTime)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onOpenDetail(a.id) }) {
                                Text(if (detailId == a.id) "收起" else "查看名额")
                            }
                        }

                        if (detailId == a.id && detail != null) {
                            Spacer(Modifier.height(8.dp))
                            detail.roles.forEach { role ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(role.name, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            if (role.numLimit == null) {
                                                "已报 ${role.signUpNum} 人"
                                            } else {
                                                "${role.signUpNum}/${role.numLimit}，剩 ${role.left}"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (role.full) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }
                                    Button(
                                        enabled = !busy && !role.full,
                                        onClick = { if (!role.full) onSign(a.id, role.supRoleId) },
                                    ) { Text(if (role.full) "已满" else "报名") }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(enabled = !busy, onClick = { onCancel(a.id) }) {
                                    Text("取消报名")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginDialog(onDismiss: () -> Unit, onToken: (ActivityToken) -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                    Text(
                        "登录学校账号",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "登录后自动返回",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LoginWebView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    onToken = onToken,
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginWebView(modifier: Modifier = Modifier, onToken: (ActivityToken) -> Unit) {
    val context = LocalContext.current
    var routeRecovered by remember { mutableStateOf(false) }
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    val current = url.orEmpty().ifBlank { view.url.orEmpty() }
                    // 兜底：若 H5 路由/存储把学校编号弄成 undefined（历史缓存/旧入口），
                    // 页面会请求 schoolNo=undefined 并弹「未找到对应编号的学校信息」。
                    // 重新用带 #/fjtcm/login 的正确入口加载一次（只重试一次，避免死循环）。
                    if (!routeRecovered && ActivityWebSession.hasUndefinedSchoolNo(current)) {
                        routeRecovered = true
                        view.loadUrl(SPA_URL)
                        return
                    }
                    view.evaluateJavascript(ActivityWebSession.ENSURE_SCHOOL_NO_JS, null)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame && BuildConfig.DEBUG) {
                        Log.d(TAG, "login page error: ${error.errorCode} ${error.description}")
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "console: ${message.message().take(200)}")
                    }
                    return true
                }
            }
            loadUrl(SPA_URL)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { CookieManager.getInstance().flush() }
            runCatching { webView.destroy() }
        }
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1200)
            // 保证 H5 路由能读到正确学校编号（只在缺失/undefined 时写入，幂等）。
            webView.evalJs(ActivityWebSession.ENSURE_SCHOOL_NO_JS)
            val raw = webView.evalJs(LG_INFO_JS) ?: continue
            if (raw.isNotBlank() && BuildConfig.DEBUG) {
                // 只打印键名/类型/长度（脱敏），用于确认 lgInfo 字段与后端 userId/classId 要求一致。
                Log.d(TAG, "lgInfo shape: ${ActivityWebSession.describeLgInfo(raw)}")
            }
            val token = ActivityWebSession.parseLgInfo(raw) ?: continue
            if (BuildConfig.DEBUG) Log.d(TAG, "lgInfo parsed: userId length=${token.userId.length}, classId length=${token.classId.length}")
            CookieManager.getInstance().flush()
            onToken(token)
            break
        }
    }
    AndroidView(factory = { webView }, modifier = modifier)
}

private const val TAG = "ActivityLogin"

private suspend fun WebView.evalJs(script: String): String? =
    suspendCancellableCoroutine { cont ->
        evaluateJavascript(script) { value -> cont.resume(value) }
    }

/** `evaluateJavascript` 返回的是 JSON 字符串，lgInfo 本身又是一段 JSON 文本。 */
internal fun parseLgInfo(raw: String): ActivityToken? = ActivityWebSession.parseLgInfo(raw)

private fun fmt(ms: Long?): String {
    if (ms == null || ms <= 0) return "—"
    return DateFormat.format("MM-dd HH:mm", Date(ms)).toString()
}
