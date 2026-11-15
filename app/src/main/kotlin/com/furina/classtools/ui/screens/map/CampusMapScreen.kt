package com.furina.classtools.ui.screens.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.LocationManager
import android.net.Uri
import android.util.Log
import android.view.View
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.furina.classtools.BuildConfig
import com.furina.classtools.ui.components.CtTopBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.resume

private const val TAG = "CampusMap"
private const val MAP_BASE = "http://121.196.47.2/map/campus_final_25d.html"
private const val MAP_VERSION = "202611152405"

/** H5 通过 `window.AndroidMap.requestLocation()` 优先向 App 要定位（HTTP 页面不能用 navigator.geolocation）。 */
private const val JS_INTERFACE_NAME = "AndroidMap"

/**
 * 页面健康检查脚本：返回画布尺寸 / 页面错误提示 / 当前 URL（只读，不改页面状态）。
 * 用于发现「WebView 白屏 / canvas 0 尺寸」类故障，便于自动重试与诊断。
 */
private val HEALTH_JS = """
    (function () {
      try {
        var c = document.getElementById('scene');
        var err = document.getElementById('errEl');
        return JSON.stringify({
          url: String(location.href || ''),
          w: c ? c.width : -1,
          h: c ? c.height : -1,
          cw: c ? c.clientWidth : -1,
          ch: c ? c.clientHeight : -1,
          title: String(document.title || ''),
          err: (err && !err.hidden) ? String(err.textContent || '').slice(0, 200) : ''
        });
      } catch (e) {
        return JSON.stringify({ url: '', w: -1, h: -1, cw: -1, ch: -1, title: '', err: 'probe-error' });
      }
    })()
""".trimIndent()

/** 兜底重绘：canvas 尺寸为 0 时手动触发 resize（页面 boot 的 resize 可能跑在 WebView 布局完成之前）。 */
private val FORCE_REPAINT_JS = """
    (function () {
      try {
        var c = document.getElementById('scene');
        if (!c) { return 'no-scene'; }
        if (c.width > 0 && c.height > 0) { return 'sized'; }
        window.dispatchEvent(new Event('resize'));
        return 'resized';
      } catch (e) { return 'error'; }
    })()
""".trimIndent()

