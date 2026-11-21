package com.furina.classtools.data.privacy

import com.furina.classtools.data.marks.MarkKind
import com.furina.classtools.data.marks.MarksRepository
import com.furina.classtools.data.marks.MarksSnapshot
import com.furina.classtools.data.network.ApiClient
import com.furina.classtools.data.network.ApiResult
import com.furina.classtools.data.network.NetJson
import com.furina.classtools.data.network.asObjectOrNull
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.sync.UserDataRepository
import com.furina.classtools.data.sync.UserdataV6
import com.furina.classtools.data.fitrecs.DefaultFitRecsRepository
import com.furina.classtools.data.fitrecs.FitRecsRepository
import com.furina.classtools.features.fit.FitRecord
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * 数据与隐私（合规）：导出我的数据 / 清除云端数据 / 清除本机数据。
 *
 * 契约与红线：
 * - `docs/PRIVACY_COMPLIANCE.md` 待办 #1/#2、`web/AGENTS.md` 隐私红线；
 * - 导出 JSON：**不含 token、不出现学号明文（脱敏 `20***01`）**、**电费（power）不导出**、
 *   名单原文（roster.rollNames）不导出、课表快照（tt）不导出（非个人数据且体积大）；
 * - 清除云端：`userdata` 覆盖为空 v6 blob（POST upsert）、`marks` 相关行 `val=false`（逐条 upsert，不发 DELETE）、
 *   `fitrecs` 全部 `arch=true`（逐条 PATCH，不发 DELETE）；部分失败返回可读错误；
 * - 清除本机：通过注入的 [PrivacyLocalDataCleaner] 实现，不硬编码 Context。
 */

// ---------------------------------------------------------------- 导出模型

/** 页面数据概览。 */
data class PrivacySummary(
    val userdataSections: Int = 0,
    val marksCount: Int = 0,
    val fitRecsCount: Int = 0,
)

/** 导出结果（json 文本 + 建议文件名 + 概览）。 */
data class PrivacyExport(
    val json: String,
    val fileName: String,
    val generatedAt: Long,
    val summary: PrivacySummary,
)

sealed interface PrivacyExportResult {
    data class Success(val export: PrivacyExport) : PrivacyExportResult
    data class Failure(val message: String) : PrivacyExportResult
}

// ---------------------------------------------------------------- 清除模型

/** 清除编排中的一项（userdata / marks / fitrecs / 本机）。 */
data class PrivacyClearItem(
    val name: String,
    val ok: Boolean,
    val detail: String = "",
)

/** 清除云端结果（逐项可读；部分失败仍返回 Done，UI 展示 failed）。 */
data class PrivacyClearReport(
    val items: List<PrivacyClearItem>,
) {
    val allOk: Boolean get() = items.all { it.ok }
    val failed: List<PrivacyClearItem> get() = items.filterNot { it.ok }

    /** 可读结果：全部成功 → 清除完成；部分失败 → 列出失败项与原因。 */
    val message: String
        get() = if (allOk) {
            "云端数据已清除"
        } else {
            "部分数据清除失败：" + failed.joinToString("；") { item ->
                if (item.detail.isBlank()) item.name else "${item.name}（${item.detail}）"
            }
        }
}

sealed interface PrivacyClearCloudResult {
    data class Done(val report: PrivacyClearReport) : PrivacyClearCloudResult
    data class Failure(val message: String) : PrivacyClearCloudResult
}

/** 本机数据清除结果。 */
data class PrivacyLocalResult(
    val ok: Boolean,
    val message: String,
    val clearedItems: Int = 0,
)

/** 本机数据清理接口（由 AppContainer 注入具体实现：名单缓存 / userdata 本机基线 / 文件缓存等）。 */
interface PrivacyLocalDataCleaner {
    /** 清除本机可重建数据；**保留身份会话**（退出登录另走 Profile 的退出）。 */
    suspend fun clearLocalData(): PrivacyLocalResult
}

