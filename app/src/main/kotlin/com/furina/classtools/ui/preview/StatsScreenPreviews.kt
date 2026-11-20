package com.furina.classtools.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.furina.classtools.data.visits.VisitRange
import com.furina.classtools.data.visits.VisitRow
import com.furina.classtools.ui.screens.stats.StatsScreen
import com.furina.classtools.ui.theme.ClassToolsTheme

private val statsPreviewRows = listOf(
    VisitRow(
        "2026-11-18-12", "9000000003", "王五", 1_794_974_400_000, "win",
        ver = "0.1.0", dev = "Windows 11 · x64",
    ),
    VisitRow(
        "2026-11-18-11", "9000000001", "张三", 1_794_971_100_000, "android",
        ver = "1.0.0-beta14", dev = "Xiaomi 23127PN0CC · Android 15",
    ),
    VisitRow(
        "2026-11-18-10", "9000000001", "张三", 1_794_969_720_000, "web",
        ver = "2.0.0", dev = "Windows · Edge 131",
    ),
    VisitRow(
        "2026-11-18-09", "9000000002", "李四", 1_794_964_020_000, "mp",
        ver = "", dev = "Xiaomi 14 · Android 15",
    ),
    VisitRow(
        "2026-11-18-08", "9000000004", "赵六", 1_794_961_800_000, "harmony",
        ver = "1.0.8", dev = "Mate 60 · HarmonyOS 5.0.0",
    ),
    VisitRow(
        "2026-11-17-21", "9000000001", "张三", 1_794_876_600_000, "web",
        ver = "2.0.0", dev = "Windows · Edge 131",
    ),
    VisitRow(
        "2026-11-17-20", "9000000002", "李四", 1_794_873_000_000, "mp",
        ver = "", dev = "Xiaomi 14 · Android 15",
    ),
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

@Preview(name = "统计 · 管理员（近 7 天）", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun StatsScreenWeekPreview() {
    ClassToolsTheme(darkTheme = false) {
        StatsScreen(
            onBack = {},
            isAdmin = true,
            repository = null,
            skipLoad = true,
            initialRows = statsPreviewRows,
            initialRange = VisitRange.WEEK,
        )
    }
}

@Preview(name = "统计 · 平板双栏（近 7 天）", widthDp = 800, heightDp = 1000, showBackground = true)
@Composable
private fun StatsScreenWidePreview() {
    ClassToolsTheme(darkTheme = false) {
        StatsScreen(
            onBack = {},
            isAdmin = true,
            repository = null,
            skipLoad = true,
            initialRows = statsPreviewRows,
            initialRange = VisitRange.WEEK,
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
