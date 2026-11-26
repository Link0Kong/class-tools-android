package com.furina.classtools.data.reminder

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.IntentCompat

/** Owns the existing live notification only for the duration of its pre-class window. */
class CourseLiveService : Service() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val card = intent?.let { IntentCompat.getParcelableExtra(it, CARD, Notification::class.java) }
        val remaining = (card?.`when` ?: 0) - System.currentTimeMillis()
        if (card == null || remaining <= 0) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        val id = intent.getIntExtra(ID, 0)
        try {
            startForeground(id, card)
        } catch (_: IllegalStateException) {
            // The app moved into the background before promotion. Its ordinary live card stays posted.
            stopSelfResult(startId)
            return START_NOT_STICKY
        } catch (_: SecurityException) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stopSelfResult(startId) }, remaining)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val CARD = "course_notification"
        private const val ID = "notification_id"

        internal fun attach(context: Context, id: Int, card: Notification) {
            val intent = Intent(context, CourseLiveService::class.java)
                .putExtra(ID, id).putExtra(CARD, card)
            // Background starts can be restricted. The original system notification still expires at the bell.
            runCatching { context.startForegroundService(intent) }
        }

        internal fun stop(context: Context) {
            context.stopService(Intent(context, CourseLiveService::class.java))
        }
    }
}
