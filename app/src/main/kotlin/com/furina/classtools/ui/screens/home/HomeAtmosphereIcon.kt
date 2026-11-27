package com.furina.classtools.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.furina.classtools.features.home.HomeAtmosphere
import com.furina.classtools.ui.theme.LocalCtDarkTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

private const val PULSE_MS = 460
private const val FEEDBACK_MS = 2200L

internal val HomeAtmosphere.feedback: String
    get() = when (this) {
        HomeAtmosphere.RAIN -> "小伞已就位，今天也替你挡一点雨。"
        HomeAtmosphere.SUN -> "给今天加一点亮色。"
        HomeAtmosphere.NIGHT -> "给今晚留一盏小灯。"
    }

internal class HomeAtmosphereResponse {
    val progress = Animatable(0f)
    var showFeedback by mutableStateOf(false)
        private set
    var tap by mutableIntStateOf(0)
        private set

    fun trigger() {
        showFeedback = true
        tap += 1
    }

    suspend fun play(motion: Boolean) {
        progress.snapTo(0f)
        if (tap == 0) return
        if (motion) {
            progress.animateTo(1f, tween(PULSE_MS, easing = LinearEasing))
        }
        delay(FEEDBACK_MS - if (motion) PULSE_MS else 0)
        showFeedback = false
    }
}

@Composable
internal fun rememberHomeAtmosphereResponse(
    scene: HomeAtmosphere?,
    account: String,
    sessionIssuedAt: Long,
    active: Boolean,
    motion: Boolean,
): HomeAtmosphereResponse? {
    // Keep the artwork visible as the workbench gives way to another page.
    if (scene == null) return null
    val response = remember(scene, account, sessionIssuedAt, active, motion) { HomeAtmosphereResponse() }
    LaunchedEffect(response, response.tap) {
        if (active) response.play(motion)
    }
    return response
}

@Composable
internal fun HomeAtmosphereIcon(scene: HomeAtmosphere, response: HomeAtmosphereResponse) {
    val label = when (scene) {
        HomeAtmosphere.RAIN -> "轻点小伞"
        HomeAtmosphere.SUN -> "轻点太阳"
        HomeAtmosphere.NIGHT -> "点亮小灯"
    }
    val accent = if (scene == HomeAtmosphere.RAIN) MaterialTheme.colorScheme.primary
        else if (LocalCtDarkTheme.current) Color(0xFFFFD580) else Color(0xFF8C5B19)

    Box(
        modifier = Modifier.size(52.dp).testTag("home_context_atmosphere")
            .clickable(role = Role.Button, onClick = response::trigger)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(36.dp)) {
            val phase = response.progress.value
            val glow = sin(PI * phase).toFloat().coerceAtLeast(0f)
            scale(size.width / 36f, pivot = Offset.Zero) {
                when (scene) {
                    HomeAtmosphere.RAIN -> drawRain(accent, phase, glow)
                    HomeAtmosphere.SUN -> drawSun(accent, glow)
                    HomeAtmosphere.NIGHT -> drawLamp(accent, glow)
                }
            }
        }
    }
}

private fun DrawScope.drawRain(accent: Color, phase: Float, glow: Float) {
    val tilt = -8f * sin(2 * PI * phase).toFloat()
    rotate(tilt, pivot = Offset(17f, 19f)) {
        val canopy = Path().apply {
            moveTo(5f, 20f)
            cubicTo(7f, 8f, 27f, 8f, 29f, 20f)
            quadraticTo(26f, 17f, 23f, 20f)
            quadraticTo(20f, 17f, 17f, 20f)
            quadraticTo(14f, 17f, 11f, 20f)
            quadraticTo(8f, 17f, 5f, 20f)
            close()
        }
        drawPath(canopy, accent.copy(alpha = 0.14f))
        drawPath(canopy, accent, style = Stroke(1.65f, join = StrokeJoin.Round))
        val handle = Path().apply {
            moveTo(17f, 20f)
            lineTo(17f, 28f)
            cubicTo(17f, 33f, 24f, 33f, 24f, 28f)
        }
        drawPath(handle, accent, style = Stroke(1.65f, cap = StrokeCap.Round))
    }
    drawLine(accent.copy(alpha = 0.65f + 0.30f * glow), Offset(25f, 4f + glow), Offset(23f, 7f + glow),
        strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawLine(accent.copy(alpha = 0.72f + 0.23f * glow), Offset(32f, 8f + 2f * glow), Offset(30f, 11f + 2f * glow),
        strokeWidth = 1.6f, cap = StrokeCap.Round)
}

private fun DrawScope.drawSun(accent: Color, glow: Float) {
    val center = Offset(18f, 18f)
    drawCircle(accent.copy(alpha = 0.045f + 0.12f * glow), 14f + 3f * glow, center)
    drawCircle(accent.copy(alpha = 0.18f + 0.20f * glow), 6.4f, center)
    drawCircle(accent, 6.4f, center, style = Stroke(1.7f))
    for (ray in 0..7) {
        val angle = PI * ray / 4
        val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
        drawLine(accent, center + direction * 10f, center + direction * (13.6f + 2f * glow),
            strokeWidth = 1.65f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawLamp(accent: Color, glow: Float) {
    drawCircle(accent.copy(alpha = 0.06f + 0.19f * glow), 9f + 5f * glow, Offset(18f, 19f))
    val shade = Path().apply {
        moveTo(13.6f, 8.5f)
        lineTo(22.4f, 8.5f)
        lineTo(27f, 19f)
        lineTo(9f, 19f)
        close()
    }
    drawPath(shade, accent.copy(alpha = 0.14f + 0.29f * glow))
    drawPath(shade, accent, style = Stroke(1.7f, join = StrokeJoin.Round))
    drawLine(accent, Offset(18f, 19.8f), Offset(18f, 28f), strokeWidth = 1.7f, cap = StrokeCap.Round)
    drawLine(accent, Offset(12.7f, 28f), Offset(23.3f, 28f), strokeWidth = 1.7f, cap = StrokeCap.Round)
}
