package com.furina.classtools.features.scan

/**
 * 「扫一扫」识别出的内容类型（纯 Kotlin，可单测；不依赖 Android）。
 *
 * 网页二维码只有两类：
 * - 扫码登录：`https://tool.furina.fun/#qr=<32 位 hex>[&p=harmony]`（通用 / 鸿蒙）；
 * - 抽签存证核验：`https://tool.furina.fun/#verify=<存证 id>`（旧码）或
 *   `https://tool.furina.fun/l/draw/<存证 id>`（统一内容链接，见 docs/app-links/CONTRACT.md）。
 * 另外兼容直接粘贴裸票据/裸存证 id（调试与手工输入）。
 */
sealed interface QrPayload {
    /** 扫码登录：ticket 交给 ECS `/qr/authorize`，harmony 仅作展示/埋点。 */
    data class Login(val ticket: String, val harmony: Boolean) : QrPayload

    /** 抽签结果核验：recordId 交给云端 `rpc/draw_record_get`。 */
    data class DrawVerify(val recordId: String) : QrPayload

    /** 非本站二维码 / 无法识别：UI 提示重新扫描。 */
    data class Unknown(val text: String) : QrPayload
}

object QrPayloadParser {

    private val TICKET_RE = Regex("^[0-9a-f]{32}$")
    private val RECORD_RE = Regex("^d[0-9a-z]{8,31}$")
    private val QR_IN_URL_RE = Regex("[#?&]qr=([0-9a-fA-F]{32})")
    private val P_IN_URL_RE = Regex("[#?&]p=([a-zA-Z]+)")
    private val VERIFY_IN_URL_RE = Regex("[#?&]verify=([A-Za-z0-9%._~-]+)")

    /** 统一内容链接 `/l/draw/<id>`（新二维码）；只认本站 HTTPS 链接。 */
    private val LINK_DRAW_RE =
        Regex("^https://tool\\.furina\\.fun/l/draw/([A-Za-z0-9%._~-]{1,64})(?:\\?[^#]*)?(?:#.*)?$")

    fun parse(raw: String?): QrPayload {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return QrPayload.Unknown("")

        // 登录二维码优先（网页 `#qr=<t>[&p=...]`）
        QR_IN_URL_RE.find(text)?.let { match ->
            val ticket = match.groupValues[1].lowercase()
            if (TICKET_RE.matches(ticket)) {
                val p = P_IN_URL_RE.find(text)?.groupValues?.getOrNull(1)?.lowercase()
                return QrPayload.Login(ticket = ticket, harmony = p == "harmony")
            }
        }

        // 抽签存证核验（统一内容链接 `/l/draw/<id>`，2026-11-27 起的新二维码）
        LINK_DRAW_RE.find(text)?.let { match ->
            val id = urlDecode(match.groupValues[1]).lowercase()
            if (RECORD_RE.matches(id)) return QrPayload.DrawVerify(id)
        }

        // 抽签存证核验（旧码 `#verify=<id>`）
        VERIFY_IN_URL_RE.find(text)?.let { match ->
            val id = urlDecode(match.groupValues[1]).lowercase()
            if (RECORD_RE.matches(id)) return QrPayload.DrawVerify(id)
        }

        // 裸票据 / 裸存证 id（手工输入、调试）
        val bare = text.lowercase()
        if (TICKET_RE.matches(bare)) return QrPayload.Login(ticket = bare, harmony = false)
        if (RECORD_RE.matches(bare)) return QrPayload.DrawVerify(bare)

        return QrPayload.Unknown(text.take(200))
    }

    private fun urlDecode(value: String): String =
        try {
            java.net.URLDecoder.decode(value, "UTF-8")
        } catch (_: Exception) {
            value
        }
}
