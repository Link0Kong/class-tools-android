# Round 27 报告 · v4.16.0（2026-11-12）

> v4 深度优化 P2：今日课程卡「现在」时间线（H-05）。版本 **v4.16.0**（versionCode 32）。

## 1. 本轮改动

| 审计项 | 内容 |
|---|---|
| H-05 | 工作台「今天」课程卡在「已结束」与「未开始」课程之间插入「现在」细线（左右 `HorizontalDivider` + 主色「现在」标签）；保留「上课中」胶囊与「今日课程已结束 · 下次 …」；`forEachIndexed` 限定仅在 DONE→非 DONE 边界插入，无重复线 |

## 2. 验证

| 项 | 结果 |
|---|---|
| 全量单测 | **1065/0** |
| 预览核验 | `HomeWorkbenchLightPreview`（10:00 场景）渲染出「现在」分割线；`artifacts/previews_h05/` 留档 |
| 快速 E2E（手机 ct-api37） | **43 PASS / 0 WARN / 0 FAIL（931.3s）**；名单三分类、投票/电费/通知横幅/清缓存、深色组、导航 10/10、logcat 0 FATAL/ANR |
| Release v4.16.0 | **2,856,126 B** / SHA-256 `CAACE9A1B19225E19EA79819CED00C3895219F2801FA0F287B52C3175575244B`（vc32）；两次冷启动 0.91s / 0.66s（本轮模拟器负载偏高）；logcat 0 FATAL/ANR |

## 3. 待办

- TalkBack 真机全量人工走查；Baseline Profile 评估
- 真实背景模糊：毛玻璃兜底（PixelCopy 备选）
- P2 剩余：G13 数据新鲜度、A-04 角色徽标、H-05 之外的时间线细化等
