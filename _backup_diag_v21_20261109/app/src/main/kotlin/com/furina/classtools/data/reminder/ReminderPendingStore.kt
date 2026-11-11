package com.furina.classtools.data.reminder

import android.content.Context
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.asStringOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * 待触发提醒事件持久化（用于 Doze/省电错过补发）。
 *
 * 工作方式：
 * 1. 每次运行结束后，把「本次规划中触发时间仍在未来」的事件存入本机（SharedPreferences）；
 * 2. 下次运行先读取上次留下的 pending：其中触发时间已过（且在上次运行后 ~ 6h 回看窗口内）的事件
 *    作为「错过补发」发送一次；去重（[ReminderDedupe]）保证不会重复；
 * 3. 已发送/已过期的 pending 在下次保存时自然淘汰（只保留未来事件）。
 *
 * 这样即使 Worker 被 Doze 延迟到触发时间之后才运行（规划器会把过点事件钳制为立即提醒，
 * 或已从本次规划中消失），也能补发一次，满足「错过 grace 窗口」补偿要求。
 */
interface ReminderPendingEventStore {
    suspend fun load(): List<ReminderEvent>

    suspend fun save(events: List<ReminderEvent>)
}

class InMemoryReminderPendingEventStore(
    initial: List<ReminderEvent> = emptyList(),
) : ReminderPendingEventStore {
    private var events: List<ReminderEvent> = initial

    override suspend fun load(): List<ReminderEvent> = events

    override suspend fun save(events: List<ReminderEvent>) {
        this.events = events
    }
}

class SharedPrefsReminderPendingEventStore(context: Context) : ReminderPendingEventStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun load(): List<ReminderEvent> =
        ReminderPendingCodec.decode(prefs.getString(KEY_EVENTS, null))

    override suspend fun save(events: List<ReminderEvent>) {
        prefs.edit()
            .putString(KEY_EVENTS, ReminderPendingCodec.encode(events.take(MAX_EVENTS)))
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "ct_reminder_pending_events"
        const val KEY_EVENTS = "events"
        const val MAX_EVENTS = 200
    }
}

/** 待触发事件编解码（纯函数，可单测）。 */
object ReminderPendingCodec {

    fun encode(events: List<ReminderEvent>): String = buildJsonArray {
        events.forEach { event ->
            add(
                buildJsonObject {
                    put("kind", JsonPrimitive(event.kind.name))
                    put("key", JsonPrimitive(event.key))
                    put("at", JsonPrimitive(event.triggerAtMillis))
                    put("title", JsonPrimitive(event.title))
                    put("body", JsonPrimitive(event.body))
                    put("tool", JsonPrimitive(event.toolId))
                },
            )
        }
    }.toString()

    fun decode(raw: String?): List<ReminderEvent> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = parseJsonOrNull(NetJson, raw) as? JsonArray ?: return emptyList()
        val out = ArrayList<ReminderEvent>(array.size)
        for (element in array) {
            val obj = element as? JsonObject ?: continue
            val kind = runCatching {
                ReminderKind.valueOf(obj["kind"].asStringOrNull().orEmpty())
            }.getOrNull() ?: continue
            val key = obj["key"].asStringOrNull().orEmpty().trim()
            val toolId = obj["tool"].asStringOrNull().orEmpty()
            if (key.isEmpty() || toolId.isEmpty()) continue
            val at = (obj["at"] as? JsonPrimitive)?.longOrNull ?: 0L
            if (at <= 0L) continue
            out += ReminderEvent(
                kind = kind,
                key = key,
                triggerAtMillis = at,
                title = obj["title"].asStringOrNull().orEmpty(),
                body = obj["body"].asStringOrNull().orEmpty(),
                toolId = toolId,
            )
        }
        return out
    }
}

/** 错过补发纯逻辑（可单测）。 */
object ReminderPendingCatchUp {

    /** 保留的最大未来事件数（避免本机偏好无限增长）。 */
    const val MAX_PENDING_EVENTS: Int = 200

    /** 下次运行应保存的事件：触发时间仍在未来（按时间排序、去重、限流）。 */
    fun pendingForNextRun(
        events: List<ReminderEvent>,
        nowMillis: Long,
        maxEvents: Int = MAX_PENDING_EVENTS,
    ): List<ReminderEvent> = events
        .filter { it.triggerAtMillis > nowMillis }
        .sortedBy { it.triggerAtMillis }
        .distinctBy { it.key }
        .take(maxEvents)

    /**
     * 上次 pending 中「已到点但可能错过」的事件：
     * 触发时间落在 `[now-lookback, now]`（含边界）且未在本次规划中重复（按 key 去重）。
     */
    fun dueCatchUp(
        pending: List<ReminderEvent>,
        nowMillis: Long,
        lookbackMillis: Long,
        keysAlreadyPlanned: Set<String> = emptySet(),
    ): List<ReminderEvent> = pending
        .filter { it.triggerAtMillis in (nowMillis - lookbackMillis)..nowMillis }
        .filterNot { it.key in keysAlreadyPlanned }
        .sortedBy { it.triggerAtMillis }
        .distinctBy { it.key }
}
