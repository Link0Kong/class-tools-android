# `docs/api/HW_NOTICES_IMPL.md` — 作业 + 通知工具实现（task-7）

> 范围：`data/homework/`、`data/notices/`（只读数据仓库 + 纯函数状态机）+ `ui/screens/homework/`、`ui/screens/notices/` 两个 Compose 页面 + `ui/preview/HomeworkNoticesPreviews.kt` + 单测。
> 契约依据：`docs/BACKEND_CONTRACT.md` §3/§5、`docs/api/ENDPOINTS.md` E4/E7/E8、`docs/api/NOTICES.md`、`docs/api/KOTLIN_MODELS.md`。
> 本轮**只读**：不提供发布作业 / 编辑通知 / 管理员操作（通知 insert/update 服务端也仅管理员可写）；不写云端。

---

## 1. 文件清单

### 数据层 `app/src/main/kotlin/com/furina/classtools/data/homework/`

| 文件 | 内容 |
|---|---|
| `HomeworkModels.kt` | `HomeworkRow`（@Serializable，含 archived/done 列但前端忽略 done）、`HomeworkAttachmentMeta`/`HomeworkAttachments`（JSON 字符串解析，失败不崩，只保留元信息，`dataOf()` 按需取原文）、`HomeworkDue`/`HomeworkDueTone`/`HomeworkFilter`/`HomeworkItem` |
| `HomeworkDue.kt` | `HomeworkDueCalculator` 纯函数：`parseDue` / `evaluate`（剩余小时/天、过期、prunable>3 天）/ `toItem` / `pruneExpired` / `filter` / `sort` / `groupLabel` / `dueText` / `shortDueText` |
| `HomeworkRepository.kt` | `HomeworkRepository` 接口 + `DefaultHomeworkRepository`：`GET homework?select=*&order=ts.asc&archived=eq.false`，TTL 10min，空数组合法，失败降级缓存；`publish/archive` 保留接口返回 false（本轮只读） |

### 数据层 `app/src/main/kotlin/com/furina/classtools/data/notices/`

| 文件 | 内容 |
|---|---|
| `NoticeModels.kt` | `NoticeRow`/`NoticeSigRow`/`NoticeGroupDto`/`NoticeSegment`/`NoticeExtension`/`NoticeRecurrenceDto`、`NoticeStatus`/`NoticeBucket`/`NoticeWhen`/`NoticeStats`、`NoticeEntry`/`ManualNotice`/`NoticeManualAttachment`/`NoticesData`/`NoticesLoadResult`、`NoticeItemMeta`（Text/Image/File/Form/Link/Merged/Unknown 元信息） |
| `NoticeBodyParser.kt` | `[[CLASS_NOTICE_V1]]` 前缀识别与 group JSON 解析；`items[]` → 渲染元信息（**不把大 base64 放进 UI 模型**）；人工通知 `attachments` 元信息；首条文本/附件数统计 |
| `NoticeOverride.kt` | `override_json` 白名单（title/cat/summary/aiSummary/deadline/eventAt/expireAt/timeLabel）解析与应用；`null/""` = 清空 |
| `NoticeStatusMachine.kt` | 纯函数状态机：`parseTs`/`fmtWhen`/`conciseAction`/`deadlineOf(extensions)`/`eventAt`/`expireAt`/`whenInfo`/`statusOf`/`statsOf`/`mostUrgent`/`recurrence next`/`absRelDates`/`anchorOf` |
| `NoticesRepository.kt` | `NoticeSignature` + `NoticesRepository`/`DefaultNoticesRepository`：两级拉取（lite 签名 → 变化才拉整表）、hidden 过滤、override 应用、TTL 10min、失败降级缓存 |

### UI `app/src/main/kotlin/com/furina/classtools/ui/screens/{homework,notices}/`

| 文件 | 内容 |
|---|---|
| `homework/HomeworkScreen.kt` | 作业列表（按截止分组/排序、完成勾选、筛选 chips、下拉刷新、四态、详情弹层、附件元信息） |
| `notices/NoticesScreen.kt` | 通知列表（自动 + 人工、状态 chip、已处理勾选、置顶、筛选 全部/未处理/置顶、hidden 过滤、详情原生渲染、四态） |
| `ui/preview/HomeworkNoticesPreviews.kt` | 4 个 @Preview（作业浅色列表 / 作业深色空态 / 通知浅色列表 / 通知深色空态） |

### 测试 `app/src/test/kotlin/com/furina/classtools/data/{homework,notices}/`

