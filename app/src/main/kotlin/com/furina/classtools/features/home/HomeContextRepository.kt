package com.furina.classtools.features.home

import androidx.compose.runtime.staticCompositionLocalOf
import com.furina.classtools.data.network.ApiRequest
import com.furina.classtools.data.network.HttpMethod
import com.furina.classtools.data.network.HttpTransport
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.NetworkClients
import com.furina.classtools.data.network.OkHttpTransport
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal interface HomeContextSource {
    fun cachedWeather(regionId: String, now: Long): HomeWeather? = null
    suspend fun weather(regionId: String, now: Long): HomeWeather?
    suspend fun cachedContent(now: Long): HomePhraseBatch? = null
    suspend fun content(now: Long): HomePhraseBatch?
}

internal val LocalHomeContextSource = staticCompositionLocalOf<HomeContextSource?> { null }

internal class HomeContextRepository(
    baseUrl: String,
    private val cacheFile: File,
    private val transport: HttpTransport = OkHttpTransport(NetworkClients.builder().followRedirects(false).build()),
    allowLoopback: Boolean = false,
) : HomeContextSource {
    private val base = baseUrl.trimEnd('/').toHttpUrlOrNull()?.takeIf {
        it.username.isEmpty() && it.password.isEmpty() && it.query == null && it.fragment == null &&
            (it.isHttps || allowLoopback && it.host == "127.0.0.1")
    }
    private data class WeatherRead(val attemptedAt: Long, val result: HomeWeather?)

    private val weatherRequests = Mutex()
    private val weatherReads = LinkedHashMap<String, WeatherRead>()
    private val contentRequests = Mutex()
    private var lastContentAttempt: Long? = null
    private var loadedDisk = false
    private var batch: HomePhraseBatch? = null

    override fun cachedWeather(regionId: String, now: Long): HomeWeather? = synchronized(weatherReads) {
        weatherReads[regionId]?.result?.takeIf { it.validFor(regionId, now) }
    }

    override suspend fun weather(regionId: String, now: Long): HomeWeather? = weatherRequests.withLock {
        val previous = synchronized(weatherReads) { weatherReads[regionId] }
        if (previous != null && now - previous.attemptedAt in 0 until HOME_WEATHER_REFRESH_MS) {
            return@withLock previous.result?.takeIf { it.validFor(regionId, now) }
        }
        val url = base?.newBuilder()?.addPathSegments("home-context/weather/v1")
            ?.addQueryParameter("region", regionId)?.build()?.toString() ?: return@withLock null
        val result = read(url)?.let { raw ->
            runCatching { NetJson.decodeFromString<HomeWeather>(raw) }.getOrNull()?.takeIf {
                it.validFor(regionId, now)
            }
        }
        synchronized(weatherReads) {
            weatherReads.remove(regionId)
            weatherReads[regionId] = WeatherRead(now, result)
            if (weatherReads.size > 32) weatherReads.remove(weatherReads.keys.first())
        }
        result
    }

    override suspend fun cachedContent(now: Long): HomePhraseBatch? = contentRequests.withLock {
        readDisk()
        batch?.takeIf { it.validAt(now) }
    }

    override suspend fun content(now: Long): HomePhraseBatch? = contentRequests.withLock {
        readDisk()
        val current = batch?.takeIf { it.validAt(now) }
        val url = base?.newBuilder()?.addPathSegments("home-context/content/v1")?.build()?.toString()
            ?: return@withLock current
        if (lastContentAttempt?.let { now - it in 0 until CONTENT_RETRY_MS } == true) return@withLock current
        val incoming = read(url)?.let(::parseBatch)?.takeIf { it.validAt(now) }
        lastContentAttempt = now
        if (incoming != null && incoming != current &&
            (current == null || Instant.parse(incoming.generatedAt) >= Instant.parse(current.generatedAt))) {
            batch = incoming
            withContext(Dispatchers.IO) { saveBatch(incoming) }
            return@withLock incoming
        }
        current
    }

    private suspend fun readDisk() {
        if (loadedDisk) return
        batch = withContext(Dispatchers.IO) {
            runCatching { cacheFile.takeIf { it.length() in 1..MAX_BODY }?.readText()?.let(::parseBatch) }.getOrNull()
        }
        loadedDisk = true
    }

    private fun saveBatch(value: HomePhraseBatch) {
        val staged = File(cacheFile.parentFile, cacheFile.name + ".tmp")
        try {
            cacheFile.parentFile?.mkdirs()
            staged.writeText(NetJson.encodeToString(HomePhraseBatch.serializer(), value))
            Files.move(staged.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: IOException) {
            staged.delete()
        }
    }

    private suspend fun read(url: String): String? = try {
        val response = transport.execute(ApiRequest(HttpMethod.GET, url, timeoutMs = 8_000))
        response.body.takeIf { response.status == 200 && it.length <= MAX_BODY }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: IOException) {
        null
    }

    companion object {
        private const val MAX_BODY = 64 * 1024
        private const val CONTENT_RETRY_MS = 15L * 60 * 1000
        private val onlineId = Regex("ai-[a-f0-9]{24}")

        private fun parseBatch(raw: String): HomePhraseBatch? {
            val pack = runCatching { NetJson.decodeFromString<HomePhraseBatch>(raw) }.getOrNull() ?: return null
            val items = pack.entries
            if (items.isEmpty() || items.size > 12 || items.map { it.id }.distinct().size != items.size) return null
            if (items.any { !it.online || !onlineId.matches(it.id) || it.text.length !in 8..46 ||
                    it.text.any(Char::isISOControl) || it.sourceTitle.isNullOrBlank() || !validHomeSource(it.sourceUrl) }) return null
            return pack
        }
    }
}

internal fun validHomeSource(value: String?): Boolean = try {
    val uri = value?.let(::URI)
    val host = uri?.host?.lowercase()
    uri?.scheme == "https" && uri.rawUserInfo == null && uri.port in listOf(-1, 443) &&
        host != null && (host == "lsc.cornell.edu" || host == "nlc.cn" || host.endsWith(".nlc.cn") ||
            host == "cas.cn" || host.endsWith(".cas.cn"))
} catch (_: java.net.URISyntaxException) {
    false
}
