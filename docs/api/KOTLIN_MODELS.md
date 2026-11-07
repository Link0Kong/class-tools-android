# `docs/api/KOTLIN_MODELS.md` — Android 数据模型（kotlinx.serialization）

> 配套：`api/ENDPOINTS.md`（接口）、`api/USERDATA_V6.md`（v6 blob）。
> 约定：① 数据库列名 = JSON 字段名（`@SerialName` 标 snake_case）；② 所有可能缺省的字段给默认值 + `ignoreUnknownKeys=true`，保证云端加列不崩；③ 时间：`ts`/`edited_at`/`checked_at` 为 **epoch ms（Long）**，`updated_at`/`ts`(power_history)/`syncedAt` 为 **ISO 字符串**（`String` 或 `Instant` 自定义序列化器），`visits.slot` 为本地时间字符串。
> `[已核实]` 字段均来自 DDL / 两端解析代码。

---

## 1. 序列化与配置

```kotlin
val NetJson = Json {
    ignoreUnknownKeys = true      // 云端加列不崩
    explicitNulls = true
    encodeDefaults = true
    coerceInputValues = true      // null → 默认值（防后端某列为 null）
}

object ApiConfig {
    const val REST_BASE = "https://tool-d4gosudok46e0e081.api.tcloudbasegateway.com/v1/rdb/rest/"
    const val TT_LEGACY_URL = "https://tool.furina.fun/tt.json"   // 2026-11-07 起 DNS 已暂停，仅兜底
    const val HEADER_TOKEN_WEB = "X-Device-Id"    // 网页头（服务端也认）
    const val HEADER_TOKEN_APP = "X-Class-Token"  // Android 统一用这个
    // PUBLISHABLE_KEY 从 BuildConfig/本地配置读取：公开 JWT（role=anon），禁止把服务端高权限 key 打进 APK
    val PUBLISHABLE_KEY: String = BuildConfig.CB_PUBLISHABLE_KEY
}
```

> Key 的来源：网页 `WEB:1845`、MP `utils/cloud-config.js:26`。本契约不复制其值。

---

## 2. 身份与 RPC

```kotlin
@Serializable
data class ClassVerifyReq(
    @SerialName("p_sid") val sid: String,
    @SerialName("p_name") val name: String
)

@Serializable
data class ClassVerifyResp(
    val ok: Boolean = false,
    val token: String = "",
    val sid: String = "",
    val name: String = "",
    val role: String = "",
    val badge: String = "",
    @SerialName("is_admin") val isAdmin: Boolean = false,
    val reason: String = ""
)
// 注意：响应可能是对象或单元素数组；用 NetJson.decodeFromString<ClassVerifyResp>
//      失败时再尝试 List<ClassVerifyResp>（MP 兼容做法，MP/cloud.js:223）。

@Serializable
data class ClassMember(
    val seat: Int = 0,          // 1..70
    val sid: String = "",
    val name: String = "",
    val role: String = "",      // 非空 = 班委
    val badge: String = "",
    @SerialName("is_admin") val isAdmin: Boolean = false
)

@Serializable
data class SetRoleReq(
    @SerialName("p_sid") val sid: String,
    @SerialName("p_role") val role: String,
    @SerialName("p_badge") val badge: String = if (role.isNotBlank()) "班委" else ""
)

/** 本机身份（加密存储；结构 = 网页 localStorage.zq_class_identity） */
@Serializable
data class Identity(
    val token: String,
    val sid: String,
    val name: String,
    val role: String = "",
    val badge: String = "",
    @SerialName("is_admin") val isAdmin: Boolean = false,
    val ts: Long = 0L            // 签发/登录时刻；<29 天自动登录（WEB:8880-8885）
)
```

---

## 3. 表 DTO

### 3.1 作业 `public.homework` `[已核实 schema.sql:6-42; WEB:1988-2016]`

```kotlin
@Serializable
data class HomeworkRow(
    val id: String,
    val subject: String = "",
    val content: String = "",
    val due: String = "",
    val date: String = "",          // YYYY-MM-DD
    val time: String = "",          // HH:MM 可空
    val author: String = "",
    val owner: String = "",         // 发布者学号
    val attachments: String = "",   // JSON 字符串：[{n,t,s,d(base64)}]
    val done: Boolean = false,      // 前端忽略（完成在 marks）
    val archived: Boolean = false,
    val ts: Long = 0L
)

@Serializable
data class HomeworkAttachment(val n: String, val t: String = "", val s: Long = 0, val d: String = "")
```

