package com.furina.classtools.data.member

import com.furina.classtools.FakeTransport
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.HttpMethod
import com.furina.classtools.testApiClient
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassLeagueRepositoryTest {

    private val now: Long = 1_760_000_000_000L

    private fun repo(transport: FakeTransport, sid: String? = "2024000002"): DefaultClassLeagueRepository =
        DefaultClassLeagueRepository(
            api = testApiClient(transport),
            currentSid = { sid },
            clock = { now },
        )

    private val leagueJson = """
        {"ok":true,"count":3,"seats":[
          {"seat":5,"name":"王五"},
          {"seat":2,"name":"李四"},
          {"seat":2,"name":"李四重复"},
          {"seat":0,"name":"坏行"},
          {"seat":9,"name":""}
        ]}
    """.trimIndent()

    // ------------------------------------------------------------ 解析

    @Test
    fun `解析团员名单 排序去重过滤坏行`() {
        val members = DefaultClassLeagueRepository.parseLeague(leagueJson)
        assertNotNull(members)
        assertEquals(listOf(2, 5), members!!.map { it.seat })
        assertEquals(listOf("李四", "王五"), members.map { it.name })
    }

    @Test
    fun `解析空名单与失败响应`() {
        assertEquals(emptyList<LeagueMember>(), DefaultClassLeagueRepository.parseLeague("""{"ok":true,"seats":[]}"""))
        assertNull(DefaultClassLeagueRepository.parseLeague("""{"ok":false,"reason":"未登录"}"""))
        assertNull(DefaultClassLeagueRepository.parseLeague("not-json"))
        assertNull(DefaultClassLeagueRepository.parseLeague("""{"ok":true}"""))
    }

    @Test
    fun `模型不含学号字段`() {
        val fieldNames = LeagueMember::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(fieldNames.any { it.contains("sid") })
        assertTrue(fieldNames.contains("seat"))
        assertTrue(fieldNames.contains("name"))
    }

    // ------------------------------------------------------------ 仓库

    @Test
    fun `加载成功并请求 class_league`() = runTest {
        val transport = FakeTransport { ApiResponse(200, leagueJson) }
        val result = repo(transport).load(force = true) as ClassLeagueResult.Success
        assertFalse(result.fromCache)
        assertEquals(2, result.members.size)
        assertEquals(HttpMethod.POST, transport.lastRequest().method)
        assertTrue(transport.lastRequest().url.endsWith("/rpc/class_league"))
        assertEquals("{}", transport.lastRequest().body)
    }

    @Test
    fun `TTL 内走缓存`() = runTest {
        val transport = FakeTransport { ApiResponse(200, leagueJson) }
        val repository = repo(transport)
        repository.load(force = true)
        val second = repository.load(force = false) as ClassLeagueResult.Success
        assertTrue(second.fromCache)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `网络失败降级缓存并提示`() = runTest {
        var fail = false
        val transport = FakeTransport {
            if (fail) throw IOException("offline") else ApiResponse(200, leagueJson)
        }
        val repository = repo(transport)
        repository.load(force = true)
        fail = true
        val result = repository.load(force = true) as ClassLeagueResult.Success
        assertTrue(result.fromCache)
        assertNotNull(result.warning)
        assertEquals(2, result.members.size)
    }

    @Test
    fun `无缓存失败返回错误`() = runTest {
        val transport = FakeTransport { throw IOException("offline") }
        val failure = repo(transport).load(force = true) as ClassLeagueResult.Failure
        assertTrue(failure.message.isNotBlank())
    }

    @Test
    fun `坏响应映射格式错误`() = runTest {
        val transport = FakeTransport { ApiResponse(200, "not-json") }
        val failure = repo(transport).load(force = true) as ClassLeagueResult.Failure
        assertEquals("团员名单格式异常", failure.message)
    }

    @Test
    fun `清空缓存后重新请求`() = runTest {
        val transport = FakeTransport { ApiResponse(200, leagueJson) }
        val repository = repo(transport)
        repository.load(force = true)
        assertEquals(2, repository.cached().size)
        repository.clear()
        assertTrue(repository.cached().isEmpty())
        repository.load(force = false)
        assertEquals(2, transport.requests.size)
    }
}

class MemberCategoryBuilderTest {

    private fun classMember(
        seat: Int,
        name: String,
        role: String = "",
        badge: String = "",
    ): ClassMember = ClassMember(seat = seat, sid = "sid-$seat", name = name, role = role, badge = badge)

    private val classMembers = listOf(
        classMember(3, "张三", role = "班长", badge = "班委"),
        classMember(1, "李四", role = "", badge = "班委"),
        classMember(2, "王五", role = "", badge = ""),
        classMember(7, "赵六", role = "团支书"),
        classMember(5, "钱七", role = "", badge = "学生"),
    )

    private val leagueMembers = listOf(
        LeagueMember(3, "张三"),
        LeagueMember(7, "赵六"),
        LeagueMember(9, "周八"),
    )

    @Test
    fun `班委判定与服务端同口径`() {
        assertTrue(MemberCategoryBuilder.isCommittee(classMember(1, "A", role = "班长")))
        assertTrue(MemberCategoryBuilder.isCommittee(classMember(2, "B", badge = "班委")))
        assertFalse(MemberCategoryBuilder.isCommittee(classMember(3, "C", badge = "学生")))
    }

    @Test
    fun `班委名单按座号升序且 badge 回退`() {
        val committee = MemberCategoryBuilder.committee(classMembers)
        assertEquals(listOf(1, 3, 7), committee.map { it.seat })
        assertEquals("班长", committee.first { it.seat == 3 }.badge)
        assertEquals("班委", committee.first { it.seat == 1 }.badge)
        assertEquals("团支书", committee.first { it.seat == 7 }.badge)
    }

    @Test
    fun `团员名单与全班名单`() {
        val league = MemberCategoryBuilder.league(leagueMembers)
        assertEquals(listOf(3, 7, 9), league.map { it.seat })
        val all = MemberCategoryBuilder.all(classMembers)
        assertEquals(listOf(1, 2, 3, 5, 7), all.map { it.seat })
        assertEquals("班长", all.first { it.seat == 3 }.badge)
        assertEquals("", all.first { it.seat == 2 }.badge)
    }

    @Test
    fun `三分类计数与 entries`() {
        val counts = MemberCategoryBuilder.counts(classMembers, leagueMembers)
        assertEquals(3, counts.committee)
        assertEquals(3, counts.league)
        assertEquals(5, counts.all)
        assertEquals(3, counts.of(MemberCategory.COMMITTEE))
        assertEquals(5, counts.of(MemberCategory.ALL))
        assertEquals(3, MemberCategoryBuilder.entries(MemberCategory.LEAGUE, classMembers, leagueMembers).size)
        assertEquals(5, MemberCategoryBuilder.entries(MemberCategory.ALL, classMembers, leagueMembers).size)
    }

    @Test
    fun `姓名搜索与可见名单`() {
        val all = MemberCategoryBuilder.all(classMembers)
        assertEquals(1, MemberCategoryBuilder.search(all, "张").size)
        assertEquals(5, MemberCategoryBuilder.search(all, "").size)
        assertEquals(1, MemberCategoryBuilder.search(all, " 赵 ").size)
        assertTrue(MemberCategoryBuilder.search(all, "不存在").isEmpty())
        assertEquals(1, MemberCategoryBuilder.visibleEntries(MemberCategory.COMMITTEE, classMembers, leagueMembers, "张").size)
    }

    @Test
    fun `名单条目模型不含学号且过滤坏数据`() {
        val fieldNames = MemberEntry::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(fieldNames.any { it.contains("sid") })
        assertTrue(fieldNames.contains("seat"))
        assertTrue(fieldNames.contains("name"))
        assertTrue(
            MemberCategoryBuilder.all(
                listOf(
                    ClassMember(seat = 0, sid = "x", name = "无座"),
                    ClassMember(seat = 1, sid = "y", name = ""),
                    ClassMember(seat = 2, sid = "z", name = "有效"),
                ),
            ).map { it.seat } == listOf(2),
        )
        assertEquals(emptyList<MemberEntry>(), MemberCategoryBuilder.league(emptyList()))
    }
}
