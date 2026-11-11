package com.furina.classtools.data.vote

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoteLogicTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now: Long = LocalDateTime.of(2026, 11, 11, 21, 0).atZone(zone).toInstant().toEpochMilli()

    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun settings(
        multi: Boolean = false,
        maxChoices: Int = 1,
        anonymous: Boolean = true,
        allowChange: Boolean = true,
        visibility: PollResultVisibility = PollResultVisibility.AFTER_VOTE,
    ) = PollSettings(multi, maxChoices, anonymous, allowChange, visibility)

    private fun draft(
        title: String = "班会主题投票",
        description: String = "",
        optionCount: Int = 2,
        settings: PollSettings = settings(),
        deadline: Long = 0L,
        audience: PollAudience = PollAudience.ALL,
        seats: List<Int> = emptyList(),
    ): PollDraft = PollDraft(
        title = title,
        description = description,
        options = (0 until optionCount).map { PollOptionDraft(text = "选项${it + 1}") },
        settings = settings,
        deadline = deadline,
        audience = audience,
        audienceSeats = seats,
    )

    // ------------------------------------------------------------ 倒计时 / 截止

    @Test
    fun `无截止且未结束为不限时`() {
        assertTrue(VoteLogic.countdown(closed = false, deadline = 0L, nowMillis = now) is PollCountdown.Unlimited)
    }

    @Test
    fun `倒计时格式 小时与分钟`() {
        val oneHour = VoteLogic.countdown(false, now + 3_723_000L, now) as PollCountdown.Remaining
        assertEquals("剩 01:02:03", oneHour.text)
        val minutes = VoteLogic.countdown(false, now + 125_000L, now) as PollCountdown.Remaining
        assertEquals("剩 02:05", minutes.text)
    }

    @Test
    fun `已截止与提前结束都是已结束`() {
        assertTrue(VoteLogic.countdown(false, now - 1L, now) is PollCountdown.Closed)
        assertTrue(VoteLogic.countdown(true, now + 60_000L, now) is PollCountdown.Closed)
        assertTrue(VoteLogic.effectiveClosed("closed", 0L, now))
        assertTrue(VoteLogic.effectiveClosed("open", now - 1L, now))
        assertFalse(VoteLogic.effectiveClosed("open", 0L, now))
        assertFalse(VoteLogic.effectiveClosed("open", now + 60_000L, now))
    }

    @Test
    fun `服务端 closed 优先于本地判定`() {
        assertTrue(VoteLogic.effectiveClosed("open", now + 60_000L, now, serverClosed = true))
        assertFalse(VoteLogic.effectiveClosed("closed", 0L, now, serverClosed = false))
    }

    @Test
    fun `列表时间标签口径`() {
        assertEquals("已结束", VoteLogic.deadlineLabel(true, now + 60_000L, now))
        assertEquals("不限时", VoteLogic.deadlineLabel(false, 0L, now))
        assertEquals("剩 01:00:00", VoteLogic.deadlineLabel(false, now + 3_600_000L, now))
        assertTrue(VoteLogic.deadlineLabel(false, at(11, 20, 10), now).startsWith("截止 11-20"))
    }

    @Test
    fun `快捷截止时间与非法分钟钳制`() {
        assertEquals(now + 30 * 60_000L, VoteLogic.quickDeadline(now, 30))
        assertEquals(now + 60_000L, VoteLogic.quickDeadline(now, 0))
        assertTrue(VoteLogic.quickDeadline(now, 999_999) > now)
    }

    // ------------------------------------------------------------ 可见性 / 改票

    @Test
    fun `结果可见性三档`() {
        val always = settings(visibility = PollResultVisibility.ALWAYS)
        assertTrue(VoteLogic.resultsVisible(always, closed = false, hasVoted = false))
        val afterVote = settings(visibility = PollResultVisibility.AFTER_VOTE)
        assertFalse(VoteLogic.resultsVisible(afterVote, closed = false, hasVoted = false))
        assertTrue(VoteLogic.resultsVisible(afterVote, closed = false, hasVoted = true))
        assertTrue(VoteLogic.resultsVisible(afterVote, closed = true, hasVoted = false))
        val afterClose = settings(visibility = PollResultVisibility.AFTER_CLOSE)
        assertFalse(VoteLogic.resultsVisible(afterClose, closed = false, hasVoted = true))
        assertTrue(VoteLogic.resultsVisible(afterClose, closed = true, hasVoted = false))
    }

    @Test
    fun `能否投票的提示顺序`() {
        assertNull(VoteLogic.voteBlockReason(false, settings(), hasVoted = false))
        assertEquals(
            "不在可见范围内",
            VoteLogic.voteBlockReason(false, settings(), hasVoted = false, inAudience = false),
        )
        assertEquals(
            "投票已结束",
            VoteLogic.voteBlockReason(true, settings(), hasVoted = false, inAudience = true),
        )
        assertEquals(
            "已投过票，不可改票",
            VoteLogic.voteBlockReason(false, settings(allowChange = false), hasVoted = true),
        )
        assertNull(VoteLogic.voteBlockReason(false, settings(allowChange = true), hasVoted = true))
        // 管理员即使在范围外也只是不能投，提示「不在可见范围内」
        assertEquals(
            "不在可见范围内",
            VoteLogic.voteBlockReason(false, settings(), hasVoted = false, inAudience = false),
        )
    }

    @Test
    fun `改票条件`() {
        assertTrue(VoteLogic.canChangeVote(settings(allowChange = true), closed = false, hasVoted = true))
        assertFalse(VoteLogic.canChangeVote(settings(allowChange = false), closed = false, hasVoted = true))
        assertFalse(VoteLogic.canChangeVote(settings(allowChange = true), closed = true, hasVoted = true))
        assertFalse(VoteLogic.canChangeVote(settings(allowChange = true), closed = false, hasVoted = false))
        assertTrue(VoteLogic.canManage(mine = true, isAdmin = false))
        assertTrue(VoteLogic.canManage(mine = false, isAdmin = true))
        assertFalse(VoteLogic.canManage(mine = false, isAdmin = false))
    }

    // ------------------------------------------------------------ 选项校验

    @Test
    fun `选项校验覆盖空 重复 未知 单选多选上限`() {
        assertTrue(VoteLogic.validateChoices(settings(), emptyList(), listOf("a", "b")) is VoteError.Client)
        assertEquals(
            VoteError.DuplicateChoices,
            VoteLogic.validateChoices(settings(), listOf("a", "a"), listOf("a", "b")),
        )
        assertEquals(
            VoteError.InvalidOptions,
            VoteLogic.validateChoices(settings(), listOf("z"), listOf("a", "b")),
        )
        assertEquals(
            VoteError.SingleChoiceOnly,
            VoteLogic.validateChoices(settings(), listOf("a", "b"), listOf("a", "b")),
        )
        assertEquals(
            VoteError.LimitExceeded(2),
            VoteLogic.validateChoices(
                settings(multi = true, maxChoices = 2),
                listOf("a", "b", "c"),
                listOf("a", "b", "c"),
            ),
        )
        assertNull(VoteLogic.validateChoices(settings(multi = true, maxChoices = 2), listOf("a", "b"), listOf("a", "b")))
        assertNull(VoteLogic.validateChoices(settings(), listOf("a"), listOf("a", "b")))
    }

    // ------------------------------------------------------------ 草稿校验 / 归一

    @Test
    fun `合法草稿无错误`() {
        assertTrue(VoteLogic.validateDraft(draft(deadline = now + 3_600_000L), now).isEmpty())
        assertTrue(
            VoteLogic.validateDraft(
                draft(audience = PollAudience.CUSTOM, seats = listOf(3, 2)),
                now,
            ).isEmpty(),
        )
        assertTrue(VoteLogic.validateDraft(draft(settings = settings(multi = true, maxChoices = 3), optionCount = 4), now).isEmpty())
    }

    @Test
    fun `标题与说明长度校验`() {
        assertTrue(VoteLogic.validateDraft(draft(title = ""), now).any { it.field == VoteField.TITLE })
        assertTrue(VoteLogic.validateDraft(draft(title = "字".repeat(81)), now).any { it.field == VoteField.TITLE })
        assertTrue(VoteLogic.validateDraft(draft(description = "字".repeat(501)), now).any { it.field == VoteField.DESCRIPTION })
        assertTrue(VoteLogic.validateDraft(draft(title = "字".repeat(80)), now).none { it.field == VoteField.TITLE })
    }

    @Test
    fun `选项数量与文字校验`() {
        assertTrue(VoteLogic.validateDraft(draft(optionCount = 1), now).any { it.code == "OPTIONS_COUNT" })
        assertTrue(VoteLogic.validateDraft(draft(optionCount = 21), now).any { it.code == "OPTIONS_COUNT" })
        val blank = draft().copy(options = listOf(PollOptionDraft(text = " "), PollOptionDraft(text = "b")))
        assertTrue(VoteLogic.validateDraft(blank, now).any { it.field == VoteField.OPTION_TEXT })
        val longText = draft().copy(
            options = listOf(PollOptionDraft(text = "字".repeat(81)), PollOptionDraft(text = "b")),
        )
        assertTrue(VoteLogic.validateDraft(longText, now).any { it.field == VoteField.OPTION_TEXT })
        val longDesc = draft().copy(
            options = listOf(PollOptionDraft(text = "a", desc = "字".repeat(201)), PollOptionDraft(text = "b")),
        )
        assertTrue(VoteLogic.validateDraft(longDesc, now).any { it.field == VoteField.OPTION_DESC })
    }

    @Test
    fun `配图前缀与大小校验`() {
        val badPrefix = draft().copy(
            options = listOf(PollOptionDraft(text = "a", img = "http://x"), PollOptionDraft(text = "b")),
        )
        assertTrue(VoteLogic.validateDraft(badPrefix, now).any { it.field == VoteField.OPTION_IMAGE })
        val tooLarge = draft().copy(
            options = listOf(
                PollOptionDraft(text = "a", img = "data:image/jpeg;base64," + "a".repeat(VoteLimits.MAX_OPTION_IMAGE_CHARS)),
                PollOptionDraft(text = "b"),
            ),
        )
        assertTrue(VoteLogic.validateDraft(tooLarge, now).any { it.code == "OPTION_IMAGE_SIZE_0" })
        val totalTooLarge = draft().copy(
            options = (1..20).map {
                PollOptionDraft(
                    text = "选项$it",
                    img = "data:image/jpeg;base64," + "a".repeat(VoteLimits.MAX_OPTION_IMAGE_CHARS - 10),
                )
            },
        )
        assertTrue(VoteLogic.validateDraft(totalTooLarge, now).any { it.code == "OPTION_IMAGE_TOTAL" })
    }

    @Test
    fun `多选最大数与选项数校验`() {
        assertTrue(
            VoteLogic.validateDraft(
                draft(settings = settings(multi = true, maxChoices = 1), optionCount = 3),
                now,
            ).any { it.field == VoteField.SETTINGS },
        )
        assertTrue(
            VoteLogic.validateDraft(
                draft(settings = settings(multi = true, maxChoices = 5), optionCount = 3),
                now,
            ).any { it.code == "MAX_CHOICES_OVER" },
        )
    }

    @Test
    fun `截止时间校验`() {
        assertTrue(VoteLogic.validateDraft(draft(deadline = -1L), now).any { it.code == "DEADLINE_NEGATIVE" })
        assertTrue(VoteLogic.validateDraft(draft(deadline = now - 1L), now).any { it.code == "DEADLINE_PAST" })
        assertTrue(VoteLogic.validateDraft(draft(deadline = now + 1L), now).none { it.field == VoteField.DEADLINE })
    }

    @Test
    fun `可见范围校验`() {
        assertTrue(
            VoteLogic.validateDraft(draft(audience = PollAudience.CUSTOM), now)
                .any { it.code == "AUDIENCE_EMPTY" },
        )
        assertTrue(
            VoteLogic.validateDraft(draft(audience = PollAudience.CUSTOM, seats = listOf(0)), now)
                .any { it.code == "AUDIENCE_SEAT" },
        )
        assertTrue(
            VoteLogic.validateDraft(draft(audience = PollAudience.CUSTOM, seats = listOf(2, 2)), now)
                .any { it.code == "AUDIENCE_DUP" },
        )
        assertTrue(
            VoteLogic.validateDraft(draft(audience = PollAudience.ALL, seats = listOf(2)), now)
                .any { it.code == "AUDIENCE_SEATS" },
        )
    }

    @Test
    fun `准备草稿生成可复用的稳定 id 与选项 id`() {
        val prepared = VoteLogic.prepareDraft(draft(), now)
        assertTrue(VoteLogic.isPollIdValid(prepared.id))
        assertEquals(listOf("o1", "o2"), prepared.options.map { it.id })
        // 重试复用同一草稿 → 同 id（服务端幂等，防重复投票）
        val retried = VoteLogic.prepareDraft(prepared, now + 1000L)
        assertEquals(prepared.id, retried.id)
        assertEquals(prepared.options.map { it.id }, retried.options.map { it.id })
        // 已有合法 id 时不覆盖
        val explicit = VoteLogic.prepareDraft(draft().copy(id = "p12345"), now)
        assertEquals("p12345", explicit.id)
    }
    @Test
    fun `草稿归一 trim 归一规则与座号排序去重`() {
        val normalized = VoteLogic.normalizeDraft(
            PollDraft(
                title = "  标题  ",
                description = " 说明 ",
                options = listOf(PollOptionDraft(text = " A ", desc = " d "), PollOptionDraft(text = "B")),
                settings = PollSettings(multi = true, maxChoices = 99),
                audience = PollAudience.CUSTOM,
                audienceSeats = listOf(5, 3, 5, 0, 99),
            ),
        )
        assertEquals("标题", normalized.title)
        assertEquals("说明", normalized.description)
        assertEquals(listOf("A", "B"), normalized.options.map { it.text })
        assertEquals(listOf(3, 5), normalized.audienceSeats)
        assertTrue(normalized.settings.maxChoices <= 2)
        assertEquals(2, normalized.options[0].id.length)
    }

    // ------------------------------------------------------------ 列表排序 / 标签

    @Test
    fun `进行中按截止升序 不限时最后 已结束按创建倒序`() {
        fun poll(id: String, closed: Boolean, created: Long, deadline: Long) = PollSummary(
            id = id,
            title = id,
            deadline = deadline,
            closed = closed,
            createdAt = created,
        )
        val polls = listOf(
            poll("closed-old", true, 10L, 0L),
            poll("unlimited", false, 30L, 0L),
            poll("soon", false, 20L, now + 1000L),
            poll("later", false, 20L, now + 10_000L),
            poll("closed-new", true, 40L, 0L),
        )
        assertEquals(listOf("soon", "later", "unlimited"), VoteLogic.sortOpen(polls).map { it.id })
        assertEquals(listOf("closed-new", "closed-old"), VoteLogic.sortClosed(polls).map { it.id })
        assertEquals(listOf("soon", "later", "unlimited"), VoteLogic.filterForTab(polls, showClosed = false).map { it.id })
    }

    @Test
    fun `可见范围与结果标签文案`() {
        assertNull(VoteLogic.audienceTag(PollAudience.ALL))
        assertEquals("班委可见", VoteLogic.audienceTag(PollAudience.COMMITTEE))
        assertEquals("团员可见", VoteLogic.audienceTag(PollAudience.LEAGUE))
        assertEquals("指定 3 人", VoteLogic.audienceTag(PollAudience.CUSTOM, 3))
        assertEquals("指定 0 人", VoteLogic.audienceTag(PollAudience.CUSTOM, 0))
        assertNull(VoteLogic.resultHiddenTag(settings(visibility = PollResultVisibility.ALWAYS)))
        assertEquals("投票后可见", VoteLogic.resultHiddenTag(settings(visibility = PollResultVisibility.AFTER_VOTE)))
        assertEquals("截止后可见", VoteLogic.resultHiddenTag(settings(visibility = PollResultVisibility.AFTER_CLOSE)))
    }

    @Test
    fun `规则标签与匿名实名`() {
        val tags = settings(multi = true, maxChoices = 2, anonymous = false, allowChange = true)
            .tags(hasVoted = false, closed = false)
        assertTrue(tags.contains("多选"))
        assertTrue(tags.contains("最多 2 项"))
        assertTrue(tags.contains("实名"))
        assertTrue(tags.contains("可改票"))
        assertTrue(tags.contains("投票后可见"))
        val anon = settings(anonymous = true, allowChange = false).tags()
        assertTrue(anon.contains("匿名"))
        assertFalse(anon.contains("可改票"))
    }

    @Test
    fun `百分比与变化文案`() {
        assertEquals(0.0, VoteLogic.percent(0, 0), 0.0001)
        assertEquals(50.0, VoteLogic.percent(5, 10), 0.0001)
        assertEquals("50.0%", VoteLogic.formatPercent(50.0))
        assertEquals("0%", VoteLogic.formatPercent(Double.NaN))
        assertEquals("+12.3%", VoteLogic.formatDeltaPercent(12.34))
        assertEquals("-4.5%", VoteLogic.formatDeltaPercent(-4.5))
        assertEquals("0.0%", VoteLogic.formatDeltaPercent(0.0))
    }

    @Test
    fun `投票与选项 ID 合法`() {
        val pollId = VoteLogic.newPollId(1_760_000_000_000L, "abcdef")
        assertTrue(VoteLogic.isPollIdValid(pollId))
        assertTrue(pollId.startsWith("p"))
        assertFalse(VoteLogic.isPollIdValid("abc"))
        assertFalse(VoteLogic.isPollIdValid("pABC"))
        val optionId = VoteLogic.newOptionId(1_760_000_000_000L, 2, "wxyz")
        assertTrue(optionId.length <= 24)
        assertTrue(optionId.startsWith("o"))
        assertEquals("MM-dd HH:mm", VoteLogic.formatAt(now, zone).length.let { if (it == 11) "MM-dd HH:mm" else "bad" })
        assertEquals(now, VoteLogic.bestNow(serverNow = now))
        assertTrue(VoteLogic.bestNow(serverNow = 0L, localNow = now) == now)
    }
}
