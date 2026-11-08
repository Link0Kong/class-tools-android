# 通知提醒（ntremind）+ 数据与隐私页增强（task-26 / v2.1.1）

> 状态：**已交付**（数据/领域 + 独立 UI 组件 + 测试 + 文档）。未改 `MainNavHost` / `AppContainer` / `ProfileScreen` / `ReminderSettingsScreen` / `DataPrivacyScreen` / 构建文件（Lead 接线）。
> 基线 v2.1.0（603/0）；本任务新增 **36 个单测全绿**（notice 20 + privacy 16，要求 ≥12/≥10）；全量 **51 classes / 639 tests / 0 failures**。
> 参考（只读）：`mp-class-tools/utils/notify.js`（ntremind 订阅语义）、`pages/data`（同步范围/8 计数）、`utils/privacy.js`（SCOPE/countsOf）、`docs/api/ENDPOINTS.md`。

---

## 1. 交付文件

| 文件 | 内容 |
|---|---|
| `data/reminder/ReminderModels.kt`（改） | `ReminderSettings.noticeEnabled`（默认 true，纳入 `anySectionEnabled`）；`ReminderKind` 新增 `NOTICE_DIGEST("nt","notice_due")` / `NOTICE_DUE("nt","notice_due")`，并为所有 kind 增加渠道名/描述/高优先级元数据 |
| `data/reminder/ReminderSettingsStore.kt`（改） | 持久化 `notice_enabled`（DataStore key），读取默认 true |
| `data/reminder/ReminderPlanner.kt`（改） | **通知提醒纯函数**：`noticeTargets` / `planNotices` / `noticeDigestBody`；`plan(...)` 新增 `notices`、`handledNoticeIds`（默认参数，向后兼容） |
| `data/reminder/ReminderScheduler.kt`（改） | `ReminderDataProvider` 新增 `noticesData()` / `handledNoticeIds()`；`ContainerReminderDataProvider` 从 `noticesRepository.cached()` + marks `ntdone` 读；`ReminderEngine` 一次扫描同时处理作业/课表/通知 |
| `data/reminder/ReminderNotifier.kt`（改） | 通知渠道按 `ReminderKind.entries` 自动创建（新增 `notice_due` 渠道），优先级取 kind 元数据 |
| `data/reminder/ReminderIntentFactory.kt`（改） | `toolIdFromExtras` 放行 `"nt"`（点击通知打开通知页） |
| `data/privacy/LocalDataSnapshot.kt`（新） | `LocalDataSnapshot`（8 计数 + 3 额外汇总）、`LocalDataStat`、`LocalDataStats.from(...)` 纯聚合、`primaryStats/summaryStats/formatCount` |
| `data/privacy/SyncScopeCatalog.kt`（新） | `SyncScopeMode`（随账号同步/仅本机/设备偏好）、`SyncScopeItem`、`SyncScopeCatalog`（marks 3 项 + userdata v6 11 section + 4 项仅本机/设备偏好，`coversAllSections()` 校验） |
| `ui/screens/data/PrivacyEnhancements.kt`（新） | `SyncScopeSectionCard()`（同步范围清单卡）、`LocalDataStatsCard(snapshot)`（本机数据 8 计数卡 + 汇总） |
| `test/.../data/reminder/NoticeReminderPlannerTest.kt`（新） | 20 用例 |
| `test/.../data/privacy/LocalDataSnapshotTest.kt`（新） | 9 用例 |
| `test/.../data/privacy/SyncScopeCatalogTest.kt`（新） | 7 用例 |

---

## 2. ntremind 规则（与 mp 对齐）

### 2.1 可提醒目标（`ReminderPlanner.noticeTargets`）

数据源：`NoticesData.auto`（人工通知无结构化时间，不参与）；规则：

| 条件 | 处理 |
|---|---|
| `recurrence != null`（长期安排） | 不提醒 |
| `hidden == true` | 不提醒 |
| marks `ntdone` 命中（已处理） | 不提醒（`handledNoticeIds`） |
| 目标时间 | `eventAt` 未来优先，否则真实 `deadline`（含延期 `extensions` 最后一条） |
| 目标 ≤ now 或 > now+7 天 | 不提醒 |
| 其余 | 生成 `NoticeTarget(id,title,targetAt,type(EVENT/DEADLINE),action,source)` |

