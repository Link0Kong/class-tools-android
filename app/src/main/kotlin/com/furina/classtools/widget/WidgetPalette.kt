package com.furina.classtools.widget

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.furina.classtools.R

internal enum class WidgetPalette(val label: String, val background: Int, val accent: Int, val ink: Int) {
    SYSTEM("默认青瓷", R.drawable.timetable_widget_background, R.color.widget_accent, R.color.widget_ink),
    BLUE("海蓝", R.drawable.widget_blue_bg, R.color.widget_blue_accent, R.color.widget_blue_ink),
    AMBER("琥珀", R.drawable.widget_amber_bg, R.color.widget_amber_accent, R.color.widget_amber_ink),
    LILAC("丁香", R.drawable.widget_lilac_bg, R.color.widget_lilac_accent, R.color.widget_lilac_ink);

    companion object {
        fun prefs(context: Context) = context.getSharedPreferences("desktop_widget_colors", Context.MODE_PRIVATE)
        fun get(context: Context, id: Int) = entries.find { it.name == prefs(context).getString(id.toString(), null) } ?: SYSTEM
    }
}

class WidgetColorActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val manager = AppWidgetManager.getInstance(this)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val widgets = DesktopWidgets.components.flatMap { (kind, provider) ->
            manager.getAppWidgetIds(android.content.ComponentName(this, provider)).map { it to kind }
        }
        fun finishWithColor(widgetId: Int, kind: WidgetKind) {
            val palettes = WidgetPalette.entries
            AlertDialog.Builder(this).setTitle(kind.label + "配色")
                .setSingleChoiceItems(palettes.map { it.label }.toTypedArray(), WidgetPalette.get(this, widgetId).ordinal) { dialog, ix ->
                    WidgetPalette.prefs(this).edit().putString(widgetId.toString(), palettes[ix].name).apply()
                    DesktopWidgets.refresh(this)
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                    dialog.dismiss()
                    finish()
                }.setNegativeButton("取消") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
        }
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val kind = widgets.firstOrNull { it.first == id }?.second
            if (kind == null) finish() else finishWithColor(id, kind)
        } else {
            val builder = AlertDialog.Builder(this).setTitle("桌面卡片配色").setOnCancelListener { finish() }
            if (widgets.isEmpty()) builder.setMessage("请先在桌面添加一张卡片。")
            else builder.setItems(widgets.map { (wid, kind) -> "${kind.label} · #$wid · ${WidgetPalette.get(this, wid).label}" }.toTypedArray()) { _, ix ->
                finishWithColor(widgets[ix].first, widgets[ix].second)
            }
            builder.setNegativeButton("关闭") { _, _ -> finish() }.show()
        }
    }
}
