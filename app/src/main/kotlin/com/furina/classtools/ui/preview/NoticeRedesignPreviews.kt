package com.furina.classtools.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.marks.MarksSnapshot
import com.furina.classtools.data.notices.ManualNotice
import com.furina.classtools.data.notices.MergedMessage
import com.furina.classtools.data.notices.NoticeEntry
import com.furina.classtools.data.notices.NoticeGroupDto
import com.furina.classtools.data.notices.NoticeItemMeta
import com.furina.classtools.data.notices.NoticeManualAttachment
import com.furina.classtools.data.notices.NoticeRecurrenceDto
import com.furina.classtools.data.notices.NoticeRow
import com.furina.classtools.data.notices.NoticeSegment
import com.furina.classtools.data.notices.NoticesData
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.screens.notices.NoticeDetailSheetContent
import com.furina.classtools.ui.screens.notices.NoticeFilter
import com.furina.classtools.ui.screens.notices.NoticeRawAttachmentLoader
import com.furina.classtools.ui.screens.notices.NoticeUiItem
import com.furina.classtools.ui.screens.notices.NoticesScreen
import com.furina.classtools.ui.screens.notices.buildUiItems
import com.furina.classtools.ui.theme.ClassToolsTheme
import com.furina.classtools.ui.theme.CtShapeTokens
import java.time.LocalDateTime
import java.time.ZoneId

// =============================================================================
// 通知界面重做预览（task-40；以小程序 pages/nt 为基准）
//
// 180 列表 · 浅色 / 181 列表 · 深色 / 182 筛选「未处理」/ 183「已过期」展开
// 184 空态 / 185 错误态 / 186 详情弹层 · 自动通知 / 187 详情弹层 · 人工通知 · 深色
// 188 大字体 1.3x / 189 平板 800dp / 190 离线缓存 / 191 搜索无结果 / 192 加载骨架
//
// 全部使用注入数据（skipLoad），不发网络请求、不读真实账号。
//
// task-43 动效预览（新增 195–201）：
// 195 列表错峰入场帧 / 196 卡片按压态 · 深色 / 197 筛选 chips 弹性帧 / 198 详情延伸帧
// 199 搜索展开 / 200 大字体 1.3x 按压态 / 201 平板 800dp 错峰入场
// =============================================================================

/** 预览用 1x1 透明 PNG（与 NoticeAttachmentDragPreviews 同源），验证图片附件缩略图链路。 */
private const val PREVIEW_PNG_DATA_URI: String =
    "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAGAAAABACAYAAADlNHIOAAABRUlEQVR42u3coW4CQRCA4WXDs2GrSGUNAtequiocqnUIDJKgavtixbWKhFyA291wub3j+zWIm39nduaSm8nv8fgX0BtRCAggAAQQgH6YlvzpebMWuSvsl+9Zv5/ktKECf38RyQKawf9+/RDlBk9fq2wJSQLOgy/weSLaJLQKOAVf4MtF3JIQ1fzuuRXHpDbU6S8jJW7R6e83C6LT328WmISHOAlf6nnHVpuDd0HByzgQQAAI0AWZD2QAASCAABCgCzqxOxxEr8HLfC4DlCAQ8BB3QEm9gwwgAASYAx6pz5cBShAIMAdABhAAAsZ0B/x8Lqp9qNnbVgaAAALQ4R0wpDorA0DAYAUM+VOkUOH6gmQBuVs/EIqWd8Sxf5BX8+lvFSALul9dY1tK7dtS7AuqYF+Q/REVbMwiooKdcTAJEwACCMD9+Ad+mXqGYQC4eQAAAABJRU5ErkJggg=="

private val previewAttachmentLoader = NoticeRawAttachmentLoader { _, ordinal, _ ->
    if (ordinal == 0) PREVIEW_PNG_DATA_URI else null
}

private fun previewNow(): Long = System.currentTimeMillis()

private fun deadlineText(daysFromNow: Long): String =
    LocalDateTime.now(ZoneId.systemDefault()).plusDays(daysFromNow).withNano(0).toString()

private fun deadlineHours(hoursFromNow: Long): String =
    LocalDateTime.now(ZoneId.systemDefault()).plusHours(hoursFromNow).withNano(0).toString()