### 2.2 事件生成（`ReminderPlanner.planNotices`）

- **每日汇总**：与作业汇总同一时间（默认 20:00，可配 `homeworkDigestHour/Minute`），覆盖「明天 + 后天」整个自然日内的未处理通知 →
  - kind `NOTICE_DIGEST`、key `nt-digest:<yyyy-MM-dd>`、标题「通知截止汇总」、正文最多 5 条 + 「等 N 条」；
- **24 小时临近提醒**：对未被当日汇总覆盖的目标，触发时间 = 目标 − 24h（`NOTICE_LEAD_MS`）；已进入窗口 → `now + 30s` 立即提醒；
  - kind `NOTICE_DUE`、key `notice:<gid>:<event|deadline>`、标题「通知即将截止/开始：<标题>」、正文=动作·时间·来源；
- 汇总已覆盖的目标不重复发临近提醒（避免当晚两条）；
- 安静时段（若开启）对汇总/临近触发点均生效；
- 去重复用 `ReminderDedupe`（同 key 当日不重复 + 20h 冷却 + 7 天保留）；
- 点击通知打开通知页：`toolId="nt"`、渠道 `notice_due`（`ReminderKind.NOTICE_DUE.highPriority=true`）。

### 2.3 Worker / 设置

- `ReminderEngine.runOnce()` 现在一次扫描：作业（cached）→ 课表（cached）→ 通知（cached）；任一缓存为空不报错；
- 通知权限/总开关/三类开关全关 → Worker 静默成功（`disabledReason` 文案已更新「作业/课表/通知提醒均已关闭」）；
- `ReminderSettings.noticeEnabled` 默认 true；关闭后不再规划通知事件；
- **无需改 Worker**：Lead 现有 `MainNavHost` 的 `onSettingsChanged` 已按 `anySectionEnabled` 重新调度/取消，新增 notice 后自动生效。

---

## 3. 数据与隐私页增强

### 3.1 本机数据 8 计数（与 mp `pages/data` 一致）

| # | 标签 | 来源 | 说明 |
|---|---|---|---|
| 1 | 已完成作业 | marks `hwDone` true 数 | 本机 marks |
| 2 | 已处理通知 | marks `ntDone` true 数 | 本机 marks |
| 3 | 置顶通知 | marks `ntPin` true 数 | 本机 marks |
| 4 | 体测记录 | 本机 fitrecs 列表大小 | Lead 传入 |
| 5 | 抽签名单 | `userdata.roster.rosterSel` 长度 | 抽签 section 不可用显示「—」 |
| 6 | 分组名单 | `userdata.group.sel` 长度 | 分组 section 不可用显示「—」 |
| 7 | 点名名单 | `userdata.roster.rollSel` 长度 | — |
| 8 | 待补发 | marks/userdata 待同步条数 | Lead 传入（默认 0） |

额外「汇总行」（Android 增强，不计入 8 格）：同步配置 section 数（与默认值不同的 userdata section 数）、首页常用工具数、班级名单缓存人数。

### 3.2 同步范围清单

`SyncScopeCatalog.items`（固定文案，不随云端下发）：

- **随账号同步**：作业完成 / 通知置顶 / 通知已处理（marks）+ `homeTools`、`fit`、`draw`、`group`、`roster`、`hide`、`tt`、`prefs`、`nick`、`power`、`water`（userdata v6 11 section，顺序与契约一致）；
- **仅本机**：身份 token、班级名单缓存；
- **设备偏好**：提醒与安静时段、日历写入结果；
- 明确「网页与小程序同一账号下互通；云端数据只有本人 token 可读」；
- `coversAllSections()` 保证 11 个 section 全覆盖、无重复（单测）。

---

## 4. 公开 API

