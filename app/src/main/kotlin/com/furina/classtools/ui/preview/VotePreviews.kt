package com.furina.classtools.ui.preview

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.member.MemberCategory
import com.furina.classtools.data.member.MemberEntry
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
import com.furina.classtools.ui.screens.vote.VoteCreateScreen
import com.furina.classtools.ui.screens.vote.VoteDetailScreen
import com.furina.classtools.ui.screens.vote.VoteListScreen
import com.furina.classtools.ui.screens.vote.VoteManageContent
import com.furina.classtools.ui.theme.ClassToolsTheme
import com.furina.classtools.data.member.RosterMemberSource

/** 预览固定时钟：2026-11-06 10:00 +08。 */
private const val VOTE_PREVIEW_NOW: Long = 1_793_930_400_000L

private val previewSettings = PollSettings(
    multi = false,
    maxChoices = 1,
    anonymous = true,
    allowChange = true,
    resultVisibility = PollResultVisibility.AFTER_VOTE,
)

private val previewMultiSettings = PollSettings(
    multi = true,
    maxChoices = 2,
    anonymous = false,
    allowChange = true,
    resultVisibility = PollResultVisibility.AFTER_VOTE,
)

private val previewPollList = PollList(
    polls = listOf(
        PollSummary(
            id = "p1",
            title = "本周班会时间投票",
            description = "选一下这周班会开在什么时候",
            mode = PollMode.QUICK,
            ownerName = "陈同学",
            settings = previewSettings,
            deadline = VOTE_PREVIEW_NOW + 2L * 60 * 60 * 1000,
            createdAt = VOTE_PREVIEW_NOW - 30L * 60 * 1000,
            audience = PollAudience.ALL,
            closed = false,
            total = 23,
            hasVoted = false,
            mine = true,
            canManage = true,
            resultsHidden = false,
        ),
        PollSummary(
            id = "p2",
            title = "班服颜色选择",
            description = "最多选 2 种颜色，实名投票",
            mode = PollMode.LONG,
            ownerName = "李四",
            settings = previewMultiSettings,
            deadline = VOTE_PREVIEW_NOW + 3L * 24 * 60 * 60 * 1000,
            createdAt = VOTE_PREVIEW_NOW - 2L * 24 * 60 * 60 * 1000,
            audience = PollAudience.COMMITTEE,
            audienceCount = 8,
            closed = false,
            total = 41,
            hasVoted = true,
            myChoices = listOf("o2"),
            resultsHidden = false,
        ),
        PollSummary(
            id = "p3",
            title = "下周自习地点",
            mode = PollMode.LONG,
            ownerName = "王五",
            settings = previewSettings.copy(resultVisibility = PollResultVisibility.AFTER_CLOSE),
            deadline = 0L,
            createdAt = VOTE_PREVIEW_NOW - 5L * 24 * 60 * 60 * 1000,
            audience = PollAudience.CUSTOM,
            audienceCount = 12,
            closed = false,
            total = 8,
        ),
        PollSummary(
            id = "p4",
            title = "春季运动会项目报名",
            mode = PollMode.LONG,
            ownerName = "赵六",
            settings = previewMultiSettings,
            deadline = VOTE_PREVIEW_NOW - 24L * 60 * 60 * 1000,
            createdAt = VOTE_PREVIEW_NOW - 8L * 24 * 60 * 60 * 1000,
            audience = PollAudience.ALL,
            closed = true,
            total = 55,
            hasVoted = true,
        ),
    ),
    serverNow = VOTE_PREVIEW_NOW,
)

private val previewPollOptions = listOf(
    PollOption(id = "o1", text = "周五 19:00", desc = "教室 302"),
    PollOption(id = "o2", text = "周六 10:00", desc = "线上腾讯会议", img = ""),
    PollOption(id = "o3", text = "周日 15:00", desc = "图书馆研讨室"),
    PollOption(id = "o4", text = "都可以"),
)

