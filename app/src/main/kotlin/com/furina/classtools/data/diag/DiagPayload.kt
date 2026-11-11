package com.furina.classtools.data.diag

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * `client_diag` 上报行的构造（契约 v2.1 §B：设备信息最小化 + detail 白名单 + 脱敏 + 去重指纹）。
 *
 * - **device 唯一白名单**（android）：`platform / osVersion / appVer / webview / screen{w,h,dpr} /
 *   memoryMB / storagePct / network / clockSkewMs`；品牌/型号/语言/API level/时区/存储总量一律不出现。
 * - **detail 唯一白名单**：`check / latency_ms / storage_pct / clock_skew_ms / err_count / code`；
 *   `check`＝设备检查 id（`DeviceDiagnostics`/`DiagEvaluator` 的 id，如 memory/storage/network/gateway/clock），
 *   本机 agent 据此生成 `diag_analysis.related.checks`，把 AI 结论挂到对应设备检查行下。
 * - `sid` 仍在行顶层（云端 RLS `cd_insert` 要求 `sid = auth_sid()`），detail/device 内绝不包含学号/姓名/token。
 */
object DiagPayload {

    const val PLATFORM: String = "android"
    const val ID_PREFIX: String = "an_"

    /** device 允许键（契约 v2.1 §B android 行）。 */
    val DEVICE_KEYS: Set<String> = DiagDeviceInfo.KEYS

    /** screen 子对象允许键。 */
    val SCREEN_KEYS: Set<String> = DiagDeviceInfo.SCREEN_KEYS

    /** detail 允许键（契约 v2.1 §B：check/latency_ms/storage_pct/clock_skew_ms/err_count/code）。 */
    val DETAIL_KEYS: Set<String> = setOf(
        "check", "latency_ms", "storage_pct", "clock_skew_ms", "err_count", "code",
    )

    /** kind 白名单（契约 v2 §2）。 */
    val KINDS: Set<String> = setOf("error", "network", "storage", "crash", "perf", "manual")

    fun newId(suffix: String = DiagSanitizer.randomSuffix(12), prefix: String = ID_PREFIX): String =
        prefix + suffix

    fun fingerprint(kind: String, level: String, checkId: String, message: String): String =
        DiagSanitizer.fingerprint(kind, level, checkId, message)

    /**
     * 构造一行上报 JSON（供 POST `client_diag` 数组使用）。
     *
     * @param sid 当前会话学号（RLS 校验用；只在此行顶层，不进入 detail/device）
     * @param device 仅允许 [DEVICE_KEYS]（由 [DiagDeviceInfo.toJson] 生成；此处再过滤一次兜底）
     */
    fun buildRow(
        id: String,
        sid: String,
        appVersion: String,
        kind: String,
        level: String,
        message: String,
        detail: Map<String, String> = emptyMap(),
        device: JsonObject = JsonObject(emptyMap()),
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
        put("detail", filterDetail(detail))
        put("device", filterDevice(device))
        put("fingerprint", JsonPrimitive(fingerprint.take(64)))
        put("ts", JsonPrimitive(now))
    }

    /**
     * 由一条设备诊断项构造上报行（自动按状态定 level/kind；fingerprint 稳定 → 10 分钟去重）。
     *
     * `detail.check` 必填（= 检查 id），供 agent 生成 `related.checks`；其余字段按检查类型最小化：
     * gateway → latency_ms、storage → storage_pct、clock → clock_skew_ms、crash → err_count/code。
     */
    fun fromCheck(
        check: DiagCheck,
        sid: String,
        appVersion: String,
        device: JsonObject,
        now: Long,
        latencyMs: Long? = null,
        storagePct: Int? = null,
        clockSkewMs: Long? = null,
        errorCount: Int? = null,
        code: String = "",
        id: String = newId(),
    ): JsonObject {
        val kind = if (check.reportKind in KINDS) check.reportKind else "error"
        val level = check.level.wire
        val message = DiagSanitizer.sanitizeText(
            "${check.label}：${check.value.ifBlank { check.hint }}".trim(),
            300,
        )
        val detail = LinkedHashMap<String, String>()
        detail["check"] = check.id
        when (check.id) {
            "gateway" -> latencyMs?.let { detail["latency_ms"] = it.toString() }
            "storage" -> storagePct?.let { detail["storage_pct"] = it.toString() }
            "clock" -> clockSkewMs?.let { detail["clock_skew_ms"] = it.toString() }
        }
        errorCount?.let { detail["err_count"] = it.toString() }
        if (code.isNotBlank()) detail["code"] = code
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

    /** 用户手动上报一条（诊断页「上报」按钮）：kind=manual；detail 仍受白名单约束。 */
    fun manualRow(
        sid: String,
        appVersion: String,
        message: String,
        detail: Map<String, String>,
        device: JsonObject,
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

    /** 崩溃详情（契约 v2.1：detail 只允许 check/code 等白名单键）。 */
    fun crashDetail(exceptionClass: String): Map<String, String> = mapOf(
        "check" to "crash",
        "code" to DiagSanitizer.sanitizeText(exceptionClass, 60),
    )

    /** 只保留 detail 白名单键，值脱敏截断（空值丢弃）。 */
    fun filterDetail(values: Map<String, String>): JsonObject {
        val out = LinkedHashMap<String, String>()
        values.forEach { (key, value) ->
            val name = key.trim()
            if (name !in DETAIL_KEYS) return@forEach
            val sanitized = DiagSanitizer.sanitizeText(value, 120)
            if (sanitized.isNotEmpty()) out[name] = sanitized
        }
        return buildJsonObject { out.forEach { (key, value) -> put(key, JsonPrimitive(value)) } }
    }

    /**
     * device 白名单过滤（页面/旧数据兜底）：
     * - 仅保留 [DEVICE_KEYS]；`screen` 是嵌套对象且仅保留 w/h/dpr；
     * - 字符串值再脱敏截断，数字/布尔原样保留。
     */
    fun filterDevice(raw: JsonObject): JsonObject {
        val out = LinkedHashMap<String, JsonElement>()
        raw.forEach { (key, element) ->
            if (key !in DEVICE_KEYS) return@forEach
            when (key) {
                "screen" -> {
                    val obj = element as? JsonObject ?: return@forEach
                    val screen = LinkedHashMap<String, JsonElement>()
                    obj.forEach { (screenKey, screenValue) ->
                        if (screenKey !in SCREEN_KEYS) return@forEach
                        val value = screenValue as? JsonPrimitive ?: return@forEach
                        screen[screenKey] = if (value.isString) {
                            JsonPrimitive(DiagSanitizer.sanitizeText(value.content, 30))
                        } else {
                            value
                        }
                    }
                    if (screen.isNotEmpty()) out[key] = JsonObject(screen)
                }
                else -> {
                    val value = element as? JsonPrimitive ?: return@forEach
                    out[key] = if (value.isString) {
                        JsonPrimitive(DiagSanitizer.sanitizeText(value.content, 60))
                    } else {
                        value
                    }
                }
            }
        }
        return JsonObject(out)
    }

    /** 提取行指纹（容错：缺失/坏类型 → 空串）。 */
    fun fingerprintOf(row: JsonObject): String =
        (row["fingerprint"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }.orEmpty()

    /** 提取上报行 detail.check（agent 生成 related.checks 的来源；旧数据可能缺失）。 */
    fun checkIdOf(row: JsonObject): String =
        ((row["detail"] as? JsonObject)?.get("check") as? JsonPrimitive)?.content
            ?.trim()
            .orEmpty()
}
