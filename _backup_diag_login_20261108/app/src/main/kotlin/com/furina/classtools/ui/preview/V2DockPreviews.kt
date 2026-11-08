package com.furina.classtools.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.tool.ToolCatalog
import com.furina.classtools.ui.components.dock.CtDockDestination
import com.furina.classtools.ui.components.dock.CtFloatingDock
import com.furina.classtools.ui.theme.ClassToolsTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person

/**
 * v2.0.0 ColorOS 17 浮岛底栏预览。
 *
 * 覆盖：浅色 / 深色 / 跟手滑动中（药丸拉伸）/ 平板限宽 / 第三项选中。
 */
private val previewDockDestinations = listOf(
    CtDockDestination("home", "工作台", Icons.Rounded.Home),
    CtDockDestination("all", "工具", Icons.Rounded.Apps),
    CtDockDestination("profile", "我的", Icons.Rounded.Person),
)

@Composable
private fun DockPreviewFrame(
    selectedIndex: Int,
    offsetFraction: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Text(
            text = "浮岛底栏预览 · 内容区",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(20.dp),
        )
        CtFloatingDock(
            destinations = previewDockDestinations,
            selectedIndex = selectedIndex,
            pagerOffsetFraction = offsetFraction,
            onSelect = {},
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Preview(name = "60 浮岛底栏 · 浅色", showBackground = true, widthDp = 412, heightDp = 240)
@Composable
private fun DockLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        DockPreviewFrame(selectedIndex = 0, offsetFraction = 0f)
    }
}

@Preview(name = "61 浮岛底栏 · 深色", showBackground = true, widthDp = 412, heightDp = 240)
@Composable
private fun DockDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        DockPreviewFrame(selectedIndex = 2, offsetFraction = 0f)
    }
}

@Preview(name = "62 底栏 · 滑动中（药丸跟手拉伸）", showBackground = true, widthDp = 412, heightDp = 240)
@Composable
private fun DockMidSwipePreview() {
    ClassToolsTheme(darkTheme = false) {
        DockPreviewFrame(selectedIndex = 0, offsetFraction = 0.42f)
    }
}

@Preview(name = "63 底栏 · 滑动中（深色）", showBackground = true, widthDp = 412, heightDp = 240)
@Composable
private fun DockMidSwipeDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        DockPreviewFrame(selectedIndex = 1, offsetFraction = -0.38f)
    }
}

@Preview(name = "64 底栏 · 平板限宽", showBackground = true, widthDp = 1000, heightDp = 260)
@Composable
private fun DockTabletPreview() {
    ClassToolsTheme(darkTheme = false) {
        DockPreviewFrame(selectedIndex = 1, offsetFraction = 0f)
    }
}

@Preview(name = "65 底栏 · 第二项选中", showBackground = true, widthDp = 412, heightDp = 240)
@Composable
private fun DockSecondSelectedPreview() {
    ClassToolsTheme(darkTheme = false) {
        DockPreviewFrame(selectedIndex = 1, offsetFraction = 0f)
    }
}

@Suppress("unused")
private val dockPreviewToolCount = ToolCatalog.tools.size
