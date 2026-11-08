package com.furina.classtools.data.diag

import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.network.NetJson
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * client_diag 自动上报（契约 §2）：fingerprint 去重（同指纹 10 分钟最多 1 条）+ 每批最多 3 条
 * + 离线/失败落「待补发」队列，下次启动在线时补报（[ClientDiagReporter.drainPending]）。
 *
 * 崩溃路径（[CrashDiagRecorder]）必须同步、轻量、绝不打印/落盘任何 token/密码：
 * 只把脱敏后的行追加到本机队列；真正的网络上报由启动后/诊断页的 drain 完成。
 */

/** 上报结果（UI 可提示「已上报 / 已排队 / 已限频」）。 */
sealed interface DiagReportOutcome {
    data object Sent : DiagReportOutcome
    data object Queued : DiagReportOutcome
    data object Skipped : DiagReportOutcome
}

/** 待补发队列（崩溃/离线场景）。崩溃线程必须是同步 append。 */
interface ClientDiagPendingStore {
    fun append(row: JsonObject)
    fun readAll(): List<JsonObject>
    fun replaceAll(rows: List<JsonObject>)
    fun size(): Int
}

/** 文件实现：`<dir>/pending_client_diag.jsonl`，一行一条（坏行跳过）。 */
class FileClientDiagPendingStore(
    private val directory: File,
    private val json: Json = NetJson,
) : ClientDiagPendingStore {

    private val file: File = File(directory, FILE_NAME)

    @Synchronized
    override fun append(row: JsonObject) {
        runCatching {
            if (!directory.exists()) directory.mkdirs()
            file.appendText(json.encodeToString(JsonObject.serializer(), row) + "\n", Charsets.UTF_8)
        }
    }

    @Synchronized
    override fun readAll(): List<JsonObject> {
        if (!file.isFile) return emptyList()
        return runCatching {
            file.readLines(Charsets.UTF_8)
                .mapNotNull { line ->
                    if (line.isBlank()) null else DiagParser.parseRows(line, json)?.firstOrNull()
                }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    override fun replaceAll(rows: List<JsonObject>) {
        runCatching {
            if (rows.isEmpty()) {
                file.delete()
                return
            }
            if (!directory.exists()) directory.mkdirs()
            val text = rows.joinToString("\n") { json.encodeToString(JsonObject.serializer(), it) }
            file.writeText(text + "\n", Charsets.UTF_8)
        }
    }

    override fun size(): Int = readAll().size

    companion object {
        const val FILE_NAME: String = "pending_client_diag.jsonl"
    }
}

/** 内存实现（单测 / Debug 预览）。 */
class InMemoryClientDiagPendingStore(
    initial: List<JsonObject> = emptyList(),
) : ClientDiagPendingStore {
    private val rows = ArrayList<JsonObject>(initial)

    @Synchronized
    override fun append(row: JsonObject) {
        rows.add(row)
    }

    @Synchronized
    override fun readAll(): List<JsonObject> = rows.toList()

    @Synchronized
    override fun replaceAll(rows: List<JsonObject>) {
        this.rows.clear()
        this.rows.addAll(rows)
    }

    @Synchronized
    override fun size(): Int = rows.size
}

/**
 * 限频器（纯 Kotlin，可单测）：
 * - 同 fingerprint 在 [fingerprintWindowMs]（默认 10 分钟）内最多 1 条；
 * - 每批（每次诊断/异常批次）最多 [maxPerBatch]（默认 3）条；
 * - [beginBatch] 在每次「重新检测」/页面进入时调用，重置批次计数（指纹窗口不重置）。
 */
class ClientDiagRateLimiter(
    private val maxPerBatch: Int = 3,
    private val fingerprintWindowMs: Long = 10L * 60 * 1000,
) {
    private val lastByFingerprint = HashMap<String, Long>()
    private var batchCount = 0

    fun beginBatch() {
        batchCount = 0
    }

    /** 判断并占用一个上报名额（线程安全）。 */
    @Synchronized
    fun allow(fingerprint: String, nowMillis: Long): Boolean =
        allowInternal(fingerprint, nowMillis, checkBatch = true)

    /** 补发队列用：同指纹窗口照常校验，但不受「每批 ≤3 条」限制。 */
    @Synchronized
    fun allowDrain(fingerprint: String, nowMillis: Long): Boolean =
        allowInternal(fingerprint, nowMillis, checkBatch = false)

    /** 上报失败时回滚占用（否则同条数据永远被自己的窗口挡住）。 */
    @Synchronized
    fun rollback(fingerprint: String) {
        lastByFingerprint.remove(fingerprint.ifBlank { "-" })
        if (batchCount > 0) batchCount -= 1
    }

    private fun allowInternal(fingerprint: String, nowMillis: Long, checkBatch: Boolean): Boolean {
        val key = fingerprint.ifBlank { "-" }
        prune(nowMillis)
        if (checkBatch && batchCount >= maxPerBatch) return false
        val last = lastByFingerprint[key]
        if (last != null && nowMillis - last < fingerprintWindowMs) return false
        lastByFingerprint[key] = nowMillis
        if (checkBatch) batchCount += 1
        return true
    }

    @Synchronized
    fun batchCount(): Int = batchCount

    @Synchronized
    fun trackedFingerprints(): Int = lastByFingerprint.size

    private fun prune(nowMillis: Long) {
        val iterator = lastByFingerprint.entries.iterator()
        while (iterator.hasNext()) {
            if (nowMillis - iterator.next().value >= fingerprintWindowMs) iterator.remove()
        }
    }
}

/** 上报器：去重限频 + 失败落队列 + 队列补报。 */
class ClientDiagReporter(
    private val repository: DiagRepository,
    private val pending: ClientDiagPendingStore,
    private val limiter: ClientDiagRateLimiter = ClientDiagRateLimiter(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** 每次「重新检测」或页面进入时调用，重置批次计数（每批最多 3 条）。 */
    fun beginBatch() = limiter.beginBatch()

    fun pendingCount(): Int = pending.size()

    /** 直接入队（崩溃路径 / 首帧离线），等 drain 补报。 */
    fun queue(row: JsonObject) = pending.append(row)

    /** 立即上报一条；限频 → Skipped；网络失败 → 入队 Queued。 */
    suspend fun report(row: JsonObject): DiagReportOutcome {
        val ts = rowTimestamp(row) ?: now()
        val fingerprint = DiagPayload.fingerprintOf(row)
        if (!limiter.allow(fingerprint, ts)) return DiagReportOutcome.Skipped
        return when (repository.reportClientDiag(row)) {
            is ApiResult.Success -> DiagReportOutcome.Sent
            is ApiResult.Failure -> {
                limiter.rollback(fingerprint)
                pending.append(row)
                DiagReportOutcome.Queued
            }
        }
    }

    /** 补报本机队列（启动后在线 / 进入诊断页时调用）；返回成功条数。 */
    suspend fun drainPending(): Int {
        val rows = pending.readAll()
        if (rows.isEmpty()) return 0
        val remaining = ArrayList<JsonObject>(rows.size)
        var sent = 0
        for (row in rows) {
            val ts = rowTimestamp(row) ?: now()
            if (!limiter.allowDrain(DiagPayload.fingerprintOf(row), ts)) {
                remaining.add(row)
                continue
            }
            when (repository.reportClientDiag(row)) {
                is ApiResult.Success -> sent += 1
                is ApiResult.Failure -> {
                    limiter.rollback(DiagPayload.fingerprintOf(row))
                    remaining.add(row)
                }
            }
        }
        pending.replaceAll(remaining)
        return sent
    }

    private fun rowTimestamp(row: JsonObject): Long? =
        (row["ts"] as? JsonPrimitive)?.longOrNull

    companion object {
        /** 一条诊断行是否具备上报资格（kind/fingerprint/ts 合法）。 */
        fun isReportable(row: JsonObject): Boolean =
            DiagPayload.fingerprintOf(row).isNotBlank() && rowTimestampOf(row) > 0

        fun rowTimestampOf(row: JsonObject): Long =
            (row["ts"] as? JsonPrimitive)?.longOrNull ?: 0L
    }
}

/**
 * 未捕获异常记录器（Application 层 handler 调用）。
 *
 * 只做三件事：脱敏异常信息 → 构造 client_diag 行（kind=crash）→ 同步追加本机待发队列。
 * **不打印**堆栈/token/密码，也不在崩溃线程里发网络请求。
 */
class CrashDiagRecorder(
    private val pending: ClientDiagPendingStore,
    private val appVersion: String,
    private val sidProvider: () -> String?,
    private val deviceProvider: () -> Map<String, String> = { emptyMap() },
    private val now: () -> Long = System::currentTimeMillis,
    private val suffixProvider: () -> String = { DiagSanitizer.randomSuffix(12) },
) {
    fun record(thread: Thread?, throwable: Throwable) {
        runCatching {
            val exception = throwable.javaClass.name.ifBlank { "Throwable" }
            val rawMessage = throwable.message.orEmpty()
            val message = DiagSanitizer.sanitizeText(
                if (rawMessage.isBlank()) exception else "$exception: $rawMessage",
                300,
            )
            val detail = LinkedHashMap<String, String>()
            detail["check_id"] = "crash"
            detail["exception"] = exception
            detail["thread"] = thread?.name.orEmpty()
            detail["source"] = "uncaught"
            val row = DiagPayload.buildRow(
                id = DiagPayload.newId(suffixProvider()),
                sid = sidProvider()?.trim().orEmpty(),
                appVersion = appVersion,
                kind = "crash",
                level = "error",
                message = message,
                detail = detail,
                device = deviceProvider(),
                fingerprint = DiagPayload.fingerprint("crash", "error", exception, message),
                now = now(),
            )
            pending.append(row)
        }
    }
}

/**
 * 安装全局 uncaught exception handler：先记录到本机队列，再交回原 handler（系统崩溃流程不变）。
 * 返回安装的 handler（便于测试断言 / 卸载）。
 */
fun installCrashHandler(
    recorder: CrashDiagRecorder,
    previous: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler(),
): Thread.UncaughtExceptionHandler {
    val handler = Thread.UncaughtExceptionHandler { thread, throwable ->
        runCatching { recorder.record(thread, throwable) }
        // 交回默认 handler：保持系统默认崩溃行为（杀进程/系统日志），不吞异常。
        previous?.uncaughtException(thread, throwable)
    }
    Thread.setDefaultUncaughtExceptionHandler(handler)
    return handler
}
