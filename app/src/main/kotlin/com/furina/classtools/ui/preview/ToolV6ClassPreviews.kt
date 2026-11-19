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
import com.furina.classtools.data.member.MemberCategory
import com.furina.classtools.data.member.MemberEntry
import com.furina.classtools.data.notices.ManualNotice
import com.furina.classtools.data.notices.NoticeEntry
import com.furina.classtools.data.notices.NoticeGroupDto
import com.furina.classtools.data.notices.NoticeItemMeta
import com.furina.classtools.data.notices.NoticeRow
import com.furina.classtools.data.notices.NoticeSegment
import com.furina.classtools.data.notices.NoticesData
import com.furina.classtools.data.vote.Poll
import com.furina.classtools.data.vote.PollAudience
import com.furina.classtools.data.vote.PollDetail
import com.furina.classtools.data.vote.PollDraft
import com.furina.classtools.data.vote.PollList
import com.furina.classtools.data.vote.PollManageAction
import com.furina.classtools.data.vote.PollManageResult
import com.furina.classtools.data.vote.PollMode
import com.furina.classtools.data.vote.PollOption
import com.furina.classtools.data.vote.PollOptionDraft
import com.furina.classtools.data.vote.PollResultVisibility
import com.furina.classtools.data.vote.PollSettings
import com.furina.classtools.data.vote.PollSummary
import com.furina.classtools.data.vote.PollVoter
import com.furina.classtools.data.vote.VoteError
import com.furina.classtools.data.vote.VoteRepository
import com.furina.classtools.data.vote.VoteResult
import com.furina.classtools.features.roster.RollStatus
import com.furina.classtools.features.roster.RosterMember
import com.furina.classtools.features.roster.RosterState
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.screens.notices.NoticeDetailSheetContent
import com.furina.classtools.ui.screens.notices.NoticeFilter
import com.furina.classtools.ui.screens.notices.NoticeUiItem
import com.furina.classtools.ui.screens.notices.NoticesScreen
import com.furina.classtools.ui.screens.notices.buildUiItems
import com.furina.classtools.ui.screens.roster.RollCallScreen
import com.furina.classtools.ui.screens.roster.RosterScreen
import com.furina.classtools.ui.screens.vote.VoteCreateScreen
import com.furina.classtools.ui.screens.vote.VoteDetailScreen
import com.furina.classtools.ui.screens.vote.VoteListScreen
import com.furina.classtools.ui.screens.vote.VoteMemberSource
import com.furina.classtools.ui.theme.ClassToolsTheme
import com.furina.classtools.ui.theme.CtShapeTokens
import java.time.LocalDateTime
import java.time.ZoneId

// =============================================================================
// 工具页 v6 统一设计语言预览（task-47：通知 / 名单 / 投票；task-54：点名拆页）
//
// 210 通知列表 · 浅色 / 211 通知列表 · 左右滑动中间态 / 212 通知列表 · 深色
// 213 名单 · 名册三分类 · 浅色 / 214 名单 · 分类左右滑动中间态
// 215 点名 · 进行中 · 浅色 / 216 点名 · 进行中 · 深色 大字体 1.3x / 217 点名 · 空名单 · 浅色
// 218 投票列表 · 浅色 / 219 投票发起 · 平板 800dp
//
// 全部注入数据 + skipLoad：不发网络请求、不读真实账号；只验证 v6 视觉/自适应。
// =============================================================================

private fun v6Now(): Long = System.currentTimeMillis()

private fun v6DeadlineHours(hoursFromNow: Long): String =
    LocalDateTime.now(ZoneId.systemDefault()).plusHours(hoursFromNow).withNano(0).toString()

// -----------------------------------------------------------------------------
// 通知 fixtures
// -----------------------------------------------------------------------------

