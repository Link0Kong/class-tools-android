# MP 对齐 P1 实现：反馈建议 + 使用说明 + 我的数据指标卡（task-24）

> 状态：**已交付**（数据/页面/组件/预览/测试/文档）。Lead 负责路由、AppContainer 与 Profile 嵌入；本轮未改 `MainNavHost` / `AppContainer` / `ProfileScreen` / 构建文件。
> 基线 v2.0.0（552/0）；本任务新增 **36 个单测全绿**（feedback 23 + me 13，要求 ≥14）。
> 参考（只读）：`mp-class-tools/pages/fb`（feedback 表 E19/E20/E21）、`pages/ug`（16 节说明）、`pages/me`（我的数据 4 指标）、`utils/cloud.js`。

---

## 1. 交付文件

### 数据/仓库（`features/feedback/`，按 task 写作用域）

| 文件 | 内容 |
|---|---|
| `FeedbackModels.kt` | `FeedbackRow`（id/content/contact/acc/done/ts）、`FeedbackLoadResult`/`FeedbackSubmitResult`/`FeedbackDoneResult`、`FeedbackFilter`、`FeedbackRules`（校验≤500 字/ID 生成/过滤/作者名/时间格式/副标题） |
| `FeedbackRepository.kt` | `FeedbackRepository` 接口 + `DefaultFeedbackRepository`：`GET feedback?select=*&order=ts.desc`（TTL 30s + 失败降级缓存）、`POST feedback`（数组、contact 空串、acc=sid）、`PATCH feedback?id=eq.<id>` `{"done":true}`（失败回退 POST upsert，与 mp 一致）、内存缓存 |

### 页面/组件

| 文件 | 内容 |
|---|---|
| `ui/screens/feedback/FeedbackScreen.kt` | 反馈建议页：表单（≤500 字+计数/校验）、提交中/成功/失败反馈、列表（普通用户「我的反馈」；管理员 全部/未处理/已处理 + 标已处理）、四态（加载/错误重试/空/数据）、小屏单列 / ≥600dp 平板双栏（表单 42% + 列表 58%）、Snackbar |
| `ui/screens/guide/UserGuideScreen.kt` | 使用说明：18 个分节（身份/工作台/抽签/分组/名册点名/作业/通知/课表/体测/电费/水量/提醒/写日程/权限/排序/反馈/隐私/FAQ），编号分节列表，宽屏限宽 760dp；**零网络、零外部链接** |
| `ui/screens/me/MeStatsSection.kt` | 「我的数据」独立组件 + `MeStats` 模型 + `MeStatsBuilder` 聚合/格式化 + `ReminderStatus`（提醒入口状态与文案） |
| `ui/preview/ParityP1Previews.kt` | 8 张预览（79–86）：反馈手机/管理员深色/错误态/平板双栏、使用说明手机/平板深色、我的数据浅色/深色（提醒未授权） |

### 测试

| 文件 | 数量 | 覆盖 |
|---|---|---|
| `test/.../features/feedback/FeedbackRulesTest.kt` | 12 | 空/超长校验、trim、ID 生成、时间格式、普通用户过滤、管理员三档、作者名回退、副标题、档位集合 |
| `test/.../features/feedback/FeedbackRepositoryTest.kt` | 11 | GET 解析与路径/鉴权头、失败降级缓存、无身份失败、POST body（contact 空/done false/ts）、空与超长不发请求、500 可读错误、PATCH 成功与失败回退 POST、TTL 缓存、坏 JSON 容错、body 数组结构 |
| `test/.../ui/screens/me/MeStatsSectionTest.kt` | 13 | marks 聚合待交/已完成、隐藏项排除、通知 marks 不串、负数钳制、数字格式化、电量格式化、可选扩展指标、抽签数、提醒文案四态、默认值 |

---

## 2. 公开签名（Lead 接线）

