package com.furina.classtools.ui.components.dock

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * M3 NavigationRail（宽度 ≥840dp 的平板/横屏使用，替代底部 NavigationBar）。
 *
 * 规范：容器 surfaceContainer、宽 80dp、item 56dp、选中 secondaryContainer 指示器；
 * 状态栏/导航栏 inset 由 Rail 自己处理。
 */
@Composable
fun CtNavigationRail(
    destinations: List<CtDockDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(80.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
    NavigationRail(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        destinations.forEachIndexed { index, destination ->
            NavigationRailItem(
                selected = index == selectedIndex,
                colors = if (com.furina.classtools.ui.theme.LocalCtDarkTheme.current) {
                    androidx.compose.material3.NavigationRailItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    )
                } else androidx.compose.material3.NavigationRailItemDefaults.colors(),
                onClick = { onSelect(index) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label,
                    )
                },
                label = { Text(destination.label) },
            )
        }
    }
    }
}


