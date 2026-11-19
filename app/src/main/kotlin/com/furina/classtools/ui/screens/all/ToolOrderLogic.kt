package com.furina.classtools.ui.screens.all

import com.furina.classtools.data.network.asObjectOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import com.furina.classtools.data.network.NetJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 「全部工具」拖动排序的纯逻辑（无 Compose/Android 依赖，可单测）。
 *
 * 契约（`web/AGENTS.md` §4b-10 / `mp-class-tools/utils/tools.js` + `utils/cloud.js`）：
 * - 顺序 = `userdata v6 prefs.toolOrder`（工具 id 数组，**全部工具目录顺序**，随账号同步）；
 * - **仅在用户实际拖动排序后写**：`toolOrderSetAt>0` 语义；默认自然顺序不写、无基线不盲写；
 * - 写云端时同时提升 `at.prefs`（与云端 LWW 合并；本端不新增顶层键）；
 * - 归一规则：合法工具 id 去重；未知/新增工具按自然顺序补到末尾（与 mp `normalizeToolOrder` 一致）。
 */
object ToolOrderLogic {

    /**
     * 归一化工具顺序（与 mp `normalizeToolOrder` 一致）：
     * 1. 保留 [raw] 中合法的 [knownIds]（去空白、去重），忽略未知/非法项；
     * 2. 把 [knownIds] 中缺失的工具按自然顺序补到末尾。
     */
    fun normalize(raw: List<String>, knownIds: List<String>): List<String> {
        val known = knownIds.distinct()
        val knownSet = known.toHashSet()
        val out = LinkedHashSet<String>()
        raw.forEach { id ->
            val trimmed = id.trim()
            if (trimmed.isNotEmpty() && trimmed in knownSet) out.add(trimmed)
        }
        known.forEach { out.add(it) }
        return out.toList()
    }

    /** 旧版「homeTools 全量顺序」迁移：长度 >4 说明是旧版顺序数据 → 作为 toolOrder 基线；否则 null（不迁移）。 */
    fun migrateLegacyHomeOrder(homeTools: List<String>, knownIds: List<String>): List<String>? =
        if (homeTools.size > MAX_HOME_TOOLS) normalize(homeTools, knownIds) else null

    /** 把 [id] 从当前位置移动到 `remainingIndex`（目标位置 = 移除自身后的插入下标）。 */
    fun moveToIndex(order: List<String>, id: String, remainingIndex: Int): List<String> {
        if (id !in order) return order
        val remaining = order.filterNot { it == id }.toMutableList()
        val index = remainingIndex.coerceIn(0, remaining.size)
        remaining.add(index, id)
        return remaining
    }

    /**
     * 2 列网格命中 → 目标下标（可直接传给 [moveToIndex]）。
     *
     * @param pointerX 指针在网格内容坐标系中的 x（内容已含滚动偏移）
     * @param pointerY 指针在网格内容坐标系中的 y
     * @param itemWidth 单卡宽度（px）
     * @param itemHeight 单卡高度（px）
     * @param spacing 水平/垂直间距（px）
     * @param itemCount 当前卡片数
     * @param draggedIndex 被拖卡当前下标（0-based）
     */
    fun gridTarget(
        pointerX: Float,
        pointerY: Float,
        itemWidth: Float,
        itemHeight: Float,
        spacing: Float,
        itemCount: Int,
        draggedIndex: Int,
        columns: Int = 2,
    ): Int {
        if (itemCount <= 0) return 0
        val cols = columns.coerceIn(1, 8)
        val (column, row) = gridCellOf(pointerX, pointerY, itemWidth, itemHeight, spacing, cols)
        // 指针落在第 slot 格 → 被拖卡片移动到该下标（移除自身后插入同一下标即可）；
        // 指针仍在自己格子上时不移动（避免抖动）。
        val slot = (row * cols + column).coerceIn(0, itemCount - 1)
        return if (slot == draggedIndex) draggedIndex else slot
    }

    /**
     * 指针在网格内容坐标系中的格子（列 0-based, 行 0-based）。
     *
     * 列以「列间隙中线」为分界（第 i 列区间 `[i*step - spacing/2, (i+1)*step - spacing/2)`），
     * 与 2 列旧实现一致；行按 `itemHeight + spacing` 取整。
     */
    fun gridCellOf(
        pointerX: Float,
        pointerY: Float,
        itemWidth: Float,
        itemHeight: Float,
        spacing: Float,
        columns: Int,
    ): Pair<Int, Int> {
        val cols = columns.coerceIn(1, 8)
        val colStep = max(1f, itemWidth + spacing)
        val column = ((pointerX + spacing / 2f) / colStep).toInt().coerceIn(0, cols - 1)
        val rowStep = max(1f, itemHeight + spacing)
        val row = floor(pointerY / rowStep).toInt().coerceAtLeast(0)
        return column to row
    }

