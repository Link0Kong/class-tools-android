package com.furina.classtools.ui.screens.map

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.furina.classtools.R
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.furina.classtools.BuildConfig
import com.furina.classtools.ui.components.CtTopBar

private const val TAG = "CampusMap"

/**
 * 离线地图 H5（打包进 APK `assets/map/`，H5 已内置全部地图数据、无外部 fetch）——**首选加载**。
 *
 * 2026-11-16 根因：地图一直依赖 ECS `http://121.196.47.2/map/...`，用户网络/DNS/防火墙稍有波动即白屏。
 * 把同一份 H5 放进 APK 后走 `file:///android_asset/`，无需任何网络请求即可渲染。
 */
private const val ASSET_MAP_URL = "file:///android_asset/map/campus_final_25d.html?embed=1"

/** 地图 H5 网络版（ECS IP 调试入口；备案/DNS 恢复后改同源 https）：本地资源不可用时的降级。 */
private const val MAP_BASE = "http://121.196.47.2/map/campus_final_25d.html"
private const val MAP_VERSION = "202611160800"

/** 离线页健康检查：H5 正常渲染时 `#scene` canvas 一定有非零尺寸且正文有内容。 */
private const val MAP_HEALTH_JS =
    "(function(){try{var s=document.getElementById('scene');" +
        "var t=(document.body&&document.body.innerText||'').replace(/\\s+/g,'');" +
        "return JSON.stringify({c:document.querySelectorAll('canvas').length," +
        "w:s?s.width:0,h:s?s.height:0,t:t.length});}catch(e){return ''}})()"

/** H5 的 canvas 尺寸按布局计算；WebView 首次加载时可能还没完成布局（canvas 高 0 → 白屏）。 */
private const val MAP_RESIZE_JS =
    "(function(){try{window.dispatchEvent(new Event('resize'));return 'ok';}catch(e){return 'error';}})()"

/** 地图页面来源：本地 asset（默认）→ ECS 网络版（降级）。 */
private enum class MapSource {
    ASSET,
    NETWORK,
}