```kotlin
// ---------- 通知提醒 ----------
data class ReminderSettings(..., val noticeEnabled: Boolean = true, ...) // 已有字段不变
enum class ReminderKind(val toolId, val channelId, val channelName, val channelDesc, val highPriority)
// 新增：NOTICE_DIGEST / NOTICE_DUE（toolId="nt", channelId="notice_due"）

object ReminderPlanner {
    enum class NoticeTargetType(val keySuffix: String, val label: String) { EVENT, DEADLINE }
    data class NoticeTarget(id, title, targetAtMillis, type, action, source) { displayName; dedupeKey }

    fun noticeTargets(notices: NoticesData?, handledIds: Set<String> = emptySet(),
                      nowMillis: Long = now, zone: ZoneId = system): List<NoticeTarget>
    fun planNotices(notices: NoticesData?, handledIds: Set<String> = emptySet(),
                    nowMillis: Long = now, settings = DEFAULT, zone = system): List<ReminderEvent>
    fun noticeDigestBody(items: List<NoticeTarget>, zone = system): String
    fun plan(rows, doneIds, plan: TtPlan?, nowMillis, settings, zone,
             notices: NoticesData? = null, handledNoticeIds: Set<String> = emptySet()): ReminderPlan
}

interface ReminderDataProvider {
    // 原有 3 个 + 新增：
    suspend fun noticesData(): NoticesData?
    suspend fun handledNoticeIds(): Set<String>
}

// ---------- 数据与隐私 ----------
data class LocalDataSnapshot(userdataSections, homeTools, hwDone, ntDone, ntPin, fitRecords,
                             rosterSel, groupSel, rollSel, pending, classMembers,
                             drawAvailable, groupAvailable)
data class LocalDataStat(key, label, value, available = true)
object LocalDataStats {
    fun from(userdata: JsonObject?, marks: MarksSnapshot, fitRecords: Int,
             classMembers: Int = 0, pending: Int = 0): LocalDataSnapshot
    fun primaryStats(snapshot): List<LocalDataStat>   // 8 格
    fun summaryStats(snapshot): List<LocalDataStat>   // 3 汇总
    fun formatCount(value: Int): String
}

enum class SyncScopeMode(label) { SYNCED, LOCAL_ONLY, DEVICE_ONLY }
data class SyncScopeItem(key, title, detail, mode, sectionKey?)
object SyncScopeCatalog {
    val items / sectionItems / marksItems / localItems
    fun coversAllSections(): Boolean
    fun bySection(sectionKey): SyncScopeItem?
    const val FOOTER
}

@Composable fun SyncScopeSectionCard(modifier: Modifier = Modifier)
@Composable fun LocalDataStatsCard(snapshot: LocalDataSnapshot, modifier: Modifier = Modifier)
```

---

## 5. Lead 接线点

### 5.1 ReminderSettingsScreen：通知开关（Lead 接线，本任务不改该文件）

在「上课提醒」卡之后加一张同款卡（模式与作业/课表一致）：

```kotlin
ReminderCard(
    title = "通知提醒",
    description = "每日汇总「明天/后天」截止或开始的通知，并在临近 24 小时内提醒",
    checked = settings.noticeEnabled && settings.enabled,
    enabled = settings.enabled,
    onCheckedChange = { value -> update { it.copy(noticeEnabled = value) } },
    icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
)
```

- 说明文案可补充：「通知提醒只针对有明确活动/截止时间的通知；长期安排与已处理通知不提醒。」；
- 无需新增权限/依赖；Worker/渠道自动生效；点击通知跳通知页（`nt`）已由 `ReminderIntentFactory.toolIdFromExtras` 放行。

### 5.2 DataPrivacyScreen：插入两个新卡（Lead 接线）

```kotlin
// DataPrivacyScreen 新增可选参数（或 MainNavHost 读取后传入）
localSnapshot: LocalDataSnapshot = LocalDataSnapshot(),

// 页面「我的数据」卡之后插入：
SyncScopeSectionCard()
LocalDataStatsCard(snapshot = localSnapshot)
```

数据聚合（**不联网**；由 MainNavHost 用现有本机缓存构建后传入）：

