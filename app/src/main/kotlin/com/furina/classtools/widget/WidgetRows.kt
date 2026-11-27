package com.furina.classtools.widget

import android.content.Context
import android.content.Intent
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

    data class Tiles(val startIndex: Int, val sections: List<WidgetSection>) : WidgetEntry {
        override val key = "tiles:" + sections.joinToString { it.link.destination }
    }
}

internal object WidgetRows {
    fun entries(kind: WidgetKind, model: WidgetContent, style: WidgetRowStyle): List<WidgetEntry> {
        if (!model.hasRecords) return emptyList()
        if (kind == WidgetKind.WORKBENCH) {
            val columns = if (style.twoColumns) 2 else 1
            return model.sections.chunked(columns).mapIndexed { index, sections -> WidgetEntry.Tiles(index * columns, sections) }
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
            if (interactive) setOnClickFillInIntent(R.id.widget_section_title, WidgetIntents.line(entry.section.link, kind))
        }
        is WidgetEntry.Tiles -> {
            fun tile(section: WidgetSection, index: Int): RemoteViews {
                val lead = section.lines.firstOrNull()
                val separateOverview = lead != null && style.tileOverview
                val line = if (separateOverview) requireNotNull(lead) else section.link.copy(
                    title = lead?.title ?: section.empty, detail = lead?.detail.orEmpty(),
                )
                return row(context, colors, kind, line, style.copy(sideDetails = false, check = !style.twoColumns && style.tileCheck),
                    owner, interactive, colors.tones[index % colors.tones.size], section.title,
                    section.link.takeIf { separateOverview })
            }
            if (!style.twoColumns) tile(entry.sections.single(), entry.startIndex)
            else RemoteViews(context.packageName, R.layout.widget_grid_row).apply {
                val slots = listOf(R.id.widget_tile_left, R.id.widget_tile_right)
                for ((column, slot) in slots.withIndex()) {
                    removeAllViews(slot)
                    entry.sections.getOrNull(column)?.let { addView(slot, tile(it, entry.startIndex + column)) }
                }
            }
        }
    }

    private fun row(
        context: Context, colors: WidgetPaint, kind: WidgetKind, line: WidgetLine, style: WidgetRowStyle,
        owner: String?, interactive: Boolean, tone: Int, caption: String = "", overview: WidgetLine? = null,
    ) = RemoteViews(context.packageName, R.layout.widget_line).apply {
        colors.band(this, R.id.widget_row_fill, R.id.widget_row_rule, tone)
        WidgetViews.label(this, colors, R.id.widget_row_caption, caption, accent = true)
        WidgetViews.label(this, colors, R.id.widget_row_title, line.title)
        val side = style.sideDetails && line.detail.isNotBlank()
        WidgetViews.label(this, colors, R.id.widget_row_detail, line.detail.takeUnless { side }.orEmpty())
        setInt(R.id.widget_row_detail, "setMaxLines", if (line.destination == "tt") 2 else 1)
        setViewVisibility(R.id.widget_row_side, WidgetViews.visible(side))
        WidgetViews.label(this, colors, R.id.widget_row_side_detail, line.detail.takeIf { side }.orEmpty())
        WidgetViews.label(this, colors, R.id.widget_row_excerpt, line.excerpt.takeIf { side && style.excerpt }.orEmpty())
        if (interactive) setOnClickFillInIntent(R.id.widget_row, WidgetIntents.line(line, kind))
        setViewVisibility(R.id.widget_row_open, WidgetViews.visible(overview != null))
        if (overview != null) {
            colors.text(this, R.id.widget_row_open, colors.accent)
            setContentDescription(R.id.widget_row_open, "打开" + caption)
            if (interactive) setOnClickFillInIntent(R.id.widget_row_open, WidgetIntents.line(overview, kind))
        }
        val check = owner != null && style.check && line.destination == "hw" && line.canComplete && line.focus.isNotBlank()
        setViewVisibility(R.id.widget_row_check, WidgetViews.visible(check))
        if (check) {
            WidgetViews.paintCheck(this, R.id.widget_row_check, line, colors)
            if (interactive) setOnClickFillInIntent(R.id.widget_row_check, Intent().setData(
                WidgetCommandReceiver.commandUri(requireNotNull(owner), WidgetCommandReceiver.COMPLETE, line.focus)))
        }
    }
}
