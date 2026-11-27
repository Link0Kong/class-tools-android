package com.furina.classtools.widget

import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.widget.RemoteViews
import com.furina.classtools.R

internal sealed interface WidgetEntry {
    val key: String

    data class Record(val line: WidgetLine, val quadrant: Int? = null) : WidgetEntry {
        override val key = "record:${line.destination}:${line.focus.ifBlank { line.homeworkId.ifBlank { line.title } }}"
    }

    data class Header(val section: WidgetSection) : WidgetEntry {
        override val key = "header:${section.title}:${section.link.focus}"
    }

    data class Tiles(val startIndex: Int, val sections: List<WidgetSection>, val featured: Boolean = false) : WidgetEntry {
        override val key = "tiles:" + sections.joinToString { it.link.destination }
    }
}

internal object WidgetRows {
    fun entries(kind: WidgetKind, model: WidgetContent, style: WidgetRowStyle): List<WidgetEntry> {
        if (!model.hasRecords) return emptyList()
        if (kind == WidgetKind.WORKBENCH) {
            val columns = if (style.twoColumns) 2 else 1
            val course = model.sections.firstOrNull()?.takeIf { style.workbenchAgenda && it.link.destination == "tt" && it.lines.isNotEmpty() }
            val offset = if (course == null) 0 else 1
            return listOfNotNull(course?.let { WidgetEntry.Tiles(0, listOf(it), featured = true) }) +
                model.sections.drop(offset).chunked(columns).mapIndexed { index, sections ->
                    WidgetEntry.Tiles(offset + index * columns, sections)
                }
        }
        val records = listOf(model.first) + model.more
        if (!style.grouped || model.sections.isEmpty()) return records.map { WidgetEntry.Record(it) }
        val grouped = model.sections.flatMapIndexed { index, section ->
            if (section.lines.isEmpty()) emptyList()
            else listOf(WidgetEntry.Header(section)) + section.lines.map {
                WidgetEntry.Record(it, index.takeIf { kind == WidgetKind.TASKS })
            }
        }
        val groupedKeys = grouped.mapTo(hashSetOf()) { it.key }
        return grouped + records.map { WidgetEntry.Record(it) }.filterNot { it.key in groupedKeys }
    }

    fun render(
        context: Context, colors: WidgetPaint, kind: WidgetKind, entry: WidgetEntry,
        style: WidgetRowStyle, owner: String?, interactive: Boolean = true,
    ): RemoteViews = when (entry) {
        is WidgetEntry.Record -> row(context, colors, kind, entry.line, style, owner, interactive,
            tone = entry.quadrant?.let(colors::quadrant) ?: colors.lane(entry.line, kind))
        is WidgetEntry.Header -> RemoteViews(context.packageName, R.layout.widget_section).apply {
            WidgetViews.label(this, colors, R.id.widget_section_title, entry.section.title, accent = true)
            if (interactive) setOnClickFillInIntent(R.id.widget_section_title, WidgetIntents.line(entry.section.link, kind, owner))
        }
        is WidgetEntry.Tiles -> {
            val paired = entry.sections.size == 2
            fun renderTile(section: WidgetSection, index: Int) =
                tile(context, colors, kind, section, style, owner, interactive, colors.tones[index % colors.tones.size],
                    paired, entry.featured)
            if (!paired) renderTile(entry.sections.single(), entry.startIndex)
            else RemoteViews(context.packageName, R.layout.widget_grid_row).apply {
                val slots = listOf(R.id.widget_tile_left, R.id.widget_tile_right)
                for ((column, slot) in slots.withIndex()) {
                    removeAllViews(slot)
                    entry.sections.getOrNull(column)?.let { addView(slot, renderTile(it, entry.startIndex + column)) }
                }
            }
        }
    }

