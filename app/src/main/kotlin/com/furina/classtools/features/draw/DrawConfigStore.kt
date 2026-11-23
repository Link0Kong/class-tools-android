package com.furina.classtools.features.draw

import com.furina.classtools.data.sync.UserDataRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject

/**
 * 抽签参数持久化接口（对齐 USERDATA_V6 的 `draw` section）。
 *
 * UI 只依赖该接口；生产实现是 [UserDataDrawConfigStore]（包一层 [UserDataRepository]）。
 * 失败语义：**静默**（参数是用户可重设的偏好，不能因为同步失败阻塞抽签本身）。
 */
interface DrawConfigStore {
    suspend fun load(): DrawConfig

    suspend fun save(config: DrawConfig)
}

/**
 * 通过账号 userdata v6 同步抽签参数。
 *
 * 说明：
 * - `load` 读 `currentSections` 的 `draw`（启动恢复时可能还没拉取云端，此时返回默认值，
 *   后续 `UserDataRepository.pull` 应用远端后由页面重新读取即可——本页面用一次性恢复，
 *   不监听远端变更，避免抽签过程中被远端覆盖）；
 * - `save` 只替换 `draw` 一个 section，其余 section 原样保留后走 `updateCurrent`
 *   （内部会落盘 + 1200ms 防抖推送）。
 */
class UserDataDrawConfigStore(
    private val userDataRepository: UserDataRepository,
) : DrawConfigStore {

    override suspend fun load(): DrawConfig {
        val sections = userDataRepository.currentSections.first()
        return DrawConfigCodec.fromJson(sections["draw"])
    }

    override suspend fun save(config: DrawConfig) {
        val current = userDataRepository.currentSections.first()
        val next = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>(current)
        val existing = current["draw"] as? JsonObject
        val updated = DrawConfigCodec.toJson(config)
        next["draw"] = if (existing == null) updated else JsonObject(existing + updated)
        userDataRepository.updateCurrent(JsonObject(next))
    }
}

/** 仅用于预览/测试的内存实现。 */
class InMemoryDrawConfigStore(initial: DrawConfig = DrawConfig()) : DrawConfigStore {
    var stored: DrawConfig = initial
        private set

    override suspend fun load(): DrawConfig = stored

    override suspend fun save(config: DrawConfig) {
        stored = config
    }
}
