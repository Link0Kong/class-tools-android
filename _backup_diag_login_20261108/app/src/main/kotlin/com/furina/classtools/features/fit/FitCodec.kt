package com.furina.classtools.features.fit

import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.asObjectOrNull
import com.furina.classtools.data.network.asStringOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 体测数据编解码：
 * 1. `userdata.fit` section（11 键：sex/grade + 9 项成绩字符串）；
 * 2. `fitrecs.data` 行（E14/E15/E16）：`{id, ts, n, sex, gr, fields{h..}/{durM,durS}, total, grade}`
 *    以 **JSON 字符串** 存在 `data` 列，本类负责 record ↔ data 字符串。
 */
object FitCodec {

    /** 解析 `userdata.fit` section（缺省 → 默认输入；未知字段忽略）。 */
    fun inputFromJson(element: JsonElement?, json: Json = NetJson): FitInput {
        val obj = element.asObjectOrNull() ?: return FitInput()
        return FitInput(
            sex = FitSex.fromWire(obj["sex"].asStringOrNull()),
            grade = FitGrade.fromWire(obj["grade"].asStringOrNull()),
            h = obj["h"].asStringOrNull()?.trim().orEmpty(),
            w = obj["w"].asStringOrNull()?.trim().orEmpty(),
            vc = obj["vc"].asStringOrNull()?.trim().orEmpty(),
            r50 = obj["r50"].asStringOrNull()?.trim().orEmpty(),
            sit = obj["sit"].asStringOrNull()?.trim().orEmpty(),
            jump = obj["jump"].asStringOrNull()?.trim().orEmpty(),
            pull = obj["pull"].asStringOrNull()?.trim().orEmpty(),
            durM = obj["durM"].asStringOrNull()?.trim().orEmpty(),
            durS = obj["durS"].asStringOrNull()?.trim().orEmpty(),
        )
    }

    /** 输出 `userdata.fit` section：固定键序 `sex, grade, h, w, vc, r50, sit, jump, pull, durM, durS`。 */
    fun toJson(input: FitInput): JsonObject = buildJsonObject {
        put("sex", JsonPrimitive(input.sex.wire))
        put("grade", JsonPrimitive(input.grade.wire))
        put("h", JsonPrimitive(input.h))
        put("w", JsonPrimitive(input.w))
        put("vc", JsonPrimitive(input.vc))
        put("r50", JsonPrimitive(input.r50))
        put("sit", JsonPrimitive(input.sit))
        put("jump", JsonPrimitive(input.jump))
        put("pull", JsonPrimitive(input.pull))
        put("durM", JsonPrimitive(input.durM))
        put("durS", JsonPrimitive(input.durS))
    }

    /** 解析 `fitrecs.data` 字符串 → [FitRecord]；坏 JSON/缺字段 → null（UI 忽略坏行）。 */
    fun recordFromData(data: String?, json: Json = NetJson): FitRecord? {
        val element = parseJsonOrNull(json, data) ?: return null
        val obj = element.asObjectOrNull() ?: return null
        val id = obj["id"].asStringOrNull()?.trim().orEmpty()
        if (id.isEmpty()) return null
        return runCatching { json.decodeFromString(FitRecord.serializer(), obj.toString()) }.getOrNull()
    }

    /** 记录 → `fitrecs.data` JSON 字符串（服务端 data 列按字符串存储）。 */
    fun recordToData(record: FitRecord, json: Json = NetJson): String =
        json.encodeToString(FitRecord.serializer(), record)

    /** 记录 → 上传/PATCH 用的 JSON 对象（字段名与 data JSON 完全一致）。 */
    fun recordToJsonObject(record: FitRecord): JsonObject =
        parseJsonOrNull(NetJson, recordToData(record))?.asObjectOrNull() ?: buildJsonObject { }
}
