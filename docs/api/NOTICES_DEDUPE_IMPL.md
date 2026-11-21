# Android 通知数据层去重 + 删除「发送测试通知」（task-38）

> 状态：**已交付**（`data/notices` 去重 + `data/reminder` 删除测试通知能力 + 单测 + 本文件）。
> 基线 git `6268510`（1065/0）；本任务新增 23 个去重用例、删除 3 个测试通知用例；全量回归数字见交付消息。
> 约束：只改数据层与单测，未改 UI/导航/构建（「发送测试通知」按钮已由 UI 队友另行移除）。

---

## 1. 根因

### 1.1 代码层原因（可直接定位）

`DefaultNoticesRepository.buildData()` 原逻辑：

```kotlin
for (row in rows) {
    if (row.archived) continue
    if (NoticeBodyParser.isAuto(row.body)) {
        val group = NoticeBodyParser.parseGroup(row.body) ?: continue   // 尾随 override/hidden
        auto.add(NoticeEntry(row = ..., group = ..., items = ..., ...)) // 1 行 = 1 张卡片
    }
}
```

- **只按「行」展示**：每条非 archived 的 `notices` 行都被当成一张独立卡片；UI 列表 key = `rowId`，
  所以同一逻辑通知只要有多行，就会显示多张。
- 数据层从未做语义归一：`NoticeSignature` 只用于缓存判断；`NoticeBodyParser/NoticeStatusMachine`
  只负责单行解析与状态计算；`NoticeOverride` 只按行应用。没有任何 `id` 之外的稳定身份。

### 1.2 数据层原因（云端行为何会重复；按行结构与管线逻辑分析）

治理管线（`scripts/sync_notices_cloud.py` + `ai_triage.py`）正常路径是
`id = auto_<group.id>`、并把不再存在的 auto 行归档；但下列情况会留下**同一逻辑通知多行**：

1. **历史遗留行**：早期采集/合并产生的 `auto_<旧gid>` 行未被本轮 `archived=true` 覆盖（归档步骤失败、
   管线中断、旧数据迁移等），`archived=eq.false` 查询仍会返回；
2. **重新采集 gid 变化**：同一消息被再次抽取时 group id 可能变化（新的 `auto_<新gid>`），旧行在下一轮
   归档前/归档失败时与新的行同时存在；
3. **补充/更正被误判为新通知**：`ai-supplements.json` 正常会合并进已有 group；若 AI 输出未命中 target
   或 target 丢失，补充内容可能作为新 group 发布，标题/时间/来源与原文高度相同但不是同一行；
4. 客户端无法直接查云端核实具体行（不读凭据、不写云端）；以上是行结构与管线逻辑可推出的原因，
   **共同点：Android 只认行 id，缺少语义去重**。

### 1.3 为什么 Web/MP 看起来没这个问题

Web/MP 也按 group 逐条展示，但：① 它们每次拉取后覆盖整表缓存，页面通常只显示当前 `nt.g`；
② 其合并/补丁逻辑（`apply_supplements`、`notice_merge.py`）在管线侧处理大部分重复；
③ 用户实际主要在 Android 端看到「同一通知多条」，且 Android 没有做任何 defensive 去重。
不管其他端是否已缓解，**数据层应在消费端自保**——本次按此修复。

---

## 2. 去重规则（`data/notices/NoticeDedupe.kt`）

### 2.1 稳定身份

| 层级 | 规则 |
|---|---|
| 第一层（强） | **同一 group id**（`NoticeEntry.id` 相等，非空）→ 直接合并（不依赖标题/时间） |
| 第二层（语义） | 不同 group id，但：规范化标题相同 **且** 分类相同 **且** 时间窗口内（≤2h）**且** 有强证据 → 合并 |
| 人工通知 | 规范化标题 + 正文相同 且 ts 相差 ≤10min → 合并；**auto 与 manual 不互相合并** |

**强证据（至少一条）**：

1. 同来源（`group.source` 规范化后相同）；
2. 条目内容重叠（`items` 指纹交集：文本内容、文件名+大小、图片 caption+体积、链接 host 等）；
3. `mergedSources` 交集；
4. 同发送人（`senderName` 相同且非空）。

**无结构化时间**（deadline/eventAt/报名窗口/expireAt 都为空）时要求更强：**同来源 + 条目/mergedSources 重叠**，
避免把两条同名「通知」误合。

**时间锚优先级**：真实截止 `deadlineOf`（含延期 extensions 最后一条）→ 活动 `eventAt` → 报名窗口
`signupEnd/signupStart` → `expireAt`；两边都有锚且相差 >2h → **不合并**（保留不同场次）。

### 2.2 合并内容（不丢信息）