private fun previewNoticesData(fromCache: Boolean = false): NoticesData {
    val now = previewNow()
    val meetings = NoticeEntry(
        row = NoticeRow(id = "auto_g_meeting", title = "关于 11 月主题班会的通知", ts = now - 25 * 60_000L),
        group = NoticeGroupDto(
            id = "g_meeting",
            title = "关于 11 月主题班会的通知",
            cat = "团务",
            source = "班级通知群",
            senderName = "陈同学",
            senderRole = "副团支部书记",
            aiSummary = "本周五下午 4 点在 01104 教室召开主题班会，请全体同学准时参加并签到。",
            aiPoints = listOf("时间：周五 16:00", "地点：01104", "需要签到"),
            aiSegments = listOf(
                NoticeSegment(type = "时间", text = "本周五 16:00"),
                NoticeSegment(type = "地点", text = "自强楼 01104"),
                NoticeSegment(type = "要求", text = "全体参加，现场签到"),
            ),
            deadline = deadlineText(2),
            deadlineAction = "开始",
            timeLabel = "11-14 16:00 开始",
            mergedSources = listOf("微信·班级通知群", "QQ·班级群"),
        ),
        items = listOf(
            NoticeItemMeta.Text(
                kind = "text",
                label = "原始消息",
                fromName = "陈同学",
                fromRole = "副团支部书记",
                timeText = "11-12 20:10",
                text = "请大家准时参加班会，记得签到。地点在自强楼 01104。",
                isFollow = false,
            ),
            NoticeItemMeta.Image(
                kind = "img",
                caption = "班会教室位置",
                ocr = false,
                hasData = true,
                approxBytes = 24_000L,
            ),
            NoticeItemMeta.File(
                kind = "file",
                name = "班会流程.pdf",
                ext = "pdf",
                sizeText = "320 KB",
                note = "",
                hasContent = true,
                pending = false,
                archiveOk = false,
                archiveCount = 0,
                archiveSummary = "",
                url = "https://example.com/notice/meeting.pdf",
            ),
            NoticeItemMeta.Link(
                kind = "link",
                name = "班会签到表",
                title = "班会签到表",
                account = "",
                host = "docs.qq.com",
                hasUrl = true,
            ),
            NoticeItemMeta.Merged(
                kind = "merged",
                name = "班委群讨论",
                count = 3,
                messages = listOf(
                    MergedMessage(
                        fromName = "陈同学",
                        fromBadge = "副团支部书记",
                        timeText = "11-12 20:12",
                        text = "投影仪已确认可用，提前 10 分钟到教室。",
                    ),
                    MergedMessage(
                        fromName = "刘一诺",
                        fromBadge = "生活委员",
                        timeText = "11-12 20:18",
                        text = "班费买水，班会结束一起发。",
                    ),
                ),
            ),
        ),
        hidden = false,
        edited = true,
    )
    val teaching = NoticeEntry(
        row = NoticeRow(id = "auto_g_teaching", title = "期中教学检查材料提交", ts = now - 3 * 3_600_000L),
        group = NoticeGroupDto(
            id = "g_teaching",
            title = "期中教学检查材料提交",
            cat = "教务",
            source = "教务通知群",
            senderRole = "学习委员",
            senderName = "王思远",
            aiSummary = "第 10-11 周开展期中教学检查，请学习委员提前整理考勤与作业归档材料。",
            aiPoints = listOf("截止：本周日 18:00", "材料：考勤表 + 作业归档"),
            deadline = deadlineText(3),
            deadlineAction = "提交",
        ),
        items = listOf(
            NoticeItemMeta.Text(
                kind = "text",
                label = "原始消息",
                fromName = "辅导员",
                fromRole = "老师",
                timeText = "11-12 09:00",
                text = "请各班学习委员整理好期中教学检查材料，周日 18:00 前提交。",
                isFollow = false,
            ),
        ),
        hidden = false,
        edited = false,
    )
    val exam = NoticeEntry(
        row = NoticeRow(id = "auto_g_exam", title = "高等数学期中考试安排", ts = now - 6 * 3_600_000L),
        group = NoticeGroupDto(
            id = "g_exam",
            title = "高等数学期中考试安排",
            cat = "考试",
            source = "班级通知群",
            senderRole = "学习委员",
            senderName = "王思远",
            aiSummary = "高数期中考试定于本周四 14:00 在 03201 教室，请带好学生证与计算器。",
            aiSegments = listOf(
                NoticeSegment(type = "时间", text = "本周四 14:00-16:00"),
                NoticeSegment(type = "地点", text = "自强楼 03201"),
            ),
            eventAt = deadlineHours(10),
            eventAction = "考试开始",
        ),
        items = listOf(
            NoticeItemMeta.Text(
                kind = "text",
                label = "原始消息",
                fromName = "数学老师",
                fromRole = "老师",
                timeText = "11-11 16:40",
                text = "高数期中考试时间确定：本周四 14:00，地点 03201。带学生证。",
                isFollow = false,
            ),
        ),
        hidden = false,
        edited = false,
    )
    val pay = NoticeEntry(
        row = NoticeRow(id = "auto_g_pay", title = "班费收缴（11 月）", ts = now - 30 * 3_600_000L),
        group = NoticeGroupDto(
            id = "g_pay",
            title = "班费收缴（11 月）",
            cat = "缴费",
            source = "班级通知群",
            senderRole = "生活委员",
            senderName = "刘一诺",
            aiSummary = "本月班费每人 30 元，请在本周内转给生活委员并备注姓名。",
            deadline = deadlineText(-1),
            deadlineAction = "缴费截止",
        ),
        items = emptyList(),
        hidden = false,
        edited = false,
    )
    val recurring = NoticeEntry(
        row = NoticeRow(id = "auto_g_recur", title = "青年大学习（长期安排）", ts = now - 50 * 3_600_000L),
        group = NoticeGroupDto(
            id = "g_recur",
            title = "青年大学习（长期安排）",
            cat = "团务",
            source = "团员通知群",
            senderRole = "团支书",
            senderName = "陈同学",
            aiSummary = "每周一 19:00 前完成青年大学习并截图打卡。",
            recurrence = NoticeRecurrenceDto(freq = "weekly", weekday = 1, time = "19:00", label = "每周一 19:00"),
        ),
        items = emptyList(),
        hidden = false,
        edited = false,
    )
    val manual = ManualNotice(
        row = NoticeRow(
            id = "manual_1",
            title = "班费收支公示（11 月）",
            body = "本月班费支出见附件：主题班会物料 86 元、班级打印 24 元、饮用水 40 元；结余 1,204 元。有疑问请私聊生活委员。",
            ts = now - 90 * 60_000L,
        ),
        attachments = listOf(
            NoticeManualAttachment(
                index = 0,
                name = "班费收支明细.xlsx",
                mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                sizeText = "28 KB",
                hasData = false,
                url = "https://example.com/notice/fee.xlsx",
            ),
        ),
    )
    val manualPhoto = ManualNotice(
        row = NoticeRow(
            id = "manual_2",
            title = "主题班会照片",
            body = "班会现场照片已上传，需要的同学自取。",
            ts = now - 120 * 60_000L,
        ),
        attachments = listOf(
            NoticeManualAttachment(
                index = 0,
                name = "班会现场.png",
                mime = "image/png",
                sizeText = "1.2 MB",
                hasData = true,
            ),
        ),
    )
    return NoticesData(
        auto = listOf(meetings, teaching, exam, pay, recurring),
        manual = listOf(manual, manualPhoto),
        hiddenCount = 0,
        fetchedAt = if (fromCache) now - 20 * 60_000L else now,
        fromCache = fromCache,
    )
}

