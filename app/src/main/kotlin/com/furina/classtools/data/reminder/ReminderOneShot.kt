package com.furina.classtools.data.reminder

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.furina.classtools.data.homework.HomeworkDueCalculator
import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.asStringOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import com.furina.classtools.data.notices.NoticeGroupDto
import com.furina.classtools.data.notices.NoticeStatusMachine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.furina.classtools.data.permission.PermissionController
import com.furina.classtools.data.permission.PermissionState
import com.furina.classtools.data.permission.PermissionType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** 用户手动设置的本机提醒，由系统闹钟触发，WorkManager 持久化补发。 */
enum class OneShotTargetKind(val toolId: String, val label: String) {
    HOMEWORK("hw", "作业"),
    NOTICE("nt", "通知"),
}

/** 可选提前量（作业/通知的截止类；活动类见 [OneShotReminderOption.BEFORE_EVENT_1_HOUR]）。 */
enum class OneShotReminderOption(val label: String, val leadMillis: Long) {
    BEFORE_1_DAY("截止前 1 天", 24L * 60 * 60 * 1000),
    BEFORE_1_HOUR("截止前 1 小时", 60L * 60 * 1000),
    AT_DEADLINE("截止时刻", 0L),
    BEFORE_EVENT_1_HOUR("活动前 1 小时", 60L * 60 * 1000),
    BEFORE_SIGNUP_5_MIN("报名开始前 5 分钟", 300_000L),
    BEFORE_SIGNUP_1_MIN("报名开始前 1 分钟", 60_000L),
    AT_SIGNUP("报名开始时", 0L),
    CUSTOM("自定义提醒", 0L),
}

/** UI 可选的一项提醒时间（含实际触发时刻）。 */
data class OneShotPlannedTime(
    val option: OneShotReminderOption,
    val label: String,
    val fireAtMillis: Long,
    val targetAtMillis: Long,
)

/** 单条提醒的稳定 key（取消/列表用）。 */
data class OneShotKey(
    val targetKind: OneShotTargetKind,
    val targetId: String,
    val fireAtMillis: Long,
) {
    /** WorkManager 唯一任务名（净化非法字符，避免 OEM/多进程异常）。 */
    val workName: String get() = ReminderOneShot.workName(targetKind, targetId, fireAtMillis)
}

/** 单条提醒调度结果（明确错误，不静默）。 */
sealed interface OneShotScheduleResult {
    data class Scheduled(
        val key: OneShotKey,
        val fireAtMillis: Long,
    ) : OneShotScheduleResult

    /** 通知权限缺失：需要 UI 引导用户开启（不排程）。 */
    data object PermissionMissing : OneShotScheduleResult

    /** 触发时间已过（或距现在太近）——提示用户选择更晚时间。 */
    data class TooLate(val targetAtMillis: Long) : OneShotScheduleResult

    /** 该类已达到上限（默认 20 条）。 */
    data class LimitReached(val limit: Int) : OneShotScheduleResult

    /** 目标没有可提醒的时间。 */
    data object EmptyTarget : OneShotScheduleResult

    data class Failed(val message: String) : OneShotScheduleResult
}

/** 已排程的单条提醒（UI 列表/取消用）。 */
data class OneShotPending(
    val key: OneShotKey,
    val label: String,
    val fireAtMillis: Long,
    val targetAtMillis: Long = fireAtMillis,
)

/** 单条提醒纯逻辑（可单测）：时间计算、key、上限、校验、通知事件。 */
object ReminderOneShot {

    const val MAX_PER_CATEGORY: Int = 20

