@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.furina.classtools.ui.screens.ppt

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.furina.classtools.data.ppt.PptImageLoader
import com.furina.classtools.data.ppt.PptMediaKind
import com.furina.classtools.data.ppt.PptVideoChapter
import com.furina.classtools.data.ppt.PptVideoInfo
import com.furina.classtools.features.ppt.PptPlayerLogic
import com.furina.classtools.features.ppt.PptVideoHealth
import com.furina.classtools.features.ppt.PptVideoLogic
import com.furina.classtools.ui.theme.CtPalette
import com.furina.classtools.ui.theme.CtRadius
import com.furina.classtools.ui.theme.CtType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 「原样播放」视频模式（契约 §11.3.1/§11.3.2）。
 *
 * - MediaPlayer + SurfaceView（项目无 ExoPlayer，不新增依赖）；H.264/AAC 系统解码；
 * - 自定义控制条：播放/暂停、`mm:ss / mm:ss`、章节上一页/下一页（按 chapters seek，非 ±10s）、
 *   章节刻度进度条（点击/拖动 seek）、倍速 1×/1.25×/1.5×/2×（PlaybackParams API≥23）、全屏（横竖屏切换）、
 *   下载原样视频（DownloadManager）、关闭、模式切换胶囊「原样/分步」；
 * - 点击舞台 = 显隐控制条；左右滑 = 上/下一页章节；键盘 Space/←/→/F/Esc/M；
 * - 失败/卡顿：重试 1 次 → 自动切分步 + Toast（`PptVideoHealth` 状态机）；
 * - 自动开始（用户从列表点击进入属用户手势）；被系统拒绝时显示大播放按钮。
 *
 * @param playbackEnabled 预览/测试可关（不创建 MediaPlayer，仅渲染静态控制条与封面）
 */
