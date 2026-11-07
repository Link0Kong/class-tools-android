package com.furina.classtools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.furina.classtools.ui.ClassToolsRoot
import com.furina.classtools.ui.theme.ClassToolsTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as ClassToolsApp).container
        setContent {
            // 外观偏好存本机（不进 userdata）：壁纸取色开关实时生效。
            val dynamicColor by container.appSettings.dynamicColor
                .collectAsStateWithLifecycle(initialValue = false)
            ClassToolsTheme(dynamicColor = dynamicColor) {
                ClassToolsRoot(container = container)
            }
        }
    }
}
