# `docs/api/DRAW_GROUP_STATE_IMPL.md` — 抽签/分组页状态跨导航恢复（task-16）

> 状态：**已修复**（`rememberSaveable + Saver`；compile + 全量单测 + 模拟器手工验收）。
> 缺陷：E2E 发现「抽签 STEP3 结果页 →『用此结果分组』→ 分组页 → 系统 BACK」回到抽签 **STEP1 配置页**。
> 根因：`DrawScreen` 的 `workflow by remember`、`GroupScreen` 的 `state by remember` 是普通 `remember`；导航到分组 destination 后抽签 composable 被销毁，BACK 重建时状态重置为初始值（STEP1）。

---

## 1. 修复方式（唯一必要改动）

| 页面 | 旧 | 新 |
|---|---|---|
| `DrawScreen` | `var workflow by remember { mutableStateOf(initialWorkflow) }` | `var workflow by rememberSaveable(stateSaver = DrawWorkflowSaver) { mutableStateOf(initialWorkflow) }` |
| `DrawScreen`（重抽开关） | `var autoRedraw by remember { mutableStateOf(true) }` | `rememberSaveable { mutableStateOf(true) }` |
| `GroupScreen` | `var state by remember { mutableStateOf(GroupWorkflowState(...)) }` | 先 `val initialState = remember { ... }`，再 `var state by rememberSaveable(stateSaver = GroupWorkflowSaver) { mutableStateOf(initialState) }` |

原理：Navigation Compose 为每个 `NavBackStackEntry` 维护 `SaveableStateHolder`。抽签页 push 分组页时抽签 entry 仍在返回栈，`rememberSaveable` 的值被保存到该 entry 的 `SavedStateRegistry`；BACK pop 分组页后抽签 composable 重建时按 key 恢复同一保存值 → 回到 STEP3（含结果与剔除重抽记录）。旋转/分屏/进程重建同样走 `SavedStateHandle` 恢复。

---

## 2. 文件清单

### 新增 `features/draw/DrawWorkflowCodec.kt`

`DrawWorkflowState` ↔ JSON 字符串（纯函数，可单测）：

```jsonc
{
  "v": 1,
  "step": "config|preview|result",
  "config": { "start":1, "end":70, "count":10, "dup":false, "excl":"", "sort":"draw", "pool":"range|roster" },
  "candidates": [ { "n": 5, "nm": "张三" } ],          // 可选
  "result": {
    "entries": [ { "n": 7 } ],
    "pool":    [ { "n": 5 }, { "n": 12, "nm": "李四" } ],
    "config":  { ...同上... },
    "log":     [ { "removed": {"n":7}, "added": {"n":9} }, { "removed": {"n":23,"nm":"王五"} } ]
  }
}
```

- `config` 复用 `DrawConfigCodec` 的线格式（start/end/count/dup/excl/sort）+ 本机字段 `pool`；
- 可选字段省略（`name=null` 不写 `nm`；无剔除记录不写 `log`；无结果不写 `result`）→ 紧凑无 `:null` 噪音；
- 防御性上限：entries/candidates/pool ≤ 2000、log ≤ 500；
- 解码容错：坏 JSON / 非对象 → `null`（Saver 的 restore 返回 null 时由 rememberSaveable 用 init 初始值，即 `initial*` 语义不变）；字段缺失/非法 → 回落默认；`step=result` 但缺结果 → 降级 `preview`（有候选）或 `config`；
- 幂等：`encode(decode(encode(state))) == encode(state)`。

### 新增 `features/group/GroupWorkflowCodec.kt`

`GroupWorkflowState` ↔ JSON 字符串：

```jsonc
{
  "v": 1,
  "step": "config|result",
  "config": { "source":"prev|names", "mode":"groups|pergroup", "n":4, "shuffle":false, "sel":[1,2] },
  "result": {
    "groups": [ { "i": 1, "members": [ { "seat": 1, "nm": "张伟" }, { "seat": 2 } ] } ],
    "config": { ...同上... }
  }
}
```