/** 「已过期」分组展开预览专用：1 条进行中 + 1 条已过期（静态预览可见折叠/展开差异）。 */
private fun previewNoticesDataExpiredMixed(): NoticesData {
    val now = previewNow()
    val active = NoticeEntry(
        row = NoticeRow(id = "auto_g_active", title = "本周班会议程征集", ts = now - 40 * 60_000L),
        group = NoticeGroupDto(
            id = "g_active",
            title = "本周班会议程征集",
            cat = "团务",
            source = "班级通知群",
            senderRole = "班长",
            senderName = "周雨桐",
            aiSummary = "本周班会议程开放征集，有想讨论的议题请在今晚 22:00 前发到班委群。",
            deadline = deadlineText(2),
            deadlineAction = "征集截止",
        ),
        items = emptyList(),
        hidden = false,
        edited = false,
    )
    val expired = NoticeEntry(
        row = NoticeRow(id = "auto_g_expired", title = "国庆假期返校登记", ts = now - 8 * 86_400_000L),
        group = NoticeGroupDto(
            id = "g_expired",
            title = "国庆假期返校登记",
            cat = "教务",
            source = "教务通知群",
            senderRole = "辅导员",
            senderName = "李老师",
            aiSummary = "国庆假期返校登记已截止，未登记的同学已由班长代为补录。",
            deadline = deadlineText(-6),
            deadlineAction = "登记截止",
        ),
        items = emptyList(),
        hidden = false,
        edited = false,
    )
    return NoticesData(
        auto = listOf(active, expired),
        manual = emptyList(),
        hiddenCount = 0,
        fetchedAt = now,
        fromCache = false,
    )
}