```kotlin
// ---------- 反馈 ----------
@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
    session: Session?,
    repository: FeedbackRepository?,
    modifier: Modifier = Modifier,
    skipLoad: Boolean = false,                 // 预览/测试
    initialRows: List<FeedbackRow> = emptyList(), // 预览
    forcedErrorMessage: String? = null,        // 预览
    nameOfSid: (String) -> String? = { null }, // 管理员列表作者名映射（用班级名单缓存）
)

class DefaultFeedbackRepository(
    api: ApiClient,
    currentSid: () -> String?,
    clock: () -> Long = System::currentTimeMillis,
    json: Json = NetJson,
    ttlMs: Long = 30_000,
    randomSuffix: () -> String = { FeedbackRules.randomBase36() },
) : FeedbackRepository

interface FeedbackRepository {
    suspend fun load(force: Boolean = false): FeedbackLoadResult
    suspend fun submit(content: String): FeedbackSubmitResult
    suspend fun markDone(id: String): FeedbackDoneResult
    suspend fun cached(): List<FeedbackRow>
}

// ---------- 使用说明 ----------
@Composable fun UserGuideScreen(onBack: () -> Unit, modifier: Modifier = Modifier)
object UserGuideContent { val sections: List<GuideSection>; const val VERSION; fun sectionOf(id) }

// ---------- 我的数据 ----------
@Composable
fun MeStatsSection(
    stats: MeStats,
    reminder: ReminderStatus,
    onOpenReminder: () -> Unit,
    modifier: Modifier = Modifier,
)

data class MeStats(
    val pendingHomework: Int = 0,
    val doneHomework: Int = 0,
    val drawRecords: Int = 0,
    val fitRecords: Int = 0,
    // 可选扩展（有数据才展示，不伪造 0）
    val pendingNotices: Int? = null,
    val classMembers: Int? = null,
    val powerRemaining: Double? = null,
    val powerRoomLabel: String? = null,
)
data class ReminderStatus(
    val enabled: Boolean = false,
    val notificationPermission: Boolean = false,
    val homeworkEnabled: Boolean = true,
    val courseEnabled: Boolean = true,
    val homeworkLeadDays: Int = 1,
    val courseLeadMinutes: Int = 15,
) { val sections; val label; val isWarning }

object MeStatsBuilder {
    fun fromCached(homeworkRows, marks, fitRecords, drawRecords, hiddenHomeworkIds = emptySet()): MeStats
    fun formatCount(value: Int): String
    fun formatPower(remaining: Double?, roomLabel: String? = null): String?
    fun extraMetrics(stats: MeStats): List<Pair<String, String>>
}
```

---

## 3. Lead 接线点

### 3.1 AppContainer（新增仓库，1 行构造）

```kotlin
// AppContainer 内（位置随现有仓库）
val feedbackRepository: FeedbackRepository = DefaultFeedbackRepository(
    api = apiClient,
    currentSid = { activeSession?.sid },
)
```

### 3.2 路由（建议 Routes 常量 + MainNavHost）

```kotlin
// Routes.kt（Lead 加）
const val FEEDBACK = "feedback"
const val GUIDE = "guide"

// MainNavHost.kt
composable(Routes.FEEDBACK) {
    FeedbackScreen(
        onBack = { navController.popBackStack() },
        session = session,
        repository = container.feedbackRepository,
        nameOfSid = { sid ->
            (container.classMembersRepository.cached() as? ClassMembersResult.Success)
                ?.members?.firstOrNull { it.sid == sid }?.name
        },
    )
}
composable(Routes.GUIDE) {
    UserGuideScreen(onBack = { navController.popBackStack() })
}
```

### 3.3 Profile 入口（design 正在改 Profile，**由 Lead/design 嵌入**，本任务不改 Profile）

```kotlin
// 「我的数据」区块（放在身份卡之后；stats 由现有仓库聚合，组件不发网络请求）
var meStats by remember { mutableStateOf(MeStats()) }
var reminderStatus by remember { mutableStateOf(ReminderStatus()) }
LaunchedEffect(session) {
    // 作业：fromCached(homeworkRepository.cached(), marksRepository.marks.value, fitRecs..., draw...)
    // 体测：container.fitRecsRepository.cached().size（或已加载列表）
    // 抽签：本机最近结果条数（可选，无则为 0）
    meStats = MeStatsBuilder.fromCached(
        homeworkRows = container.homeworkRepository.cached(),
        marks = container.marksRepository.marks.value,
        fitRecords = fitRecsCount,
        drawRecords = drawRecordCount,
        hiddenHomeworkIds = emptySet(), // 有 hw 隐藏项时传入
    )
    // 提醒入口状态
    reminderStatus = ReminderStatus(
        enabled = settings.enabled,
        notificationPermission = permissionController.state(PermissionType.NOTIFICATIONS).isGranted,
        homeworkEnabled = settings.homeworkEnabled,
        courseEnabled = settings.courseEnabled,
        homeworkLeadDays = settings.homeworkLeadDays,
        courseLeadMinutes = settings.courseLeadMinutes,
    )
}
MeStatsSection(
    stats = meStats,
    reminder = reminderStatus,
    onOpenReminder = { navController.navigate(Routes.REMINDER_SETTINGS) },
)
// 帮助区：
SettingActionRow(label = "反馈建议", actionText = "查看", onAction = { navController.navigate(Routes.FEEDBACK) })
SettingActionRow(label = "使用说明", actionText = "查看", onAction = { navController.navigate(Routes.GUIDE) })
```

> `reminderSettingsStore.settings` 可用 `collectAsStateWithLifecycle()`；`fitRecsCount` 可传 `fitrecs` 当前列表大小；`drawRecords` 无现成仓库时传 0（组件会显示 0，与 mp 首装口径一致）。

### 3.4 路由/入口清单

| 入口 | 页面 | 备注 |
|---|---|---|
| 我的 → 反馈建议 | `FeedbackScreen` | 普通用户只看自己；管理员可标已处理 |
| 我的 → 使用说明 | `UserGuideScreen` | 纯静态，零网络 |
| 我的 → 我的数据（嵌入） | `MeStatsSection` | 独立组件，含提醒设置入口 |
| 我的 → 提醒设置 | 已有（task-21 数据层） | 从 MeStatsSection 入口进入 |

