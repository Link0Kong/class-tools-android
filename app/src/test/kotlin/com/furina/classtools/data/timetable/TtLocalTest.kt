package com.furina.classtools.data.timetable

import com.furina.classtools.FakeTransport
import com.furina.classtools.createTempDir
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.sync.UserdataPullResult
import com.furina.classtools.data.sync.UserdataPushResult
import com.furina.classtools.data.sync.UserdataV6
import com.furina.classtools.data.sync.UserDataRepository
import com.furina.classtools.testApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 个人课表（userdata v6 `tt.ttLocal`）单测：
 * - ttLocal 编解码（保留 tt 其它字段、清除为 null、坏数据回退）；
 * - 个人课表写入走 userdata updateCurrent（全量 sections、其它 section 不丢）；
 * - 课表仓库优先级：个人 ttLocal > 公共 site_assets > 缓存。
 */
class TtLocalTest {

    private val now = 1_700_000_000_000L

    private fun snapshot(name: String = "体育（排球）"): TtSnapshot = TtSnapshot(
        c = listOf(
            Course(name = name, teacher = "赵阳", room = "体育馆", day = 4, start = 6, end = 7, weeks = "1-16"),
        ),
        t = "2026-08-31",
    )

    private fun ttSection(local: kotlinx.serialization.json.JsonElement? = null): JsonObject = buildJsonObject {
        put("ttView", JsonPrimitive("week"))
        put("ttUseMine", JsonPrimitive(false))
        put("ttLocal", local ?: JsonNull)
        put("termStart", JsonPrimitive("2026-08-31"))
    }

    // ------------------------------------------------------------ codec

    @Test
    fun encodeParse_roundTrip() {
        val encoded = TtLocal.encode(snapshot(), now)
        val parsed = TtLocal.parse(encoded)
        assertNotNull(parsed)
        assertEquals(1, parsed!!.c.size)
        assertEquals("体育（排球）", parsed.c.single().name)
        assertEquals("2026-08-31", parsed.t)
        val courses = encoded["c"] as JsonArray
        val course = courses[0] as JsonObject
        assertEquals("4", (course["day"] as JsonPrimitive).content)
        assertEquals(now, (encoded["at"] as JsonPrimitive).content.toLong())
    }

    @Test
    fun parse_toleratesJsonStringAndBadData() {
        val encoded = TtLocal.encode(snapshot(), now)
        val asString = JsonPrimitive(encoded.toString())
        assertEquals(1, TtLocal.parse(asString)!!.c.size)

        assertNull(TtLocal.parse(JsonNull))
        assertNull(TtLocal.parse(null))
        assertNull(TtLocal.parse(buildJsonObject { put("c", JsonArray(emptyList())) }))
        assertNull(TtLocal.parse(buildJsonObject { put("c", JsonPrimitive("oops")) }))
        assertNull(TtLocal.parse(JsonPrimitive("not-json")))
    }

    @Test
    fun applyTo_preservesOtherTtFields() {
        val section = ttSection()
        val updated = TtLocal.applyTo(section, snapshot(), now)
        assertEquals("week", (updated["ttView"] as JsonPrimitive).content)
        assertEquals("false", (updated["ttUseMine"] as JsonPrimitive).content)
        assertEquals("2026-08-31", (updated["termStart"] as JsonPrimitive).content)
        assertTrue(updated["ttLocal"] is JsonObject)
        assertNotNull(TtLocal.snapshotOf(updated))
    }

    @Test
    fun cleared_setsNullAndKeepsOtherFields() {
        val section = ttSection(TtLocal.encode(snapshot(), now))
        val cleared = TtLocal.cleared(section)
        assertTrue(cleared["ttLocal"] is JsonNull)
        assertEquals("week", (cleared["ttView"] as JsonPrimitive).content)
        assertNull(TtLocal.snapshotOf(cleared))
    }

    @Test
    fun applyTo_handlesNullOrWrongSectionType() {
        val updated = TtLocal.applyTo(null, snapshot(), now)
        assertNotNull(TtLocal.snapshotOf(updated))
        val fromString = TtLocal.applyTo(JsonPrimitive("bad"), snapshot(), now)
        assertNotNull(TtLocal.snapshotOf(fromString))
    }

    // ------------------------------------------------------------ 存储层

    private class FakeUserDataRepository(initial: JsonObject) : UserDataRepository {
        private val flow = MutableStateFlow(UserdataV6.sectionsOf(initial))
        override val currentSections: StateFlow<JsonObject> = flow.asStateFlow()
        override suspend fun pull(force: Boolean): UserdataPullResult =
            UserdataPullResult.Success(changed = false, pushed = false)

        override suspend fun push(force: Boolean, forceKeys: Set<String>): UserdataPushResult =
            UserdataPushResult.Success(false)

        override suspend fun updateCurrent(sections: JsonObject) {
            flow.value = UserdataV6.sectionsOf(sections)
        }

        override fun touch() = Unit
        override suspend fun flush(): UserdataPushResult = UserdataPushResult.Success(false)
        override suspend fun setSyncEnabled(enabled: Boolean) = Unit
        override suspend fun syncEnabled(): Boolean = true
        override suspend fun cachedBlob(): JsonObject? = null
        override suspend fun hydrateFromLocal() = Unit
    }