private fun previewMarks(): MarksSnapshot = MarksSnapshot(
    ntDone = mapOf("g_teaching" to true),
    ntPin = mapOf("manual_1" to true),
)

private fun detailItem(title: String): NoticeUiItem {
    val items = buildUiItems(previewNoticesData(), previewMarks(), previewNow())
    return items.first { it.title == title }
}

// -----------------------------------------------------------------------------
// 列表 / 状态
// -----------------------------------------------------------------------------

@Preview(name = "180 通知重做 · 列表 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun NoticeRedesignListLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        NoticesScreen(
            onBack = {},
            session = null,
            marksRepository = null,
            noticesRepository = null,
            initialData = previewNoticesData(),
            initialMarks = previewMarks(),
            skipLoad = true,
        )
    }
}

@Preview(name = "181 通知重做 · 列表 深色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun NoticeRedesignListDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        NoticesScreen(
            onBack = {},
            session = null,
            marksRepository = null,
            noticesRepository = null,
            initialData = previewNoticesData(),
            initialMarks = previewMarks(),
            skipLoad = true,
        )
    }
}

@Preview(name = "182 通知重做 · 筛选未处理", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun NoticeRedesignPendingFilterPreview() {
    ClassToolsTheme(darkTheme = false) {
        NoticesScreen(
            onBack = {},
            session = null,
            marksRepository = null,
            noticesRepository = null,
            initialData = previewNoticesData(),
            initialMarks = previewMarks(),
            initialFilter = NoticeFilter.PENDING,
            skipLoad = true,
        )
    }
}

@Preview(name = "183 通知重做 · 已过期展开", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun NoticeRedesignExpiredExpandedPreview() {
    ClassToolsTheme(darkTheme = false) {
        NoticesScreen(
            onBack = {},
            session = null,
            marksRepository = null,
            noticesRepository = null,
            initialData = previewNoticesDataExpiredMixed(),
            initialMarks = MarksSnapshot(),
            initialExpiredExpanded = true,
            skipLoad = true,
        )
    }
}

@Preview(name = "184 通知重做 · 空态", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun NoticeRedesignEmptyPreview() {
    ClassToolsTheme(darkTheme = true) {
        NoticesScreen(
            onBack = {},
            session = null,
            marksRepository = null,
            noticesRepository = null,
            initialData = NoticesData(
                auto = emptyList(),
                manual = emptyList(),
                hiddenCount = 0,
                fetchedAt = previewNow(),
                fromCache = false,
            ),
            initialMarks = MarksSnapshot(),
            skipLoad = true,
        )
    }
}

@Preview(name = "185 通知重做 · 错误态", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun NoticeRedesignErrorPreview() {
    ClassToolsTheme(darkTheme = false) {
        NoticesScreen(
            onBack = {},
            session = null,
            marksRepository = null,
            noticesRepository = null,
            forcedErrorMessage = "网络不可用，请稍后重试",
            skipLoad = true,
        )
    }
}

@Preview(name = "190 通知重做 · 离线缓存", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun NoticeRedesignOfflinePreview() {
    ClassToolsTheme(darkTheme = false) {
        NoticesScreen(
            onBack = {},
            session = null,
            marksRepository = null,
            noticesRepository = null,
            initialData = previewNoticesData(fromCache = true),
            initialMarks = previewMarks(),
            skipLoad = true,
        )
    }
}

@Preview(name = "191 通知重做 · 搜索无结果", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun NoticeRedesignSearchEmptyPreview() {
    ClassToolsTheme(darkTheme = false) {
        NoticesScreen(
            onBack = {},
            session = null,
            marksRepository = null,
            noticesRepository = null,
            initialData = previewNoticesData(),
            initialMarks = previewMarks(),
            initialQuery = "不存在的通知",
            skipLoad = true,
        )
    }
}

@Preview(name = "192 通知重做 · 加载骨架", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun NoticeRedesignLoadingPreview() {
    ClassToolsTheme(darkTheme = false) {
        NoticesScreen(
            onBack = {},
            session = null,
            marksRepository = null,
            noticesRepository = null,
            initialLoading = true,
        )
    }
}

// -----------------------------------------------------------------------------
// 详情弹层（静态内联渲染；真实页面用 CtBottomSheet 承载同一内容）
// -----------------------------------------------------------------------------

@Composable
private fun PreviewDetailSheet(
    item: NoticeUiItem,
    includeCalendarAndReminder: Boolean = true,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 28.dp),
        shape = CtShapeTokens.sheet,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            CircleShape,
                        ),
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                NoticeDetailSheetContent(
                    item = item,
                    rawAttachmentLoader = previewAttachmentLoader,
                    onAddToCalendar = if (includeCalendarAndReminder) {{ }} else null,
                    onRemindNotice = if (includeCalendarAndReminder) {{ }} else null,
                    onDismiss = {},
                )
            }
        }
    }
}

@Preview(name = "186 通知重做 · 详情弹层 自动通知", showBackground = true, widthDp = 412, heightDp = 940)
@Composable
private fun NoticeRedesignDetailAutoPreview() {
    ClassToolsTheme(darkTheme = false) {
        ExpressiveBackground(modifier = Modifier.fillMaxSize()) {
            PreviewDetailSheet(item = detailItem("关于 11 月主题班会的通知"))
        }
    }
}

@Preview(name = "187 通知重做 · 详情弹层 人工通知 深色", showBackground = true, widthDp = 412, heightDp = 940)
@Composable
private fun NoticeRedesignDetailManualDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        ExpressiveBackground(modifier = Modifier.fillMaxSize()) {
            PreviewDetailSheet(
                item = detailItem("主题班会照片"),
                includeCalendarAndReminder = false,
            )
        }
    }
}