/**
 * 校内地图（H5 2.5D）：与班级工具站共用同一份地图服务。
 *
 * 说明：当前 ECS 只有 IP 调试入口（HTTP），因此 network_security_config 仅对本机域名放行明文；
 * 备案/DNS 恢复后可切换为 https://tool.furina.fun/map/...。
 *
 * 2026-11-15 修复（「地图打不开/白屏」）：
 * 1. WebView 设置显式补齐 `useWideViewPort` / overview 模式 / 硬件层，避免 viewport 与合成差异导致 canvas 不绘制；
 * 2. 页面加载完成注入只读健康检查（canvas 尺寸 / #errEl）；canvas 尺寸为 0 时自动触发 resize 重绘；
 * 3. 健康检查失败 → 自动用「无缓存 URL + 软件渲染层」重试一次（针对模拟器/部分 WebView 的 canvas 合成白屏）；
 * 4. 仍失败 → 顶部提示「地图加载异常」+「重试」按钮，并可在系统浏览器打开同一 H5（降级入口）；
 * 5. 新增 `window.AndroidMap.requestLocation()` JS 桥：HTTP 页面无法用 navigator.geolocation，
 *    H5 已支持优先向 App 要定位，由本 App 读取系统定位后回填 `window.zqSetMyLocation(lat, lon, acc)`；
 * 6. WebView 生命周期 onResume/onPause 与 onConsoleMessage/onReceivedError 诊断日志（logcat TAG=CampusMap）。
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

    // 页面诊断 / 重试状态。
    var loading by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var diag by remember { mutableStateOf<MapHealth?>(null) }
    var retrySeq by remember { mutableIntStateOf(0) }
    val softwareLayer = remember { mutableStateOf(false) }
    var healthCheckSeq by remember { mutableIntStateOf(0) }

    fun mapUrl(seq: Int = 0): String {
        val base = "$MAP_BASE?embed=1&v=$MAP_VERSION"
        val withRoom = if (room.isNullOrBlank()) base else base + "&room=" + Uri.encode(room)
        return if (seq <= 0) withRoom else "$withRoom&retry=$seq"
    }

    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.rgb(243, 245, 236))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            // 关键：H5 依赖 <meta viewport> 与 100vh/canvas 尺寸；显式打开宽 viewport 支持，
            // 关闭 overview 缩放，保证 CSS 像素与 canvas 像素比和浏览器一致。
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = false
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.setGeolocationEnabled(true)
            settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            // canvas 白屏兜底：先走硬件层（默认）；重试时切换软件层（见 retry）。
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            if (BuildConfig.DEBUG) {
                // 允许 chrome://inspect 调试本 WebView（仅 debug 包）。
                WebView.setWebContentsDebuggingEnabled(true)
            }
            addJavascriptInterface(MapLocationBridge(context) { lat, lon, acc ->
                // JS 桥回调运行在 JavaBridge 线程，evaluateJavascript 必须回 UI 线程。
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
                    progress = newProgress
                    if (newProgress >= 100) loading = false
                }

                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "console ${message.messageLevel()}: ${message.message().take(300)}")
                    }
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    loading = true
                    progress = 0
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    loading = false
                    val current = url.orEmpty()
                    if (!current.contains("/map/")) return
                    // 触发一次只读健康检查（含自动 resize 重绘）。
                    healthCheckSeq += 1
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
                        // 主文档加载失败：提示 + 重试按钮（重试会切软件渲染层 + 无缓存 URL）。
                        error = detail
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val u = request?.url?.toString() ?: return false
                    return !(u.startsWith("http://121.196.47.2/map/") || u.startsWith("https://tool.furina.fun/map/"))
                }
            }

            loadUrl(mapUrl())
        }
    }

    // 健康检查：canvas 尺寸为 0 → 触发 resize；仍异常 → 软件渲染重试一次；再失败 → 顶部降级提示。
    val checkSeq = healthCheckSeq
    LaunchedEffect(checkSeq) {
        if (checkSeq <= 0) return@LaunchedEffect
        delay(500)
        val first = runCatching {
            webView.evaluateJavascriptAwait(HEALTH_JS)
        }.getOrNull()
        val info = parseHealth(first)
        diag = info
        if (BuildConfig.DEBUG) Log.d(TAG, "health: $info")
        if (info == null) {
            error = "页面脚本无响应"
            return@LaunchedEffect
        }
        if (!info.err.isNullOrBlank()) {
            error = "页面脚本错误：${info.err.take(120)}"
            return@LaunchedEffect
        }
        if (info.w > 0 && info.h > 0) {
            error = null
            return@LaunchedEffect
        }
        // canvas 尚未初始化：手动触发 resize 重绘，再复核一次。
        val repaint = runCatching { webView.evaluateJavascriptAwait(FORCE_REPAINT_JS) }.getOrNull()
        if (BuildConfig.DEBUG) Log.d(TAG, "force repaint: $repaint")
        delay(700)
        val second = runCatching { webView.evaluateJavascriptAwait(HEALTH_JS) }.getOrNull()
        val secondInfo = parseHealth(second)
        diag = secondInfo
        if (secondInfo != null && secondInfo.w > 0 && secondInfo.h > 0) {
            error = null
            return@LaunchedEffect
        }
        // 仍为 0：切换软件渲染层 + 无缓存重载一次（针对 canvas 合成白屏）。
        if (!softwareLayer.value && retrySeq <= 1) {
            softwareLayer.value = true
            retrySeq += 1
            runCatching {
                webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                webView.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            }
            loading = true
            webView.loadUrl(mapUrl(retrySeq))
        } else {
            error = "地图画布未初始化（已尝试重绘/软件渲染）"
        }
    }

    // WebView 生命周期：Activity 被权限弹窗/切后台暂停后恢复时，显式 resume 避免 canvas 不再重绘。
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

    fun retry() {
        error = null
        loading = true
        retrySeq += 1
        runCatching {
            webView.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            webView.loadUrl(mapUrl(retrySeq))
        }
    }

    fun openInBrowser() {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapUrl())))
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CtTopBar(
            title = "校内地图",
            subtitle = "2.5D · 按道路导航",
            onBack = onBack,
            actions = {
                androidx.compose.material3.TextButton(onClick = onOpenTimetable) {
                    androidx.compose.material3.Text("课表", style = MaterialTheme.typography.labelLarge)
                }
            },
        )
        error?.let { message ->
            MapErrorBanner(
                message = message,
                onRetry = { retry() },
                onOpenBrowser = { openInBrowser() },
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize(),
            )
            if (loading) {
                androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(2.dp),
                )
            }
        }
    }
}

/** 地图异常提示条：重试 + 系统浏览器降级入口。 */
@Composable
private fun MapErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onOpenBrowser: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) { Text("重试") }
            TextButton(onClick = onOpenBrowser) { Text("在浏览器打开") }
        }
    }
}