- `config` 复用 `GroupConfigCodec` 线格式；
- 结果**不落盘冗余文案**：`snapshot`（来源/方式/人数/分配文案）在 restore 时用 `GroupEngine.snapshotOf(config, groups)` 按唯一口径重算，保证与页面生成时一致；
- 空姓名成员省略 `nm`（恢复为空串）；上限 groups ≤200、members ≤2000；
- `step=result` 但缺结果 → 降级 `config`；坏 JSON → null。

### 新增 Saver（UI 层薄包装）

| 文件 | 内容 |
|---|---|
| `ui/screens/draw/DrawWorkflowSaver.kt` | `val DrawWorkflowSaver: Saver<DrawWorkflowState, String>`，save=`DrawWorkflowCodec.encode`，restore=`decode` |
| `ui/screens/group/GroupWorkflowSaver.kt` | `val GroupWorkflowSaver: Saver<GroupWorkflowState, String>`，同上 |

### 修改页面

- `ui/screens/draw/DrawScreen.kt`：workflow/autoRedraw 改 `rememberSaveable`（见 §1），文档注释更新；
- `ui/screens/group/GroupScreen.kt`：state 改 `rememberSaveable`（见 §1）。

> 约束遵守：未改 MainNavHost/AppContainer/主题/构建文件；userdata `draw`/`group` section 的持久化逻辑（防抖写云端）不变。

---

## 3. 兼容性与边界

- **SavedState 键**：由 `rememberSaveable` 自动生成（组合内位置 + key），不手写 key；同一个组合位置稳定。
- **版本字段 `v=1`**：未来结构变更时提升版本；当前解码器忽略未知字段（向前兼容），缺失字段回落默认（向后兼容）。
- **Bundle 大小**：正常抽签结果（≤70 人 + 候选池）编码约 3–8 KB，远低于 TransactionTooLargeException 风险；上限截断避免异常配置。
- **进程重建**：Android 系统杀进程后返回，Activity 的 `SavedStateRegistry` 恢复 saver 字符串 → 工作流恢复；当前 `phase` 会短暂显示加载（因为参数恢复 effect 重跑），但 step/result 立即恢复。
- **预览/测试语义不变**：`skipRestore`/`initialConfig`/`initialStep`/`initialCandidates`/`initialResult` 仍作为首次组合（无 saved state 时）的初始值；`@Preview` 不受影响。
- **不保存的瞬时状态**：`isDrawing`/`inlineError`/`showRosterPicker`/确认弹层等；`autoRedraw` 已保存（结果页开关返回后保持）。
- **随机源**：Saver 不保存 RNG；恢复后「重新抽签/重新分组」会使用新随机数（既有 `result` 已保存，不受影响）。

---

## 4. 单测

`app/src/test/kotlin/com/furina/classtools/ui/screens/DrawGroupStateSaverTest.kt`（**9 用例**，Saver 实际编码逻辑 round-trip）：

| # | 用例 | 覆盖 |
|---|---|---|
| 1 | draw 仅配置 round-trip 保留全部参数 | `DrawWorkflowSaver` 编码/解码；`pool=roster`；幂等 |
| 2 | draw 候选预览 round-trip 保留序号与姓名 | STEP2 candidates（含 null 姓名） |
| 3 | draw 结果含剔除重抽 log round-trip | STEP3 result entries/pool/config/log（含 added=null） |
| 4 | draw 坏 JSON 返回 null 且 RESULT 缺结果降级 | 容错：null/空/坏 JSON/数组；降级 preview/config |
| 5 | group 仅配置 round-trip 保留来源模式与名单 | NAMES + PER_GROUP + shuffle + selectedSeats |
| 6 | group 结果 round-trip 且快照按 config 重算一致 | 每组座位+姓名、snapshot、空姓名成员、幂等 |
| 7 | group 结果步骤缺结果降级为配置 | 容错降级 |
| 8 | group 配置缺字段回落默认值 | 向后兼容 |
| 9 | draw 编码为紧凑 JSON 字符串且大小可控 | 无换行/无 `:null`/体积上限 |

结果（2026-11-08 01:05，全仓最新一次运行）：

