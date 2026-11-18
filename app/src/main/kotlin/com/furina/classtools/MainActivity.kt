package com.furina.classtools

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.furina.classtools.data.push.OppPushClick
import com.furina.classtools.data.reminder.ReminderIntentFactory
import com.furina.classtools.data.settings.LocalAppSettingsStore
import com.furina.classtools.ui.ClassToolsRoot
import com.furina.classtools.ui.platform.VolumeKeyBridge
import com.furina.classtools.ui.theme.ClassToolsTheme
import com.furina.classtools.ui.theme.CtImmersiveStatusBar

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
        // navigationBarStyle 使用 auto 仅控制图标深/浅，颜色始终透明；再关闭系统对比度蒙层。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        pendingToolId.value = toolIdFrom(intent)
        pendingNoticeId.value = noticeIdFrom(intent)
        val container = (application as ClassToolsApp).container
        setContent {
            // 外观偏好存本机（不进 userdata）：壁纸取色 / 外观模式 / 隐藏状态栏均实时生效。
            val dynamicColor by container.appSettings.dynamicColor
                .collectAsStateWithLifecycle(initialValue = false)
            // 首帧用 SharedPreferences 镜像同步初始化，避免冷启动先按系统/默认渲染一帧再跳变。
            val initialAppearanceMode = remember { container.appSettings.startupAppearanceMode() }
            val initialHideStatusBar = remember { container.appSettings.startupHideStatusBar() }
            val appearanceMode by container.appSettings.appearanceMode
                .collectAsStateWithLifecycle(initialValue = initialAppearanceMode)
            val hideStatusBar by container.appSettings.hideStatusBar
                .collectAsStateWithLifecycle(initialValue = initialHideStatusBar)
            // 外观模式：跟随系统 / 强制浅色 / 强制深色（我的 → 外观 三选一）。
            val darkTheme = appearanceMode.resolveDark(isSystemInDarkTheme())
            val pendingTool by pendingToolId
            val pendingNotice by pendingNoticeId
            ClassToolsTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                // 「我的 → 外观」直接读写本机设置（MainNavHost 之外的新设置项，不改导航层）。
                CompositionLocalProvider(LocalAppSettingsStore provides container.appSettings) {
                    // 全局隐藏系统状态栏（下滑临时显示）；关闭时恢复 statusBarsPadding 的常规行为。
                    CtImmersiveStatusBar(enabled = hideStatusBar) {
                        ClassToolsRoot(
                            container = container,
                            pendingToolId = pendingTool,
                            onPendingToolConsumed = { pendingToolId.value = null },
                            pendingNoticeId = pendingNotice,
                            onPendingNoticeConsumed = { pendingNoticeId.value = null },
                        )
                    }
                }
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

    private fun toolIdFrom(intent: Intent?): String? = ReminderIntentFactory.toolIdFromExtras(
        intent?.getStringExtra(ReminderIntentFactory.EXTRA_TOOL_ID),
    )

    /**
     * OPPO PUSH 点击参数解析：优先 intent extra `noticeId`，否则解析 extra `actionParameters` JSON。
     * 解析规则与目标路由见 [OppPushClick]（纯逻辑，单测覆盖）。
     */
    private fun noticeIdFrom(intent: Intent?): String? = OppPushClick.noticeIdFrom(
        actionParametersJson = intent?.getStringExtra(OppPushClick.EXTRA_ACTION_PARAMETERS),
        directNoticeId = intent?.getStringExtra(OppPushClick.EXTRA_NOTICE_ID),
    )
}
