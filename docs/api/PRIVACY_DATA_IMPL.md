# `docs/api/PRIVACY_DATA_IMPL.md` — 数据与隐私页实现（task-15）

> 状态：**已实现**（compile + 单测 + 预览；接线由 Lead 在 AppContainer / Profile / MainNavHost 完成）。
> 合规依据：`docs/PRIVACY_COMPLIANCE.md` 待办 #1（清除云端编排）/#2（导出我的数据 JSON）；`web/AGENTS.md` §0 隐私红线；`docs/api/ENDPOINTS.md`（userdata E13 / marks E11 / fitrecs E15/E16）。
> 约束：**不改** MainNavHost / AppContainer / Profile / 构建文件；不新增权限、不改 manifest/FileProvider（复用 `${applicationId}.fileprovider` 与 `cache/exports`）。

---

## 1. 文件清单

### 数据层 `data/privacy/`

| 文件 | 内容 |
|---|---|
| `PrivacyRepository.kt` | ① `PrivacySummary` / `PrivacyExport` / `PrivacyExportResult`；② 清除结果 `PrivacyClearItem` / `PrivacyClearReport` / `PrivacyClearCloudResult` / `PrivacyLocalResult`；③ `PrivacyLocalDataCleaner` 接口 + `CallbackLocalCleaner`（组合步骤，顺序执行并聚合失败）；④ `PrivacyUserDataAccess` 接口 + `RepositoryPrivacyUserDataAccess`（读当前 sections / 清除成功后重置本机基线）；⑤ `PrivacyRepository` + `DefaultPrivacyRepository`；⑥ `PrivacyExportBuilder` 纯函数（脱敏、白名单、JSON 组装） |

### 页面 `ui/screens/data/`

| 文件 | 内容 |
|---|---|
| `DataPrivacyScreen.kt` | 顶部 `CtTopBar` + 隐私说明卡 + 数据概览 + 数据管理（导出/清除云端/清除本机）+ 隐私政策/用户协议回看 + 页脚账号删除说明；四态（加载/错误/未登录/正常）；二次确认弹层；导出文件写 `cache/exports` + FileProvider `application/json` 系统分享 |

### 预览 / 测试

- `ui/preview/DataPrivacyPreviews.kt`：**5 张 @Preview**（41 正常浅色 / 42 清除云端确认深色 / 43 清除本机确认浅色 / 44 操作结果反馈 / 45 错误态深色）。
- `app/src/test/.../data/privacy/PrivacyRepositoryTest.kt`：**16 用例**。

---

## 2. 公开 API

```kotlin
// 概览 / 导出
data class PrivacySummary(val userdataSections: Int = 0, val marksCount: Int = 0, val fitRecsCount: Int = 0)
data class PrivacyExport(val json: String, val fileName: String, val generatedAt: Long, val summary: PrivacySummary)
sealed interface PrivacyExportResult {
    data class Success(val export: PrivacyExport) : PrivacyExportResult
    data class Failure(val message: String) : PrivacyExportResult
}

// 清除
data class PrivacyClearItem(val name: String, val ok: Boolean, val detail: String = "")
data class PrivacyClearReport(val items: List<PrivacyClearItem>) {
    val allOk: Boolean
    val failed: List<PrivacyClearItem>
    val message: String   // 全部成功 / 部分失败（列出项与原因）
}
sealed interface PrivacyClearCloudResult {
    data class Done(val report: PrivacyClearReport) : PrivacyClearCloudResult
    data class Failure(val message: String) : PrivacyClearCloudResult
}
data class PrivacyLocalResult(val ok: Boolean, val message: String, val clearedItems: Int = 0)

// 注入接口
interface PrivacyLocalDataCleaner { suspend fun clearLocalData(): PrivacyLocalResult }
class CallbackLocalCleaner(steps: List<Step>) : PrivacyLocalDataCleaner   // Step(name, clear)
interface PrivacyUserDataAccess {
    suspend fun currentSections(): JsonObject
    suspend fun resetToDefaults()
}
class RepositoryPrivacyUserDataAccess(repository: UserDataRepository) : PrivacyUserDataAccess

// 仓库
interface PrivacyRepository {
    suspend fun loadSummary(): PrivacySummary
    suspend fun buildExport(): PrivacyExportResult
    suspend fun clearCloudData(): PrivacyClearCloudResult
    suspend fun clearLocalData(): PrivacyLocalResult
}
class DefaultPrivacyRepository(
    api: ApiClient,
    marksRepository: MarksRepository,
    userDataAccess: PrivacyUserDataAccess,
    currentSid: () -> String?,
    currentSession: () -> Session?,
    localCleaner: PrivacyLocalDataCleaner,
    clock: () -> Long = System::currentTimeMillis,
    json: Json = NetJson,
) : PrivacyRepository

// 纯函数
object PrivacyExportBuilder {
    val WHITELIST: List<String>      // homeTools, fit, draw, group, roster, hide, prefs, nick, water
    val EXCLUDED: List<String>       // power（电费）, tt（课表快照）
    fun maskSid(sid: String?): String          // 2024000001 → 20***01
    fun fileName(nowMillis: Long): String      // 我的数据_YYYYMMDD-HHmm.json
    fun filteredSections(sections: JsonObject): JsonObject   // 白名单 + roster.rollNames 清空
    fun build(session, sid, sections, marks, fitRecords, fitError, nowMillis): String
    fun countMarks(marks: MarksSnapshot): Int
}

// 页面
@Composable
fun DataPrivacyScreen(
    onBack: () -> Unit,
    session: Session?,
    repository: PrivacyRepository?,
    modifier: Modifier = Modifier,
    skipLoad: Boolean = false,
    initialSummary: PrivacySummary? = null,
    initialFeedback: String? = null,
    initialDialog: DataPrivacyDialog = DataPrivacyDialog.NONE,  // NONE/CLEAR_CLOUD/CLEAR_LOCAL/POLICY/AGREEMENT
    forcedErrorMessage: String? = null,
)
```

