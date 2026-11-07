# `ACCESSIBILITY.md` — 无障碍与排版健壮性（v1.0.1 收尾）

> task-17 交付说明。范围：`ui/screens/all/`、`ui/components/`、`ui/preview/`（新文件）、对应单测、本文档。
> 目标设备：Android 17（API 37），TalkBack；大字体 `fontScale=1.3`；浅/深色两套。

---

## 1. 改动清单

| 文件 | 改动 |
|---|---|
| `ui/components/A11yFormat.kt` | **新增**：`CtA11yFormat`（纯逻辑，可单测）——位置播报 `第 N 个，共 M 个`、拖动中播报、动作标签（首/末项自动隐藏越界动作）、移动后播报文案 |
| `ui/components/Accessibility.kt` | **新增**：`CtA11y`（Compose 自定义操作构造）+ `Modifier.reorderableSemantics(...)`（把「上移/下移」+ 位置状态挂到卡片语义）+ `Modifier.contentSemantics { }` |
| `ui/components/ToolCard.kt` | 新增可选参数 `contentSemantics: Modifier = Modifier`：额外语义应用到 clickable Surface 的**内容节点**，由 clickable 合并进同一读屏焦点（避免多出只含自定义操作的焦点节点） |
| `ui/screens/all/AllToolsScreen.kt` | 每张工具卡挂 `reorderableSemantics`：**TalkBack 通过「操作」菜单「上移 / 下移」即可排序**，走 `ToolOrderLogic.moveBy` → 与拖动完全相同的 `persistOrder()` 路径（写 `prefs.toolOrder` + `at.prefs`）；拖动中/预览拖动中 `stateDescription` 播报「拖动中，第 N 个，共 M 个」；首/末项不出现无效动作 |
| `ui/screens/all/ToolOrderLogic.kt` | 新增 `moveBy(order, id, delta)`（-1/+1，越界原样返回，供 TalkBack 动作使用） |
| `ui/screens/group/GroupScreen.kt` | 组数/每组人数步进的「− / +」按钮补 `contentDescription`（`"$label 减少 1" / "$label 增加 1"`），此前读屏只播报符号 |
| `ui/screens/home/HomeScreen.kt` | 「今日」磁贴数值 `maxLines=1 → 2`，fontScale 1.3 下「下一节课/待办」不再截断 |
| `ui/screens/profile/ProfileScreen.kt` | 卡片行 label 加 `weight(1f)` + value 单行省略；昵称/身份徽章行改用 `FlowRow` 自动换行——大字体/长角色名不横向裁切 |
| `ui/preview/AccessibilityPreviews.kt` | **新增** 6 张 fontScale=1.3 预览（50 工作台 / 51 我的 / 52 通知 / 53 通知深色 / 54 全部工具 / 55 全部工具拖动中） |
| `app/src/test/.../ui/components/CtA11yFormatTest.kt` | **新增** 7 用例（位置/拖动/动作标签首中末/单卡/移动播报） |
| `app/src/test/.../ui/screens/all/ToolOrderA11yTest.kt` | **新增** 6 用例（上移/下移/边界 no-op/未知 id/集合守恒） |

未改：MainNavHost / AppContainer / 主题（色板）/ 构建文件。

---

## 2. TalkBack 排序语义（核心交付）

**现状问题**：全部工具顺序此前只能通过长按拖动调整，TalkBack 用户无法完成该操作（也违背 WCAG 2.1.1 键盘/等价操作要求）。

**实现**：
```kotlin
// AllToolsScreen 每张卡（ToolCard.contentSemantics）
Modifier.reorderableSemantics(
    index = displayIndex,             // 当前顺序下标（实时）
    total = toolsInOrder.size,
    itemLabel = tool.name,
    dragging = tool.id == effectiveDraggingId,
    onMoveUp = { moveCardBy(tool.id, -1) },
    onMoveDown = { moveCardBy(tool.id, +1) },
)
```
- `customActions = [CustomAccessibilityAction("上移"), CustomAccessibilityAction("下移")]`，TalkBack 聚焦卡片后从「操作」菜单触发（不需要手势）；
- `onMoveUp/onMoveDown → ToolOrderLogic.moveBy → order 更新 → persistOrder()`：**与拖动完全同一条** `prefs.toolOrder` 持久化路径（`ToolOrderPrefs.apply`，`at.prefs=max(旧,now)`；无用户修改不写）；
- 边界：首项无「上移」、末项无「下移」；单卡/空列表无动作（`CtA11yFormat.reorderActionLabels`）；
- 位置感：`stateDescription = "第 2 个，共 10 个"`，拖动中为「拖动中，第 2 个，共 10 个」；移动后 TalkBack 会重新聚焦并播报新位置；
- 焦点不重复：语义挂在 clickable 内容节点上并合并进 clickable 焦点；卡片仍保留点击打开工具（拖动结束后 250ms 内点击被抑制，TalkBack 动作不经过点击路径）。

