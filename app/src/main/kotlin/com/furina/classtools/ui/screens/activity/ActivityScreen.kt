package com.furina.classtools.ui.screens.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.furina.classtools.data.activity.ActivityWeChatLogin
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
 * ## 2026-11-16 task-73 模拟器实测结论（activity H5 登录）
 *
 * - 登录页是**学校账号登录**（上传真实头像 + 身份证后六位 + 学号；另有「我是教师」）；
 * - **头像上传不是 `<input type=file>`**：组件点击走微信 JSSDK `wx.chooseImage/uploadImage`，
 *   由学校后端从微信拉取照片；App 内 WebView 没有微信 JSSDK 桥，点击头像**无任何反应**
 *   （实测无 file input / 无 onShowFileChooser / 无请求）；
 * - 不传头像点登录 → 客户端提示「请上传头像」；设置头像 URL 后点登录 →
 *   `POST /basic/user/student/bind` 返回 HTTP 500「openId不能为空」→ **学校账号绑定也要求微信 openId**；
 * - openId 只能由微信客户端内的学校 H5 微信授权（oauth2 authorize + code 换 token）取得，App 无法合法获取。
 *
 * 因此 UI 改为**双主路径 + 诚实降级**（[ActivityWeChatLogin]）：
 * 1) 主路径「在微信内打开活动页」：复制 H5 链接并尝试唤起微信，微信内可完成微信登录或学校账号登录；
 * 2) 次路径「学校账号登录」：App 内仍可打开官方登录页查看，但明确提示该页依赖微信 openId/头像、
 *    App 内无法完成，引导改用微信内打开；
 * - 登录页出现「openId不能为空 / 请上传头像」时，App 内显示「去微信内打开」提示；
 * - **不伪造 openId/登录态**：`lgInfo` 仍只认 H5 真实会话；原有微信 UA + 环境桩保留，
 *   让 H5 在 App 内回落到学校登录页（而不是微信 OAuth 提示页）。
 */
