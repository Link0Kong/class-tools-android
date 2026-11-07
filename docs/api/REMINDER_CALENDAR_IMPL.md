# 提醒 + 日历 + 权限领域实现（v2.0.0 Round 1 / task-21）

> 状态：**数据/领域层已完成**（纯逻辑 + Android 通知/WorkManager/CalendarContract/权限封装），Lead 负责 UI/接线。
> 基线 v1.0.1（466/0）之上新增 **59 个单测全绿**（`reminder 29 / calendar 18 / permission 12`）。
> 约束：本轮只写 `data/reminder`、`data/calendar`、`data/permission` 与测试/文档；未改 Manifest/构建文件（依赖与权限由 Lead 添加，见 §6）。

---

## 1. 文件清单

### `data/reminder/`

| 文件 | 内容 |
|---|---|
| `ReminderModels.kt` | `ReminderSettings`（总开关/作业/课表/汇总时间/提前天数/提前分钟/安静时段 + 规范化 + `isQuiet`）、`ReminderKind`（HOMEWORK_DIGEST/HOMEWORK_DUE/COURSE_SOON，含渠道 id 与工具 id）、`ReminderEvent`、`ReminderPlan`、`ReminderRunResult` |
| `ReminderPlanner.kt` | **纯函数**：`planHomework`（每日汇总 + 截止前 N 天）、`planCourse`（下一节提前 N 分钟）、`plan`（合并/排序）、`filterDue`（Worker 窗口）、`nextDigestAt`、`digestWindowEnd`、`digestBody`、`formatAt/formatDate` |
| `ReminderSettingsStore.kt` | `ReminderSettingsStore` 接口 + `DataStoreReminderSettingsStore`（`ct_reminder_settings`，10 个 preference key） |
| `ReminderDedupe.kt` | `ReminderDedupe` 纯函数（同日不重复/20h 冷却/7 天保留）+ `ReminderDedupeStore` 接口 + 内存/SharedPreferences 实现 |
| `ReminderScheduler.kt` | `ReminderScheduler` + `WorkManagerReminderScheduler`（15min 唯一周期任务）、`ReminderRuntime`（reschedule/cancel）、`ReminderDataProvider` + `ContainerReminderDataProvider`（本机缓存读，不联网）、`ReminderEngine`（一次扫描）、`ReminderWorker`、`ReminderBootReceiver` |
| `ReminderNotifier.kt` | `ReminderNotifier` 接口 + `AndroidReminderNotifier`（渠道创建、权限检查、通知样式、点击 PendingIntent） |
| `ReminderIntentFactory.kt` | 点击跳转约定：extras 常量、`toolIdFor`、`toolIdFromExtras`、`openIntent`、`pendingIntent` |

### `data/calendar/`

| 文件 | 内容 |
|---|---|
| `CalendarModels.kt` | `CalendarEvent`、`CalendarWriteResult`（Success/NeedsPermission/NoCalendarAccount/Invalid/Failed）、`CalendarWriteDecision`、`CalendarWritePolicy`（校验/权限决策/重复判定） |
| `CalendarWriter.kt` | `CalendarWriter` 接口 + `AndroidCalendarWriter`（CalendarContract 插入事件 + Reminders）、`CalendarAccountResolver`/`AndroidCalendarAccountResolver`、`CalendarIntentFactory`（`ACTION_INSERT` 兜底） |
| `CourseCalendarMapper.kt` | 课表 → 事件（周次/单双周/节次时间/教室/教师/来源标记；单周、未来 N 周） |
| `NoticeCalendarMapper.kt` | 通知 → 事件（`eligible` 纯函数：长期安排/无时间/已过期不可写；活动优先于截止；地点从结构化段落提取） |

### `data/permission/`

| 文件 | 内容 |
|---|---|
| `PermissionModels.kt` | `PermissionType`（通知/日历）、`PermissionState`（Granted/Denied/Requestable/PermanentlyDenied）、`PermissionSnapshot`、`PermissionResolver`（可测纯函数）、`PermissionController` + `AndroidPermissionController`、`PermissionRequestStore` + SharedPrefs 实现 |
| `PermissionTexts.kt` | 通知/日历的标题/说明/理由/设置引导/确认按钮 + 通知渠道常量 |

