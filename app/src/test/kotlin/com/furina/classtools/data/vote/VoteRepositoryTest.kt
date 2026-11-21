package com.furina.classtools.data.vote

import com.furina.classtools.FakeTransport
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.HttpMethod
import com.furina.classtools.testApiClient
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoteRepositoryTest {

    private val now: Long = 1_760_000_000_000L

    private fun repo(
        transport: FakeTransport,
        sid: String? = "2024000002",
    ): DefaultVoteRepository = DefaultVoteRepository(
        api = testApiClient(transport),
        currentSid = { sid },
        clock = { now },
    )

    private val listJson = """
        {"ok":true,"now":1760000000000,"polls":[
          {"id":"pabc123","title":"班会主题","description":"","mode":"quick","owner_name":"陈同学",
           "settings":{"multi":false,"maxChoices":1,"anonymous":true,"allowChange":true,"resultVisibility":"afterVote"},
           "deadline":1760001000000,"status":"open","closed_at":0,"created_at":10,"updated_at":20,
           "audience":"committee","audience_count":0,"closed":false,"total":3,
           "counts":{"o1":2,"o2":1},"has_voted":true,"my_choices":["o1"],
           "results_hidden":false,"mine":true,"can_manage":true}
        ]}
    """.trimIndent()

    private val detailJson = """
        {"ok":true,"now":1760000000000,
         "poll":{"id":"pabc123","title":"班会主题","description":"说明","mode":"long","owner_name":"陈同学",
           "settings":{"multi":true,"maxChoices":2,"anonymous":false,"allowChange":true,"resultVisibility":"afterVote"},
           "options":[{"id":"o1","text":"选项A","desc":"补充","img":"data:image/jpeg;base64,xx"},{"id":"o2","text":"选项B"}],
           "deadline":0,"status":"open","closed_at":0,"created_at":1,"updated_at":2,
           "audience":"custom","audience_count":2,"audience_seats":[2,5]},
         "closed":false,"total":2,"counts":{"o1":2,"o2":1},
         "has_voted":true,"my_choices":["o1"],"results_hidden":false,
         "mine":false,"can_manage":true,
         "voters":[{"name":"张三","choices":["o1"]},{"name":"李四","choices":["o2"]}]}
    """.trimIndent()

    // ------------------------------------------------------------ list

    @Test
    fun `list 解析字段与请求形态`() = runTest {
        val transport = FakeTransport { ApiResponse(200, listJson) }
        val result = repo(transport).list(force = true)
        val list = (result as VoteResult.Success).value
        val poll = list.polls.single()
        assertEquals("pabc123", poll.id)
        assertEquals(PollAudience.COMMITTEE, poll.audience)
        assertEquals(3, poll.total)
        assertEquals(mapOf("o1" to 2, "o2" to 1), poll.counts)
        assertTrue(poll.hasVoted)
        assertEquals(listOf("o1"), poll.myChoices)
        assertTrue(poll.canManage)
        assertEquals(1760000000000L, list.serverNow)

        val request = transport.lastRequest()
        assertEquals(HttpMethod.POST, request.method)
        assertTrue(request.url.endsWith("/rpc/poll_list"))
        assertEquals("{}", request.body)
        assertEquals("test-token", request.headers["X-Class-Token"])
    }

    @Test
    fun `list TTL 内走缓存不发请求`() = runTest {
        val transport = FakeTransport { ApiResponse(200, listJson) }
        val repository = repo(transport)
        repository.list(force = true)
        val second = repository.list(force = false) as VoteResult.Success
        assertTrue(second.value.fromCache)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `list 网络失败降级缓存并提示`() = runTest {
        var fail = false
        val transport = FakeTransport {
            if (fail) throw IOException("offline") else ApiResponse(200, listJson)
        }
        val repository = repo(transport)
        repository.list(force = true)
        fail = true
        val result = repository.list(force = true) as VoteResult.Success
        assertTrue(result.value.fromCache)
        assertNotNull(result.value.warning)
        assertEquals("pabc123", result.value.polls.single().id)
    }

    @Test
    fun `list 无缓存失败返回网络错误`() = runTest {
        val transport = FakeTransport { throw IOException("offline") }
        val result = repo(transport).list(force = true)
        assertEquals(VoteError.Network(), (result as VoteResult.Failure).error)
    }

    @Test
    fun `list 服务端业务错误映射`() = runTest {
        val transport = FakeTransport { ApiResponse(200, """{"ok":false,"reason":"身份已失效，请重新进入班级"}""") }
        val result = repo(transport).list(force = true)
        assertEquals(VoteError.IdentityInvalid, (result as VoteResult.Failure).error)
    }

    // ------------------------------------------------------------ detail

    @Test
    fun `detail 解析选项 设置 座号 投票人`() = runTest {
        val transport = FakeTransport { ApiResponse(200, detailJson) }
        val detail = (repo(transport).detail("pabc123") as VoteResult.Success).value
        assertEquals("班会主题", detail.poll.title)
        assertEquals(PollMode.LONG, detail.poll.mode)
        assertEquals(2, detail.poll.options.size)
        assertEquals("data:image/jpeg;base64,xx", detail.poll.options.first().img)
        assertTrue(detail.poll.settings.multi)
        assertEquals(2, detail.poll.settings.maxChoices)
        assertFalse(detail.poll.settings.anonymous)
        assertEquals(PollAudience.CUSTOM, detail.poll.audience)
        assertEquals(listOf(2, 5), detail.poll.audienceSeats)
        assertEquals(listOf("张三", "李四"), detail.voters.map { it.name })
        assertTrue(detail.canManage)
        assertEquals(1, transport.requests.size)
        assertEquals(HttpMethod.POST, transport.lastRequest().method)
        assertTrue(transport.lastRequest().url.endsWith("/rpc/poll_detail"))
        assertTrue(transport.lastRequest().body.orEmpty().contains("\"p_id\":\"pabc123\""))
    }

    @Test
    fun `detail 不存在与详情缓存降级`() = runTest {
        val transport = FakeTransport { ApiResponse(200, """{"ok":false,"reason":"投票不存在"}""") }
        val failure = repo(transport).detail("p_missing") as VoteResult.Failure
        assertEquals(VoteError.NotFound, failure.error)

        var fail = false
        val successTransport = FakeTransport {
            if (fail) throw IOException("offline") else ApiResponse(200, detailJson)
        }
        val repository = repo(successTransport)
        repository.detail("pabc123")
        fail = true
        // TTL 内直接缓存
        val cached = repository.detail("pabc123") as VoteResult.Success
        assertEquals("pabc123", cached.value.poll.id)
    }

    // ------------------------------------------------------------ create

    private fun validDraft(): PollDraft = PollDraft(
        title = " 班会主题 ",
        description = "说明",
        mode = PollMode.QUICK,
        options = listOf(
            PollOptionDraft(text = "选项A", desc = "补充"),
            PollOptionDraft(text = "选项B"),
        ),
        settings = PollSettings(multi = false, maxChoices = 1, anonymous = true),
        deadline = now + 30 * 60_000L,
        audience = PollAudience.CUSTOM,
        audienceSeats = listOf(5, 2),
    )

    @Test
    fun `create 客户端校验失败不发请求`() = runTest {
        val transport = FakeTransport { ApiResponse(200, detailJson) }
        val result = repo(transport).create(validDraft().copy(title = ""))
        assertTrue((result as VoteResult.Failure).error is VoteError.Client)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `create 请求体含选项 设置 可见范围 且返回详情`() = runTest {
        val transport = FakeTransport { ApiResponse(200, detailJson) }
        val detail = (repo(transport).create(validDraft()) as VoteResult.Success).value
        assertEquals("pabc123", detail.poll.id)
        val body = transport.lastRequest().body.orEmpty()
        assertTrue(body.contains("\"p_audience\":\"custom\""))
        assertTrue(body.contains("\"p_audience_seats\":[2,5]"))
        assertTrue(body.contains("\"p_options\":["))
        assertTrue(body.contains("\"resultVisibility\":\"afterVote\""))
        assertTrue(body.contains("\"p_mode\":\"quick\""))
        assertTrue(body.contains("\"p_deadline\":${now + 30 * 60_000L}"))
        val request = transport.lastRequest()
        assertTrue(request.url.endsWith("/rpc/poll_create"))
    }

    @Test
    fun `create 自动生成合法 id 与选项 id`() = runTest {
        val transport = FakeTransport { ApiResponse(200, detailJson) }
        repo(transport).create(validDraft().copy(id = ""))
        val body = transport.lastRequest().body.orEmpty()
        val idMatch = Regex(""""p_id":"(p[a-z0-9]{4,32})"""").find(body)
        assertNotNull(idMatch)
        assertTrue(body.contains("\"id\":\"o1\""))
    }

    // ------------------------------------------------------------ submit

    @Test
    fun `submit 成功请求体与结果`() = runTest {
        val transport = FakeTransport { ApiResponse(200, detailJson) }
        val detail = (repo(transport).submit("pabc123", listOf("o1")) as VoteResult.Success).value
        assertTrue(detail.hasVoted)
        val body = transport.lastRequest().body.orEmpty()
        assertTrue(body.contains("\"p_id\":\"pabc123\""))
        assertTrue(body.contains("\"p_choices\":[\"o1\"]"))
        assertTrue(transport.lastRequest().url.endsWith("/rpc/poll_submit"))
    }

    @Test
    fun `submit 空选项与重复选项不发请求`() = runTest {
        val transport = FakeTransport { ApiResponse(200, detailJson) }
        val repository = repo(transport)
        assertEquals(
            "请选择至少 1 项",
            ((repository.submit("pabc123", emptyList()) as VoteResult.Failure).error as VoteError.Client).message,
        )
        assertEquals(
            VoteError.DuplicateChoices,
            (repository.submit("pabc123", listOf("o1", "o1")) as VoteResult.Failure).error,
        )
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `submit 服务端业务错误映射`() = runTest {
        val transport = FakeTransport { ApiResponse(200, """{"ok":false,"reason":"已投过票，不可改票"}""") }
        assertEquals(
            VoteError.AlreadyVoted,
            (repo(transport).submit("pabc123", listOf("o1")) as VoteResult.Failure).error,
        )
    }

    // ------------------------------------------------------------ manage

    @Test
    fun `manage close extend delete 请求体`() = runTest {
        val transport = FakeTransport { request ->
            if (request.body.orEmpty().contains("\"p_action\":\"delete\"")) {
                ApiResponse(200, """{"ok":true,"deleted":true}""")
            } else {
                ApiResponse(200, detailJson)
            }
        }
        val repository = repo(transport)

        repository.manage("pabc123", PollManageAction.Close)
        assertEquals(
            """{"p_id":"pabc123","p_action":"close"}""",
            transport.lastRequest().body,
        )

        repository.manage("pabc123", PollManageAction.Extend(now + 3_600_000L))
        assertTrue(transport.lastRequest().body.orEmpty().contains("\"p_action\":\"extend\""))
        assertTrue(transport.lastRequest().body.orEmpty().contains("\"p_deadline\":${now + 3_600_000L}"))

        val deleted = repository.manage("pabc123", PollManageAction.Delete) as VoteResult.Success
        assertEquals(PollManageResult.Deleted("pabc123"), deleted.value)
        assertTrue(transport.lastRequest().body.orEmpty().contains("\"p_action\":\"delete\""))
    }

    @Test
    fun `manage edit 携带或不携带可见范围`() = runTest {
        val transport = FakeTransport { ApiResponse(200, detailJson) }
        val repository = repo(transport)
        val edit = PollManageAction.Edit(validDraft())
        repository.manage("pabc123", edit)
        val withAudience = transport.lastRequest().body.orEmpty()
        assertTrue(withAudience.contains("\"p_action\":\"edit\""))
        assertTrue(withAudience.contains("\"p_audience\":\"custom\""))

        repository.manage("pabc123", edit.copy(updateAudience = false))
        val withoutAudience = transport.lastRequest().body.orEmpty()
        assertFalse(withoutAudience.contains("\"p_audience\""))
        assertFalse(withoutAudience.contains("p_audience_seats"))
    }

    @Test
    fun `manage 无权限与非删除成功返回详情`() = runTest {
        val transport = FakeTransport { ApiResponse(200, """{"ok":false,"reason":"无权管理该投票"}""") }
        assertEquals(
            VoteError.NoPermission,
            (repo(transport).manage("pabc123", PollManageAction.Close) as VoteResult.Failure).error,
        )
    }

    // ------------------------------------------------------------ 通用错误/边界

    @Test
    fun `错误 reason 映射表`() {
        assertEquals(VoteError.NotFound, VoteErrors.fromReason("投票不存在"))
        assertEquals(VoteError.OutOfAudience, VoteErrors.fromReason("不在可见范围内"))
        assertEquals(VoteError.Closed, VoteErrors.fromReason("投票已结束"))
        assertEquals(VoteError.DuplicateChoices, VoteErrors.fromReason("重复的选项"))
        assertEquals(VoteError.SingleChoiceOnly, VoteErrors.fromReason("只能选 1 项"))
        assertEquals(VoteError.LimitExceeded(3), VoteErrors.fromReason("最多选 3 项"))
        assertEquals(VoteError.EditLocked, VoteErrors.fromReason("已有投票，不能修改内容"))
        assertEquals(VoteError.Validation("标题需 1–80 字"), VoteErrors.fromReason("标题需 1–80 字"))
        assertEquals(VoteError.Unknown("操作失败，请重试"), VoteErrors.fromReason("操作失败，请重试"))
        assertTrue(VoteErrors.fromReason("") is VoteError.Unknown)
    }

    @Test
    fun `坏 JSON HTTP 错误与未登录处理`() = runTest {
        val malformed = FakeTransport { ApiResponse(200, "not-json") }
        assertTrue(
            (repo(malformed).list(force = true) as VoteResult.Failure).error is VoteError.Validation,
        )

        val unauthorized = FakeTransport { ApiResponse(401, "{}") }
        assertEquals(
            VoteError.IdentityInvalid,
            (repo(unauthorized).list(force = true) as VoteResult.Failure).error,
        )

        val noSid = FakeTransport { ApiResponse(200, listJson) }
        val failure = repo(noSid, sid = null).list(force = true) as VoteResult.Failure
        assertEquals(VoteError.IdentityInvalid, failure.error)
        assertTrue(noSid.requests.isEmpty())
    }

    @Test
    fun `解析器容错 坏投票行跳过`() {
        val parsed = VoteParser.parseList(
            """{"now":1,"polls":[{"id":""},{"id":"p1","title":"ok"}]}""".toJsonObject(),
        )
        assertEquals(1, parsed.polls.size)
        assertEquals("p1", parsed.polls.single().id)
        assertTrue(VoteParser.parseSummary(null) == null)
    }

    private fun String.toJsonObject(): kotlinx.serialization.json.JsonObject =
        com.furina.classtools.data.network.NetJson.parseToJsonElement(this) as kotlinx.serialization.json.JsonObject
}