---

## 3. 行为与契约

### 3.1 导出 JSON（`buildExport`）

- 身份：`sidMasked`（`20***01`）、姓名、角色、徽章、`isAdmin`；**不含 token、不含学号明文**。
- `userdata`：白名单 9 个 section —— `homeTools/fit/draw/group/roster/hide/prefs/nick/water`；
  - **`power`（电费）不导出**（web 红线：电费不导出/不分享）；
  - **`tt`（班级课表快照）不导出**（非个人数据且体积大）；
  - `roster.rollNames`（名单原文）清空为空串，保留选择/点名状态。
- `marks`：只导出 `true` 项的 ref 与 count（`hw/nt/ntdone`），不含学号（服务端 id 内的 sid 不在 ref 中）。
- `fitrecs`：本人体测记录（id/ts/名称/性别/年级/总分/等级/9 项成绩）；**读取失败不阻断导出**，写 `fitrecs.error` 并在摘要中 count=0。
- 输出：紧凑 JSON（含 `app/format/version/exportedAt/exportedAtMs/notes`），文件名 `我的数据_YYYYMMDD-HHmm.json`。
- 纯本机生成，**离线可导出**（fitrecs 读取走 2min TTL 缓存，失败降级）。

### 3.2 清除云端（`clearCloudData`）

顺序与契约（逐项返回 `PrivacyClearItem`，部分失败仍返回 Done + 可读 message）：

1. **userdata**：`POST userdata` + `Prefer: return=representation,resolution=merge-duplicates`，body `[{"id":<sid>,"blob":<空 v6 blob>,"ts":<now>}]`；空 blob = `UserdataV6.empty()`（v=6、at 全 0、11 section 默认、homeTools=[]）；成功后**重置本机 userdata 基线**（避免下次同步把旧数据写回）。
2. **marks**：先 `refresh(force=true)` 尽力拉云端，再把本机快照中所有 `true` 的行逐条 `setMark(..., false)`（upsert `val=false`，**不 DELETE**）；失败进 marks pending，下次补发。
3. **fitrecs**：`GET fitrecs?select=*&acc=eq.<sid>&arch=eq.false&order=ts.desc`，对每行 `PATCH fitrecs?id=eq.<id>` `{"arch":true}`（**不发 DELETE**）；读取失败则该项失败且不影响其它项。

### 3.3 清除本机（`clearLocalData`）

- 通过注入的 `PrivacyLocalDataCleaner` 执行；推荐 `CallbackLocalCleaner` 步骤（示例见 §4），**保留身份会话**；失败项聚合为可读 message。
- 不做硬编码 Context；不触碰云端。

### 3.4 隐私口径（页面顶部展示）

- 不显示手机号；学号仅脱敏（`20***01`）；
- 页面与导出图不含宿舍号；**电费数据不导出**；
- 导出文件只保存在本机，由本人主动分享；
- 账号注销/数据删除请求：联系班级管理员人工处理（页脚说明）。

---