private fun v6NoticesData(fromCache: Boolean = false): NoticesData {
    val now = v6Now()
    val meeting = NoticeEntry(
        row = NoticeRow(id = "auto_g_meeting", title = "关于 11 月主题班会的通知", ts = now - 25 * 60_000L),
        group = NoticeGroupDto(
            id = "g_meeting",
            title = "关于 11 月主题班会的通知",
            cat = "团务",
            source = "班级通知群",
            senderName = "陈子麒",
            senderRole = "团支部书记",
            aiSummary = "本周五下午 4 点在 01104 教室召开主题班会，请全体同学准时参加并签到。",
            aiPoints = listOf("时间：周五 16:00", "地点：01104", "需要签到"),
            aiSegments = listOf(
                NoticeSegment(type = "时间", text = "本周五 16:00"),
                NoticeSegment(type = "地点", text = "自强楼 01104"),
                NoticeSegment(type = "要求", text = "全体参加，现场签到"),
            ),
            deadline = v6DeadlineHours(48),
            deadlineAction = "开始",
            timeLabel = "11-14 16:00 开始",
            mergedSources = listOf("微信·班级通知群", "QQ·班级群"),
        ),
        items = listOf(
            NoticeItemMeta.Text(
                kind = "text",
                label = "原始消息",
                fromName = "陈子麒",
                fromRole = "团支部书记",
                timeText = "11-12 20:10",
                text = "请大家准时参加班会，记得签到。地点在自强楼 01104。",
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
                url = "https://example.com/notice/meeting.pdf",
            ),
        ),
        hidden = false,
        edited = true,
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
            eventAt = v6DeadlineHours(10),
            eventAction = "考试开始",
        ),
        items = emptyList(),
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
            deadline = v6DeadlineHours(-24),
            deadlineAction = "缴费截止",
        ),
        items = emptyList(),
        hidden = false,
        edited = false,
    )
    val manual = ManualNotice(
        row = NoticeRow(
            id = "manual_1",
            title = "班费收支公示（11 月）",
            body = "本月班费支出见附件：主题班会物料 86 元、班级打印 24 元、饮用水 40 元；结余 1,204 元。",
            ts = now - 90 * 60_000L,
        ),
        attachments = emptyList(),
    )
    return NoticesData(
        auto = listOf(meeting, exam, pay),
        manual = listOf(manual),
        hiddenCount = 0,
        fetchedAt = now,
        fromCache = fromCache,
    )
}

private fun v6NoticeMarks(): MarksSnapshot = MarksSnapshot(
    ntDone = mapOf("g_exam" to true),
    ntPin = mapOf("manual_1" to true),
)

// -----------------------------------------------------------------------------
// 名单 fixtures
// -----------------------------------------------------------------------------

private fun v6RosterMembers(): List<RosterMember> = listOf(
    RosterMember(seat = 1, name = "陈子麒", role = "副团支部书记", badge = "班委"),
    RosterMember(seat = 2, name = "周雨桐", role = "班长", badge = "班委"),
    RosterMember(seat = 3, name = "王思远", role = "学习委员", badge = "班委"),
    RosterMember(seat = 4, name = "刘一诺", role = "生活委员", badge = "班委"),
    RosterMember(seat = 5, name = "赵子涵"),
    RosterMember(seat = 6, name = "李若曦"),
    RosterMember(seat = 7, name = "孙浩然"),
    RosterMember(seat = 8, name = "吴梓萱"),
    RosterMember(seat = 9, name = "郑一鸣"),
    RosterMember(seat = 10, name = "冯佳怡"),
)

private fun v6RosterLeagueMembers(): List<RosterMember> = listOf(
    RosterMember(seat = 1, name = "陈子麒", badge = "团员"),
    RosterMember(seat = 2, name = "周雨桐", badge = "团员"),
    RosterMember(seat = 5, name = "赵子涵", badge = "团员"),
    RosterMember(seat = 6, name = "李若曦", badge = "团员"),
)

private fun v6RosterState(): RosterState = RosterState(
    rosterSel = listOf(1, 2, 3, 5, 6),
    rollSel = listOf(1, 2, 5, 6),
    rollStatus = mapOf(
        0 to RollStatus.IN,
        1 to RollStatus.IN,
        2 to RollStatus.AB,
    ),
    rollNamesText = "1 陈子麒\n2 周雨桐\n5 赵子涵\n6 李若曦",
)

