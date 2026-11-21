package com.furina.classtools.data.privacy

import com.furina.classtools.FakeTransport
import com.furina.classtools.data.marks.MarkKind
import com.furina.classtools.data.marks.MarksRepository
import com.furina.classtools.data.marks.MarksSnapshot
import com.furina.classtools.data.marks.MarksUpdateResult
import com.furina.classtools.data.network.ApiError
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.network.HttpMethod
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.session.Session
import com.furina.classtools.features.fit.FitCodec
import com.furina.classtools.features.fit.FitFields
import com.furina.classtools.features.fit.FitRecord
import com.furina.classtools.testApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyRepositoryTest {

    private val sid = "2024000001"
    private val fixedNow = 1_760_000_000_000L
    private val session = Session(
        sid = sid,
        name = "陈同学",
        token = "secret-token-xyz",
        role = "副团支部书记",
        badge = "班委",
    )

    private fun fitRecord(id: String, ts: Long): FitRecord = FitRecord(
        id = id,
        ts = ts,
        name = "",
        sex = "m",
        gradeBucket = "12",
        fields = FitFields(h = "175"),
        total = 78.5,
        grade = "及格",
    )

    private fun fitRowJson(record: FitRecord, arch: Boolean = false): String = buildJsonObject {
        put("id", record.id)
        put("acc", sid)
        put("data", FitCodec.recordToData(record))
        put("arch", arch)
        put("ts", record.ts)
    }.toString()

    private fun fitRows(vararg rows: String): String = rows.joinToString(prefix = "[", postfix = "]")

    // ------------------------------------------------------------ 假件

    private class FakeMarks(initial: MarksSnapshot = MarksSnapshot()) : MarksRepository {
        private val flow = MutableStateFlow(initial)
        override val marks: StateFlow<MarksSnapshot> = flow
        val setCalls = mutableListOf<Triple<MarkKind, String, Boolean>>()
        var failNextSetMarks: Int = 0

        override suspend fun refresh(force: Boolean): ApiResult<MarksSnapshot> = ApiResult.Success(flow.value)

        override suspend fun setMark(kind: MarkKind, ref: String, value: Boolean): MarksUpdateResult {
            setCalls += Triple(kind, ref, value)
            if (failNextSetMarks > 0) {
                failNextSetMarks -= 1
                return MarksUpdateResult.Failure(ApiError.Http(status = 500, serverMessage = "写入失败"))
            }
            flow.value = flow.value.with(kind, ref, value)
            return MarksUpdateResult.Success
        }

        override suspend fun flushPending(): Int = 0
        override suspend fun pendingCount(): Int = 0
    }

    private class FakeUserDataAccess(
        var sections: JsonObject = JsonObject(emptyMap()),
    ) : PrivacyUserDataAccess {
        var resetCalls = 0
        override suspend fun currentSections(): JsonObject = sections
        override suspend fun resetToDefaults() {
            resetCalls += 1
            sections = UserdataV6Sections.default()
        }
    }

    /** 测试用默认 sections（避免依赖 data.sync 的完整实现）。 */
    private object UserdataV6Sections {
        fun default(): JsonObject = JsonObject(emptyMap())
    }

    private class FakeCleaner(
        private val result: PrivacyLocalResult = PrivacyLocalResult(ok = true, message = "本机数据已清除（2 项）", clearedItems = 2),
    ) : PrivacyLocalDataCleaner {
        var calls = 0
        override suspend fun clearLocalData(): PrivacyLocalResult {
            calls += 1
            return result
        }
    }

    private class Fixture(
        var fitRows: String = "[]",
        var fitGetStatus: Int = 200,
        var userdataPostStatus: Int = 201,
        var fitPatchStatus: Int = 204,
        var failOnPatchIndex: Int = -1,
        var patchCount: Int = 0,
    )

    private fun transportOf(fixture: Fixture): FakeTransport = FakeTransport { request ->
        when {
            request.method == HttpMethod.GET && request.url.contains("fitrecs?select=") ->
                ApiResponse(fixture.fitGetStatus, fixture.fitRows)

            request.method == HttpMethod.POST && request.url.endsWith("/userdata") ->
                ApiResponse(fixture.userdataPostStatus, "[]")

            request.method == HttpMethod.PATCH && request.url.contains("fitrecs?id=eq.") -> {
                val index = fixture.patchCount
                fixture.patchCount += 1
                if (fixture.failOnPatchIndex == index) {
                    ApiResponse(500, "{}")
                } else {
                    ApiResponse(fixture.fitPatchStatus, "")
                }
            }

            else -> ApiResponse(404, "{}")
        }
    }

    private fun repository(
        transport: FakeTransport,
        marks: FakeMarks = FakeMarks(),
        userData: FakeUserDataAccess = FakeUserDataAccess(),
        cleaner: FakeCleaner = FakeCleaner(),
        currentSession: Session? = session,
    ) = DefaultPrivacyRepository(
        api = testApiClient(transport),
        marksRepository = marks,
        userDataAccess = userData,
        currentSid = { currentSession?.sid },
        currentSession = { currentSession },
        localCleaner = cleaner,
        clock = { fixedNow },
    )

    private fun sections(): JsonObject = buildJsonObject {
        put("homeTools", buildJsonArray { add(JsonPrimitive("draw")) })
        put("fit", buildJsonObject { put("sex", JsonPrimitive("m")) })
        put("draw", buildJsonObject { put("n", JsonPrimitive(10)) })
        put("group", buildJsonObject { put("n", JsonPrimitive(4)) })
        put(
            "roster",
            buildJsonObject {
                put("rosterSel", buildJsonArray { add(JsonPrimitive(1)) })
                put("rollSel", buildJsonArray { add(JsonPrimitive(1)) })
                put("rollSt", buildJsonObject { put("0", JsonPrimitive("in")) })
                put("rollNames", JsonPrimitive("1 张三\n2 李四"))
            },
        )
        put("hide", buildJsonObject { put("hwHide", buildJsonObject { }) })
        put("prefs", buildJsonObject { put("hwRemind", buildJsonObject { }) })
        put("nick", JsonPrimitive("小陈"))
        put("water", buildJsonObject { put("rooms", buildJsonObject { }) })
        put("power", buildJsonObject { put("sel", JsonPrimitive("1-13---301")) })
        put("tt", buildJsonObject { put("ttView", JsonPrimitive("week")) })
    }

    // ------------------------------------------------------------ 纯函数

    @Test
    fun `学号脱敏`() {
        assertEquals("20***01", PrivacyExportBuilder.maskSid("2024000001"))
        assertEquals("***", PrivacyExportBuilder.maskSid("1234"))
        assertEquals("***", PrivacyExportBuilder.maskSid(""))
        assertEquals("***", PrivacyExportBuilder.maskSid(null))
    }

    @Test
    fun `导出文件名含时间戳`() {
        assertEquals("我的数据_20251009-1653.json", PrivacyExportBuilder.fileName(fixedNow))
    }

    @Test
    fun `userdata 白名单过滤 电费与课表不导出 名单原文清空`() {
        val filtered = PrivacyExportBuilder.filteredSections(sections())
        assertFalse(filtered.containsKey("power"))
        assertFalse(filtered.containsKey("tt"))
        assertTrue(filtered.containsKey("homeTools"))
        assertTrue(filtered.containsKey("water"))
        val roster = filtered["roster"]!!.jsonObject
        assertEquals("", roster["rollNames"]!!.jsonPrimitive.content)
        assertEquals(1, roster["rosterSel"]!!.jsonArray.size)
        assertEquals(
            setOf("homeTools", "fit", "draw", "group", "roster", "hide", "prefs", "nick", "water"),
            filtered.keys,
        )
    }

    @Test
    fun `导出 JSON 脱敏 且不含 token 与学号明文`() = runTest {
        val marks = FakeMarks(
            MarksSnapshot(
                hwDone = mapOf("hw1" to true, "hw2" to false),
                ntDone = mapOf("g1" to true),
                ntPin = mapOf("manual1" to true),
            ),
        )
        val fixture = Fixture(fitRows = fitRows(fitRowJson(fitRecord("f_a", 100))))
        val repo = repository(
            transportOf(fixture),
            marks = marks,
            userData = FakeUserDataAccess(sections()),
        )

        val result = repo.buildExport() as PrivacyExportResult.Success
        val text = result.export.json
        val root = Json.parseToJsonElement(text).jsonObject

        assertFalse(text.contains(sid))
        assertFalse(text.contains("secret-token"))
        assertEquals("20***01", root["identity"]!!.jsonObject["sidMasked"]!!.jsonPrimitive.content)
        assertEquals("陈同学", root["identity"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(3, result.export.summary.marksCount)
        assertEquals(1, result.export.summary.fitRecsCount)
        assertEquals("我的数据_20251009-1653.json", result.export.fileName)

        val exportMarks = root["marks"]!!.jsonObject
        assertEquals(1, exportMarks["hw"]!!.jsonObject["count"]!!.jsonPrimitive.content.toInt())
        assertEquals(3, PrivacyExportBuilder.countMarks(marks.marks.value))
        assertTrue((root["notes"] as JsonArray).isNotEmpty())
        assertFalse(root["userdata"]!!.jsonObject.containsKey("power"))
        assertFalse(root["userdata"]!!.jsonObject.containsKey("tt"))
        assertEquals(1, root["fitrecs"]!!.jsonObject["count"]!!.jsonPrimitive.content.toInt())
        assertNotNull(root["fitrecs"]!!.jsonObject["records"])
    }

    @Test
    fun `导出时 fitrecs 读取失败只记录错误不阻断`() = runTest {
        val fixture = Fixture(fitRows = "{}", fitGetStatus = 500)
        val repo = repository(transportOf(fixture), userData = FakeUserDataAccess(sections()))

        val result = repo.buildExport() as PrivacyExportResult.Success
        val fitrecs = Json.parseToJsonElement(result.export.json).jsonObject["fitrecs"]!!.jsonObject
        assertEquals(0, fitrecs["count"]!!.jsonPrimitive.content.toInt())
        assertTrue(fitrecs.containsKey("error"))
    }

    @Test
    fun `未登录导出失败`() = runTest {
        val repo = repository(transportOf(Fixture()), currentSession = null)
        assertTrue(repo.buildExport() is PrivacyExportResult.Failure)
    }

    @Test
    fun `概览数字来自本机 marks 与 fitrecs`() = runTest {
        val marks = FakeMarks(MarksSnapshot(hwDone = mapOf("a" to true), ntDone = mapOf("b" to true)))
        val fixture = Fixture(
            fitRows = fitRows(fitRowJson(fitRecord("f_a", 100)), fitRowJson(fitRecord("f_b", 200))),
        )
        val repo = repository(transportOf(fixture), marks = marks, userData = FakeUserDataAccess(sections()))

        val summary = repo.loadSummary()

        assertEquals(11, summary.userdataSections)
        assertEquals(2, summary.marksCount)
        assertEquals(2, summary.fitRecsCount)
    }

    @Test
    fun `清除报告可读文案`() {
        val ok = PrivacyClearReport(
            listOf(PrivacyClearItem("userdata", true), PrivacyClearItem("marks", true)),
        )
        assertTrue(ok.allOk)
        assertEquals("云端数据已清除", ok.message)

        val partial = PrivacyClearReport(
            listOf(
                PrivacyClearItem("userdata", true),
                PrivacyClearItem("fitrecs", false, "读取失败：网络不可用"),
            ),
        )
        assertFalse(partial.allOk)
        assertTrue(partial.message.contains("fitrecs"))
        assertTrue(partial.message.contains("网络不可用"))
    }

    // ------------------------------------------------------------ 清除云端

    @Test
    fun `清除云端编排 userdata 空 blob marks 置否 fitrecs 逐条软删`() = runTest {
        val fixture = Fixture(
            fitRows = fitRows(
                fitRowJson(fitRecord("f_a", 100)),
                fitRowJson(fitRecord("f_b", 200)),
                fitRowJson(fitRecord("f_old", 50), arch = true),
            ),
        )
        val transport = transportOf(fixture)
        val marks = FakeMarks(MarksSnapshot(hwDone = mapOf("hw1" to true), ntDone = mapOf("g1" to true)))
        val userData = FakeUserDataAccess(sections())
        val repo = repository(transport, marks = marks, userData = userData)

        val result = repo.clearCloudData() as PrivacyClearCloudResult.Done

        assertTrue(result.report.allOk)
        assertEquals(listOf("userdata", "marks", "fitrecs"), result.report.items.map { it.name })
        // userdata：POST 空 blob + upsert 偏好
        val post = transport.requests.first { it.method == HttpMethod.POST }
        assertEquals("https://example.invalid/v1/rdb/rest/userdata", post.url)
        assertEquals(DefaultPrivacyRepository.PREFER_UPSERT, post.headers["Prefer"])
        val body = Json.parseToJsonElement(post.body!!).jsonArray[0].jsonObject
        assertEquals(sid, body["id"]!!.jsonPrimitive.content)
        val blob = Json.parseToJsonElement(body["blob"]!!.jsonPrimitive.content).jsonObject
        assertEquals(6, blob["v"]!!.jsonPrimitive.content.toInt())
        assertTrue(blob["at"]!!.jsonObject.isNotEmpty())
        assertEquals(0, blob["homeTools"]!!.jsonArray.size)
        assertEquals(1, userData.resetCalls)
        // marks：2 条 true 全部置 false
        assertEquals(2, marks.setCalls.size)
        assertTrue(marks.setCalls.all { !it.third })
        // fitrecs：只 patch arch=false 的两条，且无 DELETE
        val patches = transport.requests.filter { it.method == HttpMethod.PATCH }
        assertEquals(2, patches.size)
        assertTrue(patches.all { it.body == DefaultPrivacyRepository.ARCHIVE_BODY })
        assertTrue(transport.requests.none { it.url.contains("delete", ignoreCase = true) })
    }

    @Test
    fun `清除云端 部分失败返回可读错误`() = runTest {
        val fixture = Fixture(
            fitRows = fitRows(fitRowJson(fitRecord("f_a", 100))),
            userdataPostStatus = 500,
            fitPatchStatus = 500,
        )
        val marks = FakeMarks(MarksSnapshot(hwDone = mapOf("hw1" to true)))
        val repo = repository(transportOf(fixture), marks = marks)

        val result = repo.clearCloudData() as PrivacyClearCloudResult.Done

        assertFalse(result.report.allOk)
        val failed = result.report.failed.map { it.name }
        assertTrue(failed.contains("userdata"))
        assertTrue(failed.contains("fitrecs"))
        assertTrue(result.report.message.contains("userdata"))
        assertTrue(result.report.message.contains("fitrecs"))
    }

    @Test
    fun `清除云端 fitrecs 读取失败不影响其它项`() = runTest {
        val fixture = Fixture(fitGetStatus = 500)
        val repo = repository(transportOf(fixture), marks = FakeMarks())

        val report = (repo.clearCloudData() as PrivacyClearCloudResult.Done).report

        assertEquals(2, report.items.count { it.ok })
        val fit = report.items.first { it.name == "fitrecs" }
        assertFalse(fit.ok)
        assertTrue(fit.detail.contains("读取失败"))
    }

    @Test
    fun `清除云端 marks 单项失败聚合`() = runTest {
        val marks = FakeMarks(
            MarksSnapshot(hwDone = mapOf("hw1" to true, "hw2" to true)),
        ).apply { failNextSetMarks = 1 }
        val repo = repository(transportOf(Fixture()), marks = marks)

        val report = (repo.clearCloudData() as PrivacyClearCloudResult.Done).report
        val marksItem = report.items.first { it.name == "marks" }
        assertFalse(marksItem.ok)
        assertTrue(marksItem.detail.contains("失败 1 项"))
    }

    @Test
    fun `未登录清除云端失败且不发请求`() = runTest {
        val transport = transportOf(Fixture())
        val repo = repository(transport, currentSession = null)

        assertTrue(repo.clearCloudData() is PrivacyClearCloudResult.Failure)
        assertTrue(transport.requests.isEmpty())
    }

    // ------------------------------------------------------------ 清除本机

    @Test
    fun `清除本机委派注入 cleaner`() = runTest {
        val cleaner = FakeCleaner()
        val repo = repository(transportOf(Fixture()), cleaner = cleaner)

        val result = repo.clearLocalData()

        assertTrue(result.ok)
        assertEquals(1, cleaner.calls)
        assertEquals(2, result.clearedItems)
    }

    @Test
    fun `组合 cleaner 顺序执行并报告失败项`() = runTest {
        val order = mutableListOf<String>()
        val cleaner = CallbackLocalCleaner(
            listOf(
                CallbackLocalCleaner.Step("名单缓存") { order += "members" },
                CallbackLocalCleaner.Step("会失败") { order += "fail"; error("磁盘错误") },
                CallbackLocalCleaner.Step("文件缓存") { order += "files" },
            ),
        )

        val result = cleaner.clearLocalData()

        assertEquals(listOf("members", "fail", "files"), order)
        assertFalse(result.ok)
        assertEquals(2, result.clearedItems)
        assertTrue(result.message.contains("会失败"))
        assertTrue(result.message.contains("磁盘错误"))
    }

    @Test
    fun `组合 cleaner 全成功`() = runTest {
        val cleaner = CallbackLocalCleaner(
            listOf(
                CallbackLocalCleaner.Step("a") { },
                CallbackLocalCleaner.Step("b") { },
            ),
        )
        val result = cleaner.clearLocalData()
        assertTrue(result.ok)
        assertEquals(2, result.clearedItems)
        assertTrue(result.message.contains("2"))
    }
}