### 测试

- `app/src/test/.../data/reminder/ReminderPlannerTest.kt`：**29**（汇总窗口/完成/过期/无截止/次日汇总/提前 3 天/即时窗口/汇总去重/开关/安静时段/排序/filterDue/课表窗口/多提前分钟/去重冷却/正文折叠/日期格式/设置规范化）
- `app/src/test/.../data/calendar/CalendarMappersTest.kt`：**18**（课程字段/单双周/周次/非法/排序/未来周/无开学日/节次越界/通知 eligible 各分支/活动优先/延期/地点/摘要截断/校验/权限决策/判重/默认值）
- `app/src/test/.../data/permission/PermissionLogicTest.kt`：**12**（四态解析/组合优先级/权限清单/快照/文案/渠道常量）

---

## 2. 公开 API（Lead 接线）

### 2.1 设置与规划

```kotlin
data class ReminderSettings(
    val enabled: Boolean = true,
    val homeworkEnabled: Boolean = true,
    val courseEnabled: Boolean = true,
    val homeworkDigestHour: Int = 20, val homeworkDigestMinute: Int = 0,
    val homeworkLeadDays: Int = 1,       // 0/1/3
    val courseLeadMinutes: Int = 15,     // 5/10/15
    val quietHoursEnabled: Boolean = false, val quietStartHour: Int = 23, val quietEndHour: Int = 7,
) {
    fun sanitized(): ReminderSettings
    fun isQuiet(triggerAtMillis: Long, zone: ZoneId = systemDefault()): Boolean
    companion object { val DEFAULT; HOMEWORK_LEAD_DAY_OPTIONS; COURSE_LEAD_MINUTE_OPTIONS }
}

interface ReminderSettingsStore {
    val settings: Flow<ReminderSettings>
    suspend fun current(): ReminderSettings
    suspend fun update(transform: (ReminderSettings) -> ReminderSettings): ReminderSettings
}
class DataStoreReminderSettingsStore(context: Context) : ReminderSettingsStore

object ReminderPlanner {
    fun planHomework(rows, doneIds, nowMillis, settings, zone): List<ReminderEvent>
    fun planCourse(plan: TtPlan?, nowMillis, settings, zone): List<ReminderEvent>
    fun plan(rows, doneIds, plan: TtPlan?, nowMillis, settings, zone): ReminderPlan
    fun filterDue(events, nowMillis, horizonMillis = 20min, graceMillis = 10min): List<ReminderEvent>
    fun nextDigestAt(nowMillis, settings, zone): Long
    fun digestWindowEnd(digestAtMillis, zone): Long
    fun digestBody(items, zone): String
}
data class ReminderEvent(kind, key, triggerAtMillis, title, body, toolId) { val notificationId: Int }
```

### 2.2 调度 / 通知 / 点击

```kotlin
interface ReminderScheduler { fun ensureScheduled(); fun cancelAll() }
class WorkManagerReminderScheduler(context) : ReminderScheduler   // 唯一周期任务 "ct_reminder_periodic"（15min）
object ReminderRuntime { fun reschedule(context); fun cancel(context) }

class ReminderEngine(context, settingsStore, dedupeStore, notifier, permissionController, dataProvider, clock, zone) {
    suspend fun runOnce(): ReminderRunResult
}
class ReminderWorker : CoroutineWorker    // 每 15min 执行 ReminderEngine.runOnce()
class ReminderBootReceiver : BroadcastReceiver  // BOOT_COMPLETED / MY_PACKAGE_REPLACED / LOCKED_BOOT_COMPLETED

interface ReminderNotifier { fun ensureChannels(); fun notify(event): Boolean }
class AndroidReminderNotifier(context) : ReminderNotifier

object ReminderIntentFactory {
    const val EXTRA_TOOL_ID / EXTRA_REMINDER_KEY / EXTRA_REMINDER_KIND
    fun toolIdFor(kind): String           // hw / tt
    fun toolIdFromExtras(toolId: String?): String?
    fun openIntent(context, event): Intent?
    fun pendingIntent(context, event): PendingIntent?
}
```

