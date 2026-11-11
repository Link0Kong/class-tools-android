package com.furina.classtools.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.graphics.vector.ImageVector

/** 顶层目的地：自适应导航（compact 底部短导航 / expanded NavigationRail）。 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Workbench(Routes.HOME, "工作台", Icons.Rounded.Home),
    AllTools(Routes.ALL, "全部", Icons.Rounded.Apps),
    Profile(Routes.PROFILE, "我的", Icons.Rounded.Person),
}
