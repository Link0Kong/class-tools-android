package com.furina.classtools.widget

import android.util.DisplayMetrics
import android.util.TypedValue
import kotlin.math.max

internal data class WidgetLayout(
    val collection: Boolean,
    val inset: Int,
    val extraControl: Boolean,
    val headerDetails: Boolean,
    val sideSummary: Boolean,
    val stackedSummaryDetail: Boolean,
    val summaryTitleLines: Int,
    val rows: WidgetRowStyle,
) {
    companion object {
        fun fit(width: Float, height: Float, metrics: DisplayMetrics): WidgetLayout {
            fun text(sp: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics) / metrics.density
            val fontWidth = (text(14f) / 14f).coerceAtLeast(1f)
            val headingHeight = (text(14f) + text(11f)) * 1.2f
            val header = max(48f, headingHeight)
            val row = max(48f, headingHeight + 8f)
            val inset = if (width < 148) 4 else 8
            val availableWidth = width - 2 * inset
            val collection = availableWidth >= 96 && height >= header + row + 4
            val roomy = height >= header + row * 2 + 8
            val sideSummary = availableWidth >= 96 + 166 * fontWidth
            return WidgetLayout(
                collection = collection,
                inset = inset,
                extraControl = availableWidth >= 96 + 76 * fontWidth && height >= 48,
                headerDetails = availableWidth >= 96 + 114 * fontWidth,
                sideSummary = sideSummary,
                stackedSummaryDetail = height >= (text(14f) + 2 * text(11f)) * 1.2f + 4,
                summaryTitleLines = if (height >= 2 * headingHeight + 4) 2 else 1,
                rows = WidgetRowStyle(
                    sideDetails = availableWidth >= 48 + 218 * fontWidth,
                    grouped = roomy && availableWidth >= 290 * fontWidth,
                    excerpt = roomy && availableWidth >= 48 + 235 * fontWidth,
                    twoColumns = availableWidth >= 2 * (48 + 108 * fontWidth) + 4,
                    check = availableWidth >= 48 + 67 * fontWidth,
                    tileOverview = availableWidth >= 48 + 72 * fontWidth,
                    tileCheck = availableWidth >= 96 + 106 * fontWidth,
                ),
            )
        }
    }
}

internal data class WidgetRowStyle(
    val sideDetails: Boolean,
    val grouped: Boolean,
    val excerpt: Boolean,
    val twoColumns: Boolean,
    val check: Boolean,
    val tileOverview: Boolean,
    val tileCheck: Boolean,
) {
    fun key(): String = listOf(sideDetails, grouped, excerpt, twoColumns, check, tileOverview, tileCheck)
        .joinToString("") { if (it) "1" else "0" }

    companion object {
        fun fromKey(key: String): WidgetRowStyle {
            fun on(index: Int) = key.getOrNull(index) == '1'
            return WidgetRowStyle(on(0), on(1), on(2), on(3), on(4), on(5), on(6))
        }
    }
}
