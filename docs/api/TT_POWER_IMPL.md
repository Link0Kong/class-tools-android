# `TT_POWER_IMPL.md` — 课表 + 电费原生实现说明

> 工程：`C:\Users\Ko_Li\.openclaw\workspace\android-class-tools`（包 `com.furina.classtools`）
> 契约：`docs/api/ENDPOINTS.md` E22/E23/E24、`docs/BACKEND_CONTRACT.md` §7.4/§7.5、`web/AGENTS.md` §4b-8/11/16/17
> 交付：task-8（课表 + 电费 数据层 + Compose 页面 + 单测 + 预览）

---

## 1. 文件清单

### 数据层 `app/src/main/kotlin/com/furina/classtools/data/`

| 文件 | 内容 |
|---|---|
| `timetable/TimetableModels.kt` | `Course` / `TtMeta` / `TtSnapshot` / `SessionInfo` / `TtPlan` / `TtLoadResult` / 节次常量 `TtPeriods`（12 节，与小程序的 `PERIODS` 逐项一致） |
| `timetable/TimetableCalendar.kt` | 纯函数：`parseTermStart` / `parseWeeks` / `weekOf` / `isActive` / `sessionsOn` / `nextSession` / `plan` / `weekRange` / `weekTitle` / `roomShort`（java.time，可注入 `ZoneId`） |
| `timetable/TimetableCache.kt` | `FileTimetableCache`：`tt_snapshot.json`（`savedAt` + snapshot）；坏文件返回 null 不崩 |
| `timetable/TimetableRepository.kt` | `TimetableRepository` 接口 + `DefaultTimetableRepository`：`site_assets?id=eq.tt` → 本机缓存；`body` 兼容 jsonb 对象/JSON 字符串；校验 `c` 数组；6h TTL；失败回退缓存 |
| `power/PowerModels.kt` | 17 间宿舍目录 `PowerRooms`、`PowerRow` / `PowerPoint` / `RangeStats` / `PowerEstimate` / `SlowStats` / `TrendLine` / `PowerTile` / `BillInfo` / `PowerRange` 等；加载结果 sealed |
| `power/PowerStats.kt` | 纯算法（逐函数对齐 web/mp）：`deductOf` / `deriveRange` / `rateFrom` / `estimate` / `slowUpdate` / `slowStats` / `estimateSlow` / `isSlowRoom` / `noUpdateText` / `trendLine` / `nearestPoint` / `cursorLabel` / `xTicks` / `stepSegments` / `changeDots` / `tilesOf` / `billOf` / `normLow` / `stateOf` 等 |
| `power/PowerRepository.kt` | `PowerRepository` 接口 + `DefaultPowerRepository`：E22（2min TTL）/ E23（5min 每档缓存、空结果不缓存、失败回退旧缓存）；`numberOrNull` 兼容 numeric/字符串 |

### UI `app/src/main/kotlin/com/furina/classtools/ui/screens/`

| 文件 | 内容 |
|---|---|
| `timetable/TimetableViewScreen.kt` | 周/日视图、上一周/下一周、今日高亮、下一节卡片、全部课程、课程详情 BottomSheet、四态、≥600dp 自适应（网格 + 右侧栏） |
| `power/PowerScreen.kt` | 关注宿舍选择（BottomSheet，写字 userdata v6 `power`）、hero 电量环、区间分段、`PowerTrendChart`、固定右上角气泡、指标 tile + 计费条、低电阈值弹层；未选宿舍 0 请求；隐私页脚 |
| `power/PowerTrendChart.kt` | Canvas 折线/阶梯线、点击/左右拖动命中最近真实采样点、竖线+圆点、x 轴刻度、图例；空态不画图；禁止横向滚动 |
| `ui/preview/TimetablePowerPreviews.kt` | 9 个 @Preview（课表周/日/深色/空态；电费浅/深/慢频日更/未选择/错误） |

### 单测 `app/src/test/kotlin/com/furina/classtools/data/`