/** 组合式本机清理器：AppContainer 一行注入多个清理步骤（顺序执行，记录失败项）。 */
class CallbackLocalCleaner(
    private val steps: List<Step>,
) : PrivacyLocalDataCleaner {

    data class Step(
        val name: String,
        val clear: suspend () -> Unit,
    )

    override suspend fun clearLocalData(): PrivacyLocalResult {
        var cleared = 0
        val failures = mutableListOf<String>()
        for (step in steps) {
            runCatching { step.clear() }
                .onSuccess { cleared += 1 }
                .onFailure { failures += "${step.name}（${it.message ?: "清理失败"}）" }
        }
        return if (failures.isEmpty()) {
            PrivacyLocalResult(ok = true, message = "本机数据已清除（$cleared 项）", clearedItems = cleared)
        } else {
            PrivacyLocalResult(
                ok = false,
                message = "部分本机数据清理失败：" + failures.joinToString("；"),
                clearedItems = cleared,
            )
        }
    }
}

// ---------------------------------------------------------------- userdata 访问

/** userdata 的窄接口（导出读当前 sections / 清除成功后重置本机基线）。 */
interface PrivacyUserDataAccess {
    suspend fun currentSections(): JsonObject

    /** 清除云端成功后把本机基线重置为默认（避免下一次同步把旧数据写回）。 */
    suspend fun resetToDefaults()
}

class RepositoryPrivacyUserDataAccess(
    private val repository: UserDataRepository,
) : PrivacyUserDataAccess {

    override suspend fun currentSections(): JsonObject =
        runCatching { repository.currentSections.first() }.getOrDefault(JsonObject(emptyMap()))

    override suspend fun resetToDefaults() {
        runCatching {
            repository.updateCurrent(UserdataV6.sectionsOf(UserdataV6.empty()))
        }
    }
}

// ---------------------------------------------------------------- 仓库

interface PrivacyRepository {
    /** 页面概览（本机 userdata / marks + fitrecs 缓存，失败不抛）。 */
    suspend fun loadSummary(): PrivacySummary

    /** 生成我的数据导出 JSON（离线可导出：fitrecs 读取失败只记错误，不阻断导出）。 */
    suspend fun buildExport(): PrivacyExportResult

    /** 清除云端数据（userdata 空 blob → marks val=false → fitrecs arch=true）。 */
    suspend fun clearCloudData(): PrivacyClearCloudResult

    /** 清除本机数据（注入的 cleaner；保留身份）。 */
    suspend fun clearLocalData(): PrivacyLocalResult
}

