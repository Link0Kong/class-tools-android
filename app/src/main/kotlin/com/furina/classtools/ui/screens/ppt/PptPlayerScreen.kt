@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.furina.classtools.ui.screens.ppt

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.view.WindowManager
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import com.furina.classtools.ui.theme.rememberReduceMotion
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.furina.classtools.data.ppt.PptDeckDetail
import com.furina.classtools.data.ppt.PptHotspot
import com.furina.classtools.data.ppt.PptImageLoader
import com.furina.classtools.data.ppt.PptLocalStore
import com.furina.classtools.data.ppt.PptManifest
import com.furina.classtools.data.ppt.PptMediaDef
import com.furina.classtools.data.ppt.PptMediaKind
import com.furina.classtools.data.ppt.PptNetworkQualityProvider
import com.furina.classtools.data.ppt.PptPlayerResult
import com.furina.classtools.data.ppt.PptRepository
import com.furina.classtools.features.ppt.PptModeChooser
import com.furina.classtools.features.ppt.PptNav
import com.furina.classtools.features.ppt.PptNetworkQuality
import com.furina.classtools.features.ppt.PptPlayMode
import com.furina.classtools.features.ppt.PptPlayerLogic
import com.furina.classtools.features.ppt.PptResumeLogic
import com.furina.classtools.features.ppt.PptResumeRecord
import com.furina.classtools.features.ppt.PptVideoLogic
import com.furina.classtools.ui.platform.VolumeKeyBridge
import kotlinx.coroutines.delay

/**
 * 沉浸式全屏播放器页（契约 §5/§7/§8.1 + DESIGN.md §4.3–§4.11）。
 *
 * 课堂可用要点：
 * - 纯黑舞台 + letterbox（contain，不裁切）；顶部 2px step 进度条；左上文件名淡出；
 * - 单击右 2/3 下一步、左 1/3 上一步（后退粒度 = step，step=0 退上页末步）；左右滑动；
 *   键盘 →/←/Space/PageDown/PageUp/Esc/B/T/S；**音量键 ± 翻页**；系统返回键先关视频/抽屉再退；
 * - 底部 48dp 控制条 3s 自动隐藏（触摸唤出，键盘翻页不唤出）；页内步点仅 step≥2 显示；
 * - 缩略图横向抽屉（点页跳转）、备注抽屉、黑屏 `B`、更多（下载原件/退出演示）；
 * - 视频/音频热点：点击才播（不自动播），媒体层不随 step 卸载，只在换页/退出时销毁；
 * - 图片失败保留上一张 + 「图片加载失败 · 重试」，不白屏不阻塞翻页；
 * - 低端机页切换降级 fade 200ms（契约 §5 允许）。
 */
@Composable
fun PptPlayerScreen(
    deckId: String,
    onClose: () -> Unit,
    repository: PptRepository,
    imageLoader: PptImageLoader,
    modifier: Modifier = Modifier,
    localStore: PptLocalStore? = null,
    networkQuality: PptNetworkQualityProvider? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }

    var loading by remember(deckId) { mutableStateOf(true) }
    var detail by remember(deckId) { mutableStateOf<PptDeckDetail?>(null) }
    var error by remember(deckId) { mutableStateOf<String?>(null) }
    var retryToken by remember(deckId) { mutableIntStateOf(0) }

    LaunchedEffect(deckId, retryToken) {
        loading = true
        error = null
        when (val result = repository.loadPlayer(deckId)) {
            is PptPlayerResult.Success -> {
                detail = result.detail
                error = null
            }

            is PptPlayerResult.NotReady -> {
                detail = null
                error = result.message
            }

            is PptPlayerResult.Failure -> {
                detail = null
                error = result.message
            }
        }
        loading = false
    }

    // 沉浸式：隐藏系统栏 + 屏幕常亮 + BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE；退出恢复。
    DisposableEffect(activity, view) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.show(WindowInsetsCompat.Type.systemBars())
            imageLoader.clearMemory()
        }
    }

    val loaded = detail
    if (loaded != null) {
        PptPlayerContent(
            detail = loaded,
            imageLoader = imageLoader,
            onClose = onClose,
            modifier = modifier,
            deckId = deckId,
            localStore = localStore,
            networkQuality = networkQuality,
        )
    } else {
        PptPlayerLoading(
            loading = loading,
            error = error,
            onClose = onClose,
            onRetry = { retryToken += 1 },
            modifier = modifier,
        )
    }
}

