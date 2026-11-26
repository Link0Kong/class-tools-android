package com.furina.classtools.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.net.Uri
import android.content.Intent
import com.furina.classtools.ui.theme.AppearanceMode
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
        val banner get() = width >= 160 && height < 96
        val compact get() = height < 96
    }
    private val layouts = listOf(Layout(50, 26, 0), Layout(100, 48, 0), Layout(160, 48, 0), Layout(230, 48, 0),
        Layout(100, 96, 0), Layout(160, 96, 0), Layout(160, 165, 1), Layout(230, 96, 0), Layout(100, 165, 1),
        Layout(230, 165, 1, true), Layout(100, 220, 2), Layout(230, 220, 2, true), Layout(100, 320, 4), Layout(230, 320, 4, true), Layout(320, 240, 2, true), Layout(320, 320, 4, true))

    fun forHost(context: Context, kind: WidgetKind, model: WidgetContent, options: Bundle, palette: WidgetPalette = WidgetPalette.SYSTEM,
                monet: Boolean = com.furina.classtools.data.settings.AppSettingsStore(context).startupDynamicColor(),
                owner: String? = null): RemoteViews {
        val appearance = com.furina.classtools.data.settings.AppSettingsStore(context).startupAppearanceMode()
        val systemDark = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val colors = WidgetPaint(context, palette, appearance.resolveDark(systemDark), monet, appearance == AppearanceMode.SYSTEM)
        val scale = context.resources.configuration.fontScale.coerceAtLeast(1f)
        fun fit(w: Int, h: Int) = layouts.filter { it.width <= w / scale && it.height <= h / scale }
            .maxByOrNull { it.width * it.height } ?: layouts.first()
        if (Build.VERSION.SDK_INT >= 31 && !options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).isNullOrEmpty()) {
            val variants = layouts.associate { layout ->
                SizeF(layout.width * scale, layout.height * scale) to render(context, kind, model, layout, colors, owner)
            }
            return RemoteViews(variants)
        }
        // Older launchers report the portrait width and landscape height as minima.
        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 96)
        val portrait = fit(minW, options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minH))
        val landscape = fit(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minW), minH)
        val view = render(context, kind, model, portrait, colors, owner)
        return if (landscape == portrait) view else RemoteViews(render(context, kind, model, landscape, colors, owner), view)
    }

    private fun render(context: Context, kind: WidgetKind, model: WidgetContent, layout: Layout, colors: WidgetPaint, owner: String?): RemoteViews {
        val board = layout.width >= 320 && layout.height >= 240 && model.sections.isNotEmpty()
        val resource = when {
            board -> R.layout.widget_board
            layout.banner -> R.layout.widget_banner
            else -> R.layout.timetable_widget
        }
        val views = RemoteViews(context.packageName, resource)
        colors.paintPaper(views)
        fun dp(value: Int) = (context.resources.displayMetrics.density * value).roundToInt()
        val inset = if (layout.compact) 5 else 10
        views.setViewPadding(R.id.widget_body, dp(inset), dp(if (layout.height >= 165) 5 else 0), dp(inset), dp(if (layout.height >= 165) 6 else 0))
        if (!layout.banner) views.setInt(R.id.widget_body, "setGravity", if (layout.compact) android.view.Gravity.CENTER_VERTICAL else android.view.Gravity.TOP)
        val overview = open(context, kind.destination)
        val first = model.first
        val primaryTarget = target(context, first, kind)
        views.setOnClickPendingIntent(R.id.widget_root, if (layout.compact) primaryTarget else overview)
        val icon = when (kind) {
            WidgetKind.TIMETABLE -> R.drawable.widget_icon_timetable
            WidgetKind.WORKBENCH -> R.drawable.widget_icon_workbench
            WidgetKind.TASKS -> R.drawable.widget_icon_tasks
            WidgetKind.NOTICES -> R.drawable.widget_icon_notices
        }
        views.setImageViewResource(R.id.widget_icon, icon)
        views.setInt(R.id.widget_icon, "setColorFilter", colors.accent)
        colors.text(views, R.id.widget_heading, colors.accent)
        val numbered = !layout.compact && model.number.isNotEmpty()
        val header = if (!numbered) model.heading else when (kind) {
            WidgetKind.TIMETABLE -> model.label + " · " + model.heading.substringBefore(" · ").takeUnless { it.contains("/") }.orEmpty().ifBlank { "下一节" }
            else -> model.label
        }
        views.setTextViewText(R.id.widget_heading, header)
        if (layout.banner) {
            views.setTextViewText(R.id.widget_course, first.title)
            colors.text(views, R.id.widget_course, colors.ink)
            views.setTextViewText(R.id.widget_details, first.detail)
            colors.text(views, R.id.widget_details, colors.ink)
            views.setViewVisibility(R.id.widget_details, if (first.detail.isBlank() || layout.width < 230) View.GONE else View.VISIBLE)
            bindCheck(context, views, R.id.widget_primary_check, first, colors, owner)
            return views
        }
        views.setViewVisibility(R.id.widget_header, if (layout.height < 48) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_icon, if (numbered) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.widget_number, model.number)
        colors.text(views, R.id.widget_number, colors.ink)
        views.setViewVisibility(R.id.widget_number, if (numbered) View.VISIBLE else View.GONE)
        views.setOnClickPendingIntent(R.id.widget_header, overview.takeUnless { layout.compact })
        views.setInt(R.id.widget_header, "setMinimumHeight", dp(if (layout.compact) 0 else 48))
        views.setTextViewText(R.id.widget_summary, model.summary)
        colors.text(views, R.id.widget_summary, colors.ink)
        val showSummary = layout.width >= 230 && layout.height >= 165 && kind != WidgetKind.NOTICES
        views.setViewVisibility(R.id.widget_summary, if (showSummary) View.VISIBLE else View.GONE)
        val add = model.createTask && layout.width >= 230 && !layout.compact
        views.setViewVisibility(R.id.widget_add, if (add) View.VISIBLE else View.GONE)
        if (add) {
            colors.text(views, R.id.widget_add, colors.accent)
            views.setOnClickPendingIntent(R.id.widget_add, open(context, "new-task"))
        }
        val refresh = kind == WidgetKind.NOTICES && (owner != null || model.number.isNotEmpty()) && layout.width >= 230 && !layout.compact
        views.setViewVisibility(R.id.widget_refresh, if (refresh) View.VISIBLE else View.GONE)
        if (refresh) {
            views.setInt(R.id.widget_refresh, "setColorFilter", colors.accent)
            views.setOnClickPendingIntent(R.id.widget_refresh, owner?.let { WidgetCommandReceiver.action(context, it, WidgetCommandReceiver.REFRESH) })
        }
        if (board) {
            populateBoard(context, views, colors, model, kind, layout, owner)
            return views
        }
        colors.band(views, R.id.widget_primary_fill, R.id.widget_primary_rule, colors.lane(first, kind))
        views.setTextViewText(R.id.widget_course, first.title)
        colors.text(views, R.id.widget_course, colors.ink)
        views.setTextViewTextSize(R.id.widget_course, TypedValue.COMPLEX_UNIT_SP, if (layout.compact) 15f else 16f)
        colors.text(views, R.id.widget_details, colors.ink)
        views.setTextViewText(R.id.widget_details, first.detail)
        val details = !layout.compact && first.detail.isNotBlank() && !(kind == WidgetKind.WORKBENCH && layout.rows > 0)
        views.setViewVisibility(R.id.widget_details, if (details) View.VISIBLE else View.GONE)
        views.setOnClickPendingIntent(R.id.widget_primary, primaryTarget.takeUnless { layout.compact })
        views.setInt(R.id.widget_primary, "setMinimumHeight", dp(if (layout.compact) 0 else 48))
        views.setInt(R.id.widget_primary_body, "setMinimumHeight", dp(if (layout.compact) 0 else 48))
        bindCheck(context, views, R.id.widget_primary_check, first, colors, owner, layout.width >= 160 && !layout.compact)
        if (layout.compact) views.setViewVisibility(R.id.widget_primary_rule, View.GONE)
        views.removeAllViews(R.id.widget_list)
        for (line in model.more.take(layout.rows)) {
            val row = RemoteViews(context.packageName, R.layout.widget_line)
            colors.band(row, R.id.widget_row_fill, R.id.widget_row_rule, colors.lane(line, kind))
            colors.text(row, R.id.widget_row_title, colors.ink)
            colors.text(row, R.id.widget_row_detail, colors.ink)
            row.setTextViewText(R.id.widget_row_title, line.title)
            row.setTextViewText(R.id.widget_row_detail, line.detail)
            row.setViewVisibility(R.id.widget_row_detail, if (layout.rowDetails && line.detail.isNotBlank()) View.VISIBLE else View.GONE)
            row.setOnClickPendingIntent(R.id.widget_row, target(context, line, kind))
            bindCheck(context, row, R.id.widget_row_check, line, colors, owner, layout.width >= 160)
            views.addView(R.id.widget_list, row)
        }
        return views
    }

    private fun populateBoard(context: Context, views: RemoteViews, colors: WidgetPaint, model: WidgetContent, kind: WidgetKind, layout: Layout, owner: String?) {
        views.removeAllViews(R.id.widget_grid)
        for ((rowIndex, sections) in model.sections.chunked(2).withIndex()) {
            val horizontal = RemoteViews(context.packageName, R.layout.widget_grid_row)
            horizontal.removeAllViews(R.id.widget_grid_row)
            for ((col, section) in sections.withIndex()) {
                val panel = RemoteViews(context.packageName, R.layout.widget_cell)
                val index = rowIndex * 2 + col
                val tone = if (kind == WidgetKind.TASKS) colors.quadrant(index) else colors.tones[index]
                panel.setInt(R.id.widget_cell_fill, "setColorFilter", colors.fill(tone))
                panel.setTextViewText(R.id.widget_cell_label, section.title)
                colors.text(panel, R.id.widget_cell_label, tone)
                panel.setOnClickPendingIntent(R.id.widget_cell, target(context, section.link, kind))
                panel.removeAllViews(R.id.widget_cell_items)
                val limit = if (kind == WidgetKind.TIMETABLE) (if (layout.height >= 320) 4 else 3) else 1
                val lines = section.lines.take(limit).ifEmpty { listOf(section.link.copy(title = section.empty)) }
                for ((slot, line) in lines.withIndex()) {
                    val item = RemoteViews(context.packageName, R.layout.widget_cell_item)
                    if (kind == WidgetKind.TIMETABLE && section.lines.isNotEmpty()) {
                        item.setViewVisibility(R.id.widget_event_rule, View.VISIBLE)
                        item.setInt(R.id.widget_event_rule, "setColorFilter", colors.tones[(col + slot) % colors.tones.size])
                    }
                    item.setTextViewText(R.id.widget_cell_title, line.title)
                    if (line.canComplete) item.setInt(R.id.widget_cell_title, "setMaxLines", 2)
                    colors.text(item, R.id.widget_cell_title, colors.ink)
                    item.setTextViewText(R.id.widget_cell_detail, line.detail)
                    colors.text(item, R.id.widget_cell_detail, colors.ink)
                    item.setViewVisibility(R.id.widget_cell_detail, if (line.detail.isBlank()) View.GONE else View.VISIBLE)
                    item.setOnClickPendingIntent(R.id.widget_cell_item, target(context, line, kind))
                    bindCheck(context, item, R.id.widget_cell_check, line, colors, owner)
                    panel.addView(R.id.widget_cell_items, item)
                }
                horizontal.addView(R.id.widget_grid_row, panel)
            }
            views.addView(R.id.widget_grid, horizontal)
        }
    }

    private fun bindCheck(context: Context, view: RemoteViews, control: Int, line: WidgetLine,
                          colors: WidgetPaint, owner: String?, fits: Boolean = true) {
        val available = fits && line.destination == "hw" && line.canComplete && line.focus.isNotBlank()
        view.setViewVisibility(control, if (available) View.VISIBLE else View.GONE)
        if (available) {
            view.setInt(control, "setColorFilter", colors.accent)
            view.setContentDescription(control, "完成任务，" + line.title.removePrefix("待办 · "))
        }
        view.setOnClickPendingIntent(control, if (available && owner != null)
            WidgetCommandReceiver.action(context, owner, WidgetCommandReceiver.COMPLETE, line.focus) else null)
    }

    private fun target(context: Context, line: WidgetLine, kind: WidgetKind) =
        open(context, line.destination.ifBlank { kind.destination },
            line.focus.ifBlank { if (line.destination == "class-homework") line.homeworkId else "" })

    private fun open(context: Context, destination: String, focus: String = ""): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).setAction(MainActivity.WIDGET_ACTION + destination)
            .setData(Uri.Builder().scheme("classtools-widget").authority("open").appendPath(destination).appendQueryParameter("focus", focus).build())
            .putExtra(MainActivity.CARD_FOCUS, focus)
            .putExtra(ReminderIntentFactory.EXTRA_TOOL_ID, if (destination == "class-homework" || destination == "new-task") "hw" else destination)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, destination.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
