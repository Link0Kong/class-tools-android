package com.furina.classtools.widget

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.furina.classtools.MainActivity
import com.furina.classtools.data.reminder.ReminderIntentFactory

internal object WidgetIntents {
    fun template(context: Context, widgetId: Int): PendingIntent =
        PendingIntent.getActivity(context, widgetId, Intent(context, WidgetRowActivity::class.java), PendingIntent.FLAG_MUTABLE)

    fun target(context: Context, line: WidgetLine, kind: WidgetKind) =
        open(context, destination(line, kind), focus(line))

    fun line(line: WidgetLine, kind: WidgetKind) =
        Intent().setData(uri(destination(line, kind), focus(line)))

    fun open(context: Context, destination: String, focus: String = ""): PendingIntent =
        PendingIntent.getActivity(context, 0, activityIntent(context, destination, focus),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun activityIntent(context: Context, destination: String, focus: String): Intent =
        Intent(context, MainActivity::class.java).setAction(MainActivity.WIDGET_ACTION + destination)
            .setData(uri(destination, focus))
            .putExtra(MainActivity.CARD_FOCUS, focus)
            .putExtra(ReminderIntentFactory.EXTRA_TOOL_ID, if (destination == "class-homework" || destination == "new-task") "hw" else destination)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    private fun uri(destination: String, focus: String) =
        Uri.Builder().scheme("classtools-widget").authority("open").appendPath(destination)
            .appendQueryParameter("focus", focus).build()

    private fun destination(line: WidgetLine, kind: WidgetKind) = line.destination.ifBlank { kind.destination }
    private fun focus(line: WidgetLine) = line.focus.ifBlank { if (line.destination == "class-homework") line.homeworkId else "" }
}

/** A collection has one activity template for both detail taps and inline completion. */
class WidgetRowActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent.data
        val destination = data?.pathSegments?.firstOrNull()
        if (data?.scheme == "classtools-widget") {
            when {
                data.authority == "open" && destination in setOf("tt", "nt", "hw", "class-homework", "home") -> {
                    startActivity(WidgetIntents.activityIntent(this, requireNotNull(destination), data.getQueryParameter("focus").orEmpty()))
                }
                data.authority == "action" && destination == WidgetCommandReceiver.COMPLETE -> {
                    sendBroadcast(Intent(this, WidgetCommandReceiver::class.java).setData(data))
                }
            }
        }
        finish()
    }
}