class DefaultPrivacyRepository(
    private val api: ApiClient,
    private val marksRepository: MarksRepository,
    private val userDataAccess: PrivacyUserDataAccess,
    private val currentSid: () -> String?,
    private val currentSession: () -> Session?,
    private val localCleaner: PrivacyLocalDataCleaner,
    private val clock: () -> Long = System::currentTimeMillis,
    private val json: Json = NetJson,
) : PrivacyRepository {

    override suspend fun loadSummary(): PrivacySummary {
        val sections = userDataAccess.currentSections()
        val marks = marksRepository.marks.value
        val fitRecs = loadFitRecs().records.size
        return PrivacySummary(
            userdataSections = sections.keys.size,
            marksCount = PrivacyExportBuilder.countMarks(marks),
            fitRecsCount = fitRecs,
        )
    }

    override suspend fun buildExport(): PrivacyExportResult {
        val session = currentSession()
            ?: return PrivacyExportResult.Failure("请先完成身份验证")
        val sid = currentSid()?.trim().takeUnless { it.isNullOrEmpty() } ?: session.sid
        if (sid.isBlank()) return PrivacyExportResult.Failure("请先完成身份验证")

        val sections = userDataAccess.currentSections()
        val marks = marksRepository.marks.value
        val fit = loadFitRecs()
        val now = clock()
        val jsonText = PrivacyExportBuilder.build(
            session = session,
            sid = sid,
            sections = sections,
            marks = marks,
            fitRecords = if (fit.error == null) fit.records else null,
            fitError = fit.error,
            nowMillis = now,
        )
        val summary = PrivacySummary(
            userdataSections = PrivacyExportBuilder.WHITELIST.count { sections.containsKey(it) },
            marksCount = PrivacyExportBuilder.countMarks(marks),
            fitRecsCount = fit.records.size,
        )
        return PrivacyExportResult.Success(
            PrivacyExport(
                json = jsonText,
                fileName = PrivacyExportBuilder.fileName(now),
                generatedAt = now,
                summary = summary,
            ),
        )
    }

    override suspend fun clearCloudData(): PrivacyClearCloudResult {
        val session = currentSession()
            ?: return PrivacyClearCloudResult.Failure("请先完成身份验证")
        val sid = currentSid()?.trim().takeUnless { it.isNullOrEmpty() } ?: session.sid
        if (sid.isBlank()) return PrivacyClearCloudResult.Failure("请先完成身份验证")

        val now = clock()
        val items = mutableListOf<PrivacyClearItem>()

        // 1) userdata：覆盖为空 v6 blob（直接 POST upsert；走 merge 会被旧 at 挡回）
        val emptyBlob = runCatching { UserdataV6.emptyBlob(json) }.getOrDefault("{}")
        val userdataBody = buildJsonArray {
            add(
                buildJsonObject {
                    put("id", JsonPrimitive(sid))
                    put("blob", JsonPrimitive(emptyBlob))
                    put("ts", JsonPrimitive(now))
                },
            )
        }.toString()
        when (val result = api.post(USERDATA_PATH, userdataBody, prefer = PREFER_UPSERT)) {
            is ApiResult.Success -> {
                userDataAccess.resetToDefaults()
                items += PrivacyClearItem("userdata", ok = true, detail = "已覆盖为空数据")
            }

            is ApiResult.Failure -> items += PrivacyClearItem(
                "userdata",
                ok = false,
                detail = result.error.displayMessage,
            )
        }

        // 2) marks：先尽力刷新，再把所有 true 行置 val=false（不 DELETE）
        val marksReport = clearMarks()
        items += marksReport

        // 3) fitrecs：全部 arch=true（逐条 PATCH，不 DELETE）
        items += clearFitRecs(session.sid)

        return PrivacyClearCloudResult.Done(PrivacyClearReport(items))
    }

    override suspend fun clearLocalData(): PrivacyLocalResult = runCatching {
        localCleaner.clearLocalData()
    }.getOrElse { error ->
        PrivacyLocalResult(ok = false, message = "本机数据清理失败：${error.message ?: "未知错误"}")
    }

    // ------------------------------------------------------------ 内部

    private suspend fun clearMarks(): PrivacyClearItem {
        runCatching { marksRepository.refresh(force = true) }
        val snapshot = marksRepository.marks.value
        val rows = buildList {
            snapshot.hwDone.filterValues { it }.keys.forEach { add(MarkKind.HW to it) }
            snapshot.ntPin.filterValues { it }.keys.forEach { add(MarkKind.NT to it) }
            snapshot.ntDone.filterValues { it }.keys.forEach { add(MarkKind.NT_DONE to it) }
        }
        if (rows.isEmpty()) return PrivacyClearItem("marks", ok = true, detail = "无需要清除的标记")
        var failed = 0
        for ((kind, ref) in rows) {
            if (marksRepository.setMark(kind, ref, false).let { it !is com.furina.classtools.data.marks.MarksUpdateResult.Success }) {
                failed += 1
            }
        }
        return if (failed == 0) {
            PrivacyClearItem("marks", ok = true, detail = "已取消 ${rows.size} 项标记")
        } else {
            PrivacyClearItem(
                "marks",
                ok = false,
                detail = "共 ${rows.size} 项，失败 $failed 项（已进入本机待补发）",
            )
        }
    }

    private suspend fun clearFitRecs(sid: String): PrivacyClearItem {
        val path = "fitrecs?select=*&acc=eq.${URLEncoder.encode(sid, "UTF-8")}&arch=eq.false&order=ts.desc"
        val rows = when (val result = api.get(path)) {
            is ApiResult.Failure -> return PrivacyClearItem(
                "fitrecs",
                ok = false,
                detail = "读取失败：${result.error.displayMessage}",
            )

            is ApiResult.Success -> DefaultFitRecsRepository.parseRows(json, result.value)
                ?: return PrivacyClearItem("fitrecs", ok = false, detail = "数据格式异常")
        }
        val targets = rows.filterNot { it.arch }
        if (targets.isEmpty()) return PrivacyClearItem("fitrecs", ok = true, detail = "无历史记录")
        var failed = 0
        for (row in targets) {
            val patchPath = "fitrecs?id=eq.${URLEncoder.encode(row.id, "UTF-8")}"
            if (api.patch(patchPath, ARCHIVE_BODY) !is ApiResult.Success) failed += 1
        }
        return if (failed == 0) {
            PrivacyClearItem("fitrecs", ok = true, detail = "已归档 ${targets.size} 条历史")
        } else {
            PrivacyClearItem("fitrecs", ok = false, detail = "共 ${targets.size} 条，失败 $failed 条")
        }
    }

    private data class FitRecsLoad(
        val records: List<FitRecord> = emptyList(),
        val error: String? = null,
    )

    private suspend fun loadFitRecs(): FitRecsLoad {
        val sid = currentSid()?.trim().takeUnless { it.isNullOrEmpty() } ?: return FitRecsLoad()
        val path = "fitrecs?select=*&acc=eq.${URLEncoder.encode(sid, "UTF-8")}&arch=eq.false&order=ts.desc"
        return when (val result = api.get(path)) {
            is ApiResult.Failure -> FitRecsLoad(error = result.error.displayMessage)
            is ApiResult.Success -> {
                val rows = DefaultFitRecsRepository.parseRows(json, result.value)
                if (rows == null) {
                    FitRecsLoad(error = "数据格式异常")
                } else {
                    FitRecsLoad(records = DefaultFitRecsRepository.recordsFromRows(rows))
                }
            }
        }
    }

    companion object {
        const val EXPORT_FORMAT: String = "privacy-export-v1"
        const val USERDATA_PATH: String = "userdata"
        const val PREFER_UPSERT: String = "return=representation,resolution=merge-duplicates"
        const val ARCHIVE_BODY: String = """{"arch":true}"""
    }
}

