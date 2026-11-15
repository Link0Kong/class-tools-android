package com.furina.classtools.data.push

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * OPPO PUSH 通知点击 → App 内跳转参数（服务端 `clickActionType=4` + `clickActionActivity` + `actionParameters`）。
 *
 * 服务端约定（三端契约，Lead 定义）：
 * ```
 * clickActionType=4
 * clickActionActivity="com.furina.classtools.MainActivity"
 * actionParameters="{\"noticeId\":\"<gid>\"}"
 * channel_id="class_notice"
 * ```
 * 客户端在 MainActivity 冷启动 / onNewIntent 两处解析：
 * - 优先直接读取 intent extra `noticeId`（部分 ROM 会展开 actionParameters）；
 * - 否则解析 intent extra `actionParameters`（JSON 字符串）里的 `noticeId`；
 * 解析成功后进入通知工具页（`nt`）并定位到该通知（NoticesScreen `initialFocusNoticeId`）。
 *
 * 本对象为纯逻辑（不引用 Android 类型），单测直接覆盖解析与路由映射。
 */
object OppPushClick {

    /** intent extra：直接携带的通知 id。 */
    const val EXTRA_NOTICE_ID: String = "noticeId"

    /** intent extra：服务端 clickActionType=4 的 actionParameters JSON 原文。 */
    const val EXTRA_ACTION_PARAMETERS: String = "actionParameters"

    /** 通知工具页的 ToolCatalog id（与 MainNavHost `"nt"` 分支一致）。 */
    const val NOTICE_TOOL_ID: String = "nt"

    /** 通知 id 白名单：组 id（如 `g_xxx` / `auto_x`）或期次 key（`gid@occ:YYYYMMDD`）。 */
    private val NOTICE_ID_PATTERN: Regex = Regex("^[A-Za-z0-9_@:.\\-]{1,128}$")

    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 校验/规范化通知 id（trim；空、超长、含可疑字符 → null，避免把任意 intent 内容当 id）。 */
    fun parseNoticeId(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (text.length > 128) return null
        if (!NOTICE_ID_PATTERN.matches(text)) return null
        return text
    }

    /**
     * 从 intent 的两类 extra 解析通知 id：
     * [directNoticeId]（extra `noticeId`）优先；否则解析 [actionParametersJson]（extra `actionParameters`）。
     * 解析失败返回 null（App 正常打开，不跳转）。
     */
    fun noticeIdFrom(actionParametersJson: String?, directNoticeId: String?): String? {
        parseNoticeId(directNoticeId)?.let { return it }
        val payload = actionParametersJson?.trim().orEmpty()
        if (payload.isEmpty()) return null
        val obj = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        // 只接受 JSON 字符串（数字/布尔等非字符串值一律忽略）
        val primitive = obj[EXTRA_NOTICE_ID] as? JsonPrimitive ?: return null
        if (!primitive.isString) return null
        return parseNoticeId(primitive.contentOrNull)
    }

    /** 点击目标：有通知 id → 通知工具页 `nt`；无 → null（普通启动）。 */
    fun targetToolId(noticeId: String?): String? =
        if (parseNoticeId(noticeId) != null) NOTICE_TOOL_ID else null
}
