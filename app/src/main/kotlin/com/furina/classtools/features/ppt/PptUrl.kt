package com.furina.classtools.features.ppt

/**
 * manifest 资源地址解析（契约 §5「URL 解析规则（三端一致）」）。
 *
 * - `img / thumb / s` 若以 `http://`、`https://`、`data:` 开头 → 直接使用；
 * - 否则 `base + 文件名`（base 结尾有无 `/` 都兼容）；
 * - 空引用 → 空串（调用方跳过加载，不报错）。
 */
object PptUrl {

    private val ABSOLUTE_PREFIXES = listOf("http://", "https://", "data:", "file:", "content:")

    /** 是否绝对地址（直接可用）。 */
    fun isAbsolute(ref: String?): Boolean {
        val value = ref?.trim().orEmpty()
        if (value.isEmpty()) return false
        val lower = value.lowercase()
        return ABSOLUTE_PREFIXES.any { lower.startsWith(it) }
    }

    /** 解析相对引用；绝对引用原样返回（去首尾空白）。 */
    fun resolve(base: String?, ref: String?): String {
        val reference = ref?.trim().orEmpty()
        if (reference.isEmpty()) return ""
        if (isAbsolute(reference)) return reference
        val b = base?.trim().orEmpty()
        if (b.isEmpty()) return reference
        val prefix = if (b.endsWith("/")) b else "$b/"
        return prefix + reference.removePrefix("/")
    }
}
