# `docs/api/DRAW_IMPL.md` — 抽签工具原生实现（task-6）

> 首个完整工具 vertical slice：`features/draw/` 领域层（纯 Kotlin）+ `ui/screens/draw/` 三步 Compose 页面 + 单测 + 预览。
> 契约依据：`docs/BACKEND_CONTRACT.md` §7.1（抽签算法）、`docs/api/USERDATA_V6.md`（`draw` section）、`docs/api/EXPORT_CARD_V2.md`（导出卡 v2）、`design/SCREENS.md` §4（抽签页方案，部分差异见 §7）。

---

## 1. 文件清单

### 领域层 `app/src/main/kotlin/com/furina/classtools/features/draw/`（纯 Kotlin，无 Android 依赖）

| 文件 | 内容 |
|---|---|
| `DrawModels.kt` | `DrawSortMode`（wire `draw/num/desc/rand`）、`DrawPoolSource`（RANGE/ROSTER）、`DrawConfig`、`DrawEntry`、`DrawResult`、`DrawRemoveLog`、`DrawError`/`DrawErrorCode` |
| `DrawValidation.kt` | `ExcludedParse`、`DrawValidation`（`Valid(numbers)`/`Invalid(error)`）、`DrawValidator`（排除号解析 + 参数校验 + 候选池构建） |
| `DrawRandom.kt` | `DrawRandom` 接口（可注入 seed）、`SeededDrawRandom`、`defaultDrawRandom()`、Fisher-Yates 洗牌扩展 |
| `DrawEngine.kt` | `DrawOutcome`、`DrawEngine`（`draw`/`drawFromCandidates`/`removeAndRedraw`/`reroll`/`sortEntries`） |
| `DrawWorkflow.kt` | `DrawStep`（CONFIG/PREVIEW/RESULT）、`DrawWorkflowState`、`DrawTransition`、`DrawWorkflow` 状态机（toPreview/startDraw/back/removeAndRedraw/changeSort/reroll/reset/editConfigFromResult） |
| `DrawConfigCodec.kt` | `userdata.draw` section 编解码（`{start,end,count,dup,excl,sort}`，含 clamp/容错） |
| `DrawExport.kt` | `DrawExportPayload`/`DrawExportBlock`/`DrawExportChip`/`DrawExportField`、`DrawExportFactory`（文件名/编号/参数区/chips/qrText） |
| `DrawConfigStore.kt` | `DrawConfigStore` 接口、`UserDataDrawConfigStore`（包装 `UserDataRepository`）、`InMemoryDrawConfigStore`（预览/测试） |

### UI `app/src/main/kotlin/com/furina/classtools/ui/screens/draw/`

| 文件 | 内容 |
|---|---|
| `DrawScreen.kt` | 页面入口 `DrawScreen(...)`；状态持有（workflow/phase/isDrawing/autoRedraw）；恢复 + 防抖持久化；四态；自适应分栏 |
| `DrawSections.kt` | 无状态组件：步骤条、参数卡、候选池预览、候选卡、结果卡（胶囊/剔除/排序/记录）、参数摘要、底部动作条、错误态 |
| `ui/preview/DrawPreviews.kt` | 6 个 @Preview：STEP1 浅色 / STEP2 浅色 / STEP3 浅色 / STEP3 深色 / 错误态 / STEP1 平板 |

### 测试 `app/src/test/kotlin/com/furina/classtools/features/draw/`

`DrawValidationTest`、`DrawEngineTest`、`DrawWorkflowTest`、`DrawConfigCodecTest`、`DrawExportTest`、`DrawConfigStoreTest`。

---

## 2. 公开 API（Lead 接线用）

### 2.1 页面入口（唯一需要 MainNavHost 调用的）

```kotlin
@Composable
fun DrawScreen(
    onBack: () -> Unit,                      // 返回：STEP1 时由导航 popBackStack；STEP2/3 内部先回上一步
    session: Session?,                       // null → 错误态（请先完成身份验证）
    onExport: (DrawExportPayload) -> Unit,   // 导出占位：拿到 payload 后由 Lead 的导出实现落图/分享
    modifier: Modifier = Modifier,
    members: List<ClassMember> = emptyList(),          // 可选：班级名单（来自 ClassMembersRepository 缓存）
    userDataRepository: UserDataRepository? = null,    // 可选：参数随账号同步（draw section）
    // 以下仅供 @Preview / 测试注入：
    initialConfig: DrawConfig = DrawConfig(),
    initialStep: DrawStep = DrawStep.CONFIG,
    initialCandidates: List<DrawEntry> = emptyList(),
    initialResult: DrawResult? = null,
    forcedErrorMessage: String? = null,
    skipRestore: Boolean = false,
)
```

