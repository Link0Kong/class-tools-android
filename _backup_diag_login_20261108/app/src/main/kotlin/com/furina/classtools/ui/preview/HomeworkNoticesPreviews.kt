package com.furina.classtools.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.marks.MarksSnapshot
import com.furina.classtools.data.notices.ManualNotice
import com.furina.classtools.data.notices.NoticeEntry
import com.furina.classtools.data.notices.NoticeGroupDto
import com.furina.classtools.data.notices.NoticeItemMeta
import com.furina.classtools.data.notices.NoticeRow
import com.furina.classtools.data.notices.NoticeSegment
import com.furina.classtools.data.notices.NoticesData
import com.furina.classtools.data.session.Session
import com.furina.classtools.ui.screens.homework.HomeworkScreen
import com.furina.classtools.ui.screens.notices.NoticesScreen
import com.furina.classtools.ui.theme.ClassToolsTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

// =============================================================================
// 作业 / 通知预览（独立文件；Lead 用 android preview render 验收）
//
// - 20 作业列表 · 浅色
// - 21 作业空态 · 深色
// - 22 通知列表 · 浅色
// - 23 通知空态 · 深色
// =============================================================================

private val previewSession = Session(
    sid = "3260707024",
    name = "陈子麒",
    token = "preview-token",
    role = "副团支部书记",
    badge = "班委",
)

private fun nowMillis(): Long = System.currentTimeMillis()

private fun dateText(daysFromNow: Long): String =
    LocalDate.now(ZoneId.systemDefault()).plusDays(daysFromNow).toString()

private fun timeText(hour: Int, minute: Int): String =
    LocalTime.of(hour, minute).toString()

private fun previewHomeworkRows(): List<HomeworkRow> {
    val now = nowMillis()
    return listOf(
        HomeworkRow(
            id = "hw-1",
            subject = "高等数学",
            content = "第三章习题 3.1–3.5，写在作业本上，下节课交。",
            due = "明天 23:59",
            date = dateText(1),
            time = "23:59",
            author = "学习委员",
            owner = "3260707001",
            attachments = """[{"n":"第三章习题.pdf","t":"application/pdf","s":245760,"d":"data:application/pdf;base64,AAAA"}]""",
            ts = now - 3_600_000,
        ),
        HomeworkRow(
            id = "hw-2",
            subject = "C 语言程序设计",
            content = "实验 4：循环结构（提交 .c 源码 + 运行截图）。",
            due = "3 天后",
            date = dateText(3),
            time = "18:00",
            author = "陈子麒",
            ts = now - 7_200_000,
        ),
        HomeworkRow(
            id = "hw-3",
            subject = "英语 1",
            content = "Unit 5 单词抄写 2 遍。",
            due = "已截止",
            date = dateText(-1),
            time = "20:00",
            author = "英语课代表",
            ts = now - 86_400_000 * 2,
        ),
        HomeworkRow(
            id = "hw-4",
            subject = "体育 1",
            content = "800 米 / 1000 米测试，穿运动服。",
            date = "",
            due = "下周上课时",
            author = "体育委员",
            ts = now - 86_400_000,
        ),
    )
}

private val previewMarks: MarksSnapshot = MarksSnapshot(hwDone = mapOf("hw-3" to true))

