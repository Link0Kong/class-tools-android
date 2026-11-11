package com.furina.classtools.data.reminder

import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.asBooleanOrNull
import com.furina.classtools.data.network.asLongOrNull
import com.furina.classtools.data.network.asStringOrNull
import com.furina.classtools.data.network.parseJsonOrNull
import com.furina.classtools.data.notices.NoticeEntry
import com.furina.classtools.data.notices.NoticeSigRow
import com.furina.classtools.data.notices.NoticeSignature
import com.furina.classtools.data.permission.PermissionController
import com.furina.classtools.data.permission.PermissionState
import com.furina.classtools.data.permission.PermissionType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * 后台通知轮询（v3.7 核心，用户反馈第 12 条「App 未在后台运行时也要能收到通知」）。
 *
 * 旧实现的问题（代码路径证据）：
 * 1. `ReminderWorker`（15 分钟周期）只调用 [ReminderEngine.runNow]，数据来自
 *    `ContainerReminderDataProvider` → `homeworkRepository.cached()` / `noticesRepository.cached()`；
 *    这两个缓存都是**内存字段**（进程被杀/冷启动即空），因此后台没有任何数据可规划；
 * 2. 该 Worker 没有网络约束、也**不发任何网络请求** → 永远不会发现"新通知"；
 * 3. `AppContainer.activeSession`（X-Class-Token 唯一来源）只在 UI `ClassToolsRoot` 里赋值，
 *    Worker 冷启动进程时 token=null → 即使加了联网也会鉴权失败。
 *
 * 本文件实现纯逻辑部分（可 JVM 单测）：
 * - 首次运行只建立基线（记录已有通知 id），**不轰炸**；
 * - 之后按「通知轻量签名」比较，只有签名变化才拉整表；
 * - 新增 id → 本地高优先级通知（IMPORTANCE_HIGH 渠道 + 声音/震动/锁屏可见 + 点击进通知页）；
 * - 本地持久化已通知 id/签名/上次轮询时间（去重 + 诊断）。
 *
 * Android 实现（SharedPreferences / 网络 / Worker 编排）见 `ReminderBackgroundAndroid.kt`。
 */
data class BackgroundPollState(
    /** 是否已完成首次基线（true 后新增 id 才会通知）。 */
    val baselineDone: Boolean = false,
    /** 已知通知 id 集合（用于去重；只保留最近 [ReminderBackgroundLogic.MAX_KNOWN_IDS] 个）。 */
    val knownIds: Set<String> = emptySet(),
    /** 上次通知轻量签名（`id:ts:hidden:edited_at|...`；一致则跳过整表拉取）。 */
    val signature: String = "",
    /** 已知通知的最大 ts（诊断/排序用）。 */
    val lastMaxTs: Long = 0L,
    /** 上次轮询尝试时间。 */
    val lastPollAtMillis: Long = 0L,
    /** 最近一次成功的轮询时间。 */
    val lastSuccessAtMillis: Long = 0L,
    /** 最近一次轮询发现的新通知条数。 */
    val lastNewCount: Int = 0,
    /** 最近一次轮询结果（人类可读、≤240 字，不含通知正文）。 */
    val lastResult: String = "",
    /** 累计轮询次数。 */
    val pollCount: Long = 0L,
)

/** 后台轮询状态编解码（纯函数，可单测；损坏 JSON → null 视为无状态）。 */
object BackgroundPollCodec {

    fun encode(state: BackgroundPollState): String = buildJsonObject {
        put("v", 1)
        put("baseline", state.baselineDone)
        put(
            "known",
            buildJsonArray {
                state.knownIds.sorted().take(ReminderBackgroundLogic.MAX_KNOWN_IDS).forEach {
                    add(JsonPrimitive(it))
                }
            },
        )
        put("sig", state.signature.take(ReminderBackgroundLogic.MAX_SIGNATURE_LENGTH))
        put("maxTs", state.lastMaxTs)
        put("lastPollAt", state.lastPollAtMillis)
        put("lastSuccessAt", state.lastSuccessAtMillis)
        put("lastNew", state.lastNewCount)
        put("lastResult", state.lastResult.take(ReminderBackgroundLogic.MAX_RESULT_LENGTH))
        put("pollCount", state.pollCount)
    }.toString()