@Composable
fun PptVideoPlayer(
    title: String,
    video: PptVideoInfo,
    videoUrl: String,
    coverUrl: String,
    imageLoader: PptImageLoader,
    initialPositionSec: Double,
    autoPlay: Boolean,
    playbackEnabled: Boolean,
    onPersist: (positionSec: Double) -> Unit,
    onRequestSteps: (pageIndex: Int, manual: Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val chapters = video.chapters
    val durationSec = video.durationSec

    var positionSec by remember(video) {
        mutableStateOf(initialPositionSec.coerceAtLeast(0.0))
    }
    var effectiveDurationSec by remember(video) { mutableStateOf(durationSec) }
    var isPlaying by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf(!playbackEnabled) }
    var buffering by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var needsManualStart by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var landscape by remember { mutableStateOf(false) }

    val engine = remember(videoUrl, playbackEnabled) {
        if (playbackEnabled && videoUrl.isNotBlank()) PptVideoEngine(context) else null
    }
    val health = remember(videoUrl) { PptVideoHealth() }
    var retryToken by remember(videoUrl) { mutableIntStateOf(0) }

    fun revealControls() {
        controlsVisible = true
        interactionTick += 1
    }

    fun persistPosition() {
        onPersist(positionSec.coerceAtLeast(0.0))
    }

    fun seekTo(targetSec: Double, keepPlaying: Boolean = true) {
        val clamped = targetSec.coerceIn(0.0, (effectiveDurationSec.takeIf { it > 0 } ?: Double.MAX_VALUE))
        positionSec = clamped
        runCatching { engine?.seekToSec(clamped) }
        ended = false
        if (keepPlaying && isPlaying) {
            runCatching { engine?.play() }
        }
    }

    fun seekPage(pageIndex: Int) {
        val target = PptVideoLogic.seekTargetForPage(chapters, pageIndex) ?: return
        seekTo(target, keepPlaying = true)
        persistPosition()
    }

    fun previousPage() {
        val target = PptVideoLogic.previousSeekTarget(chapters, positionSec) ?: return
        seekTo(target)
        persistPosition()
    }

    fun nextPage() {
        val target = PptVideoLogic.nextSeekTarget(chapters, positionSec) ?: return
        seekTo(target)
        persistPosition()
    }

    fun switchSpeed() {
        speed = PptVideoLogic.nextSpeed(speed)
        runCatching { engine?.applySpeed(speed) }
        revealControls()
    }

    fun togglePlayPause() {
        if (ended) {
            ended = false
            seekTo(0.0, keepPlaying = false)
            runCatching { engine?.play() }
            isPlaying = true
            return
        }
        if (isPlaying) {
            runCatching { engine?.pause() }
            isPlaying = false
            persistPosition()
        } else {
            runCatching { engine?.play() }
            runCatching { engine?.applySpeed(speed) }
            isPlaying = true
        }
        revealControls()
    }

    fun toggleFullscreen() {
        landscape = !landscape
        activity?.requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        revealControls()
    }

    // ------------------------------------------------------------ 播放器引擎

    if (engine != null) {
        DisposableEffect(engine) {
            engine.onPrepared = { durationMs ->
                effectiveDurationSec = if (durationMs > 0) durationMs / 1000.0 else durationSec
                prepared = true
                buffering = false
                health.onStart(System.currentTimeMillis())
                // 恢复进度：自动续播 / 章节起始
                val startSec = initialPositionSec.coerceAtLeast(0.0)
                if (startSec > 0.5) runCatching { engine.seekToSec(startSec) }
                if (autoPlay) {
                    val started = runCatching { engine.play() }.isSuccess
                    isPlaying = started
                    needsManualStart = !started
                    runCatching { engine.applySpeed(speed) }
                } else {
                    needsManualStart = true
                }
            }
            engine.onCompletion = {
                isPlaying = false
                ended = true
                positionSec = effectiveDurationSec
                persistPosition()
            }
            engine.onError = {
                buffering = false
                isPlaying = false
                val lastPage = PptVideoLogic.pageAt(chapters, positionSec)
                when (health.onError()) {
                    PptVideoHealth.Action.RETRY -> {
                        retryToken += 1
                        engine.open(videoUrl)
                    }

                    PptVideoHealth.Action.SWITCH_TO_STEPS -> {
                        persistPosition()
                        toast(context, "网络不佳，已切换分步浏览")
                        onRequestSteps(lastPage, false)
                    }

                    PptVideoHealth.Action.NONE -> Unit
                }
            }
            engine.onBuffering = { buffering = it }
            engine.open(videoUrl)
            onDispose {
                persistPosition()
                engine.release()
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    // 位置轮询 + 卡顿检测（4s×2 → 重试 1 次 → 切分步）
    LaunchedEffect(engine, retryToken) {
        while (isActive) {
            val current = engine?.positionMs()?.let { it / 1000.0 } ?: positionSec
            if (positionSec != current) positionSec = current
            val playing = engine?.isPlaying() ?: isPlaying
            if (playing != isPlaying) isPlaying = playing
            if (engine != null && prepared) {
                when (health.onTick(System.currentTimeMillis(), (current * 1000).toLong(), playing)) {
                    PptVideoHealth.Action.RETRY -> {
                        retryToken += 1
                        engine.open(videoUrl)
                    }

                    PptVideoHealth.Action.SWITCH_TO_STEPS -> {
                        persistPosition()
                        toast(context, "网络不佳，已切换分步浏览")
                        onRequestSteps(PptVideoLogic.pageAt(chapters, current), false)
                    }

                    PptVideoHealth.Action.NONE -> Unit
                }
            }
            delay(250)
        }
    }

    // 周期保存进度（每 5s）+ 控制条自动隐藏
    LaunchedEffect(engine) {
        while (isActive) {
            delay(5_000)
            if (engine != null) persistPosition()
        }
    }
    LaunchedEffect(interactionTick, controlsVisible, isPlaying) {
        if (!controlsVisible) return@LaunchedEffect
        if (!isPlaying) return@LaunchedEffect // 暂停时控制条常驻，便于课堂操作
        delay(PptPlayerLogic.CONTROLS_AUTO_HIDE_MS)
        controlsVisible = false
    }

    val keyFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { keyFocus.requestFocus() } }

    val backOwner = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current
    if (backOwner != null) {
        androidx.activity.compose.BackHandler {
            persistPosition()
            onClose()
        }
    }

    val minSwipePx = with(LocalDensity.current) { 40.dp.toPx() }
    val aspect = PptVideoLogic.aspectRatio(video)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CtPalette.MediaScrimSoft)
            .focusRequester(keyFocus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Spacebar -> {
                        togglePlayPause()
                        true
                    }

                    Key.DirectionRight, Key.PageDown -> {
                        nextPage()
                        true
                    }

                    Key.DirectionLeft, Key.PageUp -> {
                        previousPage()
                        true
                    }

                    Key.F -> {
                        toggleFullscreen()
                        true
                    }

                    Key.Escape -> {
                        persistPosition()
                        onClose()
                        true
                    }

                    Key.M -> {
                        persistPosition()
                        onRequestSteps(PptVideoLogic.pageAt(chapters, positionSec), true)
                        true
                    }

                    else -> false
                }
            },
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val stage: Pair<Dp, Dp> = if (maxWidth.value <= 0f || maxHeight.value <= 0f) {
                0.dp to 0.dp
            } else if (maxWidth.value / maxHeight.value > aspect) {
                (maxHeight * aspect) to maxHeight
            } else {
                maxWidth to (maxWidth / aspect)
            }
            Box(
                modifier = Modifier
                    .size(stage.first, stage.second)
                    .align(Alignment.Center)
                    .clip(androidx.compose.ui.graphics.RectangleShape)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            controlsVisible = !controlsVisible
                            interactionTick += 1
                        }
                    }
                    .pointerInput(minSwipePx) {
                        var drag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { drag = 0f },
                            onDragEnd = {
                                val screenWidthPx = size.width.toFloat()
                                when (
                                    PptPlayerLogic.swipeTarget(
                                        drag,
                                        PptPlayerLogic.swipeThresholdPx(screenWidthPx, minSwipePx),
                                    )
                                ) {
                                    PptPlayerLogic.SwipeTarget.NEXT -> nextPage()
                                    PptPlayerLogic.SwipeTarget.PREV -> previousPage()
                                    PptPlayerLogic.SwipeTarget.NONE -> Unit
                                }
                                drag = 0f
                            },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                drag += amount
                            },
                        )
                    },
            ) {
                if (engine != null) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceView(ctx).apply {
                                holder.addCallback(engine.surfaceCallback)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // 预览 / 关闭播放：封面占位
                    PptAsyncImage(
                        url = coverUrl,
                        loader = imageLoader,
                        modifier = Modifier.fillMaxSize(),
                        targetWidth = PptImageLoader.DISPLAY_TARGET_WIDTH,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        fallback = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = CtPalette.MediaOnScrim.copy(alpha = 0.3f),
                                    modifier = Modifier.size(64.dp),
                                )
                            }
                        },
                    )
                }

                if (engine != null && (!prepared || buffering)) {
                    CircularProgressIndicator(
                        color = CtPalette.MediaOnScrim.copy(alpha = 0.7f),
                        strokeWidth = 2.dp,
                        modifier = Modifier.align(Alignment.Center).size(30.dp),
                    )
                }

                if (needsManualStart || ended) {
                    Surface(
                        shape = CircleShape,
                        color = CtPalette.MediaScrimSoft.copy(alpha = 0.45f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(76.dp)
                            .clickable {
                                needsManualStart = false
                                if (ended) {
                                    ended = false
                                    seekTo(0.0, keepPlaying = false)
                                }
                                runCatching { engine?.play() }
                                isPlaying = true
                                revealControls()
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (ended) Icons.Rounded.Replay else Icons.Rounded.PlayArrow,
                                contentDescription = "播放",
                                tint = CtPalette.MediaOnScrim,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                }

            }
        }

        // ---------------------------------------------------------- 顶部：标题 + 模式胶囊
        androidx.compose.animation.AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 18.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title.ifBlank { "演示" },
                    color = CtPalette.MediaOnScrim.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // 「从头播放」（契约 §11.3.4：控制条/设置提供从头播放）
                if (positionSec > 5.0 || ended) {
                    Surface(
                        shape = CircleShape,
                        color = CtPalette.MediaOnScrim.copy(alpha = 0.14f),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                ended = false
                                seekTo(0.0, keepPlaying = false)
                                runCatching { engine?.play() }
                                isPlaying = true
                                revealControls()
                            },
                    ) {
                        Text(
                            text = "从头",
                            color = CtPalette.MediaOnScrim,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Surface(
                    shape = CircleShape,
                    color = CtPalette.MediaOnScrim.copy(alpha = 0.14f),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            persistPosition()
                            onRequestSteps(PptVideoLogic.pageAt(chapters, positionSec), true)
                        },
                ) {
                    Text(
                        text = "原样",
                        color = CtPalette.MediaOnScrim,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }

        // ---------------------------------------------------------- 底部控制条
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PptVideoProgressBar(
                    positionSec = positionSec,
                    durationSec = effectiveDurationSec.takeIf { it > 0 } ?: durationSec,
                    chapters = chapters,
                    onSeek = { target ->
                        seekTo(target)
                        interactionTick += 1
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    IconButton(onClick = ::togglePlayPause, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = CtPalette.MediaOnScrim,
                        )
                    }
                    Text(
                        text = "${PptVideoLogic.formatTime(positionSec)} / " +
                            PptVideoLogic.formatTime(effectiveDurationSec.takeIf { it > 0 } ?: durationSec),
                        color = CtPalette.MediaOnScrim,
                        fontSize = CtType.labelSize,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = ::previousPage, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronLeft,
                            contentDescription = "上一页",
                            tint = CtPalette.MediaOnScrim.copy(alpha = 0.9f),
                        )
                    }
                    IconButton(onClick = ::nextPage, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "下一页",
                            tint = CtPalette.MediaOnScrim.copy(alpha = 0.9f),
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(CtRadius.tile),
                        color = CtPalette.MediaOnScrim.copy(alpha = 0.12f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(CtRadius.tile))
                            .clickable { switchSpeed() },
                    ) {
                        Text(
                            text = PptVideoLogic.speedLabel(speed),
                            color = CtPalette.MediaOnScrim,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                    IconButton(onClick = ::toggleFullscreen, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (landscape) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                            contentDescription = if (landscape) "退出全屏" else "全屏",
                            tint = CtPalette.MediaOnScrim.copy(alpha = 0.9f),
                        )
                    }
                    IconButton(
                        onClick = {
                            downloadToSystemDownloads(
                                context = context,
                                url = videoUrl,
                                fileName = "${title.ifBlank { "演示" }}.mp4",
                                mimeType = "video/mp4",
                            )
                            revealControls()
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "下载原样视频",
                            tint = CtPalette.MediaOnScrim.copy(alpha = 0.9f),
                        )
                    }
                    IconButton(
                        onClick = {
                            persistPosition()
                            onClose()
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭",
                            tint = CtPalette.MediaOnScrim.copy(alpha = 0.9f),
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// 章节进度条（含章节刻度，点击/拖动 seek）
// =============================================================================

@Composable
private fun PptVideoProgressBar(
    positionSec: Double,
    durationSec: Double,
    chapters: List<PptVideoChapter>,
    onSeek: (Double) -> Unit,
) {
    val fractionTarget = PptVideoLogic.progress(positionSec, durationSec)
    val fraction by animateFloatAsState(targetValue = fractionTarget, animationSpec = tween(120), label = "video-progress")
    val density = LocalDensity.current
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val shown = dragFraction ?: fraction
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(durationSec, chapters) {
                detectTapGestures { offset ->
                    if (durationSec > 0 && size.width > 0) {
                        val f = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onSeek(f * durationSec)
                    }
                }
            }
            .pointerInput(durationSec, chapters) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (durationSec > 0 && size.width > 0) {
                            dragFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        }
                    },
                    onDragEnd = {
                        val f = dragFraction
                        dragFraction = null
                        if (f != null && durationSec > 0) onSeek(f * durationSec)
                    },
                    onDragCancel = { dragFraction = null },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        val deltaFraction = if (size.width > 0) amount / size.width.toFloat() else 0f
                        val current = (dragFraction ?: fraction) + deltaFraction
                        dragFraction = current.coerceIn(0f, 1f)
                    },
                )
            },
    ) {
        // 轨道
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(CtPalette.MediaOnScrim.copy(alpha = 0.22f)),
        )
        // 已播放
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(shown)
                .height(4.dp)
                .clip(CircleShape)
                .background(CtPalette.MediaOnScrim.copy(alpha = 0.9f)),
        )
        // 章节刻度
        chapters.forEach { chapter ->
            if (durationSec > 0) {
                val frac = PptVideoLogic.progress(chapter.startSec, durationSec)
                if (frac <= 0.001f) return@forEach
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = with(density) { (0f).toDp() })
                        .fillMaxWidth(frac)
                        .height(12.dp)
                        .width(0.dp),
                )
            }
        }
        // 用 Canvas 画刻度更稳：单独一层
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val h = size.height
            chapters.forEach { chapter ->
                if (durationSec > 0) {
                    val frac = PptVideoLogic.progress(chapter.startSec, durationSec)
                    if (frac > 0.001f && frac < 0.999f) {
                        val x = size.width * frac
                        drawLine(
                            color = CtPalette.MediaOnScrim.copy(alpha = 0.75f),
                            start = androidx.compose.ui.geometry.Offset(x, h * 0.25f),
                            end = androidx.compose.ui.geometry.Offset(x, h * 0.75f),
                            strokeWidth = 2f,
                        )
                    }
                }
            }
            // 滑块
            val x = size.width * shown
            drawCircle(
                color = CtPalette.MediaOnScrim,
                radius = h * 0.28f,
                center = androidx.compose.ui.geometry.Offset(x, h / 2f),
            )
        }
    }
}

