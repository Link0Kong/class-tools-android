package com.furina.classtools.features.group

import com.furina.classtools.data.network.NetJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * 分组工作流 [GroupWorkflowState] 的 JSON 编解码（供 `rememberSaveable(stateSaver=...)` 使用）。
 *
 * 与 `GroupConfigCodec`（userdata `group` section、跨端契约）区分：本编解码仅用于本机 SavedState，
 * 保存 `step/config/result`（含每组座位+姓名、结果快照），支持导航返回/旋转/进程重建恢复。
 *
 * 键名：
 * ```jsonc
 * { "v":1, "step":"config|result",
 *   "config":{...GroupConfigCodec 键...},
 *   "result":{"groups":[{"i":1,"members":[{"seat":1,"nm":"张三"}]}],"config":{...}} }
 * ```
 */
object GroupWorkflowCodec {

    const val VERSION: Int = 1
    private const val MAX_GROUPS: Int = 200
    private const val MAX_MEMBERS: Int = 2000

    fun encode(state: GroupWorkflowState): String =
        NetJson.encodeToString(JsonObject.serializer(), toJson(state))

    fun decode(text: String?): GroupWorkflowState? {
        if (text.isNullOrBlank()) return null
        val element = runCatching { NetJson.parseToJsonElement(text) }.getOrNull() ?: return null
        val obj = element as? JsonObject ?: return null
        return fromJson(obj)
    }

    fun toJson(state: GroupWorkflowState): JsonObject = buildJsonObject {
        put("v", JsonPrimitive(VERSION))
        put("step", JsonPrimitive(if (state.step == GroupStep.RESULT) "result" else "config"))
        put("config", GroupConfigCodec.toJson(state.config))
        state.result?.let { put("result", resultToJson(it)) }
    }

    /** 容错解码：升级/坏字段回落默认；`result` 步骤缺结果时降级为 `config`。 */
    fun fromJson(obj: JsonObject): GroupWorkflowState {
        val config = GroupConfigCodec.fromJson(obj["config"])
        val result = resultFromJson(obj["result"] as? JsonObject)
        var step = if ((obj["step"] as? JsonPrimitive)?.contentOrNull?.trim() == "result") {
            GroupStep.RESULT
        } else {
            GroupStep.CONFIG
        }
        if (step == GroupStep.RESULT && result == null) step = GroupStep.CONFIG
        return GroupWorkflowState(step = step, config = config, result = result)
    }

    fun resultToJson(result: GroupResult): JsonObject = buildJsonObject {
        put(
            "groups",
            buildJsonArray {
                result.groups.take(MAX_GROUPS).forEach { group ->
                    add(
                        buildJsonObject {
                            put("i", JsonPrimitive(group.index))
                            put("members", membersToJson(group.members))
                        },
                    )
                }
            },
        )
        put("config", GroupConfigCodec.toJson(result.config))
    }

    fun resultFromJson(obj: JsonObject?): GroupResult? {
        obj ?: return null
        val groupsElement = obj["groups"] as? kotlinx.serialization.json.JsonArray ?: return null
        val groups = groupsElement.take(MAX_GROUPS).mapNotNull { item ->
            val groupObj = item as? JsonObject ?: return@mapNotNull null
            val index = (groupObj["i"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
            val members = membersFromJson(groupObj["members"])
            if (members.isEmpty()) return@mapNotNull null
            MemberGroup(index = index, members = members)
        }
        if (groups.isEmpty()) return null
        val config = GroupConfigCodec.fromJson(obj["config"])
        // 快照由 config + groups 重新推导（不落盘冗余文案；GroupEngine.snapshotOf 是唯一口径来源）。
        val snapshot = GroupEngine.snapshotOf(config, groups)
        return GroupResult(groups = groups, config = config, snapshot = snapshot)
    }

    // ------------------------------------------------------------ members

    fun membersToJson(members: List<GroupMember>): JsonElement = buildJsonArray {
        members.take(MAX_MEMBERS).forEach { member ->
            add(
                buildJsonObject {
                    put("seat", JsonPrimitive(member.seat))
                    if (member.name.isNotBlank()) put("nm", JsonPrimitive(member.name))
                },
            )
        }
    }

    fun membersFromJson(element: JsonElement?): List<GroupMember> {
        val array = element as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return array.take(MAX_MEMBERS).mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val seat = (obj["seat"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
            val name = (obj["nm"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            GroupMember(seat = seat, name = name)
        }
    }
}
