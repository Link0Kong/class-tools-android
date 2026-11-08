# Round 10 报告 · v3.2.0（2026-11-09 凌晨）

> v3 视觉精修第二轮（P2 起步）+ 补齐「作业单条提醒」入口。版本 **v3.2.0**（versionCode 8），功能其余不变。

## 1. 本轮改动

| 项 | 内容 |
|---|---|
| 全局顶栏玻璃 | `CtTopBar`（所有工具页/设置页共用）改为半透明玻璃条：`surface α0.62` 背景，深浅色自动适配；页面标题区与内容形成层次，不改变布局与语义。 |
| 权限与提醒页 | 介绍卡、通知权限卡、日历权限卡、提醒设置入口卡、系统设置卡全部换 `CtGlassCard`（半透明+亮描边，无阴影规避 GPU 伪影）。 |
| 提醒设置页 | 提醒总开关/作业提醒/上课提醒/通知提醒/安静时段/测试诊断/说明卡全部玻璃化，权限横幅保持语义色。 |
| 作业单条提醒 | 作业列表每行新增铃铛「提醒我」按钮；作业详情弹层新增「提醒我」；点后弹提醒时间选项（截止前 1 天 / 1 小时 / 截止时刻），走 `ReminderOneShotScheduler`（与通知单条提醒同一套 WorkManager 排程、权限提示、错误文案）。 |
| 深色 | 顶栏/玻璃卡自动切深色 token，实测可读。 |

## 2. 验证

| 项 | 结果 |
|---|---|
| 单测 | **779 tests / 0 failures** |
| Debug 构建 | BUILD SUCCESSFUL；真机/模拟器截图：权限页（浅/深）、提醒设置页（浅）、作业页（浅）、数据与隐私页（玻璃顶栏） |
| Release | **2,577,485 B** / SHA-256 `1307A06DD5FB755F14811FEF5A4D9961A0EB64405762702B232CBF8339C29456` / v3.2.0 vc8；apksigner V2 通过；冷启动 1.06s、logcat 0 FATAL/ANR |
| 待补 | toolchain task-32：快速 E2E 38 项回归 + Release 复验 + 文档 §13/§12 |

## 3. 取舍 / 遗留

- 作业单条提醒的选项来自 `ReminderOneShot.homeworkPlannedTimes`（截止前 1 天/1 小时/截止时刻），已过滤过去时间并在 UI 层给出明确 toast；WorkManager 近似排程（Doze 可能延迟数分钟）。
- 余下 P2：通知/权限列表内浅玻璃、抽签/分组/体测/水量/名册页头与主卡玻璃化、动效 token 统一、真实背景模糊方案。
- 用户第 6 点仍为空，待补。

## 4. 文档

- 代码：`CtTopBar.kt`、`PermissionSettingsScreen.kt`、`ReminderSettingsScreen.kt`、`HomeworkScreen.kt`、`MainNavHost.kt`（作业提醒接线）。
- toolchain 追加：`docs/E2E_SMOKE.md §13`、`docs/RELEASE_BUILD.md §12`。

## 5. 最终验证结果（toolchain task-32，2026-11-09 凌晨）

| 项 | 结果 |
|---|---|
| 快速 E2E v3.2.0（Debug vc8） | **38 PASS / 0 WARN / 0 FAIL（818.1s）**；导航 10/10；logcat 0 FATAL/ANR；导出 +1 PNG |
| 重点回归 | `tool_hw` PASS（空态 + 筛选 + 玻璃顶栏，「提醒我」铃铛仅在有数据行渲染，不破坏布局）；`permission_management`/`reminder_settings` PASS（玻璃化后文字断言命中）；`dock_overlay` PASS（pill 唯一、0px 偏差、120dp 留白） |
| 截图 | `6x_v32_homework.png`、`6x_v32_permissions.png`、`6x_v32_reminder.png`、`6x_v32_dark_permission.png`（1080×2400） |
| Release v3.2.0 | **2,577,485 B** / SHA-256 `1307A06D…9456`；apksigner V2 通过；install Success；两次冷启动 OK（2.39s / 1.34s）；logcat 9,574 行 0 FATAL/ANR/NoSuchMethodException |
| 文档 | `E2E_SMOKE.md §13`（526 行）、`RELEASE_BUILD.md §12`（393 行） |
| 边界 | 作业「提醒我」铃铛/详情提醒（截止前1天/1小时/截止时刻）需真实作业数据验证实际排程与到达；本轮空态仅验证入口与布局 |

**结论：v3.2.0（P2 顶栏/权限/提醒页玻璃 + 作业单条提醒）完成并通过回归/Release 验证，APK 已交付桌面。**