    /** 网格行数（用于高度/边缘滚动计算与测试）。 */
    fun gridRows(itemCount: Int, columns: Int): Int {
        if (itemCount <= 0) return 0
        val cols = columns.coerceAtLeast(1)
        return (itemCount + cols - 1) / cols
    }

    /**
     * TalkBack「上移 / 下移」：[delta] 为 -1 / +1；越界或 id 不存在时原样返回（不产生无效操作）。
     */
    fun moveBy(order: List<String>, id: String, delta: Int): List<String> {
        val index = order.indexOf(id)
        if (index < 0 || delta == 0) return order
        val target = (index + delta).coerceIn(0, order.size - 1)
        if (target == index) return order
        return moveToIndex(order, id, target)
    }

    /** 顺序是否等于自然顺序（自然顺序不写云端，避免污染契约/夹具）。 */
    fun isNaturalOrder(order: List<String>, naturalIds: List<String>): Boolean =
        normalize(order, naturalIds) == naturalIds

    /**
     * 是否需要写云端：**只有用户实际拖动导致顺序变化**时才写（无基线不盲写、默认自然顺序不写）。
     * 屏幕在松手时用 `shouldPersist(拖动前, 拖动后)` 判定。
     */
    fun shouldPersist(previousOrder: List<String>, newOrder: List<String>): Boolean =
        previousOrder != newOrder

    const val MAX_HOME_TOOLS: Int = 4
}

/**
 * `userdata v6 prefs.toolOrder` 读写（纯函数，可单测）。
 *
 * - [read]：无 prefs/toolOrder 时返回空列表（调用方用自然顺序兜底，**不立即写回**）；
 * - [apply]：只改 `prefs.toolOrder` 与 `at.prefs`（其余 section/字段原样保留）；
 *   `at.prefs = max(旧值, now)`（等价 mp `toolOrderSetAt` 时间戳语义，供 LWW 合并）。
 */
object ToolOrderPrefs {

    const val PREF_KEY: String = "toolOrder"

    fun read(sections: JsonObject?): List<String> {
        val prefs = sections?.get("prefs").asObjectOrNull() ?: return emptyList()
        val array = prefs[PREF_KEY] as? JsonArray ?: return emptyList()
        return array.mapNotNull { item -> (item as? JsonPrimitive)?.takeIf { it.isString }?.content }
    }

    /** 读取 `at.prefs` 时间戳（无则 0）。 */
    fun atOf(sections: JsonObject?): Long =
        ((sections?.get("at").asObjectOrNull())?.get("prefs") as? JsonPrimitive)?.longOrNull ?: 0L

    /** 生成新 sections：prefs.toolOrder = [order]（去重），at.prefs = max(旧, now)。 */
    fun apply(
        sections: JsonObject,
        order: List<String>,
        now: Long = System.currentTimeMillis(),
    ): JsonObject {
        val oldPrefs = sections["prefs"].asObjectOrNull()
        val oldAt = sections["at"].asObjectOrNull()
        val prefs = buildJsonObject {
            oldPrefs?.forEach { (key, value) -> if (key != PREF_KEY) put(key, value) }
            put(PREF_KEY, JsonArray(order.distinct().map { JsonPrimitive(it) }))
        }
        val at = buildJsonObject {
            oldAt?.forEach { (key, value) -> put(key, value) }
            put("prefs", JsonPrimitive(maxOf(atOf(sections), now)))
        }
        return buildJsonObject {
            sections.forEach { (key, value) ->
                if (key != "prefs" && key != "at") put(key, value)
            }
            put("prefs", prefs)
            put("at", at)
        }
    }

    /** 人类可读的拖拽落位描述（调试/无障碍提示用）。 */
    fun describeDrop(oldOrder: List<String>, newOrder: List<String>, id: String): String? {
        val oldIndex = oldOrder.indexOf(id)
        val newIndex = newOrder.indexOf(id)
        if (oldIndex < 0 || newIndex < 0 || oldIndex == newIndex) return null
        return "已移动到第 ${newIndex + 1} 位"
    }

    /** 供 UI 展示：从 [sections] 读取顺序并按 [knownIds] 归一（无基线 → 自然顺序）。 */
    fun readNormalized(sections: JsonObject?, knownIds: List<String>): List<String> =
        ToolOrderLogic.normalize(read(sections), knownIds)

    /** 解析测试/预览用的 JSON 字符串（内部工具）。 */
    fun parseSections(raw: String?): JsonObject? = parseJsonOrNull(NetJson, raw).asObjectOrNull()

    @Suppress("unused")
    private fun JsonElement.unused(): Unit = Unit

    @Suppress("unused")
    private fun roundForTest(value: Float): Int = value.roundToInt()
}
