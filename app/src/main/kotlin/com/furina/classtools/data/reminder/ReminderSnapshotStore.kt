package com.furina.classtools.data.reminder

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * 「新增类」通知基线快照存储（对齐鸿蒙 `NotifyStore` 的 snapshot）。
 *
 * 用途：首次运行只建立基线不推送；之后对比出**新增**作业/通知/投票才提醒，
 * 避免用户刚装 App / 刚换机就被历史消息刷屏。
 *
 * 只存 id 与一个水费指纹，不含正文、姓名、学号；异常数据一律回退空快照（不崩）。
 */
interface ReminderSnapshotStore {
    /** 读取上次快照（没有则返回空快照，`initialized=false`）。 */
    suspend fun load(): ReminderSnapshot

    /** 覆盖保存快照。 */
    suspend fun save(snapshot: ReminderSnapshot)
}

/** 快照 JSON 编解码（纯逻辑，可单测）。 */
object ReminderSnapshotCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(snapshot: ReminderSnapshot): String = runCatching {
        json.encodeToString(ReminderSnapshot.serializer(), snapshot)
    }.getOrDefault("")

    fun decode(raw: String?): ReminderSnapshot {
        if (raw.isNullOrBlank()) return ReminderSnapshot()
        return runCatching {
            json.decodeFromString(ReminderSnapshot.serializer(), raw)
        }.getOrDefault(ReminderSnapshot())
    }
}

/** 内存实现（单测用；不触碰 Android）。 */
class InMemoryReminderSnapshotStore(
    initial: ReminderSnapshot = ReminderSnapshot(),
) : ReminderSnapshotStore {

    private var snapshot: ReminderSnapshot = initial

    override suspend fun load(): ReminderSnapshot = snapshot

    override suspend fun save(snapshot: ReminderSnapshot) {
        this.snapshot = snapshot
    }
}

/** SharedPreferences 实现（设备级，进程重启保留；随 App 卸载清除）。 */
class SharedPrefsReminderSnapshotStore(context: Context, owner: String = "") : ReminderSnapshotStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(if (owner.isEmpty()) PREFS_NAME else "${PREFS_NAME}_$owner", Context.MODE_PRIVATE)

    override suspend fun load(): ReminderSnapshot =
        ReminderSnapshotCodec.decode(prefs.getString(KEY_SNAPSHOT, null))

    override suspend fun save(snapshot: ReminderSnapshot) {
        runCatching {
            prefs.edit().putString(KEY_SNAPSHOT, ReminderSnapshotCodec.encode(snapshot)).apply()
        }
    }

    private companion object {
        const val PREFS_NAME = "ct_reminder_snapshot"
        const val KEY_SNAPSHOT = "snapshot"
    }
}