/**
 * 校内地图（网页嵌入方案）。
 *
 * 2026-11-16 用户反馈「地图还是加载不出来」，本轮把方案改为**离线优先**：
 * - **首选** `file:///android_asset/map/campus_final_25d.html?embed=1`（H5 已内置数据，无外部 fetch）；
 * - 主文档加载失败/页面健康检查失败（无 canvas/空白）→ 自动降级到 ECS 网络版并提示；
 * - 网络版仍失败 → 错误条提供「重试」「离线版」「在浏览器打开」三个出口；
 * - 顶栏「浏览器」始终用网络版 URL 交给系统浏览器（浏览器打不开 APK 内 assets）。
 *
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

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var reloadSeq by remember { mutableIntStateOf(0) }
    var source by remember { mutableStateOf(MapSource.ASSET) }
    // 本地 asset 已自动降级过一次就不再反复重试（避免加载失败时循环）。
    var assetFallbackTried by remember { mutableStateOf(false) }
    // 终极兜底：asset 与网络版都健康检查失败/主文档错误时，展示内置静态地图，
    // 保证任何 WebView/系统环境下都「至少能看到地图」，并保留重试网页入口。
    var staticFallback by remember { mutableStateOf(false) }

    fun assetUrl(): String {
        val base = ASSET_MAP_URL
        return if (room.isNullOrBlank()) base else base + "&room=" + Uri.encode(room)
    }

    fun networkUrl(): String {
        val base = "$MAP_BASE?embed=1&v=$MAP_VERSION"
        return if (room.isNullOrBlank()) base else base + "&room=" + Uri.encode(room)
    }

    fun currentUrl(): String = when (source) {
        MapSource.ASSET -> assetUrl()
        MapSource.NETWORK -> networkUrl()
    }

    fun switchToNetwork(reason: String) {
        source = MapSource.NETWORK
        notice = reason
        error = null
        loading = true
    }

    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.rgb(243, 245, 236))
            // 部分 WebView/GPU 组合对 2D canvas 页面会白屏；软件层可稳定绘制静态 2.5D 地图。
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            settings.javaScriptEnabled = true
            // file:///android_asset 页面必须允许 file access；DOM storage 供 H5 保存偏好。
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            settings.allowFileAccessFromFileURLs = true
            // 不开放 file 页面跨源网络能力：H5 已内置数据，无外部 fetch，也没有上网需求。
            settings.allowUniversalAccessFromFileURLs = false
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = false
            if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
            webChromeClient = object : WebChromeClient() {
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
                    val target = view ?: return
                    // 2026-11-16 根因：H5 在 WebView 首次布局完成前 boot 时 canvas 高度是 0
                    // （`canvas.clientHeight=0` → H5 的 resize 守卫直接 return），页面之后不会再
                    // 自动重算 → 只剩底色，表现为「地图加载不出来」。页面加载后多帧强制派发 resize。
                    listOf(250L, 800L, 1800L).forEach { delayMs ->
                        target.postDelayed({
                            target.evaluateJavascript(MAP_RESIZE_JS, null)
                        }, delayMs)
                    }
                    // 本地 asset 健康检查：无 canvas（加载成了错误页）→ 自动降级网络版；
                    // 有 canvas 但尺寸为 0（布局晚于 boot）→ 已由上面的 resize 兜底，无需降级。
                    if (source == MapSource.ASSET && !assetFallbackTried) {
                        target.evaluateJavascript(MAP_HEALTH_JS) { raw ->
                            val health = parseMapHealth(raw)
                            if (health == null || health.canvasCount <= 0) {
                                if (source == MapSource.ASSET) {
                                    assetFallbackTried = true
                                    switchToNetwork("本地离线地图加载失败，已切换网络版")
                                    target.post { target.loadUrl(currentUrl()) }
                                } else {
                                    staticFallback = true
                                    error = "WebView 无法绘制地图，已显示内置静态图；可点「重试网页」或用浏览器打开"
                                }
                            }
                        }
                    }
                    if (source == MapSource.NETWORK && !staticFallback) {
                        target.evaluateJavascript(MAP_HEALTH_JS) { raw ->
                            val health = parseMapHealth(raw)
                            if (health == null || health.canvasCount <= 0) {
                                staticFallback = true
                                error = "WebView 无法绘制地图，已显示内置静态图；可点「重试网页」或用浏览器打开"
                            }
                        }
                    }
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
                        Log.w(TAG, "main frame error: source=$source $detail")
                        if (source == MapSource.ASSET && !assetFallbackTried) {
                            assetFallbackTried = true
                            switchToNetwork("本地离线地图加载失败，已切换网络版")
                            view?.post { view.loadUrl(currentUrl()) }
                        } else {
                            error = detail
                            staticFallback = true
                        }
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val u = request?.url?.toString() ?: return false
                    // 地图页内部（离线 asset / ECS 网络版）继续留在 WebView；其它外链交给系统浏览器。
                    if (u.startsWith("file:///android_asset/map/")) return false
                    if (u.startsWith(MAP_BASE) || u.startsWith("https://tool.furina.fun/map/")) {
                        return false
                    }
                    if (u.startsWith("http://") || u.startsWith("https://")) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
                        return true
                    }
                    // 非 http(s)/asset（含 H5 的「编辑地图」等 file 链接）一律拦截，避免 file 错误页。
                    return true
                }
            }
            loadUrl(currentUrl())
        }
    }

    // 重试：只做一次 loadUrl，不做复杂重试状态机（线路切换由回调/错误条显式 loadUrl）。
    LaunchedEffect(reloadSeq) {
        if (reloadSeq <= 0) return@LaunchedEffect
        loading = true
        error = null
        webView.loadUrl(currentUrl())
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

    /** 系统浏览器打开网络版（浏览器访问不到 APK 内 assets）。 */
    fun openInBrowser() {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(networkUrl()))) }
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
            subtitle = if (source == MapSource.ASSET) "2.5D · 本地离线" else "2.5D · 网络版",
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
        notice?.let { message ->
            MapNoticeBar(message = message, onRetryLocal = {
                assetFallbackTried = true
                source = MapSource.ASSET
                notice = null
                error = null
                loading = true
                webView.loadUrl(currentUrl())
            })
        }
        error?.let { message ->
            MapFallbackBanner(
                message = message,
                source = source,
                onRetry = {
                    error = null
                    loading = true
                    staticFallback = false
                    source = MapSource.ASSET
                    assetFallbackTried = false
                    reloadSeq += 1
                },
                onSwitchSource = {
                    if (source == MapSource.ASSET) {
                        assetFallbackTried = true
                        switchToNetwork("已切换到网络版")
                    } else {
                        source = MapSource.ASSET
                        notice = null
                        error = null
                        loading = true
                        webView.loadUrl(currentUrl())
                    }
                },
                onOpenBrowser = { openInBrowser() },
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (staticFallback) {
                Image(
                    painter = painterResource(R.drawable.map_campus_fallback),
                    contentDescription = "校内 2.5D 地图（静态兜底）",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    text = "静态地图 · 网页版绘制失败",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            RoundedCornerShape(999.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                )
            } else {
                AndroidView(
                factory = { webView },
                    modifier = Modifier
                        .fillMaxSize()
                        // WebView 布局尺寸就绪后强制 H5 重算 canvas（解决「页面在布局前 boot → 白屏」）。
                        .onSizeChanged { _ ->
                            webView.evaluateJavascript(MAP_RESIZE_JS, null)
                        },
                )
            }
            if (loading && !staticFallback) {
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

/** 自动降级提示条（信息性，提供「重试离线版」出口）。 */
@Composable
private fun MapNoticeBar(message: String, onRetryLocal: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetryLocal) { Text("重试离线版") }
        }
    }
}

/** 地图降级提示条：重试 / 切换线路（离线版↔网络版）/ 在浏览器打开。 */
@Composable
private fun MapFallbackBanner(
    message: String,
    source: MapSource,
    onRetry: () -> Unit,
    onSwitchSource: () -> Unit,
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
                TextButton(onClick = onSwitchSource) {
                    Text(if (source == MapSource.ASSET) "网络版" else "离线版")
                }
                TextButton(onClick = onOpenBrowser) { Text("在浏览器打开") }
            }
        }
    }
}

/**
 * 地图页健康检查结果解析：`evaluateJavascript` 回调是 JSON 字符串字面量（如
 * `"{\"c\":2,\"w\":822,\"h\":1800,\"t\":54}"`）。
 *
 * - `canvasCount <= 0` → 页面不是地图 H5（加载失败/错误页）；
 * - `sceneWidth/Height > 0` → `#scene` canvas 已按布局尺寸初始化（能渲染）。
 */
private data class MapPageHealth(
    val canvasCount: Int,
    val sceneWidth: Int,
    val sceneHeight: Int,
    val textLength: Int,
) {
    val sized: Boolean get() = sceneWidth > 0 && sceneHeight > 0
}

private fun parseMapHealth(raw: String?): MapPageHealth? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty() || text == "null") return null
    val decoded = text
        .removeSurrounding("\"")
        .replace("\\\"", "\"")
        .replace("\\/", "/")
    fun number(key: String): Int =
        Regex("\"$key\"\\s*:\\s*(\\d+)").find(decoded)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    return MapPageHealth(
        canvasCount = number("c"),
        sceneWidth = number("w"),
        sceneHeight = number("h"),
        textLength = number("t"),
    )
}

