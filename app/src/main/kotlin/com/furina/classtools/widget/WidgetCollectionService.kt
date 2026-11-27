package com.furina.classtools.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class WidgetCollectionService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val (kind, key) = requireNotNull(intent.data).pathSegments
        return WidgetCollectionFactory(applicationContext, id, WidgetKind.valueOf(kind), WidgetRowStyle.fromKey(key))
    }

    internal companion object {
        fun adapter(context: Context, id: Int, kind: WidgetKind, style: WidgetRowStyle) =
            Intent(context, WidgetCollectionService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                // Adapter identity includes the instance and row presentation, not the current records.
                .setData(Uri.Builder().scheme("classtools-widget").authority("collection")
                    .appendPath(kind.name).appendPath(style.key()).appendQueryParameter("id", id.toString()).build())
    }
}

internal class WidgetCollectionFactory(
    private val context: Context, private val id: Int, private val kind: WidgetKind, private val style: WidgetRowStyle,
) : RemoteViewsService.RemoteViewsFactory {
    private data class Rows(val entries: List<WidgetEntry>, val colors: WidgetPaint, val owner: String?)
    @Volatile private var rows: Rows? = null

    override fun onCreate() = Unit
    override fun onDataSetChanged() = runBlocking(Dispatchers.IO) {
        val (session, _, content) = WidgetSnapshot.read(context, kind, System.currentTimeMillis())
        rows = Rows(WidgetRows.entries(kind, content, style), WidgetPaint.resolve(context, WidgetPalette.get(context, id)),
            session?.widgetOwner)
    }
    override fun onDestroy() { rows = null }
    override fun getCount() = rows?.entries?.size ?: 0
    override fun getViewAt(position: Int): RemoteViews? = rows?.let { current ->
        current.entries.getOrNull(position)?.let { WidgetRows.render(context, current.colors, kind, it, style, current.owner) }
    }
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount() = 3
    override fun hasStableIds() = true
    override fun getItemId(position: Int): Long =
        rows?.entries?.getOrNull(position)?.key?.fold(0L) { hash, char -> 31 * hash + char.code } ?: -1L
}
