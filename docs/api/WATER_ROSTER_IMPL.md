# `docs/api/WATER_ROSTER_IMPL.md` — 水量 + 名册工具实现（task-11）

> 状态：**已实现并全绿**（compile + 单测 + 预览）。
> 契约依据：`docs/api/USERDATA_V6.md`（water/roster section）、`docs/BACKEND_CONTRACT.md` §7.6/§7.7、`web/tools/tests/_water_contract.md` §0/§1/§2/§7、`design/SCREENS.md` §11/§12、`mp-class-tools/utils/water.js|roster.js`。
> 数据真源：`userdata` v6 的 `water` / `roster` section（整段 LWW，随账号同步）；名册成员来自 `class_members`（6h 缓存）。**无新增网络端点**。

---

## 1. 文件清单

### 领域层 `features/water/`（纯 Kotlin，无 Android 依赖）

| 文件 | 内容 |
|---|---|
| `WaterModels.kt` | `WaterRules`（0.58 元/度、3.03 元/吨、2 吨·5 度/人/月、4/6 人间、≤120 条、10 个月）、`WaterRecord`、`WaterRoomState`、`WaterState`、`WaterCharge`、`WaterMonthStats`、`WaterRecordReview`（Valid/Invalid，含同月覆盖标记） |
| `WaterCodec.kt` | `water` section 编解码：键序 `rooms→p/recs→m/r/ts`；人数非 4/6→4、非法月份丢、读数非有限/负数丢、同月后者覆盖、按月升序、≤120、坏房间丢弃；整数读数输出整数（与 `data/sync/WaterNormalizer` 对齐） |
| `WaterMonth.kt` | 月份状态机：`normalize/shift/withYear/current/label/shortLabel/monthsOfYear/daysInMonth/elapsedDays`，年份边界 2000–2100 钳制 |
| `WaterCalculator.kt` | `freeWater/freeElec`、`previousRecord/nextRecord/recordOf`、`charge`（用量=本次−上一条；水费=超出 2×人数吨 ×3.03；折合扣电=水费÷0.58）、`stats`（本月用量/日均/对比上月/免费额度/上次抄表）、`ruleLines` |
| `WaterMutator.kt` | `review`（月份/读数校验：≥0、2 位、不得小于上月、不得大于下一条、同月覆盖标记）、`put/delete/withPersons/clearRoom` 不可变变更 |

### 数据层 `data/water/`

| 文件 | 内容 |
|---|---|
| `WaterRepository.kt` | `WaterRepository` 接口 + `UserDataWaterRepository`：`load()` 读 `currentSections.water`；`save(state)` 替换 water section 后 `updateCurrent`（保留其它 section，含 `hide`）；`clearRoom(rv)`；失败静默（同步层待补发/缓存兜底） |

### 页面 `ui/screens/water/`、`ui/screens/roster/`

| 文件 | 内容 |
|---|---|
| `water/WaterScreen.kt` | 关注宿舍卡（沿用 `userdata.power.sel`，未选时引导并在本页写回 power.sel）、4/6 人间切换、月份选择器（‹ 上月 / YYYY年MM月 / 下月 › + 12 月网格弹层带抄表圆点 + 「本月」）、新增抄表（读数校验 + 即时预览水费/扣电 + 同月覆盖确认）、统计卡、抄表历史（删除确认）、计费规则卡；四态；≥600dp 两栏；隐私（无姓名/学号，不导出）。公开 `WaterMonthGrid` 供预览 |
| `roster/RosterScreen.kt` | 「名册 / 点名」分段页签；名册：搜索 + 全部/班委/点名名单筛选 + 座号/姓名/职务徽章 + 勾选加入点名；点名：统计条（应到/已处理/到/假/未点）、沿用抽签名单/全选/清空/重置、自适应网格（到/假按钮，已处理沉底）；写 `userdata.roster`（按姓名迁移状态）；四态；≥600dp 自适应 |

### 预览 / 测试

- `ui/preview/WaterRosterPreviews.kt`：**9 张 @Preview**（24–32）：水量记录浅色/6 人间深色/空态/月份网格、名册浅色/深色/空态、点名进行中/空名单深色。
- `app/src/test/.../features/water/WaterLogicTest.kt`：**21 用例**。
- `app/src/test/.../features/roster/RosterLogicTest.kt`：**15 用例**。

---

## 2. 公开签名（Lead 接线）

