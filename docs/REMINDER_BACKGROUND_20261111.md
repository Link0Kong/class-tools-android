# 后台通知可靠性（v3.7，用户反馈第 12 条）

> 用户原话：「请调整 app 端的通知端口，确保在应用未在后台运行时也可收到通知」。
> 本文记录**根因排查（代码路径证据）**、实现方案、硬限制与验证证据。
> 版本：**v3.7.0 / versionCode 13**；无新增第三方依赖（复用既有 WorkManager + AlarmManager + SharedPreferences）。

## 1. 目标

App 被切到后台、进程被系统/厂商清理、甚至被杀死后，仍能收到：

1. **班级新通知**（采集管线写 `public.notices` 的新行）；
2. **作业截止/每日汇总**、**通知截止/活动**、**课表下一节**等既有提醒；
3. **用户单条「提醒我」**（截止前 1 天 / 1 小时 / 截止时刻）尽量准点触发。

## 2. 现状排查与根因（为什么「App 被杀后收不到」）

以下均为 v3.6.0 代码路径事实（修复前）：

### 2.1 后台周期任务只读内存缓存，且完全不联网（主因）

- `WorkManagerReminderScheduler.ensureScheduled()` 建的是 15 分钟周期 `ReminderWorker`，**没有网络约束**；
- `ReminderWorker.doWork()` 只执行 `ReminderEngine(applicationContext).runNow()`；
- `ReminderEngine` 的数据来自 `ContainerReminderDataProvider`：
  - `homeworkRepository.cached()` / `noticesRepository.cached()` 分别读 `DefaultHomeworkRepository.cache` /
    `DefaultNoticesRepository.cache` —— 都是**进程内 `var` 字段，无磁盘持久化**；
  - `timetableRepository.cached()` 有磁盘缓存，但只有课表；
- 结论：进程被杀后 Worker 冷启动，**作业/通知缓存为空**；即使 Worker 跑起来，`ReminderPlanner`
  也规划不出任何事件；而且**没有任何网络请求**去发现"新通知"。→ 后台永远收不到新通知。

### 2.2 后台 Worker 拿不到身份 token（即使加了联网也会失败）

- `ApiClient` 的 `tokenProvider = { activeSession?.token }`；
- `AppContainer.activeSession` 只在 UI 侧 `ClassToolsRoot`（`LaunchedEffect(sessionState)`）里由
  `updateActiveSession()` 赋值；
- WorkManager Worker 在「App 进程已被杀」时冷启动本进程：`ClassToolsApp.onCreate` → `AppContainer`
  构造完成时 `activeSession == null`，且**没有任何后台恢复会话的路径**；
- 结论：后台请求会缺少 `X-Class-Token` → 401/RLS 拒绝 → 静默失败。

### 2.3 单条提醒只有 WorkManager OneTime（会显著延迟）

- `WorkManagerOneShotScheduler` 用 `OneTimeWorkRequest.setInitialDelay(fireAt-now)`；
- WorkManager 在 Doze/App Standby/厂商省电下**不保证准点**（可延迟数十分钟到数小时），
  用户设「截止前 1 小时」可能到截止后才弹；
- 未使用 `AlarmManager.setExactAndAllowWhileIdle`；Android 12+ 精确闹钟特殊权限完全未处理；
- `ReminderBootReceiver` 只重建周期任务，**不重排单条提醒**（AlarmManager 闹钟不跨重启，旧实现虽由
  WorkManager 持久化，但厂商清理/强制停止后不可靠）。

### 2.4 通知渠道懒创建且重要性偏低

- `AndroidReminderNotifier.ensureChannels()` 只在「发通知前」才创建渠道（首次 notify/engine 运行时）；
- 作业（`homework_due`）与通知汇总（`notice_due` 的汇总类）按 `highPriority=false` 创建为
  `IMPORTANCE_DEFAULT` → 无横幅、提醒感弱；
- 渠道创建后应用无法强制提高系统重要性（用户可改），而旧实现**没有渠道诊断**，用户不知道被关。

### 2.5 权限与后台限制没有可观测性

