package com.furina.classtools.data.diag

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StatFs
import java.io.File

/**
 * 设备诊断 v2.1：本机检查采集（Android）+ 判定（纯函数，可单测）。
 *
 * 契约 v2.1 §B「设备信息最小化」：
 * - 只采集/展示「可能导致功能不可用/出错」的信息：系统版本、App 版本、WebView 内核、屏幕、
 *   可用内存、可用存储百分比、网络、网关延迟、时钟偏差、本地数据可读写；
 * - **不采集品牌、型号、语言、API level、时区**（也不进上报 payload）；
 * - 上报 device 白名单 = `platform/osVersion/appVer/webview/screen{w,h,dpr}/memoryMB/storagePct/network/clockSkewMs`。
 *
 * 分两层：
 * - [AndroidDeviceProbe]：读系统 API → [DiagProbeInput]（仅本文件依赖 Android framework）；
 * - [DiagEvaluator]：把 [DiagProbeInput] 判定为 [DiagCheck] 列表（纯 Kotlin，JVM 单测直接覆盖）。
 */
data class DiagProbeInput(
    val androidVersion: String = "",
    val appVersion: String = "",
    val webviewVersion: String = "",
    /** 屏幕物理像素宽（契约 screen.w）。 */
    val screenWidthPx: Int = 0,
    /** 屏幕物理像素高（契约 screen.h）。 */
    val screenHeightPx: Int = 0,
    /** density（契约 screen.dpr，如 2.75）。 */
    val screenDensity: Float = 0f,
    val memoryTotalMb: Long = 0,
    /** 可用内存 MB（只上报这个量；总量仅用于阈值判定）。 */
    val memoryAvailMb: Long = 0,
    val lowMemory: Boolean = false,
    val storageTotalMb: Long = 0,
    val storageFreeMb: Long = 0,
    val networkType: String = "unknown",
    val networkValidated: Boolean = false,
    val gatewayLatencyMs: Long? = null,
    val gatewayError: String = "",
    val serverEpochMs: Long? = null,
    val nowMillis: Long = 0L,
    val localDataOk: Boolean = false,
    val localDataDetail: String = "",
) {
    /** 本机时钟偏差（server Date - local now；正 = 本机慢）。Date 头缺失 → null。 */
    val clockSkewMs: Long?
        get() = serverEpochMs?.let { it - nowMillis }

    /** 可用内存占比（仅用于阈值判定，不上报总量）。 */
    val memoryRatio: Double
        get() = if (memoryTotalMb > 0) memoryAvailMb.toDouble() / memoryTotalMb.toDouble() else 1.0

    /** 可用存储百分比 0–100（契约 storagePct）。 */
    val storagePct: Int
        get() = if (storageTotalMb > 0) {
            ((storageFreeMb * 100) / storageTotalMb).toInt().coerceIn(0, 100)
        } else {
            0
        }

    /** 上报 payload 的 device（契约 v2.1 §B android 白名单，唯一来源）。 */
    fun deviceInfo(): DiagDeviceInfo = DiagDeviceInfo(
        platform = DiagPayload.PLATFORM,
        osVersion = androidVersion,
        appVer = appVersion,
        webview = webviewVersion,
        screenWidth = screenWidthPx,
        screenHeight = screenHeightPx,
        screenDpr = screenDensity.toDouble(),
        memoryMB = memoryAvailMb,
        storagePct = storagePct,
        network = networkType,
        clockSkewMs = clockSkewMs,
    )

    /** 上报 device JSON（[DiagDeviceInfo.toJson]）。 */
    fun deviceJson(): kotlinx.serialization.json.JsonObject = deviceInfo().toJson()
}

/** 判定阈值（集中常量，测试与 UI 文案共用）。 */
object DiagThresholds {
    const val MEMORY_WARN_RATIO: Double = 0.10
    const val MEMORY_FAIL_RATIO: Double = 0.05
    const val STORAGE_WARN_FREE_MB: Long = 200
    const val STORAGE_FAIL_FREE_MB: Long = 50
    const val STORAGE_WARN_RATIO: Double = 0.05
    const val STORAGE_FAIL_RATIO: Double = 0.01
    const val GATEWAY_WARN_MS: Long = 3000
    const val GATEWAY_FAIL_MS: Long = 10000
    const val CLOCK_WARN_MS: Long = 2L * 60 * 1000
    const val CLOCK_FAIL_MS: Long = 10L * 60 * 1000
}

