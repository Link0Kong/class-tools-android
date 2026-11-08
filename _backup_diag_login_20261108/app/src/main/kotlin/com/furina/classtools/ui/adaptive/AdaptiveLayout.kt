package com.furina.classtools.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.theme.CtTokens

/**
 * v2 自适应框架（手机 / 平板 / 折叠屏 / 横屏）。
 *
 * 断点（与 design/tokens.json + M3 window size class 对齐）：
 * - compact：< 600dp（手机竖屏）→ 单列、内容限宽 840dp（实际不生效）、2 列工具网格；
 * - medium：600–839dp（小平板/折叠屏展开/手机横屏）→ 单列 + 限宽 840dp、3 列工具网格；
 * - expanded：≥ 840dp（平板横屏/大屏）→ **双栏**（左侧主内容 + 右侧辅助）、4 列网格、内容限宽 1200dp；
 * - large（≥1200dp）→ 内容限宽 1200dp（与 expanded 相同，只是再收窄视觉）。
 *
 * 用法：
 * ```kotlin
 * val spec = rememberCtAdaptiveSpec()
 * CtAdaptiveContent(spec) {
 *     HomeScreenContent(spec)   // spec.twoPane 时自己切双栏
 * }
 * ```
 * 页面自身**不要**再读 LocalConfiguration.screenWidthDp（统一走 spec，便于测试与 Lead 接线）。
 */
enum class CtWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

/** 一个窗口宽度对应的完整自适应规格（纯数据，可单测）。 */
data class CtAdaptiveSpec(
    val widthDp: Int,
    val widthClass: CtWidthClass,
    /** 内容最大宽度（平板居中限宽，避免拉满）。 */
    val contentMaxWidth: Dp,
    /** 页面左右内边距。 */
    val horizontalPadding: Dp,
    /** 工具网格列数（2/3/4）。 */
    val gridColumns: Int,
    /** 是否双栏（>=840dp）。 */
    val twoPane: Boolean,
    /** 双栏时主栏占比（0..1）。 */
    val mainPaneWeight: Float,
) {
    val isExpanded: Boolean get() = widthClass == CtWidthClass.EXPANDED
    val isCompact: Boolean get() = widthClass == CtWidthClass.COMPACT
}

/** 断点/列数/限宽计算（纯函数；不依赖 Android，可单测）。 */
object CtAdaptive {

    const val MEDIUM_MIN_WIDTH_DP: Int = 600
    const val EXPANDED_MIN_WIDTH_DP: Int = 840
    const val LARGE_MIN_WIDTH_DP: Int = 1200

    fun widthClassOf(widthDp: Int): CtWidthClass = when {
        widthDp >= EXPANDED_MIN_WIDTH_DP -> CtWidthClass.EXPANDED
        widthDp >= MEDIUM_MIN_WIDTH_DP -> CtWidthClass.MEDIUM
        else -> CtWidthClass.COMPACT
    }

    fun gridColumns(widthDp: Int): Int = when (widthClassOf(widthDp)) {
        CtWidthClass.COMPACT -> 2
        CtWidthClass.MEDIUM -> 3
        CtWidthClass.EXPANDED -> 4
    }

    fun contentMaxWidthDp(widthDp: Int): Int = when (widthClassOf(widthDp)) {
        CtWidthClass.COMPACT, CtWidthClass.MEDIUM -> CtTokens.Breakpoints.contentMaxWidthCompact.value.toInt()
        CtWidthClass.EXPANDED -> CtTokens.Breakpoints.contentMaxWidthExpanded.value.toInt()
    }

    fun horizontalPaddingDp(widthDp: Int): Int = when (widthClassOf(widthDp)) {
        CtWidthClass.COMPACT -> CtTokens.Spacing.pageHorizontalCompact.value.toInt()
        CtWidthClass.MEDIUM -> CtTokens.Spacing.pageHorizontalMedium.value.toInt()
        CtWidthClass.EXPANDED -> CtTokens.Spacing.pageHorizontalExpanded.value.toInt()
    }

    fun twoPane(widthDp: Int): Boolean = widthDp >= EXPANDED_MIN_WIDTH_DP

    fun specFor(widthDp: Int): CtAdaptiveSpec = CtAdaptiveSpec(
        widthDp = widthDp,
        widthClass = widthClassOf(widthDp),
        contentMaxWidth = contentMaxWidthDp(widthDp).dp,
        horizontalPadding = horizontalPaddingDp(widthDp).dp,
        gridColumns = gridColumns(widthDp),
        twoPane = twoPane(widthDp),
        mainPaneWeight = if (widthDp >= LARGE_MIN_WIDTH_DP) 0.58f else 0.55f,
    )

    /** 双栏布局是否可用（工作台：今日课程/待办左右分栏）。 */
    fun supportsWorkbenchTwoPane(spec: CtAdaptiveSpec): Boolean = spec.twoPane

    /** 限宽后内容宽度（dp）：`min(窗口宽 - 2*内边距, 内容最大宽度)`。 */
    fun effectiveContentWidthDp(widthDp: Int): Int =
        (widthDp - horizontalPaddingDp(widthDp) * 2).coerceAtMost(contentMaxWidthDp(widthDp)).coerceAtLeast(0)
}

/** 当前窗口自适应规格（CompositionLocal 不需要：页面一处读取即可）。 */
@Composable
fun rememberCtAdaptiveSpec(): CtAdaptiveSpec {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return remember(widthDp) { CtAdaptive.specFor(widthDp) }
}

/**
 * 居中限宽内容：
 * - 内容宽度 = min(窗口宽 - 2*padding, spec.contentMaxWidth)；
 * - 上下可滚动（`scrollable = true` 时），避免平板内容过长；
 * - 默认顶部对齐（页面自己放 TopBar）。
 */
@Composable
fun CtAdaptiveContent(
    spec: CtAdaptiveSpec = rememberCtAdaptiveSpec(),
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollModifier = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = spec.contentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = spec.horizontalPadding)
                .then(scrollModifier),
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}

/** 双栏布局 helper：主栏 + 辅栏（>=840dp 才启用，否则由调用方单列）。 */
@Composable
fun CtTwoPaneRow(
    spec: CtAdaptiveSpec,
    modifier: Modifier = Modifier,
    gap: Dp = CtTokens.Spacing.cardGapExpanded,
    main: @Composable (Modifier) -> Unit,
    side: @Composable (Modifier) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(gap),
    ) {
        main(Modifier.weight(spec.mainPaneWeight))
        side(Modifier.weight(1f - spec.mainPaneWeight))
    }
}