```kotlin
val snapshot = LocalDataStats.from(
    userdata = container.userDataRepository.currentSections.value, // JsonObject（11 section）
    marks = container.marksRepository.marks.value,
    fitRecords = fitRecordsCount,      // 本机已加载体测历史条数（FitScreen/FitRecs 已加载列表大小）
    classMembers = runCatching { container.classMembersRepository.cached().size }.getOrDefault(0),
    pending = pendingCount,            // marks 待同步 + userdata 待同步；未暴露时传 0
)
```

> `LocalDataStats.from` 只读取传入数据，不访问网络/Context；`LocalDataSnapshot()` 全 0 时组件仍可渲染（抽签/分组显示「—」仅当对应 section 不存在；全 0 快照下 section 不可用所以显示「—」，与「不可用显示 —」口径一致）。

### 5.3 不需要改的接线

- MainActivity 通知点击：已用 `ReminderIntentFactory.toolIdFromExtras`，本任务已放行 `nt`；
- Worker：`ReminderEngine` 已扩展，构造参数不变；
- Manifest/构建文件：无新增权限/依赖。

---

## 6. 测试

```text
新增（36/0）：
  NoticeReminderPlannerTest   20
  LocalDataSnapshotTest        9
  SyncScopeCatalogTest         7

全量（2026-11-08 09:45）：
  BUILD SUCCESSFUL — :app:testDebugUnitTest
  CLASSES=51  TOTAL=639  FAIL=0  ERR=0
```

覆盖：event/deadline 选择、extensions、recurrence/已处理/隐藏/过期/人工通知排除、明天/后天汇总窗口、24h 临近与立即提醒、汇总去重、安静时段、开关/空数据、合并排序、去重键/渠道/工具 id、标题回退；隐私 8 计数聚合、section 数、空/部分/不可用、格式化、清单完整性、仅本机项与 footer。

---

## 7. 与 mp 口径差异

### 通知提醒（ntremind）
- **一致**：只提醒有明确活动/截止时间的通知；长期安排不提醒；已处理不提醒；通知内容仅标题/时间/来源。
- **差异**：
  1. mp 用微信订阅消息（一次性授权 + 云函数发）；Android 用本机 WorkManager 通知，**不联网、不上传订阅/提醒数据**；
  2. mp 由用户为单条通知选提醒时间；Android 固定「每日汇总 + 提前 24h」规则（后续可扩展为可配置提前量）；
  3. 汇总时间复用作业汇总 20:00（mp 无汇总）；
  4. 人工通知（无结构化时间字段）不提醒。
- **隐私**：通知提醒内容不含学号/姓名/手机号/宿舍号；点击仅打开 App 通知页。

### 数据与隐私
- **一致**：8 计数标签/顺序与 mp `pages/data` 完全一致；同步范围覆盖 marks + userdata v6 11 section；「仅本机」项与 mp 一致。
- **差异**：
  1. Android 额外汇总 3 项（同步配置/首页常用工具/名单缓存人数）；
  2. 「待补发」由 Lead 注入（marks/userdata 待同步合计），当前默认 0；
  3. 抽签/分组 section 不存在时显示「—」（mp 会显示 0）；
  4. Android 不展示「本机存储 keys/KB」（需平台存储 API；如需后续可加）。

---

## 8. 遗留 / 后续

1. `FitRecsRepository` 无「只读本机缓存条数」的公开访问器：当前接线建议传已加载列表大小；若要求严格零网络，可后续在仓库加 `cachedCount()`（需 AppContainer 改动，Lead 决定）。
2. `pending` 计数未暴露（marks/userdata 内部队列）：Lead 可注入现有待补发状态；默认 0 不影响展示。
3. 通知提醒提前量固定 24h；如需可配（如 1h/6h/24h）可在 `ReminderSettings` 增加选项。
4. 通知点击若 App 未登录：走现有 pendingToolId 逻辑（登录后打开通知页）——已具备，无需改。
5. 通知小图标仍用系统占位（与 task-21 相同，design 后续替换单色品牌图标）。