**手动验证步骤**（模拟器已由 Lead E2E 使用）：
1. 设置 → 无障碍 → TalkBack 开启；
2. 全部工具页，左右滑聚焦任一卡片 → 朗读「工具名 + 第 N 个，共 M 个」+「操作」提示；
3. 打开操作菜单 → 选择「下移」→ 卡片顺序变化，焦点跟随；
4. 返回首页再进入全部工具（或杀进程重进）→ 顺序保持（prefs.toolOrder 已写）；关闭 TalkBack 后拖动排序仍可用。

---

## 3. icon-only 控件 contentDescription 审计

**扫描口径**：`ui/screens/**` + `ui/components/**` 中所有 `IconButton` / `CtIconAction` / 可点击图标；装饰性图标（卡片配图、列表 leadingIcon、状态点）保留 `contentDescription = null`（由相邻文本承载语义）。

| 页面 | 关键控件 | 结果 |
|---|---|---|
| 全局 `CtTopBar` | 返回 | ✅ 已有「返回」 |
| 全局 `CtIconAction` | 自定义图标按钮 | ✅ 强制传 `contentDescription`（编译期保证） |
| 全局 `CtTopNavigation` | 底部/侧栏导航项 | ✅ 有描述 |
| 工作台 / 我的 | 编辑/完成、管理、清除、查看 | ✅ 文本按钮（自带语义） |
| 通知 | 刷新、置顶/取消置顶、关闭大图 | ✅ 已有 |
| 通知详情 | 图片附件「点击查看大图」 | ✅ 可点图片用父级 caption/标题（见附件预览实现） |
| 课表 | 刷新、上一周、下一周 | ✅ 已有 |
| 电费 | 刷新 | ✅ 已有 |
| 水量 | 上/下月、上/下一年、删除记录 | ✅ 已有 |
| 名册 | 刷新、到/假（36dp 视觉） | ✅ 已有；触控见 §4 |
| 抽签 | 剔除条目（关闭图标） | ✅ 已有「剔除 XX」 |
| 体测 | 国标表、导出、复制、重算 | ✅ 已有 |
| 分组 | 重新分组、导出、复制 | ✅ 已有；**本轮补：组数/每组人数「−/＋」** |
| 作业 | 刷新 | ✅ 已有 |
| 数据与隐私 | 刷新 | ✅ 已有 |

结论：**本轮唯一缺失是分组页 −/＋ 步进按钮，已补齐**；其余 icon-only 控件均已具备描述。

---

## 4. 触控目标 ≥48dp 检查

| 控件 | 视觉尺寸 | 触控尺寸 | 结论 |
|---|---|---|---|
| `CtTopBar` 返回 / 各页刷新 | 48dp（M3 IconButton 默认） | 48dp | ✅ |
| `CtIconAction` | 44dp | 48dp（`IconButton` 内置 `minimumInteractiveComponentSize`） | ✅ |
| 名册 到/假 | 36dp | 48dp（同上最小交互尺寸） | ✅ |
| 抽签 剔除 | 40dp | 48dp（同上） | ✅ |
| 分组 −/＋ | 48dp | 48dp | ✅ |
| 通知 Checkbox / 置顶 | 48dp | 48dp | ✅ |
| 工具卡（首页/全部） | 半屏宽 × 142dp | ≥48dp | ✅ |
| 月份选择器 / 日期网格 | TextButton/≥40dp 行高 | 48dp（按钮最小高度） | ✅ |

扫描 `Modifier.size(<48.dp)` 与 `clickable` 的组合：未发现「独立小热区」（所有小于 48dp 的视觉尺寸均来自 M3 组件，内部自动扩展触控区）。唯一注意：名册 36dp 图标视觉偏小（触控达标），如需更醒目可后续放大到 40dp（视觉调整，不影响可达性）。

