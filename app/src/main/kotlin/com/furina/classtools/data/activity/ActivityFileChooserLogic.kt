package com.furina.classtools.data.activity

/**
 * 活动登录 WebView 的 `<input type=file>` 文件选择纯逻辑（2026-11-16 用户反馈：上传头像点击无反应）。
 *
 * 根因：WebView 默认不处理 `<input type=file>`，必须在 `WebChromeClient.onShowFileChooser`
 * 中返回 true 并打开系统选择器；否则点击上传按钮没有回调（无反应）。
 * 本文件只做可单测的纯数据处理（accept 类型归一、结果解析），不依赖 Android。
 */
object ActivityFileChooserLogic {

    /** 与 `WebChromeClient.FileChooserParams.MODE_*` 对齐的常量（避免测试依赖 Android 类）。 */
    const val MODE_OPEN: Int = 0
    const val MODE_OPEN_MULTIPLE: Int = 1
    const val MODE_SAVE: Int = 2

    /** 默认 accept 类型（无法识别时用通配类型，保证任意文件都能选）。 */
    val DEFAULT_ACCEPT: List<String> = listOf("*/*")

    /**
     * 归一 accept 类型：去空白/空项/去重；无法识别时回退 [DEFAULT_ACCEPT]。
     *
     * WebView 传来的 acceptTypes 可能是「图片通配」、带具体 MIME、或多类型合串，这里按逗号拆开。
     */
    fun normalizeAcceptTypes(raw: List<String>?): List<String> {
        val cleaned = LinkedHashSet<String>()
        raw.orEmpty().forEach { item ->
            item.orEmpty().split(',').forEach { part ->
                val value = part.trim()
                if (value.isNotEmpty()) cleaned.add(value)
            }
        }
        return if (cleaned.isEmpty()) DEFAULT_ACCEPT else cleaned.toList()
    }

    /** 是否允许多选（WebView FileChooserParams.mode == MODE_OPEN_MULTIPLE）。 */
    fun isMultiple(mode: Int?): Boolean = mode == MODE_OPEN_MULTIPLE

    /**
     * 选择器优先 MIME：图片场景用图片通配（头像上传最常见），其余用通配类型。
     * 系统选择器仍可通过 EXTRA_MIME_TYPES 支持多类型。
     */
    fun primaryMimeType(acceptTypes: List<String>): String {
        val types = if (acceptTypes.isEmpty()) DEFAULT_ACCEPT else acceptTypes
        val hasImage = types.any { it.startsWith("image/") }
        val hasVideo = types.any { it.startsWith("video/") }
        val onlyImages = hasImage && types.all { it.startsWith("image/") || it == "*/*" }
        val onlyVideos = hasVideo && types.all { it.startsWith("video/") || it == "*/*" }
        return when {
            onlyImages -> "image/*"
            onlyVideos -> "video/*"
            else -> "*/*"
        }
    }

    /**
     * 解析 Activity 选择结果：
     * - 取消（`resultOk == false`）→ null（必须回传 null 给 `filePathCallback`，否则 input 永久卡住）；
     * - 单选 → `data.data`；
     * - 多选 → `clipData` 里的所有 Uri（去重、去空）。
     */
    fun urisFromResult(
        resultOk: Boolean,
        singleUri: String?,
        clipUris: List<String>?,
    ): List<String>? {
        if (!resultOk) return null
        val uris = LinkedHashSet<String>()
        singleUri?.trim()?.takeIf { it.isNotEmpty() }?.let { uris.add(it) }
        clipUris.orEmpty().forEach { uri -> uri.trim().takeIf { it.isNotEmpty() }?.let { uris.add(it) } }
        return if (uris.isEmpty()) null else uris.toList()
    }

    /** 选择结果是否有效（非空）——用于日志/UI 诊断。 */
    fun isPicked(uris: List<String>?): Boolean = !uris.isNullOrEmpty()
}
