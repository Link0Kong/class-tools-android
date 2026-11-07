# `docs/api/FITRECS_DRAW_FLOW_IMPL.md` — fitrecs 云端读写 + 抽签 STEP2/STEP3 联动（task-13）

> 状态：**已实现**（compile + 单测 + 预览；接线在 AppContainer/MainNavHost 两处）。
> 契约依据：`docs/api/ENDPOINTS.md` E14/E15/E16（fitrecs）、E14 的 `data` JSON 字符串二次 parse；`export_card_v2_spec.md`（三种块：text / chips / groups）。
> 本轮**不改**抽签核心算法、不改其它页面/主题/构建文件。

---

## 1. 文件清单

### 新增 `data/fitrecs/`

| 文件 | 内容 |
|---|---|
| `FitRecsRepository.kt` | `FitRecsRow`（id/acc/data/arch/ts，`data` 二次 parse 成 `FitRecord`）、`FitRecsLoadResult`、`FitRecsWriteResult`、`FitRecsRepository` 接口 + `DefaultFitRecsRepository` 实现：<br>• GET `fitrecs?select=*&acc=eq.<sid>&arch=eq.false&order=ts.desc`（2min TTL）<br>• POST `fitrecs` + `Prefer: return=representation,resolution=merge-duplicates`<br>• 最多 2 条：保存后对被挤出记录 PATCH `{"arch":true}` 软删<br>• 离线 pending（保存/软删）→ 下次 `load`/`flushPending` 补发<br>• 加载失败降级本机缓存；坏行/坏 data 跳过 |

### 修改 `features/draw/`

| 文件 | 内容 |
|---|---|
| `DrawExport.kt` | 新增 `DrawResultSheetPayload`（STEP3 结果单：chips + 可选 `GroupsBlock` + 剔除重抽记录 `TextBlock`）、`DrawSheetFactory`（纯函数，可单测；文件名 `抽签结果单_<stamp>_...png`、编号 `SHEET-...`）、`DrawResultSheetPayload.toCardSpec()` 组装统一 `ExportCardSpec`；原 `DrawExportPayload`/`DrawExportFactory` 保持不变 |

### 修改 `ui/screens/draw/`

| 文件 | 内容 |
|---|---|
| `DrawScreen.kt` | 新增参数 `onGroup: (DrawResult) -> Unit`、`onExportSheet: (DrawResultSheetPayload) -> Unit`；STEP3 结果卡回调透传 |
| `DrawSections.kt` | `DrawResultCard` 新增「用此结果分组」「导出结果单」按钮（保留「导出抽签图」+「调整参数」「重新抽签」） |

### 修改 `ui/screens/fit/`

| 文件 | 内容 |
|---|---|
| `FitScreen.kt` | `onSave` 改为 `(FitRecord, List<FitRecord>, List<FitRecord>) -> Unit`（新增 dropped，用于软删）；新增 `onRename` / `onDelete` 回调；`LaunchedEffect(initialHistory)` 同步云端加载结果（保存后的 kept 与 initialHistory 一致，不会互相覆盖）；改名/删除确认后回调仓库 |

### 接线 `AppContainer` / `MainNavHost`

- `AppContainer`：新增 `val fitRecsRepository: FitRecsRepository = DefaultFitRecsRepository(api = apiClient, currentSid = { activeSession?.sid })`（**仅此一处**改动）。
- `MainNavHost`：
  - 新增 `var drawGroupMembers by remember { mutableStateOf<List<GroupMember>>(emptyList()) }`（抽签结果 → 分组页共享状态）；
  - `draw` 路由：`onGroup` 将 `DrawResult.entries`（座号+姓名）转为 `GroupMember` 并 `navigate(Routes.tool("group"))`；`onExportSheet` 走统一 `launchExport(payload.toCardSpec())`；
  - `group` 路由：`prevMembers = drawGroupMembers`；
  - `fit` 路由：首屏 `container.fitRecsRepository.load(force=false)` 填充 `initialHistory` + `flushPending()`；`onSave/onRename/onDelete` 调仓库并 Toast「已保存并同步 / 已保存本机，联网后补发」等；导出仍走 `launchExport`。

### 预览 / 测试 / 文档

- `ui/preview/FitrecsDrawFlowPreviews.kt`：4 张 @Preview（37 抽签 STEP3 结果单浅色 / 38 有剔除记录深色 / 39 分组·上一步抽签结果 / 40 体测·云端两条历史）。
- `app/src/test/.../data/fitrecs/FitRecsRepositoryTest.kt`、`app/src/test/.../features/draw/DrawResultSheetTest.kt`。
- 本文件。

---

## 2. 公开 API

### 2.1 fitrecs 仓库

