package com.furina.classtools.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.net.Uri
import android.content.Intent
import androidx.compose.ui.graphics.toArgb
import com.furina.classtools.ui.theme.*
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.furina.classtools.MainActivity
import com.furina.classtools.R
import com.furina.classtools.data.reminder.ReminderIntentFactory
import kotlin.math.roundToInt

internal object WidgetViews {
    private data class Layout(val width: Int, val height: Int, val rows: Int, val rowDetails: Boolean = false) {
        val banner get() = width >= 230 && height < 96
        val compact get() = height < 96
    }
    private val layouts = listOf(Layout(50, 26, 0), Layout(100, 48, 0), Layout(230, 48, 0),
        Layout(100, 96, 0), Layout(230, 96, 0), Layout(100, 165, 1),
        Layout(230, 165, 1, true), Layout(100, 220, 2), Layout(230, 220, 2, true), Layout(100, 320, 4), Layout(230, 320, 4, true))

    fun forHost(context: Context, kind: WidgetKind, model: WidgetContent, options: Bundle, palette: WidgetPalette = WidgetPalette.SYSTEM): RemoteViews {
        val appearance = com.furina.classtools.data.settings.AppSettingsStore(context).startupAppearanceMode()
        val scale = context.resources.configuration.fontScale.coerceAtLeast(1f)
        fun fit(w: Int, h: Int) = layouts.filter { it.width <= w / scale && it.height <= h / scale }
            .maxByOrNull { it.width * it.height } ?: layouts.first()
        if (Build.VERSION.SDK_INT >= 31 && !options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).isNullOrEmpty()) {
            val variants = layouts.associate { layout ->
                SizeF(layout.width * scale, layout.height * scale) to render(context, kind, model, layout, palette, appearance)
            }
            return RemoteViews(variants)
        }
        // Older launchers report the portrait width and landscape height as minima.
        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 96)
        val portrait = fit(minW, options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minH))
        val landscape = fit(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minW), minH)
        val view = render(context, kind, model, portrait, palette, appearance)
        return if (landscape == portrait) view else RemoteViews(render(context, kind, model, landscape, palette, appearance), view)
    }

    private fun render(context: Context, kind: WidgetKind, model: WidgetContent, layout: Layout, palette: WidgetPalette, appearance: AppearanceMode): RemoteViews {
        val views = RemoteViews(context.packageName, if (layout.banner) R.layout.widget_banner else R.layout.timetable_widget)
        val systemDark = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val dark = appearance.resolveDark(systemDark)
        val defaults = palette == WidgetPalette.SYSTEM && appearance != AppearanceMode.SYSTEM
        views.setInt(R.id.widget_root, "setBackgroundResource", if (!defaults) palette.background
            else if (dark) R.drawable.widget_default_dark else R.drawable.widget_default_light)
        val accent = (if (dark) DarkPrimary else LightPrimary).toArgb()
        val ink = (if (dark) DarkOnSurface else LightOnSurface).toArgb()
        fun RemoteViews.tint(viewId: Int, colorId: Int) {
            if (defaults) setTextColor(viewId, if (colorId == palette.ink) ink else accent)
            else if (Build.VERSION.SDK_INT >= 31) setColor(viewId, "setTextColor", colorId)
            else setTextColor(viewId, context.getColor(colorId))
        }
        val icon = when (kind) {
            WidgetKind.TIMETABLE -> R.drawable.widget_icon_timetable
            WidgetKind.WORKBENCH -> R.drawable.widget_icon_workbench
            WidgetKind.TASKS -> R.drawable.widget_icon_tasks
            WidgetKind.NOTICES -> R.drawable.widget_icon_notices
        }
        views.setImageViewResource(R.id.widget_icon, icon)
        if (defaults) views.setInt(R.id.widget_icon, "setColorFilter", accent)
        else if (Build.VERSION.SDK_INT >= 31) views.setColor(R.id.widget_icon, "setColorFilter", palette.accent)
        else views.setInt(R.id.widget_icon, "setColorFilter", context.getColor(palette.accent))
        views.tint(R.id.widget_course, palette.ink)
        views.tint(R.id.widget_heading, palette.accent)
        views.tint(R.id.widget_details, palette.accent)
        if (!layout.banner) views.tint(R.id.widget_summary, palette.accent)
        fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
        val vertical = dp(if (layout.height <= 96) 0 else 6)
        val horizontal = dp(if (layout.width >= 230) 14 else if (layout.height < 48) 6 else 10)
        views.setViewPadding(R.id.widget_root, horizontal, vertical, horizontal, vertical)
        val first = model.first
        val rowTarget = open(context, first.destination.ifBlank { kind.destination }, first.focus.ifBlank { if (first.destination == "class-homework") first.homeworkId else "" })
        val overview = open(context, kind.destination)
        views.setOnClickPendingIntent(R.id.widget_root, if (layout.compact) rowTarget else overview)
        views.setTextViewText(R.id.widget_heading, model.heading)
        views.setViewVisibility(R.id.widget_header, if (layout.height < 48) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.widget_course, first.title)
        if (!layout.banner) {
            val minimum = if (layout.compact) 0 else dp(48)
            views.setInt(R.id.widget_primary, "setMinimumHeight", minimum)
            views.setInt(R.id.widget_header, "setMinimumHeight", minimum)
            views.setOnClickPendingIntent(R.id.widget_primary, rowTarget.takeUnless { layout.compact })
            views.setOnClickPendingIntent(R.id.widget_header, overview.takeUnless { layout.compact })
        }
        val details = if (kind == WidgetKind.WORKBENCH && layout.rows > 0) "" else model.first.detail
        views.setTextViewText(R.id.widget_details, details)
        val showDetails = (layout.banner || !layout.compact) && details.isNotBlank()
        views.setViewVisibility(R.id.widget_details, if (showDetails) View.VISIBLE else View.GONE)
        if (layout.banner) return views
        val showSummary = layout.width >= 230 && model.summary.isNotBlank()
        views.setTextViewText(R.id.widget_summary, model.summary)
        views.setViewVisibility(R.id.widget_summary, if (showSummary && layout.height >= 165) View.VISIBLE else View.GONE)
        views.setTextViewTextSize(R.id.widget_course, TypedValue.COMPLEX_UNIT_SP, if (layout.compact) 16f else 18f)
        views.setInt(R.id.widget_course, "setMaxLines", if (layout.height >= 155 && !layout.rowDetails) 2 else 1)
        views.setInt(R.id.widget_details, "setMaxLines", if (layout.rows == 0 || layout.height >= 235) 2 else 1)
        views.removeAllViews(R.id.widget_list)
        for (item in model.more.take(layout.rows)) {
            val row = RemoteViews(context.packageName, R.layout.widget_line)
            row.tint(R.id.widget_row_title, palette.ink)
            row.tint(R.id.widget_row_detail, palette.accent)
            row.setTextViewText(R.id.widget_row_title, item.title)
            row.setTextViewText(R.id.widget_row_detail, item.detail)
            row.setViewVisibility(R.id.widget_row_detail, if (layout.rowDetails && item.detail.isNotBlank()) View.VISIBLE else View.GONE)
            row.setOnClickPendingIntent(R.id.widget_row, open(context, item.destination.ifBlank { kind.destination }, item.focus.ifBlank { if (item.destination == "class-homework") item.homeworkId else "" }))
            views.addView(R.id.widget_list, row)
        }
        return views
    }

    private fun open(context: Context, destination: String, focus: String = ""): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).setAction(MainActivity.WIDGET_ACTION + destination)
            .setData(Uri.Builder().scheme("classtools-widget").authority("open").appendPath(destination).appendQueryParameter("focus", focus).build())
            .putExtra(MainActivity.CARD_FOCUS, focus)
            .putExtra(ReminderIntentFactory.EXTRA_TOOL_ID, if (destination == "class-homework") "hw" else destination)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, destination.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
