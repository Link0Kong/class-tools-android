# Round 32 报告 · v4.21.0（2026-11-12）

> v4 深度优化 P2：工作台通知卡数据新鲜度（G13）。版本 **v4.21.0**（versionCode 37）。

## 1. 本轮改动

| 审计项 | 内容 |
|---|---|
| G13 | 工作台「通知待处理」卡在数据就绪（READY）时显示「更新 HH:mm」（取 `NoticesData.fetchedAt`）；失败/离线态保留行内「重试」；避免用户无法判断通知是否最新 |

## 2. 验证

| 项 | 结果 |
|---|---|
| 全量单测 | **1065/0** |
| 预览核验 | `HomeWorkbenchLightPreview` 通知卡显示「更新 10:00」（`artifacts/previews_g13/`） |
| 快速 E2E（手机 ct-api37） | **43 PASS / 0 WARN / 0 FAIL（1008.8s）**；名单三分类、投票/电费/通知横幅/清缓存、深色组、导航 10/10、logcat 0 FATAL/ANR |
| Release v4.21.0 | **2,856,126 B** / SHA-256 `24A2875A9C4DFF8B8D3F9C89C91F1986E4263DF38AA7755EE37DABDEC5938126`（vc37）；两次冷启动 0.88s / 0.64s；logcat 0 FATAL/ANR |

## 3. 待办

- 应用级 Baseline Profile（macrobenchmark 模块）
- 真实背景模糊：毛玻璃兜底（PixelCopy 备选）
- TalkBack 真机全量走查；作业卡/名单页更新时间可按需补