/** 纯函数判定：输入本机采集值 → 诊断项列表（顺序固定，8–10 项，UI/上报同源）。 */
object DiagEvaluator {

    fun evaluate(input: DiagProbeInput): List<DiagCheck> {
        val checks = ArrayList<DiagCheck>(10)

        checks += make(
            id = "os_version",
            label = "系统版本",
            status = DiagStatus.PASS,
            value = input.androidVersion.ifBlank { "未知" },
        )
        checks += make(
            id = "app_version",
            label = "App 版本",
            status = DiagStatus.PASS,
            value = input.appVersion.ifBlank { "未知" },
        )
        checks += make(
            id = "webview",
            label = "WebView 内核",
            status = DiagStatus.PASS,
            value = input.webviewVersion.ifBlank { "未知" },
        )
        checks += make(
            id = "screen",
            label = "屏幕",
            status = DiagStatus.PASS,
            value = if (input.screenWidthPx > 0 && input.screenDensity > 0f) {
                "${input.screenWidthPx}×${input.screenHeightPx} @${formatDpr(input.screenDensity)}x"
            } else {
                "未知"
            },
        )

        // 内存（只展示可用量；占比仅用于判定）
        val memoryStatus = when {
            input.memoryTotalMb <= 0 -> DiagStatus.INFO
            input.lowMemory || input.memoryRatio < DiagThresholds.MEMORY_FAIL_RATIO -> DiagStatus.FAIL
            input.memoryRatio < DiagThresholds.MEMORY_WARN_RATIO -> DiagStatus.WARN
            else -> DiagStatus.PASS
        }
        checks += make(
            id = "memory",
            label = "可用内存",
            status = memoryStatus,
            value = if (input.memoryTotalMb > 0) "可用 ${formatMb(input.memoryAvailMb)}" else "无法读取",
            hint = when (memoryStatus) {
                DiagStatus.FAIL -> if (input.lowMemory) "系统处于低内存状态" else "内存偏低，可能被杀进程"
                DiagStatus.WARN -> "内存偏低"
                else -> ""
            },
            reportKind = "perf",
        )

        // 存储（只展示百分比）
        val storageRatio = if (input.storageTotalMb > 0) {
            input.storageFreeMb.toDouble() / input.storageTotalMb.toDouble()
        } else {
            1.0
        }
        val storageStatus = when {
            input.storageTotalMb <= 0 -> DiagStatus.INFO
            input.storageFreeMb < DiagThresholds.STORAGE_FAIL_FREE_MB ||
                storageRatio < DiagThresholds.STORAGE_FAIL_RATIO -> DiagStatus.FAIL
            input.storageFreeMb < DiagThresholds.STORAGE_WARN_FREE_MB ||
                storageRatio < DiagThresholds.STORAGE_WARN_RATIO -> DiagStatus.WARN
            else -> DiagStatus.PASS
        }
        checks += make(
            id = "storage",
            label = "可用存储",
            status = storageStatus,
            value = if (input.storageTotalMb > 0) "剩余 ${input.storagePct}%" else "无法读取",
            hint = when (storageStatus) {
                DiagStatus.FAIL -> "存储空间严重不足"
                DiagStatus.WARN -> "存储空间偏少"
                else -> ""
            },
            reportKind = "storage",
        )

        // 网络
        val networkType = input.networkType.ifBlank { "unknown" }
        val offline = networkType == "offline"
        val networkStatus = when {
            offline -> DiagStatus.FAIL
            !input.networkValidated -> DiagStatus.WARN
            else -> DiagStatus.PASS
        }
        checks += make(
            id = "network",
            label = "网络",
            status = networkStatus,
            value = networkLabel(networkType) +
                if (offline) "" else if (input.networkValidated) " · 已连通" else " · 未验证连通",
            hint = when (networkStatus) {
                DiagStatus.FAIL -> "设备当前无可用网络"
                DiagStatus.WARN -> "网络未验证连通性，数据同步可能失败"
                else -> ""
            },
            reportKind = "network",
        )

        // CloudBase 网关延迟
        val gatewayStatus = when {
            input.gatewayLatencyMs == null -> DiagStatus.FAIL
            input.gatewayLatencyMs > DiagThresholds.GATEWAY_FAIL_MS -> DiagStatus.FAIL
            input.gatewayLatencyMs > DiagThresholds.GATEWAY_WARN_MS -> DiagStatus.WARN
            else -> DiagStatus.PASS
        }
        checks += make(
            id = "gateway",
            label = "CloudBase 网关延迟",
            status = gatewayStatus,
            value = input.gatewayLatencyMs?.let { "$it ms" } ?: "不可达",
            hint = when (gatewayStatus) {
                DiagStatus.FAIL -> input.gatewayError.ifBlank { "无法连接云端网关" }
                DiagStatus.WARN -> "网关响应偏慢"
                else -> ""
            },
            reportKind = "network",
        )

        // 时钟偏差（服务器 Date 头 vs 本机时钟）
        val skew = input.clockSkewMs
        val clockStatus = when {
            skew == null -> DiagStatus.INFO
            kotlin.math.abs(skew) > DiagThresholds.CLOCK_FAIL_MS -> DiagStatus.FAIL
            kotlin.math.abs(skew) > DiagThresholds.CLOCK_WARN_MS -> DiagStatus.WARN
            else -> DiagStatus.PASS
        }
        checks += make(
            id = "clock",
            label = "时钟偏差",
            status = clockStatus,
            value = when {
                skew == null -> "不可校准（无 Date 头）"
                kotlin.math.abs(skew) < 1000 -> "正常"
                skew > 0 -> "本机慢 ${formatDuration(skew)}"
                else -> "本机快 ${formatDuration(-skew)}"
            },
            hint = when (clockStatus) {
                DiagStatus.FAIL -> "本机时钟偏差过大，会影响登录/同步校验"
                DiagStatus.WARN -> "本机时钟与服务器不一致"
                else -> ""
            },
            reportKind = "perf",
        )

        // 本地数据可用性（应用私有目录可写 + 可读）
        checks += make(
            id = "local_data",
            label = "本地数据",
            status = if (input.localDataOk) DiagStatus.PASS else DiagStatus.FAIL,
            value = if (input.localDataOk) input.localDataDetail.ifBlank { "可读写" } else "不可用",
            hint = if (input.localDataOk) "" else "本机存储不可写，偏好/缓存可能丢失",
            reportKind = "storage",
        )
        return checks
    }

