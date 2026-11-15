package com.furina.classtools.data.todaycampus

/**
 * 服务入口的**本地**搜索过滤（纯逻辑，无网络、无 Android 依赖）。
 *
 * 口径：
 * - 匹配字段 = 名称 + 说明 + 补充关键词（中文别名 / 拼音缩写）；
 * - 大小写不敏感、忽略所有空白（「学 工」也能匹配「学工服务」）；
 * - 空查询返回全部入口（保持目录原顺序）。
 */
object TodayCampusSearch {

    /** 搜索词最大长度（UI 侧截断，防止超长输入）。 */
    const val MAX_QUERY_LENGTH: Int = 30

    private val WHITESPACE = Regex("\\s+")

    /** 归一化：去首尾空白 → 小写 → 去掉内部空白。 */
    fun normalize(raw: String?): String =
        raw.orEmpty().trim().lowercase().replace(WHITESPACE, "")

    /** 入口是否命中归一化查询 [normalizedQuery]（空查询恒为 true）。 */
    fun matches(entry: TodayCampusEntry, normalizedQuery: String): Boolean {
        if (normalizedQuery.isEmpty()) return true
        if (matchesText(entry.name, normalizedQuery)) return true
        if (matchesText(entry.desc, normalizedQuery)) return true
        return entry.keywords.any { matchesText(it, normalizedQuery) }
    }

    /** 过滤入口列表；空查询返回原列表（同一实例，保持顺序）。 */
    fun filter(entries: List<TodayCampusEntry>, query: String?): List<TodayCampusEntry> {
        val normalized = normalize(query)
        if (normalized.isEmpty()) return entries
        return entries.filter { matches(it, normalized) }
    }

    private fun matchesText(text: String, normalizedQuery: String): Boolean =
        normalize(text).contains(normalizedQuery)
}
