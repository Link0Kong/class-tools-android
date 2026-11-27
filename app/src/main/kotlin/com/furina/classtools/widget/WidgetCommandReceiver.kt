package com.furina.classtools.widget

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.furina.classtools.ClassToolsApp
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.tasks.changeCompletion
import com.furina.classtools.data.tasks.text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val Session.widgetOwner: String get() = (if (debug) "debug:" else "account:") + sid

/** Explicit immutable intents bind each control to the account and item it was rendered for. */
class WidgetCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val data = intent.data ?: return
        val command = data.pathSegments.firstOrNull() ?: return
        if (data.scheme != "classtools-widget" || data.authority != "action" ||
            (command != COMPLETE && command != REFRESH)) return
        val pending = goAsync()
        val app = context.applicationContext as ClassToolsApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val session = WidgetFeedStore.session(app)
                if (session == null || data.getQueryParameter("owner") != session.widgetOwner) {
                    DesktopWidgets.refresh(app)
                    return@launch
                }
                val message = when (command) {
                    COMPLETE -> {
                        val id = data.pathSegments.getOrNull(1)?.takeIf(String::isNotBlank) ?: return@launch
                        val repository = app.container.tasksFor(session)
                        var found = false
                        repository.edit { items, now ->
                            found = items.any { it.text("id") == id }
                            changeCompletion(items, id, true, now)
                        }
                        DesktopWidgets.refresh(app)
                        if (found) "已完成任务" else "任务已更新"
                    }
                    else -> "已请求刷新通知"
                }
                if (!session.debug) WidgetFeedStore.request(app, session.sid, replace = true)
                withContext(Dispatchers.Main) { Toast.makeText(app, message, Toast.LENGTH_SHORT).show() }
            } finally {
                pending.finish()
            }
        }
    }

    internal companion object {
        const val COMPLETE = "complete"
        const val REFRESH = "refresh-notices"

        fun commandUri(owner: String, command: String, id: String? = null): Uri {
            val uri = Uri.Builder().scheme("classtools-widget").authority("action").appendPath(command)
                .appendQueryParameter("owner", owner)
            if (id != null) uri.appendPath(id)
            return uri.build()
        }

        fun action(context: Context, owner: String, command: String, id: String? = null): PendingIntent {
            val intent = Intent(context, WidgetCommandReceiver::class.java).setData(commandUri(owner, command, id))
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
