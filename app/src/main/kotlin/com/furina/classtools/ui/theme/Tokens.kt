package com.furina.classtools.ui.theme

import androidx.compose.ui.unit.dp

// =============================================================================
// 班级工具站 · Design Tokens（tokens.json 的 Compose 可查表版）
//
// 用途：组件/页面需要「非 Material 主题字段」的数值时（间距、圆角、高度、模糊半径、
// 动效时长、窗口断点），统一从 CtTokens 取，不要在业务代码里散落魔法数字。
// 与 `design/tokens.json` 一一对应；改这里要同步改 spec。
// =============================================================================

/**
 * 旧令牌名 `CtTokens`（间距/圆角/尺寸/阴影/时长/断点的旧查表版）。
 *
 * 2026-11-19 统一视觉语言重构后数值**全部委托给 [CtDesign.kt]**（唯一的令牌源），
 * 保留这个对象只为不破坏既有调用点（`CtTokens.Spacing.cardGapCompact` 等）与 `design/tokens.json`
 * 的历史对齐说明。新代码请直接用 `CtSpace` / `CtRadius` / `CtSize` / `CtElevation` / `CtMotion`。
 */
object CtTokens {

    /** 间距（4dp 基准；= CtSpace）。 */
    object Spacing {
        val xs = CtSpace.xs
        val sm = CtSpace.sm
        val md = CtSpace.md
        val lg = CtSpace.lg
        val xl = CtSpace.xl
        val xxl = CtSpace.xxl
        val xxxl = CtSpace.xxxl

        val pageHorizontalCompact = CtSpace.pagePhone
        val pageHorizontalMedium = CtSpace.pageFold
        val pageHorizontalExpanded = CtSpace.pageTablet

        val sectionGapCompact = CtSpace.sectionGap
        val sectionGapExpanded = CtSpace.groupGap

        val cardGapCompact = CtSpace.cardGap
        val cardGapExpanded = CtSpace.lg

        val cardPaddingCompact = CtSpace.cardPadding
        val cardPaddingExpanded = CtSpace.xl
        val elementGapLarge = CtSpace.lg
        val elementGap = CtSpace.sm
        val textGapVertical = CtSpace.xxs
        val textGapHorizontal = CtSpace.sm

        val pageHorizontal: androidx.compose.ui.unit.Dp get() = pageHorizontalCompact
        val cardGap: androidx.compose.ui.unit.Dp get() = cardGapCompact
        val cardPadding: androidx.compose.ui.unit.Dp get() = cardPaddingCompact
    }

    /** 圆角（= CtRadius）。 */
    object Radius {
        val extraSmall = 4.dp
        val small = 8.dp
        val medium = 12.dp
        val large = CtRadius.card
        val largeIncreased = CtRadius.card
        val extraLarge = CtRadius.hero
        val extraLargeIncreased = CtRadius.hero
        val extraExtraLarge = 48.dp

        val card = CtRadius.card
        val cardLarge = CtRadius.card
        val hero = CtRadius.hero
        val cardPressed = CtRadius.cardPressed
        val input = CtRadius.input
        val sheetTop = CtRadius.sheetTop
        val dialog = CtRadius.dialog
    }

    /** 组件高度 / 尺寸（= CtSize）。 */
    object Size {
        val minTouchTarget = CtSize.minTouch
        val buttonHeightSmall = CtSize.buttonHeightCompact
        val buttonHeight = CtSize.buttonHeight
        val buttonHeightLarge = 96.dp

        val topAppBarSmall = CtSize.topBarHeight
        val topAppBarMedium = 112.dp
        val topAppBarLarge = 152.dp

        val navigationBarHeight = 64.dp
        val navigationBarTallHeight = 80.dp
        val navigationRailWidth = 80.dp
        val wideNavigationRailWidth = 256.dp

        val floatingToolbarHeight = 64.dp
        val dockedToolbarHeight = 64.dp

        val loadingIndicator = 48.dp
        val loadingIndicatorSmall = CtSize.iconLarge

        val searchBarHeight = CtSize.buttonHeight

        val listItemOneLine = CtSize.rowMinHeight
        val listItemTwoLine = 64.dp
        val listItemThreeLine = 88.dp

        val cardMinHeight = CtSize.tileMinHeight
        val cardPadding = CtSpace.cardPadding
        val tilePadding = CtSpace.tilePadding
        val tileGlyph = CtSize.glyphBox
        val pageHeaderHeight = CtSize.topBarHeight
        val harmonyButtonHeight = CtSize.buttonHeightCompact

        val chipHeight = 32.dp
        val fabSize = 56.dp
        val fabSmallSize = 40.dp
        val fabLargeSize = 96.dp
        val iconSmall = CtSize.iconSmall
        val iconMedium = CtSize.icon
        val iconLarge = CtSize.iconLarge
        val iconXLarge = CtSize.emptyIcon

        const val bottomSheetMaxHeightFraction = 0.9f
    }

    /** 阴影（= CtElevation）。 */
    object Elevation {
        val level0 = CtElevation.none
        val level1 = CtElevation.card
        val level2 = CtElevation.card
        val level3 = CtElevation.floating
        val level4 = CtElevation.floating
        val level5 = CtElevation.dragged

        val card = CtElevation.card
        val floating = CtElevation.floating
        val sheet = CtElevation.sheet
        val dialog = CtElevation.sheet
    }

    /** 动效时长（= CtMotionTokens；spring 见 [CtMotion]）。 */
    object Duration {
        const val short1 = 50
        const val short2 = 100
        const val short3 = CtMotionTokens.fastMs
        const val short4 = CtMotionTokens.shortMs
        const val medium1 = 250
        const val medium2 = CtMotionTokens.standardMs
        const val medium3 = 350
        const val medium4 = CtMotionTokens.slowMs
        const val long1 = 450
        const val long2 = 500
        const val long3 = 550
        const val long4 = 600
        const val extraLong1 = 700
        const val extraLong2 = 800
        const val extraLong3 = 900
        const val extraLong4 = 1000
    }

    /** 无障碍参数（WCAG）。 */
    object Accessibility {
        const val minTextContrast = 4.5f
        const val largeTextContrast = 3.0f
        const val graphicsContrast = 3.0f
        const val minFontScale = 0.85f
        const val maxFontScale = 2.0f
    }

    /** 窗口尺寸类断点（= CtSize.breakpoint*）。 */
    object Breakpoints {
        val compactMaxWidth = CtSize.breakpointMedium - 1.dp
        val mediumMaxWidth = CtSize.breakpointExpanded - 1.dp
        val expandedMinWidth = CtSize.breakpointExpanded
        val largeMinWidth = 1200.dp
        val extraLargeMinWidth = 1600.dp

        val contentMaxWidthCompact = 840.dp
        val contentMaxWidthExpanded = 1200.dp
        val contentMaxWidthWide = 1520.dp
    }
}