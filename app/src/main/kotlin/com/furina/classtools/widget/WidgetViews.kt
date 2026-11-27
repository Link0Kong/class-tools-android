package com.furina.classtools.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import com.furina.classtools.R
import com.furina.classtools.data.settings.AppSettingsStore
import kotlin.math.roundToInt

internal object WidgetViews {
    @Suppress("DEPRECATION")
    fun forHost(
        context: Context,
        kind: WidgetKind,
        model: WidgetContent,
        options: Bundle,
        palette: WidgetPalette = WidgetPalette.SYSTEM,
        monet: Boolean = AppSettingsStore(context).startupDynamicColor(),
        owner: String? = null,
        widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
        preview: Boolean = false,
    ): RemoteViews {
        val colors = WidgetPaint.resolve(context, palette, monet)
        val metrics = context.resources.displayMetrics
        fun fit(width: Float, height: Float) = WidgetLayout.fit(width, height, metrics)
        fun view(layout: WidgetLayout) = render(context, kind, model, layout, colors, owner, widgetId, preview)

        if (Build.VERSION.SDK_INT >= 31) {
            // These are the host's usable sizes in dp, already excluding AppWidgetHostView padding.
            val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                .orEmpty().filter { it.width > 0 && it.height > 0 }.distinct()
            if (sizes.size == 1) return sizes.single().let { view(fit(it.width, it.height)) }
            if (sizes.size in 2..16) return RemoteViews(sizes.associateWith { view(fit(it.width, it.height)) })
        }

        fun bound(key: String, fallback: Int) = options.getInt(key).takeIf { it > 0 } ?: fallback
        val minW = bound(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
        val minH = bound(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 112)
        val portrait = fit(minW.toFloat(), bound(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minH).toFloat())
        val landscape = fit(bound(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minW).toFloat(), minH.toFloat())
        return if (landscape == portrait) view(portrait) else RemoteViews(view(landscape), view(portrait))
    }

    @Suppress("DEPRECATION")
    private fun render(
        context: Context, kind: WidgetKind, model: WidgetContent, layout: WidgetLayout,
        colors: WidgetPaint, owner: String?, widgetId: Int, preview: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_collection)
        colors.paintPaper(views)
        val inset = (layout.inset * context.resources.displayMetrics.density).roundToInt()
        views.setViewPadding(R.id.widget_body, inset, 0, inset, 0)
        val overview = WidgetIntents.open(context, kind.destination)
        val primary = model.first
        val refresh = owner != null && kind == WidgetKind.NOTICES && layout.extraControl
        val create = model.createTask && layout.extraControl
        val collection = layout.collection && model.hasRecords
        views.setOnClickPendingIntent(R.id.widget_root, overview)
        views.setViewVisibility(R.id.widget_header, visible(collection))
        views.setViewVisibility(R.id.widget_summary_row, visible(!collection))
        views.setViewVisibility(R.id.widget_list, visible(collection && !preview))
        views.setViewVisibility(R.id.widget_preview_rows, visible(collection && preview))
        views.setInt(R.id.widget_body, "setGravity", if (collection || layout.collection) Gravity.TOP else Gravity.CENTER_VERTICAL)

        if (collection) {
            val caption = when {
                model.cachedRemainder > 0 -> "另 ${model.cachedRemainder} 项见全部"
                layout.headerDetails -> model.summary
                else -> model.compactStatus
            }
            val heading = if (layout.headerDetails && model.cachedRemainder == 0) kind.label else model.compactStatus
            label(views, colors, R.id.widget_heading, heading, accent = true)
            label(views, colors, R.id.widget_header_status, caption.takeUnless { it == heading }.orEmpty())
            views.setOnClickPendingIntent(R.id.widget_header_copy, overview)
            fullEntry(views, colors, R.id.widget_open, kind, overview)
            actions(context, views, colors, R.id.widget_add, R.id.widget_refresh, create, refresh, owner)
            if (preview) {
                views.removeAllViews(R.id.widget_preview_rows)
                WidgetRows.entries(kind, model, layout.rows).take(3).forEach {
                    views.addView(R.id.widget_preview_rows, WidgetRows.render(context, colors, kind, it, layout.rows, null, interactive = false))
                }
            } else {
                views.setRemoteAdapter(R.id.widget_list, WidgetCollectionService.adapter(context, widgetId, kind, layout.rows))
                views.setPendingIntentTemplate(R.id.widget_list, WidgetIntents.template(context, widgetId))
                val hint = if (kind == WidgetKind.WORKBENCH) "工作台入口，可上下滑动" else "${kind.label}，可上下滑动"
                views.setContentDescription(R.id.widget_list, hint)
            }
        } else {
            val status = model.compactStatus
            val title = if (kind == WidgetKind.TASKS && status.endsWith(" 待办"))
                primary.title.removePrefix("待办 · ") else primary.title
            label(views, colors, R.id.widget_summary_status, status, accent = true)
            label(views, colors, R.id.widget_course, title)
            val check = owner != null && primary.canComplete && primary.focus.isNotBlank() && layout.extraControl
            val side = layout.sideSummary && primary.detail.isNotBlank()
            val stackedDetail = model.compactDetail.takeIf { !side && layout.stackedSummaryDetail }.orEmpty()
            views.setInt(R.id.widget_course, "setMaxLines",
                if (stackedDetail.isNotBlank()) 1 else layout.summaryTitleLines)
            label(views, colors, R.id.widget_side_details, primary.detail.takeIf { side }.orEmpty())
            label(views, colors, R.id.widget_details, stackedDetail)
            views.setOnClickPendingIntent(R.id.widget_primary, WidgetIntents.target(context, primary, kind))
            fullEntry(views, colors, R.id.widget_summary_open, kind, overview)
            actions(context, views, colors, R.id.widget_summary_add, R.id.widget_summary_refresh, create && !check, refresh, owner)
            views.setViewVisibility(R.id.widget_primary_check, visible(check))
            if (check) {
                paintCheck(views, R.id.widget_primary_check, primary, colors)
                views.setOnClickPendingIntent(R.id.widget_primary_check,
                    WidgetCommandReceiver.action(context, requireNotNull(owner), WidgetCommandReceiver.COMPLETE, primary.focus))
            }
        }
        return views
    }

