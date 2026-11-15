package com.furina.classtools.ui.screens.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.furina.classtools.ui.components.CtTopBar

/**
 * 校内地图（H5 2.5D）：与班级工具站共用同一份地图服务。
 *
 * 说明：当前 ECS 只有 IP 调试入口（HTTP），因此 network_security_config 仅对本机域名放行明文；
 * 备案/DNS 恢复后可切换为 https://tool.furina.fun/map/...。
 */
@Composable
fun CampusMapScreen(
    onBack: () -> Unit,
    onOpenTimetable: () -> Unit = {},
    room: String? = null,
) {
    val context = LocalContext.current
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    val url = remember(room) {
        val base = "http://121.196.47.2/map/campus_final_25d.html?embed=1&v=202611152330"
        if (room.isNullOrBlank()) base else base + "&room=" + android.net.Uri.encode(room)
    }
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.setGeolocationEnabled(true)
            setBackgroundColor(android.graphics.Color.rgb(243, 245, 236))
            webChromeClient = object : WebChromeClient() {
                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?,
                ) {
                    callback?.invoke(origin, true, false)
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                }
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val u = request?.url?.toString() ?: return false
                    return !(u.startsWith("http://121.196.47.2/map/") || u.startsWith("https://tool.furina.fun/map/"))
                }
            }
            loadUrl(url)
        }
    }
    BackHandler(enabled = webView.canGoBack()) { webView.goBack() }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { webView.stopLoading() }
            runCatching { webView.destroy() }
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
        AndroidView(
            factory = { webView },
            modifier = Modifier.weight(1f),
        )
    }
}