private fun previewNoticesData(): NoticesData {
    val now = nowMillis()
    val group1 = NoticeGroupDto(
        id = "g_1",
        title = "关于 11 月主题班会的通知",
        cat = "班会",
        source = "班级通知群",
        senderName = "陈子麒",
        senderRole = "副团支部书记",
        aiSummary = "本周五下午 4 点在 01104 教室召开主题班会，请全体同学准时参加并签到。",
        aiPoints = listOf("时间：周五 16:00", "地点：01104", "需要签到"),
        aiSegments = listOf(
            NoticeSegment(type = "时间", text = "本周五 16:00"),
            NoticeSegment(type = "地点", text = "自强楼 01104"),
            NoticeSegment(type = "要求", text = "全体参加，现场签到"),
        ),
        deadline = LocalDateTime.now().plusDays(2).toString(),
        deadlineAction = "开始",
        items = listOf(
            kotlinx.serialization.json.buildJsonObject {
                put("kind", kotlinx.serialization.json.JsonPrimitive("text"))
                put("text", kotlinx.serialization.json.JsonPrimitive("请大家准时参加。"))
                put("time", kotlinx.serialization.json.JsonPrimitive("11-05 12:00"))
            },
        ),
    )
    val group2 = NoticeGroupDto(
        id = "g_2",
        title = "青年大学习第 12 期",
        cat = "学习",
        source = "团员通知群",
        aiSummary = "本周青年大学习已发布，请团员在周日 18:00 前完成观看并截图。",
        aiPoints = listOf("完成时间：周日 18:00 前", "需要截图上传"),
        deadline = LocalDateTime.now().plusHours(10).toString(),
        deadlineAction = "完成",
        items = listOf(
            kotlinx.serialization.json.buildJsonObject {
                put("kind", kotlinx.serialization.json.JsonPrimitive("file"))
                put("name", kotlinx.serialization.json.JsonPrimitive("青年大学习截图要求.pdf"))
                put("size", kotlinx.serialization.json.JsonPrimitive("1.2 MB"))
            },
        ),
    )
    val group3 = NoticeGroupDto(
        id = "g_3",
        title = "医药费报销材料提交",
        cat = "事务",
        source = "班级通知群",
        aiSummary = "需要报销医药费的同学，请把发票与清单交给生活委员。",
        deadline = LocalDateTime.now().minusDays(1).toString(),
        deadlineAction = "截止",
    )
    val auto = listOf(
        NoticeEntry(
            row = NoticeRow(id = "auto_g_1", title = group1.title, ts = now - 1_800_000),
            group = group1,
            items = listOf(
                NoticeItemMeta.Text(
                    kind = "text",
                    label = "原始消息",
                    fromName = "陈子麒",
                    fromRole = "副团支部书记",
                    timeText = "11-05 12:00",
                    text = "请大家准时参加班会，记得签到。",
                    isFollow = false,
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
                ),
            ),
            hidden = false,
            edited = false,
        ),
        NoticeEntry(
            row = NoticeRow(id = "auto_g_2", title = group2.title, ts = now - 5_400_000),
            group = group2,
            items = listOf(
                NoticeItemMeta.Text(
                    kind = "text",
                    label = "原始消息",
                    fromName = "团支书",
                    fromRole = "团支书",
                    timeText = "11-05 10:20",
                    text = "请各位团员尽快完成青年大学习。",
                    isFollow = false,
                ),
            ),
            hidden = false,
            edited = false,
        ),
        NoticeEntry(
            row = NoticeRow(id = "auto_g_3", title = group3.title, ts = now - 10_800_000),
            group = group3,
            items = emptyList(),
            hidden = false,
            edited = true,
        ),
    )
    val manual = listOf(
        ManualNotice(
            row = NoticeRow(
                id = "manual_1",
                title = "班费收支公示（11 月）",
                body = "本月班费支出见附件，有问题请私聊生活委员。",
                ts = now - 3_600_000,
            ),
            attachments = emptyList(),
        ),
    )
    return NoticesData(
        auto = auto,
        manual = manual,
        hiddenCount = 0,
        fetchedAt = now,
        fromCache = true,
    )
}

@Preview(name = "20 作业列表 · 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun HomeworkListLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        HomeworkScreen(
            onBack = {},
            session = previewSession,
            marksRepository = null,
            homeworkRepository = null,
            initialRows = previewHomeworkRows(),
            initialMarks = previewMarks,
            skipLoad = true,
        )
    }
}

@Preview(name = "21 作业空态 · 深色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun HomeworkEmptyDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        HomeworkScreen(
            onBack = {},
            session = previewSession,
            marksRepository = null,
            homeworkRepository = null,
            initialRows = emptyList(),
            initialMarks = MarksSnapshot(),
            skipLoad = true,
        )
    }
}

@Preview(name = "22 通知列表 · 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun NoticesListLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        NoticesScreen(
            onBack = {},
            session = previewSession,
            marksRepository = null,
            noticesRepository = null,
            initialData = previewNoticesData(),
            initialMarks = MarksSnapshot(ntDone = mapOf("g_3" to true)),
            skipLoad = true,
        )
    }
}

@Preview(name = "23 通知空态 · 深色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun NoticesEmptyDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        NoticesScreen(
            onBack = {},
            session = previewSession,
            marksRepository = null,
            noticesRepository = null,
            initialData = NoticesData(
                auto = emptyList(),
                manual = emptyList(),
                hiddenCount = 0,
                fetchedAt = nowMillis(),
                fromCache = true,
            ),
            initialMarks = MarksSnapshot(),
            skipLoad = true,
        )
    }
}