private val previewVotingDetail = PollDetail(
    poll = Poll(
        id = "p1",
        title = "本周班会时间投票",
        description = "选一下这周班会开在什么时候，截止前可改票。",
        mode = PollMode.QUICK,
        ownerName = "陈同学",
        settings = previewSettings,
        options = previewPollOptions,
        deadline = VOTE_PREVIEW_NOW + 2L * 60 * 60 * 1000,
        status = "open",
        createdAt = VOTE_PREVIEW_NOW - 30L * 60 * 1000,
        audience = PollAudience.ALL,
    ),
    serverNow = VOTE_PREVIEW_NOW,
    closed = false,
    total = 0,
    counts = emptyMap(),
    hasVoted = false,
    mine = true,
    canManage = true,
    resultsHidden = false,
)

private val previewResultDetail = PollDetail(
    poll = Poll(
        id = "p2",
        title = "班服颜色选择",
        description = "最多选 2 种颜色，截止前可改票。",
        mode = PollMode.LONG,
        ownerName = "李四",
        settings = previewMultiSettings,
        options = listOf(
            PollOption(id = "c1", text = "雾青"),
            PollOption(id = "c2", text = "暖沙"),
            PollOption(id = "c3", text = "灰蓝"),
            PollOption(id = "c4", text = "暖白"),
        ),
        deadline = VOTE_PREVIEW_NOW + 3L * 24 * 60 * 60 * 1000,
        status = "open",
        createdAt = VOTE_PREVIEW_NOW - 2L * 24 * 60 * 60 * 1000,
        audience = PollAudience.COMMITTEE,
        audienceCount = 8,
    ),
    serverNow = VOTE_PREVIEW_NOW,
    closed = false,
    total = 41,
    counts = mapOf("c1" to 12, "c2" to 20, "c3" to 9, "c4" to 4),
    hasVoted = true,
    myChoices = listOf("c2"),
    mine = false,
    canManage = false,
    resultsHidden = false,
    voters = listOf(
        PollVoter(name = "陈同学", choices = listOf("c2")),
        PollVoter(name = "李四", choices = listOf("c2")),
        PollVoter(name = "王五", choices = listOf("c1", "c2")),
        PollVoter(name = "赵六", choices = listOf("c3")),
        PollVoter(name = "孙七", choices = listOf("c2")),
        PollVoter(name = "周八", choices = listOf("c1")),
    ),
)

private val previewHiddenDetail = PollDetail(
    poll = Poll(
        id = "p3",
        title = "班委改选",
        description = "匿名投票，截止后公布结果。",
        mode = PollMode.LONG,
        ownerName = "班主任",
        settings = PollSettings(
            multi = false,
            maxChoices = 1,
            anonymous = true,
            allowChange = false,
            resultVisibility = PollResultVisibility.AFTER_CLOSE,
        ),
        options = listOf(
            PollOption(id = "v1", text = "张三"),
            PollOption(id = "v2", text = "李四"),
            PollOption(id = "v3", text = "王五"),
        ),
        deadline = VOTE_PREVIEW_NOW + 24L * 60 * 60 * 1000,
        status = "open",
        createdAt = VOTE_PREVIEW_NOW - 60L * 60 * 1000,
        audience = PollAudience.LEAGUE,
        audienceCount = 50,
    ),
    serverNow = VOTE_PREVIEW_NOW,
    closed = false,
    total = 30,
    counts = emptyMap(),
    hasVoted = false,
    mine = false,
    canManage = false,
    resultsHidden = true,
)

private val previewClosedManageDetail = PollDetail(
    poll = Poll(
        id = "p4",
        title = "春季运动会项目报名",
        description = "已结束，可查看结果。",
        mode = PollMode.LONG,
        ownerName = "赵六",
        settings = previewMultiSettings,
        options = listOf(
            PollOption(id = "s1", text = "100 米"),
            PollOption(id = "s2", text = "跳远"),
            PollOption(id = "s3", text = "接力"),
        ),
        deadline = VOTE_PREVIEW_NOW - 24L * 60 * 60 * 1000,
        status = "closed",
        closedAt = VOTE_PREVIEW_NOW - 24L * 60 * 60 * 1000,
        createdAt = VOTE_PREVIEW_NOW - 8L * 24 * 60 * 60 * 1000,
        audience = PollAudience.ALL,
    ),
    serverNow = VOTE_PREVIEW_NOW,
    closed = true,
    total = 55,
    counts = mapOf("s1" to 18, "s2" to 25, "s3" to 12),
    hasVoted = true,
    myChoices = listOf("s2"),
    mine = true,
    canManage = true,
    resultsHidden = false,
    voters = listOf(
        PollVoter(name = "陈同学", choices = listOf("s1")),
        PollVoter(name = "李四", choices = listOf("s2")),
    ),
)