接线（**已由 Lead 完成**，记录于此便于回溯；本任务未改 `MainNavHost`）：

```kotlin
// MainNavHost.kt（Lead 接线）
composable(Routes.TOOL) { entry ->
    val toolId = entry.arguments?.getString(Routes.TOOL_ARG).orEmpty()
    when (toolId) {
        "draw" -> DrawScreen(
            onBack = { navController.popBackStack() },
            session = session,
            userDataRepository = container.userDataRepository,
            onExport = { payload ->
                scope.launch {
                    when (val outcome = ExportPresenter.renderAndSave(context, payload.toCardSpec())) {
                        is ExportOutcome.Saved -> Toast...   // 已保存到相册
                        is ExportOutcome.Share -> Intent 分享
                        is ExportOutcome.Failed -> Toast 失败
                    }
                }
            },
        )
        else -> ToolPlaceholderScreen(...)
    }
}
```

> `DrawScreen` 位于 `com.furina.classtools.ui.screens.draw.DrawScreen`；`members` 参数可选（不传则只支持号码范围，名单来源 chip 自动禁用）。

### 2.2 导出 payload（与 `features/export` 的适配建议）

```kotlin
data class DrawExportPayload(
    val title: String,                    // "随机抽签结果"
    val subtitle: String,                 // "Step 1 / 3"
    val fileName: String,                 // 抽签结果_YYYYMMDD-HHMM_范围a-b_抽n个_不重复_按抽取顺序.png（已 safeName）
    val recordId: String,                 // DRAW-YYYYMMDD-HHMM-XXXX
    val config: List<DrawExportField>,    // 参数配置区：范围/抽取个数/允许重复/排除序号/结果排序/可用池(+候选来源)
    val blocks: List<DrawExportBlock>,    // 目前 1 个 chips 块：抽签结果 [{number,name}]
    val removeLogLines: List<String>,     // 剔除重抽记录（导出卡 P2 的 qrNote 直接用）
    val qrText: String?,                  // 有剔除记录时的二维码文本；无则 null
    val footer: String,                   // "生成时间 yyyy-MM-dd HH:mm"
    val generatedAtMillis: Long,
)
```

适配到现有导出模块：**Lead 已实现 `features/export/DrawExportMapper.kt`（`fun DrawExportPayload.toCardSpec(): ExportCardSpec`）并在 `MainNavHost` 接线**：
`onExport = { payload -> scope.launch { ExportPresenter.renderAndSave(context, payload.toCardSpec()) } }`（保存相册/分享/失败 toast 由 `ExportPresenter` 处理）。
映射规则（mapper 负责，业务层无需关心）：`file/rec/config/title/sub/foot` 直传；`blocks` → `ChipsBlock`；`removeLogLines` → 追加 `TextBlock("剔除重抽记录")` + `qrNote`（二维码 P2，`qr=null`）。
> `DrawExportFactory` 已按网页命名规则产出 `fileName`/`recordId`；如后续需要换实现，payload 字段与 `ExportCardSpec` 一一对应，重新组装即可。

### 2.3 领域层入口

```kotlin
// 校验 → 候选（STEP1）
val validation = DrawValidator.validate(config, rosterSeats = members.map { it.seat })
// 抽签（STEP2→STEP3，随机可注入 seed）
val outcome = DrawEngine.draw(config, rosterSeats, nameOf = { n -> memberName(n) }, random = SeededDrawRandom(42))
// 状态机（页面内部使用；也可给后续 ViewModel 复用）
val next = DrawWorkflow.toPreview(state, rosterSeats, nameOf)
val drawn = DrawWorkflow.startDraw(nextState, random)
val removed = DrawWorkflow.removeAndRedraw(drawnState, index = 0, autoRedraw = true, random)
// 持久化（draw section）
val store = UserDataDrawConfigStore(userDataRepository)
store.save(config)   // 只替换 draw，其余 section 原样保留 → updateCurrent → 防抖推送
```