/** 页面健康检查结果（只读 JS 返回的 JSON）。 */
private data class MapHealth(
    val w: Int,
    val h: Int,
    val cw: Int,
    val ch: Int,
    val title: String,
    val err: String?,
)

private fun parseHealth(raw: String?): MapHealth? {
    val text = decodeJsString(raw).trim()
    if (text.isEmpty() || !text.startsWith("{")) return null
    return runCatching {
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(text).jsonObject
        fun int(key: String) = (obj[key] as? kotlinx.serialization.json.JsonPrimitive)
            ?.contentOrNull?.toIntOrNull() ?: -1
        MapHealth(
            w = int("w"),
            h = int("h"),
            cw = int("cw"),
            ch = int("ch"),
            title = (obj["title"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty(),
            err = (obj["err"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() },
        )
    }.getOrNull()
}

/** `evaluateJavascript` 回调的 JSON 字符串字面量 → 原始字符串。 */
private fun decodeJsString(raw: String?): String {
    if (raw == null) return ""
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed == "null") return ""
    if (trimmed.length < 2 || !trimmed.startsWith('"') || !trimmed.endsWith('"')) return trimmed
    val body = trimmed.substring(1, trimmed.length - 1)
    val sb = StringBuilder(body.length)
    var i = 0
    while (i < body.length) {
        val c = body[i]
        if (c != '\\' || i == body.length - 1) {
            sb.append(c)
            i += 1
            continue
        }
        i += 1
        when (val esc = body[i]) {
            'n' -> sb.append('\n')
            'r' -> sb.append('\r')
            't' -> sb.append('\t')
            'b' -> sb.append('\b')
            'f' -> sb.append('\u000C')
            '"' -> sb.append('"')
            '\'' -> sb.append('\'')
            '\\' -> sb.append('\\')
            '/' -> sb.append('/')
            'u' -> {
                val hex = body.substring(i + 1, minOf(i + 5, body.length))
                val code = hex.toIntOrNull(16)
                if (code != null && hex.length == 4) {
                    sb.append(code.toChar())
                    i += 4
                } else {
                    sb.append(esc)
                }
            }
            else -> sb.append(esc)
        }
        i += 1
    }
    return sb.toString()
}

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

/**
 * 地图 H5 的 App 定位桥（`window.AndroidMap.requestLocation`）。
 *
 * H5 在 HTTP 页面无法使用 `navigator.geolocation`（非安全上下文），因此优先调用本桥；
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
                .mapNotNull { provider ->
                    runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
                }
                .maxByOrNull { it.time }
        }.getOrNull() ?: return
        onLocation(location.latitude, location.longitude, location.accuracy)
    }
}