### 2.3 权限

```kotlin
enum class PermissionType { NOTIFICATIONS, CALENDAR }
enum class PermissionState { GRANTED, REQUESTABLE, DENIED, PERMANENTLY_DENIED } // isGranted/canRequest/needsSettings
data class PermissionSnapshot(notifications, calendar) { fun stateOf(type); notificationsReady; calendarReady }
object PermissionResolver { fun resolve(granted, requestedBefore, showRationale); fun combine(states) }

interface PermissionController {
    fun state(type): PermissionState
    fun snapshot(): PermissionSnapshot
    fun markRequested(type)                       // 请求系统框前调用
    fun permissionsToRequest(type): Array<String> // 已授予的不含
    fun settingsIntent(): Intent                  // 永久拒绝后引导
    fun notificationsEnabled(): Boolean
}
class AndroidPermissionController(context, store, grantChecker, rationaleChecker, apiLevel)
class SharedPrefsPermissionRequestStore(context) : PermissionRequestStore

object PermissionTexts { val notifications/calendar: PermissionCopy; fun of(type); CHANNEL_* 常量 }
```

### 2.4 日历

```kotlin
data class CalendarEvent(title, beginMillis, endMillis, description, location, allDay, reminderMinutes=10, timeZone, sourceKey)
sealed interface CalendarWriteResult { Success(eventId, sourceKey); NeedsPermission; NoCalendarAccount; Invalid(reason); Failed(message) }
object CalendarWritePolicy { fun validate(event): String?; fun decide(state): CalendarWriteDecision; fun precheck(event, state): CalendarWriteResult?; fun isDuplicate(...) }

interface CalendarWriter { suspend fun write(event: CalendarEvent): CalendarWriteResult }
class AndroidCalendarWriter(context, permissionController, resolver, ioDispatcher) : CalendarWriter
object CalendarIntentFactory { fun insertIntent(event): Intent? }   // ACTION_INSERT 兜底

object CourseCalendarMapper {
    fun eventFor(course, week, termStart, zone, reminderMinutes): CalendarEvent?
    fun eventsForWeek(snapshot, week, zone, reminderMinutes): List<CalendarEvent>
    fun eventsForNextWeeks(snapshot, nowMillis, weeksAhead = 2, zone, reminderMinutes): List<CalendarEvent>
    fun descriptionOf(course, week): String
    const val DEFAULT_WEEKS_AHEAD = 2; MAX_WEEKS_AHEAD = 20
}
object NoticeCalendarMapper {
    fun eligible(group, nowMillis, zone): Boolean
    fun eventFor(group, nowMillis, zone, reminderMinutes): CalendarEvent?
    fun targetMillis(group, nowMillis, zone): Long?
    fun locationOf(group): String
    fun descriptionOf(group, action): String
}
```

---

## 3. 行为口径

### 3.1 作业提醒

- 数据：`HomeworkRepository.cached()`（本机缓存，TTL/离线可用）+ `marks hw` 去已完成；**不联网**。
- 无截止/坏日期/已截止/已完成 → 跳过；规划窗口 7 天。
- **每日汇总**：默认每天 20:00（可配），覆盖「汇总日 + 2 天」整个自然日（含后天 23:59）内的未完成作业 → 一条「作业截止汇总」通知（最多列 5 项 + 「等 N 项」）；汇总时间已过则排到次日。
- **截止前 N 天**：触发 = 截止 − N×24h；已进入窗口则 `now+30s` 立即提醒；被当日汇总覆盖的作业（N≤1）不重复提醒。
- 安静时段（默认关闭）：落在安静时段的触发点跳过（不延迟堆积）。

### 3.2 课表提醒

- `TtPlan.next`（`TtCalendar.plan` 产物）+ `startAt` 未来 → 触发 = 开始 − N 分钟（5/10/15）；已进入窗口 `now+30s`；无下一节/已开始/无课表数据 → 无提醒。
- 点击通知 → 打开「课表」工具（`tt`）。

### 3.3 去重 / 冷却