```kotlin
@Composable
fun WaterScreen(
    onBack: () -> Unit,
    session: Session?,
    userDataRepository: UserDataRepository?,
    modifier: Modifier = Modifier,
    clock: () -> Long = System::currentTimeMillis,
    initialRooms: Map<String, WaterRoomState> = emptyMap(), // @Preview 注入
    initialSelectedRv: String = "",                         // @Preview 注入
    initialMonth: String = "",                              // @Preview 注入
    skipLoad: Boolean = false,                              // @Preview/测试
    forcedErrorMessage: String? = null,                     // 错误态注入
)

@Composable
fun RosterScreen(
    onBack: () -> Unit,
    session: Session?,
    classMembersRepository: ClassMembersRepository?,
    userDataRepository: UserDataRepository?,
    modifier: Modifier = Modifier,
    initialMembers: List<RosterMember> = emptyList(),        // @Preview 注入
    initialRoster: RosterState? = null,                     // @Preview 注入
    initialTab: RosterTab = RosterTab.ROSTER,               // RosterTab.ROSTER / RosterTab.ROLL
    skipLoad: Boolean = false,
    forcedErrorMessage: String? = null,
)
```

MainNavHost 接线（由 Lead 完成，`ToolCatalog` 已有 `water` / `roster` id）：

```kotlin
"water" -> WaterScreen(
    onBack = { navController.popBackStack() },
    session = session,
    userDataRepository = container.userDataRepository,
)
"roster" -> RosterScreen(
    onBack = { navController.popBackStack() },
    session = session,
    classMembersRepository = container.classMembersRepository,
    userDataRepository = container.userDataRepository,
)
```

领域层可直接复用（抽签/分组选择名单等）：

```kotlin
val state = WaterCodec.fromJson(userDataRepository.currentSections.first()["water"])
val next = WaterMutator.put(state, "1-13---324", WaterRecord("2026-11", 130.75, now))
val stats = WaterCalculator.stats(next.rooms.getValue("1-13---324"), "2026-11", now)

val roster = RosterCodec.fromJson(userDataRepository.currentSections.first()["roster"])
val members = RosterLogic.membersOf((repository.load() as ClassMembersResult.Success).members)
val roll = RosterLogic.withRollSel(roster, seats, members) // 按姓名保留到/假
```

---

## 3. 行为与契约

### 水量

- 数据：`water = {rooms:{<roomverify>:{p:4|6, recs:[{m:"YYYY-MM", r:<吨>, ts:<ms>}]}}}`，**整段 LWW**；写回时保留 `userdata` 其它 section（含 `hide`）。
- 关注宿舍：沿用 `userdata.power.sel`（17 间目录复用 `data/power/PowerModels.PowerRooms`）；本页选择时写 `power.{sel,at}` 并保留 `low`。**若无关注宿舍 → 空态引导，0 请求**。
- 计费：`2 吨/人/月` 免费，超出 × `3.03` 元/吨，折合扣电 = 水费 ÷ `0.58`（1 位）；6 人间免费 12 吨；不按阶梯价。
- 统计：本月用量（本次−上一条）、日均（**当前月按自然日已过天数**，历史月按整月天数）、对比上月（用量差）、水费、折合扣电、上次抄表；**首次记录不算用量**（不伪造 0）。
- 校验：读数 ≥0、两位小数；不得小于上一条、不得大于下一条；同月重复 → 确认覆盖。
- 边界：月份 2000-01..2100-12；每间 ≤120 条（超出保留最近月份）；坏数据丢弃不崩。
- 隐私：只显示楼栋+房间号+读数；不含姓名/学号；**不导出、不分享**。

### 名册 / 点名

- 名单：`ClassMembersRepository`（`class_members`，TTL 6h，失败回退缓存）；只显示姓名/座号/职务/徽章，**不显示学号**；不导出名单。
- 状态：`roster = {rosterSel, rollSel, rollSt, rollNames}`；`rollSt` key = `rollSel` 排序后的下标（0-based），value `in`/`ab`（读取兼容「到/假」）；写回时重建 `rollNames = "座号 姓名"` 多行。
- 点名名单变化时**按姓名迁移「到/假」**（对齐 mp `roster.js writeRoll`）；`rollSel`/`rosterSel` 均为 1..99 去重升序。
- 点名交互：点「到/假」切换状态；再次点击可在未处理→到→假→未处理循环；已处理项自动沉底；统计条实时；「重置本轮」清空状态保留名单；「沿用抽签名单」写 `rosterSel` 到点名名单。
- 隐藏项：v6 `hide` 契约只有 `hwHide`/`ntHide`（作业/手工通知隐藏），**没有名单隐藏字段**；本页保存 roster section 时保留其它 section 原值，不触碰 `hide`。
- 隐私：搜索/筛选仅本机；不导出、不打印名单到日志。

