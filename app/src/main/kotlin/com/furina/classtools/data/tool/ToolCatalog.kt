package com.furina.classtools.data.tool

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.HowToVote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.furina.classtools.ui.theme.BrandMistTeal
import com.furina.classtools.ui.theme.BrandSlateBlue
import com.furina.classtools.ui.theme.BrandWarmSand

/** 工具分类，用于首页分组与「全部工具」页排序。 */
enum class ToolCategory(val label: String) {
    DEFAULT_QUICK("常用"),
    STUDY("学习"),
    CLASS("班级事务"),
    SYSTEM("系统"),
}

/**
 * 一个工具的原生入口定义。
 *
 * @param id 与后端/网页同步契约中的工具标识保持一致（homeTools 存的就是 id）
 * @param features 原生版计划功能（占位页展示；完成一个勾掉一个）
 */
data class AppTool(
    val id: String,
    val name: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val category: ToolCategory,
    val features: List<String> = emptyList(),
)

object ToolCatalog {

    private val teal = BrandMistTeal
    private val blue = BrandSlateBlue
    private val sand = BrandWarmSand
    private val leaf = Color(0xFF5F8F5B)
    private val plum = Color(0xFF8A6A92)
    private val clay = Color(0xFFB06A55)

    val tools: List<AppTool> = listOf(
        AppTool(
            id = "draw",
            name = "抽签",
            subtitle = "公平随机 · 可导出",
            icon = Icons.Rounded.Casino,
            accent = teal,
            category = ToolCategory.STUDY,
            features = listOf("STEP 1 配置范围与人数", "STEP 2 预览名单", "STEP 3 结果与导出卡片", "排除号 / 不重复 / 分组抽签"),
        ),
        AppTool(
            id = "group",
            name = "分组",
            subtitle = "随机分组 · 两列组卡",
            icon = Icons.Rounded.Groups,
            accent = blue,
            category = ToolCategory.CLASS,
            features = listOf("按人数或组数分组", "名单选择与去重", "导出分组卡片"),
        ),
        AppTool(
            id = "fit",
            name = "体测",
            subtitle = "录入 · 评分 · 历史",
            icon = Icons.Rounded.FitnessCenter,
            accent = leaf,
            category = ToolCategory.CLASS,
            features = listOf("项目成绩录入", "按性别/年级评分", "历史趋势与导出"),
        ),
        AppTool(
            id = "hw",
            name = "作业",
            subtitle = "截止提醒 · 完成状态",
            icon = Icons.AutoMirrored.Rounded.Assignment,
            accent = sand,
            category = ToolCategory.STUDY,
            features = listOf("作业列表与截止倒计时", "完成勾选（云端 marks 同步）", "本地截止提醒通知"),
        ),
        AppTool(
            id = "nt",
            name = "通知",
            subtitle = "班委通知 · 附件",
            icon = Icons.Rounded.Notifications,
            accent = clay,
            category = ToolCategory.STUDY,
            features = listOf("通知列表与已处理标记", "置顶与筛选", "附件/图片查看"),
        ),
        AppTool(
            id = "vote",
            name = "投票",
            subtitle = "限时/长时 · 实时结果",
            icon = Icons.Rounded.HowToVote,
            accent = plum,
            category = ToolCategory.CLASS,
            features = listOf("限时/截止/不限时", "单选/多选/匿名/可见范围", "实时结果与管理"),
        ),
        AppTool(
            id = "tt",
            name = "课表",
            subtitle = "周/日视图 · 下一节",
            icon = Icons.Rounded.CalendarMonth,
            accent = blue,
            category = ToolCategory.STUDY,
            features = listOf("周视图 / 日视图", "上一周 / 下一周", "今日课程与下一节课提醒"),
        ),
        AppTool(
            id = "power",
            name = "电费",
            subtitle = "剩余电量 · 趋势",
            icon = Icons.Rounded.Bolt,
            accent = Color(0xFFC29A3B),
            category = ToolCategory.CLASS,
            features = listOf("关注宿舍剩余电量", "24h / 7d / 30d 趋势（点击查看采样点）", "低电量提醒"),
        ),
        AppTool(
            id = "ppt",
            name = "演示",
            subtitle = "PPT 自动转换 · 分步播放",
            icon = Icons.Rounded.Slideshow,
            accent = blue,
            category = ToolCategory.CLASS,
            features = listOf(
                "上传 .ppt/.pptx 自动转换（分步图片 + 视频）",
                "沉浸式全屏播放（点击/滑动/音量键翻步）",
                "缩略图跳页 / 备注 / 下载原件",
            ),
        ),
        AppTool(
            id = "water",
            name = "水量",
            subtitle = "4/6 人间 · 月份记录",
            icon = Icons.Rounded.WaterDrop,
            accent = Color(0xFF4A88A8),
            category = ToolCategory.CLASS,
            features = listOf("按月份查看抄表", "本机录入与云端同步", "月份选择器"),
        ),
        AppTool(
            id = "roster",
            name = "名册",
            subtitle = "班级名单 · 点名",
            icon = Icons.Rounded.Badge,
            accent = plum,
            category = ToolCategory.CLASS,
            features = listOf("班级名单与座位号", "点名到/假", "名单不导出、不外发"),
        ),
        AppTool(
            id = "settings",
            name = "设置",
            subtitle = "账号 · 同步 · 关于",
            icon = Icons.Rounded.Settings,
            accent = Color(0xFF6F7A77),
            category = ToolCategory.SYSTEM,
            features = listOf("账号信息与退出", "同步开关与清除", "关于与备案信息"),
        ),
        AppTool(
            id = "diag",
            name = "诊断",
            subtitle = "服务健康 · 设备 · AI 修复",
            icon = Icons.Rounded.HealthAndSafety,
            accent = teal,
            category = ToolCategory.SYSTEM,
            features = listOf(
                "服务健康分组（脱敏展示）",
                "设备诊断（本机检查 + 重新检测）",
                "AI 分析原因/建议与修复流程时间线",
            ),
        ),
    )

    /** 默认「常用工具」：与网页首页前 4 个默认工具对齐，后续从 userdata.homeTools 读取。 */
    val defaultQuickIds: List<String> = listOf("draw", "group", "fit", "hw")

    fun byId(id: String): AppTool? = tools.firstOrNull { it.id == id }

    val allEntry: AppTool = AppTool(
        id = "all",
        name = "全部工具",
        subtitle = "查看全部原生工具",
        icon = Icons.Rounded.Apps,
        accent = BrandMistTeal,
        category = ToolCategory.SYSTEM,
    )

    fun quickTools(ids: List<String> = defaultQuickIds): List<AppTool> =
        ids.mapNotNull { byId(it) }.ifEmpty { defaultQuickIds.mapNotNull { byId(it) } }
}