// =============================================================================
// MediaPlayer + SurfaceView 引擎
// =============================================================================

/**
 * MediaPlayer + SurfaceView 的最小封装：自定义控制条（倍速/章节 seek）无法用 VideoView 的私有
 * MediaPlayer，因此直接用 MediaPlayer + SurfaceView（API 26 minSdk，PlaybackParams API 23+ 可用）。
 */
private class PptVideoEngine(private val context: Context) {

    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var pendingUrl: String? = null

    var onPrepared: ((durationMs: Int) -> Unit)? = null
    var onCompletion: (() -> Unit)? = null
    var onError: (() -> Unit)? = null
    var onBuffering: ((Boolean) -> Unit)? = null

    val surfaceCallback: SurfaceHolder.Callback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            surface = holder.surface
            runCatching { player?.setSurface(holder.surface) }
            // surface 就绪前已 open 的：补设 surface
            pendingUrl?.let { url ->
                if (player == null) open(url)
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            runCatching { player?.setSurface(null) }
            surface = null
        }
    }

    fun open(url: String) {
        pendingUrl = url
        release()
        val mp = MediaPlayer()
        player = mp
        runCatching {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
            )
            mp.setDataSource(url)
            mp.setOnPreparedListener { onPrepared?.invoke(it.duration) }
            mp.setOnCompletionListener { onCompletion?.invoke() }
            mp.setOnErrorListener { _, _, _ ->
                onError?.invoke()
                true
            }
            mp.setOnInfoListener { _, what, _ ->
                when (what) {
                    MediaPlayer.MEDIA_INFO_BUFFERING_START -> onBuffering?.invoke(true)
                    MediaPlayer.MEDIA_INFO_BUFFERING_END -> onBuffering?.invoke(false)
                }
                false
            }
            surface?.let { mp.setSurface(it) }
            mp.prepareAsync()
        }.onFailure {
            onError?.invoke()
        }
    }

    fun play() {
        val mp = player ?: return
        runCatching { mp.start() }
    }

    fun pause() {
        val mp = player ?: return
        runCatching { mp.pause() }
    }

    fun seekToSec(seconds: Double) {
        val mp = player ?: return
        runCatching { mp.seekTo(((seconds.coerceAtLeast(0.0) * 1000).toLong()).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()) }
    }

    fun positionMs(): Long = runCatching { player?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)

    fun durationMs(): Long = runCatching { player?.duration?.toLong() ?: 0L }.getOrDefault(0L)

    fun isPlaying(): Boolean = runCatching { player?.isPlaying == true }.getOrDefault(false)

    /** 倍速（PlaybackParams API≥23；音频保持音调）。 */
    fun applySpeed(speed: Float) {
        val mp = player ?: return
        runCatching {
            val params = PlaybackParams()
                .setSpeed(speed)
                .setPitch(1f)
                .setAudioFallbackMode(PlaybackParams.AUDIO_FALLBACK_MODE_DEFAULT)
            mp.playbackParams = params
        }
    }

    fun release() {
        val mp = player
        player = null
        runCatching { mp?.setSurface(null) }
        runCatching { mp?.reset() }
        runCatching { mp?.release() }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun toast(context: Context, message: String) {
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
}
