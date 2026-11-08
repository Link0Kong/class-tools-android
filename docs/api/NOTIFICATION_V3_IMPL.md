# 通知系统 v3（可测试/可观测/单条提醒/可靠性）（task-29）

> 状态：**已交付**（`data/reminder/` 扩展 + 单测 + 文档）。未改 Manifest / 构建文件 / 其它 UI。
> 基线 v2.1.1（639/0）；本任务新增 **31 个单测全绿**（ReminderOneShotTest 19 + ReminderEngineTest 12，要求 ≥20），全量回归见交付消息。
> 用户反馈「通知系统异常且功能不全」的审计与修复结论见 §1。

---

## 1. 现有链路审计（修复前）

| 环节 | 现状/风险 | v3 处理 |
|---|---|---|
| 周期 Worker | 15 分钟周期；每次只处理 `[now-grace(10min), now+horizon(20min)]`；Doze/省电延迟超过约 30 分钟时**错过即不再发** | 新增**错过补发**：运行状态记录上次运行时间（回看 10min~6h）+ 上次规划的未来事件持久化（pending store）；下次运行补发已过点但未发送的事件，复用去重防重复 |
| 权限 | 仅检查 POST_NOTIFICATIONS（API33+）；API≤32 用系统通知开关；缺失时**静默** | 保留静默（不打扰），但 `ReminderRunResult`/`ReminderDiagnostics` 明确返回 `reason`/`permission`；单条提醒权限缺失**返回明确错误**；`testNotification` 返回 PermissionMissing |
| 渠道 | homework_due/class_soon/notice_due 已创建，重要性固定 | 渠道元数据集中在 `ReminderKind`；重要性按 kind（课程/通知临近 HIGH）；描述文案保留 |
| 小图标 | `android.R.drawable.ic_dialog_info` 系统占位 | 新增 `ReminderSmallIcon`：优先专用单色 drawable `ic_stat_class_tools`，缺失回退系统占位；**需 Lead/design 加资源**（见 §6） |
| 去重 | 同 key 当日不重复 + 20h 冷却 + 7 天保留 | 保留；新增 pending 补发同样走去重 |
| 点击跳转 | `getLaunchIntentForPackage` 可能返回 null（部分 OEM）；flags 已 IMMUTABLE/UPDATE_CURRENT；extras 未显式清理 | 新增 OEM 回退（ACTION_MAIN+LAUNCHER+setPackage）；固定 requestCode；新增 `consumeToolId(intent)` 读取并清除 extras（防旋转/重进重复跳转） |
| 分组 | 每条通知独立，多条会刷屏 | 新增分组：`setGroup` + 组摘要（`GROUP_ALERT_SUMMARY`，子通知静默、摘要提示一次） |
| 可观测 | 只有少量内部结果，设置页无法确认通知是否正常 | `runNow()`/`diagnostics()`/`testNotification()`；Worker logcat tag=`Reminder` 单行摘要（不含通知正文/隐私） |
| 设置变更延迟 | 开关打开后要等下一轮周期（最多 15min+） | `ReminderRuntime.reschedule` 现在**自动立即触发一次** `runNow`（OneTimeWork），Lead 现有设置页调用即可生效 |
| R8 | 已确认 proguard keep 包名已在 v1.0.1 审计修复；Worker/Receiver 由 WorkManager/Manifest 引用，R8 不裁 | 文档补充回归说明（release 包安装后设置页「发送测试通知」验证） |

---

## 2. 交付文件