- key：`hw-digest:<date>` / `hw-due:<id>` / `class:<startAt>`；
- 同 key **同一本地日期只发一次** + 20h 冷却兜底；存储保留 7 天（SharedPreferences `ct_reminder_sent`，进程重启保留）。

### 3.4 调度

- WorkManager 唯一周期任务 `ct_reminder_periodic`，**15 分钟**（系统最小周期），`ExistingPeriodicWorkPolicy.UPDATE` 幂等重建；
- 每次只处理 `[now-10min, now+20min]` 内到点事件；Doze 下可能延迟（不使用精确闹钟，避免 SCHEDULE_EXACT_ALARM 权限）；
- 设置变更/开机/应用更新后调用 `ReminderRuntime.reschedule(context)`；关闭提醒用 `cancel(context)`；
- 通知权限/总开关关闭 → Worker 静默成功，不打扰。

### 3.5 权限

- 通知：Android 13+ `POST_NOTIFICATIONS`；≤12 看系统通知总开关（`areNotificationsEnabled`）。
- 日历：`READ_CALENDAR` + `WRITE_CALENDAR` 同时授予才算 `GRANTED`；合并规则见 `PermissionResolver.combine`。
- 首次请求前 `markRequested`，之后依据 `shouldShowRequestPermissionRationale` 区分 `DENIED`/`PERMANENTLY_DENIED`；永久拒绝引导 `settingsIntent`。
- 文案：`PermissionTexts`（通知/日历各一套，含理由/设置引导）。

### 3.6 日历写入

```
CalendarWritePolicy.precheck(event, permission)
  ├─ Invalid → 展示原因
  ├─ NeedsPermission/DENIED → 返回 NeedsPermission → Lead 走 ACTION_INSERT（CalendarIntentFactory）或请求权限
  └─ PROCEED → AndroidCalendarWriter
        ├─ findWritableCalendarId() == null → NoCalendarAccount → ACTION_INSERT 兜底
        └─ insert Events（标题/描述/地点/DTSTART/DTEND/时区/HAS_ALARM）+ Reminders(10min) → Success(eventId)
```
- **无权限返回 NeedsPermission（含永久拒绝），Lead 用 `ACTION_INSERT` 系统日历界面兜底**（`CalendarIntentFactory.insertIntent`），用户自行确认保存；
- 事件默认提前 10 分钟提醒、时区=本机；课程按 `TtPeriods` 精确到分钟；通知按活动/真实截止（extensions 最后一条优先）。

---

## 4. 与隐私/合规

- 提醒与日历均为**本机功能**：不新增网络请求，不上传通知/日历数据；
- 通知内容仅含作业科目/课程名/时间/教室，**不含学号/姓名/手机号/宿舍号**（宿舍号不在提醒范围）；
- `PermissionRequestStore` 只存「是否请求过」布尔值（SharedPreferences），不进云端；
- 日历写入是用户主动操作 + 系统权限双重确认；无权限时走系统 UI 由用户确认；
- 通知权限拒绝不崩溃、不反复打扰（状态透明，设置页引导）。

---

## 5. 单测

结果（2026-11-08 07:32，全仓最新一次运行）：

```text
BUILD SUCCESSFUL — :app:testDebugUnitTest
CLASSES=43  TOTAL=552  FAIL=0  ERR=0
```

本任务新增 **59 个用例全部通过**：
`ReminderPlannerTest` 29 / `CalendarMappersTest` 18 / `PermissionLogicTest` 12。
（全仓 552 含其他并行任务增量；基线 v1.0.1 为 466。）

---

## 6. Lead 接线点（按优先级）

### 6.1 依赖 / Manifest（Lead；本轮已确认构建文件已有 work-runtime-ktx）

- `app/build.gradle.kts`：`implementation(libs.androidx.work.runtime.ktx)` ✅（已存在）；`androidx.core.ktx` ✅（NotificationCompat 来源）。
- `AndroidManifest.xml`：`POST_NOTIFICATIONS` / `READ_CALENDAR` / `WRITE_CALENDAR` / `RECEIVE_BOOT_COMPLETED` ✅（Lead 已加）。
- **还差一个 receiver 声明**（本轮不能改 Manifest，请 Lead 添加）：