`HomeworkModelsTest`、`HomeworkRepositoryTest`、`NoticeStatusMachineTest`（19 个状态机/展示用例）、`NoticeBodyParserTest`、`NoticesRepositoryTest`。

---

## 2. 公开 API（Lead 接线用）

### 2.1 作业页

```kotlin
@Composable
fun HomeworkScreen(
    onBack: () -> Unit,
    session: Session?,
    marksRepository: MarksRepository?,        // 必传（完成状态唯一真源）
    homeworkRepository: HomeworkRepository?,  // 可传 null（预览/未接线时显示空态）
    modifier: Modifier = Modifier,
    initialRows: List<HomeworkRow> = emptyList(),   // @Preview/测试注入
    initialMarks: MarksSnapshot? = null,
    forcedErrorMessage: String? = null,
    skipLoad: Boolean = false,
)
```

MainNavHost 接线建议（Lead 负责；不要我改）：

```kotlin
"hw" -> HomeworkScreen(
    onBack = { navController.popBackStack() },
    session = session,
    marksRepository = container.marksRepository,
    homeworkRepository = remember { DefaultHomeworkRepository(container.apiClient) },
)
```

### 2.2 通知页

```kotlin
@Composable
fun NoticesScreen(
    onBack: () -> Unit,
    session: Session?,
    marksRepository: MarksRepository?,
    noticesRepository: NoticesRepository?,
    modifier: Modifier = Modifier,
    initialData: NoticesData? = null,
    initialMarks: MarksSnapshot? = null,
    forcedErrorMessage: String? = null,
    skipLoad: Boolean = false,
)
```

```kotlin
"nt" -> NoticesScreen(
    onBack = { navController.popBackStack() },
    session = session,
    marksRepository = container.marksRepository,
    noticesRepository = remember {
        DefaultNoticesRepository(
            api = container.apiClient,
            isAdmin = { container.currentSession?.isAdmin == true },
        )
    },
)
```

> 也可以把两个仓库挂到 `AppContainer`（`val homeworkRepository = DefaultHomeworkRepository(apiClient)` 等），
> 这样多个页面共享 TTL 缓存；两者等价，由 Lead 决定。

### 2.3 数据层入口

```kotlin
val homework = DefaultHomeworkRepository(api = container.apiClient)
when (val r = homework.load(force = false)) {
    is HomeworkLoadResult.Success -> r.rows / r.fromCache
    is HomeworkLoadResult.Failure -> r.message
}

val notices = DefaultNoticesRepository(api = container.apiClient, isAdmin = { session.isAdmin })
when (val r = notices.load(force = true)) {
    is NoticesLoadResult.Success -> r.data   // auto / manual / hiddenCount / fetchedAt / fromCache
    is NoticesLoadResult.Failure -> r.message
}

// 纯状态机（UI 外也可用）
val status = NoticeStatusMachine.statusOf(group, nowMillis)
val next = NoticeStatusMachine.recurNextTextOf(group, nowMillis)
val display = NoticeStatusMachine.absRelDates(text, NoticeStatusMachine.anchorOf(group))
```

---

## 3. 数据层行为（与契约对应）

### 作业 `data/homework`

- 路径：`GET homework?select=*&order=ts.asc&archived=eq.false`（ENDPOINTS E4）；TTL **10min**。
- `done` 列读取但**不参与判断**；完成状态来自 `marks kind='hw'`（页面把 `MarksSnapshot` 叠加到行上）。
- 空数组是合法结果（作业列表可为空），**不**当失败。坏 JSON/坏行：容错跳过；有缓存时降级缓存。
- 截止计算：`date(+time)` 解析失败 → 无截止（不崩）；<24h「还剩 N 小时」、≥24h「还剩 N 天」、
  过期「已过期 N 小时/N 天」；**过期 >3 天只在内存过滤**（不写云）。
- 附件：`attachments` JSON 字符串解析为 `[{n,t,s,d}]` 元信息（name/mime/size/data-uri 种类），
  不预解码 base64；详情需要时用 `HomeworkAttachments.dataOf(raw,index)` 按需取。

### 通知 `data/notices`

- **两级拉取**（`docs/api/NOTICES.md` §2 / ENDPOINTS E7/E8）：
  1. `GET notices?select=id,ts,hidden,edited_at&order=ts.asc,id.asc&archived=eq.false`（~2KB）；
  2. `sig = rows.join("|") { "id:ts:hidden:edited_at" }`，与缓存签名一致 → 0 次大请求；否则
     `GET notices?select=*&order=ts.asc,id.asc&archived=eq.false`；TTL 10min。
