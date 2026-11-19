package com.furina.classtools.ui.components.dock

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import com.furina.classtools.ui.theme.CtMotion
import com.furina.classtools.ui.theme.HarmonyMotion
import kotlin.math.abs

/**
 * **Tab 滑动切换容器**（task-20 / v2.0.0）。
 *
 * 功能：
 * - 3 个顶层页（工作台 / 工具 / 我的）横向滑动切换（HorizontalPager）；
 * - 页面转场与底栏联动：页面位移 8%、淡出到 0.72、缩到 0.98（跟手插值）；
 * - 底栏药丸连续位置通过 `pagerState.currentPage + currentPageOffsetFraction` 跟手
 *   （见 [CtFloatingDock.pagerOffsetFraction]）；
 * - 顶层页导航用 pager，**工具页仍是全屏 push**（MainNavHost 的 NavHost 内嵌或不嵌套均可）。
 *
 * 接线（Lead 在 MainNavHost 的工作台/工具/我的三个顶层目的地）：
 * ```kotlin
 * val pagerState = rememberCtTabPagerState(pageCount = 3)
 * Scaffold(
 *     bottomBar = {
 *         CtFloatingDock(
 *             destinations = docks,
 *             selectedIndex = pagerState.currentPage,
 *             pagerOffsetFraction = pagerState.currentPageOffsetFraction,
 *             onSelect = { i -> scope.launch { pagerState.animateScrollToPage(i) } },
 *         )
 *     },
 * ) { padding ->
 *     CtTabPager(pagerState = pagerState, modifier = Modifier.padding(padding)) { page ->
 *         when (page) { 0 -> HomeScreen(...); 1 -> AllToolsScreen(...); 2 -> ProfileScreen(...) }
 *     }
 * }
 * ```
 *
 * 如果 Lead 继续使用 NavHost（推荐，保留完整路由栈）：可只把三个顶层 composable 放进
 * 这里的 pager 内容里，工具页仍在 NavHost 的 `Routes.TOOL` push 全屏。
 */
@Composable
fun rememberCtTabPagerState(
    pageCount: Int,
    initialPage: Int = 0,
): PagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))) {
    pageCount.coerceAtLeast(1)
}

/**
 * Tab 滑动容器：每页按 [CtDockLogic.pageTransform] 做位移/缩放/淡出。
 *
 * @param beyondViewportPageCount 预加载相邻页数量（默认 1，足够跟手）
 * @param pageBackground 页面背景色（避免滑动中缝隙透黑；一般传 `MaterialTheme.colorScheme.background`）
 */
@Composable
fun CtTabPager(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    beyondViewportPageCount: Int = 1,
    pageBackground: Color = Color.Transparent,
    content: @Composable (page: Int) -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        userScrollEnabled = userScrollEnabled,
        beyondViewportPageCount = beyondViewportPageCount,
        key = { page -> page },
    ) { page ->
        var pageWidthPx by remember { mutableIntStateOf(0) }
        val transform = CtDockLogic.pageTransform(
            index = page,
            currentPage = pagerState.currentPage,
            offsetFraction = pagerState.currentPageOffsetFraction,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { pageWidthPx = it.width }
                .graphicsLayer {
                    translationX = transform.translationXFraction * pageWidthPx
                    alpha = transform.alpha
                    scaleX = transform.scale
                    scaleY = transform.scale
                }
                .background(pageBackground),
        ) {
            content(page)
        }
    }
}

/**
 * Tab 与底栏的桥接 helper（纯函数 + 组合期读取，便于 Lead 直接在 Scaffold 里用）：
 *
 * ```kotlin
 * val dockState = rememberCtDockPagerState(pagerState)
 * CtFloatingDock(..., selectedIndex = dockState.selectedIndex, pagerOffsetFraction = dockState.followFraction)
 * ```
 */
@Composable
fun rememberCtDockPagerState(pagerState: PagerState): CtDockPagerState {
    val count = pagerState.pageCount
    return remember(pagerState.currentPage, pagerState.currentPageOffsetFraction, count) {
        CtDockPagerState(
            selectedIndex = pagerState.currentPage.coerceIn(0, (count - 1).coerceAtLeast(0)),
            followFraction = pagerState.currentPageOffsetFraction,
            pageCount = count,
        )
    }
}

