package com.furina.classtools.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.furina.classtools.data.visits.VisitRow
import com.furina.classtools.ui.screens.stats.StatsScreen
import com.furina.classtools.ui.theme.ClassToolsTheme

private val statsPreviewRows = listOf(
    VisitRow("2026-11-18-12", "9000000003", "王五", 1_794_974_400_000, "win"),
    VisitRow("2026-11-18-11", "9000000001", "张三", 1_794_971_100_000, "android"),
    VisitRow("2026-11-18-10", "9000000001", "张三", 1_794_969_720_000, "web"),
    VisitRow("2026-11-18-09", "9000000002", "李四", 1_794_964_020_000, "mp"),
    VisitRow("2026-11-18-08", "9000000004", "赵六", 1_794_961_800_000, "harmony"),
)

@Preview(name = "统计 · 管理员（今天）", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun StatsScreenAdminPreview() {
    ClassToolsTheme(darkTheme = false) {
        StatsScreen(
            onBack = {},
            isAdmin = true,
            repository = null,
            skipLoad = true,
            initialRows = statsPreviewRows,
        )
    }
}

@Preview(name = "统计 · 非管理员", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun StatsScreenDeniedPreview() {
    ClassToolsTheme(darkTheme = false) {
        StatsScreen(
            onBack = {},
            isAdmin = false,
            repository = null,
            skipLoad = true,
        )
    }
}
