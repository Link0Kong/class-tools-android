package com.furina.classtools.data.power

import com.furina.classtools.FakeTransport
import com.furina.classtools.data.network.ApiResponse
import com.furina.classtools.testApiClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 宿舍目录仓库单测（E21：`GET power?select=id,label,building,room`）。
 *
 * 口径：5 min 缓存；失败回退旧缓存；空目录不写缓存（保留上次成功目录）；force 跳过缓存。
 */
class PowerDirectoryRepositoryTest {

    private val now: Long = 1_794_016_800_000L

    private val directoryJson = """
        [
          {"id":"4-23B---422","label":"23号楼B座422","building":"23号楼B座","room":"422"},
          {"id":"1-13---324","label":"13号楼324","building":"13号楼","room":"324"},
          {"id":"4-23A---321","label":"23号楼A座321","building":"23号楼A座","room":"321"}
        ]
    """.trimIndent()

    @Test
    fun load_fetchesDirectoryAndSorts() = runTest {
        val transport = FakeTransport { ApiResponse(200, directoryJson) }
        val repo = DefaultPowerDirectoryRepository(api = testApiClient(transport), clock = { now })

        val result = repo.load()
        assertTrue(result is PowerDirectoryResult.Success)
        result as PowerDirectoryResult.Success
        assertFalse(result.fromCache)
        assertEquals(listOf("1-13---324", "4-23A---321", "4-23B---422"), result.rooms.map { it.rv })

        val path = transport.requests.single().url
        assertTrue(path.contains("power?select=id,label,building,room"))
        assertTrue(path.contains("order=id.asc"))
        assertTrue(path.contains("limit=200"))
    }

    @Test
    fun load_cachesWithinTtlAndForceBypasses() = runTest {
        val transport = FakeTransport { ApiResponse(200, directoryJson) }
        var clock = now
        val repo = DefaultPowerDirectoryRepository(api = testApiClient(transport), clock = { clock })

        repo.load()
        val cached = repo.load()
        assertTrue((cached as PowerDirectoryResult.Success).fromCache)
        assertEquals(1, transport.requests.size)

        // TTL 过期：重新请求
        clock += DefaultPowerDirectoryRepository.TTL_MS + 1
        val refreshed = repo.load()
        assertFalse((refreshed as PowerDirectoryResult.Success).fromCache)
        assertEquals(2, transport.requests.size)

        // force：TTL 内也重新请求
        repo.load(force = true)
        assertEquals(3, transport.requests.size)
    }

    @Test
    fun load_failureFallsBackToCache() = runTest {
        val transport = FakeTransport { ApiResponse(200, directoryJson) }
        var clock = now
        val repo = DefaultPowerDirectoryRepository(api = testApiClient(transport), clock = { clock })
        repo.load()

        transport.responder = { throw java.io.IOException("offline") }
        clock += DefaultPowerDirectoryRepository.TTL_MS + 1
        val fallback = repo.load()
        assertTrue(fallback is PowerDirectoryResult.Success)
        fallback as PowerDirectoryResult.Success
        assertTrue(fallback.fromCache)
        assertEquals(3, fallback.rooms.size)
    }

    @Test
    fun load_failureWithoutCacheReturnsFailure() = runTest {
        val transport = FakeTransport { throw java.io.IOException("offline") }
        val repo = DefaultPowerDirectoryRepository(api = testApiClient(transport), clock = { now })

        val result = repo.load()
        assertTrue(result is PowerDirectoryResult.Failure)
        assertTrue((result as PowerDirectoryResult.Failure).message.isNotBlank())
    }

    @Test
    fun load_emptyDirectoryIsNotCached() = runTest {
        val transport = FakeTransport { ApiResponse(200, "[]") }
        val repo = DefaultPowerDirectoryRepository(api = testApiClient(transport), clock = { now })

        val first = repo.load()
        assertTrue(first is PowerDirectoryResult.Failure)
        assertEquals("宿舍目录为空", (first as PowerDirectoryResult.Failure).message)

        val second = repo.load()
        assertTrue(second is PowerDirectoryResult.Failure)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun load_emptyDirectoryKeepsPreviousCache() = runTest {
        var body = directoryJson
        val transport = FakeTransport { ApiResponse(200, body) }
        var clock = now
        val repo = DefaultPowerDirectoryRepository(api = testApiClient(transport), clock = { clock })
        repo.load()

        body = "[]"
        clock += DefaultPowerDirectoryRepository.TTL_MS + 1
        val result = repo.load()
        assertTrue(result is PowerDirectoryResult.Success)
        result as PowerDirectoryResult.Success
        assertTrue(result.fromCache)
        assertEquals(3, result.rooms.size)
    }

    @Test
    fun clearCache_forcesNextRequest() = runTest {
        val transport = FakeTransport { ApiResponse(200, directoryJson) }
        val repo = DefaultPowerDirectoryRepository(api = testApiClient(transport), clock = { now })
        repo.load()
        repo.clearCache()
        val result = repo.load()
        assertFalse((result as PowerDirectoryResult.Success).fromCache)
        assertEquals(2, transport.requests.size)
    }
}