---

## 5. 大字体（fontScale = 1.3）结论

验证方式：新增 `AccessibilityPreviews.kt` 6 张 `@Preview(fontScale = 1.3f)`（50–55），由 Lead 统一渲染验收（本任务只产出预览代码）。

| 页面 | 结论 | 处理 |
|---|---|---|
| 工作台 | 工具卡（固定 142dp）在 1.3x 下内容仍放得下（图标 44 + 内边距 32 + 两行文本 ≈126dp）；「今日」两磁贴原先单行省略，长课名会截断 | 已改「今日」数值 `maxLines=2` |
| 我的 | 设置行 label+value 超宽会顶出（无权重）；昵称+徽章放在普通 Row 里，长角色名在 1.3x 下会横向裁切 | 已改：label `weight(1f)` + value 单行省略；徽章行改 `FlowRow` 自动换行 |
| 通知 | 卡片标题/摘要按设计最多 2 行省略（可展开详情）；顶部标题栏 subtitle 单行省略（信息在筛选栏） | 保留设计；**残余风险**：卡片首行（分类 pill + 状态 pill + 置顶 + Checkbox）在 1.3x + 长分类名下可能挤压缩放，建议后续给分类 pill 加 `maxLines=1` 或改 `FlowRow`（本轮不改 NoticesScreen，避免与 Round3 稳定版冲突） |
| 全部工具 | 2 列网格固定 142dp 卡片在 1.3x 下文字放得下；拖动 ghost/命中几何不随字体变化；TalkBack 位置描述与 1.0x 相同 | ✅ |
| 全局顶栏 | 标题/副标题 `maxLines=1` 省略（导航语义，属预期） | 保留 |

---

## 6. 浅/深色对比度问题清单（仅审计，不改色板）

方法：WCAG 2.1 相对亮度公式，逐对计算 theme token 对比度（正文 AA 4.5:1、大字号/图形 3:1）。色值取自 `ui/theme/Color.kt`。

### 6.1 通过（AA 以上，供对照）

| 组合（浅色） | 对比度 | 组合（深色） | 对比度 |
|---|---|---|---|
| onSurface / surface | 16.26 | onSurface / surface | 14.37 |
| onSurfaceVariant / surface | 8.90 | onSurfaceVariant / surface | 10.91 |
| onSurfaceVariant / surfaceContainerLow | 8.52 | onSurfaceVariant / surfaceContainerLow | 10.11 |
| onSurfaceVariant / surfaceContainerHigh（胶囊） | 7.65 | onSurfaceVariant / surfaceContainerHigh | 8.50 |
| primary / surfaceContainerLow | 5.86 | primary / surfaceContainerLow | 10.05 |
| onPrimary / primary（主按钮） | 6.46 | onPrimary / primary | 7.66 |
| onPrimaryContainer / primaryContainer | 13.32 | onPrimaryContainer / primaryContainer | 7.29 |
| onErrorContainer / errorContainer | 13.28 | onErrorContainer / errorContainer | 7.23 |
| error / surface | 6.13 | error / surface | 10.85 |
| success·warning·info / surface | 4.74 / 5.05 / 5.31 | success·warning·info / surface | 10.36 / 11.78 / 10.44 |
| onPrimaryContainer@0.8 / primaryContainer@0.55 | 8.13 | 同组合 | 6.92 |
| onSurfaceVariant@0.7 / surfaceContainerLow（日期/次要标签） | 3.89 | 同组合 | 5.61 |
| onSurfaceVariant@0.8 / surfaceContainerLowest | 5.34 | 同组合 | 7.55 |

### 6.2 问题（按优先级）