- 主条目 = `rank` 更高者：`editedAt` 最高（管理员编辑优先）→ `ts` 最新 → 条目更多 → 未隐藏优先；
- `items`：并集 + 按内容指纹去重（保留主条目顺序、supplements 追加顺序；上限 64 条）；
- `mergedSources`：并集 + 双方 `source`；`crossSource = 任一方 true 或来源不同或 mergedSources 多于 1 项`；
- `extensions`：并集（按 deadline 去重）；`aiPoints/aiSegments`：并集去重；
- 语义字段（标题/分类/摘要/截止/活动/时间文案）：优先主条目，主条目为空时用另一条的；
- `row.ts = max(ts)`、`editedAt = max`、`hidden = 双方都 hidden`（管理员同组重复时优先保留可见行）；
- `edited = 任一方 edited`。

### 2.3 隐藏 / 覆盖组合

- `hidden` 过滤发生在**去重前**（保持现有语义）：非管理员先丢掉 hidden 行并计入 `hiddenCount`（按行数）；
- 管理员保留 hidden 行；若同组有隐藏+可见重复，去重后**可见行保留、hidden=false**；
- `override` 在去重前应用（标题等覆盖后参与语义匹配）；同组重复时优先挑 `editedAt` 更高的（即被编辑过的）行。

### 2.4 保守边界（避免误合）

- 不同分类/标题 → 不合并；
- 同标题同分类但时间相差 >2h → 不合并（同活动两场次）；
- 同标题无时间但来源不同/条目不同 → 不合并；
- 人工通知与自动通知形态不同 → 不合并；
- 只合「标题完全一致（规范化空白/大小写后）」的通知，不做模糊相似度合并。

---

## 3. 改动文件

| 文件 | 改动 |
|---|---|
| `data/notices/NoticeDedupe.kt`（新增） | 去重纯逻辑：`dedupeAuto` / `dedupeManual` / `sameLogical` / `merge` / `mergeItems` / `fingerprintOf` / `anchorOf` / 规范化工具、`Result(entries, mergedCount)` |
| `data/notices/NoticesRepository.kt`（改） | `buildData` 在 hidden 过滤 + override 应用后调用 `NoticeDedupe.dedupeAuto` / `dedupeManual`，再排序输出 |
| `data/reminder/ReminderDiagnostics.kt`（改） | 删除 `ReminderTestNotificationResult` 与 `ReminderActions.testNotification`；保留 `ReminderActions.diagnostics`、运行状态/补发/日志/小图标等 |
| `data/reminder/ReminderScheduler.kt`（改） | 删除 `ReminderEngine.testNotification(kind)`；保留 `runNow()`/`runOnce()`（立即检查）、`diagnostics()`、Worker、补发、去重、单条提醒 |
| `test/.../data/notices/NoticeDedupeTest.kt`（新增） | 18 用例 |
| `test/.../data/notices/NoticesDedupeIntegrationTest.kt`（新增） | 5 用例（仓库层 hidden/override/人工/不同通知保留） |
| `test/.../data/reminder/ReminderEngineTest.kt`（改） | 删除 3 个测试通知用例（其余运行/诊断/补发用例保留） |

**未改**：任何 UI/导航/AppContainer/构建文件；`NoticesScreen` 由 UI 队友并行重做，不碰。
UI 侧「发送测试通知」按钮已由 design 队友移除（当前源码已无 `testNotification` 引用，数据层删除后编译通过）。

---

## 4. 测试

```text
新增 23/0：
  NoticeDedupeTest              18
  NoticesDedupeIntegrationTest   5
删除 3 个测试通知用例（ReminderEngineTest）

全量回归（2026-11-12 20:53）：**CLASSES=87 / TOTAL=1085 / FAIL=0 / ERR=0**
（基线 1065 + 本任务 23 - 3 = 1085）。
```

覆盖：同 group id 重复（含 edited 优先/可见优先）、跨 id 语义重复合并（来源/条目/mergedSources/时间窗口）、
不同标题/分类/时间/来源保留、无时间强证据边界、条目级指纹去重与上限、人工通知去重（窗口/附件更完整保留）、
仓库层 hidden+override+人工/自动不互相合并、排序稳定、空/单条输入。

---

## 5. 已知取舍 / 后续

1. **保守合并**：时间窗口 2h、标题必须完全一致；宁可漏合也尽量不误合。若云端后续出现「标题被 AI 改写」
   的同通知重复行，客户端不会合并（建议管线侧修因，保留 `supplements` 合并语义）。
2. **跨 auto/manual 不合并**：人工通知与自动通知结构不同，强行合并可能误伤真实人工公告。
3. **hiddenCount 仍按行数**：hidden 行在去重前过滤，重复 hidden 行会计多次；这是既有契约字段语义
   （管理员隐藏行数），不影响列表展示。
4. **mergedSources 会被去重逻辑扩展**：合并时补入双方 source，可能让原本只有 1 个来源的卡片显示
   「多来源」标记；语义上正确（确实由两条合并而来）。
5. 去重只在 `buildData`（整表重建）时执行；签名未变的缓存直接返回已去重结果，无额外开销。
6. 测试通知能力已彻底删除：`ReminderEngine.testNotification`、`ReminderTestNotificationResult`、
   `ReminderActions.testNotification` 均不存在；设置页仅保留「立即检查 + 诊断」。
