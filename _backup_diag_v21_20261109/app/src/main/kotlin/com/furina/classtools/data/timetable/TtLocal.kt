package com.furina.classtools.data.timetable

import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.parseJsonOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * userdata v6 `tt.ttLocal` 编解码（跨端同字段：web `D.d.ttLocal={c,t,at}` / mp `store.ttLocal`）。
 *
 * - 写入：`TimetablePersonalStore.save()` 把「登录教务」返回的 `{c,t}` 写进 `tt` section，
 *   保留 `ttView/ttUseMine/termStart` 等其它字段（不覆盖用户偏好）；
 * - 读取：`TtLocal.parse()` 兼容对象或 JSON 字符串；`c` 为空/非法 → null（回退公共课表）；
 * - 清除：`ttLocal=null`（网页同款语义：有 ttLocal 时个人课表优先，清除后回退公共）。
 */
object TtLocal {

    const val SECTION_KEY: String = "tt"
    const val FIELD_KEY: String = "ttLocal"

    /** 解析 ttLocal（或登录 RPC 返回的 result）为课表快照；坏数据 → null。 */
    fun parse(element: JsonElement?, json: Json = NetJson): TtSnapshot? {
        val body: JsonObject = when (element) {
            is JsonObject -> element
            is JsonPrimitive -> {
                val text = element.contentOrNull
                if (text.isNullOrBlank()) return null
                parseJsonOrNull(json, text) as? JsonObject ?: return null
            }
            else -> return null
        }
        return DefaultTimetableRepository.parseSnapshotBody(json, body)
    }

    /** 快照 → ttLocal JSON（`{c:[...],t,at}`，字段与网页/小程序逐字段一致）。 */
    fun encode(snapshot: TtSnapshot, nowMillis: Long): JsonObject = buildJsonObject {
        put(
            "c",
            buildJsonArray {
                snapshot.c.forEach { course ->
                    add(
                        buildJsonObject {
                            put("name", course.name)
                            put("teacher", course.teacher)
                            put("room", course.room)
                            put("day", course.day)
                            put("start", course.start)
                            put("end", course.end)
                            put("weeks", course.weeks)
                            put("parity", course.parity)
                        },
                    )
                }
            },
        )
        put("t", snapshot.t)
        put("at", nowMillis)
    }

    /** 把快照写入 `tt` section（保留其它字段）；`ttSection` 可为空/坏类型。 */
    fun applyTo(ttSection: JsonElement?, snapshot: TtSnapshot, nowMillis: Long): JsonObject {
        val base = ttSection as? JsonObject ?: JsonObject(emptyMap())
        val out = LinkedHashMap(base)
        out[FIELD_KEY] = encode(snapshot, nowMillis)
        return JsonObject(out)
    }

    /** 清除个人课表（`ttLocal=null`，其它字段保留）。 */
    fun cleared(ttSection: JsonElement?): JsonObject {
        val base = ttSection as? JsonObject ?: JsonObject(emptyMap())
        val out = LinkedHashMap(base)
        out[FIELD_KEY] = JsonNull
        return JsonObject(out)
    }

    /** `tt` section 是否已有可用的个人课表（UI 显示「个人课表」徽标/清除入口用）。 */
    fun snapshotOf(ttSection: JsonElement?): TtSnapshot? = parse(ttSection?.let { section ->
        (section as? JsonObject)?.get(FIELD_KEY)
    })
}
