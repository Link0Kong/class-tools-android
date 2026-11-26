package com.furina.classtools.ui.screens.notices

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.furina.classtools.ClassToolsApp
import com.furina.classtools.data.notify.SignupClock
import com.furina.classtools.data.notify.SignupCountdown
import com.furina.classtools.data.notify.SignupCountdowns
import com.furina.classtools.data.reminder.reminderOwner
import com.furina.classtools.data.session.SessionState
import com.furina.classtools.ui.ClassToolsThemeHost
import com.furina.classtools.ui.components.CtToastBus
import kotlinx.coroutines.delay

/** The system chronometer owns the chip. This user-opened surface also runs while pinned over the signup app. */
class SignupCountdownActivity : ComponentActivity() {
    private var compact by mutableStateOf(false)
    private var faceBounds: Rect? = null
    private val supportsPip get() = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        compact = isInPictureInPictureMode
        val parts = intent.data?.pathSegments
        if (parts?.size != 2) { finish(); return }
        val (owner, id) = parts
        val container = (application as ClassToolsApp).container
        setContent {
            val session by container.sessionStore.state.collectAsStateWithLifecycle(initialValue = SessionState.Loading)
            var watch by remember { mutableStateOf(SignupCountdowns.get(this, owner, id)) }
            DisposableEffect(owner, id) {
                val prefs = SignupCountdowns.prefs(this@SignupCountdownActivity)
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                    watch = SignupCountdowns.get(this@SignupCountdownActivity, owner, id)
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                watch = SignupCountdowns.get(this@SignupCountdownActivity, owner, id)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            val current = watch
            val signed = (session as? SessionState.SignedIn)?.session
            if (session is SessionState.Loading) return@setContent
            if (current == null || signed?.reminderOwner != owner || !signed.isLikelyValid()) {
                LaunchedEffect(Unit) { finishAndRemoveTask() }
                return@setContent
            }
            LaunchedEffect(current.expiresAt) {
                delay((current.expiresAt - System.currentTimeMillis()).coerceAtLeast(0L))
                finishAndRemoveTask()
            }
            ClassToolsThemeHost(container) {
                val padding = if (compact) Modifier else Modifier.safeDrawingPadding().verticalScroll(rememberScrollState())
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxSize().then(padding).padding(if (compact) 8.dp else 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = if (compact) Arrangement.Center else Arrangement.spacedBy(16.dp)) {
                        if (!compact) Text("报名倒计时", style = MaterialTheme.typography.labelLarge)
                        Column(Modifier.fillMaxWidth().onGloballyPositioned {
                            val r = it.boundsInWindow()
                            faceBounds = Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
                        }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(current.title, maxLines = if (compact) 1 else 3, overflow = TextOverflow.Ellipsis,
                                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleLarge)
                            CountdownDigits(current.start, compact)
                        }
                        if (!compact) {
                            Text(current.details(), modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
                            Text("北京时间 · 按设备时钟计时", modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (supportsPip) Button(onClick = { pin() }, modifier = Modifier.fillMaxWidth()) { Text("悬浮小窗") }
                            if (current.signupUrl.isNotEmpty()) OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                                runCatching {
                                    val link = Intent(Intent.ACTION_VIEW, Uri.parse(current.signupUrl))
                                    if (supportsPip) pin()
                                    startActivity(link)
                                }.onFailure { CtToastBus.show("无法打开报名链接，请查看活动原文") }
                            }) { Text("打开报名入口") }
                            OutlinedButton(onClick = { startActivity(SignupCountdowns.noticeIntent(this@SignupCountdownActivity, id)) },
                                modifier = Modifier.fillMaxWidth()) { Text("查看活动原文") }
                            TextButton(onClick = {
                                SignupCountdowns.cancel(this@SignupCountdownActivity, owner, id)
                                finishAndRemoveTask()
                            }) { Text("结束倒计时") }
                        }
                    }
                }
            }
        }
    }

    private fun pin() {
        val params = PictureInPictureParams.Builder().setAspectRatio(Rational(2, 1))
        faceBounds?.takeUnless { it.isEmpty }?.let(params::setSourceRectHint)
        if (Build.VERSION.SDK_INT >= 31) params.setSeamlessResizeEnabled(false)
        if (!runCatching { enterPictureInPictureMode(params.build()) }.getOrDefault(false))
            CtToastBus.show("请在系统设置中允许画中画")
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        compact = isInPictureInPictureMode
    }
}

@Composable
private fun CountdownDigits(start: Long, compact: Boolean) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val left by produceState(start - System.currentTimeMillis(), start, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = start - System.currentTimeMillis()
                if (value <= 0) break
                val wait = SignupClock.delayMillis(value)
                if (wait == 0L) withFrameNanos { } else delay(wait)
            }
        }
    }
    Text(SignupClock.label(left), maxLines = 1, fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displaySmall)
}
