package com.furina.classtools.data.reminder

import android.content.Context
import java.time.Instant
import java.time.ZoneId

/**
 * 提醒去重/冷却（纯函数 + 本机存储）。
 *
 * 规则（任务口径：同 id 当日不重复）：
 * - 同一 key 在**同一本地日期**内只发一次；
 * - 额外冷却窗口（默认 20h）兜底跨日重复；
 * - 存储只保留近 7 天 key，避免无限增长。
 */
object ReminderDedupe {

    const val DEFAULT_COOLDOWN_MS: Long = 20L * 60 * 60 * 1000
    const val RETENTION_MS: Long = 7L * 24 * 60 * 60 * 1000

    /** 是否允许发送。 */
    fun shouldNotify(
        key: String,
        lastSentAtMillis: Long?,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        cooldownMillis: Long = DEFAULT_COOLDOWN_MS,
    ): Boolean {
        if (key.isBlank()) return false
        val last = lastSentAtMillis ?: return true
        if (last <= 0L) return true
        if (isSameLocalDate(last, nowMillis, zone)) return false
        if (nowMillis - last in 0..cooldownMillis) return false
        if (last > nowMillis) return false
        return true
    }

    fun isSameLocalDate(aMillis: Long, bMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val a = Instant.ofEpochMilli(aMillis).atZone(zone).toLocalDate()
        val b = Instant.ofEpochMilli(bMillis).atZone(zone).toLocalDate()
        return a == b
    }

    /** 清理过期 key（保留 [retentionMillis] 内）。 */
    fun prune(
        sent: Map<String, Long>,
        nowMillis: Long,
        retentionMillis: Long = RETENTION_MS,
    ): Map<String, Long> = sent.filterValues { it >= nowMillis - retentionMillis }.toMap()
}

/** 已发送记录存储（接口化：Worker 用 SharedPreferences，测试用内存）。 */
interface ReminderDedupeStore {
    suspend fun lastSent(key: String): Long?

    suspend fun markSent(key: String, atMillis: Long)

    suspend fun prune(olderThanMillis: Long)

    suspend fun snapshot(): Map<String, Long>
}

/** 内存实现（测试/预览；进程重启丢失，仅作兜底）。 */
class InMemoryReminderDedupeStore(
    initial: Map<String, Long> = emptyMap(),
) : ReminderDedupeStore {
    private val sent = LinkedHashMap<String, Long>(initial)

    override suspend fun lastSent(key: String): Long? = sent[key]

    override suspend fun markSent(key: String, atMillis: Long) {
        sent[key] = atMillis
    }

    override suspend fun prune(olderThanMillis: Long) {
        sent.entries.removeAll { it.value < olderThanMillis }
    }

    override suspend fun snapshot(): Map<String, Long> = LinkedHashMap(sent)
}

/** SharedPreferences 实现（进程重启保留，避免重复通知）。 */
class SharedPrefsReminderDedupeStore(context: Context, owner: String = "") : ReminderDedupeStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(if (owner.isEmpty()) PREFS_NAME else "${PREFS_NAME}_$owner", Context.MODE_PRIVATE)

    override suspend fun lastSent(key: String): Long? =
        prefs.getLong(keyPrefix(key), 0L).takeIf { it > 0L }

    override suspend fun markSent(key: String, atMillis: Long) {
        prefs.edit().putLong(keyPrefix(key), atMillis).apply()
    }

    override suspend fun prune(olderThanMillis: Long) {
        val editor = prefs.edit()
        prefs.all.forEach { (storedKey, value) ->
            if (storedKey.startsWith(KEY_PREFIX) && value is Long && value < olderThanMillis) {
                editor.remove(storedKey)
            }
        }
        editor.apply()
    }

    override suspend fun snapshot(): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(KEY_PREFIX) && value is Long && value > 0L) {
                out[key.removePrefix(KEY_PREFIX)] = value
            }
        }
        return out
    }

    private fun keyPrefix(key: String): String = KEY_PREFIX + key

    private companion object {
        const val PREFS_NAME = "ct_reminder_sent"
        const val KEY_PREFIX = "sent_"
    }
}
