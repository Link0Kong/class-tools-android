# Round 47 报告 · v4.36.0（2026-11-12）

> v4 深度优化 动效/自适应：移除动画实时生效（G22 补强）+ 平板自适应实测刷新。版本 **v4.36.0**（versionCode 52）。

## 1. 本轮改动

| 项 | 内容 |
|---|---|
| G22 补强 | `rememberReduceMotion()` 改为 `ContentObserver` 监听 `Settings.Global.ANIMATOR_DURATION_SCALE`：运行中切换「移除动画」立即触发重算，轮换/计数器/PPT 热点随之降级或恢复，无需重进页面 |
| 自适应复核 | 平板 AVD `ct-a37-tablet`（2560×1600@320=800dp）实测：工作台双栏、全部工具、我的三屏截图刷新，checkpoint dock 限宽与 4 列常用工具正常（`artifacts/adaptive-tablet/`） |

## 2. 验证

| 项 | 结果 |
|---|---|
| 全量单测 | **1065/0** |
| 设备实测 | 运行中 `animator_duration_scale` 0↔1 切换：界面正常、logcat 0 FATAL/ANR（`artifacts/v4360-reduce-live-*.png`） |
| 平板截图 | 3 屏自适应正常（`artifacts/adaptive-tablet/`） |
| 快速 E2E（手机 ct-api37） | **43 PASS / 0 WARN / 0 FAIL（1074.9s）**；导航 10/10；深色组+浅色恢复；logcat 0 FATAL/ANR |
| Release v4.36.0 | **3,026,681 B** / SHA-256 `E336A9810B9EA9124D57B558FE777B8184300262C2877A98A5650AAC46271D45`（vc52）；冷启动 **0.32s / 0.27s**；logcat 0 FATAL/ANR |

## 3. 待办

- TalkBack 真机逐项走查（播报顺序/语义人工确认）
- 页面级玻璃化（性能取舍）；Haze 在平板软件渲染下的观感以真机为准