- `POST_NOTIFICATIONS` 有声明与四态判定（`PermissionResolver`），但没有「后台提醒是否真的在工作」的诊断：
  上次轮询时间、通知渠道重要性、电池优化（Doze 白名单）、精确闹钟授权、厂商自启动均不可见。

### 2.6 根因结论

| # | 根因 | 影响 |
|---|---|---|
| R1 | 后台 Worker **不联网**、只读**内存**缓存（进程死即空） | 新通知永远不会被发现 |
| R2 | 后台无 token 恢复路径（`activeSession` 仅 UI 赋值） | 后台请求鉴权失败 |
| R3 | 单条提醒仅 WorkManager OneTime，无 AlarmManager 精确调度 | 到点提醒延迟大/不可靠 |
| R4 | 渠道懒创建 + 部分渠道 DEFAULT + 无渠道/电池优化诊断 | 用户感知「没收到」且无法自查 |
| R5 | 开机/更新只重建周期任务，不重排单条闹钟 | 重启后单条提醒可能丢失 |

## 3. 实现

### 3.1 后台通知轮询管道（核心，R1/R2）

`data/reminder/ReminderBackground.kt`（纯逻辑）+ `data/reminder/ReminderBackgroundAndroid.kt`（Android 装配）：

- **周期任务升级**：`WorkManagerReminderScheduler.ensureScheduled()` 给 15 分钟周期 `ReminderWorker`
  加 `Constraints(NetworkType.CONNECTED)`；`ReminderWorker` 改为执行 `ReminderBackgroundPipeline.run()`：
  1. **恢复身份**：从 `SessionStore`（DataStore）读会话 → 校验 29 天本地有效期/调试会话 →
     `container.updateActiveSession(session)`（让 `ApiClient` 带上 `X-Class-Token`）；
     无 token/过期 → **静默**记录原因并返回（不联网、不打扰）；
  2. **拉通知轻量签名**：`GET notices?select=id,ts,hidden,edited_at&order=ts.asc,id.asc&archived=eq.false`
     （复用 `DefaultNoticesRepository.LITE_PATH` / `parseSigRows`，约 2KB）；
  3. **首次运行只建基线**：把当前全部通知 id 写入本地 `knownIds`，**不通知历史通知**（防轰炸）；
  4. **签名未变化** → 直接结束（不拉整表、省流量）；
  5. **签名变化** → 先算新增 id，再拉整表（`noticesRepository.load(force=true)`，两级拉取口径不变）；
     用本地 `knownIds` 去重（双保险：id 集合 + 事件 key）；
  6. **新增通知 → 本地通知**：`ReminderKind.NOTICE_DUE` + 渠道覆盖
     `notice_inbox_high`（IMPORTANCE_HIGH + 声音/震动/锁屏可见），
     deep link `toolId="nt"` → 点击直接进 App 通知页；单次最多 3 条，超出合并为一条汇总；
  7. **安静时段**（既有 `ReminderSettings.quietHoursEnabled`）内只更新基线不弹，避免夜间骚扰/事后补炸；
  8. **刷新作业缓存**（`homeworkRepository.load(force=false)`，TTL 内不重复拉）→ 跑既有
     `ReminderEngine.runNow()` 发送作业汇总/截止、课表、通知到点提醒；
  9. **诊断落盘**（SharedPreferences `ct_reminder_bg_poll`）：上次轮询/最近成功时间、结果、累计次数、
     已通知 id 集合；供「权限与提醒」诊断卡展示。
- **静默失败**：无 token、离线、接口异常、401 均返回 `failed` 状态记录原因，不抛异常、不重试风暴
  （周期任务下一轮自然重试）。
- **去重/容量**：本地 `knownIds` 上限 400（保留当前接口返回的全部 id + 历史），事件 key `nt-new:<id>`。

### 3.2 单条提醒尽量精确（R3/R5）

`data/reminder/ReminderAlarm.kt`（纯逻辑）+ `data/reminder/ReminderAlarmAndroid.kt`（Android）：