// -----------------------------------------------------------------------------
// 投票 fixtures
// -----------------------------------------------------------------------------

private fun v6VoteSettings(): PollSettings = PollSettings(
    multi = false,
    maxChoices = 1,
    anonymous = false,
    allowChange = true,
    resultVisibility = PollResultVisibility.AFTER_VOTE,
)

private fun v6VoteSummaries(): List<PollSummary> {
    val now = v6Now()
    return listOf(
        PollSummary(
            id = "poll_meeting",
            title = "本周班会时间投票",
            description = "两个时间段选一个，周五前确定。",
            mode = PollMode.QUICK,
            ownerName = "陈子麒",
            settings = v6VoteSettings(),
            deadline = now + 30 * 60_000L,
            createdAt = now - 2 * 3_600_000L,
            audience = PollAudience.ALL,
            total = 22,
            counts = mapOf("o1" to 14, "o2" to 8),
            hasVoted = true,
            mine = true,
            canManage = true,
        ),
        PollSummary(
            id = "poll_committee",
            title = "班会物资采购清单确认",
            description = "班委内部确认采购清单与预算。",
            mode = PollMode.LONG,
            ownerName = "周雨桐",
            settings = v6VoteSettings().copy(resultVisibility = PollResultVisibility.AFTER_CLOSE),
            deadline = now + 3 * 24 * 3_600_000L,
            createdAt = now - 26 * 3_600_000L,
            audience = PollAudience.COMMITTEE,
            audienceCount = 7,
            total = 5,
            counts = mapOf("o1" to 3, "o2" to 2),
            hasVoted = false,
            canManage = false,
        ),
        PollSummary(
            id = "poll_closed",
            title = "国庆假期返校登记确认",
            mode = PollMode.LONG,
            ownerName = "王思远",
            settings = v6VoteSettings(),
            deadline = now - 2 * 24 * 3_600_000L,
            status = "closed",
            createdAt = now - 7 * 24 * 3_600_000L,
            audience = PollAudience.ALL,
            total = 61,
            counts = mapOf("o1" to 58, "o2" to 3),
            hasVoted = true,
            closed = true,
            canManage = false,
        ),
    )
}

private fun v6PollList(): PollList = PollList(
    polls = v6VoteSummaries(),
    serverNow = v6Now(),
)

private fun v6PollDetail(): PollDetail {
    val poll = Poll(
        id = "poll_meeting",
        title = "本周班会时间投票",
        description = "两个时间段选一个，周五前确定；如都有空请选第一项。",
        mode = PollMode.QUICK,
        ownerName = "陈子麒",
        settings = v6VoteSettings(),
        options = listOf(
            PollOption(id = "o1", text = "周五 16:00 - 17:00", desc = "01104 教室"),
            PollOption(id = "o2", text = "周五 19:00 - 20:00", desc = "01104 教室"),
            PollOption(id = "o3", text = "两个时间都可以"),
        ),
        deadline = v6Now() + 30 * 60_000L,
        createdAt = v6Now() - 2 * 3_600_000L,
        audience = PollAudience.ALL,
    )
    return PollDetail(
        poll = poll,
        serverNow = v6Now(),
        total = 22,
        counts = mapOf("o1" to 14, "o2" to 8, "o3" to 6),
        hasVoted = true,
        myChoices = listOf("o1"),
        resultsHidden = false,
        mine = true,
        canManage = true,
        voters = listOf(
            PollVoter(name = "陈子麒", choices = listOf("o1")),
            PollVoter(name = "周雨桐", choices = listOf("o1")),
            PollVoter(name = "王思远", choices = listOf("o2")),
        ),
    )
}

