package com.furina.classtools.data.update

import com.furina.classtools.BuildConfig
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.asBooleanOrNull
import com.furina.classtools.data.network.asIntOrNull
import com.furina.classtools.data.network.asStringOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

// =============================================================================
// 纯逻辑（无 android.*）：JSON 解析 / 版本比较 / 端点 fallback / 冷启动自动检查闸门 /
// 强制更新规则 / 下载状态机 / 文件校验 / 安装动作判定。全部可 JVM 单测。
// =============================================================================

/** 测试版本标识（Gradle buildConfigField 单一来源：app/build.gradle.kts）。 */
object UpdateTestVersion {
    /** 测试版本序号（当前 3 → 1.0.0-beta3）。 */
    val version: Int
        get() = BuildConfig.TEST_VERSION

    /** 「测试版本」文案（关于页 / 更新弹窗渠道标签）。 */
    val label: String
        get() = BuildConfig.TEST_VERSION_LABEL

    /** 更新弹窗标题：发现新版本（用户 2026-11-17 晚要求；测试版本号移到弹窗内容/渠道行）。 */
    fun dialogTitle(): String = "发现新版本"

    /** 关于页版本展示：v1.0.0-beta3（测试版本）。Debug 包名后缀 -debug 不展示。 */
    fun versionDisplay(versionName: String): String = "v${UpdateVersion.normalize(versionName)}（$label）"
}

/** 版本号比较 / 展示归一化（纯函数）。 */
object UpdateVersion {

    /**
     * 是否需要更新：远端 versionCode 严格大于当前 versionCode。
     * 相同 / 更小 → 已是最新（不做 versionName 回退，避免测试版数字段互相覆盖）。
     */
    fun isNewer(info: UpdateInfo, currentVersionCode: Int): Boolean = info.versionCode > currentVersionCode

    /**
     * 版本名比较（纯字符串；用于单测/展示兜底，更新判定仍以 versionCode 为准）：
     * 数值段按数字比（4.10.0 > 4.9.9），`-test1/test2` 等预发布段按数字比，
     * 同前缀时「无预发布后缀」的正式版大于预发布版（4.57.0 > 4.57.0-test1）。
     */
    fun compareVersionNames(a: String, b: String): Int {
        val left = tokenize(a)
        val right = tokenize(b)
        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            val l = left.getOrNull(index)
            val r = right.getOrNull(index)
            if (l == null && r == null) return 0
            if (l == null) {
                // a 已结束：b 剩余若全是 0（4.57 vs 4.57.0）视为相等，否则 b 是预发布 → a 更大。
                val rest = right.drop(index)
                return if (rest.all { it.number == 0 }) 0 else 1
            }
            if (r == null) {
                val rest = left.drop(index)
                return if (rest.all { it.number == 0 }) 0 else -1
            }
            if (l.number != null && r.number != null) {
                if (l.number != r.number) return l.number.compareTo(r.number)
                continue
            }
            if (l.number == null && r.number == null) {
                val byText = l.text.compareTo(r.text)
                if (byText != 0) return byText
                continue
            }
            // 一边数字一边字母：数字段低于字母段（semver：numeric < alphanumeric）。
            return if (l.number != null) -1 else 1
        }
        return 0
    }

    /** 展示归一化：去首尾空白、去 `v` 前缀、去 Debug 构建的 `-debug` 后缀。 */
    fun normalize(versionName: String): String = versionName
        .trim()
        .removePrefix("v")
        .removeSuffix("-debug")
        .trim()

    private fun tokenize(raw: String): List<Token> {
        val tokens = ArrayList<Token>()
        val current = StringBuilder()
        fun flushText() {
            if (current.isNotEmpty()) {
                tokens.add(Token(number = null, text = current.toString().lowercase()))
                current.clear()
            }
        }
        raw.trim().removePrefix("v").forEach { ch ->
            when {
                ch.isDigit() -> {
                    if (current.isNotEmpty() && !current.last().isDigit()) flushText()
                    current.append(ch)
                }
                ch.isLetter() -> {
                    if (current.isNotEmpty() && current.last().isDigit()) flushText()
                    current.append(ch)
                }
                else -> flushText()
            }
        }
        flushText()
        return tokens.map { token ->
            val number = token.text.toIntOrNull()
            if (number != null) Token(number = number, text = token.text) else token
        }
    }

    private data class Token(val number: Int?, val text: String)
}

/** 远端 latest.json 解析（容错：字段类型宽松，坏数据返回 null 走 fallback / 静默失败）。 */
object UpdateJsonParser {