- `HybridOneShotScheduler`（`ReminderOneShotFactory.create()` 默认返回）：
  - 保留原有校验：通知权限、`TooLate`（60s 最小提前量）、每类上限 20 条；
  - `OneShotDeliveryDecision`：通知权限已授予 且 `AlarmManager.canScheduleExactAlarms()`（Android 12+
    特殊权限，低版本视为可用）→ **`EXACT_ALARM`**，否则 **回退旧 WorkManager OneTime**；
  - `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, fireAt, PendingIntent)`（Doze 下仍准点）；
  - 切换投递方式时清理另一侧旧任务，避免同一提醒双发；
- `ReminderAlarmReceiver`（Manifest 显式注册、`exported=false`）：闹钟触发 → 直接发本地通知
  （不依赖 UI/Activity 进程）→ 清理排程元数据；权限缺失静默跳过；
- `ReminderBootReceiver`（BOOT_COMPLETED / LOCKED_BOOT_COMPLETED / MY_PACKAGE_REPLACED）：
  重建周期任务（新网络约束）+ `ReminderAlarmRuntime.rearmPending()` →
  读 `ct_oneshot_pending` 元数据：未来项重排 AlarmManager（`targetAt` 一并保留）、
  已到点但在 5 分钟宽限内 → 立即补发一次、过旧 → 清理；
  WorkManager 回退模式调用 `WorkManagerOneShotScheduler.rearm()` 重新入队；
- App 启动（`ClassToolsApp.onCreate`）也做一次幂等重排（覆盖强制停止后重新打开）。

### 3.3 通知渠道提前创建 + 高优先级（R4）

`data/reminder/ReminderChannels.kt`（纯规格）+ `ReminderChannelInstaller.kt`（Android）：

- 渠道规格：`homework_due`（DEFAULT）、`class_soon`（HIGH）、`notice_due`（HIGH）、
  **`notice_inbox_high`（新通知专用，HIGH + 震动 + 锁屏 PUBLIC，全新 id 保证升级用户也拿到 HIGH 行为）**；
- 创建时机提前到 `ClassToolsApp.onCreate()`、每次提醒发送前、开机/更新广播，并幂等；
- `AndroidReminderNotifier.notify()` 使用事件渠道覆盖（新通知走新渠道），并
  `setVisibility(VISIBILITY_PUBLIC)` / 高优先级，声音/震动由渠道控制。

### 3.4 权限四态引导 + 诊断 + 电池优化/自启动说明（R4）

`data/permission/PermissionGuidance.kt`（纯文案）+ UI：

- 四态引导：已授予 / 未请求 / 拒绝过 / 永久拒绝（`PermissionResolver` 既有判定），分别给
  「已开启」「开启通知权限」「重新授权」「去系统设置开启」动作与说明；
- **「我的 → 权限与提醒」新增「后台提醒（诊断）」卡**（只读、不联网）：
  后台检查是否已启用/基线、上次轮询与最近成功时间、上次结果、累计轮询次数、通知权限、
  新通知渠道系统真实重要性、电池优化是否受限、精确闹钟是否授权；