private fun v6PollDraft(): PollDraft {
    val now = v6Now()
    return PollDraft(
        title = "本周班会时间投票",
        description = "两个时间段选一个，周五前确定。",
        mode = PollMode.QUICK,
        options = listOf(
            PollOptionDraft(id = "o1", text = "周五 16:00 - 17:00"),
            PollOptionDraft(id = "o2", text = "周五 19:00 - 20:00", desc = "01104 教室"),
            PollOptionDraft(id = "o3", text = "两个时间都可以"),
        ),
        settings = PollSettings(
            multi = false,
            maxChoices = 1,
            anonymous = false,
            allowChange = true,
            resultVisibility = PollResultVisibility.AFTER_VOTE,
        ),
        deadline = now + 30 * 60_000L,
        audience = PollAudience.ALL,
    )
}

/** 预览用只读仓库：所有写操作返回失败，避免误触真实网络。 */
private class V6PreviewVoteRepository(
    private val listData: PollList,
    private val detailData: PollDetail,
) : VoteRepository {
    override suspend fun list(force: Boolean): VoteResult<PollList> = VoteResult.Success(listData)

    override suspend fun detail(pollId: String, force: Boolean): VoteResult<PollDetail> =
        VoteResult.Success(detailData)

    override suspend fun create(draft: PollDraft): VoteResult<PollDetail> =
        VoteResult.Failure(VoteError.Client("preview", "预览模式不可发起投票"))

    override suspend fun submit(pollId: String, choices: List<String>): VoteResult<PollDetail> =
        VoteResult.Failure(VoteError.Client("preview", "预览模式不可投票"))

    override suspend fun manage(pollId: String, action: PollManageAction): VoteResult<PollManageResult> =
        VoteResult.Failure(VoteError.Client("preview", "预览模式不可管理投票"))
}

private val v6PreviewVoteRepository = V6PreviewVoteRepository(
    listData = v6PollList(),
    detailData = v6PollDetail(),
)

private val v6PreviewMemberSource = object : VoteMemberSource {
    override suspend fun load(category: MemberCategory): List<MemberEntry> = when (category) {
        MemberCategory.COMMITTEE -> listOf(
            MemberEntry(seat = 1, name = "陈子麒", badge = "副团支部书记"),
            MemberEntry(seat = 2, name = "周雨桐", badge = "班长"),
            MemberEntry(seat = 3, name = "王思远", badge = "学习委员"),
        )

        MemberCategory.LEAGUE -> listOf(
            MemberEntry(seat = 1, name = "陈子麒", badge = "团员"),
            MemberEntry(seat = 2, name = "周雨桐", badge = "团员"),
        )

        MemberCategory.ALL -> listOf(
            MemberEntry(seat = 1, name = "陈子麒"),
            MemberEntry(seat = 2, name = "周雨桐"),
            MemberEntry(seat = 5, name = "赵子涵"),
        )
    }
}

// -----------------------------------------------------------------------------
// 210–212 通知
// -----------------------------------------------------------------------------

@Preview(name = "210 通知 v6 · 列表 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun ToolV6NoticesListLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        NoticesScreen(
            onBack = {},
            session = null,
            marksRepository = null,
            noticesRepository = null,
            initialData = v6NoticesData().copy(hasMore = true, remainingCount = 18, loadedRawCount = 20),
            initialMarks = v6NoticeMarks(),
            initialFilter = NoticeFilter.ALL,
            skipLoad = true,
        )
    }
}

@Preview(name = "211 通知 v6 · 左右滑动中间态", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun ToolV6NoticesSwipeMidPreview() {
    ClassToolsTheme(darkTheme = false) {
        NoticesScreen(
            onBack = {},
            session = null,
            marksRepository = null,
            noticesRepository = null,
            initialData = v6NoticesData().copy(hasMore = true, remainingCount = 18, loadedRawCount = 20),
            initialMarks = v6NoticeMarks(),
            initialFilter = NoticeFilter.ALL,
            previewPagerOffset = 0.42f,
            skipLoad = true,
        )
    }
}

@Preview(name = "212 通知 v6 · 列表 深色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun ToolV6NoticesListDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        NoticesScreen(
            onBack = {},
            session = null,
            marksRepository = null,
            noticesRepository = null,
            initialData = v6NoticesData(fromCache = true),
            initialMarks = v6NoticeMarks(),
            skipLoad = true,
        )
    }
}

