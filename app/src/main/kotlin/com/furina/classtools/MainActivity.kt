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
import com.furina.classtools.ui.platform.VolumeKeyBridge
import com.furina.classtools.ui.platform.enableClassToolsEdgeToEdge

class MainActivity : ComponentActivity() {

    /**
     * 通知点击带入的工具 id（hw/tt）。未登录时先停在身份页，登录后由 MainNavHost 消费跳转。
     * 状态放 Activity，旋转/重组不重复跳转；消费后清空。
     */
    private val pendingToolId = mutableStateOf<String?>(null)

    /**
     * OPPO PUSH 通知点击带入的通知 id（服务端 `actionParameters={"noticeId":"<gid>"}`）。
     * 冷启动（onCreate）/ 已运行（onNewIntent）都要处理；未登录时等登录进入主界面后消费。
     */
    private val pendingNoticeId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // 全透明系统栏：避免部分 ROM（ColorOS 等）在底部工具栏下画一条黑色导航栏色带。
        enableClassToolsEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            window.decorView.post {
                com.furina.classtools.widget.DesktopWidgets.publishPickerPreviews(applicationContext)
            }
        }
        pendingToolId.value = toolIdFrom(intent)
        pendingNoticeId.value = noticeIdFrom(intent)
        val container = (application as ClassToolsApp).container
        setContent {
            // 主题/外观/背景/系统栏与 ToolActivity 共用同一宿主，保证共享元素两端视觉一致。
            ClassToolsThemeHost(container) {
                ClassToolsRoot(
                    container = container,
                    pendingToolId = pendingToolId.value,
                    onPendingToolConsumed = { pendingToolId.value = null },
                    pendingNoticeId = pendingNoticeId.value,
                    onPendingNoticeConsumed = { pendingNoticeId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        toolIdFrom(intent)?.let { pendingToolId.value = it }
        noticeIdFrom(intent)?.let { pendingNoticeId.value = it }
    }

    override fun onResume() {
        super.onResume()
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

    private fun toolIdFrom(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_VIEW &&
            intent.dataString == com.furina.classtools.data.timetable.XiaobuCourseCard.TIMETABLE_LINK
        ) "tt" else if (intent?.action == "com.furina.classtools.widget.OPEN.home") "home"
        else ReminderIntentFactory.toolIdFromExtras(intent?.getStringExtra(ReminderIntentFactory.EXTRA_TOOL_ID))?.let {
            val personalTask = intent?.action == "com.furina.classtools.widget.OPEN.hw" ||
                intent?.getStringExtra(ReminderIntentFactory.EXTRA_REMINDER_KIND) == com.furina.classtools.data.reminder.ReminderKind.PERSONAL_TASK.name
            if (it == "hw" && !personalTask) "class-homework" else it
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
