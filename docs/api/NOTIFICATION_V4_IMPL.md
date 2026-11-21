# 通知系统 v4（对齐鸿蒙端「通知设置」：模块化开关 + 新增类/投票/水费通知 + 测试通知）

> 状态：**已交付**（2026-11-21）。用户要求：「参考鸿蒙端增加安卓端的可进行系统通知的工具以及内容」。
> 基线 v3（周期 Worker + 错过补发 + 分组 + 诊断）；v4 在**不改 WorkManager 调度模型**的前提下，
> 把鸿蒙 `features/notify/*` 的通知模块、内容与设置面板移植到安卓。

---

## 1. 与鸿蒙端的对应关系

| 能力 | 鸿蒙端 | 安卓端（v4） |
|---|---|---|
| 设置模型 | `NotifySettings`（master + 9 模块 + leadMinutes/classLeadMinutes） | `ReminderSettings`（`enabled` + 10 模块 + `leadMinutes`/`classLeadMinutes`；多一个安卓保留的「每日汇总」） |
| 规划 | `NotifyPlanner`（新增/截止/开抢/投票结果/水费） | `ReminderPlanner`（同名函数 + `planHomeworkNew`/`planNoticesNew`/`planNoticeSignup`/`planVotes`/`planWater`） |
| 首轮基线 | `NotifySnapshot`（preferences JSON） | `ReminderSnapshot` + `ReminderSnapshotStore`（SharedPreferences JSON） |
| 发送 | `notificationManager.publish` | `AndroidReminderNotifier`（NotificationCompat + 渠道 + 分组） |
| 测试通知 | `NotifyCenter.sendTest()` | `ReminderEngine.testNotification()` / `ReminderActions.testNotification(context)` |
| 设置面板 | `MeScreen.notifyPanel`（分组 + 开关 + 胶囊 + 测试按钮） | `ReminderSettingsScreen`（同结构：总开关 / 作业 / 上课 / 通知 / 投票与水费 / 提醒时间 / 确认提醒效果 / 诊断） |

---

## 2. 通知模块与内容（9 + 1）

| 模块 | 默认 | 触发 | 标题 | 正文（示例） | 点击跳转 |
|---|---|---|---|---|---|
| 新作业通知 `homeworkNewEnabled` | 开 | 与快照比较出现新作业（首轮只建基线） | `新作业` | `高等数学 · 截止 11-10 23:59` | `hw` |
| 作业即将截止 `homeworkDueEnabled` | 开 | 截止时间 − `leadMinutes` | `作业即将截止` | `高等数学 · 11-10 23:59 截止` | `hw` |
| 上课提醒 `classRemindEnabled` | 开 | 下一节开始 − `classLeadMinutes` | `上课提醒 · 高等数学` | `14:30–15:15 · 高等数学 · 01104 · 还有 30 分钟` | `tt` |
| 新通知提醒 `noticeNewEnabled` | 开 | 与快照比较出现新通知（首轮只建基线） | `新通知` | `陈同学 · 关于主题班会的通知` | `nt` |
| 活动截止提醒 `noticeDeadlineEnabled` | 开 | 活动/截止时间 − `leadMinutes` | `通知即将截止：…` / `通知即将开始：…` | `提交材料 · 11-10 18:00 截止 · 班级通知群` | `nt` |
| 活动开抢提醒 `noticeSignupEnabled` | 开 | 报名开始 − 5 分钟 | `活动开抢提醒` | `秋游报名 · 11-10 20:00 开始报名` | `nt` |
| 新投票提醒 `voteNewEnabled` | 开 | 与快照比较出现新投票 | `新投票` | `周三班会时间投票` | `vote` |
| 投票结果可查看 `voteResultEnabled` | 开 | 投票结束（或「截止后可见结果」的截止 + 1 分钟） | `投票结果可查看` | `班服颜色` | `vote` |
| 水费扣除提醒 `waterDeductEnabled` | 开 | 本机水量记录产生用量/水费（指纹变化） | `水费扣除提醒` | `2026-11 · 用量 10.00 吨 · 水费 ¥6.06（扣电 10.4 度）` | `water` |
| 每日汇总 `digestEnabled`（安卓保留） | 开 | 每天 20:00（可改）汇总未来 48h 截止的作业/通知 | `作业截止汇总` / `通知截止汇总` | `2 项作业即将截止：高数 11-10 23:59；英语 11-11 18:00` | `hw` / `nt` |

统一提前档位：`10/30/60/120/1440` 分钟（默认 60）；上课提前：`5/10/15/30` 分钟（默认 10）。
开启「每日汇总」时，**已被汇总覆盖**的作业/通知不再单独提醒（避免一晚两条）；关闭汇总后每个截止项单独提醒。

---

## 3. 数据与可靠性

### 3.1 快照（新增类不刷屏）
- 首次运行（`initialized=false`）**只记录基线不推送**；权限缺失时也写基线（授权后不会把历史消息当新增）；
- 各 id 列表保留最近 300 条；只存 id 与一个水费指纹（`<roomverify>|<month>|<reading>`），不含正文/姓名/学号；
- 存储：`SharedPreferences("ct_reminder_snapshot")` + `kotlinx.serialization` JSON；坏数据回退空快照。

### 3.2 数据来源（只读本机缓存，不额外联网）
- 作业：`homeworkRepository.cached()` + marks `hw`；通知：`noticesRepository.cached()` + marks `ntdone`；
- 课表：`timetableRepository.cached()` → `TtCalendar.plan`；
- 投票：`voteRepository.cachedPolls()`（v4 新增，只读列表缓存；列表由投票页刷新）；
- 水量：`userdata.water` + 关注宿舍 `userdata.power.sel`（未选时退回第一条有记录的宿舍）。