// ---------------------------------------------------------------- 纯函数构建（可单测）

/**
 * 导出 JSON 构建（纯函数）：
 * - 身份只含脱敏学号/姓名/角色/徽章/管理员标记，**不含 token**；
 * - userdata 白名单见 [WHITELIST]（**power 电费不导出**、tt 课表快照不导出、roster 名单原文清空）；
 * - marks 只导出 `true` 项的 ref；fitrecs 导出记录摘要，读取失败时附 error 且不阻断导出。
 */
object PrivacyExportBuilder {

    /** 可导出的 userdata section 白名单（顺序即 JSON 键序）。 */
    val WHITELIST: List<String> = listOf(
        "homeTools", "fit", "draw", "group", "roster", "hide", "prefs", "nick", "water",
    )

    /** 明确不导出的 section：`power`（电费，隐私红线）、`tt`（班级课表快照，非个人且体积大）。 */
    val EXCLUDED: List<String> = listOf("power", "tt")

    private val stampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.CHINA)
    private val isoFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    /** 学号脱敏：`2024000001` → `20***01`；短学号 → `***`。 */
    fun maskSid(sid: String?): String {
        val value = sid?.trim().orEmpty()
        return when {
            value.length <= 4 -> "***"
            else -> value.take(2) + "***" + value.takeLast(2)
        }
    }

    fun fileName(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val stamp = stampFormatter.format(Instant.ofEpochMilli(nowMillis).atZone(zone))
        return "我的数据_$stamp.json"
    }

    /** 按白名单过滤 userdata；roster 清空 `rollNames`（名单原文不导出）。 */
    fun filteredSections(sections: JsonObject): JsonObject {
        val out = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
        WHITELIST.forEach { key ->
            val value = sections[key] ?: return@forEach
            if (key == "roster") {
                val roster = value.asObjectOrNull() ?: return@forEach
                val sanitized = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>(roster)
                sanitized["rollNames"] = JsonPrimitive("")
                out[key] = JsonObject(sanitized)
            } else {
                out[key] = value
            }
        }
        return JsonObject(out)
    }

    fun build(
        session: Session,
        sid: String,
        sections: JsonObject,
        marks: MarksSnapshot,
        fitRecords: List<FitRecord>?,
        fitError: String?,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val exportedAt = isoFormatter.format(Instant.ofEpochMilli(nowMillis).atZone(zone))
        val root = buildJsonObject {
            put("app", "班级工具站 Android")
            put("format", DefaultPrivacyRepository.EXPORT_FORMAT)
            put("version", 1)
            put("exportedAt", exportedAt)
            put("exportedAtMs", nowMillis)
            putJsonObject("identity") {
                put("sidMasked", maskSid(sid))
                put("name", session.name)
                put("role", session.role)
                put("badge", session.badge)
                put("isAdmin", session.isAdmin)
            }
            put(
                "userdata",
                buildJsonObject {
                    filteredSections(sections).forEach { (key, value) -> put(key, value) }
                },
            )
            putJsonObject("marks") {
                put("hw", marksSummary(marks.hwDone))
                put("nt", marksSummary(marks.ntPin))
                put("ntdone", marksSummary(marks.ntDone))
            }
            if (fitRecords != null) {
                putJsonObject("fitrecs") {
                    put("count", fitRecords.size)
                    putJsonArray("records") {
                        fitRecords.forEach { record -> add(fitRecordJson(record)) }
                    }
                }
            } else {
                putJsonObject("fitrecs") {
                    put("count", 0)
                    put("error", fitError ?: "无法读取体测历史")
                }
            }
            putJsonArray("notes") {
                add(JsonPrimitive("不含 token；学号已脱敏；电费数据不导出；名单原文与课表快照不导出"))
                add(JsonPrimitive("本文件由本人主动导出，仅保存在本机或由本人分享"))
            }
        }
        return NetJson.encodeToString(JsonObject.serializer(), root)
    }

    private fun marksSummary(map: Map<String, Boolean>): JsonObject = buildJsonObject {
        val trueRefs = map.filterValues { it }.keys.sorted()
        put("count", trueRefs.size)
        putJsonArray("refs") { trueRefs.forEach { add(JsonPrimitive(it)) } }
    }

    private fun fitRecordJson(record: FitRecord): JsonObject = buildJsonObject {
        put("id", record.id)
        put("ts", record.ts)
        put("name", record.name)
        put("sex", record.sex)
        put("gradeBucket", record.gradeBucket)
        record.total?.let { put("total", it) } ?: put("total", JsonNull)
        put("grade", record.grade)
        putJsonObject("fields") {
            put("h", record.fields.h)
            put("w", record.fields.w)
            put("vc", record.fields.vc)
            put("r50", record.fields.r50)
            put("sit", record.fields.sit)
            put("jump", record.fields.jump)
            put("pull", record.fields.pull)
            put("durM", record.fields.durM)
            put("durS", record.fields.durS)
        }
    }

    /** marks 总条数（只统计 true 项）。 */
    fun countMarks(marks: MarksSnapshot): Int =
        marks.hwDone.count { it.value } + marks.ntPin.count { it.value } + marks.ntDone.count { it.value }
}