### 3.2 通知行 `public.notices` `[已核实 schema.sql:47-72; override DDL]`

```kotlin
@Serializable
data class NoticeRow(
    val id: String,
    val title: String = "",
    val body: String = "",
    val pin: Boolean = false,        // 前端忽略（置顶在 marks）
    val imp: Boolean = false,
    val author: String = "",
    val owner: String = "",
    val attachments: String = "",
    val archived: Boolean = false,
    val ts: Long = 0L,
    val audience: String = "all",    // all | committee | league | sid:<学号> | admin
    @SerialName("override_json") val overrideJson: String = "",
    val hidden: Boolean = false,
    @SerialName("edited_at") val editedAt: Long = 0L,
    @SerialName("edited_by") val editedBy: String = ""
)

/** 轻量签名行（E8，2KB 级） */
@Serializable
data class NoticeSigRow(
    val id: String,
    val ts: Long = 0,
    val hidden: Boolean = false,
    @SerialName("edited_at") val editedAt: Long = 0
)
```

### 3.3 marks

```kotlin
enum class MarkKind { HW, NT, NTDONE }

@Serializable
data class MarkRow(
    val id: String,       // "<sid>|<kind>|<ref>"
    val seat: String,     // = sid
    val kind: String,     // "hw" | "nt" | "ntdone"
    val ref: String,
    val `val`: Boolean = false,   // ⚠️ 列名 val（SQL 保留字 on 不可用）
    val ts: Long = 0L
) {
    companion object {
        fun idOf(sid: String, kind: String, ref: String) = "$sid|$kind|$ref"
    }
}
```

### 3.4 userdata 行

```kotlin
@Serializable
data class UserdataRow(
    val id: String,      // = sid
    val blob: String = "",
    val ts: Long = 0L
)
```

### 3.5 体测记录 `public.fitrecs`

```kotlin
@Serializable
data class FitrecRow(
    val id: String,
    val acc: String = "",     // = sid
    val data: String = "",    // JSON 字符串（FitRecordData）
    val arch: Boolean = false,
    val ts: Long = 0L
)

@Serializable
data class FitRecordData(
    val id: String,
    val ts: Long = 0,
    val n: String = "",                // 记录名称（"体测 11-05 20:00"）
    val sex: String = "m",
    val gr: String = "12",
    val fields: FitFields = FitFields(),
    val total: Double? = null,
    val grade: String = ""
)
```

### 3.6 访问 / 反馈 / 电费 / 课表 / 健康

```kotlin
@Serializable
data class VisitRow(
    val id: String,                // "<sid>|<slot>|<rand>"（web）或 "<sid>|<slot>"（mp）
    val slot: String,              // 本地时间 "YYYY-MM-DD-HH"
    val sid: String = "",
    val name: String = "",
    val did: String = "",          // 8~64 hex 设备码
    val ts: Long = 0L
)

@Serializable
data class FeedbackRow(
    val id: String,
    val content: String,
    val contact: String = "",      // 保留列；两端新提交均为 ""
    val acc: String = "",
    val done: Boolean = false,
    val ts: Long = 0L
)

@Serializable
data class PowerRow(
    val id: String,                 // = roomverify，如 "4-23A---321"
    val label: String = "",
    val building: String = "",
    val room: String = "",
    val roomverify: String = "",
    val quantity: Double? = null,
    val unit: String = "度",
    val low: Boolean = false,
    @SerialName("updated_at") val updatedAt: String = ""   // ISO timestamptz
)

@Serializable
data class PowerHistoryRow(
    val ts: String = "",            // ISO timestamptz
    val quantity: Double? = null
)

@Serializable
data class SiteAssetRow(
    val body: JsonElement? = null,  // jsonb：对象或 JSON 字符串，两种都兼容
    val meta: JsonElement? = null,
    @SerialName("updated_at") val updatedAt: String = ""
)

@Serializable
data class ServiceHealthRow(
    val id: String,
    val name: String = "",
    val kind: String = "local",     // local | cloud | static
    val status: String = "unknown", // ok | warn | down | unknown
    val detail: String = "",
    val meta: JsonElement? = null,
    @SerialName("checked_at") val checkedAt: Long = 0L
)
```

