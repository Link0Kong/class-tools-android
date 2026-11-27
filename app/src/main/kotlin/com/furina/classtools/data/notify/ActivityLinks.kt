package com.furina.classtools.data.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.furina.classtools.ClassToolsApp
import com.furina.classtools.MainActivity
import com.furina.classtools.ui.navigation.IncomingNavigation
import com.furina.classtools.ui.navigation.Routes

internal object ActivityLinks {
    private const val OPEN = "com.furina.classtools.live.OPEN"
    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun tag(owner: String, kind: String, id: String): String =
        Uri.Builder().scheme("ct-live").authority(kind).appendPath(owner).appendPath(id).build().toString()

    fun intent(context: Context, owner: String, kind: String, id: String): Intent =
        Intent(context, MainActivity::class.java).setAction(OPEN).setData(Uri.parse(tag(owner, kind, id)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun open(context: Context, owner: String, kind: String, id: String): PendingIntent =
        PendingIntent.getActivity(context, 0, intent(context, owner, kind, id), FLAGS)

    fun hide(context: Context, owner: String, kind: String, id: String, generation: String): PendingIntent {
        val data = Uri.parse(tag(owner, kind, id)).buildUpon().appendPath(generation).build()
        return PendingIntent.getBroadcast(context, 0,
            Intent(context, HideLiveActivityReceiver::class.java).setData(data), FLAGS)
    }

    fun navigation(intent: Intent): IncomingNavigation? {
        if (intent.action != OPEN) return null
        val uri = intent.data?.takeIf { it.scheme == "ct-live" && it.pathSegments.size == 2 } ?: return null
        val (owner, id) = uri.pathSegments
        if (owner.isBlank() || id.isBlank() || id.length > 4096) return null
        val route = when (uri.authority) {
            "vote" -> Routes.voteDetail(Uri.encode(id))
            "file" -> Routes.tool("file", focus = id)
            "deck" -> Routes.pptDetail(Uri.encode(id))
            "signup" -> Routes.tool("nt", focus = id)
            "course" -> Routes.tool("tt")
            else -> return null
        }
        val account = if (owner.startsWith("debug_")) "debug:" + owner.removePrefix("debug_") else "account:" + owner
        val parent = when (uri.authority) {
            "vote" -> Routes.VOTE
            "deck" -> Routes.tool("ppt")
            "file" -> Routes.tool("file")
            "signup" -> Routes.tool("nt")
            else -> Routes.tool("tt")
        }
        return IncomingNavigation(route, id.takeIf { uri.authority == "course" }, account, activityParent = parent)
    }
}

class HideLiveActivityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.data?.takeIf { it.scheme == "ct-live" && it.pathSegments.size == 3 } ?: return
        val (owner, id, generation) = uri.pathSegments
        when (val kind = uri.authority) {
            "vote" -> VoteWatches.hide(context, owner, id, generation)
            "signup" -> SignupCountdowns.hide(context, owner, id, generation)
            "file", "deck" -> (context.applicationContext as ClassToolsApp).container.transferActivities
                .hide(owner, kind, id, generation)
        }
    }
}