    fun summary(input: DiagProbeInput): DiagSummary =
        DiagSummary(checks = evaluate(input), ranAt = input.nowMillis)

    fun networkLabel(type: String): String = when (type.trim().lowercase()) {
        "wifi" -> "Wi-Fi"
        "cellular" -> "移动网络"
        "ethernet" -> "有线网络"
        "vpn" -> "VPN"
        "bluetooth" -> "蓝牙网络"
        "offline" -> "离线"
        else -> "未知"
    }

    /** dpr 文案：2.75 → `2.75`，2.0 → `2`。 */
    fun formatDpr(density: Float): String {
        if (density <= 0f) return "0"
        val rounded = kotlin.math.round(density * 100.0) / 100.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }

    fun formatMb(mb: Long): String = when {
        mb <= 0 -> "0 MB"
        mb >= 1024 -> {
            val rounded = kotlin.math.round(mb / 1024.0 * 10.0) / 10.0
            val text = if (rounded == rounded.toLong().toDouble()) {
                rounded.toLong().toString()
            } else {
                rounded.toString()
            }
            "$text GB"
        }
        else -> "$mb MB"
    }

    fun formatDuration(millis: Long): String {
        val totalSeconds = kotlin.math.abs(millis) / 1000
        return when {
            totalSeconds < 60 -> "${totalSeconds} 秒"
            totalSeconds < 3600 -> "${totalSeconds / 60} 分钟"
            else -> "${totalSeconds / 3600} 小时 ${(totalSeconds % 3600) / 60} 分钟"
        }
    }