// -----------------------------------------------------------------------------
// 大字体 / 平板
// -----------------------------------------------------------------------------

@Preview(
    name = "188 通知 · 窄屏大字体",
    showBackground = true,
    widthDp = 320,
    heightDp = 980,
    fontScale = 2f,
)
@Composable
private fun NoticeRedesignLargeFontPreview() {
    ClassToolsTheme(darkTheme = false) {
        NoticesScreen(
            onBack = {},
            session = null,
            marksRepository = null,
            noticesRepository = null,
            initialData = previewNoticesData(),
            initialMarks = previewMarks(),
            skipLoad = true,
        )
    }
}

@Preview(name = "通知长标题和多行来源", showBackground = true, widthDp = 320, heightDp = 760, fontScale = 2f)
@Composable
private fun NoticeLongDetailPreview() {
    ClassToolsTheme(darkTheme = true) {
        val item = detailItem("关于 11 月主题班会的通知").copy(
            title = "关于组织本学期跨学院实践交流与成果展示活动的补充通知",
            source = "跨学院实践交流与成果展示活动筹备工作群",
            senderLine = "学生活动组织委员会",
        )
        PreviewDetailSheet(item)
    }
}

@Preview(name = "189 通知重做 · 平板 800dp", showBackground = true, widthDp = 800, heightDp = 1000)
@Composable
private fun NoticeRedesignTabletPreview() {
    ClassToolsTheme(darkTheme = false) {
        NoticesScreen(
            onBack = {},
            session = null,
            marksRepository = null,
            noticesRepository = null,
            initialData = previewNoticesData(),
            initialMarks = previewMarks(),
            skipLoad = true,
        )
    }
}

@Preview(name = "193 通知重做 · 详情弹层 平板", showBackground = true, widthDp = 800, heightDp = 1000)
@Composable
private fun NoticeRedesignDetailTabletPreview() {
    ClassToolsTheme(darkTheme = true) {
        ExpressiveBackground(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.94f),
                    shape = CtShapeTokens.sheet,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    NoticeDetailSheetContent(
                        item = detailItem("关于 11 月主题班会的通知"),
                        rawAttachmentLoader = previewAttachmentLoader,
                        onDismiss = {},
                    )
                }
            }
        }
    }
}

@Preview(
    name = "194 通知重做 · 筛选 深色 大字体",
    showBackground = true,
    widthDp = 412,
    heightDp = 980,
    fontScale = 1.3f,
)
@Composable
private fun NoticeRedesignFilterDarkLargeFontPreview() {
    ClassToolsTheme(darkTheme = true) {
        NoticesScreen(
            onBack = {},
            session = null,
            marksRepository = null,
            noticesRepository = null,
            initialData = previewNoticesData(fromCache = true),
            initialMarks = previewMarks(),
            initialFilter = NoticeFilter.PINNED,
            skipLoad = true,
        )
    }
}

