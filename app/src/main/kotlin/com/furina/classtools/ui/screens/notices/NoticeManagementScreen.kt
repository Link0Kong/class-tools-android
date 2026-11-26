package com.furina.classtools.ui.screens.notices

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebMessage
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.furina.classtools.data.session.Session
import com.furina.classtools.ui.components.EmbeddedFeedbackScript
import com.furina.classtools.ui.components.ctTopBarRaw
import org.json.JSONObject

private const val SITE = "https://tool.furina.fun"
private const val MANAGEMENT_URL = "$SITE/?mobileTool=ntadmin"

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun NoticeManagementScreen(session: Session, onBack: () -> Unit) {
    var failed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var attempt by remember { mutableIntStateOf(0) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ctTopBarRaw(title = "通知管理", onBack = onBack)
        if (failed) {
            Text("暂时无法加载通知管理")
            Button(onClick = { failed = false; loading = true; attempt++ }) { Text("重试") }
        } else {
            if (loading) LinearProgressIndicator()
            key(session.sid, session.token, attempt) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                    request.isForMainFrame && request.url.toString().substringBefore('#') != MANAGEMENT_URL

                                override fun onPageFinished(view: WebView, url: String) {
                                    if (url.substringBefore('#') != MANAGEMENT_URL || failed) return
                                    view.evaluateJavascript(EmbeddedFeedbackScript, null)
                                    val identity = JSONObject().put("sid", session.sid).put("name", session.name)
                                        .put("role", session.role).put("badge", session.badge)
                                        .put("token", session.token).put("is_admin", session.isAdmin)
                                        .put("ts", System.currentTimeMillis())
                                    val message = JSONObject().put("type", "ct-native-session").put("session", identity)
                                    view.postWebMessage(WebMessage(message.toString()), Uri.parse(SITE))
                                    loading = false
                                }

                                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                    if (request.isForMainFrame) failed = true
                                }

                                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                    if (request.isForMainFrame) failed = true
                                }
                            }
                            loadUrl(MANAGEMENT_URL)
                        }
                    },
                    onRelease = { it.stopLoading(); it.destroy() },
                )
            }
        }
    }
}