---

## 4. 单测

命令：

```powershell
$env:JAVA_HOME='C:\AndroidToolchain\jdk17'
$env:ANDROID_HOME='C:\AndroidToolchain\sdk'
cd C:\Users\Ko_Li\.openclaw\workspace\android-class-tools
& 'C:\AndroidToolchain\gradle\gradle-9.9.0\bin\gradle.bat' :app:testDebugUnitTest --console=plain
```

结果（2026-11-08 00:5x，最新一次运行）：

```text
BUILD SUCCESSFUL — :app:testDebugUnitTest
CLASSES=29  TOTAL=378  FAIL=0  ERR=0  SKIP=0
```

本任务新增 **36 个用例全部通过**：

| 测试类 | tests | failures | 覆盖 |
|---|---|---|---|
| `features.water.WaterLogicTest` | 21 | 0 | 编解码（null/坏值/脏数据归一/120 条上限/往返）、与 `WaterNormalizer` 归一结果对齐、月份状态机（边界/步进/年切换/天数/已过天数）、计费（首次记录/定额内/超定额/6 人间）、统计（用量/日均/对比上月/无记录）、录入校验（非法月/非法读数/小于上月/大于下一条/覆盖标记）、写入/覆盖/删除/人数/清除/上限 |
| `features.roster.RosterLogicTest` | 15 | 0 | 编解码（null/脏数据/中文状态兼容/越界状态丢弃/往返/空 section）、名单归一（座号/空名/去重/升序/去学号）、搜索与筛选、rollNames 生成解析、按姓名迁移状态、到/假切换、设置/重置/清空、统计、网格沉底、勾选加入点名、抽签名单 |
| **合计** | **36** | **0** | |

报告：`app/build/reports/tests/testDebugUnitTest/index.html`；XML：`app/build/test-results/testDebugUnitTest/`。
`:app:compileDebugKotlin` 同一流程通过；`WaterRosterPreviewsKt.class` 已产出（@Preview 可渲染）。

### 预览（9 张）

| # | 名称 | 场景 |
|---|---|---|
| 24 | 水量 · 记录列表 · 浅色 | 4 人间 6 条记录、11 月统计、规则卡 |
| 25 | 水量 · 6 人间 · 深色 | 6 人间、免额 12 吨 |
| 26 | 水量 · 空态 · 浅色 | 未选关注宿舍引导 |
| 27 | 水量 · 月份选择器 · 浅色 | 12 月网格 + 抄表圆点 |
| 28 | 名册 · 名单 · 浅色 | 搜索/筛选/职务徽章/点名勾选 |
| 29 | 名册 · 名单 · 深色 | 同上深色 |
| 30 | 点名 · 进行中 · 浅色 | 统计条、到/假、已处理沉底 |
| 31 | 点名 · 空名单 · 深色 | 空态引导 |
| 32 | 名册 · 空态 · 浅色 | 名单未同步 |

---

## 5. 遗留项 / 后续

1. **MainNavHost/AppContainer 未改**（Lead 接线）：签名见 §2；`container.userDataRepository` / `container.classMembersRepository` 已存在，无需新增仓库属性（`data/water` 仓库为可选包装，页面内部已构造）。
2. **名册「隐藏项」**：v6 `hide` 无名单隐藏字段，本页只保证不改写；若后续产品要「隐藏成员/名单」，需先扩 v6 契约（两端同改）。
3. **点名状态迁移**：按姓名迁移已对齐 mp；若成员改名，旧状态会丢失（与 mp 同口径）。
4. **日均口径**：当前月按自然日已过天数（网页口径待与后端/网页最终核对；若网页为「记录跨度」需同步调整 `WaterCalculator.stats` 一处）。
5. **页面数据刷新**：首屏会 `userDataRepository.pull(force=false)` 一次；外部同步变化不会实时重绘当前页（重新进入即最新），与电费页一致；后续统一切 ViewModel 时再优化。
6. **E2E**：Lead 扩展 E2E 时，水量页在「测试身份无关注宿舍」下应断言空态引导（`还没有关注宿舍`），名册页应断言 70 人列表与页签切换。