private val previewCreateDraft = PollDraft(
    title = "本周班会时间投票",
    description = "选一下这周班会开在什么时候",
    mode = PollMode.QUICK,
    options = listOf(
        PollOptionDraft(text = "周五 19:00", desc = "教室 302"),
        PollOptionDraft(text = "周六 10:00"),
        PollOptionDraft(text = "周日 15:00"),
    ),
    settings = previewSettings,
    deadline = VOTE_PREVIEW_NOW + 30L * 60 * 1000,
    audience = PollAudience.ALL,
)

private val previewCreateMultiDraft = PollDraft(
    title = "班服颜色选择",
    description = "最多选 2 种颜色",
    mode = PollMode.LONG,
    options = listOf(
        PollOptionDraft(text = "雾青", desc = "主色，低调"),
        PollOptionDraft(text = "暖沙"),
        PollOptionDraft(text = "灰蓝"),
    ),
    settings = previewMultiSettings,
    deadline = VOTE_PREVIEW_NOW + 3L * 24 * 60 * 60 * 1000,
    audience = PollAudience.CUSTOM,
    audienceSeats = listOf(1, 2, 3, 5, 8),
)

private object PreviewVoteRepository : VoteRepository {
    override suspend fun list(force: Boolean): VoteResult<PollList> = VoteResult.Success(previewPollList)

    override suspend fun detail(pollId: String, force: Boolean): VoteResult<PollDetail> =
        VoteResult.Success(previewVotingDetail)

    override suspend fun create(draft: PollDraft): VoteResult<PollDetail> =
        VoteResult.Success(previewVotingDetail)

    override suspend fun submit(pollId: String, choices: List<String>): VoteResult<PollDetail> =
        VoteResult.Success(previewResultDetail)

    override suspend fun manage(pollId: String, action: PollManageAction): VoteResult<PollManageResult> =
        VoteResult.Success(PollManageResult.Updated(previewVotingDetail))
}

private object PreviewRosterMemberSource : RosterMemberSource {
    private val members = listOf(
        MemberEntry(seat = 1, name = "陈同学", badge = "班长"),
        MemberEntry(seat = 2, name = "李四", badge = "团支书"),
        MemberEntry(seat = 3, name = "王五", badge = "学习委员"),
        MemberEntry(seat = 4, name = "赵六"),
        MemberEntry(seat = 5, name = "孙七"),
        MemberEntry(seat = 6, name = "周八"),
        MemberEntry(seat = 7, name = "吴九"),
        MemberEntry(seat = 8, name = "郑十"),
    )

    override suspend fun load(category: MemberCategory): List<MemberEntry> = when (category) {
        MemberCategory.COMMITTEE -> members.filter { it.badge.isNotBlank() }
        MemberCategory.LEAGUE -> members.drop(1)
        MemberCategory.ALL -> members
    }
}

// =============================================================================
// 列表
// =============================================================================

@Preview(name = "140 投票列表 · 浅色", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun VoteListLightPreview() {
    ClassToolsTheme(darkTheme = false) {
        VoteListScreen(
            repository = PreviewVoteRepository,
            initialPolls = previewPollList,
            skipLoad = true,
            nowMillis = VOTE_PREVIEW_NOW,
        )
    }
}

@Preview(name = "141 投票列表 · 深色", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun VoteListDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        VoteListScreen(
            repository = PreviewVoteRepository,
            initialPolls = previewPollList,
            skipLoad = true,
            nowMillis = VOTE_PREVIEW_NOW,
        )
    }
}

