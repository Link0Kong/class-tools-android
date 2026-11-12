# Round 51 报告 · v4.40.0（2026-11-12）

> v4 深度优化：系统实时活动完善（结果通知 + 通知关闭提示）。版本 **v4.40.0**（versionCode 56）。

## 1. 本轮改动

| 项 | 内容 |
|---|---|
| PPT 结果通知 | 上传成功/失败后补充短暂普通通知「演示上传完成/失败」（自动消失、点击回演示列表），避免实时活动撤销后无反馈 |
| 通知关闭提示 | 通知权限未开启时，开始上传会 Toast 提示「系统通知未开启：上传进度不会显示在通知栏/灵动岛」 |
| Live Update 复测 | 重构后 debug 实时活动测试仍被系统提升 `PROMOTED_ONGOING`（dumpsys 验证），说明通知路径未被破坏 |
| 附带 | 应用级 Baseline Profile 保持 v4.39.0 最新代码版本（17,649 规则） |

## 2. 验证

| 项 | 结果 |
|---|---|
| 全量单测 | **1065/0** |
| 实时活动 | `dumpsys notification`：`flags=ONGOING_EVENT|ONLY_ALERT_ONCE|PROMOTED_ONGOING`；通知栏顶部倒计时卡片 |
| 快速 E2E（手机 ct-api37） | **43 PASS / 0 WARN / 0 FAIL（1036.4s）**；导航 10/10；深色组+浅色恢复；logcat 0 FATAL/ANR |
| Release v4.40.0 | **3,026,717 B** / SHA-256 `52CBB195A10A616323A384F2E0CF27C3855F6A0ED8EE5A731F291FBE7F014852`（vc56）；冷启动 1.27s / 0.78s（模拟器负载高）；logcat 0 FATAL/ANR |

## 3. 待办

- TalkBack 真机逐项走查；ColorOS 16 真机验证流体云呈现
- 页面级玻璃化（性能取舍）