| 文件 | 内容 |
|---|---|
| `ReminderModels.kt`（改） | `ReminderRunResult` 新增诊断字段：`reason/permission/settingsSummary/cacheSummary/checkedAtMillis/catchUpEvents` + `logSummary()` |
| `ReminderDiagnostics.kt`（新） | `ReminderRunStateStore`（上次运行时间/摘要；SharedPrefs/内存）、`ReminderCatchUp`（回看窗口计算）、`ReminderTestNotificationResult`、`ReminderDiagnostics`、`ReminderLog`（tag=`Reminder`）、`ReminderSmallIcon`、`ReminderActions`（UI 便捷 suspend 入口） |
| `ReminderPendingStore.kt`（新） | 上次规划的未来事件持久化（SharedPrefs/内存）+ `ReminderPendingCodec`（JSON 编解码）+ `ReminderPendingCatchUp`（保留未来事件 / 错过补发选择） |
| `ReminderOneShot.kt`（新） | 单条提醒：`OneShotReminderOption`（1 天/1 小时/截止时刻/活动前 1 小时）、`OneShotTargetKind`（HW/NT）、`OneShotPlannedTime` 时间 helper、`OneShotKey`、`OneShotScheduleResult`（明确错误）、`ReminderOneShotScheduler` 接口 + `WorkManagerOneShotScheduler`、`ReminderOneShotWorker`、SharedPrefs 排程元数据（`ct_oneshot_pending`）、`ReminderOneShotFactory` |
| `ReminderScheduler.kt`（改） | `ReminderEngine` 重构为可注入主构造器 + `constructor(context)`；`runNow()`/`testNotification()`/`diagnostics()`；错过补发逻辑；Worker logcat 摘要；`ReminderRuntime.runNow(context)`；`reschedule` 自动立即跑一次 |
| `ReminderNotifier.kt`（改） | 分组摘要（`ReminderGrouping`）、组告警行为、专用小图标解析、渠道元数据按 kind |
| `ReminderIntentFactory.kt`（改） | OEM launcher intent 回退、固定 PendingIntent flags/requestCode、`consumeToolId` 清理 extras |
| `test/.../ReminderOneShotTest.kt`（新） | 19：可选时间/过期过滤/key/workName/校验错误/事件字段/编解码/内存存储/补发窗口/分组/小图标/日志/诊断 |
| `test/.../ReminderEngineTest.kt`（新） | 12：假注入运行/去重/权限/开关/诊断/测试通知三态/pending 补发/回看上限/运行状态记录 |

---

## 3. 公开 API

```kotlin
// ---------- 可观测 / 可测试 ----------
class ReminderEngine(
    settingsStore, dedupeStore, notifier, permissionController, dataProvider,
    runStateStore = InMemoryReminderRunStateStore(),
    pendingStore = InMemoryReminderPendingEventStore(),
    clock = System::currentTimeMillis, zone = systemDefault(),
    catchUpMaxMillis = ReminderCatchUp.MAX_LOOKBACK_MS,
) {
    constructor(context: Context)   // 生产装配（DataStore/SharedPrefs/Android Notifier）
    suspend fun runNow(): ReminderRunResult      // runOnce() 等价别名
    suspend fun testNotification(kind: ReminderKind = HOMEWORK_DIGEST): ReminderTestNotificationResult
    suspend fun diagnostics(): ReminderDiagnostics
}

data class ReminderRunResult(sent, skipped, candidates, disabledReason,
    reason, permission, settingsSummary, cacheSummary, checkedAtMillis, catchUpEvents) { fun logSummary() }
data class ReminderDiagnostics(... settings/permission/lastRun/cache/lookback/icon/channels)
object ReminderActions {                              // 设置页直接用（suspend）
    suspend fun testNotification(context, kind = HOMEWORK_DIGEST): ReminderTestNotificationResult
    suspend fun diagnostics(context): ReminderDiagnostics
}
object ReminderRuntime {
    fun reschedule(context)   // 周期 + 立即 runNow（设置变更后调用；现有调用点自动生效）
    fun runNow(context)       // OneTimeWork 立即执行一次
    fun cancel(context)
}

// ---------- 单条提醒 ----------
enum class OneShotReminderOption { BEFORE_1_DAY, BEFORE_1_HOUR, AT_DEADLINE, BEFORE_EVENT_1_HOUR }
enum class OneShotTargetKind(val toolId: String, val label: String) { HOMEWORK("hw","作业"), NOTICE("nt","通知") }
data class OneShotPlannedTime(option, label, fireAtMillis, targetAtMillis)
data class OneShotKey(targetKind, targetId, fireAtMillis) { val workName }
sealed interface OneShotScheduleResult { Scheduled; PermissionMissing; TooLate; LimitReached; EmptyTarget; Failed }
data class OneShotPending(key, label, fireAtMillis)

object ReminderOneShot {
    const val MAX_PER_CATEGORY = 20
    fun homeworkPlannedTimes(row, now, zone): List<OneShotPlannedTime>   // 截止前1天/1小时/截止时刻
    fun noticePlannedTimes(group, now, zone): List<OneShotPlannedTime>   // 活动前1小时 + 截止三种
    fun eventFor(kind, targetId, label, targetAtMillis, zone): ReminderEvent
    fun validate(...): OneShotScheduleResult?
    fun workName(...) / key(...) / triggerAt(...) / labelOf(...)
}

interface ReminderOneShotScheduler {
    suspend fun scheduleHomeworkReminder(rowId, fireAtMillis, label, targetAtMillis = fireAtMillis): OneShotScheduleResult
    suspend fun scheduleNoticeReminder(groupId, fireAtMillis, label, targetAtMillis = fireAtMillis): OneShotScheduleResult
    suspend fun cancel(key: OneShotKey)
    suspend fun cancelAll()
    suspend fun listPending(): List<OneShotPending>
}
class WorkManagerOneShotScheduler(context, permissionController, pendingStore = SharedPrefs…, clock) : ReminderOneShotScheduler
object ReminderOneShotFactory { fun create(context, permissionController): ReminderOneShotScheduler }
```

