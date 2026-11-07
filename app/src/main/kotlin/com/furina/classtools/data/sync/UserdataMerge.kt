package com.furina.classtools.data.sync

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * userdata v6 合并算法（纯函数，可单测）。
 *
 * 与 `mp-class-tools-tests/_sync_fixtures/merge_ref.js` **逐字节等价**（docs/api/USERDATA_V6.md §2/§6）：
 * - 逐 section 比较 `at`，`remote.at[k] >= local.at[k]` 时云端赢（相等云端赢 = 幂等收敛）；
 * - 值是否参与比较用「非 null」判定（hasR/hasL）；
 * - 双方都无 → `DEFAULTS[k]` + `at=0`；
 * - 输出固定顺序 `v, at, <11 sections>, homeToolsAt`，且 `homeToolsAt = at.homeTools`；
 * - `v=5` 先归一为 `{homeTools, fit, at:{homeTools:homeToolsAt, fit:0}}` 再合并（写回 v6 由仓库负责）。
 */
object UserdataMerge {

    /** 与 `merge_ref.js mergeBlob()` 等价。 */
    fun merge(remoteIn: JsonObject?, localIn: JsonObject?): JsonObject {
        val remote = if (remoteIn != null && UserdataV6.numMs(remoteIn["v"]) == 5L) {
            normalizeV5(remoteIn)
        } else {
            remoteIn
        }

        val values = LinkedHashMap<String, JsonElement>()
        val atOut = LinkedHashMap<String, JsonElement>()

        for (key in UserdataV6.SECTION_KEYS) {
            val ra = UserdataV6.atValue(remote, key)
            val la = UserdataV6.atValue(localIn, key)
            val remoteValue = remote?.get(key)?.takeUnless { it is JsonNull }
            val localValue = localIn?.get(key)?.takeUnless { it is JsonNull }
            when {
                // tie-break：ra >= la → 云端赢
                remoteValue != null && (localValue == null || ra >= la) -> {
                    values[key] = remoteValue
                    atOut[key] = JsonPrimitive(ra)
                }
                localValue != null -> {
                    values[key] = localValue
                    atOut[key] = JsonPrimitive(la)
                }
                else -> {
                    values[key] = UserdataV6.defaultSection(key)
                    atOut[key] = JsonPrimitive(0)
                }
            }
        }

        return buildJsonObject {
            put("v", JsonPrimitive(UserdataV6.VERSION))
            put("at", JsonObject(atOut))
            UserdataV6.SECTION_KEYS.forEach { put(it, values.getValue(it)) }
            put("homeToolsAt", JsonPrimitive(UserdataV6.numMs(atOut["homeTools"])))
        }
    }

    /** 与 `merge_ref.js normalizeV5()` 等价。 */
    fun normalizeV5(old: JsonObject): JsonObject = buildJsonObject {
        put("v", JsonPrimitive(UserdataV6.VERSION))
        put(
            "at",
            buildJsonObject {
                put("homeTools", JsonPrimitive(UserdataV6.numMs(old["homeToolsAt"])))
                put("fit", JsonPrimitive(0))
            },
        )
        (old["homeTools"] as? JsonArray)?.let { put("homeTools", it) }
        (old["fit"] as? JsonObject)?.let { put("fit", it) }
    }

    /**
     * 本机视图（push 的 local 输入）：与 web `UD.localView` / mp `udSections()+udOwnedDirty` 等价。
     *
     * - 拥有的 section 取本机当前值；不拥有的从「最近合并基线」原样带上（避免用默认值覆盖云端）；
     * - 与基线不同 = 脏 → `at = max(now, base.at+1, remote.at+1)` 抢时间戳（保证本次本地改动赢）；
     * - `forceKeys` 显式强制为脏（用户刚操作/无基线恢复后确保本地赢）；
     * - `power.at` 值内镜像 `at.power`；
     * - 输出固定顺序 `v, at, <11 sections>, homeToolsAt`。
     */
    fun localView(
        base: JsonObject?,
        current: JsonObject,
        remote: JsonObject?,
        forceKeys: Set<String> = emptySet(),
        now: Long,
        ownedSections: Set<String> = UserdataV6.SECTION_KEYS.toSet(),
    ): JsonObject {
        val values = LinkedHashMap<String, JsonElement>()
        val atOut = LinkedHashMap<String, JsonElement>()

        for (key in UserdataV6.SECTION_KEYS) {
            val owned = key in ownedSections
            val currentValue = current[key]?.takeUnless { it is JsonNull }
            val baseValue = base?.get(key)?.takeUnless { it is JsonNull }
            val value: JsonElement = when {
                owned && currentValue != null -> currentValue
                !owned -> baseValue ?: UserdataV6.defaultSection(key)
                else -> UserdataV6.defaultSection(key)
            }

            val ba = UserdataV6.atValue(base, key)
            val ra = UserdataV6.atValue(remote, key)
            // 无基线（base==null）时不算脏：先 pull 建基线，避免默认值覆盖云端（契约 §4.4-5）。
            var dirty = owned && base != null && (baseValue == null || baseValue != value)
            if (owned && key in forceKeys) dirty = true
            val la = if (dirty) maxOf(now, ba + 1, ra + 1) else ba

            values[key] = value
            atOut[key] = JsonPrimitive(la)
        }

        // power 值内 at 与 at.power 镜像（契约 §1.3；跨端只读展示依赖它）
        val power = values["power"] as? JsonObject
        if (power != null) {
            val mirrored = LinkedHashMap<String, JsonElement>(power)
            mirrored["at"] = JsonPrimitive(UserdataV6.numMs(atOut["power"]))
            values["power"] = JsonObject(mirrored)
        }

        return buildJsonObject {
            put("v", JsonPrimitive(UserdataV6.VERSION))
            put("at", JsonObject(atOut))
            UserdataV6.SECTION_KEYS.forEach { put(it, values.getValue(it)) }
            put("homeToolsAt", JsonPrimitive(UserdataV6.numMs(atOut["homeTools"])))
        }
    }

    /**
     * 脏 section：本机当前值与基线不同（含「基线缺该键」= 脏）。
     * 只考虑 [ownedSections]。
     */
    fun dirtySections(
        base: JsonObject?,
        current: JsonObject,
        ownedSections: Set<String> = UserdataV6.SECTION_KEYS.toSet(),
    ): Set<String> {
        if (base == null) return emptySet()
        val dirty = LinkedHashSet<String>()
        for (key in UserdataV6.SECTION_KEYS) {
            if (key !in ownedSections) continue
            val baseValue = base[key]?.takeUnless { it is JsonNull }
            val currentValue = current[key]?.takeUnless { it is JsonNull } ?: UserdataV6.defaultSection(key)
            if (baseValue == null || baseValue != currentValue) dirty.add(key)
        }
        return dirty
    }

    /** 无基线时判断「哪些 section 与默认值不同」= 本机真实改动（跳过 prefs，避免默认偏好覆盖云端）。 */
    fun changedKeysVsDefaults(
        current: JsonObject,
        ownedSections: Set<String> = UserdataV6.SECTION_KEYS.toSet(),
    ): Set<String> {
        val changed = LinkedHashSet<String>()
        for (key in UserdataV6.SECTION_KEYS) {
            if (key !in ownedSections) continue
            if (key == "prefs") continue
            val value = current[key]?.takeUnless { it is JsonNull } ?: UserdataV6.defaultSection(key)
            if (value != UserdataV6.defaultSection(key)) changed.add(key)
        }
        return changed
    }
}
