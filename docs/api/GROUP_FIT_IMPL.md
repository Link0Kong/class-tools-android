# `GROUP_FIT_IMPL.md` — 分组 + 体测原生实现说明

> 工程：`C:\Users\Ko_Li\.openclaw\workspace\android-class-tools`（包 `com.furina.classtools`）
> 契约：`docs/BACKEND_CONTRACT.md` §7.2/§7.3、`docs/api/USERDATA_V6.md`（group/fit section）、
> `docs/api/ENDPOINTS.md` E14/E15/E16（fitrecs）、`web/tools/tests/export_card_v2_spec.md`
> 交付：task-10（领域层 + Compose 页面 + 单测 + 预览 + 两处小修复）

---

## 1. 文件清单

### `features/group/`（纯 Kotlin 领域层）

| 文件 | 内容 |
|---|---|
| `GroupModels.kt` | `GroupSource/GroupMode/GroupMember/GroupConfig/MemberGroup/GroupConfigSnapshot/GroupResult/GroupStep/GroupWorkflowState/GroupError/GroupValidation/GroupTransition` |
| `GroupEngine.kt` | `GroupRandom/SeededGroupRandom/SystemGroupRandom` + 分组引擎（组数 clamp、Fisher-Yates、轮流分配均衡、参数快照） |
| `GroupWorkflow.kt` | `GroupValidator`（成员 ≥2、n 边界、名单来源）+ `GroupWorkflow`（start/regroup/back/openResult/updateConfig） |
| `GroupConfigCodec.kt` | `userdata.group` 编解码（兼容 `pick/paste`→`names`、`per`→`pergroup`；sel 归一 1..99 去重升序） |
| `GroupExport.kt` | `GroupExportPayload`（含 `toCardSpec()`）+ `GroupExportFactory`（GroupsBlock、参数区、命名、编号、复制文本） |

### `features/fit/`（纯 Kotlin 领域层）

| 文件 | 内容 |
|---|---|
| `FitTables.kt` | 国标 2014 大学组评分表：FTS 20 档、单项表（vc/r50/sit/jump/pull/sup/dur ×4 列）、加分表、BMI 区间、输入范围（逐行移植 mp/utils/fit.js） |
| `FitModels.kt` | `FitSex/FitGrade/FitInput`（11 键字符串）/`FitFieldSpec`/`FIT_INPUT_FIELDS`/`FitRow`/`FitCalc`/`FitFields`/`FitRecord`（fitrecs.data 结构 + 展示辅助） |
| `FitScoring.kt` | `num/sec/valid/scoreOf/plusOf/ms/round10/calc/sanitizeInput`（逐函数对齐 mp `ftCalc`；含 BMI 分档/单项分/加分/缺项/等级） |
| `FitCodec.kt` | `userdata.fit` 编解码 + `fitrecs.data` JSON 字符串 ↔ `FitRecord` |
| `FitHistory.kt` | 历史记录：`isSubset/findMergeIndex/save`（补填合并、≤2 条、挤出列表）+ 删除/改名/回填 |
| `FitExport.kt` | `FitExportPayload`（含 `toCardSpec()`）+ 工厂（TextBlock「各项得分」、参数区、命名、编号、复制文本） |

### UI

| 文件 | 内容 |
|---|---|
| `ui/screens/group/GroupScreen.kt` | 配置 → 结果两阶段；来源/方式分段按钮、组数步进、打乱开关、名单选择 BottomSheet（CtChipGroup）、两列组卡；重新分组/导出/复制；四态；≥600dp 左右分栏 |
| `ui/screens/fit/FitScreen.kt` | 性别/年级分段、9 项输入（分项校验清洗）、实时评分卡（总分/等级/标准分/加分/缺项/逐项）、历史记录（回填/改名/删除，≤2）、国标表 BottomSheet、保存/导出/复制/清空；四态；≥600dp 左右分栏 |
| `ui/preview/GroupFitPreviews.kt` | 7 张预览：分组配置（浅）/结果（浅/深）；体测录入（浅/深）/历史/空态 |