---

## 3. 抽签算法（与网页 `doDraw()` 对齐）

1. `lo=start, hi=end`（校验要求 `start ≤ end`，越界/反置行内报错）；
2. 池 = `[start..end] − 排除号`；名单来源再 ∩ 班级名单座号；
3. 不重复：Fisher-Yates 洗牌后取前 `count` 个；允许重复：`count` 次独立随机；
4. 排序：`按抽取顺序` / `从小到大` / `从大到小` / `随机重排`（结果集合不变）；
5. 剔除重抽：移除第 i 项 → 不重复时从「池 − 当前结果」随机补一个（允许重复时从全池补）→ 追加 `DrawRemoveLog`；页面勾选「剔除后自动重抽」控制是否补抽；
6. 随机源 `DrawRandom` 可注入：生产 `defaultDrawRandom()`（时间种子），单测 `SeededDrawRandom(seed)` 保证可复现。

## 4. 参数持久化（userdata v6 `draw` section）

- 线格式：`{"start":1,"end":70,"count":10,"dup":false,"excl":"","sort":"draw"}`（键序与 `USERDATA_V6.md` 一致）；
- `sort` 映射：`DRAW_ORDER→draw`、`ASCENDING→num`、`DESCENDING→desc`、`RANDOM→rand`；未知值回落 `draw`；
- 页面打开时**一次性**读取 `UserDataRepository.currentSections.draw`（不监听远端变更，避免抽签过程中被其他端覆盖）；
- 参数变化后 **600ms 防抖** 调 `UserDataDrawConfigStore.save`（只替换 draw section，其余 section 原样保留 → `updateCurrent` 内部落盘 + 1200ms 推送）；失败静默；
- 候选来源（号码范围/班级名单）**不进云端契约**（`USERDATA_V6.draw` 无此字段），仅本机 UI 选择，读取时恒为 RANGE。

## 5. 四态 / 自适应 / 动效

- **正常**：三步内容（配置表单 → 候选 chips → 结果 chips）；
- **空**：STEP3 无结果时卡内 `EmptyState`（"还没有抽签结果"）；候选池为空由校验错误行内提示拦截；
- **加载**：恢复参数时全屏 `CtLoading("恢复抽签参数…")`；抽签时按钮内 spinner（最短反馈 150ms，防闪烁）；
- **错误**：未登录 / 恢复失败 → 全屏 `DrawErrorState`（重试 + 返回）；参数校验错误 → 表单/卡片内行内红字（不弹窗）；
- **自适应**：`BoxWithConstraints`，`maxWidth ≥ 600dp` 左右分栏（左参数 0.56 / 右候选或结果 0.44，桌面内容随窗口放宽），compact 单列滚动；
- **动效**：卡片 `animateContentSize(CtMotion.spatialSize)`（spring 0.8/380）、步骤条选中色、抽签 chips 重排；均可在系统「减弱动效」下降级（Compose spring 自动跟随）。
- **触控**：底部主按钮 52dp、chips ≥40/48dp、步骤条 ≥44dp；edge-to-edge：`statusBarsPadding + navigationBarsPadding`。

## 6. 单测

命令：

```powershell
$env:JAVA_HOME='C:\AndroidToolchain\jdk17'
$env:ANDROID_HOME='C:\AndroidToolchain\sdk'
cd C:\Users\Ko_Li\.openclaw\workspace\android-class-tools
& 'C:\AndroidToolchain\gradle\gradle-9.9.0\bin\gradle.bat' :app:testDebugUnitTest --console=plain
```

结果（2026-11-07 23:37，全仓最新一次运行）：

```text
BUILD SUCCESSFUL — :app:testDebugUnitTest
TOTAL tests=154 failures=0 errors=0 skipped=0
```

其中**抽签相关 69 个用例全部通过**：

| 测试类 | tests | failures |
|---|---|---|
| `features.draw.DrawValidationTest` | 15 | 0 |
| `features.draw.DrawEngineTest` | 16 | 0 |
| `features.draw.DrawWorkflowTest` | 15 | 0 |
| `features.draw.DrawExportTest` | 11 | 0 |
| `features.draw.DrawConfigCodecTest` | 8 | 0 |
| `features.draw.DrawConfigStoreTest` | 4 | 0 |
| **抽签合计** | **69** | **0** |