---

## 4. 与 mp 口径差异（如实说明）

### 反馈（fb）
- **一致**：云端 `feedback` 表 E19/E20/E21；正文 ≤500 字；contact 保留列但新提交固定空串（2026-11-05 起停收）；普通用户只读自己（RLS）；管理员可看全部/未处理/已处理并 PATCH 标记；提交成功提示；四态。
- **差异**：
  1. 管理员列表作者名：mp `cloud.memberName(acc)` 同步映射，Android 由 Lead 注入 `nameOfSid`（用本地班级名单缓存；查不到显示「（未知成员）」与 mp 一致）；
  2. mp 每次 onShow 拉取 + 下拉刷新；Android 仓库 30s TTL 缓存，页面错误态提供「重试」（可传 `force` 扩展下拉刷新，本轮未接）；
  3. mp 的 AI 页面接力（`agent.js` handoff，tab=mine）为小程序 AI 助手专属，**不迁移**（不适用）；
  4. mp 页面无「联系方式」字段（已停收），Android 一致不显示、不收集。
- **隐私**：不显示学号（acc 只在请求体/内部）；不收集联系方式；反馈内容为用户主动输入，仅管理员与本人可读。

### 使用说明（ug）
- mp 16 节（身份/工作台/抽签/名单/分组/作业通知/班委/全部/体测/反馈/点名/电费/通知附件/工作台电费/水量/分享）。
- Android 18 节：按 Android 现有功能重组（合并「作业/通知」为两节、班委并入名册/点名、新增「提醒设置 / 写日程 / 权限管理 / 数据与隐私 / 常见问题」）；**零外部链接**；不写小程序/网页专属路径；无「分享」独立节（Android 导出图片时使用系统分享，已在导出流程内提示）。
- 内容版本 `UserGuideContent.VERSION = 2026-11-08.1`，后续改文案可递增。

### 我的数据（me）
- **一致**：核心 4 指标 = 待交作业 / 已完成作业 / 抽签记录 / 体测记录（mp `pages/me` 实际口径；任务描述里的「访问统计/班级人数/通知/电量」并非 me 页指标，mp 的访问统计是管理员页）。
- **Android 增强**（可选，不伪造）：未处理通知 / 班级人数 / 关注宿舍电量（`MeStats` 可空字段，Lead 有数据才传）。
- **新增提醒入口**：mp 用微信订阅消息，Android 用「提醒设置」入口聚合展示（`ReminderStatus`：未开启 / 通知权限未开启（error 色）/ 已开启范围）。
- 不新增网络请求：组件纯展示，数据由 Lead 用现有仓库聚合传入。

---

## 5. 四态 / 自适应 / 预览

- **反馈页四态**：`LOADING`（CtLoading）→ `FAILED`（EmptyState+重试）/ `READY`（空态或数据）；提交另有「提交中 / 成功 Snackbar / 失败内联+Snackbar」。
- **使用说明**：静态内容无网络四态（设计如此），页面本身无加载/错误态；内容全部内置。
- **我的数据**：纯展示组件（无状态），`ReminderStatus` 覆盖 未开启/权限缺失/已开启 三种展示。
- **自适应**：反馈 ≥600dp 双栏（表单 + 列表）、列表卡片 max 760dp；使用说明宽 760dp 限宽；MeStatsSection 单卡片，Profile 双栏由 design 控制。
- **预览**：`ParityP1Previews.kt` 8 张（79–86），覆盖浅/深色、手机/平板、错误态、提醒未授权。

---

## 6. 测试数字

本任务新增 **36 tests / 0 failures**：

```text
FeedbackRulesTest        12
FeedbackRepositoryTest   11
MeStatsSectionTest       13
```

全量回归（2026-11-08 08:27）：

```text
BUILD SUCCESSFUL — :app:testDebugUnitTest
CLASSES=48  TOTAL=603  FAIL=0  ERR=0     （基线 v2.0.0 552；其他并行任务 +15）
```

---

## 7. 遗留 / 未做

1. 反馈页**下拉刷新**（androidx.compose.material3 pull-to-refresh）未接：错误态已有「重试」；Lead 接线时可加 `force=true` 刷新。
2. mp 的 AI 页面接力（agent handoff）不迁移（小程序专属）。
3. 反馈列表本地持久化：当前为内存缓存（进程重启后首次 load 从云端拉；无网络时无历史）。与 mp 的本地 store 不同（mp 有 `store` 缓存）——如需离线历史可后续加 DataStore（不影响当前验收）。
4. 管理员列表作者名依赖班级名单缓存；名单未加载时显示「（未知成员）」（与 mp 的 `cloud.memberName` 未命中口径一致）。
5. 使用说明未做「按当前页面跳转高亮」；纯分节列表（任务允许分节列表即可）。
