# Round 22 报告 · v4.11.0（2026-11-12）

> v4 深度优化 P1：写系统日历权限体验（T-05）。版本 **v4.11.0**（versionCode 27）。

## 1. 本轮改动

| 审计项 | 内容 |
|---|---|
| T-05 | 写日历流程改为：直接写入 → 若 `NeedsPermission` 先通过 `rememberLauncherForActivityResult(RequestMultiplePermissions)` 申请 READ/WRITE_CALENDAR → 授权后重试写入 → 仍失败/无账号/拒绝则退回系统 `ACTION_INSERT` 新建日程界面并给出提示；结果统一由 `handleCalendarWriteResult` 处理，不再只有失败 Toast |

## 2. 验证

| 项 | 结果 |
|---|---|
| 全量单测 | **1065/0** |
| 快速 E2E（手机 ct-api37） | **43 PASS / 0 WARN / 0 FAIL（948.3s）**；名单三分类、投票/电费/通知横幅/清缓存、深色组、导航 10/10、logcat 0 FATAL/ANR |
| Release v4.11.0 | **2,856,126 B** / SHA-256 `D101F6341244F79A8EE3671BC12CC0FF184C4872F1D3EDDBF01590BE2EFD686F`（vc27）；两次冷启动 0.66s / 0.39s；logcat 0 FATAL/ANR |
| 说明 | 日历权限系统弹窗不在 E2E 文本断言范围（需系统权限流程），以编译/单测/回退逻辑复核为准；模拟器无日历账号，ACTION_INSERT 兜底路径保留 |

## 3. 待办（P1 剩余）

- DG-01 诊断「简版/详细」分层；V-02 PPT 上传/下载失败行内重试细化
- 无障碍 TalkBack 全量走查、触控 ≥48dp；性能 Baseline Profile 评估
- 真实背景模糊：毛玻璃兜底（PixelCopy 备选）
