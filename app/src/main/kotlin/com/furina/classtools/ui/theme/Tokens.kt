package com.furina.classtools.ui.theme

import androidx.compose.ui.unit.dp

// =============================================================================
// 班级工具站 · Design Tokens（tokens.json 的 Compose 可查表版）
//
// 用途：组件/页面需要「非 Material 主题字段」的数值时（间距、圆角、高度、模糊半径、
// 动效时长、窗口断点），统一从 CtTokens 取，不要在业务代码里散落魔法数字。
// 与 `design/tokens.json` 一一对应；改这里要同步改 spec。
// =============================================================================

object CtTokens {

    /** 间距（4dp 基准）。 */
    object Spacing {
        val xs = 4.dp
        val sm = 8.dp
        val md = 12.dp
        val lg = 16.dp
        val xl = 24.dp
        val xxl = 32.dp
        val xxxl = 48.dp

        /**
         * 页面左右边距。
         *
         * 注意：`CtAdaptive` 的自适应框架单测以 16/24/32 为契约（AdaptiveLayoutTest），
         * 这里保持原值；鸿蒙重做后的页面显式使用 `HarmonyTokens.PagePadding = 16dp`
         * （2026-11-19 按鸿蒙 `Tokens.PAGE_PADDING` 由 20 修正）。
         */
        val pageHorizontalCompact = 16.dp
        val pageHorizontalMedium = 24.dp
        val pageHorizontalExpanded = 32.dp

        /** 区块间距（鸿蒙间隔规范：元素间大间隔 16vp；平板取 24）。 */
        val sectionGapCompact = 16.dp
        val sectionGapExpanded = 24.dp

        /** 卡片间距（鸿蒙 GAP=12）。 */
        val cardGapCompact = 12.dp
        val cardGapExpanded = 16.dp

        /** 卡片内边距（鸿蒙间隔规范：有明显边界元素 16vp；ToolTile 12 见 Size）。 */
        val cardPaddingCompact = 16.dp
        val cardPaddingExpanded = 20.dp
        /** 元素间大 / 普通间隔（鸿蒙 16vp / 8vp）。 */
        val elementGapLarge = 16.dp
        val elementGap = 8.dp
        /** 主次文本纵 / 横间隔（鸿蒙 2vp / 8vp）。 */
        val textGapVertical = 2.dp
        val textGapHorizontal = 8.dp

        /** 语义别名（v2 新页面通用）：随窗口自适应的默认值（见 ui/adaptive 的 spec 覆盖）。 */
        val pageHorizontal: androidx.compose.ui.unit.Dp get() = pageHorizontalCompact
        val cardGap: androidx.compose.ui.unit.Dp get() = cardGapCompact
        val cardPadding: androidx.compose.ui.unit.Dp get() = cardPaddingCompact
    }

    /** 圆角数值（需要动画插值/自定义 RoundedCornerShape 时用；2026-11-17 对齐鸿蒙 18/14/24/26）。 */
    object Radius {
        val extraSmall = 4.dp
        val small = 8.dp
        val medium = 12.dp
        val large = 18.dp
        val largeIncreased = 20.dp
        val extraLarge = 24.dp
        val extraLargeIncreased = 24.dp
        val extraExtraLarge = 48.dp

        val card = 18.dp
        val cardLarge = 18.dp
        val hero = 24.dp
        val cardPressed = 12.dp
        val input = 14.dp
        val sheetTop = 26.dp
        val dialog = 28.dp
    }

    /** 组件高度 / 尺寸（Material 3 + Expressive 规格）。 */
    object Size {
        /** 最小触控目标（无障碍硬线）。 */
        val minTouchTarget = 48.dp

        /** 按钮高度：M3 baseline 40dp，Expressive medium 56dp。 */
        val buttonHeightSmall = 40.dp
        val buttonHeight = 56.dp
        val buttonHeightLarge = 96.dp

        /** App bar 高度。 */
        val topAppBarSmall = 64.dp
        val topAppBarMedium = 112.dp
        val topAppBarLarge = 152.dp

        /** 底部导航 / 侧栏。 */
        val navigationBarHeight = 64.dp
        val navigationBarTallHeight = 80.dp
        val navigationRailWidth = 80.dp
        val wideNavigationRailWidth = 256.dp

        /** 悬浮工具栏 / 停靠工具栏。 */
        val floatingToolbarHeight = 64.dp
        val dockedToolbarHeight = 64.dp

        /** 加载指示器容器。 */
        val loadingIndicator = 48.dp
        val loadingIndicatorSmall = 24.dp

        /** 搜索栏。 */
        val searchBarHeight = 56.dp

        /** 列表项高度（1/2/3 行）。 */
        val listItemOneLine = 52.dp
        val listItemTwoLine = 64.dp
        val listItemThreeLine = 88.dp

        /** 鸿蒙卡片/工具磁贴。 */
        val cardMinHeight = 96.dp
        val cardPadding = 16.dp
        val tilePadding = 12.dp
        val tileGlyph = 40.dp
        val pageHeaderHeight = 60.dp
        val harmonyButtonHeight = 48.dp

        /** Chip / FAB / 图标。 */
        val chipHeight = 32.dp
        val fabSize = 56.dp
        val fabSmallSize = 40.dp
        val fabLargeSize = 96.dp
        val iconSmall = 18.dp
        val iconMedium = 24.dp
        val iconLarge = 32.dp
        val iconXLarge = 48.dp

        /** 底部弹层最大高度比例（相对屏幕）。 */
        const val bottomSheetMaxHeightFraction = 0.9f
    }

    /** 阴影层级（M3 elevation levels 0/1/3/6/8/12）。 */
    object Elevation {
        val level0 = 0.dp
        val level1 = 1.dp
        val level2 = 3.dp
        val level3 = 6.dp
        val level4 = 8.dp
        val level5 = 12.dp

        val card = 0.dp          // 卡片用描边 + 色调分层，不用阴影
        val floating = level3    // 悬浮工具栏/FAB
        val sheet = level2
        val dialog = level3
    }

    /** 动效时长（非 spring 兜底场景；spring 见 CtMotion）。 */
    object Duration {
        const val short1 = 50
        const val short2 = 100
        const val short3 = 150
        const val short4 = 200
        const val medium1 = 250
        const val medium2 = 300
        const val medium3 = 350
        const val medium4 = 400
        const val long1 = 450
        const val long2 = 500
        const val long3 = 550
        const val long4 = 600
        const val extraLong1 = 700
        const val extraLong2 = 800
        const val extraLong3 = 900
        const val extraLong4 = 1000
    }

    /** 模糊（玻璃材质）参数；仅 Android 12+ 生效，低版本自动降级为半透明纯色。 */
    object Blur {
        val contentRadius = 16.dp
        val modalRadius = 24.dp
    }

    /** 窗口尺寸类断点（与 material3-window-size-class / design spec 对齐）。 */
    object Breakpoints {
        val compactMaxWidth = 599.dp
        val mediumMaxWidth = 839.dp
        val expandedMinWidth = 840.dp
        val largeMinWidth = 1200.dp
        val extraLargeMinWidth = 1600.dp

        /** 内容最大宽度。 */
        val contentMaxWidthCompact = 840.dp
        val contentMaxWidthExpanded = 1200.dp
        val contentMaxWidthWide = 1520.dp
    }

    /** 无障碍参数。 */
    object Accessibility {
        const val minTextContrast = 4.5f
        const val largeTextContrast = 3.0f
        const val graphicsContrast = 3.0f
        const val minFontScale = 0.85f
        const val maxFontScale = 2.0f
    }
}

