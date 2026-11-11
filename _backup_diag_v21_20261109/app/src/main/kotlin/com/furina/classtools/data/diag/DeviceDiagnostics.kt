package com.furina.classtools.data.diag

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StatFs
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * 设备诊断：本机检查采集（Android）+ 判定（纯函数，可单测）。
 *
 * 检查项（契约「设备诊断」）：机型（MANUFACTURER/MODEL）、Android 版本、App 版本、屏幕、语言、
 * 内存/可用存储、网络类型/连通性、CloudBase 网关延迟、时区/时钟偏差、本地数据可用性。
 *
 * 分两层：
 * - [AndroidDeviceProbe]：读系统 API → [DiagProbeInput]（仅本文件依赖 Android framework）；
 * - [DiagEvaluator]：把 [DiagProbeInput] 判定为 [DiagCheck] 列表（纯 Kotlin，JVM 单测直接覆盖）。
 */
data class DiagProbeInput(
    val manufacturer: String = "",
    val model: String = "",
    val androidVersion: String = "",
    val sdkInt: Int = 0,
    val appVersion: String = "",
    val appVersionCode: Int = 0,
    val screenWidthDp: Int = 0,
    val screenHeightDp: Int = 0,
    val densityDpi: Int = 0,
    val language: String = "",
    val timezoneId: String = "",
    val timezoneOffsetMinutes: Int = 0,
    val memoryTotalMb: Long = 0,
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

    /** 上报 payload 的 device 字段（白名单键；值在 DiagPayload.filterMap 再脱敏）。 */
    fun deviceMap(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        out["platform"] = "android"
        if (androidVersion.isNotBlank()) out["os_version"] = androidVersion
        if (sdkInt > 0) out["sdk"] = sdkInt.toString()
        if (manufacturer.isNotBlank()) out["manufacturer"] = manufacturer
        if (model.isNotBlank()) out["model"] = model
        if (screenWidthDp > 0 && screenHeightDp > 0) {
            out["screen"] = "${screenWidthDp}x${screenHeightDp}dp"
            out["screen_dp"] = "${screenWidthDp}x${screenHeightDp}"
        }
        if (densityDpi > 0) out["density"] = densityDpi.toString()
        if (language.isNotBlank()) out["language"] = language
        if (timezoneId.isNotBlank()) {
            out["timezone"] = "$timezoneId (${DiagEvaluator.formatUtcOffset(timezoneOffsetMinutes)})"
        }
        if (networkType.isNotBlank()) out["network"] = networkType
        if (memoryTotalMb > 0) out["memory_total_mb"] = memoryTotalMb.toString()
        if (memoryTotalMb > 0) out["memory_avail_mb"] = memoryAvailMb.toString()
        if (storageTotalMb > 0) {
            out["storage_total_mb"] = storageTotalMb.toString()
            out["storage_free_mb"] = storageFreeMb.toString()
        }
        if (appVersion.isNotBlank()) out["app_version"] = appVersion
        gatewayLatencyMs?.let { out["gateway_latency_ms"] = it.toString() }
        clockSkewMs?.let { out["clock_skew_ms"] = it.toString() }
        out["local_data_ok"] = localDataOk.toString()
        return out
    }
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

/** 纯函数判定：输入本机采集值 → 诊断项列表（顺序固定，UI/上报都用）。 */
object DiagEvaluator {

    fun evaluate(input: DiagProbeInput): List<DiagCheck> {
        val checks = ArrayList<DiagCheck>(14)
        checks += make(
            id = "device",
            label = "设备型号",
            status = DiagStatus.PASS,
            value = listOf(input.manufacturer, input.model).filter { it.isNotBlank() }
                .joinToString(" ").ifBlank { "未知" },
        )
        checks += make(
            id = "os_version",
            label = "Android 版本",
            status = DiagStatus.PASS,
            value = listOf(input.androidVersion, if (input.sdkInt > 0) "API ${input.sdkInt}" else "")
                .filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "未知" },
        )
        checks += make(
            id = "app_version",
            label = "App 版本",
            status = DiagStatus.PASS,
            value = input.appVersion.ifBlank { "未知" } +
                if (input.appVersionCode > 0) " (${input.appVersionCode})" else "",
        )
        checks += make(
            id = "screen",
            label = "屏幕",
            status = DiagStatus.PASS,
            value = if (input.screenWidthDp > 0) {
                "${input.screenWidthDp}×${input.screenHeightDp}dp · ${input.densityDpi}dpi"
            } else {
                "未知"
            },
        )
        checks += make(
            id = "language",
            label = "语言",
            status = DiagStatus.PASS,
            value = input.language.ifBlank { "未知" },
        )

        // 内存
        val memoryRatio = if (input.memoryTotalMb > 0) {
            input.memoryAvailMb.toDouble() / input.memoryTotalMb.toDouble()
        } else {
            1.0
        }
        val memoryStatus = when {
            input.memoryTotalMb <= 0 -> DiagStatus.INFO
            input.lowMemory || memoryRatio < DiagThresholds.MEMORY_FAIL_RATIO -> DiagStatus.FAIL
            memoryRatio < DiagThresholds.MEMORY_WARN_RATIO -> DiagStatus.WARN
            else -> DiagStatus.PASS
        }
        checks += make(
            id = "memory",
            label = "可用内存",
            status = memoryStatus,
            value = if (input.memoryTotalMb > 0) {
                "${input.memoryAvailMb} / ${input.memoryTotalMb} MB"
            } else {
                "无法读取"
            },
            hint = when (memoryStatus) {
                DiagStatus.FAIL -> if (input.lowMemory) "系统处于低内存状态" else "可用内存过低，可能被杀进程"
                DiagStatus.WARN -> "可用内存偏低"
                else -> ""
            },
            reportKind = "perf",
        )

        // 存储
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
            value = if (input.storageTotalMb > 0) {
                "${input.storageFreeMb} / ${input.storageTotalMb} MB"
            } else {
                "无法读取"
            },
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
            value = networkLabel(networkType) + if (offline) "" else if (input.networkValidated) " · 已连通" else " · 未验证连通",
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

        // 时区（只做信息展示；偏移见 formatUtcOffset）
        checks += make(
            id = "timezone",
            label = "时区",
            status = DiagStatus.PASS,
            value = (input.timezoneId.ifBlank { "未知" }) + " " + formatUtcOffset(input.timezoneOffsetMinutes),
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

    /** 固定宽度 UTC 偏移文案，如 `UTC+08:00`。 */
    fun formatUtcOffset(minutes: Int): String {
        val sign = if (minutes >= 0) "+" else "-"
        val abs = kotlin.math.abs(minutes)
        return "UTC$sign${"%02d".format(abs / 60)}:${"%02d".format(abs % 60)}"
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
 * Android 实现：读取 Build / 屏幕 / Locale / 内存 / 存储 / 网络 / 本地目录可用性。
 *
 * CloudBase 网关延迟与服务器时钟由数据层（[DiagRepository.measureGateway]）测量后回填。
 */
class AndroidDeviceProbe(
    private val context: Context,
    private val appVersion: String = "",
    private val appVersionCode: Int = 0,
) {
    fun collectBase(nowMillis: Long = System.currentTimeMillis()): DiagProbeInput {
        val metrics = context.resources.displayMetrics
        val density = metrics.density.takeIf { it > 0f } ?: 1f
        val zone = ZoneId.systemDefault()
        val offsetMinutes = zone.rules.getOffset(Instant.ofEpochMilli(nowMillis)).totalSeconds / 60

        val memory = readMemory()
        val storage = readStorage()
        val network = readNetwork()
        val local = readLocalData()

        return DiagProbeInput(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            androidVersion = Build.VERSION.RELEASE.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            appVersion = appVersion,
            appVersionCode = appVersionCode,
            screenWidthDp = (metrics.widthPixels / density).toInt(),
            screenHeightDp = (metrics.heightPixels / density).toInt(),
            densityDpi = metrics.densityDpi,
            language = Locale.getDefault().toLanguageTag(),
            timezoneId = zone.id,
            timezoneOffsetMinutes = offsetMinutes,
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

    private fun readLocalData(): Pair<Boolean, String> {
        return try {
            val dir = context.filesDir ?: return false to "filesDir 不可用"
            if (!dir.isDirectory) return false to "应用私有目录缺失"
            val probe = File(dir, "diag_probe.tmp")
            probe.writeText("ok")
            val ok = probe.isFile && probe.length() == 2L
            probe.delete()
            if (ok) {
                true to "应用私有目录可读写"
            } else {
                false to "应用私有目录不可写"
            }
        } catch (_: Throwable) {
            false to "应用私有目录不可写"
        }
    }

    private companion object {
        const val MB: Long = 1024L * 1024L
    }
}