全仓另外 85 个来自数据层（79）+ 导出命名（6），同样 0 失败。报告：`app/build/reports/tests/testDebugUnitTest/index.html`；XML：`app/build/test-results/testDebugUnitTest/`。

> 说明：`DrawExportTest` 的 safeName 期望值曾按「下划线也替换」写错，已按 `export_card_v2_spec.md` 修正为只替换 `[\\/:*?"<>|\s]+`（下划线保留）；当前 0 失败。
> 主工程 `:app:compileDebugKotlin` 同期通过（MainNavHost 已由 Lead 接入 draw 路由 + 导出，本任务未改该文件）。

覆盖点：

| 测试类 | 覆盖 |
|---|---|
| `DrawValidationTest` | 排除号解析（逗号/顿号/空白/区间/非法片段/反向区间/越界）、范围越界、起点>终点、个数 0/越界、排除后池空、不重复超池、允许重复超池、名单来源必须提供名单/交集/过滤非法座号、单候选 |
| `DrawEngineTest` | 同 seed 可复现、不同 seed 不同、结果在范围内且不重复、排除号生效、count=池大小、不重复超池报错、允许重复可重复、从小到大/从大到小/随机排序、姓名映射、剔除+自动重抽（数量不变/被剔项消失/log）、剔除不重抽、越界下标、重新抽签、空池、随机边界 |
| `DrawWorkflowTest` | 初始状态、配置只可 STEP1 改、非法参数不能进 STEP2、候选生成与姓名、越步/重复进入拒绝、STEP2 抽签、back 出栈、剔除仅 RESULT、排序写回 config、预览不能排序、reroll、reset、editConfigFromResult |
| `DrawConfigCodecTest` | wire 键序、空/坏数据默认、clamp、未知 sort 回落、非字符串 excl/非布尔 dup 容错、roundtrip、默认值与契约一致 |
| `DrawExportTest` | 文件名规则（含允许重复/排序/无非法字符）、recordId 前缀+后缀、参数区字段、chips 映射、剔除记录文本/qrText、页脚、safeName、摘要 |
| `DrawConfigStoreTest` | 内存实现读写、userdata 读取 draw、保存只替换 draw 保留其他 section、无 draw 默认值 |

## 7. 与 `design/SCREENS.md` §4 的差异（已知，待 Lead 决策）

设计稿把「抽签工作流」定义为 **STEP1 抽签 / STEP2 分组 / STEP3 结果单**；task-6 描述定义本轮为 **STEP1 配置 / STEP2 候选预览 / STEP3 结果**（首个 vertical slice）。
本轮按 task-6 落地，状态机/组件已为后续扩展留好位置：

- STEP2 追加「分组」（`GroupState`/`GroupsBlock` 导出块在 `features/export` 已就绪）→ 新增 `DrawStep.GROUP` 与分组引擎即可；
- STEP3 结果单（抽签 chips + 分组 groups + 剔除记录）→ 直接复用 `DrawExportFactory` 再拼 `GroupsBlock`；
- 分组/结果单属于**下一增量**，不在本轮 scope。

## 8. 后续 TODO（非本轮）

1. 导出落图：把 `DrawExportPayload` → `features/export` 的 `ExportCardSpec` 渲染 PNG + 保存/分享（Lead 接线）。
2. 二维码：有剔除记录时用 `payload.qrText` 生成（P2；不引第三方库，复用现有导出模块的零依赖 QR 实现）。
3. ViewModel + `SavedStateHandle`：旋转/分屏保存 step/参数/结果（当前用 `remember`，旋转会回到初始态；设计稿 §4.5 要求保存，待与 Lead 统一引入 VM 时补）。
4. 名单选择 BottomSheet（70 人搜索 + 全选/清空）：本轮 `members` 由外部传入并使用全部座号；选择器与 `roster` section 联动放下一增量。
5. 数字滚动揭示动画（`animateIntAsState` + 交错 fade-in，设计稿 §4.6）与触感反馈（`HapticFeedback`）。
