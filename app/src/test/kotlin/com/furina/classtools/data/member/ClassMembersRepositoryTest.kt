package com.furina.classtools.data.member

import com.furina.classtools.FakeTransport
import com.furina.classtools.createTempDir
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.testApiClient
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassMembersRepositoryTest {

    private val membersJson = """
        [
          {"seat":2,"sid":"2024000003","name":"李四","role":"","badge":"","is_admin":false},
          {"seat":1,"sid":"2024000002","name":"张三","role":"副团支书","badge":"班委","is_admin":false},
          {"seat":3,"sid":"","name":"  ","role":"","badge":"","is_admin":false},
          {"seat":4,"sid":"2024000005","name":"陈同学","role":"","badge":"","is_admin":true,"extra":"ignored"}
        ]
    """.trimIndent()

    private fun repository(
        transport: FakeTransport,
        dir: File = createTempDir(),
        sid: String = "2024000002",
        clock: () -> Long = { 1_700_000_000_000L },
    ): DefaultClassMembersRepository = DefaultClassMembersRepository(
        api = testApiClient(transport),
        cache = FileClassMemberCache(dir),
        currentSid = { sid },
        clock = clock,
    )

    @Test
    fun `解析名单并按座号排序、过滤坏行`() {
        val members = DefaultClassMembersRepository.parseMembers(
            com.furina.classtools.data.network.NetJson,
            membersJson,
        )

        assertEquals(3, members.size)
        assertEquals(listOf(1, 2, 4), members.map { it.seat })
        assertEquals("张三", members[0].name)
        assertEquals("副团支书", members[0].role)
        assertTrue(members[0].isCommittee)
        assertTrue(members[2].isAdmin)
    }

    @Test
    fun `解析兼容 data 包裹`() {
        val wrapped = """{"data":$membersJson}"""
        val members = DefaultClassMembersRepository.parseMembers(
            com.furina.classtools.data.network.NetJson,
            wrapped,
        )
        assertEquals(3, members.size)
    }

    @Test
    fun `首次加载走网络并写缓存、TTL 内 0 请求`() = runTest {
        val transport = FakeTransport { ApiResponse(200, membersJson) }
        val repo = repository(transport).let { it }

        val first = repo.load() as ClassMembersResult.Success
        assertEquals(3, first.members.size)
        assertEquals(false, first.fromCache)
        assertEquals(1, transport.requests.size)
        assertEquals("POST", transport.lastRequest().method.wire)
        assertTrue(transport.lastRequest().url.endsWith("rpc/class_members"))
        assertEquals("{}", transport.lastRequest().body)

        val second = repo.load() as ClassMembersResult.Success
        assertEquals(true, second.fromCache)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `force 时绕过 TTL 重新请求`() = runTest {
        val transport = FakeTransport { ApiResponse(200, membersJson) }
        val repo = repository(transport)

        repo.load()
        repo.load(force = true)

        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `空数组视为失败并保留旧缓存`() = runTest {
        var payload = membersJson
        val transport = FakeTransport { ApiResponse(200, payload) }
        val repo = repository(transport)
        repo.load()

        payload = "[]"
        val result = repo.load(force = true) as ClassMembersResult.Success

        assertEquals(true, result.fromCache)
        assertEquals(3, result.members.size)
    }

    @Test
    fun `网络失败降级旧缓存`() = runTest {
        var fail = false
        val transport = FakeTransport {
            if (fail) throw IOException("offline") else ApiResponse(200, membersJson)
        }
        val repo = repository(transport)
        repo.load()

        fail = true
        val result = repo.load(force = true) as ClassMembersResult.Success

        assertEquals(true, result.fromCache)
        assertEquals(3, result.members.size)
    }

    @Test
    fun `无缓存且网络失败返回 Failure`() = runTest {
        val transport = FakeTransport { throw IOException("offline") }
        val repo = repository(transport)

        val result = repo.load()

        assertTrue(result is ClassMembersResult.Failure)
    }

    @Test
    fun `落盘缓存按 sid 隔离且新实例可直接读`() = runTest {
        val dir = createTempDir()
        val transportA = FakeTransport { ApiResponse(200, membersJson) }
        val repoA = repository(transportA, dir = dir, sid = "2024000002")
        repoA.load()

        // 同 sid 新实例：TTL 内直接命中文件缓存，0 请求。
        val transportA2 = FakeTransport { ApiResponse(200, "[]") }
        val repoA2 = repository(transportA2, dir = dir, sid = "2024000002")
        val cached = repoA2.load() as ClassMembersResult.Success
        assertEquals(true, cached.fromCache)
        assertEquals(0, transportA2.requests.size)

        // 换 sid：不能命中别人的缓存。
        val transportB = FakeTransport { ApiResponse(200, """[{"seat":1,"sid":"x","name":"王五"}]""") }
        val repoB = repository(transportB, dir = dir, sid = "2024000004")
        val loadedB = repoB.load() as ClassMembersResult.Success
        assertEquals(false, loadedB.fromCache)
        assertEquals("王五", loadedB.members.single().name)

        assertEquals(2, dir.listFiles()?.count { it.name.startsWith("members_") })
    }

    @Test
    fun `cached 不发请求`() = runTest {
        val transport = FakeTransport { ApiResponse(200, membersJson) }
        val repo = repository(transport)
        repo.load()
        val before = transport.requests.size

        val cached = repo.cached()

        assertEquals(3, cached.size)
        assertEquals(before, transport.requests.size)
    }

    @Test
    fun `clear 清空本机缓存`() = runTest {
        val transport = FakeTransport { ApiResponse(200, membersJson) }
        val repo = repository(transport)
        repo.load()
        repo.clear()

        assertEquals(0, repo.cached().size)
        // 再次 load 必须重新请求
        val before = transport.requests.size
        repo.load()
        assertEquals(before + 1, transport.requests.size)
    }
}