// =============================================================================
// 播放器内容层（无网络加载副作用；@Preview 直接复用）
// =============================================================================

/**
 * 播放器内容层（无网络加载副作用；@Preview 直接复用）。
 *
 * v1.2 模式编排（契约 §11.3）：
 * - `manifest.video` 存在 + 网络良好（且用户本机偏好未指定分步）→ 默认「原样播放」并自动开始；
 * - 无 video / 网络差 → 分步浏览；
 * - 用户手动切换后记住该设备偏好（SharedPreferences）；视频失败/卡顿自动回退分步（不写偏好）；
 * - 续播：7 天内位置（视频秒数 / 页·步）自动恢复，控制条/Toast 提示「续播」，可「从头播放」。
 *
 * @param deckId 续播/prefs 键（预览可空）
 * @param localStore 本机续播与模式偏好（null = 预览/测试不读写本机）
 * @param networkQuality 默认模式判定（null = 视为网络良好）
 * @param initialMode 预览注入：强制初始模式
 * @param initialVideoSec 预览注入：视频起始秒
 * @param videoPlaybackEnabled 预览/测试：false 时不创建 MediaPlayer（只渲染控制条与封面）
 */
@Composable
fun PptPlayerContent(
    detail: PptDeckDetail,
    imageLoader: PptImageLoader,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    deckId: String = "",
    localStore: PptLocalStore? = null,
    networkQuality: PptNetworkQualityProvider? = null,
    initialMode: PptPlayMode? = null,
    initialVideoSec: Double = 0.0,
    videoPlaybackEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val manifest = detail.manifest
    val counts = remember(manifest) { manifest.stepCounts }
    val video = manifest.video
    val hasVideo = manifest.hasVideo
    val entryNow = remember(deckId) { System.currentTimeMillis() }
    val savedResume = remember(deckId, localStore) { localStore?.loadResume(deckId) }
    val resumable = remember(savedResume, counts, video) {
        PptResumeLogic.shouldResume(savedResume, entryNow, counts, video?.durationSec ?: 0.0)
    }
    val network = remember(networkQuality) { networkQuality?.current() ?: PptNetworkQuality.GOOD }
    val preference = remember(localStore) { localStore?.playModePreference() }
    var mode by remember(manifest) {
        mutableStateOf(initialMode ?: PptModeChooser.initialMode(hasVideo, network, preference))
    }
    var stepsStart by remember(manifest) {
        mutableStateOf(
            if (resumable && savedResume?.mode == PptPlayMode.STEPS) {
                PptNav.normalize(counts, PptNav.PptPosition(savedResume.page, savedResume.step))
            } else {
                null
            },
        )
    }
    var videoStartSec by remember(manifest) {
        mutableStateOf(
            when {
                initialVideoSec > 0.0 -> initialVideoSec
                resumable && savedResume?.mode == PptPlayMode.VIDEO -> savedResume.videoSec
                else -> 0.0
            },
        )
    }
    val persist: (PptResumeRecord) -> Unit = remember(deckId, localStore) {
        { record ->
            if (deckId.isNotBlank()) {
                localStore?.saveResume(deckId, record.copy(updatedAtMs = System.currentTimeMillis()))
            }
        }
    }

    LaunchedEffect(Unit) {
        if (resumable && savedResume != null) toast(context, PptResumeLogic.resumeHint(savedResume))
        runCatching { localStore?.pruneResumes() }
    }

    fun switchToVideo(fromPage: Int) {
        localStore?.setPlayModePreference(PptPlayMode.VIDEO)
        videoStartSec = PptVideoLogic.seekTargetForPage(video?.chapters.orEmpty(), fromPage) ?: 0.0
        mode = PptPlayMode.VIDEO
    }

    fun switchToSteps(fromPage: Int, manual: Boolean) {
        if (manual) localStore?.setPlayModePreference(PptPlayMode.STEPS)
        stepsStart = PptNav.jumpToSlide(counts, fromPage)
        mode = PptPlayMode.STEPS
    }

    if (mode == PptPlayMode.VIDEO && hasVideo && video != null) {
        PptVideoPlayer(
            title = detail.title,
            video = video,
            videoUrl = manifest.videoUrl,
            coverUrl = detail.coverDisplayUrl,
            imageLoader = imageLoader,
            initialPositionSec = videoStartSec,
            autoPlay = true,
            playbackEnabled = videoPlaybackEnabled,
            onPersist = { positionSec -> persist(PptResumeRecord(PptPlayMode.VIDEO, videoSec = positionSec)) },
            onRequestSteps = { pageIndex, manual -> switchToSteps(pageIndex, manual) },
            onClose = onClose,
            modifier = modifier,
        )
    } else {
        PptStepsPlayer(
            detail = detail,
            imageLoader = imageLoader,
            initialPosition = stepsStart,
            onPersist = persist,
            onSwitchToVideo = if (hasVideo) ({ page -> switchToVideo(page) }) else null,
            onClose = onClose,
            modifier = modifier,
        )
    }
}