---

## 4. userdata v6 section 强类型（与 `api/USERDATA_V6.md` 对应）

```kotlin
@Serializable
data class UserdataV6(
    val v: Int = 6,
    val at: AtMap = AtMap(),
    val homeTools: List<String> = emptyList(),
    val fit: FitFields = FitFields(),
    val draw: DrawState = DrawState(),
    val group: GroupState = GroupState(),
    val roster: RosterState = RosterState(),
    val hide: HideState = HideState(),
    val tt: TtState = TtState(),
    val prefs: PrefsState = PrefsState(),
    val nick: String = "",
    val power: PowerSection = PowerSection(),
    val water: WaterSection = WaterSection(),
    @SerialName("homeToolsAt") val homeToolsAt: Long = 0L
)

@Serializable
data class AtMap(
    val homeTools: Long = 0, val fit: Long = 0, val draw: Long = 0, val group: Long = 0,
    val roster: Long = 0, val hide: Long = 0, val tt: Long = 0, val prefs: Long = 0,
    val nick: Long = 0, val power: Long = 0, val water: Long = 0
)

@Serializable
data class FitFields(
    val sex: String = "m", val grade: String = "12",
    val h: String = "", val w: String = "", val vc: String = "", val r50: String = "",
    val sit: String = "", val jump: String = "", val pull: String = "",
    val durM: String = "", val durS: String = ""
)

@Serializable
data class DrawState(
    val start: Int = 1, val end: Int = 70, val count: Int = 10,
    val dup: Boolean = false, val excl: String = "", val sort: String = "draw"
)

@Serializable
data class GroupState(
    val source: String = "prev",        // "prev" | "names"
    val mode: String = "groups",        // "groups" | "pergroup"
    val n: Int = 4,
    val shuffle: Boolean = false,
    val sel: List<Int> = emptyList()
)

@Serializable
data class RosterState(
    val rosterSel: List<Int> = emptyList(),
    val rollSel: List<Int> = emptyList(),
    val rollSt: Map<String, String> = emptyMap(),   // 索引 → "in"|"ab"（也见过中文「到」/「假」，读取端兼容）
    val rollNames: String = ""
)

@Serializable
data class HideState(
    val hwHide: Map<String, Boolean> = emptyMap(),
    val ntHide: Map<String, Boolean> = emptyMap()
)

@Serializable
data class TtState(
    val ttView: String = "",           // "week" | "day"
    val ttUseMine: Boolean = false,
    val ttLocal: JsonElement? = null,  // {c:[course],t:termStart} 或 null
    val termStart: String = ""
)

@Serializable
data class PrefsState(
    val hwRemind: Map<String, Boolean> = emptyMap(),
    val ntSub: Boolean = false,
    val ntSubAt: Long = 0,
    val toolOrder: List<String>? = null   // 只有拖过排序才出现
)

@Serializable
data class PowerSection(
    val sel: String = "",     // roomverify
    val at: Long = 0,         // 与 at.power 镜像
    val low: Int = 30         // 合法档位 [10,20,30,40,50,60,80,100]
)

@Serializable
data class WaterSection(
    val rooms: Map<String, WaterRoom> = emptyMap()
)

@Serializable
data class WaterRoom(
    val p: Int = 4,           // 4 | 6
    val recs: List<WaterRec> = emptyList()
)

@Serializable
data class WaterRec(
    val m: String,            // "YYYY-MM"
    val r: Double,            // 吨，2 位小数
    val ts: Long = 0
)
```

> **实现建议**：落库与合并用 `JsonObject`（见 `api/USERDATA_V6.md` §5），上面的 data class 用于 UI 绑定；两者通过 `BlobJson.decodeFromJsonElement<UserdataV6>(jsonObject)` 互转。注意 `tt.ttLocal` 与 `water` 的规范化必须在 decode 后做一次 `normalize()`（水表月份/读数、`p∈{4,6}`、≤120 条、排序）。

---

## 5. 业务模型（客户端本地，不落云端）

### 5.1 抽签 / 分组