    private fun make(
        id: String,
        label: String,
        status: DiagStatus,
        value: String = "",
        hint: String = "",
        reportKind: String = "error",
    ): DiagCheck = DiagCheck(
        id = id,
        label = label,
        status = status,
        value = DiagSanitizer.sanitizeText(value, 120),
        hint = DiagSanitizer.sanitizeText(hint, 160),
        reportKind = reportKind,
    )
}

/**
 * Android 实现：读取系统版本 / 屏幕 / 内存 / 存储 / 网络 / WebView 内核 / 本地目录可用性。
 *
 * CloudBase 网关延迟与服务器时钟由数据层（[DiagRepository.measureGateway]）测量后回填。
 * 品牌、型号、语言、时区、API level 一律不读取（契约 v2.1 §B）。
 */
class AndroidDeviceProbe(
    private val context: Context,
    private val appVersion: String = "",
) {
    fun collectBase(nowMillis: Long = System.currentTimeMillis()): DiagProbeInput {
        val metrics = context.resources.displayMetrics
        val density = metrics.density.takeIf { it > 0f } ?: 0f
        val memory = readMemory()
        val storage = readStorage()
        val network = readNetwork()
        val local = readLocalData()

        return DiagProbeInput(
            androidVersion = Build.VERSION.RELEASE.orEmpty(),
            appVersion = appVersion,
            webviewVersion = readWebViewVersion(),
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            screenDensity = density,
            memoryTotalMb = memory.first,
            memoryAvailMb = memory.second,
            lowMemory = memory.third,
            storageTotalMb = storage.first,
            storageFreeMb = storage.second,
            networkType = network.first,
            networkValidated = network.second,
            nowMillis = nowMillis,
            localDataOk = local.first,
            localDataDetail = local.second,
        )
    }

    private fun readMemory(): Triple<Long, Long, Boolean> {
        return try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return Triple(0L, 0L, false)
            val info = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(info)
            Triple(info.totalMem / MB, info.availMem / MB, info.lowMemory)
        } catch (_: Throwable) {
            Triple(0L, 0L, false)
        }
    }

    private fun readStorage(): Pair<Long, Long> {
        return try {
            val dir: File = context.filesDir ?: return 0L to 0L
            val stat = StatFs(dir.absolutePath)
            Pair(stat.totalBytes / MB, stat.availableBytes / MB)
        } catch (_: Throwable) {
            0L to 0L
        }
    }

    private fun readNetwork(): Pair<String, Boolean> {
        return try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return "unknown" to false
            val active = manager.activeNetwork ?: return "offline" to false
            val caps = manager.getNetworkCapabilities(active) ?: return "offline" to false
            val type = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth"
                else -> "unknown"
            }
            type to caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Throwable) {
            "unknown" to false
        }
    }

    /** WebView 内核（包版本主版本号，如 `WebView/137`）；读不到返回空串（允许可空）。 */
    private fun readWebViewVersion(): String {
        return try {
            val manager = context.packageManager
            val candidates = listOf("com.google.android.webview", "com.android.webview")
            for (pkg in candidates) {
                val info = manager.getPackageInfo(pkg, 0)
                val version = info.versionName?.trim().orEmpty()
                if (version.isNotEmpty()) {
                    return "WebView/" + version.substringBefore('.')
                }
            }
            ""
        } catch (_: Throwable) {
            ""
        }
    }

    private fun readLocalData(): Pair<Boolean, String> {
        return try {
            val dir = context.filesDir ?: return false to "filesDir 不可用"
            if (!dir.isDirectory) return false to "应用私有目录缺失"
            val probe = File(dir, "diag_probe.tmp")
            probe.writeText("ok")
            val ok = probe.isFile && probe.length() == 2L
            probe.delete()
            if (ok) true to "可读写" else false to "不可写"
        } catch (_: Throwable) {
            false to "不可写"
        }
    }

    private companion object {
        const val MB: Long = 1024L * 1024L
    }
}