    private fun fullEntry(view: RemoteViews, colors: WidgetPaint, id: Int, kind: WidgetKind, overview: PendingIntent) {
        colors.text(view, id, colors.accent)
        view.setContentDescription(id, if (kind == WidgetKind.WORKBENCH) "打开完整工作台" else "查看全部${kind.label}")
        view.setOnClickPendingIntent(id, overview)
    }

    private fun actions(
        context: Context, view: RemoteViews, colors: WidgetPaint, addId: Int, refreshId: Int,
        create: Boolean, refresh: Boolean, owner: String?,
    ) {
        view.setViewVisibility(addId, visible(create))
        view.setViewVisibility(refreshId, visible(refresh))
        if (create) {
            colors.text(view, addId, colors.accent)
            view.setOnClickPendingIntent(addId, WidgetIntents.open(context, "new-task"))
        }
        if (refresh && owner != null) {
            view.setInt(refreshId, "setColorFilter", colors.accent)
            view.setOnClickPendingIntent(refreshId, WidgetCommandReceiver.action(context, owner, WidgetCommandReceiver.REFRESH))
        }
    }

    internal fun label(view: RemoteViews, colors: WidgetPaint, id: Int, text: String, accent: Boolean = false) {
        view.setTextViewText(id, text)
        view.setViewVisibility(id, visible(text.isNotBlank()))
        colors.text(view, id, if (accent) colors.accent else colors.ink)
    }

    internal fun paintCheck(view: RemoteViews, id: Int, line: WidgetLine, colors: WidgetPaint) {
        view.setInt(id, "setColorFilter", colors.accent)
        view.setContentDescription(id, "完成任务，" + line.title.removePrefix("待办 · "))
    }

    internal fun visible(value: Boolean) = if (value) View.VISIBLE else View.GONE
}
