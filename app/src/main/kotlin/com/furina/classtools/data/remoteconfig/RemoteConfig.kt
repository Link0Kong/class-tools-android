package com.furina.classtools.data.remoteconfig

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 轻量远程配置（「设置热更」通道）。
 *
 * 只覆盖**白名单设置**，不下载代码、不执行脚本：
 * - appearance：system / light / dark
 * - hideStatusBar：是否隐藏系统状态栏
 * - dynamicColor：Material You 壁纸取色
 * - timetableCompact：课表是否使用一屏周视图
 *
 * 版本号只增不减：同一 version 只应用一次，避免每次启动覆盖用户手动改的设置。
 */
data class RemoteConfig(
    val version: Int,
    val appearance: String? = null,
    val hideStatusBar: Boolean? = null,
    val dynamicColor: Boolean? = null,
    val timetableCompact: Boolean? = null,
    val announcement: String? = null,
)

object RemoteConfigParser {
    fun parse(raw: String): RemoteConfig? = runCatching {
        val obj = JSONObject(raw)
        val version = obj.optInt("version", 0)
        if (version <= 0) return null
        RemoteConfig(
            version = version,
            appearance = obj.optString("appearance").takeIf { it.isNotBlank() },
            hideStatusBar = obj.takeIf { it.has("hideStatusBar") }?.optBoolean("hideStatusBar"),
            dynamicColor = obj.takeIf { it.has("dynamicColor") }?.optBoolean("dynamicColor"),
            timetableCompact = obj.takeIf { it.has("timetableCompact") }?.optBoolean("timetableCompact"),
            announcement = obj.optString("announcement").takeIf { it.isNotBlank() },
        )
    }.getOrNull()
}

/**
 * 拉取远程配置：OSS 上的 `android/config.json`（与 APK 更新同渠道，HTTPS、公开读）。
 * 网络失败/404/JSON 非法一律静默返回 null，不影响启动。
 */
class RemoteConfigRepository(
    private val url: String = DEFAULT_URL,
    private val timeoutMs: Int = 8_000,
) {
    suspend fun fetch(): RemoteConfig? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                RemoteConfigParser.parse(conn.inputStream.bufferedReader().use { it.readText() })
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    companion object {
        /** 与 APK 更新渠道同域；文件由发布脚本/运维上传。 */
        const val DEFAULT_URL =
            "https://tool-furina-fun.oss-cn-hongkong.aliyuncs.com/android/config.json"
    }
}