    private fun initialSections(): JsonObject = buildJsonObject {
        UserdataV6.SECTION_KEYS.forEach { key ->
            when (key) {
                "tt" -> put("tt", ttSection())
                "power" -> put(
                    "power",
                    buildJsonObject {
                        put("sel", JsonPrimitive("4-23A---305"))
                        put("at", JsonPrimitive(1L))
                        put("low", JsonPrimitive(30))
                    },
                )
                else -> put(key, UserdataV6.defaultSection(key))
            }
        }
    }

    @Test
    fun personalStore_saveAndClearThroughUserData() = runTest {
        val userData = FakeUserDataRepository(initialSections())
        val store = UserDataTimetablePersonalStore(userData, now = { now })

        assertNull(store.read())

        store.save(snapshot())
        val tt = userData.currentSections.value["tt"] as JsonObject
        assertEquals("week", (tt["ttView"] as JsonPrimitive).content)
        assertNotNull(TtLocal.snapshotOf(tt))
        assertEquals("体育（排球）", store.read()!!.c.single().name)

        // 其它 section 原样保留（power 选择未被覆盖）
        val power = userData.currentSections.value["power"] as JsonObject
        assertEquals("4-23A---305", (power["sel"] as JsonPrimitive).contentOrNull)

        store.clear()
        assertNull(store.read())
        val clearedTt = userData.currentSections.value["tt"] as JsonObject
        assertEquals("week", (clearedTt["ttView"] as JsonPrimitive).content)
        assertEquals("4-23A---305", ((userData.currentSections.value["power"] as JsonObject)["sel"] as JsonPrimitive).content)
    }

    // ------------------------------------------------------------ 仓库优先级

    private val publicRow = """
        [{"body":{"c":[{"name":"公共课","teacher":"公共老师","room":"A101","day":0,"start":1,"end":2,"weeks":"1-16"}],"t":"2026-08-31"}}]
    """.trimIndent()

    private class FakePersonalStore(var snapshot: TtSnapshot? = null) : TimetablePersonalStore {
        override suspend fun read(): TtSnapshot? = snapshot
        override suspend fun save(snapshot: TtSnapshot) {
            this.snapshot = snapshot
        }
        override suspend fun clear() {
            snapshot = null
        }
    }

    @Test
    fun repository_personalTakesPriorityOverPublicAndCache() = runTest {
        val transport = FakeTransport { ApiResponse(200, publicRow) }
        val personal = FakePersonalStore(snapshot("我的体育"))
        val repo = DefaultTimetableRepository(
            api = testApiClient(transport),
            cache = FileTimetableCache(createTempDir("tt-personal-")),
            personalStore = personal,
            clock = { now },
        )
        val result = repo.load()
        assertTrue(result is TtLoadResult.Success)
        val success = result as TtLoadResult.Success
        assertEquals(TtSource.PERSONAL, success.source)
        assertEquals("我的体育", success.snapshot.c.single().name)
        assertTrue(transport.requests.isEmpty()) // 有个人课表时不请求公共快照

        assertEquals("我的体育", repo.cached()!!.c.single().name)
    }

    @Test
    fun repository_clearPersonalFallsBackToPublic() = runTest {
        val transport = FakeTransport { ApiResponse(200, publicRow) }
        val personal = FakePersonalStore(snapshot())
        val repo = DefaultTimetableRepository(
            api = testApiClient(transport),
            cache = FileTimetableCache(createTempDir("tt-personal-")),
            personalStore = personal,
            clock = { now },
        )
        assertTrue((repo.load() as TtLoadResult.Success).source == TtSource.PERSONAL)

        repo.clearPersonal()
        val fallback = repo.load(force = true) as TtLoadResult.Success
        assertEquals(TtSource.PUBLIC, fallback.source)
        assertEquals("公共课", fallback.snapshot.c.single().name)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun repository_savePersonal_thenLoadReturnsIt() = runTest {
        val transport = FakeTransport { ApiResponse(200, publicRow) }
        val personal = FakePersonalStore()
        val repo = DefaultTimetableRepository(
            api = testApiClient(transport),
            cache = FileTimetableCache(createTempDir("tt-personal-")),
            personalStore = personal,
            clock = { now },
        )
        repo.savePersonal(snapshot("新个人课"))
        val result = repo.load() as TtLoadResult.Success
        assertEquals(TtSource.PERSONAL, result.source)
        assertEquals("新个人课", result.snapshot.c.single().name)
    }

    @Test
    fun repository_withoutPersonalStore_behavesAsPublicOnly() = runTest {
        val transport = FakeTransport { ApiResponse(200, publicRow) }
        val repo = DefaultTimetableRepository(
            api = testApiClient(transport),
            cache = FileTimetableCache(createTempDir("tt-personal-")),
            clock = { now },
        )
        val result = repo.load() as TtLoadResult.Success
        assertEquals(TtSource.PUBLIC, result.source)
        assertNull(repo.personal())
    }

    @Test
    fun encode_isCompactAndStandardJson() {
        val text = NetJson.encodeToString(JsonObject.serializer(), TtLocal.encode(snapshot(), now))
        assertEquals(Json.parseToJsonElement(text), TtLocal.encode(snapshot(), now))
    }

    @Test
    fun snapshotOf_readsFieldFromTtSection() {
        val section = ttSection(JsonPrimitive(TtLocal.encode(snapshot(), now).toString()))
        assertEquals("体育（排球）", TtLocal.snapshotOf(section)!!.c.single().name)
        assertNull(TtLocal.snapshotOf(ttSection()))
        assertNull(TtLocal.snapshotOf(JsonPrimitive("x")))
    }
}
