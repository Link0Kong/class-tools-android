package com.furina.classtools.ui.components

import android.view.View
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest

private class FeedbackHost {
    val snackbar = SnackbarHostState()
    val windows = mutableStateListOf<View>()
}

/**
 * 当前页顶栏在窗口中的位置（px）。提示层据此优先落在顶栏上方的安全带里；
 * 放不下时再落到顶栏下方，避免压住标题、返回和右侧动作。
 */
internal class FeedbackTopBar {
    var topPx by mutableIntStateOf(0)
        private set
    var bottomPx by mutableIntStateOf(0)
        private set
    private var owner: Any? = null

    fun update(owner: Any, top: Int, bottom: Int) {
        if (top <= 0 || bottom <= top) return
        this.owner = owner
        topPx = top
        bottomPx = bottom
    }

    fun clear(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        topPx = 0
        bottomPx = 0
    }
}

private val LocalFeedbackHost = staticCompositionLocalOf<FeedbackHost?> { null }
private val LocalFeedbackTopBar = staticCompositionLocalOf<FeedbackTopBar?> { null }

/**
 * 顶栏注册自己的窗口位置。顶栏包 **subtitle** 时给整块 header 注册（外层 Column 最后布局，
 * 写入的 bottom 覆盖内层），提示才能整体避开标题与副标题。
 */
@Composable
internal fun Modifier.ctFeedbackTopBarAnchor(): Modifier {
    val topBar = LocalFeedbackTopBar.current ?: return this
    val owner = remember { Any() }
    DisposableEffect(topBar, owner) { onDispose { topBar.clear(owner) } }
    return onGloballyPositioned { coordinates ->
        if (!coordinates.isAttached) return@onGloballyPositioned
        val top = coordinates.positionInWindow().y.roundToInt()
        topBar.update(owner, top, top + coordinates.size.height)
    }
}

/**
 * 提示条纵坐标：优先「顶栏上方的空闲安全带」；高度不够时退到顶栏下方；
 * 页面没有顶栏时仍按安全区顶部摆放（保持原有行为）。
 */
internal fun feedbackTopOffset(
    safeTop: Int,
    topBarTop: Int,
    topBarBottom: Int,
    popupHeight: Int,
    gap: Int,
): Int {
    if (topBarTop > 0 && topBarBottom > topBarTop) {
        val above = topBarTop - gap - popupHeight
        if (above >= 0) return above
        return topBarBottom + gap
    }
    return safeTop + gap
}

private class FeedbackPosition(
    private val safeTop: Int,
    private val left: Int,
    private val right: Int,
    private val topBarTop: Int,
    private val topBarBottom: Int,
    private val gap: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (if (layoutDirection == LayoutDirection.Ltr) left else right) +
            ((windowSize.width - left - right - popupContentSize.width) / 2).coerceAtLeast(0)
        val y = feedbackTopOffset(
            safeTop = safeTop,
            topBarTop = topBarTop,
            topBarBottom = topBarBottom,
            popupHeight = popupContentSize.height,
            gap = gap,
        )
        return IntOffset(x, y)
    }
}

/** 撤销等带动作的提示和普通反馈共用同一个队列。 */
internal val ctFeedbackState: SnackbarHostState
    @Composable get() = LocalFeedbackHost.current?.snackbar ?: remember { SnackbarHostState() }

@Composable
internal fun CtFeedbackRoot(content: @Composable () -> Unit) {
    val host = remember { FeedbackHost() }
    val topBar = remember { FeedbackTopBar() }
    val owner = LocalLifecycleOwner.current

    LaunchedEffect(owner, host) {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            CtToastBus.messages.collectLatest { message ->
                host.snackbar.showSnackbar(
                    message,
                    duration = if (message.length > 60) SnackbarDuration.Long else SnackbarDuration.Short,
                )
            }
        }
    }

    CompositionLocalProvider(LocalFeedbackHost provides host, LocalFeedbackTopBar provides topBar) {
        Box(Modifier.fillMaxSize()) {
            CtFeedbackWindow()
            content()
        }
    }
}

/** 根窗口、底部面板和对话框共用前景提示，同一时刻只在最上层窗口显示。 */
@Composable
internal fun CtFeedbackWindow() {
    val host = LocalFeedbackHost.current ?: return
    val topBar = LocalFeedbackTopBar.current
    val view = LocalView.current
    DisposableEffect(host, view) {
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) { host.windows.add(view) }
            override fun onViewDetachedFromWindow(view: View) { host.windows.remove(view) }
        }
        view.addOnAttachStateChangeListener(listener)
        if (view.isAttachedToWindow) listener.onViewAttachedToWindow(view)
        onDispose {
            view.removeOnAttachStateChangeListener(listener)
            host.windows.remove(view)
        }
    }

    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    if (host.windows.lastOrNull() !== view || lifecycle != Lifecycle.State.RESUMED ||
        host.snackbar.currentSnackbarData == null
    ) return

    val safeDrawing = WindowInsets.safeDrawing
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safeTop = safeDrawing.getTop(density)
    val left = safeDrawing.getLeft(density, layoutDirection)
    val right = safeDrawing.getRight(density, layoutDirection)
    val gap = with(density) { 8.dp.roundToPx() }
    val topBarTop = topBar?.topPx ?: 0
    val topBarBottom = topBar?.bottomPx ?: 0
    val position = remember(safeTop, left, right, topBarTop, topBarBottom, gap) {
        FeedbackPosition(safeTop, left, right, topBarTop, topBarBottom, gap)
    }

    Popup(
        popupPositionProvider = position,
        properties = PopupProperties(
            // 在整个应用窗口的安全区内定位，即使宿主只是居中的小对话框。
            flags = FLAG_NOT_FOCUSABLE or FLAG_LAYOUT_IN_SCREEN or FLAG_LAYOUT_NO_LIMITS,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            excludeFromSystemGesture = false,
        ),
    ) {
        SnackbarHost(hostState = host.snackbar) { data ->
            val action = data.visuals.actionLabel
            val colors = MaterialTheme.colorScheme
            Surface(
                modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 480.dp),
                shape = RoundedCornerShape(16.dp),
                color = colors.inverseSurface,
                contentColor = colors.inverseOnSurface,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.heightIn(min = 40.dp)
                        .padding(start = 16.dp, end = if (action == null) 16.dp else 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = data.visuals.message,
                        modifier = Modifier.weight(1f, fill = false).padding(vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (action != null) {
                        TextButton(
                            onClick = data::performAction,
                            colors = ButtonDefaults.textButtonColors(contentColor = colors.inversePrimary),
                        ) { Text(action) }
                    }
                }
            }
        }
    }
}
