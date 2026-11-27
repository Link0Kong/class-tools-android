package com.furina.classtools

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.furina.classtools.data.push.OppPushClick
import com.furina.classtools.data.reminder.ReminderIntentFactory
import com.furina.classtools.data.reminder.ReminderRuntime
import com.furina.classtools.ui.ClassToolsRoot
import com.furina.classtools.ui.ClassToolsThemeHost
import com.furina.classtools.ui.navigation.IncomingNavigation
import com.furina.classtools.ui.navigation.Routes
import com.furina.classtools.ui.navigation.navigationAccount
import com.furina.classtools.ui.platform.VolumeKeyBridge
import com.furina.classtools.ui.platform.enableClassToolsEdgeToEdge

class MainActivity : ComponentActivity() {

    private val incoming = mutableStateOf<IncomingNavigation?>(null)

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBundle(IncomingNavigation.KEY, incoming.value?.save())
        super.onSaveInstanceState(outState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 全透明系统栏：避免部分 ROM（ColorOS 等）在底部工具栏下画一条黑色导航栏色带。
        enableClassToolsEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            window.decorView.post {
                com.furina.classtools.widget.DesktopWidgets.publishPickerPreviews(applicationContext)
            }
        }
        incoming.value = if (savedInstanceState == null) routeFrom(intent) else IncomingNavigation.restore(savedInstanceState)
        val container = (application as ClassToolsApp).container
        setContent {
            // 主题/外观/背景/系统栏与 ToolActivity 共用同一宿主，保证共享元素两端视觉一致。
            ClassToolsThemeHost(container) {
                ClassToolsRoot(
                    container = container,
                    incoming = incoming.value,
                    onRouteConsumed = { id -> if (incoming.value?.id == id) incoming.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeFrom(intent)?.let { request ->
            if (incoming.value?.sameDestination(request) != true) incoming.value = request
        }
    }

    override fun onResume() {
        super.onResume()
        com.furina.classtools.data.reminder.CourseActivities.expire(applicationContext)
        ReminderRuntime.scheduleCourseAt(applicationContext, System.currentTimeMillis())
        // 访问统计：回前台补报一次（同一账号同一小时 reporter 内部去重，失败静默）。
        runCatching { (application as ClassToolsApp).container.reportVisitOnResume() }
    }

    /**
     * 音量键翻页（DESIGN §4.9）：播放器通过 [VolumeKeyBridge] 注册处理者；
     * 未注册或未消费时照常走系统音量。音量+ = 前进、音量− = 后退。
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val forward = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> true
            KeyEvent.KEYCODE_VOLUME_DOWN -> false
            else -> return super.onKeyDown(keyCode, event)
        }
        val handled = VolumeKeyBridge.handler?.invoke(forward) == true
        return handled || super.onKeyDown(keyCode, event)
    }

    private fun routeFrom(intent: Intent?): IncomingNavigation? {
        if (intent == null) return null
        com.furina.classtools.data.notify.ActivityLinks.navigation(intent)?.let { return it }
        val widgetTool = intent.action?.removePrefix(WIDGET_ACTION)?.takeIf { intent.action == WIDGET_ACTION + it }
        val focus = intent.getStringExtra(CARD_FOCUS)?.takeIf(String::isNotBlank)?.let { if (it.length <= 4096) it else "invalid" }
        val owner = (application as ClassToolsApp).container.currentSession
        val account = intent.getStringExtra(CARD_ACCOUNT) ?: owner?.navigationAccount
        fun request(route: String, courseFocus: String? = null) =
            IncomingNavigation(route, courseFocus, account, issuedAt = owner?.issuedAt)
        when (widgetTool) {
            "home" -> return request(Routes.ROOT)
            "tt" -> return request(Routes.tool("tt"), focus)
            "nt", "hw" -> return request(Routes.tool(widgetTool, focus = focus))
            "new-task" -> return request(Routes.tool("hw", createTask = true))
            "class-homework" -> return request(Routes.tool("hw", pick = true, focus = focus))
        }
        val notice = noticeIdFrom(intent)
        if (notice != null) return request(Routes.tool("nt", focus = notice))
        if (intent.action == Intent.ACTION_VIEW &&
            intent.data?.buildUpon()?.clearQuery()?.build()?.toString() == com.furina.classtools.data.timetable.XiaobuCourseCard.TIMETABLE_LINK) {
            val entity = intent.data?.getQueryParameter("activity")
            val target = entity?.let { (application as ClassToolsApp).container.xiaobuCourseSync.clickTarget(it) }
            return if (target != null) IncomingNavigation(Routes.tool("tt"), target.second, target.first, activityParent = Routes.tool("tt"))
            else request(Routes.tool("tt"), "invalid".takeIf { entity != null })
        }
        val id = ReminderIntentFactory.toolIdFromExtras(intent.getStringExtra(ReminderIntentFactory.EXTRA_TOOL_ID)) ?: return null
        if (id == "vote") return request(Routes.VOTE)
        if (id == "tt" && intent.data?.scheme == "ct-course") {
            return IncomingNavigation(Routes.tool("tt"), focus, account, activityParent = Routes.tool("tt"))
        }
        if (id == "tt") return request(Routes.tool("tt"), focus)
        val key = intent.getStringExtra(ReminderIntentFactory.EXTRA_REMINDER_KEY).orEmpty()
        val detail = ReminderIntentFactory.focusFor(key)
        return request(Routes.tool(id, pick = id == "hw" && intent.getStringExtra(ReminderIntentFactory.EXTRA_REMINDER_KIND) !=
            com.furina.classtools.data.reminder.ReminderKind.PERSONAL_TASK.name, focus = detail))
    }

    companion object {
        internal const val WIDGET_ACTION = "com.furina.classtools.widget.OPEN."
        internal const val CARD_FOCUS = "com.furina.classtools.card.FOCUS"
        internal const val CARD_ACCOUNT = "com.furina.classtools.card.ACCOUNT"
    }

    /**
     * OPPO PUSH 点击参数解析：优先 intent extra `noticeId`，否则解析 extra `actionParameters` JSON。
     * 解析规则与目标路由见 [OppPushClick]（纯逻辑，单测覆盖）。
     */
    private fun noticeIdFrom(intent: Intent?): String? = OppPushClick.noticeIdFrom(
        actionParametersJson = intent?.getStringExtra(OppPushClick.EXTRA_ACTION_PARAMETERS),
        directNoticeId = intent?.getStringExtra(OppPushClick.EXTRA_NOTICE_ID),
    )
}
