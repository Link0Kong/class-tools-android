package com.furina.classtools.ui.screens.map

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.furina.classtools.BuildConfig
import com.furina.classtools.R
import com.furina.classtools.ui.components.ctTopBarRaw
import com.furina.classtools.ui.theme.LocalCtDarkTheme
import kotlinx.coroutines.delay

private const val OFFLINE_MAP = "file:///android_asset/map/campus_final_25d.html"
private const val MAP_VERSION = "20261123-map2"
private const val READY_SCRIPT = "window.zqMapReady === true"
private const val DISMISS_SCRIPT = "typeof window.zqMapDismiss === 'function' && window.zqMapDismiss()"

private enum class MapSource(val label: String) {
    OFFLINE("离线地图"),
    ONLINE("网络地图"),
    STATIC("静态地图"),
}

private fun mapUrl(base: String, room: String?, embedded: Boolean, attempt: Int = 0): String =
    Uri.parse(base).buildUpon().apply {
        appendQueryParameter("v", MAP_VERSION)
        if (embedded) {
            appendQueryParameter("embed", "1")
            appendQueryParameter("native", "1")
        }
        if (!room.isNullOrBlank()) appendQueryParameter("room", room.trim())
        if (attempt > 0) appendQueryParameter("retry", attempt.toString())
    }.build().toString()

private fun themeScript(dark: Boolean): String {
    val theme = if (dark) "dark" else "light"
    return "if (window.zqMapTheme) window.zqMapTheme('$theme');"
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CampusMapScreen(
    onBack: () -> Unit,
    onOpenTimetable: () -> Unit = {},
    room: String? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val dark = LocalCtDarkTheme.current
    var source by remember { mutableStateOf(MapSource.OFFLINE) }
    var attempt by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }

    val url = when (source) {
        MapSource.OFFLINE -> mapUrl(OFFLINE_MAP, room, true, attempt)
        MapSource.ONLINE -> mapUrl(BuildConfig.MAP_BASE_URL, room, true, attempt)
        MapSource.STATIC -> null
    }
    val currentUrl by rememberUpdatedState(url)
    val currentDark by rememberUpdatedState(dark)

    fun advanceFallback(failedUrl: String) {
        val failedSource = if (failedUrl.startsWith("file:")) MapSource.OFFLINE else MapSource.ONLINE
        if (source != failedSource) return
        source = if (source == MapSource.OFFLINE) MapSource.ONLINE else MapSource.STATIC
        loading = source != MapSource.STATIC
    }

    fun openBrowser(uri: Uri) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }

    val webView = remember(context) {
        WebView(context).apply {
            // 部分旧 WebView 的硬件层无法显示 Canvas，保留软件绘制。
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false // android_asset 仍可访问。
            settings.allowContentAccess = false
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    if (url == currentUrl) loading = true
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (url != currentUrl) return
                    listOf(0L, 150L, 600L, 1800L, 4500L).forEach { wait ->
                        view.postDelayed({
                            if (url == currentUrl && loading) {
                                view.evaluateJavascript(themeScript(currentDark) + READY_SCRIPT) { ready ->
                                    if (url == currentUrl && ready == "true") loading = false
                                }
                            }
                        }, wait)
                    }
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    val failedUrl = request.url.toString()
                    if (request.isForMainFrame && failedUrl == currentUrl) advanceFallback(failedUrl)
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse,
                ) {
                    val failedUrl = request.url.toString()
                    if (request.isForMainFrame && failedUrl == currentUrl) advanceFallback(failedUrl)
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (request.url.toString() == currentUrl) return false
                    if (request.isForMainFrame && request.url.scheme in listOf("https", "http")) {
                        openBrowser(request.url)
                    }
                    return true
                }
            }
        }
    }

    LaunchedEffect(webView, url) {
        if (url == null) {
            webView.stopLoading()
            return@LaunchedEffect
        }
        loading = true
        webView.loadUrl(url)
        delay(9_000)
        if (loading && url == currentUrl) advanceFallback(url)
    }
    LaunchedEffect(webView, dark, loading) {
        webView.setBackgroundColor(Color.parseColor(if (dark) "#1C2823" else "#EDF1E7"))
        if (!loading && source != MapSource.STATIC) webView.evaluateJavascript(themeScript(dark), null)
    }

    DisposableEffect(webView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                Lifecycle.Event.ON_PAUSE -> webView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }
    BackHandler {
        if (source == MapSource.STATIC || loading) onBack()
        else webView.evaluateJavascript(DISMISS_SCRIPT) { closed ->
            if (closed != "true") onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ctTopBarRaw(
            subtitle = source.label,
            title = "校内地图",
            onBack = onBack,
            actions = {
                TextButton(onClick = { openBrowser(Uri.parse(mapUrl(BuildConfig.MAP_BASE_URL, room, false))) }) {
                    Text("浏览器")
                }
                TextButton(onClick = onOpenTimetable) { Text("课表") }
            },
        )
        if (source == MapSource.STATIC) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("互动地图暂不可用", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        loading = true
                        source = MapSource.OFFLINE
                        attempt += 1
                    }) { Text("重试") }
                }
            }
            Image(
                painter = painterResource(R.drawable.map_campus_fallback),
                contentDescription = "旗山校区静态地图",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
                if (loading) {
                    LinearProgressIndicator(
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(2.dp),
                    )
                }
            }
        }
    }
}