// -----------------------------------------------------------------------------
// task-43：高级交互动效（预览注入动画帧；真机由 spring/Animatable 驱动）
// -----------------------------------------------------------------------------

/** 195 列表错峰入场中间帧（后两项仍在下方淡入上浮）。 */
@Preview(name = "195 通知动效 · 错峰入场 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun NoticeMotionEntrancePreview() {
    ClassToolsTheme(darkTheme = false) {
        NoticesScreen(
            onBack = {},
            session = null,
            marksRepository = null,
            noticesRepository = null,
            initialData = previewNoticesData(),
            initialMarks = previewMarks(),
            skipLoad = true,
            previewEntranceProgress = 0.72f,
        )
    }
}

/** 196 卡片按压缩放/高光（深色）。 */
@Preview(name = "196 通知动效 · 卡片按压 深色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun NoticeMotionPressedDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        NoticesScreen(
            onBack = {},
            session = null,
            marksRepository = null,
            noticesRepository = null,
            initialData = previewNoticesData(),
            initialMarks = previewMarks(),
            skipLoad = true,
            previewPressedKey = "auto_g_meeting",
        )
    }
}

/** 197 筛选 chips 弹性放大帧 + 计数。 */
@Preview(name = "197 通知动效 · 筛选 chips 弹性", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun NoticeMotionChipBouncePreview() {
    ClassToolsTheme(darkTheme = false) {
        NoticesScreen(
            onBack = {},
            session = null,
            marksRepository = null,
            noticesRepository = null,
            initialData = previewNoticesData(),
            initialMarks = previewMarks(),
            initialFilter = NoticeFilter.PENDING,
            skipLoad = true,
            previewChipBounceKey = NoticeFilter.PENDING.name,
        )
    }
}

/** 198 点开详情时卡片延伸帧（scale/圆角/投影 + 高光；配合弹层滑入）。 */
@Preview(name = "198 通知动效 · 详情延伸帧", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun NoticeMotionExpandedCardPreview() {
    ClassToolsTheme(darkTheme = false) {
        NoticesScreen(
            onBack = {},
            session = null,
            marksRepository = null,
            noticesRepository = null,
            initialData = previewNoticesData(),
            initialMarks = previewMarks(),
            skipLoad = true,
            previewExpandedKey = "auto_g_meeting",
        )
    }
}

/** 199 搜索展开（尺寸 + 透明度动画终态；结果列表按关键词过滤）。 */
@Preview(name = "199 通知动效 · 搜索展开", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun NoticeMotionSearchExpandedPreview() {
    ClassToolsTheme(darkTheme = false) {
        NoticesScreen(
            onBack = {},
            session = null,
            marksRepository = null,
            noticesRepository = null,
            initialData = previewNoticesData(),
            initialMarks = previewMarks(),
            initialQuery = "班会",
            skipLoad = true,
        )
    }
}

/** 200 大字体 1.3x 下的按压/入场（触控与文字不裁切）。 */
@Preview(
    name = "200 通知动效 · 大字体 1.3x 按压",
    showBackground = true,
    widthDp = 412,
    heightDp = 980,
    fontScale = 1.3f,
)
@Composable
private fun NoticeMotionLargeFontPressedPreview() {
    ClassToolsTheme(darkTheme = false) {
        NoticesScreen(
            onBack = {},
            session = null,
            marksRepository = null,
            noticesRepository = null,
            initialData = previewNoticesData(),
            initialMarks = previewMarks(),
            skipLoad = true,
            previewPressedKey = "auto_g_meeting",
        )
    }
}

/** 201 平板 800dp：两列网格 + 错峰入场帧。 */
@Preview(name = "201 通知动效 · 平板 800dp 错峰", showBackground = true, widthDp = 800, heightDp = 1000)
@Composable
private fun NoticeMotionTabletEntrancePreview() {
    ClassToolsTheme(darkTheme = false) {
        NoticesScreen(
            onBack = {},
            session = null,
            marksRepository = null,
            noticesRepository = null,
            initialData = previewNoticesData(),
            initialMarks = previewMarks(),
            skipLoad = true,
            previewEntranceProgress = 0.8f,
        )
    }
}