- 自动行 = `body.startsWith("[[CLASS_NOTICE_V1]]")` → 去前缀 JSON → `NoticeGroupDto`；
  否则人工通知（附件解析元信息）。
- `hidden`：非管理员过滤掉（`hiddenCount` 计数）；管理员保留并标记（UI 右侧「已更新/置顶」语义，本页
  只在副标题显示「已隐藏 N」）。
- `override_json`：白名单 8 字段应用（`null/""` 清空）；坏 JSON 视为无覆盖。
- `items[]`：只保留元信息：文本保留全文；图片只留 caption/OCR/近似体积（不保留 base64）；
  文件/data URI 只留 name/ext/size/待获取状态/压缩包摘要；链接只留 host/account；合并消息 ≤8 条。
  解析完成后自动行的 `row.body` 与 `group.items` **清空**，避免 2MB 级 body 在 UI 层二次常驻。
- 状态机（纯函数）：优先级 `报名窗口 > 真实截止(extensions 最后一次延期) > 活动/事件 > 无时间`；
  `expireAt` 仅用于活动类自动归档；`recurrence` 永不判过期；alert（已截止但有新名单）单独展示；
  `fmtWhen` 分档与 `conciseAction` 精简；`absRelDates` 做相对日期替换（今天/明天/本周X/裸周X/裸 N 号语境判断/裸时段）。
- 已处理 = `marks kind='ntdone'`（ref = 通知组 id，即 `auto_` 前缀去掉后的 `g_*`）；
  置顶 = `marks kind='nt'`（仅人工通知有效）。两者均由 `MarksRepository` 的 StateFlow 叠加渲染（乐观更新）。

---

## 4. UI 行为

### 作业

- 顶部 `CtTopBar`「作业」+ 刷新按钮（也支持下拉刷新 `PullToRefreshBox`）；
- 筛选 chips：全部 / 未完成 / 已完成（`CtFilterChipRow`，带计数）；
- 列表按截止分组（今天/明天/MM-dd 周X/已过期/无截止），未完成在前，完成项置底并删除线；
- 卡片：Checkbox（marks hw）+ 科目/内容 2 行 + 截止 pill（语气色）+ 发布者 + 附件数；
- 详情弹层（AlertDialog）：完整内容、截止/发布者/时间、附件元信息列表、完成切换；
- 四态：加载（CtLoading）/ 空（EmptyState）/ 错误（重试）/ 正常；有缓存 + 刷新失败时顶部错误条「显示本机缓存」。
- 触控：Checkbox/IconButton 默认 ≥48dp；卡片内容区整块可点开详情。

### 通知

- 顶部 `CtTopBar`「班级通知」+ 刷新；筛选 chips：全部/未处理/置顶（带计数）；
- 卡片：`CtPressableCard`（按压回弹）；置顶人工通知左侧品牌色竖条 + 置顶按钮；
  自动通知：分类 pill + 状态 pill（状态机 label）+ 已处理 Checkbox + 标题 + 摘要 2 行 +
  时间/附件数/多源合并图标/已更新标记；
- 已完成/已处理项沉底、标题删除线；
- 详情弹层（原生渲染）：状态/分类、来源、AI 摘要、要点 bullets、结构化段落（时间/地点/要求…）、
  items 元信息（文本全文；图片/文件/收集表/链接/合并消息/未知类型只做占位说明，不联网、不 WebView）；
- 四态：加载 / 空（分筛选文案）/ 错误（重试）/ 正常；有缓存 + 刷新失败顶部错误条。
- 置顶 = 人工通知 marks nt；已处理 = 自动通知 marks ntdone（乐观更新，失败 Snackbar 提示）。

---

## 5. 单测

命令：

```powershell
$env:JAVA_HOME='C:\AndroidToolchain\jdk17'
$env:ANDROID_HOME='C:\AndroidToolchain\sdk'
cd C:\Users\Ko_Li\.openclaw\workspace\android-class-tools
& 'C:\AndroidToolchain\gradle\gradle-9.9.0\bin\gradle.bat' :app:testDebugUnitTest --console=plain
```

结果（2026-11-08 00:05，全仓最新一次运行）：

```text
BUILD SUCCESSFUL — :app:testDebugUnitTest
CLASSES=27  TOTAL=342  FAIL=0  ERR=0  SKIP=0
```