```kotlin
enum class SortMode { DRAW_ORDER, ASC, DESC, RANDOM }   // 网页 A.cfg.sort 展示用

data class DrawResult(
    val result: List<Int> = emptyList(),
    val pool: List<Int> = emptyList(),
    val log: List<RemoveLog> = emptyList(),             // 剔除重抽记录
    val removed: List<Int> = emptyList(),
    val config: DrawConfig? = null
)
data class DrawConfig(
    val range: String = "", val rangeF: String = "", val count: String = "",
    val dup: String = "否", val excl: String = "无", val sort: String = "按抽取顺序",
    val pool: String = "", val rosterN: Int = 0
)
data class RemoveLog(val r: Int, val add: List<Int> = emptyList())

data class GroupResult(
    val groups: List<List<MemberRef>> = emptyList(),
    val config: GroupConfig? = null
)
data class MemberRef(val n: Int?, val nm: String = "")   // 座号 + 姓名（chips/groups 通用）
data class GroupConfig(
    val source: String = "", val mode: String = "", val total: String = "",
    val groups: String = "", val inner: String = ""
)
```

### 5.2 体测

```kotlin
enum class FitSex { M, F }
enum class FitGrade { G12, G34 }

data class FitRow(val n: String, val sub: String, val wt: Int,
                  val v: String, val s: Int?, val p: Int = 0)
data class FitCalc(
    val rows: List<FitRow>, val std: Double, val plus: Double, val total: Double,
    val grade: String, val miss: Int, val wtDone: Int,
    val sex: String, val gr: String, val h: Double?, val w: Double?,
    val bmi: Double?, val bcat: String
)
// 阈值表：FTS/FTP/FTBMI/FTB 直接移植 WEB:7523-7564（纯常量）
```

### 5.3 课表

```kotlin
@Serializable
data class Course(
    val name: String, val teacher: String = "", val room: String = "",
    val day: Int, val start: Int, val end: Int,
    val weeks: String = "", val parity: String = "all"   // all | odd | even
)
@Serializable
data class TtSnapshot(
    val c: List<Course> = emptyList(),
    val t: String = "",                                  // 开学日 YYYY-MM-DD
    val meta: TtMeta? = null
)
@Serializable
data class TtMeta(
    val source: String = "", val fetchedAt: Long = 0, val count: Int = 0,
    val termStart: String = "", val syncedAt: String = ""
)
data class SessionInfo(  // 「今天/下一节」计算结果（MP/utils/ttplan.js）
    val name: String, val room: String, val start: Int, val end: Int,
    val startText: String, val endText: String, val span: String,
    val startAt: Long, val endAt: Long, val state: String   // next|ongoing|done
)
```

### 5.4 电费 / 水量

```kotlin
data class RoomInfo(val rv: String, val label: String, val building: String, val room: String)
data class PowerPoint(val q: Double, val t: Long)          // 采样（q=剩余电量, t=epoch ms）

data class PowerEstimate(
    val valid: Boolean = false, val rate: Double? = null, val days: Double? = null,
    val q: Double? = null, val slow: Boolean = false, val ref: Boolean = false,
    val nSeg: Int = 0, val changes: Int = 0, val lastChange: Long? = null,
    val lastRecharge: Recharge? = null, val v24: Boolean = false, val v7: Boolean = false
) { data class Recharge(val t: Long, val reset: Double) }

data class DeductEvent(val t: Long, val q: Double, val deg: Double, val water: Boolean,
                       val fee: Double? = null, val m: String = "")

data class WaterHistoryRow(
    val m: String, val r: Double, val usage: Double?, val excess: Double?,
    val fee: Double?, val deg: Double?, val within: Boolean
)
```

### 5.5 通知卡片（原生渲染模型）