    private fun tile(
        context: Context, colors: WidgetPaint, kind: WidgetKind, section: WidgetSection, style: WidgetRowStyle,
        owner: String?, interactive: Boolean, tone: Int, paired: Boolean, featured: Boolean,
    ) = RemoteViews(context.packageName, R.layout.widget_workbench_tile).apply {
        val lead = section.lines.firstOrNull()
        val line = lead ?: section.link
        val label = if (paired) when (section.link.destination) {
            "hw" -> "待办"
            "class-homework" -> "作业"
            "nt" -> "通知"
            else -> section.title
        } else section.title
        val caption = listOf(label, section.status).filter(String::isNotBlank).joinToString(" · ")
        setInt(R.id.widget_tile_fill, "setColorFilter", colors.fill(tone))
        setTextViewText(R.id.widget_tile_caption, caption)
        colors.text(this, R.id.widget_tile_caption, tone)
        val title = lead?.title ?: section.empty
        WidgetViews.label(this, colors, R.id.widget_tile_title,
            if (section.link.destination == "class-homework") title.removePrefix("作业 · ") else title)
        setTextViewTextSize(R.id.widget_tile_title, TypedValue.COMPLEX_UNIT_SP, if (featured) 17f else 13f)
        val detail = listOf(lead?.detail.orEmpty(), section.supportingDetail.takeUnless { featured }.orEmpty())
            .filter(String::isNotBlank).joinToString(" · ")
        WidgetViews.label(this, colors, R.id.widget_tile_detail, detail)
        WidgetViews.label(this, colors, R.id.widget_tile_context, section.supportingDetail.takeIf { featured }.orEmpty())
        setInt(R.id.widget_tile_detail, "setMaxLines", if (section.link.destination == "tt" && !paired) 2 else 1)
        if (interactive) setOnClickFillInIntent(R.id.widget_tile_primary, WidgetIntents.line(line, kind, owner))
        val check = bindCheck(this, colors, line, owner, interactive, paired || style.check)
        val separateOverview = lead != null && style.tileOverview && (!check || !paired && style.tileCheck)
        setViewVisibility(R.id.widget_tile_open, WidgetViews.visible(separateOverview))
        if (separateOverview) {
            setTextViewText(R.id.widget_tile_open, if (section.link.destination == "tt") "课表" else "列表")
            colors.text(this, R.id.widget_tile_open, colors.accent)
            setContentDescription(R.id.widget_tile_open, "打开${section.title}列表")
            if (interactive) setOnClickFillInIntent(R.id.widget_tile_open, WidgetIntents.line(section.link, kind, owner))
        }
    }

    private fun row(
        context: Context, colors: WidgetPaint, kind: WidgetKind, line: WidgetLine, style: WidgetRowStyle,
        owner: String?, interactive: Boolean, tone: Int,
    ) = RemoteViews(context.packageName, R.layout.widget_line).apply {
        colors.band(this, R.id.widget_row_fill, R.id.widget_row_rule, tone)
        WidgetViews.label(this, colors, R.id.widget_row_title, line.title)
        val side = style.sideDetails && line.detail.isNotBlank()
        WidgetViews.label(this, colors, R.id.widget_row_detail, line.detail.takeUnless { side }.orEmpty())
        setInt(R.id.widget_row_detail, "setMaxLines", if (line.destination == "tt") 2 else 1)
        setViewVisibility(R.id.widget_row_side, WidgetViews.visible(side))
        WidgetViews.label(this, colors, R.id.widget_row_side_detail, line.detail.takeIf { side }.orEmpty())
        WidgetViews.label(this, colors, R.id.widget_row_excerpt, line.excerpt.takeIf { side && style.excerpt }.orEmpty())
        if (interactive) setOnClickFillInIntent(R.id.widget_row, WidgetIntents.line(line, kind, owner))
        bindCheck(this, colors, line, owner, interactive, style.check)
    }

    private fun bindCheck(views: RemoteViews, colors: WidgetPaint, line: WidgetLine, owner: String?, interactive: Boolean, fits: Boolean): Boolean {
        val check = owner != null && fits && line.destination == "hw" && line.canComplete && line.focus.isNotBlank()
        views.setViewVisibility(R.id.widget_row_check, WidgetViews.visible(check))
        if (check) {
            WidgetViews.paintCheck(views, R.id.widget_row_check, line, colors)
            if (interactive) views.setOnClickFillInIntent(R.id.widget_row_check, Intent().setData(
                WidgetCommandReceiver.commandUri(requireNotNull(owner), WidgetCommandReceiver.COMPLETE, line.focus)))
        }
        return check
    }
}