```text
BUILD SUCCESSFUL — :app:testDebugUnitTest
CLASSES=35  TOTAL=451  FAIL=0  ERR=0  SKIP=0
```

其中本轮新增 Saver round-trip **9 个用例全部通过**（`ui.screens.DrawGroupStateSaverTest`）。
报告：`app/build/reports/tests/testDebugUnitTest/index.html`；XML：`app/build/test-results/testDebugUnitTest/`。

---

## 5. 手工验收（模拟器）

前置：`ct-api37` 模拟器 + Debug 包（`com.furina.classtools.debug`）。

步骤：

1. `gradle :app:assembleDebug` → `adb install -r app/build/outputs/apk/debug/app-debug.apk`；
2. 启动 App → 身份页「开发预览」→ 工作台；
3. 抽签：STEP1 配置 →「下一步：预览候选」→ STEP2 →「开始抽签」→ STEP3 结果（可先点一个 ✕ 剔除，制造 log）；
4. 滚动结果卡 →「用此结果分组」→ 分组页出现（来源=上一步抽签序号）；
5. **系统 BACK** → 应回到抽签 **STEP3 结果页**（结果与剔除记录都在；不是 STEP1）；
6. 再按页面「上一步」/底部返回：STEP3 → STEP2 → STEP1 → 退出工具（与既有 `DrawWorkflow.back` / `onBack` 语义一致）；
7. 旋转一次（`adb shell settings put system accelerometer_rotation 0; adb shell settings put system user_rotation 1`）后同样应停留在当前步骤。

**结果：PASS（2026-11-08 01:09，`ct-api37` 模拟器，Debug 包 `com.furina.classtools.debug`）**

截图目录：`<项目根>\artifacts\e2e_state_fix\`

| 文件 | 内容 |
|---|---|
| `01_draw_step1.png` | 抽签 STEP1 参数页 |
| `02_draw_step2.png` | STEP2 候选名单 |
| `03_draw_step3_result.png` | STEP3 结果页（先点掉一个候选制造剔除记录） |
| `04_group_page.png` | 「用此结果分组」进入的分组页（来源=上一步抽签序号） |
| `05_draw_after_back.png` | **系统 BACK 后回到 STEP3 结果页**（步骤指示 3 结果、10 个结果项、「剔除重抽记录（1）#1 剔除 44 → 65」均在） |
| `06_draw_after_back_log_visible.png` | 同上（滚动到剔除记录区） |
| `07_second_back_workbench.png` | 再 BACK → 返回工作台（既有系统 BACK pop 语义） |

脚本输出关键行：

```text
removed one chip (remove log created)
SHOT 05_draw_after_back.png (461133 bytes)
OK: result page restored WITH remove/redraw log section
MANUAL_VERIFY_PASS
```

验证前后对比（修复前 E2E 复现）：BACK 后回到 STEP1（步骤指示「1 配置」）且结果丢失；修复后 05 截图为 STEP3 结果页，结果项与剔除记录完整恢复。

---

## 6. 遗留 / 说明

1. **分组页自身**返回栈状态通过 Saver 恢复，但其配置在重新进入时仍会从 `userdata.group` 覆盖一次（页面既有逻辑）；若用户在分组页未保存的临时编辑后离开，重新进入可能回到上次云端参数（与本缺陷无关，既有行为）。
2. **系统 BACK 语义**：当前两个工具页**没有** `BackHandler`；系统 BACK 始终 pop 工具 destination（返回 ALL 工具页），页内步骤回退由顶栏/底部按钮触发（`DrawWorkflow.back`）。本次修复不改变该语义；E2E 依赖「BACK 返回抽签 STEP3」的 restore 行为。
3. **进程重建的进行中抽签**：`isDrawing` 不保存，杀进程时若正在抽签动画，恢复后停在原步骤（重新点抽签即可）；结果类数据不受影响。
4. **SavedState 清理**：抽签 destination 从返回栈移除（退出工具）后 SavedStateRegistry 随之释放，不会残留旧结果；重新进入是全新 STEP1（符合预期）。
