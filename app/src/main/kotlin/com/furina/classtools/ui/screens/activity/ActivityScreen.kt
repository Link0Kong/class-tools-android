package com.furina.classtools.ui.screens.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.util.Log
import android.webkit.CookieManager
import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.furina.classtools.data.activity.ActivityFileChooserLogic
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
    var hint by remember { mutableStateOf<String?>(null) }
    var reloadSeq by remember { mutableIntStateOf(0) }
    var selfTestSeq by remember { mutableIntStateOf(0) }
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
                    // DEBUG：本地 `<input type=file>` data URL 自测（验证 onShowFileChooser 生效）。
                    if (BuildConfig.DEBUG) {
                        TextButton(onClick = { selfTestSeq += 1 }) { Text("上传自测") }
                    }
                    TextButton(onClick = { reloadSeq += 1 }) { Text("重新加载") }
                }
                hint?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                    )
                }
                LoginWebView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    reloadSeq = reloadSeq,
                    selfTestSeq = selfTestSeq,
                    onHint = { hint = it },
                    onToken = onToken,
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginWebView(
    modifier: Modifier = Modifier,
    reloadSeq: Int = 0,
    selfTestSeq: Int = 0,
    onHint: (String?) -> Unit = {},
    onToken: (ActivityToken) -> Unit,
) {
    val context = LocalContext.current
    var routeRecovered by remember { mutableStateOf(false) }
    var oauthRecovered by remember { mutableStateOf(false) }
    var stubInjected by remember { mutableStateOf(false) }
    // 2026-11-16：WebView 内 `<input type=file>`（上传头像）需要 onShowFileChooser + 系统选择器；
    // 回调必须只回传一次（选中/取消都要回调，否则 input 永久卡住）。
    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        if (callback == null) return@rememberLauncherForActivityResult
        val picked = ActivityFileChooserLogic.urisFromResult(
            resultOk = result.resultCode == Activity.RESULT_OK,
            singleUri = result.data?.data?.toString(),
            clipUris = result.data?.clipData?.let { clip ->
                (0 until clip.itemCount).mapNotNull { index -> clip.getItemAt(index).uri?.toString() }
            },
        )
        runCatching {
            callback.onReceiveValue(picked?.map { Uri.parse(it) }?.toTypedArray())
        }
    }
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            // 2026-11-15：学校活动 H5 是微信内应用；普通 WebView UA 会被微信 OAuth 拦截页
            // 提示「请在微信客户端/小程序打开」。这里固定微信 UA（含 MicroMessenger/XWEB/Weixin），
            // 再配合 SEED_WX_SESSION_JS 环境桩，让 H5 走学校登录页而不是微信 OAuth。
            settings.userAgentString = ActivityWebSession.WECHAT_UA
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val url = request.url.toString()
                    // 1) 微信 OAuth 跳转：拦截并使用环境桩重新加载（否则用户只会看到微信提示页）。
                    if (ActivityWebSession.isWechatOauthUrl(url)) {
                        onHint(ActivityWebSession.MSG_OAUTH_BLOCKED)
                        if (!oauthRecovered) {
                            oauthRecovered = true
                            view.evaluateJavascript(ActivityWebSession.SEED_WX_SESSION_JS, null)
                            view.loadUrl(SPA_URL)
                        }
                        return true
                    }
                    // 2) 只允许 http(s)；weixin:// 等自定义 scheme 一律阻止（避免调起微信/其它 App）。
                    return !(url.startsWith("http://") || url.startsWith("https://"))
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    // 尽早注入环境桩/会话桩（文档刚创建时；失败也无副作用，onPageFinished 会再注入一次）。
                    view.evaluateJavascript(ActivityWebSession.SEED_WX_SESSION_JS, null)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    val current = url.orEmpty().ifBlank { view.url.orEmpty() }
                    // 环境桩注入：保证 H5 不再跳微信 OAuth（幂等，可用于后续刷新）。
                    view.evaluateJavascript(ActivityWebSession.SEED_WX_SESSION_JS, null)
                    if (!stubInjected) {
                        stubInjected = true
                        view.evaluateJavascript(ActivityWebSession.WX_ENV_STUB_JS, null)
                    }
                    // 兜底：若 H5 路由/存储把学校编号弄成 undefined（历史缓存/旧入口），
                    // 页面会请求 schoolNo=undefined 并弹「未找到对应编号的学校信息」。
                    // 重新用带 #/fjtcm/login 的正确入口加载一次（只重试一次，避免死循环）。
                    if (!routeRecovered && ActivityWebSession.hasUndefinedSchoolNo(current)) {
                        routeRecovered = true
                        view.loadUrl(SPA_URL)
                        return
                    }
                    // 兜底：如果仍停留在微信 OAuth 页面（拦截没命中的重定向），用环境桩重载 SPA。
                    if (!oauthRecovered && ActivityWebSession.isWechatOauthUrl(current)) {
                        oauthRecovered = true
                        view.loadUrl(SPA_URL)
                    }
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

                /**
                 * 2026-11-16 修复「上传头像点击无反应」：WebView 内 `<input type=file>` 默认不处理，
                 * 必须返回 true 并调起系统文件选择器；结果（含取消）必须回传 filePathCallback。
                 */
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?,
                ): Boolean {
                    // 防御：上一次未回调的请求先取消，避免 input 卡死。
                    runCatching { pendingFileCallback?.onReceiveValue(null) }
                    pendingFileCallback = filePathCallback
                    if (filePathCallback == null) return false
                    val acceptTypes = ActivityFileChooserLogic.normalizeAcceptTypes(
                        fileChooserParams?.acceptTypes?.toList(),
                    )
                    val multiple = ActivityFileChooserLogic.isMultiple(fileChooserParams?.mode)
                    val mime = ActivityFileChooserLogic.primaryMimeType(acceptTypes)
                    fun buildIntent(): Intent =
                        (runCatching { fileChooserParams?.createIntent() }.getOrNull()
                            ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = mime
                            }).apply {
                            if (type.isNullOrBlank()) type = mime
                            putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes.toTypedArray())
                            if (multiple) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        }
                    val launched = runCatching { filePickerLauncher.launch(buildIntent()) }.isSuccess
                    if (!launched) {
                        // 选择器不可用：回退 ACTION_GET_CONTENT；仍失败则回传 null（不阻塞页面）。
                        val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = mime
                            if (multiple) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        }
                        val fallbackOk = runCatching { filePickerLauncher.launch(fallback) }.isSuccess
                        if (!fallbackOk) {
                            pendingFileCallback = null
                            runCatching { filePathCallback.onReceiveValue(null) }
                            if (BuildConfig.DEBUG) Log.w(TAG, "file chooser unavailable (no activity)")
                            return false
                        }
                    }
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "file chooser shown: mime=$mime multiple=$multiple")
                    }
                    return true
                }
            }
            loadUrl(SPA_URL)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            // 页面销毁时取消未完成的文件选择回调，避免 input/filePathCallback 泄漏。
            runCatching { pendingFileCallback?.onReceiveValue(null) }
            pendingFileCallback = null
            runCatching { CookieManager.getInstance().flush() }
            runCatching { webView.destroy() }
        }
    }
    // 「重新加载」按钮：先补环境桩，再重载 H5。
    LaunchedEffect(reloadSeq) {
        if (reloadSeq <= 0) return@LaunchedEffect
        webView.evalJs(ActivityWebSession.SEED_WX_SESSION_JS)
        webView.evalJs(ActivityWebSession.WX_ENV_STUB_JS)
        webView.loadUrl(SPA_URL)
    }
    // DEBUG 自测：加载本地 data URL `<input type=file>` 页面，验证 onShowFileChooser 生效。
    LaunchedEffect(selfTestSeq) {
        if (selfTestSeq <= 0) return@LaunchedEffect
        onHint("上传自测：点击页面里的「选择文件」应弹出系统选择器")
        webView.loadUrl(FILE_SELF_TEST_DATA_URL)
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1200)
            // 环境桩（幂等）+ 学校编号兜底（只在缺失/undefined 时写入）。
            webView.evalJs(ActivityWebSession.SEED_WX_SESSION_JS)
            webView.evalJs(ActivityWebSession.ENSURE_SCHOOL_NO_JS)
            // 兜底：个别 H5 版本/缓存仍弹「请在微信…」提示时，尝试重载一次。
            val promptText = webView.evalJs(WECHAT_PROMPT_JS)
            if (promptText == "prompt") {
                onHint(ActivityWebSession.MSG_OAUTH_BLOCKED)
                if (!oauthRecovered) {
                    oauthRecovered = true
                    webView.loadUrl(SPA_URL)
                }
            }
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