// =============================================================================
// 分步浏览模式（保留 v1.1 全部能力）
// =============================================================================

@Composable
private fun PptStepsPlayer(
    detail: PptDeckDetail,
    imageLoader: PptImageLoader,
    initialPosition: PptNav.PptPosition?,
    onPersist: (PptResumeRecord) -> Unit,
    onSwitchToVideo: ((currentPage: Int) -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val manifest: PptManifest = detail.manifest
    val counts = remember(manifest) { manifest.stepCounts }
    val lowEnd = remember {
        val cores = Runtime.getRuntime().availableProcessors()
        val memoryMb = runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.memoryClass
        }.getOrDefault(512)
        PptPlayerLogic.isLowEndDevice(cores, memoryMb)
    }

    var position by remember(manifest) {
        mutableStateOf(
            initialPosition?.let { PptNav.normalize(counts, it) } ?: PptNav.first(),
        )
    }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var showThumbs by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var blackScreen by remember { mutableStateOf(false) }
    var exitConfirm by remember { mutableStateOf(false) }
    var retryToken by remember { mutableIntStateOf(0) }
    var mediaPlayback by remember { mutableStateOf<PptMediaPlayback?>(null) }

    val images = remember(manifest) { mutableStateMapOf<String, ImageBitmap>() }
    val failed = remember(manifest) { mutableStateMapOf<String, Boolean>() }
    val mediaFailed = remember(manifest) { mutableStateMapOf<String, Boolean>() }
    var lastGood by remember(manifest) { mutableStateOf<ImageBitmap?>(null) }
    var cover by remember(manifest) { mutableStateOf<ImageBitmap?>(null) }

    val pos = PptNav.normalize(counts, position)
    val currentSlide = manifest.slides.getOrNull(pos.slide)
    val currentStep = currentSlide?.steps?.getOrNull(pos.step)
    val currentRef = currentStep?.img.orEmpty()
    val currentUrl = remember(manifest, currentRef) { manifest.resolveImage(currentRef) }
    val globalIndex = PptNav.globalIndex(counts, pos)

    // 续播保存（契约 §11.3.4）：每次页/步变化写本机（SharedPreferences apply 不阻塞）。
    LaunchedEffect(pos.slide, pos.step) {
        onPersist(PptResumeRecord(PptPlayMode.STEPS, page = pos.slide, step = pos.step))
    }

    fun persistPosition() {
        onPersist(PptResumeRecord(PptPlayMode.STEPS, page = pos.slide, step = pos.step))
    }

    fun next() {
        PptNav.next(counts, pos)?.let { position = it }
    }

    fun prev() {
        PptNav.prev(counts, pos)?.let { position = it }
    }

    fun revealControls() {
        controlsVisible = true
        interactionTick += 1
    }

    fun requestClose() {
        when {
            mediaPlayback != null -> mediaPlayback = null
            showThumbs -> showThumbs = false
            showNotes -> showNotes = false
            blackScreen -> blackScreen = false
            else -> {
                persistPosition()
                val inProgress = globalIndex > 0 && !PptNav.isEnd(counts, pos)
                if (inProgress) {
                    exitConfirm = true
                } else {
                    onClose()
                }
            }
        }
    }

    // ------------------------------------------------------------ 图片加载 / 预加载

    LaunchedEffect(detail.coverDisplayUrl, imageLoader) {
        if (detail.coverDisplayUrl.isNotBlank()) {
            cover = imageLoader.load(detail.coverDisplayUrl, PptImageLoader.DISPLAY_TARGET_WIDTH)
        }
    }

    LaunchedEffect(currentUrl, retryToken, imageLoader) {
        if (currentUrl.isEmpty()) return@LaunchedEffect
        images[currentUrl]?.let {
            lastGood = it
            return@LaunchedEffect
        }
        failed.remove(currentUrl)
        val bitmap = imageLoader.load(currentUrl, PptImageLoader.DISPLAY_TARGET_WIDTH)
        if (bitmap != null) {
            images[currentUrl] = bitmap
            lastGood = bitmap
            failed.remove(currentUrl)
        } else {
            failed[currentUrl] = true
        }
    }

    // 预加载：当前步之后 2 张（契约 §8.1；当前最高优先级）
    LaunchedEffect(globalIndex, currentUrl, retryToken, imageLoader) {
        if (counts.isEmpty()) return@LaunchedEffect
        for (delta in 1..2) {
            val target = PptNav.positionAt(counts, globalIndex + delta)
            if (PptNav.globalIndex(counts, target) <= globalIndex) break
            val ref = manifest.slides.getOrNull(target.slide)?.steps?.getOrNull(target.step)?.img.orEmpty()
            val url = manifest.resolveImage(ref)
            if (url.isEmpty() || images.containsKey(url) || failed[url] == true) continue
            imageLoader.load(url, PptImageLoader.DISPLAY_TARGET_WIDTH)?.let { images[url] = it }
        }
    }

    // 媒体层只在换页 / 退出时销毁（契约 §5 补充）
    LaunchedEffect(pos.slide) {
        val playback = mediaPlayback
        if (playback != null && playback.slideIndex != pos.slide) mediaPlayback = null
    }

    // 控制条 3s 自动隐藏（触摸/点击会重置 interactionTick）
    LaunchedEffect(interactionTick, controlsVisible, showThumbs, showNotes, blackScreen, mediaPlayback) {
        if (!controlsVisible) return@LaunchedEffect
        if (showThumbs || showNotes || blackScreen || mediaPlayback != null) return@LaunchedEffect
        delay(PptPlayerLogic.CONTROLS_AUTO_HIDE_MS)
        controlsVisible = false
    }

    // 音量键 ± 翻页（Activity 转发；forward=音量+ = 前进）
    DisposableEffect(Unit) {
        VolumeKeyBridge.handler = handler@{ forward ->
            when {
                blackScreen -> {
                    blackScreen = false
                    true
                }

                else -> {
                    if (forward) next() else prev()
                    true
                }
            }
        }
        onDispose { VolumeKeyBridge.handler = null }
    }

    val backOwner = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current
    if (backOwner != null) {
        BackHandler { requestClose() }
    }

    val keyFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { keyFocus.requestFocus() } }

    val minSwipePx = with(LocalDensity.current) { 40.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(keyFocus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when {
                    blackScreen -> {
                        blackScreen = false
                        true
                    }

                    event.key == Key.DirectionRight || event.key == Key.Spacebar ||
                        event.key == Key.PageDown || event.key == Key.MoveEnd -> {
                        next()
                        true
                    }

                    event.key == Key.DirectionLeft || event.key == Key.PageUp ||
                        event.key == Key.MoveHome -> {
                        prev()
                        true
                    }

                    event.key == Key.Escape -> {
                        requestClose()
                        true
                    }

                    event.key == Key.B -> {
                        blackScreen = true
                        true
                    }

                    event.key == Key.T -> {
                        showThumbs = !showThumbs
                        revealControls()
                        true
                    }

                    event.key == Key.S -> {
                        showNotes = !showNotes
                        revealControls()
                        true
                    }

                    else -> false
                }
            },
    ) {
        // ---------------------------------------------------------- 舞台
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val aspect = PptPlayerLogic.aspectRatio(manifest.width, manifest.height)
            val availW = maxWidth
            val availH = maxHeight
            val stage: Pair<Dp, Dp> = if (availW.value <= 0f || availH.value <= 0f) {
                0.dp to 0.dp
            } else if (availW.value / availH.value > aspect) {
                (availH * aspect) to availH
            } else {
                availW to (availW / aspect)
            }
            val stageW = stage.first
            val stageH = stage.second

            Box(
                modifier = Modifier
                    .size(stageW, stageH)
                    .align(Alignment.Center)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { offset ->
                            when {
                                blackScreen -> blackScreen = false
                                showThumbs -> showThumbs = false
                                showNotes -> showNotes = false
                                else -> {
                                    revealControls()
                                    val fraction = if (size.width > 0) offset.x / size.width.toFloat() else 1f
                                    when (PptPlayerLogic.tapTarget(fraction)) {
                                        PptPlayerLogic.TapTarget.PREV -> prev()
                                        PptPlayerLogic.TapTarget.NEXT -> next()
                                    }
                                }
                            }
                        })
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
                                    PptPlayerLogic.SwipeTarget.NEXT -> next()
                                    PptPlayerLogic.SwipeTarget.PREV -> prev()
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
                val currentBitmap = images[currentUrl]
                // 失败/未到位时保留上一张（或封面），避免白屏（DESIGN §5.4）
                if (currentBitmap == null) {
                    val fallback = lastGood ?: cover
                    if (fallback != null) {
                        Image(
                            painter = remember(fallback) { BitmapPainter(fallback) },
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                AnimatedContent(
                    targetState = pos,
                    transitionSpec = {
                        val sameSlide = initialState.slide == targetState.slide
                        if (sameSlide) {
                            val duration = PptPlayerLogic.effectiveStepDurationMs(lowEnd)
                            fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
                        } else {
                            val fromSlide = manifest.slides.getOrNull(initialState.slide)
                            val kind = PptPlayerLogic.transitionOf(fromSlide?.tr)
                            val duration = PptPlayerLogic.effectivePageDurationMs(fromSlide?.tr, fromSlide?.trd, lowEnd)
                            when {
                                duration <= 0 || kind == PptPlayerLogic.TransitionKind.NONE ->
                                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))

                                kind == PptPlayerLogic.TransitionKind.PUSH ->
                                    (slideInHorizontally(tween(duration)) { it / 8 } + fadeIn(tween(duration))) togetherWith
                                        (slideOutHorizontally(tween(duration)) { -it / 8 } + fadeOut(tween(duration)))

                                else -> fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
                            }
                        }
                    },
                    label = "ppt-step",
                ) { target ->
                    val ref = manifest.slides.getOrNull(target.slide)
                        ?.steps?.getOrNull(target.step)?.img.orEmpty()
                    val url = manifest.resolveImage(ref)
                    val bitmap = images[url]
                    if (bitmap != null) {
                        Image(
                            painter = remember(bitmap) { BitmapPainter(bitmap) },
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f), strokeWidth = 2.dp)
                        }
                    }
                }

                // 图片加载失败提示（保留上一张，不阻塞翻页）
                if (failed[currentUrl] == true) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 28.dp)
                            .clip(CircleShape)
                            .clickable {
                                failed.remove(currentUrl)
                                retryToken += 1
                            },
                        color = Color(0xCC000000),
                        shape = CircleShape,
                    ) {
                        Text(
                            text = "图片加载失败 · 重试",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }

                // 媒体热点（与图片实际渲染矩形同一坐标系）
                currentStep?.media?.forEach { hotspot ->
                    PptHotspotOverlay(
                        hotspot = hotspot,
                        def = manifest.mediaDefOf(hotspot),
                        failed = mediaFailed[hotspot.src] == true,
                        stageW = stageW,
                        stageH = stageH,
                        onPlay = {
                            if (mediaFailed[hotspot.src] == true) {
                                toast(context, "视频不可用")
                            } else {
                                mediaPlayback = PptMediaPlayback(
                                    src = hotspot.src,
                                    url = manifest.resolveImage(hotspot.src),
                                    kind = hotspot.kind,
                                    slideIndex = pos.slide,
                                    durationSec = manifest.mediaDefOf(hotspot)?.durationSec ?: 0.0,
                                )
                                controlsVisible = false
                            }
                        },
                    )
                }
            }
        }

        // ---------------------------------------------------------- 顶部进度条 + 文件名
        if (!blackScreen) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.18f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(PptNav.progress(counts, pos))
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.9f)),
                )
            }
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(200)),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Text(
                    text = detail.title.ifBlank { "演示" },
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 20.dp, top = 18.dp, end = 80.dp)
                        .fillMaxWidth(),
                )
            }
        }

        // 模式切换胶囊（契约 §11.3.1：有原样视频时提供明显切换；当前「分步」）
        if (!blackScreen && onSwitchToVideo != null) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 16.dp)
                    .clip(CircleShape)
                    .clickable {
                        revealControls()
                        onSwitchToVideo(pos.slide)
                    },
            ) {
                Text(
                    text = "分步",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }

        // ---------------------------------------------------------- 控制条
        AnimatedVisibility(
            visible = controlsVisible && !blackScreen && mediaPlayback == null,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PptControlsBar(
                counts = counts,
                pos = pos,
                hasSource = detail.sourceUrl.isNotBlank(),
                onPrev = ::prev,
                onNext = ::next,
                onThumbnails = {
                    showThumbs = true
                    showNotes = false
                    revealControls()
                },
                onNotes = {
                    showNotes = true
                    showThumbs = false
                    revealControls()
                },
                onBlackScreen = { blackScreen = true },
                onDownload = {
                    if (detail.sourceUrl.isBlank()) {
                        toast(context, "该演示未保留原件")
                    } else {
                        downloadToSystemDownloads(
                            context = context,
                            url = detail.sourceUrl,
                            fileName = detail.sourceName.ifBlank { "${detail.title.ifBlank { "演示" }}.pptx" },
                        )
                    }
                },
                onRestart = {
                    position = PptNav.first()
                    revealControls()
                },
                onExit = {
                    exitConfirm = true
                },
            )
        }

        // ---------------------------------------------------------- 缩略图抽屉
        if (showThumbs) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { showThumbs = false },
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .pointerInput(Unit) { detectTapGestures { } },
                    color = Color(0xEB121414),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .width(36.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.35f)),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "缩略图 · ${PptNav.pageLabel(counts, pos.slide)}",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(manifest.slides) { index, slide ->
                                val thumbUrl = remember(manifest, index) { manifest.thumbOf(slide) }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable {
                                            position = PptNav.jumpToSlide(counts, index)
                                            showThumbs = false
                                            revealControls()
                                        },
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(128.dp)
                                            .heightIn(min = 72.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.White.copy(alpha = 0.08f)),
                                    ) {
                                        PptAsyncImage(
                                            url = thumbUrl,
                                            loader = imageLoader,
                                            modifier = Modifier.fillMaxSize(),
                                            targetWidth = PptImageLoader.THUMB_TARGET_WIDTH,
                                        )
                                        if (index == pos.slide) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color.White.copy(alpha = 0.08f)),
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${index + 1}",
                                        color = if (index == pos.slide) Color.White else Color.White.copy(alpha = 0.55f),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 3.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------- 备注抽屉
        if (showNotes) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { showNotes = false },
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .pointerInput(Unit) { detectTapGestures { } },
                    color = Color(0xEB121414),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                        Text(
                            text = "备注 · 第 ${pos.slide + 1} 页",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        val notes = currentSlide?.notes.orEmpty()
                        if (notes.isBlank()) {
                            Text(
                                text = "本页无备注",
                                color = Color.White.copy(alpha = 0.45f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            Text(
                                text = notes,
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 26.sp,
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------- 媒体层（视频/音频）
        mediaPlayback?.let { playback ->
            PptMediaLayer(
                playback = playback,
                onClose = { mediaPlayback = null },
                onFailed = {
                    mediaFailed[playback.src] = true
                    mediaPlayback = null
                    toast(context, "视频不可用")
                },
            )
        }

        // ---------------------------------------------------------- 黑屏（任意触摸/按键恢复，不显示 UI）
        if (blackScreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { blackScreen = false },
            )
        }

        // ---------------------------------------------------------- 退出确认
        if (exitConfirm) {
            AlertDialog(
                onDismissRequest = { exitConfirm = false },
                title = { Text("退出演示？") },
                text = { Text(PptNav.spokenLabel(counts, pos)) },
                confirmButton = {
                    TextButton(onClick = {
                        exitConfirm = false
                        persistPosition()
                        onClose()
                    }) { Text("退出") }
                },
                dismissButton = {
                    TextButton(onClick = { exitConfirm = false }) { Text("继续") }
                },
            )
        }
    }
}

// =============================================================================
// 控制条
// =============================================================================

@Composable
private fun PptControlsBar(
    counts: List<Int>,
    pos: PptNav.PptPosition,
    hasSource: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onThumbnails: () -> Unit,
    onNotes: () -> Unit,
    onBlackScreen: () -> Unit,
    onDownload: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit,
) {
    var moreOpen by remember { mutableStateOf(false) }
    val steps = PptNav.stepsOfSlide(counts, pos.slide)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xCC121414),
        modifier = Modifier.padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(onClick = onPrev, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Rounded.ChevronLeft,
                    contentDescription = "上一页",
                    tint = Color.White.copy(alpha = 0.9f),
                )
            }
            Text(
                text = "${pos.slide + 1} / ${counts.size}",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            if (steps >= 2) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 4.dp),
                ) {
                    repeat(steps) { index ->
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index <= pos.step) Color.White
                                    else Color.White.copy(alpha = 0.35f),
                                ),
                        )
                    }
                }
            }
            IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "下一页",
                    tint = Color.White.copy(alpha = 0.9f),
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(Color.White.copy(alpha = 0.12f)),
            )
            IconButton(onClick = onThumbnails, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Rounded.GridView,
                    contentDescription = "缩略图",
                    tint = Color.White.copy(alpha = 0.9f),
                )
            }
            IconButton(onClick = onNotes, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Notes,
                    contentDescription = "备注",
                    tint = Color.White.copy(alpha = 0.9f),
                )
            }
            IconButton(onClick = onBlackScreen, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Rounded.VisibilityOff,
                    contentDescription = "黑屏",
                    tint = Color.White.copy(alpha = 0.9f),
                )
            }
            Box {
                IconButton(onClick = { moreOpen = true }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "更多",
                        tint = Color.White.copy(alpha = 0.9f),
                    )
                }
                DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("从头播放") },
                        onClick = {
                            moreOpen = false
                            onRestart()
                        },
                    )
                    if (hasSource) {
                        DropdownMenuItem(
                            text = { Text("下载原件") },
                            onClick = {
                                moreOpen = false
                                onDownload()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("退出演示") },
                        onClick = {
                            moreOpen = false
                            onExit()
                        },
                    )
                }
            }
        }
    }
}

