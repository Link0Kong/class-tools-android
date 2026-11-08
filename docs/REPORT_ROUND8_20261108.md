# Round 8 报告 · v3.0.0（2026-11-08 夜）

> 用户 6 点反馈（第 6 点待补）的第一轮实现：深色模式、底栏玻璃/对齐、通知系统 v3、课表重构、全局视觉规范。版本 **v3.0.0**（versionCode 6）。

## 1. 用户反馈 → 实现

| # | 反馈 | 本轮实现 |
|---|---|---|
| ① | 手机暗色模式未适配 | 根因：Activity 主题固定 `windowLightStatusBar=true`（黑图标压暗底）+ 窗口背景只有浅色。新增 `values-night/themes.xml`、`values-night/colors.xml`（深色窗口避免冷启动/转场白闪），`ClassToolsTheme` 内按 `darkTheme` 用 `WindowCompat` 切换状态栏/导航栏图标外观。实测深色状态栏图标白色、页面全深色；浅色恢复黑色。 |
| ② | 底栏图标偏移 + 缺 ColorOS17 液态玻璃 | 根因：选中药丸 `offset y=0` 未垂直居中（差 8dp）。已改为按 `(60-44)/2` 居中。底栏从 `Scaffold.bottomBar` 改为**悬浮 overlay**（内容可从玻璃下滚过；工作台/工具/我的底部加 120dp 留白）；玻璃层：半透明 surface（浅色 86% 白玻璃）+ 顶部镜面高光 + 亮描边 + 12dp 悬浮阴影 + 弹性药丸。 |
| ③ | 通知系统异常/功能不全 | `data/reminder` v3：`ReminderEngine.runNow()/testNotification()/diagnostics()`、运行状态与待发事件持久化（Doze 错过补发 10min~6h）、设置变更立即跑、按作业/课表/通知分组（组摘要+子通知静默）、专用小图标 `ic_stat_class_tools`、OEM PendingIntent 兼容、单条提醒 `ReminderOneShotScheduler`（作业/通知可选截止前1天/1小时/截止时刻/活动前1小时，上限 20）。UI：设置页「发送测试通知 / 立即检查 + 诊断」+ 诊断弹窗；通知详情「提醒我」选时间排程。 |
| ④ | 课表比例 + 网页端功能 | 课表页重构（`TimetableDesign.kt` + 64KB 页面）：行高/时间列/列宽自适应（手机 5–7 列不溢出、平板双栏限宽 1200dp）、左右滑动切周（对齐 web/mp 阻尼/提交阈值/飞出飞入）、16 色稳定配色（浅/深，对比度 6.2–9.0）、课程详情（教师/教室/节次/周次/单双周/写日程）、「本周」、下一节/上课中、日视图、导出课表图片（已接 ExportPresenter）、平板右栏。另并行加入**教务登录**（学号+教务密码，仅内存不保存，直接拉课表）与 `TtLocal` 本机快照。 |
| ⑤ | ColorOS17 × 品牌全局风格重规划 | `docs/design/V3_GLOBAL_STYLE.md`（玻璃 L0–L4 层级/浅深参数、品牌色规则、圆角/字体/动效 token、组件清单、页面 P0–P2 优先级）+ `ui/components/glass/CtGlass.kt`（`CtGlassSurface`/`ctGlassSurface`/`ctGlassRefraction`/`CtGlassDockSpec`）+ `docs/design/V3_TIMETABLE_WEB_PARITY.md`（25 项对照）。本轮落地底栏+课表，页面 P1 铺开留待后续轮次。 |
| ⑥ | 用户第 6 点为空 | 待用户补充 |

## 2. 并行新增（另一会话完成，已一并验证）

- 工具页新增「诊断」工具：`DiagScreen`（服务健康 / 设备诊断 / AI 分析 / 修复任务 / 本机代理）+ `data/diag`（`DefaultDiagRepository`、`ClientDiagReporter`、`DiagSanitizer`、`DiagParser`、`DeviceDiagnostics`、崩溃记录与离线补报队列），已由 `AppContainer`、`MainNavHost`、`ToolCatalog` 接线；上报需用户主动触发。
- 教务登录：`TimetableLoginRepository`/`TtLocal`/`TimetableLoginDialog`（密码不保存、不打印），用于课表真实数据拉取。

## 3. 验证

| 项 | 结果 |
|---|---|
| 单测 | **779 tests / 0 failures**（含新增 diag/reminder/timetable login 用例） |
| Debug 构建 | BUILD SUCCESSFUL |
| Release | APK **2,577,485 B** / SHA-256 `615351BF40B45A7FD234D9B53E3E3C5747775A51A2E1C10FDB000D8AD4EE5012`；apksigner V2 通过（证书 974854df…4a1a） |
| Release 冷启动 | 两次 `Status: ok`（1.97s / 1.68s），进程存活、顶部 `MainActivity`；logcat 3,354 行 **0 FATAL EXCEPTION / 0 ANR**（`artifacts/release_v300_logcat.txt`） |
| 深色/底栏截图 | 深色工作台/工具/我的/权限/课表、浅色工作台、底栏玻璃（`artifacts/v3*.png`） |
| 待补 | toolchain task-30：v3.0.0 E2E（原 36 项 + 深色组/底栏/通知设置/诊断页/课表页）、Release 复验、矩阵、文档 §11/§10/§10 |

## 4. 已知边界 / 下一轮

| # | 项 | 说明 |
|---|---|---|
| 1 | 全局页面 P1 玻璃化 | 规范已出，工作台/工具/我的/工具页的玻璃卡与动效细化待铺开 |
| 2 | 底栏真实背景模糊 | 试过 Haze 2.x/1.7（HorizontalPager 图层取样失败、fallback 发灰）→ 本轮采用设计系统半透明玻璃；后续可尝试在 pager 外层单一图层取样或自研 RenderEffect 方案 |
| 3 | 课表真实数据 | 教务登录需本人学号+密码实测；空态/预览已覆盖 |
| 4 | 作业单条提醒 UI | 通知单条提醒已有；作业列表的「提醒我」入口待加（API 已就绪） |
| 5 | 用户第 6 点 | 待补充 |
| 6 | 真实账号/通知到达/诊断上报 | 需测试身份/本人实测 |

## 5. 文档

- `docs/design/V3_GLOBAL_STYLE.md`、`docs/design/V3_TIMETABLE_WEB_PARITY.md`
- `docs/api/NOTIFICATION_V3_IMPL.md`
- toolchain 追加：`docs/E2E_SMOKE.md §11`、`docs/ADAPTIVE_TEST_MATRIX.md §10`、`docs/RELEASE_BUILD.md §10`