| 级别 | 位置 | 组合 | 浅色 | 深色 | 影响 | 建议（不改色板） |
|---|---|---|---|---|---|---|
| **P1** | `ui/screens/power/PowerTrendChart.kt:201`（趋势图坐标轴/刻度文本，10–11sp） | `onSurfaceVariant.copy(alpha=0.45f)` / `surfaceContainerLowest` | **2.28（FAIL，连 3:1 都不到）** | 3.12（图形级 OK，正文仍 <4.5） | 数据坐标读不清，弱视/强光下几乎不可见 | 改为 `onSurfaceVariant.copy(alpha=0.7f)`（浅 4.06）或干脆不透明 `onSurfaceVariant`（8.90/10.91，AA 达标） |
| **P1** | `ui/screens/timetable/TimetableViewScreen.kt:471`（节次开始时间，labelSmall） | `onSurfaceVariant.copy(alpha=0.7f)` / `surfaceContainerLowest` | **4.06（<4.5 AA）** | 5.98 ✅ | 小字号正文不达标 | 改 `alpha=0.8f`（5.34 AA）或不透明 |
| **P2** | `ui/components/ToolCard.kt:93` 图标气泡（工具 accent 图标 24dp，浅色） | accent 图标 / `accent@0.20 over surfaceContainerLow` | teal 2.74、blue 2.61、leaf 2.78、sand 2.24、gold 2.04、water 2.84、clay 3.00、roster 3.29、gray 3.22（3:1 图形标准：6/10 不达标） | 深色全部 ≥2.97，仅 roster 2.97 边界 | 图标与文字重复表达（可视为装饰，WCAG 1.4.11 有豁免），但色弱用户辨识工具变慢 | 气泡底改 `surfaceContainerHigh`（图标对 7.65/8.50 对比），accent 只做小圆点/描边；或浅色模式 accent 加深一档（设计 token 调整，需设计确认） |
| **P2** | `ui/components/ToolCard.kt:93` 同上的「暖沙」`BrandWarmSand` | `#B98F63` 作为文字/图标 | 2.78（若当小正文用即 FAIL） | 6.32 ✅ | 设计文档已声明品牌色仅用于大字号/图形；当前无小正文使用 | 保持现状，**代码评审约定**：品牌三色不得用于 <18sp 正文（本轮未发现违规） |
| **P3** | `ui/components/CtTopBar.kt` / 列表分隔线 | `outline` / surface = 4.25（文字级别 <4.5，图形 3:1 达标） | 4.25 | 5.87 | 若 outline 被用作文本会不达标；目前仅边框/图标 | 维持；新增代码不要用 outline 作正文色 |

**结论**：明暗两套 48 角色文本对比整体健康（关键正文组合 5.86–16.26），仅 2 处降低透明度的**小字号正文**（P1）需要修；工具卡图标（P2）属可接受的装饰性图形，建议后续随设计 token 一起调整。**本轮未改任何色板/token。**

---

## 7. 验证方式与测试

```powershell
$env:JAVA_HOME='C:\AndroidToolchain\jdk17'
$env:ANDROID_HOME='C:\AndroidToolchain\sdk'
$env:ANDROID_SDK_ROOT='C:\AndroidToolchain\sdk'
cd C:\Users\Ko_Li\.openclaw\workspace\android-class-tools
& 'C:\AndroidToolchain\gradle\gradle-9.9.0\bin\gradle.bat' :app:compileDebugKotlin :app:testDebugUnitTest --console=plain
```

- 单测：`CtA11yFormatTest` 7 例 + `ToolOrderA11yTest` 6 例 = **13 新用例**（见任务汇报的全量数字，`0 failures`）。
- 预览：`AccessibilityPreviews.kt` 50–55（fontScale=1.3，含深色）。
- TalkBack 手动验证：见 §2 步骤（需真机/模拟器，由 Lead/E2E 执行；本任务仅静态验证语义定义与动作映射）。

---

## 8. 遗留 / 建议后续
1. **Notices 卡片首行**在 1.3x + 长分类名下可能压缩（§5），建议把分类/状态 pill 组改 `FlowRow` 或给 pill 加 `maxLines=1`（涉及 `NoticesScreen`，本轮未动）。
2. **趋势图坐标轴透明度**（§6 P1）与 **课表节次时间透明度**（§6 P1）建议在下一轮直接修（各改 1 个 alpha 常量），影响的是 `ui/screens/power` 与 `ui/screens/timetable`，不在本次写范围。
3. 工具卡图标对比（§6 P2）需要设计确认是否调整浅色 accent 或气泡底色（不允许本轮改色板）。
4. 无障碍自定义操作目前只在「全部工具」页实现；后续可把「上移/下移」推广到水量记录排序等列表（复用 `reorderableSemantics`）。
5. 截图/TalkBack 实测由 Lead 在 E2E 阶段执行（本任务未启动模拟器，避免与 E2E 抢占设备）。