// =============================================================================
// 热点 / 媒体层 / 加载态
// =============================================================================

/** 视频/音频热点（点击才播；失败灰化；角标显示时长）。 */
@Composable
private fun PptHotspotOverlay(
    hotspot: PptHotspot,
    def: PptMediaDef?,
    failed: Boolean,
    stageW: Dp,
    stageH: Dp,
    onPlay: () -> Unit,
) {
    val rect = PptPlayerLogic.hotspotRect(hotspot.x, hotspot.y, hotspot.w, hotspot.h)
    val badge = if (def != null && def.durationSec > 0) {
        (if (def.kind == PptMediaKind.VIDEO) "视频 " else "音频 ") +
            PptPlayerLogic.formatMediaDuration(def.durationSec)
    } else {
        ""
    }
    Box(
        modifier = Modifier
            .offset(x = stageW * rect.x, y = stageH * rect.y)
            .size(stageW * rect.w, stageH * rect.h)
            .clickable { onPlay() },
        contentAlignment = Alignment.Center,
    ) {
        val reduceMotion = rememberReduceMotion()
        val pulse = if (failed || reduceMotion) {
            1f
        } else {
            val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "ppt-hotspot")
            val value by transition.animateFloat(
                initialValue = 1f,
                targetValue = 1.07f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                ),
                label = "ppt-hotspot-pulse",
            )
            value
        }
        Surface(
            shape = CircleShape,
            color = when {
                failed -> Color.White.copy(alpha = 0.22f)
                hotspot.kind == PptMediaKind.AUDIO -> Color.Black.copy(alpha = 0.42f)
                else -> Color.Black.copy(alpha = 0.45f)
            },
            modifier = Modifier
                .size(if (hotspot.kind == PptMediaKind.AUDIO) 48.dp else 64.dp)
                .graphicsLayer(scaleX = pulse, scaleY = pulse),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (failed) {
                        Icons.Rounded.VisibilityOff
                    } else if (hotspot.kind == PptMediaKind.AUDIO) {
                        Icons.Rounded.MusicNote
                    } else {
                        Icons.Rounded.PlayArrow
                    },
                    contentDescription = if (hotspot.kind == PptMediaKind.VIDEO) "播放视频" else "播放音频",
                    tint = Color.White.copy(alpha = if (failed) 0.5f else 0.92f),
                    modifier = Modifier.size(if (hotspot.kind == PptMediaKind.AUDIO) 26.dp else 34.dp),
                )
            }
        }
        if (badge.isNotEmpty()) {
            Text(
                text = if (failed) "视频不可用" else badge,
                color = Color.White.copy(alpha = if (failed) 0.5f else 0.85f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

/** 媒体播放态：媒体层独立全屏，不随 step 卸载（只换页/退出销毁）。 */
private data class PptMediaPlayback(
    val src: String,
    val url: String,
    val kind: PptMediaKind,
    val slideIndex: Int,
    val durationSec: Double,
)

@Composable
private fun PptMediaLayer(
    playback: PptMediaPlayback,
    onClose: () -> Unit,
    onFailed: () -> Unit,
) {
    val context = LocalContext.current
    var videoView by remember(playback.src) { mutableStateOf<VideoView?>(null) }
    var failed by remember(playback.src) { mutableStateOf(false) }

    DisposableEffect(playback.src) {
        onDispose {
            runCatching { videoView?.stopPlayback() }
            videoView = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (!failed && playback.url.isNotBlank()) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setMediaController(MediaController(ctx))
                        setVideoURI(Uri.parse(playback.url))
                        setOnPreparedListener { player ->
                            player.isLooping = false
                            start()
                        }
                        setOnCompletionListener { onClose() }
                        setOnErrorListener { _, _, _ ->
                            failed = true
                            onFailed()
                            true
                        }
                        videoView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (playback.kind == PptMediaKind.AUDIO) {
                Text(
                    text = "音频播放中",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "视频不可用", color = Color.White.copy(alpha = 0.8f))
                TextButton(onClick = onClose) { Text("关闭", color = Color.White) }
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "关闭视频",
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun PptPlayerLoading(
    loading: Boolean,
    error: String?,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (loading) {
            CircularProgressIndicator(color = Color.White.copy(alpha = 0.75f), strokeWidth = 2.dp)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = error ?: "演示加载失败",
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Spacer(Modifier.height(10.dp))
                Row {
                    TextButton(onClick = onRetry) { Text("重试", color = Color.White) }
                    TextButton(onClick = onClose) { Text("关闭", color = Color.White) }
                }
            }
        }
    }
}

// =============================================================================
// 工具
// =============================================================================

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}


private fun toast(context: Context, message: String) {
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
}
