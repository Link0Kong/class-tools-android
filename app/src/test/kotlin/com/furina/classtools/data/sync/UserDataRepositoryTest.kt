package com.furina.classtools.data.sync

import com.furina.classtools.FakeTransport
import com.furina.classtools.Fixtures
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.data.network.HttpMethod
import com.furina.classtools.testApiClient
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserDataRepositoryTest {

    private val sid = "2024000001"
    private val fixedNow = 1_700_000_000_000L

    // ------------------------------------------------------------ helpers

    private fun userdataResponse(blob: String?): ApiResponse {
        val array = buildJsonArray {
            if (blob != null) {
                add(
                    buildJsonObject {
                        put("id", JsonPrimitive(sid))
                        put("blob", JsonPrimitive(blob))
                        put("ts", JsonPrimitive(1L))
                    },
                )
            }
        }
        return ApiResponse(200, array.toString())
    }

    private fun baselineBlob(): JsonObject = Fixtures.jsonObject("expected_merge_web_mp.json")

    private fun repository(
        transport: FakeTransport,
        local: UserDataLocalStore,
        scope: kotlinx.coroutines.CoroutineScope,
        clock: () -> Long = { fixedNow },
    ): DefaultUserDataRepository = DefaultUserDataRepository(
        api = testApiClient(transport),
        local = local,
        currentSid = { sid },
        scope = scope,
        clock = clock,
    )

    private fun postBlob(transport: FakeTransport): JsonObject {
        val post = transport.requests.last { it.method == HttpMethod.POST }
        val array = kotlinx.serialization.json.Json.parseToJsonElement(post.body!!) as JsonArray
        val row = array[0] as JsonObject
        return UserdataV6.decode((row["blob"] as JsonPrimitive).content)!!
    }

    // ------------------------------------------------------------ pull

    @Test
    fun `pull 拉取远端 blob 并应用到本机与缓存`() = runTest {
        val remoteBlob = UserdataV6.encode(baselineBlob())
        val transport = FakeTransport { userdataResponse(remoteBlob) }
        val local = InMemoryUserDataLocalStore()
        val repo = repository(transport, local, this)

        val result = repo.pull(force = true)

        assertTrue(result is UserdataPullResult.Success)
        val success = result as UserdataPullResult.Success
        assertTrue(success.changed)
        assertFalse(success.pushed)
        assertEquals(1, transport.requests.size)
        assertTrue(transport.lastRequest().url.contains("userdata?select=*&id=eq.$sid"))
        assertEquals(
            (baselineBlob()["fit"] as JsonObject),
            repo.currentSections.value["fit"],
        )
        assertNotNull(local.readCache())
        assertEquals(fixedNow, local.lastPullAt())
    }

    @Test
    fun `pull 在 60s TTL 内不再请求`() = runTest {
        val transport = FakeTransport { userdataResponse(UserdataV6.encode(baselineBlob())) }
        val local = InMemoryUserDataLocalStore()
        val repo = repository(transport, local, this)

        repo.pull(force = true)
        val second = repo.pull(force = false)

        assertTrue(second is UserdataPullResult.Success)
        assertTrue((second as UserdataPullResult.Success).fresh)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `pull 空表（无云端数据）按默认值建基线`() = runTest {
        val transport = FakeTransport { userdataResponse(null) }
        val local = InMemoryUserDataLocalStore()
        val repo = repository(transport, local, this)

        val result = repo.pull(force = true)

        assertTrue(result is UserdataPullResult.Success)
        assertNotNull(local.readCache())
        assertEquals(UserdataV6.defaultSection("homeTools"), repo.currentSections.value["homeTools"])
    }

    @Test
    fun `pull 拉到 v5 时合并并立即写回 v6`() = runTest {
        val legacy = Fixtures.jsonObject("legacy_v5.json")
        var getCount = 0
        val transport = FakeTransport { request ->
            when (request.method) {
                HttpMethod.GET -> {
                    getCount += 1
                    userdataResponse(UserdataV6.encode(legacy))
                }
                else -> ApiResponse(200, "[]")
            }
        }
        val local = InMemoryUserDataLocalStore()
        val repo = repository(transport, local, this)

        val result = repo.pull(force = true) as UserdataPullResult.Success

        assertTrue(result.migratedV5)
        val posts = transport.requests.filter { it.method == HttpMethod.POST }
        assertEquals(1, posts.size)
        val migrated = postBlob(transport)
        assertEquals(6, (migrated["v"] as JsonPrimitive).content.toInt())
        assertEquals(
            UserdataV6.numMs(legacy["homeToolsAt"]),
            UserdataV6.numMs(migrated["homeToolsAt"]),
        )
        assertEquals(2, getCount)
    }

    @Test
    fun `pull 无基线但有待补发时强制本机赢并补推`() = runTest {
        val transport = FakeTransport { request ->
            when (request.method) {
                HttpMethod.GET -> userdataResponse(null)
                else -> ApiResponse(200, "[]")
            }
        }
        val local = InMemoryUserDataLocalStore()
        local.markPending(setOf("homeTools"))
        val current = LinkedHashMap<String, JsonElement>()
        UserdataV6.SECTION_KEYS.forEach { current[it] = UserdataV6.defaultSection(it) }
        current["homeTools"] = JsonArray(listOf(JsonPrimitive("tt")))
        local.writeCurrent(JsonObject(current))
        val repo = repository(transport, local, this)

        repo.pull(force = true)

        val posts = transport.requests.filter { it.method == HttpMethod.POST }
        assertEquals(1, posts.size)
        val pushed = postBlob(transport)
        assertEquals(JsonArray(listOf(JsonPrimitive("tt"))), pushed["homeTools"])
        assertFalse(local.pend())
    }

    // ------------------------------------------------------------ push

    @Test
    fun `无基线且非强制 push 直接跳过（不盲推）`() = runTest {
        val transport = FakeTransport { userdataResponse(null) }
        val local = InMemoryUserDataLocalStore()
        val repo = repository(transport, local, this)

        val result = repo.push()

        assertEquals(UserdataPushResult.Skipped, result)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun `push 失败保留待补发，重试成功后清空`() = runTest {
        val transport = FakeTransport { request ->
            when (request.method) {
                HttpMethod.GET -> userdataResponse(UserdataV6.encode(baselineBlob()))
                else -> ApiResponse(500, """{"code":"XX000","message":"boom"}""")
            }
        }
        val local = InMemoryUserDataLocalStore(cache = baselineBlob())
        val repo = repository(transport, local, this)
        val modified = UserdataV6.sectionsOf(baselineBlob()).toMutableMap()
        modified["nick"] = JsonPrimitive("新昵称")
        repo.updateCurrent(JsonObject(modified))

        val failed = repo.push(force = true)
        assertTrue(failed is UserdataPushResult.Failure)
        assertTrue(local.pend())
        assertTrue(local.pendingKeys().contains("nick"))

        transport.responder = { request ->
            when (request.method) {
                HttpMethod.GET -> userdataResponse(UserdataV6.encode(baselineBlob()))
                else -> ApiResponse(200, "[]")
            }
        }
        val retried = repo.push(force = true)
        assertTrue(retried is UserdataPushResult.Success)
        assertFalse(local.pend())
    }

    @Test
    fun `push 在抑制期内不动云端`() = runTest {
        val transport = FakeTransport { userdataResponse(null) }
        val local = InMemoryUserDataLocalStore(cache = baselineBlob())
        local.setHoldUntil(fixedNow + 10_000L)
        val repo = repository(transport, local, this)

        assertEquals(UserdataPushResult.Held, repo.push(force = true))
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun `云同步关闭时 pull 与 push 都不发请求`() = runTest {
        val transport = FakeTransport { userdataResponse(null) }
        val local = InMemoryUserDataLocalStore(syncEnabled = false)
        val repo = repository(transport, local, this)

        assertEquals(UserdataPullResult.Disabled, repo.pull(force = true))
        assertEquals(UserdataPushResult.Disabled, repo.push(force = true))
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun `setSyncEnabled 恢复时补推并拉取`() = runTest {
        val transport = FakeTransport { request ->
            when (request.method) {
                HttpMethod.GET -> userdataResponse(UserdataV6.encode(baselineBlob()))
                else -> ApiResponse(200, "[]")
            }
        }
        val local = InMemoryUserDataLocalStore(cache = baselineBlob(), syncEnabled = false)
        val repo = repository(transport, local, this)

        repo.setSyncEnabled(true)

        assertTrue(local.syncEnabled())
        // 恢复后至少拉一次
        assertTrue(transport.requests.any { it.method == HttpMethod.GET })
    }

    // ------------------------------------------------------------ 防抖 / flush

    @Test
    fun `updateCurrent 后防抖 1200ms 推送且带新时间戳`() = runTest {
        val base = baselineBlob()
        val transport = FakeTransport { request ->
            when (request.method) {
                HttpMethod.GET -> userdataResponse(UserdataV6.encode(base))
                else -> ApiResponse(200, "[]")
            }
        }
        val local = InMemoryUserDataLocalStore(cache = base)
        val repo = repository(transport, local, this)
        val modified = UserdataV6.sectionsOf(base).toMutableMap()
        modified["fit"] = buildJsonObject {
            put("sex", JsonPrimitive("f"))
            put("grade", JsonPrimitive("12"))
        }

        repo.updateCurrent(JsonObject(modified))

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0, transport.requests.count { it.method == HttpMethod.POST })

        advanceTimeBy(500)
        runCurrent()

        val posts = transport.requests.filter { it.method == HttpMethod.POST }
        assertEquals(1, posts.size)
        val pushed = postBlob(transport)
        assertEquals("f", ((pushed["fit"] as JsonObject)["sex"] as JsonPrimitive).content)
        val baseFitAt = UserdataV6.atValue(base, "fit")
        assertTrue(UserdataV6.atValue(pushed, "fit") > baseFitAt)
    }

    @Test
    fun `连续 touch 只推一次（防抖合并）`() = runTest {
        val base = baselineBlob()
        val transport = FakeTransport { request ->
            when (request.method) {
                HttpMethod.GET -> userdataResponse(UserdataV6.encode(base))
                else -> ApiResponse(200, "[]")
            }
        }
        val local = InMemoryUserDataLocalStore(cache = base)
        val repo = repository(transport, local, this)
        val modified = UserdataV6.sectionsOf(base).toMutableMap()
        modified["nick"] = JsonPrimitive("a")
        repo.updateCurrent(JsonObject(modified))
        modified["nick"] = JsonPrimitive("b")
        repo.updateCurrent(JsonObject(modified))

        advanceTimeBy(1_300)
        runCurrent()

        assertEquals(1, transport.requests.count { it.method == HttpMethod.POST })
    }

    @Test
    fun `flush 立即推送并取消防抖`() = runTest {
        val base = baselineBlob()
        val transport = FakeTransport { request ->
            when (request.method) {
                HttpMethod.GET -> userdataResponse(UserdataV6.encode(base))
                else -> ApiResponse(200, "[]")
            }
        }
        val local = InMemoryUserDataLocalStore(cache = base)
        val repo = repository(transport, local, this)
        val modified = UserdataV6.sectionsOf(base).toMutableMap()
        modified["nick"] = JsonPrimitive("flush 前")
        repo.updateCurrent(JsonObject(modified))

        val flushed = repo.flush()
        assertTrue(flushed is UserdataPushResult.Success)
        assertEquals(1, transport.requests.count { it.method == HttpMethod.POST })

        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1, transport.requests.count { it.method == HttpMethod.POST })
    }

    @Test
    fun `flush 无脏数据时跳过`() = runTest {
        val transport = FakeTransport { userdataResponse(null) }
        val local = InMemoryUserDataLocalStore()
        val repo = repository(transport, local, this)

        assertEquals(UserdataPushResult.Skipped, repo.flush())
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun `无基线时用户改动会先建基线再补推（本地值不丢）`() = runTest {
        val transport = FakeTransport { request ->
            when (request.method) {
                HttpMethod.GET -> userdataResponse(null)
                else -> ApiResponse(200, "[]")
            }
        }
        val local = InMemoryUserDataLocalStore()
        val repo = repository(transport, local, this)
        val current = LinkedHashMap<String, JsonElement>()
        UserdataV6.SECTION_KEYS.forEach { current[it] = UserdataV6.defaultSection(it) }
        current["power"] = buildJsonObject {
            put("sel", JsonPrimitive("4-23A---305"))
            put("at", JsonPrimitive(fixedNow))
            put("low", JsonPrimitive(30))
        }

        repo.updateCurrent(JsonObject(current))
        advanceTimeBy(1_300)
        runCurrent()

        val pushed = postBlob(transport)
        assertEquals(
            "4-23A---305",
            ((pushed["power"] as JsonObject)["sel"] as JsonPrimitive).content,
        )
        // 已有基线（pull 建立）后本地值稳定落库
        assertNotNull(local.readCache())
    }

    // ------------------------------------------------------------ 本机镜像恢复（离线/同步关闭启动）

    @Test
    fun `hydrateFromLocal 恢复本机持久化的 sections（离线启动不回落默认）`() = runTest {
        val storedSections = buildJsonObject {
            put(
                "homeTools",
                buildJsonArray {
                    add(JsonPrimitive("draw"))
                    add(JsonPrimitive("group"))
                    add(JsonPrimitive("fit"))
                    add(JsonPrimitive("roster"))
                },
            )
            put("nick", JsonPrimitive("离线同学"))
            put(
                "water",
                buildJsonObject {
                    put("rv", JsonPrimitive("13号楼-324"))
                    put("rt", JsonPrimitive(4))
                    put("m", buildJsonArray { })
                },
            )
        }
        val transport = FakeTransport { userdataResponse(null) }
        val local = InMemoryUserDataLocalStore(current = storedSections, syncEnabled = false)
        val repo = repository(transport, local, this)
        assertTrue(repo.currentSections.value.isEmpty())

        repo.hydrateFromLocal()

        assertEquals(
            storedSections["homeTools"],
            repo.currentSections.value["homeTools"],
        )
        assertEquals("离线同学", (repo.currentSections.value["nick"] as JsonPrimitive).content)
        // 只读本机镜像，不发起任何网络请求
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun `hydrateFromLocal 本机为空或已有镜像时不覆盖`() = runTest {
        val transport = FakeTransport { userdataResponse(null) }
        // 1) 本机为空：保持空镜像
        val emptyLocal = InMemoryUserDataLocalStore(syncEnabled = false)
        val emptyRepo = repository(transport, emptyLocal, this)
        emptyRepo.hydrateFromLocal()
        assertTrue(emptyRepo.currentSections.value.isEmpty())

        // 2) 已有镜像（如刚 pull）：不被旧本机值覆盖
        val remoteBlob = UserdataV6.encode(baselineBlob())
        val remoteTransport = FakeTransport { userdataResponse(remoteBlob) }
        val local = InMemoryUserDataLocalStore(
            current = buildJsonObject { put("nick", JsonPrimitive("旧值")) },
            syncEnabled = true,
        )
        val repo = repository(remoteTransport, local, this)
        repo.pull(force = true)
        val afterPull = repo.currentSections.value
        assertTrue(afterPull.isNotEmpty())

        repo.hydrateFromLocal()

        assertEquals(afterPull, repo.currentSections.value)
    }
}