---

## 4. 可靠性规则

### 4.1 周期提醒 + 错过补发
1. 每次运行结束：
   - `ReminderRunStateStore.recordRun(now, logSummary)` 记录运行时间/摘要；
   - `ReminderPendingEventStore.save(pendingForNextRun(plan.events, now))`：仅保存触发时间仍在未来的事件（≤200 条）。
2. 下次运行：
   - `lookback = ReminderCatchUp.lookbackMillis(now, lastRun)`（默认 10 分钟，最大 6 小时）；
   - `due = filterDue(events, now, grace=lookback)`（正常窗口 + 回看）；
   - `catchUpPending = dueCatchUp(pending, now, lookback, keysAlreadyPlanned)`（已过点、未被本次规划覆盖）；
   - 合并、按触发时间排序、去重 key 后发送；标记 `catchUpEvents`；
   - 去重（`ReminderDedupe`）保证同一事件即使多次进入窗口也只发一次。
3. 例：20:00 作业汇总事件在 15:00 运行中写入 pending；若 Worker 被 Doze 延迟到 20:05 才运行，则从 pending 补发（单测覆盖）。

### 4.2 通知分组（防刷屏）
- 组：作业 `ct_reminder_group_hw` / 课表 `ct_reminder_group_tt` / 通知 `ct_reminder_group_nt`；
- 子通知 `setGroupAlertBehavior(GROUP_ALERT_SUMMARY)`（静默），组摘要 `setGroupSummary(true)` 提示一次；
- 摘要 id=组 key hash（同组覆盖）；摘要文案「N 条提醒 · 点击查看」；
- 渠道重要性：课程/通知临近 HIGH，其余 DEFAULT。

### 4.3 小图标
- `ReminderSmallIcon.RES_NAME = "ic_stat_class_tools"`：用 `getIdentifier` 动态查找（不产生编译期资源依赖）；
- 找到 → 专用单色图标；找不到 → `android.R.drawable.ic_dialog_info` 占位（当前状态，需 Lead/design 加资源）；
- 资源加入后**无需改代码**，下次 Worker/测试通知自动使用。

### 4.4 PendingIntent / OEM
- flags 固定 `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`；
- requestCode = 事件 key hash（同事件覆盖、不同事件隔离）；
- `getLaunchIntentForPackage` 返回 null 时回退 `ACTION_MAIN + CATEGORY_LAUNCHER + setPackage`（MIUI/EMUI 兼容）；
- extras 用 `consumeToolId(intent)` 读取后清除（Lead 可替换 MainActivity 现有读取；不替换也不影响功能，仅旋转时可能重复跳转）。

### 4.5 单条提醒（WorkManager OneTime）
- 取舍：不用精确闹钟（免 SCHEDULE_EXACT_ALARM 权限/审核成本），WorkManager 近似（Doze 下分钟级偏差）；
- 持久化：WorkManager 任务 + `ct_oneshot_pending` 元数据（进程死亡/重启保留）；
- 上限：每类 20 条（`ReminderOneShot.MAX_PER_CATEGORY`），超限返回 `LimitReached`；
- 明确错误：权限缺失 `PermissionMissing`、时间已过（≤now+1min）`TooLate`、空目标 `EmptyTarget`、排程异常 `Failed`；
- 取消：`cancel(key)`（同时取消 WorkManager 唯一任务并移除元数据）、`cancelAll()`；
- 点击：作业→`hw`、通知→`nt`（复用 IntentFactory/PendingIntent）。

