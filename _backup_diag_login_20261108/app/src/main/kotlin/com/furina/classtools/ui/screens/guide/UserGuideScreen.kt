package com.furina.classtools.ui.screens.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.components.CtTopBar
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.theme.CtShapeTokens

/**
 * 使用说明（对齐小程序 `pages/ug` 的分节内容）。
 *
 * 纯静态内容，**零网络请求、零外部链接**（不引入任何 URL，避免审核/遥测风险）。
 * 分节列表 + 编号气泡；小屏单列，宽屏内容限宽 760dp 居中（平板可读性）。
 *
 * 接线（Lead）：
 * ```kotlin
 * "guide" -> UserGuideScreen(onBack = { navController.popBackStack() })
 * // 我的页入口：SettingActionRow(label = "使用说明", actionText = "查看", onAction = { nav.navigate("guide") })
 * ```
 */
@Composable
fun UserGuideScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            CtTopBar(
                title = "使用说明",
                subtitle = "各功能用法速览",
                onBack = onBack,
            )
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "intro") {
                        IntroCard()
                    }
                    itemsIndexed(
                        items = UserGuideContent.sections,
                        key = { _, section -> section.id },
                    ) { index, section ->
                        GuideSectionCard(index = index + 1, section = section)
                    }
                }
            }
        }
    }
}

/** 使用说明分节（纯数据，可测试内容完整性）。 */
data class GuideSection(
    val id: String,
    val title: String,
    val body: String,
)

/**
 * 使用说明内容（只读静态；与 Android 当前页面一一对应）。
 *
 * 口径：不写小程序/网页专属路径；Android 没有的功能不写；新增原生能力（提醒/日历/权限）单独成节。
 */
object UserGuideContent {

    val sections: List<GuideSection> = listOf(
        GuideSection(
            id = "identity",
            title = "身份核验",
            body = "首次打开先输入「姓名 + 学号」，与班级名单匹配后进入工具站；换设备需重新核验。" +
                "身份只在本机保存 29 天，过期或 token 失效时回到身份页重新验证。",
        ),
        GuideSection(
            id = "home",
            title = "工作台",
            body = "首页只放「今天要做什么」：今日课程与下一节、宿舍电费、作业待交、通知待处理、常用工具。" +
                "点「编辑首页」可勾选最多 4 个常用工具；右上「全部 ›」进入工具目录。",
        ),
        GuideSection(
            id = "draw",
            title = "抽签（三步）",
            body = "第一步选范围、排除序号、参与名单；第二步可「用此结果分组」；第三步结果单支持剔除重抽、复制/导出图片。" +
                "返回上一页会保留当前步骤，中断后可继续。",
        ),
        GuideSection(
            id = "group",
            title = "分组",
            body = "成员可来自上一步抽签序号或班级名单；设置组数与每组人数后打乱分组，结果可导出图片或再抽签。",
        ),
        GuideSection(
            id = "roster",
            title = "名册 / 点名",
            body = "名册按座号展示班级成员与班委角色；勾选成员加入点名名单，点名页切换「到 / 假」并可重置本轮。" +
                "点名状态随账号同步，换设备可恢复。",
        ),
        GuideSection(
            id = "hw",
            title = "作业",
            body = "作业板分「未完成 / 已完成」，勾选完成会同步到云端；支持按截止时间排序与查看即将截止项。" +
                "在「我的 → 提醒设置」可开启作业截止提醒。",
        ),
        GuideSection(
            id = "nt",
            title = "班级通知",
            body = "通知按简报 / 筛选展示，未过期自动展开；长文可查看附件图片与文件。已处理状态随账号同步，" +
                "管理员可置顶或编辑通知。",
        ),
        GuideSection(
            id = "tt",
            title = "课表",
            body = "按周查看课表，可切换周/日视图与上一周/下一周；无课表缓存时可下拉刷新。" +
                "在课表页可把本周课程写入系统日历（需日历权限）。",
        ),
        GuideSection(
            id = "fit",
            title = "体测计算器",
            body = "按性别 / 年级录入各项成绩，按国标计算得分与等级；历史记录最多保存 2 条，可查看国标评分表与导出图片。",
        ),
        GuideSection(
            id = "power",
            title = "宿舍电费",
            body = "选择关注宿舍后可看余额与 24 小时 / 7 天 / 30 天趋势；点击或拖动趋势查看采样点详情。" +
                "13/16 号楼为日更数据，会标注「数据更新较慢 / 参考」。",
        ),
        GuideSection(
            id = "water",
            title = "水量统计",
            body = "选择关注宿舍与月份，录入 4 人间 / 6 人间抄表读数；超出免费额度按水费折算扣电，历史记录可删除重录。",
        ),
        GuideSection(
            id = "reminder",
            title = "提醒设置",
            body = "「我的 → 提醒设置」可开启作业提醒（每日截止汇总 + 提前 0/1/3 天）与上课提醒（提前 5/10/15 分钟）。" +
                "首次开启需允许通知权限；系统限制下提醒可能有几分钟延迟。",
        ),
        GuideSection(
            id = "calendar",
            title = "写日程",
            body = "课表 / 通知页可「写日程」到系统日历；已授予日历权限时直接写入并提前 10 分钟提醒，" +
                "未授权时会打开系统日历让你确认保存。",
        ),
        GuideSection(
            id = "permission",
            title = "权限管理",
            body = "「我的 → 权限与提醒」查看通知 / 日历权限状态；被永久拒绝时需到系统设置中重新开启。" +
                "权限只用于本机功能，不会上传通知或日历内容。",
        ),
        GuideSection(
            id = "sort",
            title = "全部工具与排序",
            body = "全部工具页长按工具可拖动排序，顺序随账号同步；顶部常用工具由首页「编辑首页」勾选。",
        ),
        GuideSection(
            id = "feedback",
            title = "反馈建议",
            body = "「我的 → 反馈建议」把问题或建议发给管理员；正文最多 500 字，不收集联系方式。" +
                "普通用户只看自己的反馈，管理员可标记已处理。",
        ),
        GuideSection(
            id = "privacy",
            title = "数据与隐私",
            body = "作业完成 / 通知已处理 / 抽签 / 分组 / 点名 / 偏好随账号同步，云端仅本人 token 可读；" +
                "在「数据与隐私」可导出我的数据（JSON，不含 token 与学号明文）、清除云端或本机数据。",
        ),
        GuideSection(
            id = "faq",
            title = "常见问题",
            body = "① 打不开/空白：检查网络与身份是否过期；② 数据不一致：到「数据与隐私」手动同步或下拉刷新；" +
                "③ 提醒不响：检查系统通知权限与电量优化；④ 换设备：重新验证身份后同步；⑤ 其他问题：用反馈建议联系管理员。",
        ),
    )

    /** 内容版本（内容较上一版有实质变化时递增；供设置页/关于页展示）。 */
    const val VERSION: String = "2026-11-08.1"

    fun sectionOf(id: String): GuideSection? = sections.firstOrNull { it.id == id }
}

@Composable
private fun IntroCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp),
        shape = CtShapeTokens.toolCard,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "班级工具站 · 使用说明",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "按下面的分节快速了解每个功能；所有数据仅班级身份可访问。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun GuideSectionCard(index: Int, section: GuideSection) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp),
        shape = CtShapeTokens.listItem,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            Surface(
                shape = CtShapeTokens.pill,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = section.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


@Suppress("unused")
private val unusedGuideVersion = UserGuideContent.VERSION