/** 底栏联动快照。 */
data class CtDockPagerState(
    val selectedIndex: Int,
    val followFraction: Float,
    val pageCount: Int,
) {
    /** 跟手时的连续位置（0..pageCount-1），可直接映射到药丸 x。 */
    val continuousPosition: Float
        get() = CtDockLogic.continuousPosition(selectedIndex, followFraction, pageCount)

    /** 松手后会落到的页（用于提前预取数据）。 */
    val settlingPage: Int
        get() = CtDockLogic.settlePage(selectedIndex, followFraction, pageCount)
}

/**
 * **工具页 push 转场规格**（v2.0.0）：进入 slide 8% + fade + scale 0.98；返回反向；300–400ms spring。
 *
 * Lead 在 MainNavHost 的 `composable` 上使用：
 * ```kotlin
 * composable(
 *     route = Routes.TOOL,
 *     enterTransition = { CtPageTransitions.pushEnter() },
 *     exitTransition = { CtPageTransitions.pushExit() },
 *     popEnterTransition = { CtPageTransitions.popEnter() },
 *     popExitTransition = { CtPageTransitions.popExit() },
 * ) { ... }
 * ```
 * 规格细节见 `docs/ui/V2_MOTION_SPEC.md`。
 */
object CtPageTransitions {

    /** 进入页面从右侧滑入 10%（参考 HFUT 的推入/收回连续性，比旧版 8% 更有方向感）。 */
    const val PUSH_SLIDE_FRACTION: Float = 0.10f

    /** 返回时当前页向右滑出 12%，底层页从左侧轻推 7%。 */
    const val POP_SLIDE_FRACTION: Float = 0.12f
    const val POP_UNDER_SLIDE_FRACTION: Float = 0.07f
    const val PUSH_FROM_SCALE: Float = 0.985f
    const val PUSH_UNDER_SCALE: Float = 0.972f
    const val POP_EXIT_SCALE: Float = 0.965f

    /** 进入：轻 overshoot（d=0.82, k=360）。 */
    val enterSpatial: SpringSpec<Float> = spring(dampingRatio = 0.82f, stiffness = 360f)