| 文件 | 覆盖 |
|---|---|
| `timetable/TimetableCalendarTest.kt` | parseWeeks / weekOf / isActive（单双周）/ sessionsOn / nextSession / plan 四态 / weekRange / 标题 / roomShort（16 用例） |
| `timetable/TimetableRepositoryTest.kt` | E24 解析（对象/字符串 body、缺 c 数组、非法课程 clamp）、TTL、失败回退缓存、空课表（8 用例） |
| `power/PowerStatsTest.kt` | 24 用例：normLow/stateOf/ringArc、异常扣减、区间统计（跳过异常/充值）、rateFrom、estimate 守卫与混合、慢更新、slowStats（1 段/多段/0 段）、isSlowRoom、trendLine（空/全平/降采样）、nearestPoint、cursorLabel/xTicks、stepSegments/changeDots、tilesOf、billOf |
| `power/PowerRepositoryTest.kt` | E22/E23 解析、TTL、窗口 query 与 cut 编码、空结果不缓存、失败回退、统一 cacheKey（9 用例） |

---

## 2. 公开 API / 接线方式（Lead 在 MainNavHost 接入）

### 2.1 课表

```kotlin
// container 需要新增：val timetableRepository: TimetableRepository = DefaultTimetableRepository(
//     api = apiClient,
//     cache = FileTimetableCache(File(appContext.filesDir, "timetable")),
// )

"tt" -> TimetableViewScreen(
    onBack = { navController.popBackStack() },
    repository = container.timetableRepository,
)
```
可选参数（预览/测试）：`initialSnapshot`、`initialError`、`initialDayView`、`skipLoad`、`nowMillis`。

### 2.2 电费

```kotlin
// container 需要新增：val powerRepository: PowerRepository = DefaultPowerRepository(api = apiClient)

"power" -> PowerScreen(
    onBack = { navController.popBackStack() },
    repository = container.powerRepository,
    userDataRepository = container.userDataRepository,  // 关注宿舍/低电阈值写入 power section
    session = session,
)
```
可选参数（预览/测试）：`initialSelectedRv`、`initialLow`、`initialRow`、`initialHistories`、`initialRange`、`initialSlowRoom`、`initialError`、`skipLoad`。

### 2.3 写入 userdata 的约定
- 选择宿舍/改低电阈值时：读取 `userDataRepository.currentSections.first()` → 替换 `power` section 为 `{sel, at: now, low}` → `updateCurrent(...)`；
- 顶层 `at.power` 与值内 `power.at` 的镜像由 `UserdataMerge.localView` 负责（已核实），页面不手写；
- 写失败静默（UI 仍按本地选择展示；下一轮同步补发）。

---

## 3. 与 web / 小程序口径对照（关键）

### 3.1 课表

| 项 | web / mp | Android |
|---|---|---|
| 数据源 | site_assets id=tt（mp）/ tt.json 优先（web，域名已暂停） | `site_assets?id=eq.tt`；失败回退本机缓存（不再请求旧域名） |
| body 形态 | jsonb 对象或 JSON 字符串 | 两种都解析；缺 `c` 数组 → 失败 |
| TTL | 6h | 6h（内存 + 落盘） |
| 节次时间 | `PERIODS` 12 节 | `TtPeriods.PERIODS` 逐项一致 |
| 周次 | `floor((today-termStart)/7)+1` | 同（LocalDate + floorDiv；开学前 null） |
| 单双周 | `parity` odd/even | 同 |
| 下一节 | 今天起 14 天、跳过已结束 | 同（`nextSession`） |
| 周/日切换 | 只点击切换；内容区滑动切周 | 只由分段按钮切换；上一周/下一周按钮 |
| 周末 | 无课自动隐藏 | 网格固定 7 列，无课列留空（不做隐藏，保持 7 列对齐；与 web 有差异，已标注） |

### 3.2 电费（逐算法对照 mp/utils/power.js）