---

## 5. 接线点（Lead）

### 5.1 设置页「发送测试通知」（推荐直接调用便捷入口）
```kotlin
val scope = rememberCoroutineScope()
TextButton(onClick = {
    scope.launch {
        when (val r = ReminderActions.testNotification(context, ReminderKind.HOMEWORK_DIGEST)) {
            ReminderTestNotificationResult.Sent -> toast("已发送测试通知，请查看通知栏")
            ReminderTestNotificationResult.PermissionMissing -> toast("请先开启通知权限")
            is ReminderTestNotificationResult.Failed -> toast(r.message)
        }
    }
}) { Text("发送测试通知") }
```

### 5.2 诊断（可选，设置页折叠区）
```kotlin
val diag = remember { mutableStateOf<ReminderDiagnostics?>(null) }
LaunchedEffect(Unit) { diag.value = ReminderActions.diagnostics(context) }
// 展示：权限状态、设置摘要、缓存摘要、上次运行时间/摘要、回看窗口
```

### 5.3 设置变更后立即触发
- 你现有 `MainNavHost` 的 `onSettingsChanged` 调 `container.rescheduleReminders()` → 内部已 = 周期重建 + `runNow`，**无需改调用**；
- 单条提醒 UI（作业/通知详情）：
```kotlin
val scheduler = ReminderOneShotFactory.create(context, container.permissionController)
val times = ReminderOneShot.homeworkPlannedTimes(row, now, zone)
// 选择 times[i] 后：
scheduler.scheduleHomeworkReminder(row.id, times[i].fireAtMillis, times[i].label, times[i].targetAtMillis)
// 返回 PermissionMissing/TooLate/LimitReached 时按结果提示；cancel(scheduler 返回的 key)
```
- 点击通知跳转：MainActivity 可改用 `ReminderIntentFactory.consumeToolId(intent)`（读取并清理 extras）。

### 5.4 需要 Lead/design 添加的资源
**`app/src/main/res/drawable/ic_stat_class_tools.xml`**（专用单色通知小图标，白色/纯色 24dp vector；建议 design 用品牌图形/铃铛）。示例（可替换）：
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF"
        android:pathData="M12,22a2.5,2.5 0,0 0,2.45 -2h-4.9A2.5,2.5 0,0 0,12,22zM18,16v-5a6,6 0,0 0,-5 -5.91V4a1,1 0,1 0,-2 0v1.09A6,6 0,0 0,6 11v5l-1.29,1.29A1,1 0,0 0,5.41 19h13.18a1,1 0,0 0,0.7 -1.71z"/>
</vector>
```
（加资源后无需改代码；`ReminderSmallIcon` 自动优先使用。）

---

## 6. 测试

```text
新增（31/0）：
  ReminderOneShotTest   19
  ReminderEngineTest    12
reminder 模块合计 80/0（含既有 49）
全量回归见交付消息。
```

覆盖：可选时间计算/过期过滤、key/workName 净化、上限/权限/时间校验错误、单条事件字段、JSON 编解码坏行、内存存储、回看窗口、错过补发选择、待发事件保留、通知分组/摘要、小图标回退、日志不含正文、诊断摘要；引擎注入运行、去重、权限/开关禁用、诊断字段、测试通知三态、pending 补发、回看上限、运行状态记录。

---

## 7. 与 mp 差异 & 遗留

- mp 用微信订阅消息（云函数发、用户自选时间）；Android 全部本机（WorkManager），不联网、不上传；单条提醒时间选项对齐「截止前 1 天/1 小时/截止时刻/活动前 1 小时」。
- 遗留：
  1. 专用小图标资源待 Lead/design 添加（当前占位图标可正常发通知）；
  2. 单条提醒为 WorkManager 近似（Doze 下可能延迟数分钟），未使用精确闹钟；
  3. 组摘要条数按进程内发送次数累计（进程重启后从 1 重新计数），仅影响摘要文案；
  4. 单条提醒排程元数据与 WorkManager 双写，极端情况下元数据可能残留（>1 天自动清理）；
  5. 无「取消全部单条提醒」的 UI 入口（接口 `cancelAll` 已提供，Lead 可按需加）。
