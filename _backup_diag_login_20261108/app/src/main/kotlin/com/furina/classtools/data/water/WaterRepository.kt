package com.furina.classtools.data.water

import com.furina.classtools.data.sync.UserDataRepository
import com.furina.classtools.features.water.WaterCodec
import com.furina.classtools.features.water.WaterState
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 水量仓库：包装 `UserDataRepository` 的 `water` section（整段 LWW）。
 *
 * 契约：`docs/api/USERDATA_V6.md` §1.2 + `_water_contract.md` §2/§7。
 * - 读：`currentSections.first()["water"]` → [WaterCodec.fromJson]；
 * - 写：替换 `water` section 后 `updateCurrent`（保留其它 section 原值，含 `hide`）；
 * - 失败静默（同步层自带待补发/离线缓存，页面不因写失败中断录入）；
 * - 按 sid 隔离由 [UserDataRepository] 保证（`currentSections` 随身份切换）。
 */
interface WaterRepository {
    suspend fun load(): WaterState

    /** 整段写回；`state.rooms` 为空时写 `{"rooms":{}}`（清除）。 */
    suspend fun save(state: WaterState)

    /** 删除单间宿舍水量数据。 */
    suspend fun clearRoom(roomVerify: String)
}

class UserDataWaterRepository(
    private val userDataRepository: UserDataRepository,
) : WaterRepository {

    override suspend fun load(): WaterState = runCatching {
        WaterCodec.fromJson(currentWaterSection())
    }.getOrDefault(WaterState())

    override suspend fun save(state: WaterState) {
        runCatching {
            val current = userDataRepository.currentSections.first()
            val next = LinkedHashMap<String, JsonElement>(current)
            next["water"] = WaterCodec.toJson(state)
            userDataRepository.updateCurrent(JsonObject(next))
        }
    }

    override suspend fun clearRoom(roomVerify: String) {
        val state = load()
        val key = roomVerify.trim()
        if (!state.rooms.containsKey(key)) return
        val rooms = LinkedHashMap(state.rooms)
        rooms.remove(key)
        save(WaterState(rooms))
    }

    private suspend fun currentWaterSection(): JsonElement? =
        userDataRepository.currentSections.first()["water"]
}
