package com.furina.classtools.data.ppt

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.furina.classtools.data.network.OkHttpTransport
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 播放器图片加载器（项目无 Coil / ExoPlayer，按约定自建轻量版，不引入新依赖）。
 *
 * - OkHttp 直连 OSS（公共读）+ `BitmapFactory` 解码（含 WebP，API 26 原生支持）；
 * - 支持 `data:` URL（契约 §5 允许测试注入假图）；
 * - 内存 LRU（按像素字节计费，默认约 1/8 可用堆），避免 1600px 大图 OOM；
 * - 按目标宽度（默认 1280px）`inSampleSize` 降采样：手机/平板清晰度足够，低端机解码更快；
 * - 同一 URL 并发只下一次（in-flight 共享），预加载相邻 2 步不会重复下载；
 * - 播放器退出可 [clearMemory] 释放 Bitmap。
 */
interface PptImageLoader {
    /** @param targetWidth 解码目标宽度（播放器 1280、缩略图 400） */
    suspend fun load(url: String, targetWidth: Int = DISPLAY_TARGET_WIDTH): ImageBitmap?

    /** 预加载（与 [load] 同语义，忽略结果）。 */
    suspend fun preload(url: String, targetWidth: Int = DISPLAY_TARGET_WIDTH) {
        load(url, targetWidth)
    }

    /** 释放全部内存缓存（播放器退出时调用）。 */
    fun clearMemory()

    companion object {
        /** 播放器舞台默认解码宽度。 */
        const val DISPLAY_TARGET_WIDTH: Int = 1280

        /** 缩略图解码宽度。 */
        const val THUMB_TARGET_WIDTH: Int = 400
    }
}

class OkHttpPptImageLoader(
    private val client: OkHttpClient = OkHttpTransport.defaultClient(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) : PptImageLoader {

    private val mutex = Mutex()
    private val inFlight = HashMap<String, kotlinx.coroutines.Deferred<ImageBitmap?>>()

    /** 缓存键 = `目标宽|URL`；onEldest 由 LruCache 自动淘汰。 */
    private val cache = object : LruCache<String, Bitmap>(maxCacheKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    override suspend fun load(url: String, targetWidth: Int): ImageBitmap? {
        val key = keyOf(url, targetWidth)
        if (key.isEmpty()) return null
        cache.get(key)?.let { return it.asImageBitmap() }
        val deferred = mutex.withLock {
            cache.get(key)?.let { return it.asImageBitmap() }
            inFlight.getOrPut(key) {
                scope.async(ioDispatcher) { fetchAndDecode(url, targetWidth, key) }
            }
        }
        return try {
            deferred.await()
        } finally {
            mutex.withLock { inFlight.remove(key) }
        }
    }

    override fun clearMemory() {
        cache.evictAll()
    }

    /** 下载 + 解码（阻塞，运行在 IO 调度器）。失败返回 null，不抛异常（UI 显示占位）。 */
    private fun fetchAndDecode(url: String, targetWidth: Int, key: String): ImageBitmap? {
        val bytes = try {
            readBytes(url)
        } catch (_: Throwable) {
            return null
        }
        val bitmap = try {
            decodeSampled(bytes, targetWidth)
        } catch (_: Throwable) {
            null
        } ?: return null
        cache.put(key, bitmap)
        return bitmap.asImageBitmap()
    }

    private fun readBytes(url: String): ByteArray {
        if (url.startsWith("data:", ignoreCase = true)) {
            return decodeDataUrl(url) ?: throw IOException("bad data url")
        }
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body
            return body.bytes()
        }
    }

    private fun decodeDataUrl(url: String): ByteArray? {
        val comma = url.indexOf(',')
        if (comma < 0) return null
        val meta = url.substring(0, comma)
        val payload = url.substring(comma + 1)
        return try {
            if (meta.contains(";base64", ignoreCase = true)) {
                Base64.decode(payload, Base64.DEFAULT)
            } else {
                java.net.URLDecoder.decode(payload, "UTF-8").toByteArray(Charsets.ISO_8859_1)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeSampled(bytes: ByteArray, targetWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val target = targetWidth.coerceIn(64, 4_000)
        while (bounds.outWidth / (sample * 2) >= target) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun keyOf(url: String, targetWidth: Int): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        val rounded = (targetWidth / 200) * 200 // 相近目标宽共缓存
        return "$rounded|$trimmed"
    }

    companion object {
        /** 缓存上限 = 单进程可用堆 / 8（KB），最低 8MB、最高 64MB。 */
        fun maxCacheKb(): Int {
            val heapKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
            return (heapKb / 8).coerceIn(8 * 1024, 64 * 1024)
        }
    }
}