## 4. 接线点（Lead）

### 4.1 AppContainer 构造

```kotlin
val privacyRepository: PrivacyRepository = DefaultPrivacyRepository(
    api = apiClient,
    marksRepository = marksRepository,
    userDataAccess = RepositoryPrivacyUserDataAccess(userDataRepository),
    currentSid = { activeSession?.sid },
    currentSession = { activeSession },
    localCleaner = CallbackLocalCleaner(
        listOf(
            CallbackLocalCleaner.Step("班级名单缓存") { classMembersRepository.clear() },
            CallbackLocalCleaner.Step("userdata 本机基线") {
                userDataRepository.updateCurrent(UserdataV6.sectionsOf(UserdataV6.empty()))
            },
        ),
    ),
)
```

> `userdata` 本机基线用现有 `updateCurrent(defaults)` 覆盖（`UserDataRepository` 暂无单独 clear 本机 API；如后续加 `clearLocal()` 可替换该步骤）。身份会话保留（退出登录仍走 Profile 的退出）。

### 4.2 Profile 入口

`ProfileScreen` 的「数据与隐私」卡当前为静态行（`导出我的数据 · 待接入`）。建议加参数：

```kotlin
ProfileScreen(
    ...,
    onOpenDataPrivacy: () -> Unit = {},
)
```

在「数据与隐私」卡把 `导出我的数据` 行改成一个 `SettingActionRow`/可点击行 → `onOpenDataPrivacy()`；`MainNavHost` 里：

```kotlin
composable(Routes.DATA_PRIVACY) {
    DataPrivacyScreen(
        onBack = { navController.popBackStack() },
        session = session,
        repository = container.privacyRepository,
    )
}
```

`Routes` 建议：`const val DATA_PRIVACY = "dataPrivacy"`。

### 4.3 导出文件分享依赖

- 复用已有 FileProvider：`${applicationId}.fileprovider` + `@xml/ct_file_paths` 的 `cache-path name="exports" path="exports/"`；
- 页面写 `cacheDir/exports/<我的数据_...>.json` 并 `ACTION_SEND`（`application/json` + `FLAG_GRANT_READ_URI_PERMISSION`）；
- **无需新增权限/manifest 改动**。

---

## 5. 单测

结果（2026-11-08 00:53，全仓最新一次运行）：

```text
BUILD SUCCESSFUL — :app:testDebugUnitTest
CLASSES=34  TOTAL=442  FAIL=0  ERR=0  SKIP=0
```

本轮新增 **16 个用例全部通过**：

| 测试类 | tests | failures |
|---|---|---|
| `data.privacy.PrivacyRepositoryTest` | 16 | 0 |

覆盖：学号脱敏（含短学号/空）；导出文件名；userdata 白名单（power/tt 排除、roster.rollNames 清空）；导出 JSON 不含 token/学号明文、identity 脱敏、marks 摘要、fitrecs 摘要与 notes；fitrecs 读取失败只记 error 不阻断；未登录导出失败；概览数字；清除报告文案；清除云端完整编排（userdata 空 blob v6 + upsert 偏好 + 本机基线重置 + marks 全置 false + fitrecs 逐条 PATCH arch 且无 DELETE）；部分失败可读错误；fitrecs 读取失败不影响其它项；marks 单项失败聚合；未登录清除失败且 0 请求；清除本机委派 cleaner；`CallbackLocalCleaner` 顺序/失败聚合/全成功。

---

## 6. 遗留 / 说明

1. **marks 清除范围**：只清本机快照里 `true` 的行（云端可能存在本机未缓存的旧 true 行；`refresh(force=true)` 已尽量拉全量，若离线则仅清本机快照 + pending）。
2. **userdata 空 blob**：直接 POST 覆盖（不走 merge），因此云端旧 section 会被清空；本机基线随后重置为默认。
3. **清除本机**：由 Lead 注入具体步骤；`UserDataRepository` 暂无独立 clear 本机 API，当前用 `updateCurrent(defaults)` 等价覆盖（后续可加 `clearLocal()` 更彻底）。
4. **导出文件**：Android 10+ 写 cache + 系统分享，不申请存储权限、不保存到相册（JSON 非媒体文件）；用户可另存/转发，离开 App 后流转由用户控制。
5. **账号删除**：合规要求排期 #5，当前依赖班级管理员人工处理（页面页脚已说明），未做自动注销接口。
6. **单元测试依赖**：全部用 FakeTransport/Fake 仓库，不触真实云端（`tests` 中 token 为假值）。