    fun parse(text: String?): UpdateInfo? {
        val root = parseJsonOrNull(NetJson, text) as? JsonObject ?: return null
        val versionCode = intField(root["versionCode"]) ?: return null
        if (versionCode <= 0) return null
        val apkUrl = stringField(root["apkUrl"]).orEmpty()
        if (!isHttpUrl(apkUrl)) return null
        val versionName = stringField(root["versionName"])
            ?.takeIf { it.isNotEmpty() }
            ?: versionCode.toString()
        return UpdateInfo(
            versionCode = versionCode,
            versionName = versionName,
            apkUrl = apkUrl,
            notes = notesOf(root["notes"]),
            force = boolField(root["force"]),
            channel = stringField(root["channel"])?.takeIf { it.isNotEmpty() }
                ?: UpdateInfo.DEFAULT_CHANNEL,
            sha256 = stringField(root["sha256"]).orEmpty(),
            sizeBytes = longField(root["sizeBytes"])?.takeIf { it > 0 },
        )
    }

    /** notes 允许字符串 / 字符串数组（数组按行拼接，空项过滤）。 */
    fun notesOf(element: JsonElement?): String = when (element) {
        null -> ""
        is JsonArray -> element
            .mapNotNull { item -> (item as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
            .joinToString("\n")
        is JsonPrimitive -> element.contentOrNull?.trim().orEmpty()
        else -> ""
    }

    fun isHttpUrl(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.startsWith("http://") || trimmed.startsWith("https://")
    }

    private fun stringField(element: JsonElement?): String? =
        (element as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)

    private fun intField(element: JsonElement?): Int? {
        val primitive = element as? JsonPrimitive ?: return null
        return primitive.intOrNull ?: primitive.contentOrNull?.trim()?.toIntOrNull()
    }

    private fun longField(element: JsonElement?): Long? {
        val primitive = element as? JsonPrimitive ?: return null
        return primitive.longOrNull ?: primitive.contentOrNull?.trim()?.toLongOrNull()
    }

    private fun boolField(element: JsonElement?): Boolean {
        val primitive = element as? JsonPrimitive ?: return false
        primitive.booleanOrNull?.let { return it }
        return when (primitive.contentOrNull?.trim()?.lowercase()) {
            "true", "1", "yes", "on" -> true
            else -> false
        }
    }
}

/**
 * 更新远端地址（按顺序 fallback）：
 * 1) ECS IP 调试入口（备案期唯一公网入口，`http://121.196.47.2/`，network_security_config 已放行）；
 * 2) 阿里云 OSS 公共读域名（IP 不可达时的兜底）。
 */
object UpdateEndpoints {
    const val PRIMARY: String = "http://121.196.47.2/android/latest.json"
    const val SECONDARY: String =
        "https://tool-furina-fun.oss-cn-hongkong.aliyuncs.com/android/latest.json"

    val ALL: List<String> = listOf(PRIMARY, SECONDARY)

    /** 端点链：去空白/去重/保持顺序；测试可注入自定义地址。 */
    fun chain(primary: String = PRIMARY, secondary: String = SECONDARY): List<String> =
        listOf(primary, secondary)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
}

/**
 * 冷启动自动检查闸门（纯 Kotlin，可 JVM 单测）。
 *
 * 2026-11-16 用户要求：检测到更新时在首页自动弹窗，不再依赖「我的 → 检查更新」；
 * 同时**去掉每日节流**——每次冷启动都自动检查一次，但同一进程内只检查一次（`UpdateHost` 挂在根级）。
 * - 进程刚启动：第一次 [tryBegin] 返回 true → 自动检查一次；
 * - 检查失败不重置闸门 = 失败退避：同一进程内不再自动重试（手动「检查更新」不经过本闸门，随时可用）；
 * - 下一次冷启动是新的进程/新的 Manager，自然重新检查。
 */
class UpdateAutoCheckGate {

    private var attempted = false

    /** 登记本次自动检查；返回 true = 可以检查，false = 本进程已检查过，跳过。 */
    @Synchronized
    fun tryBegin(): Boolean {
        if (attempted) return false
        attempted = true
        return true
    }
}

/**
 * 强制更新 / 下载取消规则。
 *
 * 用户 2026-11-17 晚要求：检测到新版本**只弹窗**，不自动下载、下载完成也不自动拉起安装器；
 * 由用户在弹窗里选择「立即更新 / 稍后」「立即安装」。
 */
object UpdateDialogPolicy {
    /** 非强制版本可以「稍后」；force=true 时不可取消（无稍后、无取消下载）。 */
    fun isDismissible(info: UpdateInfo): Boolean = !info.force

    fun canCancelDownload(info: UpdateInfo): Boolean = !info.force
}

/** 下载进度换算。 */
object UpdateProgressLogic {
    fun percent(downloadedBytes: Long, totalBytes: Long): Int {
        if (totalBytes <= 0L || downloadedBytes <= 0L) return 0
        return ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
    }

    fun progress(downloadedBytes: Long, totalBytes: Long): UpdateDownloadProgress =
        UpdateDownloadProgress(
            downloadedBytes = downloadedBytes.coerceAtLeast(0L),
            totalBytes = totalBytes.coerceAtLeast(0L),
            percent = percent(downloadedBytes, totalBytes),
        )
}

/** 下载状态机：事件 → 新状态（UI 与 Manager 共用，单测覆盖迁移）。 */
object UpdateDownloadReducer {
    fun reduce(current: UpdateDownloadState, event: UpdateDownloadEvent): UpdateDownloadState =
        when (event) {
            is UpdateDownloadEvent.Progress -> UpdateDownloadState.Downloading(
                UpdateProgressLogic.progress(event.downloadedBytes, event.totalBytes),
            )

            is UpdateDownloadEvent.Done -> UpdateDownloadState.Ready(event.apk)

            is UpdateDownloadEvent.Failed -> UpdateDownloadState.Failed(event.reason)
        }
}

/** APK 文件命名 / 目录（非法字符替换，保证 .apk 后缀）。 */
object UpdateFileNames {
    /** 公共 Downloads 下的子目录（FileProvider `update_public_downloads` 对应）。 */
    const val PUBLIC_DOWNLOAD_SUB_DIR: String = "ClassTools"

    /** 应用 cache 下的回退目录（FileProvider `update_cache` 对应）。 */
    const val CACHE_SUB_DIR: String = "updates"

    fun apkFileName(info: UpdateInfo): String {
        val raw = "classtools-${info.versionName.ifBlank { info.versionCode.toString() }}"
        val safe = raw
            .replace(Regex("[^A-Za-z0-9._-]"), "-")
            .trim('-', '.')
            .ifBlank { "classtools-${info.versionCode}" }
        return "$safe.apk"
    }
}

/** 下载包完整性校验（sha256；远端未提供时跳过校验）。 */
object UpdateDigest {
    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
            "%02x".format(byte)
        }

    fun sha256Hex(file: File): String = sha256Hex(file.readBytes())

    fun normalize(hash: String): String = hash
        .trim()
        .lowercase()
        .removePrefix("sha256:")
        .removePrefix("sha256=")
        .trim()

    /** expected 为空 → 不校验直接通过；格式非法（非 64 位 hex）→ 校验失败。 */
    fun matches(expected: String, actualHex: String): Boolean {
        val wanted = normalize(expected)
        if (wanted.isEmpty()) return true
        if (wanted.length != SHA256_HEX_LENGTH) return false
        if (!wanted.all { it in "0123456789abcdef" }) return false
        return wanted == normalize(actualHex)
    }

    const val SHA256_HEX_LENGTH: Int = 64
}

/** 安装动作判定（权限/无处理器两个分支是系统限制下唯一需要提前判定的情况）。 */
object UpdateInstallPolicy {
    fun nextAction(hasInstaller: Boolean, canRequestPackageInstalls: Boolean): UpdateInstallAction =
        when {
            !hasInstaller -> UpdateInstallAction.NO_HANDLER
            !canRequestPackageInstalls -> UpdateInstallAction.NEED_UNKNOWN_SOURCES_PERMISSION
            else -> UpdateInstallAction.INSTALL
        }
}

/** 下载进度展示格式化（KB/MB；纯函数可单测）。 */
object UpdateFormat {
    fun bytes(count: Long): String {
        if (count <= 0L) return "0 B"
        if (count < 1024L) return "$count B"
        val kb = count / 1024.0
        if (kb < 1024.0) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }
}

/**
 * 更新弹窗文案（纯函数，便于 JVM 单测；UI 只做排版）：
 * - 标题固定「发现新版本」；
 * - 副标题「当前 vX → 新版本 vY」（当前版本由 Manager 从 BuildConfig 注入）；
 * - 包大小 / 渠道为可选行（缺省不显示）。
 */
object UpdateDialogText {

    fun title(): String = "发现新版本"

    /** 当前 vX → 新版本 vY（当前版本未知时只显示新版本）。 */
    fun subtitle(currentVersionName: String, info: UpdateInfo): String {
        val current = UpdateVersion.normalize(currentVersionName)
        val target = UpdateVersion.normalize(info.versionName.ifBlank { info.versionCode.toString() })
        return if (current.isBlank()) "新版本 v$target" else "当前 v$current → 新版本 v$target"
    }

    /** 包大小行（`sizeBytes` 缺省/非法 → null，不显示）。 */
    fun sizeText(info: UpdateInfo): String? =
        info.sizeBytes?.takeIf { it > 0L }?.let { "包大小：${UpdateFormat.bytes(it)}" }

    /** 渠道行（空渠道 → null，不显示）。 */
    fun channelText(info: UpdateInfo): String? =
        info.channel.trim().takeIf { it.isNotEmpty() }?.let { "渠道：$it" }

    /** 下载完成后的提示（不自动拉起安装器，等用户点「立即安装」）。 */
    fun readyText(installLaunched: Boolean): String = if (installLaunched) {
        "已打开系统安装器，请在系统界面确认安装。"
    } else {
        "下载完成，点击「立即安装」。"
    }
}