| 项 | web / mp | Android |
|---|---|---|
| 房间目录 | 17 间、顺序固定 | `PowerRooms.ROOMS` 逐项一致（含 rv/label/building/room） |
| 低电阈值 | 默认 30，档位 `[10,20,30,40,50,60,80,100]`，`normLow` 就近 | `PowerStats.normLow` 同 |
| 状态 | q<low 低电 / low≤q<2low 偏低 / 其余充足 | `stateOf` 同 |
| 电量环 | `estimate.days/30`（min 0.02，无有效 0.04） | `ringArc` 同 |
| 异常扣减 | `deductOf`：U=中位数；thr=max(5,4U) | 同 |
| 区间统计 | `deriveRange`：跳过充值/异常扣减 | 同（`RangeStats`） |
| 估算 | v24/v7 各自有效；0.65/0.35 混合；rate 下限 0.05；used≤0.05 无效 | 同（`estimate`） |
| 慢更新 | 24h 相邻 \|Δq\|>0.005 次数 ≤2 | 同（`slowUpdate`，文案「数据更新较慢 · 上次变化…」/「数据未更新」） |
| 慢频房 | 13/16 号楼且 24h 变化 <6 次；30d `slowStats`；1 段按 24h 折算 ref=true | `isSlowRoom` + `slowStats` + `estimateSlow` 同 |
| 慢频图 | 阶梯线 + 真实变化点（≤60） | `stepSegments` / `changeDots` 同 |
| 上次充值 | 区间 `lastUp.reset`（充值后余额，切档变化） | **Android 跨区间**：30d 全量历史最近上升的 `delta`（充值量 `+Δq`），切档不变；慢频房用 `slowStats.lastRecharge` |
| 区间 | 24h/7d/30d；每档 5min 缓存；空结果不缓存 | `PowerRange` + 仓库缓存策略同 |
| 当前值 | 2min TTL；未选宿舍 0 请求 | 同 |
| 趋势交互 | 点击/左右拖动命中最真实点；竖线+圆点；**气泡固定右上角（绘图区外）**；禁止横滑 | `PowerTrendChart` 同：气泡在区间行右侧固定文案；点击同点取消；拖动松手保留 |
| 全平 | hi-lo<CH → y=0.5 垂直居中 | `trendLine.flat` 同 |
| 隐私 | 只显示楼栋+房间号；不导出/分享 | 页面页脚声明；无导出入口 |

### 3.3 已知差异 / 待确认（**请 Lead 核对**）
1. **课表周末列**：web/mp 周末无课会隐藏；Android 固定 7 列（保证「7×节次网格」验收），无课列留空。若要求完全一致，可后续按「当周无课则隐藏该列」调整（不影响数据层）。
2. **低电阈值展示**：mp 的 tile 可点开阈值面板；Android 同（tile → BottomSheet）。阈值写入 `power.low` 依赖 userdata 合并把 `power.at` 镜像（已由 `UserdataMerge.localView` 处理），此处只传值内 `at`。
3. **「月均估算」文案**：Android 趋势卡底部多一行「月均估算 ≈x 度/天」（由 estimate.rate 给出）；web/mp 的对应信息在 tile 副标里。若要求零差异可移除。
4. **课表「全部课程」折叠**：web 默认可展开/收起；Android 当前直接展示（侧栏/卡片），未做折叠交互。
5. **电费「期间有充值」**：Android 只要 `deriveRange.recharge==true` 就显示；与 mp 一致（未区分区间）。
6. **课表导出**：本轮按任务留占位（无导出按钮）；后续接 `ExportPayload` 即可（数据层已提供 `TtSnapshot`）。
7. **慢频房首次进入**：Android 会加载 24h → 判定慢频 → 补 7d/30d（3 个请求）；实时房也为 3 个请求（24h/7d/30d，30d 用于跨区间「上次充值」）。

---

## 4. 单测与验证

```powershell
$env:JAVA_HOME='C:\AndroidToolchain\jdk17'
$env:ANDROID_HOME='C:\AndroidToolchain\sdk'
$env:ANDROID_SDK_ROOT='C:\AndroidToolchain\sdk'
cd C:\Users\Ko_Li\.openclaw\workspace\android-class-tools
& 'C:\AndroidToolchain\gradle\gradle-9.9.0\bin\gradle.bat' :app:testDebugUnitTest --console=plain
```

结果见任务汇报（`PowerStatsTest` 24 用例、`PowerRepositoryTest` 9 用例、`TimetableCalendarTest` 16 用例、`TimetableRepositoryTest` 8 用例）。

---

## 5. 已知限制
- 仓库层未做「离线 outbox」：电费/课表只读，失败只回退缓存（无写操作）。
- 课表仓库不读 userdata 的 `ttLocal`（个人导入）——本轮仅云端快照；后续如需个人导入，在 `TimetableViewScreen` 前合并 `tt.ttLocal` 即可（数据模型一致）。
- 趋势图 x 轴刻度用 `0.07–0.93` 均分近似定位（与 mp 的 `xTicks` 同算法；视觉上不画精确刻度线）。
- 预览使用固定时间（2026-11-07 10:00 +08:00）与注入数据，不发起网络请求。
