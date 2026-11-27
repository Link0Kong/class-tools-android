package com.furina.classtools

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.furina.classtools.data.applink.AppLink
import com.furina.classtools.data.applink.AppLinkResult
import com.furina.classtools.data.push.OppPushClick
import com.furina.classtools.data.reminder.ReminderIntentFactory
import com.furina.classtools.data.reminder.ReminderRuntime
import com.furina.classtools.data.timetable.CourseFocus
import com.furina.classtools.ui.ClassToolsRoot
import com.furina.classtools.ui.ClassToolsThemeHost
import com.furina.classtools.ui.components.CtToastBus
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
        fun request(route: String, courseFocus: String? = null, taskShare: String? = null) =
            IncomingNavigation(
                route = route,
                courseFocus = courseFocus,
                account = account,
                issuedAt = owner?.issuedAt,
                taskShare = taskShare,
            )
        when (widgetTool) {
            "home" -> return request(Routes.ROOT)
            "tt" -> return request(Routes.tool("tt"), focus)
            "nt", "hw" -> return request(Routes.tool(widgetTool, focus = focus))
            "new-task" -> return request(Routes.tool("hw", createTask = true))
            "class-homework" -> return request(Routes.tool("hw", pick = true, focus = focus))
        }
        /* 统一 HTTPS 内容链接（docs/app-links/CONTRACT.md）：只认 https://tool.furina.fun/l/ 下已接管的路径。
           其余（主页、API、下载、法律页、/l/file/、classtools:// 旧链接）保持原有处理。 */
        if (intent.action == Intent.ACTION_VIEW) {
            when (val link = AppLink.parse(intent.dataString)) {
                is AppLinkResult.Draw -> return request(Routes.drawVerify(link.recordId))
                is AppLinkResult.Notice -> return request(Routes.tool("nt", focus = link.noticeId))
                is AppLinkResult.Course -> {
                    val courseFocus = CourseFocus(link.date, link.start, link.end, link.name.orEmpty()).encode()
                    return request(Routes.tool("tt"), courseFocus)
                }
                is AppLinkResult.TaskShare -> {
                    // 快照在片段里；原样交给任务页预览后确认导入，不进入导航参数。
                    intent.dataString?.takeIf(String::isNotBlank)?.let { return request(Routes.tool("hw"), taskShare = it) }
                }
                is AppLinkResult.Invalid -> {
                    CtToastBus.show("内容链接无法打开：" + invalidLinkText(link.reason))
                    return null
                }
                is AppLinkResult.File -> Unit
                AppLinkResult.NotLink -> Unit
            }
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

    /** 链接被系统交给我们、但参数不合法时的提示（不导航到错误目标）。 */
    private fun invalidLinkText(reason: String): String = when (reason) {
        "bad_version" -> "链接版本不受支持，请稍后在网页打开"
        "bad_id" -> "链接里的内容标识不合法"
        "bad_date", "bad_range" -> "课程链接参数不正确"
        "bad_payload" -> "任务分享内容不完整，请在网页打开链接"
        "too_long" -> "链接过长"
        else -> "链接格式不正确"
    }
}
