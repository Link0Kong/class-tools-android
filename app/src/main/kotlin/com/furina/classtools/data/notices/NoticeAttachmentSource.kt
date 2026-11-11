package com.furina.classtools.data.notices

/**
 * 通知附件内容形态判定（纯 JVM，可单测）：三端「附件外置」迁移第 2 步（task-5）。
 *
 * 附件内容在 `items[]` 里有两种形态，**必须同时兼容**：
 * 1. 旧数据内联：`data:<mime>;base64,<payload>`（继续支持，不改行为）；
 * 2. 新数据外置：`https://tool.furina.fun/att/<id>?exp=<unix秒>&sig=<hex64>`，
 *    备案期调试也可能是 `http://<ip>/att/...`。
 *    **代码只判断 `^https?://`，不写死域名**；GET 直出字节、无需请求头/cookie、内容不可变。
 *
 * 出现位置：图片 `item.src`、文件 `item.url`、压缩包内文件 `item.archive.files[].url`。
 * `form.realUrl` / `link.url` / `article.url` / `mini.url` 是外链，不属于本判定范围。
 */
object NoticeAttachmentSource {

    /** 内联 data URI 前缀。 */
    const val DATA_PREFIX: String = "data:"

    /** 签名 URL 长度上限（正常签名 URL < 400 字符；超长视为异常数据，不进 UI 模型）。 */
    const val MAX_URL_LENGTH: Int = 4096

    /** 附件内容形态。 */
    enum class Kind {
        /** 内联 `data:`（旧数据）。 */
        INLINE,

        /** 外置 `http(s)://` 签名 URL（新数据）。 */
        URL,

        /** 无内容 / 无法识别。 */
        NONE,
    }

    fun kindOf(value: String?): Kind {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return Kind.NONE
        if (text.startsWith(DATA_PREFIX, ignoreCase = true)) return Kind.INLINE
        return if (isHttpUrl(text)) Kind.URL else Kind.NONE
    }

    /** 是否为 `http://` 或 `https://`（大小写不敏感；只判断协议前缀，不校验 host）。 */
    fun isHttpUrl(value: String?): Boolean {
        val text = value?.trim().orEmpty()
        return text.startsWith("http://", ignoreCase = true) ||
            text.startsWith("https://", ignoreCase = true)
    }

    /**
     * 提取可用的外置 URL：仅接受 http(s)、长度 ≤ [MAX_URL_LENGTH]、不含空白字符。
     *
     * @return 规范化（trim）后的 URL；不符合返回空串（调用方按「无外置 URL」处理）
     */
    fun urlOf(value: String?): String {
        val text = value?.trim().orEmpty()
        if (text.isEmpty() || text.length > MAX_URL_LENGTH) return ""
        if (!isHttpUrl(text)) return ""
        if (text.any { it.isWhitespace() }) return ""
        return text
    }

    /** 提取内联 data URI 原文（trim 后；非 data: 返回 null）。 */
    fun inlineOf(value: String?): String? {
        val text = value?.trim().orEmpty()
        return if (text.startsWith(DATA_PREFIX, ignoreCase = true)) text else null
    }

    /** 是否是可渲染的附件内容（内联或外置）。 */
    fun hasContent(value: String?): Boolean = kindOf(value) != Kind.NONE

    /** data URI 的 MIME（`data:application/pdf;base64,x` → `application/pdf`）；非法返回 null。 */
    fun mimeOfDataUri(value: String?): String? {
        val text = inlineOf(value) ?: return null
        val headerEnd = text.indexOf(',')
        if (headerEnd <= DATA_PREFIX.length) return null
        val header = text.substring(DATA_PREFIX.length, headerEnd)
        return header.substringBefore(';').takeIf { it.isNotBlank() && it.contains('/') }
    }

    /** 人类可读体积（与 `NoticeBodyParser`/小程序口径一致）；`<=0` 返回空串（不显示 0 B）。 */
    fun formatSize(bytes: Long): String = when {
        bytes <= 0L -> ""
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(java.util.Locale.CHINA, "%.1f KB", bytes / 1024.0)
        else -> String.format(java.util.Locale.CHINA, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