### 顺手修复（task-10 第 8 条）
- `ui/components/expressive/CtBottomSheet.kt`：拆成 **`CtBottomSheet`（简化重载，不暴露 SheetState、内部 rememberModalBottomSheetState，调用方无需 OptIn）** + **`CtBottomSheetWithState`（高级重载，需 OptIn）**；
- `ui/screens/power/PowerScreen.kt`：移除趋势卡底部「月均估算 ≈x 度/天」重复行（信息在 tiles）；
- `ui/screens/timetable/TimetableViewScreen.kt`：周末两天都无课 → 隐藏周末列（有课保留 7 列；`visibleDays` 驱动表头/网格/列宽）。

---

## 2. 公开签名 / 接线点

### 2.1 GroupScreen

```kotlin
@Composable
fun GroupScreen(
    onBack: () -> Unit,
    onExport: (GroupExportPayload) -> Unit,
    modifier: Modifier = Modifier,
    session: Session? = null,
    userDataRepository: UserDataRepository? = null,
    prevMembers: List<GroupMember> = emptyList(),   // 上一步抽签结果（座号+姓名）
    rosterMembers: List<GroupMember> = emptyList(), // 班级名单（ClassMembersRepository 缓存）
    initialConfig: GroupConfig = GroupConfig(),
    initialResult: GroupResult? = null,
    initialStep: GroupStep? = null,
    forcedErrorMessage: String? = null,
    skipRestore: Boolean = false,
    randomSeed: Long? = null,
)
```

MainNavHost 接线建议（Lead）：

```kotlin
"group" -> GroupScreen(
    onBack = { navController.popBackStack() },
    onExport = { payload -> exportCoordinator.exportGroup(payload) },  // 内部 payload.toCardSpec()
    session = session,
    userDataRepository = container.userDataRepository,
    prevMembers = drawResultMembers,          // 来自 STEP1 结果；无则 emptyList()
    rosterMembers = classMembers.map { GroupMember(it.seat, it.name) },
)
```

### 2.2 FitScreen

```kotlin
@Composable
fun FitScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    session: Session? = null,
    userDataRepository: UserDataRepository? = null,
    initialInput: FitInput = FitInput(),
    initialHistory: List<FitRecord> = emptyList(),
    onSave: (FitRecord, List<FitRecord>) -> Unit = { _, _ -> },
    onExport: (FitExportPayload) -> Unit = {},
    forcedErrorMessage: String? = null,
    skipRestore: Boolean = false,
    nowMillis: Long = System.currentTimeMillis(),
)
```

MainNavHost 接线建议：

```kotlin
"fit" -> FitScreen(
    onBack = { navController.popBackStack() },
    session = session,
    userDataRepository = container.userDataRepository,
    initialHistory = container.fitrecsRepository.cached(),   // fitrecs E14（Lead 后续接线）
    onSave = { record, all -> container.fitrecsRepository.save(record, all) },
    onExport = { payload -> exportCoordinator.exportFit(payload) },
)
```

### 2.3 导出 payload 结构

`GroupExportPayload` / `FitExportPayload` 均含：
`title / subtitle / fileName / recordId / config: List<Field> / block` + `footer / generatedAtMillis`，
并提供 `toCardSpec(): ExportCardSpec`（features/export 通用模型）：
- 分组：`block = GroupsBlock(label="分组结果", groups=[[ChipItem(seat,name)]])`；
- 体测：`block = TextBlock(label="各项得分", lines=[TextLine])`；
- 命名：分组 `分组结果_<stamp>_<N>组_<M>人_<顺序|打乱>.png`（ExportKind.DRAW_STEP2）；
  体测 `体测成绩_<stamp>_<男|女>_<大一大二|大三大四>_总分<score>.png`（ExportKind.FITNESS）；
- 编号：`GROUP-YYYYMMDD-HHMM-XXXX` / `FIT-YYYYMMDD-HHMM-XXXX`；页脚 `生成时间 MM-dd HH:mm`。

---

## 3. 业务口径对照