```kotlin
data class FitRecsRow(val id: String, val acc: String = "", val data: String = "",
                      val arch: Boolean = false, val ts: Long = 0L)

sealed interface FitRecsLoadResult {
    data class Success(val records: List<FitRecord>, val fromCache: Boolean) : FitRecsLoadResult
    data class Failure(val message: String) : FitRecsLoadResult
}
sealed interface FitRecsWriteResult {
    data class Success(val synced: Boolean = true) : FitRecsWriteResult   // false = 已写本机 pending
    data class Failure(val message: String) : FitRecsWriteResult
}

interface FitRecsRepository {
    suspend fun load(force: Boolean = false): FitRecsLoadResult
    suspend fun save(record: FitRecord, kept: List<FitRecord>, dropped: List<FitRecord> = emptyList()): FitRecsWriteResult
    suspend fun rename(record: FitRecord): FitRecsWriteResult
    suspend fun archive(id: String): FitRecsWriteResult
    suspend fun pendingCount(): Int
    suspend fun flushPending(): Int
}

class DefaultFitRecsRepository(
    api: ApiClient,
    currentSid: () -> String?,
    clock: () -> Long = System::currentTimeMillis,
    ttlMs: Long = 2 * 60 * 1000,
) : FitRecsRepository
```

路径常量（单测/排障用）：`LOAD_BASE`、`FITRECS_PATH`、`PREFER_UPSERT`、`ARCHIVE_BODY`、`loadPath(sid)`、`archivePath(id)`。

### 2.2 结果单 payload

```kotlin
data class DrawSheetGroup(val members: List<DrawExportChip>)

data class DrawResultSheetPayload(
    val title: String, val subtitle: String, val fileName: String, val recordId: String,
    val config: List<DrawExportField>, val chips: List<DrawExportChip>,
    val groups: List<DrawSheetGroup>, val removeLogLines: List<String>,
    val footer: String, val generatedAtMillis: Long,
) {
    fun toCardSpec(): ExportCardSpec   // ChipsBlock → [GroupsBlock] → [TextBlock(剔除重抽记录)]
}

object DrawSheetFactory {
    fun create(result: DrawResult, groups: List<DrawSheetGroup> = emptyList(),
               nowMillis: Long = System.currentTimeMillis(), randomSuffix: String? = null): DrawResultSheetPayload
    fun removeLogLines(result: DrawResult): List<String>
    fun fileName(config: DrawConfig, stamp: String): String
    fun recordId(stamp: String, suffix: String): String
}
```

### 2.3 页面参数（接线段）

```kotlin
DrawScreen(
    onBack, session,
    onExport: (DrawExportPayload) -> Unit,
    onGroup: (DrawResult) -> Unit = {},                       // 用此结果分组（Lead 接线）
    onExportSheet: (DrawResultSheetPayload) -> Unit = {},     // 导出结果单（Lead 接线）
    ...
)

FitScreen(
    onBack,
    initialHistory: List<FitRecord> = emptyList(),            // fitrecs 云端加载结果
    onSave: (FitRecord, List<FitRecord>, List<FitRecord>) -> Unit = { _, _, _ -> }, // 新记录 + kept + dropped
    onRename: (FitRecord) -> Unit = {},                       // 改名 → POST upsert
    onDelete: (FitRecord) -> Unit = {},                       // 删除 → PATCH arch=true
    ...
)
```

---

## 3. fitrecs 互通行为

- `data` 列是 **JSON 字符串**：`{id,ts,n(名称),sex,gr(年级),fields{h..durS},total,grade}`；`FitCodec.recordFromData/recordToData` 二次 parse/tostring。
- GET 恒带 `arch=eq.false`；仍防御式过滤本机 `arch=true` 行。
- 保存：新记录 POST upsert；`kept` 更新缓存（按 ts 降序、最多 2 条）；`dropped` 逐条 PATCH `{"arch":true}`（无 DELETE 权限）。
- 离线：POST/PATCH 失败 → 写 pending；`load(force=true)` 前 + 页面进入时 `flushPending()` 补发；`FitRecsWriteResult.Success.synced=false` 让 UI 提示「已保存本机，联网后补发」。
- 改名：按 id POST upsert 覆盖 `data`（不新增行）；失败进 pending。
- 删除：PATCH arch=true；成功即从本机缓存移除；失败进 pending。

---

## 4. 抽签 STEP2/STEP3 联动

### 4.1 用此结果分组（STEP2）

- STEP3 结果卡新增「用此结果分组」按钮；
- `DrawResult.entries`（座号 + 可选姓名）→ `GroupMember(seat, name)` → MainNavHost 共享状态 `drawGroupMembers` → `navigate(Routes.tool("group"))`；
- 分组页 `GroupScreen(prevMembers = drawGroupMembers)` 的「上一步抽签序号」来源即抽签结果（GroupConfig 默认 `source = PREV`）。

### 4.2 结果单（STEP3）

- 「导出结果单」：chips（抽签结果）+ 分组块（若传 `groups`；当前 draw 页不持有分组结果，默认空，分组页自己有分组导出）+ 剔除重抽记录文本块；
- 「导出抽签图」保留原 `DrawExportPayload` 入口（与结果单两个按钮并存）；
- 两个入口最终都走统一 `ExportCardSpec` → `ExportPresenter.renderAndSave`（Lead 的 `launchExport`）。

