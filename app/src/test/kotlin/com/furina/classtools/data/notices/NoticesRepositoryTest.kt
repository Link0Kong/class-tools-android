package com.furina.classtools.data.notices

import com.furina.classtools.FakeTransport
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.testApiClient
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticesRepositoryTest {

    private val liteJson = """[{"id":"auto_g_1","ts":100,"hidden":false,"edited_at":0}]"""

    private fun fullJson(
        autoTitle: String = "班会",
        hidden: Boolean = false,
        overrideJson: String = "",
        bodyPrefix: String = NOTICE_BODY_PREFIX,
    ): String = """
        [
          {"id":"auto_g_1","title":"","body":"${escapeForJson(bodyPrefix + autoGroupJson(autoTitle))}",
           "ts":100,"hidden":$hidden,"edited_at":0,"override_json":"${escapeForJson(overrideJson)}"},
          {"id":"manual_1","title":"班费公示","body":"正文","ts":50,"hidden":false,
           "attachments":"[{\"n\":\"a.pdf\",\"t\":\"application/pdf\",\"s\":10}]"}
        ]
    """.trimIndent()

    private fun autoGroupJson(title: String): String =
        """{"id":"g_1","title":"$title","cat":"班会","aiSummary":"摘要","deadline":"2026-11-08T16:00:00+08:00"}"""

    private fun escapeForJson(text: String): String =
        text.replace("\\", "\\\\").replace("\"", "\\\"")

    private class Fixture(
        var lite: String = "",
        var full: String = "",
        var liteFail: Boolean = false,
        var fullFail: Boolean = false,
    )

    private fun repository(
        transport: FakeTransport,
        isAdmin: Boolean = false,
        clock: () -> Long = { 1_700_000_000_000L },
    ) = DefaultNoticesRepository(
        api = testApiClient(transport),
        isAdmin = { isAdmin },
        clock = clock,
        // 本文件覆盖**旧两级链路**（签名 → 分页正文）；light meta 优先/回退见 NoticesMetaTest。
        metaEnabled = false,
    )

    private fun transportOf(fixture: Fixture): FakeTransport = FakeTransport { request ->
        when {
            request.url.contains("select=id,ts,hidden,edited_at") -> {
                if (fixture.liteFail) throw IOException("offline") else ApiResponse(200, fixture.lite)
            }
            else -> {
                if (fixture.fullFail) throw IOException("offline") else ApiResponse(200, fixture.full)
            }
        }
    }

    @Test
    fun `首次加载走两级 签名后拉整表`() = runTest {
        val fixture = Fixture(lite = liteJson, full = fullJson())
        val transport = transportOf(fixture)
        val repo = repository(transport)

        val result = repo.load(force = true)

        val data = (result as NoticesLoadResult.Success).data
        assertEquals(1, data.auto.size)
        assertEquals(1, data.manual.size)
        assertEquals("班会", data.auto.single().title)
        assertEquals(1, data.manual.single().attachments.size)
        assertEquals(2, transport.requests.size)
        // 第一级路径与字段
        assertTrue(transport.requests[0].url.contains("notices?select=id,ts,hidden,edited_at"))
        // 第二级路径
        assertTrue(transport.requests[1].url.contains("notices?select=*"))
        assertEquals("test-token", transport.requests[1].headers["X-Class-Token"])
    }

    @Test
    fun `签名未变时不再拉整表`() = runTest {
        val fixture = Fixture(lite = liteJson, full = fullJson())
        val transport = transportOf(fixture)
        val repo = repository(transport)

        repo.load(force = true) // lite + full
        repo.load(force = true) // lite 命中签名

        assertEquals(3, transport.requests.size)
        assertEquals(1, transport.requests.count { it.url.contains("select=*") })
    }

    @Test
    fun `签名变化时重新拉整表`() = runTest {
        val fixture = Fixture(lite = liteJson, full = fullJson())
        val transport = transportOf(fixture)
        val repo = repository(transport)
        repo.load(force = true)

        fixture.lite = """[{"id":"auto_g_1","ts":999,"hidden":false,"edited_at":0}]"""
        repo.load(force = true)

        assertEquals(2, transport.requests.count { it.url.contains("select=*") })
    }

    @Test
    fun `TTL 内直接返回缓存不发请求`() = runTest {
        var now = 1_000_000L
        val fixture = Fixture(lite = liteJson, full = fullJson())
        val transport = transportOf(fixture)
        val repo = repository(transport) { now }

        repo.load()
        now += DefaultNoticesRepository.DEFAULT_TTL_MS - 1
        val second = repo.load() as NoticesLoadResult.Success

        assertEquals(2, transport.requests.size)
        assertTrue(second.data.fromCache)
    }

    @Test
    fun `hidden 对非管理员过滤 对管理员保留并计数`() = runTest {
        val fixture = Fixture(lite = liteJson, full = fullJson(hidden = true))
        val nonAdmin = repository(transportOf(fixture), isAdmin = false)
        val data = (nonAdmin.load(force = true) as NoticesLoadResult.Success).data
        assertEquals(0, data.auto.size)
        assertEquals(1, data.hiddenCount)

        val fixture2 = Fixture(lite = liteJson, full = fullJson(hidden = true))
        val admin = repository(transportOf(fixture2), isAdmin = true)
        val adminData = (admin.load(force = true) as NoticesLoadResult.Success).data
        assertEquals(1, adminData.auto.size)
        assertTrue(adminData.auto.single().hidden)
        assertEquals(1, adminData.hiddenCount)
    }

    @Test
    fun `override_json 应用到 group`() = runTest {
        val fixture = Fixture(
            lite = liteJson,
            full = fullJson(overrideJson = """{"title":"管理员改过的标题","aiSummary":null}"""),
        )
        val repo = repository(transportOf(fixture))
        val data = (repo.load(force = true) as NoticesLoadResult.Success).data
        val entry = data.auto.single()
        assertEquals("管理员改过的标题", entry.title)
        assertEquals("", entry.group.aiSummary)
        // edited_at=0 时不算「管理员编辑过」（override 本身不置 edited 标记）
        assertFalse(entry.edited)
    }

    @Test
    fun `坏 body 的自动行被跳过 不影响手动行`() = runTest {
        val fixture = Fixture(
            lite = liteJson,
            full = """[
              {"id":"auto_g_1","body":"$NOTICE_BODY_PREFIX{bad","ts":100},
              {"id":"manual_1","title":"人工","body":"正文","ts":50}
            ]""",
        )
        val repo = repository(transportOf(fixture))
        val data = (repo.load(force = true) as NoticesLoadResult.Success).data
        assertEquals(0, data.auto.size)
        assertEquals(1, data.manual.size)
    }

    @Test
    fun `第一级失败时有缓存则降级`() = runTest {
        val fixture = Fixture(lite = liteJson, full = fullJson())
        val repo = repository(transportOf(fixture))
        repo.load(force = true)

        fixture.liteFail = true
        val second = repo.load(force = true) as NoticesLoadResult.Success
        assertTrue(second.data.fromCache)
        assertEquals(1, second.data.auto.size)
    }

    @Test
    fun `无缓存时两级失败返回 Failure`() = runTest {
        val fixture = Fixture(liteFail = true)
        val repo = repository(transportOf(fixture))
        assertTrue(repo.load(force = true) is NoticesLoadResult.Failure)

        val fixture2 = Fixture(lite = liteJson, fullFail = true)
        val repo2 = repository(transportOf(fixture2))
        assertTrue(repo2.load(force = true) is NoticesLoadResult.Failure)
    }

    @Test
    fun `签名解析与整表解析的容错`() {
        assertEquals(1, DefaultNoticesRepository.parseSigRows(com.furina.classtools.data.network.NetJson, liteJson)!!.size)
        assertEquals(null, DefaultNoticesRepository.parseSigRows(com.furina.classtools.data.network.NetJson, "{}"))
        assertEquals(null, DefaultNoticesRepository.parseRows(com.furina.classtools.data.network.NetJson, "oops"))

        val rows = DefaultNoticesRepository.parseRows(
            com.furina.classtools.data.network.NetJson,
            """[{"id":"a"},{"noId":true},"x"]""",
        )!!
        assertEquals(listOf("a"), rows.map { it.id })
    }

    @Test
    fun `cached 返回最近一次结果`() = runTest {
        val fixture = Fixture(lite = liteJson, full = fullJson())
        val repo = repository(transportOf(fixture))
        assertNotNull(repo.load(force = true))
        val cached = repo.cached()
        assertNotNull(cached)
        assertFalse(cached!!.fromCache)
        assertEquals(1, cached.auto.size)
    }
}