```kotlin
data class NoticeCard(
    val gid: String,
    val title: String,
    val cat: String,
    val source: String = "",
    val groupName: String = "",
    val senderName: String = "", val senderRole: String = "", val senderBadge: String = "",
    val statusLabel: String = "", val statusCls: String = "", val expired: Boolean = false,
    val timeLabel: String = "",
    val summary: String = "",
    val points: List<String> = emptyList(),
    val segs: List<NoticeSeg> = emptyList(),     // {type, cls, text}
    val items: List<NoticeItem> = emptyList(),
    val quotes: List<NoticeQuote> = emptyList(),
    val recurring: Boolean = false, val recurLabel: String = "", val nextText: String = "",
    val hidden: Boolean = false, val updated: Boolean = false,
    val canDone: Boolean = true,
    val deadlineTs: Long? = null
)

sealed interface NoticeItem {
    val key: String
    data class File(override val key: String, val name: String, val extKind: String,
                    val meta: String, val hasUrl: Boolean, val pending: Boolean,
                    val archiveOk: Boolean, val archiveCount: Int,
                    val archiveSummary: String, val archiveFiles: List<ArchiveFile>) : NoticeItem
    data class Form(override val key: String, val name: String, val statusText: String,
                    val closed: Boolean, val closeText: String, val note: String,
                    val hasUrl: Boolean) : NoticeItem
    data class Link(override val key: String, val kind: String, val name: String,
                    val title: String, val account: String, val host: String,
                    val hasUrl: Boolean) : NoticeItem
    data class Merged(override val key: String, val name: String, val count: Int,
                      val messages: List<MergedMessage>) : NoticeItem
}
data class ArchiveFile(val name: String, val extKind: String, val extText: String,
                       val size: String, val hasUrl: Boolean)
data class MergedMessage(val key: String, val fromName: String, val fromBadge: String,
                         val time: String, val text: String)
data class NoticeQuote(val key: String, val label: String, val fromName: String,
                       val fromBadge: String, val time: String, val text: String,
                       val truncated: Boolean)
data class NoticeSeg(val type: String, val cls: String, val text: String)
data class NoticeImage(val caption: String, val ocr: Boolean, val index: Int)
```

---

## 6. 错误与结果包装

```kotlin
sealed interface ApiResult<out T> {
    data class Ok<T>(val data: T) : ApiResult<T>
    data class Http(val status: Int, val code: String?, val message: String?) : ApiResult<Nothing>
    data class Network(val cause: Throwable?, val message: String?) : ApiResult<Nothing>
    data class Unauthorized : ApiResult<Nothing>       // 401 → 清身份回登录
}

@Serializable
data class PostgrestError(
    val code: String? = null,
    val message: String? = null,
    @SerialName("requestId") val requestId: String? = null
)
```

---

## 7. Repository 接口建议

```kotlin
interface ClassRepository {
    // 身份
    suspend fun verify(sid: String, name: String): ApiResult<ClassVerifyResp>
    suspend fun members(): ApiResult<List<ClassMember>>
    suspend fun setRole(sid: String, role: String): ApiResult<Unit>

    // 数据
    suspend fun homework(force: Boolean = false): ApiResult<List<HomeworkRow>>
    suspend fun publishHomework(row: HomeworkRow): ApiResult<Unit>
    suspend fun archiveHomework(id: String): ApiResult<Unit>

    suspend fun notices(force: Boolean = false): ApiResult<List<NoticeRow>>   // 内部先走签名
    suspend fun adminEditNotice(gid: String, fields: JsonObject): ApiResult<Unit>
    suspend fun adminHideNotice(gid: String, hidden: Boolean): ApiResult<Unit>

    suspend fun marks(force: Boolean = false): ApiResult<List<MarkRow>>
    suspend fun setMark(kind: String, ref: String, value: Boolean): ApiResult<Unit>

    suspend fun userdata(force: Boolean = false): ApiResult<UserdataV6?>
    suspend fun pushUserdata(): ApiResult<Unit>          // 内部 GET+merge+POST

    suspend fun fitrecs(): ApiResult<List<FitrecRow>>
    suspend fun saveFitrec(row: FitrecRow): ApiResult<Unit>
    suspend fun deleteFitrec(id: String): ApiResult<Unit>

    suspend fun logVisit(): ApiResult<Unit>              // 每小时一次，失败静默
    suspend fun sendFeedback(content: String): ApiResult<Unit>

    suspend fun powerRow(rv: String): ApiResult<PowerRow?>
    suspend fun powerHistory(rv: String, range: String): ApiResult<List<PowerHistoryRow>>

    suspend fun ttSnapshot(): ApiResult<TtSnapshot>      // site_assets 优先 → tt.json 兜底 → 缓存
}
```

> 同步编排（TTL/防抖/outbox）建议独立成 `SyncEngine`（见主文档 §10），Repository 只负责单次请求与解析。