    fun decode(raw: String?): BackgroundPollState? {
        if (raw.isNullOrBlank()) return null
        val obj = parseJsonOrNull(NetJson, raw) as? JsonObject ?: return null
        val known = (obj["known"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
            ?.take(ReminderBackgroundLogic.MAX_KNOWN_IDS)
            ?.toSet()
            .orEmpty()
        return BackgroundPollState(
            baselineDone = obj["baseline"].asBooleanOrNull() ?: false,
            knownIds = known,
            signature = obj["sig"].asStringOrNull().orEmpty(),
            lastMaxTs = obj["maxTs"].asLongOrNull() ?: 0L,
            lastPollAtMillis = obj["lastPollAt"].asLongOrNull() ?: 0L,
            lastSuccessAtMillis = obj["lastSuccessAt"].asLongOrNull() ?: 0L,
            lastNewCount = (obj["lastNew"].asLongOrNull() ?: 0L).toInt().coerceAtLeast(0),
            lastResult = obj["lastResult"].asStringOrNull().orEmpty(),
            pollCount = obj["pollCount"].asLongOrNull() ?: 0L,
        )
    }
}

/** 后台轮询纯逻辑（无 Android 依赖）。 */
object ReminderBackgroundLogic {

    /** WorkManager 周期任务系统下限（分钟），不可调小。 */
    const val MIN_INTERVAL_MINUTES: Long = 15L

    /** 默认周期（分钟）。 */
    const val DEFAULT_INTERVAL_MINUTES: Long = 15L

    /** 单次最多弹出的"新通知"条数；超出合并为一条汇总（防轰炸）。 */
    const val MAX_NOTIFY_PER_POLL: Int = 3

    /** 本地已通知 id 保留上限（防无限增长）。 */
    const val MAX_KNOWN_IDS: Int = 400

    const val MAX_SIGNATURE_LENGTH: Int = 64_000

    const val MAX_RESULT_LENGTH: Int = 240

    /** 周期分钟规范化（≥15 且 ≤24h）。 */
    fun intervalMinutes(requested: Long = DEFAULT_INTERVAL_MINUTES): Long =
        requested.coerceIn(MIN_INTERVAL_MINUTES, 24L * 60L)

    /** 是否应执行后台新通知轮询（总开关 + 通知提醒开关；与既有设置口径一致）。 */
    fun pollEnabled(settings: ReminderSettings): Boolean = settings.enabled && settings.noticeEnabled

    /** 通知轻量签名（复用 [NoticeSignature] 口径：id:ts:hidden:edited_at，按服务端顺序）。 */
    fun signatureOf(rows: List<NoticeSigRow>): String = NoticeSignature.ofRows(validRows(rows))

    fun validRows(rows: List<NoticeSigRow>): List<NoticeSigRow> = rows.filter { it.id.isNotBlank() }

    /** 相比已知 id 的新增行（按 ts 升序；用于通知排序）。 */
    fun newRows(knownIds: Set<String>, rows: List<NoticeSigRow>): List<NoticeSigRow> =
        validRows(rows)
            .filterNot { it.id in knownIds }
            .sortedBy { it.ts }

    /**
     * 已知 id 集合更新：**必须保留本次接口返回的全部当前 id**（否则已读会被重复通知），
     * 再补历史 id 到 [max] 上限；超限时丢弃历史（只影响已删除的旧通知）。
     */
    fun pruneKnownIds(
        knownIds: Set<String>,
        rows: List<NoticeSigRow>,
        max: Int = MAX_KNOWN_IDS,
    ): Set<String> {
        val current = validRows(rows).map { it.id }.distinct()
        val out = LinkedHashSet<String>(minOf(current.size, max) * 2)
        current.forEach {
            if (out.size < max) out += it
        }
        knownIds.sorted().forEach {
            if (out.size < max) out += it
        }
        return out
    }

    /** 安静时段是否应静默（静默时仍更新基线，避免安静时段结束后补炸）。 */
    fun quietAt(settings: ReminderSettings, atMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        settings.isQuiet(atMillis, zone)

    /** 新通知通知标题。 */
    fun newNoticeTitle(entry: NoticeEntry): String {
        val title = entry.title.trim().ifBlank { "班级通知" }
        return "新通知：$title"
    }

    /** 新通知通知正文：分类 · 摘要 · 来源（每段截断，不含隐私）。 */
    fun newNoticeBody(entry: NoticeEntry): String {
        val cat = entry.group.cat.trim().takeIf { it.isNotBlank() }
        val summary = entry.group.aiSummary.ifBlank { entry.group.summary }
            .trim()
            .takeIf { it.isNotBlank() }
            ?.take(80)
        val source = entry.group.source.trim().takeIf { it.isNotBlank() }?.take(30)
        return listOfNotNull(cat, summary, source).joinToString(" · ").ifBlank { "点击查看通知详情" }
    }

    /**
     * 把新增通知映射为本地提醒事件：
     * - 只取 `!hidden` 的条目（管理员隐藏的不提醒）；
     * - 事件 key = `nt-new:<id>`（同一通知只弹一次，另有本地 knownIds 去重双保险）；
     * - 渠道 = [ReminderChannels.NEW_NOTICE_ID]（IMPORTANCE_HIGH、声音/震动/锁屏可见）；
     * - deep link：`toolId = "nt"` → 点击打开 App 通知页；
     * - 超过 [MAX_NOTIFY_PER_POLL] 条合并为一条汇总（key 含最大 ts + 当前时间，避免与下一轮冲突）。
     */
    fun newNoticeEvents(
        entries: List<NoticeEntry>,
        newIds: Set<String>,
        nowMillis: Long,
    ): List<ReminderEvent> {
        if (newIds.isEmpty()) return emptyList()
        val fresh = entries
            .filter { it.id in newIds && !it.hidden && it.id.isNotBlank() }
            .distinctBy { it.id }
            .sortedByDescending { it.ts }
        if (fresh.isEmpty()) return emptyList()
        val shown = fresh.take(MAX_NOTIFY_PER_POLL)
        val events = ArrayList<ReminderEvent>(shown.size + 1)
        shown.forEach { entry ->
            events += ReminderEvent(
                kind = ReminderKind.NOTICE_DUE,
                key = "nt-new:${entry.id}",
                triggerAtMillis = nowMillis,
                title = newNoticeTitle(entry),
                body = newNoticeBody(entry),
                toolId = ReminderKind.NOTICE_DUE.toolId,
                channelIdOverride = ReminderChannels.NEW_NOTICE_ID,
            )
        }
        val remaining = fresh.size - shown.size
        if (remaining > 0) {
            events += ReminderEvent(
                kind = ReminderKind.NOTICE_DUE,
                key = "nt-new-summary:${fresh.maxOf { it.ts }}:$nowMillis",
                triggerAtMillis = nowMillis,
                title = "班级新通知",
                body = "共 ${fresh.size} 条新通知，另有 $remaining 条未展示，点击查看全部",
                toolId = ReminderKind.NOTICE_DUE.toolId,
                channelIdOverride = ReminderChannels.NEW_NOTICE_ID,
            )
        }
        return events
    }
}

/** 通知接口（窄接口：生产实现走 ApiClient + NoticesRepository；单测注入假实现）。 */
interface BackgroundNoticeGateway {
    /** 拉轻量签名行；失败（网络/格式异常）→ null（静默，不崩）。 */
    suspend fun fetchSignatureRows(): List<NoticeSigRow>?

    /** 拉整表通知（签名变化时才调用）；失败 → null。 */
    suspend fun fetchEntries(): List<NoticeEntry>?
}

/** 后台轮询状态存储（生产 = SharedPreferences；单测 = 内存）。 */
interface BackgroundPollStateStore {
    suspend fun load(): BackgroundPollState?

    suspend fun save(state: BackgroundPollState)
}

class InMemoryBackgroundPollStateStore(
    initial: BackgroundPollState? = null,
) : BackgroundPollStateStore {
    private var state: BackgroundPollState? = initial

    override suspend fun load(): BackgroundPollState? = state

    override suspend fun save(state: BackgroundPollState) {
        this.state = state
    }
}

/** 一次后台轮询的结果（写诊断/日志用；不含通知正文/隐私）。 */
data class BackgroundPollOutcome(
    val baselineEstablished: Boolean = false,
    val signatureChanged: Boolean = false,
    val newIds: List<String> = emptyList(),
    val notified: Int = 0,
    val failed: Boolean = false,
    val state: BackgroundPollState = BackgroundPollState(),
    val reason: String = "",
) {
    fun logSummary(): String = buildString {
        append("[CT_BG] baseline=").append(baselineEstablished)
        append(" changed=").append(signatureChanged)
        append(" new=").append(newIds.size)
        append(" notified=").append(notified)
        append(" failed=").append(failed)
        append(" reason=").append(reason)
    }
}

/**
 * 后台轮询执行器（可单测）：
 * 设置/权限检查 → 拉签名 → 首次建基线 → 签名未变直接返回 → 签名变化拉整表 → 新 id 通知 → 保存状态。
 * 全程失败静默（返回 failed 状态，不抛异常），符合「无 token/离线则静默」要求。
 */
class ReminderBackgroundRunner(
    private val gateway: BackgroundNoticeGateway,
    private val stateStore: BackgroundPollStateStore,
    private val settingsStore: ReminderSettingsStore,
    private val notifier: ReminderNotifier,
    private val permissionController: PermissionController,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    suspend fun run(): BackgroundPollOutcome {
        val now = clock()
        val previous = runCatching { stateStore.load() }.getOrNull()
        val settings = runCatching { settingsStore.current() }.getOrDefault(ReminderSettings.DEFAULT)

        suspend fun record(
            result: String,
            base: BackgroundPollState? = previous,
            newCount: Int = 0,
            success: Boolean = false,
            failed: Boolean = false,
        ): BackgroundPollOutcome {
            val state = (base ?: BackgroundPollState()).copy(
                lastPollAtMillis = now,
                lastSuccessAtMillis = if (success) now else base?.lastSuccessAtMillis ?: 0L,
                lastNewCount = newCount,
                lastResult = result.take(ReminderBackgroundLogic.MAX_RESULT_LENGTH),
                pollCount = (base?.pollCount ?: 0L) + 1L,
            )
            runCatching { stateStore.save(state) }
            return BackgroundPollOutcome(failed = failed, state = state, reason = result)
        }

        if (!ReminderBackgroundLogic.pollEnabled(settings)) {
            return record("后台通知轮询已关闭（设置）")
        }
        val permission = runCatching {
            permissionController.state(PermissionType.NOTIFICATIONS)
        }.getOrDefault(PermissionState.REQUESTABLE)
        if (!permission.isGranted) {
            // 无通知权限时仍不联网（省电/隐私），记录原因供诊断页展示。
            return record("通知权限未开启，跳过后台轮询")
        }
        val rows = gateway.fetchSignatureRows() ?: return record("后台轮询失败（网络/接口异常）", failed = true)
        val valid = ReminderBackgroundLogic.validRows(rows)
        val signature = ReminderBackgroundLogic.signatureOf(valid)

        // 首次运行：只建基线，不通知历史通知（防轰炸）。
        if (previous == null || !previous.baselineDone) {
            val baseline = BackgroundPollState(
                baselineDone = true,
                knownIds = ReminderBackgroundLogic.pruneKnownIds(emptySet(), valid),
                signature = signature,
                lastMaxTs = valid.maxOfOrNull { it.ts } ?: 0L,
                lastPollAtMillis = now,
                lastSuccessAtMillis = now,
                lastNewCount = 0,
                lastResult = "首次运行：已建立基线（${valid.size} 条已有通知，不提醒）",
                pollCount = 1L,
            )
            runCatching { stateStore.save(baseline) }
            return BackgroundPollOutcome(
                baselineEstablished = true,
                state = baseline,
                reason = baseline.lastResult,
            )
        }

        // 签名未变化：不拉整表（省流量），仅更新轮询时间。
        if (signature == previous.signature) {
            return record("无变化（${valid.size} 条通知）", success = true)
        }

        // 签名变化：先算新增 id，再拉整表（失败则保留旧 knownIds，下一轮重试）。
        val added = ReminderBackgroundLogic.newRows(previous.knownIds, valid)
        val entries = gateway.fetchEntries()
            ?: return record("通知签名已变化但整表拉取失败，下一轮重试", failed = true)
        val nextKnown = ReminderBackgroundLogic.pruneKnownIds(previous.knownIds, valid)
        val baseState = previous.copy(
            knownIds = nextKnown,
            signature = signature,
            lastMaxTs = valid.maxOfOrNull { it.ts } ?: previous.lastMaxTs,
        )

        if (added.isEmpty()) {
            return record(
                "签名变化但无新增通知（编辑/隐藏）",
                base = baseState,
                success = true,
            )
        }
        if (ReminderBackgroundLogic.quietAt(settings, now, zone)) {
            // 安静时段：只更新基线不发通知（避免安静时段结束后补轰炸）。
            return record(
                "发现 ${added.size} 条新通知，安静时段已静默",
                base = baseState,
                newCount = added.size,
                success = true,
            )
        }

        val events = ReminderBackgroundLogic.newNoticeEvents(entries, added.map { it.id }.toSet(), now)
        var notified = 0
        if (events.isNotEmpty()) {
            runCatching { notifier.ensureChannels() }
            events.forEach { event ->
                if (runCatching { notifier.notify(event) }.getOrDefault(false)) notified += 1
            }
        }
        val reason = if (events.isEmpty()) {
            "发现 ${added.size} 条新通知但无可见条目"
        } else {
            "发现 ${added.size} 条新通知，已通知 $notified 条"
        }
        return record(
            reason,
            base = baseState,
            newCount = added.size,
            success = true,
        ).copy(signatureChanged = true, newIds = added.map { it.id }, notified = notified)
    }
}

/** 后台提醒诊断快照（诊断页展示；纯数据 + 纯文案）。 */
data class BackgroundReminderDiagnostics(
    val lastPollAtMillis: Long? = null,
    val lastSuccessAtMillis: Long? = null,
    val lastNewCount: Int = 0,
    val lastResult: String = "",
    val pollCount: Long = 0L,
    val baselineDone: Boolean = false,
    val permission: PermissionState = PermissionState.REQUESTABLE,
    val notificationsEnabled: Boolean = false,
    val newNoticeChannelImportance: Int? = null,
    /** true = 系统已允许不受电池优化限制（Doze 白名单）。 */
    val batteryOptimizationIgnored: Boolean = false,
    /** true = 精确闹钟可用（单条提醒可准点）。 */
    val exactAlarmAllowed: Boolean = false,
    val periodicIntervalMinutes: Long = ReminderBackgroundLogic.DEFAULT_INTERVAL_MINUTES,
) {
    /** 后台提醒链路的"可以工作"最低条件（权限 + 系统通知开关）。 */
    val ready: Boolean get() = notificationsEnabled && permission.isGranted

    fun formatLines(zone: ZoneId = ZoneId.systemDefault()): List<String> = buildList {
        add(
            "后台检查：${if (baselineDone) "已启用" else "尚未建立基线"} · 约 " +
                "${periodicIntervalMinutes.coerceAtLeast(ReminderBackgroundLogic.MIN_INTERVAL_MINUTES)} 分钟一次（需网络）",
        )
        add("上次轮询：${formatTime(lastPollAtMillis, zone)}${if (lastNewCount > 0) " · 新增 $lastNewCount 条" else ""}")
        add("最近成功：${formatTime(lastSuccessAtMillis, zone)}")
        if (lastResult.isNotBlank()) add("结果：$lastResult")
        if (pollCount > 0L) add("累计轮询：$pollCount 次")
        val guide = com.furina.classtools.data.permission.PermissionGuidance.notification(permission)
        add(
            "通知权限：${guide.statusLabel}" +
                if (!notificationsEnabled) "（系统通知总开关已关闭）" else "",
        )
        add("新通知渠道：${ReminderChannels.importanceLabel(newNoticeChannelImportance)}")
        add(
            if (batteryOptimizationIgnored) {
                "电池优化：不受限制（后台检查较准时）"
            } else {
                "电池优化：可能受限（提醒可能延迟，可设为不受限）"
            },
        )
        add(
            if (exactAlarmAllowed) {
                "精确闹钟：已授权（单条提醒更准时）"
            } else {
                "精确闹钟：未授权（单条提醒回退 WorkManager，可能延迟）"
            },
        )
    }

    private fun formatTime(millis: Long?, zone: ZoneId): String {
        if (millis == null || millis <= 0L) return "尚未运行"
        return TIME_FORMATTER.format(Instant.ofEpochMilli(millis).atZone(zone))
    }

    private companion object {
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)
    }
}