其中**本次新增的作业/通知测试 68 个全部通过**：

| 测试类 | tests | failures |
|---|---|---|
| `data.homework.HomeworkModelsTest` | 13 | 0 |
| `data.homework.HomeworkRepositoryTest` | 9 | 0 |
| `data.notices.NoticeStatusMachineTest` | 20 | 0 |
| `data.notices.NoticeBodyParserTest` | 15 | 0 |
| `data.notices.NoticesRepositoryTest` | 11 | 0 |
| **本任务合计** | **68** | **0** |

（全仓 342 含其他队友增量：数据层同步/抽签/导出/体测/分组/电量等；数字会随并行任务继续增长。）
报告：`app/build/reports/tests/testDebugUnitTest/index.html`；XML：`app/build/test-results/testDebugUnitTest/`。
主工程 `:app:compileDebugKotlin` 同一流程通过（含 Lead 接线后的 `hw`/`nt` 路由与工作台真实数据）。

> 修复记录：`absRelDates` 的「本周X/下周X」正则原先要求出现两个「周」字（`本周五` 实际是「本周」+「五」），
> 在真实日期为周五的机器上导致相对日期替换断言失败；已改为 `（本周|这周|下周）\\s*周?([一二三四五六日天])`，
> 并用固定锚点（2026-11-07 周六）断言 `本周五→11-06 / 下周一→11-09 / 周日下午→11-08`，不依赖真实今天。

覆盖点：

| 测试类 | 覆盖 |
|---|---|
| `HomeworkModelsTest` | 截止解析/非法值、剩余小时/天/过期标签、无截止、过期 >3 天才清理、prune、排序（未完成在前/完成沉底/无截止最后）、筛选、分组标题、短/完整截止文本、附件解析（元信息/体积估算/坏 JSON 容错/按需取原文）、体积格式化、toItem 组合 |
| `HomeworkRepositoryTest` | 契约路径与 token 头、TTL、force、空数组合法、坏 JSON、网络失败降级缓存、坏行跳过、parseRows 容错、写接口只读 |
| `NoticeStatusMachineTest` | urgent/today/soon/active/expired、extensions 最后一次延期、报名窗口优先、expireAt 活动归档、recurrence 不判过期 + weekly/monthly_day 下次时间、alert、无时间、fmtWhen 分档、conciseAction、whenInfo 优先级、statsOf、mostUrgent、absRelDates（今天/明天/本周/下周/裸周/不误伤楼号）、anchorOf |
| `NoticeBodyParserTest` | CLASS_NOTICE_V1 前缀/解析/坏 JSON、文本与补充、图片元信息（不含 base64）、文件待获取、收集表/链接、合并消息上限、未知 kind、首条文本/附件数、人工附件、override 白名单/清空/坏 JSON、签名格式、buildData 分流 + hidden + body 清理 |
| `NoticesRepositoryTest` | 两级拉取（lite→full）、签名未变不拉整表、签名变化重拉、TTL、hidden 管理员/非管理员、override 应用、坏 body 跳过、一级/二级失败降级、无缓存 Failure、解析容错、cached |

---

## 6. 已知限制（本轮）

1. **只读**：无作业发布/删除、无通知发布/编辑（服务端 `notices` 写权限也仅管理员）；`HomeworkRepository.publish/archive` 保留签名但返回 false。
2. **附件/图片不预览**：详情只展示元信息；图片 base64 不解码、不进入 UI 模型；文件/链接不打开（后续增量做 Intent/图片解码）。
3. **通知详情无 WebView 兜底**：未知 `items[].kind` 只显示占位；富文本/PDF 后续增量再评估原生或受限 WebView。
4. **无 ViewModel/SavedStateHandle**：旋转/分屏会重新加载（仓库有内存缓存，体感影响小）；后续统一切 ViewModel 时迁移。
5. **人工通知的隐藏/删除**不做（隐藏项走 `userdata.hide.ntHide`，本轮未接 UD 的 hide section）；管理员编辑不做。
6. **`homework` 的个人隐藏**（`userdata.hide.hwHide`）未接；普通完成勾选已接 marks。

## 7. 与后续任务的接口

- 导出：作业清单导出可复用 `features/export/ExportCardSpec` 的 `TextBlock`；通知详情若后续需要「原文/附件」可接 `HomeworkAttachments.dataOf` 同类按需读取。
- 分组/抽签等其他工具复用 `CtTopBar`/`CtFilterChipRow`/`CtPressableCard`/状态 pill 模式。