// -----------------------------------------------------------------------------
// 213–214 名单（仅名册；三分类左右滑动）
// -----------------------------------------------------------------------------

@Preview(name = "213 名单 v6 · 名册三分类 浅色", showBackground = true, widthDp = 412, heightDp = 1000)
@Composable
private fun ToolV6RosterCommitteeLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        RosterScreen(
            onBack = {},
            session = null,
            classMembersRepository = null,
            userDataRepository = null,
            initialMembers = v6RosterMembers(),
            initialRoster = v6RosterState(),
            initialCategory = MemberCategory.COMMITTEE,
            initialLeagueMembers = v6RosterLeagueMembers(),
            skipLeagueLoad = true,
            skipLoad = true,
        )
    }
}

@Preview(name = "214 名单 v6 · 分类左右滑动中间态", showBackground = true, widthDp = 412, heightDp = 1000)
@Composable
private fun ToolV6RosterCategorySwipeMidPreview() {
    ClassToolsTheme(darkTheme = false) {
        RosterScreen(
            onBack = {},
            session = null,
            classMembersRepository = null,
            userDataRepository = null,
            initialMembers = v6RosterMembers(),
            initialRoster = v6RosterState(),
            initialCategory = MemberCategory.COMMITTEE,
            initialLeagueMembers = v6RosterLeagueMembers(),
            skipLeagueLoad = true,
            skipLoad = true,
            previewCategoryOffset = 0.42f,
        )
    }
}

// -----------------------------------------------------------------------------
// 215–217 点名（独立页）
// -----------------------------------------------------------------------------

@Preview(name = "215 点名 v6 · 进行中 浅色", showBackground = true, widthDp = 412, heightDp = 1000)
@Composable
private fun ToolV6RollCallLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        RollCallScreen(
            onBack = {},
            session = null,
            classMembersRepository = null,
            userDataRepository = null,
            initialMembers = v6RosterMembers(),
            initialRoster = v6RosterState(),
            skipLoad = true,
        )
    }
}

@Preview(
    name = "216 点名 v6 · 进行中 深色 大字体 1.3x",
    showBackground = true,
    widthDp = 412,
    heightDp = 1100,
    fontScale = 1.3f,
)
@Composable
private fun ToolV6RollCallDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        RollCallScreen(
            onBack = {},
            session = null,
            classMembersRepository = null,
            userDataRepository = null,
            initialMembers = v6RosterMembers(),
            initialRoster = v6RosterState(),
            skipLoad = true,
        )
    }
}

@Preview(name = "217 点名 v6 · 空名单 浅色", showBackground = true, widthDp = 412, heightDp = 1000)
@Composable
private fun ToolV6RollCallEmptyPreview() {
    ClassToolsTheme(darkTheme = false) {
        RollCallScreen(
            onBack = {},
            session = null,
            classMembersRepository = null,
            userDataRepository = null,
            initialMembers = v6RosterMembers(),
            initialRoster = RosterState(),
            skipLoad = true,
        )
    }
}

// -----------------------------------------------------------------------------
// 218–219 投票
// -----------------------------------------------------------------------------

@Preview(name = "218 投票 v6 · 列表 浅色", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun ToolV6VoteListLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        VoteListScreen(
            repository = v6PreviewVoteRepository,
            initialPolls = v6PollList(),
            skipLoad = true,
        )
    }
}

@Preview(name = "219 投票 v6 · 发起 平板 800dp", showBackground = true, widthDp = 800, heightDp = 1000)
@Composable
private fun ToolV6VoteCreateTabletPreview() {
    ClassToolsTheme(darkTheme = false) {
        VoteCreateScreen(
            repository = v6PreviewVoteRepository,
            memberSource = v6PreviewMemberSource,
            initialDraft = v6PollDraft(),
            skipLoad = true,
        )
    }
}

// -----------------------------------------------------------------------------
// 220 通知 · 平板（两列列表 + v6 卡片）
// -----------------------------------------------------------------------------