### 3.3 通知渠道/分组（v3 规则不变）
- 渠道：`homework_due` / `class_soon` / `notice_due` / `vote_alert`（新）/ `water_alert`（新）/ `ct_test`（测试）；
- 分组：作业 / 上课 / 通知 / 投票（新）/ 水费（新）/ 测试；子通知静默 + 组摘要提示一次；
- 去重：同 key 当日不重复 + 20h 冷却 + 7 天保留；Doze 延迟补发沿用 v3。

### 3.4 权限
- `POST_NOTIFICATIONS`（API 33+）未授予时引擎静默返回 `disabledReason=通知权限未开启`；
- 设置页顶部横幅 + 总开关内「去开启权限」；测试通知在权限缺失时返回 `PermissionMissing`。

---

## 4. 公开 API（v4 新增/变更）

```kotlin
// 设置（DataStore；含 v1–v3 旧键迁移）
data class ReminderSettings(
    enabled, homeworkNewEnabled, homeworkDueEnabled, classRemindEnabled,
    noticeNewEnabled, noticeDeadlineEnabled, noticeSignupEnabled,
    voteNewEnabled, voteResultEnabled, waterDeductEnabled, digestEnabled,
    leadMinutes, classLeadMinutes, homeworkDigestHour/Minute,
    quietHoursEnabled/Start/End,
) {
    val anyModuleEnabled: Boolean
    val anySectionEnabled: Boolean        // enabled && anyModuleEnabled
    fun enabledCount(): Int               // 已开启模块数（设置页/我的页）
    companion object {
        val LEAD_OPTIONS: List<Int> = listOf(10, 30, 60, 120, 1440)
        val CLASS_LEAD_OPTIONS: List<Int> = listOf(5, 10, 15, 30)
        fun normalizeLead(minutes: Int): Int
        fun normalizeClassLead(minutes: Int): Int
        fun leadLabel(minutes: Int): String  // "10 分钟" / "1 小时" / "1 天"
    }
}

// 规划器（纯函数）
ReminderPlanner.planHomeworkNew(rows, doneIds, now, settings, snapshot, zone)
ReminderPlanner.planNoticesNew(notices, handledIds, now, settings, snapshot)
ReminderPlanner.planNoticeSignup(notices, handledIds, now, settings)
ReminderPlanner.planVotes(polls, now, settings, snapshot)
ReminderPlanner.planWater(roomKey, room, now, settings, snapshot)
ReminderPlanner.nextSnapshot(rows, notices, polls, waterRoomKey, waterRoom, previous)
ReminderPlanner.waterFingerprint(roomKey, month, reading)

// 快照存储
interface ReminderSnapshotStore { suspend fun load(): ReminderSnapshot; suspend fun save(s) }
class SharedPrefsReminderSnapshotStore(context)   // ct_reminder_snapshot
object ReminderSnapshotCodec { encode / decode }

// 测试通知（设置页「发送测试通知」）
suspend fun ReminderEngine.testNotification(): ReminderTestNotificationResult
object ReminderActions { suspend fun testNotification(context): ReminderTestNotificationResult }
sealed interface ReminderTestNotificationResult { Sent; PermissionMissing; Failed(message) }

// 点击跳转工具白名单新增 vote/water
ReminderIntentFactory.TOOL_IDS = setOf("hw", "tt", "nt", "vote", "water")
```

---

## 5. 设置页（我的 → 通知设置）

结构对齐鸿蒙 `notifyPanel`：
1. **允许通知**（总开关；附系统权限状态与「去开启权限」）；
2. **作业提醒**：新作业通知 / 作业即将截止 / 每日汇总；
3. **上课提醒** + 提前时间胶囊（5/10/15/30 分钟）；
4. **通知提醒**：新通知 / 活动截止 / 活动开抢；
5. **投票与水费**：新投票 / 投票结果 / 水费扣除；
6. **提醒时间**：截止类统一提前胶囊（10 分钟 / 30 分钟 / 1 小时 / 2 小时 / 1 天）；
7. **确认提醒效果**：发送测试通知（本地立即发一条，检查权限与样式）；
8. **检查与诊断**：立即检查 + 诊断（权限/设置/缓存/上次运行/补发窗口），随后可「管理权限」。

「我的」页入口文案：`通知设置 · 已开启 N 项 · 截止提前 X` / `未开启 · 点击设置` / `通知权限未开启 · 点击设置`。

---

## 6. 兼容与迁移

- **旧键迁移**：`homework_enabled`/`course_enabled`/`notice_enabled` → 对应模块全开/全关；
  `homework_lead_days`（0/1/3）→ `leadMinutes`（0→10 分钟、1/3→1 天）；`course_lead_minutes` → `classLeadMinutes`；
  写回时同步写旧键，回退旧版本行为不突变。
- **默认值变化**：新增 9 个模块默认开启、每日汇总默认开启（保持 beta20 及以前的实际体验）；
  默认上课提前从 15 分钟改为 10 分钟（对齐鸿蒙），用户原值在迁移时保留（5/10/15 均合法）。
- 通知设置仍是**设备级本机偏好**，不进云端 userdata。

---

## 7. 测试

```text
ReminderPlannerTest       37（新增类/开抢/投票/水费/快照/档位文案）
NoticeReminderPlannerTest 29（提前窗口改统一 leadMinutes）
ReminderEngineTest        19（测试通知三态、首轮基线、权限缺失建基线、投票/水费接线）
ReminderOneShotTest       20（诊断摘要新字段）
MeStatsSectionTest        13（入口文案「已开启 N 项」）
全量：:app:testDebugUnitTest 通过（见发版记录）
```