    /** 退出：临界阻尼、稍快，让收起动作干净不晃（d=1, k=420）。 */
    val exitSpatial: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 420f)

    private fun <T> enterSpec(): SpringSpec<T> = spring(dampingRatio = 0.82f, stiffness = 360f)
    private fun <T> exitSpec(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 420f)

    /** 进入：右侧滑入 10% + 淡入 + 0.985→1 缩放。 */
    fun pushEnter(): EnterTransition =
        slideInHorizontally(animationSpec = enterSpec<IntOffset>()) { full -> (full * PUSH_SLIDE_FRACTION).toInt() } +
            fadeIn(animationSpec = CtMotion.fadeIn) +
            scaleIn(animationSpec = enterSpec<Float>(), initialScale = PUSH_FROM_SCALE)

    /** 被覆盖页退出：轻微左移 6% + 淡出 + 缩到 0.972（层叠后退感）。 */
    fun pushExit(): ExitTransition =
        slideOutHorizontally(animationSpec = exitSpec<IntOffset>()) { full -> -(full * PUSH_SLIDE_FRACTION * 0.6f).toInt() } +
            fadeOut(animationSpec = CtMotion.fadeOut) +
            scaleOut(animationSpec = exitSpec<Float>(), targetScale = PUSH_UNDER_SCALE)

    /** 返回时恢复页：从左侧轻推 7% + 淡入 + 0.985→1（先压暗再浮现）。 */
    fun popEnter(): EnterTransition =
        slideInHorizontally(animationSpec = enterSpec<IntOffset>()) { full -> -(full * POP_UNDER_SLIDE_FRACTION).toInt() } +
            fadeIn(animationSpec = CtMotion.fadeIn) +
            scaleIn(animationSpec = enterSpec<Float>(), initialScale = PUSH_FROM_SCALE)

    /** 返回：当前页右滑 12% + 淡出 + 缩到 0.965，配合工具卡收回覆盖层形成「回到卡片」的连续感。 */
    fun popExit(): ExitTransition =
        slideOutHorizontally(animationSpec = exitSpec<IntOffset>()) { full -> (full * POP_SLIDE_FRACTION).toInt() } +
            fadeOut(animationSpec = CtMotion.fadeOut) +
            scaleOut(animationSpec = exitSpec<Float>(), targetScale = POP_EXIT_SCALE)

    // -------------------------------------------------------------------------
    // 鸿蒙转场（2026-11-19，对照 harmony `ui/theme/Motion.ets`）
    //
    // 鸿蒙 `Motion.pageEnter()`  = 透明度 + 右移 28vp + 0.985 缩放；
    // 鸿蒙 `Motion.pageExit()`   = 透明度 + 右移 24vp + 0.99 缩放；
    // 时长 `Motion.SLOW = 380ms`，标准缓动 cubic-bezier(.4,0,.2,1)。
    //
    // 与 [pushEnter]/[pushExit] 的差别：位移是**绝对值**（28/24vp，不随屏宽放大），
    // 且被覆盖页与进入页**同向右移**（鸿蒙 Navigation Stack 的层叠语言），不再左移。
    // 转场函数在 NavHost 的 transitionSpec（非 @Composable）里调用，读不到 LocalDensity，
    // 因此按 360vp 手机折算为屏宽比例（28/360≈7.8%，24/360≈6.7%；平板略小于 28vp，观感更收敛）。
    // -------------------------------------------------------------------------

    /** 鸿蒙页面进入位移（屏宽比例，≈28vp @360vp）。 */
    const val HARMONY_ENTER_FRACTION: Float = 0.078f

    /** 鸿蒙页面退出位移（屏宽比例，≈24vp @360vp）。 */
    const val HARMONY_EXIT_FRACTION: Float = 0.067f

    /** 鸿蒙页面转场时长（Motion.SLOW）。 */
    const val HARMONY_PAGE_MS: Int = 380

    /** 鸿蒙淡入淡出时长（Motion.NORMAL 近似；与位移同时长会让淡入过慢）。 */
    const val HARMONY_FADE_MS: Int = 260

    private fun harmonyPageSpec(): FiniteAnimationSpec<Float> =
        tween(durationMillis = HARMONY_PAGE_MS, easing = HarmonyMotion.ease)

    private fun harmonyFadeSpec(): FiniteAnimationSpec<Float> =
        tween(durationMillis = HARMONY_FADE_MS, easing = HarmonyMotion.ease)

    private fun harmonyPageSpecInt(): FiniteAnimationSpec<IntOffset> =
        tween(durationMillis = HARMONY_PAGE_MS, easing = HarmonyMotion.ease)

    /** 鸿蒙「进入」：右移 28vp → 0 + 淡入 + 0.985 → 1。 */
    fun harmonyPushEnter(): EnterTransition =
        slideInHorizontally(animationSpec = harmonyPageSpecInt()) { full ->
            (full * HARMONY_ENTER_FRACTION).toInt()
        } +
            fadeIn(animationSpec = harmonyFadeSpec()) +
            scaleIn(animationSpec = harmonyPageSpec(), initialScale = HarmonyMotion.PageEnterScale)

    /** 鸿蒙「被覆盖页退出」：右移 0 → 24vp + 淡出 + 1 → 0.99。 */
    fun harmonyPushExit(): ExitTransition =
        slideOutHorizontally(animationSpec = harmonyPageSpecInt()) { full ->
            (full * HARMONY_EXIT_FRACTION).toInt()
        } +
            fadeOut(animationSpec = harmonyFadeSpec()) +
            scaleOut(animationSpec = harmonyPageSpec(), targetScale = HarmonyMotion.PageExitScale)

    /** 鸿蒙「返回时恢复页」：不做位移（鸿蒙只对退出页做位移），从 0.99 淡入到 1。 */
    fun harmonyPopEnter(): EnterTransition =
        fadeIn(animationSpec = harmonyFadeSpec()) +
            scaleIn(animationSpec = harmonyPageSpec(), initialScale = HarmonyMotion.PageExitScale)

    /** 鸿蒙「返回时当前页退出」：右移 24vp + 淡出 + 缩到 0.99。 */
    fun harmonyPopExit(): ExitTransition =
        slideOutHorizontally(animationSpec = harmonyPageSpecInt()) { full ->
            (full * HARMONY_EXIT_FRACTION).toInt()
        } +
            fadeOut(animationSpec = harmonyFadeSpec()) +
            scaleOut(animationSpec = harmonyPageSpec(), targetScale = HarmonyMotion.PageExitScale)
    /** 供预览/测试：给定滑动进度 (0..1) 的页面参数（与 pager 插值一致）。 */
    fun progressTransform(progress: Float): CtDockLogic.PageTransform =
        CtDockLogic.pageTransform(
            index = 0,
            currentPage = 0,
            offsetFraction = progress.coerceIn(0f, 1f),
        )

    @Suppress("unused")
    private fun unusedAbs(value: Float): Float = abs(value)
}

