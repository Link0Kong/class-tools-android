package com.furina.classtools.data.timetable

import com.furina.classtools.data.sync.UserDataRepository
import com.furina.classtools.data.sync.UserdataV6
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 个人课表本机存储（userdata v6 `tt.ttLocal`，走既有 UD 同步：跨端同字段、逐 section 时间戳合并）。
 *
 * 写入只改 `tt` section 内的 `ttLocal` 字段并 `updateCurrent(全量 sections)`：
 * - 必须带全量 sections，否则 `UserdataMerge` 会把缺失 section 当默认值判脏 → 可能把云端其它偏好覆盖成默认值；
 * - 首帧 sections 尚未 hydrate 时回退「最近合并基线 blob」的 sections；
 * - 写入失败静默（UI 仍可按本次结果渲染，下一轮同步补发）。
 */
interface TimetablePersonalStore {
    /** 读个人课表（无/坏 → null，回退公共课表）。 */
    suspend fun read(): TtSnapshot?

    /** 保存个人课表（写入 `tt.ttLocal` 并触发 UD 防抖推送）。 */
    suspend fun save(snapshot: TtSnapshot)

    /** 清除个人课表（`ttLocal=null`，回退公共课表）。 */
    suspend fun clear()
}

class UserDataTimetablePersonalStore(
    private val userDataRepository: UserDataRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : TimetablePersonalStore {

    override suspend fun read(): TtSnapshot? {
        val sections = currentSections()
        return TtLocal.snapshotOf(sections[TtLocal.SECTION_KEY])
    }

    override suspend fun save(snapshot: TtSnapshot) {
        val sections = currentSections()
        val currentTt = sections[TtLocal.SECTION_KEY]
        val updatedTt = TtLocal.applyTo(ttSection(currentTt), snapshot, now())
        userDataRepository.updateCurrent(withTtSection(sections, updatedTt))
    }

    override suspend fun clear() {
        val sections = currentSections()
        val currentTt = sections[TtLocal.SECTION_KEY]
        val updatedTt = TtLocal.cleared(ttSection(currentTt))
        userDataRepository.updateCurrent(withTtSection(sections, updatedTt))
    }

    /** 当前 11 个 section；内存镜像为空（尚未 hydrate/pull）时回退最近合并基线。 */
    private suspend fun currentSections(): JsonObject {
        val current = userDataRepository.currentSections.value
        if (current.isNotEmpty()) return current
        val cached = runCatching { userDataRepository.cachedBlob() }.getOrNull()
        return if (cached != null) UserdataV6.sectionsOf(cached) else JsonObject(emptyMap())
    }

    private fun ttSection(element: JsonElement?): JsonElement? =
        element ?: UserdataV6.defaultSection(TtLocal.SECTION_KEY)

    private fun withTtSection(sections: JsonObject, tt: JsonObject): JsonObject {
        val out = LinkedHashMap<String, JsonElement>()
        sections.forEach { (key, value) -> if (key != TtLocal.SECTION_KEY) out[key] = value }
        out[TtLocal.SECTION_KEY] = tt
        // 恢复固定 section 顺序（UserdataV6.SECTION_KEYS），避免合并/落盘键序抖动。
        val ordered = LinkedHashMap<String, JsonElement>()
        UserdataV6.SECTION_KEYS.forEach { key -> out[key]?.let { ordered[key] = it } }
        return JsonObject(ordered)
    }
}