### 4.3 导航方案评估（只写结论，不改代码）

**结论：本轮用「NavHost 内共享状态」**（`MainNavHost` 顶部 `var drawGroupMembers by remember`），**不推荐 navArgument 传 JSON**。

| 方案 | 优点 | 缺点 | 结论 |
|---|---|---|---|
| **A. 共享状态（NavHost 级 `remember`）** | 类型安全（直接传 `List<GroupMember>`）；零编解码；无 URL 长度/转义问题；实现 1 处、易测 | 进程重建/淡出后台后丢失（导航回退仍保留，因为状态在 NavHost 组合内）；不能深链直达分组结果 | ✅ **采用**（本轮只需「抽签→分组」单向前进，且 70 人姓名 JSON 体积不可控） |
| B. navArgument JSON | 可深链、可恢复；Navigation 参数天然随 backstack | 需额外编解码器 + URL encode；70 人姓名 JSON 可能数 KB，`NavType.StringType` 路由参数有长度/字符转义风险；`DrawResult` 含 log/config 全量序列化成本高；预览/测试需构造 JSON | ❌ 不采用（本轮） |
| C. 共享 ViewModel（NavBackStackEntry / activity scoped） | 类型安全 + 可跨页双向（分组结果回传抽签页）+ 旋转恢复 | 需新增 ViewModel 依赖/生命周期样板；本轮 Lead 只允许改 AppContainer/MainNavHost 两处接线，新增 ViewModel 会扩大改动面 | ⚠️ 后续若要做「分组结果回抽签页出结果单」再引入（推荐用 NavHost 级 SavedStateHandle 或 activity-scoped ViewModel） |

**后续建议**：当需要「分组完成 → 回抽签 STEP3 结果单含分组块」时，升级为方案 C（activity-scoped 共享 ViewModel 或 NavHost 级 `SavedStateHandle` 保存 `drawGroupMembers` / `groupResult`）；仍不建议 JSON navArgument（隐私数据体积 + 转义风险）。

---

## 5. 单测

结果（2026-11-08 00:47，全仓最新一次运行）：

```text
BUILD SUCCESSFUL — :app:testDebugUnitTest
CLASSES=33  TOTAL=426  FAIL=0  ERR=0  SKIP=0
```

本轮新增 **22 个用例全部通过**：

| 测试类 | tests | failures |
|---|---|---|
| `data.fitrecs.FitRecsRepositoryTest` | 14 | 0 |
| `features.draw.DrawResultSheetTest` | 8 | 0 |
| **本任务合计** | **22** | **0** |

报告：`app/build/reports/tests/testDebugUnitTest/index.html`；XML：`app/build/test-results/testDebugUnitTest/`。
`:app:compileDebugKotlin` 同一流程通过；`FitrecsDrawFlowPreviewsKt.class` 已产出。

覆盖点：

| 测试类 | 覆盖 |
|---|---|
| `data.fitrecs.FitRecsRepositoryTest` | GET 路径/字段/排序/最多 2 条；arch 行与坏 data 跳过；空数组合法；非数组失败；TTL；网络失败降级缓存；POST upsert body/Prefer/acc/data；被挤出记录 PATCH arch；保存失败 pending + flush；改名 upsert；软删成功移出缓存 / 失败 pending；pending 计数；纯解析/body 组装 |
| `features.draw.DrawResultSheetTest` | 结果单标题/文件名/编号；chips 与参数区；剔除重抽文本（与抽签图同口径）；`toCardSpec` 块顺序（chips → 剔除文本）；无剔除只有 chips；已分组插入 `GroupsBlock`（chips 在前）；空分组不生成块；允许重复/降序文件名 |

---

## 6. 遗留 / 注意

1. **结果单的分组块**：当前「抽签→分组」是单向前进（分组页自己有 `GroupExport`），draw 页拿不到 `GroupResult`，所以结果单默认无 groups；如需在抽签结果单里带分组，按 §4.3 方案 C 升级。
2. **体测历史**：`FitScreen` 首屏加载在 MainNavHost 路由内 `LaunchedEffect`；旋转会重新读仓库（2min TTL 内 0 请求）。改名/删除 Toast 由 MainNavHost 统一给。
3. **fitrecs 补发**：pending 队列为内存态（进程被杀即丢）；本轮不做落盘 pending（与 marks 写失败重试不同，体测保存低频；后续可复用 UserData 的 pending 思路）。
4. **数据隐私**：fitrecs 只含体测成绩与自定义名称，无学号/手机号；导出卡不含姓名（自定义名也不进导图）。
5. **E2E**：`draw` 结果页应可见「导出抽签图 / 导出结果单」与「用此结果分组」；点分组进入分组页且成员为抽签结果；`fit` 保存后 Toast 文案随联网状态变化。
