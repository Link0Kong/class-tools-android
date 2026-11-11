# Round 19 报告 · v4.8.0（2026-11-12）

> v4 深度优化 P1 批次 C/D：审计文案清理 + 计数器性能优化。版本 **v4.8.0**（versionCode 24）。

## 1. 本轮改动

| 审计项 | 内容 |
|---|---|
| N-01 | 作业详情删除开发版语气文案「附件预览与发布/删除为后续增量（本轮只读）」 |
| N-02 | 通知未知附件不再暴露内部字段（kind/keys），统一「附件 · 暂不支持预览」 |
| P-06 | 电费慢更提示「数据更新较慢 · 上次变化」与「参考」由 labelSmall 提升为 labelMedium（深色下更清晰） |
| 性能 | `CtAnimatedCounter`/`CtAnimatedDecimalCounter` 改为 `derivedStateOf` 只在显示字符串变化时重组 Text（整数计数从逐帧重组降为按步进重组，配合 `rememberUpdatedState` 保持 formatter 最新） |

## 2. 验证

| 项 | 结果 |
|---|---|
| 全量单测 | **1065/0** |
| 快速 E2E（手机 ct-api37） | **43 PASS / 0 WARN / 0 FAIL（952.1s）**；名单三分类、投票/电费/通知横幅/清缓存、深色组、导航 10/10、logcat 0 FATAL/ANR |
| Release v4.8.0 | **2,856,126 B** / SHA-256 `E930E898C3929EBE943FAED8995703BD13B77D639063AC848833D0A89A6A55BB`（vc24）；两次冷启动 0.63s / 0.44s；logcat 0 FATAL/ANR |

## 3. 待办（P1 剩余）

- N-03 通知搜索 + 筛选空态清除；N-04 URL 附件图片加载
- T-05 写日程权限态/撤销；P-03 宿舍选择器搜索与最近使用；W-03 水量录入实时校验
- DG-02 诊断下拉刷新/暂停轮询；V-02 上传/下载失败行内重试
- 无障碍：TalkBack 全量走查、触控 ≥48dp；性能：Baseline Profile 评估