    private val HH_MM = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)

    /** 作业可选时间：截止前 1 天 / 1 小时 / 截止时刻（过滤已过去的选项）。 */
    fun homeworkPlannedTimes(
        row: HomeworkRow,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<OneShotPlannedTime> {
        val dueAt = HomeworkDueCalculator.parseDue(row.date, row.time, zone) ?: return emptyList()
        return listOf(
            OneShotReminderOption.BEFORE_1_DAY,
            OneShotReminderOption.BEFORE_1_HOUR,
            OneShotReminderOption.AT_DEADLINE,
        ).map { option ->
            OneShotPlannedTime(
                option = option,
                label = labelOf(option, dueAt, zone),
                fireAtMillis = triggerAt(dueAt, option),
                targetAtMillis = dueAt,
            )
        }.filter { it.fireAtMillis > nowMillis }
    }

    /** 通知可选时间：活动前 1 小时；截止前 1 天/1 小时/截止时刻（过滤已过去的选项）。 */
    fun noticePlannedTimes(
        group: NoticeGroupDto,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<OneShotPlannedTime> {
        val out = mutableListOf<OneShotPlannedTime>()
        NoticeStatusMachine.nextSignup(group, nowMillis, zone)?.start?.let { start ->
            for (option in listOf(OneShotReminderOption.BEFORE_SIGNUP_5_MIN,
                    OneShotReminderOption.BEFORE_SIGNUP_1_MIN, OneShotReminderOption.AT_SIGNUP)) {
                out += OneShotPlannedTime(option, labelOf(option, start, zone), triggerAt(start, option), start)
            }
        }
        val eventAt = NoticeStatusMachine.eventAtOf(group, zone)?.takeIf { it > nowMillis }
        if (eventAt != null) {
            out += OneShotPlannedTime(
                option = OneShotReminderOption.BEFORE_EVENT_1_HOUR,
                label = labelOf(OneShotReminderOption.BEFORE_EVENT_1_HOUR, eventAt, zone),
                fireAtMillis = triggerAt(eventAt, OneShotReminderOption.BEFORE_EVENT_1_HOUR),
                targetAtMillis = eventAt,
            )
        }
        val deadline = NoticeStatusMachine.deadlineOf(group, zone)?.takeIf { it > nowMillis }
        if (deadline != null) {
            listOf(
                OneShotReminderOption.BEFORE_1_DAY,
                OneShotReminderOption.BEFORE_1_HOUR,
                OneShotReminderOption.AT_DEADLINE,
            ).forEach { option ->
                out += OneShotPlannedTime(
                    option = option,
                    label = labelOf(option, deadline, zone),
                    fireAtMillis = triggerAt(deadline, option),
                    targetAtMillis = deadline,
                )
            }
        }
        return out.filter { it.fireAtMillis > nowMillis }
    }

    fun triggerAt(targetAtMillis: Long, option: OneShotReminderOption): Long =
        (targetAtMillis - option.leadMillis).coerceAtLeast(0L)

    fun labelOf(
        option: OneShotReminderOption,
        targetAtMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = "${option.label}（${HH_MM.format(Instant.ofEpochMilli(targetAtMillis).atZone(zone))}）"

    /** 稳定任务名（WorkManager unique work）：`ct_oneshot_<kind>_<id>_<fireAt>`。 */
    fun workName(kind: OneShotTargetKind, targetId: String, fireAtMillis: Long): String {
        val cleanId = targetId.trim().map { ch ->
            if (ch.isLetterOrDigit() || ch == '-' || ch == '_') ch else '_'
        }.joinToString("").take(48)
        return "ct_oneshot_${kind.name.lowercase()}_${cleanId}_$fireAtMillis"
    }

    /** 取消/排程用的 key。 */
    fun key(kind: OneShotTargetKind, targetId: String, fireAtMillis: Long): OneShotKey =
        OneShotKey(kind, targetId.trim(), fireAtMillis)

    /** 排程校验（权限/时间/上限）；null 表示可排程。 */
    fun validate(
        targetKind: OneShotTargetKind,
        targetId: String,
        fireAtMillis: Long,
        nowMillis: Long,
        permission: PermissionState,
        pendingOfCategory: Int,
        targetAtMillis: Long = fireAtMillis,
    ): OneShotScheduleResult? {
        if (targetId.isBlank()) return OneShotScheduleResult.EmptyTarget
        if (permission != PermissionState.GRANTED) return OneShotScheduleResult.PermissionMissing
        if (fireAtMillis <= nowMillis) {
            return OneShotScheduleResult.TooLate(targetAtMillis)
        }
        if (pendingOfCategory >= MAX_PER_CATEGORY) {
            return OneShotScheduleResult.LimitReached(MAX_PER_CATEGORY)
        }
        return null
    }

    /** 构造单条提醒的通知事件（Worker 与测试共用）。 */
    fun eventFor(
        kind: OneShotTargetKind,
        targetId: String,
        label: String,
        targetAtMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ReminderEvent {
        val title = if (kind == OneShotTargetKind.HOMEWORK) "作业提醒" else "通知提醒"
        return ReminderEvent(
            kind = if (kind == OneShotTargetKind.HOMEWORK) ReminderKind.HOMEWORK_DUE else ReminderKind.NOTICE_DUE,
            key = "oneshot:${kind.name.lowercase()}:$targetId",
            triggerAtMillis = targetAtMillis,
            title = title,
            body = "$label · ${HH_MM.format(Instant.ofEpochMilli(targetAtMillis).atZone(zone))}",
            toolId = kind.toolId,
        )
    }
}

// ---------------------------------------------------------------- 持久化

/** 单条提醒排程元数据存储（SharedPreferences；WorkManager 自身也持久化，两者配合用于列表/取消/上限）。 */
interface OneShotPendingStore {
    suspend fun load(): List<OneShotPending>

    suspend fun add(item: OneShotPending)

    suspend fun remove(key: OneShotKey)

    suspend fun clear()
}

class InMemoryOneShotPendingStore(
    initial: List<OneShotPending> = emptyList(),
) : OneShotPendingStore {
    private val items = initial.toMutableList()

    override suspend fun load(): List<OneShotPending> = items.toList()

    override suspend fun add(item: OneShotPending) {
        items.removeAll { it.key == item.key }
        items += item
    }

    override suspend fun remove(key: OneShotKey) {
        items.removeAll { it.key == key }
    }

    override suspend fun clear() {
        items.clear()
    }
}

/** 编解码（纯函数，可单测）：JSON 数组，字段 kind/id/label/fireAt。 */
object OneShotPendingCodec {

    fun encode(items: List<OneShotPending>): String = buildJsonArray {
        items.forEach { item ->
            add(
                buildJsonObject {
                    put("kind", JsonPrimitive(item.key.targetKind.name))
                    put("id", JsonPrimitive(item.key.targetId))
                    put("label", JsonPrimitive(item.label))
                    put("fireAt", JsonPrimitive(item.fireAtMillis))
                    put("targetAt", JsonPrimitive(item.targetAtMillis))
                },
            )
        }
    }.toString()

    fun decode(raw: String?): List<OneShotPending> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = parseJsonOrNull(NetJson, raw) as? JsonArray ?: return emptyList()
        val out = ArrayList<OneShotPending>(array.size)
        for (element in array) {
            val obj = element as? kotlinx.serialization.json.JsonObject ?: continue
            val kind = runCatching {
                OneShotTargetKind.valueOf(obj["kind"].asStringOrNull().orEmpty())
            }.getOrNull() ?: continue
            val id = obj["id"].asStringOrNull().orEmpty().trim()
            if (id.isEmpty()) continue
            val fireAt = (obj["fireAt"] as? JsonPrimitive)?.longOrNull ?: 0L
            if (fireAt <= 0L) continue
            out += OneShotPending(
                key = OneShotKey(kind, id, fireAt),
                label = obj["label"].asStringOrNull().orEmpty(),
                fireAtMillis = fireAt,
                targetAtMillis = (obj["targetAt"] as? JsonPrimitive)?.longOrNull ?: fireAt,
            )
        }
        return out
    }
}

class SharedPrefsOneShotPendingStore(context: Context, owner: String = "") : OneShotPendingStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(if (owner.isEmpty()) PREFS_NAME else "${PREFS_NAME}_$owner", Context.MODE_PRIVATE)

    override suspend fun load(): List<OneShotPending> =
        OneShotPendingCodec.decode(prefs.getString(KEY_ITEMS, null))

    override suspend fun add(item: OneShotPending) {
        val next = load().filterNot { it.key == item.key } + item
        write(next)
    }

    override suspend fun remove(key: OneShotKey) {
        write(load().filterNot { it.key == key })
    }

    override suspend fun clear() {
        prefs.edit().remove(KEY_ITEMS).commit()
    }

    private fun write(items: List<OneShotPending>) {
        prefs.edit().putString(KEY_ITEMS, OneShotPendingCodec.encode(items)).commit()
    }

    private companion object {
        const val PREFS_NAME = "ct_oneshot_pending"
        const val KEY_ITEMS = "items"
    }
}

// ---------------------------------------------------------------- 调度器

/** 单条提醒调度器（接口；Android 实现见 [WorkManagerOneShotScheduler]）。 */
interface ReminderOneShotScheduler {
    suspend fun scheduleHomeworkReminder(
        rowId: String,
        fireAtMillis: Long,
        label: String,
        targetAtMillis: Long = fireAtMillis,
    ): OneShotScheduleResult

    suspend fun scheduleNoticeReminder(
        groupId: String,
        fireAtMillis: Long,
        label: String,
        targetAtMillis: Long = fireAtMillis,
    ): OneShotScheduleResult

    suspend fun cancel(key: OneShotKey)

    suspend fun cancelAll()

    suspend fun listPending(): List<OneShotPending>
}

private val oneShotLock = Mutex()

/** Bind pre-upgrade entries to the signed-in account once. The old worker can still wake them. */
private suspend fun pendingFor(context: Context, owner: String): OneShotPendingStore {
    val store = SharedPrefsOneShotPendingStore(context, owner)
    val legacy = SharedPrefsOneShotPendingStore(context)
    val items = legacy.load()
    if (items.isNotEmpty()) {
        val existing = store.load().map { it.key }.toSet()
        for (item in items) if (item.key !in existing) store.add(item)
        legacy.clear()
    }
    return store
}

private fun OneShotKey.backupName(owner: String) = "ct_manual:$owner:$targetKind:${Uri.encode(targetId)}:$fireAtMillis"

private fun armOneShot(context: Context, owner: String, item: OneShotPending, replace: Boolean) {
    val key = item.key
    val delay = (key.fireAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
    val input = Data.Builder().putString("owner", owner).putString("kind", key.targetKind.name)
        .putString("targetId", key.targetId).putLong("fireAt", key.fireAtMillis).build()
    val request = OneTimeWorkRequestBuilder<ReminderOneShotWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS).setInputData(input).addTag(WorkManagerOneShotScheduler.TAG).build()
    WorkManager.getInstance(context).enqueueUniqueWork(key.backupName(owner),
        if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
    if (delay > 0) UserReminderAlarms.set(context, key.targetKind.name, owner, key.targetId,
        key.fireAtMillis.toString(), key.fireAtMillis)
}

internal suspend fun restoreOneShotAlarms(context: Context, owner: String) = oneShotLock.withLock {
    val store = pendingFor(context, owner)
    val oldest = System.currentTimeMillis() - ReminderCatchUp.MAX_LOOKBACK_MS
    for (item in store.load()) {
        if (item.fireAtMillis < oldest) store.remove(item.key) else armOneShot(context, owner, item, false)
    }
}

class WorkManagerOneShotScheduler(
    context: Context,
    private val permissionController: PermissionController,
    private val clock: () -> Long = System::currentTimeMillis,
) : ReminderOneShotScheduler {
    private val appContext = context.applicationContext

    override suspend fun scheduleHomeworkReminder(rowId: String, fireAtMillis: Long, label: String, targetAtMillis: Long) =
        schedule(OneShotTargetKind.HOMEWORK, rowId, fireAtMillis, label, targetAtMillis)

    override suspend fun scheduleNoticeReminder(groupId: String, fireAtMillis: Long, label: String, targetAtMillis: Long) =
        schedule(OneShotTargetKind.NOTICE, groupId, fireAtMillis, label, targetAtMillis)

    private suspend fun schedule(kind: OneShotTargetKind, id: String, at: Long, label: String, target: Long): OneShotScheduleResult =
        withContext(Dispatchers.IO) {
            val owner = reminderSession(appContext)?.reminderOwner ?: return@withContext OneShotScheduleResult.Failed("请先登录")
            oneShotLock.withLock {
                val store = pendingFor(appContext, owner)
                val now = clock()
                val key = ReminderOneShot.key(kind, id, at)
                ReminderOneShot.validate(kind, id, at, now, permissionController.state(PermissionType.NOTIFICATIONS),
                    store.load().count { it.key != key && it.key.targetKind == kind && it.fireAtMillis > now }, target)
                    ?.let { return@withLock it }
                val item = OneShotPending(key, label.ifBlank { kind.label }.take(160), at, target)
                try {
                    store.add(item)
                    armOneShot(appContext, owner, item, true)
                    OneShotScheduleResult.Scheduled(key, at)
                } catch (e: Exception) {
                    store.remove(key)
                    OneShotScheduleResult.Failed(e.localizedMessage ?: "无法设置系统提醒")
                }
            }
        }

    private suspend fun remove(store: OneShotPendingStore, owner: String, key: OneShotKey) {
        store.remove(key)
        UserReminderAlarms.cancel(appContext, key.targetKind.name, owner, key.targetId, key.fireAtMillis.toString())
        WorkManager.getInstance(appContext).cancelUniqueWork(key.backupName(owner))
        com.furina.classtools.data.calendar.ReminderCalendar.remove(appContext, key.calendarKey(owner))
    }

    override suspend fun cancel(key: OneShotKey) = withContext(Dispatchers.IO) {
        val owner = reminderSession(appContext)?.reminderOwner ?: return@withContext
        oneShotLock.withLock { remove(pendingFor(appContext, owner), owner, key) }
    }

    override suspend fun cancelAll() = withContext(Dispatchers.IO) {
        val owner = reminderSession(appContext)?.reminderOwner ?: return@withContext
        oneShotLock.withLock {
            val store = pendingFor(appContext, owner)
            for (item in store.load()) remove(store, owner, item.key)
        }
    }

    override suspend fun listPending(): List<OneShotPending> = withContext(Dispatchers.IO) {
        val owner = reminderSession(appContext)?.reminderOwner ?: return@withContext emptyList()
        oneShotLock.withLock { pendingFor(appContext, owner).load().filter { it.fireAtMillis > clock() }.sortedBy { it.fireAtMillis } }
    }

    companion object { const val TAG = "ct_oneshot_reminder" }
}

internal fun OneShotKey.calendarKey(owner: String) = "reminder:$owner:$targetKind:${Uri.encode(targetId)}:$fireAtMillis"

internal suspend fun deliverOneShot(context: Context, key: OneShotKey, expectedOwner: String?,
    legacyTargetAt: Long = 0L): Boolean = oneShotLock.withLock {
    val owner = reminderSession(context)?.reminderOwner ?: return true
    if (expectedOwner != null && owner != expectedOwner) return true
    val store = pendingFor(context, owner)
    val item = store.load().find { it.key == key } ?: return true
    val now = System.currentTimeMillis()
    if (key.fireAtMillis > now) {
        UserReminderAlarms.set(context, key.targetKind.name, owner, key.targetId, key.fireAtMillis.toString(), key.fireAtMillis)
        return true
    }
    if (now - key.fireAtMillis > ReminderCatchUp.MAX_LOOKBACK_MS) {
        store.remove(key)
        return true
    }
    if (key.targetKind == OneShotTargetKind.NOTICE &&
        com.furina.classtools.data.notify.SignupCountdowns.deliverOpening(context, owner, key.targetId, key.fireAtMillis)) {
        store.remove(key)
        return true
    }
    val notifier = AndroidReminderNotifier(context, owner)
    notifier.ensureChannels()
    val event = ReminderOneShot.eventFor(key.targetKind, key.targetId, item.label,
        legacyTargetAt.takeIf { it > 0 } ?: item.targetAtMillis)
    val sent = notifier.notify(event.copy(triggerAtMillis = key.fireAtMillis))
    if (sent) store.remove(key)
    sent
}

class ReminderOneShotWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val kind = OneShotTargetKind.entries.find { it.name == inputData.getString("kind") } ?: return Result.success()
        val id = inputData.getString("targetId") ?: return Result.success()
        val key = OneShotKey(kind, id, inputData.getLong("fireAt", 0L))
        val sent = deliverOneShot(applicationContext, key, inputData.getString("owner"), inputData.getLong("targetAt", 0L))
        return if (sent || runAttemptCount >= 3) Result.success() else Result.retry()
    }
}

/** UI 便捷工厂：`ReminderOneShotFactory.create(context, container.permissionController)`。 */
object ReminderOneShotFactory {
    fun create(
        context: Context,
        permissionController: PermissionController,
    ): ReminderOneShotScheduler = WorkManagerOneShotScheduler(context, permissionController)
}