- 新增「后台提醒与省电（建议）」卡：电池优化说明、厂商自启动说明（小米/华为/OPPO/vivo）、
  精确闹钟说明、硬限制口径；**不申请 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`、不弹系统豁免框**；
- 「提醒」设置页说明更新：后台约 15 分钟联网检查、单条提醒优先精确闹钟/回退 WorkManager；
  「立即检查 + 诊断」同时展示后台诊断。
- 通知权限被拒时 `PermissionTexts` 文案更新：说明后台检查用途与渠道检查提示。

### 3.5 数据与隐私

- 只新增本机 SharedPreferences：`ct_reminder_bg_poll`（后台轮询状态：**通知 id/签名/时间/结果**），
  沿用 `ct_oneshot_pending`（新增 `targetAt` 字段，向后兼容旧 JSON）。
- 不上传任何新数据、不新增第三方依赖、不改 userdata v6 / marks 契约；诊断文案不含通知正文/学号/姓名。

## 4. 硬限制（如实）

1. **无厂商推送 / FCM 时无法实时秒达**：本实现是「本机周期轮询」，系统 WorkManager 周期下限
   **约 15 分钟**；息屏/Doze/App Standby/厂商清理会进一步延迟（可能数十分钟到小时级）。
2. **需要网络**：离线时后台任务等待网络；恢复网络后的下一次窗口才能发现新通知。
3. **需要 token 有效**：班级 token 30 天有效；本地按 29 天判断，过期后后台静默跳过、App 打开后回身份页
   （与既有安全口径一致）。
4. **需要通知权限 + 系统通知总开关**：被拒/被永久关闭/渠道被关闭时不会通知；诊断卡会显示具体原因。
5. **精确闹钟是特殊权限**（Android 12+「闹钟和提醒」）：未授权时单条提醒回退 WorkManager，
   会有分钟级到小时级延迟；授权后仍受系统 Doze 白名单影响（电池优化不受限时更准）。
6. **厂商自启动/后台限制**：部分 ROM 清理后台后不恢复 WorkManager，需要用户允许自启动/后台运行
   （设置页已给说明，不强制）。
7. **首次运行只建立基线**：安装/登录后第一次后台轮询不会把历史通知全部弹出（防轰炸），
   之后的新通知才提醒。

## 5. 用户侧设置建议（设置页/文档口径）

1. 系统设置 → 应用 → 班级工具站 → **通知：允许**，并确认「新通知提醒」「作业提醒」渠道未被关闭；
2. Android 12+：特殊应用权限 → **闹钟和提醒：允许**（单条提醒更准）；
3. 电池：设为**不受限制**（或允许后台运行）；
4. 小米/华为/OPPO/vivo：允许**自启动**、后台运行、锁定后台任务；
5. 打开 App 会立即刷新一次，可临时核对「权限与提醒 → 后台提醒（诊断）」。

## 6. 验证证据

- `:app:testDebugUnitTest`：**BUILD SUCCESSFUL，1056 tests / 0 failures / 0 errors / 0 skipped（83 suites）**；
  基线 994/0 无回归；新增 62 项：
  - `ReminderBackgroundTest` **25**：周期下限、轮询开关、新增行/签名口径/knownIds 截断、首次只建基线、
    签名未变不拉整表、新增去重与高优先级事件、隐藏通知不提醒、权限/开关关闭不联网、签名/整表失败静默保留基线、
    安静时段静默、通知失败计数、状态编解码容错、诊断文案；
  - `ReminderAlarmTest` **22**：精确闹钟 vs WorkManager 回退决策、权限缺失回退、载荷/标签净化/deep link、
    `targetAt` 编解码与旧 JSON 兼容、重排计划（未来/宽限内/过期/超远）、混合调度器（精确/回退/切换清理/
    权限缺失/TooLate/上限/取消/取消全部/列表过滤）；
  - `ReminderChannelsTest` **15**：渠道唯一性/新通知 HIGH 规格/类型映射/事件渠道覆盖/重要性文案、
    四态权限引导、电池优化/自启动/精确闹钟/硬限制文案。
- `assembleRelease`：**BUILD SUCCESSFUL（2m 9s）**；APK **2,774,274 B**，
  SHA-256 **`FA3DEB4C9D84CD69822A9B99BE1CBF48FD1D0A60422EA5931CD4E3952D7FF353`**；
  `apksigner verify` v2 通过（同一 keystore 证书）；`aapt2` 实测 versionCode 13 / versionName 3.7.0、
  `SCHEDULE_EXACT_ALARM` 权限与 `ReminderAlarmReceiver(exported=false)` 已打入。
  完整构建/门禁记录见 `docs/RELEASE_BUILD.md` §17。
- **边界**：构建机无连接设备，未做安装/冷启动与真机到达验证（由 Lead 合并后统一执行）；
  后台系统调度行为（Doze/厂商/精确闹钟授权）只能在真机验证。

## 7. 回滚

- 代码回退到 v3.6.0 对应提交即可（无 DB 迁移、无 userdata 契约变更、无新依赖）；
- 用户已创建的 AlarmManager 闹钟在回退版本中不会被触发（接收器类不存在），
  排程元数据会在下次开机/启动被旧版忽略（旧版只读 kind/id/label/fireAt，忽略新增 targetAt）。
