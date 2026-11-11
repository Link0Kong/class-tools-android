# Round 12 报告 · v4.1.0（2026-11-12）

> v4 深度优化 P1 批次 A：交互可靠性（全局撤销 + 名单状态修复收尾）。版本 **v4.1.0**（versionCode 17）。

## 1. 本轮改动

| 项 | 内容 |
|---|---|
| 全局撤销（5s Snackbar） | 名单页「清空点名名单 / 重置本轮」、作业完成/取消完成、水量删除记录 → Snackbar 带「撤销」；撤销后写回上一状态并提示「已撤销」 |
| 名单状态收尾（v4.0.3 延续） | 标题/错误文案统一「名单」；空名单/加载失败时名册 tab 仍保留三分类+搜索+刷新；点名失败显示「名单加载失败 + 重试」 |
| E2E 脚本 | 修正名单相关 labels（名单 / 名单 · 点名 / 名单加载失败 / 名单为空）、roster states 含空态；`-SkipWater` 用于测试身份无宿舍稳定回归 |

## 2. 验证

| 项 | 结果 |
|---|---|
| 全量单测 | **1065/0** |
| 快速 E2E（v4.1.0 Debug） | **42 PASS / 0 WARN / 0 FAIL（938.5s）**，含名单三分类+点名、投票页、电费页、通知权限横幅、清缓存确认、深色组、导航 10/10、logcat 0 FATAL/ANR；导出相册 +1 |
| Release v4.1.0 | **2,856,130 B** / SHA-256 `8491DD5055C2C3DA9E5F7CF3AB64B6A5E040EBA8A1C1927C3BF0E07D15CD9E43`（vc17）；两次冷启动 0.51s / 0.33s；logcat 0 FATAL/ANR |
| 证据 | `artifacts/e2e/e2e_v410_fast.log`、`summary.json`（47 张截图）、`v410-release-identity.png` |

## 3. 待办（后续批次）

- 批次 A 余项：更多页面下拉刷新（工作台/诊断/演示）、预测性返回一致性。
- 批次 B：CtMotion 对齐 M3 Expressive motionScheme、specular 跟随触摸、弹层动效统一、列表进入动效。
- 批次 C：无障碍（TalkBack/触控 ≥48dp/大字体 1.3 全页）、平板/横屏。
- 批次 D：动画 phase deferral、列表稳定性、Baseline Profile。
- 体测删除撤销（需云端恢复接口）暂不做。
