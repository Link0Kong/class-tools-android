package com.furina.classtools

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.furina.classtools.data.reminder.ReminderIntentFactory
import com.furina.classtools.ui.ClassToolsRoot
import com.furina.classtools.ui.theme.ClassToolsTheme

class MainActivity : ComponentActivity() {

    /**
     * 通知点击带入的工具 id（hw/tt）。未登录时先停在身份页，登录后由 MainNavHost 消费跳转。
     * 状态放 Activity，旋转/重组不重复跳转；消费后清空。
     */
    private val pendingToolId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingToolId.value = toolIdFrom(intent)
        val container = (application as ClassToolsApp).container
        setContent {
            // 外观偏好存本机（不进 userdata）：壁纸取色开关实时生效。
            val dynamicColor by container.appSettings.dynamicColor
                .collectAsStateWithLifecycle(initialValue = false)
            val pendingTool by pendingToolId
            ClassToolsTheme(dynamicColor = dynamicColor) {
                ClassToolsRoot(
                    container = container,
                    pendingToolId = pendingTool,
                    onPendingToolConsumed = { pendingToolId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        toolIdFrom(intent)?.let { pendingToolId.value = it }
    }

    private fun toolIdFrom(intent: Intent?): String? = ReminderIntentFactory.toolIdFromExtras(
        intent?.getStringExtra(ReminderIntentFactory.EXTRA_TOOL_ID),
    )
}
