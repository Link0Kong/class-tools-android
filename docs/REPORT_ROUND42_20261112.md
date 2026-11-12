# Round 42 报告 · v4.31.0（2026-11-12）

> v4 深度优化 无障碍/自适应：课表大字体行高自适应（T-01）。版本 **v4.31.0**（versionCode 47）。

## 1. 本轮改动

| 审计项 | 内容 |
|---|---|
| T-01 | `TimetableLayout.metricsFor(widthDp, twoPane, fontScale)` 新增 `fontScale` 参数：系统字体 >1.0x 时按比例放大节次行高、左侧时间列宽与日期表头高度（上限 1.35x）；课程名可完整换行，不再被固定 52dp 行高截断 |
| 适配 | `WeekGrid` 通过 `LocalDensity.current.fontScale` 传入 metrics，字体缩放变化即时生效；行高增加后网格整体变高，依赖页面纵向滚动 |

## 2. 验证

| 项 | 结果 |
|---|---|
| 全量单测 | **1065/0** |
| 预览核验 | 1.3x 字体课表：行高与表头放大，「形势与政策」两行完整显示、房间号不丢（`artifacts/previews_t01/`） |
| 快速 E2E（手机 ct-api37） | **43 PASS / 0 WARN / 0 FAIL（1117.2s）**；课表步骤通过；导航 10/10；深色组+浅色恢复；logcat 0 FATAL/ANR |
| Release v4.31.0 | **2,987,256 B** / SHA-256 `81444CE31E1D4B60FCAC1B11E6A6A169D20FC6AE4A566FDC996F359D1A94A27B`（vc47）；冷启动 0.74s / 0.62s；logcat 0 FATAL/ANR |

## 3. 待办

- 真实背景模糊（毛玻璃兜底，PixelCopy 备选）
- TalkBack 真机全量走查
- T-08 平板两栏底部对齐、A-04 角色徽标、H-07 首屏统一骨架等 P2 剩余