### 3.1 分组（web/mp `doGroup`）
- 组数：`groups` 模式 `clamp(n, 2, 成员数)`；`pergroup` 模式 `ceil(成员数/n)`；
- 分配：`out[i % groups].push(member)` → 人数差 ≤1（均衡）；
- 打乱：Fisher-Yates（可注入 seed；未打乱按原顺序）；
- 文案：`上一步抽签序号/班级名单（N 人）`、`按组数(N 组)/按每组人数(N 人)`、`打乱后随机分配/按原顺序轮流分配`；
- `userdata.group`：`{source, mode, n, shuffle, sel}`；兼容小程序旧词汇。

### 3.2 体测（web `fit` / mp `fit.js`）
- 权重：BMI 15%、肺活量 15%、50m 20%、坐位体前屈 10%、立定跳远 10%、引体/仰卧 10%、长跑 20%；
- 单项分：`FTS` 20 档 × 四列（m12/m34/f12/f34）；`hi` 指标 `v>=阈值`、跑步 `v<=阈值`；都不命中 = 0；
- BMI：`w/(h/100)²`；低体重 80 / 正常 100 / 超重 80 / 肥胖 60（男 17.9–23.9，女 17.2–23.9，>27.9 肥胖）；
- 加分：引体/仰卧/长跑（gap 超阈值 → FTP 表，≤10）；
- 总分：`round10(Σ score*weight/100 + plus)`；等级 ≥90 优秀 / ≥80 良好 / ≥60 及格 / 否则不及格；
- 缺项按 0 计并计入 `miss`；`userdata.fit` 11 键；历史 ≤2 条、补填合并（旧记录字段是新输入子集时更新而非新增）。

---

## 4. 单测与验证

```powershell
$env:JAVA_HOME='C:\AndroidToolchain\jdk17'
$env:ANDROID_HOME='C:\AndroidToolchain\sdk'
$env:ANDROID_SDK_ROOT='C:\AndroidToolchain\sdk'
cd C:\Users\Ko_Li\.openclaw\workspace\android-class-tools
& 'C:\AndroidToolchain\gradle\gradle-9.9.0\bin\gradle.bat' :app:compileDebugKotlin :app:testDebugUnitTest --console=plain
```

新增测试类（结果见任务汇报）：
- `features/group/GroupEngineTest.kt`：组数 clamp、均衡、顺序、seed 可复现、集合守恒、快照文案、校验边界、状态机（start/regroup/back/openResult）；
- `features/group/GroupCodecExportTest.kt`：group section 往返/旧词汇/脏 seat 归一；导出 GroupsBlock/参数/命名/编号/复制文本/cardSpec；
- `features/fit/FitScoringTest.kt`：单项边界（高优/低优/性别年级列）、BMI 四档、缺项/权重、长跑解析与加分、引体/仰卧加分、满分 120 优秀、round10/ms、输入清洗；
- `features/fit/FitCodecHistoryTest.kt`：fit section 往返、fitrecs.data 往返/坏数据、展示辅助、子集/合并索引、保存合并/≤2/挤出、删除改名回填、导出 payload。

---

## 5. 遗留项
1. **fitrecs 云端读写**（E14/E15/E16）未实现：本轮 UI 用 `initialHistory` + `onSave` 回调，仓库由 Lead/后续 task 接线；删除历史记录时需同步 `arch=true` 软删（回调里带 `all` 列表供仓库对比）。
2. **分组「上一步抽签结果」来源**：由 Lead 从抽签 STEP1 结果生成 `List<GroupMember>` 传入；本轮不在 AppContainer 里缓存。
3. **体测历史改名/删除**未接云端：本地 state 生效并通过 `onSave` 回传；改名/删除接口待 fitrecs 仓库。
4. **分组未做「结果单（STEP3）」**：本轮到分组结果为止（结果单导出由 Draw 工作流的导出能力覆盖，Lead 可在 M6 统一）。
5. **课表周末列隐藏**只做 UI 层（无独立单测；预览覆盖「周末无课」与「周末有课」两种，见文件）。
6. 剪贴板使用 `LocalClipboardManager`（Compose 新版本提示 deprecated，功能正常；后续可切 `LocalClipboard`）。
