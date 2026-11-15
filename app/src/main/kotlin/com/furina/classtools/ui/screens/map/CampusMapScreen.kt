package com.furina.classtools.ui.screens.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.furina.classtools.BuildConfig
import com.furina.classtools.ui.components.CtTopBar

private const val TAG = "CampusMap"

/** 地图 H5 页面（ECS IP 调试入口；备案/DNS 恢复后改同源 https）。 */
private const val MAP_BASE = "http://121.196.47.2/map/campus_final_25d.html"
private const val MAP_VERSION = "202611152330"

/** H5 已内置的 App 定位桥（HTTP 页面不能用 navigator.geolocation）。 */
private const val JS_INTERFACE_NAME = "AndroidMap"

/**
 * 校内地图（网页嵌入方案，2026-11-15 用户反馈「App 打不开，改网页嵌入」）。
 *
 * 方案：
 * - 用 WebView 直接加载 ECS H5：`MAP_BASE?embed=1&v=<版本>`（唯一数据源，不在原生侧重写 canvas）；
 * - **不再**做原生 canvas 渲染依赖/复杂健康检查重试；只保留最基本的加载进度、主文档错误提示；
 * - 顶部：Compose CtTopBar 与系统状态栏保持安全距离（`statusBarsPadding`），WebView 填满顶栏以下区域；
 * - 降级：顶栏「浏览器」按钮随时可在系统浏览器打开同一 H5；主文档加载失败/白屏时错误条提供
 *   「重试」「嵌入备用」「浏览器」三个入口——「嵌入备用」用本地 data URL 包装页内嵌 iframe 指向 H5；
 * - 定位：H5 优先调用 `window.AndroidMap.requestLocation()`，本 App 读系统最近定位后回填
 *   `window.zqSetMyLocation(lat, lon, acc)`（HTTP 页面无法用浏览器 geolocation）。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CampusMapScreen(
    onBack: () -> Unit,
    onOpenTimetable: () -> Unit = {},
    room: String? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadSeq by remember { mutableIntStateOf(0) }
    var iframeFallback by remember { mutableStateOf(false) }

    fun mapUrl(): String {
        val base = "$MAP_BASE?embed=1&v=$MAP_VERSION"
        return if (room.isNullOrBlank()) base else base + "&room=" + Uri.encode(room)
    }

    /**
     * 降级用本地包装页：data URL + 100% iframe 指向 H5。
     *
     * 某些 WebView/设备对 canvas 页面直接加载会白屏；iframe 包装常能绕过（iframe 内独立文档/合成层）。
     * 包装页不联网、不含任何敏感数据，只是内嵌官方 H5。
     */
    fun iframeWrapperUrl(): String {
        val html = """<!doctype html><html><head><meta charset="utf-8">""" +
            """<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">""" +
            "<style>html,body{margin:0;padding:0;height:100%;background:#F3F5EC}" +
            "iframe{display:block;width:100%;height:100%;border:0}</style></head><body>" +
            """<iframe src="${mapUrl()}" allow="geolocation" referrerpolicy="no-referrer"></iframe>""" +
            "</body></html>"
        return "data:text/html;charset=utf-8," + Uri.encode(html)
    }

    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.rgb(243, 245, 236))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.setGeolocationEnabled(true)
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = false
            if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
            addJavascriptInterface(MapLocationBridge(context) { lat, lon, acc ->
                // JS 桥回调在 JavaBridge 线程，回 UI 线程再执行 JS。
                post { evaluateJavascript("window.zqSetMyLocation($lat,$lon,$acc)", null) }
            }, JS_INTERFACE_NAME)
            webChromeClient = object : WebChromeClient() {
                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?,
                ) {
                    callback?.invoke(origin, true, false)
                }

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress >= 100) loading = false
                }

                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    if (BuildConfig.DEBUG) Log.d(TAG, "console ${message.messageLevel()}: ${message.message().take(300)}")
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    loading = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    loading = false
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    webError: WebResourceError?,
                ) {
                    if (request?.isForMainFrame == true) {
                        loading = false
                        val detail = "HTTP/网络错误 ${webError?.errorCode ?: -1} " +
                            webError?.description?.toString().orEmpty()
                        Log.w(TAG, "main frame error: $detail")
                        error = detail
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val u = request?.url?.toString() ?: return false
                    // 只在本 WebView 内保留地图页；其它外链交给系统浏览器。
                    if (u.startsWith("http://121.196.47.2/map/") || u.startsWith("https://tool.furina.fun/map/")) {
                        return false
                    }
                    if (u.startsWith("http://") || u.startsWith("https://")) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
                        return true
                    }
                    return true
                }
            }
            loadUrl(mapUrl())
        }
    }

    // 重试/切换到 iframe 备用：只做一次 loadUrl，不做复杂重试状态机。
    LaunchedEffect(reloadSeq, iframeFallback) {
        if (reloadSeq <= 0 && !iframeFallback) return@LaunchedEffect
        loading = true
        error = null
        webView.loadUrl(if (iframeFallback) iframeWrapperUrl() else mapUrl())
    }

    // WebView 生命周期：权限弹窗/切后台返回后恢复，避免 canvas 不重绘。
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> runCatching { webView.onResume() }
                Lifecycle.Event.ON_PAUSE -> runCatching { webView.onPause() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = webView.canGoBack()) { webView.goBack() }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { webView.stopLoading() }
            runCatching { webView.destroy() }
        }
    }

    fun openInBrowser() {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapUrl()))) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        CtTopBar(
            title = "校内地图",
            subtitle = "2.5D · 按道路导航",
            onBack = onBack,
            actions = {
                TextButton(onClick = { openInBrowser() }) {
                    Text("浏览器", style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = onOpenTimetable) {
                    Text("课表", style = MaterialTheme.typography.labelLarge)
                }
            },
        )
        error?.let { message ->
            MapFallbackBanner(
                message = message,
                onRetry = {
                    iframeFallback = false
                    reloadSeq += 1
                },
                onIframe = { iframeFallback = true },
                onOpenBrowser = { openInBrowser() },
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
            if (loading) {
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

/** 地图降级提示条：重试 / 嵌入备用 iframe / 系统浏览器打开。 */
@Composable
private fun MapFallbackBanner(
    message: String,
    onRetry: () -> Unit,
    onIframe: () -> Unit,
    onOpenBrowser: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onRetry) { Text("重试") }
                TextButton(onClick = onIframe) { Text("嵌入备用") }
                TextButton(onClick = onOpenBrowser) { Text("在浏览器打开") }
            }
        }
    }
}

/**
 * 地图 H5 的 App 定位桥（`window.AndroidMap.requestLocation`）。
 *
 * H5 在 HTTP 页面无法使用 `navigator.geolocation`，因此优先调用本桥；
 * 这里只读取系统「最近一次已知定位」并回填 `window.zqSetMyLocation(lat, lon, acc)`，
 * 不对外暴露任何其它能力/数据。
 */
private class MapLocationBridge(
    private val context: Context,
    private val onLocation: (Double, Double, Float) -> Unit,
) {
    @JavascriptInterface
    fun requestLocation() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val location = runCatching {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return@runCatching null
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
                .maxByOrNull { it.time }
        }.getOrNull() ?: return
        onLocation(location.latitude, location.longitude, location.accuracy)
    }
}
