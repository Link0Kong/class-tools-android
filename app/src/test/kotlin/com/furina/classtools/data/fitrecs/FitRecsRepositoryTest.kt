package com.furina.classtools.data.fitrecs

import com.furina.classtools.FakeTransport
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.HttpMethod
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.features.fit.FitCodec
import com.furina.classtools.features.fit.FitFields
import com.furina.classtools.features.fit.FitRecord
import com.furina.classtools.testApiClient
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FitRecsRepositoryTest {

    private val sid = "2024000001"

    private fun record(
        id: String,
        ts: Long,
        name: String = "",
        h: String = "175",
    ) = FitRecord(
        id = id,
        ts = ts,
        name = name,
        sex = "m",
        gradeBucket = "12",
        fields = FitFields(h = h),
        total = 78.5,
        grade = "及格",
    )

    private fun rowJson(record: FitRecord, arch: Boolean = false): String = buildJsonObject {
        put("id", record.id)
        put("acc", sid)
        put("data", FitCodec.recordToData(record))
        put("arch", arch)
        put("ts", record.ts)
    }.toString()

    private fun rowsJson(vararg rows: String): String = rows.joinToString(prefix = "[", postfix = "]")

    private class Fixture(
        var loadBody: String = "[]",
        var failGet: Boolean = false,
        var failPost: Boolean = false,
        var failPatch: Boolean = false,
    )

    private fun transportOf(fixture: Fixture): FakeTransport = FakeTransport { request ->
        when {
            request.method == HttpMethod.GET && request.url.contains("fitrecs?select=") -> {
                if (fixture.failGet) throw IOException("offline")
                ApiResponse(200, fixture.loadBody)
            }

            request.method == HttpMethod.POST && request.url.endsWith("/fitrecs") -> {
                if (fixture.failPost) throw IOException("offline")
                ApiResponse(201, "[]")
            }

            request.method == HttpMethod.PATCH && request.url.contains("fitrecs?id=eq.") -> {
                if (fixture.failPatch) throw IOException("offline")
                ApiResponse(204, "")
            }

            else -> ApiResponse(404, "")
        }
    }

    private fun repository(
        transport: FakeTransport,
        clock: () -> Long = { 1_700_000_000_000L },
    ) = DefaultFitRecsRepository(
        api = testApiClient(transport),
        currentSid = { sid },
        clock = clock,
    )

    // ------------------------------------------------------------ 读

    @Test
    fun `加载解析 data JSON 字符串 按 ts 降序最多 2 条`() = runTest {
        val fixture = Fixture(
            loadBody = rowsJson(
                rowJson(record("f_a", 100)),
                rowJson(record("f_c", 300)),
                rowJson(record("f_b", 200)),
            ),
        )
        val transport = transportOf(fixture)
        val repo = repository(transport)

        val result = repo.load(force = true) as FitRecsLoadResult.Success

        assertFalse(result.fromCache)
        assertEquals(listOf("f_c", "f_b"), result.records.map { it.id })
        assertEquals("175", result.records.first().fields.h)
        assertEquals(1, transport.requests.size)
        assertEquals(
            "https://example.invalid/v1/rdb/rest/fitrecs?select=*&acc=eq.$sid&arch=eq.false&order=ts.desc",
            transport.lastRequest().url,
        )
        assertEquals("test-token", transport.lastRequest().headers["X-Class-Token"])
    }

    @Test
    fun `arch 行与坏 data 行被跳过`() = runTest {
        val rows = rowsJson(
            rowJson(record("f_arch", 300), arch = true),
            """{"id":"f_bad","acc":"$sid","data":"not-json","arch":false,"ts":200}""",
            """{"id":"","data":"{}"}""",
            rowJson(record("f_ok", 100)),
        )
        val repo = repository(transportOf(Fixture(loadBody = rows)))

        val result = repo.load(force = true) as FitRecsLoadResult.Success

        assertEquals(listOf("f_ok"), result.records.map { it.id })
    }

    @Test
    fun `空数组是合法结果`() = runTest {
        val repo = repository(transportOf(Fixture(loadBody = "[]")))
        val result = repo.load(force = true) as FitRecsLoadResult.Success
        assertTrue(result.records.isEmpty())
        assertFalse(result.fromCache)
    }

    @Test
    fun `非数组坏响应且无缓存时失败`() = runTest {
        val repo = repository(transportOf(Fixture(loadBody = """{"bad":1}""")))
        assertTrue(repo.load(force = true) is FitRecsLoadResult.Failure)
    }

    @Test
    fun `TTL 内不重复请求`() = runTest {
        var now = 1_000_000L
        val fixture = Fixture(loadBody = rowsJson(rowJson(record("f_a", 100))))
        val transport = transportOf(fixture)
        val repo = repository(transport) { now }

        repo.load()
        now += DefaultFitRecsRepository.DEFAULT_TTL_MS - 1
        val second = repo.load() as FitRecsLoadResult.Success

        assertEquals(1, transport.requests.size)
        assertTrue(second.fromCache)
    }

    @Test
    fun `网络失败降级本机缓存`() = runTest {
        val fixture = Fixture(loadBody = rowsJson(rowJson(record("f_a", 100))))
        val repo = repository(transportOf(fixture))
        repo.load(force = true)

        fixture.failGet = true
        val result = repo.load(force = true) as FitRecsLoadResult.Success

        assertTrue(result.fromCache)
        assertEquals(listOf("f_a"), result.records.map { it.id })
    }

    // ------------------------------------------------------------ 写

    @Test
    fun `保存走 POST upsert 并软删被挤出的旧记录`() = runTest {
        val fixture = Fixture(loadBody = "[]")
        val transport = transportOf(fixture)
        val repo = repository(transport)
        val fresh = record("f_new", 500)
        val old = record("f_old", 400)

        val result = repo.save(
            record = fresh,
            kept = listOf(fresh, old),
            dropped = listOf(record("f_gone", 300)),
        ) as FitRecsWriteResult.Success

        assertTrue(result.synced)
        val post = transport.requests.first { it.method == HttpMethod.POST }
        assertEquals("https://example.invalid/v1/rdb/rest/fitrecs", post.url)
        assertEquals(
            DefaultFitRecsRepository.PREFER_UPSERT,
            post.headers["Prefer"],
        )
        val body = post.body.orEmpty()
        assertTrue(body.trimStart().startsWith("["))
        assertTrue(body.contains("\"id\":\"f_new\""))
        assertTrue(body.contains("\"acc\":\"$sid\""))
        assertTrue(body.contains("\"data\":"))
        assertTrue(body.contains("\"arch\":false"))
        val patch = transport.requests.first { it.method == HttpMethod.PATCH }
        assertTrue(patch.url.contains("f_gone"))
        assertEquals(DefaultFitRecsRepository.ARCHIVE_BODY, patch.body)
        assertEquals(0, repo.pendingCount())
    }

    @Test
    fun `保存失败写本机 pending 且 flush 后补发`() = runTest {
        val fixture = Fixture(loadBody = "[]", failPost = true)
        val transport = transportOf(fixture)
        val repo = repository(transport)
        val fresh = record("f_new", 500)

        val first = repo.save(fresh, listOf(fresh)) as FitRecsWriteResult.Success
        assertFalse(first.synced)
        assertEquals(1, repo.pendingCount())

        fixture.failPost = false
        assertEquals(1, repo.flushPending())
        assertEquals(0, repo.pendingCount())
    }

    @Test
    fun `改名按 id upsert 新 data`() = runTest {
        val fixture = Fixture(loadBody = rowsJson(rowJson(record("f_a", 100))))
        val transport = transportOf(fixture)
        val repo = repository(transport)
        repo.load(force = true)

        val renamed = record("f_a", 100).copy(name = "补测")
        val result = repo.rename(renamed) as FitRecsWriteResult.Success
        assertTrue(result.synced)
        val post = transport.requests.first { it.method == HttpMethod.POST }
        val array = NetJson.parseToJsonElement(post.body.orEmpty()) as kotlinx.serialization.json.JsonArray
        val obj = array[0] as kotlinx.serialization.json.JsonObject
        val data = (obj["data"] as kotlinx.serialization.json.JsonPrimitive).content
        assertEquals("补测", FitCodec.recordFromData(data)!!.name)
    }

    @Test
    fun `软删成功并移出缓存`() = runTest {
        val fixture = Fixture(loadBody = rowsJson(rowJson(record("f_a", 100))))
        val transport = transportOf(fixture)
        val repo = repository(transport)
        repo.load(force = true)

        val result = repo.archive("f_a") as FitRecsWriteResult.Success
        assertTrue(result.synced)
        val patch = transport.requests.last()
        assertEquals(HttpMethod.PATCH, patch.method)
        assertTrue(patch.url.contains("fitrecs?id=eq.f_a"))
        assertEquals(0, repo.pendingCount())

        fixture.loadBody = "[]"
        val after = repo.load(force = true) as FitRecsLoadResult.Success
        assertTrue(after.records.isEmpty())
    }

    @Test
    fun `软删失败进入 pending 并可补发`() = runTest {
        val fixture = Fixture(loadBody = rowsJson(rowJson(record("f_a", 100))), failPatch = true)
        val repo = repository(transportOf(fixture))
        repo.load(force = true)

        val result = repo.archive("f_a") as FitRecsWriteResult.Success
        assertFalse(result.synced)
        assertEquals(1, repo.pendingCount())

        fixture.failPatch = false
        assertEquals(1, repo.flushPending())
        assertEquals(0, repo.pendingCount())
    }

    @Test
    fun `保存失败时被挤出记录也进入 pending`() = runTest {
        val fixture = Fixture(loadBody = "[]", failPost = true, failPatch = true)
        val repo = repository(transportOf(fixture))
        val fresh = record("f_new", 500)

        repo.save(fresh, listOf(fresh), dropped = listOf(record("f_gone", 400)))
        // 1 条保存 + 1 条软删
        assertEquals(2, repo.pendingCount())
    }

    // ------------------------------------------------------------ 纯解析

    @Test
    fun `parseRows 容错与 recordsFromRows 排序截断`() {
        val rows = rowsJson(
            rowJson(record("f_a", 100)),
            rowJson(record("f_b", 300)),
            rowJson(record("f_c", 200)),
            """{"noId":true}""",
            "not-an-object",
        )
        val parsed = DefaultFitRecsRepository.parseRows(NetJson, rows)!!
        assertEquals(listOf("f_a", "f_b", "f_c"), parsed.map { it.id })
        assertEquals(listOf("f_b", "f_c"), DefaultFitRecsRepository.recordsFromRows(parsed).map { it.id })
        assertNull(DefaultFitRecsRepository.parseRows(NetJson, "{}"))
        assertNull(DefaultFitRecsRepository.parseRows(NetJson, "not-json"))
    }

    @Test
    fun `buildUpsertBody 是数组且 data 为 JSON 字符串`() {
        val body = DefaultFitRecsRepository.buildUpsertBody(sid, listOf(record("f_a", 100)))
        assertTrue(body.trimStart().startsWith("["))
        val parsed = NetJson.parseToJsonElement(body)
        assertTrue(parsed is kotlinx.serialization.json.JsonArray)
        val obj = (parsed as kotlinx.serialization.json.JsonArray)[0] as kotlinx.serialization.json.JsonObject
        assertEquals("f_a", (obj["id"] as kotlinx.serialization.json.JsonPrimitive).content)
        val data = (obj["data"] as kotlinx.serialization.json.JsonPrimitive).content
        // data 是字符串，内部是对象 JSON
        assertEquals("f_a", FitCodec.recordFromData(data)!!.id)
    }
}
