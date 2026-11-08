package com.furina.classtools.data.diag

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * `client_diag` 上报行的构造（契约 §2：字段白名单 + 脱敏 + 去重指纹）。
 *
 * 契约原文列出的行字段：`{ id, platform, app_ver, kind, level, message, detail:{}, device:{}, fingerprint, ts }`；
 * 另外 **`sid` 必须随行提交**——云端 RLS `cd_insert` 要求 `sid = auth_sid()`（见 2026-11-08_diag_login.sql），
 * 客户端不传 sid 会被 RLS 拒绝。sid 只出现在行顶层（本人身份，不打印/不落日志），
 * detail/device 内绝不包含学号/姓名/宿舍号/token/密码/URL/路径。
 */
object DiagPayload {

    const val PLATFORM: String = "android"
    const val ID_PREFIX: String = "an_"

    /** device 允许键（契约 §2：平台/系统版本/机型/屏幕/内存/网络类型/语言/应用版本/延迟/存储用量/特性布尔）。 */
    val DEVICE_KEYS: Set<String> = setOf(
        "platform", "os_version", "sdk", "manufacturer", "model",
        "screen", "screen_dp", "density", "language", "timezone", "network",
        "memory_total_mb", "memory_avail_mb", "storage_total_mb", "storage_free_mb",
        "app_version", "gateway_latency_ms", "clock_skew_ms", "local_data_ok", "features",
    )

    /** detail 允许键（检查 id/状态/延迟/比例/错误码等，全部转成字符串后脱敏）。 */
    val DETAIL_KEYS: Set<String> = setOf(
        "check_id", "status", "kind", "level", "hint", "value",
        "latency_ms", "clock_skew_ms", "network_type", "network_validated",
        "memory_ratio", "storage_ratio", "available_mb", "free_mb", "total_mb",
        "error_code", "error_message", "exception", "thread", "screen", "source", "app_version",
    )

    /** kind 白名单（契约 §2）。 */
    val KINDS: Set<String> = setOf("error", "network", "storage", "crash", "perf", "manual")

    fun newId(suffix: String = DiagSanitizer.randomSuffix(12), prefix: String = ID_PREFIX): String =
        prefix + suffix

    fun fingerprint(kind: String, level: String, checkId: String, message: String): String =
        DiagSanitizer.fingerprint(kind, level, checkId, message)

    /**
     * 构造一行上报 JSON（供 POST `client_diag` 数组使用）。
     *
     * @param sid 当前会话学号（RLS 校验用；只在此行顶层，不进入 detail/device）
     */
    fun buildRow(
        id: String,
        sid: String,
        appVersion: String,
        kind: String,
        level: String,
        message: String,
        detail: Map<String, String> = emptyMap(),
        device: Map<String, String> = emptyMap(),
        fingerprint: String,
        now: Long,
    ): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(id))
        put("sid", JsonPrimitive(sid))
        put("platform", JsonPrimitive(PLATFORM))
        put("app_ver", JsonPrimitive(DiagSanitizer.sanitizeText(appVersion, 40)))
        put("kind", JsonPrimitive(kind.takeIf { it in KINDS } ?: "error"))
        put("level", JsonPrimitive(DiagLevel.fromWire(level).wire))
        put("message", JsonPrimitive(DiagSanitizer.sanitizeText(message, 300)))
        put("detail", filterMap(detail, DETAIL_KEYS))
        put("device", filterMap(device, DEVICE_KEYS))
        put("fingerprint", JsonPrimitive(fingerprint.take(64)))
        put("ts", JsonPrimitive(now))
    }

    /** 由一条设备诊断项构造上报行（自动按状态定 level/kind；fingerprint 稳定 → 10 分钟去重）。 */
    fun fromCheck(
        check: DiagCheck,
        sid: String,
        appVersion: String,
        device: Map<String, String>,
        now: Long,
        id: String = newId(),
    ): JsonObject {
        val kind = if (check.reportKind in KINDS) check.reportKind else "error"
        val level = check.level.wire
        val message = DiagSanitizer.sanitizeText(
            "${check.label}：${check.value.ifBlank { check.hint }}".trim(),
            300,
        )
        val detail = LinkedHashMap<String, String>()
        detail["check_id"] = check.id
        detail["status"] = check.status.name.lowercase()
        detail["kind"] = check.reportKind
        detail["level"] = level
        if (check.value.isNotBlank()) detail["value"] = check.value
        if (check.hint.isNotBlank()) detail["hint"] = check.hint
        return buildRow(
            id = id,
            sid = sid,
            appVersion = appVersion,
            kind = kind,
            level = level,
            message = message,
            detail = detail,
            device = device,
            fingerprint = fingerprint(kind, level, check.id, message),
            now = now,
        )
    }

    /** 用户手动上报一条（诊断页「上报」按钮）：kind=manual。 */
    fun manualRow(
        sid: String,
        appVersion: String,
        message: String,
        detail: Map<String, String>,
        device: Map<String, String>,
        now: Long,
        id: String = newId(),
    ): JsonObject {
        val sanitized = DiagSanitizer.sanitizeText(message, 300)
        return buildRow(
            id = id,
            sid = sid,
            appVersion = appVersion,
            kind = "manual",
            level = "info",
            message = sanitized.ifBlank { "用户手动上报" },
            detail = detail,
            device = device,
            fingerprint = fingerprint("manual", "info", "manual", sanitized),
            now = now,
        )
    }

    fun deviceJson(device: Map<String, String>): JsonObject = filterMap(device, DEVICE_KEYS)

    /** 只保留白名单键，值逐个脱敏截断（空值丢弃）。 */
    fun filterMap(values: Map<String, String>, allowed: Set<String>): JsonObject {
        val out = LinkedHashMap<String, String>()
        values.forEach { (key, value) ->
            val name = key.trim()
            if (name !in allowed) return@forEach
            val sanitized = DiagSanitizer.sanitizeText(value, 300)
            if (sanitized.isNotEmpty()) out[name] = sanitized
        }
        return buildJsonObject { out.forEach { (key, value) -> put(key, JsonPrimitive(value)) } }
    }

    /** 提取行指纹（容错：缺失/坏类型 → 空串）。 */
    fun fingerprintOf(row: JsonObject): String =
        (row["fingerprint"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }.orEmpty()
}