@Preview(name = "142 投票列表 · 空态", showBackground = true, widthDp = 412, heightDp = 640)
@Composable
private fun VoteListEmptyPreview() {
    ClassToolsTheme(darkTheme = false) {
        VoteListScreen(
            repository = PreviewVoteRepository,
            initialPolls = PollList(serverNow = VOTE_PREVIEW_NOW),
            skipLoad = true,
            nowMillis = VOTE_PREVIEW_NOW,
        )
    }
}

@Preview(name = "143 投票列表 · 网络错误", showBackground = true, widthDp = 412, heightDp = 640)
@Composable
private fun VoteListErrorPreview() {
    ClassToolsTheme(darkTheme = false) {
        VoteListScreen(
            repository = PreviewVoteRepository,
            initialError = VoteError.Network(),
            skipLoad = true,
            nowMillis = VOTE_PREVIEW_NOW,
        )
    }
}

@Preview(name = "144 投票列表 · 平板", showBackground = true, widthDp = 1000, heightDp = 800)
@Composable
private fun VoteListTabletPreview() {
    ClassToolsTheme(darkTheme = false) {
        VoteListScreen(
            repository = PreviewVoteRepository,
            initialPolls = previewPollList,
            skipLoad = true,
            nowMillis = VOTE_PREVIEW_NOW,
        )
    }
}

// =============================================================================
// 详情
// =============================================================================

@Preview(name = "145 投票详情 · 未投票（可管理）", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun VoteDetailVotingPreview() {
    ClassToolsTheme(darkTheme = false) {
        VoteDetailScreen(
            pollId = "p1",
            repository = PreviewVoteRepository,
            initialDetail = previewVotingDetail,
            skipLoad = true,
            nowMillis = VOTE_PREVIEW_NOW,
        )
    }
}

@Preview(name = "146 投票详情 · 实时结果与实名投票人", showBackground = true, widthDp = 412, heightDp = 1000)
@Composable
private fun VoteDetailResultPreview() {
    ClassToolsTheme(darkTheme = false) {
        VoteDetailScreen(
            pollId = "p2",
            repository = PreviewVoteRepository,
            initialDetail = previewResultDetail,
            skipLoad = true,
            nowMillis = VOTE_PREVIEW_NOW,
        )
    }
}

@Preview(name = "147 投票详情 · 结果隐藏", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun VoteDetailHiddenPreview() {
    ClassToolsTheme(darkTheme = true) {
        VoteDetailScreen(
            pollId = "p3",
            repository = PreviewVoteRepository,
            initialDetail = previewHiddenDetail,
            skipLoad = true,
            nowMillis = VOTE_PREVIEW_NOW,
        )
    }
}

@Preview(name = "148 投票详情 · 已结束管理", showBackground = true, widthDp = 412, heightDp = 1000)
@Composable
private fun VoteDetailManagePreview() {
    ClassToolsTheme(darkTheme = false) {
        VoteDetailScreen(
            pollId = "p4",
            repository = PreviewVoteRepository,
            initialDetail = previewClosedManageDetail,
            initialManageSheetOpen = true,
            skipLoad = true,
            nowMillis = VOTE_PREVIEW_NOW,
        )
    }
}

@Preview(name = "149 投票详情 · 平板", showBackground = true, widthDp = 1000, heightDp = 800)
@Composable
private fun VoteDetailTabletPreview() {
    ClassToolsTheme(darkTheme = false) {
        VoteDetailScreen(
            pollId = "p2",
            repository = PreviewVoteRepository,
            initialDetail = previewResultDetail,
            skipLoad = true,
            nowMillis = VOTE_PREVIEW_NOW,
        )
    }
}

// =============================================================================
// 发起
// =============================================================================

@Preview(name = "150 发起投票 · 默认", showBackground = true, widthDp = 412, heightDp = 1000)
@Composable
private fun VoteCreateDefaultPreview() {
    ClassToolsTheme(darkTheme = false) {
        VoteCreateScreen(
            repository = PreviewVoteRepository,
            initialDraft = previewCreateDraft,
            memberSource = PreviewRosterMemberSource,
            skipLoad = true,
            nowMillis = VOTE_PREVIEW_NOW,
        )
    }
}

@Preview(name = "151 发起投票 · 多选与指定成员", showBackground = true, widthDp = 412, heightDp = 1000)
@Composable
private fun VoteCreateMultiPreview() {
    ClassToolsTheme(darkTheme = false) {
        VoteCreateScreen(
            repository = PreviewVoteRepository,
            initialDraft = previewCreateMultiDraft,
            memberSource = PreviewRosterMemberSource,
            skipLoad = true,
            nowMillis = VOTE_PREVIEW_NOW,
        )
    }
}

@Preview(name = "152 发起投票 · 指定成员选择器", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun VoteCreateMemberPickerPreview() {
    ClassToolsTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            VoteCreateScreen(
                repository = PreviewVoteRepository,
                initialDraft = previewCreateMultiDraft,
                memberSource = PreviewRosterMemberSource,
                initialMemberPickerOpen = true,
                skipLoad = true,
                nowMillis = VOTE_PREVIEW_NOW,
            )
        }
    }
}

@Preview(name = "153 发起投票 · 深色", showBackground = true, widthDp = 412, heightDp = 1000)
@Composable
private fun VoteCreateDarkPreview() {
    ClassToolsTheme(darkTheme = true) {
        VoteCreateScreen(
            repository = PreviewVoteRepository,
            initialDraft = previewCreateDraft,
            memberSource = PreviewRosterMemberSource,
            skipLoad = true,
            nowMillis = VOTE_PREVIEW_NOW,
        )
    }
}

@Preview(name = "154 发起投票 · 平板", showBackground = true, widthDp = 1000, heightDp = 900)
@Composable
private fun VoteCreateTabletPreview() {
    ClassToolsTheme(darkTheme = false) {
        VoteCreateScreen(
            repository = PreviewVoteRepository,
            initialDraft = previewCreateMultiDraft,
            memberSource = PreviewRosterMemberSource,
            skipLoad = true,
            nowMillis = VOTE_PREVIEW_NOW,
        )
    }
}

@Preview(
    name = "155 投票列表 · 大字体 1.3x",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
    fontScale = 1.3f,
)
@Composable
private fun VoteListLargeFontPreview() {
    ClassToolsTheme(darkTheme = false) {
        VoteListScreen(
            repository = PreviewVoteRepository,
            initialPolls = previewPollList,
            skipLoad = true,
            nowMillis = VOTE_PREVIEW_NOW,
        )
    }
}

@Preview(name = "156 投票管理动作（弹层内容）", showBackground = true, widthDp = 412, heightDp = 520)
@Composable
private fun VoteManageContentPreview() {
    ClassToolsTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            VoteManageContent(
                canEdit = false,
                closed = true,
                onEdit = {},
                onClose = {},
                onExtend = {},
                onDelete = {},
            )
        }
    }
}

