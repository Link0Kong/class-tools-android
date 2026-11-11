# Round 17 报告 · v4.6.0（2026-11-12）

> v4 深度优化 P1 批次 C/D 续：预览渲染恢复 + 大字投票修复 + 演示列表下拉刷新。版本 **v4.6.0**（versionCode 22）。

## 1. 本轮改动

| 项 | 内容 |
|---|---|
| 预览渲染恢复 | 上次卡在 `android preview render` 的 `preview list` 阶段，本轮带 `-v` 复跑确认是首次 Gradle 预热/依赖解析（后续成功输出 JSON）；批量渲染 **182 张预览**成功（`artifacts/previews/...`），日志 `artifacts/previews/render_v460.log` |
| 大字投票修复 | 预览走查发现「发起投票 → 截止时间」三段标签在 1.3× 字号下被截断为「指…/不…」；标签改为「限时/指定/不限」并重新渲染确认 `VoteCreateLargeFontPreview` 三段完整 |
| 大字投票详情复查 | `VoteDetailLargeFontPreview` 渲染核验：标题/状态标签/选项百分比/投票进度/票数均正常（投票人区在预览高度外，可滚动） |
| 演示下拉刷新 | `PptListContent` 加 `PullToRefreshBox`（权重区域，1200ms 兜底收起），演示列表支持下拉刷新 |
| 其他预览 | 工作台/全部工具/名单/通知/我的 等大字体预览核验，布局无截断 |

## 2. 验证

| 项 | 结果 |
|---|---|
| 全量单测 | **1065/0** |
| 快速 E2E（手机 ct-api37） | **43 PASS / 0 WARN / 0 FAIL（929.0s）**；名单三分类 segments=True、投票/电费/通知横幅/清缓存、深色组、导航 10/10、logcat 0 FATAL/ANR |
| 预览 | 182 张渲染成功；大字投票详情/发起复查通过（157/158 修复后重渲染） |
| Release v4.6.0 | **2,856,126 B** / SHA-256 `61AAD1C2C0914185E1FC733E7BA563E9657C49E66F827BE3B86D7CD6F0227A7A`（vc22）；两次冷启动 0.48s / 0.35s；logcat 0 FATAL/ANR |

## 3. 待办

- TalkBack 全量语义走查（剩余页面）、触控 ≥48dp 逐项核对。
- 批次 D：Baseline Profile 评估、CtAnimatedCounter 绘制阶段化。
- 真实背景模糊：维持毛玻璃（Haze/GraphicsLayer+RenderEffect 模拟器不可见），评估 PixelCopy 合成方案。