@Composable
fun ActivityScreen(
    repository: ActivityRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
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
    // 2026-11-16：微信登录引导状态（检测结果 + 操作反馈）。
    var wechatPresence by remember { mutableStateOf(ActivityWeChatLogin.Presence.UNKNOWN) }
    var wechatHint by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = true
        error = null
        when (val r = repository.list()) {
            is ActivityResult.Ok -> items = r.value
            is ActivityResult.Err -> error = r.message
        }
        loading = false
    }

    /** 复制活动 H5 链接（供微信内打开）。 */
    fun copyActivityLink() {
        wechatHint = if (copyLinkToClipboard(context, ActivityWeChatLogin.shareUrl())) {
            ActivityWeChatLogin.MSG_LINK_COPIED
        } else {
            ActivityWeChatLogin.MSG_COPY_FAILED
        }
    }

    /** 在微信中打开：复制链接 + 尝试微信内打开 / 拉起微信；失败明确降级（不假装已登录）。 */
    fun openActivityInWeChat() {
        val url = ActivityWeChatLogin.shareUrl()
        val copied = copyLinkToClipboard(context, url)
        val launched = launchWeChat(context, url)
        wechatHint = when {
            launched -> ActivityWeChatLogin.MSG_WECHAT_OPENED
            !copied -> ActivityWeChatLogin.MSG_COPY_FAILED
            wechatPresence == ActivityWeChatLogin.Presence.NOT_INSTALLED ->
                ActivityWeChatLogin.MSG_WECHAT_MISSING
            else -> ActivityWeChatLogin.MSG_WECHAT_UNKNOWN_AFTER_FAIL
        }
    }

    LaunchedEffect(Unit) {
        wechatPresence = detectWeChatPresence(context)
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
                    wechatPresence = wechatPresence,
                    wechatHint = wechatHint,
                    onOpenInWeChat = { openActivityInWeChat() },
                    onCopyLink = { copyActivityLink() },
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
            wechatPresence = wechatPresence,
            wechatHint = wechatHint,
            onOpenInWeChat = { openActivityInWeChat() },
            onCopyLink = { copyActivityLink() },
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
private fun LoginPrompt(
    onLogin: () -> Unit,
    onBack: () -> Unit,
    wechatPresence: ActivityWeChatLogin.Presence = ActivityWeChatLogin.Presence.UNKNOWN,
    wechatHint: String? = null,
    onOpenInWeChat: () -> Unit = {},
    onCopyLink: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("还没登录学校账号", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "活动 H5 登录需要微信环境（openId 只能微信客户端授权取得），推荐在微信内打开完成登录。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ---- 主路径 1：在微信内打开（完整可用）----
        Spacer(Modifier.height(18.dp))
        Text(
            ActivityWeChatLogin.TITLE_OPEN_IN_WECHAT,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            ActivityWeChatLogin.DESC_OPEN_IN_WECHAT,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onCopyLink,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text(ActivityWeChatLogin.BTN_COPY_LINK) }
            Button(
                onClick = onOpenInWeChat,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text(ActivityWeChatLogin.BTN_OPEN_IN_WECHAT) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            ActivityWeChatLogin.statusText(wechatPresence),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        wechatHint?.takeIf { it.isNotBlank() }?.let { text ->
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // ---- 次路径：App 内学校登录页（受限，仅查看/备用）----
        Spacer(Modifier.height(18.dp))
        Text(
            ActivityWeChatLogin.TITLE_SCHOOL_LOGIN_LIMITED,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            ActivityWeChatLogin.DESC_SCHOOL_LOGIN_LIMITED,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(ActivityWeChatLogin.BTN_OPEN_SCHOOL_LOGIN) }

        Spacer(Modifier.height(14.dp))
        Text(
            ActivityWeChatLogin.HINT_OPENID_LIMIT,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            ActivityWeChatLogin.HINT_DEGRADED,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "登录信息只保存在本机，不上传任何服务器。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
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
private fun LoginDialog(
    onDismiss: () -> Unit,
    onToken: (ActivityToken) -> Unit,
    wechatPresence: ActivityWeChatLogin.Presence = ActivityWeChatLogin.Presence.UNKNOWN,
    wechatHint: String? = null,
    onOpenInWeChat: () -> Unit = {},
    onCopyLink: () -> Unit = {},
) {
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
                // 2026-11-16 task-73：该登录页依赖微信 openId/微信头像上传，App 内无法完成；
                // 顶部明确提示并给出「在微信中打开」出口（不是 App 内微信登录，不会伪造登录态）。
                LoginLimitedStrip(
                    presence = wechatPresence,
                    hint = wechatHint,
                    onOpenInWeChat = onOpenInWeChat,
                    onCopyLink = onCopyLink,
                )
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

/** 登录 WebView 顶部的「App 内登录受限」提示条：明确该页需要微信能力 + 微信内打开出口。 */
@Composable
private fun LoginLimitedStrip(
    presence: ActivityWeChatLogin.Presence,
    hint: String?,
    onOpenInWeChat: () -> Unit,
    onCopyLink: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = ActivityWeChatLogin.DESC_SCHOOL_LOGIN_LIMITED,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCopyLink) { Text(ActivityWeChatLogin.BTN_COPY_LINK) }
            TextButton(onClick = onOpenInWeChat) {
                Text(ActivityWeChatLogin.BTN_OPEN_IN_WECHAT)
            }
        }
        Text(
            text = ActivityWeChatLogin.statusText(presence),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        hint?.takeIf { it.isNotBlank() }?.let { text ->
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 3,
            )
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
                    // 2) 只允许 http(s)；weixin:// 等自定义 scheme 一律阻止（避免页面静默调起微信）。
                    //    微信登录引导由界面上的「在微信中打开」按钮负责（复制链接 + 显式 Intent）。
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
        var lastLoginRequirement = ""
        while (isActive) {
            delay(1200)
            // 环境桩（幂等）+ 学校编号兜底（只在缺失/undefined 时写入）。
            webView.evalJs(ActivityWebSession.SEED_WX_SESSION_JS)
            webView.evalJs(ActivityWebSession.ENSURE_SCHOOL_NO_JS)
            // 兜底：个别 H5 版本/缓存仍弹「请在微信…」提示时，尝试重载一次。
            // 注意：`evaluateJavascript` 回调会把 JS 字符串值再包一层 JSON 引号（如 "prompt"），
            // 这里先去掉引号再比较（旧实现直接比较 "prompt"，实际永远不相等）。
            val promptText = webView.evalJs(WECHAT_PROMPT_JS).orEmpty().trim().trim('"')
            if (promptText == "prompt") {
                onHint(ActivityWebSession.MSG_OAUTH_BLOCKED)
                if (!oauthRecovered) {
                    oauthRecovered = true
                    webView.loadUrl(SPA_URL)
                }
            }
            // task-73 实测：该登录页依赖微信 openId / 微信头像上传，App 内无法完成。
            // 检测到服务端「openId不能为空」或客户端「请上传头像」时，明确提示改用「在微信中打开」。
            val requirement = webView.evalJs(ActivityWeChatLogin.LOGIN_REQUIREMENT_JS)
                .orEmpty().trim().trim('"')
            if (requirement != lastLoginRequirement) {
                lastLoginRequirement = requirement
                when (requirement) {
                    "openid" -> onHint(
                        ActivityWeChatLogin.MSG_PAGE_REQUIRES_WECHAT + "（服务端提示：openId不能为空）",
                    )
                    "avatar" -> onHint(
                        ActivityWeChatLogin.MSG_PAGE_REQUIRES_WECHAT + "（页面提示：请上传头像）",
                    )
                    else -> Unit
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
internal fun parseLgInfo(raw: String?): ActivityToken? = ActivityWebSession.parseLgInfo(raw)

// ---- 微信登录引导（2026-11-16）--------------------------------------------------

/**
 * 检测微信客户端（best-effort）。
 *
 * Android 11+ 未声明 `<queries>` 时 `getLaunchIntentForPackage` / `resolveActivity` 对非可见
 * 包一律返回 null —— 此时返回 [ActivityWeChatLogin.Presence.UNKNOWN]（**不能**断言未安装），
 * 由「在微信中打开」按钮实际尝试唤起（`startActivity` 不受 package visibility 限制）。
 */
private fun detectWeChatPresence(context: Context): ActivityWeChatLogin.Presence {
    val manager = context.packageManager
    val launchIntent = runCatching {
        manager.getLaunchIntentForPackage(ActivityWeChatLogin.WECHAT_PACKAGE)
    }.getOrNull()
    val resolved = launchIntent != null || runCatching {
        @Suppress("DEPRECATION")
        manager.resolveActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(ActivityWeChatLogin.WEIXIN_SCHEME)),
            0,
        ) != null
    }.getOrDefault(false)
    return ActivityWeChatLogin.presence(resolved, Build.VERSION.SDK_INT)
}

/** 复制活动 H5 链接到剪贴板（返回是否成功）。 */
private fun copyLinkToClipboard(context: Context, text: String): Boolean = try {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (manager == null) {
        false
    } else {
        manager.setPrimaryClip(ClipData.newPlainText("学校活动", text))
        true
    }
} catch (e: Exception) {
    false
}

/**
 * 尝试在微信中打开活动 H5：
 * 1. 优先 `ACTION_VIEW` 学校 H5 + `setPackage(com.tencent.mm)`（部分微信版本用内置浏览器打开）；
 * 2. 失败则仅拉起微信客户端（`weixin://`）——链接已复制，用户粘贴到微信打开。
 *
 * 只用系统 Intent，不接入微信 SDK/开放标签，不获取、不伪造 openId。
 */
private fun launchWeChat(context: Context, url: String): Boolean {
    val direct = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .setPackage(ActivityWeChatLogin.WECHAT_PACKAGE)
    if (runCatching { context.startActivity(direct) }.isSuccess) return true
    val openApp = Intent(Intent.ACTION_VIEW, Uri.parse(ActivityWeChatLogin.WEIXIN_SCHEME))
    return runCatching { context.startActivity(openApp) }.isSuccess
}

private fun fmt(ms: Long?): String {
    if (ms == null || ms <= 0) return "—"
    return DateFormat.format("MM-dd HH:mm", Date(ms)).toString()
}