@Suppress("unused")
private val votePreviewSpacing = 12.dp

// =============================================================================
// 大字体 1.3 适配（无障碍/系统字号；对应 P1 批次 C 复查）
// =============================================================================

@Preview(
    name = "157 投票详情 · 大字体 1.3",
    showBackground = true,
    widthDp = 412,
    heightDp = 1000,
    fontScale = 1.3f,
)
@Composable
private fun VoteDetailLargeFontPreview() {
    ClassToolsTheme(darkTheme = false) {
        VoteDetailScreen(
            pollId = "p2",
            repository = PreviewVoteRepository,
            initialDetail = previewResultDetail,
            skipLoad = true,
            nowMillis = VOTE_PREVIEW_NOW,
        )
    }
}

@Preview(
    name = "158 发起投票 · 大字体 1.3",
    showBackground = true,
    widthDp = 412,
    heightDp = 1000,
    fontScale = 1.3f,
)
@Composable
private fun VoteCreateLargeFontPreview() {
    ClassToolsTheme(darkTheme = false) {
        VoteCreateScreen(
            repository = PreviewVoteRepository,
            initialDraft = previewCreateDraft,
            memberSource = PreviewRosterMemberSource,
            skipLoad = true,
            nowMillis = VOTE_PREVIEW_NOW,
        )
    }
}