/**
 * 页面是否出现微信打开提示（只读检测 + 自动关掉 H5 的「服务器异常」弹层）。
 *
 * 返回 `prompt` 表示出现「请在微信…」提示；`dismissed` 表示关掉了一个非致命弹层；其它为 `ok`。
 */
private const val WECHAT_PROMPT_JS = """
    (function () {
      try {
        var text = (document.body && document.body.innerText) || '';
        if (text.indexOf('请在微信') >= 0) { return 'prompt'; }
        var dialogs = document.querySelectorAll('.weui-dialog__btn_primary, .vux-dialog .weui-dialog__btn_primary, .vux-x-dialog .weui-dialog__btn_primary');
        for (var i = 0; i < dialogs.length; i++) {
          var btn = dialogs[i];
          var label = String(btn.innerText || '').replace(/\s+/g, '');
          if (label.indexOf('确定') < 0 && label.indexOf('知道了') < 0) { continue; }
          var box = btn.closest('.weui-dialog, .vux-dialog, .vux-x-dialog') || btn.parentElement;
          var boxText = box ? String(box.innerText || '') : '';
          if (boxText.indexOf('服务器异常') >= 0 || boxText.indexOf('请稍后重试') >= 0) {
            btn.click();
            return 'dismissed';
          }
        }
        return 'ok';
      } catch (e) {
        return 'ok';
      }
    })()
"""

private const val TAG = "ActivityLogin"

/**
 * DEBUG 专用：本地 data URL 的 `<input type=file>` 自测页（不联网、无账号数据）。
 *
 * 用于设备上验证「上传头像点击无反应」修复：点「上传自测」→ 点页面里的选择文件 →
 * 应弹出系统文件选择器；选择后页面显示文件名/大小（即 onShowFileChooser 回调链路正常）。
 */
private val FILE_SELF_TEST_DATA_URL: String by lazy {
    val html = "<!doctype html><html><head><meta charset='utf-8'>" +
        "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
        "<style>body{font-family:sans-serif;padding:16px}h3{margin:0 0 8px}" +
        "input,button{font-size:16px;margin-top:12px}#out{color:#4E9082;margin-top:12px}</style></head><body>" +
        "<h3>上传自测（onShowFileChooser）</h3><p>点击下面按钮选择图片（与活动头像上传同款 input）：</p>" +
        "<input id='pick' type='file' accept='image/*'><p id='out'>（尚未选择）</p>" +
        "<script>var p=document.getElementById('pick');p.addEventListener('change',function(){" +
        "var f=p.files&&p.files.length?p.files[0]:null;" +
        "document.getElementById('out').textContent=f?'已选择：'+f.name+'（'+f.size+' B）':'（未选择）';});</script>" +
        "</body></html>"
    "data:text/html;charset=utf-8," + Uri.encode(html)
}

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