```xml
<receiver
    android:name=".data.reminder.ReminderBootReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
        <action android:name="android.intent.action.LOCKED_BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

### 6.2 AppContainer

```kotlin
val reminderSettingsStore: ReminderSettingsStore = DataStoreReminderSettingsStore(appContext)
val reminderDedupeStore: ReminderDedupeStore = SharedPrefsReminderDedupeStore(appContext)
val permissionController: PermissionController = AndroidPermissionController(appContext)   // UI 侧建议用 Activity Context 重建
val calendarWriter: CalendarWriter = AndroidCalendarWriter(appContext, permissionController)
val reminderScheduler: ReminderScheduler = WorkManagerReminderScheduler(appContext)
// 启动/回前台时（可选）：reminderScheduler.ensureScheduled()
```

### 6.3 设置页（design/Lead）

- 「我的 → 提醒」：绑定 `reminderSettingsStore.settings`；修改后 `store.update {}` 并 `ReminderRuntime.reschedule(context)`（关闭总开关时 `cancel`）；
- 通知权限未授予时展示 `PermissionTexts.notifications` 引导：`AndroidPermissionController(activity)` → `markRequested(NOTIFICATIONS)` → `rememberLauncherForActivityResult(RequestMultiplePermissions)` → `permissionsToRequest`；永久拒绝 → `settingsIntent`；
- 作业提前档位 `0/1/3` 天、课表 `5/10/15` 分钟；汇总时间默认 20:00。

### 6.4 通知点击 → 工具页

- `MainActivity` 读取 `ReminderIntentFactory.EXTRA_TOOL_ID`（`hw`/`tt`）：
  - 已登录：`navController.navigate(Routes.tool(toolId))`；
  - 未登录：先展示身份页，登录完成后消费 pending toolId（Lead 在 `ClassToolsRoot` 处理）；
  - 处理完清除 intent/extra，避免旋转重复跳转。

### 6.5 课表/通知「写日程」入口

- 课表页：`CourseCalendarMapper.eventsForWeek/NextWeeks` → `calendarWriter.write(event)`；
- 通知详情：`NoticeCalendarMapper.eligible/eventFor` → `calendarWriter.write(event)`；
- 返回 `NeedsPermission`/`DENIED` → 请求日历权限或 `CalendarIntentFactory.insertIntent(event)` 兜底；
- `NoCalendarAccount` → 直接 ACTION_INSERT 兜底（提示用户选日历账号）。

### 6.6 数据前提

- Worker 只读本机缓存：作业/课表需已被页面加载过（App 启动 `refreshCoreData()` 已拉名单/marks/userdata；建议 Lead 在 App 启动后台顺带 `homeworkRepository.load(false)` / `timetableRepository.load(false)` 预热，保证次日提醒可用）。

---

## 7. 已知限制 / 后续（v2.0.0）

1. **通知精度**：WorkManager 15min 周期，Doze 下可能延迟；若后续要求分钟级精确，需评估 `AlarmManager` + `SCHEDULE_EXACT_ALARM`（商店合规成本）或前台服务。
2. **通知小图标**：当前用系统占位 `android.R.drawable.ic_dialog_info`；design/Lead 应替换为单色品牌图标（`res/drawable/ic_stat_*.xml`）。
3. **通知提醒（ntremind）**：小程序支持「单条通知截止提醒」；本轮只做作业/课表两类，通知提醒列 P1（可复用 Planner：`NoticeCalendarMapper.targetMillis` + deadline 提前 N 小时）。
4. **日历去重**：当前只提供 `sourceKey` 判重辅助；重复点击「写日程」会生成多条（系统日历可手动删）。后续可查询 `CalendarContract.Events` 的 `CUSTOM_APP_URI`/描述标记实现幂等。
5. **日历更新/删除**：仅插入，不支持改期/取消（后续可保存 eventId）。
6. **权限理由**：`AndroidPermissionController` 需 Activity Context 才能正确判断 rationale；后台 Worker 使用的是 App Context（只判断授予/拒绝，不弹框）。
7. **设置未跟账号同步**：提醒设置是本机设备偏好（与主题一致），不入 userdata；换设备需重设。
